"""
VitalFi — Detección del hotspot móvil compartido desde este PC (Windows).

Lee la configuración ICS (icssvc) y monitorea la señal del adaptador
Wi-Fi Direct / clientes conectados al punto de acceso del PC.
"""

from __future__ import annotations

import ctypes
import re
import struct
import subprocess
import winreg
from dataclasses import dataclass
from typing import Optional

from windows_wifi import WifiNetwork, _hidden_startupinfo, _run_netsh


ICS_SETTINGS_KEY = r"SYSTEM\CurrentControlSet\Services\icssvc\Settings"
ICS_PRIVATE_VALUE = "PrivateConnectionSettings"
ICS_PUBLIC_VALUE = "PublicConnectionSettings"


@dataclass
class PcHotspotInfo:
    ssid: str
    password: str = ""
    active: bool = False
    band: str = "2.4 GHz"
    adapter_name: str = ""
    client_count: int = 0
    client_rssi: int = 0

    def to_wifi_network(self) -> WifiNetwork:
        signal = max(self.client_rssi, 70 if self.active else 0)
        return WifiNetwork(
            ssid=self.ssid,
            bssid="pc-hotspot",
            signal_pct=min(100, max(0, signal)),
            band=self.band,
            channel=0,
            connected=self.active,
        )


def _parse_ics_private(blob: bytes) -> tuple[str, str]:
    """Extrae SSID y clave del registro ICS (UTF-16LE)."""
    if len(blob) < 8:
        return "", ""

    def read_utf16z(start: int) -> tuple[str, int]:
        chars: list[str] = []
        pos = start
        while pos + 1 < len(blob):
            code = struct.unpack_from("<H", blob, pos)[0]
            pos += 2
            if code == 0:
                break
            chars.append(chr(code))
        return "".join(chars), pos

    ssid, pos = read_utf16z(4)
    while pos + 1 < len(blob) and struct.unpack_from("<H", blob, pos)[0] == 0:
        pos += 2
    password, _ = read_utf16z(pos)
    return ssid, password


def _decode_utf16_strings(blob: bytes) -> list[str]:
    ssid, password = _parse_ics_private(blob)
    out = [s for s in (ssid, password) if s]
    return out


def _read_registry_binary(name: str) -> Optional[bytes]:
    try:
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, ICS_SETTINGS_KEY) as key:
            value, _ = winreg.QueryValueEx(key, name)
            if isinstance(value, bytes):
                return value
    except OSError:
        return None
    return None


def _get_hotspot_ssid_from_registry() -> str:
    private = _read_registry_binary(ICS_PRIVATE_VALUE)
    if not private:
        return ""
    strings = _decode_utf16_strings(private)
    return strings[0] if strings else ""


def get_pc_hotspot_info() -> Optional[PcHotspotInfo]:
    """Lee SSID del hotspot configurado en este PC y si está activo."""
    ssid = _get_hotspot_ssid_from_registry()
    if not ssid:
        return None

    private = _read_registry_binary(ICS_PRIVATE_VALUE) or b""
    strings = _decode_utf16_strings(private)
    password = strings[1] if len(strings) > 1 else ""
    active = _is_hotspot_active()
    adapter = _find_wifi_direct_adapter_name()
    clients = _get_hotspot_clients(ssid) if active else []

    client_rssi = 0
    if clients:
        client_rssi = max(c["signal_pct"] for c in clients)

    return PcHotspotInfo(
        ssid=ssid,
        password=password,
        active=active,
        adapter_name=adapter,
        client_count=len(clients),
        client_rssi=client_rssi,
    )


def _is_hotspot_active() -> bool:
    """Detecta si el hotspot móvil está transmitiendo."""
    output = subprocess.run(
        ["netsh", "interface", "show", "interface"],
        capture_output=True,
        text=True,
        encoding="cp850",
        startupinfo=_hidden_startupinfo(),
        check=False,
    ).stdout or ""

    for line in output.splitlines():
        lower = line.lower()
        if "wi-fi direct" in lower or "wifi direct" in lower:
            if "conectado" in lower or "connected" in lower:
                return True

    public = _read_registry_binary(ICS_PUBLIC_VALUE)
    if public and len(public) > 8:
        strings = _decode_utf16_strings(public)
        if strings and strings[0]:
            return True

    return False


