param([Parameter(Mandatory=$true)][string]$PostgresBin)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$runId = [guid]::NewGuid().ToString('N').Substring(0, 10)
$run = Join-Path $repo ".cache/issue17-$runId"
$data = Join-Path $run 'pg'
$old = @{}
foreach ($key in @('SERMS_EQUIPMENT_TEST_JDBC_URL','SERMS_EQUIPMENT_TEST_DB_USER','SERMS_EQUIPMENT_TEST_DB_PASSWORD',
    'SERMS_APP_TEST_JDBC_URL','SERMS_APP_TEST_DB_USER','SERMS_APP_TEST_DB_PASSWORD')) {
  $old[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
New-Item -ItemType Directory -Force $run | Out-Null
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start(); $port = $listener.LocalEndpoint.Port; $listener.Stop()
$started = $false
Push-Location $repo
try {
  & (Join-Path $PostgresBin 'initdb.exe') -D $data -U serms_test -A trust --encoding=UTF8 --locale=C *> (Join-Path $run 'initdb.log')
  if ($LASTEXITCODE -ne 0) { throw 'initdb failed' }
  $start = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @(
    '-D', ('"{0}"' -f $data), '-l', ('"{0}"' -f (Join-Path $run 'postgres.log')),
    '-o', ('"-h 127.0.0.1 -p {0}"' -f $port), '-w', 'start'
  ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'start.out') -RedirectStandardError (Join-Path $run 'start.err')
  $started = $true
  if (-not $start.WaitForExit(30000)) { throw 'PostgreSQL startup timed out' }
  $start.WaitForExit()
  $start.Refresh()
  if ($null -ne $start.ExitCode -and $start.ExitCode -ne 0) { throw 'PostgreSQL startup failed' }
  & (Join-Path $PostgresBin 'pg_ctl.exe') -D $data status *> (Join-Path $run 'status.log')
  if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL is not running after startup' }
  & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $port -U serms_test serms_test_equipment
  if ($LASTEXITCODE -ne 0) { throw 'createdb failed' }
  & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $port -U serms_test serms_test_app
  if ($LASTEXITCODE -ne 0) { throw 'createdb failed' }
  $env:SERMS_EQUIPMENT_TEST_JDBC_URL = "jdbc:postgresql://127.0.0.1:$port/serms_test_equipment"
  $env:SERMS_EQUIPMENT_TEST_DB_USER = 'serms_test'
  $env:SERMS_EQUIPMENT_TEST_DB_PASSWORD = ''
  $env:SERMS_APP_TEST_JDBC_URL = "jdbc:postgresql://127.0.0.1:$port/serms_test_app"
  $env:SERMS_APP_TEST_DB_USER = 'serms_test'
  $env:SERMS_APP_TEST_DB_PASSWORD = ''
  mvn -B -f app/pom.xml -Puc03-postgres-it '-Dit.test=EquipmentManagementPostgresIT' verify
  if ($LASTEXITCODE -ne 0) { throw 'Equipment management PostgreSQL tests failed' }
} finally {
  if ($started) { & (Join-Path $PostgresBin 'pg_ctl.exe') -D $data -m fast -w stop | Out-Null }
  foreach ($key in $old.Keys) { [Environment]::SetEnvironmentVariable($key, $old[$key], 'Process') }
  Pop-Location
}
