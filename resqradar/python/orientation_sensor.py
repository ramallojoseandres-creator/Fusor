"""
VitalFi — Orientación del laptop para localización direccional.

Fuentes (en orden de prioridad):
1. Brújula Windows (winrt) si el hardware la tiene
2. Rotación manual con flechas ← →
3. Barrido automático (gira el laptop lentamente para triangular)
"""

from __future__ import annotations

import threading
import time
from typing import Optional


class OrientationReader:
  """Lee o estima el rumbo del frente del laptop (0° = adelante)."""

  def __init__(self, sweep_rate_deg: float = 1.5):
    self._lock = threading.Lock()
    self._bearing_deg = 0.0
    self._source = "sweep"
    self._manual_bearing = 0.0
    self._sweep_rate = sweep_rate_deg
    self._last_manual_time = 0.0
    self._running = False
    self._thread: Optional[threading.Thread] = None
    self._compass = None

  def start(self) -> str:
    if self._running:
      return self._source

    if self._try_start_compass():
      self._source = "compass"
    else:
      self._source = "sweep"

    self._running = True
    self._thread = threading.Thread(target=self._update_loop, daemon=True)
    self._thread.start()
    return self._source

  def stop(self) -> None:
    self._running = False
    if self._thread:
      self._thread.join(timeout=1.0)
      self._thread = None
    self._compass = None

  def _try_start_compass(self) -> bool:
    try:
      from winrt.windows.devices.sensors import Compass  # type: ignore

      compass = Compass.get_default()
      if compass is None:
        return False

      def on_reading_changed(sender, args):
        reading = args.reading
        heading = reading.heading_true_north
        if heading is None:
          heading = reading.heading_magnetic_north
        if heading is not None:
          with self._lock:
            self._bearing_deg = float(heading) % 360.0
            self._source = "compass"

      compass.reading_changed(on_reading_changed)
      self._compass = compass
      return True
    except Exception:
      return False

  def _update_loop(self) -> None:
    while self._running:
      if self._source == "compass":
        time.sleep(0.05)
        continue

      with self._lock:
        if self._source == "manual":
          self._bearing_deg = self._manual_bearing % 360.0
        else:
          # Barrido automático: invita a girar el laptop físicamente
          idle = time.time() - self._last_manual_time
          if idle > 1.5:
            self._bearing_deg = (self._bearing_deg + self._sweep_rate) % 360.0
            self._source = "sweep"
          else:
            self._bearing_deg = self._manual_bearing % 360.0

      time.sleep(0.05)

  def adjust_manual(self, delta_deg: float) -> None:
    with self._lock:
      self._manual_bearing = (self._manual_bearing + delta_deg) % 360.0
      self._bearing_deg = self._manual_bearing
      self._source = "manual"
      self._last_manual_time = time.time()

  def reset_manual(self) -> None:
    with self._lock:
      self._manual_bearing = 0.0
      self._bearing_deg = 0.0
      self._source = "sweep"
      self._last_manual_time = 0.0

  def get_bearing_deg(self) -> float:
    with self._lock:
      return self._bearing_deg % 360.0

  def get_bearing_rad(self) -> float:
    import math
    return math.radians(self.get_bearing_deg())

  def get_source_label(self) -> str:
    labels = {
      "compass": "Brújula",
      "manual": "Manual (← →)",
      "sweep": "Barrido (gira el laptop)",
    }
    with self._lock:
      return labels.get(self._source, self._source)
