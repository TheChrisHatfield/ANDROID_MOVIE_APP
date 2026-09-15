# Planning Rosetta — mine report

**Hook:** `planning.rosetta`, `learning.loop`  
**Cycle:** 2026-09-13  
**Sources mined:** torrtux-c clone, active spec/contracts, sibling Hive rosettas (CRYPTOMATTE pattern), repo execution tree

Process artifact only — behavioral requirements discovered here must land in `docs/specs/torrent-movie-app/` before implementation cites them as FRs.

---

## S — Source (torrtux-c)

**Location:** sibling clone — see `AGENTS.md`  
**Shape:** single `torrtux.py` (~1600 LOC), `setup.py`, `README.md` — **no `requirements.txt`** despite README instructing `pip install -r requirements.txt`.

### Reusable surface

| Symbol | Lines (approx) | Port target |
|--------|----------------|-------------|
| `TorrentSite` base | 43–105 | `torrtux_core/base.py` |
| 25 site subclasses | 107–1081 | `torrtux_core/sites/` |
| `TorrentSearcher` | 1083–1300 | `torrtux_core/searcher.py` |
| `parse_size`, filters | 1302–1317 | `torrtux_core/filters.py` |
| `ThreadPoolExecutor` parallel | 1492–1512 | enable by default in API |

### Site inventory (TorrentSearcher.sites)

PirateBay, X1337, KickassTorrents, YTS, Nyaa, EZTV, TorrentGalaxy, LimeTorrents, TorrentDownloads, Torlock, Zooqle, TorrentFunk, ETTV, MagnetDL, Bitsearch, Glodls, TorrentProject, SkyTorrents, YourBittorrent, GoodTorrent, Torrentz2, RARBG, ArabTorrents, FitGirlRepacks, LinuxTracker.

**Movie profile (spec default):** YTS, X1337, PirateBay, TorrentGalaxy, MagnetDL, EZTV — subset of above.

### Source facts (mine)

| ID | Fact | Implication |
|----|------|-------------|
| S1 | `BeautifulSoup(..., "lxml")` used; `lxml` **not** in `setup.py` | Add `lxml` to `services/search` deps |
| S2 | CLI-only: `tabulate`, `termcolor`, `argparse`, `Spinner` | Strip on port; not in API image |
| S3 | Many sites call `get_magnet_link(detail_url)` inside `parse_results` | N+1 HTTP; API should defer magnet to `/results/{id}/magnet` |
| S4 | `GoodTorrent.parse_results` returns `[]` (stub) | Exclude from movie profile or mark low-priority |
| S5 | `RARBG` always empty + warning | Exclude from default profile |
| S6 | `test_sites()` sequential; no cache TTL | API: cache health results; avoid per-search full probe |
| S7 | No upstream tests | Our `services/search/tests` is the safety net |
| S8 | Seeds often `str`; `"-"` for unknown | Filter logic must handle non-numeric (already in torrtux CLI) |

---

## C — Cartridge / Spec Kit (this repo)

**Active feature:** `torrent-movie-app`  
**Contracts:** `openapi.yaml`, `seedbox-rutorrent.md`, `ui-yatse-reference.md`

### Spec coverage (honest)

| Area | Spec/plan | Execution |
|------|-----------|-----------|
| Search API surface | Defined | Not built |
| ruTorrent + `dir_edit` | Defined | Not built |
| Uploaded dedup | Defined | Not built |
| Yatse UI IA | Defined | Not built |
| Search service **deployment** (phone → API) | Partial (assumption: LAN/VPS) | **Gap G8** |
| ruTorrent **response parse** contract test | Mentioned, not specified | **Gap G9** |
| Result cache TTL / max size | Plan says in-memory UUID | **Gap G14** |
| Android cleartext HTTP (dev API) | Not mentioned | **Gap G11** |
| `info_hash` base32 vs hex | Implied | **Gap G10** — needs contract test |

### Execution tree today

```
src/android_movie_app/     # placeholder package only
tests/test_placeholder.py  # 1 pass
services/search/           # MISSING
android/                   # MISSING
```

Root `pyproject.toml` has **no** search-service dependencies — search is a separate package per plan (T001).

---

## L — Learning loop / cross-repo patterns

| Pattern | Source | Apply here |
|---------|--------|------------|
| Domain hooks in Rosetta | CRYPTOMATTE `planning-rosetta-stone.md` | Added below in main Rosetta |
| Gap backlog + loot shift | `continual-learning/gap-backlog.md` | Updated with mine IDs |
| Symbiotic mine (S/C/L) | GEN_PBR skill | This document |
| Hive gates | `.hive/spec-kit/WORKFLOW.md` | spec-drift → wiring-audit → ci-check |

---

## Δ — Risks / open questions

