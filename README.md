# stress-detect

**Deep learning–based early mental stress detection and forecasting from passive
behavioral phone data.**

> ⚠️ This is an academic research prototype, **not a medical device**. It produces
> **no diagnostic or clinical claims**.

An Android app passively collects behavioral signals from the phone, runs on-device
deep-learning inference, and shows the user how today's stress compares to **their own
personal baseline** — never an absolute population percentage. The headline contribution
is **forecasting stress 12–24 hours ahead**, not merely detecting it.

## Repository layout
This is a **two-language monorepo**:

| Path    | What it holds                                                       |
|---------|---------------------------------------------------------------------|
| `android/` | Kotlin / Jetpack Compose app (single Gradle module). See `android/CLAUDE.md`. |
| `ml/`      | Python / PyTorch pipeline: features, training, evaluation. See `ml/CLAUDE.md`. |
| `docs/`    | Source-of-truth documentation (see below).                       |
| `fixtures/`| Shared synthetic parity trace, committed, read by both test suites. |
| `.githooks/` | Pre-commit data guard.                                          |

## Documentation
- **`docs/feature-spec.md`** — the single source of truth for every feature (carries a
  `SPEC_VERSION`). Both feature extractors must match it exactly.
- `docs/architecture.md` — data flow: sensors → Room → features → ONNX → UI.
- `docs/ethics-and-privacy.md` — consent, what is collected, on-device guarantees.
- `docs/roadmap.md` — 16-week plan.

## Setup for collaborators
After cloning, the **first thing** every collaborator must run:

```sh
git config core.hooksPath .githooks
```

`core.hooksPath` is **local git config and does NOT survive a clone.** If you skip this
step, the pre-commit data guard is **inactive for you** and data/model blobs could be
committed. Run it once per clone.

Then:
- **Android:** open `android/` in Android Studio and follow `android/README.md`.
- **ML:** `pip install -r ml/requirements.txt` (pin versions to your Kaggle env).

## Data & Privacy
- **No participant data lives in this repository.** `ml/data/` is gitignored and enforced
  by the pre-commit hook.
- Raw sensor streams **never leave the device** — only derived features are exported.
- Typing is captured as **timing only** (inter-key intervals, backspace counts, pause
  distributions); typed characters are never stored, logged, or transmitted.
- Evaluation uses **leave-one-subject-out** cross-validation.

See `docs/ethics-and-privacy.md` for the full policy.

## Status
Scaffold only — no implementation code yet. See `docs/roadmap.md`.

## License
MIT — see `LICENSE`.
