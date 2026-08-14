"""EXPERIMENT 4 — Is there anything to predict? Quantify the label's day-to-day variance
and its own noise floor. Distinguishes "behaviour doesn't predict real variation" from
"there is little real variation to predict".

Run:  ml/.venv/bin/python ml/src/experiments/exp4_label_noise.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np
import pandas as pd
from scipy.stats import pearsonr

from features.labels import build_subject_day_labels, load_stress_responses
from features.spec_constants import ORDINAL_MAX

SUBJECT_MEAN_MAE = 16.95  # from Phase 2 LOSO (docs/phase2-results.md)
LINE = "=" * 78


def main():
    print(LINE); print("EXPERIMENT 4 — label noise floor & day-to-day structure"); print(LINE)
    resp, _ = load_stress_responses()
    resp["score"] = (ORDINAL_MAX - resp["ordinal"]) / ORDINAL_MAX * 100.0  # per-response 0–100
    day = build_subject_day_labels(resp)  # subject-day stress_score (mean of per-response)

    # ── 1. within-day EMA disagreement (measurement noise floor) ──────────────────────
    per_day = resp.groupby(["subject", "local_date"])["score"]
    stds, mads, rngs, ns = [], [], [], []
    for _, s in per_day:
        if len(s) >= 2:
            m = s.mean()
            stds.append(s.std(ddof=0)); mads.append((s - m).abs().mean())
            rngs.append(s.max() - s.min()); ns.append(len(s))
    stds, mads, rngs = np.array(stds), np.array(mads), np.array(rngs)
    n_multi = len(stds); n_days = per_day.ngroups
    print("\n## 1. Within-day EMA disagreement (per-response, 0–100 scale)")
    print(f"  subject-days with >=2 responses: {n_multi}/{n_days} ({100*n_multi/n_days:.0f}%); "
          f"mean responses/those days = {np.mean(ns):.1f}")
    print(f"  within-day STD  : mean={stds.mean():.1f}  median={np.median(stds):.1f}  "
          f"p10={np.percentile(stds,10):.1f} p90={np.percentile(stds,90):.1f}  (0 → all responses agree)")
    print(f"  within-day |dev from day-mean| (MAD, an MAE-comparable noise floor): mean={mads.mean():.1f}")
    print(f"  within-day RANGE: mean={rngs.mean():.1f}  (max possible 100)")
    frac0 = float((stds == 0).mean())
    print(f"  fraction of multi-response days where ALL responses agree exactly: {frac0:.2f}")

    # ── 2. variance decomposition / ICC (between vs within subject) ────────────────────
    print("\n## 2. Variance decomposition of subject-day stress_score (ICC)")
    grand = day["stress_score"].mean()
    groups = [gg["stress_score"].to_numpy() for _, gg in day.groupby("subject")]
    k = len(groups); N = sum(len(x) for x in groups)
    means = np.array([x.mean() for x in groups]); ns_i = np.array([len(x) for x in groups])
    SSB = float(np.sum(ns_i * (means - grand) ** 2)); dfB = k - 1
    SSW = float(np.sum([((x - x.mean()) ** 2).sum() for x in groups])); dfW = N - k
    MSB, MSW = SSB / dfB, SSW / dfW
    n0 = (N - np.sum(ns_i ** 2) / N) / (k - 1)
    var_b = max((MSB - MSW) / n0, 0.0); var_w = MSW
    icc = var_b / (var_b + var_w) if (var_b + var_w) > 0 else np.nan
    print(f"  total SD={day['stress_score'].std():.1f} | between-subject SD={np.sqrt(var_b):.1f} | "
          f"within-subject SD={np.sqrt(var_w):.1f}")
    print(f"  ICC(1) = {icc:.2f}  →  {100*icc:.0f}% of variance is BETWEEN subjects, "
          f"{100*(1-icc):.0f}% is WITHIN subject (day-to-day)")

    # ── 3. day-to-day autocorrelation (does day N predict N+1?) ───────────────────────
    print("\n## 3. Day-to-day autocorrelation of stress_score")
    raw_a, raw_b, cen_a, cen_b = [], [], [], []
    persist_err, mean_err = [], []
    for s, gg in day.groupby("subject"):
        gg = gg.sort_values("local_date")
        vals = gg["stress_score"].to_numpy()
        subj_mean = vals.mean()
        for j in range(len(vals) - 1):
            raw_a.append(vals[j]); raw_b.append(vals[j + 1])
            cen_a.append(vals[j] - subj_mean); cen_b.append(vals[j + 1] - subj_mean)  # de-trait
            persist_err.append(abs(vals[j + 1] - vals[j])); mean_err.append(abs(vals[j + 1] - subj_mean))
    r_raw = pearsonr(raw_a, raw_b)[0]
    r_cen = pearsonr(cen_a, cen_b)[0]
    print(f"  RAW pooled lag-1 r = {r_raw:+.3f}  — but this is inflated by between-subject trait means")
    print(f"  WITHIN-subject (mean-centered) lag-1 r = {r_cen:+.3f}  →  lag-1 R²={r_cen**2:.3f} "
          f"({100*r_cen**2:.0f}% of day-to-day movement is autocorrelated)")
    print(f"  persistence (predict day N+1 = day N): MAE={np.mean(persist_err):.1f}  "
          f"vs predict subject-mean: MAE={np.mean(mean_err):.1f}  "
          f"→ persistence {'beats' if np.mean(persist_err) < np.mean(mean_err) else 'loses to'} subject-mean")

    # ── 4. noise floor vs the subject-mean MAE ────────────────────────────────────────
    print("\n## 4. Noise floor vs achievable error")
    print(f"  within-day measurement noise (MAD)      : {mads.mean():.1f}  (irreducible; no model can beat)")
    print(f"  within-subject day-to-day SD (signal+noise): {np.sqrt(var_w):.1f}")
    print(f"  subject-mean baseline MAE (Phase 2 LOSO): {SUBJECT_MEAN_MAE:.1f}")
    ratio = mads.mean() / SUBJECT_MEAN_MAE
    verdict = ("AT CEILING: measurement noise ≈ subject-mean error — little real day-to-day "
               "variation exists to predict; no feature set could have worked."
               if ratio > 0.8 else
               "Room exists: real day-to-day variation is larger than the noise floor — "
               "behaviour genuinely fails to predict it.")
    print(f"  noise/achievable ratio = {ratio:.2f}  →  {verdict}")


if __name__ == "__main__":
    main()
