from metadata.genre_pool import _diversify_unique_movies


def test_diversify_caps_releases_per_movie():
    rows = [
        {"name": "Alpha 2020 1080p", "seeds": "10", "site": "YTS"},
        {"name": "Alpha 2020 720p", "seeds": "8", "site": "YTS"},
        {"name": "Alpha 2020 480p", "seeds": "5", "site": "YTS"},
        {"name": "Alpha 2020 WEB-DL", "seeds": "4", "site": "1337x"},
        {"name": "Alpha 2020 HDRip", "seeds": "3", "site": "1337x"},
        {"name": "Beta 2021 1080p", "seeds": "20", "site": "YTS"},
    ]
    out = _diversify_unique_movies(rows, max_per_movie=4)
    alpha = [r for r in out if r["name"].startswith("Alpha")]
    beta = [r for r in out if r["name"].startswith("Beta")]
    assert len(alpha) == 4
    assert len(beta) == 1
    assert len(out) == 5
