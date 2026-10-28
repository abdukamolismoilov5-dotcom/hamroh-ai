package uz.hamroh.core.replay

import uz.hamroh.core.EngineEvent
import uz.hamroh.core.RiskEngine
import uz.hamroh.core.alerts.LocalClock
import uz.hamroh.core.model.ImuSample
import uz.hamroh.core.model.LocationFix
import uz.hamroh.core.model.Poi
import uz.hamroh.core.model.PoiType
import uz.hamroh.core.vision.Box
import uz.hamroh.core.vision.Detection
import uz.hamroh.core.vision.ObjClass

/**
 * TZ 8-bo'lim: "qayta o'ynatish" testlari uchun trek formati (CSV, '#' — izoh):
 *   CLOCK,<isoDay>,<minuteOfDay>
 *   POI,<id>,<type>,<lat>,<lon>[,<speedLimit>]
 *   L,<tMs>,<lat>,<lon>,<speedMps>,<bearing|->
 *   I,<tMs>,<totalG>,<verticalMs2>
 *   D,<tMs>,<cls>,<conf>,<l>,<t>,<r>,<b>[;<cls>,<conf>,<l>,<t>,<r>,<b>...]   (bo'sh = "D,<tMs>")
 *   E,<tMs>,<ear|->
 *   T,<tMs>                              (vaqt tiki)
 * Ilovadagi "trek yozish" rejimi ham shu formatda yozadi.
 */
object TrackReplay {
    data class Result(val events: List<Pair<Long, EngineEvent>>) {
        val alerts get() = events.mapNotNull { (it.second as? EngineEvent.Raise)?.alert }
    }

    fun run(csv: String, engine: RiskEngine = RiskEngine()): Result {
        val pois = ArrayList<Poi>()
        var clock = LocalClock(1, 10 * 60)
        val out = ArrayList<Pair<Long, EngineEvent>>()
        for (raw in csv.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val f = line.split(',')
            when (f[0]) {
                "CLOCK" -> clock = LocalClock(f[1].toInt(), f[2].toInt())
                "POI" -> pois += Poi(f[1], PoiType.valueOf(f[2]), f[3].toDouble(), f[4].toDouble(), f.getOrNull(5)?.toIntOrNull())
                "L" -> {
                    val t = f[1].toLong()
                    val fix = LocationFix(t, f[2].toDouble(), f[3].toDouble(), f[4].toDouble(), f[5].takeIf { it != "-" }?.toDouble())
                    engine.onLocation(fix, pois, clock).forEach { out += t to it }
                }
                "I" -> {
                    val t = f[1].toLong()
                    engine.onImu(ImuSample(t, f[2].toDouble(), f[3].toDouble())).forEach { out += t to it }
                }
                "D" -> {
                    val t = f[1].toLong()
                    val rest = line.substringAfter("D,$t").removePrefix(",")
                    val dets = if (rest.isBlank()) emptyList() else rest.split(';').map { d ->
                        val p = d.split(',')
                        Detection(ObjClass.valueOf(p[0]), p[1].toFloat(), Box(p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()))
                    }
                    engine.onDetections(t, dets).forEach { out += t to it }
                }
                "E" -> {
                    val t = f[1].toLong()
                    engine.onEar(t, f[2].takeIf { it != "-" }?.toFloat()).forEach { out += t to it }
                }
                "T" -> { val t = f[1].toLong(); engine.tick(t).forEach { out += t to it } }
                else -> error("Noma'lum qator: $line")
            }
        }
        return Result(out)
    }
}
