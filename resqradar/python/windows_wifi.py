"""
VitalFi — Windows Wi-Fi: escaneo, selección de red y captura RSSI.

Usa la API nativa WLAN (wlanapi) para lecturas más frecuentes que netsh.
"""

from __future__ import annotations

import ctypes
import re
import subprocess
import threading
import time
from dataclasses import dataclass, field
from typing import Any, Optional

import numpy as np

# Importación diferida para evitar ciclos
_hotspot_module = None


def _hotspot():
    global _hotspot_module
    if _hotspot_module is None:
        import hotspot as _hotspot_module  # noqa: PLC0415
    return _hotspot_module


@dataclass
class AntennaStream:
    """Serie temporal RSSI de un cliente conectado al hotspot (una antena)."""

    bssid: str
    label: str
    timestamps: list[float] = field(default_factory=list)
    quality_values: list[float] = field(default_factory=list)
    signal_percentages: list[float] = field(default_factory=list)
    rssi_values: list[float] = field(default_factory=list)
    last_signal: float = 0.0
    last_quality: float = 0.0
    last_rssi: float = -100.0
    source: str = "hotspot-antena"


@dataclass
class WifiNetwork:
    ssid: str
    bssid: str
    signal_pct: int
    band: str
    channel: int
    connected: bool = False
    is_pc_hotspot: bool = False
    hotspot_active: bool = False

    @property
    def label(self) -> str:
        if self.is_pc_hotspot:
            state = "ACTIVO" if self.hotspot_active else "apagado"
            return f"[HOTSPOT DE ESTE PC] {self.ssid}  ({state})"
        mark = " [CONECTADO]" if self.connected else ""
        return f"{self.ssid}  ({self.signal_pct}% | {self.band} | ch{self.channel}){mark}"


def _hidden_startupinfo() -> subprocess.STARTUPINFO:
    startupinfo = subprocess.STARTUPINFO()
    startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startupinfo.wShowWindow = subprocess.SW_HIDE
    return startupinfo


def _run_netsh(args: list[str]) -> str:
    result = subprocess.run(
        ["netsh", "wlan", *args],
        capture_output=True,
        text=True,
        encoding="cp850",
        startupinfo=_hidden_startupinfo(),
        check=False,
    )
    return result.stdout or ""


def get_connected_network() -> Optional[WifiNetwork]:
    output = _run_netsh(["show", "interfaces"])
    ssid_match = re.search(r"SSID\s*:\s*(.+)", output)
    if not ssid_match:
        return None
    ssid = ssid_match.group(1).strip()
    if not ssid or ssid.lower() in ("desconectado", "disconnected", "n/a"):
        return None

    signal_match = re.search(r"(\d+)%", output)
    rssi_match = re.search(r"Rssi\s*:\s*(-?\d+)", output, re.I)
    bssid_match = re.search(r"BSSID\s*:\s*([0-9a-f:]+)", output, re.I)
    band_match = re.search(r"Banda\s*:\s*(.+)", output) or re.search(r"Band\s*:\s*(.+)", output)
    channel_match = re.search(r"Canal\s*:\s*(\d+)", output) or re.search(r"Channel\s*:\s*(\d+)", output)

    signal_pct = int(signal_match.group(1)) if signal_match else 0
    if rssi_match and signal_pct == 0:
        signal_pct = max(0, min(100, int((float(rssi_match.group(1)) + 100) * 2)))

    return WifiNetwork(
        ssid=ssid,
        bssid=bssid_match.group(1).lower() if bssid_match else "",
        signal_pct=signal_pct,
        band=(band_match.group(1).strip() if band_match else "?"),
        channel=int(channel_match.group(1)) if channel_match else 0,
        connected=True,
    )


