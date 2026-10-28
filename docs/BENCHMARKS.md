# O'lchovlar (TZ 4-bo'lim)

Qoida: bu yerda faqat **o'lchangan** raqamlar yoziladi. O'lchanmagan ko'rsatkich — "o'lchanmagan".

## Avtomatik testlar (2026-10-15, bulut muhiti)

| Qism | Natija |
|---|---|
| Yadro (Kotlin, `:core`) | 59 unit test o'tdi, shu jumladan 10 ta qayta o'ynatish treki (`testdata/tracks`) |
| Ed25519 tekshiruvi | RFC 8032 vektorlari + JDK va Python `cryptography` imzolari bilan o'zaro mos |
| Server (FastAPI) | 11 API testi o'tdi |
| Vositalar (Python) | 5 test o'tdi |
| Maktab hududi ogohlantirishi (sintetik trek, 52 km/soat) | hudud chegarasiga ≥ 6 s qolganda (talab bajarildi) |
| Radar ogohlantirishi (sintetik, 100 km/soat) | radarga ≥ 8 s qolganda (talab bajarildi) |

## Qurilmada o'lchanadigan (hali o'lchanmagan)

| Ko'rsatkich | Maqsad | Natija |
|---|---|---|
| Xavfdan ovozgacha (Lite) | ≤ 100 ms | o'lchanmagan (`VoicePlayer.lastLatencyMs` logga yoziladi) |
| Inferens FPS (Helio G85 / SD 680) | ≥ 5 / ≥ 10 | o'lchanmagan (ekranda "kamera N fps") |
| Batareya, 1 soat | Lite ≤ 6%, to'liq ≤ 20% | o'lchanmagan |
| RAM | ≤ 120 / 300 MB | o'lchanmagan |
| 4 soat uzluksiz | qotishsiz | o'lchanmagan |
| Noto'g'ri piyoda signali | ≤ 1 / soat | o'lchanmagan ("AI baholash" bo'limi hisoblaydi) |
