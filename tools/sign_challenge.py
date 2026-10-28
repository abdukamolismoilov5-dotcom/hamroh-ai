#!/usr/bin/env python3
"""Ilova ko'rsatgan mualliflik so'rovini (yoki istalgan matnni) egasining yopiq kaliti bilan imzolaydi.

    python sign_challenge.py --key owner_private.pem "HAMROH-AUTH-v1|...|...|...|..."
    python sign_challenge.py --key owner_private.pem --file RELEASES.md     # fayl matnini imzolash

Internetga ulanmaydi. Chiqqan imzoni ilovaning "Mualliflikni tekshirish" oynasiga kiriting.
"""
import argparse
import getpass
import os
import sys
from pathlib import Path

from hamroh_keys import fingerprint, load_private, parse_challenge, public_raw, sign_text


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--key", required=True, help="owner_private.pem yo'li")
    ap.add_argument("--file", help="imzolanadigan fayl (so'rov o'rniga)")
    ap.add_argument("--qr", action="store_true", help="imzoni terminalda QR-kod ko'rinishida ham chiqarish (qrcode paketi kerak)")
    ap.add_argument("text", nargs="?", help="ilova ko'rsatgan so'rov matni")
    args = ap.parse_args()

    password = os.environ.get("HAMROH_KEY_PASSWORD")
    pw = password.encode() if password is not None else getpass.getpass("Kalit paroli: ").encode()
    key = load_private(args.key, pw or None)

    if args.file:
        text = Path(args.file).read_text(encoding="utf-8")
    elif args.text:
        text = args.text.strip()
        info = parse_challenge(text)
        print(f"So'rov: versiya {info['app_version']}, APK sertifikati {info['cert_sha256'][:16]}...", file=sys.stderr)
    else:
        text = sys.stdin.read().strip()
    sig = sign_text(key, text)
    print("Kalit izi:", fingerprint(public_raw(key)), file=sys.stderr)
    print(sig)
    if args.qr:
        try:
            import qrcode
            q = qrcode.QRCode(border=1)
            q.add_data(sig)
            q.print_ascii(invert=True)
        except ImportError:
            print("QR uchun: pip install qrcode", file=sys.stderr)


if __name__ == "__main__":
    main()
