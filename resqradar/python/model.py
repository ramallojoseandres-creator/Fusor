"""
VitalFi — Deep Neural Network for Vital Signs Detection

Multi-task model:
- Task 1: Binary detection (person present / not present)
- Task 2: Vital signs regression (respiratory rate, heart rate)

Architecture: Conv1D spatial encoder + BiLSTM temporal encoder + dual heads
"""

import math
import torch
import torch.nn as nn
import torch.nn.functional as F


class Conv1DEncoder(nn.Module):
    """Spatial encoder: reduces feature dimension with 1D convolutions.

    Processes each time step independently to extract spatial patterns
    across subcarrier features.
    """

    def __init__(self, in_channels: int, hidden_channels: int = 64):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv1d(in_channels, hidden_channels, kernel_size=1),
            nn.BatchNorm1d(hidden_channels),
            nn.ReLU(),
            nn.Conv1d(hidden_channels, hidden_channels, kernel_size=1),
            nn.BatchNorm1d(hidden_channels),
            nn.ReLU(),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: (batch, seq_len, in_channels)
        Returns:
            (batch, seq_len, hidden_channels)
        """
        # Conv1d expects (batch, channels, seq_len)
        x = x.permute(0, 2, 1)
        x = self.net(x)
        return x.permute(0, 2, 1)


class LSTMEncoder(nn.Module):
    """Unidirectional LSTM temporal encoder."""

    def __init__(self, input_size: int, hidden_size: int = 128, num_layers: int = 2, dropout: float = 0.3):
        super().__init__()
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0.0,
        )
        self.output_size = hidden_size

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: (batch, seq_len, input_size)
        Returns:
            (batch, hidden_size)
        """
        _, (ht, _) = self.lstm(x)
        return ht[-1]  # last layer, last time step


class BiLSTMEncoder(nn.Module):
    """Bidirectional LSTM temporal encoder."""

    def __init__(self, input_size: int, hidden_size: int = 128, num_layers: int = 2, dropout: float = 0.3):
        super().__init__()
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0.0,
            bidirectional=True,
        )
        self.output_size = hidden_size * 2  # forward + backward

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: (batch, seq_len, input_size)
        Returns:
            (batch, hidden_size * 2)
        """
        _, (ht, _) = self.lstm(x)
        # ht shape: (num_layers * 2, batch, hidden_size)
        # Concatenate last forward and last backward
        forward_last = ht[-2]
        backward_last = ht[-1]
        return torch.cat([forward_last, backward_last], dim=1)


class PositionalEncoding(nn.Module):
    """Standard sinusoidal positional encoding for Transformer."""

    def __init__(self, d_model: int, max_len: int = 5000, dropout: float = 0.1):
        super().__init__()
        self.dropout = nn.Dropout(p=dropout)

        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model))
        pe[:, 0::2] = torch.sin(position * div_term)
        if d_model > 1:
            pe[:, 1::2] = torch.cos(position * div_term[:d_model // 2])
        pe = pe.unsqueeze(0)
        self.register_buffer("pe", pe)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x + self.pe[:, :x.size(1)]
        return self.dropout(x)


class TransformerTemporalEncoder(nn.Module):
    """Transformer-based temporal encoder."""

    def __init__(
        self,
        input_size: int,
        d_model: int = 64,
        nhead: int = 4,
        num_layers: int = 1,
        dropout: float = 0.1,
    ):
        super().__init__()
        self.input_proj = nn.Linear(input_size, d_model)
        self.pos_encoder = PositionalEncoding(d_model, dropout=dropout)
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=nhead,
            dim_feedforward=d_model * 4,
            dropout=dropout,
            batch_first=True,
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.output_size = d_model

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: (batch, seq_len, input_size)
        Returns:
            (batch, d_model) — mean-pooled over sequence
        """
        x = self.input_proj(x)
        x = self.pos_encoder(x)
        x = self.transformer(x)
        return x.mean(dim=1)  # global average pooling


class VitalFiModel(nn.Module):
    """Multi-task model for vital signs detection.

    Architecture:
        CSI input → Conv1D spatial encoder → Temporal encoder → dual heads
        - Detection head: person present (binary)
        - Vitals head: respiratory rate + heart rate (regression)
    """

    def __init__(
        self,
        input_features: int = 15,    # 3 * pca_components (raw + resp + cardiac)
        spatial_hidden: int = 64,
        encoder_type: str = "bilstm",  # "lstm", "bilstm", "transformer"
        temporal_hidden: int = 128,
        temporal_layers: int = 2,
        dropout: float = 0.3,
    ):
        super().__init__()

        # Spatial encoder
        self.spatial_encoder = Conv1DEncoder(input_features, spatial_hidden)

        # Temporal encoder
        if encoder_type == "lstm":
            self.temporal_encoder = LSTMEncoder(spatial_hidden, temporal_hidden, temporal_layers, dropout)
        elif encoder_type == "bilstm":
            self.temporal_encoder = BiLSTMEncoder(spatial_hidden, temporal_hidden, temporal_layers, dropout)
        elif encoder_type == "transformer":
            self.temporal_encoder = TransformerTemporalEncoder(
                spatial_hidden, d_model=spatial_hidden, nhead=4, num_layers=temporal_layers, dropout=dropout,
            )
        else:
            raise ValueError(f"Unknown encoder type: {encoder_type}")

        enc_out = self.temporal_encoder.output_size

        # Detection head (binary classification)
        self.detection_head = nn.Sequential(
            nn.Linear(enc_out, 64),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(64, 1),
        )

        # Vitals regression head
        self.vitals_head = nn.Sequential(
            nn.Linear(enc_out, 64),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(64, 2),  # (respiratory_rate, heart_rate)
            nn.ReLU(),         # rates are non-negative
        )

    def forward(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        """
        Args:
            x: (batch, seq_len, input_features)

        Returns:
            presence: (batch, 1) — logit for person detection
            vitals: (batch, 2) — (respiratory_rate_bpm, heart_rate_bpm)
        """
        # Spatial encoding
        spatial = self.spatial_encoder(x)

        # Temporal encoding
        temporal = self.temporal_encoder(spatial)

        # Dual heads
        presence = self.detection_head(temporal)
        vitals = self.vitals_head(temporal)

        return presence, vitals
