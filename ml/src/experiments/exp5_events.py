"""EXPERIMENT 5 (last diagnostic) — do exogenous EVENTS explain the within-subject variance
that behaviour misses?

Feature groups:
  PER-SUBJECT (deployable): deadline_today, deadline_next3, calendar_count_today
  COHORT-WIDE (same for all subjects on a date; would NOT transfer): day_of_week, week_of_term

Conditions vs the existing backbone/subject-mean numbers:
  (i)   events only
  (ii)  backbone + events (all)
  (iii) backbone + PER-SUBJECT events only  ← the one that matters for deployability

DECISION: if (iii) beats subject-mean MAE 16.95 by a meaningful margin, events are the
missing driver; else accept the null and pivot the target.

Run:  ml/.venv/bin/python ml/src/experiments/exp5_events.py
"""
from __future__ import annotations

import datetime as dt
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np
import pandas as pd

from evaluation import metrics as M
from evaluation.loso import loso_regression
from features import aux_features, screenlock_features
from features.build_dataset import build_dataset
from paths import RAW_ROOT

TARGET = "stress_score"
BACKBONE = screenlock_features.feature_names() + aux_features.feature_names()
PERSUBJ_EVENTS = ["deadline_today", "deadline_next3", "calendar_count_today"]
COHORT_EVENTS = ["day_of_week", "week_of_term"]
ALL_EVENTS = PERSUBJ_EVENTS + COHORT_EVENTS
TERM_START = dt.date(2013, 3, 27)
LINE = "=" * 78


def _reg(df, feat):
    df = df.drop_duplicates(["subject"] + feat).reset_index(drop=True)
    p = loso_regression(df, feat, TARGET)
    return M.regression_metrics(p["y_true"], p["y_pred"])


def load_deadlines():
    m = pd.read_csv(RAW_ROOT / "education" / "deadlines.csv")
    long = m.melt(id_vars="uid", var_name="date", value_name="n")
    long["date"] = pd.to_datetime(long["date"], errors="coerce").dt.date
    d = {(r.uid, r.date): float(r.n) for r in long.dropna(subset=["date"]).itertuples(index=False)}
    return d, set(m["uid"])


def load_calendar():
    out, subs = {}, set()
    for fp in sorted((RAW_ROOT / "calendar").glob("calendar_u*.csv")):
        s = "u" + fp.name.split("_u")[-1].split(".")[0]
        subs.add(s)
        df = pd.read_csv(fp, encoding="utf-8-sig")
        d = pd.to_datetime(df["DATE"], errors="coerce").dt.date
        for day, n in d.dropna().value_counts().items():
            out[(s, day)] = float(n)
    return out, subs


def main():
    print(LINE); print("EXPERIMENT 5 — exogenous events"); print(LINE)
    base, _ = build_dataset(save=False)
    dl, dl_subs = load_deadlines()
    cal, cal_subs = load_calendar()
    print(f"  deadlines: PER-SUBJECT, {len(dl_subs)} subjects | calendar: PER-SUBJECT, {len(cal_subs)} subjects")
    print(f"  base samples: {len(base)} subject-days, {base['subject'].nunique()} subjects\n")

    rows = []
    for r in base.itertuples(index=False):
        D = r.local_date
        dtoday = dl.get((r.subject, D), 0.0 if r.subject in dl_subs else np.nan)
        dnext = np.nan
        if r.subject in dl_subs:
            dnext = sum(dl.get((r.subject, D + dt.timedelta(days=k)), 0.0) for k in (1, 2, 3))
        cnt = cal.get((r.subject, D), 0.0 if r.subject in cal_subs else np.nan)
        rows.append({
            "deadline_today": dtoday, "deadline_next3": dnext, "calendar_count_today": cnt,
            "day_of_week": float(D.weekday()),
            "week_of_term": float((D - TERM_START).days // 7),
        })
    ev = pd.DataFrame(rows)
    df = pd.concat([base.reset_index(drop=True), ev.reset_index(drop=True)], axis=1)

    # event coverage on the modelling samples
    print("  event coverage on samples: "
          f"deadline_today non-null {df['deadline_today'].notna().mean():.0%}, "
          f"calendar_count non-null {df['calendar_count_today'].notna().mean():.0%}; "
          f"mean deadline_today={np.nanmean(df['deadline_today']):.2f}, "
          f"next3={np.nanmean(df['deadline_next3']):.2f}, cal={np.nanmean(df['calendar_count_today']):.2f}")

    # references
    p0 = loso_regression(base.drop_duplicates(["subject"] + BACKBONE).reset_index(drop=True), BACKBONE, TARGET)
    subj_mean = M.regression_metrics(p0["y_true"], M.subject_mean_baseline(p0, "y_true"))["MAE"]
    m_back = M.regression_metrics(p0["y_true"], p0["y_pred"])

    conds = [
        ("backbone only [current]", BACKBONE),
        ("(i) events only", ALL_EVENTS),
        ("(ii) backbone + events (all)", BACKBONE + ALL_EVENTS),
        ("(iii) backbone + PER-SUBJECT events", BACKBONE + PERSUBJ_EVENTS),
    ]
    print("\n  {:<40}{:>7}{:>8}{:>10}".format("condition", "n", "MAE", "Spearman"))
    res = {}
    for name, feat in conds:
        m = _reg(df, feat)
        res[name] = m
        print("  {:<40}{:>7}{:>8.2f}{:>10.3f}".format(name, m["n"], m["MAE"], m["Spearman"]))
    print("\n  reference: subject-mean MAE {:.2f} | global-mean MAE {:.2f}".format(
        subj_mean, M.regression_metrics(p0["y_true"], _gm(p0))["MAE"]))

    m3 = res["(iii) backbone + PER-SUBJECT events"]["MAE"]
    margin = subj_mean - m3
    print("\n" + LINE)
    print(f"DECISION: condition (iii) MAE {m3:.2f} vs subject-mean {subj_mean:.2f}  →  "
          f"{'BEATS' if margin > 0 else 'does NOT beat'} by {margin:+.2f} pts")
    if margin >= 1.0:
        print("  → events look like a real driver; calendar/deadlines become core features.")
    else:
        print("  → events do NOT rescue it. Accept the null as the finding and pivot the target.")
    print(LINE)


def _gm(pred):
    tot = pred["y_true"].sum(); n = len(pred); out = np.empty(n, float)
    for s, idx in pred.groupby("subject").groups.items():
        idx = list(idx); sub = pred.loc[idx, "y_true"].sum(); k = len(idx)
        out[[pred.index.get_loc(i) for i in idx]] = (tot - sub) / (n - k)
    return out


if __name__ == "__main__":
    main()
