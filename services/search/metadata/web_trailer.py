"""Ad-hoc YouTube trailers when TMDB and indexer rows have none.

Query Wikipedia/Wikidata (and optional YouTube Data API / Google CSE)
using torrent title, year, overview, date, and release names. Rank hits
that look like official trailers. Do not scrape YouTube or Google HTML.
"""
from __future__ import annotations

import hashlib
import logging
import os
import re
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from urllib.parse import parse_qs, urlparse

import requests

from metadata.web_poster import coerce_year, overview_hint_tokens

logger = logging.getLogger(__name__)

_MAX_PER_REQUEST = 8
_MAX_CANDIDATES = 8
_MIN_SCORE = 5.0
_FETCH_TIMEOUT = 8
_FILL_BUDGET_SEC = 6.0
_MISS_TTL_SEC = 300.0
_MAX_EXTRA_NAME = 80
_UA = "TorrentMovieApp/1.0 (trailer-fallback; +https://local.search-api)"
_YT_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
_YT_IN_URL_RE = re.compile(
    r"(?:[?&]v=|/embed/|/shorts/|youtu\.be/)([A-Za-z0-9_-]{11})",
    re.IGNORECASE,
)
_REJECT = (
    "review", "reaction", "explained", "recap", "ending explained",
    "leaked", "fan-made", "fanmade", "soundtrack", " ost", "gameplay",
    "fake trailer", "video essay", "ranking", "top 10",
)
_cache_lock = threading.Lock()
_memory_hit: dict[str, str] = {}
_memory_miss: dict[str, float] = {}


@dataclass(frozen=True)
class TrailerCandidate:
    video_id: str
    title: str = ""
    snippet: str = ""
    source: str = ""


def trailer_id_for(title: str, year: int | None) -> str:
    key = f"{title.strip().lower()}|{year or ''}"
    return hashlib.sha256(key.encode("utf-8")).hexdigest()[:20]


def extract_youtube_id(raw: object) -> str | None:
    text = str(raw or "").strip()
    if not text:
        return None
    match = _YT_IN_URL_RE.search(text)
    if match:
        return match.group(1)
    parsed = urlparse(text)
    if parsed.query:
        values = parse_qs(parsed.query).get("v") or []
        if values and _YT_ID_RE.match(values[0]):
            return values[0]
    if "/" not in text and "?" not in text and _YT_ID_RE.match(text):
        return text
    return None


def is_usable_trailer_key(raw: object) -> bool:
    return extract_youtube_id(raw) is not None


def build_trailer_queries(
    title: str,
    year: int | None,
    *,
    overview: str | None = None,
    torrent_names: list[str] | None = None,
    date: str | None = None,
) -> list[str]:
    title = (title or "").strip()
    if not title:
        return []
    year_text = str(year) if year else ""
    if not year_text and (date or "").strip()[:4].isdigit():
        year_text = date.strip()[:4]
    queries: list[str] = []
    if year_text:
        queries.append(f"{title} {year_text} official trailer")
        queries.append(f"{title} {year_text} trailer")
        queries.append(f"{title} {year_text} film")
    else:
        queries.append(f"{title} official trailer")
        queries.append(f"{title} trailer")
        queries.append(f"{title} film")
    hints = overview_hint_tokens(overview)
    if hints:
        queries.append(f"{title} {' '.join(hints[:3])} trailer")
    extra_name = ""
    for raw in torrent_names or []:
        cleaned = re.sub(r"[._]+", " ", raw or "").strip()
        if cleaned and title.lower() not in cleaned.lower():
            extra_name = cleaned[:_MAX_EXTRA_NAME]
            break
    if extra_name:
        queries.append(f"{title} {extra_name} official trailer")
    seen: set[str] = set()
    unique: list[str] = []
    for query in queries:
        key = query.lower()
        if key in seen:
            continue
        seen.add(key)
        unique.append(query)
    return unique[:5]


