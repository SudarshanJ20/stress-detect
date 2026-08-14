# Phase 2 — StudentLife screen/lock baseline (result)

**This is a result, not a failure.** With the retrospective-scope backbone (screen/lock
features) and an XGBoost baseline evaluated leave-one-subject-out (LOSO), the model does
**not** beat a per-subject-mean baseline, and the binary target sits at/below chance. We
verify three independent ways that this is a *true null / negative transfer*, not a bug.
The headline finding: **screen/lock behaviour alone does not carry cross-subject
momentary-stress signal in StudentLife.** That is a legitimate, thesis-relevant result — it
is the empirical motivation for personalization / self-baseline.

Spec: `docs/feature-spec.md` v0.4.0. Reproduce: `ml/.venv/bin/python ml/src/training/run_baseline.py`.

## Method (summary)
- **Label:** Stress EMA, remapped to a monotonic wellbeing ordinal (raw `3<2<1<4<5` → `0..4`)
  — see spec "Label decoding". Primary target = subject-day `stress_score` (0–100, 100=most
  stressed); secondary = binary `{2,3}` vs `{4,5}` (level-1 dropped).
- **Sample = one subject-day** with ≥1 valid EMA (not per-response — that shares ~99% of the
  window and collapses effective n). Features from the 7-day window ending at that day's
  local-midnight boundary; gated at ≥3/7 days of phonelock.
- **Features:** backbone screen/lock only (sleep proxy + regularity, unlocks, session
  structure, person-relative night use, circadian regularity) + auxiliary call/SMS with
  missingness flags.
- **Evaluation:** LOSO (48 folds); never split within a subject. Tripwires for subject
  leakage and duplicate feature vectors fire on every fold.

## Sample accounting (real n is visible)
| stage | count |
|---|---|
| valid Stress EMA responses (explicit `level`) | 2167 |
| → subject-days with ≥1 valid EMA | 1270 |
| − gated (7-day window has <3 days of phonelock) | −109 |
| − collapsed identical-window duplicates (within-subject, adjacent days) | −4 |
| **= regression samples** | **1157** (48 subjects) |
| binary subset (level-1 dropped, non-tie) | **733** (47 subjects) |

Dropped no-`level` entries: 241 (109 coordinate pings, 122 ambiguous bare digits, 10 other)
— recorded as a spec limitation, not recovered.

## Results (LOSO)

### Primary — regression, `stress_score` 0–100 (label spread: mean 54.2, sd 25.3)
| model / baseline | MAE | RMSE | Spearman |
|---|---|---|---|
| XGBoost (screen/lock + aux) | 22.59 | 28.45 | **−0.146** |
| baseline: global mean | 19.94 | 25.51 | −0.516 |
| **baseline: subject mean** (LODO) | **16.95** | 21.84 | 0.473 |

Calibration slope −0.40 (ideal 1.0), binned error 10.9 pts. The model is **5.6 MAE worse
than the subject-mean baseline** and beats a subject's own mean for only **8 / 48** subjects.

### Secondary — binary `{2,3}` vs `{4,5}` (n=733, 56% stressed)
| model / baseline | macro-F1 | ROC-AUC | Brier | ECE |
|---|---|---|---|---|
| XGBoost | 0.444 | **0.405** | 0.336 | 0.263 |
| baseline: majority | 0.359 | 0.500 | — | — |
| baseline: stratified random | 0.500 | ~0.500 | — | — |

macro-F1 edges the majority baseline, but **ROC-AUC 0.405 is below chance** — the ranking is
mildly inverted on held-out subjects.

## Why the null is real (three-way verification)
1. **The pipeline can learn.** Train==test (in-sample) fit is near-perfect: **MAE 8.95,
   Spearman 0.924.** Features, labels, remap, and windowing are wired correctly; the model
   has ample capacity.
2. **There is no univariate signal.** Every backbone feature's pooled Spearman with
   `stress_score` is **|ρ| ≤ 0.14**, and the two "largest" are data-quantity proxies, not
   behaviour:

   | feature | ρ | | feature | ρ |
   |---|---|---|---|---|
   | n_sleep_nights | +0.14 | | screen_on_fraction | +0.06 |
   | session_duration_iqr | +0.10 | | unlock_count_per_day_mean | +0.06 |
   | days_with_data | +0.09 | | sleep_duration_median | −0.01 |
   | nighttime_use_fraction_fixed | +0.04 | | circadian_regularity | −0.00 |
   | sleep_onset/wake/regularity | ≈0 | | call/sms features | ≈0 |

