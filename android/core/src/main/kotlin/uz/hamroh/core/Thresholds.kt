package uz.hamroh.core

/**
 * Barcha chegaraviy qiymatlar bitta joyda (TZ 9-bo'lim: "sehrli sonlar yo'q").
 * Har bir qiymat yonida tegishli talab raqami (FR-x.y) ko'rsatilgan.
 */
object Thresholds {
    // --- FR-1: avtomatik ishga tushish ---
    /** FR-1.1: shu tezlikdan yuqori harakat haydash deb hisoblanadi, km/soat. */
    const val AUTOSTART_SPEED_KMH = 15.0
    /** FR-1.1: tezlik shuncha vaqt yuqori turishi kerak, ms. */
    const val AUTOSTART_HOLD_MS = 60_000L
    /** FR-1.2: shu tezlikdan past = to'xtagan, km/soat. */
    const val IDLE_SPEED_KMH = 3.0
    /** FR-1.2: to'xtab turish shuncha davom etsa "kutish" rejimi, ms. */
    const val IDLE_STANDBY_MS = 5 * 60_000L
    /** FR-1.2: to'xtab turish shuncha davom etsa xizmat to'xtaydi, ms. */
    const val IDLE_STOP_MS = 15 * 60_000L

    // --- FR-2: xarita ogohlantirishlari ---
    /** FR-2.2: minimal ogohlantirish masofasi, m. */
    const val POI_MIN_WARN_DISTANCE_M = 150.0
    /** FR-2.2: ogohlantirish shuncha soniya oldin beriladi. */
    const val POI_WARN_LEAD_S = 8.0
    /** FR-2.2: POI harakat yo'nalishidan shuncha gradus ichida bo'lishi kerak. */
    const val POI_SECTOR_DEG = 45.0
    /** FR-2.3: maktab/bog'cha hududi radiusi, m. */
    const val SCHOOL_ZONE_RADIUS_M = 150.0
    /** FR-2.4: cheklovdan shuncha oshgunga qadar jimlik, km/soat. */
    const val SPEEDING_SILENT_KMH = 5.0
    /** FR-2.4: shundan yuqori oshish = ovozli xabar, km/soat. */
    const val SPEEDING_VOICE_KMH = 10.0
    /** Bitta POI uchun takroriy ogohlantirish oralig'i, ms. */
    const val POI_REPEAT_MS = 10 * 60_000L

    // --- FR-2.6: ko'rish ufqi ---
    const val LOOKAHEAD_URBAN_M = 45.0
    const val LOOKAHEAD_ARTERIAL_M = 80.0
    const val LOOKAHEAD_HIGHWAY_M = 140.0
    const val LOOKAHEAD_ARTERIAL_FROM_KMH = 60.0
    const val LOOKAHEAD_HIGHWAY_FROM_KMH = 90.0

    // --- FR-3: jamoaviy xarita ---
    /** FR-3.1: vertikal tezlanish og'ishi, m/s². */
    const val POTHOLE_ACCEL_MS2 = 3.5
    /** FR-3.1: kuzatish oynasi, ms. */
    const val POTHOLE_WINDOW_MS = 300L
    /** FR-3.1: minimal tezlik, km/soat. */
    const val POTHOLE_MIN_SPEED_KMH = 15.0
    /** Ikkita chuqur hodisasi orasidagi minimal vaqt, ms. */
    const val POTHOLE_REFRACTORY_MS = 2_000L
    /** FR-3.2: qattiq tormoz sekinlashishi, m/s². */
    const val HARD_BRAKE_MS2 = 4.0
    /** FR-3.2: sekinlashish davomiyligi, ms. */
    const val HARD_BRAKE_MIN_MS = 500L
    /** FR-3.5: metan holati shuncha vaqtdan keyin eskiradi, ms. */
    const val GAS_STATUS_STALE_MS = 2 * 60 * 60_000L

