"""Single source of the constants that `docs/feature-spec.md` (v0.4.0) makes authoritative.

Every threshold here is mirrored 1:1 by a row in the spec with a one-line rationale.
Code must not introduce a magic number that is not also in the spec. The Android extractor
(Phase 5) must implement the SAME values; the parity test (Phase 6) enforces it.
"""
from __future__ import annotations

# Bump together with docs/feature-spec.md. Exposed so the parity test / ONNX metadata match.
SPEC_VERSION = "v0.6.0"

# StudentLife was collected at Dartmouth (Hanover, NH), spring 2013 = US Eastern.
# Every clock/date/night computation MUST localize epoch → this zone. Asserted at runtime;
# a wrong zone silently corrupts every sleep/night/circadian feature.
TIMEZONE = "America/New_York"

# ── Stress EMA label (verbatim anchors live in the spec) ─────────────────────────────
# Raw 1–5 is NON-MONOTONIC and must NEVER be averaged/compared. It becomes a true ordinal
# only under this wellbeing permutation: 3 < 2 < 1 < 4 < 5  (most-stressed → most-positive).
# Remapped ordinal is 0..4, higher = better wellbeing / less stress.
RAW_TO_ORDINAL = {3: 0, 2: 1, 1: 2, 4: 3, 5: 4}
ORDINAL_MAX = 4  # max remapped value; used to rescale to the 0–100 stress score

# Secondary binary target (subset): {2,3}=stressed vs {4,5}=not; level 1 dropped as the
# ambiguous "a little stressed" near-baseline state.
BINARY_STRESSED_RAW = frozenset({2, 3})
BINARY_NOT_RAW = frozenset({4, 5})
BINARY_DROP_RAW = frozenset({1})

# ── Windowing ────────────────────────────────────────────────────────────────────────
WINDOW_DAYS = 7               # analysis window (spec §5): queryEvents ~10d retention is binding
COVERAGE_MIN_DAYS = 3         # drop a subject-day whose 7-day window has < this many days of data

# Weekly-aggregation target (Phase 2 period-level diagnostic): a subject-week label is the
# mean remapped stress over that week's responses. Require this many responses/week so a
# sparse week's noisy mean doesn't enter — median is 5/wk; >=3 keeps 75% of weeks and 47/48
# subjects while ~halving the weekly-mean standard error vs a 1-response week.
MIN_RESPONSES_PER_WEEK = 3

# A gap between consecutive LOCKED intervals is treated as a phone-in-use session only if
# it is <= this long. Screens auto-lock after minutes, so a multi-hour "unlocked" gap is
# missing lock events / phone-off, not real use — counting it inflates screen-on time.
MAX_SESSION_MINUTES = 180

# ── Sleep detection (from phonelock "locked" intervals) ──────────────────────────────
MIN_SLEEP_MINUTES = 90        # a locked interval shorter than this is not "main sleep"
# Main nightly sleep = the longest locked interval whose LOCAL midpoint falls in this band.
# Band wraps past midnight: 20:00 → 12:00 next day.
SLEEP_MIDPOINT_BAND = (20, 12)

# Sanity gate: if the cohort median of sleep_duration_median is outside this range the
# heuristic is broken — STOP rather than train on garbage.
SLEEP_SANITY_MIN_H = 5.0
SLEEP_SANITY_MAX_H = 11.0

# ── Night-time usage ─────────────────────────────────────────────────────────────────
# Fixed clock band (secondary feature, kept only for ablation vs the person-relative one).
NIGHT_FIXED_BAND = (0, 6)     # 00:00 → 06:00 local
# Person-relative night = each subject's own [median sleep onset, median sleep wake] over
# the window (primary). No extra constant: derived from that subject's detected sleep.

# ── Circadian regularity ─────────────────────────────────────────────────────────────
CIRCADIAN_BINS = 24          # hourly use-profile bins per day; regularity = mean pairwise corr

__all__ = [
    "SPEC_VERSION", "TIMEZONE", "RAW_TO_ORDINAL", "ORDINAL_MAX",
    "BINARY_STRESSED_RAW", "BINARY_NOT_RAW", "BINARY_DROP_RAW",
    "WINDOW_DAYS", "COVERAGE_MIN_DAYS", "MIN_SLEEP_MINUTES", "SLEEP_MIDPOINT_BAND",
    "SLEEP_SANITY_MIN_H", "SLEEP_SANITY_MAX_H", "NIGHT_FIXED_BAND", "CIRCADIAN_BINS",
]