def score_trailer_candidate(
    candidate: TrailerCandidate,
    *,
    title: str,
    year: int | None,
    overview: str | None = None,
) -> float:
    blob = f"{candidate.title} {candidate.snippet}".lower()
    if any(bad in blob for bad in _REJECT):
        return -20.0
    score = 0.0
    for token in title.lower().split():
        token = re.sub(r"[^a-z0-9]", "", token)
        if len(token) > 2 and token in blob:
            score += 2.0
    if year and str(year) in blob:
        score += 3.0
    if "official trailer" in blob:
        score += 8.0
    elif "trailer" in blob:
        score += 4.0
    if "teaser" in blob:
        score += 2.0
    if candidate.source in {"wikidata", "wikipedia"}:
        score += 2.5
    if candidate.source == "youtube":
        score += 1.5
    for hint in overview_hint_tokens(overview):
        if hint in blob:
            score += 1.0
    return score


def _is_recent_miss(cache_id: str) -> bool:
    now = time.time()
    with _cache_lock:
        stamped = _memory_miss.get(cache_id)
        if stamped is None:
            return False
        if now - stamped > _MISS_TTL_SEC:
            del _memory_miss[cache_id]
            return False
        return True


def _note_miss(cache_id: str) -> None:
    with _cache_lock:
        _memory_miss[cache_id] = time.time()


def _note_hit(cache_id: str, video_id: str) -> None:
    with _cache_lock:
        _memory_hit[cache_id] = video_id
        _memory_miss.pop(cache_id, None)


def _cached_hit(cache_id: str) -> str | None:
    with _cache_lock:
        return _memory_hit.get(cache_id)


def _session() -> requests.Session:
    session = requests.Session()
    session.headers.update({"User-Agent": _UA, "Accept": "application/json"})
    return session


def _wikidata_candidates(
    session: requests.Session,
    title: str,
    year: int | None,
) -> list[TrailerCandidate]:
    try:
        search = session.get(
            "https://www.wikidata.org/w/api.php",
            params={
                "action": "wbsearchentities",
                "search": f"{title} {year or ''}".strip(),
                "language": "en",
                "uselang": "en",
                "type": "item",
                "limit": 5,
                "format": "json",
            },
            timeout=_FETCH_TIMEOUT,
        )
        search.raise_for_status()
        hits = search.json().get("search") or []
        out: list[TrailerCandidate] = []
        seen: set[str] = set()
        for hit in hits[:4]:
            entity_id = str(hit.get("id") or "").strip()
            if not entity_id:
                continue
            label = str(hit.get("label") or title)
            description = str(hit.get("description") or "")
            entity = session.get(
                f"https://www.wikidata.org/wiki/Special:EntityData/{entity_id}.json",
                timeout=_FETCH_TIMEOUT,
            )
            entity.raise_for_status()
            claims = (
                ((entity.json().get("entities") or {}).get(entity_id) or {}).get("claims")
                or {}
            )
            for snak in claims.get("P1651") or []:
                value = (
                    ((snak.get("mainsnak") or {}).get("datavalue") or {}).get("value")
                )
                video_id = extract_youtube_id(value)
                if not video_id or video_id in seen:
                    continue
                seen.add(video_id)
                out.append(
                    TrailerCandidate(
                        video_id=video_id,
                        title=f"{label} official trailer",
                        snippet=description,
                        source="wikidata",
                    )
                )
        return out
    except Exception as exc:
        logger.debug("wikidata trailer search failed: %s", exc)
        return []


