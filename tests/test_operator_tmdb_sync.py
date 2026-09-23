"""FR-042: Android gradle and search-service operator TMDB keys must stay in sync."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def _gradle_tmdb_key() -> str:
    text = (ROOT / "android/gradle.properties").read_text(encoding="utf-8")
    match = re.search(r"^missysBundledTmdbApiKey=(.+)$", text, re.MULTILINE)
    assert match, "missysBundledTmdbApiKey missing from android/gradle.properties"
    return match.group(1).strip()


def _python_tmdb_key() -> str:
    text = (ROOT / "services/search/operator_defaults.py").read_text(encoding="utf-8")
    match = re.search(r'BUNDLED_TMDB_API_KEY = "([^"]+)"', text)
    assert match, "BUNDLED_TMDB_API_KEY missing from operator_defaults.py"
    return match.group(1).strip()


def test_operator_tmdb_keys_match_across_android_and_search_service():
    gradle_key = _gradle_tmdb_key()
    python_key = _python_tmdb_key()
    assert gradle_key == python_key
    assert len(gradle_key) >= 16
