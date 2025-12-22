$commits = git -C "D:\learngit\huixiangshhuoquan" log --reverse --format="%H"
$commitArray = @($commits -split "`r`n" | Where-Object { $_ -match "^[a-f0-9]{40}$" })
$total = $commitArray.Count

$startDate = Get-Date "2024-10-05"
$endDate = Get-Date "2025-03-08"
$totalDays = ($endDate - $startDate).Days

# Build env filter as case statement
$envFilter = @'
case $GIT_COMMIT in
'@

for ($i = 0; $i -lt $total; $i++) {
    $days = [Math]::Floor(($totalDays * $i) / ($total - 1))
    $commitDate = $startDate.AddDays($days)
    $dateStr = $commitDate.ToString("yyyy-MM-ddTHH:mm:ss")
    $hash = $commitArray[$i]
    $envFilter += "  ${hash}) export GIT_AUTHOR_DATE='${dateStr}'; export GIT_COMMITTER_DATE='${dateStr}' ;;" + "`n"
}

$envFilter += "esac" + "`n"

# Write env filter to a file
$envFilterPath = "D:\learngit\huixiangshhuoquan\.git\env-filter.sh"
$envFilter | Out-File -FilePath $envFilterPath -Encoding ASCII -NoNewline
Write-Host "Env filter written with $total entries"

# Run filter-branch once
Write-Host "Running git filter-branch..."
Set-Location "D:\learngit\huixiangshhuoquan"
cmd /c "git filter-branch -f --env-filter `"source .git/env-filter.sh`" -- --all"
Write-Host "Done"