3. **LOSO shows negative transfer.** Because true signal ≈ 0, the model fits training-subject
   idiosyncrasies that *invert* on held-out subjects → Spearman −0.15, AUC 0.41. Between-
   subject trait differences dominate (subject means sd ≈ 14 pts), which is exactly why the
   subject-mean baseline is strong and the behavioural features add nothing across subjects.

**Interpretation.** The ~16-pt within-person day-to-day variation exists but is *not*
explained by screen/lock behaviour in a cross-subject model. This does not yet tell us
*why* (retrospective constraint vs. intrinsically hard task vs. window/label mismatch vs.
the harshness of zero-history LOSO) — Experiments 1–3 (next) isolate those causes.

## Full per-subject LOSO tables

### Regression (sorted worst→best MAE)
```
subject  n  MAE  subjmean_MAE  beats_subjmean  Spearman
    u17 31 50.9           4.5           False     -0.35
    u46 20 35.3          29.2           False      0.05
    u45 17 34.1          25.4           False     -0.45
    u56 16 33.9          26.7           False     -0.33
    u33 36 33.8          30.6           False     -0.51
    u07 16 33.0          10.2           False      0.46
    u50  4 32.8           0.0           False       NaN
    u49 44 30.8          24.1           False     -0.34
    u59 65 30.6          15.0           False     -0.17
    u03 15 28.8          26.7           False     -0.56
    u34  4 27.3          16.7           False      0.00
    u35 24 27.2          20.8           False     -0.13
    u12 22 26.9          25.2           False     -0.26
    u25 13 25.8          24.5           False     -0.08
    u42 18 25.3          16.4           False     -0.11
    u52 29 25.1          24.3           False     -0.06
    u04 26 23.9          21.2           False      0.17
    u30 18 22.7          16.7           False     -0.62
    u14 26 22.2           6.8           False      0.32
    u22 28 21.9          14.7           False     -0.09
    u27 18 21.6          19.7           False     -0.13
    u16 50 21.4          18.7           False      0.01
    u57 43 20.9          20.9           False     -0.04
    u08 38 20.6          12.4           False      0.06
    u39  1 20.6           0.0           False       NaN
    u18 13 20.4          24.4            True      0.22
    u01 22 20.4          15.4           False     -0.36
    u32 39 20.3          12.4           False     -0.02
    u43 32 20.3          21.4            True      0.17
    u41  8 20.1          14.7           False      0.37
    u00 34 18.9          19.9            True      0.17
    u19 51 18.2          18.3            True     -0.06
    u44 48 17.4          10.3           False     -0.06
    u47  7 17.4          15.5           False     -0.47
    u54 16 16.8          16.7           False     -0.07
    u02 26 16.8          14.7           False      0.26
    u31  9 16.5          14.6           False      0.19
    u58 37 15.1          16.3            True      0.25
    u10 42 14.7          12.2           False     -0.16
    u09  2 14.4          25.0            True       NaN
    u36 36 13.8          11.8           False     -0.04
    u51 33 13.7          13.6           False     -0.02
    u53 28 13.6          13.1           False      0.15
    u05  2 13.6          25.0            True       NaN
    u24 14 13.5          11.9           False      0.32
    u20  6 11.1           8.3           False     -0.39
    u23 22 11.0           4.2           False     -0.18
    u15  8  8.9          12.9            True      0.44
```
Model beats the subject's own mean for **8 / 48** subjects. Note the mean does **not** hide
chance-level subjects — the failure is broad, not a few outliers dragging a good average.

