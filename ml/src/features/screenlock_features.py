"""Backbone features from phonelock LOCKED intervals, over one 7-day window.

Definitions and thresholds mirror docs/feature-spec.md v0.4.0. Clock/night/sleep quantities
are computed in local time (America/New_York); clock-time central tendency & regularity use
CIRCULAR statistics (mean resultant vector) so the midnight wrap is handled correctly.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from features.spec_constants import (
    BAND_EDGE_EPS, CIRCADIAN_BINS, MAX_SESSION_MINUTES, MIN_SLEEP_MINUTES,
    NIGHT_FIXED_BAND, SLEEP_MIDPOINT_BAND, TIMEZONE,
)

_H = 3600.0


# ── circular stats on clock-hours (period 24) ────────────────────────────────────────
def _circ(hours: np.ndarray):
    if len(hours) == 0:
        return np.nan, np.nan
    ang = 2 * np.pi * (hours % 24) / 24.0
    C, S = np.cos(ang).mean(), np.sin(ang).mean()
    # R <= 1 by construction; anything above is float error. Clamped so sqrt(-2 ln R) can
    # never see a negative argument and return a spurious NaN. Inert here (R > 1.0 occurs
    # in 0 of 1161 StudentLife samples) but kept so BOTH extractors are robust the same
    # way — the JVM and numpy differ by ~1 ULP in cos/sin/atan2, and the Kotlin port hit
    # exactly this. See docs/feature-spec.md §8.
    R = min(np.hypot(C, S), 1.0)
    mean_h = (np.arctan2(S, C) % (2 * np.pi)) * 24 / (2 * np.pi)
    sd_h = np.sqrt(-2.0 * np.log(R)) * 24 / (2 * np.pi) if R > 0 else np.nan
    return mean_h, sd_h


def _in_band(hour: float, band: tuple[float, float]) -> bool:
    """Half-open [lo, hi) in clock hours, wrapping past midnight.

    Edges are SNAPPED (BAND_EDGE_EPS): exactly on `lo` is INSIDE, exactly on `hi` is
    OUTSIDE. Without this, a ~1 ULP difference between the JVM and numpy in the circular
    mean that produces `lo`/`hi` flips the result for a subject whose unlock time equals
    their own mean wake time — a boolean divergence no tolerance can absorb. The Kotlin
    `ClockBand.contains` implements the identical rule from the same constant.
    """
    lo, hi = band
    if abs(hour - lo) < BAND_EDGE_EPS:
        return True
    if abs(hour - hi) < BAND_EDGE_EPS:
        return False
    return (lo <= hour < hi) if lo < hi else (hour >= lo or hour < hi)  # wrap past midnight


def _local(ts_utc: np.ndarray) -> pd.DatetimeIndex:
    return pd.to_datetime(ts_utc, unit="s", utc=True).tz_convert(TIMEZONE)


def screenlock_window_features(locked: np.ndarray, w0: int, w1: int) -> dict:
    """`locked`: (n,2) int64 epoch-second LOCKED intervals for one subject (any order).
    Returns the backbone feature dict for the window [w0, w1)."""
    f: dict[str, float] = {}
    # intervals overlapping the window
    if locked.size:
        m = (locked[:, 1] > w0) & (locked[:, 0] < w1)
        iv = locked[m]
        iv = iv[np.argsort(iv[:, 0])]
    else:
        iv = np.empty((0, 2), dtype="int64")

    # ---- coverage (distinct local dates touched by any locked interval, clipped) -------
    dates = set()
    for s, e in iv:
        s_c, e_c = max(int(s), w0), min(int(e), w1)
        d0 = _local(np.array([s_c]))[0].normalize()
        d1 = _local(np.array([e_c]))[0].normalize()
        for d in pd.date_range(d0, d1, freq="D"):
            dates.add(d.date())
    days_with_data = len(dates)
    f["days_with_data"] = float(days_with_data)
    if len(iv) == 0 or days_with_data == 0:
        return _empty_like(f)

    starts_l = _local(iv[:, 0]); ends_l = _local(iv[:, 1])
    dur_h = (iv[:, 1] - iv[:, 0]) / _H

    # ---- sleep: longest qualifying locked interval per night ----------------------------
    mids_utc = (iv[:, 0] + iv[:, 1]) / 2.0
    mids_l = _local(mids_utc.astype("int64"))
    mid_hour = mids_l.hour + mids_l.minute / 60.0
    qualifies = (dur_h * 60 >= MIN_SLEEP_MINUTES) & np.array(
        [_in_band(h, SLEEP_MIDPOINT_BAND) for h in mid_hour]
    )
    nights = {}  # night_key -> (dur_h, onset_hour, wake_hour, midpoint_hour)
    for i in np.where(qualifies)[0]:
        night_key = (mids_l[i] - pd.Timedelta(hours=12)).date()
        onset_h = starts_l[i].hour + starts_l[i].minute / 60.0
        wake_h = ends_l[i].hour + ends_l[i].minute / 60.0
        cand = (dur_h[i], onset_h, wake_h, mid_hour[i])
        if night_key not in nights or cand[0] > nights[night_key][0]:
            nights[night_key] = cand
    if nights:
        arr = np.array(list(nights.values()))
        f["n_sleep_nights"] = float(len(nights))
        f["sleep_duration_median"] = float(np.median(arr[:, 0]))
        onset_mean, onset_sd = _circ(arr[:, 1])
        wake_mean, _ = _circ(arr[:, 2])
        _, mid_sd = _circ(arr[:, 3])
        f["sleep_onset_hours"] = onset_mean
        f["sleep_wake_hours"] = wake_mean
        f["sleep_onset_regularity"] = onset_sd       # circular SD (lower = more regular)
        f["sleep_midpoint_regularity"] = mid_sd
    else:
        for k in ("n_sleep_nights", "sleep_duration_median", "sleep_onset_hours",
                  "sleep_wake_hours", "sleep_onset_regularity", "sleep_midpoint_regularity"):
            f[k] = np.nan

    # ---- unlocks (end of each locked interval that truly ends inside the window) --------
    unlock_mask = (iv[:, 1] >= w0) & (iv[:, 1] < w1)
    unlock_ts = iv[unlock_mask, 1]
    unlock_l = _local(unlock_ts) if unlock_ts.size else _local(np.array([], dtype="int64"))
    unlock_dates = pd.Series(unlock_l.date if unlock_ts.size else [], dtype="object")
    per_day_unlocks = unlock_dates.value_counts()
    f["unlock_count_per_day_mean"] = float(len(unlock_ts) / days_with_data)
    f["unlock_count_sd"] = float(per_day_unlocks.reindex(list(dates)).fillna(0).std(ddof=0))

    # ---- use-sessions = gaps between consecutive locked intervals -----------------------
    sess = []  # (start_utc, end_utc)
    max_sess_s = MAX_SESSION_MINUTES * 60
    for a in range(len(iv) - 1):
        gs, ge = int(iv[a, 1]), int(iv[a + 1, 0])
        gs, ge = max(gs, w0), min(ge, w1)
        if gs < ge <= gs + max_sess_s:  # drop implausibly long gaps = data-gap/phone-off, not use
            sess.append((gs, ge))
    sess = np.array(sess, dtype="int64") if sess else np.empty((0, 2), "int64")
    sess_dur_min = (sess[:, 1] - sess[:, 0]) / 60.0 if sess.size else np.array([])
    f["session_count_per_day_mean"] = float(len(sess) / days_with_data)
    f["session_duration_median"] = float(np.median(sess_dur_min)) if sess.size else np.nan
    f["session_duration_iqr"] = (
        float(np.subtract(*np.percentile(sess_dur_min, [75, 25]))) if sess.size else np.nan
    )

    # per-day use seconds → screen_on_fraction (mean daily fraction over days-with-data)
    use_by_day = {}
    for gs, ge in sess:
        d = _local(np.array([gs]))[0].date()
        use_by_day[d] = use_by_day.get(d, 0.0) + (ge - gs)
    frac = [min(use_by_day.get(d, 0.0) / 86400.0, 1.0) for d in dates]
    f["screen_on_fraction"] = float(np.mean(frac))

    # ---- night-time use: person-relative (primary) + fixed (ablation) -------------------
    if sess.size:
        sess_start_l = _local(sess[:, 0])
        sess_hour = sess_start_l.hour + sess_start_l.minute / 60.0
        total_use = float((sess[:, 1] - sess[:, 0]).sum())
    else:
        sess_hour = np.array([]); total_use = 0.0

    def _night_use_fraction(band):
        if not sess.size or total_use == 0:
            return np.nan
        mask = np.array([_in_band(h, band) for h in sess_hour])
        return float((sess[mask, 1] - sess[mask, 0]).sum() / total_use)

    def _night_unlocks_per_day(band):
        if not unlock_ts.size:
            return 0.0
        uh = unlock_l.hour + unlock_l.minute / 60.0
        mask = np.array([_in_band(h, band) for h in uh])
        return float(mask.sum() / days_with_data)

    # personal band = [circ-mean onset, circ-mean wake]; falls back to fixed if no sleep
    if not np.isnan(f["sleep_onset_hours"]) and not np.isnan(f["sleep_wake_hours"]):
        personal = (f["sleep_onset_hours"], f["sleep_wake_hours"])
    else:
        personal = NIGHT_FIXED_BAND
    f["nighttime_use_fraction_personal"] = _night_use_fraction(personal)
    f["nighttime_unlock_per_day_personal"] = _night_unlocks_per_day(personal)
    f["nighttime_use_fraction_fixed"] = _night_use_fraction(NIGHT_FIXED_BAND)
    f["nighttime_unlock_per_day_fixed"] = _night_unlocks_per_day(NIGHT_FIXED_BAND)

    # ---- circadian regularity: mean pairwise corr of daily hourly-use profiles ----------
    prof = {}
    for gs, ge in sess:
        cur = gs
        while cur < ge:
            t = _local(np.array([cur]))[0]
            day = t.date(); hr = t.hour
            nxt = min(ge, int((t.normalize() + pd.Timedelta(hours=hr + 1)).timestamp()))
            prof.setdefault(day, np.zeros(CIRCADIAN_BINS))[hr] += (nxt - cur)
            cur = nxt
    vecs = [v for v in prof.values() if v.std() > 0]
    if len(vecs) >= 2:
        M = np.vstack(vecs)
        corr = np.corrcoef(M)
        iu = np.triu_indices(len(vecs), k=1)
        f["circadian_regularity"] = float(np.nanmean(corr[iu]))
    else:
        f["circadian_regularity"] = np.nan
    return f


_FEATURE_KEYS = [
    "days_with_data", "n_sleep_nights", "sleep_duration_median", "sleep_onset_hours",
    "sleep_wake_hours", "sleep_onset_regularity", "sleep_midpoint_regularity",
    "unlock_count_per_day_mean", "unlock_count_sd", "session_count_per_day_mean",
    "session_duration_median", "session_duration_iqr", "screen_on_fraction",
    "nighttime_use_fraction_personal", "nighttime_unlock_per_day_personal",
    "nighttime_use_fraction_fixed", "nighttime_unlock_per_day_fixed", "circadian_regularity",
]


def _empty_like(f: dict) -> dict:
    out = {k: np.nan for k in _FEATURE_KEYS}
    out.update(f)
    return out


def feature_names() -> list[str]:
    return list(_FEATURE_KEYS)
