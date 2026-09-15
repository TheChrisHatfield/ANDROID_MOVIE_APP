<!-- BOOTSTRAP: hive_planner init -->

# AGENTS.md — ANDROID_MOVIE_APP

Durable operating guidance for agentic coding in this repo.

## Repository root

`F:/DRIVE_DOWNLOADS/ANDROID_MOVIE_APP`

## Planning Rosetta Stone (required anchor)

- **Search phrase:** `Planning Rosetta Stone`
- **Hook ID:** `planning.rosetta`
- **File:** `docs/planning-rosetta-stone.md`

## Startup read order

See [`START_HERE.md`](START_HERE.md).

## Delta model

- **Holistic** — mission, goals, constraints
- **Macro** — architecture, modules, workflows
- **Micro** — feature scope, tasks, files, tests

## Active feature

**torrent-movie-app** — Android movie torrent search (torrtux-c repurposed) + seedbox magnet handoff.

- Spec: `docs/specs/torrent-movie-app/spec.md`
- Plan: `docs/specs/torrent-movie-app/plan.md`
- Tasks: `docs/specs/torrent-movie-app/tasks.md`
- Micro: `docs/delta/20_micro/torrent-movie-app.md`
- Gap backlog: `continual-learning/gap-backlog.md`
- Rosetta mine: `docs/planning-rosetta-mine.md`

## Durable Workspace Facts

- **Symbiotic handoff:** behavioral truth in Spec Kit (`docs/specs/`), operational truth in this file (Continual Learning / `agents-propose`). See `docs/cl-spec-integration.md`.
- **Agent learning loop:** `continual-learning/learning-loop.md` (hook `learning.loop`) — process only; feature truth stays in spec.
- Install Hive CLI from Hive Code Planner repo: `py -3.11 -m pip install -e ".[dev]"` (run from that repo).
- Handoff gate: `py -3.11 -m hive_planner ci-check` from repo root.
- torrtux-c reference clone (read-only): `F:\DRIVE_DOWNLOADS\torrtux-c` — ported under `services/search/torrtux_core/`; re-extract via `services/search/scripts/extract_sites.py`. # operational
- Search API run: `cd services/search && py -3.11 -m uvicorn api.main:app --host 0.0.0.0 --port 8765` (install first with `pip install -e ".[dev]"` in that dir). # operational
- Search tests: `cd services/search && py -3.11 -m pytest -q` # operational
- Android app: `android/` — requires Android SDK; see `android/README.md`. # operational
- Repo wiring tests: `py -3.11 -m pytest -q` from repo root (4 tests). # operational
