from metadata.grouping import build_movie_groups


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
    groups, ungrouped = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert len(groups) == 1
    assert groups[0]["poster_url"] == "https://yts.rs/images/superman.jpg"
    assert groups[0]["overview"] == "Man of steel returns."
    assert groups[0]["trailer_youtube_key"] == "abc123"
