# StudentLife Dataset — Inventory & Retrospective-Retrieval Assessment

**Source:** StudentLife (Dartmouth College, spring 2013), downloaded from a Kaggle mirror.
**Location in repo:** `ml/data/raw/studentlife/` (gitignored via `ml/data/raw/*`; verified — nothing appears as untracked in `git status`).
**Do not modify** anything under `ml/data/raw/`. This document is read-only analysis; no pipeline code has been written.

> Android retrievability rows below are a **planning assessment**, not verified platform facts. Per `CLAUDE.md`, current Android background-execution / permission / Play-policy rules are marked **NEEDS-VERIFICATION** or **NEEDS-DEVICE-TEST** and must be confirmed against official docs / a real device before we rely on them.

---

## 1. At a glance

| Property | Value |
|---|---|
| Total size on disk | **2.8 GB** |
| Total files (excl. `.DS_Store`) | 1,984 |
| Participants | **49** (ids `u00`–`u59` with gaps — matches the official Dartmouth release of 49 users) |
| Sensing study window | **2013-03-27 → 2013-06-01** (~10 weeks, spring term) |
| Passive-log window (app/call/sms) | poll-time within study; **logged-event times reach back to Feb 2013** (historical phone logs) |

### Size by top-level stream
| Stream | Size | Stream | Size |
|---|---|---|---|
| `sensing/` | 2.4 GB | `EMA/` | 6.3 MB |
| `app_usage/` | 429 MB | `dinning/` | 368 KB |
| `sms/` | 14 MB | `calendar/` | 248 KB |
| `call_log/` | 7.7 MB | `survey/` | 184 KB |
| | | `education/` | 44 KB |

### `sensing/` subfolders (all present)
`activity`, `audio`, `bluetooth`, `conversation`, `dark`, `gps`, `phonecharge`, `phonelock`, `wifi`, `wifi_location` — **10 subfolders**.

---

## 2. Completeness vs. official Dartmouth release

**Verdict: the mirror looks complete and untruncated.** All expected sensing subfolders and all headline components are present, participant count and study dates match the published dataset.

- **Participants:** 49 across every sensing stream — matches the official 49. ✅
- **Expected sensing subfolders** (activity, bluetooth, conversation, dark, gps, phonecharge, phonelock, wifi): **all present**, ✅ plus `audio` and `wifi_location` (also part of the official release).
- **Dates:** sensing spans 2013-03-27 → 2013-06-01, the documented ~10-week term. ✅
- **Labels:** `EMA/` (26 instruments incl. Stress, PAM, Mood, Sleep, Social, Behavior) and `survey/` (8 validated instruments, pre/post) are present. ✅

**Extras beyond the headline release** (present here, useful to us): `app_usage/`, `call_log/`, `sms/`, `calendar/`, `education/`, `dinning/`.

**Caveats / things to watch (not truncation, but data-quality):**
- **`call_log/` & `sms/` are sparse at the *event* level** — see §4. All 49 have files, but most rows are empty poll heartbeats.
- `calendar/` covers **28/49** users; `dinning/` **31/49** — expected (opt-in / derived), not a mirror defect.
- A handful of `timestamp`-column outliers in app/call/sms reach into late 2013–Jan 2014 (stale poll rows); the actual *logged-event* dates stay within the study.

---

## 3. Per-stream inventory

Legend for cadence: cadence is the *within-active-sampling* median inter-record gap; several streams sample in bursts with long idle gaps (phone off / not charging).

### Sensing streams (all 49 participants, all CSV)

