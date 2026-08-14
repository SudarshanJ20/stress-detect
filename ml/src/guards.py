"""Loud tripwires. Every one raises rather than warns — a silent violation here corrupts
the whole result. Referenced by the spec's label-decoding and evaluation rules.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from features.spec_constants import TIMEZONE


class SpecViolation(AssertionError):
    """Raised when code violates an authoritative rule in docs/feature-spec.md."""


# ── Stress scale ─────────────────────────────────────────────────────────────────────
def assert_timezone(tz: str) -> None:
    """The zone is load-bearing for every clock feature; assert it is the spec's zone and
    that it actually localizes (catches a missing tzdata install too)."""
    if tz != TIMEZONE:
        raise SpecViolation(f"timezone must be {TIMEZONE!r} (spec), got {tz!r}")
    try:
        pd.Timestamp("2013-04-01 03:00", tz="UTC").tz_convert(tz)
    except Exception as e:  # pragma: no cover - environment issue
        raise SpecViolation(f"cannot localize to {tz!r}: {e}") from e


def assert_raw_scale_protected(series: pd.Series) -> None:
    """The RAW 1–5 Stress scale is non-monotonic and must never be averaged/compared.
    We store it as an unordered pandas Categorical precisely so numeric ops blow up. If
    this column is ever numeric, some code path is treating the raw scale as ordinal."""
    if not isinstance(series.dtype, pd.CategoricalDtype):
        raise SpecViolation(
            "raw Stress level must stay categorical (non-arithmetic); found "
            f"dtype={series.dtype}. The raw 1–5 scale must never be averaged/compared — "
            "use the remapped ordinal (features.labels.remap_level) instead."
        )
    if getattr(series.dtype, "ordered", False):
        raise SpecViolation("raw Stress level Categorical must be UNORDERED (no comparisons).")


def forbid_raw_ordinal_op(*_a, **_k):  # pragma: no cover - it exists to be un-callable
    raise SpecViolation(
        "arithmetic/ordinal operation attempted on the RAW Stress scale — forbidden by spec. "
        "Remap first (3→0,2→1,1→2,4→3,5→4)."
    )


# ── Evaluation integrity ─────────────────────────────────────────────────────────────
def assert_no_subject_leakage(train_subjects, test_subjects) -> None:
    overlap = set(train_subjects) & set(test_subjects)
    if overlap:
        raise SpecViolation(f"LOSO leakage: subjects in both train and test: {sorted(overlap)}")


def assert_no_duplicate_feature_vectors(X: pd.DataFrame, context: str) -> None:
    """Cheap tripwire for the temporal-leak / accidental-duplication failure mode: no two
    samples in the same fold may share an identical feature vector."""
    dup = X.duplicated(keep=False)
    if dup.any():
        n = int(dup.sum())
        example = X[dup].head(2).to_dict("records")
        raise SpecViolation(
            f"[{context}] {n} rows share an identical feature vector with another row — "
            f"likely duplicated/leaked samples. Example pair: {example}"
        )


__all__ = [
    "SpecViolation", "assert_timezone", "assert_raw_scale_protected",
    "forbid_raw_ordinal_op", "assert_no_subject_leakage",
    "assert_no_duplicate_feature_vectors",
]
