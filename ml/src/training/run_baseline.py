"""Phase-2 baseline entry point: ETL → features → LOSO → report.

Run:  ml/.venv/bin/python ml/src/training/run_baseline.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # ml/src on path

import numpy as np
import pandas as pd

from evaluation import metrics as M
from evaluation.loso import loso_classification, loso_regression
from features.build_dataset import build_dataset
from features.spec_constants import (
    SLEEP_SANITY_MAX_H, SLEEP_SANITY_MIN_H, SPEC_VERSION,
)
from features import aux_features, screenlock_features
from guards import assert_no_duplicate_feature_vectors
from paths import PROCESSED_DIR

pd.set_option("display.width", 160)
pd.set_option("display.max_columns", 40)
LINE = "=" * 78


def _global_mean_baseline(pred: pd.DataFrame) -> np.ndarray:
    tot = pred["y_true"].sum(); n = len(pred)
    out = np.empty(n, float)
    for s, idx in pred.groupby("subject").groups.items():
        idx = list(idx); sub = pred.loc[idx, "y_true"].sum(); k = len(idx)
        out[[pred.index.get_loc(i) for i in idx]] = (tot - sub) / (n - k)  # other subjects' mean
    return out


def main() -> None:
    print(LINE); print(f"Phase 2 — StudentLife screen/lock baseline   (SPEC {SPEC_VERSION})"); print(LINE)

    df, stats = build_dataset(save=True)
    print("\n## Dataset")
    print(f"  raw Stress responses with a level : {stats['raw_responses_with_level']}")
    print(f"  dropped (no level key)            : {stats['dropped_no_level']}")
    print(f"    (122 bare-digit `null` entries are ambiguous → dropped, not recovered — spec limitation)")
    print(f"  subject-days total / gated / collapsed / kept : {stats['subject_day_samples_total']}"
          f" / {stats['subject_day_gated_low_coverage']} (coverage<3d)"
          f" / {stats['subject_day_collapsed_duplicates']} (dup windows) / {stats['subject_day_samples_kept']}")
    print(f"  REGRESSION n (subject-days)       : {stats['regression_n']}")
    print(f"  BINARY n (subject-days, subset)   : {stats['binary_n']}")
    print(f"  subjects                          : {stats['subjects']}")
    print(f"  parquet                           : {stats.get('parquet')}")

    feat_cols = screenlock_features.feature_names() + aux_features.feature_names()
    assert_no_duplicate_feature_vectors(df[feat_cols], "full feature matrix")

    # ── sleep sanity gate ────────────────────────────────────────────────────────────
    sd_med = float(np.nanmedian(df["sleep_duration_median"]))
    print(f"\n## Sleep sanity gate: cohort median sleep_duration_median = {sd_med:.2f} h")
    print("   distribution (h):",
          {k: round(float(v), 2) for k, v in
           df["sleep_duration_median"].describe(percentiles=[.1, .25, .5, .75, .9]).items()})
    if not (SLEEP_SANITY_MIN_H <= sd_med <= SLEEP_SANITY_MAX_H):
        print(f"\n!! STOP: median sleep {sd_med:.2f}h outside [{SLEEP_SANITY_MIN_H}, {SLEEP_SANITY_MAX_H}] "
              "— sleep-detection heuristic looks broken. Not training. Tell the human.")
        sys.exit(2)
    print("   → within plausible range; proceeding.")

    report = {"spec_version": SPEC_VERSION, "dataset": {k: v for k, v in stats.items()
                                                        if k != "feature_columns"}}

    # ── PRIMARY: regression on stress_score (0–100, 100=most stressed) ────────────────
    print("\n" + LINE); print("PRIMARY TARGET — regression: subject-day stress_score (0–100)"); print(LINE)
    reg = df.dropna(subset=["stress_score"]).reset_index(drop=True)
    rp = loso_regression(reg, feat_cols, "stress_score")
    model_m = M.regression_metrics(rp["y_true"], rp["y_pred"])
    cal = M.regression_calibration(rp["y_true"], rp["y_pred"])
    gm = _global_mean_baseline(rp); sm = M.subject_mean_baseline(rp, "y_true")
    base_global = M.regression_metrics(rp["y_true"], gm)
    base_subj = M.regression_metrics(rp["y_true"], sm)
    print(f"\n  label spread: mean={rp['y_true'].mean():.1f}  sd={rp['y_true'].std():.1f}  "
          f"min={rp['y_true'].min():.1f}  max={rp['y_true'].max():.1f}")
    print("\n  {:<22}{:>8}{:>8}{:>10}".format("model / baseline", "MAE", "RMSE", "Spearman"))
    for name, m in [("XGBoost", model_m), ("baseline: global mean", base_global),
                    ("baseline: SUBJECT mean", base_subj)]:
        print("  {:<22}{:>8.2f}{:>8.2f}{:>10}".format(
            name, m["MAE"], m["RMSE"], f"{m['Spearman']:.3f}" if m["Spearman"] == m["Spearman"] else "n/a"))
    print(f"  calibration: slope={cal['calib_slope']:.2f} (ideal 1.0), "
          f"binned error={cal['calib_error']:.1f} pts")
    beats = base_subj["MAE"] - model_m["MAE"]
    print(f"\n  >> vs SUBJECT-MEAN baseline (the one that matters): "
          f"MAE {'BETTER' if beats > 0 else 'WORSE'} by {abs(beats):.2f} pts "
          f"({'model learns day-to-day variation' if beats > 0 else 'model does NOT beat per-person mean'})")

    # per-subject regression table (full, not just the mean)
    print("\n  FULL per-subject LOSO table (regression):")
    rows = []
    smap = dict(zip(range(len(rp)), sm))
    rp["_subjmean"] = sm
    for s, g in rp.groupby("subject"):
        mm = M.regression_metrics(g["y_true"], g["y_pred"])
        bm = M.regression_metrics(g["y_true"], g["_subjmean"])
        rows.append({"subject": s, "n": mm["n"], "MAE": round(mm["MAE"], 1),
                     "subjmean_MAE": round(bm["MAE"], 1),
                     "beats_subjmean": mm["MAE"] < bm["MAE"],
                     "Spearman": round(mm["Spearman"], 2) if mm["Spearman"] == mm["Spearman"] else np.nan})
    reg_tbl = pd.DataFrame(rows).sort_values("MAE", ascending=False)
    print(reg_tbl.to_string(index=False))
    n_beat = int(reg_tbl["beats_subjmean"].sum())
    print(f"\n  subjects where model beats their own mean: {n_beat}/{len(reg_tbl)}")
    report["regression"] = {"model": model_m, "baseline_global": base_global,
                            "baseline_subject_mean": base_subj, "calibration": cal,
                            "subjects_beating_subject_mean": n_beat,
                            "per_subject": reg_tbl.to_dict("records")}

    # ── SECONDARY: binary classification on the {2,3} vs {4,5} subset ────────────────
    print("\n" + LINE); print("SECONDARY TARGET — binary: {2,3}=stressed vs {4,5}=not (level 1 dropped)"); print(LINE)
    clf = df.dropna(subset=["binary_label"]).reset_index(drop=True)
    clf["binary_label"] = clf["binary_label"].astype(int)
    print(f"  binary subset n={len(clf)} subject-days, {clf['subject'].nunique()} subjects, "
          f"pos(stressed) rate={clf['binary_label'].mean():.2f}  (NOTE: different n from regression)")
    cp = loso_classification(clf, feat_cols, "binary_label")
    cm = M.classification_metrics(cp["y_true"], cp["y_prob"])
    base_rate = cp["y_true"].mean()
    maj_pred = np.full(len(cp), 1 if base_rate >= 0.5 else 0)
    from sklearn.metrics import f1_score
    maj_f1 = float(f1_score(cp["y_true"], maj_pred, average="macro", zero_division=0))
    print("\n  {:<24}{:>10}{:>9}{:>9}{:>8}".format("model / baseline", "macro_F1", "ROC_AUC", "Brier", "ECE"))
    print("  {:<24}{:>10.3f}{:>9}{:>9.3f}{:>8.3f}".format(
        "XGBoost", cm["macro_F1"], f"{cm['ROC_AUC']:.3f}", cm["Brier"], cm["ECE"]))
    print("  {:<24}{:>10.3f}{:>9}{:>9}{:>8}".format(
        "baseline: majority", maj_f1, "0.500", "n/a", "n/a"))
    print("  {:<24}{:>10.3f}{:>9}".format("baseline: stratified rand", 0.5, "~0.500"))

    print("\n  FULL per-subject LOSO table (classification):")
    rows = []
    for s, g in cp.groupby("subject"):
        both = g["y_true"].nunique() == 2
        gm2 = M.classification_metrics(g["y_true"], g["y_prob"])
        rows.append({"subject": s, "n": len(g), "pos_rate": round(g["y_true"].mean(), 2),
                     "macro_F1": round(gm2["macro_F1"], 2),
                     "ROC_AUC": round(gm2["ROC_AUC"], 2) if both else np.nan})
    clf_tbl = pd.DataFrame(rows).sort_values("macro_F1")
    print(clf_tbl.to_string(index=False))
    report["classification"] = {"model": cm, "baseline_majority_macroF1": maj_f1,
                                "n": int(len(cp)), "per_subject": clf_tbl.to_dict("records")}

    # save metrics json into the gitignored processed dir
    out = PROCESSED_DIR / f"baseline_metrics_{SPEC_VERSION}.json"
    out.write_text(json.dumps(report, indent=2, default=str))
    print("\n" + LINE); print(f"metrics json → {out}"); print(LINE)


if __name__ == "__main__":
    main()
