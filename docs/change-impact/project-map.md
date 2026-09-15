<!-- BOOTSTRAP: hive_planner init -->

# Project Map

**Hook:** `change.impact`, `next.files`

| Module | Role | Spec / Delta deps |
|--------|------|-------------------|
| `docs/delta/*.md` | Primary planning truth | `context.delta` |
| `docs/planning-rosetta-stone.md` | Hook legend | `planning.rosetta` |
| `docs/planning-rosetta-mine.md` | Mined gaps S/C/L/Δ | `planning.rosetta`, `learning.loop` |
| `continual-learning/` | Agent loop, gap-backlog | `learning.loop` |
| `services/search/torrtux_core/` | Ported torrtux scrapers | `search.torrtux` |
| `services/search/api/` | FastAPI search service | `search.api` |
| `android/app/` | Compose UI shell | `android.ui` |
| `android/core/network/` | Retrofit SearchApi | `android.client` |
| `android/core/data/` | Settings, Room, ruTorrent | `seedbox.rutorrent`, `seedbox.duplicate_guard` |
