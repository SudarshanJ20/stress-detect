# Feature Specification — SINGLE SOURCE OF TRUTH

```
SPEC_VERSION: v0.6.0
```

This file is **authoritative**. Both the Android extractor (`android/…/features`) and the
Python extractor (`ml/src/features`) MUST implement exactly these features and produce
**identical vectors from identical input**. The parity test compares both against
`fixtures/synthetic_trace.json`.

**Versioning:** bump `SPEC_VERSION` on ANY change (adding/removing/redefining a feature,
window, unit, or missing-data rule). Both implementations expose the `SPEC_VERSION` they
implement; the parity test asserts they match; the value is embedded in exported ONNX
model metadata.

**Rules for filling this in (team):**
- Every cell marked `TODO` is to be filled by the team. **Do not invent thresholds or
  magic numbers.**
- `missing-data rule` must say exactly what happens when the signal is absent for a window
  (e.g. impute / null / drop window) — never leave silent behavior.
- `android_source` / `python_source` name the concrete source API / column.

Columns: `feature_name | definition | aggregation_window | unit | dtype | missing-data
rule | android_source | python_source | studentlife_coverage | retrospective_availability`

`studentlife_coverage` is **informational only** — it records how many of StudentLife's
49 bootstrap participants have real (non-empty) data for the backing StudentLife stream,
so we know where the bootstrap signal is thin. It is **not** part of the parity vector and
does not affect the parity test. `— (not in StudentLife)` means the feature has no
StudentLife source and will come only from our own collection or a modern-device API.

`retrospective_availability` is **RESOLVED** by the on-device probe (Samsung Galaxy S24
Ultra, Android 16 / API 36, run 2026-08-15 — full output in
`docs/device-probe-results.md`). It answers: at install time, can the feature be
backfilled from history already on the device? `YES` (with the API + retention window),
`NO`, or `N/A` (forward-collected only / derived from the clock). Also informational, not
part of the parity vector. See **Feature scope constraints** below for how these map to
backbone / auxiliary / out-of-scope.

---

## Typing
> ⚠️ Timing-derived features ONLY. NEVER characters. No cell here may reference typed
> content. The collector must be architecturally incapable of receiving text.

| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage | retrospective_availability |
|---|---|---|---|---|---|---|---|---|---|
| inter_key_interval_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) | N/A — forward-collected only |
| inter_key_interval_iqr | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) | N/A — forward-collected only |
| backspace_rate | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) | N/A — forward-collected only |
| pause_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) | N/A — forward-collected only |

## Touch
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage | retrospective_availability |
|---|---|---|---|---|---|---|---|---|---|
| tap_rate | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) | N/A — forward-collected only |
| swipe_velocity_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) | N/A — forward-collected only |

## Usage
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage | retrospective_availability |
|---|---|---|---|---|---|---|---|---|---|
| screen_on_time | TODO | TODO | TODO | TODO | TODO | `sensing/UsageEventsSource` → locked intervals (§8) → `features/ScreenLockFeatures` | TODO | 49/49 (phonelock episodes) | **YES — queryEvents SCREEN on/off, ~10d retention (BACKBONE)** |
| unlock_count | TODO | TODO | TODO | TODO | TODO | `sensing/UsageEventsSource` KEYGUARD_HIDDEN (§8) | TODO | 49/49 (phonelock episodes) | **YES — queryEvents KEYGUARD_HIDDEN, ~10d (BACKBONE)** |
| app_switch_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (app_usage — RUNNING_TASKS poll, not a usage timeline; see constraints) | YES — UsageStats INTERVAL_DAILY, ~10d (WEEKLY ~3.5wk, MONTHLY ~5mo but coarse) |

