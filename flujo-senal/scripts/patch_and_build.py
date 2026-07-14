#!/usr/bin/env python3
"""Rebuild FLUJO TV as SEÑAL: same UI/code, brand + colors + data origin only."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VENDOR_APK = ROOT / "vendor" / "flujo-tv-862.apk"
OUT_DIR = ROOT / "dist"
COLOR_FROM = b"dc4800"
COLOR_TO = b"7c3aed"
COLOR_FROM_UP = b"DC4800"
COLOR_TO_UP = b"7C3AED"
HOST_FROM = b"adfereredadasww.ai"
HOST_TO = b"senalapi-origen.tv"
assert len(HOST_FROM) == len(HOST_TO)
assert len(COLOR_FROM) == len(COLOR_TO)


def run(cmd: list[str], cwd: Path | None = None) -> None:
    print("+", " ".join(cmd))
    subprocess.check_call(cmd, cwd=str(cwd) if cwd else None)


def replace_bytes(data: bytes, old: bytes, new: bytes) -> tuple[bytes, int]:
    if len(old) != len(new):
        raise ValueError(f"length mismatch {old!r} -> {new!r}")
    count = data.count(old)
    return data.replace(old, new), count


def patch_xml_tree(decoded: Path) -> None:
    # Strings: FLUJO -> SEÑAL
    replacements = {
        "FLUJO": "SEÑAL",
        "Flujotv": "Señaltv",
        "FlujoTV": "SeñalTV",
        "Flujo": "Señal",
        "flujo": "señal",
    }
    for strings in decoded.glob("res/values*/strings.xml"):
        text = strings.read_text(encoding="utf-8")
        orig = text
        for old, new in replacements.items():
            text = text.replace(old, new)
        if text != orig:
            strings.write_text(text, encoding="utf-8")
            print(f"patched strings {strings.relative_to(decoded)}")

    # Colors #dc4800 family -> SEÑAL purple #7C3AED
    color_swaps = [
        ("#ffdc4800", "#ff7c3aed"),
        ("#e4dc4800", "#e47c3aed"),
        ("#80dc4800", "#807c3aed"),
        ("#dc4800", "#7c3aed"),
        ("#ffff6d27", "#ff7c3aed"),
        ("#ffe75946", "#ff7c3aed"),
        ("#ffdc4800".upper(), "#ff7c3aed".upper()),
    ]
    xml_files = list((decoded / "res").rglob("*.xml"))
    patched = 0
    for xml in xml_files:
        text = xml.read_text(encoding="utf-8")
        orig = text
        for old, new in color_swaps:
            text = text.replace(old, new)
            text = text.replace(old.upper(), new.upper())
            text = text.replace(old.lower(), new.lower())
        if text != orig:
            xml.write_text(text, encoding="utf-8")
            patched += 1
    print(f"patched {patched} xml color/brand files")

    # apktool.yml label
    yml = decoded / "apktool.yml"
    if yml.exists():
        y = yml.read_text(encoding="utf-8")
        y = y.replace("flujo-tv-reference.apk", "SenalTV-flujo-fork.apk")
        yml.write_text(y, encoding="utf-8")


def patch_dex(dex_path: Path) -> None:
    data = dex_path.read_bytes()
    total = 0
    for old, new in [
        (HOST_FROM, HOST_TO),
        (b"flujo_protocol", b"senal_protocol"),
        (b"app_flujo", b"app_senal"),
        (COLOR_FROM, COLOR_TO),
        (COLOR_FROM_UP, COLOR_TO_UP),
        (b"#DC4800", b"#7C3AED"),
        (b"#dc4800", b"#7c3aed"),
    ]:
        data, n = replace_bytes(data, old, new)
        total += n
        print(f"dex replace {old.decode(errors='ignore')} x{n}")
    dex_path.write_bytes(data)
    print(f"dex total replacements: {total}")


def ensure_network_cleartext(decoded: Path) -> None:
    nsc = decoded / "res/xml/network_security_config.xml"
    if not nsc.exists():
        return
    text = nsc.read_text(encoding="utf-8")
    if "senalapi-origen.tv" not in text:
        # allow cleartext to SEÑAL bridge / IP
        inject = """
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">senalapi-origen.tv</domain>
        <domain includeSubdomains="true">185.192.20.245</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
