# Seedbox contract: ruTorrent (Seedhost)

**Hook:** `spec.flow`  
**Consumer:** Android `RuTorrentClient`  
**Reference deployment:** Seedhost Snow slot with ruTorrent Web UI

## Base URL (user-configured)

Users enter their ruTorrent Web UI root in Settings. Example shape (not hardcoded in app):

```
https://<username>.<slot>.seedhost.eu/rutorrent/
```

Example host from project owner: `https://chris82.snow.seedhost.eu/rutorrent/`

The app MUST normalize trailing slashes and derive the add-torrent endpoint as:

```
{base_url}php/addtorrent.php
```

## Add magnet

| Field | Value |
|-------|--------|
| Method | `POST` |
| URL | `{base_url}php/addtorrent.php` |
| Content-Type | `application/x-www-form-urlencoded` |
| Body | `url={urlencoded_magnet_uri}&dir_edit={urlencoded_download_path}` |
| Auth | HTTP Basic (default) or Digest — configurable in Settings |

### Download directory (required)

Every movie magnet MUST be sent with `dir_edit` so rTorrent saves to the user's movies folder on the seedbox. TV-show magnets MUST use the TV shows folder.

| Setting | Default |
|---------|---------|
| **download_directory** (movies) | `/home5/chris82/downloads/MOVIES/` |
| **tv_download_directory** | `/home5/chris82/downloads/TVSHOWS/` |

### Settings UI: Magnet download folders

| UI label | `Magnet download folder (movies)` / `Magnet download folder (TV shows)` |
|----------|--------------------------|
| Field type | Single-line text input (absolute path) |
| Defaults | `/home5/chris82/downloads/MOVIES/` and `/home5/chris82/downloads/TVSHOWS/` |
| Validation | Non-empty; must start with `/`; trailing slash optional (app normalizes) |
| Persistence | EncryptedSharedPreferences alongside other seedbox settings |
| Behavior | User can change anytime; next "Send to seedbox" uses the saved path |

- App MUST include `dir_edit` on every `addtorrent.php` POST (not optional for MVP).
- If ruTorrent returns `FailedDirectory`, surface a clear settings error pointing user to fix **Magnet download folder**.

Additional optional form fields:

| Field | Purpose |
|-------|---------|
| `label` | ruTorrent label / custom1 (e.g. `movies`) |
| `torrents_start_stopped` | If present, torrent starts stopped |

Upstream handler: [ruTorrent `php/addtorrent.php`](https://github.com/Novik/ruTorrent/blob/master/php/addtorrent.php) calls `rTorrent::sendMagnet()` when `url` starts with `magnet:`.

## Success criteria

Treat add as **successful** only when:

1. HTTP response is **2xx**, AND
2. Response body or redirect query indicates `status=Success` for the submitted URL (ruTorrent redirects to `addtorrent.php?` with status in query for browser uploads; Android client MUST parse JSON/HTML response per ruTorrent version).

## Torrent status (Uploaded screen)

Poll ruTorrent via the **HTTPRPC** plugin:

| Field | Value |
|-------|--------|
| Method | `POST` |
| URL | `{base_url}plugins/httprpc/action.php` |
| Body | `mode=list` |
| Auth | Same HTTP Basic/Digest as add magnet |

Response JSON shape: `{ "t": { "<INFO_HASH>": [ ...fields... ] }, "cid": "..." } }`. Match `<INFO_HASH>` (40-char hex, case-insensitive) to local **Uploaded** entries. Display progress from `d.get_bytes_done`, `d.get_size_bytes`, `d.get_left_bytes`, `d.get_down_rate`, and state flags.

If HTTPRPC returns 404 or the hash is absent from `t`, show **Not on seedbox** for that row (torrent removed remotely or never added).

Treat as **failed** when:

- HTTP 401/403 → bad credentials or auth scheme mismatch
- HTTP 5xx / timeout → seedbox unreachable
- Body contains `Failed`, `FailedURL`, or `FailedDirectory`

## Seedhost notes

Per [Seedhost KB — browser extension setup](https://www.seedhost.eu/client-area/knowledgebase/306/Adding-torrent-files-via-web-browser-extension.html):

- Server type: **ruTorrent WebUI**
- Port: **443**, SSL enabled
- Relative path: `/rutorrent`
- Credentials: ruTorrent WebUI username and password (from client area)

Remote Torrent Adder and similar extensions use the same `addtorrent.php` endpoint pattern.

## Android implementation sketch

```kotlin
// POST {baseUrl}php/addtorrent.php
// Header: Authorization: Basic ...
// Body: url=magnet%3A%3Fxt%3D...&dir_edit=%2Fhome5%2Fchris82%2Fdownloads%2FMOVIES%2F
```

`SeedboxClient` interface allows future qBittorrent/Transmission adapters; **MVP implements `RuTorrentClient` only**.

## Security

- Credentials stored in EncryptedSharedPreferences only
- Never log username, password, or full magnet URIs in release builds
- Base URL is user-supplied; no seedbox host committed to source control
