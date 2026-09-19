from unittest.mock import MagicMock

from api.genre_pool_cache import GenrePoolCache
from api.genre_service import GenreBrowseService, _serve_cached_rows


def test_serve_cached_rows_applies_min_seeds():
    rows = [
        {"name": "Low 2020 1080p", "seeds": "2", "site": "YTS"},
        {"name": "High 2020 1080p", "seeds": "50", "site": "YTS"},
    ]
    filtered = _serve_cached_rows(rows, min_seeds=10, max_seeds=None, max_size=None, limit=None)
    assert len(filtered) == 1
    assert "High" in filtered[0]["name"]


def test_genre_cache_misses_on_movie_profile_mismatch():
    cache = GenrePoolCache()
    cache.put(
        "horror",
        [{"name": "Film 2020 1080p", "seeds": "10", "site": "YTS"}],
        [],
        movie_profile=True,
    )
    assert cache.get("horror", movie_profile=True) is not None
    assert cache.get("horror", movie_profile=False) is None


def test_browse_applies_filters_on_cache_hit():
    cache = GenrePoolCache()
    cache.put(
        "horror",
        [
            {"name": "Low 2020 1080p", "seeds": "2", "site": "YTS"},
            {"name": "High 2020 1080p", "seeds": "50", "site": "YTS"},
        ],
        [],
        movie_profile=True,
    )
    service = GenreBrowseService(MagicMock(), cache, lambda _: (MagicMock(configured=False), False))
    outcome = service.browse("horror", min_seeds=10, movie_profile=True)
    assert len(outcome.results) == 1
    assert "High" in outcome.results[0]["name"]
