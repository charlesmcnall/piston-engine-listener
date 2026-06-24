$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $projectRoot ".toolchain"
$jdk = Get-ChildItem -Path $toolchain -Directory -Filter "jdk-17*" | Select-Object -First 1

if ($null -eq $jdk) {
    throw "Portable JDK not found under $toolchain"
}

$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $toolchain "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

& (Join-Path $toolchain "gradle-9.4.1\bin\gradle.bat") -p $projectRoot assembleDebug