def _find_wifi_direct_adapter_name() -> str:
    output = subprocess.run(
        ["netsh", "interface", "show", "interface"],
        capture_output=True,
        text=True,
        encoding="cp850",
        startupinfo=_hidden_startupinfo(),
        check=False,
    ).stdout or ""

    for line in output.splitlines():
        if "Direct" in line or "direct" in line:
            parts = line.split()
            if len(parts) >= 4:
                return " ".join(parts[3:])
    return "Microsoft Wi-Fi Direct Virtual Adapter"


def _get_arp_hotspot_clients() -> list[dict]:
    """Clientes en la subred típica del hotspot móvil de Windows (192.168.137.x)."""
    output = subprocess.run(
        ["arp", "-a"],
        capture_output=True,
        text=True,
        encoding="cp850",
        startupinfo=_hidden_startupinfo(),
        check=False,
    ).stdout or ""

    clients: list[dict] = []
    for line in output.splitlines():
        match = re.search(
            r"(\d+\.\d+\.\d+\.\d+)\s+([0-9a-fA-F\-]{17})\s+(din[aá]mico|dynamic)",
            line,
            re.I,
        )
        if not match:
            continue
        ip, mac_raw, _kind = match.groups()
        if not ip.startswith("192.168.137."):
            continue
        if ip.endswith(".255") or ip.endswith(".1"):
            continue
        bssid = mac_raw.replace("-", ":").lower()
        short = bssid[-8:].replace(":", "")
        clients.append(
            {
                "ssid": f"Rescatista {short}",
                "label": f"Antena {short}",
                "bssid": bssid,
                "rssi": -58,
                "signal_pct": 55,
                "interface": "arp",
                "ip": ip,
            }
        )
    return clients


def _merge_hotspot_clients(wlan_clients: list[dict], arp_clients: list[dict]) -> list[dict]:
    """Une clientes WLAN y ARP por MAC; prioriza RSSI medido."""
    merged: dict[str, dict] = {}
    for client in arp_clients + wlan_clients:
        bssid = (client.get("bssid") or "").lower()
        if not bssid:
            continue
        existing = merged.get(bssid)
        if existing is None or client.get("signal_pct", 0) > existing.get("signal_pct", 0):
            merged[bssid] = dict(client)
        elif client.get("label") and not existing.get("label"):
            existing["label"] = client["label"]
    return list(merged.values())


def _get_hotspot_clients(hotspot_ssid: str = "") -> list[dict]:
    """Obtiene clientes conectados al hotspot (WLAN + tabla ARP)."""
    if not hotspot_ssid:
        hotspot_ssid = _get_hotspot_ssid_from_registry()
    wlan_clients: list[dict] = []
    try:
        wlan_clients = _wlan_bss_clients(hotspot_ssid)
    except OSError:
        pass
    arp_clients = _get_arp_hotspot_clients()
    return _merge_hotspot_clients(wlan_clients, arp_clients)


