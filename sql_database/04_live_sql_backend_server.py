# -*- coding: utf-8 -*-
"""
Hamroh AI — Jonli SQL Backend Serveri (Port 8000)
=================================================
Mobil ilovadan (Android APK yoki Web MVP) ro'yxatdan o'tgan har bir foydalanuvchini
shu zahoti (real vaqtda) `hamroh_ai_offline.db` SQL bazasiga (`drivers` va
`devices_smart_box` jadvallariga) qo'shadi hamda SQL Boshqaruv Paneliga uzatadi.
"""

import os
import json
import time
import sqlite3
import shutil
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_FILE = os.path.join(BASE_DIR, "hamroh_ai_offline.db")
SQL_SCHEMA_FILE = os.path.join(BASE_DIR, "01_hamroh_ai_schema.sql")
DASHBOARD_HTML = os.path.join(BASE_DIR, "03_SQL_Baza_Boshqaruv_Paneli.html")
JSON_SNAPSHOT = os.path.join(BASE_DIR, "db_snapshot.json")

# Qo'shimcha nusxa papkalari (avtomatik sinxronlash uchun)
MIRROR_DIRS = [
    r"C:\Users\Abdukamol\OneDrive\Desktop\Hamroh_AI_SQL_Baza",
    r"C:\Users\Abdukamol\OneDrive\Desktop\papka\Hamroh_AI_SQL_Baza",
]


def ensure_db_initialized():
    if not os.path.exists(DB_FILE) and os.path.exists(SQL_SCHEMA_FILE):
        conn = sqlite3.connect(DB_FILE)
        with open(SQL_SCHEMA_FILE, "r", encoding="utf-8") as f:
            conn.executescript(f.read())
        conn.commit()
        conn.close()


def get_all_tables_data():
    ensure_db_initialized()
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    tables = [
        "drivers",
        "devices_smart_box",
        "trips",
        "dms_biometric_logs",
        "adas_road_hazards",
        "offline_poi_radars",
        "sos_emergency_incidents",
        "v_driver_safety_summary",
        "v_critical_alerts_report",
    ]
    data = {}
    for t in tables:
        try:
            if t == "drivers":
                cur.execute("SELECT * FROM drivers ORDER BY driver_id DESC;")
            elif t == "devices_smart_box":
                cur.execute("SELECT * FROM devices_smart_box ORDER BY device_id DESC;")
            else:
                cur.execute(f"SELECT * FROM {t};")
            data[t] = [dict(r) for r in cur.fetchall()]
        except Exception as e:
            data[t] = [{"error": str(e)}]

    conn.close()

    # JSON snapshot va boshqa papkalardagi nusxalarni yangilash
    try:
        with open(JSON_SNAPSHOT, "w", encoding="utf-8") as jf:
            json.dump(data, jf, ensure_ascii=False, indent=2)
        for m_dir in MIRROR_DIRS:
            if os.path.exists(m_dir):
                shutil.copyfile(DB_FILE, os.path.join(m_dir, "hamroh_ai_offline.db"))
                shutil.copyfile(JSON_SNAPSHOT, os.path.join(m_dir, "db_snapshot.json"))
    except Exception:
        pass

    return data


