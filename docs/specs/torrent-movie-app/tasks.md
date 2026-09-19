---
description: "Task list for torrent-movie-app — torrtux-c repurposed Android + search API"
---

# Tasks: Torrent Movie App

<!-- ROSETTA-NAV -->

## Agent navigation (Rosetta Stone)

Load [`docs/planning-rosetta-stone.md`](../../planning-rosetta-stone.md) (`planning.rosetta`) before implementing tasks.

- **Spec:** [`spec.md`](./spec.md)
- **Plan:** [`plan.md`](./plan.md)
- **Delta micro:** [`torrent-movie-app.md`](../../delta/20_micro/torrent-movie-app.md)
- **Hooks:** `spec.flow`, `integration.wiring_audit`

**Input**: Design documents from `/specs/torrent-movie-app/`

**Prerequisites**: plan.md, spec.md, contracts/openapi.yaml

**Organization**: Tasks grouped by user story (P1 search, P1 seedbox, P2 settings, P3 filters).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project skeleton and torrtux reference wiring

- [x] T001 Create `services/search/` Python package with `pyproject.toml` (requests, beautifulsoup4, lxml, fastapi, uvicorn, pydantic, pytest)
- [x] T002 [P] Add `vendor/torrtux-c` reference doc pointing to `F:\DRIVE_DOWNLOADS\torrtux-c` (or git submodule) in `services/search/README.md`
- [x] T003 [P] Create `android/` Gradle project skeleton (Kotlin, Compose Material3, minSdk 26, Retrofit, EncryptedSharedPreferences)
- [x] T003a [P] `MainScaffold` — Yatse-style navigation drawer (Search, Uploaded, Settings, Help) per `contracts/ui-yatse-reference.md`
- [x] T004 [P] Copy `docs/specs/torrent-movie-app/contracts/openapi.yaml` into `android/core/network` as codegen input reference

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Refactored torrtux core + API shell — MUST complete before user stories

- [x] T005 Extract `TorrentSite` base to `services/search/torrtux_core/base.py` from `torrtux.py`
- [x] T006 [P] Split site adapters into `services/search/torrtux_core/sites/` (movie profile sites first: YTS, X1337, PirateBay, TorrentGalaxy, MagnetDL, EZTV)
- [x] T007 Port `TorrentSearcher`, `parse_size`, filters to `services/search/torrtux_core/searcher.py` and `filters.py`
- [x] T008 Create `services/search/torrtux_core/profiles.py` with `MOVIE_SITES` default list
- [x] T009 Implement Pydantic models in `services/search/api/models.py` matching OpenAPI schemas
- [x] T010 Implement FastAPI app in `services/search/api/main.py` with `/v1/health`, `/v1/sites/health`, `/v1/search`, `/v1/results/{id}/magnet`
- [x] T011 [P] Unit tests for `parse_size` and filters in `services/search/tests/test_filters.py`
- [x] T012 [P] Mocked HTTP integration test for `/v1/search` in `services/search/tests/test_api_search.py`

**Checkpoint**: `uvicorn api.main:app` returns search JSON for a mocked indexer response

---

## Phase 3: User Story 1 — Search for a movie (Priority: P1) 🎯 MVP

**Goal**: Android user searches a title and sees aggregated torrent results

**Independent Test**: Emulator → API on LAN → search "Inception" → list shows results with site/size/seeds

### Implementation for User Story 1

- [x] T013 [P] [US1] Retrofit `SearchApi` interface in `android/core/network/SearchApi.kt` per OpenAPI
- [x] T014 [P] [US1] `TorrentResult` data class + repository in `android/core/data/SearchRepository.kt`
- [x] T015 [US1] `SearchViewModel` with query state, loading, error in `android/app/.../SearchViewModel.kt`
- [x] T016 [US1] `SearchScreen` — Yatse library-style card list + search bar in `android/app/.../SearchScreen.kt`
- [x] T016a [P] [US1] `TorrentResultCard` composable — site badge, seeds, size, duplicate ribbon
- [x] T017 [US1] `TorrentDetailScreen` — Yatse media-detail layout; primary Send CTA; fetch magnet via `/v1/results/{id}/magnet` if missing
- [x] T018 [US1] Wire navigation Search → Detail in `android/app/.../NavGraph.kt`
- [x] T019 [US1] Handle partial indexer failure — show results + non-blocking error snackbar (FR-010)

