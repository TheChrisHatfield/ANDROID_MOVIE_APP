# Shared E2E paths — everything under repo .tools/ (not C:\Users\...\AppData)
$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$ToolsRoot = Join-Path $RepoRoot ".tools"

$E2E = @{
    RepoRoot   = $RepoRoot
    ToolsRoot  = $ToolsRoot
    Jdk        = Join-Path $ToolsRoot "jdk-17.0.20.1+1"
    SdkRoot    = Join-Path $ToolsRoot "android-sdk"
    AvdHome    = Join-Path $ToolsRoot "android-avd"
    Gradle     = Join-Path $ToolsRoot "gradle-8.7\bin\gradle.bat"
    LocalProps = Join-Path $RepoRoot "android\local.properties"
}

function Write-AndroidLocalProperties {
    param([string]$SdkRoot)
    $escaped = ($SdkRoot -replace "\\", "/") -replace ":", "\\:"
    "sdk.dir=$escaped" | Set-Content -Path $E2E.LocalProps -Encoding ASCII
}

function Set-AndroidEnv {
    param([string]$SdkRoot, [string]$AvdHome, [string]$Jdk)
    $avdDir = Join-Path $AvdHome "avd"
    $env:JAVA_HOME = $Jdk
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot
    # Emulator expects .ini files directly under ANDROID_AVD_HOME (like %USERPROFILE%\.android\avd).
    $env:ANDROID_AVD_HOME = $avdDir
    $env:PATH = "$Jdk\bin;$SdkRoot\platform-tools;$SdkRoot\emulator;$env:PATH"
}
