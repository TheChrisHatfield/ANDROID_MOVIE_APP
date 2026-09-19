"""MCT genre branching + Thompson sampling ranker for torrent pools."""
from __future__ import annotations

import json
import logging
import math
import random
import re
import threading
from dataclasses import dataclass, field
from pathlib import Path

from metadata.title_parse import group_key_for, parse_torrent_movie_title
from torrtux_core.filters import seed_count
from torrtux_core.genres import genre_search_query

_TOKEN_RE = re.compile(r"[a-z0-9]{3,}")

# UCB exploration constant (√2 is standard for bounded rewards in [0, 1]).
_MCT_EXPLORATION_C = math.sqrt(2.0)
# Default simulation count — cheap on typical pool sizes (~50 branches).
_DEFAULT_MCT_ITERATIONS = 40
# Live indexer searches during MCT expansion (pulls unique titles from the pool).
_DEFAULT_LIVE_EXPAND_BUDGET = 8
_LIVE_SEARCH_RELEASE_LIMIT = 4
# Expansion merges related title branches above this Jaccard token overlap.
_MCT_EXPAND_THRESHOLD = 0.35
# Pre-MCT merge for near-duplicate parsed titles.
_PRE_MERGE_THRESHOLD = 0.55


def _title_tokens(title: str) -> set[str]:
    return set(_TOKEN_RE.findall(title.lower()))


def _token_similarity(a: str, b: str) -> float:
    ta, tb = _title_tokens(a), _title_tokens(b)
    if not ta or not tb:
        return 0.0
    return len(ta & tb) / len(ta | tb)


@dataclass
class GenreBranch:
    """MCT branch node: one movie cluster under a genre root."""
    group_key: str
    title: str
    year: int | None
    releases: list[dict] = field(default_factory=list)
    alpha: float = 1.0
    beta: float = 1.0
    genre_rank: int = 9999
    visits: int = 0
    value_sum: float = 0.0
    merged_keys: set[str] = field(default_factory=set)
    live_expanded: bool = False

    def avg_seeds(self) -> float:
        values = [seed_count(row.get("seeds", "-")) or 0 for row in self.releases]
        return sum(values) / len(values) if values else 0.0

    def mean_reward(self) -> float:
        if self.visits == 0:
            return 0.0
        return self.value_sum / self.visits

    def thompson_sample(self) -> float:
        return random.betavariate(max(self.alpha, 0.1), max(self.beta, 0.1))


logger = logging.getLogger(__name__)
_FEEDBACK_LOCK = threading.Lock()
_FEEDBACK_PATH = Path(__file__).resolve().parents[1] / "data" / "genre_branch_feedback.json"
_branch_feedback: dict[str, dict[str, tuple[float, float]]] = {}


def _load_persisted_feedback() -> None:
    if not _FEEDBACK_PATH.exists():
        return
    try:
        raw = json.loads(_FEEDBACK_PATH.read_text(encoding="utf-8"))
        for genre_id, branches in raw.items():
            bucket: dict[str, tuple[float, float]] = {}
            for key, pair in branches.items():
                if isinstance(pair, (list, tuple)) and len(pair) == 2:
                    bucket[str(key)] = (float(pair[0]), float(pair[1]))
            if bucket:
                _branch_feedback[genre_id.strip().lower()] = bucket
    except Exception as exc:
        logger.warning("genre feedback load failed: %s", exc)


def _persist_feedback() -> None:
    try:
        _FEEDBACK_PATH.parent.mkdir(parents=True, exist_ok=True)
        serializable = {
            genre_id: {key: [alpha, beta] for key, (alpha, beta) in branches.items()}
            for genre_id, branches in _branch_feedback.items()
        }
        _FEEDBACK_PATH.write_text(
            json.dumps(serializable, indent=2, sort_keys=True),
            encoding="utf-8",
        )
    except Exception as exc:
        logger.warning("genre feedback save failed: %s", exc)


def get_branch_feedback(genre_id: str) -> dict[str, tuple[float, float]]:
    return dict(_branch_feedback.get(genre_id.strip().lower(), {}))


