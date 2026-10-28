# Ovozli xabarlar ro'yxati

Fayllar: `android/app/src/main/res/raw/<til>_<kalit>.ogg` (til: uz, ru, en).
Hozirgi fayllar `tools/make_voice.py` (espeak-ng) bilan yaratilgan — sintetik ovoz. Studiya yozuvi tayyor bo'lganda
**xuddi shu nom bilan** almashtiring (OGG yoki MP3 → OGG, mono, 44.1/22 kHz, -16 LUFS atrofida). Kod o'zgarmaydi.

Matnlar `tools/make_voice.py` dagi `PHRASES` lug'atida (20 ta kalit × 3 til = 60 fayl):
pedestrian_ahead, child_ahead, forward_collision, fatigue_rest, speeding, school_zone, school_zone_enhanced,
kindergarten_zone, speed_camera, crosswalk, speed_bump, pothole, lead_moved, break_suggestion,
phone_distraction, thermal_camera_off, sos_countdown, sos_sent, sos_cancelled, drive_started.
