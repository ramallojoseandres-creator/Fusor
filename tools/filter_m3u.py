#!/usr/bin/env python3
"""
Headless playlist health filter inspired by kamalsoft/m3u-editor.

Uses the same approaches as:
  - FastM3UParser (performance_utils.py)
  - ValidationWorker.check_url (m3u_editor.py)
  - Smart URL dedupe (keep richest EXTINF metadata)

Source: https://github.com/kamalsoft/m3u-editor

Usage:
  python3 tools/filter_m3u.py lista_fusionada.m3u -o lista_fusionada.m3u
"""

from __future__ import annotations

import argparse
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple


DEFAULT_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
)


@dataclass
class M3UEntry:
    name: str = ""
    url: str = ""
    group: str = ""
    logo: str = ""
    tvg_id: str = ""
    tvg_chno: str = ""
    duration: str = "-1"
    user_agent: Optional[str] = None
    raw_extinf: str = ""
    extra_tags: List[str] = field(default_factory=list)

    def richness(self) -> int:
        score = 0
        if self.logo:
            score += 3
        if self.tvg_id:
            score += 2
        if self.group:
            score += 1
        if self.user_agent:
            score += 1
        if self.name and self.name != "Unknown":
            score += 1
        score += min(len(self.raw_extinf), 200) // 40
        return score

    def to_m3u_block(self) -> str:
        lines: List[str] = []
        if self.raw_extinf:
            lines.append(self.raw_extinf.rstrip("\r"))
        else:
            attrs = [f'#EXTINF:{self.duration or "-1"}']
            if self.tvg_id:
                attrs.append(f'tvg-id="{self.tvg_id}"')
            if self.logo:
                attrs.append(f'tvg-logo="{self.logo}"')
            if self.group:
                attrs.append(f'group-title="{self.group}"')
            lines.append(" ".join(attrs) + f",{self.name or self.url}")
        lines.extend(self.extra_tags)
        lines.append(self.url)
        return "\n".join(lines)


class FastM3UParser:
    """Regex-light parser aligned with kamalsoft/m3u-editor FastM3UParser."""

    @staticmethod
    def parse_lines(lines: List[str]) -> List[M3UEntry]:
        entries: List[M3UEntry] = []
        current = M3UEntry()
        pending_tags: List[str] = []

        for raw in lines:
            line = raw.strip()
            if not line:
                continue
            if line.startswith("#EXTM3U"):
                continue

            if line.startswith("#EXTINF:"):
                current = M3UEntry(raw_extinf=line, extra_tags=pending_tags)
                pending_tags = []
                parts = line.split(",", 1)
                if len(parts) > 1:
                    current.name = parts[1].strip()
                attr_part = parts[0]
                dur = re.search(r"#EXTINF:([-0-9]+)", attr_part)
                if dur:
                    current.duration = dur.group(1)
                for attr, field_name in (
                    ("group-title", "group"),
                    ("tvg-logo", "logo"),
                    ("tvg-id", "tvg_id"),
                    ("tvg-chno", "tvg_chno"),
                    ("http-user-agent", "user_agent"),
                ):
                    match = re.search(rf'{attr}="([^"]*)"', attr_part, re.I)
                    if match:
                        setattr(current, field_name, match.group(1))
                continue

            if line.startswith("#EXTVLCOPT:"):
                low = line.lower()
                if "http-user-agent=" in low:
                    current.user_agent = line.split("=", 1)[1].strip()
                else:
                    current.extra_tags.append(line)
                continue

            if line.startswith("#"):
                # Keep companion directives with the next URL when possible.
                pending_tags.append(line)
                continue

            url = line
            if current.raw_extinf or current.name:
                current.url = url
                if pending_tags:
                    current.extra_tags.extend(pending_tags)
                    pending_tags = []
                entries.append(current)
                current = M3UEntry()
            else:
                e = M3UEntry(name="Unknown", url=url, extra_tags=pending_tags)
                pending_tags = []
                entries.append(e)

        return entries


