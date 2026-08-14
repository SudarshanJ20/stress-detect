"""Stress EMA → labels.

Two targets, per the approved design (docs/feature-spec.md §"Label decoding — Stress EMA"):
  * PRIMARY (regression): subject-day mean of the REMAPPED ordinal, rescaled to 0–100 where
    100 = most stressed. Uses every subject-day with >=1 valid EMA.
  * SECONDARY (binary): {2,3}=stressed vs {4,5}=not, level-1 dropped; subject-day majority.

The RAW 1–5 scale is non-monotonic and is kept as an UNORDERED Categorical so any attempt
to average/compare it raises (guards.assert_raw_scale_protected).
"""
from __future__ import annotations

import glob
import json
import os
from collections import Counter

import pandas as pd

from features.spec_constants import (
    BINARY_DROP_RAW, BINARY_NOT_RAW, BINARY_STRESSED_RAW,
    ORDINAL_MAX, RAW_TO_ORDINAL, TIMEZONE,
)
from paths import STRESS_EMA_DIR

VALID_RAW = set(RAW_TO_ORDINAL)  # {1,2,3,4,5}


def remap_level(raw: int) -> int:
    """Raw 1–5 → wellbeing ordinal 0–4 (higher = better). The ONLY sanctioned way to turn a
    raw Stress level into a number."""
    if raw not in RAW_TO_ORDINAL:
        raise ValueError(f"raw Stress level {raw!r} not in {sorted(RAW_TO_ORDINAL)}")
    return RAW_TO_ORDINAL[raw]


def load_stress_responses(stress_dir=STRESS_EMA_DIR) -> tuple[pd.DataFrame, dict]:
    """Return (responses_df, drop_stats).

    responses_df columns: subject, resp_time_utc, local_date, raw_level (Categorical), ordinal.
    Only entries with an explicit 'level' in 1..5 are kept; everything else is dropped and
    tallied in drop_stats (spec limitation: 122 bare-digit `null` entries are ambiguous and
    intentionally NOT recovered).
    """
    rows = []
    drop = Counter()
    for fp in sorted(glob.glob(os.path.join(str(stress_dir), "Stress_u*.json"))):
        subject = "u" + os.path.basename(fp).split("_u")[-1].split(".")[0]
        with open(fp) as fh:
            entries = json.load(fh)
        for e in entries:
            if "level" in e and str(e["level"]).strip() in {"1", "2", "3", "4", "5"}:
                raw = int(e["level"])
                rows.append((subject, int(e["resp_time"]), raw, remap_level(raw)))
            elif "null" in e:
                v = str(e["null"])
                drop["coord" if "," in v else f"bare:{v}"] += 1
            else:
                drop["other"] += 1

    df = pd.DataFrame(rows, columns=["subject", "resp_time_utc", "raw_level", "ordinal"])
    ts = pd.to_datetime(df["resp_time_utc"], unit="s", utc=True).dt.tz_convert(TIMEZONE)
    df["local_ts"] = ts
    df["local_date"] = ts.dt.date
    # Raw scale protected: unordered Categorical so .mean()/comparisons raise.
    df["raw_level"] = pd.Categorical(df["raw_level"], categories=[1, 2, 3, 4, 5], ordered=False)
    return df, dict(drop)


def _binary_day_label(raw_series: pd.Series) -> float:
    """Subject-day binary label from that day's responses: majority of {2,3} vs {4,5},
    level 1 dropped. NaN if no {2,3,4,5} responses or an exact tie."""
    raw = [int(x) for x in raw_series if int(x) not in BINARY_DROP_RAW]
    stressed = sum(1 for x in raw if x in BINARY_STRESSED_RAW)
    nots = sum(1 for x in raw if x in BINARY_NOT_RAW)
    if stressed == 0 and nots == 0:
        return float("nan")
    if stressed == nots:
        return float("nan")
    return 1.0 if stressed > nots else 0.0


def build_subject_day_labels(responses: pd.DataFrame) -> pd.DataFrame:
    """One row per (subject, local_date). PRIMARY regression target `stress_score` (0–100,
    100=most stressed) from the remapped ordinal; SECONDARY `binary_label`."""
    out = []
    for (subject, date), g in responses.groupby(["subject", "local_date"], sort=True):
        ordinal_mean = g["ordinal"].mean()  # SAFE: operates on remapped ordinal, not raw
        stress_score = (ORDINAL_MAX - ordinal_mean) / ORDINAL_MAX * 100.0
        binary = _binary_day_label(g["raw_level"])
        n_bin = int(sum(int(x) not in BINARY_DROP_RAW for x in g["raw_level"]))
        out.append({
            "subject": subject,
            "local_date": date,
            "n_responses": len(g),
            "ordinal_mean": ordinal_mean,
            "stress_score": stress_score,     # PRIMARY regression target
            "binary_label": binary,           # SECONDARY (may be NaN)
            "n_binary_responses": n_bin,
        })
    return pd.DataFrame(out)
