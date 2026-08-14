"""Auxiliary call/SMS features. Per spec §1/§4 these are NEVER backbone and each value MUST
travel with an explicit `*_present` missingness flag: a subject who lacks the stream gets
value 0 AND present=0, so the model can tell "no calls this week" from "we can't see calls".
"""
from __future__ import annotations

import numpy as np

from features.spec_constants import WINDOW_DAYS


def aux_window_features(
    call_ts: np.ndarray, sms_ts: np.ndarray, w0: int, w1: int,
    subject_has_calls: bool, subject_has_sms: bool,
) -> dict:
    def rate(ts, has_stream):
        if not has_stream:
            return 0.0, 0.0  # value, present — missing stream, flagged by present=0
        n = int(((ts >= w0) & (ts < w1)).sum()) if ts.size else 0
        return n / float(WINDOW_DAYS), 1.0

    call_rate, call_present = rate(call_ts, subject_has_calls)
    sms_rate, sms_present = rate(sms_ts, subject_has_sms)
    return {
        "call_count_per_day": call_rate,
        "call_present": call_present,
        "sms_count_per_day": sms_rate,
        "sms_present": sms_present,
    }


def feature_names() -> list[str]:
    return ["call_count_per_day", "call_present", "sms_count_per_day", "sms_present"]
