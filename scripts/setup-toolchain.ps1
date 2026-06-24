$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $projectRoot ".toolchain"
$sdk = Join-Path $toolchain "android-sdk"
$latestCmdline = Join-Path $sdk "cmdline-tools\latest"

New-Item -ItemType Directory -Force -Path $toolchain | Out-Null

function Expand-IfMissing {
    param(
        [string] $Url,
        [string] $ZipPath,
        [string] $Destination,
        [string] $ExpectedDirectoryPattern
    )

    $existing = Get-ChildItem -Path $Destination -Directory -Filter $ExpectedDirectoryPattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $existing) {
        return
    }

    if (!(Test-Path $ZipPath)) {
        Invoke-WebRequest -Uri $Url -OutFile $ZipPath
    }
    Expand-Archive -Path $ZipPath -DestinationPath $Destination -Force
}

Expand-IfMissing `
    -Url "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" `
    -ZipPath (Join-Path $toolchain "temurin-17-jdk.zip") `
    -Destination $toolchain `
    -ExpectedDirectoryPattern "jdk-17*"

Expand-IfMissing `
    -Url "https://services.gradle.org/distributions/gradle-9.4.1-bin.zip" `
    -ZipPath (Join-Path $toolchain "gradle-9.4.1-bin.zip") `
    -Destination $toolchain `
    -ExpectedDirectoryPattern "gradle-9.4.1"

if (!(Test-Path (Join-Path $latestCmdline "bin\sdkmanager.bat"))) {
    $cmdZip = Join-Path $toolchain "commandlinetools-win-14742923_latest.zip"
    $cmdUnpack = Join-Path $toolchain "cmdline-tools-unpacked"
    if (!(Test-Path $cmdZip)) {
        Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip" -OutFile $cmdZip
    }
    Expand-Archive -Path $cmdZip -DestinationPath $cmdUnpack -Force
    New-Item -ItemType Directory -Force -Path $latestCmdline | Out-Null
    Copy-Item -Path (Join-Path $cmdUnpack "cmdline-tools\*") -Destination $latestCmdline -Recurse -Force
}

$jdk = Get-ChildItem -Path $toolchain -Directory -Filter "jdk-17*" | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$localProperties = Join-Path $projectRoot "local.properties"
$escapedSdk = $sdk.Replace("\", "\\").Replace(":", "\:")
"sdk.dir=$escapedSdk" | Set-Content -Path $localProperties -Encoding ASCII

$sdkmanager = Join-Path $latestCmdline "bin\sdkmanager.bat"
1..80 | ForEach-Object { "y" } | & $sdkmanager --sdk_root=$sdk --licenses
& $sdkmanager --sdk_root=$sdk "platform-tools" "platforms;android-36" "build-tools;36.0.0"

Write-Host "Android toolchain ready under $toolchain"

