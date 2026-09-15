# Move Android SDK + AVD from C: to repo .tools/, then remove old C: copies.
# Run from repo root: .\scripts\e2e\migrate-off-c-drive.ps1
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\paths.ps1"

$oldSdk = Join-Path $env:LOCALAPPDATA "android-movie-e2e-sdk"
$oldAvd = Join-Path $env:USERPROFILE ".android"
$newSdk = $E2E.SdkRoot
$newAvd = $E2E.AvdHome

Write-Host "=== Migrate Android tooling off C: ==="
Write-Host "Target SDK: $newSdk"
Write-Host "Target AVD: $newAvd"

# Stop emulator / adb
Get-Process -Name "qemu-system*","emulator","adb" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 2

New-Item -ItemType Directory -Force -Path $E2E.ToolsRoot | Out-Null
New-Item -ItemType Directory -Force -Path $newAvd | Out-Null

if ((Test-Path $oldSdk) -and -not (Test-Path "$newSdk\platform-tools\adb.exe")) {
    Write-Host "Moving SDK from $oldSdk ..."
    robocopy $oldSdk $newSdk /E /MOVE /NFL /NDL /NJH /NJS | Out-Null
    if (Test-Path $oldSdk) { Remove-Item $oldSdk -Recurse -Force -ErrorAction SilentlyContinue }
} elseif (-not (Test-Path "$newSdk\platform-tools\adb.exe")) {
    Write-Host 'No C: SDK found - run setup-android-sdk.ps1 after this script.'
}

if (Test-Path "$oldAvd\avd\torrent_movie_e2e.avd") {
    Write-Host "Moving AVD torrent_movie_e2e ..."
    New-Item -ItemType Directory -Force -Path "$newAvd\avd" | Out-Null
    if (Test-Path "$newAvd\avd\torrent_movie_e2e.avd") {
        Remove-Item "$newAvd\avd\torrent_movie_e2e.avd" -Recurse -Force
    }
    robocopy "$oldAvd\avd\torrent_movie_e2e.avd" "$newAvd\avd\torrent_movie_e2e.avd" /E /MOVE /NFL /NDL /NJH /NJS | Out-Null
    if (Test-Path "$oldAvd\avd\torrent_movie_e2e.ini") {
        Copy-Item "$oldAvd\avd\torrent_movie_e2e.ini" "$newAvd\avd\torrent_movie_e2e.ini" -Force
        # Fix paths inside ini/avd config
        $ini = "$newAvd\avd\torrent_movie_e2e.ini"
        if (Test-Path $ini) {
            (Get-Content $ini) -replace [regex]::Escape($oldAvd), $newAvd |
                Set-Content $ini -Encoding ASCII
        }
        $cfg = "$newAvd\avd\torrent_movie_e2e.avd\config.ini"
        if (Test-Path $cfg) {
            (Get-Content $cfg) -replace [regex]::Escape($oldAvd), $newAvd |
                Set-Content $cfg -Encoding ASCII
        }
        $avdDir = "$newAvd\avd\torrent_movie_e2e.avd"
        Get-ChildItem $avdDir -File -Recurse | ForEach-Object {
            $text = Get-Content $_.FullName -Raw -ErrorAction SilentlyContinue
            if ($null -eq $text) { return }
            $updated = $text.Replace($oldSdk, $newSdk).
                Replace("$oldAvd\avd\..\avd", "$newAvd\avd").
                Replace($oldAvd, $newAvd)
            if ($updated -ne $text) {
                Set-Content -Path $_.FullName -Value $updated -NoNewline -Encoding ASCII
            }
        }
    }
}

Write-AndroidLocalProperties -SdkRoot $newSdk
Set-AndroidEnv -SdkRoot $newSdk -AvdHome $newAvd -Jdk $E2E.Jdk

Write-Host ""
Write-Host "=== Optional: uninstall winget platform-tools (duplicate on C:) ==="
Write-Host "  winget uninstall --id Google.PlatformTools"
Write-Host ""
Write-Host "=== Optional: delete leftover C: SDK folder if empty ==="
Write-Host "  Remove-Item '$oldSdk' -Recurse -Force -ErrorAction SilentlyContinue"
Write-Host ""
Write-Host "=== Set permanently (User env vars) ==="
Write-Host "  ANDROID_SDK_ROOT = $newSdk"
Write-Host "  ANDROID_AVD_HOME = $newAvd\avd"
Write-Host "  JAVA_HOME        = $($E2E.Jdk)"
Write-Host ""
Write-Host 'Done. Re-run: .\scripts\e2e\setup-android-sdk.ps1 only if SDK missing on F:'