| Stream | File / naming | Columns | Cadence | Coverage | Missingness / notes |
|---|---|---|---|---|---|
| **activity** | `activity_u##.csv` | `timestamp, activity inference` | ~3 s (bursts) | 49/49 | `activity inference` code: **0=stationary, 1=walking, 2=running, 3=unknown**. Huge files (u00 ≈ 924k rows). Gaps when phone off. |
| **audio** | `audio_u##.csv` | `timestamp, audio inference` | ~1 s | 49/49 | Code: **0=silence, 1=voice, 2=noise, 3=unknown**. Largest stream (1.3 GB; u00 ≈ 5.1M rows). Inference only — **no raw audio**. |
| **conversation** | `conversation_u##.csv` | `start_timestamp, end_timestamp` | episodic | 49/49 | Derived conversation episodes (from audio). One row per detected conversation. |
| **gps** | `gps_u##.csv` | `time, provider, network_type, accuracy, latitude, longitude, altitude, bearing, speed, travelstate` | ~20 min | 49/49 | Periodic fixes. `travelstate` ∈ {stationary, moving}. Sparse rows (u00 ≈ 4.3k). |
| **bluetooth** | `bt_u##.csv` | `time, MAC, class_id, level` | burst scans | 49/49 | Nearby BT devices per periodic scan; `level` = RSSI. MAC is raw in this stream. |
| **wifi** | `wifi_u##.csv` | `time, BSSID, freq, level` | burst scans | 49/49 | Nearby APs per scan; `level` = RSSI. Large (700 MB; u00 ≈ 507k rows). |
| **wifi_location** | `wifi_location_u##.csv` | `time, location` | ~20 s | 49/49 | Named campus location inferred from Wi-Fi (e.g. `in[east-wheelock]`). |
| **dark** | `dark_u##.csv` | `start, end` | episodic | 49/49 | Episodes where ambient light ≈ dark (proxy for phone-in-pocket/night). epoch pairs. |
| **phonecharge** | `phonecharge_u##.csv` | `start, end` | episodic | 49/49 | Charging episodes (epoch start/end). |
| **phonelock** | `phonelock_u##.csv` | `start, end` | episodic | 49/49 | Screen-locked episodes (epoch start/end). |

All epoch timestamps are **Unix seconds, UTC**.

### Label streams

**`EMA/` — Ecological Momentary Assessment (in-the-moment self-report; the primary labels).**
`EMA/EMA_definition.json` defines the instruments; `EMA/response/<Instrument>/<Instrument>_u##.json` holds per-user responses as a JSON array, each entry `{ ...answers, resp_time (epoch s) }`. 26 instruments total; the ones relevant to us:

| Instrument | Construct | Item / value range | Responses/participant (min / median / max) | Total |
|---|---|---|---|---|
| **Stress** | Momentary perceived stress | `level` 1–5 — **non-monotonic**: 1=a little stressed, 2=definitely stressed, 3=stressed out, **4=feeling good, 5=feeling great** | 4 / 41 / 269 | 2,408 (2,167 with a `level`; 241 location-only pings) |
| **PAM** | Affect (Photographic Affect Meter) | picture selection → affect grid | 8 / 195 / 437 | 9,040 |
| **Mood** | Momentary happy/sad | happy 1–4, sad 1–4 (+ yes/no gates) | 0 / 2 / 83 | 277 |
| **Sleep** | Prior-night sleep | hours 1–19 (coded ≤3h…12h), rate 1–4, trouble-staying-awake 1–4 | 6 / 31 / 73 | 1,644 |
| **Social** | Social contact count | 1–6 bucket (0–4 … >100 people) | 6 / 27 / 79 | 1,410 |
| **Behavior** | Big-Five-style momentary behavior | 5 items, 1–5 | 0 / 10 / 96 | 814 |
| **Activity** | Momentary activity | multi-item | 0 / 14 / 55 | 833 |
| **Exercise** | Exercise self-report | multi-item | 0 / 11 / 44 | 763 |

> **Stress-label caveat (important for modeling):** the 1–5 scale mixes stress magnitude (1–3) with positive affect (4–5); it is **not** an ordinal stress axis. We must remap (e.g. collapse to stressed {1,2,3} vs. not-stressed {4,5}, or treat as categorical) — do not average it. Response counts are highly uneven per participant (some users have only single-digit responses); LOSO folds must tolerate label-poor users.

**`survey/` — validated pre/post questionnaires (trait / baseline, not momentary).** One CSV per instrument; `uid,type(pre|post),<items…>,Response`. ~46–47 unique participants each; `pre` n≈46, `post` n≈37–39 (post-term attrition).

