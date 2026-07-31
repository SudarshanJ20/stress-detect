# Architecture

End-to-end data flow. Everything up to and including inference happens **on-device**; only
derived features ever cross the device boundary.

```
   ┌─────────────────────────────────────────────────────────────────────┐
   │ DEVICE                                                               │
   │                                                                     │
   │  Sensors                                                            │
   │   • usage (UsageStatsManager)                                       │
   │   • motion / activity                                               │
   │   • location (FusedLocationProvider)                                │
   │   • typing TIMING only (AccessibilityService — no characters)       │
   │        │                                                            │
   │        ▼                                                            │
   │  Room (on-device store)                                             │
   │        │                                                            │
   │        ▼                                                            │
   │  Feature extraction  ── must match docs/feature-spec.md ──┐         │
   │   (android/…/features)                                    │         │
   │        │                                     SPEC_VERSION │         │
   │        ▼                                                  │         │
   │  ONNX Runtime Mobile                                      │         │
   │   • detect current stress                                │         │
   │   • FORECAST 12–24h ahead  ◄── headline contribution     │         │
   │        │                                                  │         │
   │        ▼                                                  │         │
   │  UI (Jetpack Compose)                                     │         │
   │   • today vs. the user's OWN baseline (no population %)   │         │
   └──────────────────────────────────────────────────────────┼─────────┘
                                                               │
            derived features ONLY (manual export / upload)     ▼
   ┌─────────────────────────────────────────────────────────────────────┐
   │ OFF-DEVICE (research)                                                │
   │  ml/data/processed/*.parquet  (versioned snapshots, gitignored)     │
   │        │                                                            │
   │        ▼                                                            │
   │  ml/src/features  ── must match Android extractor (parity test) ──   │
   │        │                                                            │
   │        ▼                                                            │
   │  Training (Kaggle free tier): XGBoost baseline → multimodal DL →     │
   │  12–24h forecasting head → personalisation                          │
   │        │                                                            │
   │        ▼                                                            │
   │  Evaluation: leave-one-subject-out CV, ablations, SHAP              │
   │        │                                                            │
   │        ▼                                                            │
   │  ONNX export (SPEC_VERSION embedded in model metadata) → device     │
   └─────────────────────────────────────────────────────────────────────┘
```

## The parity contract
The two feature extractors are the highest-risk coupling in the system. Guardrails:
- `docs/feature-spec.md` is the single source of truth and carries `SPEC_VERSION`.
- `fixtures/synthetic_trace.json` is a shared input→expected-features trace committed to
  the repo and run by BOTH the Kotlin and Python test suites.
- The parity test fails if the two extractors disagree, or if either implements a
  `SPEC_VERSION` different from the fixture.

## Self-baseline calibration
The UI never shows a population percentile. Predictions are compared against a
per-user baseline built from that user's own history. Calibration lives in
`ml/src/evaluation` and is carried on-device for display.

## On-device boundary
Raw sensor streams stay in Room and are never exported. Only derived features (matching
the spec) may be written to Parquet snapshots for research. There is no server-side
inference and no remote training database.

## Android runtime specifics — NEEDS-VERIFICATION
Background-execution limits, `foregroundServiceType`, AccessibilityService policy, and
permission requirements must be verified against current official Android documentation
before implementation. Do not hardcode unverified API levels.
