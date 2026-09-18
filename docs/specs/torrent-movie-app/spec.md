# Feature Specification: Torrent Movie App

**Feature Branch**: `torrent-movie-app`

**Created**: 2026-09-13

**Status**: Draft

**Input**: Android app to search movies via torrent indexers (repurposing torrtux-c) and send magnet links to a seedbox in a few clicks.

<!-- ROSETTA-NAV -->

## Agent navigation (Rosetta Stone)

Before implementing, load the hook map — do not jump straight into requirements.

1. [`docs/planning-rosetta-stone.md`](../../planning-rosetta-stone.md) — hook `planning.rosetta`
2. Follow **UNLOCKS**: `context.delta` → [`00_holistic.md`](../../delta/00_holistic.md), [`10_macro.md`](../../delta/10_macro.md)
3. This feature — hook `spec.flow`:
   - **Feature id:** `torrent-movie-app`
   - Delta micro: [`torrent-movie-app.md`](../../delta/20_micro/torrent-movie-app.md)
   - Plan: [`plan.md`](./plan.md)
   - Tasks: [`tasks.md`](./tasks.md)
   - Contracts: [`contracts/openapi.yaml`](./contracts/openapi.yaml), [`contracts/seedbox-rutorrent.md`](./contracts/seedbox-rutorrent.md), [`contracts/ui-yatse-reference.md`](./contracts/ui-yatse-reference.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Search for a movie (Priority: P1)

As a user, I open the app, type a movie title, and see a unified list of torrent results from multiple indexers (powered by repurposed torrtux-c logic) so I can pick the best release without visiting each site.

**Why this priority**: Core value — without search there is no app.

**Independent Test**: Configure search API URL → enter "Inception" → receive ≥1 result with name, size, seeds, site, and result id within 30s.

**Acceptance Scenarios**:

1. **Given** the search service is running and reachable, **When** the user submits a movie title, **Then** the app displays aggregated results sorted by seed count (descending) where available.
2. **Given** search results are shown, **When** the user taps a result, **Then** the app shows detail including magnet link (fetched on demand if not inline).
3. **Given** some indexers are down, **When** the user searches, **Then** partial results from working sites still appear and failures are not shown as success.

---

### User Story 2 - Send magnet to seedbox (Priority: P1)

As a user who has configured my seedbox, I tap one button on a torrent detail screen to add the magnet to my seedbox so downloading starts without manual copy-paste.

**Why this priority**: Completes the few-click promise; search alone does not deliver downloads.

**Independent Test**: Configure ruTorrent Web UI credentials (Seedhost) → select a result with magnet → tap "Send to seedbox" → torrent appears in ruTorrent on the seedbox.

**Acceptance Scenarios**:

1. **Given** valid seedbox settings saved, **When** the user taps "Send to seedbox" on a result with a magnet, **Then** the app calls ruTorrent with `dir_edit=/home5/chris82/downloads/MOVIES/` (or user-configured path) and shows success confirmation.
2. **Given** invalid seedbox credentials, **When** the user taps "Send to seedbox", **Then** the app shows a clear error and does not report success.
3. **Given** a result without a magnet link, **When** the user views detail, **Then** "Send to seedbox" is disabled with explanation.
4. **Given** a magnet already in the uploaded list (same info-hash), **When** the user taps "Send to seedbox", **Then** the app blocks the send and shows that this magnet was already uploaded (unless removed from the list).

---

### User Story 3 - Uploaded magnets list (Priority: P2)

As a user, I see a list of movies I've already sent to my seedbox, and I can remove entries so I don't accidentally upload the same magnet twice.

**Why this priority**: Prevents duplicate downloads on the seedbox; lightweight local history with user control.

**Independent Test**: Send a magnet → it appears in Uploaded list → try sending same magnet again → blocked → remove from list → send succeeds again.

**Acceptance Scenarios**:

1. **Given** a successful "Send to seedbox", **When** ruTorrent confirms success, **Then** the app adds an entry to **Uploaded** with display name, site, sent date/time, and magnet info-hash.
2. **Given** the **Uploaded** screen, **When** the user views the list, **Then** entries are shown newest-first with movie/torrent name and date sent.
3. **Given** an entry in the uploaded list, **When** the user deletes/removes it, **Then** it is removed from local storage and the same magnet may be sent again.
4. **Given** search results or torrent detail, **When** a result's magnet matches an uploaded info-hash, **Then** the UI indicates "Already uploaded" (badge or disabled send with explanation).

---

### User Story 4 - Configure seedbox and search preferences (Priority: P2)

As a user, I enter my seedbox URL, credentials, magnet download folder, and preferred indexers once in Settings so everyday use is search-and-tap only — and I can change where magnets save if my seedbox layout changes.

**Why this priority**: Required for repeat use but can follow MVP search+send with hardcoded dev settings.

**Independent Test**: Change download directory in Settings → send a magnet → verify ruTorrent receives the new `dir_edit` path (not the previous one).

**Acceptance Scenarios**:

1. **Given** the settings screen, **When** the user saves ruTorrent base URL (e.g. `https://user.slot.seedhost.eu/rutorrent/`), username, and password, **Then** credentials are stored encrypted and never logged.
2. **Given** saved settings, **When** the app restarts, **Then** seedbox configuration is restored without re-entry.
3. **Given** the **Magnet download folder** field (default `/home5/chris82/downloads/MOVIES/`), **When** the user enters a different absolute path and saves, **Then** all subsequent "Send to seedbox" actions use the new path in `dir_edit` until changed again.
4. **Given** a "movie sites only" toggle (default on), **When** searching, **Then** only the movie profile indexers are queried.

---

### User Story 5 - Yatse-style navigation and layout (Priority: P2)

As a user familiar with Yatse, I navigate the app through a clear left drawer and categorized screens (Search, Uploaded, Settings) with card-based lists and a detail screen centered on one primary action — so the app feels intuitive without relearning patterns.

**Why this priority**: Reduces UX design risk by adopting a proven Android media-app structure; aligns with user expectation.

**Independent Test**: Open app → drawer shows Search / Uploaded / Settings / Help → Search is default → torrent detail has prominent Send button → Settings grouped by category.

**Acceptance Scenarios**:

1. **Given** the app launches, **When** the main UI loads, **Then** a Yatse-style **navigation drawer** is available with sections: Search (default), Uploaded, Settings, Help.
2. **Given** the Search screen, **When** results load, **Then** they display as **Material cards** (title, site, size, seeds) similar to Yatse library browsing — not a raw table.
3. **Given** torrent detail, **When** viewed, **Then** layout follows Yatse media-detail pattern: metadata block + single primary **Send to seedbox** action.
4. **Given** the app bar, **When** seedbox is configured, **Then** a **host/status indicator** shows connected vs error (Yatse host-icon analog).
5. **Given** Settings, **When** opened, **Then** options are **grouped** (General, Seedbox, Search, Interface) like Yatse’s tiered settings — not one flat list.

---

### User Story 6 - Filter and sort results (Priority: P3)

As a user, I filter by minimum seeds and maximum size so I quickly find a healthy, reasonably sized release.

**Why this priority**: torrtux-c already supports these filters; improves UX but not blocking MVP.

**Independent Test**: Search with min-seeds=10 → all displayed results show ≥10 seeds.

**Acceptance Scenarios**:

1. **Given** search results, **When** the user sets min seeds to 10, **Then** only results with ≥10 seeds remain.
2. **Given** search results, **When** the user sets max size to 4GB, **Then** results above 4GB are hidden.

---

### User Story 8 - Kodi-style movie library cards (Priority: P2)

As a user searching for a movie, I see **one card per film** (poster, title, year, short description) with duplicate releases from different indexers grouped underneath — like Kodi's movie library — and I can watch a trailer before picking a torrent release.

**Why this priority**: Raw torrent rows for the same film are noisy; metadata makes search feel like a movie app.

**Independent Test**: Search "Inception" with TMDB API key configured on search server → grouped card shows poster + overview + trailer button; expand shows compact per-site releases.

**Acceptance Scenarios**:

1. **Given** multiple indexers return the same film, **When** results load, **Then** the app shows one **movie group** per title/year with a release count, not duplicate full-width cards.
2. **Given** TMDB is configured (`TMDB_API_KEY` on search service), **When** a group is shown, **Then** poster image and short overview (≤3 lines) appear on the card.
3. **Given** TMDB returns a YouTube trailer, **When** the user taps the trailer control, **Then** the trailer opens in YouTube (or browser).
4. **Given** a grouped movie card, **When** the user expands releases, **Then** each row shows site, size, and seeds in one compact line; tap opens detail/send flow unchanged.

---

### User Story 7 - Phone and Z Fold layouts (Priority: P1)

As a user on **any** supported Android device (traditional phone or Samsung Galaxy Z Fold), I get the same search, detail, and seedbox-send behavior — with a two-pane search+detail layout on Z Fold inner screen when unfolded, and standard single-pane navigation otherwise.

**Why this priority**: One APK ships to both form factors; fold-only or phone-only bugs block release for half the audience.

**Independent Test**:

- **Phone lane:** Pixel-class device or emulator → search → tap result → detail screen → send to seedbox.
- **Fold lane:** Samsung Z Fold unfolded (inner width ≥600dp) → search in left pane → select result → detail + send in right pane; cover→unfold restores detail when applicable.

**Acceptance Scenarios**:

1. **Given** a non-Fold phone, **When** the user taps a search result, **Then** the app navigates to a full-screen detail route with the same magnet/send behavior as fold.
2. **Given** a Samsung Z Fold on first launch, **When** the inner screen is wide enough, **Then** search and detail appear side by side using shared `SearchScreen` and `TorrentDetailScreen` (not duplicate business logic).
3. **Given** a Z Fold folded or narrow (&lt;600dp width), **When** the user taps a result, **Then** behavior matches the phone lane (single-pane navigation).
4. **Given** a release candidate build, **When** QA completes a bug hunt cycle, **Then** both phone and fold lanes were exercised for any change touching navigation, selection, or `android/.../fold/`.

---

### Edge Cases

- No indexers reachable → show "No sources available" with retry, not empty success.
- Search query returns zero hits → empty state with suggestion to broaden query.
- Seedbox offline → timeout with retry; no false "added" toast.
- Invalid download path → ruTorrent `FailedDirectory`; app prompts user to fix directory in Settings.
- Magnet fetch fails on detail page → show error + retry button.
- User on mobile network with indexer blocks → optional note to use VPN (informational only).
- Duplicate magnet (same info-hash) → block send; user must remove from Uploaded list to re-send.
- Uploaded list empty → show empty state with short explanation.
- All indexers down → API 503; app shows "No sources available" with retry (maps torrtux CLI VPN hint as informational only).
- Zero search hits across working indexers → empty state; suggest broader query (torrtux: "No results found from any site").
- Magnet cache expired (1h TTL) on detail → 404 `Result not found or expired`; detail screen offers retry back to search.
- Magnet detail fetch fails → 404 `Magnet unavailable`; detail screen shows error + retry (FR-028).
- `min_seeds` filter with YTS/EZTV-heavy results → many rows hidden; optional UI note that unknown-seed releases were excluded.
- `pages>1` ineffective for **YTS** and **EZTV** (adapters ignore page in URL); pagination stops when a page returns zero rows.
- **MagnetDL** builds browse URL from first letter of query (`a` fallback for empty); single-char queries behave differently.
- Multi-magnet detail pages → first `magnet:` link wins (upstream torrtux behavior).
- Health probe hits site **homepage**, not search endpoint — indexer can pass health but fail search (HTML change, CAPTCHA).
- Relative `detail_url` from adapters (e.g. YTS) MUST be normalized to absolute before magnet fetch.
- Private trackers / login-walled indexers → out of scope v1 (no indexer auth in torrtux).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST refactor torrtux-c `TorrentSite` adapters and `TorrentSearcher` into a standalone Python package (`torrtux_core`) without CLI dependencies.
- **FR-002**: System MUST expose a REST API (`/v1/search`, `/v1/sites/health`, `/v1/results/{id}/magnet`) documented in `contracts/openapi.yaml`.
- **FR-003**: Android app MUST allow movie title search and display results in a scrollable list (name, site, size, seeds).
- **FR-004**: Android app MUST fetch magnet links on demand for selected results.
- **FR-005**: Android app MUST send magnet URIs to seedbox via **ruTorrent** `POST {base_url}php/addtorrent.php` with form fields `url={magnet}` and `dir_edit={download_directory}` plus HTTP Basic/Digest auth (see [`contracts/seedbox-rutorrent.md`](./contracts/seedbox-rutorrent.md)).
- **FR-005a**: Settings MUST expose a **Magnet download folder** field; default `/home5/chris82/downloads/MOVIES/`, user-editable at any time; value persisted and sent as `dir_edit` on every magnet add.
- **FR-005b**: Changing the download folder in Settings MUST take effect immediately for the next send — no app restart required.
- **FR-006**: System MUST store seedbox credentials using Android EncryptedSharedPreferences / Keystore.
- **FR-007**: Search API MUST default to a **movie site profile**: YTS, 1337x, The Pirate Bay, TorrentGalaxy, MagnetDL, LimeTorrents (configurable). EZTV is TV-focused and excluded from the default movie profile.
- **FR-008**: System MUST run indexer health checks (torrtux `test_connection`) at API startup or on `/v1/sites/health`.
- **FR-009**: Search API MUST support `min_seeds`, `max_size`, `limit`, and `sites` query parameters (ported from torrtux filters).
- **FR-009a**: Android Settings MUST expose **Search API base URL** (user-configurable); dev default `http://10.0.2.2:8765` for emulator → host machine.
- **FR-009b**: Search API MUST cache search results in memory keyed by UUID for magnet lazy-fetch: **max 500 entries**, **TTL 1 hour**; evict oldest on overflow.
- **FR-010**: System MUST NOT report search or seedbox add as successful when underlying HTTP calls fail.
- **FR-011**: App MUST maintain a local **Uploaded** list of successfully sent magnets (on-device only; no cloud sync in v1).
- **FR-012**: Each uploaded entry MUST store at minimum: `info_hash` (from magnet `btih`), `display_name`, `site`, `sent_at` (timestamp), and full `magnet_uri` for reference.
- **FR-013**: Before sending to seedbox, app MUST check `info_hash` against the uploaded list; if present, MUST block send and show duplicate warning (user may remove entry to allow re-send).
- **FR-014**: Uploaded screen MUST allow the user to **delete/remove** individual entries; removal is immediate and persisted locally.
- **FR-014a**: When seedbox is configured, **Uploaded** MUST show live ruTorrent status per entry (download progress, seeding, paused, or not on seedbox) by matching stored `info_hash` via ruTorrent HTTPRPC `mode=list`; refresh on screen open and pull-to-refresh.
- **FR-015**: Android UI MUST follow Yatse-inspired information architecture per [`contracts/ui-yatse-reference.md`](./contracts/ui-yatse-reference.md): navigation drawer, categorized sections, card lists, detail-with-primary-action.
- **FR-016**: App MUST use **Material Design 3** (Jetpack Compose Material3) for visual consistency with modern Android media apps.
- **FR-017**: App bar MUST include a **seedbox status indicator** (connected / error / unknown) when seedbox is configured.
- **FR-018**: Settings MUST be grouped into **General**, **Seedbox**, **Search**, and **Interface** sections (Yatse tiered-settings pattern).
- **FR-019**: Indexer health probes MUST `GET` each site's mirror list in order (first HTTP **200** wins), **10s** timeout, `allow_redirects=true`; non-200 marks indexer down for that session.
- **FR-020**: Indexer search and magnet-resolution HTTP calls MUST use a **15s** per-request timeout.
- **FR-021**: Site adapters MUST emit an absolute **`detail_url`** on every result. Magnets MUST NOT be fetched during search except when the search row already contains an inline `magnet:` link (e.g. The Pirate Bay, Nyaa); otherwise magnet resolution is deferred to `/v1/results/{id}/magnet`.
- **FR-022**: Search pipeline order MUST be: apply filters → **sort by seeds descending** (unknown seeds last) → **then** apply `limit` (top-N by seed health).
- **FR-023**: `/v1/sites/health` MUST cache probe results for **300 seconds**; `refresh=true` bypasses cache and re-probes all indexers.
- **FR-024**: When zero indexers pass health, search API MUST return **503** with detail `No working indexers`; Android MUST surface **"No sources available"** with retry (not empty success).
- **FR-025**: `min_seeds` filter MUST exclude results with unknown seed counts (`"-"` or non-numeric); when active, UI SHOULD warn that seed-less indexers (e.g. YTS) may be hidden.
- **FR-026**: Parallel search MUST cap concurrency at **`min(8, working_site_count)`** thread pool workers.
- **FR-027**: Search API logs MUST NOT include magnet URIs, seedbox credentials, or indexer URLs containing auth tokens.
- **FR-028**: Magnet endpoint MUST distinguish **404** cases: `Result not found or expired` (cache miss/TTL) vs `Magnet unavailable` (detail fetch failed).
- **FR-029**: `max_size` / `min_size` parameters MUST accept torrtux-style units (`GB`, `MB`, `KB`, `B` suffix); README-style `M`/`G`/`T` shorthands are out of scope unless parser extended.
- **FR-030**: Site name filters (`sites` query param, Settings movie-profile toggle) MUST match indexers **case-insensitively** on canonical site names.
- **FR-031**: Search API SHOULD return `failed_sites[]` when individual indexers error during a parallel search; Android SHOULD surface a non-blocking info message (partial success per US1 scenario 3).
- **FR-032**: Android MUST ship as **one APK** with shared search/detail/send logic; Z Fold MUST NOT use a separate app module or forked repositories.
- **FR-032a**: On any device when `screenWidthDp >= 600` (tablet, fold unfolded, landscape phone), the app MUST enable two-pane search+detail; below that width MUST use single-pane navigation. Samsung Z Fold cover→unfold selection restore remains fold-specific (FR-032c).
- **FR-032b**: Two-pane mode MUST compose shared `SearchScreen` and `TorrentDetailScreen` inside `FoldSearchDetailLayout`; magnet resolution and seedbox send MUST behave identically to the phone detail route.
- **FR-032c**: Transition from cover/single-pane detail to unfolded two-pane MUST restore the selected result when possible (`pendingFoldDetailId` or equivalent).
- **FR-033**: Bug-hunt and release validation MUST follow **dual-lane** coverage: universal fixes in shared layers plus explicit phone and fold smoke for navigation/selection changes (see `.cursor/rules/bug-hunt-dual-lane.mdc`).
- **FR-034**: Fixes SHOULD land in shared code first; fold-only wiring changes MUST include fold-lane validation evidence in the cycle summary.
- **FR-035**: Search API MUST support `group=true` (default) to merge torrent rows for the same film (parsed title + year) into `groups[]` with compact `releases[]` per indexer.
- **FR-036**: When `TMDB_API_KEY` is set on the search service, grouped results SHOULD include `poster_url`, `overview` (short plot), and `trailer_youtube_key` from TMDB.
- **FR-037**: Android search UI MUST render movie group cards (poster, title, year, overview, trailer affordance) and expandable compact release rows; detail/send flow unchanged per release.
- **FR-038**: Torrent detail screen MUST show movie metadata (poster, overview, trailer) when available from grouped search; Settings MAY store optional TMDB API key passed to search API as `tmdb_api_key`.

### Key Entities

- **TorrentResult**: `id`, `name`, `site`, `size`, `seeds`, `leeches`, `date`, `magnet` (optional until resolved), `detail_url`
- **SearchRequest**: `query`, `sites[]`, `min_seeds`, `max_size`, `limit`, `pages`
- **SeedboxConfig**: `type` (`rutorrent` for MVP), `base_url`, `username`, `password`, `auth_scheme` (basic|digest), `download_directory` (default `/home5/chris82/downloads/MOVIES/`), `label` (optional)
- **AddTorrentJob**: `result_id`, `magnet`, `status` (pending|success|failed), `error_message`
- **UploadedMagnet**: `id`, `info_hash` (unique), `display_name`, `site`, `magnet_uri`, `sent_at`, `download_directory` (path used at send time)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: User completes first-time setup and adds a torrent to seedbox in ≤5 minutes (including seedbox config).
- **SC-002**: Repeat session: search → select → send to seedbox in ≤3 taps after app open.
- **SC-003**: Search returns first results within 30 seconds for a popular title on a healthy network (p95).
- **SC-004**: 100% of "success" toasts correspond to HTTP 2xx from seedbox API (no false success).
- **SC-005**: ≥80% of torrtux movie-profile site adapters compile and pass unit smoke tests without modification beyond import paths.
- **SC-006**: 100% of duplicate magnet send attempts (same info-hash in uploaded list) are blocked before ruTorrent API call.
- **SC-007**: Every release-candidate bug hunt documents **phone lane** and **fold lane** smoke results; cycles that touch `NavGraph` or `android/.../fold/` MUST NOT complete with only one lane tested.

## Assumptions

- User has a **Seedhost (or compatible) seedbox** running **ruTorrent** with Web UI credentials. Reference deployment: `https://chris82.snow.seedhost.eu/rutorrent/` (configured in app Settings, not hardcoded).
- Search service runs on a host reachable by the phone (LAN, VPS, or dev machine); v1 does not require public deployment.
- torrtux-c remains the canonical reference; vendored copy under `services/search/vendor/torrtux-c` or path reference to `F:\DRIVE_DOWNLOADS\torrtux-c` during refactor.
- "Seedbox" means remote torrent client with HTTP API, not a specific commercial product.
- Movie search is the primary use case; other content types are out of scope for default profile.

## Out of scope

- Play Store distribution, DRM, or media playback in v1.
- In-app VPN or proxy configuration.
- Account system or multi-user cloud sync.
- qBittorrent and Transmission adapters (post-MVP).
- CLI `--latest` browse feed, `--category`, `--lang` (torrtux no-ops).
- Per-indexer rate limiting and User-Agent rotation (post-MVP; parallel default may trigger blocks).
- Search API authentication (v1 LAN-trust; see G17 if exposing publicly).
- `min_size` / `max_seeds` REST params (core supports; defer unless Android UI adds controls).
