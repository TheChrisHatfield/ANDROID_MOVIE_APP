# Feature Micro Brief: torrent-movie-app

**Hook:** `spec.flow`

<!-- ROSETTA-NAV -->

## Agent navigation (Rosetta Stone)

Load [`docs/planning-rosetta-stone.md`](../../planning-rosetta-stone.md) (`planning.rosetta`) before coding from this brief.

- **Hooks:** `context.delta`, `spec.flow`
- **Spec Kit:** [`spec.md`](../../specs/torrent-movie-app/spec.md), [`plan.md`](../../specs/torrent-movie-app/plan.md), [`tasks.md`](../../specs/torrent-movie-app/tasks.md)
- **Upstream reference:** `F:\DRIVE_DOWNLOADS\torrtux-c\torrtux.py`

## Feature Name

Torrent Movie App — Android search + seedbox magnet handoff (torrtux-c repurposed)

## Scope

| In scope | Out of scope |
|----------|--------------|
| Refactor torrtux-c into Python search microservice | Rewriting all site parsers from scratch |
| REST search API with movie-default site profile | Full torrtux CLI parity |
| Android app: search, results, detail, seedbox send | On-device torrent client |
| Local uploaded-magnets list (editable, dedup by info-hash) | Cloud sync of upload history |
| ruTorrent `addtorrent.php` seedbox adapter (MVP, Seedhost) | qBittorrent / Transmission in v1 |
| Secure on-device seedbox credential storage | Cloud-hosted credential sync |
| Integration tests for API + seedbox mock | Play Store release pipeline |

## Key files (planned)

```
services/search/torrtux_core/     # ported from torrtux.py
services/search/api/main.py       # FastAPI app
android/                          # Kotlin Compose app
docs/specs/torrent-movie-app/     # spec trio + contracts
```

## Agent loop

- Process: [`continual-learning/learning-loop.md`](../../continual-learning/learning-loop.md)
- Gaps: [`continual-learning/gap-backlog.md`](../../continual-learning/gap-backlog.md)

## Validation

```powershell
# Search service smoke
cd services/search
py -3.11 -m pytest tests/ -q

# API health + search
uvicorn api.main:app --port 8765
curl "http://127.0.0.1:8765/v1/search?q=inception&limit=5"

# Hive gates
py -3.11 -m hive_planner spec-drift-check
py -3.11 -m hive_planner ci-check
```