"""
        text = text.replace("</network-security-config>", inject + "</network-security-config>")
        nsc.write_text(text, encoding="utf-8")
        print("updated network_security_config for SEÑAL origins")


def build(apktool: str, keystore: Path, alias: str, storepass: str) -> Path:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if not VENDOR_APK.exists():
        raise SystemExit(f"missing vendor apk: {VENDOR_APK}")

    with tempfile.TemporaryDirectory(prefix="senal-fork-") as tmp:
        tmp_path = Path(tmp)
        decoded = tmp_path / "decoded"
        unsigned = tmp_path / "unsigned.apk"
        aligned = OUT_DIR / "SenalTV-unsigned-aligned.apk"
        signed = OUT_DIR / "SenalTV.apk"

        run([apktool, "d", "-f", "-o", str(decoded), str(VENDOR_APK)])
        patch_xml_tree(decoded)
        ensure_network_cleartext(decoded)

        # Keep original classes.dex: apktool only has stub smali; inject patched dex after build
        run([apktool, "b", "--use-aapt2", "-o", str(unsigned), str(decoded)])

        # Replace classes.dex inside rebuilt apk with patched original dex
        dex_src = tmp_path / "classes.dex"
        with zipfile.ZipFile(VENDOR_APK, "r") as zin:
            dex_src.write_bytes(zin.read("classes.dex"))
        patch_dex(dex_src)

        patched_apk = tmp_path / "patched.apk"
        with zipfile.ZipFile(unsigned, "r") as zin, zipfile.ZipFile(
            patched_apk, "w", compression=zipfile.ZIP_DEFLATED
        ) as zout:
            for info in zin.infolist():
                data = zin.read(info.filename)
                if info.filename == "classes.dex":
                    data = dex_src.read_bytes()
                # store dex/arsc/native without recompression when original was STORED
                compress = (
                    zipfile.ZIP_STORED
                    if info.filename.endswith((".so", ".arsc", ".dex", ".mp4"))
                    else zipfile.ZIP_DEFLATED
                )
                zout.writestr(info.filename, data, compress_type=compress)

        zipalign = shutil.which("zipalign") or str(
            Path.home() / "Android/Sdk/build-tools/35.0.0/zipalign"
        )
        apksigner = shutil.which("apksigner") or str(
            Path.home() / "Android/Sdk/build-tools/35.0.0/apksigner"
        )
        run([zipalign, "-f", "4", str(patched_apk), str(aligned)])
        run(
            [
                apksigner,
                "sign",
                "--ks",
                str(keystore),
                "--ks-key-alias",
                alias,
                "--ks-pass",
                f"pass:{storepass}",
                "--key-pass",
                f"pass:{storepass}",
                "--out",
                str(signed),
                str(aligned),
            ]
        )
        run([apksigner, "verify", str(signed)])
        print(f"OK -> {signed} ({signed.stat().st_size} bytes)")
        return signed


def ensure_keystore(path: Path, alias: str, storepass: str) -> None:
    if path.exists():
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    run(
        [
            "keytool",
            "-genkeypair",
            "-v",
            "-keystore",
            str(path),
            "-alias",
            alias,
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "10000",
            "-storepass",
            storepass,
            "-keypass",
            storepass,
            "-dname",
            "CN=Senal TV, OU=TV, O=Senal, L=City, S=ST, C=US",
        ]
    )


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--apktool", default=shutil.which("apktool") or "apktool")
    p.add_argument("--keystore", type=Path, default=ROOT / "branding" / "senal.keystore")
    p.add_argument("--alias", default="senal")
    p.add_argument("--storepass", default="senaltv")
    args = p.parse_args()
    ensure_keystore(args.keystore, args.alias, args.storepass)
    build(args.apktool, args.keystore, args.alias, args.storepass)


if __name__ == "__main__":
    main()