def insert_user_to_sql(phone: str, first_name: str, last_name: str, email: str = "", car_model: str = "", car_plate: str = "", region: str = "", plan: str = "PRO"):
    """Ilovadan ro'yxatdan o'tgan foydalanuvchini darhol SQL bazaga qo'shish."""
    ensure_db_initialized()
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    full_name = f"{first_name} {last_name}".strip()
    if not full_name:
        full_name = "Yangi Haydovchi"

    clean_phone = phone.strip() if phone else f"+99890{int(time.time()) % 10000000:07d}"
    if not car_model:
        car_model = "Chevrolet Cobalt"
    if not region:
        region = "Farg'ona viloyati"

    # Davlat raqami kiritilmagan bo'lsa unikal raqam generatsiya qilamiz
    if not car_plate:
        cur.execute("SELECT COUNT(*) AS cnt FROM drivers;")
        next_num = cur.fetchone()["cnt"] + 101
        car_plate = f"40 H {next_num} AI"

    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # Agar bu telefon raqami avval ro'yxatdan o'tgan bo'lsa, yangilaymiz; bo'lmasa INSERT qilamiz
    cur.execute("SELECT driver_id FROM drivers WHERE phone_number = ?;", (clean_phone,))
    existing = cur.fetchone()

    if existing:
        driver_id = existing["driver_id"]
        cur.execute(
            """
            UPDATE drivers
            SET full_name = ?, car_model = ?, region = ?, subscription_plan = ?, created_at = ?
            WHERE driver_id = ?;
            """,
            (full_name, car_model, region, plan, now_str, driver_id)
        )
        sql_executed = f"UPDATE drivers SET full_name='{full_name}', car_model='{car_model}' WHERE phone_number='{clean_phone}';"
    else:
        # 1. drivers jadvaliga INSERT
        try:
            cur.execute(
                """
                INSERT INTO drivers (
                    full_name, phone_number, car_model, car_plate_number,
                    region, experience_years, voice_language, family_sos_phone,
                    safety_score, subscription_plan, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """,
                (
                    full_name, clean_phone, car_model, car_plate,
                    region, 3, "uz", clean_phone,
                    100.0, plan, now_str
                )
            )
        except sqlite3.IntegrityError:
            car_plate = f"40 X {int(time.time()) % 900 + 100} AI"
            cur.execute(
                """
                INSERT INTO drivers (
                    full_name, phone_number, car_model, car_plate_number,
                    region, experience_years, voice_language, family_sos_phone,
                    safety_score, subscription_plan, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """,
                (
                    full_name, clean_phone, car_model, car_plate,
                    region, 3, "uz", clean_phone,
                    100.0, plan, now_str
                )
            )

        driver_id = cur.lastrowid

        # 2. devices_smart_box jadvaliga avto-biriktirish
        serial_no = f"HMAI-APP-2026-{driver_id:04d}"
        cur.execute(
            """
            INSERT OR IGNORE INTO devices_smart_box (
                driver_id, serial_number, device_type, connection_port,
                npu_processor, firmware_version, is_offline_ready, last_sync_at
            ) VALUES (?, ?, 'SMARTPHONE_APP + SMART_BOX', 'TYPE-C USB', 'Mobile NPU INT8 (12ms)', 'v2.4.0-live', 1, ?);
            """,
            (driver_id, serial_no, now_str)
        )
        sql_executed = (
            f"INSERT INTO drivers (full_name, phone_number, car_model, car_plate_number, region, safety_score, subscription_plan) "
            f"VALUES ('{full_name}', '{clean_phone}', '{car_model}', '{car_plate}', '{region}', 100.0, '{plan}');"
        )

    conn.commit()
    conn.close()
    print(f"\n[JONLI SQL INSERT] Yangi foydalanuvchi bazaga qo'shildi: ID={driver_id} | {full_name} | {clean_phone} | {car_model} ({car_plate})")
    return driver_id, full_name, clean_phone, car_model, car_plate, sql_executed


