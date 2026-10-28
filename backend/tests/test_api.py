import os
import sys
import time
from pathlib import Path

import pytest

os.environ.setdefault("HAMROH_DB", ":memory:")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient  # noqa: E402

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402
from app.services import LogSms  # noqa: E402


class FakeMessenger:
    def __init__(self):
        self.messages, self.locations = [], []

    def send_message(self, chat_id, text):
        self.messages.append((chat_id, text)); return True

    def send_location(self, chat_id, lat, lon):
        self.locations.append((chat_id, lat, lon)); return True


@pytest.fixture
def env(tmp_path):
    s = Settings(db_path=str(tmp_path / "t.sqlite3"), jwt_secret="test-secret-0123456789abcdef0123456789", telegram_webhook_secret="hook",
                 owner_pub_path=tmp_path / "owner.pub", owner_statement_path=tmp_path / "st.txt", rate_limit_per_min=10_000)
    sms, msg = LogSms(), FakeMessenger()
    client = TestClient(create_app(s, sms, msg))
    return client, sms, msg, s


def login(client, sms, phone="+998901234567"):
    assert client.post("/api/v1/auth/otp/request", json={"phone": phone}).status_code == 200
    code = sms.sent[-1][1].split(": ")[1][:6]
    r = client.post("/api/v1/auth/otp/verify", json={"phone": phone, "code": code})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['token']}"}


def anon(client):
    return {"Authorization": f"Bearer {client.post('/api/v1/auth/anonymous').json()['token']}"}


def test_otp_flow_and_errors(env):
    client, sms, _, _ = env
    assert client.post("/api/v1/auth/otp/request", json={"phone": "998901234567"}).status_code == 422
    h1 = login(client, sms)
    h2 = login(client, sms)
    me1 = client.get("/api/v1/family/contacts", headers=h1)
    assert me1.status_code == 200
    # bir xil raqam -> bir xil foydalanuvchi
    import jwt
    assert jwt.decode(h1["Authorization"][7:], "test-secret-0123456789abcdef0123456789", algorithms=["HS256"])["sub"] == \
        jwt.decode(h2["Authorization"][7:], "test-secret-0123456789abcdef0123456789", algorithms=["HS256"])["sub"]
    client.post("/api/v1/auth/otp/request", json={"phone": "+998901111111"})
    for _ in range(5):
        assert client.post("/api/v1/auth/otp/verify", json={"phone": "+998901111111", "code": "000000"}).status_code in (400,)
    assert client.post("/api/v1/auth/otp/verify", json={"phone": "+998901111111", "code": "000000"}).status_code == 429
    assert client.get("/api/v1/family/contacts").status_code == 401
    assert client.get("/api/v1/family/contacts", headers={"Authorization": "Bearer xxx"}).status_code == 401


def test_crowd_confirmation_needs_three_users(env):
    client, _, _, _ = env
    now = int(time.time())
    users = [anon(client) for _ in range(3)]
    ev = lambda dlat: {"events": [{"type": "pothole", "lat": 41.311 + dlat, "lon": 69.279, "severity": 8, "ts": now}]}
    bbox = "41.30,69.27,41.32,69.29"
    client.post("/api/v1/crowd/events", json=ev(0), headers=users[0])
    client.post("/api/v1/crowd/events", json=ev(0.00005), headers=users[0])  # o'sha odam yana — sanalmaydi
    assert client.get(f"/api/v1/crowd/points?bbox={bbox}").json()["points"] == []
    client.post("/api/v1/crowd/events", json=ev(0.0001), headers=users[1])  # ~11 m
    client.post("/api/v1/crowd/events", json=ev(-0.0001), headers=users[2])
    pts = client.get(f"/api/v1/crowd/points?bbox={bbox}").json()["points"]
    assert len(pts) == 1 and pts[0]["confirmed"] == 1 and pts[0]["reports"] == 4
    # 25 m dan uzoq — alohida nuqta
    client.post("/api/v1/crowd/events", json=ev(0.001), headers=users[1])
    allpts = client.get(f"/api/v1/crowd/points?bbox={bbox}&confirmed_only=false").json()["points"]
    assert len(allpts) == 2


def test_crowd_rejects_old_and_out_of_region(env):
    client, _, _, _ = env
    h = anon(client)
    old = {"events": [{"type": "pothole", "lat": 41.3, "lon": 69.2, "ts": int(time.time()) - 30 * 86400}]}
    assert client.post("/api/v1/crowd/events", json=old, headers=h).json()["accepted"] == 0
    far = {"events": [{"type": "pothole", "lat": 10.0, "lon": 69.2, "ts": int(time.time())}]}
    assert client.post("/api/v1/crowd/events", json=far, headers=h).status_code == 422
    assert client.get("/api/v1/crowd/points?bbox=30,50,45,80").status_code == 422


def test_confirmed_camera_appears_in_poi_delta(env):
    client, _, _, _ = env
    now = int(time.time())
    for _ in range(3):
        client.post("/api/v1/crowd/events", headers=anon(client),
                    json={"events": [{"type": "report_camera", "lat": 41.2, "lon": 69.1, "ts": now}]})
    pois = client.get("/api/v1/poi/delta").json()["pois"]
    assert len(pois) == 1 and pois[0]["type"] == "SPEED_CAMERA"


