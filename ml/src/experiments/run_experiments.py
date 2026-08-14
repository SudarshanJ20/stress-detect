"""Phase-2 diagnostics: WHY is the screen/lock baseline null? Three experiments, one table.
DIAGNOSTIC ONLY — does not change product scope or the spec.

  1. Ceiling test   — add out-of-scope streams (conversation/activity/audio/gps/dark).
  2. Construct test — vary the feature window (1/2/3/7 d) against the momentary label.
  3. Personalized   — give the model the subject's first k chronological days.

Run:  ml/.venv/bin/python ml/src/experiments/run_experiments.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # ml/src

import numpy as np
import pandas as pd

from etl.studentlife_etl import build_events
from evaluation import metrics as M
from evaluation.loso import loso_regression
from experiments import extended_features as EF
from features import aux_features, screenlock_features
from features.build_dataset import build_dataset
from features.spec_constants import TIMEZONE
from models.xgboost_baseline import make_regressor
from paths import RAW_ROOT

TARGET = "stress_score"
BACKBONE = screenlock_features.feature_names() + aux_features.feature_names()
LINE = "=" * 78


def _win_dates(d, w):
    return [d - pd.Timedelta(days=i) for i in range(1, w + 1)]  # D-1 .. D-W (as date via .date())


def _reg(df, feat):
    # collapse identical (subject+feature) rows first (short windows produce all-NaN dupes);
    # same rationale as the main pipeline's duplicate-window collapse.
    df = df.drop_duplicates(["subject"] + feat).reset_index(drop=True)
    p = loso_regression(df, feat, TARGET)
    m = M.regression_metrics(p["y_true"], p["y_pred"])
    return m, p


def backbone_at_window(samples, lock_by, call_by, sms_by, hc, hs, w):
    rows = []
    for r in samples.itertuples(index=False):
        w1 = pd.Timestamp(r.local_date, tz=TIMEZONE); w0 = w1 - pd.Timedelta(days=w)
        a, b = int(w0.timestamp()), int(w1.timestamp())
        sl = screenlock_features.screenlock_window_features(
            lock_by.get(r.subject, np.empty((0, 2), "int64")), a, b)
        ax = aux_features.aux_window_features(
            call_by.get(r.subject, np.empty(0, "int64")), sms_by.get(r.subject, np.empty(0, "int64")),
            a, b, r.subject in hc, r.subject in hs)
        rows.append({"subject": r.subject, "local_date": r.local_date,
                     "stress_score": r.stress_score, **sl, **ax})
    return pd.DataFrame(rows)


def personalized_eval(df, feat, k):
    """First k chronological days of each subject go into TRAIN; evaluate on the rest.
    Baseline = predict test days with the mean of that subject's k train days."""
    mp, mt, yb, yt = [], [], [], []
    for s, g in df.groupby("subject"):
        g = g.sort_values("local_date")
        if len(g) <= k:
            continue
        tr_s, te_s = g.iloc[:k], g.iloc[k:]
        train = pd.concat([df[df["subject"] != s], tr_s])
        model = make_regressor()
        model.fit(train[feat].to_numpy(float), train[TARGET].to_numpy(float))
        pred = np.clip(model.predict(te_s[feat].to_numpy(float)), 0, 100)
        mp.extend(pred); yt.extend(te_s[TARGET].to_numpy(float))
        yb.extend([tr_s[TARGET].mean()] * len(te_s))     # personal train-mean baseline
    return (M.regression_metrics(yt, mp), M.regression_metrics(yt, yb), len(yt))