def scan_networks(refresh: bool = True) -> list[WifiNetwork]:
    if refresh:
        _run_netsh(["refresh"])

    output = _run_netsh(["show", "networks", "mode=bssid"])
    connected = get_connected_network()
    connected_ssid = connected.ssid if connected else ""
    connected_bssid = connected.bssid if connected else ""

    networks: list[WifiNetwork] = []
    blocks = re.split(r"\n\s*SSID\s*\d+\s*:\s*", "\n" + output)

    for block in blocks[1:]:
        lines = block.strip().splitlines()
        if not lines:
            continue
        ssid = lines[0].strip()
        if not ssid:
            continue

        bssid_parts = re.findall(
            r"BSSID\s*\d*\s*:\s*([0-9a-f:]+)([\s\S]*?)(?=BSSID\s*\d*\s*:|$)",
            block,
            re.I,
        )
        if not bssid_parts:
            signal_match = re.search(r"Se\S*al\s*:\s*(\d+)%", block, re.I) or re.search(
                r"Signal\s*:\s*(\d+)%", block, re.I
            )
            band_match = re.search(r"Banda\s*:\s*(.+)", block) or re.search(r"Band\s*:\s*(.+)", block)
            channel_match = re.search(r"Canal\s*:\s*(\d+)", block) or re.search(r"Channel\s*:\s*(\d+)", block)
            networks.append(
                WifiNetwork(
                    ssid=ssid,
                    bssid="",
                    signal_pct=int(signal_match.group(1)) if signal_match else 0,
                    band=(band_match.group(1).strip() if band_match else "?"),
                    channel=int(channel_match.group(1)) if channel_match else 0,
                    connected=ssid == connected_ssid,
                )
            )
            continue

        for bssid, tail in bssid_parts:
            signal_match = re.search(r"Se\S*al\s*:\s*(\d+)%", tail, re.I) or re.search(
                r"Signal\s*:\s*(\d+)%", tail, re.I
            )
            band_match = re.search(r"Banda\s*:\s*(.+)", tail) or re.search(r"Band\s*:\s*(.+)", tail)
            channel_match = re.search(r"Canal\s*:\s*(\d+)", tail) or re.search(r"Channel\s*:\s*(\d+)", tail)
            networks.append(
                WifiNetwork(
                    ssid=ssid,
                    bssid=bssid.lower(),
                    signal_pct=int(signal_match.group(1)) if signal_match else 0,
                    band=(band_match.group(1).strip() if band_match else "?"),
                    channel=int(channel_match.group(1)) if channel_match else 0,
                    connected=ssid == connected_ssid and bssid.lower() == connected_bssid,
                )
            )

    # Mejor señal primero; conectada arriba
    networks.sort(key=lambda n: (not n.connected, -n.signal_pct, n.ssid))

    try:
        hs = _hotspot().get_pc_hotspot_info()
        if hs:
            entry = hs.to_wifi_network()
            entry.is_pc_hotspot = True
            entry.hotspot_active = hs.active
            networks.insert(0, entry)
    except Exception:
        pass

    return networks


def connect_network(ssid: str) -> bool:
    output = _run_netsh(["connect", f"name={ssid}"])
    time.sleep(2.0)
    connected = get_connected_network()
    return connected is not None and connected.ssid == ssid


