# Feature Specification — SINGLE SOURCE OF TRUTH

```
SPEC_VERSION: v0.3.0
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
| screen_on_time | TODO | TODO | TODO | TODO | TODO | UsageStatsManager (TODO) | TODO | 49/49 (phonelock episodes) | **YES — queryEvents SCREEN on/off, ~10d retention (BACKBONE)** |
| unlock_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (phonelock episodes) | **YES — queryEvents KEYGUARD_HIDDEN, ~10d (BACKBONE)** |
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

---

## Label decoding — Stress EMA

The bootstrap stress label is the StudentLife **Stress EMA** (`EMA/response/Stress/`),
item `level`. Its 1–5 scale is **NON-MONOTONIC** and must be decoded before use.

Raw option → meaning (from `EMA/EMA_definition.json`):

| raw `level` | meaning | valence |
|---|---|---|
| 1 | A little stressed | stressed |
| 2 | Definitely stressed | stressed |
| 3 | Stressed out | stressed (most) |
| 4 | Feeling good | positive |
| 5 | Feeling great | positive (most) |

Key point: values **1→3 increase in stress**, then **4→5 jump to positive affect** and
*decrease* in stress. The axis is not ordinal — a higher number is **not** "more stress".

**Correct decoding** (canonical binary target; both extractors MUST agree):
- `stressed`   ← `level ∈ {1, 2, 3}`
- `not_stressed` ← `level ∈ {4, 5}`
- responses with **no `level`** (location-only pings; ~10% of Stress entries) are **not
  labels** — drop them, do not coerce to 0.

**Worked example.** A participant's Stress responses in a window are
`level = [4, 1, 5, 3, 2]`:
- Naive (WRONG) mean = `(4+1+5+3+2)/5 = 3.0` → looks "mid/stressed-out", which is
  meaningless because the scale isn't ordinal.
- Correct decode → `[not_stressed, stressed, not_stressed, stressed, stressed]` →
  3 stressed / 2 not-stressed → **stressed fraction = 0.6**.

**Prohibited:** never average, sum, or treat raw `level` as ordinal/continuous; never feed
raw `level` to the model as a numeric target or feature. Always decode to the categorical
scheme above (or an explicitly documented alternative recorded in this section) first.
