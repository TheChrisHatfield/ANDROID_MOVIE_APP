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


def test_pool_rows_from_ranked_dedupes_live_discoveries():
    from metadata.genre_pool import pool_rows_from_ranked

    ranked = [
        {"name": "Alpha 2020 1080p", "seeds": "10", "site": "YTS", "detail_url": "a1"},
        {"name": "Alpha 2020 720p", "seeds": "5", "site": "1337x", "detail_url": "a2"},
        {"name": "Beta 2021 1080p", "seeds": "8", "site": "YTS", "detail_url": "b1"},
    ]
    pool = pool_rows_from_ranked(ranked)
    assert len(pool) == 3


@patch("api.genre_service._REFRESH_WAIT_SECONDS", 0.0)
@patch("api.genre_service.fetch_genre_pool_rows")
def test_browse_does_not_steal_in_flight_refresh(mock_fetch):
    cache = GenrePoolCache()
    assert cache.mark_refreshing("horror", movie_profile=True)
    service = GenreBrowseService(MagicMock(), cache, lambda _: (MagicMock(configured=False), False))
    outcome = service.browse("horror", movie_profile=True)
    mock_fetch.assert_not_called()
    assert outcome.results == []
    assert outcome.refresh_in_progress is True
    assert cache.is_refreshing("horror", movie_profile=True)


@patch("api.genre_service._REFRESH_WAIT_SECONDS", 0.3)
@patch("api.genre_service._REFRESH_POLL_SECONDS", 0.01)
@patch("api.genre_service.fetch_genre_pool_rows")
def test_force_refresh_waits_for_in_flight_instead_of_serving_stale(mock_fetch):
    import threading
    import time

    cache = GenrePoolCache()
    cache.put(
        "horror",
        [{"name": "Stale 2020 1080p", "seeds": "10", "site": "YTS"}],
        [],
        movie_profile=True,
    )
    assert cache.mark_refreshing("horror", movie_profile=True)

    def complete_in_flight() -> None:
        time.sleep(0.05)
        cache.put(
            "horror",
            [{"name": "Fresh 2021 1080p", "seeds": "50", "site": "YTS"}],
            [],
            movie_profile=True,
        )
        cache.clear_refreshing("horror", movie_profile=True)

    worker = threading.Thread(target=complete_in_flight)
    worker.start()
    service = GenreBrowseService(MagicMock(), cache, lambda _: (MagicMock(configured=False), False))
    outcome = service.browse("horror", movie_profile=True, force_refresh=True)
    worker.join()
    mock_fetch.assert_not_called()
    assert len(outcome.results) == 1
    assert "Fresh" in outcome.results[0]["name"]


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


@patch("api.genre_service.fetch_genre_pool_rows")
def test_browse_refetches_when_cached_pool_has_fewer_pages(mock_fetch):
    mock_fetch.return_value = SearchOutcome(
        results=[{"name": "Wide 2021 1080p", "seeds": "20", "site": "YTS"}],
        failed_sites=[],
    )
    cache = GenrePoolCache()
    cache.put(
        "horror",
        [{"name": "Narrow 2020 1080p", "seeds": "10", "site": "YTS"}],
        [],
        movie_profile=True,
        page_limit=2,
    )
    service = GenreBrowseService(MagicMock(), cache, lambda _: (MagicMock(configured=False), False))
    outcome = service.browse("horror", movie_profile=True, page_limit=10)
    mock_fetch.assert_called_once()
    assert mock_fetch.call_args.kwargs["page_limit"] == 10
    assert "Wide" in outcome.results[0]["name"]
    assert cache.get("horror", movie_profile=True).page_limit == 10


@patch("api.genre_service._REFRESH_WAIT_SECONDS", 0.3)
@patch("api.genre_service._REFRESH_POLL_SECONDS", 0.01)
@patch("api.genre_service.fetch_genre_pool_rows")
def test_browse_rebuilds_after_narrow_warm_refresh(mock_fetch):
    import threading
    import time

    mock_fetch.return_value = SearchOutcome(
        results=[{"name": "Wide 2021 1080p", "seeds": "20", "site": "YTS"}],
        failed_sites=[],
    )
    cache = GenrePoolCache()
    assert cache.mark_refreshing("horror", movie_profile=True)

    def complete_warm() -> None:
        time.sleep(0.05)
        cache.put(
            "horror",
            [{"name": "Warm 2020 1080p", "seeds": "10", "site": "YTS"}],
            [],
            movie_profile=True,
            page_limit=3,
        )
        cache.clear_refreshing("horror", movie_profile=True)

    worker = threading.Thread(target=complete_warm)
    worker.start()
    service = GenreBrowseService(MagicMock(), cache, lambda _: (MagicMock(configured=False), False))
    outcome = service.browse("horror", movie_profile=True, page_limit=10)
    worker.join()
    mock_fetch.assert_called_once()
    assert mock_fetch.call_args.kwargs["page_limit"] == 10
    assert "Wide" in outcome.results[0]["name"]
