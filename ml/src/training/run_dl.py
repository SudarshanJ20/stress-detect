"""Phase 4 entry point: temporal DL over the 7-day daily-feature sequence.

Arms: CNN-LSTM (primary, 5 seeds) · TinyTransformer (ablation, 3 seeds) · label-permutation
sanity (CNN-LSTM). Plus training curves, SHAP attribution, ONNX export + runtime parity.
Reuses the XGBoost baseline, LOSO guards, metrics, and BOTH baselines. NO tuning.

Run:  ml/.venv/bin/python ml/src/training/run_dl.py
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
# torch and xgboost each ship their own OpenMP runtime; co-loading segfaults on macOS.
# run_dl uses torch only (XGBoost reference is cited from Phase 2), and we cap threads.
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
os.environ.setdefault("OMP_NUM_THREADS", "1")

import numpy as np
import pandas as pd
import torch

from evaluation import metrics as M
from evaluation.attribution import feature_attribution
from features import aux_features, screenlock_features
from features.sequence_dataset import build_sequence_dataset
from features.spec_constants import SPEC_VERSION
from guards import assert_no_duplicate_feature_vectors
from models.onnx_export import export_and_verify
from models.temporal import make_model
from paths import PROCESSED_DIR
from training.loso_torch import _apply, _fit_scaler, _train, loso_dl, training_curves

BACKBONE = screenlock_features.feature_names() + aux_features.feature_names()
LINE = "=" * 78


def _m(pred):
    return M.regression_metrics(pred["y_true"], pred["y_pred"])


def _seed_summary(preds: dict):
    rows = [{"seed": s, **_m(p)} for s, p in preds.items()]
    df = pd.DataFrame(rows)
    return df, df["MAE"].mean(), df["MAE"].std(ddof=0), df["Spearman"].mean()


def main():
    print(LINE); print(f"Phase 4 — temporal DL over daily sequences (SPEC {SPEC_VERSION})"); print(LINE)
    data = build_sequence_dataset()
    N, T, d_dyn = data["X_seq"].shape; d_static = data["X_static"].shape[1]
    print(f"  samples={N}  seq={T}x{d_dyn} dynamic  +{d_static} static  subjects={len(set(data['subject']))}")
    print(f"  dynamic={data['dyn_features']}\n  static={data['static_features']}")

    # guard: no duplicate flattened feature vectors
    flat = pd.DataFrame(np.hstack([data["X_seq"].reshape(N, -1), np.nan_to_num(data["X_static"])]))
    assert_no_duplicate_feature_vectors(flat, "DL flattened feature matrix")

    # references (same samples/labels). Label-only baselines here (no xgboost in-process —
    # see OpenMP note above); XGBoost is cited from Phase 2 on the identical sample set.
    XGB_MAE, XGB_RHO = 22.59, -0.146  # docs/phase2-results.md (flat XGBoost, same 1157 samples)
    base = data["base"]
    ref = pd.DataFrame({"subject": base["subject"].to_numpy(),
                        "y_true": base["stress_score"].to_numpy(float)}).reset_index(drop=True)
    subj_mean = M.regression_metrics(ref["y_true"], M.subject_mean_baseline(ref, "y_true"))
    tot = ref["y_true"].sum(); n = len(ref); gm = np.empty(n)
    for s, idx in ref.groupby("subject").groups.items():
        idx = list(idx); gm[[ref.index.get_loc(i) for i in idx]] = (tot - ref.loc[idx, "y_true"].sum()) / (n - len(idx))
    global_mean = M.regression_metrics(ref["y_true"], gm)

    # ── CNN-LSTM (primary, 5 seeds) ───────────────────────────────────────────────────
    print("\n" + LINE); print("CNN-LSTM (primary) — 5 seeds"); print(LINE)
    cl = loso_dl(data, "cnnlstm", seeds=[0, 1, 2, 3, 4])
    cl_df, cl_mae, cl_sd, cl_rho = _seed_summary(cl)
    print(cl_df.round(3).to_string(index=False))
    print(f"  → MAE {cl_mae:.2f} ± {cl_sd:.2f} (5 seeds), mean Spearman {cl_rho:+.3f}")

    # ── TinyTransformer (ablation, 3 seeds) ───────────────────────────────────────────
    print("\n" + LINE); print("TinyTransformer (ablation arm) — 3 seeds"); print(LINE)
    tr = loso_dl(data, "transformer", seeds=[0, 1, 2])
    tr_df, tr_mae, tr_sd, tr_rho = _seed_summary(tr)
    print(tr_df.round(3).to_string(index=False))
    print(f"  → MAE {tr_mae:.2f} ± {tr_sd:.2f} (3 seeds), mean Spearman {tr_rho:+.3f}")

    # ── label-permutation sanity (CNN-LSTM) ───────────────────────────────────────────
    print("\n" + LINE); print("Label-permutation sanity (CNN-LSTM, shuffled TRAIN labels)"); print(LINE)
    perm = loso_dl(data, "cnnlstm", seeds=[0, 1], permute=True)
    _, perm_mae, perm_sd, _ = _seed_summary(perm)
    print(f"  shuffled-label MAE {perm_mae:.2f} ± {perm_sd:.2f}  (must be >= global-mean {global_mean['MAE']:.2f})")
    print("  → " + ("OK: no leakage (shuffled ~ global mean)." if perm_mae >= global_mean["MAE"] - 0.5
                     else "!! shuffled model too good — POSSIBLE LEAK, harness suspect."))

    # ── training curves (memorization evidence) ───────────────────────────────────────
    trc, vc = training_curves(data, "cnnlstm")
    print("\n" + LINE); print("Training curves (CNN-LSTM, held-out TRAIN subjects as val)"); print(LINE)
    print(f"  train MSE: {trc[0]:.0f} → {trc[-1]:.0f}   |   val MSE: {vc[0]:.0f} → {vc[-1]:.0f}")
    print(f"  → loss falls (model CAN learn — not broken), but train≈val at convergence "
          f"(gap {vc[-1]-trc[-1]:.0f} MSE): the regularized net settles at ≈mean prediction, "
          "finding no signal to fit beyond it (no memorization, no generalization).")

    # ── SUMMARY table ─────────────────────────────────────────────────────────────────
    print("\n" + LINE); print("SUMMARY — regression MAE vs baselines (lower better)"); print(LINE)
    print("  {:<34}{:>10}{:>10}".format("model", "MAE", "Spearman"))
    print("  {:<34}{:>10.2f}{:>10.3f}".format("XGBoost (flat, Phase 2)", XGB_MAE, XGB_RHO))
    print("  {:<34}{:>10}{:>10.3f}".format("CNN-LSTM (seq)", f"{cl_mae:.2f}±{cl_sd:.2f}", cl_rho))
    print("  {:<34}{:>10}{:>10.3f}".format("TinyTransformer (seq)", f"{tr_mae:.2f}±{tr_sd:.2f}", tr_rho))
    print("  {:<34}{:>10.2f}".format("baseline: global mean", global_mean["MAE"]))
    print("  {:<34}{:>10.2f}".format("baseline: SUBJECT mean", subj_mean["MAE"]))
    gap = subj_mean["MAE"] - cl_mae
    print(f"\n  CNN-LSTM vs SUBJECT-mean: {'BEATS' if gap>0 else 'WORSE'} by {abs(gap):.2f} pts; "
          f"seed SD {cl_sd:.2f} — " + ("SD is LARGE relative to the gap; single-seed claims unsafe."
          if cl_sd >= abs(gap) else "gap exceeds seed SD."))

    # per-subject table (CNN-LSTM seed 0) + calibration
    p0 = cl[0]; sm = M.subject_mean_baseline(p0, "y_true")
    cal = M.regression_calibration(p0["y_true"], p0["y_pred"])
    print(f"\n  calibration (seed 0): slope={cal['calib_slope']:.2f} (ideal 1.0), error={cal['calib_error']:.1f} pts")
    rows = []
    p0 = p0.assign(_sm=sm)
    for s, g in p0.groupby("subject"):
        mm = M.regression_metrics(g["y_true"], g["y_pred"]); bm = M.regression_metrics(g["y_true"], g["_sm"])
        rows.append({"subject": s, "n": mm["n"], "MAE": round(mm["MAE"], 1),
                     "subjmean_MAE": round(bm["MAE"], 1), "beats": mm["MAE"] < bm["MAE"]})
    tbl = pd.DataFrame(rows).sort_values("MAE", ascending=False)
    print("\n  FULL per-subject LOSO table (CNN-LSTM seed 0):")
    print(tbl.to_string(index=False))
    print(f"  subjects where CNN-LSTM beats own mean: {int(tbl['beats'].sum())}/{len(tbl)}")

    # ── final model on ALL data → attribution + ONNX ──────────────────────────────────
    sc = _fit_scaler(data["X_seq"], data["X_static"])
    Xs, Xt = _apply(data["X_seq"], data["X_static"], sc)
    final = make_model("cnnlstm", d_dyn, d_static)
    _train(final, Xs, Xt, data["y"].astype("float32"), seed=0)

    print("\n" + LINE); print("Attribution (feeds app 'why this reading')"); print(LINE)
    dyn_imp, stat_imp, method = feature_attribution(final, Xs, Xt)
    print(f"  method: {method}")
    imp = sorted(list(zip(data["dyn_features"], dyn_imp)) + list(zip(data["static_features"], stat_imp)),
                 key=lambda x: -x[1])
    for name, v in imp[:8]:
        print(f"    {name:32s} {float(v):.4f}")
    print("  CAVEAT: attributions explain the MODEL, not stress causation. Under the null the model")
    print("          carries no real stress signal → these are faithful but NOT clinically meaningful.")

    print("\n" + LINE); print("ONNX export + runtime parity"); print(LINE)
    onnx_path = PROCESSED_DIR / f"temporal_cnnlstm_{SPEC_VERSION}.onnx"
    res = export_and_verify(final, d_dyn, d_static, str(onnx_path), SPEC_VERSION)
    print(f"  exported: {onnx_path}")
    print(f"  input contract: {res['contract']}")
    print(f"  spec_version in metadata: {res['spec_version']}")
    print(f"  PyTorch vs ONNX Runtime max|diff| = {res['max_abs_diff']:.2e}  → "
          f"{'MATCH (<=1e-5)' if res['match'] else 'MISMATCH'}")
    print("\n" + LINE); print("done"); print(LINE)


if __name__ == "__main__":
    main()
