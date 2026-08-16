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

## Phase 6 — Parity + ONNX &nbsp;`[COMPLETE]`
Kotlin feature extraction == Python on the shared fixture; model export.
- **Exit met:** `fixtures/synthetic_trace.json` now holds **13 cases**, each targeting one
  named spec rule (midnight-wrap sleep, both sleep-band edges, the 90-min and 180-min
  cutoffs, coverage gate, intervals open at each window edge, empty window, DST in **both**
  directions, the band-edge tie-break, and `days_with_data = 8`). Python
  (`ml/tests/test_parity_fixture.py`) and Kotlin (`FixtureParityTest`) both run it to
  **1e-6**, with the zone passed explicitly and a test asserting the run FAILS under the
  device zone. ✅
- **On-device inference verified on an emulator** (API 35): ONNX Runtime Mobile vs PyTorch
  over all 13 cases, **max|diff| = 7.63e-6 ≤ 1e-5**. `OnnxStressModel` refuses to load a
  model whose `spec_version` metadata disagrees with the extractor, and standardizes from
  the scaler embedded in the model's metadata. ✅
- **SPEC_VERSION agreement across all four** — doc, `spec_constants.py`, `SpecConstants.kt`
  (`SpecVersionTest`) and the ONNX metadata (`OnnxParityTest`). `SharedConstantsTest` extends
  this to every mirrored threshold, so a value can no longer drift on one side alone.
- **The fixture earned its keep on the first run**, catching a bug no unit test could have:
  the JVM and numpy differ by ~1 ULP in `cos`/`sin`/`atan2`, which flipped a person-relative
  night-band boundary decision (0.0 vs 0.75) for the common case of a subject whose unlock
  time equals their own mean wake time — 12 such places in the real corpus. A boolean flip
  cannot be absorbed by a tolerance, so the comparison itself was made deterministic:
  **`BAND_EDGE_EPS`**, applied identically in both extractors (feature-spec §9).
  `SPEC_VERSION` → **v0.7.0**, model retrained and re-exported; **every headline number is
  unchanged** (MAE 20.76 ± 0.10, Spearman −0.116, 14/48), confirmed by re-running the
  pipeline rather than assumed.
- **Both guards verified to FAIL when violated**, not just to pass: perturbing
  `MAX_SESSION_MINUTES` and swapping the window to calendar arithmetic each broke the
  fixture (the latter by exactly 3600 s on the DST cases), and skipping standardization
  broke the on-device test by 14 stress points.
- **Two lessons recorded for later phases** (feature-spec §10): the exported model had no
  scaler, so the app would have fed raw features to a model expecting z-scores and shown a
  plausible wrong score (62.60 vs 48.54) with no crash — a model artifact without its
  preprocessing is incomplete, and the loader now refuses such a file. And the DST
  perturbation failed ONLY the window-bounds assertion, with no feature changing on any of
  the 13 cases — so a parity test that compares only final outputs can certify a wrong
  intermediate; pin the intermediates too.

## Phase 7 — UI + demo &nbsp;`[COMPLETE]`
Result screen (stress percentage, contributing factors, suggestions) and a trend chart.
- **Exit met:** the full flow — onboarding → permissions → PSS-4 → analysis → result —
  runs end to end on an emulator in **21 seconds**, well inside the 2-minute target. ✅
- **PSS-4** (Cohen, Kamarck & Mermelstein 1983) in the published wording, verified against
  two independent sources; items 2 and 3 reverse-scored, total 0–16. `Pss4Test` asserts each
  item **verbatim** so a copy edit fails the build, and asserts the reversal per item —
  confirmed on device (answers 2/2/1/3 → **10 of 16**, which only holds if the reversal is
  applied). The published stem asks about *the last month* while the phone window is 7 days;
  the wording was NOT adapted, and the result screen states the mismatch.
- **Framing is in the UI, not a help page:** the questionnaire is the result; the model
  estimate is visually demoted (muted, dashed, smaller) and carries its own invalidation
  inline, with the real numbers behind an expander (20.8 vs 16.9 vs 19.9, ρ = −0.12);
  factors are labelled context, not causes; suggestions are a fixed lookup table with a
  "not advice for you personally" disclaimer. No diagnostic language anywhere.
- **Attribution is occlusion, not SHAP, and is not called SHAP.** SHAP yields *global*
  importances; ranking by those would have meant comparing the user against the StudentLife
  population, which the self-baseline rule forbids. Occlusion is local by construction.
  Factor wording compares each user only to **their own week**.
- **Honest degradation is in the type**: `AnalysisResult` carries `usageAccessMissing`,
  `meetsCoverage`, `commsIncluded` and `modelUnavailableReason` explicitly, so a missing
  stream cannot shrink the factor list silently — declined call/SMS features are excluded
  from ranking (absent ≠ average) and the screen says so.
- **Demo mode** replays the fixture's `demo_week` case — added because every other case is a
  four-interval rule test (~0.7 unlocks/day), correct for what it pins but nonsense as an
  example week. It is generated and parity-checked with the rest, so the demo cannot drift.
  A `DEMO DATA` banner sits in the root scaffold, on every screen.
- **Lesson recorded (feature-spec §10.3):** the "busiest day" copy bug — correct numbers,
  false sentence, every test green. Generated prose is output that unit tests do not cover.

## Phase 8 — Evaluation + writeup
Final LOSO, ablations, SHAP, calibration, per-subject variance.
- **Exit:** evaluation + interpretability results written up for the report/defense.

## Phase 9 — Stretch
Live collection for mobility, a 12–24h forecasting head, typing dynamics, personal-baseline
calibration.
- **Exit:** optional — each stretch item is independently shippable, none block Phases 2–8.
