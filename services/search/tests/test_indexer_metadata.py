from metadata.grouping import _apply_tmdb, build_movie_groups
from metadata.tmdb_client import TmdbClient, TmdbMovieInfo


def test_group_uses_yts_poster_without_tmdb(monkeypatch):
    monkeypatch.setattr("metadata.grouping.fill_missing_posters", lambda buckets, **kwargs: 0)
    monkeypatch.setattr("metadata.grouping.fill_missing_trailers", lambda buckets, **kwargs: 0)
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
    groups, ungrouped, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=True)
    assert len(groups) == 1
    assert groups[0]["poster_url"] == "https://yts.rs/images/superman.jpg"
    assert groups[0]["overview"] == "Man of steel returns."
    assert groups[0]["trailer_youtube_key"] is None


def test_indexer_skips_invalid_trailer_and_keeps_later_valid_id(monkeypatch):
    monkeypatch.setattr("metadata.grouping.fill_missing_posters", lambda buckets, **kwargs: 0)
    monkeypatch.setattr("metadata.grouping.fill_missing_trailers", lambda buckets, **kwargs: 0)
    rows = [
        {
            "id": "1",
            "name": "Inception 2010 1080p",
            "site": "YTS",
            "poster_url": "https://yts.rs/images/inception.jpg",
            "overview": "Dream heist.",
            "trailer_youtube_key": "abc123",
        },
        {
            "id": "2",
            "name": "Inception 2010 720p",
            "site": "1337x",
            "trailer_youtube_key": "dQw4w9WgXcQ",
        },
    ]
    groups, _, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=True)
    assert groups[0]["trailer_youtube_key"] == "dQw4w9WgXcQ"


def test_indexer_keeps_valid_youtube_id(monkeypatch):
    monkeypatch.setattr("metadata.grouping.fill_missing_posters", lambda buckets, **kwargs: 0)
    monkeypatch.setattr("metadata.grouping.fill_missing_trailers", lambda buckets, **kwargs: 0)
    rows = [
        {
            "id": "1",
            "name": "Dune 2021 1080p",
            "site": "YTS",
            "poster_url": "https://yts.rs/images/dune.jpg",
            "trailer_youtube_key": "dQw4w9WgXcQ",
        },
    ]
    groups, _, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=True)
    assert groups[0]["trailer_youtube_key"] == "dQw4w9WgXcQ"


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
    groups, ungrouped, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert groups[0]["poster_url"] == "https://yts.rs/images/dune.jpg"
    assert groups[0]["trailer_youtube_key"] is None


def test_yts_year_from_date_groups_single_card():
    rows = [
        {"id": "1", "name": "Superman [1080p]", "site": "YTS", "date": "2025"},
        {"id": "2", "name": "Superman [720p]", "site": "YTS", "date": "2025"},
    ]
    groups, ungrouped, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
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


def test_tmdb_ignores_invalid_trailer_and_keeps_indexer_id(monkeypatch):
    bucket = {
        "title": "Inception",
        "year": 2010,
        "overview": "YTS plot",
        "poster_url": "https://yts.rs/poster.jpg",
        "trailer_youtube_key": "dQw4w9WgXcQ",
    }
    tmdb = TmdbClient(api_key="test-key")

    def fake_lookup(title, year=None):
        return TmdbMovieInfo(
            title="Inception",
            year=2010,
            overview="TMDB plot",
            poster_url=None,
            trailer_youtube_key="not-a-vid",
        )

    monkeypatch.setattr(tmdb, "lookup", fake_lookup)
    _apply_tmdb(bucket, tmdb)
    assert bucket["trailer_youtube_key"] == "dQw4w9WgXcQ"
