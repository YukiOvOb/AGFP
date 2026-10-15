param(
  [Parameter(Mandatory=$true)][string]$PostgresBin,
  [string]$JavaHome = $env:JAVA_HOME,
  [string]$MavenCommand = '',
  [int]$DatabasePort = 55433,
  [int]$BackendPort = 8082,
  [int]$WebPort = 5174,
  [switch]$SkipBuild,
  [switch]$KeepAlive
)
$ErrorActionPreference = 'Stop'
function RunLogged([string]$File, [string[]]$Arguments, [string]$Log) {
  # Windows PowerShell 5.1 turns native stderr warnings into ErrorRecords.
  # Native success/failure is determined by the process exit code.
  $savedPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    & $File @Arguments *> $Log
    $exitCode = $LASTEXITCODE
  } finally { $ErrorActionPreference = $savedPreference }
  if ($exitCode -ne 0) { Get-Content -LiteralPath $Log -Tail 35; throw "Native command failed ($exitCode): $File" }
}
$repo = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $repo '.cache/local-demo'
$stateFile = Join-Path $runtime 'state.json'
$accessFile = Join-Path $runtime 'access.json'
$data = Join-Path $runtime 'pg18'
$pg = (Resolve-Path -LiteralPath $PostgresBin).Path
if ((& (Join-Path $pg 'postgres.exe') --version) -notmatch 'PostgreSQL\) 18\.') { throw 'PostgreSQL 18 is required' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
$node = (Get-Command node -ErrorAction Stop).Source
$pnpm = (Get-Command pnpm -ErrorAction Stop).Source
if ((& $node --version) -notmatch '^v24\.') { throw 'Node.js 24 is required' }
if (!$MavenCommand) { $MavenCommand = Join-Path $repo 'app/mvnw.cmd' }
if (Test-Path -LiteralPath $stateFile) { throw 'A local demo state exists. Run scripts/stop-local.ps1 first.' }
$databaseAlreadyRunning = $false
foreach ($port in @($DatabasePort, $BackendPort, $WebPort)) {
  $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
  try { $listener.Start() } catch {
    $pidFile = Join-Path $data 'postmaster.pid'
    if ($port -eq $DatabasePort -and (Test-Path -LiteralPath $pidFile)) {
      $pidLines = Get-Content -LiteralPath $pidFile
      $savedPreference = $ErrorActionPreference
      try {
        $ErrorActionPreference = 'Continue'
        & (Join-Path $pg 'pg_ctl.exe') -D $data status *> (Join-Path $runtime 'pg-status.log')
        $runningCode = $LASTEXITCODE
      } finally { $ErrorActionPreference = $savedPreference }
      if ($runningCode -eq 0 -and $pidLines[3] -eq "$DatabasePort" -and $pidLines[5] -eq '127.0.0.1') { $databaseAlreadyRunning = $true }
    }
    if (!$databaseAlreadyRunning -or $port -ne $DatabasePort) { throw "Port $port is already in use; choose another port. No existing process was stopped." }
  }
  finally { $listener.Stop() }
}
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$jar = Join-Path $repo 'app/target/serms-0.1.0-SNAPSHOT.jar'
$runtimeJar = Join-Path $runtime 'serms-demo.jar'
$vite = Join-Path $repo 'frontend/node_modules/vite/bin/vite.js'
if (!$SkipBuild) {
  Write-Output 'Building the backend (test verification is a separate command)...'
  Push-Location $repo
  try {
    # Clean first: app/target content left by another branch's build would otherwise be packaged into the JAR.
    RunLogged $MavenCommand @('-B','-f','app/pom.xml','clean','package','-DskipTests') (Join-Path $runtime 'build-backend.log')
  } finally { Pop-Location }
  Push-Location (Join-Path $repo 'frontend')
  try {
    RunLogged $pnpm @('install','--frozen-lockfile') (Join-Path $runtime 'install-frontend.log')
    RunLogged $pnpm @('build') (Join-Path $runtime 'build-frontend.log')
  } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $jar) -or !(Test-Path -LiteralPath $vite)) { throw 'Build artifacts are missing. Start without -SkipBuild.' }