def choose_network_interactive(ssid: Optional[str] = None, auto_connect: bool = True) -> WifiNetwork:
    print("\nEscaneando redes Wi-Fi...")
    networks = scan_networks(refresh=True)
    if not networks:
        raise RuntimeError("No se encontraron redes Wi-Fi. Activa el adaptador Wi-Fi.")

    if ssid:
        matches = [n for n in networks if n.ssid.lower() == ssid.lower()]
        if not matches:
            raise RuntimeError(f"No se encontró la red '{ssid}'. Ejecuta: python main.py networks")
        selected = matches[0]
    else:
        print("\n" + "=" * 55)
        print(" REDES DISPONIBLES — selecciona una para escanear")
        print("=" * 55)
        for i, net in enumerate(networks, start=1):
            if net.is_pc_hotspot:
                print(f"  {i:2}. {net.label}  <-- punto de acceso de tu PC")
            else:
                print(f"  {i:2}. {net.label}")
            if net.bssid and not net.is_pc_hotspot:
                print(f"      BSSID: {net.bssid}")
        print("=" * 55)

        while True:
            choice = input(f"Elige número [1-{len(networks)}]: ").strip()
            if choice.isdigit() and 1 <= int(choice) <= len(networks):
                selected = networks[int(choice) - 1]
                break
            print("Opción inválida, intenta de nuevo.")

    if selected.is_pc_hotspot:
        _hotspot().print_hotspot_guide(_hotspot().get_pc_hotspot_info() or _hotspot().PcHotspotInfo(
            ssid=selected.ssid, active=selected.hotspot_active
        ))
        if not selected.hotspot_active:
            answer = input("El hotspot esta apagado. Activar en Configuracion y continuar? [s/N]: ").strip().lower()
            if answer not in ("s", "si", "y", "yes"):
                raise SystemExit(0)
        print(
            f"\n[OK] Monitoreando hotspot del PC: {selected.ssid}\n"
            "Tip: conecta un celular al hotspot y colocalo cerca del escombro.\n"
        )
        return selected

    connected = get_connected_network()
    if connected and connected.ssid == selected.ssid:
        print(f"\n[OK] Ya conectado a: {selected.ssid} ({selected.signal_pct}%)")
        selected.connected = True
        return selected

    print(f"\nRed seleccionada: {selected.ssid} ({selected.signal_pct}%)")
    if not auto_connect:
        print("Modo escaneo pasivo (sin conectar). La señal se leerá por barridos.")
        return selected

    answer = input("¿Conectar a esta red para mejor detección? [S/n]: ").strip().lower()
    if answer in ("", "s", "si", "sí", "y", "yes"):
        print(f"Conectando a {selected.ssid}...")
        if connect_network(selected.ssid):
            refreshed = get_connected_network()
            if refreshed:
                print(f"[OK] Conectado a {refreshed.ssid} — señal {refreshed.signal_pct}%")
                return refreshed
        print("No se pudo conectar. Continuando en modo escaneo pasivo.")

    return selected


def _quality_to_signal_pct(quality: float) -> float:
    """Convierte wlanSignalQuality a porcentaje 0-100."""
    if quality <= 100:
        return float(quality)
    return float(quality) * 100.0 / 255.0


def _quality_to_dbm(quality: float) -> float:
    return (_quality_to_signal_pct(quality) / 2.0) - 100.0