## Mobility
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage | retrospective_availability |
|---|---|---|---|---|---|---|---|---|---|
| location_variance | TODO | TODO | TODO | TODO | TODO | FusedLocationProvider (TODO) | TODO | 49/49 (gps) | NO — OUT OF SCOPE (no location history on device; GPS is forward-only) |
| places_visited | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (gps) | NO — OUT OF SCOPE (no location history) |
| time_at_primary_location | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (gps) | NO — OUT OF SCOPE (no location history) |

## Sleep-proxy
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage | retrospective_availability |
|---|---|---|---|---|---|---|---|---|---|
| inferred_sleep_duration | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (phonelock/dark/activity; Sleep EMA for reference) | **YES — screen-off gaps via queryEvents, ~10d (BACKBONE; screen/lock-derived, NOT Health Connect sleep)** |
| longest_inactive_window | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (phonelock/dark/activity) | **YES — queryEvents SCREEN/KEYGUARD, ~10d (BACKBONE)** |

## Motion
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage | retrospective_availability |
|---|---|---|---|---|---|---|---|---|---|
| activity_fraction_still | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (activity inference) | NO — OUT OF SCOPE (activity recognition is forward-only; no history) |
| step_count | TODO | TODO | TODO | TODO | TODO | Health Connect / sensors (TODO) | TODO | — (not in StudentLife; activity inference has no step count) | NO — OUT OF SCOPE (Health Connect empty on probe device: 0 Steps records) |

## Social
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage | retrospective_availability |
|---|---|---|---|---|---|---|---|---|---|
| comms_app_time | TODO | TODO | TODO | TODO | TODO | TODO | TODO | call_log 20/49, sms 23/49 (sparse — NOT a backbone; see constraints) | YES (AUXILIARY) — CallLog ~2mo [exactly 1998 rows → likely a cap, NEEDS-VERIFICATION], SMS ~10mo; REQUIRES missingness indicator |
| notification_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) | N/A — forward-collected only |

## Ambient
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage | retrospective_availability |
|---|---|---|---|---|---|---|---|---|---|
| ambient_light_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (dark; episodic dark-periods only, no raw light level) | NO — OUT OF SCOPE (no light-sensor history) |
| time_of_day_bucket | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (derived from clock) | N/A — derived from clock (always available) |

---

## Feature scope constraints

Findings from the StudentLife bootstrap inventory (`docs/dataset-inventory.md`). These
constrain how features may be built and are **binding on both extractors**.

### 1. Sparse communication/calendar streams are NOT backbone features
Real (non-empty) StudentLife coverage:
- **`call_log` — 20/49 participants** have any real call events (the other 29 are empty
  poll heartbeats).
- **`sms` — 23/49 participants** have any real message events (the other 26 are empty).
- **`calendar` — 28/49 participants** have data.

Rules:
- These streams **must not be backbone features** — no feature the model *depends on* may
  be derived from `call_log`, `sms`, or `calendar`.
- They **may** be used as *auxiliary* inputs **only if paired with an explicit
  per-window missingness indicator** (a companion binary/`is_present` feature), so the
  model can learn to ignore them when absent. A feature derived from these streams is
  never allowed to be silently zero/imputed without its missingness flag.
- Any model or ablation must remain valid (train, evaluate, forecast) for a participant
  who has **none** of these three streams. Graceful degradation is a requirement, not a
  nice-to-have.

### 2. `app_usage` in StudentLife is a poll, not a usage timeline
- StudentLife's `app_usage` is a periodic **`getRunningTasks()` (RUNNING_TASKS) poll** —
  a snapshot of running/foreground tasks at each sample. It is **not** a foreground-usage
  timeline and carries **no historical event times**.
- It is therefore **not directly equivalent** to Android's `UsageStatsManager`
  (`queryUsageStats` / `queryEvents`) output, which our app will use.
- **Any mapping from StudentLife `app_usage` → a `UsageStatsManager`-style feature must be
  explicitly documented and justified** in this spec (definition, assumptions, and why
  the poll-derived quantity is a valid proxy) before it may back a feature. Until then,
  treat cross-source equivalence as unproven.

