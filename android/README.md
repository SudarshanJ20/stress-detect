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
./gradlew :app:testDebugUnitTest         # 48 JVM tests: fixture parity, architecture, spec version
./gradlew :app:assembleDebug

# on-device inference parity (needs a device/emulator + an exported model):
ml/.venv/bin/python ml/src/training/run_dl.py   # exports the .onnx + model_reference.json
./tools/push_model_assets.sh                    # stages them into app assets (gitignored)
./gradlew :app:connectedDebugAndroidTest        # ONNX Runtime Mobile vs PyTorch, 1e-5
```

## Setting up an emulator for the on-device test

The instrumented test needs a real device or emulator, and **none of this is in the repo** —
the SDK, the system image and the AVD are machine-local. A fresh machine with only Android
Studio's SDK installed has no `sdkmanager` and no system image, so start here.

```sh
SDK="$HOME/Library/Android/sdk"          # macOS default; adjust for your platform

# 1. command-line tools (sdkmanager / avdmanager) — not installed by Android Studio
curl -sSLo /tmp/cmdline-tools.zip \
  https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip
unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extract
mkdir -p "$SDK/cmdline-tools"
mv /tmp/cmdline-tools-extract/cmdline-tools "$SDK/cmdline-tools/latest"

# 2. licenses + a system image (~1.5 GB)
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
  "system-images;android-35;google_apis;arm64-v8a" "platforms;android-35"

# 3. create the AVD
echo "no" | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n parity6 \
  -k "system-images;android-35;google_apis;arm64-v8a" --force

# 4. boot it headless, then wait for the device
"$SDK/emulator/emulator" -avd parity6 -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect &
"$SDK/platform-tools/adb" wait-for-device
```

Notes:
- Use the **arm64-v8a** image on Apple Silicon; `x86_64` runs under emulation and is far
  slower. On an Intel host, swap the ABI.
- The URL above pins a specific cmdline-tools build; check
  <https://developer.android.com/studio#command-line-tools-only> for the current one.
- `-no-window` keeps it headless, which is what CI or a remote session needs. Drop it if you
  want to watch the app.
- The on-device test reports its margin to logcat:
  `adb logcat -d -s OnnxParityTest` → `max|diff| = 7.62939453125E-6 (tolerance 1.0E-5)`.
- Verified on API 35 (`parity6`), arm64-v8a, macOS. The app's `minSdk` is 29, so any image
  from API 29 up will do.

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

`features/` is a line-for-line port of `ml/src/features`. `FixtureParityTest` runs the
shared contract in `fixtures/synthetic_trace.json` — 13 cases, each targeting one named
spec rule — against the Python extractor's own output, to `1e-6`. The zone is always passed
explicitly (`SpecConstants.PARITY_TIMEZONE` in tests, the device zone in the app), and a
test asserts the run *fails* under the machine's local zone: a parity test that relies on
an ambient default proves nothing.

That fixture earned its keep on first run, catching a bug no unit test could have found —
the JVM and numpy differ by ~1 ULP in `cos`/`sin`/`atan2`, which flipped a night-band
boundary decision. See `docs/feature-spec.md` §9 and `BAND_EDGE_EPS`.

## Inference parity

`OnnxParityTest` is an **instrumented** test, not a JVM one: a JVM test would exercise the
desktop ONNX Runtime rather than the Mobile kernels that ship in the APK, and the question
is whether what runs *on the phone* agrees with what was trained. It feeds the fixture
inputs through `OnnxStressModel` and asserts the output matches PyTorch within `1e-5`.

`OnnxStressModel` refuses to run a model whose `spec_version` metadata disagrees with
`SpecConstants.SPEC_VERSION`, and standardizes inputs using the scaler embedded in the
model's own metadata — scoring raw features against a model trained on z-scores would be a
silent wrong answer, not a crash.

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
