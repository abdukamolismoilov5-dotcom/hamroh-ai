import subprocess
import sys
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import hamroh_keys as hk  # noqa: E402

TOOLS = Path(__file__).resolve().parents[1]


def make_key(tmp_path, password=b"test-password-123"):
    key = Ed25519PrivateKey.generate()
    pem = tmp_path / "owner_private.pem"
    pem.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                      serialization.BestAvailableEncryption(password)))
    pub = tmp_path / "owner.pub"
    pub.write_bytes(hk.public_raw(key))
    return key, pem, pub


def test_sign_and_verify_roundtrip(tmp_path):
    key, _, pub = make_key(tmp_path)
    text = "HAMROH-AUTH-v1|00112233445566778899aabbccddeeff|1760000000000|1.0.0|ab12"
    sig = hk.sign_text(key, text)
    assert hk.verify_text(hk.load_public(pub), text, sig)
    assert not hk.verify_text(hk.load_public(pub), text + "x", sig)


def test_cli_sign_then_verify(tmp_path):
    _, pem, pub = make_key(tmp_path)
    text = "HAMROH-AUTH-v1|00112233445566778899aabbccddeeff|1760000000000|1.0.0|ab12"
    env = {"HAMROH_KEY_PASSWORD": "test-password-123", "PATH": "/usr/bin:/bin"}
    r = subprocess.run([sys.executable, str(TOOLS / "sign_challenge.py"), "--key", str(pem), text],
                       capture_output=True, text=True, env=env, cwd=TOOLS)
    assert r.returncode == 0, r.stderr
    sig = r.stdout.strip().splitlines()[-1]
    ok = subprocess.run([sys.executable, str(TOOLS / "verify.py"), "--pub", str(pub), "--sig", sig, text],
                        capture_output=True, text=True, cwd=TOOLS)
    assert ok.returncode == 0 and "TO'G'RI" in ok.stdout
    bad = subprocess.run([sys.executable, str(TOOLS / "verify.py"), "--pub", str(pub), "--sig", sig, text + "!"],
                         capture_output=True, text=True, cwd=TOOLS)
    assert bad.returncode == 1


def test_wrong_password_fails(tmp_path):
    _, pem, _ = make_key(tmp_path)
    env = {"HAMROH_KEY_PASSWORD": "wrong", "PATH": "/usr/bin:/bin"}
    r = subprocess.run([sys.executable, str(TOOLS / "sign_challenge.py"), "--key", str(pem),
                        "HAMROH-AUTH-v1|00112233445566778899aabbccddeeff|1|1|a"],
                       capture_output=True, text=True, env=env, cwd=TOOLS)
    assert r.returncode != 0


def test_rejects_non_challenge_text(tmp_path):
    import pytest
    with pytest.raises(ValueError):
        hk.parse_challenge("salom dunyo")


def test_fingerprint_matches_kotlin_definition():
    raw = bytes(range(32))
    import hashlib
    assert hk.fingerprint(raw) == hashlib.sha256(raw).hexdigest()[:32]
