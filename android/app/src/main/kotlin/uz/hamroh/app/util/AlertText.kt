package uz.hamroh.app.util

import uz.hamroh.core.model.Alert
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.AlertType

/** Ekran va overlay uchun ogohlantirish matni ("buyruq emas, fakt" tamoyili). */
object AlertText {
    fun of(a: Alert): String {
        val limit = a.args["limit"]
        val dist = a.args["distance"]
        return when (a.type) {
            AlertType.PEDESTRIAN -> T("Oldinda piyoda", "Впереди пешеход", "Pedestrian ahead") + (dist?.let { " · $it m" } ?: "")
            AlertType.PEDESTRIAN_CHILD -> T("Oldinda bola bo'lishi mumkin", "Возможно, ребёнок впереди", "Possible child ahead") + (dist?.let { " · $it m" } ?: "")
            AlertType.FORWARD_COLLISION -> T("Oldingi mashinaga juda yaqin", "Опасное сближение", "Too close to the car ahead")
            AlertType.FATIGUE -> T("Charchoq belgilari — dam oling", "Признаки усталости — отдохните", "Signs of fatigue — take a rest")
            AlertType.SPEEDING -> T("Tezlik cheklovdan yuqori", "Скорость выше лимита", "Above the speed limit") + (limit?.let { " ($it)" } ?: "")
            AlertType.SCHOOL_ZONE -> if (a.level == AlertLevel.URGENT) T("Maktab hududi · dars vaqti", "Школьная зона · время занятий", "School zone · school hours")
                else T("Maktab hududi", "Школьная зона", "School zone")
            AlertType.KINDERGARTEN_ZONE -> T("Bog'cha hududi", "Детский сад", "Kindergarten zone")
            AlertType.SPEED_CAMERA -> T("Fotoradar", "Камера скорости", "Speed camera") + (limit?.let { " · $it km/h" } ?: "") + (dist?.let { " · $it m" } ?: "")
            AlertType.CROSSWALK -> T("Piyodalar o'tish joyi", "Пешеходный переход", "Pedestrian crossing")
            AlertType.SPEED_BUMP -> T("Sun'iy notekislik", "Лежачий полицейский", "Speed bump")
            AlertType.POTHOLE -> T("Oldinda chuqur", "Впереди яма", "Pothole ahead")
            AlertType.LEAD_MOVED -> T("Yo'l ochiq", "Можно ехать", "Traffic is moving")
            AlertType.BREAK_SUGGESTION -> T("2 soatdan beri haydayapsiz — tanaffus", "Более 2 часов за рулём — перерыв", "2 hours of driving — take a break")
            AlertType.PHONE_DISTRACTION -> T("Telefonga qaramang", "Не отвлекайтесь на телефон", "Eyes on the road")
            AlertType.SOS_COUNTDOWN -> T("Kuchli zarba sezildi", "Обнаружен сильный удар", "Strong impact detected")
            AlertType.THERMAL -> T("Telefon qizidi — kamera to'xtatildi", "Перегрев — камера приостановлена", "Overheating — camera paused")
        }
    }

    /** Ovoz fayli kaliti (ba'zi turlar uchun darajaga qarab). */
    fun voiceKey(a: Alert): String = when {
        a.type == AlertType.SCHOOL_ZONE && a.level == AlertLevel.URGENT -> "school_zone_enhanced"
        else -> a.type.voiceKey
    }
}
