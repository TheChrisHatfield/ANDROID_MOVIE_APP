from unittest.mock import MagicMock, patch

from metadata.genre_browse import curated_genre_search, keyword_genre_search
from metadata.tmdb_client import TmdbDiscoverMovie
from torrtux_core.searcher import SearchOutcome, TorrentSearcher


def _movie(title: str, rank: int = 0) -> TmdbDiscoverMovie:
    return TmdbDiscoverMovie(
        tmdb_id=100 + rank,
        title=title,
        year=2020,
        overview=f"{title} overview",
        poster_url=f"https://image.tmdb.org/t/p/w342/{title.lower()}.jpg",
        popularity=100.0 - rank,
    )


@patch.object(TorrentSearcher, "search")
def test_curated_genre_search_orders_by_discover_rank(mock_search):
    searcher = TorrentSearcher(site_classes=[])
    tmdb = MagicMock()
    tmdb.configured = True
    tmdb.discover_movies.return_value = [_movie("Alpha"), _movie("Beta", 1)]

    def side_effect(query, **kwargs):
        if query == "Alpha":
            return SearchOutcome(
                [{"name": "Alpha 2020 1080p", "seeds": "5", "size": "2 GB", "site": "YTS"}],
                [],
            )
        return SearchOutcome(
            [{"name": "Beta 2020 1080p", "seeds": "99", "size": "2 GB", "site": "YTS"}],
            [],
        )

    mock_search.side_effect = side_effect
    outcome = curated_genre_search(searcher, "horror", tmdb, limit=10)
    assert outcome is not None
    assert outcome.results[0]["name"].startswith("Alpha")
    assert outcome.results[0].get("poster_url")
    assert outcome.results[1]["name"].startswith("Beta")


@patch.object(TorrentSearcher, "search")
def test_curated_returns_none_when_discover_empty(mock_search):
    searcher = TorrentSearcher(site_classes=[])
    tmdb = MagicMock()
    tmdb.configured = True
    tmdb.discover_movies.return_value = []
    assert curated_genre_search(searcher, "horror", tmdb) is None
    mock_search.assert_not_called()


@patch.object(TorrentSearcher, "search")
def test_keyword_genre_search_uses_genre_term(mock_search):
    searcher = TorrentSearcher(site_classes=[])
    mock_search.return_value = SearchOutcome([], [])
    keyword_genre_search(searcher, "sci-fi", limit=20)
    mock_search.assert_called_once()
    assert mock_search.call_args.args[0] == "science fiction"


@patch.object(TorrentSearcher, "search")
def test_browse_genre_uses_curated_when_tmdb_available(mock_search):
    from api.main import _searcher

    tmdb = MagicMock()
    tmdb.configured = True
    tmdb.discover_movies.return_value = [_movie("Curated Film")]
    mock_search.return_value = SearchOutcome(
        [{"name": "Curated Film 2024 1080p", "seeds": "10", "size": "1 GB", "site": "YTS"}],
        [],
    )
    with patch.object(_searcher, "working_sites", [MagicMock(name="YTS")]):
        outcome = _searcher.browse_genre("horror", tmdb=tmdb, limit=10)
    assert any("Curated Film" in row["name"] for row in outcome.results)
