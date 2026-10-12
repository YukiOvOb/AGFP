param([string]$PostgresBin = '')
# Isolated databases only. Existing development databases and deployment are never touched.
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$runId = [guid]::NewGuid().ToString('N').Substring(0,10)
$testDir = Join-Path $repo ".cache/zhoufanhao-$runId"
$container = "serms-zhou-$runId"
$secret = [guid]::NewGuid().ToString('N')
$keys = @('PGPASSWORD','SERMS_TEST_JDBC_URL','SERMS_TEST_DB_USER','SERMS_TEST_DB_PASSWORD',
  'SERMS_APP_TEST_JDBC_URL','SERMS_APP_TEST_DB_USER','SERMS_APP_TEST_DB_PASSWORD',
  'SERMS_UC03_TEST_JDBC_URL','SERMS_UC03_TEST_DB_USER','SERMS_UC03_TEST_DB_PASSWORD')
$previous = @{}; foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
$started = $false
New-Item -ItemType Directory -Force $testDir | Out-Null
Push-Location $repo
function SqlFile([string]$database,[string]$file) {
  if ($PostgresBin) { & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $testPort -U serms_test -d $database -v ON_ERROR_STOP=1 -f $file }
  else {
    docker cp $file "${container}:/tmp/test.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy SQL' }
    docker exec $container psql -U serms_test -d $database -v ON_ERROR_STOP=1 -f /tmp/test.sql
  }
  if ($LASTEXITCODE -ne 0) { throw "SQL failed: $file" }
}
try {
  if ($PostgresBin) {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,0)
    $listener.Start(); $testPort = $listener.LocalEndpoint.Port; $listener.Stop()
    & (Join-Path $PostgresBin 'initdb.exe') -D (Join-Path $testDir 'pg') -U serms_test -A trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed' }
    # Keep the server from inheriting PowerShell's captured output handles. Waiting
    # for pg_ctl alone also avoids waiting for the long-running PostgreSQL process.
    $pgStartup = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @(
      '-D', ('"{0}"' -f (Join-Path $testDir 'pg')),
      '-l', ('"{0}"' -f (Join-Path $testDir 'postgres.log')),
      '-o', ('"-h 127.0.0.1 -p {0}"' -f $testPort), '-w', 'start'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testDir 'pg-start.out') -RedirectStandardError (Join-Path $testDir 'pg-start.err')
    if (-not $pgStartup.WaitForExit(30000) -or $pgStartup.ExitCode -ne 0) { throw 'PostgreSQL startup failed' }
    $started = $true
    $env:PGPASSWORD = ''
    foreach ($database in @('serms_test','serms_test_app','serms_test_uc03')) {
      & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $testPort -U serms_test $database
      if ($LASTEXITCODE -ne 0) { throw 'createdb failed' }
    }
    $secret = ''
  } else {
    docker run -d --name $container -e POSTGRES_DB=serms_test -e POSTGRES_USER=serms_test -e "POSTGRES_PASSWORD=$secret" -p 127.0.0.1::5432 postgres:18-alpine
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop, or pass -PostgresBin for an installed PostgreSQL 18' }
    $started = $true
    $ready = $false
    for ($i=0;$i -lt 60;$i++) {
      docker exec $container pg_isready -U serms_test -d serms_test *> $null
      if ($LASTEXITCODE -eq 0) { $ready=$true; break }; Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'PostgreSQL not ready' }
    $testPort = ((docker port $container 5432/tcp).Trim() -split ':')[-1]
    foreach ($database in @('serms_test_app','serms_test_uc03')) {
      docker exec $container createdb -U serms_test $database
      if ($LASTEXITCODE -ne 0) { throw 'createdb failed' }
    }
  }
  $base = 'app/src/main/resources/db/migration'
  SqlFile 'serms_test' "$base/V001__sprint1.sql"
  SqlFile 'serms_test' 'database/src/test/resources/v1-upgrade-fixture.sql'
  SqlFile 'serms_test' "$base/V002__align_serms_model.sql"
  foreach ($prefix in @('SERMS_TEST','SERMS_APP_TEST','SERMS_UC03_TEST')) {
    $database = switch($prefix) { 'SERMS_TEST' {'serms_test'} 'SERMS_APP_TEST' {'serms_test_app'} default {'serms_test_uc03'} }
    [Environment]::SetEnvironmentVariable("${prefix}_JDBC_URL","jdbc:postgresql://127.0.0.1:$testPort/$database",'Process')
    [Environment]::SetEnvironmentVariable("${prefix}_DB_USER",'serms_test','Process')
    [Environment]::SetEnvironmentVariable("${prefix}_DB_PASSWORD",$secret,'Process')
  }
  mvn -B -f database/pom.xml verify
  if ($LASTEXITCODE -ne 0) { throw 'V002 database regression failed' }
  SqlFile 'serms_test' 'database/src/test/resources/v4-upgrade-fixture.sql'
  SqlFile 'serms_test' "$base/V004__loan_operations.sql"
  SqlFile 'serms_test' 'database/src/test/resources/v4-upgrade-assertions.sql'
  mvn -B -f app/pom.xml -Puc03-postgres-it clean verify
  if ($LASTEXITCODE -ne 0) { throw 'Application or UC03 PostgreSQL tests failed' }
} finally {
  if ($started) {
    if ($PostgresBin) { & (Join-Path $PostgresBin 'pg_ctl.exe') -D (Join-Path $testDir 'pg') -m fast -w stop | Out-Null }
    else { docker rm -f -v $container | Out-Null }
  }
  foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
  Pop-Location
}
