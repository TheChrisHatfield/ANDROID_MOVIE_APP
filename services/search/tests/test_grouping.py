from metadata.grouping import build_movie_groups


def test_groups_duplicate_movies():
    rows = [
        {"id": "1", "name": "Inception 2010 1080p BluRay", "site": "YTS"},
        {"id": "2", "name": "Inception.2010.720p.WEB-DL", "site": "TPB"},
        {"id": "3", "name": "Interstellar 2014 1080p", "site": "YTS"},
    ]
    groups, ungrouped = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert len(groups) == 2
    inception = next(g for g in groups if g["title"] == "Inception")
    assert inception["release_count"] == 2
    interstellar = next(g for g in groups if g["title"] == "Interstellar")
    assert interstellar["release_count"] == 1
    assert ungrouped == []


def test_yearless_duplicates_group_by_title():
    rows = [
        {"id": "1", "name": "Avatar 1080p BluRay", "site": "YTS"},
        {"id": "2", "name": "Avatar 720p WEB-DL", "site": "TPB"},
    ]
    groups, ungrouped = build_movie_groups(rows, enrich_metadata=False)
    assert len(groups) == 1
    assert groups[0]["title"] == "Avatar"
    assert groups[0]["release_count"] == 2
    assert ungrouped == []


def test_single_release_becomes_group_card():
    rows = [{"id": "1", "name": "Obscure Film 2020 1080p", "site": "YTS"}]
    groups, ungrouped = build_movie_groups(rows, enrich_metadata=False)
    assert len(groups) == 1
    assert groups[0]["release_count"] == 1
    assert ungrouped == []


def test_max_groups_still_groups_overflow_without_tmdb():
    rows = [
        {"id": str(i), "name": f"Film {i} 2020 1080p", "site": "YTS"}
        for i in range(35)
    ]
    groups, ungrouped = build_movie_groups(rows, tmdb=None, enrich_metadata=False, max_groups=30)
    assert len(groups) == 35
    assert ungrouped == []
