"""Ad-hoc movie posters when TMDB and indexer rows have none.

Query Wikipedia/Wikimedia (and optional Google CSE images) using torrent
title, year, overview, date, and release names. Rank hits against those
tokens, fetch one image, crop-resize to TMDB w342 (342×513), cache locally.
"""
from __future__ import annotations

import hashlib
import io
import logging
import os
import re
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path

import requests
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = False
Image.MAX_IMAGE_PIXELS = 20_000_000

logger = logging.getLogger(__name__)

POSTER_WIDTH = 342
POSTER_HEIGHT = 513
POSTER_PATH_PREFIX = "/v1/posters/"
_MAX_PER_REQUEST = 8
_MAX_CANDIDATES = 6
_MIN_SCORE = 4.0
_FETCH_TIMEOUT = 8
_FILL_BUDGET_SEC = 6.0
_MISS_TTL_SEC = 300.0
_MAX_BYTES = 1_500_000
_MAX_EXTRA_NAME = 80
_UA = "TorrentMovieApp/1.0 (poster-fallback; +https://local.search-api)"
_STOPWORDS = {
    "the", "a", "an", "and", "or", "of", "in", "on", "to", "for", "with",
    "his", "her", "their", "from", "into", "this", "that", "is", "are",
    "was", "were", "by", "as", "at", "it", "be", "who", "whom",
}
_REJECT = (
    "wallpaper", "screenshot", "subtitle", "xxx", "porn", "nsfw",
    "fan-made", "fanmade", "collage", "meme",
)
_POSTER_ID_RE = re.compile(r"^[0-9a-f]{16,64}$")

_cache_lock = threading.Lock()
_memory_miss: dict[str, float] = {}


@dataclass(frozen=True)
class PosterCandidate:
    url: str
    title: str = ""
    snippet: str = ""
    source: str = ""


def poster_cache_dir() -> Path:
    override = os.environ.get("POSTER_CACHE_DIR", "").strip()
    if override:
        path = Path(override)
    else:
        path = Path(__file__).resolve().parents[1] / "data" / "posters"
    path.mkdir(parents=True, exist_ok=True)
    return path


def poster_id_for(title: str, year: int | None) -> str:
    key = f"{title.strip().lower()}|{year or ''}"
    return hashlib.sha256(key.encode("utf-8")).hexdigest()[:20]


def is_poster_id(poster_id: str) -> bool:
    return bool(_POSTER_ID_RE.match(poster_id or ""))


def cached_poster_path(poster_id: str) -> Path | None:
    if not is_poster_id(poster_id):
        return None
    path = poster_cache_dir() / f"{poster_id}.jpg"
    return path if path.is_file() else None


def poster_public_path(poster_id: str) -> str:
    return f"{POSTER_PATH_PREFIX}{poster_id}.jpg"


def is_usable_poster_url(url: object) -> bool:
    text = str(url or "").strip()
    if not text:
        return False
    lower = text.lower()
    if lower.startswith(POSTER_PATH_PREFIX):
        return True
    if not lower.startswith(("http://", "https://")):
        return False
    path = lower.split("?", 1)[0]
    if path.endswith((".html", ".htm", ".php", ".asp", ".aspx")):
        return False
    if "/torrent/" in path or "/description.php" in path:
        return False
    return True


def coerce_year(year: object) -> int | None:
    if year is None or isinstance(year, bool):
        return None
    if isinstance(year, int):
        return year if 1880 <= year <= 2100 else None
    text = str(year).strip()
    if text.isdigit():
        value = int(text)
        return value if 1880 <= value <= 2100 else None
    return None


def wiki_page_image_urls(page: dict) -> list[str]:
    """Prefer thumbnail over original so oversized files do not skip a usable poster."""
    original = (page.get("original") or {}).get("source")
    thumb = (page.get("thumbnail") or {}).get("source")
    urls: list[str] = []
    for url in (thumb, original):
        text = str(url or "").strip()
        if text and text not in urls:
            urls.append(text)
    return urls


def _is_recent_miss(poster_id: str) -> bool:
    now = time.time()
    with _cache_lock:
        stamped = _memory_miss.get(poster_id)
        if stamped is None:
            return False
        if now - stamped > _MISS_TTL_SEC:
            del _memory_miss[poster_id]
            return False
        return True


def _note_miss(poster_id: str) -> None:
    with _cache_lock:
        _memory_miss[poster_id] = time.time()


