# ml/ — Python / PyTorch conventions

Feature engineering, model training, evaluation, forecasting. Read the root **CLAUDE.md**
first for the four non-negotiable constraints; they apply here in full.

## Stack
Python, PyTorch, pandas, scikit-learn, XGBoost (baseline), SHAP. Runs on Kaggle Notebooks
free tier. No cloud backend, no training database.

## Structure
- `src/features/`   — feature extraction; MUST match `android/…/features` output.
- `src/models/`     — model definitions (baseline XGBoost + DL forecasters).
- `src/training/`   — training loops, LOSO splits, Kaggle entry points.
- `src/evaluation/` — metrics, SHAP, self-baseline calibration.
- `notebooks/`      — exploration; keep heavy logic in `src/`.
- `data/raw/`, `data/processed/` — gitignored; Parquet snapshots only.

## Hard constraints (repeat)
- FEATURE PARITY: `src/features` MUST produce identical vectors to the Android side for
  identical input. `docs/feature-spec.md` is authoritative — change the spec + both sides
  + `SPEC_VERSION` together, never one alone.
- EVALUATION: leave-one-subject-out CV ONLY. Never random-split windows from one user
  across train/test. Report per-subject held-out results.
- TYPING: consume only inter-key intervals / backspace counts / pause distributions —
  never any character content (it never exists in the data).
- No raw sensor streams in the repo — derived features / Parquet only.

## SPEC_VERSION
The Python feature extractor must expose the `SPEC_VERSION` it implements. The parity test
asserts it matches the Android side and the `fixtures/` trace; the value is embedded in
ONNX model metadata at export.

## Data & reproducibility
- Bootstrap: StudentLife (Dartmouth). Later: own ~20–30 user collection.
- Training reads versioned Parquet from `data/processed/` — treat snapshots as immutable
  versions; never commit them.
- Pin dependencies in `requirements.txt` to the Kaggle free-tier environment.

## DON'Ts
- Do NOT commit data, `*.parquet`, `*.csv`, `*.db`, model binaries.
- Do NOT introduce random train/test splits within a subject.
- Do NOT produce absolute population percentiles — forecasts/scores feed a self-baseline
  comparison.