    // --- FR-4: kamera ---
    /** FR-4.4: mashina kengligi, m. */
    const val CAR_WIDTH_M = 1.8
    /** FR-4.4: koridorning har tomondagi zaxirasi, m. */
    const val CORRIDOR_MARGIN_M = 0.5
    /** FR-4.5: obyekt ketma-ket shuncha inferensda ko'rinishi kerak. */
    const val VISION_MIN_HITS = 2
    /** FR-4.5: ishonch chegarasi. */
    const val VISION_MIN_CONFIDENCE = 0.45f
    /** FR-4.5: maktab hududidagi ishonch chegarasi. */
    const val VISION_MIN_CONFIDENCE_SCHOOL = 0.38f
    /** FR-4.6: shundan yuqori ishonch = ovozli xabar, aks holda "tik". */
    const val VISION_VOICE_CONFIDENCE = 0.45f
    /** FR-4.5: TTC chegarasi, s. */
    const val PEDESTRIAN_TTC_S = 3.5
    /** FR-4.5: obyekt shuncha soniyada koridorga kirishi bashorat qilinadi. */
    const val CORRIDOR_ENTRY_HORIZON_S = 1.8
    /** FR-4.5: minimal tezlik, km/soat. */
    const val VISION_MIN_SPEED_KMH = 0.0
    /** FR-4.7: oldingi mashina TTC chegarasi, s. */
    const val FCW_TTC_S = 3.5
    /** FR-4.7: minimal tezlik, km/soat. */
    const val FCW_MIN_SPEED_KMH = 0.0
    /** FR-4.8: oldingi mashina shuncha vaqt uzoqlashsa "yo'l ochiq", ms. */
    const val LEAD_MOVED_MS = 2_000L
    /** FR-4.9: kuzatuv rejimi davomiyligi, kun (Ovoz darhol ishlashi uchun 0). */
    const val SHADOW_MODE_DAYS = 0
    /** FR-4.11: shundan yorqin piksellar qarshi fara deb niqoblanadi (0..255). */
    const val GLARE_LUMA = 250
    /** FR-4.11: o'rtacha yorug'lik shundan past = tungi rejim (0..255). */
    const val NIGHT_MEAN_LUMA = 60
    /** FR-4.12: shu tezlikdan yuqorida kamera yoqiladi, km/soat. */
    const val CAMERA_ON_SPEED_KMH = 15.0
    /** Odamning o'rtacha bo'yi (bola ehtimolini baholash uchun), m. */
    const val ADULT_HEIGHT_M = 1.65
    /** Shundan past bo'yli piyoda "bola bo'lishi mumkin", m. */
    const val CHILD_HEIGHT_M = 1.35
    /** Kuzatuvchi obyektni yo'qotgunga qadar kutadigan vaqt, ms. */
    const val TRACK_MAX_AGE_MS = 2_200L
    /** Kuzatuvchida bir obyektga moslash uchun minimal IoU. */
    const val TRACK_MIN_IOU = 0.2f

    // --- FR-5: haydovchi holati ---
    /** FR-5.1: EAR shundan past = ko'z yumilgan. */
    const val EAR_CLOSED = 0.21f
    /** FR-5.1: uzluksiz yumilish chegarasi, ms. */
    const val EYES_CLOSED_ALERT_MS = 1_500L
    /** FR-5.1: "uzoq yumilish" (miltillashdan uzun), ms. */
    const val LONG_BLINK_MS = 500L
    /** FR-5.1: oynadagi uzoq yumilishlar soni. */
    const val LONG_BLINKS_IN_WINDOW = 3
    /** FR-5.1: oyna, ms. */
    const val LONG_BLINK_WINDOW_MS = 60_000L
    /** FR-5.3: uzluksiz haydash chegarasi, ms. */
    const val CONTINUOUS_DRIVING_MS = 2 * 60 * 60_000L
    /** FR-5.3: shuncha to'xtash haydashni "uzadi", ms. */
    const val DRIVING_BREAK_RESET_MS = 15 * 60_000L
    /** FR-5.4: tezlik chegarasi, km/soat. */
    const val SCREEN_DISTRACTION_SPEED_KMH = 60.0
    /** FR-5.4: oynada ekran yoqilishlar soni. */
    const val SCREEN_ON_COUNT = 3
    /** FR-5.4: oyna, ms. */
    const val SCREEN_ON_WINDOW_MS = 5 * 60_000L

    // --- FR-6/7: zarba va SOS ---
    const val G = 9.80665
    /** FR-6.2: klipni himoyalash uchun zarba, g. */
    const val DASHCAM_PROTECT_G = 2.5
    /** FR-7.1: SOS uchun zarba, g. */
    const val SOS_IMPACT_G = 4.0
    /** FR-7.1: zarbadan keyingi harakatsizlik, ms. */
    const val SOS_STILL_MS = 10_000L
    /** FR-7.1: bekor qilish uchun hisob, ms. */
    const val SOS_COUNTDOWN_MS = 30_000L
    /** FR-7.4: erkin tushish (umumiy tezlanish shundan past), g. */
    const val FREE_FALL_G = 0.3
    /** FR-7.4: erkin tushish shuncha davom etsa telefon tushgan, ms. */
    const val FREE_FALL_MIN_MS = 150L
    /** FR-7.4: zarba erkin tushishdan keyin shuncha vaqt ichida bo'lsa, tushish deb hisoblanadi, ms. */
    const val FREE_FALL_TO_IMPACT_MS = 1_000L
    /** FR-7.4: shundan past tezlikda tushish SOS emas, km/soat. */
    const val DROP_MAX_SPEED_KMH = 5.0
    /** Harakatsizlik: tezlik shundan past, km/soat. */
    const val STILL_SPEED_KMH = 2.0
    /** Harakatsizlik: tezlanish g dan shuncha og'ishdan oshmasin, m/s². */
    const val STILL_ACCEL_DEV_MS2 = 1.5

    // --- FR-8: baho ---
    const val SCORE_MAX = 100

    // --- FR-9: eslatmalar ---
    const val OIL_INTERVAL_KM_DEFAULT = 7_000
    val REMINDER_STAGES_DAYS = intArrayOf(30, 7, 1)

    // --- FR-10: ovoz ---
    /** FR-10.2: bir xil xabar shu vaqt ichida qayta aytilmaydi, ms. */
    const val ALERT_DEDUP_MS = 12_000L

    // --- FR-AU: mualliflik ---
    /** FR-AU.2: so'rov amal qilish muddati, ms. */
    const val CHALLENGE_TTL_MS = 10 * 60_000L
}
