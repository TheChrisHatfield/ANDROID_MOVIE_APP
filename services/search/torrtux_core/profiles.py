"""Indexer profiles for search API."""

MOVIE_SITE_NAMES = [
    "YTS",
    "1337x",
    "The Pirate Bay",
    "TorrentGalaxy",
    "MagnetDL",
    "LimeTorrents",
]

# Upstream stubs / non-movie — excluded from default movie profile
EXCLUDED_FROM_MOVIE_PROFILE = {
    "EZTV",
    "RARBG",
    "Good-Torrent",
    "FitGirl Repacks",
    "LinuxTracker",
    "Nyaa",
}
