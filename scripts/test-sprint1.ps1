# Requires Docker Desktop, JDK 17+ and Maven. Uses a new disposable database each run.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$containerName = 'serms-test-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$testPassword = [guid]::NewGuid().ToString('N')
$variables = @('SERMS_TEST_JDBC_URL', 'SERMS_TEST_DB_USER', 'SERMS_TEST_DB_PASSWORD')
$previous = @{}
foreach ($key in $variables) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$created = $false
Push-Location $repoRoot
try {
    docker run -d --name $containerName -e POSTGRES_DB=serms_test -e POSTGRES_USER=serms_test -e "POSTGRES_PASSWORD=$testPassword" -p 127.0.0.1::5432 postgres:17-alpine
    if ($LASTEXITCODE -ne 0) { throw 'Unable to start test database' }
    $created = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $containerName pg_isready -U serms_test -d serms_test *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Database did not become ready in 60 seconds' }
    docker cp database/src/main/resources/db/migration/V001__sprint1.sql "${containerName}:/tmp/001.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy migration' }
    docker exec $containerName psql -U serms_test -d serms_test -v ON_ERROR_STOP=1 -f /tmp/001.sql
    if ($LASTEXITCODE -ne 0) { throw 'Migration failed' }
    $binding = docker port $containerName 5432/tcp
    if ($LASTEXITCODE -ne 0) { throw 'Cannot discover database port' }
    $testPort = ($binding.Trim() -split ':')[-1]
    $env:SERMS_TEST_JDBC_URL = "jdbc:postgresql://127.0.0.1:$testPort/serms_test"
    $env:SERMS_TEST_DB_USER = 'serms_test'
    $env:SERMS_TEST_DB_PASSWORD = $testPassword
    mvn -B -f database/pom.xml clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Database build or tests failed' }
} finally {
    if ($created) { docker rm -f -v $containerName | Out-Null }
    foreach ($key in $variables) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    Pop-Location
}