"""Generate `fixtures/synthetic_trace.json` — the cross-language parity contract.

Run from `ml/`:  python tools/build_parity_fixture.py

Every case is SYNTHETIC (no participant data). Each block targets ONE named rule from
`docs/feature-spec.md`, so a parity failure names a rule rather than a line number; the
mapping is documented in `fixtures/README.md`.

The expected vectors are produced by the PYTHON extractor (`ml/src/features`), which makes
this file the contract the Kotlin port must satisfy. `ml/tests/test_parity_fixture.py`
re-runs Python against it so the fixture also catches future PYTHON drift — otherwise
regenerating would silently bless a regression.
"""
from __future__ import annotations

import datetime as dt
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "ml" / "src"))

import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

from features import aux_features, screenlock_features  # noqa: E402
from features.build_dataset import _window_bounds  # noqa: E402
from features.spec_constants import (  # noqa: E402
    COVERAGE_MIN_DAYS, SPEC_VERSION, TIMEZONE, WINDOW_DAYS,
)

TZ = TIMEZONE


def ts(local: str) -> int:
    """Local wall-clock string → epoch seconds. Raises on an ambiguous/nonexistent time,
    which is exactly what we want: a DST-ambiguous literal in a fixture would make the
    expected values depend on pandas' disambiguation policy rather than on the spec."""
    return int(pd.Timestamp(local, tz=TZ).timestamp())


def case(
    name: str,
    exercises: str,
    label_date: str,
    intervals: list[tuple[str, str]],
    calls: list[str] | None = None,
    sms: list[str] | None = None,
    has_calls: bool = True,
    has_sms: bool = True,
) -> dict:
    y, m, d = (int(x) for x in label_date.split("-"))
    w0, w1 = _window_bounds(dt.date(y, m, d))

    iv = sorted((ts(a), ts(b)) for a, b in intervals)
    # The ETL drops `end <= start`, so such a row can never reach the extractor in
    # production — a fixture containing one would pin undefined behaviour (and Kotlin's
    # LockedInterval rejects it outright). Fail loudly on a typo instead.
    for (a, b), (la, lb) in zip(iv, intervals):
        assert b > a, f"{name}: interval {la} → {lb} has end <= start"
    arr = np.array(iv, dtype="int64") if iv else np.empty((0, 2), dtype="int64")

    call_ts = np.array([ts(c) for c in (calls or [])], dtype="int64")
    sms_ts = np.array([ts(s) for s in (sms or [])], dtype="int64")

    feats = screenlock_features.screenlock_window_features(arr, w0, w1)
    aux = aux_features.aux_window_features(call_ts, sms_ts, w0, w1, has_calls, has_sms)
    expected = {**feats, **aux}

    return {
        "name": name,
        "exercises": exercises,
        "label_date": label_date,
        # Both sides must DERIVE the window from label_date; these are recorded so a
        # window mismatch (e.g. calendar vs absolute day arithmetic across DST) is caught
        # precisely instead of surfacing as a dozen confusing feature diffs.
        "expected_window": {"start_utc": w0, "end_utc": w1},
        "expected_window_local": {
            "start": str(pd.Timestamp(w0, unit="s", tz="UTC").tz_convert(TZ)),
            "end": str(pd.Timestamp(w1, unit="s", tz="UTC").tz_convert(TZ)),
        },
        "locked_intervals": [[int(a), int(b)] for a, b in iv],
        "locked_intervals_local": [
            [str(pd.Timestamp(a, unit="s", tz="UTC").tz_convert(TZ)),
             str(pd.Timestamp(b, unit="s", tz="UTC").tz_convert(TZ))]
            for a, b in iv
        ],
        "calls": [int(x) for x in call_ts],
        "sms": [int(x) for x in sms_ts],
        "has_calls": has_calls,
        "has_sms": has_sms,
        "expected_features": expected,
        "expected_days_with_data": float(feats["days_with_data"]),
        "expected_meets_coverage": bool(feats["days_with_data"] >= COVERAGE_MIN_DAYS),
    }


def nightly(start_day: str, nights: int, onset: str, wake: str) -> list[tuple[str, str]]:
    """`nights` consecutive overnight locks, onset on day N and wake on day N+1."""
    d0 = dt.date.fromisoformat(start_day)
    out = []
    for i in range(nights):
        a = d0 + dt.timedelta(days=i)
        b = a + dt.timedelta(days=1)
        out.append((f"{a} {onset}", f"{b} {wake}"))
    return out


