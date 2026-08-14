# Device Probe Results — retrospective retrieval feasibility

Resolves the "retrospective retrieval" question left open in `docs/dataset-inventory.md` §5:
**at install time, how far back can each data source be backfilled from history already on
the device?** A throwaway diagnostic app (`tools/device-probe/`, gitignored) queried each
source as far back as the OS would answer.

- **Device:** Samsung Galaxy S24 Ultra (`SM-S928B`, `e3q`)
- **OS:** Android 16, API level 36
- **Run date:** 2026-08-15 (02:44:44 local)
- **Single device, single run** — treat retention numbers as one data point, not a
  guarantee across OEMs / usage profiles. Items below marked NEEDS-VERIFICATION.

## Raw output (verbatim)

```
Device Probe results
====================

## Device
Manufacturer: samsung
Model: SM-S928B
Device: e3q
Android version: 16
API level (SDK_INT): 36
Probe run at: 2026-08-15 02:44:44 (local)  /  epoch_ms=1786742084996

## UsageStatsManager.queryUsageStats
INTERVAL_DAILY: records=2390, earliest=2026-08-05 17:01:41 (local)  /  epoch_ms=1785929501125
INTERVAL_WEEKLY: records=1143, earliest=2026-07-22 16:57:42 (local)  /  epoch_ms=1784719662918
INTERVAL_MONTHLY: records=1960, earliest=2026-03-15 16:02:39 (local)  /  epoch_ms=1773570759153
NOTE: OS aggregates/prunes these buckets; "earliest" is the real retention floor. NEEDS-VERIFICATION per OEM.

## UsageStatsManager.queryEvents (screen/unlock)
SCREEN_INTERACTIVE: 2007
SCREEN_NON_INTERACTIVE: 2002
KEYGUARD_HIDDEN (unlock): 1138
KEYGUARD_SHOWN (lock): 1137
matching events total: 6284
earliest matching event: 2026-08-05 17:01:58 (local)  /  epoch_ms=1785929518390
NOTE: raw event retention is shorter than daily buckets. NEEDS-VERIFICATION on-device.

## CallLog.Calls
records=1998
earliest=2026-06-19 08:41:51 (local)  /  epoch_ms=1781838711701

## SMS (Telephony.Sms)
records=1759
earliest=2025-10-05 17:04:31 (local)  /  epoch_ms=1759664071805
NOTE: reading SMS is allowed with READ_SMS; Play-Store distribution is restricted (irrelevant for sideload). NEEDS-VERIFICATION.

## Health Connect (Sleep + Steps)
SleepSessionRecord: count=0, earliest=—
StepsRecord: count=0, earliest=—
NOTE: history depth depends on which apps write to Health Connect on this device. NEEDS-VERIFICATION.
```

## Interpretation

Retention is measured as (run date − earliest record).

| Source | Records | Earliest | Retrospective depth | Verdict |
|---|---|---|---|---|
| `queryEvents` (screen on/off, lock/unlock) | 6,284 | 2026-08-05 | **~10 days** | **YES — backbone** |
| `queryUsageStats` INTERVAL_DAILY | 2,390 | 2026-08-05 | ~10 days | YES |
| `queryUsageStats` INTERVAL_WEEKLY | 1,143 | 2026-07-22 | ~3.5 weeks | YES (coarser) |
| `queryUsageStats` INTERVAL_MONTHLY | 1,960 | 2026-03-15 | ~5 months | YES (coarsest) |
| `CallLog.Calls` | **1998** | 2026-06-19 | ~2 months* | YES — auxiliary, *see caveat* |
| `Telephony.Sms` | 1,759 | 2025-10-05 | ~10 months | YES — auxiliary |
| Health Connect Sleep | 0 | — | none | **NO** |
| Health Connect Steps | 0 | — | none | **NO** |

**1. Screen/lock is the backbone, and ~10 days is the binding constraint.**
Both raw-event sources (`queryEvents` and `INTERVAL_DAILY`) floor out at the *same*
~10-day boundary (2026-08-05). Screen on/off + lock/unlock events are dense (6,284 events)
and are the one signal that is both retrospectively retrievable here **and** densely
present in StudentLife (`phonelock` 49/49). This ~10-day event retention is the tightest
limit on the whole design → it drives the **7-day analysis window** (7 < 10 leaves margin;
see `docs/feature-spec.md` §5).

**2. Aggregate usage reaches further back but is coarser.** `INTERVAL_WEEKLY` (~3.5 wk)
and `INTERVAL_MONTHLY` (~5 mo) extend well past the 10-day event floor, but only as
pre-aggregated per-app buckets — no per-event timing, so they can't reconstruct fine
screen/lock dynamics. Useful at most as low-resolution context, not backbone.

**3. ⚠️ CallLog returned exactly 1998 records — almost certainly a CAP, not true
retention (NEEDS-VERIFICATION).** 1998 is suspiciously close to a round 2000, which
strongly suggests a provider/query row cap rather than a genuine ~2-month retention depth.
If it is a cap, then **retention depth is inversely proportional to call volume: heavy
callers get *less* history (a shallower time window) than light callers.** That makes any
CallLog-derived feature's effective lookback **user-dependent and confounded with call
frequency** — a real feature-design hazard. Before using CallLog features we must: (a)
re-query with an explicit large/absent `LIMIT` and time-range to see if >1998 rows return,
and (b) if capped, either window CallLog to a fixed *time* span (not a row count) or pair
it with its per-window missingness/So indicator. This keeps CallLog **auxiliary only**, as
already required in `docs/feature-spec.md` §1/§4.

**4. SMS (~10 months, 1,759 rows) looks like genuine retention**, not an obvious cap
(1,759 is not near a round number and is *below* CallLog's 1998 despite a longer span).
Still auxiliary, still requires a missingness indicator.

**5. Health Connect is empty (0 Sleep, 0 Steps) → OUT OF SCOPE.** Nothing writes to Health
Connect on this device (no Samsung Health / fitness app feeding it). It cannot seed a
retrospective baseline, so Sleep/Steps via Health Connect are excluded from the
retrospective design (`docs/feature-spec.md` §4). This is device/user-dependent, not an OS
guarantee — hence NEEDS-VERIFICATION, but the design must not *depend* on it.

**Net:** backbone = screen/lock (~10 days) → 7-day window; Call/SMS auxiliary with the cap
caveat above; mobility, activity, ambient, Bluetooth/Wi-Fi, and Health Connect out of
scope for retrospective backfill.
