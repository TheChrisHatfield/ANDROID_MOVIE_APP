# UI reference: Yatse-inspired layout

**Hook:** `spec.flow`  
**Reference app:** [Yatse](https://yatse.tv/) — Kodi/Plex remote for Android  
**Goal:** Categorize navigation, screens, and interaction patterns like Yatse while adapting content to torrent search + seedbox handoff.

> Yatse is the UX model, not a dependency. We mirror its **information architecture** and **Material-style polish**, not Kodi-specific features.

## Yatse pattern → This app mapping

| Yatse | Torrent Movie App |
|-------|-------------------|
| Left navigation menu (host icon opens drawer) | **Navigation drawer** — Search, Uploaded, Settings, Help |
| Host icon + online/sync status | **Seedbox status chip** — connected / error / checking (ruTorrent reachability) |
| Movies library | **Search** — movie torrent discovery (primary section) |
| Global search | **App bar search** — persistent on Search screen; submits to torrtux API |
| Media detail (poster, metadata, play) | **Torrent detail** — title, site, seeds, size, magnet, **Send to seedbox** CTA |
| Files / playlists | **Uploaded** — editable sent-magnet history (duplicate guard) |
| Settings (Normal / Advanced / Expert tiers) | **Settings** grouped: *General*, *Seedbox*, *Search*, *Interface* |
| Remote screen | *Out of scope* — no playback remote in v1 |
| PVR / TV Shows / Music | *Out of scope* — movies-only v1; drawer items hidden not stubbed |
| Swipeable tabs, sort, filter, layout toggle | **Search toolbar** — filter chips (seeds, size), sort (seeds desc default), list/grid toggle (optional P3) |
| Long-press actions | **Uploaded** — long-press → delete; search result long-press → copy magnet |
| Pull to refresh / force sync | **Pull-to-refresh** on Search and Uploaded |
| Now-playing mini bar | **Last sent banner** (optional P3) — shows most recent successful upload with link to Uploaded |

## Navigation structure

```
┌─────────────────────────────────────┐
│ [≡]  Torrent Movies    [🔍][host] │  ← Top app bar: drawer, title, search, seedbox status
├─────────────────────────────────────┤
│                                     │
│         Main content area           │  ← Search | Uploaded | Settings | Help
│                                     │
└─────────────────────────────────────┘

Drawer (Yatse-style left menu):
  ● Search          ← default landing
  ● Uploaded
  ─────────
  ● Settings
  ● Help / About
```

- **Default screen:** Search (like Yatse defaulting to library/remote flow).
- **Host tap:** opens seedbox quick panel — connection test, open seedbox URL in browser (optional).
- **Back behavior:** detail → list → drawer root (standard Android).

## Screen layouts

### Search (Yatse “Movies library” analog)

- Top: search field + filter icon (opens bottom sheet for min seeds / max size).
- Body: **card list** (default) — each card shows title, site badge, size, seeds, “Already uploaded” ribbon if duplicate.
- Empty: illustration + “Search for a movie”.
- Loading: skeleton cards or linear progress (Yatse-style non-blocking refresh).

### Torrent detail (Yatse media detail analog)

- **Hero block:** large title + site + seed/leech stats.
- **Metadata section:** size, date, indexer.
- **Primary FAB or full-width button:** “Send to seedbox” (Yatse’s Play equivalent).
- Secondary: copy magnet, view on indexer (optional).
- Duplicate state: primary button disabled + explanation + link to Uploaded entry.

### Uploaded (Yatse “Files / history” analog)

- Chronological list, newest first.
- Row: title, site, date sent, download folder used.
- **Swipe-to-delete** or trash icon (editable list per FR-014).
- Empty state: “No uploads yet — search and send your first movie.”

### Settings (Yatse tiered settings analog)

| Group | Fields |
|-------|--------|
| **General** | Legal disclaimer, theme (follow system) |
| **Seedbox** | ruTorrent URL, username, password, auth scheme, magnet download folder |
| **Search** | API base URL, movie-sites-only toggle, default min seeds |
| **Interface** | List vs grid (optional), show seedbox status bar |

- Use `PreferenceScreen`-style grouped lists or Compose `ListItem` sections with section headers (Yatse Normal tier for MVP; Advanced = auth scheme + API URL).

## Visual language

- **Material Design 3** — dynamic color, elevated cards, consistent 16dp padding.
- **Typography:** title large for detail hero; label small for site badges.
- **Color semantics:** green = sent/success, amber = duplicate warning, red = error, muted = offline seedbox.
- **Z Fold (inner screen ≥600dp):** two-pane search + detail (Yatse landscape pattern) via `FoldSearchDetailLayout`; phone and folded narrow use single-pane detail navigation.

## Out of scope (Yatse features not ported)

- Kodi/Plex remote, casting, widgets, Wear, voice commands, PVR, music libraries, custom command remapping.

## References

- [Yatse how-to / navigation](https://yatse.tv/wiki/how-to-use-yatse-kodi-remote)
- [Yatse interface settings](https://yatse.tv/wiki/yatse-interface-settings)
