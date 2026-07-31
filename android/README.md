# android/

The Android app is a **single Gradle module**. No Gradle project is committed by the
scaffold yet — generate it here.

## Generate the Gradle project
1. In Android Studio: **New Project → Empty Activity (Compose)**.
2. Set the location to this `android/` directory.
3. Base package / applicationId: **`com.stressdetect`**.
   - ⚠️ The applicationId is visible to participants under **Settings → Apps**. For the
     participant-facing study build, use a neutral display name and applicationId
     (see `docs/ethics-and-privacy.md`). "Decide study-build applicationId" is a team
     **TODO**.
4. Create the four packages under `app/src/main/java/com/stressdetect/`:
   `sensing/`, `features/`, `inference/`, `ui/`.

## Enforce architecture with Konsist
Add the Konsist test dependency and implement these tests in `app/src/test/` (see
`android/CLAUDE.md` for the authoritative list):
- No class in `ui/` imports from `sensing/`.
- Raw sensor / keystroke types are not referenced outside `sensing/`.
- `inference/` may depend on `features/`, never the reverse.

## Before you commit
Ensure the pre-commit hook is active for your clone:

```sh
git config core.hooksPath .githooks
```

## Runtime specifics — NEEDS-VERIFICATION
Background-execution limits, `foregroundServiceType`, AccessibilityService policy,
UsageStats / Health Connect permissions, and min/target SDK must be verified against
current official Android docs before use. Do not hardcode unverified API levels.
