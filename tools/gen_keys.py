#!/usr/bin/env python3
"""Egasining kalit juftligini yaratadi. FAQAT egasi, o'z kompyuterida, internetsiz ishga tushiradi.

    python gen_keys.py --out C:/kalitlar

Natija: owner_private.pem (parol bilan shifrlangan) va owner.pub (32 bayt, ilovaga joylanadi).
"""
import argparse
import getpass
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from hamroh_keys import fingerprint, public_raw


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=".", help="kalitlar yoziladigan papka")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    priv_path = out / "owner_private.pem"
    if priv_path.exists():
        raise SystemExit(f"{priv_path} allaqachon bor — eski kalitni o'chirib yubormaslik uchun to'xtadim.")
    pw = getpass.getpass("Yopiq kalit uchun parol (kamida 12 belgi): ").encode()
    if len(pw) < 12 or pw != getpass.getpass("Parolni takrorlang: ").encode():
        raise SystemExit("Parol juda qisqa yoki mos kelmadi.")
    key = Ed25519PrivateKey.generate()
    priv_path.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                            serialization.BestAvailableEncryption(pw)))
    pub = public_raw(key)
    (out / "owner.pub").write_bytes(pub)
    print("Tayyor.")
    print("  Yopiq kalit :", priv_path, "(hech kimga bermang, 2 nusxada saqlang)")
    print("  Ochiq kalit :", out / "owner.pub", "-> android/app/src/main/assets/owner.pub")
    print("  Kalit izi   :", fingerprint(pub))


if __name__ == "__main__":
    main()