Copy-Item -LiteralPath $jar -Destination $runtimeJar -Force
if (!(Test-Path -LiteralPath $accessFile)) {
  if (Test-Path -LiteralPath $data) { throw 'Existing demo data has no access.json. Restore the local access file; do not overwrite the database.' }
  $accounts = @('borrower@example.test','borrower2@example.test','approver@example.test','custodian@example.test','maintainer@example.test','admin@example.test')
  $access = [ordered]@{databasePassword=[guid]::NewGuid().ToString('N'); demoPassword=[guid]::NewGuid().ToString('N'); accounts=$accounts}
  $access | ConvertTo-Json | Set-Content -LiteralPath $accessFile -Encoding utf8
}
$access = Get-Content -LiteralPath $accessFile -Raw | ConvertFrom-Json
$passwordFile = Join-Path $runtime 'db-password.txt'
$access.databasePassword | Set-Content -LiteralPath $passwordFile -Encoding ascii
$keys = @('PGPASSWORD','SERMS_DB_URL','SERMS_DB_USER','SERMS_DB_PASSWORD','SPRING_PROFILES_ACTIVE','SERVER_ADDRESS','SERVER_PORT','SERMS_API_TARGET','SERMS_LOCAL_DEMO_PASSWORD')
$previous = @{}; foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$state = [ordered]@{repo=$repo; postgresBin=$pg; data=$data; databasePort=$DatabasePort; backendPort=$BackendPort; webPort=$WebPort; backendJar=$runtimeJar; backendPid=0; frontendPid=0}
$started = $false
function Ready([string]$url, [Diagnostics.Process]$process, [string]$log) {
  for ($i=0; $i -lt 90; $i++) {
    try { $result = Invoke-RestMethod -Uri $url -TimeoutSec 2; if ($result.status -eq 'UP') { return } } catch {}
    # A process that has already exited cannot become ready; report its log instead of waiting out the retries.
    if ($process.HasExited) { throw "Process $($process.Id) exited before $url became ready. Inspect $log (and the .err file next to it)." }
    Start-Sleep -Milliseconds 500
  }
  throw "Readiness check failed: $url. Inspect $runtime logs."
}
try {
  $env:PGPASSWORD = $access.databasePassword
  if (!(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
    RunLogged (Join-Path $pg 'initdb.exe') @('-D',$data,'-U','serms_local','-A','scram-sha-256',"--pwfile=$passwordFile",'--encoding=UTF8','--locale=C') (Join-Path $runtime 'initdb.log')
  }
  Write-Output 'Starting isolated PostgreSQL 18...'
  if (!$databaseAlreadyRunning) {
    $pgStart = Start-Process -FilePath (Join-Path $pg 'pg_ctl.exe') -ArgumentList @('-D',('"'+$data+'"'),'-l',('"'+(Join-Path $runtime 'postgres.log')+'"'),'-o',('"-h 127.0.0.1 -p '+$DatabasePort+'"'),'-w','start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'pg-start.out') -RedirectStandardError (Join-Path $runtime 'pg-start.err')
    if (!$pgStart.WaitForExit(30000)) { throw 'PostgreSQL startup timed out; inspect pg-start.err' }
    $pgStart.WaitForExit()
    $pgStart.Refresh()
    if ($null -ne $pgStart.ExitCode -and $pgStart.ExitCode -ne 0) { throw 'PostgreSQL startup failed; inspect pg-start.err' }
  } else { Write-Output "Reusing this checkout's PostgreSQL cluster after an interrupted startup." }
  $started = $true
  $exists = & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p $DatabasePort -U serms_local -d postgres -w -tAc "SELECT 1 FROM pg_database WHERE datname='serms_local_demo'"
  if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the isolated database' }
  if ($exists -ne '1') {
    & (Join-Path $pg 'createdb.exe') -h 127.0.0.1 -p $DatabasePort -U serms_local -w serms_local_demo
    if ($LASTEXITCODE -ne 0) { throw 'Demo database creation failed' }
  }
  $env:SERMS_DB_URL = "jdbc:postgresql://127.0.0.1:$DatabasePort/serms_local_demo?currentSchema=serms,public"
  $env:SERMS_DB_USER = 'serms_local'
  $env:SERMS_DB_PASSWORD = $access.databasePassword
  $env:SPRING_PROFILES_ACTIVE = 'local'
  $env:SERVER_ADDRESS = '127.0.0.1'
  $env:SERVER_PORT = "$BackendPort"
  Write-Output 'Starting Spring Boot and applying the existing Flyway migrations...'
  $backend = Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$runtimeJar+'"')) -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'backend.log') -RedirectStandardError (Join-Path $runtime 'backend.err')
  $state.backendPid = $backend.Id
  Ready "http://127.0.0.1:$BackendPort/api/v1/health" $backend (Join-Path $runtime 'backend.log')
  $env:SERMS_LOCAL_DEMO_PASSWORD = $access.demoPassword
  RunLogged (Join-Path $pg 'psql.exe') @('-h','127.0.0.1','-p',"$DatabasePort",'-U','serms_local','-d','serms_local_demo','-w','-f',(Join-Path $PSScriptRoot 'demo-data.sql')) (Join-Path $runtime 'seed.log')
  $env:SERMS_API_TARGET = "http://127.0.0.1:$BackendPort"
  Write-Output 'Starting React with same-origin forwarding to the real backend...'
  $frontend = Start-Process -FilePath $node -ArgumentList @(('"'+$vite+'"'),'--host','127.0.0.1','--port',"$WebPort") -WorkingDirectory (Join-Path $repo 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'frontend.log') -RedirectStandardError (Join-Path $runtime 'frontend.err')
  $state.frontendPid = $frontend.Id
  Ready "http://127.0.0.1:$WebPort/api/v1/health" $frontend (Join-Path $runtime 'frontend.log')
  $state | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding utf8
  Write-Output "Local SERMS: http://127.0.0.1:$WebPort"
  Write-Output "Local-only accounts/password: $accessFile"
  Write-Output "Stop: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stop-local.ps1"
} catch {
  if ($state.frontendPid) { Stop-Process -Id $state.frontendPid -ErrorAction SilentlyContinue }
  if ($state.backendPid) { Stop-Process -Id $state.backendPid -ErrorAction SilentlyContinue }
  if ($started) { & (Join-Path $pg 'pg_ctl.exe') -D $data -m fast -w stop *> (Join-Path $runtime 'pg-stop.log') }
  throw
} finally {
  foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
if ($KeepAlive) {
  # Keep child services alive in terminals that use a process job for cleanup.
  while (Test-Path -LiteralPath $stateFile) { Start-Sleep -Seconds 2 }
}
