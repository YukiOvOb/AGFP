$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $repo '.cache/local-demo'
$stateFile = Join-Path $runtime 'state.json'
if (!(Test-Path -LiteralPath $stateFile)) { Write-Output 'No registered local demo to stop.'; exit 0 }
$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
if ([IO.Path]::GetFullPath($state.repo) -ne [IO.Path]::GetFullPath($repo) -or [IO.Path]::GetFullPath($state.data) -ne [IO.Path]::GetFullPath((Join-Path $runtime 'pg18'))) { throw 'Runtime paths do not match this checkout' }
$backendPath = if ($state.backendJar) { '.cache/local-demo/serms-demo.jar' } else { 'app/target/serms-0.1.0-SNAPSHOT.jar' }
foreach ($entry in @(@($state.frontendPid,'frontend/node_modules/vite/bin/vite.js'), @($state.backendPid,$backendPath))) {
  $processId = [int]$entry[0]
  $expectedPath = (Join-Path $repo $entry[1]).Replace('/','\')
  $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
  if ($process) {
    if (!$process.CommandLine -or !$process.CommandLine.Replace('/','\').Contains($expectedPath)) { throw "PID $processId belongs to another process; refusing to stop it" }
    Stop-Process -Id $processId
  }
}
& (Join-Path $state.postgresBin 'pg_ctl.exe') -D $state.data -m fast -w stop
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL stop failed; runtime state retained' }
Remove-Item -LiteralPath $stateFile
Write-Output 'Stopped this local demo. Database, logs and local credentials were preserved.'