### 3. Era mismatch — StudentLife (2013, Android 4.x) ≠ modern device
- StudentLife was collected in **2013 on Android 4.x**, **before** `UsageStatsManager`
  (API 21, Android 5.0) and **before** Health Connect existed.
- Therefore: **absence of a stream in StudentLife does NOT imply it is unavailable on a
  modern device, and presence in StudentLife does NOT imply it is still retrievable the
  same way.** The `studentlife_coverage` column describes only the 2013 bootstrap corpus.
- Modern-device retrievability was **unresolved** at inventory time; it is now **RESOLVED
  by the on-device probe** (see §4 and `docs/device-probe-results.md`). The
  `retrospective_availability` column reflects those measured results, not StudentLife
  presence. Remaining unknowns (e.g. the CallLog record cap) stay **NEEDS-VERIFICATION**.

### 4. Retrospective-design scope (RESOLVED — on-device probe, Samsung S24 Ultra, Android 16 / API 36, 2026-08-15)

The probe measured how far back each source can be backfilled at install time. Result:

- **BACKBONE — screen / lock-derived features.** Full coverage on *both* sides:
  StudentLife `phonelock` **49/49**, and on-device **`UsageStatsManager.queryEvents`**
  (screen on/off, `KEYGUARD` lock/unlock) with **~10 days** retrospective retention. These
  are the only features available *and* dense on both the bootstrap corpus and a modern
  device, so the model's core must be built on them. Covers `screen_on_time`,
  `unlock_count`, `inferred_sleep_duration`, `longest_inactive_window`.
- **AUXILIARY — Call / SMS.** Retrospectively available (CallLog ~2 months — but exactly
  1998 rows came back, likely a **cap** rather than true retention, **NEEDS-VERIFICATION**;
  SMS ~10 months) but sparse in StudentLife (§1). Usable **only** with an explicit
  per-window missingness indicator; the model must never depend on them.
- **OUT OF SCOPE for the retrospective design:**
  - **Mobility (GPS / location)** — no historical location is retrievable on-device; GPS is
    forward-only.
  - **Activity / motion** — activity recognition is live-only; no history.
  - **Ambient (light)** — no light-sensor history.
  - **Bluetooth / Wi-Fi co-location** — no scan history; live scans only, throttled.
  - **Health Connect (Sleep, Steps)** — returned **0 records** on the probe device (nothing
    writes to Health Connect here), so it cannot seed a baseline. Do not build on it.

  Reason (common to all): the retrospective design backfills a baseline from what is
  *already stored* on the device at install; these sources keep **no queryable history**
  (or none on this device), so they cannot contribute to the retrospective window. They may
  still be collected *going forward* by the product, but they are excluded from the
  retrospective/bootstrap model.

### 5. Analysis window = **7 days**

The retrospective analysis/aggregation window is fixed at **7 days**. Rationale:
- The **binding constraint is `queryEvents` retention (~10 days)** on the probe device;
  the backbone (screen/lock) is only reliably backfillable within that window.
- **7 days leaves margin** below the ~10-day floor for OEM/usage variance, so a fresh
  install can always reconstruct a full window.
- It **matches K-EmoPhone's 7-day collection window**, keeping our retrospective baseline
  comparable to that reference dataset.

### 6. Implemented backbone/aux features & thresholds (Phase 2)

**Timezone.** All clock/date/night/sleep computation localizes epochs to
`America/New_York` (Dartmouth, spring 2013). **Asserted at runtime**
(`guards.assert_timezone`) — a wrong zone silently corrupts every clock feature.

**Thresholds** (single source: `ml/src/features/spec_constants.py`; each with rationale):

