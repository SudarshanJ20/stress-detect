"""Assemble the modelling table: one row per (subject, local_date-with-valid-EMA), features
from the 7-day window ENDING at that day's local-midnight boundary (so the labelled day's own
behaviour never leaks into its features), gated by >= COVERAGE_MIN_DAYS of phonelock data.
"""
from __future__ import annotations

import datetime as dt

import numpy as np
import pandas as pd

from etl.studentlife_etl import build_events
from features import aux_features, screenlock_features
from features.labels import build_subject_day_labels, load_stress_responses
from features.spec_constants import COVERAGE_MIN_DAYS, SPEC_VERSION, TIMEZONE, WINDOW_DAYS
from guards import assert_raw_scale_protected, assert_timezone
from paths import PROCESSED_DIR

META_COLS = ["subject", "local_date", "stress_score", "binary_label",
             "n_responses", "n_binary_responses"]


def _collapse_duplicate_windows(df: pd.DataFrame, feat_cols: list[str]) -> tuple[pd.DataFrame, int]:
    """Adjacent label-days of one subject can yield IDENTICAL feature vectors when the
    non-overlapping boundary day has no phonelock data. Such rows are the same 7-day
    evidence, so collapse them to a single sample (regression label = mean; binary =
    majority) rather than double-count them in that subject's LOSO fold."""
    keys = ["subject"] + feat_cols
    grp = df.groupby(keys, dropna=False, sort=False)
    if grp.ngroup().nunique() == len(df):
        return df, 0
    out = []
    for _, sub in grp:
        r = sub.iloc[0].copy()
        if len(sub) > 1:
            r["stress_score"] = sub["stress_score"].mean()
            b = sub["binary_label"].dropna()
            r["binary_label"] = (
                float("nan") if len(b) == 0 or b.mean() == 0.5 else float(b.mean() > 0.5)
            )
            r["n_responses"] = int(sub["n_responses"].sum())
            r["n_binary_responses"] = int(sub["n_binary_responses"].sum())
            r["local_date"] = sub["local_date"].min()
        out.append(r)
    collapsed = pd.DataFrame(out)[df.columns].reset_index(drop=True)
    return collapsed, len(df) - len(collapsed)


def _window_bounds(local_date: dt.date) -> tuple[int, int]:
    w1 = pd.Timestamp(local_date, tz=TIMEZONE)               # local midnight of the labelled day
    w0 = w1 - pd.Timedelta(days=WINDOW_DAYS)
    return int(w0.timestamp()), int(w1.timestamp())


def build_dataset(save: bool = True) -> tuple[pd.DataFrame, dict]:
    assert_timezone(TIMEZONE)
    ev = build_events()
    lock, calls, sms = ev["phonelock"], ev["calls"], ev["sms"]

    subj_has_calls = set(calls["subject"].unique())
    subj_has_sms = set(sms["subject"].unique())
    lock_by = {s: g[["start_utc", "end_utc"]].to_numpy("int64")
               for s, g in lock.groupby("subject")}
    call_by = {s: g["event_utc"].to_numpy("int64") for s, g in calls.groupby("subject")}
    sms_by = {s: g["event_utc"].to_numpy("int64") for s, g in sms.groupby("subject")}

    responses, drop_stats = load_stress_responses()
    assert_raw_scale_protected(responses["raw_level"])       # tripwire: raw scale stays categorical
    day_labels = build_subject_day_labels(responses)

    rows = []
    n_gated = 0
    for r in day_labels.itertuples(index=False):
        w0, w1 = _window_bounds(r.local_date)
        locked = lock_by.get(r.subject, np.empty((0, 2), "int64"))
        sl = screenlock_features.screenlock_window_features(locked, w0, w1)
        if sl["days_with_data"] < COVERAGE_MIN_DAYS:
            n_gated += 1
            continue
        aux = aux_features.aux_window_features(
            call_by.get(r.subject, np.empty(0, "int64")),
            sms_by.get(r.subject, np.empty(0, "int64")),
            w0, w1, r.subject in subj_has_calls, r.subject in subj_has_sms,
        )
        rows.append({
            "subject": r.subject, "local_date": r.local_date,
            "stress_score": r.stress_score, "binary_label": r.binary_label,
            "n_responses": r.n_responses, "n_binary_responses": r.n_binary_responses,
            **sl, **aux,
        })

    df = pd.DataFrame(rows)
    feat_cols = screenlock_features.feature_names() + aux_features.feature_names()
    df = df[META_COLS + feat_cols]
    df, n_collapsed = _collapse_duplicate_windows(df, feat_cols)

    stats = {
        "spec_version": SPEC_VERSION,
        "raw_responses_with_level": int(len(responses)),
        "dropped_no_level": drop_stats,
        "subject_day_samples_total": int(len(day_labels)),
        "subject_day_samples_kept": int(len(df)),
        "subject_day_gated_low_coverage": int(n_gated),
        "subject_day_collapsed_duplicates": int(n_collapsed),
        "regression_n": int(df["stress_score"].notna().sum()),
        "binary_n": int(df["binary_label"].notna().sum()),
        "subjects": int(df["subject"].nunique()),
        "feature_columns": feat_cols,
    }
    if save:
        PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
        date_tag = dt.date.today().strftime("%Y%m%d")
        out = PROCESSED_DIR / f"studentlife_features_{SPEC_VERSION}_{date_tag}.parquet"
        df.to_parquet(out, index=False)
        stats["parquet"] = str(out)
    return df, stats