def check_url(url: str, user_agent: Optional[str] = None, timeout: float = 5.0) -> Tuple[bool, str]:
    """Health check inspired by m3u-editor ValidationWorker (HEAD, GET fallback).

    IPTV CDNs often reject HEAD with 404/403 while GET (playlist body) works,
    so any HEAD failure retries with a ranged GET — same spirit as fusor.sh.
    """
    headers = {"User-Agent": user_agent if user_agent else DEFAULT_UA}

    def try_get() -> Tuple[bool, str]:
        req = urllib.request.Request(url, headers={**headers, "Range": "bytes=0-0"}, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as response:
                response.read(256)
                if 200 <= response.status < 400 or response.status in (405, 416):
                    return True, f"OK GET ({response.status})"
                return False, f"GET status: {response.status}"
        except urllib.error.HTTPError as e:
            if e.code in (200, 206, 405, 416):
                return True, f"OK GET ({e.code})"
            # Last try without Range
            try:
                req2 = urllib.request.Request(url, headers=headers, method="GET")
                with urllib.request.urlopen(req2, timeout=timeout) as response:
                    response.read(256)
                    if 200 <= response.status < 400:
                        return True, f"OK GET ({response.status})"
                    return False, f"GET status: {response.status}"
            except Exception as inner:
                return False, f"HTTP {e.code} / {inner}"
        except Exception as e:
            return False, f"GET error: {e}"

    try:
        req = urllib.request.Request(url, headers=headers, method="HEAD")
        with urllib.request.urlopen(req, timeout=timeout) as response:
            if 200 <= response.status < 400:
                return True, f"OK ({response.status})"
            return try_get()
    except urllib.error.HTTPError:
        return try_get()
    except Exception:
        return try_get()


def dedupe_by_url(entries: List[M3UEntry]) -> List[M3UEntry]:
    best: Dict[str, M3UEntry] = {}
    order: List[str] = []
    for e in entries:
        key = e.url.strip()
        if not key:
            continue
        if key not in best:
            best[key] = e
            order.append(key)
        elif e.richness() > best[key].richness():
            best[key] = e
    return [best[k] for k in order]


def filter_playlist(
    entries: List[M3UEntry],
    workers: int = 80,
    timeout: float = 5.0,
) -> Tuple[List[M3UEntry], int, int]:
    alive: List[Optional[M3UEntry]] = [None] * len(entries)
    ok = 0
    dead = 0
    done = 0
    total = len(entries)
    t0 = time.time()

    def job(idx: int, entry: M3UEntry) -> Tuple[int, bool, str]:
        valid, msg = check_url(entry.url, entry.user_agent, timeout=timeout)
        return idx, valid, msg

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(job, i, e) for i, e in enumerate(entries)]
        for fut in as_completed(futures):
            idx, valid, msg = fut.result()
            done += 1
            if valid:
                alive[idx] = entries[idx]
                ok += 1
            else:
                dead += 1
            if done % 200 == 0 or done == total:
                elapsed = time.time() - t0
                rate = done / elapsed if elapsed else 0
                print(
                    f"  … {done}/{total}  vivos={ok} muertos={dead}  ({rate:.1f}/s)",
                    file=sys.stderr,
                )

    return [e for e in alive if e is not None], ok, dead


def write_m3u(path: Path, entries: List[M3UEntry]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as f:
        f.write("#EXTM3U\n")
        f.write("# Filtered with tools/filter_m3u.py (kamalsoft/m3u-editor health check)\n")
        for e in entries:
            f.write(e.to_m3u_block())
            f.write("\n")


def main() -> int:
    ap = argparse.ArgumentParser(description="Filter M3U with m3u-editor-style health checks")
    ap.add_argument("input", type=Path)
    ap.add_argument("-o", "--output", type=Path, required=True)
    ap.add_argument("-j", "--workers", type=int, default=80)
    ap.add_argument("-t", "--timeout", type=float, default=5.0)
    ap.add_argument("--skip-validate", action="store_true", help="Only parse + dedupe")
    args = ap.parse_args()

    text = args.input.read_text(encoding="utf-8", errors="replace")
    entries = FastM3UParser.parse_lines(text.splitlines())
    print(f"Parsed {len(entries)} entries from {args.input}", file=sys.stderr)

    before = len(entries)
    entries = dedupe_by_url(entries)
    print(f"Deduped URL: {before} → {len(entries)}", file=sys.stderr)

    if not args.skip_validate:
        print(
            f"Health-checking {len(entries)} streams "
            f"(workers={args.workers}, timeout={args.timeout}s)…",
            file=sys.stderr,
        )
        entries, ok, dead = filter_playlist(entries, workers=args.workers, timeout=args.timeout)
        print(f"Kept {ok} · dropped {dead}", file=sys.stderr)

    write_m3u(args.output, entries)
    print(f"Wrote {len(entries)} channels → {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
