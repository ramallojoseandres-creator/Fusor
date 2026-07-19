"""
VitalFi — Detección adaptativa de respiración/actividad en señal Wi-Fi gruesa.
Optimizado para señales débiles bajo escombros.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from csi_processor import hampel_filter, respiratory_filter
from vital_features import estimate_dominant_frequency, signal_snr, temporal_variance


@dataclass
class DetectionResult:
    is_breathing: bool
    is_activity: bool
    resp_freq: float
    resp_rate: float
    var: float
    snr: float
    noise_floor: float
    unique_values: int
    signal_flat: bool
    status: str
    hint: str
    confidence: float = 0.0
    periodicity: float = 0.0
    spectral_energy: float = 0.0


def _preprocess_signal(x: np.ndarray, fs: float, rubble_mode: bool) -> np.ndarray:
    """Enfatiza micro-variaciones útiles para respiración a través de escombros."""
    x = x - np.mean(x)
    d1 = np.diff(x, prepend=x[0])
    d1 = d1 - np.mean(d1)

    if not rubble_mode:
        return 0.65 * x + 0.35 * d1

    d2 = np.diff(d1, prepend=d1[0])
    d2 = d2 - np.mean(d2)

    # Quitar deriva lenta (paredes, temperatura, multipath estático)
    win = max(5, int(fs * 15))
    if len(x) >= win:
        kernel = np.ones(win, dtype=np.float64) / win
        trend = np.convolve(x, kernel, mode="same")
        x = x - trend

    return 0.45 * x + 0.35 * d1 + 0.20 * d2


def _respiratory_periodicity(filtered: np.ndarray, fs: float) -> float:
    """Autocorrelación en banda respiratoria (2–12 s de período)."""
    if len(filtered) < max(30, int(fs * 12)):
        return 0.0

    centered = filtered - np.mean(filtered)
    ac = np.correlate(centered, centered, mode="full")
    ac = ac[len(ac) // 2 :]
    if ac[0] <= 1e-12:
        return 0.0
    ac = ac / ac[0]

    lag_min = max(1, int(fs / 0.55))   # ~1.8 s máx freq
    lag_max = min(len(ac) - 1, int(fs / 0.06))  # ~16 s mín freq
    if lag_max <= lag_min:
        return 0.0

    segment = ac[lag_min:lag_max]
    return float(np.max(segment)) if len(segment) else 0.0


def _spectral_resp_energy(filtered: np.ndarray, fs: float, band: tuple[float, float]) -> float:
    n = len(filtered)
    if n < 10:
        return 0.0
    spectrum = np.abs(np.fft.fft(filtered))[: n // 2] ** 2
    freqs = np.fft.fftfreq(n, d=1.0 / fs)[: n // 2]
    mask = (freqs >= band[0]) & (freqs <= band[1])
    if not np.any(mask):
        return 0.0
    return float(np.sum(spectrum[mask]) / max(np.sum(spectrum), 1e-12))


def _compute_confidence(
    is_breathing: bool,
    is_activity: bool,
    var: float,
    snr: float,
    periodicity: float,
    spectral_energy: float,
    rubble_mode: bool,
) -> float:
    score = 0.0
    if is_breathing:
        score += 0.55
    if is_activity:
        score += 0.15
    score += min(0.25, var * 8.0)
    score += min(0.20, snr * 0.08)
    score += min(0.30, periodicity * 0.6)
    score += min(0.15, spectral_energy * 2.0)
    if rubble_mode and periodicity > 0.22 and (is_activity or var > 0.0001):
        score += 0.12
    return float(min(1.0, score))


@dataclass
class RubbleConfidenceTracker:
    """Acumula evidencia temporal — vital para señales débiles bajo escombros."""

    score: float = 0.0
    decay: float = 0.985
    hits: int = 0
    calibrating: bool = True
    calib_seconds: float = 45.0
    _start_time: float = field(default=0.0, repr=False)

    def begin(self, now: float) -> None:
        self._start_time = now
        self.calibrating = True
        self.score = 0.0
        self.hits = 0

    def update(self, detection: DetectionResult, now: float) -> float:
        if self._start_time <= 0:
            self.begin(now)

        elapsed = now - self._start_time
        if self.calibrating and elapsed >= self.calib_seconds:
            self.calibrating = False

        if self.calibrating:
            self.score = max(0.0, self.score * 0.99)
            return self.score

        boost = 0.08 + detection.confidence * 0.18
        if detection.is_breathing:
            self.score = min(1.0, self.score * self.decay + boost * 1.8)
            self.hits += 1
        elif detection.periodicity > 0.28 and detection.is_activity:
            self.score = min(1.0, self.score * self.decay + boost * 1.1)
            self.hits += 1
        elif detection.periodicity > 0.32:
            self.score = min(1.0, self.score * self.decay + boost * 0.7)
        elif detection.is_activity:
            self.score = min(1.0, self.score * self.decay + boost * 0.4)
        else:
            self.score *= self.decay

        return self.score

    def likely_present(self, min_score: float = 0.38) -> bool:
        return not self.calibrating and self.score >= min_score

    def reset(self, now: float) -> None:
        self.begin(now)


def analyze_vital_signal(
    signal: np.ndarray,
    fs: float,
    sensitivity: float = 1.0,
    rubble_mode: bool = False,
) -> DetectionResult:
    """
    Analiza señal Wi-Fi (calidad 0-255 o RSSI) buscando respiración o actividad.

    sensitivity: 1.0 = normal, 3.0 = máximo para escombros.
    rubble_mode: umbrales más bajos y preprocesado reforzado.
    """
    if len(signal) < 30:
        return DetectionResult(
            is_breathing=False,
            is_activity=False,
            resp_freq=0.0,
            resp_rate=0.0,
            var=0.0,
            snr=0.0,
            noise_floor=0.0,
            unique_values=len(np.unique(signal)),
            signal_flat=True,
            status="Recolectando muestras...",
            hint="Espera al menos 10 segundos de datos.",
        )

    x = np.asarray(signal, dtype=np.float64)
    unique_values = len(np.unique(np.round(x, 2)))
    raw_std = float(np.std(x))
    signal_flat = unique_values <= 1 or raw_std < (1e-5 if rubble_mode else 1e-4)

    combined = _preprocess_signal(x, fs, rubble_mode)

    calib_len = min(len(combined), max(20, int(fs * (12 if rubble_mode else 8))))
    noise_floor = float(np.std(combined[:calib_len])) or 1e-6

    cleaned = hampel_filter(combined, window_size=5 if rubble_mode else 3, threshold=3.5 if rubble_mode else 2.5)
    detrended = cleaned - np.mean(cleaned)

    resp_band = (0.06, 0.65) if rubble_mode else (0.08, 0.55)
    try:
        filt_low = 0.06 if rubble_mode else 0.1
        filtered = respiratory_filter(detrended, fs=fs)
        # Re-filtrar con banda más amplia en escombros
        if rubble_mode and fs >= 4:
            from csi_processor import bandpass_filter
            filtered = bandpass_filter(detrended, filt_low, 0.65, fs)
    except Exception:
        filtered = detrended

    resp_freq, _peak_mag = estimate_dominant_frequency(filtered, fs, resp_band)
    resp_rate = resp_freq * 60.0
    var = temporal_variance(filtered)
    snr = signal_snr(filtered, fs, resp_band)
    periodicity = _respiratory_periodicity(filtered, fs)
    spec_energy = _spectral_resp_energy(filtered, fs, resp_band)

    sens = max(0.5, sensitivity)
    var_threshold = max(0.00015 if rubble_mode else 0.0003, (noise_floor * 1.2) ** 2) / sens
    snr_threshold = max(0.18 if rubble_mode else 0.35, 0.8 / sens)
    activity_threshold = max(0.08 if rubble_mode else 0.15, noise_floor * 1.5) / sens
    periodicity_threshold = 0.22 if rubble_mode else 0.35

    is_activity = (
        raw_std > activity_threshold
        or (unique_values >= 2 if rubble_mode else unique_values >= 3)
        or var > var_threshold * 0.4
        or periodicity > periodicity_threshold * 0.8
    )

    classic_breathing = (
        var > var_threshold
        and snr > snr_threshold
        and resp_freq >= resp_band[0]
        and (is_activity or unique_values >= 2)
    )

    periodic_breathing = (
        rubble_mode
        and periodicity > periodicity_threshold
        and resp_freq >= resp_band[0]
        and (var > var_threshold * 0.35 or spec_energy > 0.02)
    )

    weak_breathing = (
        rubble_mode
        and is_activity
        and periodicity > periodicity_threshold * 0.85
        and spec_energy > 0.015
    )

    is_breathing = classic_breathing or periodic_breathing or weak_breathing

    confidence = _compute_confidence(
        is_breathing, is_activity, var, snr, periodicity, spec_energy, rubble_mode
    )

    if is_breathing:
        status = "Respiración detectada"
        hint = (
            "Patrón respiratorio bajo escombros. Mantén el entorno quieto."
            if rubble_mode
            else "Gira el laptop para triangular la posición."
        )
    elif is_activity:
        status = "Actividad / posible movimiento"
        hint = (
            "Hay variación periódica débil. Sigue escaneando 30–60 s más."
            if rubble_mode
            else "Hay cambios en la señal. Quédate quieto respirando para refinar."
        )
    elif signal_flat:
        status = "Señal plana — sin variación"
        hint = (
            "Acerca router/antenas a 0.5 m del escombro. Usa 2.4 GHz. "
            "Evita movimiento de rescatistas durante calibración."
            if rubble_mode
            else "Acércate a 0.5–1 m del router y respira hacia él."
        )
    else:
        status = "Analizando escombros..." if rubble_mode else "Buscando patrones vitales"
        hint = (
            f"Periodicidad={periodicity:.2f} | Var={var:.4f} | SNR={snr:.2f} — "
            "espera calibración o acerca el emisor."
            if rubble_mode
            else f"Var={var:.4f} (>{var_threshold:.4f}) | SNR={snr:.2f} (>{snr_threshold:.2f})"
        )

    return DetectionResult(
        is_breathing=is_breathing,
        is_activity=is_activity,
        resp_freq=float(resp_freq),
        resp_rate=float(resp_rate),
        var=float(var),
        snr=float(snr),
        noise_floor=float(noise_floor),
        unique_values=unique_values,
        signal_flat=signal_flat,
        status=status,
        hint=hint,
        confidence=confidence,
        periodicity=periodicity,
        spectral_energy=spec_energy,
    )


def fuse_antenna_detections(
    results: list[tuple[str, str, DetectionResult]],
    rubble_mode: bool = False,
) -> tuple[DetectionResult, list[tuple[str, str, DetectionResult]]]:
    """Combina detecciones de varias antenas (clientes del hotspot)."""
    if not results:
        return DetectionResult(
            is_breathing=False,
            is_activity=False,
            resp_freq=0.0,
            resp_rate=0.0,
            var=0.0,
            snr=0.0,
            noise_floor=0.0,
            unique_values=0,
            signal_flat=True,
            status="Sin antenas conectadas",
            hint="Conecta celulares al hotspot y colócalos alrededor del escombro.",
        ), []

    breathing = [(b, l, r) for b, l, r in results if r.is_breathing]
    periodic_hits = [
        (b, l, r) for b, l, r in results
        if r.periodicity > (0.24 if rubble_mode else 0.32) and r.is_activity
    ]
    activity = [(b, l, r) for b, l, r in results if r.is_activity and not r.is_breathing]

    # En escombros: 2+ antenas con patrón periódico → respiración fusionada
    if rubble_mode and len(periodic_hits) >= 2 and not breathing:
        breathing = periodic_hits

    if rubble_mode and len(periodic_hits) >= 1 and len(activity) >= 2 and not breathing:
        breathing = periodic_hits[:1]

    if breathing:
        best = max(breathing, key=lambda item: item[2].confidence)
        n = len(breathing)
        total = len(results)
        merged = DetectionResult(
            is_breathing=True,
            is_activity=True,
            resp_freq=best[2].resp_freq,
            resp_rate=best[2].resp_rate,
            var=max(r.var for _, _, r in breathing),
            snr=max(r.snr for _, _, r in breathing),
            noise_floor=best[2].noise_floor,
            unique_values=max(r.unique_values for _, _, r in results),
            signal_flat=False,
            status=f"Respiración en {n}/{total} antenas",
            hint=(
                f"Antenas activas: {', '.join(label for _, label, _ in breathing)}. "
                "Varias antenas confirman presencia bajo escombros."
            ),
            confidence=min(1.0, sum(r.confidence for _, _, r in breathing) / len(breathing) + 0.1 * (n - 1)),
            periodicity=max(r.periodicity for _, _, r in breathing),
            spectral_energy=max(r.spectral_energy for _, _, r in breathing),
        )
        return merged, breathing

    if activity:
        best = max(activity, key=lambda item: item[2].confidence)
        merged = DetectionResult(
            is_breathing=False,
            is_activity=True,
            resp_freq=best[2].resp_freq,
            resp_rate=best[2].resp_rate,
            var=best[2].var,
            snr=best[2].snr,
            noise_floor=best[2].noise_floor,
            unique_values=best[2].unique_values,
            signal_flat=best[2].signal_flat,
            status=f"Actividad en {len(activity)}/{len(results)} antenas",
            hint="Hay movimiento en alguna antena. Mantén respiración tranquila cerca del escombro.",
            confidence=best[2].confidence,
            periodicity=best[2].periodicity,
            spectral_energy=best[2].spectral_energy,
        )
        return merged, []

    flat = all(r.signal_flat for _, _, r in results)
    best = max(results, key=lambda item: item[2].confidence)
    return DetectionResult(
        is_breathing=False,
        is_activity=False,
        resp_freq=0.0,
        resp_rate=0.0,
        var=best[2].var,
        snr=best[2].snr,
        noise_floor=best[2].noise_floor,
        unique_values=best[2].unique_values,
        signal_flat=flat,
        status="Buscando en todas las antenas",
        hint=(
            f"{len(results)} antena(s). Coloca cada celular a ~0.5–1 m del escombro, "
            "separados entre sí, y espera la calibración."
        ),
        confidence=best[2].confidence,
        periodicity=best[2].periodicity,
        spectral_energy=best[2].spectral_energy,
    ), []