class _WlanApiReader:
    """Lector de calidad de señal vía wlanapi.dll (Windows)."""

    WLAN_INTF_OPCODE_CURRENT_CONNECTION = 7

    def __init__(self):
        from ctypes import POINTER, Structure, byref, c_void_p, wintypes

        self._wintypes = wintypes
        self._byref = byref
        self._c_void_p = c_void_p
        self._POINTER = POINTER
        self.wlanapi = ctypes.windll.wlanapi

        class GUID(Structure):
            _fields_ = [
                ("Data1", wintypes.DWORD),
                ("Data2", wintypes.WORD),
                ("Data3", wintypes.WORD),
                ("Data4", wintypes.BYTE * 8),
            ]

        class DOT11_SSID(Structure):
            _fields_ = [("uSSIDLength", wintypes.DWORD), ("ucSSID", wintypes.BYTE * 32)]

        class WLAN_ASSOCIATION_ATTRIBUTES(Structure):
            _fields_ = [
                ("dot11Ssid", DOT11_SSID),
                ("dot11BssType", wintypes.DWORD),
                ("dot11Bssid", wintypes.BYTE * 6),
                ("dot11PhyType", wintypes.DWORD),
                ("uDot11PhyIndex", wintypes.DWORD),
                ("wlanSignalQuality", wintypes.DWORD),
                ("ulRxRate", wintypes.ULONG),
                ("ulTxRate", wintypes.ULONG),
            ]

        class WLAN_CONNECTION_ATTRIBUTES(Structure):
            _fields_ = [
                ("isState", wintypes.DWORD),
                ("wlanConnectionMode", wintypes.DWORD),
                ("strProfileName", wintypes.WCHAR * 256),
                ("wlanAssociationAttributes", WLAN_ASSOCIATION_ATTRIBUTES),
            ]

        class WLAN_INTERFACE_INFO(Structure):
            _fields_ = [
                ("InterfaceGuid", GUID),
                ("strInterfaceDescription", wintypes.WCHAR * 256),
                ("isState", wintypes.DWORD),
            ]

        class WLAN_INTERFACE_INFO_LIST(Structure):
            _fields_ = [
                ("dwNumberOfItems", wintypes.DWORD),
                ("dwIndex", wintypes.DWORD),
            ]

        self._GUID = GUID
        self._WLAN_CONNECTION_ATTRIBUTES = WLAN_CONNECTION_ATTRIBUTES
        self._WLAN_INTERFACE_INFO = WLAN_INTERFACE_INFO
        self._WLAN_INTERFACE_INFO_LIST = WLAN_INTERFACE_INFO_LIST

        self._handle = ctypes.c_void_p()
        negotiated = wintypes.DWORD()
        rc = self.wlanapi.WlanOpenHandle(2, None, byref(negotiated), byref(self._handle))
        if rc != 0:
            raise OSError(f"WlanOpenHandle falló (código {rc})")

        iface_list = c_void_p()
        rc = self.wlanapi.WlanEnumInterfaces(self._handle, None, byref(iface_list))
        if rc != 0:
            self.wlanapi.WlanCloseHandle(self._handle, None)
            raise OSError(f"WlanEnumInterfaces falló (código {rc})")

        # El primer elemento está a 8 bytes del inicio de la lista (2 x DWORD de cabecera)
        iface = WLAN_INTERFACE_INFO.from_address(iface_list.value + 8)
        self._guid = iface.InterfaceGuid
        self._interface_name = iface.strInterfaceDescription
        self.wlanapi.WlanFreeMemory(iface_list)

    def close(self) -> None:
        if self._handle and self._handle.value:
            self.wlanapi.WlanCloseHandle(self._handle, None)
            self._handle = ctypes.c_void_p()

    def read_connected(self) -> tuple[str, int, int, str]:
        """Devuelve (ssid, quality 0-255, signal_pct, bssid)."""
        iface_list = self._c_void_p()
        rc = self.wlanapi.WlanEnumInterfaces(self._handle, None, self._byref(iface_list))
        if rc != 0 or not iface_list.value:
            return "", 0, 0, ""

        try:
            iface = self._WLAN_INTERFACE_INFO.from_address(iface_list.value + 8)
            guid = iface.InterfaceGuid

            data_size = self._wintypes.DWORD()
            data = self._c_void_p()
            opcode = self._wintypes.DWORD()
            rc = self.wlanapi.WlanQueryInterface(
                self._handle,
                self._byref(guid),
                self.WLAN_INTF_OPCODE_CURRENT_CONNECTION,
                None,
                self._byref(data_size),
                self._byref(data),
                self._byref(opcode),
            )
            if rc != 0:
                return "", 0, 0, ""

            conn = ctypes.cast(data, self._POINTER(self._WLAN_CONNECTION_ATTRIBUTES)).contents
            assoc = conn.wlanAssociationAttributes
            ssid = bytes(assoc.dot11Ssid.ucSSID[: assoc.dot11Ssid.uSSIDLength]).decode("utf-8", "replace")
            quality = int(assoc.wlanSignalQuality)
            bssid = ":".join(f"{b:02x}" for b in assoc.dot11Bssid)
            signal_pct = int(_quality_to_signal_pct(quality))
            self.wlanapi.WlanFreeMemory(data)
            return ssid, quality, signal_pct, bssid
        finally:
            self.wlanapi.WlanFreeMemory(iface_list)


