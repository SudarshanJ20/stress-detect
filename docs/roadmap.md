# Roadmap — 16 weeks (relative; no calendar dates)

Two milestones are **IMMOVABLE** — everything else flexes around them.

## Immovable milestones
- [ ] **Wk1 — IMMOVABLE: ethics/IRB application SUBMITTED.** Approval has multi-week lead
      time; submit BEFORE docs are polished. Nothing that touches real participants starts
      until approval lands.
- [ ] **Wk6 — IMMOVABLE: collector app installed on participant phones.** Data collection
      must start on schedule to fit the collection window into the semester.

## Recruitment
- [ ] Recruit **~30 participants to retain ~20** (assume 30–40% attrition).
- [ ] Block out semester **exam weeks** on the schedule. Spanning an exam period is
      **DESIRABLE** — it acts as quasi-experimental validation (naturally elevated stress).

## Phase plan (parallel tracks)

### Wk1 — Kickoff
- [ ] Ethics/IRB submitted (IMMOVABLE, above).
- [ ] Repo scaffold committed and pushed (public).
- [ ] `docs/feature-spec.md` drafted (structure + candidate features).

### Wk1–2 — Lock the contract
- [ ] `feature-spec.md` **LOCKED**: every `TODO` filled; `SPEC_VERSION` set (≥ v0.1.0).
- [ ] Shared parity fixture (`fixtures/synthetic_trace.json`) finalized.

### Wk2–5 — ML insurance policy (must finish before collection starts)
- [ ] StudentLife ETL → versioned Parquet in `ml/data/processed/`.
- [ ] XGBoost baseline.
- [ ] Leave-one-subject-out (LOSO) evaluation harness.

### Wk3–6 — Collector app (ugly UI is fine)
- [ ] Sensing (usage, motion, location, typing-timing) → Room.
- [ ] EMA prompts (self-report labels).
- [ ] Derived-feature export.
- [ ] **Konsist architecture tests** in `app/src/test/` (ui ⊥ sensing; raw types confined
      to `sensing/`; `inference/` → `features/` only).

### Wk6 — Collection go-live
- [ ] Parity test GREEN (Android features == Python features == fixture; SPEC_VERSION
      match).
- [ ] App installed on participant phones (IMMOVABLE, above).

### Wk6–10 — **Data collecting in background** (parallel with DL work)
- [ ] Monitor uptime / dropout; keep participants engaged.

### Wk7–11 — Modeling
- [ ] Multimodal DL model.
- [ ] 12–24h forecasting head.
- [ ] Personalisation (self-baseline calibration).

### Wk10–13 — Real app + on-device
- [ ] Production Compose UI (self-baseline comparison only).
- [ ] ONNX export (SPEC_VERSION embedded in model metadata).
- [ ] On-device inference + demo mode.

### Wk12–16 — Evaluation & writeup
- [ ] LOSO evaluation on collected data; ablations; SHAP.
- [ ] Report / thesis / defense materials.
