from metadata.genre_tree import (
    build_genre_branches,
    rank_pool_thompson,
    run_mct_simulations,
)


def test_merge_similar_title_branches():
    rows = [
        {"name": "Ghostbusters Afterlife 2021 1080p", "seeds": "10", "site": "YTS"},
        {"name": "Ghostbusters Afterlife 2021 720p", "seeds": "5", "site": "1337x"},
        {"name": "Inception 2010 1080p", "seeds": "99", "site": "YTS"},
    ]
    branches = build_genre_branches(rows)
    assert len(branches) == 2
    ghost = next(b for b in branches if "Ghostbusters" in b.title)
    assert len(ghost.releases) == 2


def test_thompson_rank_preserves_branch_grouping():
    rows = [
        {"name": "Alpha 2020 1080p", "seeds": "5", "site": "YTS", "_genre_rank": 0},
        {"name": "Beta 2020 1080p", "seeds": "200", "site": "YTS", "_genre_rank": 1},
    ]
    ranked = rank_pool_thompson(rows, genre_id="action")
    assert all("_branch_key" in row for row in ranked)
    assert ranked[0]["name"].startswith("Alpha") or ranked[0]["name"].startswith("Beta")


def test_mct_records_visits_and_derives_priors():
    rows = [
        {"name": "Horror Alpha 2020 1080p", "seeds": "80", "site": "YTS", "_genre_rank": 0},
        {"name": "Horror Beta 2020 1080p", "seeds": "2", "site": "YTS", "_genre_rank": 5},
        {"name": "Horror Gamma 2021 1080p", "seeds": "10", "site": "YTS", "_genre_rank": 2},
    ]
    branches = build_genre_branches(rows, auto_merge=False)
    run_mct_simulations(branches, genre_id="horror", iterations=30)
    visited = [b for b in branches if b.visits > 0]
    assert visited
    assert any(b.alpha > 1.0 and b.beta >= 1.0 for b in visited)


def test_mct_expansion_merges_related_titles():
    rows = [
        {"name": "Insidious Chapter 3 2015 1080p", "seeds": "20", "site": "YTS", "_genre_rank": 1},
        {"name": "Insidious 3 2015 720p", "seeds": "15", "site": "1337x", "_genre_rank": 2},
        {"name": "Comedy Unrelated 2020 1080p", "seeds": "5", "site": "YTS", "_genre_rank": 9},
    ]
    branches = build_genre_branches(rows, auto_merge=False)
    initial_count = len(branches)
    run_mct_simulations(branches, genre_id="horror", iterations=50)
    assert len(branches) < initial_count
    insidious = next(b for b in branches if "Insidious" in b.title)
    assert len(insidious.releases) >= 2


def test_mct_metadata_on_ranked_rows():
    rows = [
        {"name": "Action Hero 2020 1080p", "seeds": "50", "site": "YTS", "_genre_rank": 0},
        {"name": "Action Sidekick 2021 1080p", "seeds": "10", "site": "YTS", "_genre_rank": 3},
    ]
    ranked = rank_pool_thompson(rows, genre_id="action", mct_iterations=20)
    assert ranked[0].get("_mct_visits", 0) > 0
    assert "_mct_mean_reward" in ranked[0]
