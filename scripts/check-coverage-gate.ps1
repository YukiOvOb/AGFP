param([string]$JacocoCoreJar = '')
# Exercise the actual 70% Maven gate, then restore the successful run's coverage bytes.
# Do not run concurrently with another Maven build in this checkout.
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path $PSScriptRoot -Parent
$taskExec = Join-Path $taskRepo 'app/target/jacoco.exec'
$taskLogs = Join-Path $taskRepo '.cache/coverage-gate'
if (!(Test-Path -LiteralPath $taskExec)) { throw 'Run the complete backend verify first' }
if (!$JacocoCoreJar) {
  $JacocoCoreJar = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository/org/jacoco/org.jacoco.core/0.8.14/org.jacoco.core-0.8.14.jar'
}
if (!(Test-Path -LiteralPath $JacocoCoreJar)) { throw 'Provide -JacocoCoreJar from your Maven local repository' }
New-Item -ItemType Directory -Force -Path $taskLogs | Out-Null
$taskBytes = [IO.File]::ReadAllBytes($taskExec)
$taskHash = (Get-FileHash -LiteralPath $taskExec -Algorithm SHA256).Hash
Push-Location (Join-Path $taskRepo 'app')
try {
  & java --class-path $JacocoCoreJar (Join-Path $PSScriptRoot 'support/EmptyCoverage.java') $taskExec
  if ($LASTEXITCODE -ne 0) { throw 'Could not generate zero-execution data' }
  $taskMaven = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\mvnw.cmd' } else { './mvnw' }
  $taskPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = 'Continue'
    & $taskMaven -B jacoco:check@business-coverage *> (Join-Path $taskLogs 'negative.log')
    $taskExit = $LASTEXITCODE
  } finally { $ErrorActionPreference = $taskPreference }
  $taskLog = Get-Content -LiteralPath (Join-Path $taskLogs 'negative.log') -Raw
  if ($taskExit -eq 0 -or $taskLog -notmatch 'lines covered ratio is 0\.00, but expected minimum is 0\.70') {
    throw 'Negative check did not fail specifically for the unchanged 70% line-coverage requirement; inspect negative.log'
  }
  Write-Output 'PASS: zero coverage is rejected at the unchanged 70% threshold.'
} finally {
  [IO.File]::WriteAllBytes($taskExec, $taskBytes)
  Pop-Location
}
if ((Get-FileHash -LiteralPath $taskExec -Algorithm SHA256).Hash -ne $taskHash) { throw 'Coverage bytes were not restored' }
Push-Location (Join-Path $taskRepo 'app')
try {
  & $taskMaven -B jacoco:check@business-coverage
  if ($LASTEXITCODE -ne 0) { throw 'Restored real coverage must pass the same gate' }
} finally { Pop-Location }
Write-Output 'PASS: original coverage restored byte-for-byte and the actual gate passes.'
