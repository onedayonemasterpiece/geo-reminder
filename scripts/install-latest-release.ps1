[CmdletBinding()]
param(
    [string]$Repository = "onedayonemasterpiece/geo-reminder",
    [string]$DeviceSerial = $env:DEVICE_SERIAL,
    [string]$DestinationRoot = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Package = "com.onedayonemasterpiece.georeminder.debug"
$Activity = "$Package/com.onedayonemasterpiece.georeminder.MainActivity"

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is not available in PATH: $Name"
    }
}

Assert-Command "gh"
Assert-Command "adb"

$releaseJson = & gh api "repos/$Repository/releases?per_page=30"
if ($LASTEXITCODE -ne 0) {
    throw "Could not read GitHub releases for $Repository"
}
$release = $releaseJson |
    ConvertFrom-Json |
    Where-Object { $_.prerelease -eq $true -and $_.draft -eq $false -and ([string]$_.tag_name).StartsWith("debug-") } |
    Sort-Object { [DateTime]$_.published_at } -Descending |
    Select-Object -First 1
if ($null -eq $release) {
    throw "No published debug prerelease found for $Repository"
}

$tag = [string]$release.tag_name
if ([string]::IsNullOrWhiteSpace($DestinationRoot)) {
    $DestinationRoot = Join-Path (Split-Path $PSScriptRoot -Parent) ".local\apk"
}
$destination = Join-Path $DestinationRoot $tag
if (Test-Path $destination) {
    Remove-Item $destination -Recurse -Force
}
New-Item $destination -ItemType Directory -Force | Out-Null

& gh release download $tag `
    --repo $Repository `
    --pattern "geo-reminder-debug.apk" `
    --pattern "geo-reminder-debug.apk.sha256" `
    --pattern "build-info.json" `
    --dir $destination
if ($LASTEXITCODE -ne 0) {
    throw "Could not download release $tag"
}

$apk = Join-Path $destination "geo-reminder-debug.apk"
$checksum = Join-Path $destination "geo-reminder-debug.apk.sha256"
if (-not (Test-Path $apk)) {
    throw "APK not found after download: $apk"
}
if (-not (Test-Path $checksum)) {
    throw "Checksum not found after download: $checksum"
}

$expectedSha = ((Get-Content $checksum -Raw).Trim() -split "\s+")[0].ToLowerInvariant()
$actualSha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expectedSha -ne $actualSha) {
    throw "APK checksum mismatch. Expected $expectedSha, got $actualSha"
}

$deviceOutput = & adb devices
if ($LASTEXITCODE -ne 0) {
    throw "adb devices failed"
}
$authorizedDevices = @()
foreach ($line in $deviceOutput) {
    if ($line -match '^([^\s]+)\s+device(?:\s|$)') {
        $authorizedDevices += $Matches[1]
    }
}

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($authorizedDevices.Count -ne 1) {
        throw "Expected exactly one authorized ADB device, found $($authorizedDevices.Count). Set DEVICE_SERIAL explicitly."
    }
    $DeviceSerial = $authorizedDevices[0]
} elseif ($authorizedDevices -notcontains $DeviceSerial) {
    throw "DEVICE_SERIAL=$DeviceSerial is not an authorized connected device"
}

$installed = & adb -s $DeviceSerial shell pm list packages $Package
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect installed package state"
}
if ($installed -contains "package:$Package") {
    Write-Host "Existing app detected; updating with adb install -r. App data and journal will not be cleared."
}

$installOutput = & adb -s $DeviceSerial install -r $apk 2>&1
$installStatus = $LASTEXITCODE
$installOutput | ForEach-Object { Write-Host $_ }
if ($installStatus -ne 0) {
    throw "Install failed. The script did not uninstall the app or clear its data."
}

& adb -s $DeviceSerial shell am start -n $Activity | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "APK was installed, but the application could not be started"
}

Write-Host ""
Write-Host "Installed verified APK: $apk"
Write-Host "Release: $tag"
Write-Host "SHA-256: $actualSha"
Write-Host "Device: $DeviceSerial"
Write-Host ""
Write-Host "On the phone:"
Write-Host "1. Grant precise location."
Write-Host "2. Open app settings and choose Location -> Allow all the time."
Write-Host "3. Grant notifications."
Write-Host "4. Keep Samsung battery mode Optimized initially; inspect restrictions only after a reproducible miss."
