"""EXPERIMENT 6 — WEEKLY (period-level) target.

Aggregate EMA to subject-week: label = mean remapped stress over that week's responses
(0–100, 100=most stressed), keeping weeks with >= MIN_RESPONSES_PER_WEEK. Backbone features
over that same week's 7 days. Same LOSO harness, guards, baselines.

Decision: if the model beats subject-mean MAE at week level, period-level constructs are the
answer and we build on that; else stop modelling on StudentLife and wait for GLOBEM.

Run:  ml/.venv/bin/python ml/src/experiments/exp6_weekly.py
"""
from __future__ import annotations

import datetime as dt
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np
import pandas as pd

from etl.studentlife_etl import build_events
from evaluation import metrics as M
from evaluation.loso import loso_regression
from features import aux_features, screenlock_features
from features.labels import load_stress_responses
from features.spec_constants import (
    COVERAGE_MIN_DAYS, MIN_RESPONSES_PER_WEEK, ORDINAL_MAX, TIMEZONE,
)
from guards import assert_no_duplicate_feature_vectors, assert_timezone

TARGET = "stress_score"
BACKBONE = screenlock_features.feature_names() + aux_features.feature_names()
WITHIN_RESP_SD = 9.4  # per-response within-day STD on 0–100 (Exp 4)
LINE = "=" * 78


def _icc(day, col):
    grand = day[col].mean()
    groups = [g[col].to_numpy() for _, g in day.groupby("subject") if len(g) >= 1]
    k = len(groups); N = sum(len(x) for x in groups)
    means = np.array([x.mean() for x in groups]); ns = np.array([len(x) for x in groups])
    SSB = float(np.sum(ns * (means - grand) ** 2)); SSW = float(np.sum([((x - x.mean()) ** 2).sum() for x in groups]))
    MSB, MSW = SSB / (k - 1), SSW / (N - k)
    n0 = (N - np.sum(ns ** 2) / N) / (k - 1)
    var_b = max((MSB - MSW) / n0, 0.0)
    return var_b / (var_b + MSW), np.sqrt(var_b), np.sqrt(MSW)


def main():
    print(LINE); print(f"EXPERIMENT 6 — weekly target (>= {MIN_RESPONSES_PER_WEEK} responses/week)"); print(LINE)
    assert_timezone(TIMEZONE)
    resp, _ = load_stress_responses()
    resp["score"] = (ORDINAL_MAX - resp["ordinal"]) / ORDINAL_MAX * 100.0
    resp["week"] = resp["local_date"].map(lambda d: d - dt.timedelta(days=d.weekday()))  # Monday

    wk = resp.groupby(["subject", "week"]).agg(
        n_responses=("ordinal", "size"), mean_ordinal=("ordinal", "mean")).reset_index()
    wk["stress_score"] = (ORDINAL_MAX - wk["mean_ordinal"]) / ORDINAL_MAX * 100.0
    print(f"\n  subject-weeks (>=1 resp): {len(wk)}; responses/week median={wk['n_responses'].median():.0f} "
          f"(p25={wk['n_responses'].quantile(.25):.0f}, p75={wk['n_responses'].quantile(.75):.0f})")
    wk = wk[wk["n_responses"] >= MIN_RESPONSES_PER_WEEK].reset_index(drop=True)

    ev = build_events()
    lock_by = {s: g[["start_utc", "end_utc"]].to_numpy("int64") for s, g in ev["phonelock"].groupby("subject")}
    call_by = {s: g["event_utc"].to_numpy("int64") for s, g in ev["calls"].groupby("subject")}
    sms_by = {s: g["event_utc"].to_numpy("int64") for s, g in ev["sms"].groupby("subject")}
    hc, hs = set(ev["calls"]["subject"]), set(ev["sms"]["subject"])

    rows, gated = [], 0
    for r in wk.itertuples(index=False):
        w0 = int(pd.Timestamp(r.week, tz=TIMEZONE).timestamp())
        w1 = int((pd.Timestamp(r.week, tz=TIMEZONE) + pd.Timedelta(days=7)).timestamp())
        sl = screenlock_features.screenlock_window_features(lock_by.get(r.subject, np.empty((0, 2), "int64")), w0, w1)
        if sl["days_with_data"] < COVERAGE_MIN_DAYS:
            gated += 1; continue
        ax = aux_features.aux_window_features(call_by.get(r.subject, np.empty(0, "int64")),
                                              sms_by.get(r.subject, np.empty(0, "int64")),
                                              w0, w1, r.subject in hc, r.subject in hs)
        rows.append({"subject": r.subject, "week": r.week, "stress_score": r.stress_score,
                     "n_responses": r.n_responses, **sl, **ax})
    df = pd.DataFrame(rows).drop_duplicates(["subject"] + BACKBONE).reset_index(drop=True)
    df["local_date"] = df["week"]  # loso helper expects this column name
    print(f"  kept after >=3-resp gate and >={COVERAGE_MIN_DAYS}-day coverage gate ({gated} gated): "
          f"{len(df)} subject-weeks, {df['subject'].nunique()} subjects")
    assert_no_duplicate_feature_vectors(df[BACKBONE], "weekly feature matrix")

    # ICC at week level (vs day-level 0.27)
    icc, sd_b, sd_w = _icc(df, "stress_score")
    print(f"\n  WEEK-level ICC(1) = {icc:.2f}  (day-level was 0.27)  | between-SD={sd_b:.1f}, within-SD={sd_w:.1f}")
    med_r = df["n_responses"].median()
    noise = WITHIN_RESP_SD / np.sqrt(med_r)
    print(f"  est. week-level noise floor ≈ {WITHIN_RESP_SD:.1f}/√{med_r:.0f} = {noise:.1f} (SD of weekly-mean label)")

    # LOSO
    p = loso_regression(df, BACKBONE, TARGET)
    m = M.regression_metrics(p["y_true"], p["y_pred"])
    subj = M.regression_metrics(p["y_true"], M.subject_mean_baseline(p, "y_true"))
    tot = p["y_true"].sum(); n = len(p); gm = np.empty(n)
    for s, idx in p.groupby("subject").groups.items():
        idx = list(idx); gm[[p.index.get_loc(i) for i in idx]] = (tot - p.loc[idx, "y_true"].sum()) / (n - len(idx))
    glob = M.regression_metrics(p["y_true"], gm)
    print("\n  {:<26}{:>7}{:>8}{:>8}{:>10}".format("condition", "n", "MAE", "RMSE", "Spearman"))
    for name, mm in [("XGBoost backbone (weekly)", m), ("baseline: global mean", glob),
                     ("baseline: SUBJECT mean", subj)]:
        print("  {:<26}{:>7}{:>8.2f}{:>8.2f}{:>10.3f}".format(name, mm["n"], mm["MAE"], mm["RMSE"], mm["Spearman"]))

    margin = subj["MAE"] - m["MAE"]
    print("\n" + LINE)
    print(f"DECISION: weekly model MAE {m['MAE']:.2f} vs subject-mean {subj['MAE']:.2f} → "
          f"{'BEATS' if margin > 0 else 'does NOT beat'} by {margin:+.2f} pts")
    print("  → period-level constructs are the answer; build on weekly." if margin >= 1.0
          else "  → weekly does not beat the mean either. Stop modelling on StudentLife; wait for GLOBEM.")
    print(LINE)


if __name__ == "__main__":
    main()