class HamrohSQLHandler(BaseHTTPRequestHandler):
    def _set_cors(self, status=200, content_type="application/json; charset=utf-8"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()

    def do_OPTIONS(self):
        self._set_cors(200)

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path in ["/", "/dashboard", "/03_SQL_Baza_Boshqaruv_Paneli.html"]:
            if os.path.exists(DASHBOARD_HTML):
                self._set_cors(200, "text/html; charset=utf-8")
                with open(DASHBOARD_HTML, "rb") as f:
                    self.wfile.write(f.read())
                return

        if path == "/api/v1/sql/tables":
            data = get_all_tables_data()
            self._set_cors(200)
            self.wfile.write(json.dumps({"status": "ok", "tables": data}, ensure_ascii=False).encode("utf-8"))
            return

        # Boshqa GET endpointlar (APK uchun)
        self._set_cors(200)
        self.wfile.write(json.dumps({"status": "ok", "points": [], "stations": [], "contacts": [], "offers": []}).encode("utf-8"))

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get("Content-Length", 0))
        raw_body = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            body = json.loads(raw_body) if raw_body else {}
        except Exception:
            body = {}

        # 1. ILOVADAN RO'YXATDAN O'TISH (/api/v1/auth/register)
        if path in ["/api/v1/auth/register", "/api/register"]:
            phone = body.get("phone", "")
            first_name = body.get("first_name", "")
            last_name = body.get("last_name", "")
            email = body.get("email", "")
            car_model = body.get("car_model", "Chevrolet Cobalt")
            car_plate = body.get("car_plate_number", "")
            region = body.get("region", "Farg'ona viloyati")

            driver_id, full_name, clean_phone, car_m, car_p, sql_q = insert_user_to_sql(
                phone, first_name, last_name, email, car_model, car_plate, region, plan="FREE"
            )
            all_tables = get_all_tables_data()
            resp = {
                "status": "ok",
                "token": f"hamroh_sql_user_{driver_id}",
                "driver_id": driver_id,
                "full_name": full_name,
                "phone": clean_phone,
                "car_model": car_m,
                "car_plate_number": car_p,
                "sql_executed": sql_q,
                "tables": all_tables,
            }
            self._set_cors(200)
            self.wfile.write(json.dumps(resp, ensure_ascii=False).encode("utf-8"))
            return

        # 2. ILOVADAN TIZIMGA KIRISH (/api/v1/auth/login)
        if path == "/api/v1/auth/login":
            phone = body.get("phone", "")
            driver_id, full_name, clean_phone, car_m, car_p, sql_q = insert_user_to_sql(
                phone, body.get("first_name", "Haydovchi"), body.get("last_name", ""), "", "Chevrolet Cobalt", "", "O'zbekiston", plan="PRO"
            )
            get_all_tables_data()
            resp = {
                "status": "ok",
                "token": f"hamroh_login_{driver_id}",
                "user": {
                    "driver_id": driver_id,
                    "phone": clean_phone,
                    "first_name": full_name.split()[0],
                    "last_name": " ".join(full_name.split()[1:]),
                    "is_subscribed": True,
                }
            }
            self._set_cors(200)
            self.wfile.write(json.dumps(resp, ensure_ascii=False).encode("utf-8"))
            return

        # 3. GOOGLE ORQALI KIRISH (/api/v1/auth/google)
        if path == "/api/v1/auth/google":
            email = body.get("email", "google.user@gmail.com")
            name = body.get("name", "Google Foydalanuvchi")
            parts = name.split(" ", 1)
            fn = parts[0]
            ln = parts[1] if len(parts) > 1 else ""
            driver_id, full_name, clean_phone, car_m, car_p, sql_q = insert_user_to_sql(
                f"+99890{int(time.time()) % 10000000:07d}", fn, ln, email, "Chevrolet Gentra", "", "Toshkent shahri", plan="PRO"
            )
            get_all_tables_data()
            resp = {
                "status": "ok",
                "token": f"hamroh_google_{driver_id}",
                "user": {"email": email, "name": full_name, "is_subscribed": True}
            }
            self._set_cors(200)
            self.wfile.write(json.dumps(resp, ensure_ascii=False).encode("utf-8"))
            return

        # 4. PRO OBUNA TO'LOVI (/api/v1/subscription/pay)
        if path == "/api/v1/subscription/pay":
            ensure_db_initialized()
            conn = sqlite3.connect(DB_FILE)
            conn.execute("UPDATE drivers SET subscription_plan = 'PRO' WHERE driver_id = (SELECT MAX(driver_id) FROM drivers);")
            conn.commit()
            conn.close()
            get_all_tables_data()
            self._set_cors(200)
            self.wfile.write(json.dumps({"status": "success", "expires_at": int(time.time()) + 30 * 86400}).encode("utf-8"))
            return

        # Standart javob
        self._set_cors(200)
        self.wfile.write(json.dumps({"status": "ok", "token": "hamroh_tok_live", "delivered": 1}).encode("utf-8"))


def run_server(port=8000):
    ensure_db_initialized()
    get_all_tables_data()
    server = HTTPServer(("0.0.0.0", port), HamrohSQLHandler)
    print("=" * 72)
    print(f"  HAMROH AI — JONLI SQL BACKEND SERVERI ISHGA TUSHDI!")
    print(f"  Lokal Boshqaruv Paneli : http://localhost:{port}")
    print(f"  Mobil APK API Manzili  : http://192.168.199.78:{port}")
    print(f"  SQL Baza Fayli         : {DB_FILE}")
    print("=" * 72)
    server.serve_forever()


if __name__ == "__main__":
    run_server(8000)
