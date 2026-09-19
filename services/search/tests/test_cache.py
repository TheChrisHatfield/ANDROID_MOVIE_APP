import time
from uuid import UUID

from api.cache import ResultCache


def test_cache_clear():
    from uuid import UUID

    cache = ResultCache(max_entries=10, ttl_seconds=60)
    stored = cache.put_many([{"name": "a", "site": "s"}])
    result_id = UUID(stored[0]["id"])
    assert cache.get(result_id) is not None
    cache.clear()
    assert cache.get(result_id) is None


def test_cache_put_get_and_ttl():
    cache = ResultCache(max_entries=10, ttl_seconds=1)
    stored = cache.put_many([{"name": "Inception", "site": "YTS", "seeds": "10"}])
    result_id = UUID(stored[0]["id"])
    assert cache.get(result_id)["name"] == "Inception"
    time.sleep(1.1)
    assert cache.get(result_id) is None


def test_get_refreshes_ttl():
    cache = ResultCache(max_entries=10, ttl_seconds=2)
    stored = cache.put_many([{"name": "Inception", "site": "YTS"}])
    result_id = UUID(stored[0]["id"])
    original_created = cache._store[result_id].created_at
    time.sleep(1.0)
    assert cache.get(result_id) is not None
    assert cache._store[result_id].created_at > original_created
    time.sleep(1.1)
    assert cache.get(result_id) is not None


def test_resolve_magnet_extends_ttl():
    cache = ResultCache(max_entries=10, ttl_seconds=2)
    stored = cache.put_many([{"name": "Inception", "site": "YTS", "magnet": None}])
    result_id = UUID(stored[0]["id"])
    original_created = cache._store[result_id].created_at
    time.sleep(1.0)
    cache.resolve_magnet(result_id, "magnet:?xt=urn:btih:abc")
    assert cache._store[result_id].created_at > original_created
    assert cache.get(result_id) is not None


def test_cache_evicts_oldest_on_overflow():
    cache = ResultCache(max_entries=2, ttl_seconds=60)
    cache.put_many([{"name": "one", "site": "a"}])
    cache.put_many([{"name": "two", "site": "a"}])
    third = cache.put_many([{"name": "three", "site": "a"}])
    assert len(third) == 1
    assert len(cache._store) == 2


def test_put_many_batch_returns_only_surviving_ids():
    cache = ResultCache(max_entries=2, ttl_seconds=60)
    stored = cache.put_many(
        [
            {"name": "one", "site": "a"},
            {"name": "two", "site": "a"},
            {"name": "three", "site": "a"},
        ]
    )
    assert len(stored) == 2
    for row in stored:
        assert cache.get(UUID(row["id"])) is not None


def test_resolve_magnet_returns_defensive_copy():
    cache = ResultCache(max_entries=10, ttl_seconds=60)
    stored = cache.put_many([{"name": "Inception", "site": "YTS", "magnet": None}])
    result_id = UUID(stored[0]["id"])
    resolved = cache.resolve_magnet(result_id, "magnet:?xt=urn:btih:abc")
    resolved["magnet"] = "mutated"
    assert cache.get(result_id)["magnet"] == "magnet:?xt=urn:btih:abc"


def test_put_many_returns_defensive_copies():
    cache = ResultCache(max_entries=10, ttl_seconds=60)
    stored = cache.put_many([{"name": "Inception", "site": "YTS"}])
    stored[0]["name"] = "mutated"
    assert cache.get(UUID(stored[0]["id"]))["name"] == "Inception"


def test_put_many_reuses_stable_id_for_same_release():
    cache = ResultCache(max_entries=10, ttl_seconds=60)
    row = {"name": "Inception", "site": "YTS", "detail_url": "https://yts.mx/movies/inception-2010"}
    first = cache.put_many([row])[0]
    second = cache.put_many([dict(row)])[0]
    assert first["id"] == second["id"]
    cache.resolve_magnet(UUID(first["id"]), "magnet:?xt=urn:btih:abc")
    third = cache.put_many([dict(row)])[0]
    assert third["id"] == first["id"]
    assert cache.get(UUID(first["id"]))["magnet"] == "magnet:?xt=urn:btih:abc"


def test_put_many_preserves_existing_metadata_on_reput():
    cache = ResultCache(max_entries=10, ttl_seconds=60)
    row = {
        "name": "Inception 2010 1080p",
        "site": "YTS",
        "detail_url": "https://yts.mx/movies/inception-2010",
        "poster_url": "https://image.tmdb.org/poster.jpg",
        "overview": "Dream within a dream",
    }
    first = cache.put_many([row])[0]
    second = cache.put_many(
        [
            {
                "name": "Inception 2010 1080p",
                "site": "YTS",
                "detail_url": "https://yts.mx/movies/inception-2010",
                "seeds": "99",
            }
        ]
    )[0]
    assert first["id"] == second["id"]
    cached = cache.get(UUID(first["id"]))
    assert cached["poster_url"] == "https://image.tmdb.org/poster.jpg"
    assert cached["overview"] == "Dream within a dream"


def test_stable_id_differs_for_same_name_different_size():
    cache = ResultCache(max_entries=10, ttl_seconds=60)
    first = cache.put_many(
        [{"name": "Release", "site": "1337x", "size": "1 GB", "seeds": "10"}]
    )[0]
    second = cache.put_many(
        [{"name": "Release", "site": "1337x", "size": "2 GB", "seeds": "5"}]
    )[0]
    assert first["id"] != second["id"]