class WindowsWifiCollector:
    """Recolector RSSI con API WLAN nativa + escaneo de red objetivo."""

    def __init__(
        self,
        target_ssid: Optional[str] = None,
        target_bssid: Optional[str] = None,
        poll_interval: float = 0.2,
        passive_scan_interval: float = 2.0,
        rubble_mode: bool = False,
    ):
        self.target_ssid = target_ssid
        self.target_bssid = (target_bssid or "").lower()
        self.poll_interval = poll_interval
        self.passive_scan_interval = passive_scan_interval
        self.rubble_mode = rubble_mode
        self.running = False
        self.thread: Optional[threading.Thread] = None

        self.lock = threading.Lock()
        self.timestamps: list[float] = []
        self.rssi_values: list[float] = []
        self.signal_percentages: list[float] = []
        self.quality_values: list[float] = []
        self.max_history = 3000

        self.last_ssid = "Unknown"
        self.last_rssi = -100.0
        self.last_signal = 0.0
        self.last_quality = 0.0
        self.source = "init"
        self.connected_to_target = False
        self.unique_recent = 0
        self._wlan: Optional[_WlanApiReader] = None
        self._last_passive_scan = 0.0
        self._passive_cache_signal = 0
        self.pc_hotspot_mode = self.target_bssid == "pc-hotspot"
        self.antenna_streams: dict[str, AntennaStream] = {}
        self.active_antenna_count = 0

    def start(self) -> None:
        if self.running:
            return
        try:
            self._wlan = _WlanApiReader()
            self.source = "wlanapi"
            print(f"Colector WLAN API activo ({self._wlan._interface_name})")
        except OSError as exc:
            self._wlan = None
            self.source = "netsh"
            print(f"WLAN API no disponible ({exc}). Usando netsh.")

        self.running = True
        self.thread = threading.Thread(target=self._poll_loop, daemon=True)
        self.thread.start()

    def stop(self) -> None:
        self.running = False
        if self.thread:
            self.thread.join(timeout=1.5)
            self.thread = None
        if self._wlan:
            self._wlan.close()
            self._wlan = None

    def _read_passive_scan(self) -> tuple[str, float, float]:
        now = time.time()
        if now - self._last_passive_scan < self.passive_scan_interval:
            return self.target_ssid or "", self._passive_cache_signal, self._passive_cache_signal

        self._last_passive_scan = now
        networks = scan_networks(refresh=True)
        matches = [n for n in networks if n.ssid == self.target_ssid]
        if self.target_bssid:
            matches = [n for n in matches if n.bssid == self.target_bssid] or matches
        if not matches:
            return self.target_ssid or "", 0.0, 0.0

        best = max(matches, key=lambda n: n.signal_pct)
        self._passive_cache_signal = float(best.signal_pct)
        quality = best.signal_pct * 255.0 / 100.0
        return best.ssid, quality, float(best.signal_pct)

    def _read_hotspot_sample(self) -> tuple[str, float, float, str]:
        """Lee señal del hotspot del PC (clientes conectados o adaptador)."""
        antennas = self._read_hotspot_antennas()
        if antennas:
            best = max(antennas, key=lambda a: a[4])
            bssid, _label, ssid, quality, signal_pct, source = best
            return ssid, quality, signal_pct, source

        hs = _hotspot()
        info = hs.get_pc_hotspot_info()
        ssid = info.ssid if info else (self.target_ssid or "PC-Hotspot")

        if self._wlan is not None:
            _csid, quality, signal_pct, _bssid = self._wlan.read_connected()
            if quality > 0:
                return ssid, float(quality), float(signal_pct), "hotspot-pc"

        pct = max(1.0, self._passive_cache_signal)
        return ssid, pct * 2.55, pct, "hotspot"

    def _read_hotspot_antennas(self) -> list[tuple[str, str, str, float, float, str]]:
        """Devuelve una muestra por cada cliente conectado al hotspot."""
        hs = _hotspot()
        info = hs.get_pc_hotspot_info()
        ssid = info.ssid if info else (self.target_ssid or "PC-Hotspot")
        if not info or not info.active:
            return []

        clients = hs._get_hotspot_clients(info.ssid)
        samples: list[tuple[str, str, str, float, float, str]] = []
        for client in clients:
            bssid = (client.get("bssid") or "").lower()
            if not bssid:
                continue
            label = client.get("label") or client.get("ssid") or f"Antena {bssid[-8:]}"
            pct = float(client.get("signal_pct") or 0)
            if pct <= 0:
                pct = 1.0
            quality = pct * 2.55
            samples.append((bssid, label, ssid, quality, pct, "hotspot-antena"))
        return samples

    def _ensure_antenna(self, bssid: str, label: str) -> AntennaStream:
        stream = self.antenna_streams.get(bssid)
        if stream is None:
            stream = AntennaStream(bssid=bssid, label=label)
            self.antenna_streams[bssid] = stream
            print(f"[Antena] Nuevo sensor conectado: {label} ({bssid})")
        elif label and stream.label != label:
            stream.label = label
        return stream

    def _append_antenna_sample(
        self, bssid: str, label: str, quality: float, signal_pct: float, source: str
    ) -> None:
        stream = self._ensure_antenna(bssid, label)
        timestamp = time.time()
        if self.rubble_mode and len(stream.quality_values) >= 8:
            recent = stream.quality_values[-min(120, len(stream.quality_values)) :]
            baseline = float(np.median(recent))
            quality = baseline + (quality - baseline) * 2.8
        rssi = _quality_to_dbm(quality)
        stream.last_quality = quality
        stream.last_signal = signal_pct
        stream.last_rssi = rssi
        stream.source = source
        stream.timestamps.append(timestamp)
        stream.quality_values.append(quality)
        stream.signal_percentages.append(signal_pct)
        stream.rssi_values.append(rssi)
        if len(stream.timestamps) > self.max_history:
            stream.timestamps.pop(0)
            stream.quality_values.pop(0)
            stream.signal_percentages.pop(0)
            stream.rssi_values.pop(0)

    def _poll_hotspot_antennas(self) -> None:
        antennas = self._read_hotspot_antennas()
        with self.lock:
            seen = {bssid for bssid, *_ in antennas}
            stale = [bssid for bssid in self.antenna_streams if bssid not in seen]
            for bssid in stale:
                del self.antenna_streams[bssid]
            self.active_antenna_count = len(antennas)

            if antennas:
                for bssid, label, ssid, quality, signal_pct, source in antennas:
                    self._append_antenna_sample(bssid, label, quality, signal_pct, source)

                avg_quality = sum(a[3] for a in antennas) / len(antennas)
                avg_signal = sum(a[4] for a in antennas) / len(antennas)
                self._append_sample_nolock(antennas[0][2], avg_quality, avg_signal, "hotspot-multi")
                return

        ssid, quality, signal_pct, source = self._read_hotspot_sample()
        if ssid and quality > 0:
            self._append_sample(ssid, quality, signal_pct, source)

    def _read_sample(self) -> tuple[str, float, float, str]:
        if self.pc_hotspot_mode:
            return self._read_hotspot_sample()

        if self._wlan is not None:
            ssid, quality, signal_pct, bssid = self._wlan.read_connected()
            if ssid and (not self.target_ssid or ssid == self.target_ssid):
                if not self.target_bssid or bssid == self.target_bssid:
                    return ssid, float(quality), float(signal_pct), "wlanapi"
                # SSID correcto pero BSSID distinto (dual-band / mesh): usar conexión activa
                if not self.pc_hotspot_mode:
                    return ssid, float(quality), float(signal_pct), "wlanapi"

        if self.target_ssid:
            ssid, quality, signal_pct = self._read_passive_scan()
            return ssid, quality, signal_pct, "scan"

        output = _run_netsh(["show", "interfaces"])
        ssid_match = re.search(r"SSID\s*:\s*(.+)", output)
        ssid = ssid_match.group(1).strip() if ssid_match else "Disconnected"
        signal_match = re.search(r"(\d+)%", output)
        rssi_match = re.search(r"Rssi\s*:\s*(-?\d+)", output, re.I)
        signal_pct = float(signal_match.group(1)) if signal_match else 0.0
        if rssi_match:
            quality = max(0.0, min(255.0, (float(rssi_match.group(1)) + 100.0) * 255.0 / 50.0))
        else:
            quality = signal_pct * 255.0 / 100.0
        return ssid, quality, signal_pct, "netsh"

    def _append_sample(self, ssid: str, quality: float, signal_pct: float, source: str) -> None:
        with self.lock:
            self._append_sample_nolock(ssid, quality, signal_pct, source)

    def _enhance_rubble_quality(self, quality: float) -> float:
        """Amplifica desviaciones respecto a la línea base (señal débil bajo escombros)."""
        if not self.rubble_mode or len(self.quality_values) < 8:
            return quality
        recent = self.quality_values[-min(120, len(self.quality_values)) :]
        baseline = float(np.median(recent))
        return baseline + (quality - baseline) * 2.8

    def _append_sample_nolock(self, ssid: str, quality: float, signal_pct: float, source: str) -> None:
        timestamp = time.time()
        stored_quality = self._enhance_rubble_quality(quality)
        rssi = _quality_to_dbm(stored_quality)

        self.last_ssid = ssid
        self.last_quality = stored_quality
        self.last_signal = signal_pct
        self.last_rssi = rssi
        self.source = source
        self.connected_to_target = bool(
            self.pc_hotspot_mode
            or (
                self.target_ssid
                and ssid == self.target_ssid
                and source.startswith(("wlanapi", "hotspot", "scan", "netsh"))
            )
        )

        self.timestamps.append(timestamp)
        self.rssi_values.append(rssi)
        self.signal_percentages.append(signal_pct)
        self.quality_values.append(stored_quality)

        if len(self.timestamps) > self.max_history:
            self.timestamps.pop(0)
            self.rssi_values.pop(0)
            self.signal_percentages.pop(0)
            self.quality_values.pop(0)

        recent = self.quality_values[-80:]
        self.unique_recent = len(set(round(v, 2) for v in recent))

    def _poll_loop(self) -> None:
        while self.running:
            start_time = time.time()
            try:
                if self.pc_hotspot_mode:
                    self._poll_hotspot_antennas()
                else:
                    ssid, quality, signal_pct, source = self._read_sample()
                    if ssid and (quality > 0 or signal_pct > 0):
                        self._append_sample(ssid, quality, signal_pct, source)
            except Exception as exc:
                print(f"Error leyendo Wi-Fi: {exc}")

            elapsed = time.time() - start_time
            time.sleep(max(0.02, self.poll_interval - elapsed))

    def get_data(self) -> dict[str, Any]:
        with self.lock:
            antennas = [
                {
                    "bssid": stream.bssid,
                    "label": stream.label,
                    "last_rssi": stream.last_rssi,
                    "last_signal": stream.last_signal,
                    "last_quality": stream.last_quality,
                    "source": stream.source,
                    "timestamps": list(stream.timestamps),
                    "rssi": list(stream.rssi_values),
                    "signal_percent": list(stream.signal_percentages),
                    "quality": list(stream.quality_values),
                }
                for stream in self.antenna_streams.values()
            ]
            return {
                "ssid": self.last_ssid,
                "last_rssi": self.last_rssi,
                "last_signal": self.last_signal,
                "last_quality": self.last_quality,
                "source": self.source,
                "connected_to_target": self.connected_to_target,
                "unique_recent": self.unique_recent,
                "active_antenna_count": self.active_antenna_count,
                "antennas": antennas,
                "timestamps": list(self.timestamps),
                "rssi": list(self.rssi_values),
                "signal_percent": list(self.signal_percentages),
                "quality": list(self.quality_values),
            }

    def clear(self) -> None:
        with self.lock:
            self.timestamps.clear()
            self.rssi_values.clear()
            self.signal_percentages.clear()
            self.quality_values.clear()
            self.antenna_streams.clear()
            self.active_antenna_count = 0


if __name__ == "__main__":
    nets = scan_networks()
    print(f"Redes encontradas: {len(nets)}")
    for n in nets:
        print(n.label, n.bssid)