def test_gas_status_and_staleness(env):
    client, _, _, s = env
    h = anon(client)
    r = client.put("/api/v1/gas/osm-node-123/status", headers=h,
                   json={"lat": 40.38, "lon": 71.78, "name": "Metan 1", "status": "open", "queue": "6-15"})
    assert r.status_code == 200
    st = client.get("/api/v1/gas?bbox=40.3,71.7,40.5,71.9").json()["stations"]
    assert st[0]["queue"] == "6-15" and st[0]["stale"] is False
    client.app.state.db.execute("UPDATE gas_status SET updated_at=updated_at-?", (s.gas_stale_s + 10,))
    assert client.get("/api/v1/gas?bbox=40.3,71.7,40.5,71.9").json()["stations"][0]["stale"] is True


def test_live_trip_lifecycle(env):
    client, _, _, s = env
    h = anon(client)
    t = client.post("/api/v1/trips/live", headers=h, json={"lat": 41.3, "lon": 69.2}).json()
    assert t["url"].endswith(f"/t/{t['id']}")
    assert client.patch(f"/api/v1/trips/live/{t['id']}", json={"lat": 41.31, "lon": 69.21, "speed_kmh": 50},
                        headers={"X-Trip-Token": "wrong"}).status_code == 404
    assert client.patch(f"/api/v1/trips/live/{t['id']}", json={"lat": 41.31, "lon": 69.21, "speed_kmh": 50},
                        headers={"X-Trip-Token": t["update_token"]}).status_code == 200
    assert client.get(f"/api/v1/trips/live/{t['id']}").json()["speed_kmh"] == 50
    page = client.get(f"/t/{t['id']}")
    assert page.status_code == 200 and "leaflet" in page.text
    client.patch(f"/api/v1/trips/live/{t['id']}", json={"lat": 41.31, "lon": 69.21, "ended": True},
                 headers={"X-Trip-Token": t["update_token"]})
    client.app.state.db.execute("UPDATE live_trips SET ended_at=ended_at-?", (s.live_trip_ttl_after_end_s + 5,))
    assert client.get(f"/api/v1/trips/live/{t['id']}").status_code == 404


def test_family_link_sos_and_arrived(env):
    client, sms, msg, _ = env
    h = login(client, sms)
    code = client.post("/api/v1/family/link-code", headers=h, json={"role": "family"}).json()["code"]
    upd = {"message": {"text": f"/start {code}", "chat": {"id": 555, "first_name": "Ona"}}}
    assert client.post("/bot/webhook/wrong", json=upd).status_code == 404
    assert client.post("/bot/webhook/hook", json=upd).status_code == 200
    contacts = client.get("/api/v1/family/contacts", headers=h).json()["contacts"]
    assert contacts[0]["name"] == "Ona"
    # kod bir martalik
    client.post("/bot/webhook/hook", json={"message": {"text": f"/start {code}", "chat": {"id": 777}}})
    assert len(client.get("/api/v1/family/contacts", headers=h).json()["contacts"]) == 1
    r = client.post("/api/v1/sos", headers=h, json={"lat": 41.3, "lon": 69.2, "peak_g": 6.2, "ts": int(time.time())})
    assert r.json()["delivered"] == 1
    assert any("avariya" in m[1] and "maps.google.com" in m[1] for m in msg.messages)
    assert msg.locations[-1] == (555, 41.3, 69.2)
    assert client.post("/api/v1/notify/arrived", headers=h, json={"place": "Uy", "lat": 0, "lon": 0}).json()["delivered"] == 1
    client.post("/bot/webhook/hook", json={"message": {"text": "/stop", "chat": {"id": 555}}})
    assert client.get("/api/v1/family/contacts", headers=h).json()["contacts"] == []


def test_offers_labelled_as_ads(env):
    client, _, _, _ = env
    client.app.state.db.execute("INSERT INTO partner_offers(kind,title,body,lat,lon,radius_m,phone) VALUES "
                                "('oil','Moy markazi','10% chegirma',41.31,69.28,5000,'+998900000000')")
    o = client.get("/api/v1/offers?lat=41.311&lon=69.279&kind=oil").json()["offers"]
    assert o[0]["label"] == "Reklama" and o[0]["distance_m"] < 500
    assert client.get("/api/v1/offers?lat=40.0&lon=65.0&kind=oil").json()["offers"] == []


def test_delete_me(env):
    client, sms, _, _ = env
    h = login(client, sms)
    client.post("/api/v1/crowd/events", headers=h,
                json={"events": [{"type": "pothole", "lat": 41.3, "lon": 69.2, "ts": int(time.time())}]})
    assert client.delete("/api/v1/me", headers=h).json()["deleted"]
    assert client.get("/api/v1/family/contacts", headers=h).status_code == 401
    assert client.app.state.db.one("SELECT COUNT(*) c FROM crowd_reports")["c"] == 0


def test_authorship_info(env):
    client, _, _, s = env
    assert "fingerprint" not in client.get("/api/v1/authorship/info").json()
    s.owner_pub_path.write_bytes(bytes(range(32)))
    info = client.get("/api/v1/authorship/info").json()
    import hashlib
    assert info["fingerprint"] == hashlib.sha256(bytes(range(32))).hexdigest()[:32]


def test_rate_limit(tmp_path):
    s = Settings(db_path=str(tmp_path / "r.sqlite3"), jwt_secret="x"*40, rate_limit_per_min=5)
    c = TestClient(create_app(s, LogSms(), FakeMessenger()))
    codes = [c.get("/health").status_code for _ in range(7)]
    assert codes[:5] == [200] * 5 and codes[-1] == 429