| constant | value | rationale |
|---|---|---|
| `WINDOW_DAYS` | 7 | analysis window (§5) |
| `COVERAGE_MIN_DAYS` | 3 | drop a subject-day whose 7-day window has <3 days of phonelock — too little to summarize |
| `MIN_SLEEP_MINUTES` | 90 | a locked interval shorter than this is a nap/idle, not main sleep |
| `SLEEP_MIDPOINT_BAND` | 20:00–12:00 | main sleep = longest locked interval whose local midpoint falls in this overnight band |
| `MAX_SESSION_MINUTES` | 180 | gaps between locks longer than this are phone-off/missing-data, not real use (screens auto-lock in minutes) |
| circular stats | — | clock-time central tendency/regularity use the mean-resultant vector so the midnight wrap is handled (regularity = circular SD) |
| duplicate-window collapse | — | adjacent subject-days with identical feature vectors (sparse-data artifact) are collapsed to one sample (label averaged) so they don't double-count in a LOSO fold |

**Backbone features** (from phonelock LOCKED intervals; `python_source =
ml/src/features/screenlock_features.py`; window = 7 d; missing → `NaN`, native to XGBoost):
sleep — `sleep_duration_median`, `sleep_onset_hours`, `sleep_wake_hours`,
`sleep_onset_regularity` (circular SD), `sleep_midpoint_regularity`, `n_sleep_nights`;
usage — `unlock_count_per_day_mean`, `unlock_count_sd`, `session_count_per_day_mean`,
`session_duration_median`, `session_duration_iqr`, `screen_on_fraction`;
night — `nighttime_use_fraction_personal`, `nighttime_unlock_per_day_personal` (person-
relative = each subject's own [median onset, median wake]; **primary**), plus
`nighttime_use_fraction_fixed`, `nighttime_unlock_per_day_fixed` (00:00–06:00, ablation only);
circadian — `circadian_regularity` (mean pairwise corr of daily hourly-use profiles);
coverage — `days_with_data`.

**Auxiliary features** (`aux_features.py`) — call/SMS, each with a mandatory `*_present`
missingness flag per §1: `call_count_per_day` + `call_present`, `sms_count_per_day` +
`sms_present`. Value is 0 when the subject lacks the stream, but `*_present=0` marks it.

**Sample = one subject-day** with ≥1 valid EMA (not one per EMA response — response-level
samples share ~99% of their 7-day window and collapse the effective n). Features come from
the 7-day window ending at that day's local-midnight boundary (the labelled day's own
behaviour never enters its features). Gated by `COVERAGE_MIN_DAYS`.

**Phase-2 result (honest).** With these backbone features + an XGBoost baseline under LOSO,
the model does **not** beat the per-subject-mean baseline (regression MAE 22.6 vs subject-
mean 17.0; Spearman −0.15) and sits at/below chance for the binary target (AUC ≈ 0.41).
In-sample fit is near-perfect (Spearman 0.92) and univariate feature↔stress correlations are
all |ρ| ≤ 0.14, so this is a **true null / negative-transfer** result, not a pipeline bug:
screen/lock behaviour alone does not carry cross-subject momentary-stress signal in
StudentLife. Motivates personalization / self-baseline and richer features in later phases.
Full numbers: `ml/src/training/run_baseline.py` output + `data/processed/baseline_metrics_v0.4.0.json`.

### 7. Temporal sequence representation & ONNX contract (Phase 4)

The Phase-4 temporal models consume the SAME 1157 samples/labels, but as a **7-day daily
sequence** instead of one flat aggregate. Two parts (`ml/src/features/sequence_dataset.py`):

- **Dynamic — `(7, 8)` per-day sequence** (oldest→newest day of the 7-day window):
  `[unlock_count, session_count, session_duration_median, screen_on_fraction,
  nighttime_use_fraction_fixed, call_count, sms_count, has_data]`. Missing day → 0 + the
  `has_data` mask channel.
- **Static — `(9,)`** window-level quantities undefined for a single day (sleep crosses
  midnight; regularity/circadian need multiple days):
  `[sleep_duration_median, sleep_onset_hours, sleep_wake_hours, sleep_onset_regularity,
  sleep_midpoint_regularity, circadian_regularity, days_with_data, call_present, sms_present]`.
  So the DL model sees ≥ what XGBoost saw; the sequence isolates the daily usage rhythm.

