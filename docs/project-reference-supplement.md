# Project reference supplement

Read from the code at `SPEC_VERSION = v0.7.0` (`ml/src/features/spec_constants.py:10`,
`android/.../features/SpecConstants.kt:17`). Anything not found in the code is marked
**NOT FOUND** rather than inferred.

---

## 1. Hyperparameters

### 1.1 Was any hyperparameter search run?

**No. None. Not for any of the three models.**

A grep across `ml/src`, `ml/tools`, `ml/tests` for `grid_search`, `RandomizedSearchCV`,
`optuna`, `hyperopt`, `param_grid`, `cross_val`, `sweep`, `early_stop`, `patience`,
`scheduler`, `StepLR`, `ReduceLROnPlateau` returns **two hits, both comments**:

- `ml/src/training/run_dl.py:5` — "Reuses the XGBoost baseline, LOSO guards, metrics, and BOTH baselines. NO tuning."
- `ml/src/training/loso_torch.py:2` — "Fixed a-priori hyperparameters — NO tuning to beat the baseline (per the standing rule)."

Consequences, stated plainly:

- **No search space exists**, therefore no selection criterion exists. Every value below is
  a single hardcoded literal, chosen a priori and never varied.
- **No early stopping and no LR schedule** anywhere. Epoch count is a fixed constant.
- **No validation split inside any LOSO fold.** The only train/val split in the repo is
  `loso_torch.training_curves` (line 91), which exists solely to plot train-vs-val curves
  for the write-up; it feeds no model selection.
- Tuning was therefore not "minimal" — it was **zero**. The honest framing is that the
  models were deliberately fixed and small so that a null result could not be attributed to
  under-tuning, and so that a win could not be manufactured by searching against LOSO folds.
  The flip side, which the code does not hide: nobody has established that these
  hyperparameters are anywhere near the best achievable for this data.

### 1.2 XGBoost

Source: `ml/src/models/xgboost_baseline.py`.

Shared config (`_COMMON`, lines 8–12), used by both the regressor and the classifier:

| parameter | value |
|---|---|
| `n_estimators` | 300 |
| `max_depth` | 4 |
| `learning_rate` | 0.05 |
| `subsample` | 0.8 |
| `colsample_bytree` | 0.8 |
| `reg_lambda` | 1.0 |
| `random_state` | 0 |
| `n_jobs` | 4 |

- **Regressor** (`make_regressor`): `objective="reg:squarederror"` — squared-error loss.
- **Classifier** (`make_classifier`): `objective="binary:logistic"`,
  `eval_metric="logloss"`, `scale_pos_weight = neg/pos` computed **from that fold's training
  labels** (line 20–21), falling back to `1.0` if there are no positives.
- **Batch size / epochs**: not applicable (tree boosting). 300 boosting rounds, no
  `early_stopping_rounds`, no `eval_set`.
- **Seeds**: exactly **one** — `random_state=0`. No multi-seed repetition for XGBoost.
- **Input shape**: `(n_samples, 22)` `float64` — `train[feature_cols].to_numpy(float)` in
  `ml/src/evaluation/loso.py:24`, where `feature_cols =
  screenlock_features.feature_names() + aux_features.feature_names()` = 18 + 4 = 22.
  NaN is passed through deliberately (XGBoost handles it natively; no imputation).
- Predictions are clipped to `[0, 100]` (`loso.py:30`).

### 1.3 CNN-LSTM (primary)

Architecture: `ml/src/models/temporal.py:18–34`. Instantiated by `make_model("cnnlstm", d_dyn, d_static)`
with **both defaults left untouched** (`hidden=32`, `dropout=0.3`).

```
conv : Conv1d(in=d_dyn=8, out=32, kernel_size=3, padding=1) → ReLU
lstm : LSTM(input=32, hidden=32, num_layers=1, batch_first=True)   # no dropout arg → 0
       take out[:, -1, :]  (last timestep = newest day)
head : Linear(32 + d_static=9 → 32) → ReLU → Dropout(p=0.3) → Linear(32 → 1) → squeeze(-1)
```

- Dropout appears in **one place only**: the head, `p=0.3`. The conv and LSTM have none.
- No batch/layer norm, no residuals, no attention.

Training config (`ml/src/training/loso_torch.py:23`, `_train` lines 40–59):

| item | value | source |
|---|---|---|
| loss | `nn.MSELoss()` | `loso_torch.py:43` |
| optimiser | `torch.optim.Adam` | `loso_torch.py:42` |
| learning rate | `1e-3` (`LR`) | `loso_torch.py:23` |
| weight decay | `1e-4` (`WD`) | `loso_torch.py:23,42` |
| batch size | `64` (`BATCH`) | `loso_torch.py:23,49` |
| epochs | `50` (`EPOCHS`), fixed | `loso_torch.py:23,47` |
| early stopping | **none** | — |
| LR schedule | **none** | — |
| gradient clipping | **none** | — |
| shuffling | `torch.randperm` with a per-seed `torch.Generator` | `loso_torch.py:45,48` |

- **Seeds: 5** — `seeds=[0, 1, 2, 3, 4]` (`run_dl.py:75`). Reported as mean ± population SD
  (`ddof=0`, `run_dl.py:47`).
- The **final shipped/exported model** is trained with `seed=0` on **all** data
  (`run_dl.py:132–135`).
- Label-permutation sanity arm reuses CNN-LSTM with `seeds=[0, 1]` and `permute=True`
  (`run_dl.py:89`), shuffling **train** labels only (`loso_torch.py:77`).

**Seeding caveat visible in the code:** `torch.manual_seed(seed)` is called *inside* `_train`
(`loso_torch.py:41`), i.e. **after** `make_model(...)` has already constructed the network
(`loso_torch.py:78–79`). Weight initialisation for the first fold therefore draws from
PyTorch's ambient default-generator state rather than from the fold seed, so a fresh process
is not bit-reproducible from `seeds=[0,1,2,3,4]` alone. The seeds do control batch ordering,
dropout masks, and the label permutation.

### 1.4 TinyTransformer (ablation arm)

Architecture: `ml/src/models/temporal.py:37–53`. Instantiated via
`make_model("transformer", d_dyn, d_static)` with all defaults (`d_model=32`, `nhead=4`,
`dropout=0.3`, `seq_len=7`).

```
proj : Linear(d_dyn=8 → 32)
pos  : nn.Parameter zeros of shape (1, 7, 32)      # learned positional embedding, zero-init
enc  : TransformerEncoder(num_layers=1) of
         TransformerEncoderLayer(d_model=32, nhead=4, dim_feedforward=64,
                                 dropout=0.3, batch_first=True)
       then .mean(dim=1)                            # mean-pool over the 7 days
head : Linear(32 + d_static=9 → 32) → ReLU → Dropout(p=0.3) → Linear(32 → 1) → squeeze(-1)
```

