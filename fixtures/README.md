# fixtures/

Holds the **shared synthetic parity trace** — the contract test for feature parity
between the Android (Kotlin) and Python extractors.

- **Committed on purpose.** `.gitignore` has an explicit un-ignore (`!fixtures/`,
  `!fixtures/**`) so these files are tracked despite the broad data-ignore rules, and the
  pre-commit hook exempts everything under `fixtures/`.
- **JSON, not CSV**, deliberately — so it can never collide with the `*.csv` data-ignore
  rule.
- **Read by BOTH test suites:** Kotlin tests in `app/src/test/` and Python tests over
  `ml/src/features`. Both feed `synthetic_trace.json`'s inputs through their extractor and
  assert the output equals `expected_features`, and that the implemented `SPEC_VERSION`
  matches the trace.

## `synthetic_trace.json`
Synthetic (no real participant data). Structure:
- `spec_version` — the `SPEC_VERSION` from `docs/feature-spec.md` this trace targets.
- `inputs` — synthetic raw-signal records per modality.
- `expected_features` — the feature vector both extractors must produce.

Values are `TODO` until `feature-spec.md` is locked (Wk1–2). **Do not invent numbers** —
fill inputs and expected outputs together when the spec is finalized, and bump
`spec_version` in lockstep.
