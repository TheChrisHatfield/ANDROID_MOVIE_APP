<!-- BOOTSTRAP: hive_planner init -->

# Holistic — ANDROID_MOVIE_APP

<!-- ROSETTA-NAV -->

## Agent navigation (Rosetta Stone)

Load [`docs/planning-rosetta-stone.md`](../../planning-rosetta-stone.md) (`planning.rosetta`) before using this Delta layer.

- **Hook:** `context.delta`
- **Unlocks:** macro / micro / spec alignment
- **Next:** [`10_macro.md`](../10_macro.md)
- **Learning loop (agents):** [`continual-learning/learning-loop.md`](../../continual-learning/learning-loop.md) (`learning.loop`) · gaps: [`gap-backlog.md`](../../continual-learning/gap-backlog.md)
- **Rosetta mine:** [`planning-rosetta-mine.md`](../planning-rosetta-mine.md) — torrtux + spec gaps (S/C/L/Δ)

## Mission

Deliver a **few-click Android experience** for searching movies across torrent indexers and sending chosen magnet links to a user-configured **seedbox** — without rebuilding indexer scraping logic from scratch.

**Source asset:** [torrtux-c](https://github.com/almezali/torrtux-c) (local clone at `F:\DRIVE_DOWNLOADS\torrtux-c`) provides multi-site search, parsing, filtering, and magnet extraction. This project **repurposes and refactors** that logic into a service layer the Android app consumes.

## Goals

- **G1 — Repurpose, not rewrite:** Port or wrap torrtux-c site adapters and `TorrentSearcher` rather than authoring new scrapers.
- **G2 — Few-click UX:** Search → pick result → send magnet to seedbox in ≤3 primary taps after initial setup. Navigation and layout **Yatse-inspired** (drawer sections, card lists, detail + primary action).
- **G3 — Movie-first defaults:** Default site set and filters tuned for movies (YTS, 1337x, TPB, TorrentGalaxy, MagnetDL, etc.).
- **G4 — Seedbox handoff:** Send magnets to user's **ruTorrent** seedbox via `php/addtorrent.php`, saving to `/home5/chris82/downloads/MOVIES/` via `dir_edit`.
- **G5 — Duplicate guard:** Maintain an editable local **Uploaded** list (by magnet info-hash) so the same movie is not sent to the seedbox twice unless the user removes it from the list.
- **G6 — Spec-driven delivery:** All behavior defined in Spec Kit before implementation (`torrent-movie-app`).

## Constraints

- Android client does **not** embed Python scraping; torrtux logic lives in a **search backend** (refactored from torrtux-c).
- Users supply their own seedbox credentials; app stores them securely on-device (Android Keystore).
- Legal disclaimer: tool is for users who have rights to access content; no piracy facilitation beyond generic search/transfer mechanics.
- MVP targets Android 8+ (API 26+); Kotlin + Jetpack Compose.

## Non-goals

- In-app BitTorrent downloading on the phone (seedbox handles acquisition).
- Building a new indexer or tracker.
- Desktop or iOS clients in v1.
- Full parity with every torrtux CLI flag in v1.
