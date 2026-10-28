"""Hamroh AI mualliflik vositalari uchun umumiy funksiyalar (Ed25519).

Yopiq kalit faqat egasining kompyuterida, parol bilan shifrlangan PEM faylda turadi.
Bu modul kalitni hech qayerga yubormaydi va tarmoqqa murojaat qilmaydi.
"""
from __future__ import annotations

import base64
import hashlib
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

CHALLENGE_PREFIX = "HAMROH-AUTH-v1"


def fingerprint(pub_raw: bytes) -> str:
    """Ochiq kalit izi: SHA-256 ning birinchi 32 hex belgisi (ilovadagi bilan bir xil)."""
    return hashlib.sha256(pub_raw).hexdigest()[:32]


def load_public(path: str | Path) -> bytes:
    """owner.pub: 32 xom bayt, 64 hex belgi yoki base64."""
    data = Path(path).read_bytes()
    if len(data) == 32:
        return data
    text = data.decode("ascii").strip()
    if len(text) == 64:
        return bytes.fromhex(text)
    raw = base64.b64decode(text)
    if len(raw) != 32:
        raise ValueError("owner.pub formati noto'g'ri")
    return raw


def load_private(path: str | Path, password: bytes | None) -> Ed25519PrivateKey:
    key = serialization.load_pem_private_key(Path(path).read_bytes(), password=password)
    if not isinstance(key, Ed25519PrivateKey):
        raise ValueError("Bu Ed25519 kaliti emas")
    return key


def public_raw(key: Ed25519PrivateKey) -> bytes:
    return key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)


def sign_text(key: Ed25519PrivateKey, text: str) -> str:
    """UTF-8 matnni imzolaydi, URL-safe base64 (paddingsiz) qaytaradi — ilova shuni qabul qiladi."""
    sig = key.sign(text.encode("utf-8"))
    return base64.urlsafe_b64encode(sig).decode("ascii").rstrip("=")


def decode_signature(text: str) -> bytes:
    t = "".join(text.split())
    if len(t) == 128:
        try:
            return bytes.fromhex(t)
        except ValueError:
            pass
    t = t.replace("-", "+").replace("_", "/").rstrip("=")
    t += "=" * ((4 - len(t) % 4) % 4)
    raw = base64.b64decode(t)
    if len(raw) != 64:
        raise ValueError("Imzo 64 bayt bo'lishi kerak")
    return raw


def verify_text(pub_raw: bytes, text: str, signature: str) -> bool:
    try:
        Ed25519PublicKey.from_public_bytes(pub_raw).verify(decode_signature(signature), text.encode("utf-8"))
        return True
    except Exception:
        return False


def parse_challenge(text: str) -> dict:
    parts = text.strip().split("|")
    if len(parts) != 5 or parts[0] != CHALLENGE_PREFIX:
        raise ValueError("Bu Hamroh AI so'rovi emas")
    return {"nonce": parts[1], "issued_ms": int(parts[2]), "app_version": parts[3], "cert_sha256": parts[4]}
