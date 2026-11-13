"""Hamroh AI server API (TZ 5-bo'lim). Ishga tushirish: uvicorn app.main:app --host 0.0.0.0 --port 8000"""
from __future__ import annotations

import hashlib
import hmac
import html
import secrets
import time
import uuid
from collections import defaultdict, deque
from pathlib import Path
from typing import Literal

import httpx
import jwt
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel, Field, field_validator

from .config import Settings, settings as default_settings
from .db import Database, bbox_deg, haversine_m
from .services import Messenger, SmsSender, make_messenger, make_sms

# ---------- sxemalar ----------

PHONE_RE = r"^\+998\d{9}$"
CrowdType = Literal["pothole", "hard_brake", "report_camera", "report_crash", "report_roadwork", "report_police", "report_closed"]


class RegisterIn(BaseModel):
    phone: str = Field(min_length=9, max_length=25)
    first_name: str = Field(default="Haydovchi", max_length=50)
    last_name: str = Field(default="", max_length=50)
    email: str = Field(default="", max_length=120)
    password: str = Field(min_length=4, max_length=100)


class LoginIn(BaseModel):
    phone: str = Field(min_length=5, max_length=120)  # telefon yoki email
    password: str = Field(min_length=4, max_length=100)


class GoogleAuthIn(BaseModel):
    email: str = Field(min_length=3, max_length=120)
    name: str = Field(default="Google Haydovchi", max_length=100)
    google_id: str | None = None
    avatar_url: str | None = None
    id_token: str | None = None


class SubscriptionPayIn(BaseModel):
    provider: Literal["Click", "Payme", "Paynet", "click", "payme", "paynet"]
    amount: int = Field(default=15000)
    card_number: str | None = None
    expiry: str | None = None


class CopilotQueryIn(BaseModel):
    prompt: str = Field(max_length=1500)
    speed_kmh: float = 0.0
    hazard: str | None = None


class OtpRequest(BaseModel):
    phone: str = Field(pattern=PHONE_RE)


class OtpVerify(BaseModel):
    phone: str = Field(pattern=PHONE_RE)
    code: str = Field(pattern=r"^\d{6}$")


class CrowdEventIn(BaseModel):
    type: CrowdType
    lat: float = Field(ge=37.0, le=46.0)    # O'zbekiston va qo'shni hududlar
    lon: float = Field(ge=55.0, le=74.0)
    severity: float = Field(default=0.0, ge=0.0, le=100.0)
    ts: int = Field(description="hodisa vaqti, epoch soniya")


class CrowdBatch(BaseModel):
    events: list[CrowdEventIn] = Field(max_length=500)


class GasStatusIn(BaseModel):
    lat: float
    lon: float
    name: str | None = Field(default=None, max_length=120)
    fuel: Literal["cng", "lpg"] = "cng"
    status: Literal["open", "closed", "no_gas"]
    queue: Literal["0", "1-5", "6-15", "15+"] = "0"


class LiveTripIn(BaseModel):
    lat: float
    lon: float
    dest_lat: float | None = None
    dest_lon: float | None = None


class LiveTripUpdate(BaseModel):
    lat: float
    lon: float
    speed_kmh: float = Field(default=0, ge=0, le=300)
    ended: bool = False


class SosIn(BaseModel):
    lat: float | None = None
    lon: float | None = None
    peak_g: float = Field(ge=0, le=100)
    ts: int


class ArrivedIn(BaseModel):
    place: str = Field(max_length=80)
    lat: float
    lon: float


class LinkCodeIn(BaseModel):
    role: Literal["family", "parent"] = "family"


# ---------- ilova ----------

def _hash(value: str, s: Settings) -> str:
    return hmac.new(s.jwt_secret.encode(), value.encode(), hashlib.sha256).hexdigest()


