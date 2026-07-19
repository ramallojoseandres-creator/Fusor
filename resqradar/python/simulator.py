"""
VitalFi — Synthetic CSI Data Simulator

Generates realistic CSI amplitude data with injected respiratory and
cardiac signals, plus environmental noise and rubble attenuation.
Used for training and validating the model without real hardware.
"""

import numpy as np
from typing import Optional


def generate_breathing_signal(
    duration_sec: float,
    fs: float,
    rate_bpm: float,
    amplitude: float = 1.0,
) -> np.ndarray:
    """Generate a sinusoidal breathing signal.

    Args:
        duration_sec: Duration in seconds.
        fs: Sampling frequency in Hz.
        rate_bpm: Breathing rate in breaths per minute.
        amplitude: Peak amplitude of the breathing modulation.

    Returns:
        1-D array of the breathing signal.
    """
    t = np.arange(0, duration_sec, 1.0 / fs)
    freq = rate_bpm / 60.0
    # Breathing is not perfectly sinusoidal — add slight harmonic
    signal = amplitude * (np.sin(2 * np.pi * freq * t) +
                          0.15 * np.sin(2 * np.pi * 2 * freq * t))
    return signal


def generate_heartbeat_signal(
    duration_sec: float,
    fs: float,
    rate_bpm: float,
    amplitude: float = 0.2,
) -> np.ndarray:
    """Generate a heartbeat signal.

    Args:
        duration_sec: Duration in seconds.
        fs: Sampling frequency in Hz.
        rate_bpm: Heart rate in beats per minute.
        amplitude: Peak amplitude (much smaller than breathing).

    Returns:
        1-D array of the heartbeat signal.
    """
    t = np.arange(0, duration_sec, 1.0 / fs)
    freq = rate_bpm / 60.0
    # Heartbeat has sharper peaks — add harmonics
    signal = amplitude * (np.sin(2 * np.pi * freq * t) +
                          0.3 * np.sin(2 * np.pi * 2 * freq * t) +
                          0.1 * np.sin(2 * np.pi * 3 * freq * t))
    return signal


def generate_multipath_noise(
    num_subcarriers: int,
    num_packets: int,
    noise_std: float = 0.5,
) -> np.ndarray:
    """Generate correlated multipath noise across subcarriers.

    Args:
        num_subcarriers: Number of CSI subcarriers.
        num_packets: Number of time samples.
        noise_std: Standard deviation of noise.

    Returns:
        Noise matrix of shape (num_subcarriers, num_packets).
    """
    # Base noise
    noise = np.random.randn(num_subcarriers, num_packets) * noise_std

    # Add correlated low-frequency drift (simulates slow environmental changes)
    t = np.linspace(0, 1, num_packets)
    for i in range(num_subcarriers):
        drift_freq = np.random.uniform(0.01, 0.05)
        drift_amp = np.random.uniform(0.1, 0.3)
        noise[i] += drift_amp * np.sin(2 * np.pi * drift_freq * t * num_packets / 100.0)

    return noise


