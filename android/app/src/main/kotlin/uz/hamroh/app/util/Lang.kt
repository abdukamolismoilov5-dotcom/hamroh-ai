package uz.hamroh.app.util

/**
 * Uch tilli matnlar (FR TZ 0-bo'lim, 7-qoida). Matn chaqirilgan joyda uchala tilda yoziladi:
 * `T("Salom", "Привет", "Hello")`. Til ilova ichida almashtiriladi (Prefs.language).
 */
object Lang {
    @Volatile var current: String = "uz"
}

fun T(uz: String, ru: String, en: String): String = when (Lang.current) {
    "ru" -> ru
    "en" -> en
    else -> uz
}
