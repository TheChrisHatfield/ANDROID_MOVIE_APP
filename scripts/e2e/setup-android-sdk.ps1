# Bootstrap Android SDK under repo .tools/android-sdk (not C:\Users\...\AppData)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\paths.ps1"

$sdkRoot = $E2E.SdkRoot
$jdk = $E2E.Jdk
$cmdZip = Join-Path $E2E.ToolsRoot "cmdline-tools-win.zip"

if (-not (Test-Path "$jdk\bin\java.exe")) {
    Write-Host "JDK 17 not found — run setup-host-tools.ps1 first"
    exit 1
}
Set-AndroidEnv -SdkRoot $sdkRoot -AvdHome $E2E.AvdHome -Jdk $jdk
New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null
New-Item -ItemType Directory -Force -Path $E2E.AvdHome | Out-Null

if (-not (Test-Path "$sdkRoot\cmdline-tools\latest\bin\sdkmanager.bat")) {
    if (-not (Test-Path $cmdZip) -or (Get-Item $cmdZip).Length -lt 140000000) {
        Write-Host "Downloading Android commandline tools..."
        curl.exe -L "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -o $cmdZip
    }
    New-Item -ItemType Directory -Force -Path "$sdkRoot\cmdline-tools" | Out-Null
    Expand-Archive -Path $cmdZip -DestinationPath "$sdkRoot\cmdline-tools" -Force
    if (Test-Path "$sdkRoot\cmdline-tools\cmdline-tools") {
        Move-Item "$sdkRoot\cmdline-tools\cmdline-tools" "$sdkRoot\cmdline-tools\latest" -Force
    }
}

$sdkmanager = "$sdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"
("y`n" * 50) | & $sdkmanager --sdk_root=$sdkRoot --licenses | Out-Null
& $sdkmanager --sdk_root=$sdkRoot "platform-tools" "platforms;android-34" "build-tools;34.0.0" "emulator" "system-images;android-34;google_apis;x86_64"

$avdManager = "$sdkRoot\cmdline-tools\latest\bin\avdmanager.bat"
if (-not (Test-Path "$E2E.AvdHome\avd\torrent_movie_e2e.avd")) {
    echo no | & $avdManager create avd -n torrent_movie_e2e -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force
}

Write-AndroidLocalProperties -SdkRoot $sdkRoot
Write-Host "SDK ready: $sdkRoot"
Write-Host "AVD home: $($E2E.AvdHome)"
Write-Host "Wrote $($E2E.LocalProps)"