def generate_synthetic_csi(
    duration_sec: float = 10.0,
    fs: float = 100.0,
    num_subcarriers: int = 342,
    person_present: bool = True,
    respiratory_rate: Optional[float] = None,
    heart_rate: Optional[float] = None,
    rubble_attenuation: float = 0.3,
    noise_level: float = 0.5,
    base_amplitude: float = 42.0,
    rng: Optional[np.random.Generator] = None,
) -> dict:
    """Generate a complete synthetic CSI sample.

    Args:
        duration_sec: Sample duration in seconds.
        fs: Sampling frequency in Hz.
        num_subcarriers: Number of CSI subcarriers (3 antennas × 114 = 342).
        person_present: Whether a person is present (breathing + heartbeat).
        respiratory_rate: Breathing rate in BPM (randomly sampled if None).
        heart_rate: Heart rate in BPM (randomly sampled if None).
        rubble_attenuation: Attenuation factor for rubble (0=full block, 1=no rubble).
        noise_level: Standard deviation of noise.
        base_amplitude: Mean CSI amplitude baseline.
        rng: NumPy random generator for reproducibility.

    Returns:
        Dictionary with:
        - 'csi_amplitude': shape (num_subcarriers, num_packets)
        - 'person_present': bool
        - 'respiratory_rate': float (BPM) or 0
        - 'heart_rate': float (BPM) or 0
        - 'fs': sampling frequency
    """
    if rng is None:
        rng = np.random.default_rng()

    num_packets = int(duration_sec * fs)

    # Base amplitude (different per subcarrier)
    csi = np.ones((num_subcarriers, num_packets)) * base_amplitude
    subcarrier_offsets = rng.uniform(-2.0, 2.0, size=(num_subcarriers, 1))
    csi += subcarrier_offsets

    resp_rate = 0.0
    hr_rate = 0.0

    if person_present:
        # Random vital signs if not specified
        if respiratory_rate is None:
            resp_rate = rng.uniform(12.0, 20.0)
        else:
            resp_rate = respiratory_rate

        if heart_rate is None:
            hr_rate = rng.uniform(60.0, 100.0)
        else:
            hr_rate = heart_rate

        # Generate vital signals
        breathing = generate_breathing_signal(duration_sec, fs, resp_rate, amplitude=1.5)
        heartbeat = generate_heartbeat_signal(duration_sec, fs, hr_rate, amplitude=0.3)

        # Apply rubble attenuation
        breathing *= rubble_attenuation
        heartbeat *= rubble_attenuation

        # Each subcarrier is affected differently (antenna pattern / frequency response)
        for i in range(num_subcarriers):
            # Random sensitivity per subcarrier
            resp_sensitivity = rng.uniform(0.3, 1.0)
            card_sensitivity = rng.uniform(0.1, 0.8)
            # Random phase offset per subcarrier (different path lengths)
            phase_offset = rng.uniform(0, 2 * np.pi)

            t = np.arange(num_packets) / fs
            resp_freq = resp_rate / 60.0
            card_freq = hr_rate / 60.0

            csi[i] += resp_sensitivity * breathing
            csi[i] += card_sensitivity * heartbeat

    # Add multipath noise
    noise = generate_multipath_noise(num_subcarriers, num_packets, noise_level)
    csi += noise

    return {
        "csi_amplitude": csi.astype(np.float32),
        "person_present": person_present,
        "respiratory_rate": resp_rate,
        "heart_rate": hr_rate,
        "fs": fs,
    }


def generate_dataset(
    num_samples: int = 500,
    duration_sec: float = 10.0,
    fs: float = 100.0,
    num_subcarriers: int = 342,
    presence_ratio: float = 0.5,
    rubble_range: tuple[float, float] = (0.1, 0.5),
    noise_range: tuple[float, float] = (0.3, 1.0),
    seed: int = 42,
) -> list[dict]:
    """Generate a complete synthetic dataset.

    Args:
        num_samples: Total number of samples.
        duration_sec: Duration of each sample.
        fs: Sampling frequency.
        num_subcarriers: Number of subcarriers.
        presence_ratio: Fraction of samples with a person present.
        rubble_range: Min/max rubble attenuation.
        noise_range: Min/max noise level.
        seed: Random seed.

    Returns:
        List of sample dictionaries.
    """
    rng = np.random.default_rng(seed)
    dataset = []

    num_present = int(num_samples * presence_ratio)

    for i in range(num_samples):
        person_present = i < num_present
        rubble_atten = rng.uniform(*rubble_range) if person_present else 0.0
        noise_lvl = rng.uniform(*noise_range)

        sample = generate_synthetic_csi(
            duration_sec=duration_sec,
            fs=fs,
            num_subcarriers=num_subcarriers,
            person_present=person_present,
            rubble_attenuation=rubble_atten,
            noise_level=noise_lvl,
            rng=rng,
        )
        dataset.append(sample)

    # Shuffle
    rng.shuffle(dataset)
    return dataset


if __name__ == "__main__":
    # Quick test: generate one sample and print stats
    sample = generate_synthetic_csi(person_present=True, respiratory_rate=16.0, heart_rate=72.0)
    csi = sample["csi_amplitude"]
    print(f"CSI shape: {csi.shape}")
    print(f"Person present: {sample['person_present']}")
    print(f"Respiratory rate: {sample['respiratory_rate']:.1f} BPM")
    print(f"Heart rate: {sample['heart_rate']:.1f} BPM")
    print(f"CSI amplitude range: [{csi.min():.2f}, {csi.max():.2f}]")
    print(f"CSI mean: {csi.mean():.2f}, std: {csi.std():.2f}")
