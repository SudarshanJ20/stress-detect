# Feature Specification — SINGLE SOURCE OF TRUTH

```
SPEC_VERSION: v0.2.0
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
rule | android_source | python_source | studentlife_coverage`

`studentlife_coverage` is **informational only** — it records how many of StudentLife's
49 bootstrap participants have real (non-empty) data for the backing StudentLife stream,
so we know where the bootstrap signal is thin. It is **not** part of the parity vector and
does not affect the parity test. `— (not in StudentLife)` means the feature has no
StudentLife source and will come only from our own collection or a modern-device API.
See **Feature scope constraints** and **Label decoding — Stress EMA** below.

---

## Typing
> ⚠️ Timing-derived features ONLY. NEVER characters. No cell here may reference typed
> content. The collector must be architecturally incapable of receiving text.

| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage |
|---|---|---|---|---|---|---|---|---|
| inter_key_interval_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) |
| inter_key_interval_iqr | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) |
| backspace_rate | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) |
| pause_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) |

## Touch
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage |
|---|---|---|---|---|---|---|---|---|
| tap_rate | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) |
| swipe_velocity_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) |

## Usage
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage |
|---|---|---|---|---|---|---|---|---|
| screen_on_time | TODO | TODO | TODO | TODO | TODO | UsageStatsManager (TODO) | TODO | 49/49 (phonelock episodes) |
| unlock_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (phonelock episodes) |
| app_switch_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (app_usage — RUNNING_TASKS poll, not a usage timeline; see constraints) |

## Mobility
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage |
|---|---|---|---|---|---|---|---|---|
| location_variance | TODO | TODO | TODO | TODO | TODO | FusedLocationProvider (TODO) | TODO | 49/49 (gps) |
| places_visited | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (gps) |
| time_at_primary_location | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (gps) |

## Sleep-proxy
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage |
|---|---|---|---|---|---|---|---|---|
| inferred_sleep_duration | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (phonelock/dark/activity; Sleep EMA for reference) |
| longest_inactive_window | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (phonelock/dark/activity) |

## Motion
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage |
|---|---|---|---|---|---|---|---|---|
| activity_fraction_still | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (activity inference) |
| step_count | TODO | TODO | TODO | TODO | TODO | Health Connect / sensors (TODO) | TODO | — (not in StudentLife; activity inference has no step count) |

## Social
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage |
|---|---|---|---|---|---|---|---|---|
| comms_app_time | TODO | TODO | TODO | TODO | TODO | TODO | TODO | call_log 20/49, sms 23/49 (sparse — NOT a backbone; see constraints) |
| notification_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (not in StudentLife) |

## Ambient
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source | studentlife_coverage |
|---|---|---|---|---|---|---|---|---|
| ambient_light_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO | 49/49 (dark; episodic dark-periods only, no raw light level) |
| time_of_day_bucket | TODO | TODO | TODO | TODO | TODO | TODO | TODO | — (derived from clock) |

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
- Modern-device retrievability is tracked separately and remains **unresolved pending the
  on-device probe** (see `docs/dataset-inventory.md` §5). Do not fill a retrospective /
  retrievability column here from StudentLife presence alone; per `CLAUDE.md`, current
  Android platform behavior stays **NEEDS-VERIFICATION** until confirmed on-device.

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
