"""Per-feature attribution for the temporal model → feeds the app's "why this reading".

IMPORTANT CAVEAT (must reach the UI): attribution explains what the MODEL responds to, not
what causes stress. Under the Phase-4 null the model has no real stress signal, so these
weights are faithful to the model but NOT clinically/causally meaningful. The app must never
imply otherwise.

Primary method: SHAP GradientExplainer. Falls back to gradient×input saliency if SHAP's
multi-input path errors on this torch/shap combo — both are valid gradient attributions.
"""
from __future__ import annotations

import numpy as np
import torch
import torch.nn as nn


class _FlatWrap(nn.Module):
    """SHAP's GradientExplainer is unreliable with multi-input torch models, so present the
    model as a single flat input (seq flattened ++ static) and reshape internally."""
    def __init__(self, model, T, d_dyn):
        super().__init__(); self.model = model; self.T = T; self.d_dyn = d_dyn

    def forward(self, x):
        k = self.T * self.d_dyn
        # SHAP's torch gradient path indexes outputs[:, idx] → needs a 2-D (B,1) output
        return self.model(x[:, :k].reshape(-1, self.T, self.d_dyn), x[:, k:]).unsqueeze(-1)


def feature_attribution(model, Xs, Xt, n_bg=100, n_explain=200, seed=0):
    """Returns (dyn_importance[d_dyn], static_importance[d_static], method_name).
    Xs (N,7,d_dyn), Xt (N,d_static) are already standardized float32 arrays."""
    model.eval()
    N, T, d_dyn = Xs.shape
    flat = np.hstack([Xs.reshape(N, -1), Xt]).astype("float32")
    k = T * d_dyn
    rng = np.random.default_rng(seed)
    bg = rng.choice(N, min(n_bg, N), replace=False)
    ex = rng.choice(N, min(n_explain, N), replace=False)
    try:
        import shap
        wrap = _FlatWrap(model, T, d_dyn); wrap.eval()
        expl = shap.GradientExplainer(wrap, torch.tensor(flat[bg]))
        sv = expl.shap_values(torch.tensor(flat[ex]))
        sv = np.asarray(sv[0] if isinstance(sv, list) else sv)
        if sv.ndim == 3:            # (M, features, n_outputs=1) → drop the single-output axis
            sv = sv[..., 0]
        dyn = np.abs(sv[:, :k]).reshape(-1, T, d_dyn).mean(axis=(0, 1))
        stat = np.abs(sv[:, k:]).mean(axis=0)
        return dyn, stat, "SHAP GradientExplainer"
    except Exception as e:  # robust fallback so the deliverable exists regardless
        xs = torch.tensor(Xs[ex], requires_grad=True); xt = torch.tensor(Xt[ex], requires_grad=True)
        model(xs, xt).sum().backward()
        dyn = (xs.grad * xs).abs().mean(dim=(0, 1)).detach().numpy()
        stat = (xt.grad * xt).abs().mean(dim=0).detach().numpy()
        return dyn, stat, f"gradient×input fallback ({type(e).__name__})"