# The demo person's habitual shape. Onset/wake drift a little night to night so the
# regularity features are non-zero.
DEMO_NIGHTS = [
    ("23:18", "07:05"), ("23:42", "07:12"), ("00:05", "07:30"), ("23:05", "06:50"),
    ("23:55", "07:20"), ("01:10", "08:15"), ("23:30", "07:00"),
]
DEMO_DAY_LOCKS = [
    ("07:40", "08:25"), ("09:10", "10:05"), ("11:30", "12:15"),
    ("13:20", "14:40"), ("16:05", "17:10"), ("19:30", "20:25"), ("21:15", "22:10"),
]


def demo_intervals(
    start_day: dt.date,
    skips: dict[int, set[int]],
    extra: dict[int, list[tuple[str, str]]] | None = None,
) -> list[tuple[str, str]]:
    """One demo week from [DEMO_NIGHTS] + [DEMO_DAY_LOCKS], starting at `start_day`.

    `skips` drops day-lock indices on the given day so the daily series actually varies;
    `extra` adds further locked blocks to every day (used by the prior weeks).
    """
    intervals: list[tuple[str, str]] = []
    for index in range(7):
        day = start_day + dt.timedelta(days=index)
        onset, wake = DEMO_NIGHTS[index]
        # An onset after midnight belongs to the FOLLOWING calendar date.
        onset_day = day + dt.timedelta(days=1) if onset < "12:00" else day
        wake_day = onset_day if onset < "12:00" else day + dt.timedelta(days=1)
        intervals.append((f"{onset_day} {onset}", f"{wake_day} {wake}"))
        for lock_index, (start, end) in enumerate(DEMO_DAY_LOCKS):
            if lock_index in skips.get(index, set()):
                continue
            intervals.append((f"{day} {start}", f"{day} {end}"))
        for start, end in (extra or {}).get(index, []):
            intervals.append((f"{day} {start}", f"{day} {end}"))
    return intervals


def demo_week() -> list[tuple[str, str]]:
    """A plausible-looking week for DEMO MODE.

    The other cases are minimal traces built to pin one rule each, so they contain a
    handful of intervals and produce a week with (say) 0.7 unlocks a day — accurate for
    what they test, but nonsense to show someone as an example week. This case exists so
    demo mode replays something that reads like a real person's week.

    Volumes are deliberately modest (~8 unlocks/day rather than a realistic 50+) to keep
    the committed fixture readable; the SHAPE is what matters for a demo — nightly sleep,
    a morning cluster, daytime gaps, some evening use, one late night.
    """
    return demo_intervals(dt.date(2013, 4, 8), {2: {1, 4}, 5: {0, 2, 6}})


# The prior weeks' extra locked blocks: a long afternoon away-from-phone stretch that
# drifts by the day. It is what makes the demo week read as MORE screen time than usual
# without touching the nights, and the drift keeps the days from looking identical (seven
# identical days would push circadian regularity to ~0.81 and the rhythm row would then
# report a change that is an artefact of the fixture rather than of the week).
DEMO_PRIOR_EXTRA = {
    0: [("14:45", "16:00"), ("17:20", "18:40")],
    1: [("15:10", "16:00"), ("17:15", "19:10")],
    2: [("14:45", "16:00"), ("17:30", "19:20")],
    3: [("15:00", "16:00"), ("17:10", "18:50")],
    4: [("14:50", "16:00"), ("17:40", "19:15")],
    5: [("15:20", "16:00"), ("17:15", "19:05")],
    6: [("14:55", "16:00"), ("17:25", "19:00")],
}


