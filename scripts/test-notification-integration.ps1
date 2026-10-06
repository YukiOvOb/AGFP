# Tests the unchanged upstream notification services against the PostgreSQL adapter.
$ErrorActionPreference='Stop'
$repoRoot=Split-Path $PSScriptRoot -Parent
$containerName='serms-notification-test-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$testPassword=[guid]::NewGuid().ToString('N')
$keys=@('SERMS_TEST_JDBC_URL','SERMS_UPGRADE_JDBC_URL','SERMS_TEST_DB_USER','SERMS_TEST_DB_PASSWORD')
$previous=@{}
foreach($key in $keys){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
$created=$false
Push-Location $repoRoot
try {
    & "$PSScriptRoot/prepare-notification-contract.ps1"
    docker run -d --name $containerName -e POSTGRES_DB=serms_test -e POSTGRES_USER=serms_test -e "POSTGRES_PASSWORD=$testPassword" -p 127.0.0.1::5432 postgres:17-alpine
    if($LASTEXITCODE -ne 0){throw 'Cannot start isolated test database'}
    $created=$true
    $ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++){
        docker exec $containerName pg_isready -h 127.0.0.1 -U serms_test -d serms_test *> $null
        if($LASTEXITCODE -eq 0){$ready=$true;break}
        Start-Sleep -Seconds 1
    }
    if(-not $ready){throw 'Test database did not start'}
    docker exec $containerName createdb -U serms_test serms_upgrade
    if($LASTEXITCODE -ne 0){throw 'Cannot create upgrade test database'}
    $steps=@(
        'database/src/main/resources/db/migration/V001__sprint1.sql',
        'database/src/main/resources/db/migration/V002__align_serms_model.sql',
        'integration/notifications/src/test/resources/v2-notification-fixture.sql'
    )
    foreach($path in $steps){
        docker cp $path "${containerName}:/tmp/step.sql"
        if($LASTEXITCODE -ne 0){throw "Cannot copy $path"}
        docker exec $containerName psql -U serms_test -d serms_upgrade -v ON_ERROR_STOP=1 -f /tmp/step.sql
        if($LASTEXITCODE -ne 0){throw "Upgrade fixture failed: $path"}
    }
    $binding=docker port $containerName 5432/tcp
    if($LASTEXITCODE -ne 0){throw 'Cannot discover test port'}
    $testPort=($binding.Trim() -split ':')[-1]
    $env:SERMS_TEST_JDBC_URL="jdbc:postgresql://127.0.0.1:$testPort/serms_test?currentSchema=serms,public"
    $env:SERMS_UPGRADE_JDBC_URL="jdbc:postgresql://127.0.0.1:$testPort/serms_upgrade?currentSchema=serms,public"
    $env:SERMS_TEST_DB_USER='serms_test'
    $env:SERMS_TEST_DB_PASSWORD=$testPassword
    mvn -B -f integration/notifications/pom.xml clean verify
    if($LASTEXITCODE -ne 0){throw 'Notification contract tests failed'}
    $entries=jar tf integration/notifications/target/serms-notification-postgres-0.1.0-SNAPSHOT.jar
    if($LASTEXITCODE -ne 0){throw 'Cannot inspect adapter artifact'}
    if($entries -match '^sg/edu/nus/serms/notification/'){throw 'Compile-only upstream fixtures leaked into adapter JAR'}
    Write-Output 'Adapter JAR contains no duplicate upstream notification classes'
} finally {
    if($created){docker rm -f -v $containerName | Out-Null}
    foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
    Pop-Location
}