def overview_hint_tokens(overview: str | None, *, limit: int = 4) -> list[str]:
    words: list[str] = []
    for raw in re.findall(r"[A-Za-z][A-Za-z']{2,}", overview or ""):
        token = raw.lower()
        if token.endswith("'s"):
            token = token[:-2]
        if token in _STOPWORDS or token in words:
            continue
        words.append(token)
        if len(words) >= limit:
            break
    return words


def build_poster_queries(
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
        queries.append(f"{title} {year_text} movie poster")
        queries.append(f"{title} {year_text} film")
    else:
        queries.append(f"{title} movie poster")
        queries.append(f"{title} film")
    hints = overview_hint_tokens(overview)
    if hints:
        queries.append(f"{title} {' '.join(hints[:3])} movie")
    extra_name = ""
    for raw in torrent_names or []:
        cleaned = re.sub(r"[._]+", " ", raw or "").strip()
        if cleaned and title.lower() not in cleaned.lower():
            extra_name = cleaned[:_MAX_EXTRA_NAME]
            break
    if extra_name:
        queries.append(f"{title} {extra_name} movie poster")
    # Preserve order, drop dupes.
    seen: set[str] = set()
    unique: list[str] = []
    for query in queries:
        key = query.lower()
        if key in seen:
            continue
        seen.add(key)
        unique.append(query)
    return unique[:4]


def score_candidate(
    candidate: PosterCandidate,
    *,
    title: str,
    year: int | None,
    overview: str | None = None,
) -> float:
    blob = f"{candidate.title} {candidate.snippet} {candidate.url}".lower()
    if any(bad in blob for bad in _REJECT):
        return -20.0
    score = 0.0
    for token in title.lower().split():
        token = re.sub(r"[^a-z0-9]", "", token)
        if len(token) > 2 and token in blob:
            score += 2.0
    if year and str(year) in blob:
        score += 3.0
    if any(word in blob for word in ("poster", "film", "movie")):
        score += 2.0
    if "wikipedia" in candidate.url or "wikimedia" in candidate.url:
        score += 2.5
    # Wikimedia thumbs are sized for posters; originals often exceed _MAX_BYTES.
    if "/thumb/" in candidate.url:
        score += 1.5
    if candidate.source == "google":
        score += 0.5
    for hint in overview_hint_tokens(overview):
        if hint in blob:
            score += 1.0
    return score


def fit_poster_image(image: Image.Image) -> Image.Image:
    src = image.convert("RGB")
    if src.width < 8 or src.height < 8:
        raise ValueError("image too small")
    target_ratio = POSTER_WIDTH / POSTER_HEIGHT
    src_ratio = src.width / src.height
    if src_ratio > target_ratio:
        new_w = max(1, int(src.height * target_ratio))
        left = max(0, (src.width - new_w) // 2)
        src = src.crop((left, 0, left + new_w, src.height))
    else:
        new_h = max(1, int(src.width / target_ratio))
        top = max(0, (src.height - new_h) // 2)
        src = src.crop((0, top, src.width, top + new_h))
    return src.resize((POSTER_WIDTH, POSTER_HEIGHT), Image.Resampling.LANCZOS)


def encode_poster_jpeg(image: Image.Image) -> bytes:
    fitted = fit_poster_image(image)
    buf = io.BytesIO()
    fitted.save(buf, format="JPEG", quality=85, optimize=True)
    return buf.getvalue()


def _session() -> requests.Session:
    session = requests.Session()
    session.headers.update({"User-Agent": _UA, "Accept": "*/*"})
    return session


def _wikipedia_candidates(session: requests.Session, query: str) -> list[PosterCandidate]:
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
                "prop": "pageimages|extracts",
                "piprop": "thumbnail|original",
                "pithumbsize": 600,
                "exintro": 1,
                "explaintext": 1,
                "titles": "|".join(titles[:4]),
                "format": "json",
            },
            timeout=_FETCH_TIMEOUT,
        )
        pages.raise_for_status()
        out: list[PosterCandidate] = []
        page_map = (pages.json().get("query") or {}).get("pages") or {}
        for page in page_map.values():
            title = str(page.get("title") or "")
            snippet = str(page.get("extract") or "")[:400]
            for url in wiki_page_image_urls(page):
                out.append(
                    PosterCandidate(
                        url=url,
                        title=title,
                        snippet=snippet,
                        source="wikipedia",
                    )
                )
        return out
    except Exception as exc:
        logger.debug("wikipedia poster search failed: %s", exc)
        return []


def _google_image_candidates(session: requests.Session, query: str) -> list[PosterCandidate]:
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
                "q": query,
                "searchType": "image",
                "num": 5,
                "safe": "active",
                "imgSize": "large",
            },
            timeout=_FETCH_TIMEOUT,
        )
        resp.raise_for_status()
        out: list[PosterCandidate] = []
        for item in resp.json().get("items") or []:
            link = str(item.get("link") or "").strip()
            if not link:
                continue
            out.append(
                PosterCandidate(
                    url=link,
                    title=str(item.get("title") or ""),
                    snippet=str(item.get("snippet") or ""),
                    source="google",
                )
            )
        return out
    except Exception as exc:
        logger.debug("google cse poster search failed: %s", exc)
        return []


