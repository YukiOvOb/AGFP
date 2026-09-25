# Export only notification implementation sources for compatibility tests, never into production sources.
$ErrorActionPreference='Stop'
$repoRoot=Split-Path $PSScriptRoot -Parent
$contractCommit='01b50778701ed8a729039790238581bb59e929b5'
Push-Location $repoRoot
try {
    git cat-file -e "$contractCommit^{commit}"
    if($LASTEXITCODE -ne 0){throw 'Fetch origin feature/wang-yuanmeng-sprint1-notifications before testing'}
    $base='app/src/main/java/'
    $contract=@(
        'sg/edu/nus/serms/notification/domain/NotificationRequest.java',
        'sg/edu/nus/serms/notification/domain/NotificationType.java',
        'sg/edu/nus/serms/notification/domain/ReminderPolicy.java',
        'sg/edu/nus/serms/notification/service/LoanReminderGuard.java',
        'sg/edu/nus/serms/notification/repository/NotificationRequestStore.java'
    )
    foreach($relative in $contract){
        $upstream=((git show "${contractCommit}:${base}$relative") -join "`n")+"`n"
        if($LASTEXITCODE -ne 0){throw "Missing contract: $relative"}
        $fixture=[IO.File]::ReadAllText((Join-Path $repoRoot "integration/notifications/src/contract/java/$relative")).Replace("`r`n","`n")
        if($fixture -cne $upstream){throw "Contract fixture differs from upstream: $relative"}
    }
    $paths=git ls-tree -r --name-only $contractCommit -- app/src/main/java/sg/edu/nus/serms/notification
    if($LASTEXITCODE -ne 0){throw 'Cannot list upstream notification sources'}
    foreach($path in $paths){
        $relative=$path.Substring($base.Length)
        if($contract -contains $relative -or $relative -match '/controller/'){continue}
        $destination=Join-Path $repoRoot ".cache/notification-upstream/src/$relative"
        New-Item -ItemType Directory -Force (Split-Path $destination -Parent) | Out-Null
        $content=((git show "${contractCommit}:$path") -join "`n")+"`n"
        if($LASTEXITCODE -ne 0){throw "Cannot read $path"}
        [IO.File]::WriteAllText($destination,$content,(New-Object Text.UTF8Encoding($false)))
    }
    Write-Output "Verified notification contracts against $contractCommit"
} finally { Pop-Location }