| File | Instrument | Construct | Range |
|---|---|---|---|
| `PHQ-9.csv` | Patient Health Questionnaire-9 | Depression severity | per-item 0–3 (“Not at all”…“Nearly every day”), sum 0–27 |
| `PerceivedStressScale.csv` | PSS(-10) | Perceived stress (last month) | per-item 0–4, sum 0–40 |
| `panas.csv` | PANAS | Positive/negative affect | 20 items, 1–5 |
| `FlourishingScale.csv` | Flourishing Scale | Psychological well-being | 8 items, 1–7 |
| `LonelinessScale.csv` | UCLA Loneliness | Loneliness | 20 items, 1–4 |
| `psqi.csv` | PSQI | Sleep quality | component + global scores |
| `BigFive.csv` | Big Five Inventory | Personality (OCEAN) | 44 items, 1–5 |
| `vr_12.csv` | VR-12 | Health-related QoL | mixed |

**Other context streams:** `education/` — study-level `class.csv`, `deadlines.csv`, `grades.csv`, `piazza.csv`, `class_info.json` (not per-participant sensor data). `calendar/` — 28 users, `id,device,timestamp,ACCOUNT_LABEL,DATE,TIME`. `dinning/` — 31 users, per-user `.txt` dining-hall visits.

---

## 4. Focus: `app_usage/`, `call_log/`, `sms/` (our retrospective backbone)

These weren’t in StudentLife’s headline analysis, so I checked them hard. **Headline: all three have files for all 49 users, but call/SMS are sparse at the *event* level, and `app_usage` uses a mechanism that no longer works on modern Android.**

Shared shape: `id, device, timestamp, <payload columns…>`. `timestamp` (col 3) is the **poll/collection time**; the real event time lives in the payload (`CALLS_date`, `MESSAGES_date`, both **epoch milliseconds**). Rows with an empty payload are **empty poll heartbeats**.

| Stream | Users w/ files | Total rows | Rows/user (min·median·max) | **Users with real events** | Notes |
|---|---|---|---|---|---|
| **app_usage** | 49/49 | 1,990,510 | 5,403 · 34,174 · 149,969 | 49/49 (all polled) | Payload = `RUNNING_TASKS_*` (a live snapshot of running/foreground tasks each poll). **No historical event dates** — it is a real-time poll, not a backfillable log. |
| **call_log** | 49/49 | 71,801 | 534 · 1,487 · 2,215 | **20/49** (6,452 real call events) | 29 users have **zero** populated call rows — only id/device/timestamp; payload empty. |
| **sms** | 49/49 | 92,584 | 534 · 1,487 · 5,292 | **23/49** (29,359 real SMS events) | 26 users have **zero** populated SMS rows. |

**Interpretation / implications for our design:**
- **`call_log` / `sms` are genuinely sparse.** Despite 49 files each, usable message/call *events* exist for only ~20 and ~23 participants respectively — well below the 49 with sensing. Any model leaning on call/SMS features should expect to train/evaluate on a **~40–47% subset** and must degrade gracefully when a user has none. The empty users are most plausibly permission/probe failures, not “no communication.”
- **The good news for the retrospective thesis:** where call/SMS *are* populated, the logged event times **predate the study (down to Feb 2013)** — proof that StudentLife obtained them by reading the phone’s **stored CallLog / SMS ContentProviders at enrollment**, i.e. the exact retrospective-backfill mechanism our app depends on. That validates the design in principle.
- **`app_usage` does NOT validate retrospective app usage.** It captured `getRunningTasks()`-style live snapshots, which (a) is deprecated/blocked on modern Android and (b) carries no historical timeline — you can’t backfill it. Our retrospective app-usage must instead come from `UsageStatsManager` (different API, retention-limited) — see table below.
- **PII is hashed:** call/SMS numbers, contact names, and **SMS bodies** are all stored as `{"ONE_WAY_HASH":"…"}` (SHA-1), not plaintext. Good precedent, and consistent with our typing/content-privacy rule — our own pipeline must likewise derive **metadata only** (counts, timing, length), never content.

---

## 5. Retrospective retrievability on Android

Does today’s Android let a freshly-installed app **backfill** each StudentLife signal from what the device already stored? `YES` = a documented API exposes history; `NO` = only collectable going-forward (or not at all); `NEEDS-DEVICE-TEST` = plausible but retention/OEM/Play-policy dependent and must be measured on-device.

All platform-policy specifics below are **NEEDS-VERIFICATION** against current official docs before we commit.

