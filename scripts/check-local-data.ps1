$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $repo '.cache/local-demo'
$state = Get-Content -LiteralPath (Join-Path $runtime 'state.json') -Raw | ConvertFrom-Json
$access = Get-Content -LiteralPath (Join-Path $runtime 'access.json') -Raw | ConvertFrom-Json
if ([IO.Path]::GetFullPath($state.repo) -ne [IO.Path]::GetFullPath($repo)) { throw 'Demo state does not belong to this checkout' }
$psql = Join-Path $state.postgresBin 'psql.exe'
$previousPassword = $env:PGPASSWORD
$previousDemo = $env:SERMS_LOCAL_DEMO_PASSWORD
$env:PGPASSWORD = $access.databasePassword
$env:SERMS_LOCAL_DEMO_PASSWORD = $access.demoPassword
$connection = @('-h','127.0.0.1','-p',"$($state.databasePort)",'-U','serms_local','-w','-v','ON_ERROR_STOP=1')
$marker = [guid]::NewGuid().ToString()
$inserted = $false
function Query([string]$sql) {
  $result = & $psql @connection -d serms_local_demo -tA -c $sql
  if ($LASTEXITCODE -ne 0) { throw 'Demo data query failed' }
  return ($result -join '').Trim()
}
function Seed([string]$database, [string]$name, [int]$expected) {
  $log = Join-Path $runtime "$name.log"
  $preference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    & $psql @connection -d $database -f (Join-Path $PSScriptRoot 'demo-data.sql') *> $log
    $exitCode = $LASTEXITCODE
  } finally { $ErrorActionPreference = $preference }
  if ($exitCode -ne $expected) { throw "Unexpected seed exit code ($exitCode) for $name; inspect $log" }
  return Get-Content -LiteralPath $log -Raw
}
$snapshot = "SELECT json_build_object('users',(SELECT count(*) FROM serms.app_user),'equipment',(SELECT count(*) FROM serms.equipment),'reservations',(SELECT count(*) FROM serms.reservation),'loans',(SELECT count(*) FROM serms.loan),'cases',(SELECT count(*) FROM serms.maintenance_case),'passwordFingerprint',(SELECT md5(string_agg(password_hash,',' ORDER BY user_id)) FROM serms.app_user))::text"
try {
  $before = Query $snapshot
  Seed 'serms_local_demo' 'repeat-seed' 0 | Out-Null
  if ((Query $snapshot) -ne $before) { throw 'Repeated seeding changed data or passwords' }
  Write-Output 'PASS idempotent provisioning: data and account passwords preserved'
  $wrong = Seed 'postgres' 'wrong-database' 3
  if (($wrong -replace '\s','') -notmatch 'requirestheisolatedserms_local_demo') { throw 'Expected database-name guard was not observed' }
  Write-Output 'PASS other database rejected before provisioning'
  Query "INSERT INTO serms.app_user(user_id,email,display_name,password_hash,account_status,notification_principal) VALUES ('$marker','$marker@example.invalid','Disabled isolation-test marker','not-a-login-hash','DISABLED','$marker')" | Out-Null
  $inserted = $true
  $foreign = Seed 'serms_local_demo' 'unrelated-data' 3
  if (($foreign -replace '\s','') -notmatch 'containingunrelatedusersorequipment') { throw 'Expected unrelated-data guard was not observed' }
  Query "DELETE FROM serms.app_user WHERE user_id='$marker' AND account_status='DISABLED' AND display_name='Disabled isolation-test marker'" | Out-Null
  $inserted = $false
  if ((Query $snapshot) -ne $before) { throw 'Guard verification changed existing demo records' }
  Write-Output 'PASS unrelated-data provisioning rejected with existing data preserved'
  [ordered]@{status='passed'; checks=3; checkedAt=[DateTime]::UtcNow.ToString('o'); scenarios=@('idempotent seed/password preservation','wrong database rejected','unrelated data rejected/transaction preservation')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'data-check-summary.json') -Encoding utf8
} finally {
  if ($inserted) { Query "DELETE FROM serms.app_user WHERE user_id='$marker' AND account_status='DISABLED' AND display_name='Disabled isolation-test marker'" | Out-Null }
  $env:PGPASSWORD = $previousPassword
  $env:SERMS_LOCAL_DEMO_PASSWORD = $previousDemo
}
