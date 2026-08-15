"""Leave-one-subject-out training for the Phase-4 torch models. Reuses the Phase-2 guards;
metrics/baselines come from evaluation.metrics. Fixed a-priori hyperparameters — NO tuning
to beat the baseline (per the standing rule).

Standardization is fit on the TRAIN fold only (per-feature, NaN-aware) and applied to the
held-out subject — never the reverse.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np
import pandas as pd
import torch
import torch.nn as nn

from guards import assert_no_subject_leakage
from models.temporal import make_model

EPOCHS, LR, WD, BATCH = 50, 1e-3, 1e-4, 64


def _fit_scaler(seq, static):
    d = seq.reshape(-1, seq.shape[2])
    dm, ds = np.nanmean(d, 0), np.nanstd(d, 0); ds[ds == 0] = 1.0
    sm, ss = np.nanmean(static, 0), np.nanstd(static, 0); ss[ss == 0] = 1.0
    return dm, ds, sm, ss


def _apply(seq, static, sc):
    dm, ds, sm, ss = sc
    s = np.nan_to_num((seq - dm) / ds, nan=0.0).astype("float32")
    t = np.nan_to_num((static - sm) / ss, nan=0.0).astype("float32")
    return s, t


def _train(model, Xs, Xt, y, seed, epochs=EPOCHS, val=None):
    torch.manual_seed(seed)
    opt = torch.optim.Adam(model.parameters(), lr=LR, weight_decay=WD)
    lossf = nn.MSELoss()
    Xs, Xt, y = torch.tensor(Xs), torch.tensor(Xt), torch.tensor(y)
    n = len(y); g = torch.Generator().manual_seed(seed)
    tr_curve, val_curve = [], []
    for _ in range(epochs):
        model.train(); perm = torch.randperm(n, generator=g); tot = 0.0
        for i in range(0, n, BATCH):
            idx = perm[i:i + BATCH]
            opt.zero_grad()
            loss = lossf(model(Xs[idx], Xt[idx]), y[idx])
            loss.backward(); opt.step(); tot += loss.item() * len(idx)
        tr_curve.append(tot / n)
        if val is not None:
            model.eval()
            with torch.no_grad():
                val_curve.append(lossf(model(val[0], val[1]), val[2]).item())
    return tr_curve, val_curve


def loso_dl(data, model_name, seeds, permute=False):
    """Returns {seed: predictions DataFrame[subject, local_date, y_true, y_pred]}."""
    subj = data["subject"]; uniq = sorted(set(subj))
    d_dyn, d_static = data["X_seq"].shape[2], data["X_static"].shape[1]
    out = {}
    for seed in seeds:
        preds = []
        for s in uniq:
            tr, te = subj != s, subj == s
            assert_no_subject_leakage(subj[tr], subj[te])
            sc = _fit_scaler(data["X_seq"][tr], data["X_static"][tr])
            Xs_tr, Xt_tr = _apply(data["X_seq"][tr], data["X_static"][tr], sc)
            Xs_te, Xt_te = _apply(data["X_seq"][te], data["X_static"][te], sc)
            ytr = data["y"][tr].astype("float32").copy()
            if permute:
                np.random.default_rng(seed).shuffle(ytr)   # shuffle TRAIN labels only
            model = make_model(model_name, d_dyn, d_static)
            _train(model, Xs_tr, Xt_tr, ytr, seed)
            model.eval()
            with torch.no_grad():
                yp = model(torch.tensor(Xs_te), torch.tensor(Xt_te)).numpy()
            preds.append(pd.DataFrame({
                "subject": subj[te], "local_date": data["local_date"][te],
                "y_true": data["y"][te], "y_pred": np.clip(yp, 0, 100),
            }))
        out[seed] = pd.concat(preds, ignore_index=True)
    return out


def training_curves(data, model_name, seed=0, val_frac=0.2, epochs=EPOCHS):
    """One subject-level train/val split (val = held-out TRAIN subjects, never the LOSO test
    subject) to show train↓ while val flat = memorization-without-generalization."""
    subj = data["subject"]; uniq = sorted(set(subj))
    rng = np.random.default_rng(seed)
    val_subs = set(rng.choice(uniq, max(1, int(len(uniq) * val_frac)), replace=False))
    tr = np.array([s not in val_subs for s in subj]); va = ~tr
    sc = _fit_scaler(data["X_seq"][tr], data["X_static"][tr])
    Xs_tr, Xt_tr = _apply(data["X_seq"][tr], data["X_static"][tr], sc)
    Xs_va, Xt_va = _apply(data["X_seq"][va], data["X_static"][va], sc)
    d_dyn, d_static = data["X_seq"].shape[2], data["X_static"].shape[1]
    model = make_model(model_name, d_dyn, d_static)
    val = (torch.tensor(Xs_va), torch.tensor(Xt_va), torch.tensor(data["y"][va].astype("float32")))
    return _train(model, Xs_tr, Xt_tr, data["y"][tr].astype("float32"), seed, epochs, val=val)
