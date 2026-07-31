# android/ — Kotlin / Android conventions

Passive sensing + on-device ONNX inference app. Read the root **CLAUDE.md** first for the
four non-negotiable constraints; they apply here in full.

## Stack
Kotlin, Jetpack Compose, Material 3, Room, WorkManager, foreground service for sensing,
UsageStatsManager, AccessibilityService (timing signals ONLY), FusedLocationProvider,
Health Connect (optional), ONNX Runtime Mobile.

## Structure — single Gradle module `app/`, packages inside
Base package: `com.stressdetect`. Packages live at
`app/src/main/java/com/stressdetect/<pkg>/` (see `android/README.md` to generate the
Gradle project). There are NO sibling module dirs.
- `com.stressdetect.sensing`   — collectors: usage, motion, location, typing timing.
- `com.stressdetect.features`  — derived feature vectors (mirror of `ml/src/features`).
- `com.stressdetect.inference` — ONNX Runtime Mobile model load + prediction.
- `com.stressdetect.ui`        — Compose screens; self-baseline comparison only.

## Hard constraints (repeat)
- TYPING: capture inter-key intervals, backspace counts, pause distributions ONLY.
  The typing collector must have NO API surface that receives characters/text.
- FEATURE PARITY: features here MUST equal `ml/src/features` output for identical input.
  `docs/feature-spec.md` is authoritative.
- DATA EGRESS: only derived features may persist/export; raw sensor streams never leave
  the device.

## Konsist architecture tests (in `app/src/test/`) — hard constraints
These enforce the reason we chose packages over Gradle modules. Do not drop them:
- No class in `ui/` may import from `sensing/`.
- Raw sensor / keystroke types must NOT be referenced outside `sensing/`.
- `inference/` may depend on `features/`, never the reverse.

## SPEC_VERSION
The Android feature extractor must expose the `SPEC_VERSION` (from `docs/feature-spec.md`)
it implements. The parity test asserts it matches the Python side and the `fixtures/`
trace; the value is written into ONNX model metadata at export.

## Conventions
- Sensing writes to Room; features are computed from Room, not from live streams.
- All background work via WorkManager / a typed foreground service.
- No character logging anywhere; no PII in logs.

## NEEDS-VERIFICATION (do not assert from memory)
- Background-execution limits and exact `foregroundServiceType` values/requirements.
- AccessibilityService allowed use + policy constraints for timing-only signals.
- UsageStatsManager & Health Connect permission requirements.
- Min/target SDK — confirm with the team; do not hardcode an unverified API level.
