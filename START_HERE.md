<!-- BOOTSTRAP: hive_planner init -->

# START_HERE

Loader entrypoint for coding agents in **ANDROID_MOVIE_APP**.

## First retrieval step

Search for **Planning Rosetta Stone** or hook `planning.rosetta`:

1. [`docs/planning-rosetta-stone.md`](docs/planning-rosetta-stone.md) — domain hooks + navigation
2. [`docs/planning-rosetta-mine.md`](docs/planning-rosetta-mine.md) — mined gaps (S/C/L/Δ)
3. [`AGENTS.md`](AGENTS.md)

## Read order

1. `docs/planning-rosetta-stone.md`
2. `AGENTS.md`
3. `docs/delta/00_holistic.md` and `docs/delta/10_macro.md` (when present)
4. Active `docs/delta/20_micro/<feature>.md` and `docs/specs/<feature>/` trio (when present)

## Context priority

- **Primary:** Delta docs + active spec/plan/tasks
- **Secondary:** `AGENTS.md` + `.cursor/rules/`
- **Tertiary:** document-sourced excerpts (optional)

## Execution rule

Before editing code, identify the active feature, map work to a `HOOK_ID`, and read the active spec/plan/tasks.