Standardization is fit on the TRAIN fold only. **ONNX input contract (Phase-5 Kotlin MUST
construct exactly this; embedded in the model's `metadata_props`):**

```
input  seq    : float32 (B, 7, 8)   # dynamic, standardized
input  static : float32 (B, 9)      # static, standardized
output stress : float32 (B,)        # 0–100, 100 = most stressed
metadata: spec_version = <SPEC_VERSION>, input_contract = "<the line above>"
```

Inference is **batch = 1** (`(1,7,8)` + `(1,9)`) — the app scores one user-window at a time;
the exporter's variable-batch LSTM warning does not apply. PyTorch↔ONNX Runtime parity is
asserted to **≤ 1e-5** at export (`ml/src/models/onnx_export.py`); the bump to this
SPEC_VERSION was gated on that parity passing.

### 8. Android source mapping — `queryEvents` → LOCKED intervals (Phase 5)

§2 requires that **any** mapping from a StudentLife stream to a `UsageStatsManager`-style
feature be documented and justified here *before* it may back a feature. This section is
that justification for the backbone. It is **binding on the Kotlin extractor**.

**The mapping.** StudentLife's `phonelock` stream is a list of `(start, end)` LOCKED
intervals. On-device, the equivalent is reconstructed from
`UsageStatsManager.queryEvents`:

```
locked interval = [ KEYGUARD_SHOWN , KEYGUARD_HIDDEN )     (epoch seconds, half-open)
unlock          =   KEYGUARD_HIDDEN  (= the END of a locked interval, as in the Python ETL)
use-session     =   the gap between consecutive locked intervals, kept iff <= MAX_SESSION_MINUTES
```

Downstream everything is **identical to the Python path**: the same interval list feeds the
same `screenlock_window_features` logic, so no feature definition differs by platform — only
the source of the intervals does.

**Why keyguard events, not the more abundant screen events.** The probe
(`docs/device-probe-results.md`) returned **`SCREEN_INTERACTIVE` 2007 / `SCREEN_NON_INTERACTIVE`
2002** vs **`KEYGUARD_HIDDEN` 1138 / `KEYGUARD_SHOWN` 1137** over the same ~10 days — the
screen turns on roughly **1.8×** more often than the device is actually unlocked. The excess
is notification glances, ambient/always-on display, and incoming-call wakes, none of which
are *device use* by a person. Two consequences decided the choice:

1. **Screen events fragment sleep.** A single notification at 03:00 splits one 8-hour
   screen-off interval into two ~4-hour ones. `MIN_SLEEP_MINUTES` (90) would keep both, and
   the per-night "longest qualifying interval" rule would then report ~4 h instead of ~8 h —
   a direct, silent corruption of `sleep_duration_median`, the backbone feature.
2. **Keyguard is the closer analogue to `phonelock`.** StudentLife's stream records the
   device being *locked* (unusable without the user authenticating), which is semantically
   `KEYGUARD_SHOWN → KEYGUARD_HIDDEN`, not "display powered off". Using screen events would
   make the Android and StudentLife "locked interval" mean different things, breaking the
   cross-source equivalence §2 demands.

Trade-off accepted: users with **no lock screen configured** emit no keyguard events and
will produce an empty/low-coverage window. This is handled — not hidden — by
`COVERAGE_MIN_DAYS` (3), which gates such a window out rather than scoring it. Raw events of
**both** kinds are still persisted (below) so this decision can be revisited without
re-querying.

**Open intervals are DROPPED, never clamped.** An interval whose `KEYGUARD_SHOWN` precedes
the query start, or whose `KEYGUARD_HIDDEN` falls after the query end, is discarded — it is
**not** clamped to the window boundary. Rationale: clamping fabricates a long locked
interval out of a *missing* event. A window that begins mid-lock would synthesize a
multi-hour interval whose midpoint can land in `SLEEP_MIDPOINT_BAND`, inventing a "night of
sleep" that never happened and corrupting `sleep_duration_median` / `n_sleep_nights` /
`sleep_onset_regularity` — the backbone. Losing at most one real interval at each window
edge is strictly preferable to fabricating one. (Python's ETL has the same effect: it reads
already-paired `(start, end)` rows, so an unpaired lock simply does not exist there.)

**Other derivation rules** (deterministic, in `sensing/LockedIntervalDerivation`):
- consecutive duplicate states are collapsed — `SHOWN, SHOWN, HIDDEN` yields one interval
  opened by the **first** `SHOWN` (the later one is a redundant re-post, not a new lock);
- `end <= start` intervals are dropped, mirroring the Python ETL's `end_utc > start_utc`;
- `DEVICE_SHUTDOWN` closes any open interval as *unknown* (dropped) and `DEVICE_STARTUP`
  begins a fresh state — a powered-off phone is a **data gap**, not a lock. Counting it as
  sleep would be the same fabrication as clamping;
- intervals are sorted by start before use (Python sorts too; `queryEvents` order is not
  contractual).

**Verified API levels** (from the installed SDK's `platforms/*/data/api-versions.xml`, not
from memory): `KEYGUARD_SHOWN`, `KEYGUARD_HIDDEN`, `SCREEN_INTERACTIVE`,
`SCREEN_NON_INTERACTIVE` = **API 28**; `DEVICE_SHUTDOWN`, `DEVICE_STARTUP` = **API 29**;
`UsageStatsManager` itself = API 21. The shutdown/startup gap rule therefore sets
**`minSdk = 29`**. `MOVE_TO_FOREGROUND` is deprecated at API 29 and is not used.

**Permission.** `queryEvents`/`queryUsageStats` need `android.permission.PACKAGE_USAGE_STATS`,
a *special* (not runtime) permission granted via `Settings.ACTION_USAGE_ACCESS_SETTINGS`;
grant state is checked with `AppOpsManager.OPSTR_GET_USAGE_STATS`. CallLog/SMS use the
runtime `READ_CALL_LOG` / `READ_SMS`. — NEEDS-VERIFICATION against current official docs
before the participant build.

**Timezone.** Python fixes `TIMEZONE = America/New_York` because StudentLife is Dartmouth
2013. That is a property of *the bootstrap corpus*, not of the feature definitions, so the
Kotlin extractor takes the zone as a **parameter**: the app passes the device zone
(`ZoneId.systemDefault()`), and the parity test / any StudentLife replay passes
`SpecConstants.PARITY_TIMEZONE` (= the Python `TIMEZONE`) **explicitly**. Identical input +
identical zone ⇒ identical vectors, which is what the parity test asserts. A parity test that
passed only because both sides happened to read the same ambient zone would be worthless, so
it must never rely on the default.

**Not backbone.** `queryUsageStats(INTERVAL_DAILY)` per-app buckets are cached as
low-resolution context only. Per §2 the StudentLife `app_usage` RUNNING_TASKS poll is *not*
equivalent to them, and that mapping is **not** justified here — so no feature may be built
on them under this SPEC_VERSION.

**Caching.** Computed vectors are cached in Room keyed by `(windowEnd, SPEC_VERSION)`, so a
version bump invalidates rather than silently reuses vectors computed under older rules. Raw
derived events are persisted at first run because `queryEvents` retention (~10 d) means
anything not captured then is unrecoverable.

---

## Label decoding — Stress EMA

The bootstrap stress label is the StudentLife **Stress EMA** (`EMA/response/Stress/`),
item `level`, question_text **"Right now, I am..."**.

**Verbatim anchors** (from `EMA/EMA_definition.json`, item `level`):

| raw `level` | verbatim anchor |
|---|---|
| 1 | `A little stressed` |
| 2 | `Definitely stressed` |
| 3 | `Stressed out` |
| 4 | `Feeling good` |
| 5 | `Feeling great` |

The RAW numbering is **NOT monotonic** on a stress/wellbeing axis: 1→3 is *ascending
stress* (a little → definitely → stressed out) and 4→5 is *ascending positive affect*.
So raw 3 is the MOST stressed and raw 1 ("a little stressed") is the mildest/near-baseline
— **not** a monotonic 1..5 scale.

### Ordinal remap (this is what makes the scale usable)
Ordered by wellbeing, the anchors are monotonic under the permutation
`3 "Stressed out" < 2 "Definitely stressed" < 1 "A little stressed" < 4 "Feeling good" < 5 "Feeling great"`.
Apply this remap to get a genuine 5-point ordinal (higher = better wellbeing / less stress):

| raw `level` | 3 | 2 | 1 | 4 | 5 |
|---|---|---|---|---|---|
| **remapped ordinal** | 0 | 1 | 2 | 3 | 4 |

Reasoning: the remap preserves **all** responses (2167) instead of discarding the 45% at
level 1, and it turns a non-monotonic scale into a defensible ordinal that can be averaged
under the standard **Likert caveat** (equal spacing between anchors is *assumed*, not
measured). Implemented in `ml/src/features/labels.py::remap_level`; constants in
`ml/src/features/spec_constants.py::RAW_TO_ORDINAL`.

### The ordinal rule (replaces the old "never treat as ordinal")
- The **RAW** 1–5 scale must **never** be averaged, summed, or compared — it is meaningless
  as a number. Stored as an **unordered pandas Categorical** so any arithmetic raises
  (`guards.assert_raw_scale_protected`; the assertion fires on operations against the RAW
  scale specifically).
- The **REMAPPED** ordinal (0–4) **may** be averaged/compared, with the Likert caveat above.

### Targets
- **PRIMARY — regression.** Per subject-day: `stress_score = (4 − mean(remapped)) / 4 × 100`,
  a 0–100 score where **100 = most stressed**. This is what the app displays, so it is what
  we optimise. Uses every subject-day with ≥1 valid EMA.
- **SECONDARY — binary (subset).** `{2,3}` = stressed vs `{4,5}` = not; **level 1 dropped**
  as ambiguous. Subject-day label = majority of that day's non-level-1 responses (ties → drop).
  Report on this subset only, with its own `n` stated so it is not confused with the
  regression `n`.

**Worked example.** A subject-day's responses are raw `level = [4, 1, 5, 3, 2]`:
- Remap → `[3, 2, 4, 0, 1]`; mean = 2.0; `stress_score = (4−2.0)/4×100 = 50.0`.
- Binary subset (drop the level-1) → raw `[4,5,3,2]` → not,not,stressed,stressed → tie → **dropped**.
- Naive mean of the RAW numbers (2.0→"stressed out"-ish) is meaningless and **forbidden**.

### Weekly aggregation (period-level target — Phase 2 diagnostic)
After the day-level target proved a well-evidenced null (`docs/phase2-results.md`), a
coarser **subject-week** target is tested: label = mean remapped stress over all valid
responses in that week (rescaled 0–100). A subject-week is kept only with
**`MIN_RESPONSES_PER_WEEK = 3`** responses — median is 5/week (IQR 3–8), so ≥3 keeps 75% of
weeks and 47/48 subjects while ~halving the weekly-mean standard error vs a 1-response week
(9.4/√1 → 9.4/√3 ≈ 5.4). Recorded in `ml/src/features/spec_constants.py`.

**Limitation (recorded).** 122 entries store a bare digit under a `"null"` key
(`bare:1`=39, `bare:2`=22, `bare:3`=44, `bare:4`=15, `bare:5`=2) plus 9 `Unknown` and 109
coordinate strings. These *might* be mis-keyed levels but are indistinguishable from
location answers, so all no-`level` entries (241 total) are **dropped, not recovered**.
