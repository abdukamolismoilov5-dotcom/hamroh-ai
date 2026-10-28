"""SQLite ma'lumotlar bazasi (bitta fayl). Katta yuklamada PostgreSQL + PostGIS ga ko'chiriladi (sxema mos)."""
from __future__ import annotations

import math
import sqlite3
import threading
import time

SCHEMA = """
PRAGMA journal_mode=WAL;
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    phone_hash TEXT UNIQUE,
    created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS otp (
    phone_hash TEXT PRIMARY KEY,
    code_hash TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS crowd_points (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    severity REAL NOT NULL DEFAULT 0,
    reports INTEGER NOT NULL DEFAULT 0,
    confirmed INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS crowd_points_geo ON crowd_points(lat, lon);
CREATE TABLE IF NOT EXISTS crowd_reports (
    point_id INTEGER NOT NULL REFERENCES crowd_points(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    ts INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS crowd_reports_point ON crowd_reports(point_id, ts);
CREATE TABLE IF NOT EXISTS gas_status (
    station_id TEXT PRIMARY KEY,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    name TEXT,
    fuel TEXT NOT NULL,
    status TEXT NOT NULL,
    queue TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    updated_by TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS live_trips (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    lat REAL, lon REAL, speed_kmh REAL,
    dest_lat REAL, dest_lon REAL,
    started_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    ended_at INTEGER
);
CREATE TABLE IF NOT EXISTS family_links (
    code TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    role TEXT NOT NULL,
    expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS family_contacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    chat_id INTEGER NOT NULL,
    name TEXT,
    role TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE(user_id, chat_id)
);
CREATE TABLE IF NOT EXISTS partner_offers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    radius_m REAL NOT NULL DEFAULT 5000,
    phone TEXT,
    active INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts INTEGER NOT NULL,
    user_id TEXT,
    action TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS subscriptions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    provider TEXT NOT NULL,
    amount INTEGER NOT NULL,
    card_mask TEXT,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);
"""


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6_371_008.8
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(h)))


def bbox_deg(lat: float, radius_m: float) -> tuple[float, float]:
    dlat = radius_m / 111_320.0
    dlon = radius_m / (111_320.0 * max(1e-6, math.cos(math.radians(lat))))
    return dlat, dlon


class Database:
    def __init__(self, path: str):
        self._lock = threading.RLock()
        self.conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA foreign_keys=ON")
        self.conn.executescript(SCHEMA)

        # Migratsiyalar: telefon, ism, familiya, email, parol, google va obuna
        try:
            cols = {r["name"] for r in self.conn.execute("PRAGMA table_info(users)").fetchall()}
            needed = [
                ("phone", "TEXT"),
                ("first_name", "TEXT"),
                ("last_name", "TEXT"),
                ("email", "TEXT"),
                ("password_hash", "TEXT"),
                ("auth_provider", "TEXT DEFAULT 'phone'"),
                ("google_id", "TEXT"),
                ("avatar_url", "TEXT"),
                ("is_subscribed", "INTEGER DEFAULT 0"),
                ("subscription_method", "TEXT"),
                ("subscription_expires_at", "INTEGER DEFAULT 0"),
            ]
            for col_name, col_type in needed:
                if col_name not in cols:
                    self.conn.execute(f"ALTER TABLE users ADD COLUMN {col_name} {col_type}")
        except Exception:
            pass

    def execute(self, sql: str, params: tuple | dict = ()) -> sqlite3.Cursor:
        with self._lock:
            return self.conn.execute(sql, params)

    def query(self, sql: str, params: tuple | dict = ()) -> list[sqlite3.Row]:
        with self._lock:
            return self.conn.execute(sql, params).fetchall()

    def one(self, sql: str, params: tuple | dict = ()) -> sqlite3.Row | None:
        with self._lock:
            return self.conn.execute(sql, params).fetchone()

    def transaction(self):
        return _Tx(self)

    def audit(self, user_id: str | None, action: str) -> None:
        self.execute("INSERT INTO audit_log(ts,user_id,action) VALUES (?,?,?)", (int(time.time()), user_id, action))


class _Tx:
    def __init__(self, db: Database):
        self.db = db

    def __enter__(self):
        self.db._lock.acquire()
        self.db.conn.execute("BEGIN")
        return self.db

    def __exit__(self, exc_type, *_):
        try:
            self.db.conn.execute("ROLLBACK" if exc_type else "COMMIT")
        finally:
            self.db._lock.release()
