"""Learning-loop wiring: key implementation paths exist on disk."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_search_service_wired():
    assert (ROOT / "services/search/api/main.py").is_file()
    assert (ROOT / "services/search/torrtux_core/searcher.py").is_file()


def test_android_layers_exist():
    assert (ROOT / "android/app/src/main/java/com/torrentmovie/app/ui/NavGraph.kt").is_file()
    assert (ROOT / "android/core/data/src/main/java/com/torrentmovie/core/data/seedbox/RuTorrentClient.kt").is_file()
    assert (ROOT / "android/core/network/src/main/java/com/torrentmovie/core/network/SearchApi.kt").is_file()


def test_rosetta_mine_present():
    assert (ROOT / "docs/planning-rosetta-mine.md").is_file()