CASES = [
    # ── 0. the demo week (not an edge case — see demo_week above) ─────────────────────
    case(
        "demo_week",
        "A plausible week, used by the app's DEMO MODE. Not an edge case: it exists so a "
        "demo replays something shaped like a real person's week (nightly sleep with some "
        "drift, a morning cluster, daytime gaps, evening use, one late night) rather than "
        "a four-interval rule test. It is still generated and parity-checked like every "
        "other case, so the demo can never drift from verified behaviour.",
        "2013-04-15",
        demo_week(),
        calls=["2013-04-09 10:15", "2013-04-09 18:40", "2013-04-11 12:05",
               "2013-04-12 20:30", "2013-04-14 09:50"],
        sms=["2013-04-08 08:10", "2013-04-08 21:35", "2013-04-10 13:20",
             "2013-04-11 19:05", "2013-04-13 11:45", "2013-04-14 16:20"],
    ),

    # ── 0a/0b. the two weeks BEFORE the demo week ─────────────────────────────────────
    # The result screen compares the current week to the same person's own earlier weeks.
    # On a real phone those come from the cached vectors of previous runs, so a fresh
    # install — and therefore every demo — has none, and every row would show a value with
    # no direction. These two exist so the comparison is demonstrable in a viva.
    #
    # They are the SAME person: identical nights, identical daily lock skeleton. What
    # differs is a long afternoon away-block (less phone use than the demo week) and a
    # busier week of calls and texts, which is what makes the demo read
    #   screen ↑ a little more   ·   rest → unchanged   ·   comms ↓ quieter   ·   rhythm →
    # Being ordinary cases they are generated and parity-checked like every other one, so
    # the demo still cannot show a number the Python side has not verified.
    case(
        "demo_prior_week_1",
        "The week before `demo_week`, for DEMO MODE's own-baseline comparison. Not an edge "
        "case. Same person and same nights as `demo_week` with a long drifting afternoon "
        "away-block and a busier comms week, so the demo week reads as more screen time "
        "and quieter contact than this person's usual.",
        "2013-04-08",
        demo_intervals(dt.date(2013, 4, 1), {1: {3}, 4: {2, 5}}, DEMO_PRIOR_EXTRA),
        calls=["2013-04-01 09:40", "2013-04-01 17:25", "2013-04-02 12:30",
               "2013-04-03 08:55", "2013-04-03 19:10", "2013-04-04 14:20",
               "2013-04-05 11:05", "2013-04-06 16:45", "2013-04-07 10:30",
               "2013-04-07 20:15"],
        sms=["2013-04-01 07:55", "2013-04-02 09:20", "2013-04-02 21:10",
             "2013-04-03 13:35", "2013-04-04 18:05", "2013-04-05 08:40",
             "2013-04-05 22:25", "2013-04-06 12:50", "2013-04-07 15:15"],
    ),
    case(
        "demo_prior_week_2",
        "The week before `demo_prior_week_1`, so DEMO MODE's comparison averages two "
        "earlier weeks rather than resting on one. Not an edge case; see "
        "`demo_prior_week_1`.",
        "2013-04-01",
        demo_intervals(dt.date(2013, 3, 25), {0: {2}, 3: {1, 6}}, DEMO_PRIOR_EXTRA),
        calls=["2013-03-25 10:05", "2013-03-25 19:35", "2013-03-26 13:15",
               "2013-03-27 09:25", "2013-03-27 18:50", "2013-03-28 15:40",
               "2013-03-29 11:20", "2013-03-30 17:05", "2013-03-31 12:40"],
        sms=["2013-03-25 08:25", "2013-03-26 11:50", "2013-03-26 20:40",
             "2013-03-27 14:10", "2013-03-28 09:05", "2013-03-29 19:30",
             "2013-03-30 13:45", "2013-03-31 16:55", "2013-03-31 21:20"],
    ),

    # ── 1. midnight-wrap sleep ────────────────────────────────────────────────────────
    case(
        "midnight_wrap_sleep",
        "Sleep intervals crossing midnight: onset ~23:30 (day N), wake ~07:15 (day N+1). "
        "Forces CIRCULAR statistics — an arithmetic mean of [23.5, 7.25] would give ~15:00 "
        "(mid-afternoon) instead of ~03:00, and night_key must group the interval with the "
        "day it STARTED via the midpoint-minus-12h rule.",
        "2013-04-15",
        nightly("2013-04-08", 6, "23:30", "07:15")
        + [("2013-04-09 12:00", "2013-04-09 13:00"),
           ("2013-04-10 12:00", "2013-04-10 14:30"),
           ("2013-04-11 09:00", "2013-04-11 09:45")],
        calls=["2013-04-10 10:00", "2013-04-12 18:20"],
        sms=["2013-04-09 08:05", "2013-04-13 21:40"],
    ),

    # ── 2. sleep-band edges, just INSIDE ──────────────────────────────────────────────
    case(
        "sleep_band_edge_inside",
        "SLEEP_MIDPOINT_BAND is [20:00, 12:00) and wraps midnight. Midpoints land exactly "
        "on 20:00 (inclusive lower bound) and on 11:59 (just inside the exclusive upper "
        "bound), so BOTH must qualify as sleep.",
        "2013-04-15",
        [("2013-04-09 18:30", "2013-04-09 21:30"),   # midpoint exactly 20:00
         ("2013-04-11 10:29", "2013-04-11 13:29"),   # midpoint 11:59
         ("2013-04-12 14:00", "2013-04-12 14:45"),   # filler, not sleep
         ("2013-04-13 08:00", "2013-04-13 08:30")],
    ),

    # ── 3. sleep-band edges, just OUTSIDE ─────────────────────────────────────────────
    case(
        "sleep_band_edge_outside",
        "Mirror of the previous case one minute the other way: midpoints 19:59 (just below "
        "the lower bound) and exactly 12:00 (the EXCLUSIVE upper bound). Neither may count "
        "as sleep, so all six sleep features are NaN — which also exercises the fallback "
        "of the person-relative night band to NIGHT_FIXED_BAND when no sleep was detected.",
        "2013-04-15",
        [("2013-04-09 18:29", "2013-04-09 21:29"),   # midpoint 19:59 → out
         ("2013-04-11 10:30", "2013-04-11 13:30"),   # midpoint exactly 12:00 → out
         ("2013-04-12 14:00", "2013-04-12 14:45"),
         ("2013-04-13 08:00", "2013-04-13 08:30")],
    ),

    # ── 4. MIN_SLEEP_MINUTES edge ─────────────────────────────────────────────────────
    case(
        "min_sleep_duration_edge",
        "MIN_SLEEP_MINUTES = 90 is inclusive (`>=`). A 90-minute overnight lock must count "
        "as sleep; an 89-minute one on another night must not.",
        "2013-04-15",
        [("2013-04-09 23:00", "2013-04-10 00:30"),   # exactly 90 min → sleep
         ("2013-04-11 23:00", "2013-04-12 00:29"),   # 89 min → not sleep
         ("2013-04-12 22:00", "2013-04-12 23:30"),   # 90 min → sleep
         ("2013-04-13 09:00", "2013-04-13 09:20")],
    ),

    # ── 5. MAX_SESSION_MINUTES edge ───────────────────────────────────────────────────
    case(
        "session_cutoff_edges",
        "A use-session is the gap between consecutive locks, kept iff `gap <= 180 min` "
        "(inclusive). Gaps here are 179, exactly 180, and 181 minutes: the first two are "
        "sessions, the third is a data gap / phone-off and must be DROPPED. Dropping it "
        "also removes its screen-on time, so session_count, the median/IQR and "
        "screen_on_fraction all move together.",
        "2013-04-15",
        [("2013-04-09 08:00", "2013-04-09 09:00"),
         ("2013-04-09 11:59", "2013-04-09 12:59"),   # gap 179 min → session
         ("2013-04-10 08:00", "2013-04-10 09:00"),
         ("2013-04-10 12:00", "2013-04-10 13:00"),   # gap 180 min → session (inclusive)
         ("2013-04-11 08:00", "2013-04-11 09:00"),
         ("2013-04-11 12:01", "2013-04-11 13:01"),   # gap 181 min → DROPPED
         ("2013-04-12 08:00", "2013-04-12 09:00"),
         ("2013-04-12 10:00", "2013-04-12 11:00")],
    ),

    # ── 6. below the coverage gate ────────────────────────────────────────────────────
    case(
        "below_coverage_gate",
        f"Only 2 distinct local dates have lock data, below COVERAGE_MIN_DAYS = "
        f"{COVERAGE_MIN_DAYS}. Features are still COMPUTED (and cached) but the window must "
        "be reported as failing the gate, never scored. Verifies both sides agree on the "
        "gate, not just on the numbers.",
        "2013-04-15",
        [("2013-04-12 23:10", "2013-04-13 07:05"),
         ("2013-04-13 12:00", "2013-04-13 12:40")],
        has_calls=False,
        has_sms=False,
    ),

    # ── 7. interval open at the window START ──────────────────────────────────────────
    case(
        "interval_open_at_window_start",
        "A lock that began BEFORE the window and ends inside it. The interval is kept whole "
        "(overlap test `end > w0`), so its full duration counts toward sleep, but coverage "
        "clips it to w0 — the pre-window days must not appear as days_with_data.",
        "2013-04-15",
        [("2013-04-07 21:30", "2013-04-08 08:10"),   # starts a day before w0 = 2013-04-08
         ("2013-04-09 23:20", "2013-04-10 07:00"),
         ("2013-04-10 13:00", "2013-04-10 13:50"),
         ("2013-04-11 23:40", "2013-04-12 07:20")],
    ),

    # ── 8. interval open at the window END ────────────────────────────────────────────
    case(
        "interval_open_at_window_end",
        "A lock that starts inside the window and ends AFTER it. Its end is beyond w1, so it "
        "must NOT be counted as an unlock (`end < w1` fails) even though the interval itself "
        "is kept for sleep. Catches an implementation that counts every interval end.",
        "2013-04-15",
        [("2013-04-10 23:15", "2013-04-11 07:05"),
         ("2013-04-11 14:00", "2013-04-11 14:40"),
         ("2013-04-12 23:00", "2013-04-13 06:50"),
         ("2013-04-14 22:50", "2013-04-15 07:30")],  # ends after w1 = 2013-04-15 00:00
    ),

    # ── 9. empty window ───────────────────────────────────────────────────────────────
    case(
        "empty_window",
        "No lock data at all. Every feature must be NaN with days_with_data = 0 — NOT zeros. "
        "A zero here would read as a real measurement ('slept 0 hours', 'never unlocked') "
        "instead of 'unknown', which is the distinction the missing-data rule exists for.",
        "2013-04-15",
        [],
        has_calls=False,
        has_sms=False,
    ),

    # ── 10. DST spring forward ────────────────────────────────────────────────────────
    case(
        "dst_spring_forward",
        "Window spans the 2013-03-10 spring-forward (02:00 → 03:00 skipped). Because pandas' "
        "Timedelta is an ABSOLUTE duration, the window START is 2013-03-03 23:00 local, NOT "
        "midnight — Kotlin must use second arithmetic, not LocalDate.minusDays. Also covers "
        "a sleep interval spanning the skipped hour, whose wall-clock duration is one hour "
        "shorter than its elapsed duration.",
        "2013-03-11",
        [("2013-03-05 23:20", "2013-03-06 07:10"),
         ("2013-03-07 23:35", "2013-03-08 07:25"),
         ("2013-03-09 23:30", "2013-03-10 07:30"),   # spans the skipped 02:00–03:00 hour
         ("2013-03-10 13:00", "2013-03-10 13:45"),
         ("2013-03-10 23:15", "2013-03-11 06:45")],
        calls=["2013-03-08 09:15"],
        sms=["2013-03-06 20:00", "2013-03-10 15:30"],
    ),

    # ── 11. DST fall back ─────────────────────────────────────────────────────────────
    case(
        "dst_fall_back",
        "Window spans the 2013-11-03 fall-back (01:00–02:00 occurs twice). Window start is "
        "2013-10-28 01:00 local — again absolute, not calendar. The overnight lock spans the "
        "repeated hour and so is one hour LONGER in elapsed time than its wall clock "
        "suggests. All literals avoid the ambiguous 01:00–02:00 window itself, so the "
        "expected values do not depend on any disambiguation policy.",
        "2013-11-04",
        [("2013-10-29 23:10", "2013-10-30 07:00"),
         ("2013-10-31 23:25", "2013-11-01 07:15"),
         ("2013-11-02 23:30", "2013-11-03 07:30"),   # spans the repeated hour
         ("2013-11-03 14:00", "2013-11-03 14:50"),
         ("2013-11-03 23:05", "2013-11-04 06:40")],
        calls=["2013-11-01 12:00", "2013-11-03 18:45"],
        sms=["2013-10-30 09:00"],
    ),

    # ── 12. band-edge tie-break, BOTH directions ──────────────────────────────────────
    case(
        "band_edge_tiebreak",
        "Pins the BAND_EDGE_EPS tie-break convention itself, in both directions. Five "
        "identical nights (onset 23:30, wake 07:10) put the person-relative night band at "
        "[23:30, 07:10). A 45-minute lock ends EXACTLY on the low edge (23:30) and each "
        "sleep ends EXACTLY on the high edge (07:10), so every night contributes one unlock "
        "at each bound. Convention: low edge → INSIDE, high edge → OUTSIDE, hence exactly "
        "the 23:30 unlocks count as night-time. Flipping either half of the convention "
        "changes nighttime_unlock_per_day_personal, so this case tests the RULE rather than "
        "the tolerance — which a 1-ULP difference in the circular mean cannot do.",
        "2013-04-15",
        [(f"2013-04-{d:02d} 22:45", f"2013-04-{d:02d} 23:30") for d in range(8, 13)]
        + [(f"2013-04-{d:02d} 23:30", f"2013-04-{d + 1:02d} 07:10") for d in range(8, 13)]
        + [(f"2013-04-{d:02d} 12:00", f"2013-04-{d:02d} 13:00") for d in range(9, 13)]
        + [(f"2013-04-{d:02d} 15:00", f"2013-04-{d:02d} 15:30") for d in range(9, 13)],
    ),

    # ── 13. days_with_data = 8 ────────────────────────────────────────────────────────
    case(
        "days_with_data_eight",
        "The recorded quirk (feature-spec §8): a 7-day window reports EIGHT days of coverage "
        "because an interval crossing w1 clips to the label day's local midnight, whose "
        "DATE is the label day. days_with_data is the denominator of every per-day rate, so "
        "this deflates them. Mirrored deliberately — parity outranks correctness — and "
        "pinned here so neither side 'fixes' it unilaterally.",
        "2013-04-15",
        nightly("2013-04-08", 6, "23:30", "07:10")
        + [("2013-04-14 23:30", "2013-04-15 07:10")],  # crosses w1 → adds the 8th date
        calls=["2013-04-09 10:00"],
        sms=["2013-04-11 16:00"],
    ),
]


