# AGENTS.md — Rubato

> **Status:** Planning / Pre-development (Pi-side ground truth applied)
> **Last updated:** 2026-09-23
> **Owner:** micho
> **Source of truth:** the Raspberry Pi home server (`/home/micho/AGENTS.md`, `~/music-guru/`, `~/docker/`). This file lives in `~/rubato/` on the Pi but the fork itself is developed on PC with the Pi reachable via Cloudflare Tunnel.

---

## 1. Project Overview

**Rubato** is a fork of the open-source Android music player [Tempus](https://github.com/eddyizm/tempus) (Subsonic/Navidrome client, GPL-3.0), extended with:

1. **Discovery of NOT-owned music** — browse artists/tracks/albums/playlists outside the library via external catalog (Deezer/MusicBrainz, wrapped by the Pi backend), with a **slskd button**: search → download to the Pi → import into library → notify when playable. Spotify-like UX, Soulseek as source. The small "owned library" discovery (Daily Mix, New Arrivals via `getRandomSongs` / `getAlbumList2?type=newest`) is secondary.
2. **One-click Soulseek download** — a button on any track/album/playlist that calls the Pi backend (`guru-api`). The file is NOT downloaded to the phone: it lands in the Pi music library and is then streamed normally via Subsonic API (Tempus "pin" for offline cache afterwards).
3. **Full Android Auto support** — inherited from upstream Tempus, **phone-side enqueue only**. AA templates allow browse/playback/search only, no custom download buttons.

Plus: a **PWA for iPhone** (Safari-installable, Subsonic + `guru-api`, background playback; no CarPlay — in car via Bluetooth).

The name "Rubato" is a wordplay on Tempo/Tempus, "rubato" (italian for stolen) and "Tempo rubato".

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────┐
│              ANDROID (Player, on PC dev)              │
│  Tempus fork, flavor `tempus` ONLY (see §6)          │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐  │
│  │  Subsonic   │  │  Discovery  │  │ GuruClient   │  │
│  │  Client     │  │  tab (ext.  │  │ (enqueue +   │  │
│  │  (stream)   │  │  catalog)   │  │  poll status)│  │
│  └─────┬──────┘  └──────┬──────┘  └──────┬───────┘  │
│        │                │                │           │
│        └────────────────┼────────────────┘           │
│                         │                            │
│              ┌──────────▼──────────┐                 │
│              │  MediaBrowser (AA)  │  browse/play   │
│              │  phone enqueues     │  only          │
│              └─────────────────────┘                 │
└───────────────────────┬──────────────────────────────┘
                        │ HTTPS via Cloudflare Tunnel:
                        │ Subsonic API + guru-api (auth token)
┌───────────────────────▼──────────────────────────────┐
│              RASPBERRY PI 4 (Home Server)             │
│  arm64, Raspberry Pi OS Lite (trixie), Docker except  │
│  voice bridge + maintenance scripts                  │
│                                                      │
│  ┌─────────────┐  ┌───────────┐  ┌────────────────┐ │
│  │  Navidrome   │  │  slskd    │  │  guru-api      │ │
│  │  :4533       │  │  :5030    │  │  :8000 (NEW)   │ │
│  └──────┬──────┘  └─────┬─────┘  └───────┬────────┘ │
│         │               │                │          │
│         │          ┌────▼──────┐    ┌─────▼───────┐  │
│         │          │ Soulseek  │    │ music-guru  │  │
│         │          │ Network   │    │ fetch/compare│  │
│         │          └───────────┘    │ consolida   │  │
│         │                          └─────┬───────┘  │
│         └──────── Music Library ◄────────┘          │
│                 /mnt/music/library (NVMe)            │
└──────────────────────────────────────────────────────┘
```

**Do NOT rebuild:** Navidrome, slskd, cloudflared, Lidarr, the daily pipeline, `music-guru`. The new code is a thin `guru-api` wrapper + the Android fork (+ optional PWA).

---

## 3. Existing Infrastructure (Raspberry Pi — REAL, verified 2026-09-23)

| Service | Port | Notes |
|---|---|---|
| **Navidrome** | 4533 | Library `/mnt/music/library` (NVMe). Container config `/data/navidrome.toml`. **Incremental scan adds but does NOT purge ghost entries — after big moves a full scan from UI is needed. Fix post-import rescan or downloaded tracks won't appear (UX killer).** |
| **slskd** | 5030/5031/50300 | Soulseek daemon, API enabled. Creds via env from age vault, never in cleartext. Rate-limit ~120 searches/h + backoff already in `fetch`. Peer slots/speed variable (seconds → minutes). Known Soulseek blocklist on some artist names → tool retries variants automatically. |
| **Lidarr** | 8686 | Does move+rename (`{Artist} - {Album} - {nn} - {Title}` + `[Opus-192]` suffix from pipeline). Manual Import from UI for non-Lidarr downloads. |
| ***arr / jellyfin / qbit** | various | `~/docker/docker-compose.yml`. In-container paths kept = old OSMC paths (radarr/sonarr `/media/stuff`, lidarr `/data`). |
| **music-guru** | — | `~/music-guru/music_guru.py`: `scan` (Essential/Deep Cuts/Hidden Gems + `l0` for one-hit-wonders), `fetch <json> --commit` (slskd enqueue, per-album strategy, duration/artist filters, stale-requeue >24h excluding failed peer), `compare` (POSSESSED/IN-DOWNLOAD/MISSING + STRAY, same match rules as fetch), `retag --hours N --commit` (**ALWAYS after move/import**, MusicBrainz as truth, compilation drain to original album), `fixtags`, `audit`, `consolida [--queue] [--commit]` (**the import worker: lock `data/consolida.lock`, states queued→running→done/failed, reports + machine-readable manifests in `data/consolida_reports/`, trash-dated recoverable, never `rm`, m4a→Opus auto, junk-tag albums to `_incoming/`**). Tests: `python3 tests/test_safety.py`. Dedicated agent: `/agent music-scout`. |
| **Daily pipeline** | 02:30 | `run_daily.sh`: fstrim → `convert_flac.sh` (FLAC/MP3/M4A→Opus 192k) → `rinomina_opus.sh` → `check_rg2.sh` (rsgain ReplayGain) → `get_lyrics.py` → rsync backup to `/mnt/stuff/music/library`. Don't fight it. |
| **Config backup** | 03:15 | Hardlink incrementals, sqlite `.backup` Sundays only (lidarr.db ~1.4G, ~40 min). |

**Removed long ago, do NOT reintroduce:** `beets`, headphones, spotdl, Kodi, HASS. Retag = `music-guru` + Lidarr, not beets (see §10 Q1).

---

## 4. Pi Access from PC (Cloudflare Tunnel)

The Pi is reachable from the dev PC via `cloudflared` tunnel — no LAN/VPN needed:

```bash
# ~/.ssh/config (PC)
Host pi-dev
    HostName pi.tuodominio.dev
    User micho
    ProxyCommand cloudflared access ssh --hostname %h

# Port-forward session (when needed)
ssh pi-dev -L 4533:localhost:4533 -L 8000:localhost:8000
# NOTE: forward 5030 (slskd) only for debugging; the app must talk
# to guru-api :8000, never directly to slskd.
```

Rules:
- The phone/app talks to **Subsonic API + `guru-api` only**, both behind the tunnel with an auth token. Never expose slskd raw.
- **Secrets never leave the Pi vault** (`~/.secrets.age`, age key `~/.config/age/keys.txt`). No `.env` / `.arl` / `slskd.yml` copies to PC, no secrets in git, argv, logs or screenshots. Stack starts with `~/docker/up.sh up -d` (decrypts vault → process env; never `--env-file`, breaks on `$` in passwords).
- Validate compose changes with `docker compose config` before `up.sh`.

---

## 5. What To Build

### 5.1 `guru-api` (Pi, FIRST — thin FastAPI wrapper, no Celery)

Python 3.11+, FastAPI + `httpx` + `asyncio` (Pi 4: no Celery worker). It shells out to the existing CLI — do not reimplement matching/scan rules.

Proposed contract:

| Method | Path | Maps to |
|---|---|---|
| `GET` | `/discover?q=` | external catalog (Deezer/MusicBrainz, shared scan rules: merge remaster/deluxe variants, remix only L2+, denylist) |
| `POST` | `/slskd/enqueue {artist, title, album?}` | `fetch` (returns job id; stale-requeue + failed-peer exclusion built in) |
| `GET` | `/slskd/status/{id}` | slskd queue state + `data/consolida_queue.json` states (queued→running→done/failed) |
| `POST` | `/import` | `consolida --queue --commit` (lock-safe, re-entrant) + Navidrome rescan trigger |

Per-job pipeline: slskd search → pick (flac>aac>mp3≥256k, free slot→speed, basename/duration±20%/artist filters) → download → wait (async poll, "add and I'll notify" UX — slskd is NOT instant) → `retag` → move to `/mnt/music/library` (trash-dated dups, `(v2)` variants, junk to `_incoming/`) → Navidrome `GET /rest/startScan?fullScan=true` (Navidrome extension to Subsonic API, verified in docs — server-side after import, not from the phone) → status `completed` + manifest path. Full scan purges ghosts; quick scan does not. Cost: full scan on Pi 4 takes minutes — trigger it server-side once per import, and poll `getScanStatus` before marking `completed`.

### 5.2 PWA for iPhone (SECOND)

Plain web client: Subsonic API for library + `guru-api` for discovery/button. Installable from Safari, background playback OK. No CarPlay (needs native app + Apple entitlement) — car via Bluetooth.

### 5.3 Player Fork (LAST — on PC)

- Upstream `https://github.com/eddyizm/tempus`, add as `git remote upstream`. Fork location TBD. Package `com.tuonome.Rubato` to coexist with stock Tempus.
- **Build flavor `tempus` ONLY** (`assembleTempusDebug` / `assembleTempusRelease`). Verified in `/tmp/opencode/tempus`: all Auto code (`AutomotiveRepository`, `MediaBrowserTree.kt`, `MediaService`, `ic_aa_*`) lives in the `src/tempus/` source-set; the `degoogled` flavor (`com.eddyizm.degoogled.tempus`) excludes Auto + Chromecast.
- **Stack is Java/XML + Media3/ExoPlayer** (Retrofit/OkHttp, Room, Glide), NOT Compose-first. Compose exists only in small components (e.g. `NowPlayingArtworkPager.kt`). Discovery work means: `subsonic/api/<domain>/` (`*Service.java` Retrofit + `*Client.java`) → add parallel `guru/api/` client with second configurable URL; extend `SearchingClient` + `SearchViewModel.java` + `fragment_search.xml` (+ `SearchFragment.java`) with a Discovery tab + slskd button → `guru-api`; enqueue → WorkManager polls `guru-api` → server does the import + `startScan?fullScan=true` itself → phone only refreshes + plays + notifies.
- APK via Android Studio, sideload; on phone enable Developer options → Android Auto Developer settings → **"Unknown Sources"**. Test with DHU emulator + real head unit.

---

## 6. Player Choice & Rationale (corrected)

| Player | Android Auto | UI Stack | Verdict |
|---|---|---|---|
| Ultrasonic | ✅ mature | XML/Views | verbose for fast UI work |
| Vibrdrome (`ddmoney420`, MIT) | ✅ | Compose | upstream stalled ~Apr 2026 → bit-rot risk on a daily driver |
| **Tempus (`eddyizm/tempus`, GPL-3.0)** | ✅ (flavor `tempus` only) | **Java/XML + Media3, active (v4.26.1 Sep 2026, ~1.1k stars), Navidrome-supported** | **chosen** |
| DSub / Subtracks | ✅ / ⚠️ | Java/XML / Flutter | legacy / Flutter+AA pain |

Earlier draft wrongly described Tempus as "Compose + Material3, 5-10x faster" — that is not the codebase. Plan Fragment-based work accordingly.

---

## 7. Fork Strategy (living fork)

```bash
git clone https://github.com/TUO_USER/tempus.git Rubato
cd Rubato
git remote add upstream https://github.com/eddyizm/tempus.git
# upstream updates: git fetch upstream && git merge upstream/main
# PRs against upstream branch `development`
```

1. Isolate new code in separate packages, touch existing files minimally: Discovery → `com.Rubato.ui.discovery.*`, download → `com.Rubato.download.*`, middleware client → `com.Rubato.api.*`.
2. Hook navigation with one route + one bottom-bar item.
3. Toolchain (verified): Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10, Java 21 via Foojay, compileSdk/targetSdk 36, minSdk 24. FFmpeg decoder = local AAR (`libs/`, `bin/build.sh`). Tests local-only, no CI.

Rebranding: `app_name`, `applicationId`/`namespace`, `res/mipmap-*` icons, splash/theme, About screen (keep GPL-3.0 authors credited).

---

## 8. Feature Plan

### 8.1 Discovery (external catalog first)

`guru-api /discover` (Deezer/MB, shared `music-guru` rules) rendered in the new Discovery tab with per-track "owned / in-download / missing" badges from `compare` logic. Owned-library sections (`getRandomSongs`, `getAlbumList2?type=newest|frequent`, `getGenres`, ListenBrainz optional) are cheap extras.

### 8.2 One-Click Download

Tap ⬇️ → `POST /slskd/enqueue` → `job_id` → toast "queued — will notify" → WorkManager polls `/slskd/status/{id}` → on `completed` (server already ran `startScan?fullScan=true`), phone refreshes → play. Handle gracefully: slskd delays, 120/h rate-limit, MB evening 503s (degraded + explicit report), no-m4a (server converts), trash-safe server side.

### 8.3 Android Auto

Inherited. **Phone enqueues, car only browses/plays/searches** (template limitation). Verify custom `MediaBrowser` roots handle Discovery; test with "Unknown Sources".

---

## 9. Development Phases

| Phase | Goal | Depends |
|---|---|---|
| **0 — Validate** | Clone Tempus on PC, `assembleTempusDebug`, connect to Pi Navidrome via tunnel, verify AA | PC + tunnel |
| **1 — guru-api** | Scaffold FastAPI on Pi: discover/enqueue/status/import over `music-guru` + **fix Navidrome rescan** | Pi |
| **2 — Download button** | `GuruService`/`GuruClient`, ⬇️ button, polling, notify | Phase 1 |
| **3 — Discovery UI** | Discovery tab (external catalog + `compare` badges) | Phase 1 |
| **4 — AA check** | Expose playable results in `MediaBrowser` tree | Phase 3 |
| **5 — Polish** | Job queue UI, errors, PWA, ListenBrainz | all |

Hobby estimate: backend 2–4 d (code exists) · PWA 1–2 w · fork + Auto test 1–2 w.

---

## 10. Pi-Side Answers (formerly open questions — resolved here)

1. **Retag pipeline?** No beets (removed long ago). Use `music-guru`: `fetch` → Lidarr Manual Import → **always** `retag --hours N --commit` → optional `fixtags`. Callable via shell from `guru-api`. Conventions: Lidarr naming, `[Opus-192]` suffix, trash-dated (never `rm`), manifests in `data/consolida_reports/`.
2. **slskd auth?** Env from age vault (`SLSKD_*`), localhost:5030. PC/app never sees creds — only `guru-api` token.
3. **Scan trigger?** Subsonic `startScan`, BUT incremental doesn't purge/index reliably → **full scan needed after big imports**. Navidrome extends the API: `GET /rest/startScan?fullScan=true` (verified in Navidrome docs, plus `getScanStatus` with `scanType`). Phase 1 fix: `guru-api` triggers the full scan itself post-import and polls status before marking the job `completed`. Phone never calls `startScan` directly.
4. **Library path?** `/mnt/music/library`. No separate beets out-dir; `consolida` stages (`_staging_place`, `_incoming/`) then moves there.
5. **Resources?** Pi 4 shared with Navidrome/slskd/*arr — **no Celery**, plain `asyncio` + subprocess.
6. **Automation conflicts?** Daily pipeline 02:30, backup 03:15 (Sun ~40 min), cloudflared watchdog 4h, bridge health 4h. Don't run heavy imports against those windows.
7. **Network?** Soulseek flaky by nature — variable slots/speed, search timeouts, artist blocklist workarounds, MB throttling. Server already retries/excludes failed peers; app must be notify-later, not blocking.

---

## 11. House Rules (from the Pi — mandatory)

- **ABSOLUTE (passwords):** never print/show/echo secrets in output, commands, files or commits. Vault `~/.secrets.age` only; user decrypts (`~/.secrets.sh show`), rotation via `~/secrets/rotate_secrets.py` (dry-run default). Never commit `.env`, `slskd.yml`, `.arl`, `navidrome.toml` (LastFM keys), `.qbpass`, `*.env.cred`.
- Media on `/mnt/music/library` is irreplaceable; backup on `/mnt/stuff/music/library`. Dry-run destructive ops.
- All scripts log in Italian. Don't be alarmed by "Trovati", "Rinomino", etc.
- After staging: check `find /mnt/music/library -type d \( -name "* " -o -name "*." \)` (trailing-space/dot ghosts).
- Never `os.remove`; trash-dated. `consolida --commit` aborts if >max(10,30%) moves without `--force`, then reconciles.
- No Google Nest ("Blackie") tests without explicit permission.

---

## 12. Tech Stack Summary

| Layer | Technology |
|---|---|
| Player (Android) | Java/XML + Kotlin bits, Media3/ExoPlayer, Retrofit/OkHttp, Room, Glide; flavor `tempus` |
| Player networking | OkHttp/Retrofit → Subsonic API + `guru-api` (second base URL + token) |
| Backend (Pi) | Python 3.11+, FastAPI, httpx, asyncio wrapping `music-guru` |
| Organize/retag | Lidarr + `music-guru` (mutagen + MusicBrainz), Opus-192 pipeline |
| Music server / P2P / tunnel | Navidrome :4533 / slskd :5030 / cloudflared |
| Dev | Android Studio + DHU, Git, SSH via `pi-dev`, ADB |

---

## License & Credits

- Tempus: GPL-3.0 by [eddyizm](https://github.com/eddyizm/tempus) (fork of Tempo v3.9.0 by CappielloAntonio). Fork stays GPL-3.0, sideload for personal use; share sources if distributing outside the private circle. Keep authors credited in About.