def _download_image(session: requests.Session, url: str) -> Image.Image | None:
    try:
        with session.get(url, timeout=_FETCH_TIMEOUT, stream=True) as resp:
            resp.raise_for_status()
            content_type = (resp.headers.get("Content-Type") or "").lower()
            if content_type and not (
                content_type.startswith("image/") or "octet-stream" in content_type
            ):
                return None
            buf = bytearray()
            for chunk in resp.iter_content(chunk_size=16384):
                buf.extend(chunk)
                if len(buf) > _MAX_BYTES:
                    return None
        image = Image.open(io.BytesIO(bytes(buf)))
        image.load()
        return image
    except Exception:
        return None


def resolve_web_poster(
    title: str,
    year: int | None,
    *,
    overview: str | None = None,
    torrent_names: list[str] | None = None,
    date: str | None = None,
    session: requests.Session | None = None,
) -> str | None:
    """Return public `/v1/posters/{id}.jpg` path, or None."""
    title = (title or "").strip()
    if not title:
        return None
    year = coerce_year(year)
    poster_id = poster_id_for(title, year)
    cached = cached_poster_path(poster_id)
    if cached:
        return poster_public_path(poster_id)
    if _is_recent_miss(poster_id):
        return None

    queries = build_poster_queries(
        title,
        year,
        overview=overview,
        torrent_names=torrent_names,
        date=date,
    )
    own_session = session is None
    session = session or _session()
    try:
        ranked: list[tuple[float, PosterCandidate]] = []
        seen_urls: set[str] = set()
        for query in queries:
            for candidate in (
                *_wikipedia_candidates(session, query),
                *_google_image_candidates(session, query),
            ):
                if candidate.url in seen_urls:
                    continue
                seen_urls.add(candidate.url)
                score = score_candidate(
                    candidate, title=title, year=year, overview=overview,
                )
                if score >= _MIN_SCORE:
                    ranked.append((score, candidate))
            if len(ranked) >= _MAX_CANDIDATES:
                break
        ranked.sort(key=lambda item: item[0], reverse=True)
        jpeg: bytes | None = None
        for _, candidate in ranked[:_MAX_CANDIDATES]:
            image = _download_image(session, candidate.url)
            if image is None:
                continue
            try:
                jpeg = encode_poster_jpeg(image)
                break
            except Exception:
                continue
        if not jpeg:
            _note_miss(poster_id)
            return None
        dest = poster_cache_dir() / f"{poster_id}.jpg"
        dest.write_bytes(jpeg)
        return poster_public_path(poster_id)
    finally:
        if own_session:
            session.close()


def fill_missing_posters(buckets: list[dict], *, limit: int = _MAX_PER_REQUEST) -> int:
    """Fill `poster_url` on group buckets that still lack one. Returns fill count."""
    filled = 0
    pending: list[dict] = []
    for bucket in buckets:
        if is_usable_poster_url(bucket.get("poster_url")):
            continue
        if bucket.get("poster_url"):
            bucket["poster_url"] = None
        title = str(bucket.get("title") or "").strip()
        if not title:
            continue
        year = coerce_year(bucket.get("year"))
        poster_id = poster_id_for(title, year)
        if cached_poster_path(poster_id):
            bucket["poster_url"] = poster_public_path(poster_id)
            filled += 1
            continue
        if _is_recent_miss(poster_id):
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
        return bucket, resolve_web_poster(
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
                    bucket, url = future.result()
                except Exception as exc:
                    logger.debug("web poster worker failed: %s", exc)
                    continue
                if url:
                    bucket["poster_url"] = url
                    filled += 1
        except TimeoutError:
            logger.debug("web poster fill budget exceeded after %.1fs", _FILL_BUDGET_SEC)
    finally:
        pool.shutdown(wait=False, cancel_futures=True)
    return filled
