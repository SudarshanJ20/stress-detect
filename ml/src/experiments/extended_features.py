"""DIAGNOSTIC ONLY (Experiment 1 ceiling test) — NOT product features.

Daily aggregates for the OUT-OF-SCOPE StudentLife streams that the published StudentLife
analyses found signal in: conversation, activity, audio, gps (mobility), dark. Aggregated
per (subject, local_date) once, then summed over a sample's window dates. These streams are
NOT retrospectively retrievable on-device (docs/dataset-inventory.md §5); this only measures
an achievable-performance ceiling, and must never leak into the product feature set.
"""
from __future__ import annotations

import glob
import os

import numpy as np
import pandas as pd

from features.spec_constants import TIMEZONE
from paths import RAW_ROOT

_SENS = RAW_ROOT / "sensing"


def _subject(fp: str) -> str:
    return "u" + os.path.basename(fp).split("_u")[-1].split(".")[0]


def _read(fp):
    """Some StudentLife sensing CSVs (e.g. gps) have header rows repeated mid-file from a
    concatenated export. Read, strip column names — callers coerce & drop those junk rows."""
    df = pd.read_csv(fp)
    df.columns = df.columns.str.strip()
    return df


def _local_date(ts: pd.Series):
    t = pd.to_numeric(ts, errors="coerce")  # non-numeric (repeated-header) rows → NaN → NaT
    return pd.to_datetime(t, unit="s", utc=True).dt.tz_convert(TIMEZONE).dt.date


def _daily_point(glob_pat, ts_col, val_col):
    """value-count of a categorical inference code per (subject,date). Streamed per file."""
    out = {}
    for fp in sorted(glob.glob(str(_SENS / glob_pat))):
        df = _read(fp)
        s = _subject(fp)
        df["date"] = _local_date(df[ts_col])
        df["code"] = pd.to_numeric(df[val_col], errors="coerce")
        df = df.dropna(subset=["date", "code"])
        g = df.groupby(["date", df["code"].astype(int)]).size()
        for (d, code), n in g.items():
            out.setdefault((s, d), {})[int(code)] = out.get((s, d), {}).get(int(code), 0) + int(n)
    return out


def activity_daily():
    return _daily_point("activity/activity_u*.csv", "timestamp", "activity inference")


def audio_daily():
    return _daily_point("audio/audio_u*.csv", "timestamp", "audio inference")


def _interval_daily(glob_pat, start_col, end_col):
    out = {}
    for fp in sorted(glob.glob(str(_SENS / glob_pat))):
        df = _read(fp)
        s = _subject(fp)
        st = pd.to_numeric(df[start_col], errors="coerce")
        en = pd.to_numeric(df[end_col], errors="coerce")
        df = df.assign(date=_local_date(st), dur=en - st).dropna(subset=["date", "dur"])
        for d, g in df.groupby("date"):
            out[(s, d)] = (float(g["dur"].sum()), int(len(g)))
    return out


def conversation_daily():
    return _interval_daily("conversation/conversation_u*.csv", "start_timestamp", "end_timestamp")


def dark_daily():
    return _interval_daily("dark/dark_u*.csv", "start", "end")


def gps_daily():
    out = {}
    for fp in sorted(glob.glob(str(_SENS / "gps/gps_u*.csv"))):
        df = _read(fp)
        s = _subject(fp)
        df["date"] = _local_date(df["time"])
        df["lat"] = pd.to_numeric(df["latitude"], errors="coerce")
        df["lon"] = pd.to_numeric(df["longitude"], errors="coerce")
        df = df.dropna(subset=["date", "lat", "lon"])
        for d, g in df.groupby("date"):
            stat = float((g["travelstate"] == "stationary").mean())
            out[(s, d)] = (float(g["lat"].mean()), float(g["lon"].mean()), stat, int(len(g)))
    return out


def precompute_all():
    return {"activity": activity_daily(), "audio": audio_daily(),
            "conversation": conversation_daily(), "dark": dark_daily(), "gps": gps_daily()}


EXTENDED_FEATURES = [
    "activity_still_frac", "activity_active_frac",
    "audio_silence_frac", "audio_voice_frac",
    "conversation_sec_per_day", "conversation_count_per_day",
    "dark_sec_per_day", "gps_location_variance", "gps_stationary_frac",
]


def extended_window_features(pre: dict, subject: str, window_dates: list) -> dict:
    """Aggregate the daily precomputes over the sample's window dates."""
    f = {k: np.nan for k in EXTENDED_FEATURES}
    keys = [(subject, d) for d in window_dates]

    # activity codes: 0 still, 1 walk, 2 run, 3 unknown
    ac = {0: 0, 1: 0, 2: 0}
    for k in keys:
        for code, n in pre["activity"].get(k, {}).items():
            if code in ac:
                ac[code] += n
    tot = sum(ac.values())
    if tot:
        f["activity_still_frac"] = ac[0] / tot
        f["activity_active_frac"] = (ac[1] + ac[2]) / tot

    # audio codes: 0 silence, 1 voice, 2 noise
    au = {0: 0, 1: 0, 2: 0}
    for k in keys:
        for code, n in pre["audio"].get(k, {}).items():
            if code in au:
                au[code] += n
    tota = sum(au.values())
    if tota:
        f["audio_silence_frac"] = au[0] / tota
        f["audio_voice_frac"] = au[1] / tota

    # conversation / dark: per-day rates over days-with-data
    for stream, sec_key, cnt_key in [("conversation", "conversation_sec_per_day", "conversation_count_per_day"),
                                     ("dark", "dark_sec_per_day", None)]:
        vals = [pre[stream].get(k) for k in keys if pre[stream].get(k) is not None]
        if vals:
            f[sec_key] = float(np.mean([v[0] for v in vals]))
            if cnt_key:
                f[cnt_key] = float(np.mean([v[1] for v in vals]))

    # gps: location variance = spread of daily-mean positions across the window
    gv = [pre["gps"].get(k) for k in keys if pre["gps"].get(k) is not None]
    if gv:
        lats = np.array([v[0] for v in gv]); lons = np.array([v[1] for v in gv])
        stat = np.array([v[2] for v in gv])
        f["gps_location_variance"] = float(np.log(np.var(lats) + np.var(lons) + 1e-12))
        f["gps_stationary_frac"] = float(np.nanmean(stat))
    return f