def _wikipedia_candidates(session: requests.Session, query: str) -> list[TrailerCandidate]:
    try:
        search = session.get(
            "https://en.wikipedia.org/w/api.php",
            params={
                "action": "query",
                "list": "search",
                "srsearch": query,
                "srlimit": 4,
                "format": "json",
            },
            timeout=_FETCH_TIMEOUT,
        )
        search.raise_for_status()
        hits = (search.json().get("query") or {}).get("search") or []
        titles = [str(hit.get("title") or "").strip() for hit in hits if hit.get("title")]
        if not titles:
            return []
        pages = session.get(
            "https://en.wikipedia.org/w/api.php",
            params={
                "action": "query",
                "prop": "extlinks|extracts",
                "ellimit": 80,
                "exintro": 1,
                "explaintext": 1,
                "titles": "|".join(titles[:4]),
                "format": "json",
            },
            timeout=_FETCH_TIMEOUT,
        )
        pages.raise_for_status()
        out: list[TrailerCandidate] = []
        seen: set[str] = set()
        page_map = (pages.json().get("query") or {}).get("pages") or {}
        for page in page_map.values():
            page_title = str(page.get("title") or "")
            snippet = str(page.get("extract") or "")[:400]
            for link in page.get("extlinks") or []:
                href = str(link.get("*") or link.get("url") or "")
                if "youtu" not in href.lower():
                    continue
                video_id = extract_youtube_id(href)
                if not video_id or video_id in seen:
                    continue
                seen.add(video_id)
                out.append(
                    TrailerCandidate(
                        video_id=video_id,
                        title=f"{page_title} trailer",
                        snippet=f"{snippet} {href}",
                        source="wikipedia",
                    )
                )
        return out
    except Exception as exc:
        logger.debug("wikipedia trailer search failed: %s", exc)
        return []


def _youtube_api_candidates(session: requests.Session, query: str) -> list[TrailerCandidate]:
    key = (os.environ.get("YOUTUBE_API_KEY") or os.environ.get("GOOGLE_API_KEY") or "").strip()
    if not key:
        return []
    try:
        resp = session.get(
            "https://www.googleapis.com/youtube/v3/search",
            params={
                "key": key,
                "part": "snippet",
                "type": "video",
                "videoEmbeddable": "true",
                "maxResults": 5,
                "q": query,
                "safeSearch": "moderate",
            },
            timeout=_FETCH_TIMEOUT,
        )
        resp.raise_for_status()
        out: list[TrailerCandidate] = []
        for item in resp.json().get("items") or []:
            video_id = extract_youtube_id((item.get("id") or {}).get("videoId"))
            if not video_id:
                continue
            snippet = item.get("snippet") or {}
            out.append(
                TrailerCandidate(
                    video_id=video_id,
                    title=str(snippet.get("title") or ""),
                    snippet=f"{snippet.get('description') or ''} {snippet.get('channelTitle') or ''}",
                    source="youtube",
                )
            )
        return out
    except Exception as exc:
        logger.debug("youtube data api search failed: %s", exc)
        return []


def _google_cse_youtube_candidates(
    session: requests.Session,
    query: str,
) -> list[TrailerCandidate]:
    key = (os.environ.get("GOOGLE_CSE_API_KEY") or os.environ.get("GOOGLE_API_KEY") or "").strip()
    cx = (os.environ.get("GOOGLE_CSE_ID") or "").strip()
    if not key or not cx:
        return []
    try:
        resp = session.get(
            "https://www.googleapis.com/customsearch/v1",
            params={
                "key": key,
                "cx": cx,
                "q": f"{query} site:youtube.com",
                "num": 5,
                "safe": "active",
            },
            timeout=_FETCH_TIMEOUT,
        )
        resp.raise_for_status()
        out: list[TrailerCandidate] = []
        for item in resp.json().get("items") or []:
            video_id = extract_youtube_id(item.get("link"))
            if not video_id:
                continue
            out.append(
                TrailerCandidate(
                    video_id=video_id,
                    title=str(item.get("title") or ""),
                    snippet=str(item.get("snippet") or ""),
                    source="google",
                )
            )
        return out
    except Exception as exc:
        logger.debug("google cse trailer search failed: %s", exc)
        return []


def _oembed_title(session: requests.Session, video_id: str) -> str | None:
    try:
        resp = session.get(
            "https://www.youtube.com/oembed",
            params={
                "url": f"https://www.youtube.com/watch?v={video_id}",
                "format": "json",
            },
            timeout=_FETCH_TIMEOUT,
        )
        if resp.status_code != 200:
            return None
        return str((resp.json() or {}).get("title") or "") or ""
    except Exception:
        return None