| StudentLife stream / column | Retrievable retrospectively on Android? | Notes |
|---|---|---|
| **call_log** — date, duration, type, number(hashed), name | **YES** | `CallLog.Calls` ContentProvider stores full history. Needs `READ_CALL_LOG` (runtime) + Play “Calls & Texts” policy exemption. Number/name → hash only. |
| **sms** — date, address(hashed), type, thread_id | **YES** | `Telephony.Sms` ContentProvider stores full inbox/sent history. `READ_SMS` is a **Play-restricted** permission (default SMS handler / exemption). |
| **sms** — body | **NO (by policy, ours)** | Technically readable, but our content-privacy rule forbids storing it. Derive length/metadata only; never persist characters. |
| **calendar** — events, times | **YES** | `CalendarContract` exposes past & future events. `READ_CALENDAR` (runtime). |
| **app_usage** — foreground app & time | **NEEDS-DEVICE-TEST** | Not via StudentLife’s `getRunningTasks` (blocked). Use `UsageStatsManager.queryUsageStats/queryEvents` — retrospective but **retention-limited** (daily buckets kept longer than raw events; OEM-dependent). Needs `PACKAGE_USAGE_STATS` (Settings special access). Measure real retention window. |
| **app_usage** — `RUNNING_TASKS_*` exact columns | **NO** | `getRunningTasks()` deprecated/neutered since Android 5+. Not reproducible. |
| **phonelock** — screen lock/unlock episodes | **NEEDS-DEVICE-TEST** | `UsageStatsManager` events include screen-interactive / keyguard events within its retention window; else collect going-forward via broadcasts (`YES` forward). |
| **phonecharge** — charging episodes | **NEEDS-DEVICE-TEST** | No app-accessible historical charge log (`dumpsys batterystats` is not app-reachable). Going-forward via `ACTION_POWER_CONNECTED/DISCONNECTED` = **YES forward, NO backfill**. |
| **gps** — lat/long history | **NO (direct) / NEEDS-DEVICE-TEST (export)** | No OS API returns historical fixes. Only Google **Maps Timeline / Location History** if the user enabled it, via user-initiated Takeout export → NEEDS-DEVICE-TEST. Going-forward via `FusedLocationProvider` = YES forward. |
| **activity** — inference (still/walk/run) | **NO backfill / NEEDS-DEVICE-TEST (Health Connect)** | `ActivityRecognition` is live-only. Historical steps/activity **may** exist in **Health Connect / Google Fit** if the user used a fitness app → NEEDS-DEVICE-TEST. Going-forward = YES. |
| **audio** — inference (silence/voice/noise) | **NO** | No stored history; mic capture is live-only and privacy-sensitive. Forward-only if at all. |
| **conversation** — episodes | **NO** | Derived from audio; no backfill. Forward-only. |
| **bluetooth** — nearby-device scans | **NO** | No scan history stored; live scans forward only (and scan throttling applies). |
| **wifi** — nearby-AP scans (BSSID/RSSI) | **NO** | No scan history; live scans forward, throttled since Android 9. |
| **wifi_location** — inferred named place | **NO** | Derived from live Wi-Fi; no backfill. |
| **dark** — ambient-dark episodes | **NO** | Light-sensor history not stored; forward-only. |
| **EMA (Stress/PAM/Mood/Sleep/…)** | **N/A (self-report)** | Not device-retrievable; collected via our own in-app prompts. |
| **survey (PHQ-9, PSS, PANAS, …)** | **N/A (self-report)** | Collected via our own onboarding questionnaires. |

**Takeaways for the retrospective design:**
1. **The retrospectively-backfillable core is small:** `call_log`, `sms` (metadata only), `calendar`, and — pending on-device retention testing — `app_usage` (via `UsageStatsManager`), `phonelock`. Everything sensor-derived (activity, audio, conversation, bluetooth, wifi, gps, dark) is essentially **forward-collection only**.
2. Of that core, **call/SMS coverage in StudentLife itself is only ~20–23/49 users**, so any retrospective baseline built on them is thin — plan for graceful degradation and lean on `app_usage`/`calendar`/`phonelock` for breadth.
3. Confirm every **NEEDS-DEVICE-TEST / NEEDS-VERIFICATION** row on a real device + current Play policy before committing feature-spec entries.

---

*Generated as inventory only — no pipeline code written, no files under `ml/data/raw/` modified.*
