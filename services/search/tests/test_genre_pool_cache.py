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


def test_get_does_not_evict_refreshing_placeholder():
    cache = GenrePoolCache()
    assert cache.mark_refreshing("horror", movie_profile=True) is True
    assert cache.get("horror", movie_profile=True) is None
    assert cache.is_refreshing("horror", movie_profile=True)
    assert cache.mark_refreshing("horror", movie_profile=True) is False


def test_expired_rows_still_served_while_refreshing():
    cache = GenrePoolCache()
    cache.put("horror", [{"name": "Film 2020 1080p", "site": "YTS"}], [])
    assert cache.mark_refreshing("horror")
    entry = cache.get("horror")
    assert entry is not None
    entry.created_at = time.time() - STALE_TTL_SECONDS - 1
    served = cache.get("horror")
    assert served is not None
    assert served.rows
    assert cache.is_refreshing("horror")
