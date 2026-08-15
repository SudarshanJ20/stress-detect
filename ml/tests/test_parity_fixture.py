"""Python half of the cross-language parity contract (`fixtures/synthetic_trace.json`).

The Kotlin half is
`android/app/src/test/java/com/stressdetect/features/FixtureParityTest.kt`. Both feed the
SAME inputs through their extractor and assert the SAME expected vectors to 1e-6.

The fixture is generated from this extractor, so these tests look circular — they are not.
They pin the committed expectations against the CURRENT code, so a later change to
`ml/src/features` fails here instead of being silently blessed the next time someone
regenerates the fixture. That is the drift this file exists to catch.
"""
from __future__ import annotations

import datetime as dt
import json
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from features import aux_features, screenlock_features
from features.build_dataset import _window_bounds
from features.spec_constants import COVERAGE_MIN_DAYS, SPEC_VERSION, TIMEZONE

REPO = Path(__file__).resolve().parents[2]
FIXTURE = REPO / "fixtures" / "synthetic_trace.json"


@pytest.fixture(scope="module")
def trace() -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def _case_ids(trace_data: dict) -> list[str]:
    return [c["name"] for c in trace_data["cases"]]


def _load() -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


ALL_CASES = _load()["cases"]


def _compute(case: dict, zone: str | None = None) -> dict:
    """Run the real extractor over one case. `zone` overrides the window arithmetic only,
    which is what the 'wrong zone must fail' tests need."""
    y, m, d = (int(x) for x in case["label_date"].split("-"))
    if zone is None:
        w0, w1 = _window_bounds(dt.date(y, m, d))
    else:
        end = pd.Timestamp(dt.date(y, m, d), tz=zone)
        start = end - pd.Timedelta(days=7)
        w0, w1 = int(start.timestamp()), int(end.timestamp())

    intervals = case["locked_intervals"]
    arr = (np.array(intervals, dtype="int64") if intervals
           else np.empty((0, 2), dtype="int64"))
    calls = np.array(case["calls"], dtype="int64")
    sms = np.array(case["sms"], dtype="int64")

    feats = screenlock_features.screenlock_window_features(arr, w0, w1)
    aux = aux_features.aux_window_features(
        calls, sms, w0, w1, case["has_calls"], case["has_sms"],
    )
    return {"window": (w0, w1), **feats, **aux}


def _assert_matches(case: dict, computed: dict, tolerance: float) -> None:
    for name, expected in case["expected_features"].items():
        actual = computed[name]
        if expected is None or (isinstance(expected, float) and np.isnan(expected)):
            assert np.isnan(actual), f"{case['name']}/{name}: expected NaN, got {actual}"
        else:
            assert abs(actual - expected) <= tolerance, (
                f"{case['name']}/{name}: expected {expected}, got {actual} "
                f"(delta {abs(actual - expected):.3e} > {tolerance:.0e})"
            )


# ── the contract ────────────────────────────────────────────────────────────────────────

def test_fixture_spec_version_matches_code(trace):
    assert trace["spec_version"] == SPEC_VERSION, (
        f"fixture targets {trace['spec_version']} but the code implements {SPEC_VERSION}. "
        f"Regenerate with `python ml/tools/build_parity_fixture.py` after a spec bump."
    )


def test_fixture_declares_the_spec_timezone(trace):
    assert trace["parity_timezone"] == TIMEZONE


def test_fixture_covers_every_required_edge_case(trace):
    # Named in the Phase-6 brief; if a case is dropped the contract silently narrows.
    required = {
        "midnight_wrap_sleep", "sleep_band_edge_inside", "sleep_band_edge_outside",
        "session_cutoff_edges", "below_coverage_gate", "interval_open_at_window_start",
        "interval_open_at_window_end", "empty_window", "dst_spring_forward",
        "dst_fall_back", "days_with_data_eight",
    }
    assert required <= {c["name"] for c in trace["cases"]}


@pytest.mark.parametrize("case", ALL_CASES, ids=_case_ids(_load()))
def test_python_matches_fixture(case, trace):
    computed = _compute(case)
    _assert_matches(case, computed, trace["tolerance"])


@pytest.mark.parametrize("case", ALL_CASES, ids=_case_ids(_load()))
def test_window_bounds_match_fixture(case):
    """Pins the ABSOLUTE (not calendar) day arithmetic — see the DST cases."""
    w0, w1 = _compute(case)["window"]
    assert w0 == case["expected_window"]["start_utc"]
    assert w1 == case["expected_window"]["end_utc"]


@pytest.mark.parametrize("case", ALL_CASES, ids=_case_ids(_load()))
def test_coverage_gate_matches_fixture(case):
    days = _compute(case)["days_with_data"]
    assert (days >= COVERAGE_MIN_DAYS) == case["expected_meets_coverage"]


def test_every_feature_name_is_covered(trace):
    """The contract must span the whole vector — a feature missing from the fixture is a
    feature nothing checks for parity."""
    expected_names = set(trace["feature_names"])
    for case in trace["cases"]:
        assert set(case["expected_features"]) == expected_names, case["name"]


# ── the fixture must FAIL under the wrong zone ──────────────────────────────────────────

def test_dst_cases_fail_under_a_different_zone():
    """If this ever passes, the parity test is not actually pinning the timezone: the
    expected values would agree regardless of the zone, and a zone bug on either side would
    go undetected."""
    for name in ("dst_spring_forward", "dst_fall_back"):
        case = next(c for c in ALL_CASES if c["name"] == name)
        wrong = _compute(case, zone="Asia/Kolkata")
        assert wrong["window"] != tuple(case["expected_window"].values()), (
            f"{name}: window under Asia/Kolkata equals the America/New_York window — "
            f"the fixture is not sensitive to the timezone at all"
        )


def test_a_calendar_day_window_would_break_the_dst_cases():
    """Guards the exact Phase-5 finding: `w0 = w1 - Timedelta(days=7)` is ABSOLUTE. A
    calendar-aware equivalent (same wall-clock time, 7 calendar days back) differs by an
    hour across a DST boundary — and by nothing at all on an ordinary week, which is what
    makes it dangerous."""
    case = next(c for c in ALL_CASES if c["name"] == "dst_spring_forward")
    y, m, d = (int(x) for x in case["label_date"].split("-"))
    end = pd.Timestamp(dt.date(y, m, d), tz=TIMEZONE)

    absolute = int((end - pd.Timedelta(days=7)).timestamp())
    calendar = int(
        pd.Timestamp(dt.date(y, m, d) - dt.timedelta(days=7), tz=TIMEZONE).timestamp()
    )

    assert absolute == case["expected_window"]["start_utc"]
    assert calendar != absolute, "the DST case does not actually discriminate the two"
    assert calendar - absolute == 3600
