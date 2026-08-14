"""Leave-one-subject-out cross-validation. NEVER splits within a subject — the whole
subject is the test fold. Leakage and duplicate-vector tripwires fire on every fold.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from guards import assert_no_duplicate_feature_vectors, assert_no_subject_leakage
from models.xgboost_baseline import make_classifier, make_regressor


def _folds(df: pd.DataFrame):
    for s in sorted(df["subject"].unique()):
        yield s, df[df["subject"] != s], df[df["subject"] == s]


def loso_regression(df: pd.DataFrame, feature_cols: list[str], target: str) -> pd.DataFrame:
    preds = []
    for s, train, test in _folds(df):
        assert_no_subject_leakage(train["subject"], test["subject"])
        assert_no_duplicate_feature_vectors(test[feature_cols], f"reg test fold {s}")
        model = make_regressor()
        model.fit(train[feature_cols].to_numpy(float), train[target].to_numpy(float))
        yp = model.predict(test[feature_cols].to_numpy(float))
        preds.append(pd.DataFrame({
            "subject": test["subject"].to_numpy(),
            "local_date": test["local_date"].to_numpy(),
            "y_true": test[target].to_numpy(float),
            "y_pred": np.clip(yp, 0, 100),
        }))
    return pd.concat(preds, ignore_index=True)


def loso_classification(df: pd.DataFrame, feature_cols: list[str], target: str) -> pd.DataFrame:
    preds = []
    for s, train, test in _folds(df):
        if train[target].nunique() < 2:
            continue
        assert_no_subject_leakage(train["subject"], test["subject"])
        assert_no_duplicate_feature_vectors(test[feature_cols], f"clf test fold {s}")
        ytr = train[target].to_numpy(int)
        model = make_classifier(ytr)
        model.fit(train[feature_cols].to_numpy(float), ytr)
        prob = model.predict_proba(test[feature_cols].to_numpy(float))[:, 1]
        preds.append(pd.DataFrame({
            "subject": test["subject"].to_numpy(),
            "y_true": test[target].to_numpy(int),
            "y_prob": prob,
        }))
    return pd.concat(preds, ignore_index=True)
