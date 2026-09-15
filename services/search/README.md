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

## Movie metadata (posters, overview, trailers)

Set a free [TMDB API key](https://www.themoviedb.org/settings/api) on the search service host:

```powershell
$env:TMDB_API_KEY = "your-key-here"
py -3.11 -m uvicorn api.main:app --host 0.0.0.0 --port 8765
```

Search uses `group=true` (default) to merge duplicate films and `enrich=true` to fetch TMDB metadata when the key is set. Without a key, grouping still works; posters/descriptions/trailers are omitted.

## Test

```powershell
cd services/search
py -3.11 -m pytest -q
```

## Upstream

Do not edit `F:\DRIVE_DOWNLOADS\torrtux-c` in place. Re-run `scripts/extract_sites.py` if upstream site parsers change.
