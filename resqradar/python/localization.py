"""
VitalFi — Localización polar → cartesiana y acumulación por rumbo.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np


def polar_to_xy(distance_m: float, bearing_deg: float) -> tuple[float, float]:
  """Convierte distancia + rumbo a coordenadas X/Y (Y = adelante del laptop)."""
  rad = math.radians(bearing_deg)
  x = distance_m * math.sin(rad)
  y = distance_m * math.cos(rad)
  return x, y


@dataclass
class AnchorAntenna:
  """Posición de un rescatista/celular conectado al hotspot."""

  bssid: str
  label: str
  x: float
  y: float
  bearing_deg: float
  score: float = 0.0


@dataclass
class MultiAnchorLocalizer:
  """
  Cada celular conectado al hotspot es una antena fija alrededor del escombro.
  El PC (hotspot) queda en el origen; las antenas se reparten en círculo.
  """

  radius_m: float = 3.0
  decay: float = 0.9
  anchors: dict[str, AnchorAntenna] = field(default_factory=dict)
  target_x: float = 0.0
  target_y: float = 0.0
  target_score: float = 0.0

  def reset(self) -> None:
    self.anchors.clear()
    self.target_x = 0.0
    self.target_y = 0.0
    self.target_score = 0.0

  def _place_anchor(self, bssid: str, label: str, index: int, total: int) -> AnchorAntenna:
    bearing = (index + 0.5) * 360.0 / max(1, total)
    x, y = polar_to_xy(self.radius_m, bearing)
    anchor = AnchorAntenna(bssid=bssid, label=label, x=x, y=y, bearing_deg=bearing)
    self.anchors[bssid] = anchor
    return anchor

  def sync_anchors(self, antenna_labels: list[tuple[str, str]]) -> None:
    """Registra o reubica antenas según clientes conectados."""
    total = len(antenna_labels)
    for index, (bssid, label) in enumerate(antenna_labels):
      if bssid in self.anchors:
        self.anchors[bssid].label = label
        continue
      self._place_anchor(bssid, label, index, total)

    active = {bssid for bssid, _ in antenna_labels}
    for bssid in list(self.anchors):
      if bssid not in active:
        del self.anchors[bssid]

  def update_detection(
      self, bssid: str, distance_m: float, strength: float, variance: float
  ) -> None:
    anchor = self.anchors.get(bssid)
    if anchor is None or strength <= 0:
      return

    dist_to_center = math.hypot(anchor.x, anchor.y) or 1.0
    toward_center_x = -anchor.x / dist_to_center
    toward_center_y = -anchor.y / dist_to_center
    scale = min(1.0, max(0.2, variance * 4.0))
    est_x = anchor.x + toward_center_x * distance_m * scale
    est_y = anchor.y + toward_center_y * distance_m * scale
    weight = min(1.0, strength)

    self.target_x = self.target_x * self.decay + est_x * weight * (1.0 - self.decay)
    self.target_y = self.target_y * self.decay + est_y * weight * (1.0 - self.decay)
    self.target_score = self.target_score * self.decay + weight * (1.0 - self.decay)
    anchor.score = anchor.score * self.decay + weight * (1.0 - self.decay)

  def best_estimate(self, min_score: float = 0.12) -> tuple[float, float, float] | None:
    if self.target_score < min_score:
      return None
    distance = math.hypot(self.target_x, self.target_y)
    bearing = math.degrees(math.atan2(self.target_x, self.target_y)) % 360.0
    return bearing, distance, self.target_score

  def anchor_points(self) -> list[tuple[float, float, str, float]]:
    return [(a.x, a.y, a.label, a.score) for a in self.anchors.values()]


@dataclass
class BearingAccumulator:
  """Acumula detecciones por sector angular para triangular la posición."""

  num_bins: int = 36
  decay: float = 0.92
  scores: np.ndarray = field(init=False)
  distances: np.ndarray = field(init=False)

  def __post_init__(self) -> None:
    self.scores = np.zeros(self.num_bins, dtype=np.float64)
    self.distances = np.zeros(self.num_bins, dtype=np.float64)

  def reset(self) -> None:
    self.scores.fill(0.0)
    self.distances.fill(0.0)

  def _bin_index(self, bearing_deg: float) -> int:
    return int(bearing_deg / 360.0 * self.num_bins) % self.num_bins

  def update(self, bearing_deg: float, distance_m: float, strength: float) -> None:
    idx = self._bin_index(bearing_deg)
    self.scores *= self.decay
    self.distances *= self.decay
    self.scores[idx] += strength
    self.distances[idx] = (
      self.distances[idx] * (1.0 - 0.25) + distance_m * 0.25
      if self.distances[idx] > 0
      else distance_m
    )

  def best_estimate(self, min_score: float = 0.15) -> tuple[float, float, float] | None:
    peak = float(self.scores.max())
    if peak < min_score:
      return None
    idx = int(np.argmax(self.scores))
    bearing = (idx + 0.5) * 360.0 / self.num_bins
    distance = float(self.distances[idx]) if self.distances[idx] > 0 else 2.5
    return bearing, distance, peak

  def heatmap_points(self) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Puntos (x, y, intensidad) para el mapa de calor."""
    xs, ys, vals = [], [], []
    for i, score in enumerate(self.scores):
      if score < 0.02:
        continue
      bearing = (i + 0.5) * 360.0 / self.num_bins
      dist = self.distances[i] if self.distances[i] > 0 else 2.0
      x, y = polar_to_xy(dist, bearing)
      xs.append(x)
      ys.append(y)
      vals.append(score)
    if not xs:
      return np.array([]), np.array([]), np.array([])
    return np.array(xs), np.array(ys), np.array(vals)
