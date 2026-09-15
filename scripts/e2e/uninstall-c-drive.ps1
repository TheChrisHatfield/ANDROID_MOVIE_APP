# Remove C: copies after migrate-off-c-drive.ps1 (does NOT delete F: .tools)
$ErrorActionPreference = "Stop"

Write-Host "Stopping emulator/adb..."
Get-Process -Name "qemu-system*","emulator","adb" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 2

$remove = @(
    (Join-Path $env:LOCALAPPDATA "android-movie-e2e-sdk"),
    (Join-Path $env:USERPROFILE ".android\avd\torrent_movie_e2e.avd"),
    (Join-Path $env:USERPROFILE ".android\avd\torrent_movie_e2e.ini")
)

foreach ($p in $remove) {
    if (Test-Path $p) {
        Write-Host "Removing $p"
        Remove-Item $p -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "Optional winget uninstall (platform-tools duplicate):"
Write-Host "  winget uninstall --id Google.PlatformTools"
Write-Host ""
Write-Host "C: cleanup done. Tooling should live under:"
Write-Host "  F:\DRIVE_DOWNLOADS\ANDROID_MOVIE_APP\.tools\"
