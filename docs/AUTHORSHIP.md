# Mualliflikni tasdiqlash (FR-AU)

Himoya usulning sirligiga emas, faqat egada turadigan **yopiq kalitga** tayanadi.
Bu hujjatni ochiq e'lon qilish xavfsiz: kalitsiz hech kim imzo yasay olmaydi.

## Bir martalik tayyorgarlik (egasi o'z kompyuterida, internetsiz)

1. `pip install cryptography`
2. `python tools/gen_keys.py --out D:\kalitlar`
   - `owner_private.pem` — parol bilan shifrlangan yopiq kalit. **Hech kimga bermang.** 2 ta fleshkada saqlang.
   - `owner.pub` — ochiq kalit (32 bayt). Uni `android/app/src/main/assets/owner.pub` ga nusxalang
     (bu fayl `.gitignore` da, repozitoriyga tushmaydi).
3. APK imzo kaliti: `keytool -genkeypair -v -keystore D:\kalitlar\hamroh.jks -alias hamroh -keyalg RSA -keysize 4096 -validity 36500`
4. Sertifikat izi: `keytool -list -v -keystore D:\kalitlar\hamroh.jks -alias hamroh` → `SHA256:` qatori.
5. `%USERPROFILE%\.gradle\gradle.properties` fayliga (loyiha papkasiga EMAS):
   ```
   HAMROH_KEYSTORE=D:/kalitlar/hamroh.jks
   HAMROH_KEYSTORE_PASSWORD=...
   HAMROH_KEY_ALIAS=hamroh
   HAMROH_KEY_PASSWORD=...
   EXPECTED_CERT_SHA256=<4-qadamdagi SHA256, ikki nuqtalar bilan ham bo'ladi>
   OWNER_BUILD_TAG=<faqat siz biladigan qiymat>
   HAMROH_SERVER_URL=https://sizning-serveringiz
   ```
6. Reliz: `gradlew :app:assembleRelease`. Yuqoridagilardan biri yo'q bo'lsa, Gradle reliz yig'ishni to'xtatadi.

## Istalgan vaqtda isbotlash

1. Ilovada: **Yana → Ilova → Mualliflikni tekshirish**. Ekranda so'rov (matn + QR) chiqadi.
2. So'rov matnini kompyuteringizga o'tkazing (Telegram "Saqlangan xabarlar" yoki nusxa olish).
3. `python tools/sign_challenge.py --key D:\kalitlar\owner_private.pem --qr "HAMROH-AUTH-v1|..."`
4. Chiqqan imzoni ilovaga kiriting yoki ekrandagi QR'ni ilova bilan skanerlang.
5. Ilova yashil oynada **"EGASI TASDIQLANDI"** va kalit izini ko'rsatadi. So'rov 10 daqiqa amal qiladi va bir martalik.

## Uchinchi shaxs uchun tekshiruv

Hakam yoki ekspert faqat ochiq kalit bilan tekshiradi:

```
python tools/verify.py --pub owner.pub --sig <imzo> "imzolangan matn"
python tools/verify.py --pub owner.pub --sig-file RELEASES.md.sig --file RELEASES.md
```

## Qo'shimcha dalillar

- **APK yaxlitligi (FR-AU.4):** ilova o'z imzo sertifikatini `EXPECTED_CERT_SHA256` bilan solishtiradi.
  Mos kelmasa "rasmiy emas" ogohlantirishi chiqadi.
- **Qurilish belgisi (FR-AU.5):** `OWNER_BUILD_TAG` har bir APK ichida (`BuildConfig`). Uni faqat siz bilasiz.
- **Reliz yozuvlari (FR-AU.6):** har relizda `RELEASES.md` ga manba arxivi va APK SHA-256 yoziladi, keyin
  `python tools/sign_challenge.py --key ... --file RELEASES.md > RELEASES.md.sig`.
- **Kod tarixi (FR-AU.7):** `git config commit.gpgsign true` (yoki SSH imzo), har commit imzolangan.
- **Rasmiy ro'yxatdan o'tkazish:** Intellektual mulk agentligida EHM uchun dastur sifatida.
