param(
  [string]$PostgresBin = '',
  [switch]$IncludeLoanIntegration
)
# Disposable loopback databases only; never uses the configured development/deployment database.
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path $PSScriptRoot -Parent
$taskRunId = [guid]::NewGuid().ToString('N').Substring(0,10)
$taskDirectory = Join-Path $taskRepo ".cache/uc02-$taskRunId"
$taskContainer = "serms-uc02-$taskRunId"
$taskPassword = [guid]::NewGuid().ToString('N')
$taskKeys = foreach ($taskPrefix in @('SERMS_APP_TEST','SERMS_UC02_TEST','SERMS_UC03_TEST','SERMS_UC05_TEST')) {
  foreach ($taskSuffix in @('JDBC_URL','DB_USER','DB_PASSWORD')) { "${taskPrefix}_${taskSuffix}" }
}
$taskPrevious = @{}
foreach ($taskKey in $taskKeys) { $taskPrevious[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey,'Process') }
$taskStarted = $false
New-Item -ItemType Directory -Path $taskDirectory | Out-Null
Push-Location $taskRepo
try {
  if ($PostgresBin) {
    $taskPostgresBin = (Resolve-Path -LiteralPath $PostgresBin).Path
    $taskVersion = & (Join-Path $taskPostgresBin 'postgres.exe') --version
    if ($LASTEXITCODE -ne 0 -or $taskVersion -notmatch 'PostgreSQL\) 18\.') { throw 'PostgreSQL 18 is required' }
    $taskListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,0)
    $taskListener.Start(); $taskPort = $taskListener.LocalEndpoint.Port; $taskListener.Stop()
    # Trust is for throw-away localhost fixtures only. No system service or existing data is used.
    & (Join-Path $taskPostgresBin 'initdb.exe') -D (Join-Path $taskDirectory 'pg') -U serms_test -A trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the disposable PostgreSQL cluster' }
    & (Join-Path $taskPostgresBin 'pg_ctl.exe') -D (Join-Path $taskDirectory 'pg') -l (Join-Path $taskDirectory 'postgres.log') -o "-h 127.0.0.1 -p $taskPort" -w start
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the disposable PostgreSQL cluster' }
    $taskStarted = $true; $taskPassword = ''
  } else {
    docker run -d --name $taskContainer -e POSTGRES_USER=serms_test -e "POSTGRES_PASSWORD=$taskPassword" -p 127.0.0.1::5432 postgres:18-alpine
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop or provide -PostgresBin' }
    $taskStarted = $true; $taskReady = $false
    for ($taskAttempt=0; $taskAttempt -lt 60; $taskAttempt++) {
      # The image's temporary init server accepts Unix-socket connections, then restarts.
      # Only the final server listens on TCP; do not accept temporary socket readiness.
      docker exec $taskContainer pg_isready -h 127.0.0.1 -p 5432 -U serms_test -d postgres *> $null
      if ($LASTEXITCODE -eq 0) { $taskReady = $true; break }
      Start-Sleep -Seconds 1
    }
    if (!$taskReady) { throw 'Disposable PostgreSQL did not become ready' }
    $taskPort = ((docker port $taskContainer 5432/tcp).Trim() -split ':')[-1]
  }
  foreach ($taskEntry in @(
      @{Prefix='SERMS_APP_TEST'; Database="serms_test_uc02_history_$taskRunId"},
      @{Prefix='SERMS_UC02_TEST'; Database="serms_test_uc02_$taskRunId"},
      @{Prefix='SERMS_UC03_TEST'; Database="serms_test_uc03_$taskRunId"},
      @{Prefix='SERMS_UC05_TEST'; Database="serms_test_uc05_$taskRunId"})) {
    if ($PostgresBin) {
      & (Join-Path $taskPostgresBin 'createdb.exe') -h 127.0.0.1 -p $taskPort -U serms_test $taskEntry.Database
    } else { docker exec $taskContainer createdb -U serms_test $taskEntry.Database }
    if ($LASTEXITCODE -ne 0) { throw 'Could not create an isolated test database' }
    [Environment]::SetEnvironmentVariable($taskEntry.Prefix+'_JDBC_URL',"jdbc:postgresql://127.0.0.1:$taskPort/"+$taskEntry.Database,'Process')
    [Environment]::SetEnvironmentVariable($taskEntry.Prefix+'_DB_USER','serms_test','Process')
    [Environment]::SetEnvironmentVariable($taskEntry.Prefix+'_DB_PASSWORD',$taskPassword,'Process')
  }
  Push-Location (Join-Path $taskRepo 'app')
  try {
    if ($IsWindows -or $env:OS -eq 'Windows_NT') { $taskMaven = '.\mvnw.cmd' } else { $taskMaven = './mvnw' }
    & $taskMaven -B -Puc02-postgres-it clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Application regression or UC02 PostgreSQL acceptance failed' }
    if ($IncludeLoanIntegration) {
      # The main profile includes UC03 and UC05. Give each a fresh database; otherwise UC05's
      # fallback to the UC03 connection imports unrelated active loans into its global projection.
      & $taskMaven -B -Puc03-postgres-it failsafe:integration-test failsafe:verify
      if ($LASTEXITCODE -ne 0) { throw 'UC03/UC05 PostgreSQL integration regression failed' }
    }
  } finally { Pop-Location }
} finally {
  if ($taskStarted) {
    if ($PostgresBin) {
      & (Join-Path $taskPostgresBin 'pg_ctl.exe') -D (Join-Path $taskDirectory 'pg') -m fast -w stop | Out-Null
    } else { docker stop $taskContainer | Out-Null }
  }
  foreach ($taskKey in $taskKeys) { [Environment]::SetEnvironmentVariable($taskKey,$taskPrevious[$taskKey],'Process') }
  Pop-Location
}
