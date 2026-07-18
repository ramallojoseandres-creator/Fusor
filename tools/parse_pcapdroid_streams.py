#!/usr/bin/env python3
"""
Extract unique Xuper-style HLS stream URLs from PCAPdroid exports.

Supports:
  - PCAPdroid CSV (connections list)
  - Plain text dumps with "GET /path HTTP/1.1" + "Host: ip:port"
  - Mixed logs / pasted captures

Usage:
  python3 tools/parse_pcapdroid_streams.py capture.csv
  python3 tools/parse_pcapdroid_streams.py capture.csv payload1.txt payload2.txt -o dist/xuper_captured.m3u
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

GET_RE = re.compile(
    r"GET\s+(/(?:[A-Za-z0-9._~-]+/)+\S*?)\s+HTTP/\d",
    re.IGNORECASE,
)
HOST_RE = re.compile(
    r"Host:\s*([^\s:]+)(?::(\d+))?",
    re.IGNORECASE,
)
URL_RE = re.compile(
    r"https?://(\d{1,3}(?:\.\d{1,3}){3}):(\d+)/([A-Za-z0-9._~/-]+)",
    re.IGNORECASE,
)
# Relative stream paths like /goqqc1m/jgbhpq or /cjii/fvwthpscddfk1f
PATH_RE = re.compile(r"(?<![\w/])(/[A-Za-z0-9._~-]{2,40}/[A-Za-z0-9._~-]{2,80})(?![\w./])")

DEFAULT_PORT = "12341"
NOISE_PREFIXES = (
    "/api/",
    "/static/",
    "/assets/",
    "/favicon",
    "/css/",
    "/js/",
    "/img/",
)


def looks_like_stream_path(path: str) -> bool:
    path = path.split("?", 1)[0]
    if not path.startswith("/") or path.count("/") < 2:
        return False
    low = path.lower()
    if any(low.startswith(p) for p in NOISE_PREFIXES):
        return False
    if low.endswith((".ts", ".jpg", ".png", ".gif", ".css", ".js", ".ico", ".woff", ".mp4")):
        return False
    # Xuper live paths are usually two opaque segments, sometimes more for variants
    parts = [p for p in path.strip("/").split("/") if p]
    if len(parts) < 2 or len(parts) > 4:
        return False
    # Reject obvious English words / long readable paths
    joined = "".join(parts)
    if re.search(r"(channel|live|movie|series|epg|playlist|player)", joined, re.I):
        return True
    # Prefer opaque ids (mixed alnum, no spaces)
    return all(re.fullmatch(r"[A-Za-z0-9._~-]{2,80}", p) for p in parts)


def add_stream(found: dict[str, str], host: str, port: str, path: str) -> None:
    path = path.split("?", 1)[0]
    if not looks_like_stream_path(path):
        return
    if not host:
        return
    port = port or DEFAULT_PORT
    url = f"http://{host}:{port}{path}"
    found.setdefault(url, path)


def parse_text(text: str, found: dict[str, str], default_host: str = "", default_port: str = DEFAULT_PORT) -> None:
    host = default_host
    port = default_port
    pending_path = None

    for raw in text.splitlines():
        line = raw.strip("\x00").strip()
        m_host = HOST_RE.search(line)
        if m_host:
            host = m_host.group(1)
            port = m_host.group(2) or default_port
            if pending_path:
                add_stream(found, host, port, pending_path)
                pending_path = None
            continue

        m_get = GET_RE.search(line)
        if m_get:
            pending_path = m_get.group(1)
            if host:
                add_stream(found, host, port, pending_path)
                pending_path = None
            continue

        for m in URL_RE.finditer(line):
            add_stream(found, m.group(1), m.group(2), "/" + m.group(3).lstrip("/"))

        # CSV Info / URL columns sometimes contain host:port/path
        m_hp = re.search(
            r"(\d{1,3}(?:\.\d{1,3}){3}):(\d+)/([A-Za-z0-9._~/-]+)",
            line,
        )
        if m_hp:
            add_stream(found, m_hp.group(1), m_hp.group(2), "/" + m_hp.group(3).lstrip("/"))


def parse_csv(path: Path, found: dict[str, str]) -> None:
    with path.open(newline="", encoding="utf-8", errors="replace") as f:
        # Detect if it is really CSV
        sample = f.read(4096)
        f.seek(0)
        if "DstIp" not in sample and "DstPort" not in sample and "Info" not in sample:
            parse_text(f.read(), found)
            return
        reader = csv.DictReader(f)
        for row in reader:
            info = (row.get("Info") or row.get("URL") or "").strip()
            dst = (row.get("DstIp") or "").strip()
            port = (row.get("DstPort") or "").strip()
            proto = (row.get("Proto") or "").strip().upper()
            # Prefer HTTP(S) rows on stream port
            if port == DEFAULT_PORT or DEFAULT_PORT in info:
                if info.startswith("http://") or info.startswith("https://"):
                    u = urlparse(info)
                    add_stream(found, u.hostname or dst, str(u.port or port or DEFAULT_PORT), u.path or "/")
                elif "/" in info and not info.startswith("http"):
                    # host:port/path or just path-ish in Info
                    if re.match(r"\d+\.\d+\.\d+\.\d+", info):
                        parse_text(f"http://{info}\n", found)
                    else:
                        parse_text(info + "\n", found, default_host=dst, default_port=port or DEFAULT_PORT)
                elif dst and port == DEFAULT_PORT and proto in {"HTTP", "TCP", ""}:
                    # Connection exists but path unknown — skip (need payload/HTTP export)
                    pass
            # Also scan all text fields for embedded URLs/paths
            blob = " ".join(str(v) for v in row.values() if v)
            parse_text(blob, found, default_host=dst, default_port=port or DEFAULT_PORT)


def write_m3u(urls: list[str], out: Path) -> None:
    lines = ["#EXTM3U", "#PLAYLIST:Xuper captured streams (cookie required)"]
    for i, url in enumerate(urls, 1):
        path = urlparse(url).path.strip("/")
        name = path.replace("/", " · ") or f"Stream {i}"
        lines.append(f"#EXTINF:-1 tvg-id=\"{i}\" group-title=\"Xuper Capturado\",{name}")
        lines.append(url)
        lines.append("")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description="Parse PCAPdroid exports for Xuper HLS paths")
    ap.add_argument("inputs", nargs="+", type=Path, help="CSV / TXT / log files")
    ap.add_argument("-o", "--output", type=Path, default=Path("dist/xuper_captured.m3u"))
    ap.add_argument("--host", default="", help="Default host if missing in dump")
    ap.add_argument("--port", default=DEFAULT_PORT, help="Default stream port")
    args = ap.parse_args()

    found: dict[str, str] = {}
    for p in args.inputs:
        if not p.exists():
            print(f"missing: {p}", file=sys.stderr)
            continue
        data = p.read_bytes()
        # Skip pure TLS binary dumps
        if data[:3] == b"\x16\x03" and b"GET " not in data[:2048]:
            print(f"skip TLS binary: {p}", file=sys.stderr)
            continue
        text = data.decode("utf-8", errors="replace")
        if p.suffix.lower() == ".csv" or "DstIp," in text[:200]:
            parse_csv(p, found)
        else:
            parse_text(text, found, default_host=args.host, default_port=args.port)

    urls = sorted(found.keys())
    print(f"unique streams: {len(urls)}")
    for u in urls:
        print(u)
    if urls:
        write_m3u(urls, args.output)
        print(f"wrote {args.output}")
        print("NOTE: these URLs need the live Cookie header from Xuper to play.")
    else:
        print(
            "No stream paths found.\n"
            "Export PCAPdroid CSV after browsing many channels, and/or paste HTTP dumps "
            "containing GET + Host lines for port 12341.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
