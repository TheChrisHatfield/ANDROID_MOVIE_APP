from unittest.mock import MagicMock

import metadata.genre_tree as genre_tree
from metadata.genre_tree import (
    build_genre_branches,
    get_branch_feedback,
    rank_pool_thompson,
    record_genre_branch_feedback,
    run_mct_simulations,
)
from torrtux_core.searcher import SearchOutcome


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


def test_mct_live_expansion_adds_indexer_rows():
    rows = [
        {"name": "Horror Alpha 2020 1080p", "seeds": "80", "site": "YTS", "_genre_rank": 0},
        {"name": "Horror Beta 2020 1080p", "seeds": "2", "site": "YTS", "_genre_rank": 5},
    ]
    branches = build_genre_branches(rows, auto_merge=False)
    searcher = MagicMock()
    searcher.search.return_value = SearchOutcome(
        [
            {"name": "Horror Alpha 2020 720p", "seeds": "40", "site": "1337x", "detail_url": "a2"},
            {"name": "Horror New Title 2021 1080p", "seeds": "30", "site": "YTS", "detail_url": "n1"},
        ],
        [],
    )
    run_mct_simulations(
        branches,
        genre_id="horror",
        iterations=20,
        searcher=searcher,
        mct_live=True,
        live_expand_budget=4,
    )
    alpha = next(b for b in branches if "Alpha" in b.title)
    assert len(alpha.releases) >= 2
    assert any("New Title" in b.title for b in branches)
    assert searcher.search.called


def test_thompson_rank_is_non_deterministic():
    titles = [
        "Nebula Drift", "Iron Harbor", "Silent Reef", "Crimson Vale",
        "Glass Orchard", "Winter Signal", "Copper Atlas", "Velvet Circuit",
        "Ash Meridian", "Pale Horizon", "Rogue Lantern", "Static Garden",
    ]
    rows = [
        {"name": f"{title} 2020 1080p", "seeds": "25", "site": "YTS", "_genre_rank": 3}
        for title in titles
    ]
    orders = set()
    for _ in range(12):
        ranked = rank_pool_thompson(rows, genre_id="action", mct_iterations=5)
        first = ranked[0]["name"].split(" 2020")[0]
        orders.add(first)
    assert len(orders) > 1


def test_feedback_persists_to_disk(tmp_path, monkeypatch):
    feedback_file = tmp_path / "genre_branch_feedback.json"
    monkeypatch.setattr(genre_tree, "_FEEDBACK_PATH", feedback_file)
    monkeypatch.setattr(genre_tree, "_branch_feedback", {})
    record_genre_branch_feedback("horror", "alpha-2020", True)
    assert feedback_file.exists()
    monkeypatch.setattr(genre_tree, "_branch_feedback", {})
    genre_tree._load_persisted_feedback()
    assert get_branch_feedback("horror")["alpha-2020"][0] > 1.0


def test_mct_metadata_on_ranked_rows():
    rows = [
        {"name": "Action Hero 2020 1080p", "seeds": "50", "site": "YTS", "_genre_rank": 0},
        {"name": "Action Sidekick 2021 1080p", "seeds": "10", "site": "YTS", "_genre_rank": 3},
    ]
    ranked = rank_pool_thompson(rows, genre_id="action", mct_iterations=20)
    assert ranked[0].get("_mct_visits", 0) > 0
    assert "_mct_mean_reward" in ranked[0]

