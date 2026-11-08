-- ============================================================================
-- HAMROH AI — MILLIY SUN'IY INTELLEKT VA YO'L XAVFSIZLIGI PLATFORMASI
-- MA'LUMOTLAR BAZASI SXEMASI VA BOSHLANG'ICH MA'LUMOTLAR (SQLite / PostgreSQL)
-- Muallif: Ismoilov Abdukamol (Farg'ona davlat texnika universiteti - FDTU)
-- ============================================================================

PRAGMA foreign_keys = ON;

-- Eski jadvallar bo'lsa tozalash (qayta yaratish uchun)
DROP VIEW IF EXISTS v_driver_safety_summary;
DROP VIEW IF EXISTS v_critical_alerts_report;
DROP TABLE IF EXISTS sos_emergency_incidents;
DROP TABLE IF EXISTS adas_road_hazards;
DROP TABLE IF EXISTS dms_biometric_logs;
DROP TABLE IF EXISTS offline_poi_radars;
DROP TABLE IF EXISTS trips;
DROP TABLE IF EXISTS devices_smart_box;
DROP TABLE IF EXISTS drivers;

-- ============================================================================
-- 1-JADVAL: DRIVERS (Haydovchilar va ularning milliy avtomobillari)
-- ============================================================================
CREATE TABLE drivers (
    driver_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name           TEXT NOT NULL,
    phone_number        TEXT NOT NULL UNIQUE,
    car_model           TEXT NOT NULL,          -- Masalan: Chevrolet Cobalt, Nexia-3, Damas, Spark, Matiz
    car_plate_number    TEXT NOT NULL UNIQUE,   -- Masalan: 40 A 777 AA (Farg'ona), 01 M 555 BB (Toshkent)
    region              TEXT NOT NULL,          -- Viloyat / Shahar
    experience_years    INTEGER DEFAULT 1,      -- Haydovchilik tajribasi (yangi haydovchilar uchun AI ustoz rejimi)
    voice_language      TEXT DEFAULT 'uz',      -- Ogohlantirish tili: 'uz', 'ru', 'en'
    family_sos_phone    TEXT NOT NULL,          -- Oila a'zosining favqulodda SMS raqami
    safety_score        REAL DEFAULT 100.0,     -- Umumiy xavfsizlik reytingi (0 - 100 ball)
    subscription_plan   TEXT DEFAULT 'PRO',     -- 'FREE', 'PRO', 'B2B_FLEET'
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 2-JADVAL: DEVICES_SMART_BOX (Smart Box apparatlari va mobil qurilmalar)
-- ============================================================================
CREATE TABLE devices_smart_box (
    device_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    driver_id           INTEGER NOT NULL,
    serial_number       TEXT NOT NULL UNIQUE,   -- Masalan: HMAI-BOX-2026-0001
    device_type         TEXT NOT NULL,          -- 'SMART_VISION_BOX', 'ANDROID_TABLET', 'SMARTPHONE_APP'
    connection_port     TEXT DEFAULT 'TYPE-C',  -- 'TYPE-C USB', 'WIRELESS_LOCAL'
    npu_processor       TEXT DEFAULT 'Rockchip RK3566 INT8 NPU (12ms)',
    firmware_version    TEXT DEFAULT 'v2.4.0-offline',
    is_offline_ready    INTEGER DEFAULT 1,      -- 1 = 100% Internetsiz ishlashga tayyor
    last_sync_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id) REFERENCES drivers(driver_id) ON DELETE CASCADE
);

-- ============================================================================
-- 3-JADVAL: TRIPS (Haydovchining safarlari tarixi)
-- ============================================================================
CREATE TABLE trips (
    trip_id             INTEGER PRIMARY KEY AUTOINCREMENT,
    driver_id           INTEGER NOT NULL,
    device_id           INTEGER NOT NULL,
    start_location      TEXT NOT NULL,
    end_location        TEXT NOT NULL,
    distance_km         REAL NOT NULL,
    duration_minutes    INTEGER NOT NULL,
    max_speed_kmh       REAL NOT NULL,
    avg_speed_kmh       REAL NOT NULL,
    drowsiness_alerts   INTEGER DEFAULT 0,      -- Safar davomida necha marta mudrash aniqlandi
    collision_warnings  INTEGER DEFAULT 0,      -- Necha marta yo'ldagi xavf (bola/mashina) aniqlandi
    trip_safety_score   REAL DEFAULT 100.0,     -- Shu safar uchun yakuniy ball
    network_mode        TEXT DEFAULT '100% OFFLINE',
    started_at          DATETIME NOT NULL,
    ended_at            DATETIME NOT NULL,
    FOREIGN KEY (driver_id) REFERENCES drivers(driver_id) ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES devices_smart_box(device_id) ON DELETE CASCADE
);

-- ============================================================================
-- 4-JADVAL: DMS_BIOMETRIC_LOGS (Old kamera: 468 nuqtali yuz, ko'z va esnash tahlili)
-- ============================================================================
CREATE TABLE dms_biometric_logs (
    dms_log_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    trip_id             INTEGER NOT NULL,
    driver_id           INTEGER NOT NULL,
    event_type          TEXT NOT NULL,          -- 'MIKROUYQU', 'ESNASH_CHARCHOQ', 'CHALGISH_TELEFON', 'NORMAL'
    ear_eye_ratio       REAL NOT NULL,          -- Ko'z ochiqligi koeffitsienti (EAR < 0.22 bo'lsa ko'z yumuq)
    mar_mouth_ratio     REAL NOT NULL,          -- Og'iz ochiqligi koeffitsienti (MAR > 0.65 bo'lsa esnash)
    head_yaw_deg        REAL DEFAULT 0.0,       -- Boshning chap/o'ngga burilish burchagi (gradus)
    eye_closed_ms       INTEGER DEFAULT 0,      -- Ko'z necha millisekund yumuq turdi
    confidence_pct      REAL DEFAULT 96.4,      -- Biometrik aniqlik foizi
    voice_alert_uz      TEXT NOT NULL,          -- O'zbek tilida berilgan ovozli buyruq
    gps_lat             REAL NOT NULL,
    gps_lon             REAL NOT NULL,
    recorded_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id) ON DELETE CASCADE,
    FOREIGN KEY (driver_id) REFERENCES drivers(driver_id) ON DELETE CASCADE
);

-- ============================================================================
-- 5-JADVAL: ADAS_ROAD_HAZARDS (Orqa kamera: 30 FPS yo'l skaneri, bola va avariya xavfi)
-- ============================================================================
CREATE TABLE adas_road_hazards (
    hazard_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    trip_id             INTEGER NOT NULL,
    driver_id           INTEGER NOT NULL,
    detected_object     TEXT NOT NULL,          -- 'BOLA_YUGURMOQDA', 'PIYODA_OTISH_JOYI', 'AVTOMOBIL_KESKIN_TORMOZ', 'YOL_BELGISI'
    distance_meters     REAL NOT NULL,          -- Xavfgacha qolgan masofa (metr)
    ttc_seconds         REAL NOT NULL,          -- To'qnashuvgacha qolgan vaqt (Time-To-Collision, soniya)
    car_speed_kmh       REAL NOT NULL,          -- O'sha lahzadagi mashina tezligi
    npu_inference_ms    REAL DEFAULT 12.0,      -- Neyron tarmoq tahlil vaqti (millisekund)
    severity_level      TEXT NOT NULL,          -- 'KRITIK_XAVF', 'OGOHLANTIRISH', 'AXBOROT'
    voice_warning_text  TEXT NOT NULL,          -- O'zbekcha shoshilinch ovozli xabar
    driver_braked_in_time INTEGER DEFAULT 1,    -- 1 = Haydovchi o'z vaqtida tormoz bosib xavfni bartaraf etdi
    gps_lat             REAL NOT NULL,
    gps_lon             REAL NOT NULL,
    recorded_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id) ON DELETE CASCADE,
    FOREIGN KEY (driver_id) REFERENCES drivers(driver_id) ON DELETE CASCADE
);

-- ============================================================================
-- 6-JADVAL: OFFLINE_POI_RADARS (Internetsiz ishlovchi Radarlar, Maktablar va Yo'l belgilari)
-- ============================================================================
CREATE TABLE offline_poi_radars (
    poi_id              INTEGER PRIMARY KEY AUTOINCREMENT,
    poi_type            TEXT NOT NULL,          -- 'FOTORADAR', 'MAKTAB_HUDUDI', 'XAVFLI_CHORRAHA', 'TOG_DOVONI', 'METAN_SHOXOBCHA'
    title_uz            TEXT NOT NULL,
    region              TEXT NOT NULL,
    road_name           TEXT NOT NULL,
    speed_limit_kmh     INTEGER DEFAULT 60,     -- Tezlik cheklovi (km/soat)
    alert_distance_m    INTEGER DEFAULT 450,    -- Necha metr oldin ovozli ogohlantirish beriladi
    gps_lat             REAL NOT NULL,
    gps_lon             REAL NOT NULL,
    voice_prompt_uz     TEXT NOT NULL,
    is_active           INTEGER DEFAULT 1
);

-- ============================================================================
-- 7-JADVAL: SOS_EMERGENCY_INCIDENTS (Avto-SOS va G-Sensor avariya xabarlari)
-- ============================================================================
CREATE TABLE sos_emergency_incidents (
    incident_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    trip_id             INTEGER NOT NULL,
    driver_id           INTEGER NOT NULL,
    impact_g_force      REAL NOT NULL,          -- Zarba kuchi (G-sensor, masalan 4.8 G)
    incident_status     TEXT NOT NULL,          -- 'AVTO_SMS_YUBORILDI', 'HAYDOVCHI_BEKOR_QILDI_XAVFSIZ', 'TEZ_YORDAM_112_CHAQIRILDI'
    sms_sent_to         TEXT NOT NULL,          -- Oila raqami va 112 xizmati
    gps_lat             REAL NOT NULL,
    gps_lon             REAL NOT NULL,
    map_short_link      TEXT NOT NULL,
    occurred_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id) ON DELETE CASCADE,
    FOREIGN KEY (driver_id) REFERENCES drivers(driver_id) ON DELETE CASCADE
);

-- ============================================================================
-- TEZKOR QIDIRUV INDEKSLARI (12ms ichida oflayn qidirish uchun)
-- ============================================================================
CREATE INDEX idx_trips_driver ON trips(driver_id);
CREATE INDEX idx_dms_trip ON dms_biometric_logs(trip_id, event_type);
CREATE INDEX idx_adas_trip ON adas_road_hazards(trip_id, severity_level);
CREATE INDEX idx_poi_coords ON offline_poi_radars(gps_lat, gps_lon, poi_type);

-- ============================================================================
-- ANALITIK KO'RINISHLAR (SQL VIEWS — Hisobot va Dashboard uchun)
-- ============================================================================
CREATE VIEW v_driver_safety_summary AS
SELECT 
    d.driver_id,
    d.full_name,
    d.car_model,
    d.car_plate_number,
    d.region,
    d.safety_score,
    COUNT(DISTINCT t.trip_id) AS jami_safarlar,
    ROUND(COALESCE(SUM(t.distance_km), 0), 1) AS jami_masofa_km,
    COALESCE(SUM(t.drowsiness_alerts), 0) AS jami_uyqu_ogohlantirish,
    COALESCE(SUM(t.collision_warnings), 0) AS jami_xavf_ogohlantirish
FROM drivers d
LEFT JOIN trips t ON d.driver_id = t.driver_id
GROUP BY d.driver_id;

CREATE VIEW v_critical_alerts_report AS
SELECT 
    'ADAS_YOL_XAVFI' AS tizim_turi,
    d.full_name AS haydovchi,
    d.car_model AS avtomobil,
    a.detected_object AS hodisa_turi,
    ROUND(a.distance_meters, 1) || ' metr (TTC: ' || ROUND(a.ttc_seconds, 1) || 's)' AS texnik_korsatkich,
    a.voice_warning_text AS ovozli_xabar,
    a.recorded_at AS vaqt
FROM adas_road_hazards a
JOIN drivers d ON a.driver_id = d.driver_id
UNION ALL
SELECT 
    'DMS_YUZ_BIOMETRIYA' AS tizim_turi,
    d.full_name AS haydovchi,
    d.car_model AS avtomobil,
    m.event_type AS hodisa_turi,
    'EAR: ' || ROUND(m.ear_eye_ratio, 2) || ' | MAR: ' || ROUND(m.mar_mouth_ratio, 2) AS texnik_korsatkich,
    m.voice_alert_uz AS ovozli_xabar,
    m.recorded_at AS vaqt
FROM dms_biometric_logs m
JOIN drivers d ON m.driver_id = d.driver_id
WHERE m.event_type != 'NORMAL'
ORDER BY vaqt DESC;

-- ============================================================================
-- HAQIQIY BOSHLANG'ICH MA'LUMOTLAR (SEED DATA — O'ZBEKISTON AVTOPARKI)
-- ============================================================================

INSERT INTO drivers (full_name, phone_number, car_model, car_plate_number, region, experience_years, voice_language, family_sos_phone, safety_score, subscription_plan) VALUES
('Ismoilov Abdukamol', '+998901234567', 'Chevrolet Cobalt', '40 A 777 AA', 'Farg''ona viloyati', 4, 'uz', '+998901112233', 98.5, 'PRO'),
('Ahadjonov Asadullo', '+998912345678', 'Chevrolet Nexia-3', '40 B 555 BB', 'Farg''ona viloyati', 3, 'uz', '+998912223344', 95.0, 'PRO'),
('Karimov Rustamjon', '+998933456789', 'Chevrolet Damas', '40 C 312 DA', 'Marg''ilon shahri', 8, 'uz', '+998933334455', 89.4, 'B2B_FLEET'),
('Tursunov Javohir', '+998944567890', 'Chevrolet Spark', '01 M 880 KA', 'Toshkent shahri', 1, 'uz', '+998944445566', 92.0, 'FREE'),
('Qodirov Ulug''bek', '+998975678901', 'Daewoo Matiz', '50 E 404 MA', 'Namangan viloyati', 6, 'uz', '+998975556677', 91.2, 'PRO'),
('Nazarov Bekzod', '+998996789012', 'Chevrolet Gentra', '60 K 909 LA', 'Andijon viloyati', 5, 'ru', '+998996667788', 96.8, 'B2B_FLEET');

INSERT INTO devices_smart_box (driver_id, serial_number, device_type, connection_port, npu_processor, firmware_version, is_offline_ready) VALUES
(1, 'HMAI-BOX-2026-0001', 'SMART_VISION_BOX', 'TYPE-C USB', 'Rockchip RK3566 INT8 NPU (12ms)', 'v2.4.0-offline', 1),
(2, 'HMAI-BOX-2026-0002', 'SMART_VISION_BOX', 'TYPE-C USB', 'Rockchip RK3566 INT8 NPU (12ms)', 'v2.4.0-offline', 1),
(3, 'HMAI-TAB-2026-0003', 'ANDROID_TABLET', 'TYPE-C USB', 'Snapdragon Hexagon NPU (14ms)', 'v2.4.0-offline', 1),
(4, 'HMAI-APP-2026-0004', 'SMARTPHONE_APP', 'PESHOYNA_MAGNIT', 'Apple Neural Engine / TFLite', 'v2.4.0-offline', 1),
(5, 'HMAI-BOX-2026-0005', 'SMART_VISION_BOX', 'TYPE-C USB', 'Rockchip RK3566 INT8 NPU (12ms)', 'v2.4.0-offline', 1),
(6, 'HMAI-BOX-2026-0006', 'SMART_VISION_BOX', 'TYPE-C USB', 'Rockchip RK3566 INT8 NPU (12ms)', 'v2.4.0-offline', 1);

INSERT INTO trips (driver_id, device_id, start_location, end_location, distance_km, duration_minutes, max_speed_kmh, avg_speed_kmh, drowsiness_alerts, collision_warnings, trip_safety_score, network_mode, started_at, ended_at) VALUES
(1, 1, 'Farg''ona sh. (FDTU)', 'Qo''qon sh.', 88.4, 78, 86.0, 68.0, 0, 1, 98.5, '100% OFFLINE', '2026-11-06 08:15:00', '2026-11-06 09:33:00'),
(1, 1, 'Qo''qon sh.', 'Toshkent sh. (Qamchiq dovoni orqali)', 235.0, 210, 95.0, 67.1, 1, 2, 96.0, '100% OFFLINE (DOVON)', '2026-11-06 18:00:00', '2026-11-06 21:30:00'),
(2, 2, 'Farg''ona markazi', 'Marg''ilon temir yo''l vokzali', 16.5, 24, 62.0, 41.2, 0, 1, 95.0, '100% OFFLINE', '2026-11-07 09:10:00', '2026-11-07 09:34:00'),
(3, 3, 'Marg''ilon buyum bozori', 'Andijon sh.', 74.2, 85, 82.0, 52.4, 2, 2, 88.5, '100% OFFLINE', '2026-11-07 22:30:00', '2026-11-07 23:55:00'),
(4, 4, 'Toshkent (Chilonzor)', 'Yunusobod IT Park', 19.8, 32, 68.0, 37.1, 0, 1, 93.0, '100% OFFLINE', '2026-11-08 10:00:00', '2026-11-08 10:32:00'),
(5, 5, 'Namangan sh.', 'Chust tumani', 42.0, 45, 78.0, 56.0, 1, 1, 91.2, '100% OFFLINE', '2026-11-08 14:20:00', '2026-11-08 15:05:00');

INSERT INTO dms_biometric_logs (trip_id, driver_id, event_type, ear_eye_ratio, mar_mouth_ratio, head_yaw_deg, eye_closed_ms, confidence_pct, voice_alert_uz, gps_lat, gps_lon, recorded_at) VALUES
(2, 1, 'ESNASH_CHARCHOQ', 0.27, 0.72, 3.5, 320, 97.2, 'Diqqat! Charchoq alomatlari aniqlandi. Qahva ichib dam oling!', 41.0845, 70.6214, '2026-11-06 19:45:12'),
(4, 3, 'MIKROUYQU', 0.16, 0.31, -8.2, 1380, 98.1, 'DIQQAT! UYG''ONING! O''ZINGIZGA KELING! Ko''zingiz yumilmoqda!', 40.6124, 72.0541, '2026-11-07 23:18:44'),
(4, 3, 'ESNASH_CHARCHOQ', 0.24, 0.69, 4.0, 410, 96.5, 'Charchoq aniqlandi! Yo''l chetida 10 daqiqa to''xtab oling!', 40.6890, 72.1980, '2026-11-07 23:39:05'),
(6, 5, 'CHALGISH_TELEFON', 0.29, 0.25, 29.4, 0, 95.8, 'Diqqatni yo''lga qarating! Telefonga chalg''imang!', 41.0012, 71.3845, '2026-11-08 14:42:19'),
(1, 1, 'NORMAL', 0.32, 0.21, 1.2, 0, 98.9, 'Biometrik holat: Normal (Diqqat 98%)', 40.3864, 71.7864, '2026-11-06 08:30:00');

INSERT INTO adas_road_hazards (trip_id, driver_id, detected_object, distance_meters, ttc_seconds, car_speed_kmh, npu_inference_ms, severity_level, voice_warning_text, driver_braked_in_time, gps_lat, gps_lon, recorded_at) VALUES
(1, 1, 'BOLA_YUGURMOQDA', 11.4, 1.3, 44.0, 11.8, 'KRITIK_XAVF', 'XAVF! O''ng tomondan bola yugurib chiqdi! Zudlik bilan tormoz bosing!', 1, 40.3892, 71.7831, '2026-11-06 08:28:15'),
(2, 1, 'AVTOMOBIL_KESKIN_TORMOZ', 18.5, 1.6, 72.0, 12.1, 'KRITIK_XAVF', 'Diqqat! Oldindagi yuk mashinasi keskin tormoz bosdi! Masofani saqlang!', 1, 41.0920, 70.6350, '2026-11-06 20:12:33'),
(3, 2, 'PIYODA_OTISH_JOYI', 14.2, 1.8, 52.0, 12.0, 'OGOHLANTIRISH', 'Diqqat! Piyodalar o''tish joyida odam bor, tezlikni pasaytiring!', 1, 40.4215, 71.7512, '2026-11-07 09:22:10'),
(4, 3, 'AVTOMOBIL_KESKIN_TORMOZ', 9.8, 1.1, 65.0, 11.9, 'KRITIK_XAVF', 'XAVF! To''qnashuv ehtimoli! Darhol tormoz bosing!', 1, 40.6510, 72.1120, '2026-11-07 23:25:50'),
(5, 4, 'YOL_BELGISI', 35.0, 3.2, 58.0, 11.5, 'AXBOROT', 'Yangi haydovchi yordamchisi: Maktab hududi belgisi, tezlikni 30 km/soatga tushiring.', 1, 41.2856, 69.2045, '2026-11-08 10:14:22');

INSERT INTO offline_poi_radars (poi_type, title_uz, region, road_name, speed_limit_kmh, alert_distance_m, gps_lat, gps_lon, voice_prompt_uz) VALUES
('FOTORADAR', 'Al-Farg''oniy shoh ko''chasi Hikvision radari', 'Farg''ona sh.', 'Al-Farg''oniy ko''chasi', 60, 450, 40.3864, 71.7864, 'Oldinda 450 metrda fotoradar! Cheklangan tezlik 60 kilometr soat.'),
('MAKTAB_HUDUDI', 'Farg''ona 1-sonli ixtisoslashgan maktab o''tish joyi', 'Farg''ona sh.', 'Mustaqillik ko''chasi', 30, 350, 40.3812, 71.7795, 'Diqqat! Oldinda maktab hududi va bolalar o''tish joyi! Tezlik 30.'),
('XAVFLI_CHORRAHA', 'Marg''ilon - Farg''ona markaziy halqa chorrahasi', 'Farg''ona vil.', 'A-373 xalqaro trassasi', 60, 500, 40.4310, 71.7420, 'Diqqat! Gavjum chorraha, piyodalar va svetoforga e''tiborli bo''ling!'),
('TOG_DOVONI', 'Qamchiq dovoni xavfli burilish va tuman hududi', 'Namangan - Toshkent', 'A-373 Qamchiq dovoni', 50, 600, 41.0845, 70.6214, '100% Oflayn rejim faol: Oldinda Qamchiq dovoni keskin burilishi, masofani 30 metr saqlang!'),
('FOTORADAR', 'Amir Temur shoh ko''chasi Kompleks Radar', 'Toshkent sh.', 'Amir Temur shoh ko''chasi', 60, 450, 41.3111, 69.2797, 'Oldinda fotoradar va avtobus yo''lagi kamerasi! Tezlik 60.'),
('METAN_SHOXOBCHA', 'Farg''ona Eko-Metan 24/7 Avto-Gaz Shoxobchasi', 'Farg''ona vil.', 'Farg''ona halqa yo''li', 40, 400, 40.3980, 71.8120, 'O''ng tomonda 400 metrda Metan gaz quyish shoxobchasi mavjud.');

INSERT INTO sos_emergency_incidents (trip_id, driver_id, impact_g_force, incident_status, sms_sent_to, gps_lat, gps_lon, map_short_link, occurred_at) VALUES
(4, 3, 3.4, 'HAYDOVCHI_BEKOR_QILDI_XAVFSIZ', '+998933334455 (Oila)', 40.6510, 72.1120, 'https://maps.google.com/?q=40.6510,72.1120', '2026-11-07 23:25:55');
