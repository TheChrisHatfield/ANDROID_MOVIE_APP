<!-- BOOTSTRAP: hive_planner init -->

# Macro — ANDROID_MOVIE_APP

<!-- ROSETTA-NAV -->

## Agent navigation (Rosetta Stone)

Load [`docs/planning-rosetta-stone.md`](../../planning-rosetta-stone.md) (`planning.rosetta`) before using this Delta layer.

- **Hook:** `context.delta`
- **Unlocks:** micro briefs and spec.flow
- **Next:** [`20_micro/`](./) feature briefs

## Architecture

Three-layer system: **Android UI → Search API → Seedbox API**.

```
┌─────────────────────────────────────────────────────────────┐
│  Android App (Kotlin / Jetpack Compose)                     │
│  - Search screen, results list, torrent detail              │
│  - Settings: seedbox host, credentials, default sites      │
│  - "Send to seedbox" action                                 │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS / JSON
┌──────────────────────────▼──────────────────────────────────┐
│  Search Service (Python — refactored from torrtux-c)        │
│  - TorrentSite adapters (ported from torrtux.py)            │
│  - TorrentSearcher orchestration + parallel search            │
│  - REST: /search, /sites/health, /magnet/{id}               │
│  - Movie site profile (subset of torrtux indexers)          │
└──────────────────────────┬──────────────────────────────────┘
                           │ magnet URI
┌──────────────────────────▼──────────────────────────────────┐
│  User Seedbox — ruTorrent on Seedhost (MVP)                   │
│  - POST php/addtorrent.php with magnet URL                    │
└─────────────────────────────────────────────────────────────┘
```

### Module boundaries

| Module | Location | Responsibility |
|--------|----------|----------------|
| `torrtux-core` | `services/search/` | Refactored torrtux site classes, models, filters |
| `search-api` | `services/search/api/` | FastAPI HTTP surface |
| `android-app` | `android/` | UI, ViewModels, seedbox client, secure settings |
| `shared-contracts` | `docs/specs/torrent-movie-app/contracts/` | OpenAPI + JSON schemas |

### torrtux-c reuse map

| torrtux-c artifact | Target | Change |
|--------------------|--------|--------|
| `TorrentSite` base + subclasses | `services/search/torrtux_core/sites/` | Split one file → package; drop CLI deps |
| `TorrentSearcher` | `services/search/torrtux_core/searcher.py` | Return typed dicts; configurable site list |
| `parse_size`, filters | `services/search/torrtux_core/filters.py` | Reuse as-is |
| `get_magnet_link` | Per-site + lazy fetch endpoint | Defer magnet scrape until user selects |
| CLI (`main`, tabulate, termcolor) | **Not ported** | Replaced by API + Android UI |

## Workflows

1. **Planning:** Delta holistic/macro → micro brief → Spec Kit trio (`spec.md`, `plan.md`, `tasks.md`).
2. **Backend first:** Refactor torrtux → expose `/search`; integration test against live indexers (smoke).
3. **Android MVP:** Search UI wired to API; seedbox settings + add-magnet.
4. **Validation:** Wiring audit per task; `hive_planner ci-check` before handoff.

## Dependencies

| Component | Role |
|-----------|------|
| torrtux-c | Upstream scraper reference (`F:\DRIVE_DOWNLOADS\torrtux-c`) |
| Spec Kit | Behavioral spec/plan/tasks |
| Hive CLI | `ci-check`, `spec-drift-check`, impact packets |
| FastAPI + uvicorn | Search service HTTP |
| OkHttp / Retrofit | Android API client |
| Android Keystore | Encrypted seedbox credentials |
