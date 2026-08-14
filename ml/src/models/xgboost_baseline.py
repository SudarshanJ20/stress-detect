"""XGBoost baselines. Modest depth/regularization — this is a floor, not the final model.
XGBoost handles NaN natively, so missing features (e.g. circadian on <2 days) are fine."""
from __future__ import annotations

import numpy as np
import xgboost as xgb

_COMMON = dict(
    n_estimators=300, max_depth=4, learning_rate=0.05,
    subsample=0.8, colsample_bytree=0.8, reg_lambda=1.0,
    random_state=0, n_jobs=4,
)


def make_regressor() -> xgb.XGBRegressor:
    return xgb.XGBRegressor(objective="reg:squarederror", **_COMMON)


def make_classifier(y_train: np.ndarray) -> xgb.XGBClassifier:
    pos = float((y_train == 1).sum()); neg = float((y_train == 0).sum())
    spw = (neg / pos) if pos > 0 else 1.0
    return xgb.XGBClassifier(
        objective="binary:logistic", eval_metric="logloss",
        scale_pos_weight=spw, **_COMMON,
    )