**Checkpoint**: End-to-end search and detail view without seedbox

---

## Phase 4: User Story 2 — Send magnet to seedbox (Priority: P1)

**Goal**: One-tap magnet handoff to ruTorrent on Seedhost seedbox

**Independent Test**: Tap "Send to seedbox" → torrent visible in ruTorrent at configured base URL (e.g. `https://chris82.snow.seedhost.eu/rutorrent/`)

### Implementation for User Story 2

- [x] T020 [P] [US2] `SeedboxClient` interface in `android/core/data/seedbox/SeedboxClient.kt`
- [x] T021 [US2] `RuTorrentClient` — `POST {base_url}php/addtorrent.php` body `url={magnet}&dir_edit={download_directory}` (default `/home5/chris82/downloads/MOVIES/`) + Basic/Digest auth per `contracts/seedbox-rutorrent.md`
- [x] T022 [US2] `SeedboxRepository.addMagnet(magnet)` with success/failure result type; parse ruTorrent Success/Failed response
- [x] T023 [US2] Add "Send to seedbox" button on `TorrentDetailScreen` — disabled when no magnet
- [x] T024 [US2] Success/error toasts mapped to actual HTTP status + ruTorrent body status (no false success per FR-010)
- [x] T025 [P] [US2] Unit test `RuTorrentClient` with MockWebServer in `android/core/data/src/test/`
- [x] T025a [P] [US2] `MagnetHashUtil` — extract `info_hash` from magnet URI in `android/core/data/MagnetHashUtil.kt`

**Checkpoint**: Full P1 flow — search → pick → send to seedbox

---

## Phase 5: User Story 3 — Uploaded magnets list (Priority: P2)

**Goal**: Local editable history to prevent duplicate magnet uploads

**Independent Test**: Send magnet → appears in Uploaded → duplicate send blocked → delete entry → send allowed again

### Implementation for User Story 3 (Uploaded)

- [x] T039 [P] [US3] Room entity `UploadedMagnet` + DAO in `android/core/data/db/UploadedMagnet.kt`
- [x] T040 [US3] `UploadedRepository` — add, list (newest first), delete, `contains(infoHash)` in `android/core/data/UploadedRepository.kt`
- [x] T041 [US3] On successful seedbox send, insert `UploadedMagnet` with name, site, hash, magnet, sent_at, download_directory
- [x] T042 [US3] Before send, check duplicate — block with dialog if `info_hash` already in list (FR-013)
- [x] T043 [US3] `UploadedScreen` Compose UI — list with delete per row (swipe or icon) in `android/app/.../UploadedScreen.kt`
- [x] T044 [US3] `UploadedViewModel` + nav entry (bottom bar tab or drawer item "Uploaded")
- [x] T045 [P] [US3] Show "Already uploaded" badge on search results and detail when hash matches list
- [x] T046 [P] [US3] Unit tests for `UploadedRepository` duplicate detection in `android/core/data/src/test/`

**Checkpoint**: Duplicate magnets cannot be sent until user removes from Uploaded list

---

## Phase 6: User Story 4 — Configure seedbox and preferences (Priority: P2)

**Goal**: Persistent encrypted settings for seedbox and API URL

**Independent Test**: Save settings → force-stop app → relaunch → settings retained

### Implementation for User Story 4 (Settings)

- [x] T026 [P] [US4] `SettingsRepository` with EncryptedSharedPreferences in `android/core/data/SettingsRepository.kt`
- [x] T027 [US4] `SettingsScreen` — search API URL, ruTorrent base URL, username, password, auth scheme (basic/digest), **Magnet download folder** text field (default `/home5/chris82/downloads/MOVIES/`, user-editable), movie-sites toggle
- [x] T028 [US4] `SettingsViewModel` + validation (URL format, absolute path for download folder, required fields); changes apply immediately without restart
- [x] T029 [US4] Inject settings into `SearchRepository` and `SeedboxRepository` via Hilt/manual DI
- [x] T030 [US4] First-run setup flow or settings prompt when seedbox not configured

