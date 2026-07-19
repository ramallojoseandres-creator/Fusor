"""
VitalFi — Main Entry Point

Commands:
- router: Detect life under rubble using a Wi-Fi router as AP
- live: Run real-time RSSI breathing detection with live visualization
"""

import argparse
import sys
import os
import time
import numpy as np
import torch

# Import project modules
from simulator import generate_dataset, generate_synthetic_csi
from train import train
from windows_wifi import WindowsWifiCollector, choose_network_interactive, get_connected_network, scan_networks, WifiNetwork
from csi_processor import preprocess_csi
from orientation_sensor import OrientationReader
from localization import BearingAccumulator, MultiAnchorLocalizer, polar_to_xy
from detection import analyze_vital_signal, fuse_antenna_detections, RubbleConfidenceTracker
from radar3d import Radar3DView
from router_mode import print_router_setup, choose_router_network


def run_simulation(args):
    """Generate synthetic dataset."""
    os.makedirs(args.output, exist_ok=True)
    print(f"Generating {args.num_samples} samples at {args.output}...")
    samples = generate_dataset(
        num_samples=args.num_samples,
        duration_sec=args.duration_sec,
        fs=args.fs,
    )
    # Save as npz file
    output_path = os.path.join(args.output, "dataset.npz")
    np.savez_compressed(output_path, samples=samples)
    print(f"Dataset saved successfully to {output_path}!")


def run_network_list(_args):
    """List available Wi-Fi networks."""
    print("\nEscaneando redes Wi-Fi...\n")
    try:
        hs = __import__("hotspot").get_pc_hotspot_info()
        if hs:
            state = "ACTIVO" if hs.active else "apagado"
            ssid_safe = hs.ssid.encode("ascii", "replace").decode("ascii")
            print(f"  >>> HOTSPOT DE ESTE PC: {ssid_safe} ({state})")
            if hs.client_count:
                print(f"      Clientes conectados: {hs.client_count}")
            print()
    except Exception:
        pass

    networks = scan_networks(refresh=True)
    if not networks:
        print("No se encontraron redes.")
        return
    print(f"{'#':>3}  {'SSID':<28} {'Señal':>5}  {'Banda':<8} {'Canal':>5}  Estado")
    print("-" * 72)
    for i, net in enumerate(networks, start=1):
        state = "CONECTADO" if net.connected else ""
        if net.is_pc_hotspot:
            hs_state = "ACTIVO" if net.hotspot_active else "apagado"
            ssid_safe = net.ssid.encode("ascii", "replace").decode("ascii")
            print(f"{i:3}  [HOTSPOT PC] {ssid_safe:<22} {hs_state:>8}  {net.band:<8}")
            continue
        ssid_safe = net.ssid.encode("ascii", "replace").decode("ascii")
        band_safe = net.band.encode("ascii", "replace").decode("ascii")
        print(f"{i:3}  {ssid_safe:<28} {net.signal_pct:>4}%  {band_safe:<8} {net.channel:>5}  {state}")
        if net.bssid:
            print(f"     BSSID: {net.bssid}")


