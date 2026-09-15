# Search service (torrtux_core + FastAPI)

Ported from read-only upstream `torrtux-c` (see repo `AGENTS.md`).

## Install

```powershell
cd services/search
py -3.11 -m pip install -e ".[dev]"
```

## Run API

```powershell
cd services/search
py -3.11 -m uvicorn api.main:app --host 0.0.0.0 --port 8765
```

Android emulator → host: `http://10.0.2.2:8765`

## Test

```powershell
cd services/search
py -3.11 -m pytest -q
```

## Upstream

Do not edit `F:\DRIVE_DOWNLOADS\torrtux-c` in place. Re-run `scripts/extract_sites.py` if upstream site parsers change.
