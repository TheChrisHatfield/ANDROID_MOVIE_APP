import time

from api.genre_pool_cache import GenrePoolCache, FRESH_TTL_SECONDS, STALE_TTL_SECONDS


def test_genre_pool_stale_while_revalidate():
    cache = GenrePoolCache()
    cache.put("horror", [{"name": "Film 2020 1080p", "site": "YTS"}], [])
    entry = cache.get("horror")
    assert entry is not None
    assert entry.is_fresh()

    entry.created_at = time.time() - FRESH_TTL_SECONDS - 1
    stale = cache.get("horror")
    assert stale is not None
    assert not stale.is_fresh()


def test_expired_genre_pool_entry_is_evicted():
    cache = GenrePoolCache()
    cache.put("horror", [{"name": "Film 2020 1080p", "site": "YTS"}], [])
    entry = cache.get("horror")
    assert entry is not None
    entry.created_at = time.time() - STALE_TTL_SECONDS - 1
    assert cache.get("horror") is None
