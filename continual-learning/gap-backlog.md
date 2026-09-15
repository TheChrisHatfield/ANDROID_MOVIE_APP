# Gap backlog (agent-maintained)

**Hook:** `learning.loop`  
**Active feature:** `torrent-movie-app`

---

## Status snapshot

| Area | State |
|------|--------|
| Spec / plan / tasks / contracts | **Extended** — FR-019–FR-030 (cycle 2 mine) |
| `services/search/` | Done — **13 pytest**; G30/G31/G36/G45 aligned |
| `android/` | **APK builds** — `gradlew assembleDebug test`; device install needs emulator/phone |
| Root wiring tests | 4 pytest |
| Learning loop | **Closed** — spec + impl aligned for lazy magnet / sort |

---

## Closed gaps

G1–G20 — initial mine + full-stack slice (see `planning-rosetta-mine.md`).

---

## Open gaps (optional)

| ID | Issue | Target |
|----|-------|--------|
| **G13** | Live indexer smoke 2026 | `@pytest.mark.live` (deferred) |
| **G17** | Search API auth if public | post-MVP |

---

## Loot shift (latest)

**Bottleneck:** Manual seedbox send in emulator (needs your ruTorrent credentials in Settings).  
**Done this cycle:** Spec polish slice — `failed_sites`, FR-024/025/028 Android UX, filter sheet, pull-refresh, disclaimer.

---

## Validation evidence

```text
device-e2e: emulator-5554 booted; APK installed; MainActivity launched
emulator -> host: ping 10.0.2.2 ok; adb reverse tcp:8765
host smoke: 18 indexers; search 0 results (network/indexer blocks)
script: scripts/e2e/device-e2e.ps1
```