def _nan_to_null(obj):
    """Recursively replace NaN with None so the output is strict, portable JSON."""
    if isinstance(obj, dict):
        return {k: _nan_to_null(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [_nan_to_null(v) for v in obj]
    if isinstance(obj, float) and np.isnan(obj):
        return None
    return obj


def main() -> None:
    fixture = {
        "spec_version": SPEC_VERSION,
        "description": (
            "Shared synthetic parity trace — the contract test for feature parity between "
            "the Kotlin extractor (android/app/src/main/java/com/stressdetect/features) and "
            "the Python extractor (ml/src/features). No real participant data. Each case "
            "targets one named rule in docs/feature-spec.md; see fixtures/README.md. "
            "Regenerate with `python ml/tools/build_parity_fixture.py`, and bump "
            "spec_version in lockstep with docs/feature-spec.md."
        ),
        "generator": "ml/tools/build_parity_fixture.py",
        # The zone is part of the CONTRACT, not the environment. Both suites must pass it
        # explicitly; a parity run under the machine's local zone must fail, not silently
        # agree because the machine happens to be set to US Eastern.
        "parity_timezone": TZ,
        "window_days": WINDOW_DAYS,
        "coverage_min_days": COVERAGE_MIN_DAYS,
        "tolerance": 1e-6,
        "feature_names": (
            screenlock_features.feature_names() + aux_features.feature_names()
        ),
        "cases": CASES,
    }

    out = REPO / "fixtures" / "synthetic_trace.json"
    # STRICT JSON: missing values are serialized as `null`, never as a bare `NaN` token.
    # `NaN` is not valid JSON and parsers disagree about it (some accept it, some throw,
    # some yield the string "NaN"), which would make the contract depend on the JSON
    # library rather than on the spec. Both suites map null → NaN on read.
    text = json.dumps(_nan_to_null(fixture), indent=2, allow_nan=False)
    out.write_text(text + "\n", encoding="utf-8")
    print(f"wrote {out} ({len(CASES)} cases, spec {SPEC_VERSION})")
    for c in CASES:
        print(f"  - {c['name']:32s} days_with_data={c['expected_days_with_data']:>4} "
              f"gate={'PASS' if c['expected_meets_coverage'] else 'FAIL'}")


if __name__ == "__main__":
    main()
