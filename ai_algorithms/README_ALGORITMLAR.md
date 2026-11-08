# 🧠 Hamroh AI — Barcha Ishlatilgan Algoritmlar va Matematik Modellar

> **Muallif va Ishlanma Egasi:** Ismoilov Abdukamol  
> **Loyiha:** "Hamroh AI" — Avtomobil Faol Yo'l Xavfsizligi va DMS Tizimi  
> **Texnik daraja:** 100% Oflayn, 12ms Edge NPU, 468 Nuqtali Yuz Biometriyasi va ADAS

---

## 📌 Mundarija
1. [DMS (Driver Monitoring System) 468 Nuqtali Yuz Biometriyasi](#1-dms-face-biometrics)
2. [ADAS Old Kamera To'qnashuv Xavfi va TTC Formulalari](#2-adas-collision-warning)
3. [Haydovchi Xavfsizlik Skori va Flot Telemetriya Modeli](#3-driver-safety-scoring)
4. [12ms Edge NPU Konveyeri va Kvantlash Modeli](#4-edge-npu-pipeline)
5. [Professional Video Tahlil Dasturi](#5-video-analyzer)

---

## 1. DMS 468 Nuqtali Yuz Biometriyasi (`01_DMS_Face_Biometrics_468pts.py`)

Haydovchi yuzidagi 468 ta biometrik tayanch nuqtalar orqali quyidagi 3 ta asosiy ko'rsatkich real vaqtda o'lchanadi:

### A) Eye Aspect Ratio (EAR) — Mikrouyquni aniqlash
Ko'zning vertikal va gorizontal o'lchamlari nisbati orqali qovoq yumilishi hisoblanadi:

$$\text{EAR} = \frac{\|p_2 - p_6\| + \|p_3 - p_5\|}{2 \cdot \|p_1 - p_4\|}$$

- **Ochiq ko'z:** $\text{EAR} \ge 0.28$
- **Qisman yumilish:** $0.22 \le \text{EAR} < 0.28$
- **Mikrouyqu (Xavf):** $\text{EAR} < 0.22$ holati $t \ge 1.2$ soniya davom etsa, darhol o'zbek tilida ovozli ogohlantirish beriladi.

### B) Mouth Aspect Ratio (MAR) — Eshnash va charchoq
Og'iz lablarining ochilish darajasi:

$$\text{MAR} = \frac{\|m_2 - m_8\| + \|m_3 - m_7\| + \|m_4 - m_6\|}{3 \cdot \|m_1 - m_5\|}$$

- $\text{MAR} > 0.65$ holati $t \ge 2.0$ soniya davom etsa, eshnash deb qayd etiladi.

### C) Head Pose Estimation (PnP) — Telefonga chalg'ish
3D kanonik yuz modeli va 2D proyeksiyalar orqali burchaklar olinadi:
- **Pitch:** Boshning pastga egilishi ($< -18^\circ$ bo'lsa, telefonga qarash).
- **Yaw:** Boshning chapga/o'ngga burilishi ($> 25^\circ$ bo'lsa, yo'ldan chalg'ish).

---

## 2. ADAS Old Kamera To'qnashuv Xavfi (`02_ADAS_Forward_Collision_Warning.py`)

Old kameradagi piyodalar, harakatlanuvchi bolalar va to'siqlar uchun **Time-To-Collision (TTC)** hisoblanadi:

$$\text{TTC} = \frac{d}{v_{\text{rel}}} = \frac{d}{v_{\text{host}} - v_{\text{target}}}$$

- **$\text{TTC} > 3.5$ s:** Yashil (Xavfsiz masofa).
- **$2.4 < \text{TTC} \le 3.5$ s:** Sariq (Diqqat).
- **$1.5 < \text{TTC} \le 2.4$ s:** Olovrang (Xavfli yaqinlashish).
- **$\text{TTC} \le 1.5$ s yoki Bola masofasi $< 12$ m:** **KRITIK TORMOZ SIGNAL!**

---

## 3. Haydovchi Xavfsizlik Skori (`03_Driver_Risk_Score_Engine.py`)

Haydovchi reytingi $S_0 = 100$ balldan boshlanadi va har bir qoidabuzarlik uchun kamayadi:

$$S(t) = \max\left(0, 100 - \sum w_i N_i(t) + \beta \cdot T_{\text{safe}}\right)$$

- **Mikrouyqu jarimasi:** $-25$ ball
- **Telefonga chalg'ish:** $-15$ ball
- **Kritik tormoz yaqinlashuvi:** $-20$ ball
- **Tiklanish:** Har 60 soniya xavfsiz haydash uchun $+0.5$ ball.

---

## 4. Oflayn Edge NPU Konveyeri (`04_Offline_Edge_NPU_Pipeline.py`)

Smart Vision Box bort apparatida $12.4\text{ ms}$ ichida to'liq sikl:
1. **DMA Zero-Copy kadr uzatish:** $1.05\text{ ms}$
2. **INT8 Bilinear masshtablash:** $2.10\text{ ms}$
3. **NPU parallel neyron tarmoq xulosasi:** $6.95\text{ ms}$
4. **Post-processing va ovozli trigger:** $1.80\text{ ms}$
- **Jami kechikish:** $\approx 12\text{ ms}$ (Sekundiga 80+ FPS gacha tezlik, $0\text{ MB}$ internet talab qilinadi).

---

## 5. Professional Video Tahlil Dasturi (`06_Pro_Video_Analyzer.py`)

Istalgan videoni buyruqlar satridan yoki dasturdan to'g'ridan-to'g'ri tahlil qilish imkoniyati:
```bash
python 06_Pro_Video_Analyzer.py
```
Natija avtomatik ravishda barcha brauzerlar va smartfonlarda ochiluvchi standart **H.264 MP4** formatida telemetriya grafikasi bilan saqlanadi.