**Checkpoint**: No hardcoded credentials; repeatable user setup

---

## Phase 7: User Story 5 — Filter and sort results (Priority: P3)

**Goal**: Min seeds and max size filters in UI

**Independent Test**: Set min seeds 10 → all visible results ≥10 seeds

### Implementation for User Story 5

- [x] T031 [P] [US5] Filter state in `SearchViewModel` (min_seeds, max_size)
- [x] T032 [US5] Filter chips UI on `SearchScreen`
- [x] T033 [US5] Pass filter query params to `/v1/search` (reuse torrtux filter logic server-side)

**Checkpoint**: Filters work client + server consistently

---

## Phase 8: User Story 5 — Yatse-style UI polish (Priority: P2)

**Goal**: Navigation, status, and grouped settings match Yatse reference

**Independent Test**: Drawer navigation works; seedbox status visible; settings grouped; pull-to-refresh on Search and Uploaded

### Implementation for User Story 5

- [x] T047 [P] [US5] `SeedboxStatusChip` in app bar — ping ruTorrent or last-send status per FR-017
- [x] T048 [US5] `SettingsScreen` refactor — section headers: General, Seedbox, Search, Interface (FR-018)
- [x] T049 [P] [US5] `HelpScreen` — legal disclaimer + setup tips (Yatse Help analog)
- [x] T050 [P] [US5] Pull-to-refresh on `SearchScreen` and `UploadedScreen`
- [x] T051 [US5] Filter bottom sheet on Search (min seeds, max size) — Yatse filter analog

**Checkpoint**: App navigation and visual hierarchy feel consistent with ui-yatse-reference.md

---

## Phase 9: Polish & Cross-Cutting

- [x] T034 [P] In-app legal disclaimer screen (first launch)
- [x] T035 [P] `services/search/README.md` — run instructions, emulator networking (`10.0.2.2`)
- [x] T036 Update `docs/change-impact/project-map.md` with new modules
- [x] T037 Run `py -3.11 -m hive_planner spec-drift-check` and `ci-check`
- [x] T038 Wiring audit attestation for P1 flows (search + seedbox)
- [x] T052 [US8] Web poster fallback when TMDB/indexer have no image (FR-036a) — Wikipedia/Wikimedia (+ optional Google CSE), resize 342×513, `/v1/posters/{id}.jpg`

---

## Dependencies & Execution Order

- **Phase 1–2** block all user stories
- **US1 + US2** are both P1; US2 depends on T017 (detail screen) from US1
- **US3 (Uploaded)** depends on T022/T024 (successful send path) + T025a (hash util)
- **US4 (Settings)** can parallelize after T021 but needs T029 to wire settings
- **US5** depends on US1 search pipeline only

### MVP slice (minimum shippable)

T001 → T012 → T013–T019 → T020–T024 (dev settings hardcoded until T026–T030)

---

## After each implementation task — Integration wiring audit (required)

- [x] Traced caller → handler → core (no layer break)
- [x] No false success when a sub-step failed or was skipped
- [x] Integration-level validation evidence attached
- [x] See `.cursor/rules/integration-wiring-audit.mdc`

## Bug hunt cycles — dual-lane coverage (required)

Per spec **US7**, **FR-032–FR-034**, **SC-007**, and `.cursor/rules/bug-hunt-dual-lane.mdc`:

- [ ] **Universal pass** — API + `core:data` + shared UI; pytest + Android unit tests
- [ ] **Phone lane** — `scripts/e2e/device-e2e.ps1` smoke (single-pane nav)
- [ ] **Fold lane** — Z Fold manual smoke (two-pane + cover→unfold + narrow fallback)
- [ ] Classify each fix: `universal` | `phone-wiring` | `fold-wiring`

Before Continual Learning promotion, run `py -3.11 -m hive_planner spec-drift-check`.
