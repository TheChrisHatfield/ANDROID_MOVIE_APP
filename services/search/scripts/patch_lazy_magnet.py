"""One-off patch: FR-021 lazy magnet + detail_url on all provider results."""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "torrtux_core" / "sites" / "providers.py"
text = path.read_text(encoding="utf-8")

text = text.replace(
    'magnet = magnet_link["href"] if magnet_link else (self.get_magnet_link(detail_url) if detail_url else "")',
    'magnet = magnet_link["href"] if magnet_link else None',
)
text = text.replace(
    '"magnet": self.get_magnet_link(detail_url),',
    '"magnet": None,',
)
text = text.replace("magnet = self.get_magnet_link(detail_url)", "magnet = None")
text = text.replace(
    'magnet = magnet["href"] if magnet else self.get_magnet_link(detail_url)',
    'magnet_el = magnet\n                magnet = magnet_el["href"] if magnet_el else None',
)
text = text.replace(
    "detail_url = movie.select_one('a')['href']",
    "detail_url = urljoin(self.working_url, movie.select_one('a')['href'])",
)
text = text.replace(
    """                magnet = None
                # Get magnet from detail page
                try:
                    magnet = None
                except:
                    pass""",
    "                magnet = None",
)
text = text.replace(
    'detail_url = post.find("a")["href"]',
    'detail_url = urljoin(self.working_url, post.find("a")["href"])',
)

needle = '"magnet": magnet,\n                    "site": self.name'
replacement = '"magnet": magnet,\n                    "detail_url": detail_url,\n                    "site": self.name'
if needle not in text:
    raise SystemExit("needle not found for bulk detail_url insert")
text = text.replace(needle, replacement)

if "get_magnet_link" in text:
    raise SystemExit("get_magnet_link still referenced in providers.py")

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
