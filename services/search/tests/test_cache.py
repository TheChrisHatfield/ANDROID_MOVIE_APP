import time
from uuid import uuid4

from api.cache import ResultCache


def test_resolve_magnet_extends_ttl():
    cache = ResultCache(ttl_seconds=60)
    row = {"name": "t", "site": "1337x", "magnet": None, "detail_url": "https://example.com"}
    result_id = cache.put_many([row])[0]["id"]
    entry_id = __import__("uuid").UUID(result_id)
    with cache._lock:
        cache._store[entry_id].created_at = time.time() - 59

    resolved = cache.resolve_magnet(entry_id, "magnet:?xt=urn:btih:abc")
    assert resolved is not None
    assert cache.get(entry_id) is not None
