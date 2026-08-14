"""StudentLife raw → normalized events (Parquet snapshot).

Focus per Phase 2 scope:
  * phonelock  — BACKBONE. `start,end` epoch-second LOCKED intervals (49/49 coverage).
                 An unlock = the END of a locked interval; a use-session = the gap between
                 consecutive locked intervals.
  * call_log   — AUXILIARY. Real call events only (populated CALLS_date, epoch ms).
  * sms        — AUXILIARY. Real message events only (populated MESSAGES_date, epoch ms).

Empty poll-heartbeat rows (no event date) are dropped — that is why call/sms cover far
fewer than 49 subjects (see docs/dataset-inventory.md §4).
"""
from __future__ import annotations

import glob
import os

import pandas as pd

from paths import CALLLOG_DIR, PHONELOCK_DIR, SMS_DIR


def _subject_from(path: str, prefix: str) -> str:
    base = os.path.basename(path)
    return "u" + base.split("_u")[-1].split(".")[0]


def load_phonelock(dir_=PHONELOCK_DIR) -> pd.DataFrame:
    """columns: subject, start_utc, end_utc (epoch seconds; LOCKED intervals)."""
    frames = []
    for fp in sorted(glob.glob(os.path.join(str(dir_), "phonelock_u*.csv"))):
        df = pd.read_csv(fp, usecols=["start", "end"])
        df = df.dropna()
        df["subject"] = _subject_from(fp, "phonelock")
        frames.append(df.rename(columns={"start": "start_utc", "end": "end_utc"}))
    out = pd.concat(frames, ignore_index=True)
    out[["start_utc", "end_utc"]] = out[["start_utc", "end_utc"]].astype("int64")
    out = out[out["end_utc"] > out["start_utc"]]  # drop degenerate/zero-length locks
    return out.sort_values(["subject", "start_utc"]).reset_index(drop=True)


def _load_comm(dir_, glob_pat: str, date_col: str, kind: str) -> pd.DataFrame:
    """Generic call/sms loader keyed on the true event date column (epoch MILLIS)."""
    frames = []
    for fp in sorted(glob.glob(os.path.join(str(dir_), glob_pat))):
        header = pd.read_csv(fp, nrows=0, encoding="utf-8-sig").columns
        if date_col not in header:
            continue  # empty-stream subject: file is just id,device,timestamp (no events)
        df = pd.read_csv(fp, usecols=[date_col], encoding="utf-8-sig", dtype=str)
        ev = pd.to_numeric(df[date_col], errors="coerce").dropna()
        if ev.empty:
            continue  # subject has only empty poll heartbeats → no real events
        frames.append(pd.DataFrame({
            "subject": _subject_from(fp, kind),
            "event_utc": (ev.astype("int64") // 1000),  # ms → s
            "kind": kind,
        }))
    if not frames:
        return pd.DataFrame(columns=["subject", "event_utc", "kind"])
    return pd.concat(frames, ignore_index=True).sort_values(["subject", "event_utc"])


def load_calls(dir_=CALLLOG_DIR) -> pd.DataFrame:
    return _load_comm(dir_, "call_log_u*.csv", "CALLS_date", "call")


def load_sms(dir_=SMS_DIR) -> pd.DataFrame:
    return _load_comm(dir_, "sms_u*.csv", "MESSAGES_date", "sms")


def build_events(save_path=None) -> dict:
    """Load all three streams. Optionally write a unified long-format Parquet snapshot
    (kind, subject, start_utc, end_utc) with point events as start==end."""
    lock = load_phonelock()
    calls = load_calls()
    sms = load_sms()
    if save_path is not None:
        long = pd.concat([
            lock.assign(kind="locked")[["subject", "kind", "start_utc", "end_utc"]],
            calls.rename(columns={"event_utc": "start_utc"}).assign(end_utc=lambda d: d["start_utc"])[
                ["subject", "kind", "start_utc", "end_utc"]],
            sms.rename(columns={"event_utc": "start_utc"}).assign(end_utc=lambda d: d["start_utc"])[
                ["subject", "kind", "start_utc", "end_utc"]],
        ], ignore_index=True)
        long.to_parquet(save_path, index=False)
    return {"phonelock": lock, "calls": calls, "sms": sms}
