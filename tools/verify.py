#!/usr/bin/env python3
"""Egasi imzolagan matnni FAQAT ochiq kalit bilan tekshiradi. Istalgan uchinchi shaxs ishlatishi mumkin.

    python verify.py --pub owner.pub --sig <imzo> "imzolangan matn"
    python verify.py --pub owner.pub --sig-file RELEASES.md.sig --file RELEASES.md

Chiqish kodi: 0 — imzo to'g'ri, 1 — noto'g'ri.
"""
import argparse
import sys
from pathlib import Path

from hamroh_keys import fingerprint, load_public, verify_text


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pub", required=True)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--sig")
    g.add_argument("--sig-file")
    ap.add_argument("--file")
    ap.add_argument("text", nargs="?")
    a = ap.parse_args()
    pub = load_public(a.pub)
    sig = a.sig if a.sig else Path(a.sig_file).read_text().strip()
    text = Path(a.file).read_text(encoding="utf-8") if a.file else (a.text or sys.stdin.read()).strip()
    ok = verify_text(pub, text, sig)
    print(("TO'G'RI" if ok else "NOTO'G'RI") + f" — kalit izi {fingerprint(pub)}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