- 1 encoder layer, 4 heads, `d_model=32` (head dim 8), FFN width 64, dropout 0.3 in both the
  encoder layer and the head.
- No attention mask / padding mask is passed (`forward` calls `self.enc(h)` with no mask).
- **Loss, optimiser, LR, weight decay, batch size, epochs, early stopping: identical to
  CNN-LSTM** — the same `_train` and the same module-level constants are used.
- **Seeds: 3** — `seeds=[0, 1, 2]` (`run_dl.py:82`).
- Not exported to ONNX; only CNN-LSTM is (`run_dl.py:134`).

### 1.5 Exact input tensor shapes (both temporal models)

From `ml/src/features/sequence_dataset.py:72–74` and `ml/src/models/onnx_export.py:24,31`:

```
X_seq    : float32 (N, 7, 8)     → per batch: seq    (B, 7, 8)
X_static : float32 (N, 9)        → per batch: static (B, 9)
y        : float32 (N,)          → output:    stress (B,)
```

`d_dyn = 8` and `d_static = 9` are read at runtime from the data
(`run_dl.py:53`, `loso_torch.py:65`), not hardcoded in the models. Inference in the app is
batch = 1: `(1, 7, 8)` + `(1, 9)`.

---

## 2. Feature list — all 22

Python: `ml/src/features/screenlock_features.py` (18 backbone) +
`ml/src/features/aux_features.py` (4 auxiliary). Column order is
`screenlock_features.feature_names() + aux_features.feature_names()`
(`build_dataset.py:93`), mirrored in Kotlin by `FeatureExtractor.FEATURE_NAMES`
(`FeatureExtractor.kt:40`).

All 18 backbone values are produced by **one** function per language —
`screenlock_window_features(locked, w0, w1)` / `ScreenLockFeatures.windowFeatures(locked, w0, w1, zone)`
— so the "function that computes it" column names that function plus the specific helper
that does the arithmetic. Window = the 7 days ending at the labelled day's local midnight
(`build_dataset._window_bounds`). Missing → `NaN`.

### Backbone — screen/lock (from LOCKED intervals)