def resolve_web_trailer(
    title: str,
    year: int | None,
    *,
    overview: str | None = None,
    torrent_names: list[str] | None = None,
    date: str | None = None,
    session: requests.Session | None = None,
) -> str | None:
    """Return an 11-character YouTube video id, or None."""
    title = (title or "").strip()
    if not title:
        return None
    year = coerce_year(year)
    cache_id = trailer_id_for(title, year)
    cached = _cached_hit(cache_id)
    if cached:
        return cached
    if _is_recent_miss(cache_id):
        return None

    queries = build_trailer_queries(
        title,
        year,
        overview=overview,
        torrent_names=torrent_names,
        date=date,
    )
    own_session = session is None
    session = session or _session()
    try:
        ranked: list[tuple[float, TrailerCandidate]] = []
        seen_ids: set[str] = set()

        def _add(candidate: TrailerCandidate) -> None:
            if candidate.video_id in seen_ids:
                return
            seen_ids.add(candidate.video_id)
            score = score_trailer_candidate(
                candidate, title=title, year=year, overview=overview,
            )
            if score >= _MIN_SCORE:
                ranked.append((score, candidate))

        for candidate in _wikidata_candidates(session, title, year):
            _add(candidate)
        for query in queries:
            for candidate in (
                *_wikipedia_candidates(session, query),
                *_youtube_api_candidates(session, query),
                *_google_cse_youtube_candidates(session, query),
            ):
                _add(candidate)
            if len(ranked) >= _MAX_CANDIDATES:
                break
        ranked.sort(key=lambda item: item[0], reverse=True)
        for _, candidate in ranked[:_MAX_CANDIDATES]:
            oembed_title = _oembed_title(session, candidate.video_id)
            if oembed_title is None:
                continue
            if oembed_title:
                rescored = TrailerCandidate(
                    video_id=candidate.video_id,
                    title=oembed_title,
                    snippet=candidate.snippet,
                    source=candidate.source,
                )
                if score_trailer_candidate(
                    rescored, title=title, year=year, overview=overview,
                ) < _MIN_SCORE:
                    continue
            _note_hit(cache_id, candidate.video_id)
            return candidate.video_id
        _note_miss(cache_id)
        return None
    finally:
        if own_session:
            session.close()


def fill_missing_trailers(buckets: list[dict], *, limit: int = _MAX_PER_REQUEST) -> int:
    """Fill `trailer_youtube_key` on group buckets that still lack a usable id."""
    filled = 0
    pending: list[dict] = []
    for bucket in buckets:
        existing = extract_youtube_id(bucket.get("trailer_youtube_key"))
        if existing:
            bucket["trailer_youtube_key"] = existing
            continue
        if bucket.get("trailer_youtube_key"):
            bucket["trailer_youtube_key"] = None
        title = str(bucket.get("title") or "").strip()
        if not title:
            continue
        year = coerce_year(bucket.get("year"))
        cache_id = trailer_id_for(title, year)
        cached = _cached_hit(cache_id)
        if cached:
            bucket["trailer_youtube_key"] = cached
            filled += 1
            continue
        if _is_recent_miss(cache_id):
            continue
        pending.append(bucket)
        if len(pending) >= limit:
            break
    if not pending:
        return filled

    workers = min(4, len(pending))

    def _one(bucket: dict) -> tuple[dict, str | None]:
        releases = bucket.get("releases") or []
        names = [str(row.get("name") or "") for row in releases[:4]]
        dates = [str(row.get("date") or "") for row in releases if row.get("date")]
        return bucket, resolve_web_trailer(
            str(bucket.get("title") or ""),
            coerce_year(bucket.get("year")),
            overview=str(bucket.get("overview") or "") or None,
            torrent_names=names,
            date=dates[0] if dates else None,
        )

    pool = ThreadPoolExecutor(max_workers=workers)
    try:
        futures = [pool.submit(_one, bucket) for bucket in pending]
        try:
            for future in as_completed(futures, timeout=_FILL_BUDGET_SEC):
                try:
                    bucket, video_id = future.result()
                except Exception as exc:
                    logger.debug("web trailer worker failed: %s", exc)
                    continue
                if video_id:
                    bucket["trailer_youtube_key"] = video_id
                    filled += 1
        except TimeoutError:
            logger.debug("web trailer fill budget exceeded after %.1fs", _FILL_BUDGET_SEC)
    finally:
        pool.shutdown(wait=False, cancel_futures=True)
    return filled
