from unittest.mock import MagicMock, patch

from api.genre_pool_cache import GenrePoolCache
from api.genre_service import GenreBrowseService, _serve_cached_rows
from torrtux_core.searcher import SearchOutcome


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


@patch("api.genre_service.fetch_genre_pool_rows")
def test_browse_serves_partial_cache_entry(mock_fetch):
    cache = GenrePoolCache()
    cache.put(
        "horror",
        [{"name": "Partial 2020 1080p", "seeds": "10", "site": "YTS"}],
        [],
        partial=True,
        movie_profile=True,
    )
    service = GenreBrowseService(MagicMock(), cache, lambda _: (MagicMock(configured=False), False))
    outcome = service.browse("horror", movie_profile=True)
    mock_fetch.assert_not_called()
    assert outcome.results


def test_cache_put_never_downgrades_full_to_partial():
    cache = GenrePoolCache()
    cache.put(
        "horror",
        [{"name": "Full 2020 1080p", "seeds": "10", "site": "YTS"}],
        [],
        partial=False,
        movie_profile=True,
    )
    cache.put(
        "horror",
        [{"name": "Smaller 2020 1080p", "seeds": "5", "site": "YTS"}],
        [],
        partial=True,
        movie_profile=True,
    )
    entry = cache.get("horror", movie_profile=True)
    assert entry is not None
    assert not entry.partial


@patch("api.genre_service.rank_genre_pool_rows")
def test_browse_reranks_on_cache_hit(mock_rank):
    mock_rank.return_value = [
        {"name": "High 2020 1080p", "seeds": "50", "site": "YTS"},
        {"name": "Low 2020 1080p", "seeds": "2", "site": "YTS"},
    ]
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
    service.browse("horror", movie_profile=True)
    mock_rank.assert_called_once()


@patch("api.genre_service.fetch_genre_pool_rows")
def test_force_refresh_empty_invalidates_cache(mock_fetch):
    mock_fetch.return_value = SearchOutcome([], [])
    cache = GenrePoolCache()
    cache.put(
        "horror",
        [{"name": "Stale 2020 1080p", "seeds": "10", "site": "YTS"}],
        [],
        movie_profile=True,
    )
    service = GenreBrowseService(MagicMock(), cache, lambda _: (MagicMock(configured=False), False))
    service.browse("horror", movie_profile=True, force_refresh=True)
    assert cache.get("horror", movie_profile=True) is None


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
