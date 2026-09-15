from torrtux_core.http_client import http_get
from torrtux_core.sites.providers import PirateBay, YTS

for cls in (PirateBay, YTS):
    site = cls()
    ok = site.test_connection()
    url = site.build_search_url("inception", 0)
    response = http_get(url, timeout=30)
    rows = site.parse_results(response.content, "inception")
    print(f"{site.name}: health={ok} apibay={getattr(site, '_apibay_mode', False)} parsed={len(rows)}")
    if rows:
        sample = rows[0]
        print(f"  sample={sample['name'][:70]} seeds={sample['seeds']} magnet={bool(sample.get('magnet'))}")
