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

## Phase 2 — Pipeline + baseline &nbsp;`[COMPLETE]`
StudentLife ETL → Parquet, feature extraction, XGBoost, LOSO harness.
- **Exit met:** a real LOSO number — and it is a **validated null**. Screen/lock (and the
  full StudentLife sensor suite) do not predict momentary EMA stress at day *or* week level;
  the only predictable structure is the per-person trait mean. Fully diagnosed across
  Experiments 1–6 (`docs/phase2-results.md`, `docs/findings-summary.md`). ✅
- The **harness is dataset-agnostic** (ETL → remapped labels → backbone features → LOSO →
  guards → global/subject-mean baselines) and re-runs unchanged on **GLOBEM / K-EmoPhone**,
  which are purpose-built for momentary affect. No further modelling on StudentLife.

## Phase 3 — Feature lock
Reconcile K-EmoPhone / GLOBEM schemas *if* access lands; finalize the feature contract.
- **Exit:** `SPEC_VERSION` locked; every `feature-spec.md` `TODO` filled (schemas reconciled,
  or reconciliation explicitly deferred if access hasn't arrived).

## Phase 4 — Deep models &nbsp;`[COMPLETE on StudentLife]`
Temporal encoders over the 7-day daily sequence, calibration, SHAP attribution, ablations
vs. the XGBoost baseline, ONNX export + runtime parity.
- **Exit met:** CNN-LSTM (5 seeds) + TinyTransformer (ablation) reported against XGBoost and
  BOTH baselines (`docs/phase4-results.md`). The sequence representation stably beats flat
  XGBoost (22.59 → 20.76 ± 0.10) yet is **still a null** (below global-mean 19.94, 3.8 off
  subject-mean 16.95) — which closes the "model-too-simple" objection to the Exp 1–6 chain.
  SHAP attribution + ONNX export (PyTorch↔ORT parity ≤ 1e-5, `SPEC_VERSION` in metadata)
  built and validated; `ml/tests/test_spec_version.py` guards code↔doc drift. ✅
- **Deferred to GLOBEM:** cross-dataset evaluation (StudentLife has no signal to transfer).
  The temporal-DL + attribution + ONNX infra re-runs unchanged there.

## Phase 5 — Android app &nbsp;`[COMPLETE]`
On-device historical queries, feature extraction matching the Python pipeline, Room caching,
Konsist architecture tests.
- **Note:** **Health Connect is OUT OF SCOPE** — the device probe returned **0 records**
  (Sleep and Steps); nothing writes to it, so it cannot seed the retrospective baseline.
- **SPEC_VERSION guard (mirror of `ml/tests/test_spec_version.py`):** an Android unit/Konsist
  test MUST assert the Kotlin `SPEC_VERSION` equals the one in `docs/feature-spec.md` and the
  ONNX metadata, failing the build on drift. Silent code/doc/model drift is the exact failure
  the version field exists to catch (Phase 6 parity depends on it).
- **Exit met:** single Gradle module in `android/` (`com.stressdetect`, packages
  `sensing` / `features` / `data` / `inference` / `ui`) reconstructs the 7-day window
  on-device from `queryEvents` + CallLog/SMS history, caches it in Room, and passes
  **39 unit tests** including Konsist and the SPEC_VERSION guard. ✅
- **Backbone mapping recorded** in `feature-spec.md` §8: locked interval =
  `KEYGUARD_SHOWN → KEYGUARD_HIDDEN` (keyguard, not screen — the probe saw ~1.8× more screen
  wakes than unlocks, and a notification at 03:00 would halve a reported sleep); intervals
  open at a window edge are **dropped, never clamped**; `minSdk = 29` because
  `DEVICE_SHUTDOWN/STARTUP` (data gap ≠ lock) are API 29, verified against the SDK's
  `api-versions.xml`.
- **Parity is asserted against the real Python extractor** — the expected values in
  `ScreenLockFeaturesParityTest` were produced by running `ml/src/features` over the same
  intervals, not hand-computed. The timezone is always passed explicitly, so the test cannot
  pass by both sides reading the same ambient default.
- **Two parity hazards found and recorded** (`feature-spec.md` §8): pandas `Timedelta` is an
  ABSOLUTE duration, so window arithmetic must be in seconds — `minusDays` would have broken
  parity across DST twice a year, invisibly; and `days_with_data` can reach 8 in a 7-day
  window, deflating every per-day rate (mirrored, not fixed — **TODO(team)**, and the fix
  starts on the Python side).
- **Deferred to Phase 6:** the `fixtures/synthetic_trace.json` contract test read by both
  suites (the fixture is still `TODO`), and ONNX inference (`inference/` is an interface
  carrying the §7 input contract).

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
