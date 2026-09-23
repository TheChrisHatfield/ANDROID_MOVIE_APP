# Device E2E bootstrap: emulator + API + APK install + launch
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\paths.ps1"

$repoRoot = $E2E.RepoRoot
$jdk = $E2E.Jdk
$sdk = $E2E.SdkRoot
$avdHome = $E2E.AvdHome
$apk = Join-Path $repoRoot "android\app\build\outputs\apk\debug\app-debug.apk"
$searchDir = Join-Path $repoRoot "services\search"
$adb = Join-Path $sdk "platform-tools\adb.exe"

Set-AndroidEnv -SdkRoot $sdk -AvdHome $avdHome -Jdk $jdk

function Stop-StaleTorrentMovieEmulator {
    Get-Process -Name "qemu-system-x86_64", "emulator" -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 2
    $lock = Join-Path $avdHome "avd\torrent_movie_e2e.avd\hardware-qemu.ini.lock"
    if (Test-Path $lock) {
        Remove-Item -Recurse -Force $lock -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path $apk)) {
    Write-Error "APK missing - run: cd android; .\gradlew.bat assembleDebug"
}

Push-Location $searchDir
py -3.11 -m pip install -e ".[dev]" -q
Pop-Location

$apiProc = $null
try {
    Invoke-WebRequest http://127.0.0.1:8765/v1/health -UseBasicParsing -TimeoutSec 2 | Out-Null
    Write-Host "Search API already running on :8765"
} catch {
    Write-Host "Starting search API..."
    $apiProc = Start-Process py -ArgumentList "-3.11","-m","uvicorn","api.main:app","--host","0.0.0.0","--port","8765" `
        -WorkingDirectory $searchDir -PassThru -WindowStyle Hidden
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        try {
            $r = Invoke-WebRequest http://127.0.0.1:8765/v1/health -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { $ready = $true; break }
        } catch {}
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        if ($apiProc) { Stop-Process -Id $apiProc.Id -Force -ErrorAction SilentlyContinue }
        Write-Error "Search API did not start on :8765"
    }
}

$serial = (& $adb devices | Select-String "emulator-\d+\s+device" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)
if (-not $serial) {
    Write-Host "No emulator - starting torrent_movie_e2e AVD..."
    $emu = Join-Path $sdk "emulator\emulator.exe"
    if (-not (Test-Path $emu)) { Write-Error "Emulator missing - run setup-android-sdk.ps1" }
    Stop-StaleTorrentMovieEmulator
    # Host GPU: swiftshader often hangs here when opengl32sw is missing (emulator 37.x).
    Start-Process $emu -ArgumentList @(
        "-avd", "torrent_movie_e2e",
        "-no-snapshot-load", "-no-snapshot-save",
        "-gpu", "host",
        "-no-audio"
    ) -WindowStyle Minimized
    & $adb wait-for-device
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 5
        $serial = (& $adb devices | Select-String "emulator-\d+\s+device" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)
        if ($serial) {
            $boot = & $adb -s $serial shell getprop sys.boot_completed 2>&1
            if ($boot -match "1") { break }
        }
    }
}
if (-not $serial) { Write-Error "Emulator did not boot in time" }
Write-Host "Device: $serial"

& $adb -s $serial reverse tcp:8765 tcp:8765
& $adb -s $serial install -r $apk
& $adb -s $serial shell am start -n com.torrentmovie.app/.MainActivity

Write-Host ""
Write-Host "=== Manual checklist ==="
Write-Host "1. Accept disclaimer dialog"
Write-Host "2. Settings -> Search API: http://10.0.2.2:8765 (default)"
Write-Host "3. Settings -> Seedbox URL, username, password, magnet folder"
Write-Host "4. Search -> pick result -> Send to seedbox"
Write-Host ""
$smoke = Join-Path $PSScriptRoot "smoke_search_api.py"
py -3.11 $smoke
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
