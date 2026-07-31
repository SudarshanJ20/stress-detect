# Feature Specification — SINGLE SOURCE OF TRUTH

```
SPEC_VERSION: v0.1.0
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
rule | android_source | python_source`

---

## Typing
> ⚠️ Timing-derived features ONLY. NEVER characters. No cell here may reference typed
> content. The collector must be architecturally incapable of receiving text.

| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source |
|---|---|---|---|---|---|---|---|
| inter_key_interval_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| inter_key_interval_iqr | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| backspace_rate | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| pause_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO |

## Touch
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source |
|---|---|---|---|---|---|---|---|
| tap_rate | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| swipe_velocity_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO |

## Usage
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source |
|---|---|---|---|---|---|---|---|
| screen_on_time | TODO | TODO | TODO | TODO | TODO | UsageStatsManager (TODO) | TODO |
| unlock_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| app_switch_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO |

## Mobility
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source |
|---|---|---|---|---|---|---|---|
| location_variance | TODO | TODO | TODO | TODO | TODO | FusedLocationProvider (TODO) | TODO |
| places_visited | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| time_at_primary_location | TODO | TODO | TODO | TODO | TODO | TODO | TODO |

## Sleep-proxy
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source |
|---|---|---|---|---|---|---|---|
| inferred_sleep_duration | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| longest_inactive_window | TODO | TODO | TODO | TODO | TODO | TODO | TODO |

## Motion
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source |
|---|---|---|---|---|---|---|---|
| activity_fraction_still | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| step_count | TODO | TODO | TODO | TODO | TODO | Health Connect / sensors (TODO) | TODO |

## Social
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source |
|---|---|---|---|---|---|---|---|
| comms_app_time | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| notification_count | TODO | TODO | TODO | TODO | TODO | TODO | TODO |

## Ambient
| feature_name | definition | aggregation_window | unit | dtype | missing-data rule | android_source | python_source |
|---|---|---|---|---|---|---|---|
| ambient_light_median | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
| time_of_day_bucket | TODO | TODO | TODO | TODO | TODO | TODO | TODO |
