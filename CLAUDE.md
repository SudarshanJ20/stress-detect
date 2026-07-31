# stress-detect — project memory

Deep learning–based **early mental stress detection & forecasting** from passive
behavioral phone data. On-device DL inference; the UI compares today's stress to the
user's **own personal baseline** (never an absolute population percentile). Headline
contribution: **forecasting stress 12–24h ahead**, not just detecting it.

Academic research prototype — **not a medical device**, no diagnostic/clinical claims.

## This is a two-language monorepo
- Android app (Kotlin) → `android/` — see **android/CLAUDE.md** for Android rules.
- ML pipeline (Python) → `ml/` — see **ml/CLAUDE.md** for ML rules.
- Docs (source of truth) → `docs/`.
Always read the sub-CLAUDE.md for the side you are editing.

## Non-negotiable constraints (apply to BOTH sides)
1. TYPING PRIVACY — typing data = inter-key intervals, backspace counts, pause
   distributions ONLY. NEVER store, log, or transmit typed characters. Code must be
   architecturally incapable of capturing character content.
2. FEATURE PARITY — Android and Python feature extraction MUST produce identical
   vectors from identical input. `docs/feature-spec.md` is authoritative. Never
   change one side without updating the other, the spec, and `SPEC_VERSION`.
3. EVALUATION — leave-one-subject-out cross-validation. NEVER random-split windows
   from the same user across train/test.
4. DATA EGRESS — no raw sensor streams leave the device. Derived features only.

## SPEC_VERSION
`docs/feature-spec.md` carries a `SPEC_VERSION` (starts at v0.1.0; bump on any feature
change). Both feature implementations expose the version they implement; the parity test
asserts the two match each other and the `fixtures/` trace; the version is embedded in
exported ONNX model metadata.

## Repo layout
- `docs/feature-spec.md`   — SINGLE SOURCE OF TRUTH for every feature.
- `docs/architecture.md`   — data flow: sensors → Room → features → ONNX → UI.
- `docs/ethics-and-privacy.md` — consent, collection, on-device guarantees.
- `docs/roadmap.md`        — 16-week checkbox plan.
- `android/`               — Kotlin app (single Gradle module, packages inside).
- `ml/`                    — Python training/eval; reads Parquet from `ml/data/processed/`.
- `fixtures/`              — committed synthetic parity trace (JSON); never gitignored.
- `.githooks/pre-commit`   — data guard; install with `git config core.hooksPath .githooks`.

## Data & training model
- Bootstrap dataset: StudentLife (Dartmouth). Own collection later (~20–30 users).
- Training runs on Kaggle Notebooks (free tier). No cloud, no server.
- Training reads **versioned Parquet snapshots** from `ml/data/processed/`.
- `ml/data/` is gitignored — never commit raw or derived data.

## DON'Ts
- Do NOT show absolute/population stress percentages — only self-baseline deltas.
- Do NOT commit data, `*.parquet`, `*.csv`, `*.db`, model binaries, `local.properties`.
- Do NOT do server-side inference, raw sensor egress, or keep a remote training DB.
  Transport of DERIVED FEATURES ONLY off-device (manual export / participant upload)
  is expected and permitted.
- Do NOT change a feature on one side only (breaks parity → wrong predictions).
- Do NOT put anything identifying in the repo (it is public and permanent): no IRB/ethics
  numbers, supervisor/examiner/participant names, internal URLs, or specific exam dates.
  Reference them generically and leave a TODO.
- Do NOT assert current Android background-execution / foreground-service-type /
  AccessibilityService rules from memory — verify against official docs and mark
  NEEDS-VERIFICATION until confirmed.

## Status
Scaffold only — no implementation code yet. See `docs/roadmap.md`.