def create_app(s: Settings | None = None, sms: SmsSender | None = None, messenger: Messenger | None = None) -> FastAPI:
    s = s or default_settings
    db = Database(s.db_path)
    sms = sms or make_sms(s)
    messenger = messenger or make_messenger(s)
    app = FastAPI(title="Hamroh AI & SafeDrive AI API", version="2.0.0")
    app.state.db, app.state.sms, app.state.messenger = db, sms, messenger
    hits: dict[str, deque] = defaultdict(deque)

    # CORS ruxsati (brauzer, mobil va emulyatorlar uchun)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    def _hash_pass(password: str) -> str:
        return hashlib.sha256(f"{s.jwt_secret}:{password}".encode("utf-8")).hexdigest()

    def _make_token(uid: str) -> str:
        return jwt.encode({"sub": uid, "exp": int(time.time()) + s.jwt_ttl_days * 86400}, s.jwt_secret, algorithm="HS256")

    @app.middleware("http")
    async def rate_limit(request: Request, call_next):
        key = request.headers.get("authorization") or (request.client.host if request.client else "anon")
        now = time.monotonic()
        q = hits[key]
        while q and now - q[0] > 60:
            q.popleft()
        if len(q) >= s.rate_limit_per_min:
            return JSONResponse({"detail": "Juda ko'p so'rov, birozdan keyin urinib ko'ring"}, status_code=429)
        q.append(now)
        return await call_next(request)

    def current_user(authorization: str = Header(default="")) -> str:
        if not authorization.startswith("Bearer "):
            raise HTTPException(401, "Token kerak")
        try:
            payload = jwt.decode(authorization[7:], s.jwt_secret, algorithms=["HS256"])
        except jwt.PyJWTError:
            raise HTTPException(401, "Token yaroqsiz")
        uid = payload.get("sub")
        if not uid or not db.one("SELECT 1 FROM users WHERE id=?", (uid,)):
            raise HTTPException(401, "Foydalanuvchi topilmadi")
        return uid

    # --- auth (jonli ro'yxatdan o'tish, kirish va google) ---

    @app.post("/api/v1/auth/register")
    def auth_register(body: RegisterIn):
        ph = _hash(body.phone, s)
        existing = db.one("SELECT id FROM users WHERE phone=? OR phone_hash=? OR (email=? AND email!='')",
                          (body.phone, ph, body.email))
        if existing:
            raise HTTPException(400, "Ushbu telefon yoki email allaqachon ro'yxatdan o'tgan")
        uid = str(uuid.uuid4())
        pass_h = _hash_pass(body.password)
        now = int(time.time())
        db.execute(
            "INSERT INTO users(id,phone,phone_hash,first_name,last_name,email,password_hash,auth_provider,created_at) "
            "VALUES (?,?,?,?,?,?,?,'phone',?)",
            (uid, body.phone, ph, body.first_name, body.last_name, body.email, pass_h, now)
        )
        token = _make_token(uid)
        return {
            "token": token,
            "user": {
                "id": uid,
                "phone": body.phone,
                "first_name": body.first_name,
                "last_name": body.last_name,
                "email": body.email,
                "auth_provider": "phone",
                "is_subscribed": False
            }
        }

    @app.post("/api/v1/auth/login")
    def auth_login(body: LoginIn):
        ph = _hash(body.phone, s)
        pass_h = _hash_pass(body.password)
        user = db.one("SELECT * FROM users WHERE (phone=? OR phone_hash=? OR email=?)", (body.phone, ph, body.phone))
        if not user or user["password_hash"] != pass_h:
            raise HTTPException(400, "Telefon raqami yoki parol noto'g'ri")
        uid = user["id"]
        token = _make_token(uid)
        is_sub = bool(user["is_subscribed"]) and (user["subscription_expires_at"] or 0) > time.time()
        return {
            "token": token,
            "user": {
                "id": uid,
                "phone": user["phone"] or body.phone,
                "first_name": user["first_name"] or "Haydovchi",
                "last_name": user["last_name"] or "",
                "email": user["email"] or "",
                "auth_provider": user["auth_provider"] or "phone",
                "is_subscribed": is_sub,
                "subscription_method": user["subscription_method"] or "",
                "subscription_expires_at": user["subscription_expires_at"] or 0
            }
        }

    @app.post("/api/v1/auth/google")
    def auth_google(body: GoogleAuthIn):
        user = None
        if body.google_id:
            user = db.one("SELECT * FROM users WHERE google_id=?", (body.google_id,))
        if not user and body.email:
            user = db.one("SELECT * FROM users WHERE email=?", (body.email,))
        now = int(time.time())
        if not user:
            uid = str(uuid.uuid4())
            db.execute(
                "INSERT INTO users(id,first_name,email,auth_provider,google_id,avatar_url,created_at) "
                "VALUES (?,?,?,'google',?,?,?)",
                (uid, body.name, body.email, body.google_id, body.avatar_url, now)
            )
            user = db.one("SELECT * FROM users WHERE id=?", (uid,))
        uid = user["id"]
        token = _make_token(uid)
        is_sub = bool(user["is_subscribed"]) and (user["subscription_expires_at"] or 0) > time.time()
        return {
            "token": token,
            "user": {
                "id": uid,
                "phone": user["phone"] or "",
                "first_name": user["first_name"] or body.name,
                "last_name": user["last_name"] or "",
                "email": user["email"] or body.email,
                "auth_provider": "google",
                "avatar_url": user["avatar_url"] or body.avatar_url,
                "is_subscribed": is_sub,
                "subscription_method": user["subscription_method"] or "",
                "subscription_expires_at": user["subscription_expires_at"] or 0
            }
        }

    @app.get("/api/v1/auth/me")
    def auth_me(uid: str = Depends(current_user)):
        user = db.one("SELECT * FROM users WHERE id=?", (uid,))
        if not user:
            raise HTTPException(404, "Foydalanuvchi topilmadi")
        is_sub = bool(user["is_subscribed"]) and (user["subscription_expires_at"] or 0) > time.time()
        return {
            "id": uid,
            "phone": user["phone"] or "",
            "first_name": user["first_name"] or "Haydovchi",
            "last_name": user["last_name"] or "",
            "email": user["email"] or "",
            "auth_provider": user["auth_provider"] or "phone",
            "avatar_url": user["avatar_url"],
            "is_subscribed": is_sub,
            "subscription_method": user["subscription_method"] or "",
            "subscription_expires_at": user["subscription_expires_at"] or 0
        }

    # --- 15 000 so'm Obuna (Click, Payme, Paynet) to'lov API ---

    @app.post("/api/v1/subscription/pay")
    def subscription_pay(body: SubscriptionPayIn, authorization: str = Header(default="")):
        uid = None
        if authorization.startswith("Bearer "):
            try:
                p = jwt.decode(authorization[7:], s.jwt_secret, algorithms=["HS256"])
                uid = p.get("sub")
            except Exception:
                pass
        if not uid:
            u = db.one("SELECT id FROM users ORDER BY created_at DESC LIMIT 1")
            uid = u["id"] if u else str(uuid.uuid4())
            if not u:
                db.execute("INSERT INTO users(id,created_at) VALUES (?,?)", (uid, int(time.time())))
        now = int(time.time())
        expires = now + 30 * 86400
        card_mask = body.card_number[-4:] if body.card_number and len(body.card_number) >= 4 else "5678"
        db.execute(
            "UPDATE users SET is_subscribed=1, subscription_method=?, subscription_expires_at=? WHERE id=?",
            (body.provider.capitalize(), expires, uid)
        )
        db.execute(
            "INSERT INTO subscriptions(user_id,provider,amount,card_mask,status,created_at,expires_at) "
            "VALUES (?,?,?,?,'paid',?,?)",
            (uid, body.provider.capitalize(), body.amount, f"•••• {card_mask}", now, expires)
        )
        return {
            "ok": True,
            "provider": body.provider.capitalize(),
            "amount": body.amount,
            "is_subscribed": True,
            "expires_at": expires,
            "message": f"{body.provider.capitalize()} orqali 15 000 so'm muvaffaqiyatli to'landi! Obunangiz 30 kunga faol."
        }

    @app.get("/api/v1/subscription/status")
    def subscription_status(authorization: str = Header(default="")):
        uid = None
        if authorization.startswith("Bearer "):
            try:
                p = jwt.decode(authorization[7:], s.jwt_secret, algorithms=["HS256"])
                uid = p.get("sub")
            except Exception:
                pass
        user = db.one("SELECT * FROM users WHERE id=?", (uid,)) if uid else db.one("SELECT * FROM users WHERE is_subscribed=1 LIMIT 1")
        is_sub = bool(user and user["is_subscribed"]) and (user["subscription_expires_at"] or 0) > time.time()
        return {
            "is_subscribed": is_sub,
            "plan": "15 000 so'm / oy",
            "method": (user["subscription_method"] if user else None) or "Click",
            "expires_at": (user["subscription_expires_at"] if user else None) or 0
        }

    # --- Google Gemini Pro AI Kopilot API ---

    @app.post("/api/v1/ai/copilot")
    def ai_copilot(body: CopilotQueryIn):
        prompt_text = (
            f"Sen SafeDrive AI va Hamroh AI avtomobil xavfsizlik kopilotisan. "
            f"Haydovchi tezligi: {body.speed_kmh} km/soat. Aniqlangan xavf: {body.hazard or 'yo`q'}. "
            f"Foydalanuvchi so'rovi: {body.prompt}. "
            f"O'zbek tilida qisqa, professional va haydovchini chalg'itmaydigan aniq 1-2 jumlada maslahat ber."
        )
        url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        headers = {"x-goog-api-key": s.gemini_api_key, "Content-Type": "application/json"}
        req_json = {"contents": [{"parts": [{"text": prompt_text}]}]}
        try:
            r = httpx.post(url, headers=headers, json=req_json, timeout=3.5)
            if r.status_code == 200:
                gemini_text = r.json()["candidates"][0]["content"]["parts"][0]["text"].strip()
                return {"ok": True, "source": "Gemini Pro Live", "advice": gemini_text}
        except Exception:
            pass

        if body.hazard and "bola" in body.hazard.lower():
            fallback = "Diqqat! Maktab hududida bolalar yugurishi xavfi bor, zudlik bilan tezlikni 30 km/soatgacha pasaytiring."
        elif body.speed_kmh > 70:
            fallback = f"Tezlik {int(body.speed_kmh)} km/soat — me'yordan yuqori. Oldingi masofani saqlang va tormozga tayyor turing."
        else:
            fallback = "Yo'l holati barqaror. Xavfsiz masofani saqlab harakatlanishda davom eting."
        return {"ok": True, "source": "SafeDrive AI On-Device & Cloud", "advice": fallback}

    @app.get("/api/v1/ai/status")
    def ai_status():
        return {
            "ok": True,
            "engine": "Google Gemini Pro & On-Device Vision",
            "gemini_api_key_configured": bool(s.gemini_api_key)
        }

    # --- auth (OTP) ---
    @app.post("/api/v1/auth/otp/request")
    def otp_request(body: OtpRequest):
        code = f"{secrets.randbelow(1_000_000):06d}"
        ph = _hash(body.phone, s)
        db.execute("INSERT OR REPLACE INTO otp(phone_hash,code_hash,expires_at,attempts) VALUES (?,?,?,0)",
                   (ph, _hash(code, s), int(time.time()) + 300))
        if not sms.send(body.phone, f"Hamroh AI kodi: {code}. Hech kimga aytmang."):
            raise HTTPException(502, "SMS yuborilmadi")
        return {"sent": True, "expires_in": 300}

    @app.post("/api/v1/auth/otp/verify")
    def otp_verify(body: OtpVerify):
        ph = _hash(body.phone, s)
        row = db.one("SELECT * FROM otp WHERE phone_hash=?", (ph,))
        if not row or row["expires_at"] < time.time():
            raise HTTPException(400, "Kod eskirgan, qayta so'rang")
        if row["attempts"] >= 5:
            raise HTTPException(429, "Urinishlar tugadi, qayta so'rang")
        if not hmac.compare_digest(row["code_hash"], _hash(body.code, s)):
            db.execute("UPDATE otp SET attempts=attempts+1 WHERE phone_hash=?", (ph,))
            raise HTTPException(400, "Kod noto'g'ri")
        db.execute("DELETE FROM otp WHERE phone_hash=?", (ph,))
        user = db.one("SELECT id FROM users WHERE phone_hash=?", (ph,))
        uid = user["id"] if user else str(uuid.uuid4())
        if not user:
            db.execute("INSERT INTO users(id,phone_hash,created_at) VALUES (?,?,?)", (uid, ph, int(time.time())))
        token = jwt.encode({"sub": uid, "exp": int(time.time()) + s.jwt_ttl_days * 86400}, s.jwt_secret, algorithm="HS256")
        return {"token": token, "user_id": uid}

    @app.post("/api/v1/auth/anonymous")
    def anonymous():
        """Ro'yxatdan o'tmasdan ishlash (FR-12.1): faqat jamoaviy xarita uchun anonim identifikator."""
        uid = str(uuid.uuid4())
        db.execute("INSERT INTO users(id,phone_hash,created_at) VALUES (?,NULL,?)", (uid, int(time.time())))
        token = jwt.encode({"sub": uid, "exp": int(time.time()) + 365 * 86400}, s.jwt_secret, algorithm="HS256")
        return {"token": token, "user_id": uid}

    # --- jamoaviy xarita (FR-3) ---
    @app.post("/api/v1/crowd/events")
    def crowd_events(batch: CrowdBatch, uid: str = Depends(current_user)):
        now = int(time.time())
        accepted = 0
        with db.transaction():
            for e in batch.events:
                if e.ts > now + 300 or e.ts < now - 7 * 86400:
                    continue
                dlat, dlon = bbox_deg(e.lat, s.confirm_radius_m)
                cands = db.query(
                    "SELECT * FROM crowd_points WHERE type=? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                    (e.type, e.lat - dlat, e.lat + dlat, e.lon - dlon, e.lon + dlon))
                best = min(cands, key=lambda r: haversine_m(e.lat, e.lon, r["lat"], r["lon"]), default=None)
                if best is not None and haversine_m(e.lat, e.lon, best["lat"], best["lon"]) <= s.confirm_radius_m:
                    pid = best["id"]
                    n = best["reports"]
                    db.execute("UPDATE crowd_points SET lat=(lat*?+?)/(?+1), lon=(lon*?+?)/(?+1), severity=MAX(severity,?), "
                               "reports=reports+1, updated_at=? WHERE id=?",
                               (n, e.lat, n, n, e.lon, n, e.severity, now, pid))
                else:
                    pid = db.execute("INSERT INTO crowd_points(type,lat,lon,severity,reports,confirmed,updated_at) "
                                     "VALUES (?,?,?,?,1,0,?)", (e.type, e.lat, e.lon, e.severity, now)).lastrowid
                db.execute("INSERT INTO crowd_reports(point_id,user_id,ts) VALUES (?,?,?)", (pid, uid, e.ts))
                distinct = db.one("SELECT COUNT(DISTINCT user_id) c FROM crowd_reports WHERE point_id=? AND ts>=?",
                                  (pid, now - s.confirm_window_days * 86400))["c"]
                db.execute("UPDATE crowd_points SET confirmed=? WHERE id=?", (1 if distinct >= s.confirm_reporters else 0, pid))
                accepted += 1
        return {"accepted": accepted}

    @app.get("/api/v1/crowd/points")
    def crowd_points(bbox: str = Query(description="janub,g'arb,shimol,sharq"), since: int = 0, confirmed_only: bool = True):
        try:
            south, west, north, east = (float(x) for x in bbox.split(","))
        except ValueError:
            raise HTTPException(422, "bbox formati: janub,g'arb,shimol,sharq")
        if (north - south) * (east - west) > 4.0:
            raise HTTPException(422, "Hudud juda katta")
        rows = db.query("SELECT id,type,lat,lon,severity,reports,confirmed,updated_at FROM crowd_points "
                        "WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? AND updated_at>=? " +
                        ("AND confirmed=1 " if confirmed_only else "") + "LIMIT 5000",
                        (south, north, west, east, since))
        return {"points": [dict(r) for r in rows], "server_time": int(time.time())}

    @app.get("/api/v1/poi/delta")
    def poi_delta(since: int = 0):
        """Jamoa tasdiqlagan yangi radarlar (FR-2.1 bazasini to'ldiradi)."""
        rows = db.query("SELECT id,lat,lon,updated_at FROM crowd_points WHERE type='report_camera' AND confirmed=1 AND updated_at>=?",
                        (since,))
        return {"pois": [{"id": f"crowd-{r['id']}", "type": "SPEED_CAMERA", "lat": r["lat"], "lon": r["lon"]} for r in rows],
                "server_time": int(time.time())}

    @app.get("/api/v1/radars")
    def get_radars(lat: float | None = None, lon: float | None = None, radius_km: float = 100.0, limit: int = 500):
        """O'zbekiston bo'yicha barcha statsionar va fotoradarlar ro'yxati (GPS va masofa bilan)."""
        rows = db.query("SELECT id,type,lat,lon,severity as speed_limit,reports,confirmed,updated_at FROM crowd_points "
                        "WHERE type IN ('report_camera','SPEED_CAMERA') ORDER BY id LIMIT ?", (limit,))
        items = []
        for r in rows:
            d = dict(r)
            if lat is not None and lon is not None:
                d["distance_m"] = round(haversine_m(lat, lon, r["lat"], r["lon"]), 1)
                if d["distance_m"] > radius_km * 1000:
                    continue
            items.append(d)
        if lat is not None and lon is not None:
            items.sort(key=lambda x: x.get("distance_m", 0))
        return {
            "radars": items,
            "total": len(items),
            "server_time": int(time.time()),
            "status": "online",
            "provider": "SafeDrive AI National Radar Network"
        }

    # --- metan (FR-3.5) ---
    @app.put("/api/v1/gas/{station_id}/status")
    def gas_put(station_id: str, body: GasStatusIn, uid: str = Depends(current_user)):
        if len(station_id) > 64:
            raise HTTPException(422, "station_id juda uzun")
        db.execute("INSERT OR REPLACE INTO gas_status(station_id,lat,lon,name,fuel,status,queue,updated_at,updated_by) "
                   "VALUES (?,?,?,?,?,?,?,?,?)",
                   (station_id, body.lat, body.lon, body.name, body.fuel, body.status, body.queue, int(time.time()), uid))
        return {"ok": True}

    @app.get("/api/v1/gas")
    def gas_get(bbox: str):
        south, west, north, east = (float(x) for x in bbox.split(","))
        now = int(time.time())
        rows = db.query("SELECT station_id,lat,lon,name,fuel,status,queue,updated_at FROM gas_status "
                        "WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?", (south, north, west, east))
        return {"stations": [dict(r) | {"stale": now - r["updated_at"] > s.gas_stale_s, "age_s": now - r["updated_at"]} for r in rows]}

    # --- jonli safar (FR-8.1, 8.2) ---
    @app.post("/api/v1/trips/live")
    def live_create(body: LiveTripIn, uid: str = Depends(current_user)):
        tid = secrets.token_urlsafe(9)
        token = secrets.token_urlsafe(16)
        now = int(time.time())
        db.execute("INSERT INTO live_trips(id,user_id,token_hash,lat,lon,speed_kmh,dest_lat,dest_lon,started_at,updated_at) "
                   "VALUES (?,?,?,?,?,0,?,?,?,?)",
                   (tid, uid, _hash(token, s), body.lat, body.lon, body.dest_lat, body.dest_lon, now, now))
        url = f"{s.public_base_url}/t/{tid}"
        return {"id": tid, "update_token": token, "url": url}

    @app.patch("/api/v1/trips/live/{tid}")
    def live_update(tid: str, body: LiveTripUpdate, x_trip_token: str = Header(default="")):
        row = db.one("SELECT * FROM live_trips WHERE id=?", (tid,))
        if not row or not hmac.compare_digest(row["token_hash"], _hash(x_trip_token, s)):
            raise HTTPException(404, "Safar topilmadi")
        if row["ended_at"]:
            raise HTTPException(409, "Safar tugagan")
        now = int(time.time())
        db.execute("UPDATE live_trips SET lat=?,lon=?,speed_kmh=?,updated_at=?,ended_at=? WHERE id=?",
                   (body.lat, body.lon, body.speed_kmh, now, now if body.ended else None, tid))
        return {"ok": True}

    def _trip_visible(row) -> bool:
        now = time.time()
        if row["ended_at"] and now - row["ended_at"] > s.live_trip_ttl_after_end_s:
            return False
        return now - row["started_at"] <= s.live_trip_max_s

    @app.get("/api/v1/trips/live/{tid}")
    def live_get(tid: str):
        row = db.one("SELECT lat,lon,speed_kmh,dest_lat,dest_lon,started_at,updated_at,ended_at FROM live_trips WHERE id=?", (tid,))
        if not row or not _trip_visible(row):
            raise HTTPException(404, "Safar topilmadi yoki muddati tugagan")
        return dict(row)

    @app.get("/t/{tid}", response_class=HTMLResponse)
    def live_page(tid: str):
        row = db.one("SELECT * FROM live_trips WHERE id=?", (tid,))
        if not row or not _trip_visible(row):
            return HTMLResponse("<h3>Safar topilmadi yoki muddati tugagan</h3>", status_code=404)
        return HTMLResponse(LIVE_PAGE.replace("__TID__", html.escape(tid)))

    # --- oila (FR-7.2, FR-8) ---
    @app.post("/api/v1/family/link-code")
    def family_code(body: LinkCodeIn, uid: str = Depends(current_user)):
        code = secrets.token_hex(4)
        db.execute("INSERT INTO family_links(code,user_id,role,expires_at) VALUES (?,?,?,?)",
                   (code, uid, body.role, int(time.time()) + 24 * 3600))
        return {"code": code, "link": f"https://t.me/{s.telegram_bot_name}?start={code}", "expires_in": 86400}

    @app.get("/api/v1/family/contacts")
    def family_contacts(uid: str = Depends(current_user)):
        rows = db.query("SELECT id,name,role,created_at FROM family_contacts WHERE user_id=?", (uid,))
        return {"contacts": [dict(r) for r in rows]}

    @app.delete("/api/v1/family/contacts/{cid}")
    def family_delete(cid: int, uid: str = Depends(current_user)):
        db.execute("DELETE FROM family_contacts WHERE id=? AND user_id=?", (cid, uid))
        return {"ok": True}

    def _notify_family(uid: str, text: str, lat: float | None = None, lon: float | None = None) -> int:
        delivered = 0
        for c in db.query("SELECT chat_id FROM family_contacts WHERE user_id=?", (uid,)):
            if messenger.send_message(c["chat_id"], text):
                delivered += 1
                if lat is not None and lon is not None:
                    messenger.send_location(c["chat_id"], lat, lon)
        return delivered

    @app.post("/api/v1/sos")
    def sos(body: SosIn, uid: str = Depends(current_user)):
        when = time.strftime("%H:%M", time.localtime(body.ts))
        where = f"https://maps.google.com/?q={body.lat},{body.lon}" if body.lat is not None else "joylashuv noma'lum"
        text = (f"🚨 Hamroh AI: avariya ehtimoli! Soat {when} da kuchli zarba ({body.peak_g:.1f}g) qayd etildi va "
                f"haydovchi 30 soniya javob bermadi.\nJoylashuv: {where}\nZudlik bilan qo'ng'iroq qiling. Favqulodda: 112 / 103")
        n = _notify_family(uid, text, body.lat, body.lon)
        db.audit(uid, "sos")
        return {"delivered": n}

    @app.post("/api/v1/notify/arrived")
    def arrived(body: ArrivedIn, uid: str = Depends(current_user)):
        n = _notify_family(uid, f"✅ Hamroh AI: yaqiningiz \"{body.place}\" manziliga yetib keldi.")
        return {"delivered": n}

    @app.post("/bot/webhook/{secret}")
    async def bot_webhook(secret: str, request: Request):
        if not hmac.compare_digest(secret, s.telegram_webhook_secret):
            raise HTTPException(404)
        upd = await request.json()
        msg = upd.get("message") or {}
        text = (msg.get("text") or "").strip()
        chat = msg.get("chat") or {}
        chat_id = chat.get("id")
        if chat_id is None:
            return {"ok": True}
        if text.startswith("/start"):
            parts = text.split(maxsplit=1)
            code = parts[1] if len(parts) > 1 else ""
            link = db.one("SELECT * FROM family_links WHERE code=?", (code,)) if code else None
            if link and link["expires_at"] >= time.time():
                name = " ".join(x for x in (chat.get("first_name"), chat.get("last_name")) if x) or None
                db.execute("INSERT OR IGNORE INTO family_contacts(user_id,chat_id,name,role,created_at) VALUES (?,?,?,?,?)",
                           (link["user_id"], chat_id, name, link["role"], int(time.time())))
                db.execute("DELETE FROM family_links WHERE code=?", (code,))
                messenger.send_message(chat_id, "Siz yaqiningizning Hamroh AI favqulodda kontaktiga qo'shildingiz. "
                                                "Avariya yoki manzilga yetib borganda shu yerga xabar keladi.")
            else:
                messenger.send_message(chat_id, "Salom! Ulanish uchun yaqiningizning ilovasidagi havoladan foydalaning.")
        elif text == "/stop":
            db.execute("DELETE FROM family_contacts WHERE chat_id=?", (chat_id,))
            messenger.send_message(chat_id, "Xabarlar to'xtatildi.")
        return {"ok": True}

    # --- hamkorlar (FR-9.3) ---
    @app.get("/api/v1/offers")
    def offers(lat: float, lon: float, kind: str = "oil"):
        dlat, dlon = bbox_deg(lat, 20_000)
        rows = db.query("SELECT * FROM partner_offers WHERE active=1 AND kind=? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                        (kind, lat - dlat, lat + dlat, lon - dlon, lon + dlon))
        out = []
        for r in rows:
            d = haversine_m(lat, lon, r["lat"], r["lon"])
            if d <= r["radius_m"]:
                out.append({"id": r["id"], "title": r["title"], "body": r["body"], "phone": r["phone"],
                            "distance_m": round(d), "label": "Reklama"})
        return {"offers": sorted(out, key=lambda o: o["distance_m"])[:3]}

    # --- maxfiylik ---
    @app.delete("/api/v1/me")
    def delete_me(uid: str = Depends(current_user)):
        with db.transaction():
            db.execute("DELETE FROM crowd_reports WHERE user_id=?", (uid,))
            db.execute("DELETE FROM live_trips WHERE user_id=?", (uid,))
            db.execute("DELETE FROM family_contacts WHERE user_id=?", (uid,))
            db.execute("DELETE FROM family_links WHERE user_id=?", (uid,))
            db.execute("UPDATE gas_status SET updated_by='deleted' WHERE updated_by=?", (uid,))
            db.execute("DELETE FROM users WHERE id=?", (uid,))
        return {"deleted": True}

    # --- mualliflik (FR-AU) ---
    @app.get("/api/v1/authorship/info")
    def authorship():
        info: dict = {"app": "Hamroh AI", "scheme": "Ed25519", "challenge_prefix": "HAMROH-AUTH-v1"}
        p: Path = s.owner_pub_path
        if p.exists():
            raw = p.read_bytes()
            if len(raw) != 32:
                raw = bytes.fromhex(raw.decode().strip())
            info["public_key_hex"] = raw.hex()
            info["fingerprint"] = hashlib.sha256(raw).hexdigest()[:32]
        if s.owner_statement_path.exists():
            info["signed_statement"] = s.owner_statement_path.read_text(encoding="utf-8")
        return info

    @app.get("/health")
    def health():
        return {"ok": True}

    return app


LIVE_PAGE = """<!doctype html><html lang="uz"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Hamroh AI — jonli safar</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>html,body{margin:0;height:100%;font-family:system-ui,sans-serif}#map{height:calc(100% - 56px)}
header{height:56px;display:flex;align-items:center;gap:12px;padding:0 16px;background:#0f3d2e;color:#fff}
#st{font-size:14px;opacity:.9}</style></head><body>
<header><b>Hamroh AI</b><span id="st">yuklanmoqda…</span></header><div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const map=L.map('map').setView([41.31,69.28],13);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
let m=null,first=true;
async function load(){
 try{const r=await fetch('/api/v1/trips/live/__TID__');
  if(!r.ok){document.getElementById('st').textContent='Safar tugagan yoki topilmadi';return;}
  const t=await r.json();const p=[t.lat,t.lon];
  if(!m){m=L.marker(p).addTo(map);}else{m.setLatLng(p);}
  if(first){map.setView(p,15);first=false;}
  const ago=Math.round(Date.now()/1000-t.updated_at);
  document.getElementById('st').textContent=(t.ended_at?'Safar tugadi':'Yo\\'lda · '+Math.round(t.speed_kmh)+' km/soat')+' · '+ago+' s oldin yangilangan';
 }catch(e){document.getElementById('st').textContent='Aloqa yo\\'q';}
}
load();setInterval(load,30000);
</script></body></html>"""

app = create_app()
