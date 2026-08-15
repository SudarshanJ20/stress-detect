# android/

Single Gradle module (`app/`), base package `com.stressdetect`. Packages, not sibling
modules — the layering is enforced by Konsist tests (below), not by module boundaries.

```
app/src/main/java/com/stressdetect/
  sensing/     retrospective queries; ALL raw Android sensor/provider types live here
  features/    pure-JVM mirror of ml/src/features (no Android imports at all)
  data/        Room store + extraction coordinator + WorkManager worker
  inference/   ONNX Runtime Mobile (Phase 6 — interface only today)
  ui/          Compose screens; may not import sensing
```

## Build

```sh
./gradlew :app:testDebugUnitTest     # 39 unit tests: parity, architecture, spec version
./gradlew :app:assembleDebug
```

Toolchain: Gradle 8.13, AGP 8.11.1, Kotlin 2.0.21, JDK 17 target, `compileSdk 36`.

`local.properties` (gitignored) must point at your SDK: `sdk.dir=/path/to/Android/sdk`.

**`minSdk = 29` is a requirement, not a preference.** `UsageEvents.Event.DEVICE_SHUTDOWN`
and `DEVICE_STARTUP` — which distinguish a powered-off phone (a data gap) from a locked one
(possible sleep) — are API 29; `KEYGUARD_SHOWN/HIDDEN` are API 28. Verified against the
installed SDK's `platforms/*/data/api-versions.xml`.

## What the data layer does

At install it backfills a 7-day window from history already on the device
(`docs/feature-spec.md` §5, §8):

| source | API | role |
|---|---|---|
| screen/lock | `UsageStatsManager.queryEvents` | **BACKBONE** — locked intervals ⇒ sleep/usage/night/circadian features |
| app usage | `queryUsageStats(INTERVAL_DAILY)` | context only; **no feature may be built on it** |
| calls | `CallLog.Calls` (DATE column only) | auxiliary, requires a `call_present` flag |
| SMS | `Telephony.Sms` (DATE column only) | auxiliary, requires an `sms_present` flag |

Health Connect is **out of scope** — the device probe returned 0 records.

Raw events → Room → features computed *from Room* → cached vector keyed by
`(labelDate, SPEC_VERSION)`. Raw events are kept because `queryEvents` retention is ~10
days: what is not captured at install is gone permanently.

## Feature parity

`features/` is a line-for-line port of `ml/src/features`. `ScreenLockFeaturesParityTest`
asserts the Kotlin output against numbers produced by **running the Python extractor** over
the same intervals — not hand arithmetic. The zone is always passed explicitly
(`SpecConstants.PARITY_TIMEZONE` in tests, the device zone in the app), because a parity
test that relies on an ambient default proves nothing.

The full contract test over `fixtures/synthetic_trace.json`, read by both suites, is Phase 6.

## Enforced by tests — do not weaken

`ArchitectureTest` (Konsist) and `SpecVersionTest`:
- `ui` must not import `sensing`;
- raw sensor/provider types confined to `sensing`;
- `inference` may depend on `features`, never the reverse (and `features` imports no Android);
- `sensing` imports neither `ui` nor `data`; Room DAOs stay in `data`;
- no property may be named so as to hold typed/message content;
- Kotlin `SPEC_VERSION` == `docs/feature-spec.md` == `ml/src/features/spec_constants.py`.

## Before you commit

```sh
git config core.hooksPath .githooks
```

## NEEDS-VERIFICATION
Background-execution limits, `foregroundServiceType`, AccessibilityService policy, and the
exact UsageStats/READ_SMS permission requirements must be checked against current official
Android docs before the participant build. The study-build `applicationId` and display name
are still a team TODO (`docs/ethics-and-privacy.md`).
