<!-- BOOTSTRAP: hive_planner init -->

# Planning Rosetta Stone — ANDROID_MOVIE_APP

Search phrase: **Planning Rosetta Stone** · Hook: `planning.rosetta`

Mined reference for **torrent-movie-app**: torrtux-c upstream, Spec Kit contracts, and agent navigation. Deep mine report: [`planning-rosetta-mine.md`](planning-rosetta-mine.md).

---

## Core hooks (Hive)

| HOOK_ID | Meaning |
|---------|---------|
| `planning.rosetta` | This legend |
| `context.delta` | Holistic / macro / micro docs |
| `cursor.rules` | `.cursor/rules/*.mdc` |
| `change.impact` | Impact packets before edits |
| `change.bugfix_mode` | Bugfix impact mode |
| `spec.flow` | Spec Kit spec / plan / tasks |
| `integration.cl_spec` | Spec Kit (behavioral) + Continual Learning (operational) |
| `integration.wiring_audit` | End-to-end chain validation before task complete |
| `learning.loop` | Agent playbook — [`../continual-learning/learning-loop.md`](../continual-learning/learning-loop.md) |

---

## Domain hooks (mined — torrent-movie-app)

| HOOK_ID | Meaning | Unlocks / relates |
|---------|---------|-------------------|
| `search.torrtux` | Port torrtux-c scrapers → `services/search/torrtux_core/` | → `search.api`, `search.indexers` |
| `search.indexers` | Site adapters, health probe, movie profile subset | → `search.filters`, `search.api` |
| `search.filters` | `parse_size`, min/max seeds, limit | → `search.api` |
| `search.api` | FastAPI `/v1/search`, magnet lazy-fetch, result cache | → `android.client` |
| `seedbox.rutorrent` | `POST php/addtorrent.php` — `url` + `dir_edit` | → `seedbox.settings` |
| `seedbox.settings` | Base URL, auth, magnet download folder (Settings) | → `seedbox.rutorrent` |
| `seedbox.duplicate_guard` | Uploaded list by `info_hash`; block re-send | → `android.ui` |
| `android.client` | Kotlin Compose app, Retrofit, Room | → `android.ui` |
| `android.ui` | Yatse-style drawer, cards, detail CTA | → contracts `ui-yatse-reference.md` |

---

## Agent navigation

Load `planning.rosetta` first. Then:

1. **Process** → `learning.loop` → [`continual-learning/learning-loop.md`](../continual-learning/learning-loop.md) + [`gap-backlog.md`](../continual-learning/gap-backlog.md)
2. **Mission / constraints** → `context.delta` → [`delta/00_holistic.md`](delta/00_holistic.md)
3. **Modules / layers** → [`delta/10_macro.md`](delta/10_macro.md)
4. **Active feature** → `spec.flow` → [`specs/torrent-movie-app/`](specs/torrent-movie-app/)
5. **Upstream scraper (read-only)** → `search.torrtux` → sibling torrtux-c clone (`AGENTS.md`) → mine [`planning-rosetta-mine.md`](planning-rosetta-mine.md) §S
6. **Search HTTP contract** → `search.api` → [`specs/torrent-movie-app/contracts/openapi.yaml`](specs/torrent-movie-app/contracts/openapi.yaml)
7. **Seedbox handoff** → `seedbox.rutorrent` → [`specs/torrent-movie-app/contracts/seedbox-rutorrent.md`](specs/torrent-movie-app/contracts/seedbox-rutorrent.md)
8. **Duplicate prevention** → `seedbox.duplicate_guard` → spec US3 + Room `UploadedMagnet`
9. **Android UX** → `android.ui` → [`specs/torrent-movie-app/contracts/ui-yatse-reference.md`](specs/torrent-movie-app/contracts/ui-yatse-reference.md)
10. **Open gaps / loot shift** → [`planning-rosetta-mine.md`](planning-rosetta-mine.md) §Δ + [`continual-learning/gap-backlog.md`](../continual-learning/gap-backlog.md)

---

## Layer map (execution)

```text
android/ (Compose) ──HTTPS JSON──► services/search/api/ (FastAPI)
                                         │
                                         ▼
                                   torrtux_core/  ◄── ported from torrtux-c
                                         │
Android SeedboxClient ──HTTPS POST──────► ruTorrent addtorrent.php
Room uploaded_magnets ◄── on success ────┘
```

---

## Primary files

| Purpose | Path |
|---------|------|
| Entry | [`START_HERE.md`](../START_HERE.md) |
| Operations | [`AGENTS.md`](../AGENTS.md) |
| Holistic | [`delta/00_holistic.md`](delta/00_holistic.md) |
| Macro | [`delta/10_macro.md`](delta/10_macro.md) |
| Micro | [`delta/20_micro/torrent-movie-app.md`](delta/20_micro/torrent-movie-app.md) |
| Spec | [`specs/torrent-movie-app/spec.md`](specs/torrent-movie-app/spec.md) |
| Plan | [`specs/torrent-movie-app/plan.md`](specs/torrent-movie-app/plan.md) |
| Tasks | [`specs/torrent-movie-app/tasks.md`](specs/torrent-movie-app/tasks.md) |
| Mine report | [`planning-rosetta-mine.md`](planning-rosetta-mine.md) |
| CL integration | [`cl-spec-integration.md`](cl-spec-integration.md) |
| Learning loop | [`continual-learning/learning-loop.md`](../continual-learning/learning-loop.md) |

---

## UNLOCKS quick reference

| If you are working on… | Load hooks |
|------------------------|------------|
| Splitting torrtux.py | `search.torrtux`, `search.indexers` |
| FastAPI routes / cache | `search.api`, `spec.flow` |
| Movie site defaults | `search.indexers`, `search.filters` |
| ruTorrent POST | `seedbox.rutorrent`, `seedbox.settings` |
| Uploaded / dedup | `seedbox.duplicate_guard` |
| Compose screens | `android.ui`, `android.client` |
| Hive handoff | `learning.loop`, `integration.wiring_audit` |