def record_genre_branch_feedback(genre_id: str, group_key: str, success: bool) -> None:
    with _FEEDBACK_LOCK:
        bucket = _branch_feedback.setdefault(genre_id.strip().lower(), {})
        record_branch_feedback(bucket, group_key.strip().lower(), success)
        _persist_feedback()


_load_persisted_feedback()


def build_genre_branches(
    rows: list[dict],
    feedback: dict[str, tuple[float, float]] | None = None,
    *,
    auto_merge: bool = True,
) -> list[GenreBranch]:
    """Build branch nodes from pooled torrent rows (genre = implicit root)."""
    buckets: dict[str, GenreBranch] = {}
    order: list[str] = []

    for row in rows:
        title, year = parse_torrent_movie_title(str(row.get("name") or ""))
        if not title.strip():
            continue
        key = group_key_for(title, year)
        branch = buckets.get(key)
        if branch is None:
            rank = int(row.get("_genre_rank", 9999))
            seeds = seed_count(row.get("seeds", "-")) or 0
            branch = GenreBranch(
                group_key=key,
                title=title,
                year=year,
                genre_rank=rank,
                alpha=1.0 + min(seeds / 50.0, 5.0),
                beta=1.0,
            )
            if feedback and key in feedback:
                branch.alpha, branch.beta = feedback[key]
            buckets[key] = branch
            order.append(key)
        else:
            branch.genre_rank = min(branch.genre_rank, int(row.get("_genre_rank", 9999)))
            if not feedback or key not in feedback:
                seeds = seed_count(row.get("seeds", "-")) or 0
                branch.alpha = max(branch.alpha, 1.0 + min(seeds / 50.0, 5.0))
        branch.releases.append(row)

    branches = [buckets[key] for key in order if key in buckets]
    if auto_merge:
        _merge_similar_branches(branches, threshold=_PRE_MERGE_THRESHOLD)
    if feedback:
        _apply_feedback_aliases(branches, feedback)
    return branches


def _apply_feedback_aliases(
    branches: list[GenreBranch],
    feedback: dict[str, tuple[float, float]],
) -> None:
    """Apply feedback recorded under merged alias keys to surviving branches."""
    for branch in branches:
        for alias, (alpha, beta) in feedback.items():
            if alias == branch.group_key or alias in branch.merged_keys:
                branch.alpha, branch.beta = alpha, beta


def _merge_similar_branches(branches: list[GenreBranch], threshold: float = 0.55) -> None:
    """Merge near-duplicate title branches before MCT (same parsed-title variants)."""
    merged = True
    while merged:
        merged = False
        for i, left in enumerate(branches):
            if left not in branches:
                continue
            for j in range(i + 1, len(branches)):
                right = branches[j]
                if right not in branches:
                    continue
                sim = _token_similarity(left.title, right.title)
                if sim < threshold:
                    continue
                if len(left.releases) >= len(right.releases):
                    winner, loser = left, right
                else:
                    winner, loser = right, left
                winner.releases.extend(loser.releases)
                winner.genre_rank = min(winner.genre_rank, loser.genre_rank)
                winner.alpha = max(winner.alpha, loser.alpha)
                winner.merged_keys.add(loser.group_key)
                winner.merged_keys.update(loser.merged_keys)
                branches.remove(loser)
                merged = True
                break
            if merged:
                break


def _rollout_reward(branch: GenreBranch, genre_id: str) -> float:
    """Cheap in-memory rollout: seed quality + TMDB rank + release depth + genre alignment."""
    seeds_score = min(branch.avg_seeds() / 100.0, 1.0)
    rank_score = 1.0 / (1.0 + branch.genre_rank / 10.0)
    release_score = min(len(branch.releases) / 6.0, 1.0)

    genre_query = genre_search_query(genre_id) or genre_id
    genre_tokens = _title_tokens(genre_query)
    title_tokens = _title_tokens(branch.title)
    if genre_tokens:
        genre_align = len(genre_tokens & title_tokens) / len(genre_tokens)
    else:
        genre_align = 0.5

    reward = (
        0.35 * seeds_score
        + 0.35 * rank_score
        + 0.15 * release_score
        + 0.15 * genre_align
    )
    return max(0.0, min(1.0, reward))


