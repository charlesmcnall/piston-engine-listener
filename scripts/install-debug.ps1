$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $projectRoot ".toolchain"
$adb = Join-Path $toolchain "android-sdk\platform-tools\adb.exe"
$apk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"

& $adb devices
& $adb install -r $apk

