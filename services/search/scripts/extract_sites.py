"""One-off: extract site classes from upstream torrtux-c."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = Path(r"F:\DRIVE_DOWNLOADS\torrtux-c\torrtux.py")
OUT = ROOT / "torrtux_core" / "sites" / "providers.py"

lines = UPSTREAM.read_text(encoding="utf-8").splitlines()
start = next(i for i, line in enumerate(lines) if line.startswith("class PirateBay"))
end = next(i for i, line in enumerate(lines) if line.startswith("class TorrentSearcher"))
block = lines[start:end]
text = "\n".join(block)
# Remove termcolor print in get_magnet_link copies (sites use base get_magnet_link)

header = '''"""Site adapters ported from torrtux-c (read-only upstream)."""
from urllib.parse import urljoin, quote

import requests
from bs4 import BeautifulSoup

from torrtux_core.base import TorrentSite

'''
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(header + text + "\n", encoding="utf-8")
print(f"wrote {OUT} ({len(block)} lines)")
