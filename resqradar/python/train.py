"""
VitalFi — Training Loop

Multi-task training with combined loss:
  L = BCE(detection) + λ · MSE(vitals)  [vitals loss only when person present]
"""

import os
import time
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, random_split

from model import VitalFiModel
from dataset import VitalSignsDataset
from simulator import generate_dataset


def train_one_epoch(
    model: nn.Module,
    loader: DataLoader,
    optimizer: torch.optim.Optimizer,
    device: torch.device,
    vitals_weight: float = 0.5,
) -> dict:
    """Train for one epoch.

    Returns:
        Dictionary with average losses and metrics.
    """
    model.train()
    total_loss = 0.0
    total_det_loss = 0.0
    total_vit_loss = 0.0
    correct = 0
    total = 0

    bce_loss = nn.BCEWithLogitsLoss()
    mse_loss = nn.MSELoss()

    for x, presence, vitals in loader:
        x = x.to(device)
        presence = presence.to(device)
        vitals = vitals.to(device)

        optimizer.zero_grad()

        pred_presence, pred_vitals = model(x)

        # Detection loss (always)
        det_loss = bce_loss(pred_presence, presence)

        # Vitals loss (only for samples where person is present)
        mask = presence.squeeze() > 0.5
        if mask.any():
            vit_loss = mse_loss(pred_vitals[mask], vitals[mask])
        else:
            vit_loss = torch.tensor(0.0, device=device)

        # Combined loss
        loss = det_loss + vitals_weight * vit_loss

        loss.backward()
        optimizer.step()

        total_loss += loss.item() * x.size(0)
        total_det_loss += det_loss.item() * x.size(0)
        total_vit_loss += vit_loss.item() * x.size(0)

        # Accuracy
        preds = (torch.sigmoid(pred_presence) > 0.5).float()
        correct += (preds == presence).sum().item()
        total += x.size(0)

    n = len(loader.dataset)
    return {
        "loss": total_loss / n,
        "det_loss": total_det_loss / n,
        "vit_loss": total_vit_loss / n,
        "accuracy": correct / total,
    }


@torch.no_grad()
def evaluate(
    model: nn.Module,
    loader: DataLoader,
    device: torch.device,
    vitals_weight: float = 0.5,
) -> dict:
    """Evaluate model on a dataset.

    Returns:
        Dictionary with losses, accuracy, and vital signs MAE.
    """
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0

    bce_loss = nn.BCEWithLogitsLoss()
    mse_loss = nn.MSELoss()

    all_resp_errors = []
    all_heart_errors = []

    for x, presence, vitals in loader:
        x = x.to(device)
        presence = presence.to(device)
        vitals = vitals.to(device)

        pred_presence, pred_vitals = model(x)

        det_loss = bce_loss(pred_presence, presence)
        mask = presence.squeeze() > 0.5
        if mask.any():
            vit_loss = mse_loss(pred_vitals[mask], vitals[mask])
            # Per-sample errors for MAE
            errors = (pred_vitals[mask] - vitals[mask]).abs().cpu().numpy()
            all_resp_errors.extend(errors[:, 0])
            all_heart_errors.extend(errors[:, 1])
        else:
            vit_loss = torch.tensor(0.0, device=device)

        loss = det_loss + vitals_weight * vit_loss
        total_loss += loss.item() * x.size(0)

        preds = (torch.sigmoid(pred_presence) > 0.5).float()
        correct += (preds == presence).sum().item()
        total += x.size(0)

    n = len(loader.dataset)
    results = {
        "loss": total_loss / n,
        "accuracy": correct / total,
    }

    if all_resp_errors:
        results["resp_mae"] = float(np.mean(all_resp_errors))
        results["heart_mae"] = float(np.mean(all_heart_errors))
    else:
        results["resp_mae"] = float("nan")
        results["heart_mae"] = float("nan")

    return results


