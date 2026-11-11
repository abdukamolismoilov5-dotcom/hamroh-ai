# 🛡️ Hamroh AI — Milliy Sun'iy Intellektli Haydovchi Yordamchisi va Faol Yo'l Xavfsizligi Ekotizimi

[![Python](https://img.shields.io/badge/Python-3.10%2B-blue.svg?logo=python&logoColor=white)](https://www.python.org/)
[![Android](https://img.shields.io/badge/Android-Kotlin%20%7C%20Compose-green.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688.svg?logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![Edge AI](https://img.shields.io/badge/Edge%20AI-MediaPipe%20%7C%20TFLite-orange.svg)](https://developers.google.com/mediapipe)
[![Database](https://img.shields.io/badge/Database-SQLite3%20100%25%20Offline-blueviolet.svg?logo=sqlite&logoColor=white)](https://sqlite.org/)
[![License](https://img.shields.io/badge/License-MIT-brightgreen.svg)](LICENSE)

---

## 📌 Mundarija
1. [Loyiha Haqida](#-loyiha-haqida)
2. [O'zbekistondagi Muammo va Dolzarblik](#-ozbekistondagi-muammo-va-dolzarblik)
3. [Innovatsion Yechim](#-innovatsion-yechim)
4. [Tizim Arxitekturasi va Repozitoriy Tuzilmasi](#-tizim-arxitekturasi-va-repozitoriy-tuzilmasi)
5. [AI Algoritmlari va Matematik Modellar](#-ai-algoritmlari-va-matematik-modellar)
6. [SQL Ma'lumotlar Bazasi va Maxfiylik](#-sql-malumotlar-bazasi-va-maxfiylik)
7. [Android Mobil Ilova (Edge-AI Client)](#-android-mobil-ilova-edge-ai-client)
8. [FastAPI Backend Server](#-fastapi-backend-server)
9. [Smart Vision Box ($39 Apparat Ta'minoti)](#-smart-vision-box-39-apparat-taminoti)
10. [Moliyalashtirish Smeta Taqsimoti ($7,000 / $8,000)](#-moliyalashtirish-smeta-taqsimoti-7000--8000)
11. [Ilmiy Tadqiqot va Intellektual Mulk (Patent)](#-ilmiy-tadqiqot-va-intellektual-mulk-patent)
12. [Noldan Ishga Tushirish (Quickstart Guide)](#-noldan-ishga-tushirish-quickstart-guide)
13. [Loyiha Jamoasi](#-loyiha-jamoasi)
14. [Litsenziya va Aloqa](#-litsenziya-va-aloqa)

---

## 📖 Loyiha Haqida

**"Hamroh AI"** — O‘zbekiston avtomobil yo‘llarida sodir bo‘layotgan yo‘l-transport hodisalarining (YTH) bosh omillari bo‘lgan **haydovchining rulda uxlab qolishi (charchoq, mikrouyqu)** hamda **oldindagi to‘siq/piyodalar bilan xavfli masofani vaqtida sezmaslik** muammolarini tubdan hal etuvchi milliy sun’iy intellektli avtonom xavfsizlik ekotizimidir.

Tizim qimmatbaho xorijiy ADAS tizimlariga ($5,000+) to‘liq milliy va hamyonbop muqobil bo‘lib, ikkita formatda taqdim etiladi:
1. **Har qanday Android smartfon uchun bepul mobil ilova** (haydovchi telefonining old va orqa kameralaridan foydalanadi).
2. **Avtoparklar, yuk mashinalari va taksoparklar uchun ixcham apparat ($39 Smart Vision Box)**.

Eng muhim ustunlik: Tizim **100% Oflayn Edge AI** rejimida ishlaydi — Qamchiq dovoni, Ustyurt cho‘llari va mobil internet umuman yo‘q bo‘lgan chekka hududlarda ham uzluksiz hayotni asraydi.

---

## ⚠️ O'zbekistondagi Muammo va Dolzarblik

* 🚨 **10,000+ YTH har yili:** O‘zbekistonda yiliga 10 mingdan ziyod yo‘l-transport hodisasi sodir bo‘ladi.
* 💤 **80%+ Sabab — Inson Omili:** Avariyalarning aksariyati haydovchining toliqishi, tungi yo‘lda ko‘zi ilinib qolishi (mikrouyqu) yoki telefonga chalg‘ishi tufayli ro‘y beradi.
* 📶 **Aloqa yo‘qligi:** Shaharlararo magistral yo‘llarning katta qismida internet sekin yoki butunlay mavjud emas. Bulutga bog‘liq ilovalar bunday vaziyatda ishlamay qoladi.
* 💰 **Xorijiy ADAS qimmatligi:** Mobileye va xorijiy apparatlar qimmat ($2,000 – $5,000), o‘zbek tilida gapirmaydi va mahalliy yo‘l sharoitlariga moslashmagan.

---

## 💡 Innovatsion Yechim

| Xususiyat | Oddiy Radar/Navigatorlar | Xorijiy ADAS (Mobileye) | **Hamroh AI Ekotizimi** |
| :--- | :--- | :--- | :--- |
| **Charchoq va uyquni aniqlash** | ❌ Yo‘q | ⚠️ Faqat qimmat modellarda | ✅ **MediaPipe 468 nuqtali 3D Face Mesh** |
| **To‘qnashuv vaqtini hisoblash (TTC)** | ❌ Yo‘q | ✅ Bor (Radar orqali) | ✅ **3D Monokulyar Gibrid TTC Algoritmi** |
| **Internetga bog‘liqlik** | ⚠️ Internet talab qiladi | ✅ Oflayn apparat | ✅ **100% Oflayn Edge AI (NPU/CPU)** |
| **Til va ovozli ogohlantirish** | Faqat marshrut | Ingliz/Rus | ✅ **O‘zbekcha tabiiy ovozli assistent** |
| **Narx** | Bepul (lekin himoya qilmaydi) | $2,000 – $5,000 | **Bepul Ilova / $39 Smart Vision Box** |
| **Xavfsiz Baza va Maxfiylik** | Serverga video uzatadi | Korporativ yopiq | ✅ **Lokal SQLite3, shaxsiy video tashqariga chiqmaydi** |

---

## 🏛️ Tizim Arxitekturasi va Repozitoriy Tuzilmasi

```text
hamroh-ai/
├── ai_algorithms/               # Python neyron tarmoq va kompyuter ko'rishi algoritmlari
│   ├── 01_DMS_Face_Biometrics_468pts.py     # Yuz to'ri, EAR ko'z ochiqligi, mikrouyqu detektori
│   ├── 02_ADAS_Forward_Collision_Warning.py # Oldindagi xavf, TTC to'qnashuv vaqti, masofa
│   ├── 03_Driver_Risk_Score_Engine.py       # Haydovchi xavfsizlik balini hisoblash mexanizmi
│   ├── 04_Offline_Edge_NPU_Pipeline.py      # Edge NPU/CPU optimallashtirilgan oqim
│   ├── 06_Pro_Video_Analyzer.py             # Dashcam video tahlilchisi
│   └── README_ALGORITMLAR.md                # Algoritmlar bo'yicha batafsil qo'llanma
├── android/                     # Android Kotlin (Production-ready mobil ilova)
│   ├── app/                     # UI (Jetpack Compose), CameraX, Google Auth, Servislar
│   ├── core/                    # Sof Kotlin biznes-mantiq, xavf qoidalari va Unit-testlar
│   └── build.gradle.kts         # Gradle konfiguratsiyasi
├── backend/                     # FastAPI jonli server (Sinxronizatsiya, Bot, SMS, Webhook)
│   ├── app/main.py              # Jonli API endpointlar, Google OAuth, JWT autentifikatsiya
│   ├── hamroh.sqlite3           # Markaziy SQL ma'lumotlar bazasi
│   ├── run_backend.bat          # 1 marta bosish bilan serverni ishga tushirish skripti
│   └── requirements.txt         # Python paketlar (FastAPI, Uvicorn, PyJWT, Cryptography)
├── docs/                        # Loyihaning to'liq rasmiy hujjatlari
│   ├── article/                 # Ilmiy maqolalar (Yangi alifboda, IMRAD formati, Word)
│   ├── presentations/           # Rasmiy taqdimotlar ($7,000 Canva va $8,000 Respublika PPTX)
│   ├── smeta/                   # Rasmiy moliyaviy smetalar ($7,000 & $8,000 DOCX, PDF, Arizalar)
│   ├── play_market/             # Google Play tavsifi va Maxfiylik siyosati
│   └── AUTHORSHIP.md            # Mualliflik va xavfsizlik qoidalari
├── sql_database/                # Ma'lumotlar bazasi arxitekturasi va boshqaruv paneli
│   ├── 01_hamroh_ai_schema.sql  # 8 ta asosiy relatsion jadval va Triggerlar
│   ├── 03_SQL_Baza_Boshqaruv_Paneli.html # Veb interaktiv SQL monitoring paneli
│   └── hamroh_ai_offline.db     # Oflayn SQLite fayli
├── testdata/                    # Sinov treklari va simulyatsiya ma'lumotlari
├── tools/                       # Ovoz sintezi (60 ta audio), POI import va test skriptlari
├── index.html                   # Rasmiy startap vebsayt bosh sahifasi (GitHub Pages)
├── README.md                    # Ushbu to'liq bosh qo'llanma
└── LICENSE                      # MIT Litsenziyasi
```

---

## 🧠 AI Algoritmlari va Matematik Modellar

### 1. DMS (Driver Monitoring System) — Yuz Biometriyasi
* **468 nuqtali 3D MediaPipe Face Mesh:** Ko‘z qovoqlari, ko‘z qorachig‘i, og‘iz burchaklari va bosh burilish burchaklarini 3D koordinatalarda kuzatadi.
* **EAR (Eye Aspect Ratio):** Ko‘zning vertikal va gorizontal nuqtalari nisbatini doimiy hisoblaydi:
  $$\text{EAR} = \frac{\|p_2 - p_6\| + \|p_3 - p_5\|}{2 \|p_1 - p_4\|}$$
  Agar $\text{EAR} < 0.21$ holati 1.2 soniyadan ortiq davom etsa, mikrouyqu alarmi ishga tushadi.
* **MAR (Mouth Aspect Ratio):** Og‘iz ochilishini tahlil qilib, ketma-ket esnash soni bo‘yicha charchoq darajasini belgilaydi.
* **Head Pose Estimation:** Haydovchi nigohi yo‘ldan chetga (telefonga yoki yonga) 2 soniyadan ortiq burilsa, chalg‘ish ogohlantiriladi.

### 2. ADAS (Advanced Driver Assistance System) — Old Ko'rinish
* **INT8 Kvantlangan EfficientDet-Lite0:** NPU chipida soniyasiga 30 kadr (FPS) tezlikda piyodalar, velosipedlar, yengil va yuk avtomobillarini aniqlaydi.
* **3D Monokulyar Masofa Baholash:** Bitta kamera orqali kamera balandligi, optik markaz va obyekt piksellari asosida masofani ($d$) aniqlaydi:
  $$d \approx \frac{f \cdot H_{\text{real}}}{h_{\text{bbox}}}$$
* **TTC (Time-To-Collision):** To‘qnashuvgacha qolgan vaqt:
  $$\text{TTC} = \frac{d}{v_{\text{nisbiy}}}$$
  Agar $\text{TTC} \le 1.5$ soniya bo‘lsa — qizil xavf signali va o‘zbekcha shoshilinch ovoz beriladi.
* **Kalman Alpha-Beta Filtri:** Kamera tebranishlari va shovqinlarni tekislaydi, yolg‘on ogohlantirishlar (false positives) ehtimolini 98% ga kamaytiradi.

---

## 💾 SQL Ma'lumotlar Bazasi va Maxfiylik

Hamroh AI arxitekturasida ma'lumotlar xavfsizligi va maxfiyligi birinchi o‘rinda turadi:
* Haydovchining shaxsiy video oqimi **hech qachon internetga yoki tashqi serverga uzatilmaydi**.
* Barcha hodisalar qurilmaning o‘zidagi lokal **`hamroh.sqlite3`** bazasida qayd etiladi:
  1. `drivers` — haydovchi profili, sozlamalar va umumiy xavfsizlik reytingi;
  2. `trips` — safar vaqti, masofasi, o‘rtacha tezlik va xavf indeksi;
  3. `dms_events` — mikrouyqu, esnash va chalg‘ish voqealari ro‘yxati;
  4. `adas_events` — xavfli yaqinlashish va keskin tormoz holatlari;
  5. `daily_scores` — kunlik 100 ballik haydash xavfsizligi xulosasi;
  6. `system_logs` — NPU harorati, FPS va model ishlash telemetriyasi.

---

## 📱 Android Mobil Ilova (Edge-AI Client)

Ilova zamonaviy **Kotlin** va **Jetpack Compose** deklarativ UI arxitekturasida qurilgan:
* **CameraX Dual Pipeline:** Haydovchi kamerasi (DMS) va yo‘l kamerasini (ADAS) parallel tahlil qilish;
* **O‘zbekcha Tabiiy Ovozlar:** 60 dan ziyod professional tayyorlangan ogohlantirish audiolari (`res/raw`);
* **Jonli Google Auth:** Google hisob tanlash dialogi (Account Chooser) va JWT orqali markaziy serverga xavfsiz ulanish;
* **Energy-Efficient:** NPU va GPU apparat tezlatkichlaridan foydalanib, telefonni qizib ketishdan va batareyani ortiqcha sarflashdan asraydi.

---

## 🖥️ FastAPI Backend Server

* **Texnologiyalar:** Python 3.10+, FastAPI, Uvicorn, PyJWT, Cryptography, SQLite3.
* **Port:** `0.0.0.0:8000` (mahalliy tarmoq va internet orqali ochiq).
* **Imkoniyatlar:**
  * `/api/v1/auth/google` — Jonli Google avtorizatsiyasi va JWT token yaratish;
  * `/api/v1/sync` — Internet paydo bo‘lganda oflayn to‘plangan statistikani markazga xavfsiz sinxronlash;
  * `/api/v1/sos` — Avariya yuz berganda yaqinlariga SMS (Eskiz.uz) va Telegram bot orqali GPS koordinatali xabar yo‘llash;
  * `/docs` — Interaktiv Swagger API hujjatlari.

---

## 📦 Smart Vision Box ($39 Apparat Ta'minoti)

Logistika kompaniyalari, avtobus saroylari va taksoparklar uchun ishlab chiqilgan ixcham apparat:
* **Chip:** Rockchip RK3566 / Allwinner (1.0 TOPS NPU neyron protsessor);
* **Sensorlar:** Keng burchakli Full HD kamera (IR tungi yoritgich bilan), 6-o‘qli akselerometr/giroskop, GPS/GLONASS moduli;
* **Korpus:** Avtomobil old oynasiga 10 soniyada o‘rnatiluvchi ixcham, issiqlikka chidamli korpus;
* **Narx:** Tan narxi ~$28, sotuv narxi ~$39 (bozordagi xorijiy $2,000 lik tizimlardan 50 barobar arzon).

---

## 💰 Moliyalashtirish Smeta Taqsimoti ($7,000 / $8,000)

Startapni kengaytirish va to‘liq ishlab chiqarishga yo‘naltirilgan rasmiy smeta jadvallari:

### A. $7,000 (89,950,000 so'm) Asosiy Variant
| № | Xarajat Yo'nalishi | Qisqa Tavsif | Ajratilgan Mablag' ($) | Ulush (%) |
|---|---|---|---|---|
| **1** | **Reklama va Marketing (PR)** | YouTube, Telegram, Target reklama va PR kampaniyalar | **$2,000** | **28.6%** |
| **2** | **Bulutli GPU (RTX 4090)** | AI neyron tarmoqlarini o'zbek yo'llariga o'rgatish (180 soat) | **$1,200** | **17.1%** |
| **3** | **Bulutli Server va Do'konlar** | 1 yillik VPS server, Google Play Console developer litsenziyasi | **$1,180** | **16.9%** |
| **4** | **Dala Sinovlari (50 ta avto)** | Farg'ona-Toshkent yo'lida 50 ta mashinada 1200L yoqilg'i sinovi | **$1,110** | **15.9%** |
| **5** | **Sinov Smartfonlari** | AI NPU chipli 3 ta test smartfoni (Snapdragon / Dimensity) | **$570** | **8.1%** |
| **6** | **Avto-Holderlar va Shtativlar** | 50 ta mashinaga tebranishsiz peshona tutqichlari | **$370** | **5.3%** |
| **7** | **Adliya Patenti (DGU)** | Davlat patent idorasiga rasmiy ro'yxatdan o'tkazish to'lovlari | **$300** | **4.3%** |
| **8** | **Zaxira Jamg'armasi** | Kutilmagan operatsion va transport xarajatlari | **$270** | **3.8%** |
|  | **JAMI** | **Startapni to'liq yo'lga qo'yish paketi** | **$7,000** | **100%** |

*(Eslatma: Respublika tanlovi uchun $8,000 lik kengaytirilgan smeta `docs/smeta/` papkasida to‘liq ilova qilingan).*

---

## 🔬 Ilmiy Tadqiqot va Intellektual Mulk (Patent)

* **Ilmiy Maqola:** *"Sun’iy intellekt va kompyuter ko‘rishi asosida haydovchining charchoq darajasi hamda xavfli to‘qnashuvlarni real vaqtda aniqlovchi avtonom ekotizim"* (Mualliflar: Bozorov Abdukamol, Ismoilov Abdukamol — FDTU).
* **Standart:** Yangi o‘zbek alifbosida, xalqaro IMRAD ilmiy talablari asosida tayyorlangan (`docs/article/`).
* **Patent:** O‘zbekiston Respublikasi Adliya vazirligi huzuridagi Intellektual mulk agentligiga EHM uchun yaratilgan dastur sifatida rasmiy DGU patent arizasi kiritilgan.

---

## 🚀 Noldan Ishga Tushirish (Quickstart Guide)

### 1. Talablar:
* Windows 10/11 yoki Linux / macOS;
* Python 3.10 yoki undan yuqori;
* Android Studio (Ladybug 2024.2+ yoki yangiroq) va JDK 17 / Android Studio JBR.

### 2. Backend Serverni Ishga Tushirish:
```bash
# Backend papkasiga o'tish
cd backend

# Kutubxonalarni o'rnatish
pip install -r requirements.txt

# Serverni yoqish (yoki run_backend.bat faylini 2 marta bosing)
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```
Server ishga tushgach, brauzerda oching:
* Swagger API Hujjatlari: [http://localhost:8000/docs](http://localhost:8000/docs)

### 3. Android Mobil Ilovani Yig'ish (APK):
```bash
cd android

# Gradle orqali Debug APK yig'ish
.\gradlew.bat assembleDebug
```
Tayyor APK fayl: `android/app/build/outputs/apk/debug/app-debug.apk` manzilida hosil bo‘ladi va uni to‘g‘ridan-to‘g‘ri telefonga o‘rnatish mumkin.

### 4. Veb Boshqaruv Panelini Ko'rish:
`sql_database/03_SQL_Baza_Boshqaruv_Paneli.html` yoki ildizdagi `index.html` faylini istalgan brauzerda ochib, loyihaning barcha ko‘rsatkichlarini jonli ko‘rishingiz mumkin.

---

## 👥 Loyiha Jamoasi

| Ism-sharif | Rol va Lavozim | Mas'uliyat sohasi | Muassasa |
|---|---|---|---|
| **Ismoilov Abdukamol** | **Founder & Lead AI / Backend Developer** | Loyiha asoschisi, Computer Vision (MediaPipe, TFLite), Edge AI neyron tarmoqlari, FastAPI backend va apparat ta'minoti muhandisi | Farg'ona davlat texnika universiteti (FDTU) |
| **Ahadjonov Asadullo** | **Co-Founder & Senior Mobile Developer** | Loyiha hammuassisi, Android Jetpack Compose, CameraX real-vaqt video oqimi, sensorlar telemetriyasi va mobil arxitektura | Farg'ona davlat texnika universiteti (FDTU) |

---

## 📄 Litsenziya va Aloqa

Ushbu loyiha [MIT Litsenziyasi](LICENSE) asosida ochiq manbali sifatida tarqatiladi.

* 🌐 **Startap Veb-Sayti:** [https://abdukamolismoilov5-dotcom.github.io/hamroh-ai](https://abdukamolismoilov5-dotcom.github.io/hamroh-ai)
* 💻 **GitHub Repozitoriy:** [github.com/abdukamolismoilov5-dotcom/hamroh-ai](https://github.com/abdukamolismoilov5-dotcom/hamroh-ai)
* 📧 **Elektron Pochta:** abdukamolismoilov5@gmail.com
* 📱 **Telegram:** [@abdukamo_l](https://t.me/abdukamo_l)
* 🏢 **Muassasa:** Farg'ona davlat texnika universiteti (FDTU), Farg'ona, O'zbekiston.
