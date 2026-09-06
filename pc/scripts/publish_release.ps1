param(
    [string]$tag = "v1.0.4",
    [string]$releaseName = "Master Companion v1.0.4 - Broadcast Device Selector & Spotify Remote"
)

$ErrorActionPreference = "Stop"

# Get GitHub credentials from Git Credential Manager
$credInput = @"
protocol=https
host=github.com
"@
$credOutput = $credInput | git credential fill
$token = ""
foreach ($line in $credOutput -split "`n") {
    if ($line -match "^password=(.+)$") {
        $token = $matches[1].Trim()
    }
}

if (-not $token) {
    Write-Error "Could not retrieve GitHub token from git credential manager."
    exit 1
}

$repo = "Reapzmedia/master-companionion"
$notesPath = Join-Path $PSScriptRoot "..\..\RELEASE_NOTES.md"
$body = if (Test-Path $notesPath) { Get-Content -Raw -Encoding UTF8 $notesPath } else { "Release $tag" }

Write-Host "Creating GitHub Release $tag for $repo..."
$headers = @{
    "Authorization" = "Bearer $token"
    "Accept" = "application/vnd.github+json"
    "User-Agent" = "MasterCompanion-ReleaseScript"
}

$releasePayload = @{
    tag_name = $tag
    name = $releaseName
    body = $body
    draft = $false
    prerelease = $false
} | ConvertTo-Json

$createUrl = "https://api.github.com/repos/$repo/releases"
$releaseResponse = Invoke-RestMethod -Uri $createUrl -Method Post -Headers $headers -Body $releasePayload -ContentType "application/json"

$releaseId = $releaseResponse.id
Write-Host "Release created successfully with ID: $releaseId"

$apkPath = Join-Path $PSScriptRoot "..\..\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "Release APK not found at: $apkPath"
    exit 1
}

$apkItem = Get-Item $apkPath
Write-Host "Uploading asset app-release.apk..."

$uploadUrl = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=app-release.apk"
$uploadHeaders = @{
    "Authorization" = "Bearer $token"
    "Accept" = "application/vnd.github+json"
    "User-Agent" = "MasterCompanion-ReleaseScript"
    "Content-Type" = "application/vnd.android.package-archive"
}

$apkBytes = [System.IO.File]::ReadAllBytes($apkPath)
$assetResponse = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -Body $apkBytes

Write-Host "Asset uploaded successfully!"
Write-Host "Download URL: $($assetResponse.browser_download_url)"
Write-Host "Release URL: $($releaseResponse.html_url)"