def run_live_detection(args):
    """Real-time breathing detection using PC Wi-Fi RSSI."""
    router_mode = getattr(args, "router_mode", False)
    rubble_mode = router_mode or getattr(args, "pc_hotspot", False)

    if router_mode:
        print_router_setup()
        print("=" * 50)
        print(" VITALFI: MODO ROUTER / ESCOMBROS")
        print("=" * 50)
        choose_fn = choose_router_network
    else:
        print("\n" + "="*50)
        print(" VITALFI: DETECCIÓN DE VIDA VÍA WI-FI")
        print("="*50)
        choose_fn = choose_network_interactive

    try:
        if getattr(args, "pc_hotspot", False):
            import hotspot as hotspot_mod
            hotspot_mod.print_hotspot_setup_guide()
            info = hotspot_mod.get_pc_hotspot_info()
            if not info:
                print("Error: no hay hotspot configurado en este PC.")
                sys.exit(1)
            hotspot_mod.print_hotspot_guide(info)
            target = WifiNetwork(
                ssid=info.ssid,
                bssid="pc-hotspot",
                signal_pct=70,
                band="2.4 GHz",
                channel=0,
                is_pc_hotspot=True,
                hotspot_active=info.active,
            )
        else:
            target = choose_fn(
                ssid=args.ssid,
                auto_connect=not args.no_connect,
            )
    except RuntimeError as exc:
        print(f"Error: {exc}")
        sys.exit(1)

    print(f"\nMonitoreando red: {target.ssid}")
    if not target.is_pc_hotspot:
        live_conn = get_connected_network()
        if live_conn and live_conn.ssid == target.ssid:
            target = live_conn
    if target.bssid and target.bssid != "pc-hotspot":
        print(f"BSSID preferido: {target.bssid}")
    elif target.is_pc_hotspot:
        print("Modo: hotspot multi-antena (cada celular conectado = sensor)")

    collector = WindowsWifiCollector(
        target_ssid=target.ssid,
        target_bssid=target.bssid or None,
        poll_interval=1.0 / args.fs,
        passive_scan_interval=args.scan_interval,
        rubble_mode=rubble_mode,
    )
    collector.start()

    if rubble_mode:
        print(
            "Modo escombros: calibración ~45 s en silencio, luego detección sensible.\n"
            "Consejo: router/antenas a 0.5 m del montículo, banda 2.4 GHz.\n"
        )

    orientation = OrientationReader(sweep_rate_deg=1.5)
    bearing_source = orientation.start()
    print(f"Orientación: {bearing_source} ({orientation.get_source_label()})")
    print("Controles: ← → rumbo | Q/E rotar 3D | W/S inclinar | V reset vista | R reiniciar | C centrar")

    # Import matplotlib locally
    try:
        import matplotlib.pyplot as plt
        from matplotlib.animation import FuncAnimation
        import matplotlib.gridspec as gridspec
        from matplotlib.patches import Circle
    except ImportError:
        print("Error: matplotlib is required for live visualization.")
        print("Install it with: pip install matplotlib")
        collector.stop()
        orientation.stop()
        sys.exit(1)

    # Set up matplotlib figure (Sleek dark theme)
    plt.style.use("dark_background")
    fig = plt.figure(figsize=(22, 9))
    fig.canvas.manager.set_window_title(
        "VitalFi — Modo Router / Escombros" if router_mode
        else "VitalFi — Localizador de Personas y Signos Vitales"
    )

    # Col 0: señales | Col 1: radar polar + silueta | Col 2: radar 3D | Col 3: mapa 2D
    gs = gridspec.GridSpec(3, 4, figure=fig, width_ratios=[1.0, 0.85, 1.35, 1.0])

    ax_raw = fig.add_subplot(gs[0, 0])
    ax_filtered = fig.add_subplot(gs[1, 0])
    ax_fft = fig.add_subplot(gs[2, 0])

    ax_radar = fig.add_subplot(gs[0:2, 1], polar=True)
    ax_human = fig.add_subplot(gs[2, 1])
    ax_3d = fig.add_subplot(gs[:, 2], projection="3d")
    ax_map = fig.add_subplot(gs[:, 3])

    # Lines for signals
    line_raw, = ax_raw.plot([], [], color="#00ffcc", label="Calidad señal (0-255)")
    line_filtered, = ax_filtered.plot([], [], color="#ff3366", linewidth=2, label="Señal Respiratoria")
    line_fft, = ax_fft.plot([], [], color="#ffcc00", label="Espectro FFT")

    # Set up signal plots axes
    ax_raw.set_title("Señal Wi-Fi Objetivo", fontsize=11, color="white", loc="left")
    ax_raw.set_ylabel("Calidad / %")
    ax_raw.grid(True, alpha=0.1)
    ax_raw.legend(loc="upper left")

    ax_filtered.set_title("Movimiento de Pecho (Respiración Filtrada)", fontsize=11, color="white", loc="left")
    ax_filtered.set_ylabel("Amplitud Relativa")
    ax_filtered.grid(True, alpha=0.1)
    ax_filtered.legend(loc="upper left")

    ax_fft.set_title("Espectrograma de Frecuencia", fontsize=11, color="white", loc="left")
    ax_fft.set_xlabel("Frecuencia (Hz)")
    ax_fft.set_ylabel("Magnitud")
    ax_fft.grid(True, alpha=0.1)
    ax_fft.legend(loc="upper left")

    # Set up radar plot
    ax_radar.set_title("Radar Direccional", fontsize=12, color="white", pad=20)
    ax_radar.set_theta_zero_location("N")
    ax_radar.set_theta_direction(-1)
    ax_radar.set_ylim(0, 5.0)
    ax_radar.set_yticks([1.5, 3.0, 5.0])
    ax_radar.set_yticklabels(["1.5m (Cerca)", "3.0m (Medio)", "5.0m (Lejos)"], color="#888888", fontsize=8)
    ax_radar.grid(True, color="#444444", alpha=0.5)

    # Radar elements
    radar_sweep, = ax_radar.plot([], [], color="#33cc33", alpha=0.7, linewidth=2)
    radar_target, = ax_radar.plot([], [], 'o', color="#ff3366", markersize=12, label="Persona Detectada")
    radar_target_glow, = ax_radar.plot([], [], 'o', color="#ff3366", markersize=24, alpha=0.3)
    radar_beam, = ax_radar.plot([], [], color="#00ffcc", alpha=0.35, linewidth=3, label="Rumbo laptop")

    # Mapa 2D cartesiano (metros)
    map_range = 5.5
    ax_map.set_title("Mapa 2D (vista superior)", fontsize=12, color="white", pad=10)
    ax_map.set_xlabel("X → Este (m)", color="#aaaaaa")
    ax_map.set_ylabel("Y → Norte / Adelante (m)", color="#aaaaaa")
    ax_map.set_xlim(-map_range, map_range)
    ax_map.set_ylim(-map_range, map_range)
    ax_map.set_aspect("equal")
    ax_map.grid(True, alpha=0.15, color="#555555")
    ax_map.axhline(0, color="#333333", linewidth=0.8)
    ax_map.axvline(0, color="#333333", linewidth=0.8)

    for radius, label in [(1.5, "1.5m"), (3.0, "3m"), (5.0, "5m")]:
        ring = Circle((0, 0), radius, fill=False, color="#2a4a2a", linestyle="--", linewidth=0.8, alpha=0.6)
        ax_map.add_patch(ring)
        ax_map.text(radius * 0.72, radius * 0.72, label, color="#446644", fontsize=7, alpha=0.8)

    laptop_marker, = ax_map.plot([0], [0], marker="s", color="#00ffcc", markersize=10, label="PC / Hotspot")
    antenna_scatter = ax_map.scatter([], [], marker="^", color="#33aaff", s=90, alpha=0.9, label="Antenas")
    antenna_labels_text: list = []
    laptop_dir_line, = ax_map.plot([0, 0], [0, 1.2], color="#00ffcc", linewidth=2.5, alpha=0.9, label="Frente laptop")
    heatmap_scatter = ax_map.scatter([], [], c=[], cmap="hot", s=80, alpha=0.45, vmin=0, vmax=1, label="Historial")
    map_target, = ax_map.plot([], [], "o", color="#ff3366", markersize=14, label="Persona")
    map_target_glow, = ax_map.plot([], [], "o", color="#ff3366", markersize=28, alpha=0.25)
    map_trail, = ax_map.plot([], [], "-", color="#ff6699", alpha=0.35, linewidth=1.5)
    map_coords_text = ax_map.text(
        0.02, 0.98, "", transform=ax_map.transAxes,
        va="top", ha="left", color="#ffcc00", fontsize=10, family="monospace",
    )
    ax_map.legend(loc="lower right", fontsize=7, framealpha=0.3)

    radar3d = Radar3DView(ax_3d, range_m=map_range)

    # Set up human silhouette plot
    ax_human.axis("off")
    ax_human.set_xlim(-1, 1)
    ax_human.set_ylim(-0.2, 1.8)

    # Human figure lines (head, spine, arms, legs)
    head = plt.Circle((0, 1.4), 0.15, color="#444444", fill=True)
    ax_human.add_patch(head)
    spine, = ax_human.plot([0, 0], [0.5, 1.25], color="#444444", linewidth=6)
    left_arm, = ax_human.plot([0, -0.4], [1.1, 0.8], color="#444444", linewidth=4)
    right_arm, = ax_human.plot([0, 0.4], [1.1, 0.8], color="#444444", linewidth=4)
    left_leg, = ax_human.plot([0, -0.3], [0.5, 0.0], color="#444444", linewidth=4)
    right_leg, = ax_human.plot([0, 0.3], [0.5, 0.0], color="#444444", linewidth=4)

    # Header text
    info_text = fig.text(
        0.5, 0.96, "Inicializando...",
        ha="center", va="center", color="#00ffcc", fontsize=13, weight="bold"
    )

    plt.tight_layout(rect=[0, 0, 1, 0.94])

    window_size_packets = int(args.window_sec * args.fs)
    bearing_map = BearingAccumulator(num_bins=36)
    multi_anchor = MultiAnchorLocalizer(radius_m=3.0)
    multi_antenna_mode = bool(getattr(args, "pc_hotspot", False))
    rubble_tracker = RubbleConfidenceTracker(calib_seconds=45.0 if rubble_mode else 0.0)
    position_trail: list[tuple[float, float, float]] = []

    # State variables for animation
    state = {
        "sweep_angle": 0.0,
        "var_history": [],
        "last_x": None,
        "last_y": None,
        "last_bearing": None,
    }

    def on_key(event):
        if event.key in ("left", "right"):
            delta = -5.0 if event.key == "left" else 5.0
            orientation.adjust_manual(delta)
        elif event.key == "r":
            bearing_map.reset()
            multi_anchor.reset()
            rubble_tracker.reset(time.time())
            position_trail.clear()
        elif event.key == "c":
            orientation.reset_manual()
        elif event.key == "q":
            radar3d.rotate_azim(-12)
        elif event.key == "e":
            radar3d.rotate_azim(12)
        elif event.key == "w":
            radar3d.rotate_elev(8)
        elif event.key == "s":
            radar3d.rotate_elev(-8)
        elif event.key == "v":
            radar3d.reset_view()

    fig.canvas.mpl_connect("key_press_event", on_key)

    def _proximity_from_var(var: float, is_breathing: bool, is_activity: bool):
        if is_breathing or is_activity:
            if var > 0.4:
                return "MUY CERCA", 1.0, "#ff3366"
            if var > 0.15:
                return "DISTANCIA MEDIA", 2.5, "#ff9900"
            return "LEJOS", 4.0, "#ffcc00"
        return "N/A", None, "#444444"

    def update(frame):
        bearing_deg = orientation.get_bearing_deg()
        bearing_rad = np.radians(bearing_deg)

        # Update sweep angle for radar scanning look
        state["sweep_angle"] = (state["sweep_angle"] + 0.08) % (2 * np.pi)

        # Laptop direction on map (Y = adelante)
        dir_x, dir_y = polar_to_xy(1.2, bearing_deg)
        laptop_dir_line.set_data([0, dir_x], [0, dir_y])

        data = collector.get_data()
        antennas_data = data.get("antennas") or []
        antenna_count = data.get("active_antenna_count", len(antennas_data))
        quality = data.get("quality") or data["rssi"]
        timestamps = data["timestamps"]
        ssid = data["ssid"]
        wifi_source = data.get("source", "?")
        unique_vals = data.get("unique_recent", 0)
        connected_ok = data.get("connected_to_target", False)

        n_samples = len(quality)
        min_samples = max(30, int(args.fs * 5))
        breathing_antennas: list[tuple[str, str, object]] = []
        detection = None
        signal_window = np.array([], dtype=np.float64)
        t_window = np.array([], dtype=np.float64)

        if multi_antenna_mode and antennas_data:
            antenna_results = []
            for ant in antennas_data:
                ant_quality = ant.get("quality") or ant.get("rssi") or []
                if len(ant_quality) < min_samples:
                    continue
                if len(ant_quality) > window_size_packets:
                    ant_window = np.array(ant_quality[-window_size_packets:], dtype=np.float64)
                else:
                    ant_window = np.array(ant_quality, dtype=np.float64)
                det = analyze_vital_signal(
                    ant_window, fs=args.fs, sensitivity=args.sensitivity, rubble_mode=rubble_mode
                )
                antenna_results.append((ant["bssid"], ant.get("label", ant["bssid"]), det))

            multi_anchor.sync_anchors(
                [(ant["bssid"], ant.get("label", ant["bssid"])) for ant in antennas_data]
            )
            detection, breathing_antennas = fuse_antenna_detections(antenna_results, rubble_mode=rubble_mode)

            for bssid, _label, det in antenna_results:
                if det.is_breathing or det.is_activity:
                    _prox, dist_m, _color = _proximity_from_var(det.var, det.is_breathing, det.is_activity)
                    strength = min(1.0, det.var * det.snr * 0.5)
                    if dist_m is not None:
                        multi_anchor.update_detection(bssid, dist_m, strength, det.var)

            if antenna_results:
                best_idx = max(
                    range(len(antenna_results)),
                    key=lambda i: antenna_results[i][2].snr * antenna_results[i][2].var,
                )
                best_bssid = antenna_results[best_idx][0]
                best_ant = next(a for a in antennas_data if a["bssid"] == best_bssid)
                signal_window = np.array(
                    (best_ant.get("quality") or best_ant.get("rssi") or [])[-window_size_packets:],
                    dtype=np.float64,
                )
                t_window = np.array(best_ant["timestamps"][-len(signal_window):], dtype=np.float64)
                if len(t_window) > 0:
                    t_window = t_window - t_window[0]
            n_samples = len(signal_window)

            anchor_pts = multi_anchor.anchor_points()
            if anchor_pts:
                antenna_scatter.set_offsets(np.column_stack([[p[0], p[1]] for p in anchor_pts]))
                antenna_scatter.set_sizes([70 + 90 * min(1.0, p[3]) for p in anchor_pts])
            else:
                antenna_scatter.set_offsets(np.empty((0, 2)))

            for txt in antenna_labels_text:
                txt.remove()
            antenna_labels_text.clear()
            for x, y, label, score in anchor_pts:
                txt = ax_map.text(
                    x, y + 0.25, label, color="#88ccff", fontsize=7,
                    ha="center", va="bottom", alpha=0.7 + 0.3 * min(1.0, score),
                )
                antenna_labels_text.append(txt)
        else:
            antenna_scatter.set_offsets(np.empty((0, 2)))
            for txt in antenna_labels_text:
                txt.remove()
            antenna_labels_text.clear()

            if n_samples > window_size_packets:
                signal_window = np.array(quality[-window_size_packets:], dtype=np.float64)
                t_window = np.array(timestamps[-window_size_packets:], dtype=np.float64)
            else:
                signal_window = np.array(quality, dtype=np.float64)
                t_window = np.array(timestamps, dtype=np.float64)
            if len(t_window) > 0:
                t_window = t_window - t_window[0]

        collecting_msg = (
            f"Red: {ssid} | Fuente: {wifi_source} | "
            f"{'Antenas: ' + str(antenna_count) + ' | ' if multi_antenna_mode else ''}"
            f"{'CALIBRANDO escombros... ' if rubble_mode and rubble_tracker.calibrating else ''}"
            f"Recolectando ({n_samples}/{min_samples})...\n"
            f"Rumbo: {bearing_deg:.0f}° | Q/E rotar 3D | W/S inclinar | ← → rumbo"
        )

        if n_samples < min_samples:
            info_text.set_text(collecting_msg)
            radar_sweep.set_data([state["sweep_angle"], state["sweep_angle"]], [0, 5.0])
            radar_beam.set_data([bearing_rad, bearing_rad], [0, 5.0])
            last_x = state.get("last_x")
            last_y = state.get("last_y")
            radar3d.update(
                pos_x=last_x,
                pos_y=last_y,
                bearing_deg=bearing_deg,
                trail=position_trail,
                antenna_points=multi_anchor.anchor_points() if multi_antenna_mode else [],
                heatmap_xyz=[],
                is_breathing=False,
                target_color="#444444",
                confidence=0.0,
                now=time.time(),
            )
            return (
                line_raw, line_filtered, line_fft, radar_sweep, radar_beam, laptop_dir_line,
                antenna_scatter,
            )

        if detection is None:
            detection = analyze_vital_signal(
                signal_window, fs=args.fs, sensitivity=args.sensitivity, rubble_mode=rubble_mode
            )

        rubble_confidence = 0.0
        if rubble_mode:
            rubble_confidence = rubble_tracker.update(detection, time.time())

        is_breathing = detection.is_breathing
        is_activity = detection.is_activity
        # Evidencia acumulada: confirmar presencia aunque un solo frame sea débil
        if rubble_mode and rubble_tracker.likely_present() and not is_breathing:
            is_breathing = detection.periodicity > 0.2 or detection.is_activity
            is_activity = True
        if rubble_mode and rubble_tracker.likely_present(min_score=0.55):
            is_breathing = True
        resp_freq = detection.resp_freq
        resp_rate = detection.resp_rate
        var = detection.var
        snr = detection.snr

        # Señal filtrada para visualización
        try:
            from csi_processor import respiratory_filter
            filtered = respiratory_filter(signal_window - np.mean(signal_window), fs=args.fs)
        except Exception:
            filtered = signal_window - np.mean(signal_window)

        n = len(filtered)
        freqs = np.fft.fftfreq(n, d=1.0 / args.fs)[:n // 2]
        fft_vals = np.abs(np.fft.fft(filtered))[:n // 2]

        # Proximity heuristics based on signal variance
        proximity, distance, proximity_color = _proximity_from_var(var, is_breathing, is_activity)

        # Localización direccional
        pos_x, pos_y = None, None
        used_bearing = bearing_deg
        if multi_antenna_mode and antennas_data:
            best_anchor = multi_anchor.best_estimate(min_score=0.08 if rubble_mode else 0.12)
            if best_anchor is not None:
                used_bearing, est_distance, _peak = best_anchor
                pos_x, pos_y = polar_to_xy(est_distance, used_bearing)
            elif (is_breathing or is_activity) and distance is not None and breathing_antennas:
                bssid = breathing_antennas[0][0]
                anchor = multi_anchor.anchors.get(bssid)
                if anchor is not None:
                    pos_x, pos_y = polar_to_xy(distance * 0.6, anchor.bearing_deg)
                    used_bearing = anchor.bearing_deg
        else:
            if (is_breathing or is_activity) and distance is not None:
                strength = min(1.0, var * snr * 0.5)
                bearing_map.update(bearing_deg, distance, strength)

            best = bearing_map.best_estimate()
            if best is not None:
                used_bearing, est_distance, _peak = best
                pos_x, pos_y = polar_to_xy(est_distance, used_bearing)
            elif is_breathing and distance is not None:
                pos_x, pos_y = polar_to_xy(distance, bearing_deg)
                used_bearing = bearing_deg

        if pos_x is not None and pos_y is not None:
            state["last_x"], state["last_y"] = pos_x, pos_y
            state["last_bearing"] = used_bearing
            dist_xy = float(np.hypot(pos_x, pos_y))
            pos_z = Radar3DView._depth_under_rubble(dist_xy, rubble_confidence if rubble_mode else detection.confidence)
            position_trail.append((pos_x, pos_y, pos_z))
            if len(position_trail) > 80:
                position_trail.pop(0)

        # Calculate movement trend
        trend = "N/A"
        trend_color = "#cccccc"
        if is_breathing:
            state["var_history"].append(var)
            if len(state["var_history"]) > 50:
                state["var_history"].pop(0)

            if len(state["var_history"]) >= 20:
                recent_avg = np.mean(state["var_history"][-10:])
                past_avg = np.mean(state["var_history"][-20:-10])
                diff = recent_avg - past_avg

                if diff > 0.02:
                    trend = "¡TE ESTÁS ACERCANDO! ⬆️"
                    trend_color = "#33cc33"  # Green
                elif diff < -0.02:
                    trend = "TE ESTÁS ALEJANDO ⬇️"
                    trend_color = "#ff3333"  # Red
                else:
                    trend = "Estable ➡️"
                    trend_color = "#ffcc00"  # Yellow
        else:
            state["var_history"].clear()

        # Update signal lines
        line_raw.set_data(t_window, signal_window)
        ax_raw.set_xlim(t_window[0], t_window[-1])
        if signal_window.max() > signal_window.min():
            ax_raw.set_ylim(signal_window.min() - 1, signal_window.max() + 1)
        else:
            ax_raw.set_ylim(signal_window.min() - 2, signal_window.max() + 2)

        line_filtered.set_data(t_window, filtered)
        ax_filtered.set_xlim(t_window[0], t_window[-1])
        ax_filtered.set_ylim(filtered.min() - 0.2, filtered.max() + 0.2)

        line_fft.set_data(freqs, fft_vals)
        ax_fft.set_xlim(0, 1.0)
        ax_fft.set_ylim(0, max(1.0, fft_vals.max() * 1.1))

        # Update Radar Visualization
        radar_sweep.set_data([state["sweep_angle"], state["sweep_angle"]], [0, 5.0])
        radar_beam.set_data([bearing_rad, bearing_rad], [0, 5.0])

        target_bearing_rad = np.radians(used_bearing) if pos_x is not None else None
        if multi_antenna_mode and antennas_data:
            show_dist = np.hypot(pos_x, pos_y) if pos_x is not None else distance
        else:
            best = bearing_map.best_estimate()
            show_dist = best[1] if best is not None else distance
        if target_bearing_rad is not None and show_dist is not None:
            wobble = 0.03 * np.sin(2 * np.pi * resp_freq * time.time()) if is_breathing else 0.0
            radar_target.set_data([target_bearing_rad + wobble], [show_dist])
            radar_target_glow.set_data([target_bearing_rad + wobble], [show_dist])
            radar_target.set_color(proximity_color if is_breathing else "#ff9900")
            radar_target_glow.set_color(proximity_color if is_breathing else "#ff9900")
        else:
            radar_target.set_data([], [])
            radar_target_glow.set_data([], [])

        # Update 2D Map
        hx, hy, hv = bearing_map.heatmap_points()
        if len(hx) > 0:
            vmax = max(float(hv.max()), 0.2)
            heatmap_scatter.set_offsets(np.column_stack([hx, hy]))
            heatmap_scatter.set_array(hv / vmax)
            heatmap_scatter.set_sizes(40 + 120 * (hv / vmax))
        else:
            heatmap_scatter.set_offsets(np.empty((0, 2)))

        if pos_x is not None and pos_y is not None:
            dot_color = proximity_color if is_breathing else "#ff9900"
            map_target.set_data([pos_x], [pos_y])
            map_target_glow.set_data([pos_x], [pos_y])
            map_target.set_color(dot_color)
            map_target_glow.set_color(dot_color)
            trail_x = [p[0] for p in position_trail]
            trail_y = [p[1] for p in position_trail]
            map_trail.set_data(trail_x, trail_y)
            map_coords_text.set_text(
                f"X = {pos_x:+.2f} m\n"
                f"Y = {pos_y:+.2f} m\n"
                f"Dist = {np.hypot(pos_x, pos_y):.2f} m\n"
                f"Rumbo = {used_bearing:.0f}°"
            )
        else:
            map_target.set_data([], [])
            map_target_glow.set_data([], [])
            if state["last_x"] is not None:
                map_coords_text.set_text(
                    f"Última posición:\n"
                    f"X = {state['last_x']:+.2f} m\n"
                    f"Y = {state['last_y']:+.2f} m"
                )
            else:
                map_coords_text.set_text("Gira el laptop o usa ← →\npara triangular posición")

        # Radar 3D — navegación espacial
        heatmap_xyz: list[tuple[float, float, float, float]] = []
        hx, hy, hv = bearing_map.heatmap_points()
        if len(hx) > 0:
            for x, y, val in zip(hx, hy, hv):
                heatmap_xyz.append((float(x), float(y), -0.4, float(val)))

        antenna_pts = multi_anchor.anchor_points() if multi_antenna_mode else []
        conf_for_3d = rubble_confidence if rubble_mode else detection.confidence
        radar3d.update(
            pos_x=pos_x,
            pos_y=pos_y,
            bearing_deg=bearing_deg,
            trail=position_trail,
            antenna_points=antenna_pts,
            heatmap_xyz=heatmap_xyz,
            is_breathing=is_breathing,
            target_color=proximity_color if is_breathing else "#ff9900",
            confidence=conf_for_3d,
            now=time.time(),
        )

        # Update Human Figure & Animation
        if is_breathing:
            # Chest expands/contracts relative to breathing frequency
            scale = 1.0 + 0.15 * np.sin(2 * np.pi * resp_freq * time.time())
            # Color figure based on proximity (Red=very close, Green=breathing/alive)
            fig_color = proximity_color

            # Animate arms/spine slightly
            spine.set_data([0, 0], [0.5, 1.25])
            left_arm.set_data([0, -0.4 * scale], [1.1, 0.8])
            right_arm.set_data([0, 0.4 * scale], [1.1, 0.8])
            left_leg.set_data([0, -0.3], [0.5, 0.0])
            right_leg.set_data([0, 0.3], [0.5, 0.0])
            head.set_color(fig_color)
            spine.set_color(fig_color)
            left_arm.set_color(fig_color)
            right_arm.set_color(fig_color)
            left_leg.set_color(fig_color)
            right_leg.set_color(fig_color)
        else:
            # Dead/no signal state: colored dark gray
            head.set_color("#333333")
            spine.set_color("#333333")
            left_arm.set_color("#333333")
            right_arm.set_color("#333333")
            left_leg.set_color("#333333")
            right_leg.set_color("#333333")

        # Update Status Header Text
        conn_label = "conectado" if connected_ok else f"escaneo ({wifi_source})"
        antenna_line = f" | Antenas: {antenna_count}" if multi_antenna_mode else ""
        conf_line = f" | Confianza: {rubble_confidence * 100:.0f}%" if rubble_mode else ""
        if pos_x is not None and is_breathing:
            info_text.set_text(
                f"Red: {ssid} [{conn_label}]{antenna_line}{conf_line} | PERSONA DETECTADA (¡VIVA!)\n"
                f"Posición: X={pos_x:+.2f}m  Y={pos_y:+.2f}m  |  Distancia: {np.hypot(pos_x, pos_y):.1f}m  |  Rumbo: {used_bearing:.0f}°\n"
                f"RPM: {resp_rate:.1f} | SNR: {snr:.1f} | {proximity} | {detection.status} | {trend}"
            )
            info_text.set_color(trend_color if trend != "N/A" and "Estable" not in trend else proximity_color)
        elif is_activity:
            info_text.set_text(
                f"Red: {ssid} [{conn_label}]{antenna_line} | ACTIVIDAD DETECTADA\n"
                f"{detection.status} | Valores únicos: {detection.unique_values} | Var: {var:.4f} | SNR: {snr:.2f}\n"
                f"{detection.hint}"
            )
            info_text.set_color("#ffcc00")
        elif pos_x is not None:
            info_text.set_text(
                f"SSID: {ssid} | Última posición estimada (señal débil)\n"
                f"X={pos_x:+.2f}m  Y={pos_y:+.2f}m  |  Distancia: {np.hypot(pos_x, pos_y):.1f}m  |  Rumbo: {used_bearing:.0f}°\n"
                f"Gira el laptop o usa ← → para refinar | R reiniciar mapa"
            )
            info_text.set_color("#ff9900")
        elif is_breathing:
            info_text.set_text(
                f"Red: {ssid} [{conn_label}] | Respiración detectada — gira el laptop para fijar posición\n"
                f"Rumbo actual: {bearing_deg:.0f}° | Proximidad: {proximity} (~{distance:.1f}m) | ← → para ajustar"
            )
            info_text.set_color(proximity_color)
        else:
            hint = detection.hint
            if router_mode and detection.signal_flat:
                hint = (
                    "Acerca el ROUTER al escombro (0.5 m). Usa banda 2.4 GHz. "
                    "Espera 60 s en silencio para calibrar."
                )
            info_text.set_text(
                f"Red: {ssid} [{conn_label}]{antenna_line}{conf_line} | {detection.status}\n"
                f"Unicos: {detection.unique_values} | Var: {var:.4f} | SNR: {snr:.2f} | "
                f"Periodicidad: {detection.periodicity:.2f} | Rumbo: {bearing_deg:.0f}°\n"
                f"{hint}"
            )
            info_text.set_color("#ff6666" if detection.signal_flat else "#00ffcc")

        return (
            line_raw, line_filtered, line_fft, radar_sweep, radar_beam,
            radar_target, radar_target_glow, laptop_dir_line,
            map_target, map_target_glow, map_trail, heatmap_scatter, antenna_scatter,
            spine, left_arm, right_arm, left_leg, right_leg,
        )

    ani = FuncAnimation(fig, update, interval=100, blit=False)
    plt.show()

    collector.stop()
    orientation.stop()


def run_hotspot_detection(args):
    """Modo hotspot: el PC comparte Wi-Fi y actúa como punto de acceso."""
    args.router_mode = True
    args.pc_hotspot = True
    if not hasattr(args, "no_connect"):
        args.no_connect = True
    if getattr(args, "sensitivity", 1.5) <= 2.5:
        args.sensitivity = 3.0
    if getattr(args, "window_sec", 30.0) <= 60.0:
        args.window_sec = 90.0
    if getattr(args, "fs", 5.0) <= 5.0:
        args.fs = 10.0
    if getattr(args, "scan_interval", 2.0) >= 1.5:
        args.scan_interval = 0.8
    run_live_detection(args)


def run_router_detection(args):
    """Modo optimizado: router Wi-Fi cerca de escombros."""
    args.router_mode = True
    if getattr(args, "sensitivity", 1.5) <= 2.5:
        args.sensitivity = 3.0
    if getattr(args, "window_sec", 30.0) <= 60.0:
        args.window_sec = 90.0
    if getattr(args, "fs", 5.0) <= 5.0:
        args.fs = 10.0
    if getattr(args, "scan_interval", 2.0) >= 1.5:
        args.scan_interval = 0.8
    run_live_detection(args)


def main():
    parser = argparse.ArgumentParser(description="VitalFi: Detección de Signos Vitales via Wi-Fi")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # 1. Simulate command
    sim_parser = subparsers.add_parser("simulate", help="Generar datos sintéticos CSI")
    sim_parser.add_argument("--num_samples", type=int, default=500, help="Número de muestras")
    sim_parser.add_argument("--duration_sec", type=float, default=10.0, help="Duración en segundos")
    sim_parser.add_argument("--fs", type=float, default=100.0, help="Frecuencia de muestreo (Hz)")
    sim_parser.add_argument("--output", type=str, default="data/synthetic", help="Ruta de salida")

    # 2. Train command
    train_parser = subparsers.add_parser("train", help="Entrenar modelo multitarjeta")
    train_parser.add_argument("--encoder", choices=["lstm", "bilstm", "transformer"], default="bilstm", help="Tipo de encoder")
    train_parser.add_argument("--num_samples", type=int, default=200, help="Número de muestras sintéticas")
    train_parser.add_argument("--epochs", type=int, default=30, help="Número de épocas")
    train_parser.add_argument("--batch_size", type=int, default=8, help="Tamaño de batch")
    train_parser.add_argument("--lr", type=float, default=1e-4, help="Learning rate")

    # 3. Networks command
    subparsers.add_parser("networks", help="Listar redes Wi-Fi disponibles")

    # 4. Hotspot PC command
    hotspot_parser = subparsers.add_parser(
        "hotspot",
        help="Usar el hotspot Wi-Fi compartido desde este PC",
    )
    hotspot_parser.add_argument("--fs", type=float, default=10.0)
    hotspot_parser.add_argument("--window_sec", type=float, default=90.0)
    hotspot_parser.add_argument("--sensitivity", type=float, default=3.0)
    hotspot_parser.add_argument("--scan-interval", type=float, default=0.8, dest="scan_interval")

    # 5. Router / escombros command
    router_parser = subparsers.add_parser(
        "router",
        help="Detectar persona bajo escombros usando un router Wi-Fi",
    )
    router_parser.add_argument("--fs", type=float, default=10.0, help="Frecuencia de muestreo (Hz)")
    router_parser.add_argument("--window_sec", type=float, default=90.0, help="Segundos de ventana")
    router_parser.add_argument("--ssid", type=str, default=None, help="SSID del router (sin menu)")
    router_parser.add_argument("--sensitivity", type=float, default=3.0, help="Sensibilidad (3.0=escombros)")
    router_parser.add_argument("--scan-interval", type=float, default=0.8, dest="scan_interval")
    router_parser.add_argument("--no-connect", action="store_true", help="No conectar al router")

    # 6. Live command
    live_parser = subparsers.add_parser("live", help="Monitoreo de respiración en tiempo real (RSSI)")
    live_parser.add_argument("--fs", type=float, default=5.0, help="Frecuencia de muestreo (Hz)")
    live_parser.add_argument("--window_sec", type=float, default=30.0, help="Segundos de ventana a mostrar/analizar")
    live_parser.add_argument("--ssid", type=str, default=None, help="SSID de la red a monitorear (sin menú)")
    live_parser.add_argument("--sensitivity", type=float, default=1.5, help="Sensibilidad (1=normal, 2=más sensible)")
    live_parser.add_argument("--scan-interval", type=float, default=2.0, dest="scan_interval", help="Segundos entre escaneos pasivos")
    live_parser.add_argument("--pc-hotspot", action="store_true", help="Usar hotspot de este PC")
    live_parser.add_argument("--no-connect", action="store_true", help="No conectar automáticamente a la red elegida")

    args = parser.parse_args()

    if args.command == "simulate":
        run_simulation(args)
    elif args.command == "train":
        train(
            encoder_type=args.encoder,
            num_samples=args.num_samples,
            epochs=args.epochs,
            batch_size=args.batch_size,
            learning_rate=args.lr,
        )
    elif args.command == "networks":
        run_network_list(args)
    elif args.command == "router":
        run_router_detection(args)
    elif args.command == "hotspot":
        run_hotspot_detection(args)
    elif args.command == "live":
        run_live_detection(args)


if __name__ == "__main__":
    main()
