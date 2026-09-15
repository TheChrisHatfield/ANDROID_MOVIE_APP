"""One-off probe for live indexer HTML structure."""
from pathlib import Path

from torrtux_core.http_client import http_get
from torrtux_core.sites.providers import PirateBay, YTS

OUT = Path(__file__).resolve().parents[1] / "tests" / "fixtures"

for cls in (PirateBay, YTS):
    site = cls()
    site.test_connection()
    url = site.build_search_url("inception", 0)
    resp = http_get(url, timeout=20)
    name = site.name.replace(" ", "_").lower()
    path = OUT / f"{name}_search.html"
    path.write_bytes(resp.content[:120000])
    rows = site.parse_results(resp.content, "inception")
    print(f"{site.name}: {resp.status_code} bytes={len(resp.content)} parsed={len(rows)}")
    print(f"  url={url}")
    print(f"  saved={path}")
