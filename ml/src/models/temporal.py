"""Phase-4 temporal models over the 7-day daily-feature sequence.

Both are deliberately SMALL + regularized (dropout, tiny hidden dim) for n≈1157 / 48
subjects — capacity to memorize training folds, not enough to fake a generalizing win.

  CNNLSTM        — primary. 1D-CNN (local daily patterns) → LSTM (7-step dynamics) → head.
  TinyTransformer — ablation arm. length-7 self-attention; expected to do worse on this n.

The head concatenates the static (window-level sleep/regularity) vector after the temporal
encoder, so the model sees >= what XGBoost saw. Output = scalar stress score (0–100).
"""
from __future__ import annotations

import torch
import torch.nn as nn


class CNNLSTM(nn.Module):
    def __init__(self, d_dyn: int, d_static: int, hidden: int = 32, dropout: float = 0.3):
        super().__init__()
        self.conv = nn.Sequential(
            nn.Conv1d(d_dyn, 32, kernel_size=3, padding=1), nn.ReLU(),
        )
        self.lstm = nn.LSTM(32, hidden, batch_first=True)
        self.head = nn.Sequential(
            nn.Linear(hidden + d_static, 32), nn.ReLU(), nn.Dropout(dropout), nn.Linear(32, 1),
        )

    def forward(self, x_seq: torch.Tensor, x_static: torch.Tensor) -> torch.Tensor:
        # x_seq (B,7,d_dyn) → conv wants (B,d_dyn,7)
        h = self.conv(x_seq.transpose(1, 2)).transpose(1, 2)   # (B,7,32)
        out, _ = self.lstm(h)                                  # (B,7,hidden)
        last = out[:, -1, :]                                   # last timestep (newest day)
        return self.head(torch.cat([last, x_static], dim=1)).squeeze(-1)


class TinyTransformer(nn.Module):
    def __init__(self, d_dyn: int, d_static: int, d_model: int = 32, nhead: int = 4,
                 dropout: float = 0.3, seq_len: int = 7):
        super().__init__()
        self.proj = nn.Linear(d_dyn, d_model)
        self.pos = nn.Parameter(torch.zeros(1, seq_len, d_model))
        layer = nn.TransformerEncoderLayer(d_model, nhead, dim_feedforward=64,
                                           dropout=dropout, batch_first=True)
        self.enc = nn.TransformerEncoder(layer, num_layers=1)
        self.head = nn.Sequential(
            nn.Linear(d_model + d_static, 32), nn.ReLU(), nn.Dropout(dropout), nn.Linear(32, 1),
        )

    def forward(self, x_seq: torch.Tensor, x_static: torch.Tensor) -> torch.Tensor:
        h = self.proj(x_seq) + self.pos
        h = self.enc(h).mean(dim=1)                            # mean-pool over 7 days
        return self.head(torch.cat([h, x_static], dim=1)).squeeze(-1)


def make_model(name: str, d_dyn: int, d_static: int) -> nn.Module:
    if name == "cnnlstm":
        return CNNLSTM(d_dyn, d_static)
    if name == "transformer":
        return TinyTransformer(d_dyn, d_static)
    raise ValueError(f"unknown model {name!r}")
