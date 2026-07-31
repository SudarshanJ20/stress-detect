# Ethics & Privacy

Academic research prototype — **not a medical device**. It makes **no diagnostic or
clinical claims**. This document states what is collected, what is guaranteed, and the
participant-facing safeguards.

> This repository is **public and permanent**. It contains **no** ethics/IRB application
> numbers, supervisor/examiner names, participant names or contacts, institution-internal
> URLs, or specific exam dates. Such items are referenced generically (e.g. "see the
> ethics application on file") with a `TODO` where a value would otherwise go.

## Informed consent
- Participants give informed consent before any collection begins.
- Consent covers what is sensed, retention, withdrawal, and export of derived features.
- Ethics/IRB approval must be in place before collection. Application reference: TODO
  (**do not paste the number here** — see the ethics application on file).

## What is collected (per modality)
Behavioral, timing-derived signals only. See `docs/feature-spec.md` for the exact,
authoritative feature list per modality (typing, touch, usage, mobility, sleep-proxy,
motion, social, ambient).

## Typing — the hard guarantee
- Typing is captured as **timing only**: inter-key intervals, backspace counts, pause
  distributions.
- Typed characters are **never** stored, logged, or transmitted. The collector is
  designed to be **architecturally incapable** of receiving character content.

## On-device processing
- Raw sensor streams **never leave the device**; they live in on-device Room storage.
- Inference (detection + 12–24h forecast) runs on-device via ONNX Runtime Mobile.
- **No server-side inference and no remote training database.** Only **derived features**
  may be transported off-device (manual export / participant upload) for research.

## User control
- Participants may opt out and withdraw at any time.
- On request, their data is deleted. Retention window: TODO (team).

## Framing (avoid overclaiming)
- The UI shows today's stress relative to the user's **own baseline** — never an absolute
  population percentage and never a clinical diagnosis.

## Naming & demand characteristics
- The participant-facing build should use a **neutral display name** (e.g. "Baseline" or
  "Rhythm"). An app labelled "StressDetect" **primes the very thing being measured** and
  introduces demand characteristics.
- Note: the applicationId **`com.stressdetect` is visible to participants** under
  **Settings → Apps**. Decide a neutral **study-build applicationId** — TODO (team).

## Data governance
- IRB / data-governance details, DPIA, and retention specifics: TODO (team; keep the
  documents off this public repo).
