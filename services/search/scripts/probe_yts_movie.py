import json
import re

from torrtux_core.http_client import http_get

r = http_get("https://yts.rs/movie/inception-2010", timeout=20)
match = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', r.text)
data = json.loads(match.group(1))
import pprint

pp = data["props"]["pageProps"]
pprint.pp(pp, depth=3)
