"""
VitalFi — Modo router / escombros.

Configuración para usar un router Wi-Fi como emisor cerca de escombros
y la laptop como receptor que analiza la señal.
"""

from __future__ import annotations

from typing import Optional

from windows_wifi import WifiNetwork, connect_network, get_connected_network, scan_networks


def print_router_setup() -> None:
    print(
        """
================================================================
  MODO ROUTER — DETECCION BAJO ESCOMBROS
================================================================

  Colocacion recomendada:

       [ LAPTOP ]              [ ESCOMBROS ]
           |                         |
           |    senal Wi-Fi 2.4GHz   |
           +-----------X-------------+  <- persona atrapada
                       |
                  [ ROUTER ]
              (a 0.5-1 m del escombro,
               antenas hacia el monticulo)

  Pasos:
  1. Configura el router en modo Wi-Fi / hotspot (2.4 GHz si puedes).
  2. Pon el router PEGADO al escombro (0.3–0.5 m), antenas hacia adentro.
  3. Conecta la laptop a la red del router.
  4. Ejecuta: python main.py router
  5. CALIBRACION: primeros 45 s sin mover nada (ni rescatistas ni laptop).
  6. Usa el RADAR 3D central para orientarte (flecha hacia la victima).
  7. Gira la laptop lentamente 360 grados para triangular posicion.

  Importante:
  - 2.4 GHz penetra mejor que 5 GHz en concreto y escombros.
  - VitalFi amplifica micro-variaciones y acumula confianza en el tiempo.
  - Profundidad real con laptop: ~1 a 3 metros (prototipo, no rescate oficial).
  - Si no detecta respiracion, acerca mas el router al escombro.

================================================================
"""
    )


def _is_24ghz(band: str) -> bool:
    b = band.lower().replace(" ", "")
    return "2.4" in b or "2,4" in b


def choose_router_network(ssid: Optional[str] = None, auto_connect: bool = True) -> WifiNetwork:
    """Selecciona la red del router; prioriza 2.4 GHz para penetracion."""
    print("\nEscaneando redes del router...")
    connected = get_connected_network()
    if ssid and connected and connected.ssid.lower() == ssid.lower():
        print(f"\n[OK] Laptop ya conectada al router: {connected.ssid} ({connected.signal_pct}%)")
        _print_placement_reminder(connected)
        return connected

    networks = scan_networks(refresh=True)
    if not networks:
        raise RuntimeError("No hay redes Wi-Fi visibles. Enciende el router y activa el Wi-Fi.")

    if ssid:
        matches = [n for n in networks if n.ssid.lower() == ssid.lower()]
        if not matches:
            raise RuntimeError(
                f"No se ve la red del router '{ssid}'. "
                "Verifica que este encendido y cerca."
            )
        selected = _pick_best_bssid(matches)
    else:
        print("\n" + "=" * 58)
        print(" REDES DEL ROUTER — elige la red que emite el router")
        print("=" * 58)
        for i, net in enumerate(networks, start=1):
            tags = []
            if net.connected:
                tags.append("CONECTADO")
            if _is_24ghz(net.band):
                tags.append("RECOMENDADO 2.4GHz")
            elif "5" in net.band:
                tags.append("5GHz (menos penetracion)")
            tag_str = f" [{', '.join(tags)}]" if tags else ""
            print(f"  {i:2}. {net.ssid}  {net.signal_pct}%  {net.band}  ch{net.channel}{tag_str}")
            if net.bssid:
                print(f"      BSSID: {net.bssid}")
        print("=" * 58)
        print("Tip: usa la red 2.4 GHz del router para mejor penetracion en escombros.")

        while True:
            choice = input(f"Elige numero [1-{len(networks)}]: ").strip()
            if choice.isdigit() and 1 <= int(choice) <= len(networks):
                matches = [n for n in networks if n.ssid == networks[int(choice) - 1].ssid]
                selected = _pick_best_bssid(matches)
                break
            print("Opcion invalida.")

    connected = get_connected_network()
    if connected and connected.ssid == selected.ssid:
        print(f"\n[OK] Laptop conectada al router: {selected.ssid} ({selected.signal_pct}%)")
        selected.connected = True
        _print_placement_reminder(selected)
        return selected

    print(f"\nRouter seleccionado: {selected.ssid} ({selected.signal_pct}%, {selected.band})")
    if not _is_24ghz(selected.band):
        print("AVISO: estas en 5 GHz. Si puedes, cambia el router a 2.4 GHz.")

    if not auto_connect:
        print("Modo escaneo pasivo (sin conectar). Mejor conectar la laptop al router.")
        _print_placement_reminder(selected)
        return selected

    answer = input("Conectar la laptop al router ahora? [S/n]: ").strip().lower()
    if answer in ("", "s", "si", "y", "yes"):
        print(f"Conectando a {selected.ssid}...")
        if connect_network(selected.ssid):
            refreshed = get_connected_network()
            if refreshed:
                print(f"[OK] Conectado — senal {refreshed.signal_pct}%")
                _print_placement_reminder(refreshed)
                return refreshed
        print("No se pudo conectar. Coloca el router mas cerca e intenta de nuevo.")

    _print_placement_reminder(selected)
    return selected


def _pick_best_bssid(networks: list[WifiNetwork]) -> WifiNetwork:
    """Elige el BSSID con mejor senal; prioriza 2.4 GHz."""
    if len(networks) == 1:
        return networks[0]
    sorted_nets = sorted(
        networks,
        key=lambda n: (_is_24ghz(n.band), n.signal_pct),
        reverse=True,
    )
    return sorted_nets[0]


def _print_placement_reminder(net: WifiNetwork) -> None:
    band_note = "buena penetracion" if _is_24ghz(net.band) else "penetracion limitada"
    print(
        f"\nListo. Red: {net.ssid} | {net.band} ({band_note})\n"
        "Coloca el ROUTER contra el escombro y la laptop a 1-3 m del router.\n"
    )
