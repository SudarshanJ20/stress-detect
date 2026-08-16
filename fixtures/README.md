# fixtures/

Holds the **shared synthetic parity trace** — the contract test for feature parity between
the Android (Kotlin) and Python extractors.

- **Committed on purpose.** `.gitignore` has an explicit un-ignore (`!fixtures/`,
  `!fixtures/**`) so these files are tracked despite the broad data-ignore rules, and the
  pre-commit hook exempts everything under `fixtures/`.
- **JSON, not CSV**, deliberately — so it can never collide with the `*.csv` data-ignore
  rule.
- **Read by BOTH test suites:** `ml/tests/test_parity_fixture.py` and
  `android/app/src/test/java/com/stressdetect/features/FixtureParityTest.kt`. Both feed the
  fixture's inputs through their extractor and assert the output equals `expected_features`
  to **1e-6**, and that the implemented `SPEC_VERSION` matches the trace.

Synthetic throughout — generated arithmetic, no real participant data.

## Regenerating

```sh
cd ml && python tools/build_parity_fixture.py
```

`expected_features` is produced by the **Python** extractor, which makes Python the
reference and Kotlin the side that must match. The Python test re-runs against the
committed file anyway, so regenerating cannot silently bless a Python regression — if the
Python output changes, the diff shows up in this file and must be justified by a spec change
and a `SPEC_VERSION` bump.

## Structure

| key | meaning |
|---|---|
| `spec_version` | the `SPEC_VERSION` this trace targets; must equal `docs/feature-spec.md` |
| `parity_timezone` | the zone BOTH sides must pass **explicitly** (see below) |
| `tolerance` | `1e-6` — the agreement both extractors must reach |
| `feature_names` | canonical column order (backbone then auxiliary) |
| `cases[]` | one block per rule under test |

Each case carries `label_date` (both sides **derive** the 7-day window from it),
`expected_window` (so a window-arithmetic bug is caught precisely rather than as a dozen
confusing feature diffs), `locked_intervals` in epoch seconds plus a
`locked_intervals_local` echo for human review, `calls`/`sms`, the `has_calls`/`has_sms`
availability flags, and `expected_features`.

**The zone is part of the contract, not of the environment.** Both suites pass
`parity_timezone` explicitly and each has a test asserting the run *fails* under the
machine's local zone. A parity test that passed only because the machine happened to be set
to US Eastern would verify nothing.

## What each case exercises

A failure names a rule, not a line number.

| case | rule under test | why it would otherwise slip through |
|---|---|---|
| `demo_week` | *(not an edge case)* the app's **demo mode** replays this | Every other case is a minimal rule test — four intervals, ~0.7 unlocks a day — which is correct for what it pins but nonsense to show someone as an example week. This one is shaped like a real week (nightly sleep with drift, a morning cluster, daytime gaps, evening use, one late night) and is generated and parity-checked like the rest, so the demo can never drift from verified behaviour |
| `midnight_wrap_sleep` | circular statistics; `night_key` = midpoint − 12 h | an arithmetic mean of onset 23:30 and wake 07:15 gives mid-afternoon; a naive night key splits one night across two dates |
| `sleep_band_edge_inside` | `SLEEP_MIDPOINT_BAND` = `[20:00, 12:00)`, wrapping | midpoints land exactly on 20:00 and on 11:59 — both must qualify |
| `sleep_band_edge_outside` | the same bounds one minute the other way | 19:59 and exactly 12:00 must NOT qualify (upper bound exclusive); also exercises the night-band fallback when no sleep is found |
| `min_sleep_duration_edge` | `MIN_SLEEP_MINUTES = 90`, inclusive | 90 min counts, 89 min does not — an off-by-one in `>=` is otherwise invisible |
| `session_cutoff_edges` | `MAX_SESSION_MINUTES = 180`, inclusive | gaps of 179 / 180 / 181 min: the last is a data gap and must be dropped, which moves session count, median, IQR and `screen_on_fraction` together |
| `below_coverage_gate` | `COVERAGE_MIN_DAYS = 3` | features are still computed and cached, but the window must be reported as failing the gate — both sides must agree on the gate, not just the numbers |
| `interval_open_at_window_start` | overlap `end > w0`, coverage clipped to `w0` | the interval counts whole toward sleep, but pre-window dates must not appear in `days_with_data` |
| `interval_open_at_window_end` | unlock requires `end < w1` | catches an implementation that counts every interval end as an unlock |
| `empty_window` | missing-data rule | every feature must be **NaN**, not 0 — a zero reads as "slept 0 hours" instead of "unknown" |
| `dst_spring_forward` | absolute vs calendar window arithmetic (feature-spec §8) | pandas `Timedelta` is absolute, so the window starts 2013-03-03 **23:00**, not midnight; `LocalDate.minusDays` would silently shift it. Also covers a sleep spanning the skipped hour |
| `dst_fall_back` | the same, the other direction | window starts 2013-10-28 **01:00**; the overnight lock spans the repeated hour and is an hour longer in elapsed time than its wall clock suggests |
| `band_edge_tiebreak` | the `BAND_EDGE_EPS` convention itself (feature-spec §9) | unlocks land exactly on **both** band bounds (23:30 low, 07:10 high), so `nighttime_unlock_per_day_personal` = 5/6 pins "low edge → IN, high edge → OUT". A 1-ULP difference flips a boolean, which no tolerance can catch — so this case tests the **rule**, not the tolerance |
| `days_with_data_eight` | the recorded quirk (feature-spec §8) | a 7-day window reports **8** coverage days because an interval crossing `w1` clips to the label day's midnight; it deflates every per-day rate, is mirrored deliberately, and is pinned here so neither side "fixes" it unilaterally |

## `model_reference.json` — the on-device INFERENCE contract

`synthetic_trace.json` proves both extractors compute the same **features**. It says nothing
about whether the app's ONNX Runtime produces the same **prediction** as the PyTorch model
that was trained. `model_reference.json` closes that gap: for each fixture case it records
the raw model inputs, the standardized inputs, and the **PyTorch output**, so the Android
instrumented test (`OnnxParityTest`) can assert its own on-device output matches within
`1e-5`. Without a shared reference, "the app ran the model" would prove only that it
produced *a* number.

Written by `ml/src/training/run_dl.py` at export time; regenerate whenever the model or
`SPEC_VERSION` changes. Committed (small, synthetic, derived from the committed fixture) —
unlike the `.onnx` binary itself, which stays gitignored and is staged into the app by
`android/tools/push_model_assets.sh`.

Both the standardization statistics and `spec_version` are also embedded in the ONNX
metadata, so the app can standardize correctly and refuse to run a mismatched model.

## Out of scope for this fixture

The `queryEvents` → locked-interval **derivation** rules (unmatched locks dropped rather
than clamped, duplicate collapse, shutdown as a data gap) are Android-only — Python reads
already-paired `(start, end)` rows from StudentLife, so there is nothing to compare against.
They are covered by `LockedIntervalDerivationTest` and specified in `docs/feature-spec.md` §8.
