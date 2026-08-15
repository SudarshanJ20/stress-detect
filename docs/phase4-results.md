# Phase 4 — temporal deep-learning models (result)

Reproduce: `ml/.venv/bin/python ml/src/training/run_dl.py`. Same 1157 subject-day samples /
48 subjects / labels / LOSO harness / guards / baselines as Phase 2 — the only change is the
input representation (7-day **daily sequence** vs one flat 7-day aggregate).

## Why this ran on StudentLife despite Phase 2's "stop modelling"
Phase 2 closed day- and week-level modelling on StudentLife *for the flat-aggregate
representation*. A temporal model is a **genuinely new hypothesis**: XGBoost saw a flat
7-day vector; a CNN-LSTM/transformer sees the **ordered daily sequence**, so it can exploit
day-to-day dynamics the aggregate discards — the one untested lever. And regardless of
outcome, Phase 4 builds the **durable infrastructure** (temporal model, DL-LOSO harness,
SHAP attribution, ONNX export + runtime parity) that re-runs unchanged on GLOBEM and feeds
the app. That is why "stop modelling" (flat, day/week) did not preclude this.

## Setup
Each sample → dynamic `(7, 8)` daily sequence (unlock/session/screen/night-use/comms +
`has_data` mask) + static `(9,)` window-level vector (sleep, regularity, circadian, coverage,
comms-present). Standardization fit on TRAIN fold only. Models kept deliberately small +
regularized (no tuning to beat the baseline). CNN-LSTM primary (5 seeds); TinyTransformer
ablation arm (3 seeds).

## Results — with baselines in the SAME table (non-negotiable)
| model | MAE | Spearman |
|---|---|---|
| XGBoost (flat aggregate, Phase 2) | 22.59 | −0.146 |
| **CNN-LSTM (sequence)** | **20.76 ± 0.10** | −0.115 |
| TinyTransformer (sequence) | 21.83 ± 0.07 | −0.032 |
| baseline: global mean | **19.94** | — |
| baseline: subject mean | **16.95** | 0.47 |

## The CNN-LSTM result, stated precisely (both directions)
1. **Sequence structure DID help — real and stable, not noise.** 22.59 (XGBoost flat) →
   **20.76** (CNN-LSTM), with a 5-seed **SD of only 0.10**. The temporal representation
   carries information the flat 7-day aggregate discards.
2. **It is STILL a null.** 20.76 is **below the global mean (19.94)**, **3.81 MAE short of
   the subject mean (16.95)**, and its Spearman stays **negative (−0.115)**. The gap to the
   subject mean (3.81) dwarfs the seed SD (0.10) — a stable null, not a seed artefact.
3. **Why this matters more than a flat null:** it **rules out "the model was too simple."**
   The architecture demonstrably extracts *more* signal than XGBoost did (a stable 1.8-MAE
   gain from ordering alone) and **the task remains unsolvable** — no model, flat or
   temporal, beats predicting a constant per-person mean. That **closes the last plausible
   objection to the Experiment 1–6 chain**: it isn't the features, the window, the
   evaluation, the label aggregation, the missing events, *or* model capacity.

> Do not cite the 22.59 → 20.76 gain without the baselines beside it: on its own it reads as
> success; against global-mean 19.94 and subject-mean 16.95 it is still a loss. Same trap as
> the weekly-aggregation result.

## Representation vs architecture (transformer cross-check)
**Both** sequence models beat flat XGBoost (CNN-LSTM 20.76, TinyTransformer 21.83, vs 22.59),
so the gain is **primarily attributable to the sequence REPRESENTATION**, not one specific
architecture. CNN-LSTM beats the transformer (20.76 < 21.83) exactly as predicted a priori:
at n≈1157 / length-7, the transformer is over-hungry and its self-attention buys little,
while the CNN-LSTM's inductive bias fits short sequences + small data. So: representation
carries the information; architecture modulates how much of it is captured.

## Sanity checks (validate the harness — and every prior result)
- **Label permutation:** CNN-LSTM with shuffled TRAIN labels → MAE **19.52 ± 0.13**, i.e.
  ≈ the global mean (19.94). A shuffled-label model at chance means **no leakage** in the
  LOSO harness — retroactively validating Phases 2–4.
- **Training curves** (held-out TRAIN subjects as val): train MSE 3449 → 628, val MSE
  3918 → 680. Loss falls, so the network **can learn** (not broken). Train ≈ val at
  convergence (gap ~50 MSE): the regularized net settles at **≈ mean prediction with no
  signal to fit beyond it** — neither memorizing nor generalizing, because there is no
  behaviour→stress signal to find.

## Per-subject (CNN-LSTM, seed 0)
**14 / 48** subjects beaten vs their own mean (up from 8/48 for XGBoost), but still a
minority, and the winners skew to low-n / low-variance subjects. Calibration slope −0.41
(ideal 1.0), error 7.8 pts. Full table in the run output.

## Attribution (feeds the app's "why this reading")
Method: **SHAP GradientExplainer** (via a single-input wrapper). Top drivers:
`days_with_data`, `nighttime_use_fraction_fixed`, `has_data`, `sms_count`,
`circadian_regularity`. Tellingly, the strongest are **data-quantity / coverage** features
(`days_with_data`, `has_data`) — the model keys on *how much data exists*, not on
stress-relevant behaviour, which is exactly what a null model would do.

> **CAVEAT (must reach the UI):** attribution explains what the MODEL responds to, not what
> causes stress. Under this null the model carries no real stress signal, so these weights
> are faithful to the model but **NOT clinically or causally meaningful**. The app must
> never imply otherwise.

## ONNX export + runtime parity
Exported `temporal_cnnlstm_v0.6.0.onnx` (gitignored). **PyTorch vs ONNX Runtime max|diff| =
3.8e-06 ≤ 1e-5 → MATCH.** Input contract (also embedded in `metadata_props`, alongside
`spec_version=v0.6.0`):
```
seq:    float32 (1,7,8)   static: float32 (1,9)   →   stress: float32 (1,)
```
Inference is batch = 1 (one user-window). **`SPEC_VERSION` was bumped v0.5.0 → v0.6.0 only
after this parity passed**, and `ml/tests/test_spec_version.py` now guards code↔doc drift
(the Phase-5 Kotlin must add the mirror test + match this contract).

## Conclusion
Phase 4 **confirms and strengthens** the Phase-2 null: a temporal architecture extracts more
than the flat baseline (stably) yet still cannot beat a per-person mean, closing the
"model-too-simple" objection. The negative finding is now comprehensive across features,
window, evaluation, aggregation, events, and model capacity. The temporal-DL + attribution +
ONNX infrastructure is built and validated, and re-runs unchanged on **GLOBEM / K-EmoPhone**.
