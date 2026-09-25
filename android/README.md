# Torrent Movie App (Android)

Kotlin + Jetpack Compose client. Requires **Android SDK** and JDK 17.

## First-time setup (no admin)

All tooling installs under **repo `.tools/`** on your chosen drive (not `C:\Users\...\AppData`).

From repo root:

```powershell
.\scripts\e2e\setup-host-tools.ps1   # portable JDK 17 + Gradle wrapper → .tools/
.\scripts\e2e\setup-android-sdk.ps1  # SDK + emulator → .tools/android-sdk
```

### Already installed on C:?

```powershell
.\scripts\e2e\migrate-off-c-drive.ps1   # move SDK + AVD to .tools/
.\scripts\e2e\uninstall-c-drive.ps1      # delete leftover C: copies
winget uninstall --id Google.PlatformTools  # optional duplicate adb
```

## Build

```powershell
$env:JAVA_HOME = "..\.tools\jdk-17.0.20.1+1"   # or system JDK 17
cd android
.\gradlew.bat assembleDebug test
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Operator keys (no user setup in Settings)

TMDB is pre-set in `gradle.properties` (`missysBundledTmdbApiKey`) and `services/search/operator_defaults.py` — keep those in sync. Optional overrides: `android/local.properties`, `services/search/.env`, or Settings.

Search API auto-install (FR-040): set `missysBundledSearchApiUrl` in `gradle.properties` or `local.properties`. If unset, **debug** APKs bake this PC’s LAN IPv4 as `http://<ip>:8765` so a phone on the same Wi-Fi does not need a manual Settings URL. TMDB is still `missysBundledTmdbApiKey`.

## Device E2E

Automated bootstrap (emulator + API + install + launch):

```powershell
.\scripts\e2e\device-e2e.ps1
```

Manual steps:

1. Start search API on host: `.\scripts\e2e\run-host-e2e.ps1` (smoke) or manual uvicorn on `0.0.0.0:8765`
2. Start Android emulator (API 34+) or connect phone with USB debugging
3. `adb install -r app\build\outputs\apk\debug\app-debug.apk`
4. In app **Settings**:
   - Search API: `http://10.0.2.2:8765` (emulator) or `http://<LAN-IP>:8765` (physical device)
   - ruTorrent URL, credentials, magnet folder `/home5/chris82/downloads/MOVIES/`
5. Search → detail → **Send to seedbox** → verify in ruTorrent

## Dev networking

- Search API on host: `services/search` uvicorn on port 8765
- Emulator API URL in Settings: `http://10.0.2.2:8765`
- Cleartext allowed for `10.0.2.2` via `network_security_config.xml`

## Modules

| Module | Role |
|--------|------|
| `:app` | Compose UI, navigation |
| `:core:network` | Retrofit `SearchApi` |
| `:core:data` | Repositories, Room, ruTorrent client |
