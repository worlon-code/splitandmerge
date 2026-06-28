$ErrorActionPreference = "Stop"

Write-Host "Starting deployment for Video Splitter..." -ForegroundColor Cyan

# 1. Read version from build.gradle.kts
$buildGradle = Get-Content -Path "app\build.gradle.kts" -Raw
$versionNameMatch = [regex]::Match($buildGradle, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatch = [regex]::Match($buildGradle, 'versionCode\s*=\s*(\d+)')

if (-not $versionNameMatch.Success -or -not $versionCodeMatch.Success) {
    Write-Error "Could not parse versionName or versionCode from app\build.gradle.kts"
    exit 1
}

$versionName = $versionNameMatch.Groups[1].Value
$versionCode = [int]$versionCodeMatch.Groups[1].Value
$tagName = "v$versionName"

Write-Host "Detected Version: $versionName ($versionCode)"

# 2. Extract changelog from AI/CHANGELOG.md for the current version
$changelogContent = Get-Content -Path "AI\CHANGELOG.md" -Raw
$changelogPattern = "(?sm)## \[$versionName\][^\n]*\n(.*?)(\n## \[[0-9]|\Z)"
$changelogMatch = [regex]::Match($changelogContent, $changelogPattern)

if (-not $changelogMatch.Success) {
    Write-Error "Could not find changelog entry for version $versionName in AI\CHANGELOG.md"
    exit 1
}

$releaseNotes = $changelogMatch.Groups[1].Value.Trim()
$releaseNotesFile = "temp_release_notes.txt"
Set-Content -Path $releaseNotesFile -Value $releaseNotes -Encoding UTF8

# Parse changelog into array for JSON
$changelogArray = $releaseNotes -split "`r?`n" | Where-Object { $_.Trim() -ne "" } | ForEach-Object { $_.Trim() }

# 3. Check APK and compute SHA-256
$apkPath = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "Release APK not found at $apkPath. Run '.\gradlew assembleRelease' first."
    exit 1
}

$hash = (Get-FileHash -Path $apkPath -Algorithm SHA256).Hash
$apkSize = (Get-Item $apkPath).Length

# 4. Generate videosplitter-version.json
$jsonObj = @{
    versionName = $versionName
    versionCode = $versionCode
    sha256 = $hash
    apkUrl = "https://github.com/worlon-code/splitandmerge/releases/download/$tagName/app-release.apk"
    changelog = $changelogArray
    sizeBytes = $apkSize
}

$jsonPath = "videosplitter-version.json"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($jsonPath, ($jsonObj | ConvertTo-Json -Depth 5), $utf8NoBom)

Write-Host "Generated $jsonPath with SHA-256: $hash"

# 5. Push to git
Write-Host "Pushing commits and tags to origin..."
git add $jsonPath
git commit -m "chore: Update manifest $jsonPath for release $tagName" --allow-empty
git push origin main --tags

# 6. Create GitHub Release using gh CLI
Write-Host "Creating GitHub Release $tagName..."
if (Get-Command gh -ErrorAction SilentlyContinue) {
    gh release create $tagName $apkPath $jsonPath --title "Release $tagName" --notes-file $releaseNotesFile
    Write-Host "GitHub Release created successfully."
} else {
    Write-Host "GitHub CLI (gh) not found. Skipping GitHub Release creation. Normal git tag pushed." -ForegroundColor Yellow
}

# 7. Cleanup
Remove-Item -Path $releaseNotesFile -ErrorAction SilentlyContinue

Write-Host '==========================================================' -ForegroundColor Green
Write-Host "DEPLOY COMPLETE - Video Splitter $tagName" -ForegroundColor Green
Write-Host "APK:         $apkPath"
Write-Host "JSON:        $jsonPath"
Write-Host "SHA-256:     $hash"
Write-Host "Size:        $([math]::Round($apkSize / 1MB, 2)) MB"
Write-Host "GitHub:      Successfully pushed tags and created release."
Write-Host "URL:         https://github.com/worlon-code/splitandmerge/releases/tag/$tagName"
Write-Host '==========================================================' -ForegroundColor Green
