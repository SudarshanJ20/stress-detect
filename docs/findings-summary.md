# Findings summary — Phase 2 (StudentLife baseline)

One-page account of the Phase 2 modelling result. Detail and tables:
`docs/phase2-results.md`. Reproduce: `ml/.venv/bin/python ml/src/training/run_baseline.py`
and `ml/src/experiments/*.py`.

## Question
Can passive phone behaviour predict a user's **momentary stress** (StudentLife Stress EMA),
evaluated **leave-one-subject-out** so it reflects a new user? Label = the EMA remapped to a
monotonic wellbeing ordinal, then a 0–100 stress score (100 = most stressed).

## Headline
**No — and it is a robust, fully-diagnosed null, not a bug.** Across day and week
granularity, no feature set beats predicting each person's own mean stress. The only
predictable structure in these data is the between-person **trait level**; day-to-day
movement, though large and real, is not recoverable from behaviour.

## Baseline result (day level, LOSO, n = 1157 subject-days / 48 subjects)
| | MAE | Spearman |
|---|---|---|
| XGBoost (screen/lock backbone) | 22.59 | −0.15 |
| baseline: global mean | 19.94 | −0.52 |
| **baseline: subject mean** | **16.95** | 0.47 |
The model loses to the per-person mean and is worse than chance. In-sample fit is near-
perfect (Spearman 0.92) and every feature's univariate ρ with stress is ≤ 0.14 → a genuine
null / negative transfer, not a pipeline error.

## The diagnostic chain — each experiment isolates one cause
| # | Question | Answer |
|---|---|---|
| **1** | Is the null caused by our *retrospective* feature restriction? | **No.** Adding the out-of-scope streams StudentLife's own papers used (conversation, activity, audio, GPS, dark) changes MAE by +0.09. The full sensor suite fails too. |
| **2** | Is the 7-day *window* mismatched to a momentary label? | **No.** 1 / 2 / 3 / 7-day windows are all flat (~22 MAE, negative Spearman). |
| **3** | Is LOSO's *zero-history* evaluation too harsh? | **No.** Giving the model a subject's first k days doesn't help; it loses to that subject's own running mean. |
| **4** | Is there *anything real* to predict? | **Yes.** ICC(1) = 0.27 → **73% of variance is within-subject day-to-day**; measurement noise (within-day MAD) is only 8.9, so ~19-pt SD of the daily variation is real signal. Not at ceiling. |
| **5** | Do *exogenous events* (deadlines/calendar) explain it? | **No.** Per-subject events give the first transferable signal (Spearman +0.20) but MAE ≈ 20.7 — still 3–4 pts worse than the subject-mean; combined with backbone, no gain. |
| **6** | Does a coarser *weekly* target work? | **No — the aggregation trap.** Weekly model MAE "improves" 22.59 → 16.14, but the subject-mean baseline improves 16.95 → 11.41 (ICC rises 0.27 → 0.42): the target got easier, not the model better. Behavioural Spearman = 0.023. |

## Two facts that decide everything (Experiment 4)
- **There IS real day-to-day variation** — 73% of variance is within-subject and only ~9 pts
  of the 21.4-pt within-subject SD is measurement noise. So "nothing to predict" is *false*.
- **Behaviour doesn't capture it, and neither does history** — within-person lag-1
  autocorrelation is only **0.26 (R² 0.07)** (the pooled 0.48 is inflated by between-subject
  trait means and is misleading). ⇒ **12–24 h forecasting from behavioural / self-report
  history alone is not supportable on this data.**

## Methodological lesson (worth keeping)
Reporting the weekly MAE (16.14) *without* its baseline (11.41) would have read as a
success. It was a loss. **Baseline reporting against BOTH the global mean and the
subject mean is non-negotiable**, and a Spearman near zero outweighs a MAE that fell for the
wrong reason (an easier target).

## Phase 4 addendum — temporal deep models (does capacity rescue it?)
A CNN-LSTM over the 7-day daily **sequence** (vs XGBoost's flat aggregate) — full write-up in
`docs/phase4-results.md`:

| model | MAE | Spearman |
|---|---|---|
| XGBoost (flat) | 22.59 | −0.146 |
| **CNN-LSTM (sequence, 5 seeds)** | **20.76 ± 0.10** | −0.115 |
| TinyTransformer (sequence, 3 seeds) | 21.83 ± 0.07 | −0.032 |
| global mean | 19.94 | — |
| subject mean | 16.95 | 0.47 |

Sequence structure **stably helps** (22.59 → 20.76, seed SD 0.10; both DL arms beat flat, so
the gain is representation- not architecture-driven) yet the task is **still unsolvable**
(below global mean, 3.8 off subject mean, Spearman negative). This **closes the last
objection** — "the model was too simple." Label-permutation passed (no leakage); training
curves show the net can learn but only reaches ≈mean prediction. **Answer: not capacity.**

## Conclusion & next step
StudentLife is the wrong place to *prove* passive-behaviour stress prediction — the
momentary target has no cross-subject behavioural signal at any granularity. The Phase 2
**pipeline is validated and dataset-agnostic** (ETL → remapped labels → backbone features →
LOSO → guards → baselines) and re-runs unchanged on **GLOBEM / K-EmoPhone**, which are
purpose-built for momentary affect. That is the next dataset; no further modelling on
StudentLife.
