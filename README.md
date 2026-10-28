# Hamroh AI

Oddiy Android telefonni haydovchining aqlli hamrohiga aylantiruvchi ilova. U maktab hududi, radar, piyoda,
chuqur va charchoq haqida oflayn ovoz bilan ogohlantiradi, avariyada yaqinlarga xabar beradi va mashina
bo'yicha majburiy muddatlarni eslatib turadi. Texnik topshiriq: "Hamroh AI — dasturlash uchun texnik topshiriq".

## Tuzilma

```
android/        Android loyiha (Android Studio bilan ochiladi)
  core/         sof Kotlin yadro: barcha xavf qoidalari (Android'siz, unit testlar bilan)
  app/          Android ilova: xizmat, kamera, GPS, UI (Compose)
backend/        FastAPI server (jamoaviy xarita, metan, oila, SOS, jonli safar)
tools/          kalit/imzo vositalari, ovoz yaratish, POI import, test treklari
testdata/       qayta o'ynatish treklari (TZ 8-bo'lim)
docs/           mualliflik, maxfiylik, o'lchovlar, savollar
```

## Ilovani yig'ish (Windows, Android Studio 2025.1+)

1. Android Studio → **Open** → `hamroh-ai/android` papkasini tanlang. Gradle sinxronlashuvini kuting
   (birinchi marta internet kerak: kutubxonalar va 2 ta MediaPipe modeli ~10 MB yuklanadi).
2. **Build → Build App Bundle(s)/APK(s) → Build APK(s)** yoki terminalda `gradlew :app:assembleDebug`.
3. APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Telefonga o'rnating.
4. Testlar: `gradlew :core:test` (60 ta test, shu jumladan `testdata/tracks` treklari).

Reliz (egasining kaliti bilan) — `docs/AUTHORSHIP.md`.

### Emulyatorda sinash
Emulyator → ⋯ → **Location → Routes**: marshrut qo'ying va "Play route" bosing. Ilova haydash boshlanganini
sezadi, atrofdagi OSM obyektlarini yuklaydi va ogohlantira boshlaydi. Kamera AI faqat haqiqiy telefonda ma'noli.

## Server

```
cd backend
pip install -r requirements.txt
set HAMROH_JWT_SECRET=<64 tasodifiy belgi>
set TELEGRAM_BOT_TOKEN=<BotFather tokeni>
set TELEGRAM_BOT_NAME=<bot nomi>
set TELEGRAM_WEBHOOK_SECRET=<tasodifiy>
set HAMROH_PUBLIC_URL=https://sizning-domen
uvicorn app.main:app --host 0.0.0.0 --port 8000
```
Telegram webhook: `https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://sizning-domen/bot/webhook/<TELEGRAM_WEBHOOK_SECRET>`.
SMS (Eskiz.uz): `HAMROH_SMS_MODE=eskiz`, `ESKIZ_EMAIL`, `ESKIZ_PASSWORD`. Docker: `docker build -t hamroh backend`.
Testlar: `python -m pytest backend/tests`.

Ilovada server manzili: **Yana → Sozlamalar → Server** (yoki yig'ishda `HAMROH_SERVER_URL`).
Server bo'lmasa ham ilova ishlaydi: barcha ogohlantirishlar oflayn; faqat jamoaviy xarita, metan, oila va
jonli safar server talab qiladi.

## Ovozlar
`python tools/make_voice.py` — 60 ta fayl (uz/ru/en). Studiya yozuvlari bilan almashtirish: `docs/AUDIO_LIST.md`.

## Mualliflik
`docs/AUTHORSHIP.md`. Yopiq kalit hech qachon repozitoriyga, ilovaga yoki boshqa odamga berilmaydi.