| # | feature | what it measures | unit | computed by (Python → Kotlin) |
|---|---|---|---|---|
| 1 | `days_with_data` | number of distinct local calendar dates touched by any (clipped) locked interval in the window | days, 0–7 | `screenlock_window_features` lines 70–79 → `windowFeatures` lines 65–80 |
| 2 | `n_sleep_nights` | how many nights in the window had a qualifying main-sleep interval | nights | `screenlock_window_features` line 103 (`nights` dict) → `windowFeatures` line 118 |
| 3 | `sleep_duration_median` | median length of the detected main-sleep intervals | hours | `np.median(arr[:,0])` line 104 → `NumPyCompat.median` line 119–120 |
| 4 | `sleep_onset_hours` | circular mean of the clock hour at which main sleep started | hour-of-day, 0–24 | `_circ(arr[:,1]).mean` line 105 → `circular(...).mean` line 121,124 |
| 5 | `sleep_wake_hours` | circular mean of the clock hour at which main sleep ended | hour-of-day, 0–24 | `_circ(arr[:,2]).mean` line 106 → `circular(...).mean` line 122,125 |
| 6 | `sleep_onset_regularity` | circular SD of sleep-onset clock hour — lower = more regular bedtime | hours | `_circ(...)` sd, line 105,110 → `circular(...).sd` line 126 |
| 7 | `sleep_midpoint_regularity` | circular SD of the sleep-midpoint clock hour | hours | `_circ(arr[:,3])` sd, line 107,111 → `circular(...).sd` line 127 |
| 8 | `unlock_count_per_day_mean` | unlocks (= ends of locked intervals falling inside the window) divided by `days_with_data` | unlocks/day | line 123 → `windowFeatures` line 135 |
| 9 | `unlock_count_sd` | population SD (`ddof=0`) of the per-date unlock counts, over the dates in `days_with_data` (absent dates count 0) | unlocks/day | line 124 → `NumPyCompat.stdPopulation` line 137–139 |
| 10 | `session_count_per_day_mean` | use-sessions (gaps between consecutive locked intervals, ≤ `MAX_SESSION_MINUTES`=180 min) per day | sessions/day | line 136 → line 153 |
| 11 | `session_duration_median` | median use-session length | minutes | `np.median` line 137 → `NumPyCompat.median` line 154–155 |
| 12 | `session_duration_iqr` | P75 − P25 of use-session lengths | minutes | `np.percentile` line 138–140 → `NumPyCompat.iqr` line 156–157 |
| 13 | `screen_on_fraction` | mean over dates-with-data of (that date's in-session seconds / 86400), each clamped to ≤ 1 | fraction, 0–1 | lines 143–148 → lines 160–167 |
| 14 | `nighttime_use_fraction_personal` | share of total session seconds whose session **start hour** falls in that person's own night band `[circ-mean onset, circ-mean wake)` (falls back to 00:00–06:00 if no sleep detected) | fraction, 0–1 | `_night_use_fraction(personal)` lines 158–176 → `nightUseFraction(personalBand)` lines 173–198 |
| 15 | `nighttime_unlock_per_day_personal` | unlocks whose clock hour falls in that personal night band, per `days_with_data` (returns `0.0`, not NaN, when there are no unlocks) | unlocks/day | `_night_unlocks_per_day(personal)` lines 164–177 → `nightUnlocksPerDay` lines 184–199 |
| 16 | `nighttime_use_fraction_fixed` | same as #14 but with the fixed clock band `NIGHT_FIXED_BAND` = 00:00–06:00 (ablation only) | fraction, 0–1 | line 178 → line 200 |
| 17 | `nighttime_unlock_per_day_fixed` | same as #15 with the fixed 00:00–06:00 band (ablation only) | unlocks/day | line 179 → line 201 |
| 18 | `circadian_regularity` | mean of the upper-triangle pairwise Pearson correlations between each day's 24-bin hourly use-seconds profile (only days whose profile has non-zero SD; NaN if fewer than 2 such days) | correlation, −1…1 | lines 181–198 → `NumPyCompat.meanPairwiseCorrelation` lines 203–223 |

### Auxiliary — call/SMS (never backbone; each value paired with a presence flag)

| # | feature | what it measures | unit | computed by (Python → Kotlin) |
|---|---|---|---|---|
| 19 | `call_count_per_day` | calls in the window ÷ `WINDOW_DAYS` (7) — note the denominator is 7, **not** `days_with_data`; `0.0` when the stream is unavailable | calls/day | `aux_window_features` → `rate` (`aux_features.py:16–22`) → `AuxFeatures.windowFeatures` → `rate` (`AuxFeatures.kt:29,40–49`) |
| 20 | `call_present` | 1 if the call stream is available for this subject/device, else 0 — lets the model separate "no calls" from "cannot see calls" | flag, 0/1 | same as #19 |
| 21 | `sms_count_per_day` | messages in the window ÷ `WINDOW_DAYS` (7); `0.0` when unavailable | messages/day | same as #19 |
| 22 | `sms_present` | 1 if the SMS stream is available, else 0 | flag, 0/1 | same as #19 |

### The DL model does not consume these 22 directly

The temporal models consume a re-shaped view (`sequence_dataset.py:26–34`,
`SequenceFeatures.kt:23–32`):

- **dynamic `(7, 8)`**, one vector per day, oldest → newest:
  `unlock_count`, `session_count`, `session_duration_median`, `screen_on_fraction`,
  `nighttime_use_fraction_fixed`, `call_count`, `sms_count`, `has_data`.
  These are the *same* screen/lock functions applied to a **single-day** window
  (`_daily_vector` / `SequenceFeatures.dailyVector`), plus raw per-day call/SMS counts and a
  `has_data` mask — so `unlock_count` here is a single day's count, and `call_count`/`sms_count`
  are raw counts, not the ÷7 rates of #19/#21.
- **static `(9,)`**: `sleep_duration_median`, `sleep_onset_hours`, `sleep_wake_hours`,
  `sleep_onset_regularity`, `sleep_midpoint_regularity`, `circadian_regularity`,
  `days_with_data`, `call_present`, `sms_present` — window-level quantities a single day
  cannot define.

Four of the 22 (`n_sleep_nights`, `unlock_count_sd`, `session_duration_iqr`,
`nighttime_*_personal`) are used by XGBoost but **not** by the temporal models.

---

## 3. File inventory

28 Python files and 87 Kotlin files — 59 under `src/main`, 27 under `src/test` (one of which,
`ParityFixture.kt`, is a shared helper rather than a test), 1 under `src/androidTest` — plus 4
Gradle `.kts` scripts. Counted at the commit that added this file: `HomeSummary.kt`,
`HomeSummaryTest.kt`, `ExtractionGateway.kt`, `MainActivity.kt` and `HomeScreen.kt` had
just landed with the Home screen.

### Python — `ml/src/` (top level)

| file | what it does |
|---|---|
| `paths.py` | Repo-relative paths for the gitignored `ml/data/{raw,processed}` tree and the StudentLife sub-directories. |
| `guards.py` | `SpecViolation` plus five tripwires that raise, never warn: timezone check, raw-1–5-scale-must-stay-categorical, `forbid_raw_ordinal_op`, subject-leakage, duplicate-feature-vector. |

### Python — `ml/src/etl/`

| file | what it does |
|---|---|
| `studentlife_etl.py` | StudentLife raw CSVs → normalized events: `load_phonelock` (LOCKED intervals, drops `end<=start`), `load_calls`/`load_sms` (event-date column only, ms→s, drops empty poll heartbeats), `build_events` returning all three streams. |

### Python — `ml/src/features/`

| file | what it does |
|---|---|
| `spec_constants.py` | The single source of every threshold: `SPEC_VERSION`, `TIMEZONE`, the stress remap tables, `WINDOW_DAYS`, `COVERAGE_MIN_DAYS`, session/sleep/night constants, `BAND_EDGE_EPS`, `CIRCADIAN_BINS`. |
| `labels.py` | Stress EMA → labels: `remap_level` (raw 3<2<1<4<5 → ordinal 0–4), `load_stress_responses` (keeps only explicit levels 1–5, tallies drops, stores raw as an *unordered* Categorical), `build_subject_day_labels` (0–100 `stress_score` + binary label). |
| `screenlock_features.py` | The 18 backbone features from locked intervals: `screenlock_window_features` plus `_circ` (circular mean/SD), `_in_band` (snapped clock-band membership), `_local`, `feature_names`. |
| `aux_features.py` | The 4 call/SMS features — `aux_window_features` and `feature_names`; every rate paired with a `*_present` flag. |
| `build_dataset.py` | Assembles the modelling table: one row per labelled subject-day, features from the 7-day window ending at that day's local midnight, coverage gate, `_collapse_duplicate_windows`, optional versioned Parquet write; returns `(df, stats)`. |
| `sequence_dataset.py` | The Phase-4 view: `DYNAMIC_FEATURES` (8) / `STATIC_FEATURES` (9), `_day_bounds`, `_daily_vector`, `build_sequence_dataset` → `X_seq (N,7,8)`, `X_static (N,9)`, `y`, subject/date arrays and the flat reference frame. |

### Python — `ml/src/models/`

| file | what it does |
|---|---|
| `xgboost_baseline.py` | `_COMMON` hyperparameters plus `make_regressor` and `make_classifier` (the latter computes `scale_pos_weight` from the fold's labels). |
| `temporal.py` | `CNNLSTM`, `TinyTransformer`, and `make_model(name, d_dyn, d_static)`. |
| `onnx_export.py` | `export_and_verify`: `torch.onnx.export` at opset 17, appends the five `metadata_props` keys (incl. the scaler), then compares PyTorch vs ONNX Runtime. |
| `model_reference.py` | Writes `fixtures/model_reference.json` — per fixture case, the raw and standardized inputs plus the PyTorch and ORT outputs, so the on-device test can localise a mismatch. |

### Python — `ml/src/evaluation/`

| file | what it does |
|---|---|
| `loso.py` | `_folds` (one subject out, sorted), `loso_regression`, `loso_classification`; guards fire per fold, predictions clipped to 0–100. |
| `metrics.py` | `rmse`, `regression_metrics`, `regression_calibration` (binned error + slope), `subject_mean_baseline` (leave-one-day-out per-subject mean), `_ece`, `classification_metrics`. |
| `attribution.py` | `feature_attribution` — SHAP `GradientExplainer` over a `_FlatWrap` single-input adapter, falling back to gradient×input saliency; returns dynamic/static importances and the method name. |

### Python — `ml/src/training/`

| file | what it does |
|---|---|
| `run_baseline.py` | Phase-2 entry point: build dataset → sleep sanity gate (exits 2 if the cohort median is outside 5–11 h) → LOSO regression and classification → baselines, calibration, full per-subject tables → metrics JSON. |
| `loso_torch.py` | The DL LOSO harness: `EPOCHS/LR/WD/BATCH`, `_fit_scaler`, `_apply`, `_train`, `loso_dl` (with an optional train-label permutation), `training_curves`. |
| `run_dl.py` | Phase-4 entry point: CNN-LSTM 5 seeds, TinyTransformer 3 seeds, label-permutation sanity, training curves, summary vs both baselines, per-subject table, then an all-data final model → attribution → ONNX export → `model_reference.json`. |

### Python — `ml/src/experiments/` (diagnostics; not product code)

| file | what it does |
|---|---|
| `run_experiments.py` | Three Phase-2 diagnostics in one table: ceiling test with out-of-scope streams, feature window varied 1/2/3/7 d, and a personalized split giving the model each subject's first *k* days. |
| `extended_features.py` | Daily aggregates for the out-of-scope StudentLife streams (activity, audio, conversation, dark, gps) used only by the ceiling test; `EXTENDED_FEATURES` must never reach the product set. |
| `exp4_label_noise.py` | Quantifies the label itself: within-day EMA disagreement (the noise floor), between/within-subject variance decomposition + ICC(1), lag-1 autocorrelation, then noise-floor vs achievable-error verdict. |
| `exp5_events.py` | Tests whether exogenous events (deadlines, calendar counts, day-of-week, week-of-term) explain the within-subject variance behaviour misses, separating per-subject (deployable) from cohort-wide features. |
| `exp6_weekly.py` | Re-runs the whole pipeline at a subject-*week* target (mean remapped stress, ≥ `MIN_RESPONSES_PER_WEEK`) to test whether a period-level construct is predictable where the daily one is not. |

### Python — `ml/tests/`, `ml/tools/`

| file | what it does |
|---|---|
| `tests/conftest.py` | Puts `ml/src` on `sys.path` for pytest. |
| `tests/test_spec_version.py` | Asserts the code `SPEC_VERSION` equals the one declared in `docs/feature-spec.md`. |
| `tests/test_parity_fixture.py` | Python half of the cross-language contract: re-runs this extractor over `fixtures/synthetic_trace.json` and asserts the committed expectations to 1e-6, so future Python drift fails here instead of being blessed on the next regeneration. |
| `tools/build_parity_fixture.py` | Generates `fixtures/synthetic_trace.json` — synthetic, one case block per named spec rule, expected vectors produced by the Python extractor; raises on DST-ambiguous or degenerate literals. |

### Kotlin — `android/.../data/` (13)

| file | what it does |
|---|---|
| `AppPreferences.kt` | `AppPreferences` — SharedPreferences for `themeChoice`, `demoMode`, `hasSeenIntro`. |
| `CheckInRepository.kt` | `CheckInRepository` (+ `Entry`) — record/read/delete PSS-4 totals, keeping demo and real histories separate via `isDemo`. |
| `Daos.kt` | Seven Room `@Dao` interfaces incl. `FeatureVectorDao.deleteOtherSpecVersions`. |
| `DemoTraceSource.kt` | `DemoTraceSource` (+ `DemoWindow`) — parses `assets/synthetic_trace.json`, loading `demo_week` and `demo_prior_week_1/2`. |
| `Entities.kt` | Seven Room `@Entity` classes; `FeatureVectorEntity` has nullable feature columns and PK `labelDate`+`specVersion`. |
| `ExtractionGateway.kt` *(modified)* | `ExtractionGateway` (+ `ExtractionSummary`, `WeekContext`) — the UI's only door into sensing: permission checks, settings intent, backfill enqueue/run, cached vector, week context. |
| `FeatureVectorMapper.kt` | `FeatureVectorMapper` — snake_case feature map (NaN) ↔ Room row (null). |
| `ResultRepository.kt` | `ResultRepository.analyse(...)` and `AnalysisResult` — scores PSS-4, records the check-in, builds the device or demo window, loads `assets/stress_model.onnx`, runs prediction + occlusion attribution. |
| `RetrospectiveBackfillWorker.kt` | One-shot `CoroutineWorker` (+ `enqueue()`) running the extractor. |
| `RetrospectiveExtractor.kt` | `RetrospectiveExtractor` + `ExtractionOutcome` — OS history → Room → features from Room → cache + `extraction_run` log. |
| `StressDetectDatabase.kt` | Room database v2, 7 entities, hand-written `MIGRATION_1_2` adding `check_in`, singleton `get()`. |
| `WeekFeatures.kt` | `WeekFeatures` — `priorWeeks()` (non-overlapping, same spec version, covered, max 4), `vectorOf`, `latest`. |
| `WindowAssembly.kt` | `WindowAssembly` (+ `Assembled`) — pure assembly of the sequence/static arrays, daily series, prior-week mean (`meanOfWeeks`) and `toResult`. |

### Kotlin — `android/.../features/` (8)

| file | what it does |
|---|---|
| `AnalysisWindow.kt` | `AnalysisWindow` with `endingAtMidnightOf` / `mostRecentComplete` — 7-day `[startUtc, endUtc)` using absolute-second arithmetic for pandas parity. |
| `AuxFeatures.kt` | `AuxFeatures` — the 4 call/SMS features; port of `aux_features.py`. |
| `FeatureExtractor.kt` | `WindowFeatureVector` (+ `meetsCoverage`, `asArray`) and `FeatureExtractor.extract` — the 22-name vector; mirrors the per-sample body of `build_dataset`. |
| `LockedInterval.kt` | `LockedInterval` — `startUtc`/`endUtc`/`durationSeconds`, requires `endUtc > startUtc`. |
| `NumPyCompat.kt` | `NumPyCompat` — `percentile`, `median`, `iqr`, `stdPopulation` (ddof=0), `pearson`, `meanPairwiseCorrelation`, matching NumPy exactly. |
| `ScreenLockFeatures.kt` | `ScreenLockFeatures.windowFeatures` — the 18 backbone features; line-for-line port of `screenlock_features.py`, incl. the `circular` mean/SD helper and the `R ≤ 1` clamp. |
| `SequenceFeatures.kt` | `SequenceFeatures` — the 8 dynamic / 9 static names, `dynamicSequence` (7×8), `dailyVector`, `staticVector`; raw values with NaN intact. |
| `SpecConstants.kt` | `SpecConstants` (Kotlin mirror of `spec_constants.py`, `SPEC_VERSION = "v0.7.0"`, `PARITY_TIMEZONE`) and `ClockBand.contains` with `BAND_EDGE_EPS`-snapped edges. |

### Kotlin — `android/.../inference/` (3)

| file | what it does |
|---|---|
| `StressModel.kt` | `interface StressModel { predict(sequence, static): Float }` — the inference contract. |
| `OnnxStressModel.kt` | ONNX Runtime implementation: refuses to load on `spec_version` mismatch or missing standardization metadata, standardizes then maps NaN→0, runs `seq`/`static` → `stress`. |
| `OcclusionAttribution.kt` | `OcclusionAttribution.rank` (+ `FeatureContribution`) — per-window occlusion (feature → NaN → training mean, re-predict, sort by \|delta\|), excluding the four availability flags. |

### Kotlin — `android/.../sensing/` (7)

| file | what it does |
|---|---|
| `RawUsageEvent.kt` | `RawUsageEvent` and `UsageEventType` (keyguard shown/hidden, screen interactive/non-interactive, shutdown/startup). |
| `UsageEventsSource.kt` | `queryEvents` — `UsageStatsManager.queryEvents` over the window plus a 3-day pre-margin, mapped to `UsageEventType` in epoch seconds. |
| `LockedIntervalDerivation.kt` | `derive(events)` — KEYGUARD_SHOWN→HIDDEN pairing into `LockedInterval`s; drops unmatched/degenerate ones, treats shutdown/startup as data gaps. |
| `UsageAccess.kt` | `UsageAccess.isGranted` via `AppOpsManager` with a testable `isGrantedFrom(mode, hasPermission)`; `settingsIntent()`. |
| `UsageStatsSource.kt` | `queryDailyBuckets` (+ `DailyAppUsage`) — per-app `INTERVAL_DAILY` foreground buckets, cached as context only; no feature may read it. |
| `CallLogSource.kt` | `CallLogSource` — reads only `CallLog.Calls.DATE` in a bounded selection; returns second-resolution timestamps + row count. |
| `SmsSource.kt` | `SmsSource` — reads only `Telephony.Sms.DATE`. |

### Kotlin — `android/.../survey/` (1)

| file | what it does |
|---|---|
| `Pss4.kt` | `Pss4` — verbatim items/anchors, `MIN/MAX_SCORE`, citation, `score`, `completedResponses`, `percentOfMaximum`; `Pss4Item.contribution` handles reverse scoring. No Android and no `com.stressdetect.*` imports. |

### Kotlin — `android/.../ui/` (3) and `ui/theme/` (3)

| file | what it does |
|---|---|
| `MainActivity.kt` *(modified)* | `MainActivity` + `StressDetectApp()` — holds all app state (theme, demo mode, responses, history, last result/extraction, week context), refreshes `PermissionState` on ON_RESUME, dispatches every `Screen`. |
| `Navigation.kt` | `Screen` sealed interface (incl. `Analysing(answers)`), `BackStack` (`push`/`replaceTop`/`replaceAll`/`pop`), `rememberBackStack`. |
| `PermissionState.kt` | `PermissionState` — observable usage/comms grants, re-read by `refresh()` rather than cached. |
| `theme/Theme.kt` | `CalmColors`, light/dark palettes, `LocalCalmColors`, `CalmPalette`, `Space` (8dp grid, radii, max content width), `ThemeChoice`, `StressDetectTheme`. |
| `theme/Type.kt` | Bundled Fraunces/Inter variable fonts as `FontFamily`s, `CalmType` (`hero`, `question`, `eyebrow`), `CalmTypography`. |
| `theme/Motion.kt` | `rememberAnimationsEnabled()` — reads `ANIMATOR_DURATION_SCALE`; the single gate for all animation. |

### Kotlin — `android/.../ui/components/` (6)

| file | what it does |
|---|---|
| `Components.kt` | Shared composables: `ScreenScaffold`, `Eyebrow`, `Track`, `CalmCard`, `PrimaryButton`, `LinkText`, `QuietButton`, `Body`, `Caption`, `ScreenTitle`, `SectionHeading`, `ThemeOption`, `DemoBanner`. |
| `BreathingFigure.kt` | `BreathingFigure(band, …)` + `washDepth`/`drawFigure` — canvas face where the band changes only wash depth and mouth curve. |
| `FaceGeometry.kt` | `MouthGeometry.of(...)` — the unit-testable mouth numbers; curve clamped ≥ 0, `isLevel` so a flat mouth is drawn as a line. |
| `CountUpNumber.kt` | `CountUpNumber(value, suffix, duration)` + `heroFontScaleCap` — counts up once, stops growing past 1.3× font scale. |
| `Sparkline.kt` | `Sparkline(scores, maxScore, height)` — fixed 0–max canvas line with quarter gridlines and a dot per check-in. |
| `TrendArrow.kt` | `TrendArrow(direction, size)` — canvas up/down/level arrow for `WeekSummary.Direction` (drawn, not a glyph, for contrast). |

### Kotlin — `android/.../ui/content/` (7)

| file | what it does |
|---|---|
| `Bands.kt` | `Band` (LOW/SOME/MODERATE/HIGH with label, blurb, mouth curve) and `Band.forScore` (0–4/5–8/9–12/13+). |
| `WeekSummary.kt` | `WeekSummary.build` (+ `Direction`, `Row`, `Result`) — the four result rows (screen, rest, comms, rhythm) compared to the prior-week mean with per-metric deadbands; `direction` returns null with no prior week; `capSuggestions` max 2. |
| `Observations.kt` | `Observations.build` — picks ≤3 non-overlapping observations from a fixed candidate list, each with a paired suggestion; `duration`/`clock` formatters; `allCopyStrings()` for the copy test. |
| `Factors.kt` | `Factors` — feature→human labels, fixed suggestion table, the two disclaimers, `rankableFeatures()`, `describe(...)`, number/clock/duration formatters. |
| `HistoryStats.kt` | `HistoryStats.build` (+ `Stats`) — weekly mean percent and week-over-week point change (hidden under 3 check-ins unless demo), `countSummary`, `formatChange`. |
| `ThingsThatHelp.kt` | `ThingsThatHelp` — the fixed four items (never varying with score) and `extraFor(band)`, non-null only at HIGH. |
| `HomeSummary.kt` | `HomeSummary` — `relativeDay(date, today)` and `phoneLine(weekValues, priorValues)`, one Home sentence derived from `WeekSummary`'s screen-row direction; null below `COVERAGE_MIN_DAYS` or when the feature is missing. |

### Kotlin — `android/.../ui/screens/` (8)

| file | what it does |
|---|---|
| `FirstRunScreen.kt` | One-time short data explanation before the first check-in. |
| `PermissionsScreen.kt` | Required usage-access card (opens Settings) + optional calls/messages card; continue copy differs in demo mode. |
| `HomeScreen.kt` *(modified)* | Time-of-day greeting, small breathing figure, last check-in + mini sparkline, `HomeSummary.phoneLine` context line, check-in button, History/About links. |
| `Pss4Screen.kt` | One verbatim PSS-4 item per screen, five full-row `selectable` anchors, progress dots. |
| `AnalysisScreen.kt` | Pulsing three-dot progress plus a four-step checklist while extraction/inference runs. |
| `ResultScreen.kt` | Breathing figure, questionnaire percent via `CountUpNumber`, band label/blurb, `WeekSummary` rows with `TrendArrow`, `ThingsThatHelp`, Done + "How this works". |
| `HistoryScreen.kt` | Sparkline of past check-ins (hidden until 3 distinct days unless demo), `HistoryStats` stat row, recent-entry list. |
| `AboutScreen.kt` | Full disclosure: score provenance, the model's negative evaluation, last model output, extraction read-out, what the app reads, permission/delete actions, theme picker, version line with long-press demo toggle. |

### Kotlin — tests, `android/app/src/test/` (27) and `androidTest/` (1)

| file | what it verifies |
|---|---|
| `architecture/ArchitectureTest.kt` | Konsist layering/privacy: `ui` imports neither `sensing` nor `inference`; 11 raw platform types appear only in `sensing`; `features` imports no `inference` and no `android.*`/`androidx.*`; `survey` imports nothing; no property named `keyChar`/`typedText`/`messageBody`/`smsBody`/`phoneNumber`/`contactName`/`callerName`/`textContent`; every `@Dao` lives in `data`. |
| `features/SpecVersionTest.kt` | Three tests: the Kotlin `SPEC_VERSION` equals the one in `docs/feature-spec.md`, is well-formed `vX.Y.Z`, and equals the Python constant parsed out of `spec_constants.py`. |
| `features/SharedConstantsTest.kt` | Parses `spec_constants.py` and compares (delta 0.0) `WINDOW_DAYS`, `COVERAGE_MIN_DAYS`, `MAX_SESSION_MINUTES`, `MIN_SLEEP_MINUTES`, `CIRCADIAN_BINS`, `BAND_EDGE_EPS`, both clock bands, and `TIMEZONE` vs `PARITY_TIMEZONE`. It checks only these named constants, not that the two sets are equal. |
| `features/FixtureParityTest.kt` | The Kotlin half of the parity contract over `synthetic_trace.json`: every feature of every case within `trace.tolerance` (NaN as NaN), window bounds and coverage gate per case, `feature_names` equals `FeatureExtractor.FEATURE_NAMES`, 11 required edge cases present, parity holds with the JVM default zone forced to Asia/Kolkata — plus a negative control that computing *under* Asia/Kolkata must fail. |
| `features/ScreenLockFeaturesParityTest.kt` | Unit-level parity over 24 hand-built intervals: all 18 backbone values verbatim to 1e-9, the 4 aux values, unavailable-stream → value 0 *and* `present` 0, empty window all-NaN, the 7×8 sequence equals `_daily_vector`, the 22-name order pinned. |
| `features/NumPyCompatTest.kt` | Verbatim NumPy values where naive Kotlin diverges: `median`, linear-interpolated percentiles, `iqr`, ddof=0 sd, `pearson` (and NaN on zero variance), and the circular-stat quirks (`circular([23,1]).mean == 24.0`). |
| `features/AnalysisWindowTest.kt` | Exact window epochs, pandas-style *absolute* 7-day duration across the DST start, and that the zone argument is genuinely honoured. |
| `features/LockedIntervalDerivationTest.kt` | The §8 derivation rules: pairing, unmatched-HIDDEN and still-open intervals dropped not clamped, duplicate SHOWNs collapsed, zero-length dropped, shutdown discards the open lock, screen events never define or split an interval, events sorted first. |
| `features/ParityFixture.kt` | Not a test — the shared reader for `synthetic_trace.json` (`Case`/`Trace`, repo-root walk, JSON `null` → `NaN`). |
| `data/BundledFixtureTest.kt` | `assets/synthetic_trace.json` is byte-identical to `fixtures/synthetic_trace.json`, so demo mode cannot run against a stale copy. |
| `data/CarryThroughTest.kt` | End-to-end carry of real extractor output through `WindowAssembly` into `WeekSummary`: named features survive, coverage/days correct, exactly 4 rows in order, phrases directionless on a first week and directed once a prior week exists, assembly deterministic, `meanOfWeeks` skips NaN. |
| `sensing/UsageAccessTest.kt` | `isGrantedFrom` semantics — `MODE_ALLOWED` granted regardless of permission, `MODE_IGNORED`/`ERRORED` denied even with it, `MODE_DEFAULT` defers to the permission. |
| `survey/Pss4Test.kt` | The instrument: verbatim items and anchors, items 2 and 3 reverse-scored, extremes and worked examples, `percentOfMaximum` is of 16 and not a percentile, malformed input throws. |
| `survey/Pss4AnswersTest.kt` | `completedResponses` passes a full set through in order and returns null for any gap — and pins that the old `it ?: 0` default would have scored 8/16 = 50 %. |
| `survey/Pss4RangeTest.kt` | Walks all 5⁴ = 625 answer sets: every integer 0–16 reachable, 0–100 % spanned, each item alone moves the total by the full anchor range. |
| `ui/CopyRulesTest.kt` | Greps the real `ui/` string literals: research vocabulary banned outside `AboutScreen`/`Factors`, clinical words banned outside About, alarming phrases banned everywhere, and About must still contain its specific numbers and caveats. |
| `ui/ContrastTest.kt` | WCAG ratios computed from the real palette: ≥4.5:1 for text and button labels in both themes, ≥3:1 for strokes/borders/arrows, plus two documenting assertions of the two combinations that are deliberately below 4.5:1. |
| `ui/WeekSummaryTest.kt` | The largest test: first-week phrasing, UP/DOWN/LEVEL with per-metric deadbands, absence handling (missing/NaN feature drops its row, `*_present = 0` drops comms), reasons for missing access and thin coverage, topic-tied suggestions capped at 2, banned copy, and the demo fixture's expected four directions. |
| `ui/ObservationsTest.kt` | Observation selection (≤3, one per topic, non-empty fallbacks, every one with a suggestion), unavailable/thin-coverage reasons, the `duration`/`clock` formatters incl. edges, 18 banned words, and `Band.forScore` boundaries. |
| `ui/FactorsTest.kt` | Every rankable feature has a label and suggestion, the four availability flags excluded, self-comparison wording only, all-NaN → "Not enough data", no clinical/prescriptive phrasing, clock formatting edges. |
| `ui/HistoryStatsTest.kt` | Null below three check-ins but shown in demo, last-7-day average as % of 16, signed week-over-week change (null not 0 for an empty week), figures reconcile by subtraction, `formatChange`/`countSummary` read as English. |
| `ui/HomeSummaryTest.kt` | `relativeDay` renderings incl. future-date clamping; `phoneLine` busier/quieter/steady, no-prior fallback, and null for empty/low-coverage/missing input; an agreement test that it never contradicts `WeekSummary`'s screen row; copy guards. |
| `ui/ThingsThatHelpTest.kt` | The section never varies with score — same 4 items in a pinned order, `extraFor` non-null only at HIGH, and that extra line free of urgency words. |
| `ui/FaceGeometryTest.kt` | `MouthGeometry.of` for every band: non-zero width, HIGH's level mouth flagged for `drawLine` (the blank-face bug), never a frown, hostile input coerced, mouth inside the circle. |
| `ui/HeroScaleTest.kt` | `heroFontScaleCap` is 1f below 1.3×, holds effective size constant to 2.0×, and keeps "100%" inside 272dp using measured Fraunces advances. |
| `ui/NavigationTest.kt` | `Screen.Analysing` carries the answers as its payload, extreme answer sets give different destinations, a 3-item list throws, and `replaceAll(Home)` leaves `canGoBack == false`. |
| `ui/PermissionStateTest.kt` | `PermissionState` re-reads its supplier: a grant flipped in Settings is invisible until `refresh()` then visible, revocation likewise, first read in the constructor. |
| `androidTest/inference/OnnxParityTest.kt` | On-device: model and reference `spec_version` both equal `SpecConstants.SPEC_VERSION`; per case, \|on-device − PyTorch `expected_stress`\| within the fixture's `tolerance` (1e-5) feeding **raw** inputs through the model's own embedded scaler; and re-deriving `(raw − dyn_mean)/dyn_sd` in float32 matches `std_seq` to 1e-9. |

### Build, config, assets

| file | what it does |
|---|---|
| `android/settings.gradle.kts` | Repositories, `FAIL_ON_PROJECT_REPOS`, root name, single `include(":app")` with a note not to add sibling modules. |
| `android/build.gradle.kts` | Declares the four plugins (`android.application`, `kotlin.android`, `kotlin.compose`, `ksp`) with `apply false`. |
| `android/app/build.gradle.kts` | namespace `com.stressdetect`, compileSdk/targetSdk 36, minSdk 29, JVM 17, Compose + buildConfig, KSP Room `schemaLocation`, deps incl. Room, WorkManager, `onnxruntime-android`, Konsist, `org.json`. |
| `android/gradle/libs.versions.toml` | Version catalog — AGP 8.11.1, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.10.01, Room 2.6.1, Work 2.9.1, coroutines 1.9.0, Konsist 0.17.3, onnxruntime 1.19.2. |
| `android/app/src/main/AndroidManifest.xml` | `PACKAGE_USAGE_STATS`, `READ_CALL_LOG`, `READ_SMS`; **no INTERNET**; `allowBackup=false` + extraction rules; one exported launcher activity. |
| `android/app/src/main/res/xml/data_extraction_rules.xml` | Excludes root/database/sharedpref/file from both cloud backup and device transfer. |
| `android/app/src/main/res/values/{strings,themes}.xml`, `mipmap-anydpi-v26/ic_launcher.xml`, `font/{fraunces,inter}.ttf` | App name, base theme, placeholder adaptive icon, the two bundled variable fonts. |
| `android/app/proguard-rules.pro` | Empty placeholder. |
| `android/tools/push_model_assets.sh` | Greps `SPEC_VERSION` from the spec, copies the exported `.onnx` → `assets/stress_model.onnx` and `fixtures/model_reference.json` → `assets/model_reference.json`, errors if either is missing. |
| `android/app/src/main/assets/` | `synthetic_trace.json` (shared parity fixture, also replayed by demo mode), `model_reference.json`, and the gitignored `stress_model.onnx`. |
| `android/app/schemas/…/{1,2}.json` | Committed Room schema exports for database versions 1 and 2. |
| `.githooks/pre-commit` | Data guard; installed with `git config core.hooksPath .githooks`. |
| `ml/requirements.txt` | Pinned Python 3.9 stack: numpy 1.26.4, pandas 2.2.3, scipy 1.13.1, scikit-learn 1.5.2, xgboost 2.1.4, shap 0.46.0, torch 2.2.2, onnx 1.16.2, onnxruntime 1.18.1, pytest 8.3.2. |

---

## 4. LOSO details

### 4.1 Fold construction

Two separate implementations, both strictly one-subject-out, both guarded.

**XGBoost path** — `ml/src/evaluation/loso.py::_folds` (lines 13–15):

```python
for s in sorted(df["subject"].unique()):
    yield s, df[df["subject"] != s], df[df["subject"] == s]
```

One fold per subject, every row of that subject in test, everything else in train. There is
no random splitting anywhere in the file. `loso_classification` additionally **skips** a fold
whose training labels have `nunique() < 2` (`loso.py:38`).

**Temporal-DL path** — `ml/src/training/loso_torch.py::loso_dl` (lines 62–88): same
construction over `sorted(set(subject))` using boolean masks `subj != s` / `subj == s`, with
the whole loop wrapped in an outer loop over seeds. A fresh model is constructed per fold
(`make_model` line 78) — no weight carry-over between folds.

**Per-fold tripwires** (both raise, never warn — `ml/src/guards.py`):

- `assert_no_subject_leakage(train, test)` — set intersection must be empty
  (`loso.py:20,40`; `loso_torch.py:71`).
- `assert_no_duplicate_feature_vectors(...)` — no two rows in the fold may share an identical
  feature vector (`loso.py:22,41`; and once over the whole flattened DL matrix,
  `run_dl.py:58–59`).

Predictions are clipped to `[0, 100]` in both paths (`loso.py:30`, `loso_torch.py:85`).

### 4.2 Subject-mean baseline

`ml/src/evaluation/metrics.py::subject_mean_baseline` (lines 43–52). It is a
**leave-one-day-out mean of the subject's own true labels**:

```python
tot, n = vals.sum(), len(vals)
out[...] = (tot - vals[j]) / (n - 1) if n > 1 else vals[j]
```

i.e. for each subject-day, the mean of *that subject's other* labelled days. It never uses
model predictions, and it never uses the day being predicted. Degenerate case: a subject with
a single day gets its own value back (zero error on that day).

The companion **global-mean baseline** is leave-one-*subject*-out — the mean of all *other*
subjects' labels — and is implemented **twice, inline and duplicated**:
`run_baseline._global_mean_baseline` (lines 31–37) and again in `run_dl.main` (lines 68–70),
plus a third copy `run_experiments._gm` (lines 155–160). Same formula in all three.

`run_experiments.personalized_eval` (lines 63–78) uses a different, non-LOSO baseline: the
mean of that subject's first *k* chronological training days.

### 4.3 Is preprocessing fitted inside or outside the fold?

| step | inside or outside the fold | evidence |
|---|---|---|
| **Standardization (DL only)** | **INSIDE** — fit on train fold only, applied to the held-out subject | `_fit_scaler(data["X_seq"][tr], data["X_static"][tr])` then `_apply(...)` to both train and test with those train statistics (`loso_torch.py:72–74`) |
| Scaling for XGBoost | none exists (trees; NaN passed through natively) | `loso.py:24` passes raw `float` arrays |
| NaN handling | inside — `np.nan_to_num(..., nan=0.0)` runs **after** standardizing, so 0 means "train mean" | `loso_torch.py:35–36` |
| Zero-variance guard | inside — `ds[ds == 0] = 1.0`, `ss[ss == 0] = 1.0` on train statistics | `loso_torch.py:28–29` |
| Feature extraction | outside, but per-sample only — each window's features depend on that subject's own raw events and no cohort statistic, so no cross-subject information enters | `build_dataset.py:71–91` |
| Label construction (`stress_score`) | outside — subject-day mean of the remapped ordinal, per subject only | `features/labels.py:83–101` |
| Coverage gate (`days_with_data < 3` dropped) | outside, before folding | `build_dataset.py:77–79` |
| Duplicate-window collapse | outside, before folding — grouped by `["subject"] + feat_cols`, so collapsing never spans subjects | `build_dataset.py:23–46` |

`_fit_scaler` computes `np.nanmean`/`np.nanstd` over the sequence flattened to
`(N_train × 7, d_dyn)` and over `(N_train, d_static)` (`loso_torch.py:26–30`) — NaN-aware, so
missing days do not bias the statistics.

**Two things that are deliberately outside LOSO and should not be read as held-out results:**

1. The **exported model** is fit on *all* data with a scaler also fit on all data
   (`run_dl.py:132–135`). That is correct for a shipped artifact but it is not an evaluated
   model.
2. **SHAP / attribution** runs on that all-data model (`run_dl.py:138`), and the code says so
   (`attribution.py:4–7`, `run_dl.py:144–145`).

`training_curves` (`loso_torch.py:91–104`) holds out a random 20 % of **training subjects**
as validation — subject-level, never the LOSO test subject — and is used only to plot curves.

---

## 5. ONNX export

Source: `ml/src/models/onnx_export.py::export_and_verify`, called from
`ml/src/training/run_dl.py:149–152`.

### 5.1 Export call

- **Opset version: `17`** (`onnx_export.py:29`).
- `input_names = ["seq", "static"]`, `output_names = ["stress"]`.
- `dynamic_axes = {"seq": {0: "batch"}, "static": {0: "batch"}, "stress": {0: "batch"}}` —
  batch is the only dynamic axis.
- Tracing inputs: `torch.randn(1, 7, d_dyn)` and `torch.randn(1, d_static)`.
- Output path: `ml/data/processed/temporal_cnnlstm_{SPEC_VERSION}.onnx` (gitignored).

### 5.2 Metadata keys written to `metadata_props`

Written by re-loading with `onnx.load`, appending `metadata_props` entries, and re-saving
(`onnx_export.py:47–50`). Exactly **five** keys, all string-valued:

| key | value |
|---|---|
| `spec_version` | `"v0.7.0"` — the `SPEC_VERSION` literal |
| `input_contract` | `"seq:(B,7,8) float32; static:(B,9) float32; output stress:(B,)"` (built at line 31) |
| `standardization` | `json.dumps` of `{"dyn_mean": [8 floats], "dyn_sd": [8], "static_mean": [9], "static_sd": [9], "order": "standardize_then_nan_to_zero"}` |
| `dyn_features` | `json.dumps` of the 8 dynamic column names, in order |
| `static_features` | `json.dumps` of the 9 static column names, in order |

The scaler parameters are the four arrays from `_fit_scaler` — `dyn_mean`, `dyn_sd`,
`static_mean`, `static_sd` — plus the explicit `order` field. `dyn_features`,
`static_features` and `standardization` are only written when the corresponding argument is
non-`None`; `run_dl.py:149–152` passes all three, so a real export always carries all five keys.

### 5.3 Export-time verification

`onnx_export.py:52–58` opens an `onnxruntime.InferenceSession`, runs the same random tracing
inputs through PyTorch and ORT, and returns `max_abs_diff` plus
`np.allclose(..., atol=1e-5)`. `run_dl.py:156–157` prints it as `MATCH (<=1e-5)` / `MISMATCH`.
This is a **print, not an assert** — the export is not aborted on mismatch.

A second, stronger check writes `fixtures/model_reference.json`
(`ml/src/models/model_reference.py`): for each of the 13 committed fixture cases it records
`raw_seq`, `raw_static`, `std_seq`, `std_static`, the PyTorch output (`expected_stress`) and
the ORT output (`onnxruntime_stress`), with `tolerance: 1e-05`, so an on-device mismatch
localises to either the scaler or the model.

That file is what the instrumented test consumes. `OnnxParityTest.kt` reads **both** assets
from the APK (`stress_model.onnx`, `model_reference.json`), does **not** hardcode the
prediction tolerance — it uses `ref.getDouble("tolerance")`, currently `1e-5` — and feeds
`raw_seq`/`raw_static` so the model's own embedded scaler does the standardizing. A separate
hardcoded `1e-9` is used for the check that re-deriving `(raw − dyn_mean)/dyn_sd` in float32
reproduces the reference's `std_seq`.

### 5.4 How the app reads the metadata

`android/.../inference/OnnxStressModel.kt`, `load(modelBytes)` (lines 118–150), reading
`session.metadata.customMetadata`:

1. **`spec_version`** — missing ⇒ `error(...)`; present but `!= SpecConstants.SPEC_VERSION`
   ⇒ `check(...)` fails (lines 123–134). Refusing to run is deliberate: mismatched feature
   definitions would produce a confident wrong number.
2. **`standardization`** — missing ⇒ `error(...)` (lines 136–141). Parsed with `org.json`
   into `Standardization(dynMean, dynSd, staticMean, staticSd)` via the private
   `JSONObject.doubleArray` helper (lines 142–155).
3. At `predict` time each raw feature is standardized with those arrays and **NaN maps to
   `0.0f` only after standardizing** (`standardize`, lines 97–101) — matching the exported
   `order: "standardize_then_nan_to_zero"`. `z.isNaN()` also maps to `0.0f`, which covers an
   `sd` of 0 arriving from metadata.

**The app does not read `input_contract`, `dyn_features`, or `static_features`.** Column
order is instead hardcoded on the Kotlin side as
`SequenceFeatures.DYNAMIC_FEATURE_NAMES` / `STATIC_FEATURE_NAMES`, and only the *sizes* are
checked (`OnnxStressModel.kt:44–54`). A permuted-but-same-length export would therefore pass
load and score silently wrong — the metadata that would catch it is written but unread.
`SPEC_VERSION` equality is the only thing standing in the way.

Tensor construction (lines 51–74): `FloatBuffer`s shaped `(1, 7, 8)` and `(1, 9)`, fed as
`"seq"` and `"static"`. The rank-1 `stress` output is read defensively — both `FloatArray`
and `Array<*>` are accepted (lines 83–90).

**Getting the model onto the device:** `android/tools/push_model_assets.sh` greps
`SPEC_VERSION` out of `docs/feature-spec.md`, then copies
`ml/data/processed/temporal_cnnlstm_${SPEC_VERSION}.onnx` → `assets/stress_model.onnx` and
`fixtures/model_reference.json` → `assets/model_reference.json`. Both the model binary and
the assets dir are gitignored, so a fresh clone has no model.

**At runtime:** `data/ResultRepository.loadModel()` (lines 112–119) reads the
`stress_model.onnx` asset and calls `OnnxStressModel.load`. It catches
`FileNotFoundException` (no model bundled) and `IllegalStateException` (spec-version or
scaler mismatch) and returns `null` in both cases — the questionnaire result is still shown,
the model estimate is not.
