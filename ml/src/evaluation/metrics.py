"""Metrics + reference baselines for both targets.

The SUBJECT-MEAN baseline is the one that matters: predict each day with the mean of that
subject's OTHER days (leave-one-day-out). If the model can't beat "this person's usual
score", it hasn't learned day-to-day variation — which is the entire claim.
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from scipy.stats import spearmanr
from sklearn.metrics import (
    brier_score_loss, f1_score, mean_absolute_error, roc_auc_score,
)


# ── regression ───────────────────────────────────────────────────────────────────────
def rmse(y, p):
    return float(np.sqrt(np.mean((np.asarray(y) - np.asarray(p)) ** 2)))


def regression_metrics(y, p) -> dict:
    y, p = np.asarray(y, float), np.asarray(p, float)
    rho = spearmanr(y, p).statistic if len(y) > 2 and np.std(p) > 0 else np.nan
    return {"MAE": float(mean_absolute_error(y, p)), "RMSE": rmse(y, p),
            "Spearman": float(rho), "n": int(len(y))}


def regression_calibration(y, p, bins=10) -> dict:
    """Binned reliability + calibration slope (actual ~ pred). slope≈1, error≈0 is ideal."""
    y, p = np.asarray(y, float), np.asarray(p, float)
    try:
        q = pd.qcut(p, min(bins, len(np.unique(p))), duplicates="drop")
    except (ValueError, IndexError):
        return {"calib_error": np.nan, "calib_slope": np.nan}
    d = pd.DataFrame({"y": y, "p": p, "q": q})
    g = d.groupby("q", observed=True).agg(mp=("p", "mean"), my=("y", "mean"), n=("y", "size"))
    err = float(np.average(np.abs(g["mp"] - g["my"]), weights=g["n"]))
    slope = float(np.polyfit(p, y, 1)[0]) if np.std(p) > 0 else np.nan
    return {"calib_error": err, "calib_slope": slope}


def subject_mean_baseline(pred_df: pd.DataFrame, target: str) -> np.ndarray:
    """Leave-one-day-out per-subject mean of the true target."""
    out = np.empty(len(pred_df), float)
    for s, idx in pred_df.groupby("subject").groups.items():
        idx = list(idx)
        vals = pred_df.loc[idx, target].to_numpy(float)
        tot, n = vals.sum(), len(vals)
        for j, i in enumerate(idx):
            out[pred_df.index.get_loc(i)] = (tot - vals[j]) / (n - 1) if n > 1 else vals[j]
    return out


# ── classification ───────────────────────────────────────────────────────────────────
def _ece(y, prob, bins=10) -> float:
    y, prob = np.asarray(y, float), np.asarray(prob, float)
    edges = np.linspace(0, 1, bins + 1)
    e = 0.0
    for lo, hi in zip(edges[:-1], edges[1:]):
        m = (prob >= lo) & (prob < hi if hi < 1 else prob <= hi)
        if m.any():
            e += (m.mean()) * abs(y[m].mean() - prob[m].mean())
    return float(e)


def classification_metrics(y, prob) -> dict:
    y = np.asarray(y, int); prob = np.asarray(prob, float); pred = (prob >= 0.5).astype(int)
    both = len(np.unique(y)) == 2
    return {
        "macro_F1": float(f1_score(y, pred, average="macro", zero_division=0)),
        "ROC_AUC": float(roc_auc_score(y, prob)) if both else np.nan,
        "Brier": float(brier_score_loss(y, prob)),
        "ECE": _ece(y, prob),
        "n": int(len(y)), "pos_rate": float(np.mean(y)),
    }
