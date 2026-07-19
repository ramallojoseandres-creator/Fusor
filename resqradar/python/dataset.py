"""
VitalFi — PyTorch Dataset for Vital Signs Detection

Wraps synthetic or real CSI data into a PyTorch Dataset with
preprocessing applied on-the-fly.
"""

import numpy as np
import torch
from torch.utils.data import Dataset

from csi_processor import preprocess_csi


class VitalSignsDataset(Dataset):
    """PyTorch dataset for vital signs detection from CSI.

    Each sample contains:
    - Preprocessed CSI features (PCA-reduced, filtered)
    - Binary label: person present (1) or not (0)
    - Regression targets: respiratory rate (BPM), heart rate (BPM)
    """

    def __init__(
        self,
        samples: list[dict],
        fs: float = 100.0,
        pca_components: int = 5,
        seq_length: int = 1000,
    ):
        """
        Args:
            samples: List of dicts from simulator.generate_dataset() or real data.
                Each dict must have: 'csi_amplitude', 'person_present',
                'respiratory_rate', 'heart_rate'.
            fs: Sampling frequency.
            pca_components: Number of PCA components for subcarrier reduction.
            seq_length: Fixed sequence length (will pad/truncate).
        """
        self.samples = samples
        self.fs = fs
        self.pca_components = pca_components
        self.seq_length = seq_length

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        sample = self.samples[idx]
        csi_amp = sample["csi_amplitude"]  # (num_subcarriers, num_packets)

        # Preprocess
        processed = preprocess_csi(
            csi_amp,
            fs=self.fs,
            pca_components=self.pca_components,
        )

        # Use PCA components as features: (n_components, num_packets)
        features = processed["pca"]

        # Also include respiratory and cardiac filtered signals
        resp = processed["respiratory"]
        cardiac = processed["cardiac"]

        # Stack all features: (3 * n_components, num_packets)
        combined = np.vstack([features, resp, cardiac])

        # Transpose to (num_packets, num_features) for sequence models
        combined = combined.T

        # Pad or truncate to fixed sequence length
        actual_len = combined.shape[0]
        if actual_len > self.seq_length:
            combined = combined[:self.seq_length]
        elif actual_len < self.seq_length:
            pad = np.zeros((self.seq_length - actual_len, combined.shape[1]))
            combined = np.vstack([combined, pad])

        # Convert to tensors
        x = torch.FloatTensor(combined)

        # Labels
        presence = torch.FloatTensor([1.0 if sample["person_present"] else 0.0])
        vitals = torch.FloatTensor([
            sample["respiratory_rate"],
            sample["heart_rate"],
        ])

        return x, presence, vitals
