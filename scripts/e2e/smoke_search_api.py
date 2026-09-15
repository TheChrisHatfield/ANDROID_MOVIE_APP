"""Host-side E2E smoke: search API liveness + search round-trip."""
from __future__ import annotations

import sys
import urllib.error
import urllib.request
import json

BASE = "http://127.0.0.1:8765"


def get(path: str, timeout: int = 60) -> tuple[int, dict | list | str]:
    req = urllib.request.Request(f"{BASE}{path}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode()
            try:
                return resp.status, json.loads(body)
            except json.JSONDecodeError:
                return resp.status, body
    except urllib.error.HTTPError as exc:
        body = exc.read().decode()
        try:
            return exc.code, json.loads(body)
        except json.JSONDecodeError:
            return exc.code, body


def main() -> int:
    status, health = get("/v1/health")
    assert status == 200 and health == {"status": "ok"}, health

    status, sites = get("/v1/sites/health")
    assert status == 200 and "working" in sites, sites
    print(f"sites health: {sites.get('count', 0)} working (cached)")

    # Search may take up to ~30s with parallel indexer fan-out
    status, search = get("/v1/search?q=inception&limit=3&pages=1")
    if status == 503:
        print("WARN: no working indexers (503) — network/indexer issue, API OK")
        return 0
    assert status == 200, search
    assert isinstance(search.get("failed_sites"), list), search
    flat = list(search.get("results", []))
    for group in search.get("groups", []):
        flat.extend(group.get("releases", []))
    print(f"search: {search.get('count', 0)} visible ({len(flat)} releases)")
    for row in flat:
        assert row.get("site") != "EZTV", "EZTV must not appear in movie profile results"
    if not flat:
        print("WARN: zero results — indexers may be blocked")
        return 0

    result_id = flat[0]["id"]
    status, magnet = get(f"/v1/results/{result_id}/magnet", timeout=30)
    if status == 404:
        print("WARN: magnet unavailable for first result (lazy-fetch or cache)")
    else:
        assert status == 200 and str(magnet.get("magnet", "")).startswith("magnet:")
        print("magnet: ok")

    print("smoke_search_api: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"smoke_search_api: FAIL — {exc}", file=sys.stderr)
        raise SystemExit(1)
