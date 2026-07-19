"""
VitalFi — Vital Signs Feature Extraction from CSI

Extracts respiratory rate, heart rate, spectral energy, and SNR
from preprocessed CSI signals.
"""

import numpy as np
from scipy.fft import fft, fftfreq
from scipy.signal import find_peaks, stft


def estimate_dominant_frequency(
    signal: np.ndarray,
    fs: float,
    freq_range: tuple[float, float],
) -> tuple[float, float]:
    """Find the dominant frequency within a band using FFT.

    Args:
        signal: 1-D time-domain signal.
        fs: Sampling frequency in Hz.
        freq_range: (low_hz, high_hz) band of interest.

    Returns:
        (dominant_frequency_hz, peak_magnitude)
    """
    n = len(signal)
    spectrum = np.abs(fft(signal))[:n // 2]
    freqs = fftfreq(n, d=1.0 / fs)[:n // 2]

    # Mask to frequency range of interest
    mask = (freqs >= freq_range[0]) & (freqs <= freq_range[1])
    if not np.any(mask):
        return 0.0, 0.0

    masked_spectrum = spectrum[mask]
    masked_freqs = freqs[mask]

    peak_idx = np.argmax(masked_spectrum)
    return float(masked_freqs[peak_idx]), float(masked_spectrum[peak_idx])


def spectral_energy(signal: np.ndarray, fs: float, freq_range: tuple[float, float]) -> float:
    """Compute spectral energy within a frequency band.

    Args:
        signal: 1-D time-domain signal.
        fs: Sampling frequency.
        freq_range: (low_hz, high_hz).

    Returns:
        Total energy in the specified band.
    """
    n = len(signal)
    spectrum = np.abs(fft(signal))[:n // 2] ** 2
    freqs = fftfreq(n, d=1.0 / fs)[:n // 2]

    mask = (freqs >= freq_range[0]) & (freqs <= freq_range[1])
    return float(np.sum(spectrum[mask]))


def signal_snr(signal: np.ndarray, fs: float, vital_band: tuple[float, float]) -> float:
    """Compute signal-to-noise ratio for a vital signs band.

    SNR = energy_in_vital_band / energy_outside_vital_band

    Args:
        signal: 1-D time-domain signal.
        fs: Sampling frequency.
        vital_band: (low_hz, high_hz) of the vital signal.

    Returns:
        SNR ratio (linear, not dB).
    """
    n = len(signal)
    spectrum = np.abs(fft(signal))[:n // 2] ** 2
    freqs = fftfreq(n, d=1.0 / fs)[:n // 2]

    in_band = (freqs >= vital_band[0]) & (freqs <= vital_band[1])
    energy_signal = np.sum(spectrum[in_band])
    energy_noise = np.sum(spectrum[~in_band])

    if energy_noise < 1e-12:
        return float("inf")
    return float(energy_signal / energy_noise)


def temporal_variance(signal: np.ndarray) -> float:
    """Compute temporal variance — a simple presence indicator.

    A person breathing causes periodic fluctuations, increasing variance
    compared to an empty environment.
    """
    return float(np.var(signal))


def compute_spectrogram(
    signal: np.ndarray,
    fs: float,
    window_sec: float = 4.0,
    overlap_ratio: float = 0.75,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Compute Short-Time Fourier Transform spectrogram.

    Args:
        signal: 1-D time-domain signal.
        fs: Sampling frequency.
        window_sec: Window length in seconds.
        overlap_ratio: Overlap between windows.

    Returns:
        (frequencies, times, spectrogram_magnitude)
    """
    nperseg = int(window_sec * fs)
    noverlap = int(nperseg * overlap_ratio)
    f, t, Zxx = stft(signal, fs=fs, nperseg=nperseg, noverlap=noverlap)
    return f, t, np.abs(Zxx)


def extract_vital_features(
    respiratory_signals: np.ndarray,
    cardiac_signals: np.ndarray,
    fs: float = 100.0,
) -> dict:
    """Extract all vital sign features from preprocessed CSI.

    Args:
        respiratory_signals: PCA components filtered to respiratory band,
            shape (n_components, num_packets).
        cardiac_signals: PCA components filtered to cardiac band,
            shape (n_components, num_packets).
        fs: Sampling frequency.

    Returns:
        Dictionary of extracted features.
    """
    resp_band = (0.1, 0.5)
    cardiac_band = (0.8, 2.0)

    # Use first PCA component (strongest signal)
    resp_signal = respiratory_signals[0]
    card_signal = cardiac_signals[0]

    # Dominant frequencies
    resp_freq, resp_mag = estimate_dominant_frequency(resp_signal, fs, resp_band)
    heart_freq, heart_mag = estimate_dominant_frequency(card_signal, fs, cardiac_band)

    # Convert to human-readable units
    respiratory_rate = resp_freq * 60.0   # breaths per minute
    heart_rate = heart_freq * 60.0        # beats per minute

    # Spectral energies
    resp_energy = spectral_energy(resp_signal, fs, resp_band)
    card_energy = spectral_energy(card_signal, fs, cardiac_band)

    # SNR
    resp_snr = signal_snr(resp_signal, fs, resp_band)
    card_snr = signal_snr(card_signal, fs, cardiac_band)

    # Temporal variance (presence indicator)
    resp_variance = temporal_variance(resp_signal)

    # Person detection heuristic: high respiratory energy + variance
    # indicates a person is present
    presence_score = min(resp_snr, 100.0) / 100.0  # normalize to [0, 1]

    return {
        "respiratory_rate_bpm": respiratory_rate,
        "heart_rate_bpm": heart_rate,
        "respiratory_peak_magnitude": resp_mag,
        "cardiac_peak_magnitude": heart_mag,
        "respiratory_energy": resp_energy,
        "cardiac_energy": card_energy,
        "respiratory_snr": resp_snr,
        "cardiac_snr": card_snr,
        "temporal_variance": resp_variance,
        "presence_score": presence_score,
    }