def _select_branch_ucb(
    branches: list[GenreBranch],
    root_visits: int,
    exploration_c: float,
) -> GenreBranch:
    """UCB1 selection at genre root over branch children."""
    unvisited = [branch for branch in branches if branch.visits == 0]
    if unvisited:
        return unvisited[0]

    best: GenreBranch | None = None
    best_score = -1.0
    log_root = math.log(root_visits + 1)
    for branch in branches:
        exploit = branch.value_sum / branch.visits
        explore = exploration_c * math.sqrt(log_root / branch.visits)
        score = exploit + explore
        if score > best_score:
            best_score = score
            best = branch
    return best or branches[0]


def _release_key(row: dict) -> str:
    detail = str(row.get("detail_url") or "").strip()
    if detail:
        return detail
    return str(row.get("name") or "").strip().lower()


def _expand_branch_live(
    selected: GenreBranch,
    branches: list[GenreBranch],
    searcher,
    *,
    sites: list | None,
    movie_profile: bool,
    threshold: float = _MCT_EXPAND_THRESHOLD,
) -> bool:
    """
    MCT expansion via live indexer search on the selected branch title.
    Merges matching releases and can introduce new unique title branches.
    """
    if selected.live_expanded:
        return False
    selected.live_expanded = True
    try:
        outcome = searcher.search(
            selected.title,
            sites=sites,
            movie_profile=movie_profile,
            page_limit=1,
            parallel=True,
            limit=_LIVE_SEARCH_RELEASE_LIMIT,
        )
    except Exception:
        return False

    added = False
    for row in outcome.results:
        title, year = parse_torrent_movie_title(str(row.get("name") or ""))
        if not title.strip():
            continue
        key = group_key_for(title, year)
        row_key = _release_key(row)
        if key == selected.group_key or _token_similarity(title, selected.title) >= threshold:
            if not any(_release_key(existing) == row_key for existing in selected.releases):
                selected.releases.append(row)
                added = True
            continue

        existing = next((branch for branch in branches if branch.group_key == key), None)
        if existing is not None:
            if not any(_release_key(r) == row_key for r in existing.releases):
                existing.releases.append(row)
                added = True
            continue

        seeds = seed_count(row.get("seeds", "-")) or 0
        new_branch = GenreBranch(
            group_key=key,
            title=title,
            year=year,
            releases=[row],
            genre_rank=int(row.get("_genre_rank", 9999)),
            alpha=1.0 + min(seeds / 50.0, 5.0),
        )
        branches.append(new_branch)
        added = True
    return added


def _expand_branch(
    selected: GenreBranch,
    branches: list[GenreBranch],
    threshold: float = _MCT_EXPAND_THRESHOLD,
) -> bool:
    """
    MCT expansion: merge the most similar unmerged sibling branch into selected.
    Derives related-title clusters without extra indexer calls.
    """
    best_candidate: GenreBranch | None = None
    best_sim = threshold
    for other in branches:
        if other is selected:
            continue
        if other.group_key in selected.merged_keys:
            continue
        sim = _token_similarity(selected.title, other.title)
        if sim >= best_sim:
            best_sim = sim
            best_candidate = other
    if best_candidate is None:
        return False

    selected.releases.extend(best_candidate.releases)
    selected.genre_rank = min(selected.genre_rank, best_candidate.genre_rank)
    selected.alpha = max(selected.alpha, best_candidate.alpha)
    selected.merged_keys.add(best_candidate.group_key)
    selected.merged_keys.update(best_candidate.merged_keys)
    branches.remove(best_candidate)
    return True


def _backpropagate(branch: GenreBranch, reward: float, root_visits: int) -> int:
    """Update branch statistics and root visit count."""
    branch.visits += 1
    branch.value_sum += reward
    return root_visits + 1


def _apply_mct_thompson_priors(branch: GenreBranch) -> None:
    """
    Convert MCT rollout evidence into Beta(alpha, beta) priors for Thompson sampling.
    Blends seed-based initial alpha with simulation outcomes.
    """
    if branch.visits == 0:
        return
    mean = branch.mean_reward()
    mct_alpha = 1.0 + mean * branch.visits
    mct_beta = 1.0 + (1.0 - mean) * branch.visits
    branch.alpha = max(branch.alpha, mct_alpha)
    branch.beta = max(branch.beta, mct_beta)