### Classification (sorted worst→best macro-F1)
```
subject  n  pos_rate  macro_F1  ROC_AUC
    u34  2      0.00      0.00      NaN
    u20  1      1.00      0.00      NaN
    u50  4      1.00      0.00      NaN
    u09  1      0.00      0.00      NaN
    u05  1      0.00      0.00      NaN
    u41  5      1.00      0.17      NaN
    u59 51      0.22      0.18     0.61
    u56 11      0.27      0.18     0.25
    u47  4      0.25      0.20     0.00
    u35 15      0.33      0.20     0.14
    u01 11      0.45      0.21     0.03
    u17 31      1.00      0.21      NaN
    u45 11      0.18      0.21     0.11
    u49 32      0.22      0.25     0.26
    u32 19      0.00      0.27      NaN
    u25 11      0.36      0.27     0.29
    u23  5      0.20      0.29     0.00
    u08 30      1.00      0.30      NaN
    u52 19      0.58      0.32     0.31
    u22 19      0.11      0.32     0.24
    u15  2      0.50      0.33     1.00
    u44 21      0.38      0.33     0.34
    u12 12      0.58      0.33     0.26
    u42 12      0.08      0.33     0.09
    u33 33      0.61      0.34     0.19
    u24  9      0.89      0.36     0.50
    u43 19      0.84      0.36     0.50
    u07 16      0.06      0.38     0.27
    u04 16      1.00      0.38      NaN
    u27  9      0.78      0.42     0.21
    u51 14      0.93      0.42     0.00
    u30 14      0.14      0.43     0.62
    u31  4      0.25      0.43     0.33
    u16 37      0.78      0.43     0.46
    u14 24      1.00      0.43      NaN
    u46 15      0.73      0.44     0.36
    u57 23      0.78      0.46     0.49
    u19 40      0.78      0.47     0.46
    u53 11      0.82      0.48     0.44
    u10 27      0.30      0.49     0.45
    u54  8      0.50      0.50     0.69
    u03 10      0.40      0.52     0.42
    u58 23      0.78      0.55     0.67
    u18  5      0.80      0.58     0.50
    u02 13      0.46      0.61     0.57
    u00 22      0.55      0.72     0.67
    u36 11      0.82      0.81     1.00
```
(`NaN` AUC = the held-out subject has only one class; F1 still defined.)

## What this does and doesn't tell us
- **Does:** screen/lock backbone + population LOSO ≠ momentary stress; the retrospective
  backbone alone is insufficient as a cross-subject predictor.
- **Doesn't (yet):** whether the cause is the retrospective *constraint*, the *task/label*
  being intrinsically hard, a *window↔label temporal mismatch*, or the *harshness of
  zero-history LOSO*. Three diagnostic experiments isolate each — below.

## Diagnostics (Experiments 1–3): why the null?

Reproduce: `ml/.venv/bin/python ml/src/experiments/run_experiments.py`. Same subject-day
samples, same labels, same LOSO. Regression MAE (lower is better):

| condition | n | MAE | RMSE | Spearman |
|---|---|---|---|---|
| **reference: subject-mean baseline** | 1157 | **16.95** | 21.84 | 0.47 |
| reference: global-mean baseline | 1157 | 19.94 | 25.51 | −0.52 |
| LOSO backbone (7d) — current | 1157 | 22.59 | 28.45 | −0.146 |
| Exp1: backbone + extended streams | 1157 | 22.68 | 28.28 | −0.124 |
| Exp1: extended-only (conv/activity/audio/gps/dark) | 1157 | 21.91 | 27.60 | 0.002 |
| Exp2: backbone 1d window | 1131 | 22.42 | 27.98 | −0.120 |
| Exp2: backbone 2d window | 1148 | 21.67 | 27.45 | −0.066 |
| Exp2: backbone 3d window | 1154 | 22.35 | 27.96 | −0.111 |

Exp3 — personalized (train on each subject's first *k* chronological days, test on the rest):

| k own days in train | test n | model MAE | personal train-mean MAE |
|---|---|---|---|
| 3 | 1017 | 22.57 | 21.30 |
| 5 | 929 | 22.58 | 19.62 |
| 10 | 726 | 23.03 | 19.50 |

**Three-way falsification** — each experiment rules out one candidate cause:
1. **Exp 1 (ceiling): the retrospective constraint is NOT the cause.** Adding the
   out-of-scope streams StudentLife's own analyses used (conversation, activity, audio,
   GPS, dark) changes MAE by **+0.09** (nothing); extended-only is Spearman **0.002** and
   *worse* than the global mean. No achievable ceiling is being forfeited by the
   retrospective restriction — the full sensor suite fails at *this* task too.
2. **Exp 2 (window): the 7-day window is NOT the cause.** 1/2/3/7-day windows are all flat
   at ~22 MAE with negative Spearman. No window predicts a momentary label; it is not a
   temporal-window mismatch. (PSS survey is unusable: 46 subjects, 85 responses, ~1–2 each.)
