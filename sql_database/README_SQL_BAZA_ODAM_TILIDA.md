# 🗄️ HAMROH AI — SQL MA'LUMOTLAR BAZASI (ODAM TILIDA TUSHUNTIRISH)

**Muallif:** Ismoilov Abdukamol (FDTU)  
**Baza texnologiyasi:** **SQLite3** (mobil telefon va Smart Box ichida 100% internetsiz ishlash uchun) hamda **PostgreSQL** (markaziy server va taksoparklar uchun).

---

## 🧠 SQL Baza loyihada nima vazifa bajaradi? (Odam tilida)

Ko'pchilik *«Loyihang 100% oflayn (internetsiz) ishlasa, ma'lumotlarni qayerda saqlaydi, radarlarni qayerdan biladi?»* deb so'raydi.  
Javob juda oddiy: **Telefonning yoki Smart Box'ning o'z xotirasida `hamroh_ai_offline.db` nomli mitti, lekin juda tez ishlaydigan SQL baza bor!**

Ushbu SQL bazada **7 ta jadval (Table)** va **2 ta tayyor hisobot oynasi (View)** mavjud:

---

### 📋 1-Jadval: `drivers` (Haydovchilar va mashinalar)
- **Nima saqlanadi?** Haydovchining ismi-sharifi, telefon raqami, mashinasining rusumi (*Cobalt, Nexia-3, Damas, Spark, Matiz*), davlat raqami (`40 A 777 AA`), necha yillik haydovchi ekani, xavfsizlik reytingi (`0–100 ball`) va avariya bo'lganda SMS boradigan **oila a'zosining raqami**.
- **Nima uchun kerak?** Tizim haydovchining tajribasiga qarab gapiradi (yangi haydovchi bo'lsa yo'l belgilarini ham o'rgatib boradi).

### 📷 2-Jadval: `devices_smart_box` (Smart Box apparatlari)
- **Nima saqlanadi?** Peshoynaga yopishtirilgan **Smart Vision Box** apparatining seriya raqami (`HMAI-BOX-2026-0001`), u qanday ulangani (`Type-C USB` yoki `Planshet`), ichidagi NPU protsessor turi va proshivka versiyasi.

### 🛣️ 3-Jadval: `trips` (Safarlar tarixi)
- **Nima saqlanadi?** Haydovchi qayerdan qayerga borgani (*masalan, Qo'qon — Toshkent Qamchiq dovoni orqali*), necha kilometr yurgani, maksimal tezligi, yo'lda necha marta mudragani va yakunda necha ball olgani.

### 👀 4-Jadval: `dms_biometric_logs` (Oldi kamera — 468 nuqtali yuz skaneri)
- **Nima saqlanadi?** Haydovchi uxlab qolishi (`MIKROUYQU`), esnashi (`ESNASH_CHARCHOQ`) yoki telefonga chalg'igani (`CHALGISH_TELEFON`).
- Shu joyda ko'zning ochiqlik raqami (`ear_eye_ratio = 0.16`), og'iz ochiqligi (`mar_mouth_ratio = 0.72`), necha millisekund ko'z yumuq turgani va o'zbek tilida aytilgan ovozli bong (*«DIQQAT! UYG'ONING!»*) vaqti bilan yozib qo'yiladi.

### 🚨 5-Jadval: `adas_road_hazards` (Orqa kamera — 30 FPS yo'l xavflari)
- **Nima saqlanadi?** Yo'lda to'satdan yugurib chiqqan bola (`BOLA_YUGURMOQDA`), piyoda yoki keskin tormoz bosgan mashina.
- Xavfgacha necha metr qolgani (`11.4 metr`), to'qnashuvgacha necha soniya qolgani (`TTC = 1.3 soniya`) va haydovchi o'z vaqtida tormoz bosib bolani saqlab qolgani (`driver_braked_in_time = 1`) saqlanadi.

### 📍 6-Jadval: `offline_poi_radars` (100% Oflayn Radarlar, Maktablar va Yo'l belgilari)
- **Nima saqlanadi?** O'zbekistondagi barcha fotoradarlar, maktablar oldidagi piyodalar o'tish joylari, Qamchiq dovoni xavfli burilishlari va Metan shoxobchalarining aniq GPS koordinatalari (`gps_lat`, `gps_lon`) hamda tezlik cheklovlari (`30`, `50`, `60 km/s`).
- **Siri nimada?** Bu jadval telefonning ichida turgani uchun, tog'da yoki cho'lda internet o'chib qolsa ham, SQL indeks orqali **0.4 millisekundda** yaqinlashayotgan radarni topib, ovozli ogohlantiradi!

### 🆘 7-Jadval: `sos_emergency_incidents` (Avto-SOS va Avariya xabarlari)
- **Nima saqlanadi?** Agar qattiq zarba bo'lsa, G-sensor kuchi (`masalan, 3.4 G`), xarita ssilkasi va oila a'zosiga hamda 112 ga SMS ketgan-ketmagani yoziladi.
