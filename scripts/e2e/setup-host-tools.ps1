# Portable JDK 17 + Gradle wrapper bootstrap (no admin)
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$tools = Join-Path $repoRoot ".tools"
$jdkZip = Join-Path $tools "jdk17.zip"
$jdkDir = Join-Path $tools "jdk-17.0.20.1+1"

New-Item -ItemType Directory -Force -Path $tools | Out-Null

if (-not (Test-Path "$jdkDir\bin\java.exe")) {
    Write-Host "Downloading Temurin JDK 17..."
    Invoke-WebRequest -Uri "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip" -OutFile $jdkZip -UseBasicParsing
    Expand-Archive -Path $jdkZip -DestinationPath $tools -Force
}

$gradleZip = Join-Path $tools "gradle-8.7-bin.zip"
if (-not (Test-Path "$tools\gradle-8.7\bin\gradle.bat")) {
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -OutFile $gradleZip -UseBasicParsing
    Expand-Archive -Path $gradleZip -DestinationPath $tools -Force
}

$env:JAVA_HOME = $jdkDir
$env:PATH = "$jdkDir\bin;$env:PATH"
Push-Location (Join-Path $repoRoot "android")
& "$tools\gradle-8.7\bin\gradle.bat" wrapper --gradle-version 8.7
Pop-Location
Write-Host "JDK: $jdkDir"
Write-Host "Gradle wrapper ready in android/"
