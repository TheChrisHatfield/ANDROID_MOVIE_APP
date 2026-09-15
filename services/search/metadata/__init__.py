"""Movie metadata and torrent result grouping (Kodi-style library cards)."""

from metadata.grouping import build_movie_groups
from metadata.title_parse import parse_torrent_movie_title

__all__ = ["build_movie_groups", "parse_torrent_movie_title"]
