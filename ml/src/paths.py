"""Repo-relative paths. `ml/data/**` is gitignored (raw + processed snapshots)."""
from __future__ import annotations

from pathlib import Path

_ML = Path(__file__).resolve().parents[1]          # .../ml
RAW_ROOT = _ML / "data" / "raw" / "studentlife"
PROCESSED_DIR = _ML / "data" / "processed"
REPORTS_DIR = _ML / "reports"                       # metrics json (gitignored via data? see below)

STRESS_EMA_DIR = RAW_ROOT / "EMA" / "response" / "Stress"
PHONELOCK_DIR = RAW_ROOT / "sensing" / "phonelock"
CALLLOG_DIR = RAW_ROOT / "call_log"
SMS_DIR = RAW_ROOT / "sms"