def train(
    encoder_type: str = "bilstm",
    num_samples: int = 500,
    epochs: int = 50,
    batch_size: int = 16,
    learning_rate: float = 1e-4,
    vitals_weight: float = 0.5,
    pca_components: int = 5,
    duration_sec: float = 10.0,
    fs: float = 100.0,
    patience: int = 15,
    save_dir: str = "checkpoints",
):
    """Full training pipeline with synthetic data.

    Args:
        encoder_type: "lstm", "bilstm", or "transformer".
        num_samples: Number of synthetic samples to generate.
        epochs: Maximum training epochs.
        batch_size: Batch size.
        learning_rate: Initial learning rate.
        vitals_weight: Weight for vitals loss (λ).
        pca_components: Number of PCA components.
        duration_sec: Duration of each sample in seconds.
        fs: Sampling frequency.
        patience: Early stopping patience.
        save_dir: Directory to save checkpoints.
    """
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Device: {device}")
    print(f"Encoder: {encoder_type}")

    # Generate synthetic data
    print(f"\nGenerating {num_samples} synthetic samples...")
    t0 = time.time()
    samples = generate_dataset(
        num_samples=num_samples,
        duration_sec=duration_sec,
        fs=fs,
    )
    print(f"Generated in {time.time() - t0:.1f}s")

    # Create dataset
    seq_length = int(duration_sec * fs)
    input_features = 3 * pca_components  # raw PCA + respiratory + cardiac

    full_dataset = VitalSignsDataset(
        samples, fs=fs, pca_components=pca_components, seq_length=seq_length,
    )

    # Split: 80% train, 20% validation
    train_size = int(0.8 * len(full_dataset))
    val_size = len(full_dataset) - train_size
    train_dataset, val_dataset = random_split(full_dataset, [train_size, val_size])

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True, num_workers=0)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False, num_workers=0)

    print(f"Train: {train_size}, Val: {val_size}")

    # Model
    model = VitalFiModel(
        input_features=input_features,
        encoder_type=encoder_type,
    ).to(device)

    param_count = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"Model parameters: {param_count:,}")

    # Optimizer & scheduler
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate, weight_decay=1e-5)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, mode="min", factor=0.5, patience=5, verbose=True,
    )

    # Training loop
    os.makedirs(save_dir, exist_ok=True)
    best_val_loss = float("inf")
    epochs_no_improve = 0

    print(f"\n{'Epoch':>5} | {'Train Loss':>10} | {'Train Acc':>9} | {'Val Loss':>8} | {'Val Acc':>7} | {'Resp MAE':>8} | {'HR MAE':>6}")
    print("-" * 75)

    for epoch in range(1, epochs + 1):
        train_metrics = train_one_epoch(model, train_loader, optimizer, device, vitals_weight)
        val_metrics = evaluate(model, val_loader, device, vitals_weight)

        scheduler.step(val_metrics["loss"])

        print(
            f"{epoch:5d} | {train_metrics['loss']:10.4f} | {train_metrics['accuracy']:8.1%} | "
            f"{val_metrics['loss']:8.4f} | {val_metrics['accuracy']:6.1%} | "
            f"{val_metrics['resp_mae']:7.2f} | {val_metrics['heart_mae']:6.2f}"
        )

        # Early stopping & checkpointing
        if val_metrics["loss"] < best_val_loss:
            best_val_loss = val_metrics["loss"]
            epochs_no_improve = 0
            checkpoint_path = os.path.join(save_dir, "best_model.pt")
            torch.save({
                "epoch": epoch,
                "model_state_dict": model.state_dict(),
                "optimizer_state_dict": optimizer.state_dict(),
                "val_loss": best_val_loss,
                "val_accuracy": val_metrics["accuracy"],
                "encoder_type": encoder_type,
                "input_features": input_features,
            }, checkpoint_path)
        else:
            epochs_no_improve += 1
            if epochs_no_improve >= patience:
                print(f"\nEarly stopping at epoch {epoch} (no improvement for {patience} epochs)")
                break

    print(f"\nBest validation loss: {best_val_loss:.4f}")
    print(f"Checkpoint saved to: {os.path.abspath(os.path.join(save_dir, 'best_model.pt'))}")

    return model


if __name__ == "__main__":
    train(encoder_type="bilstm", num_samples=200, epochs=30, batch_size=8)
