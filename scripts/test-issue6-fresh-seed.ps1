param([Parameter(Mandatory=$true)][string]$PostgresBin)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$run = Join-Path $repo ('.cache/issue6-fresh-' + [guid]::NewGuid().ToString('N').Substring(0, 10))
$data = Join-Path $run 'pg'
$pg = (Resolve-Path -LiteralPath $PostgresBin).Path
$jar = Join-Path $repo 'app/target/serms-0.1.0-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Build the backend JAR first.' }
New-Item -ItemType Directory -Force $run | Out-Null
function FreePort {
  $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
  $listener.Start(); $port = $listener.LocalEndpoint.Port; $listener.Stop()
  return $port
}
$dbPort = FreePort
$appPort = FreePort
$old = @{}
foreach ($key in @('SERMS_DB_URL','SERMS_DB_USER','SERMS_DB_PASSWORD','SPRING_PROFILES_ACTIVE',
    'SERVER_ADDRESS','SERVER_PORT','SERMS_LOCAL_DEMO_PASSWORD')) {
  $old[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
$backend = $null
$started = $false
function Query([string]$sql) {
  $result = & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p $dbPort -U serms_fresh -d serms_local_demo -tA -v ON_ERROR_STOP=1 -c $sql
  if ($LASTEXITCODE -ne 0) { throw 'Fresh database query failed.' }
  return ($result -join '').Trim()
}
function Seed([string]$log) {
  $preference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p $dbPort -U serms_fresh -d serms_local_demo -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot 'demo-data.sql') *> (Join-Path $run $log)
    $exitCode = $LASTEXITCODE
  } finally { $ErrorActionPreference = $preference }
  if ($exitCode -ne 0) { throw "Seed failed: $log" }
}
function StartBackend {
  $process = Start-Process -FilePath 'java.exe' -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'backend.log') -RedirectStandardError (Join-Path $run 'backend.err')
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    if ($process.HasExited) { throw 'The fresh database backend exited during startup.' }
    try {
      $health = Invoke-RestMethod -Uri "http://127.0.0.1:$appPort/api/v1/health" -TimeoutSec 2
      if ($health.status -eq 'UP') { return $process }
    } catch {}
    Start-Sleep -Milliseconds 500
  }
  throw 'Fresh database backend did not become healthy.'
}
try {
  & (Join-Path $pg 'initdb.exe') -D $data -U serms_fresh -A trust --encoding=UTF8 --locale=C *> (Join-Path $run 'initdb.log')
  if ($LASTEXITCODE -ne 0) { throw 'initdb failed.' }
  $start = Start-Process -FilePath (Join-Path $pg 'pg_ctl.exe') -ArgumentList @(
    '-D', ('"' + $data + '"'), '-l', ('"' + (Join-Path $run 'postgres.log') + '"'),
    '-o', ('"-h 127.0.0.1 -p ' + $dbPort + '"'), '-w', 'start'
  ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'start.log') -RedirectStandardError (Join-Path $run 'start.err')
  if (-not $start.WaitForExit(30000)) { throw 'PostgreSQL startup timed out.' }
  & (Join-Path $pg 'pg_ctl.exe') -D $data status *> (Join-Path $run 'status.log')
  if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup failed.' }
  $started = $true
  & (Join-Path $pg 'createdb.exe') -h 127.0.0.1 -p $dbPort -U serms_fresh serms_local_demo
  if ($LASTEXITCODE -ne 0) { throw 'createdb failed.' }
  $env:SERMS_DB_URL = "jdbc:postgresql://127.0.0.1:$dbPort/serms_local_demo?currentSchema=serms,public"
  $env:SERMS_DB_USER = 'serms_fresh'
  $env:SERMS_DB_PASSWORD = ''
  $env:SPRING_PROFILES_ACTIVE = 'local'
  $env:SERVER_ADDRESS = '127.0.0.1'
  $env:SERVER_PORT = "$appPort"
  $env:SERMS_LOCAL_DEMO_PASSWORD = [guid]::NewGuid().ToString('N')
  $backend = StartBackend
  $migrations = Query "SELECT count(*) || ':' || max(version) FROM public.serms_flyway_history WHERE success"
  if ($migrations -notin @('6:006','6:6')) { throw "Unexpected Flyway history: $migrations" }
  Seed 'seed-first.log'
  $snapshot = "SELECT json_build_object('users',(SELECT json_agg(to_jsonb(u) ORDER BY user_id) FROM serms.app_user u),'roles',(SELECT json_agg(to_jsonb(ur) ORDER BY user_id,role_id) FROM serms.user_role ur),'equipment',(SELECT json_agg(to_jsonb(e) ORDER BY equipment_id) FROM serms.equipment e),'categories',(SELECT json_agg(to_jsonb(c) ORDER BY name) FROM serms.equipment_category c))::text"
  $before = Query $snapshot
  $masterData = Query "SELECT count(*) || ':' || count(DISTINCT serial_number) || ':' || count(*) FILTER (WHERE status='RETIRED' AND condition='POOR') FROM serms.equipment"
  if ($masterData -ne '8:8:1') { throw "Unexpected V005 equipment data: $masterData" }
  Seed 'seed-repeat.log'
  if ((Query $snapshot) -ne $before) { throw 'Repeated seed changed the fresh database.' }
  Stop-Process -Id $backend.Id
  $backend.WaitForExit(10000) | Out-Null
  $backend = StartBackend
  if ((Query $snapshot) -ne $before) { throw 'Restart changed the fresh database.' }
  Write-Output "PASS V001-V006 Flyway, fresh V005 seed, repeat seed and backend restart. Evidence: $run"
} finally {
  if ($backend -and !$backend.HasExited) { Stop-Process -Id $backend.Id -ErrorAction SilentlyContinue }
  if ($started) { & (Join-Path $pg 'pg_ctl.exe') -D $data -m fast -w stop | Out-Null }
  foreach ($key in $old.Keys) { [Environment]::SetEnvironmentVariable($key, $old[$key], 'Process') }
}