| ID | Question | Suggested resolution | Promote to |
|----|----------|----------------------|------------|
| G8 | Where does search API run for production phone use? | Spec: Settings field `search_api_base_url`; dev default `http://10.0.2.2:8765` emulator | spec + openapi |
| G9 | How to parse ruTorrent Success across versions? | MockWebServer fixtures from real Seedhost response | contract test + seedbox-rutorrent.md |
| G10 | `btih` hex vs base32 in magnet URIs | `MagnetHashUtil` normalize to uppercase hex40 | spec FR + unit test |
| G11 | HTTP cleartext to LAN API on Android 9+ | `network_security_config.xml` debug-only | plan + android tasks |
| G12 | `lxml` missing upstream | Pin in `services/search/pyproject.toml` | T001 |
| G13 | Which movie-profile indexers work live in 2026? | `@pytest.mark.live` smoke; document in gap-backlog | tests |
| G14 | Result cache eviction | Max 500 entries / 1h TTL in API v1 | plan or spec |
| G15 | FitGirl/LinuxTracker in movie search? | Out of movie profile (games/linux) | profiles.py |
| G16 | Parallel search default on API? | Yes — torrtux `--parallel` behavior | searcher config |
| G17 | Search API auth? | Open on LAN v1; API key post-MVP if public | spec out-of-scope note |
| G18 | Seedbox status chip: what ping? | HEAD on ruTorrent base URL or health from last send | ui-yatse + tasks |
| G19 | Room vs DataStore for Uploaded list | Room per plan T039 | — |
| G20 | torrtux vendor: submodule vs copy | Copy/split into repo; submodule optional | T002 README |

---

## Loot shift (post-mine)

**Bottleneck unchanged:** G1/G2 (torrtux port + API) — but port strategy is now explicit (S1–S8).  
**New pre-flight:** resolve G12 (lxml), G3 (N+1 magnet deferral), G4/G5 (stub sites) during T005–T008.  
**Spec amendments recommended:** G8, G10, G11 before Android phase.

---

## Validation (this cycle)

```text
Mine: torrtux.py + specs + repo tree (read-only)
spec-drift-check: run after Rosetta update
```

---

## Cycle 2 — 2026-09-13 (learning loop re-mine)

**Sources:** torrtux-c `torrtux.py`, `services/search/` implementation vs spec FR-001–FR-018  
**Promoted:** FR-019–FR-030 in `spec.md`; openapi `refresh`, `movie_profile`, error semantics

### New gaps (G21–G55 summary)

| ID | Fact | Spec / action | Priority |
|----|------|---------------|----------|
| G21 | Health: 10s GET, 200 only, first mirror | → FR-019 | P1 |
| G22 | Search/magnet: 15s timeout | → FR-020 | P1 |
| G23 | Mirror list; no rotation on search fail | Contract note | P2 |
| G24 | Parallel swallows per-site errors silently | Edge case; optional `failed_sites[]` post-MVP | P1 |
| G25 | Pagination stops on empty page | Edge case in spec | P2 |
| G26 | YTS/EZTV ignore `page` param | Edge case in spec | P2 |
| G27 | MagnetDL first-letter URL | Edge case in spec | P2 |
| G28 | TPB `orderby=99` pre-sort | Contract note | P3 |
| G29 | First magnet link wins on detail page | Edge case in spec | P2 |
| G30 | Inline `get_magnet_link` during search | → FR-021; **impl gap** | P1 |
| G31 | `detail_url` omitted from adapter dicts | → FR-021; **impl gap** | P1 |
| G32 | `min_seeds` drops `"-"` seeds | → FR-025 | P1 |
| G33 | `min_size`/`max_seeds` not in REST | Out of scope v1 | P2 |
| G34 | `parse_size` no M/G/T shorthand | → FR-029 | P2 |
| G35 | `pages` 1–10 | Already in entity | P3 |
| G36 | `limit` before sort in searcher | → FR-022; **impl gap** | P1 |
| G37 | Parallel cap min(8, n) | → FR-026 | P2 |
| G38 | Health cache 300s + refresh | → FR-023; openapi | P2 |
| G39 | 503 → Android copy | → FR-024 | P2 |
| G40 | Magnet 404 distinction | → FR-028 | P2 |
| G41 | No User-Agent / indexer auth | Out of scope + VPN note | P2 |
| G42 | No rate limiting | Out of scope post-MVP | P3 |
| G43 | RARBG silent skip | profiles.py exclude | P3 |
| G44 | `--latest` browse | Out of scope | P3 |
| G45 | YTS relative detail_url | Edge case; **impl gap** | P1 |
| G46 | Inline magnet when on search row | → FR-021 exception | P2 |
| G47 | Magnet uses all sites not working | Post-MVP | P3 |
| G48 | `movie_profile` param | openapi + FR-030 area | P2 |
| G49 | Unknown seeds sort last | FR-022 | P3 |
| G50 | No credential logging | → FR-027 | P2 |
| G51 | Case-insensitive site names | → FR-030 | P2 |
| G52 | CLI user strings → Android copy | Edge cases in spec | P2 |
| G53 | No indexer auth | Out of scope | P2 |
| G54 | Health ≠ searchability | Edge case in spec | P2 |
| G55 | category/lang no-ops | Out of scope | P3 |

### Implementation gaps (execution vs new spec)

| Gap | File | Fix |
|-----|------|-----|
| G30+G31 | `torrtux_core/sites/providers.py` | Stop N+1 magnet fetch; include `detail_url` in every result dict |
| G36 | `torrtux_core/searcher.py` | Sort before limit |
| G45 | `providers.py` YTS | `urljoin(working_url, detail_url)` |

### Loot shift (cycle 2)

**Bottleneck:** Close impl gaps G30/G31/G36/G45 before device E2E.  
**Optional:** G13 live smoke; G24 failed_sites telemetry; G17 API auth if public.