3. **Exp 3 (LOSO harshness): zero-history evaluation is NOT the cause.** Giving the model
   the subject's own early days doesn't help (MAE stays ~22.6 and loses to the personal
   train-mean baseline at every k). Personalizing the *mean* helps (21.3→19.5); the
   *features* add nothing on top — they don't track day-to-day stress even within a person.

**The crux — do not lose this.** StudentLife's published signal was for **term-level trait
outcomes** (PHQ-9 depression, GPA, loneliness), aggregated over the whole 10-week term —
**not** momentary per-day EMA stress evaluated leave-one-subject-out. Our task is a
fundamentally different and harder one. All three experiments converge: the null is **not**
our retrospective scope, our window, or our evaluation — it is that **momentary EMA stress
is essentially unpredictable from passive phone behaviour at day granularity in
StudentLife**, at any window, any feature set, personalized or not. The only predictive
structure in these data is each person's **trait mean** (subject-mean MAE 16.95); day-to-day
movement around it is ~noise with respect to behaviour.

**Product implication (for decision, not yet acted on).** The "forecast day-to-day stress
from passive behaviour" claim is *not supported by StudentLife*; the between-person
self-baseline is. Open directions: change the label to a construct with signal (e.g. PHQ-9),
lean on K-EmoPhone / GLOBEM (purpose-built for momentary affect) once access lands, or
reframe the product around the personal baseline. **Experiment 4** (label noise floor)
decides whether the problem is "behaviour doesn't predict real variation" or "there is
little real variation to predict" — those need opposite responses.

*Data-quality note:* several `gps` files carry header rows repeated mid-file (concatenated
Kaggle export); the extended-stream readers coerce timestamps to numeric and drop those
junk rows. Recorded in `docs/dataset-inventory.md`.

## Experiment 4 — is there anything to predict? (label noise floor)

Reproduce: `ml/.venv/bin/python ml/src/experiments/exp4_label_noise.py`. This distinguishes
*"behaviour doesn't predict real variation"* from *"there is little real variation to
predict"* — opposite problems needing opposite responses.

| quantity | value |
|---|---|
| **ICC(1)** of subject-day `stress_score` | **0.27** → 27% between-subject, **73% within-subject (day-to-day)** |
| between-subject SD / within-subject SD | 13.2 / **21.4** |
| within-day EMA disagreement (MAD, per-response 0–100) | **8.9** = measurement noise floor (irreducible) |
| ⇒ real day-to-day signal SD (√(21.4² − 8.9²)) | **≈ 19 pts** — far above the 8.9 noise floor |
| within-subject lag-1 autocorrelation | **r = 0.26, R² = 0.07** (only 7% of day-to-day movement is autocorrelated) |
| noise floor vs subject-mean MAE (8.9 / 16.9) | 0.52 |

**The task is NOT at ceiling — there is substantial real variation to explain.** 73% of
stress variance is within-subject day-to-day, and only ~9 of the 21.4-pt within-subject SD
is measurement noise, leaving a real day-to-day signal of **~19 pts SD**. (The subject-mean
baseline "won" Phase 2 only because behaviour features added negative value — with ICC 0.27
it can itself explain just 27% of the variance.)

**But that real signal is captured by neither of the obvious sources.** Passive behaviour
misses it (Exps 1–3), and within-person **lag-1 autocorrelation is only 0.26 (R² 0.07)**, so
persistence/AR won't recover it either (persistence MAE 17.4 *loses* to subject-mean 16.2).

> **Use the within-subject autocorrelation (0.26), never the pooled 0.48.** The pooled
> lag-1 correlation across all subject-days is +0.48, but that is inflated by stable
> between-subject *trait* means (person A always high, person B always low → adjacent days
> of the same person correlate for reasons unrelated to temporal dynamics). Mean-centering
> each subject removes the trait level and leaves the true day-to-day momentum: **0.26**.

**Explicit consequence for the headline claim:** with within-person day-to-day
autocorrelation of only **0.26 (R² 0.07)**, **12–24 h stress forecasting from behavioural /
self-report history alone is not supportable on this data** — yesterday explains ~7% of
today's movement, and behaviour adds nothing. Forecasting would require the *exogenous
drivers* of daily stress (deadlines, exams, workload, events), not history alone.
Experiment 5 tests exactly that.
