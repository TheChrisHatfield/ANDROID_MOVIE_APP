from metadata.grouping import build_movie_groups


def test_groups_use_mct_branch_key_when_present():
    rows = [
        {"name": "Insidious 3 2015 720p", "site": "1337x", "_branch_key": "insidious|2015"},
        {"name": "Insidious Chapter 3 2015 1080p", "site": "YTS", "_branch_key": "insidious|2015"},
    ]
    groups, ungrouped, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert len(groups) == 1
    assert groups[0]["group_key"] == "insidious|2015"
    assert len(groups[0]["releases"]) == 2
    assert not ungrouped


def test_groups_sort_by_genre_rank_when_present():
    rows = [
        {"name": "Beta 2020 1080p", "seeds": "99", "size": "2 GB", "site": "YTS", "_genre_rank": 1},
        {"name": "Alpha 2020 1080p", "seeds": "5", "size": "2 GB", "site": "YTS", "_genre_rank": 0},
    ]
    groups, _, _ = build_movie_groups(rows, tmdb=None, enrich_metadata=False)
    assert groups[0]["title"] == "Alpha"
    assert groups[1]["title"] == "Beta"
