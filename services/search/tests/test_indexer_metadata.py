from metadata.grouping import _apply_tmdb, build_movie_groups
from metadata.tmdb_client import TmdbClient, TmdbMovieInfo


def test_group_uses_yts_poster_without_tmdb():
    rows = [
        {
            "id": "1",
            "name": "Superman 2025 1080p",
            "site": "YTS",
            "poster_url": "https://yts.rs/images/superman.jpg",
            "overview": "Man of steel returns.",
            "trailer_youtube_key": "abc123",
        },
        {
            "id": "2",
            "name": "Superman 2025 720p",
            "site": "YTS",
            "poster_url": "https://yts.rs/images/superman.jpg",
        },
    ]
    groups, ungrouped = build_movie_groups(rows, tmdb=None, enrich_metadata=True)
    assert len(groups) == 1
    assert groups[0]["poster_url"] == "https://yts.rs/images/superman.jpg"
    assert groups[0]["overview"] == "Man of steel returns."
    assert groups[0]["trailer_youtube_key"] == "abc123"


def test_indexer_metadata_applied_when_enrich_disabled():
    rows = [
        {
            "id": "1",
            "name": "Dune 2021 1080p",
            "site": "YTS",
            "poster_url": "https://yts.rs/images/dune.jpg",
            "trailer_youtube_key": "dune-trailer",
        },
    ]
    groups, ungrouped = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert groups[0]["poster_url"] == "https://yts.rs/images/dune.jpg"
    assert groups[0]["trailer_youtube_key"] == "dune-trailer"


def test_yts_year_from_date_groups_single_card():
    rows = [
        {"id": "1", "name": "Superman [1080p]", "site": "YTS", "date": "2025"},
        {"id": "2", "name": "Superman [720p]", "site": "YTS", "date": "2025"},
    ]
    groups, ungrouped = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert len(groups) == 1
    assert groups[0]["year"] == 2025
    assert groups[0]["release_count"] == 2


def test_tmdb_does_not_wipe_yts_poster(monkeypatch):
    bucket = {
        "title": "Inception",
        "year": 2010,
        "overview": "YTS plot",
        "poster_url": "https://yts.rs/poster.jpg",
        "trailer_youtube_key": "yts123",
    }
    tmdb = TmdbClient(api_key="test-key")

    def fake_lookup(title, year=None):
        return TmdbMovieInfo(
            title="Inception",
            year=2010,
            overview="TMDB plot",
            poster_url=None,
            trailer_youtube_key=None,
        )

    monkeypatch.setattr(tmdb, "lookup", fake_lookup)
    _apply_tmdb(bucket, tmdb)
    assert bucket["poster_url"] == "https://yts.rs/poster.jpg"
    assert bucket["overview"] == "TMDB plot"
    assert bucket["trailer_youtube_key"] == "yts123"