def _wlan_bss_clients(hotspot_ssid: str) -> list[dict]:
    from ctypes import POINTER, Structure, byref, c_void_p, wintypes

    wlanapi = ctypes.windll.wlanapi

    class GUID(Structure):
        _fields_ = [
            ("Data1", wintypes.DWORD),
            ("Data2", wintypes.WORD),
            ("Data3", wintypes.WORD),
            ("Data4", wintypes.BYTE * 8),
        ]

    class DOT11_SSID(Structure):
        _fields_ = [("uSSIDLength", wintypes.DWORD), ("ucSSID", wintypes.BYTE * 32)]

    class WLAN_INTERFACE_INFO(Structure):
        _fields_ = [
            ("InterfaceGuid", GUID),
            ("strInterfaceDescription", wintypes.WCHAR * 256),
            ("isState", wintypes.DWORD),
        ]

    class WLAN_INTERFACE_INFO_LIST(Structure):
        _fields_ = [("dwNumberOfItems", wintypes.DWORD), ("dwIndex", wintypes.DWORD)]

    class WLAN_RATE_SET(Structure):
        _fields_ = [
            ("uRateSetLength", wintypes.DWORD),
            ("usRateSet", wintypes.USHORT * 126),
        ]

    class WLAN_BSS_ENTRY(Structure):
        _fields_ = [
            ("dot11Ssid", DOT11_SSID),
            ("uPhyId", wintypes.ULONG),
            ("dot11Bssid", wintypes.BYTE * 6),
            ("dot11BssType", wintypes.DWORD),
            ("dot11BssPhyType", wintypes.DWORD),
            ("lRssi", ctypes.c_long),
            ("uLinkQuality", wintypes.ULONG),
            ("bInRegDomain", wintypes.BOOL),
            ("usBeaconPeriod", wintypes.USHORT),
            ("ullTimestamp", ctypes.c_ulonglong),
            ("ullHostTimestamp", ctypes.c_ulonglong),
            ("usCapabilityInformation", wintypes.USHORT),
            ("ulChCenterFrequency", wintypes.ULONG),
            ("wlanRateSet", WLAN_RATE_SET),
            ("ulIeOffset", wintypes.ULONG),
            ("ulIeSize", wintypes.ULONG),
        ]

    class WLAN_BSS_LIST(Structure):
        _fields_ = [("dwTotalSize", wintypes.DWORD), ("dwNumberOfItems", wintypes.DWORD)]

    handle = ctypes.c_void_p()
    negotiated = wintypes.DWORD()
    if wlanapi.WlanOpenHandle(2, None, byref(negotiated), byref(handle)) != 0:
        raise OSError("WlanOpenHandle falló")

    clients: list[dict] = []
    try:
        iface_list = c_void_p()
        if wlanapi.WlanEnumInterfaces(handle, None, byref(iface_list)) != 0:
            return []

        count = ctypes.cast(iface_list, POINTER(WLAN_INTERFACE_INFO_LIST)).contents.dwNumberOfItems
        base = iface_list.value + 8
        iface_size = ctypes.sizeof(WLAN_INTERFACE_INFO)

        hotspot_ssid_lower = hotspot_ssid.lower()

        for i in range(count):
            iface = WLAN_INTERFACE_INFO.from_address(base + i * iface_size)
            desc = iface.strInterfaceDescription.lower()
            if "direct" not in desc and i > 0:
                continue

            bss_list = c_void_p()
            # dot11BssTypeAny = 3
            rc = wlanapi.WlanGetNetworkBssList(
                handle, byref(iface.InterfaceGuid), None, 3, True, None, byref(bss_list)
            )
            if rc != 0 or not bss_list.value:
                continue

            hdr = ctypes.cast(bss_list, POINTER(WLAN_BSS_LIST)).contents
            entry_base = bss_list.value + ctypes.sizeof(WLAN_BSS_LIST)
            entry_size = ctypes.sizeof(WLAN_BSS_ENTRY)

            for j in range(hdr.dwNumberOfItems):
                entry = WLAN_BSS_ENTRY.from_address(entry_base + j * entry_size)
                ssid = bytes(entry.dot11Ssid.ucSSID[: entry.dot11Ssid.uSSIDLength]).decode(
                    "utf-8", "replace"
                )
                bssid = ":".join(f"{b:02x}" for b in entry.dot11Bssid)
                rssi = int(entry.lRssi)
                signal_pct = max(0, min(100, int((rssi + 100) * 2)))

                if hotspot_ssid_lower and ssid.lower() == hotspot_ssid_lower:
                    continue  # es el propio AP, no un cliente

                clients.append(
                    {
                        "ssid": ssid or bssid,
                        "label": f"Antena {bssid[-8:].replace(':', '')}",
                        "bssid": bssid,
                        "rssi": rssi,
                        "signal_pct": signal_pct,
                        "interface": iface.strInterfaceDescription,
                    }
                )

            wlanapi.WlanFreeMemory(bss_list)

        wlanapi.WlanFreeMemory(iface_list)
    finally:
        wlanapi.WlanCloseHandle(handle, None)

    return clients


