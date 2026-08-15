"""Daily-feature SEQUENCE dataset for the Phase-4 temporal models.

Same 1157 subject-day samples / labels as the XGBoost baseline (fair ablation), but instead
of one flat 7-day aggregate vector, each sample becomes a (7, d_dyn) sequence of PER-DAY
features + a static vector of window-level quantities that are undefined per single day.

  dynamic (per day, the temporal lever): unlock/session/screen/night-use/comms + has_data mask
  static  (window-level): sleep + regularity + circadian + coverage + comms-present flags

Sleep/regularity/circadian are window-level (a single calendar day can't define sleep
regularity, and sleep crosses midnight) → they go in the static vector, so the DL model has
>= the information XGBoost had, and the SEQUENCE isolates the daily usage rhythm.
"""
from __future__ import annotations

import datetime as dt

import numpy as np
import pandas as pd

from etl.studentlife_etl import build_events
from features import screenlock_features
from features.build_dataset import build_dataset
from features.spec_constants import TIMEZONE, WINDOW_DAYS

DYNAMIC_FEATURES = [
    "unlock_count", "session_count", "session_duration_median", "screen_on_fraction",
    "nighttime_use_fraction_fixed", "call_count", "sms_count", "has_data",
]
STATIC_FEATURES = [
    "sleep_duration_median", "sleep_onset_hours", "sleep_wake_hours",
    "sleep_onset_regularity", "sleep_midpoint_regularity", "circadian_regularity",
    "days_with_data", "call_present", "sms_present",
]


def _day_bounds(day: dt.date) -> tuple[int, int]:
    a = pd.Timestamp(day, tz=TIMEZONE)
    return int(a.timestamp()), int((a + pd.Timedelta(days=1)).timestamp())


def _daily_vector(locked, calls, sms, day: dt.date) -> list[float]:
    w0, w1 = _day_bounds(day)
    sl = screenlock_features.screenlock_window_features(locked, w0, w1)
    has = sl["days_with_data"]  # 0 or 1 for a single-day window
    call_n = float(((calls >= w0) & (calls < w1)).sum()) if calls.size else 0.0
    sms_n = float(((sms >= w0) & (sms < w1)).sum()) if sms.size else 0.0
    return [
        sl["unlock_count_per_day_mean"], sl["session_count_per_day_mean"],
        sl["session_duration_median"], sl["screen_on_fraction"],
        sl["nighttime_use_fraction_fixed"], call_n, sms_n, 1.0 if has >= 1 else 0.0,
    ]


def build_sequence_dataset():
    """Returns dict with X_seq (N,7,d_dyn), X_static (N,s), y (N,), subject (N,), dates,
    and the reference flat feature frame (for the duplicate-vector guard / XGBoost parity)."""
    base, _ = build_dataset(save=False)  # identical sample set + window-level features
    ev = build_events()
    lock_by = {s: g[["start_utc", "end_utc"]].to_numpy("int64") for s, g in ev["phonelock"].groupby("subject")}
    call_by = {s: g["event_utc"].to_numpy("int64") for s, g in ev["calls"].groupby("subject")}
    sms_by = {s: g["event_utc"].to_numpy("int64") for s, g in ev["sms"].groupby("subject")}

    seqs = []
    for r in base.itertuples(index=False):
        locked = lock_by.get(r.subject, np.empty((0, 2), "int64"))
        calls = call_by.get(r.subject, np.empty(0, "int64"))
        sms = sms_by.get(r.subject, np.empty(0, "int64"))
        days = [r.local_date - dt.timedelta(days=WINDOW_DAYS - k) for k in range(WINDOW_DAYS)]  # oldest→newest
        seqs.append([_daily_vector(locked, calls, sms, d) for d in days])

    X_seq = np.asarray(seqs, dtype="float32")               # (N, 7, d_dyn)
    X_static = base[STATIC_FEATURES].to_numpy("float32")    # (N, s)
    y = base["stress_score"].to_numpy("float32")
    return {
        "X_seq": X_seq, "X_static": X_static, "y": y,
        "subject": base["subject"].to_numpy(), "local_date": base["local_date"].to_numpy(),
        "dyn_features": DYNAMIC_FEATURES, "static_features": STATIC_FEATURES,
        "base": base,
    }