def main():
    print(LINE); print("Phase 2 diagnostics — WHY the null?"); print(LINE)
    base, stats = build_dataset(save=False)
    samples = base[["subject", "local_date", "stress_score"]].copy()
    ev = build_events(); lock, calls, sms = ev["phonelock"], ev["calls"], ev["sms"]
    lock_by = {s: g[["start_utc", "end_utc"]].to_numpy("int64") for s, g in lock.groupby("subject")}
    call_by = {s: g["event_utc"].to_numpy("int64") for s, g in calls.groupby("subject")}
    sms_by = {s: g["event_utc"].to_numpy("int64") for s, g in sms.groupby("subject")}
    hc, hs = set(calls["subject"]), set(sms["subject"])

    # reference baselines (fixed sample set)
    _, p0 = _reg(base, BACKBONE)
    subj_mean = M.regression_metrics(p0["y_true"], M.subject_mean_baseline(p0, "y_true"))
    m_back = M.regression_metrics(p0["y_true"], p0["y_pred"])
    table = [("LOSO backbone (7d)  [current]", m_back["n"], m_back)]

    # ── EXPERIMENT 1 — ceiling test ───────────────────────────────────────────────────
    print("\n" + LINE); print("EXP 1 — ceiling test: add out-of-scope streams (conversation/activity/audio/gps/dark)")
    print(LINE); print("  precomputing daily aggregates (audio is large, ~minutes)…", flush=True)
    pre = EF.precompute_all()
    ext_rows = []
    for r in samples.itertuples(index=False):
        wd = [d.date() for d in _win_dates(pd.Timestamp(r.local_date), 7)]
        ext_rows.append(EF.extended_window_features(pre, r.subject, wd))
    ext = pd.DataFrame(ext_rows)
    ext_df = pd.concat([base.reset_index(drop=True), ext.reset_index(drop=True)], axis=1)
    m_ext, _ = _reg(ext_df, BACKBONE + EF.EXTENDED_FEATURES)
    m_extonly, _ = _reg(ext_df, EF.EXTENDED_FEATURES)
    table += [("LOSO backbone+extended (7d)", m_ext["n"], m_ext),
              ("LOSO extended-ONLY (7d)", m_extonly["n"], m_extonly)]
    print(f"  backbone MAE {m_back['MAE']:.2f} → backbone+extended MAE {m_ext['MAE']:.2f}  "
          f"(delta {m_ext['MAE']-m_back['MAE']:+.2f});  extended-only MAE {m_extonly['MAE']:.2f}")

    # ── EXPERIMENT 2 — construct/window mismatch ──────────────────────────────────────
    print("\n" + LINE); print("EXP 2 — window vs momentary label: recompute backbone at 1/2/3/7 d (same samples)")
    print(LINE)
    for w in (1, 2, 3):
        dfw = backbone_at_window(samples, lock_by, call_by, sms_by, hc, hs, w)
        mw, _ = _reg(dfw, BACKBONE)
        table.append((f"LOSO backbone ({w}d window)", mw["n"], mw))
        print(f"  {w}d window: MAE {mw['MAE']:.2f}  Spearman {mw['Spearman']:+.3f}")
    print(f"  7d window: MAE {m_back['MAE']:.2f}  Spearman {m_back['Spearman']:+.3f} (current)")

    # PSS survey availability
    pss = pd.read_csv(RAW_ROOT / "survey" / "PerceivedStressScale.csv")
    print(f"\n  PSS (PerceivedStressScale) survey: {pss['uid'].nunique()} subjects, {len(pss)} responses "
          f"({(pss['type']=='pre').sum()} pre / {(pss['type']=='post').sum()} post) — "
          "~1–2 per subject; far too few to train a per-day model on.")

    # ── EXPERIMENT 3 — personalized split ─────────────────────────────────────────────
    print("\n" + LINE); print("EXP 3 — personalized: first k chronological days of each subject in TRAIN")
    print(LINE)
    pers = []
    for k in (3, 5, 10):
        mk, bk, n = personalized_eval(base, BACKBONE, k)
        pers.append((k, n, mk, bk))
        print(f"  k={k:2d}: model MAE {mk['MAE']:.2f} (Spearman {mk['Spearman']:+.3f})  |  "
              f"personal train-mean MAE {bk['MAE']:.2f}  | test n={n}  "
              f"→ {'BEATS' if mk['MAE'] < bk['MAE'] else 'loses to'} personal mean")

    # ── single comparison table ───────────────────────────────────────────────────────
    print("\n" + LINE); print("SUMMARY — regression MAE (lower better) vs references"); print(LINE)
    print("  reference baselines:  subject-mean MAE {:.2f} | global-mean MAE {:.2f}".format(
        subj_mean["MAE"], M.regression_metrics(p0["y_true"], _gm(p0))["MAE"]))
    print("\n  {:<34}{:>6}{:>8}{:>8}{:>10}".format("condition", "n", "MAE", "RMSE", "Spearman"))
    for name, n, m in table:
        print("  {:<34}{:>6}{:>8.2f}{:>8.2f}{:>10.3f}".format(name, n, m["MAE"], m["RMSE"], m["Spearman"]))
    print("\n  personalized (Exp 3), evaluated on each subject's LATER days:")
    print("  {:<34}{:>6}{:>8}{:>10}".format("k (own days in train)", "n", "MAE", "pers-mean"))
    for k, n, mk, bk in pers:
        print("  {:<34}{:>6}{:>8.2f}{:>10.2f}".format(f"  k={k}", n, mk["MAE"], bk["MAE"]))
    print("\n  (subject-mean baseline = 16.95; any condition must beat THAT to matter.)")


def _gm(pred):
    tot = pred["y_true"].sum(); n = len(pred); out = np.empty(n, float)
    for s, idx in pred.groupby("subject").groups.items():
        idx = list(idx); sub = pred.loc[idx, "y_true"].sum(); k = len(idx)
        out[[pred.index.get_loc(i) for i in idx]] = (tot - sub) / (n - k)
    return out


if __name__ == "__main__":
    main()