def get_pc_hotspot_network_entry() -> Optional[WifiNetwork]:
    """Entrada especial para listas de redes: hotspot de este PC."""
    info = get_pc_hotspot_info()
    if not info:
        return None
    net = info.to_wifi_network()
    return net


def print_hotspot_setup_guide() -> None:
    print(
        """
================================================================
  MODO HOTSPOT — CADA RESCATISTA ES UNA ANTENA
================================================================

  Esquema:

       [ TU PC = hotspot central ]
          /      |      \\
    [Cel 1]   [Cel 2]   [Cel 3]   ... cada uno conectado al hotspot
        \\       |       /
         [ ESCOMBROS / persona ]

  Cada celular conectado al hotspot actua como antena Wi-Fi
  alrededor del escombro. VitalFi analiza la senal de TODOS
  a la vez para detectar respiracion y triangular la posicion.

  Pasos:
  1. Configuracion > Red e Internet > Hotspot movil > ACTIVAR
  2. Cada rescatista conecta su celular al hotspot
  3. Colocan los celulares alrededor del escombro (~1 m, separados)
  4. Ejecuta: python main.py hotspot

================================================================
"""
    )


def print_hotspot_guide(info: PcHotspotInfo) -> None:
    state = "ACTIVO" if info.active else "APAGADO (activalo en Configuracion > Hotspot movil)"
    print(
        f"\n  HOTSPOT DE ESTE PC: {info.ssid}\n"
        f"  Estado: {state}\n"
        f"  Adaptador: {info.adapter_name}\n"
    )
    if info.active:
        if info.client_count:
            print(
                f"  Antenas conectadas: {info.client_count} "
                f"(senal max ~{info.client_rssi}%)"
            )
            print(
                "  Cada celular es un sensor. Distribuyelos alrededor del escombro."
            )
        else:
            print(
                "  Sin antenas conectadas.\n"
                "  Tip: cada rescatista conecta su celular al hotspot y lo coloca\n"
                "       cerca del escombro (~1 m). Mas antenas = mejor triangulacion."
            )
    else:
        print(
            "  Para usarlo:\n"
            "  1. Configuracion > Red e Internet > Hotspot movil > Activar\n"
            f"  2. El SSID sera: {info.ssid}\n"
            "  3. Vuelve a ejecutar VitalFi y selecciona [HOTSPOT DE ESTE PC]\n"
        )


def choose_pc_hotspot(ssid: Optional[str] = None) -> WifiNetwork:
    info = get_pc_hotspot_info()
    if not info:
        raise RuntimeError(
            "No se encontro hotspot configurado en este PC.\n"
            "Configuralo en: Configuracion > Red e Internet > Hotspot movil"
        )

    if ssid and info.ssid.lower() != ssid.lower():
        raise RuntimeError(f"El hotspot de este PC se llama '{info.ssid}', no '{ssid}'.")

    print_hotspot_guide(info)

    if not info.active:
        answer = input("El hotspot esta apagado. Continuar en modo preparado? [s/N]: ").strip().lower()
        if answer not in ("s", "si", "y", "yes"):
            print("Activa el hotspot movil y vuelve a ejecutar.")
            raise SystemExit(0)

    net = info.to_wifi_network()
    print(
        f"\n[OK] Monitoreando hotspot del PC: {info.ssid}\n"
        "Cada celular conectado al hotspot sera una antena de deteccion.\n"
        "Distribuye los celulares alrededor del escombro.\n"
    )
    return net