def run_mct_simulations(
    branches: list[GenreBranch],
    *,
    genre_id: str,
    iterations: int = _DEFAULT_MCT_ITERATIONS,
    exploration_c: float = _MCT_EXPLORATION_C,
    searcher=None,
    sites: list | None = None,
    movie_profile: bool = True,
    mct_live: bool = False,
    live_expand_budget: int = _DEFAULT_LIVE_EXPAND_BUDGET,
) -> list[GenreBranch]:
    """
    Full MCT loop: select (UCB) → expand (live indexer or in-memory merge) → rollout → backpropagate.
    After simulations, derive Thompson priors from visit/reward stats on each branch.
    """
    if len(branches) < 2 or iterations <= 0:
        for branch in branches:
            _apply_mct_thompson_priors(branch)
        return branches

    root_visits = sum(branch.visits for branch in branches)
    live_expands = 0
    for _ in range(iterations):
        selected = _select_branch_ucb(branches, root_visits, exploration_c)
        expanded = False
        if (
            mct_live
            and searcher is not None
            and live_expands < live_expand_budget
            and selected.visits <= 2
        ):
            expanded = _expand_branch_live(
                selected,
                branches,
                searcher,
                sites=sites,
                movie_profile=movie_profile,
            )
            if expanded:
                live_expands += 1
        if not expanded:
            _expand_branch(selected, branches)
        reward = _rollout_reward(selected, genre_id)
        root_visits = _backpropagate(selected, reward, root_visits)

    for branch in branches:
        _apply_mct_thompson_priors(branch)
    return branches


def rank_pool_thompson(
    rows: list[dict],
    feedback: dict[str, tuple[float, float]] | None = None,
    *,
    genre_id: str | None = None,
    mct_iterations: int = _DEFAULT_MCT_ITERATIONS,
    searcher=None,
    sites: list | None = None,
    movie_profile: bool = True,
    mct_live: bool = False,
    live_expand_budget: int = _DEFAULT_LIVE_EXPAND_BUDGET,
) -> list[dict]:
    """
    MCT branching → Thompson-bandit ordering.
    Genre root → UCB/expand/rollout/backprop → Beta priors → Thompson sample × popularity.
    Each call re-samples Thompson draws (non-deterministic shelf order).
    """
    if not rows:
        return rows
    branches = build_genre_branches(rows, feedback=feedback)
    if not branches:
        return rows

    if genre_id:
        run_mct_simulations(
            branches,
            genre_id=genre_id,
            iterations=mct_iterations,
            searcher=searcher,
            sites=sites,
            movie_profile=movie_profile,
            mct_live=mct_live,
            live_expand_budget=live_expand_budget,
        )

    scored: list[tuple[float, int, GenreBranch]] = []
    for branch in branches:
        prior = 1.0 / (1.0 + branch.genre_rank)
        sample = branch.thompson_sample() * (0.5 + prior) * (1.0 + branch.avg_seeds() / 100.0)
        scored.append((sample, branch.genre_rank, branch))
    scored.sort(key=lambda item: (-item[0], item[1]))

    ranked: list[dict] = []
    for _, _, branch in scored:
        branch.releases.sort(
            key=lambda row: -(seed_count(row.get("seeds", "-")) or 0),
        )
        for idx, row in enumerate(branch.releases):
            row["_branch_key"] = branch.group_key
            row["_branch_order"] = len(ranked) + idx
            row["_mct_visits"] = branch.visits
            row["_mct_mean_reward"] = round(branch.mean_reward(), 4)
        ranked.extend(branch.releases)
    return ranked


def record_branch_feedback(branches: dict[str, tuple[float, float]], group_key: str, success: bool) -> None:
    """Update Thompson prior for a branch (click/send feedback from client)."""
    alpha, beta = branches.get(group_key, (1.0, 1.0))
    if success:
        branches[group_key] = (alpha + 1.0, beta)
    else:
        branches[group_key] = (alpha, beta + 1.0)

