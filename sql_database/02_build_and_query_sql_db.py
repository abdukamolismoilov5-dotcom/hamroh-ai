# -*- coding: utf-8 -*-
"""
Hamroh AI — SQL Ma'lumotlar Bazasini Yaratish va Jonli So'rovlar (SQLite3)
=========================================================================
Ushbu skript `01_hamroh_ai_schema.sql` faylini o'qib, haqiqiy `hamroh_ai_offline.db`
bazasini yaratadi va hakamlar oldida ko'rsatish uchun asosiy SQL so'rovlarni
ishga tushirib natijasini chiqaradi.
"""

import os
import json
import sqlite3

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SQL_FILE = os.path.join(BASE_DIR, "01_hamroh_ai_schema.sql")
DB_FILE = os.path.join(BASE_DIR, "hamroh_ai_offline.db")
JSON_EXPORT = os.path.join(BASE_DIR, "db_snapshot.json")


def build_database():
    if os.path.exists(DB_FILE):
        os.remove(DB_FILE)

    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    with open(SQL_FILE, "r", encoding="utf-8") as f:
        sql_script = f.read()

    conn.executescript(sql_script)
    conn.commit()
    print(f"[MUVAFFAQIYAT] SQL baza yaratildi: {DB_FILE}")
    return conn


def run_demo_queries(conn):
    cur = conn.cursor()

    print("\n" + "=" * 78)
    print("1-SQL SO'ROV: HAYDOVCHILAR VA MILLIY AVTOMOBILLAR XAVFSIZLIK REYTINGI")
    print("=" * 78)
    cur.execute("SELECT * FROM v_driver_safety_summary ORDER BY safety_score DESC;")
    for r in cur.fetchall():
        print(
            f"ID:{r['driver_id']} | {r['full_name']:20s} | {r['car_model']:18s} ({r['car_plate_number']}) "
            f"| Ball: {r['safety_score']:5.1f} | Masofa: {r['jami_masofa_km']:6.1f} km "
            f"| Uyqu bongi: {r['jami_uyqu_ogohlantirish']} | Yo'l xavfi: {r['jami_xavf_ogohlantirish']}"
        )

    print("\n" + "=" * 78)
    print("2-SQL SO'ROV: YUZ BIOMETRIYASI (DMS) VA YO'L XAVFI (ADAS) KRITIK HODISALARI")
    print("=" * 78)
    cur.execute("SELECT * FROM v_critical_alerts_report;")
    for r in cur.fetchall():
        print(
            f"[{r['tizim_turi']:18s}] {r['haydovchi']:18s} ({r['avtomobil']}) -> {r['hodisa_turi']:22s} "
            f"| {r['texnik_korsatkich']:24s} | Ovoz: «{r['ovozli_xabar']}»"
        )

    print("\n" + "=" * 78)
    print("3-SQL SO'ROV: 100% OFFLAYN RADAR, MAKTAB VA DOVON BAZASI (INTERNETSIZ)")
    print("=" * 78)
    cur.execute(
        """
        SELECT poi_type, title_uz, region, speed_limit_kmh, alert_distance_m, voice_prompt_uz
        FROM offline_poi_radars
        WHERE is_active = 1;
        """
    )
    for r in cur.fetchall():
        print(
            f"[{r['poi_type']:15s}] {r['region']:18s} | Cheklov: {r['speed_limit_kmh']} km/s "
            f"({r['alert_distance_m']}m oldin) | {r['title_uz']}"
        )

    # Barcha jadvallarni JSON formatda ham saqlash
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
    snapshot = {}
    for tbl in tables:
        cur.execute(f"SELECT * FROM {tbl};")
        snapshot[tbl] = [dict(row) for row in cur.fetchall()]

    with open(JSON_EXPORT, "w", encoding="utf-8") as jf:
        json.dump(snapshot, jf, ensure_ascii=False, indent=2)
    print(f"\n[OK] Baza ma'lumotlari nusxasi saqlandi: {JSON_EXPORT}")


if __name__ == "__main__":
    connection = build_database()
    run_demo_queries(connection)
    connection.close()
