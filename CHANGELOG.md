# O'zgarishlar jurnali

## 1.0.1 — 2026-10-15
- **Namoyish safari (FR-11):** "Haydash" ekranida "Namoyish safari (simulyatsiya)" tugmasi. GPS o'rniga sintetik
  marshrut haqiqiy RiskEngine va ovoz orqali o'tadi: maktab hududi, piyodalar o'tish joyi, fotoradar, tezlik oshishi,
  sun'iy notekislik, chuqur. Hakamlar oldida mashinasiz ko'rsatish uchun. `DemoRouteTest` shu marshrutni tekshiradi.
- **Yig'ish:** `:core` modulidagi Java 17 toolchain talabi olib tashlandi — Android Studio'ning JDK 21 si bilan yig'iladi.
- **Tekshirildi:** Windows + Android Studio 2025.1 da `gradlew :core:test :app:assembleDebug` — BUILD SUCCESSFUL,
  59/59 yadro testi o'tdi (DemoRouteTest bilan 60/60 — keyingi yig'ishda Windowsda ham tekshiriladi).

## 1.0.0 — 2026-10-15 (birinchi to'liq versiya, TZ 0–4-bosqichlar)

### Qilindi
- **Yadro (`android/core`, sof Kotlin):** RiskEngine, AlertArbiter (ustuvorlik, 30 s dedup, kuzatuv rejimi),
  POI ogohlantirishlari (maktab jadvali, radar yo'nalishi, tezlik oshishi), haydash holati (FR-1.1/1.2),
  GPS Kalman filtri, chuqur va qattiq tormoz, zarba → SOS holat mashinasi (telefon tushishini ajratadi),
  charchoq (EAR), 2 soatlik tanaffus, ekran chalg'ishi, monokulyar masofa + gravitatsiyadan avtokalibrlash,
  IoU + alfa-beta kuzatuvchi, piyoda/bola/oldingi mashina/"yo'l ochiq", CLAHE va qarshi fara niqobi,
  moy va hujjat eslatmalari, haftalik baho, Ed25519 tekshiruvi va so'rov-javob, trek qayta o'ynatish.
- **Android ilova:** avtostart (Bluetooth, Activity Recognition, bildirishnoma zaxirasi), Foreground Service,
  navigator ustidagi oyna, OSM'dan POI yuklash (Overpass) va oflayn kesh, 60 ta oflayn ovoz fayli (uz/ru/en),
  MediaPipe yo'l va yuz modellari, videoregistrator (60 s bo'laklar, zarbada himoya + SHA-256 JSON),
  SOS (to'liq ekran, Telegram + SMS), oila (Telegram taklif, "Yetib bordim", jonli safar),
  jamoaviy xabarlar, metan navbati, xarita, mashina eslatmalari, hamkor takliflari ("Reklama" belgisi),
  haftalik baho kartochkasi, AI baholash (kuzatuv rejimi), kliplar, sozlamalar, mualliflik tekshiruvi (QR).
- **Server:** TZ 5-bo'limdagi barcha API, Telegram webhook, jonli safar sahifasi, rate limit, ma'lumotni o'chirish.
- **Vositalar:** kalit yaratish, imzolash, tekshirish, ovoz yaratish, test treklari.

### TZ'dan ongli farqlar (sababi bilan)
| TZ | Amalda | Sabab |
|---|---|---|
| 15 ta Gradle moduli, Hilt, Room | `core` + `app`, qo'lda DI, SQLiteOpenHelper | Kamroq bog'liqlik — birinchi yig'ishda xato xavfi past; keyin ajratish oson |
| MapLibre oflayn xarita | osmdroid (OSM plitalari, keshlanadi) | Xarita ogohlantirish uchun shart emas (POI oflayn), oddiyroq |
| Retrofit | HttpURLConnection + org.json | Bog'liqliksiz |
| Video ustiga matn yozish | Yonida JSON (vaqt, tezlik, koordinata, SHA-256) | Dalil sifatida kuchliroq, CPU tejaladi |
| aiogram bot | FastAPI webhook ichida | Bitta xizmat, kamroq infratuzilma |
| PostgreSQL + PostGIS | SQLite (sxema mos) | Pilot uchun yetarli; ko'chirish rejalashtirilgan |
| EncryptedSharedPreferences, SQLCipher | Oddiy ichki xotira | Maxfiy kalit ilovada yo'q; 2-versiyada qo'shiladi |
| Studiya ovozlari | espeak-ng sintetik ovoz | Studiya fayllari bilan almashtiriladi (nomlar tayyor) |
| YOLOv8n | EfficientDet-Lite0 | AGPL litsenziyasi muammosi |
| Yo'l va yuz kamerasi birga | Rejim tanlanadi | Ko'p telefonlar ikki kamerani birga ochmaydi |

### Ma'lum cheklovlar
- Android 14+ da fon holatidan kamera xizmatini boshlash taqiqlangan: avtostartda faqat GPS rejimi yonadi,
  kamera ilova ochilganda yoki bildirishnoma bosilganda qo'shiladi.
- `SEND_SMS` ruxsati Google Play'da alohida ruxsatnoma talab qiladi; Play versiyasida SMS zaxirasi o'chirilishi mumkin.
- Kamera AI (FR-4) qurilmada dala sinovidan o'tmagan — standart holatda 14 kunlik kuzatuv rejimida.
