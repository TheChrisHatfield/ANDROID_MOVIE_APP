# Plan: torrent-movie-app

**Hook:** `spec.flow`  
**Spec:** [spec.md](./spec.md)  
**Status:** Draft

<!-- ROSETTA-NAV -->

## Agent navigation (Rosetta Stone)

Load [`docs/planning-rosetta-stone.md`](../../planning-rosetta-stone.md) (`planning.rosetta`) before editing this plan.

- **Spec:** [`spec.md`](./spec.md)
- **Delta micro:** [`torrent-movie-app.md`](../../delta/20_micro/torrent-movie-app.md)
- **Hooks:** `spec.flow`, `context.delta`

## Approach

**Strategy: on-device search in the APK.** Phase 1 extracted torrtux into a Python package (reference / optional operator service). Phase 2 Android client. Phase 3+ **search, browse, genre, magnet, TMDB run on the phone** (`android/core/data/.../ondevice`), not a PC Search API.

### Phase 1 — Search backend (torrtux refactor)

1. Create `services/search/` Python project with `pyproject.toml` (deps: `requests`, `beautifulsoup4`, `lxml`, `fastapi`, `uvicorn`, `pydantic`).
2. Copy `torrtux.py` → split into:
   - `torrtux_core/sites/*.py` — one module per indexer (or grouped batches)
   - `torrtux_core/base.py` — `TorrentSite` base class
   - `torrtux_core/searcher.py` — `TorrentSearcher`
   - `torrtux_core/filters.py` — `parse_size`, seed/size filters
   - `torrtux_core/profiles.py` — `MOVIE_SITES` list
3. Remove CLI-only imports (`tabulate`, `termcolor`, `argparse`, spinner).
4. Add Pydantic models matching OpenAPI contract.
5. FastAPI routes:
   - `GET /v1/health`
   - `GET /v1/sites/health` — runs `test_sites()`, returns working list
   - `GET /v1/search` — wraps `search_all_sites` with query params
   - `GET /v1/results/{id}/magnet` — lazy magnet resolution
6. In-memory result cache keyed by UUID for magnet lazy-fetch (v1; Redis later if needed).
7. Tests: unit tests for `parse_size`, filters; integration smoke with mocked HTTP; optional live smoke marked `@pytest.mark.live`.

### Phase 2 — Android MVP (Yatse-inspired shell)

**UX reference:** [`contracts/ui-yatse-reference.md`](./contracts/ui-yatse-reference.md)

1. Create `android/` Gradle project (Kotlin, Compose Material3, minSdk 26).
2. Modules: `app`, `core/network`, `core/data`.
3. **App shell:** `MainScaffold` with navigation drawer (Search, Uploaded, Settings, Help) + top app bar with seedbox status chip.
4. Retrofit client for search API per `contracts/openapi.yaml`.
5. Screens:
   - `SearchScreen` — Yatse library analog: search field, filter sheet, **card-based** results list
   - `TorrentDetailScreen` — Yatse media-detail analog: hero metadata + primary **Send to seedbox** CTA
   - `UploadedScreen` — Yatse files/history analog: swipe-to-delete sent magnets
   - `SettingsScreen` — grouped sections: General, Seedbox, Search, Interface
   - `HelpScreen` — disclaimer + links (Yatse Help analog)
5. `RuTorrentClient` — `POST {base_url}php/addtorrent.php` with `url={magnet}` + `dir_edit=/home5/chris82/downloads/MOVIES/` + HTTP Basic/Digest auth.
6. Use `EncryptedSharedPreferences` for credentials.
7. **Uploaded magnets** — Room DB table `uploaded_magnets` keyed by `info_hash`; `UploadedRepository` for add/list/delete/contains checks.

### Phase 3 — Duplicate prevention

- Extract `info_hash` from magnet (`xt=urn:btih:...`) on send and on search result display.
- On successful ruTorrent add → insert `UploadedMagnet`.
- Before send → `UploadedRepository.contains(infoHash)` → block with dialog if true.
- `UploadedScreen` in bottom nav or drawer; delete removes row and allows future re-send.

### Phase 4 — Polish

- Filter chips (min seeds, max size)
- Pull-to-refresh on results
- Error banners per FR-010
- TMDB + web poster (FR-036a) and YouTube trailer (FR-036b) fallbacks on grouped results
- Brand as **Missy's Movies** with Missy portrait launcher icon and Pantone Red 032 C (`#EF3340`) app background (FR-039)
- Optional: qBittorrent / Transmission adapters behind `SeedboxClient` interface

## Technical decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Runtime for scrapers | Kotlin on-device (Jsoup + OkHttp) | Deployable APK; no PC host |
| Android UI | Jetpack Compose Material3 | Yatse-like drawer + cards; fast to build |
| UX model | Yatse IA mapping | Proven media-app navigation; see ui-yatse-reference.md |
| Seedbox MVP | ruTorrent `addtorrent.php` | User's Seedhost slot; same endpoint browser extensions use |
| Parallel search | `ThreadPoolExecutor` (from torrtux) | Already proven in upstream |
| API versioning | `/v1/` prefix | Room for contract evolution |

## Repo layout

```
ANDROID_MOVIE_APP/
├── android/                          # Kotlin app (new)
├── services/search/
│   ├── torrtux_core/                 # refactored from torrtux-c
│   ├── api/main.py
│   ├── tests/
│   └── pyproject.toml
├── docs/specs/torrent-movie-app/
│   ├── spec.md
│   ├── plan.md
│   ├── tasks.md
│   └── contracts/openapi.yaml
│   └── contracts/seedbox-rutorrent.md
└── vendor/                           # optional git submodule → torrtux-c
```

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Indexer HTML changes break parsers | Keep torrtux structure; site-level tests; health endpoint |
| Android cannot reach localhost API | Document LAN IP; emulator `10.0.2.2` mapping |
| ruTorrent 401 auth | Support Basic + Digest; test against Seedhost; clear error UI |
| Seedbox API variants | `SeedboxClient` interface; ruTorrent first |
| Legal concerns | In-app disclaimer; user-provided infrastructure only |

## Validation plan

- `pytest services/search/tests` — unit + mocked integration
- Manual: Android emulator → dev API → test seedbox
- `py -3.11 -m hive_planner spec-drift-check`
- Wiring audit: UI tap → ViewModel → Retrofit → API → TorrentSearcher → site parser
