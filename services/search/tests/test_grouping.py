from metadata.grouping import build_movie_groups


def test_groups_duplicate_movies():
    rows = [
        {"id": "1", "name": "Inception 2010 1080p BluRay", "site": "YTS"},
        {"id": "2", "name": "Inception.2010.720p.WEB-DL", "site": "TPB"},
        {"id": "3", "name": "Interstellar 2014 1080p", "site": "YTS"},
    ]
    groups, ungrouped = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert len(groups) == 1
    assert groups[0]["title"] == "Inception"
    assert groups[0]["release_count"] == 2
    assert len(ungrouped) == 1
    assert ungrouped[0]["name"].startswith("Interstellar")


def test_yearless_duplicates_stay_ungrouped():
    rows = [
        {"id": "1", "name": "Avatar 1080p BluRay", "site": "YTS"},
        {"id": "2", "name": "Avatar 720p WEB-DL", "site": "TPB"},
    ]
    groups, ungrouped = build_movie_groups(rows, enrich_metadata=False)
    assert groups == []
    assert len(ungrouped) == 2


def test_single_release_stays_ungrouped():
    rows = [{"id": "1", "name": "Obscure Film 2020 1080p", "site": "YTS"}]
    groups, ungrouped = build_movie_groups(rows, enrich_metadata=False)
    assert groups == []
    assert len(ungrouped) == 1
