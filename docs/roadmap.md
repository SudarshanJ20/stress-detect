# Roadmap — Phases 0–9 (no dates)

Design after the pivot: **retrospective, single-shot stress analysis** trained on **public
datasets** (StudentLife, and — if access lands — K-EmoPhone, GLOBEM). At install the app
backfills a **7-day** window from history already on the device (screen/lock backbone;
call/SMS auxiliary) and returns a one-shot result. **No participant recruitment, no ethics
approval blocker, no background data collection, no fixed-week milestones.** Live
collection is a stretch goal only (Phase 9).

Each phase has a one-line **Exit** criterion. Order is roughly sequential but tracks can
overlap.

## Phase 0 — Data access &nbsp;`[COMPLETE]`
StudentLife acquired; K-EmoPhone + GLOBEM access requested.
- **Exit:** StudentLife on disk (`ml/data/raw/`); access requests for K-EmoPhone + GLOBEM sent. ✅

## Phase 1 — Feasibility &nbsp;`[COMPLETE]`
Dataset inventoried, on-device retrospective probe run, feature scope resolved, 7-day
analysis window fixed.
- **Exit:** `docs/dataset-inventory.md`, `docs/device-probe-results.md`, and
  `docs/feature-spec.md` (scope + 7-day window) all recorded. ✅

## Phase 2 — Pipeline + baseline
StudentLife ETL → Parquet, feature extraction, XGBoost, LOSO harness.
- **Exit:** a real LOSO number from an XGBoost baseline on StudentLife.

## Phase 3 — Feature lock
Reconcile K-EmoPhone / GLOBEM schemas *if* access lands; finalize the feature contract.
- **Exit:** `SPEC_VERSION` locked; every `feature-spec.md` `TODO` filled (schemas reconciled,
  or reconciliation explicitly deferred if access hasn't arrived).

## Phase 4 — Deep models
Multimodal / temporal encoders, regression head with calibration, SHAP attribution,
cross-dataset evaluation, ablations vs. the XGBoost baseline.
- **Exit:** DL results reported against XGBoost, with cross-dataset eval + ablations + SHAP.

## Phase 5 — Android app
On-device historical queries, feature extraction matching the Python pipeline, Room caching,
Konsist architecture tests.
- **Note:** **Health Connect is OUT OF SCOPE** — the device probe returned **0 records**
  (Sleep and Steps); nothing writes to it, so it cannot seed the retrospective baseline.
- **Exit:** app reconstructs the 7-day feature window on-device from historical queries;
  Konsist tests green.

## Phase 6 — Parity + ONNX
Kotlin feature extraction == Python on the shared fixture; model export.
- **Exit:** parity test GREEN on `fixtures/synthetic_trace.json`; ONNX exported with
  `SPEC_VERSION` embedded in metadata.

## Phase 7 — UI + demo
Result screen (stress percentage, contributing factors, suggestions) and a trend chart.
- **Exit:** end-to-end demo runs in under 2 minutes.

## Phase 8 — Evaluation + writeup
Final LOSO, ablations, SHAP, calibration, per-subject variance.
- **Exit:** evaluation + interpretability results written up for the report/defense.

## Phase 9 — Stretch
Live collection for mobility, a 12–24h forecasting head, typing dynamics, personal-baseline
calibration.
- **Exit:** optional — each stretch item is independently shippable, none block Phases 2–8.
