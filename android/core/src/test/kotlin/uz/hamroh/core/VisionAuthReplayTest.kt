package uz.hamroh.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.hamroh.core.auth.AuthResult
import uz.hamroh.core.auth.Challenge
import uz.hamroh.core.auth.Ed25519
import uz.hamroh.core.auth.OwnerVerifier
import uz.hamroh.core.crowd.RoadEventType
import uz.hamroh.core.geo.Geo
import uz.hamroh.core.model.AlertLevel
import uz.hamroh.core.model.AlertType
import uz.hamroh.core.replay.TrackReplay
import uz.hamroh.core.safety.CrashSosMachine
import uz.hamroh.core.vision.Box
import uz.hamroh.core.vision.CameraGeometry
import uz.hamroh.core.vision.Detection
import uz.hamroh.core.vision.LumaOps
import uz.hamroh.core.vision.ObjClass
import uz.hamroh.core.vision.Tracker
import uz.hamroh.core.vision.VisionRiskEvaluator
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan

class VisionTest {
    private val g = CameraGeometry()
    private val fy = 0.5 / tan(Math.toRadians(24.0))
    private val fx = 0.5 / tan(Math.toRadians(33.0))

    /** Haqiqiy dunyo holatidan (masofa, yon siljish, bo'y) normallashgan quti yasash. */
    private fun boxFor(d: Double, x: Double, h: Double, w: Double = 0.5): Box {
        val slant = sqrt(d * d + 1.25 * 1.25)
        val bottom = 0.5 + fy * tan(atan(1.25 / d))
        val heightN = h * fy / slant
        val cx = 0.5 + x * fx / slant
        val wN = w * fx / slant
        return Box((cx - wN / 2).toFloat(), (bottom - heightN).toFloat(), (cx + wN / 2).toFloat(), bottom.toFloat())
    }

    @Test fun groundDistanceRoundTrip() {
        for (d in listOf(5.0, 12.0, 25.0, 50.0)) {
            val b = boxFor(d, 0.0, 1.7)
            assertEquals(d, g.groundDistanceM(b.bottom)!!, d * 0.02)
            assertEquals(1.7, g.objectHeightM(b, d), 0.1)
        }
        assertEquals(null, g.groundDistanceM(0.45f))
    }

    @Test fun pitchFromGravity() {
        assertEquals(0.0, CameraGeometry.pitchFromGravity(9.8, 0.0, 0.0), 1e-9)
        assertEquals(Math.toRadians(10.0), CameraGeometry.pitchFromGravity(9.8 * kotlin.math.cos(Math.toRadians(10.0)), 0.0, 9.8 * kotlin.math.sin(Math.toRadians(10.0))), 1e-9)
    }

    private fun runScenario(egoMs: Double, startD: Double, startX: Double, vxMs: Double, height: Double, cls: ObjClass, relClosing: Double = egoMs): List<uz.hamroh.core.model.Alert> {
        val tracker = Tracker { g }
        val eval = VisionRiskEvaluator()
        val alerts = ArrayList<uz.hamroh.core.model.Alert>()
        var d = startD
        var x = startX
        var t = 0L
        repeat(20) {
            val tracks = tracker.update(t, listOf(Detection(cls, 0.8f, boxFor(d, x, height, if (cls.isVehicle) 1.8 else 0.5))))
            alerts += eval.evaluate(t, tracks, g, egoMs, 80.0, false)
            t += 100; d -= relClosing * 0.1; x += vxMs * 0.1
            if (d < 3) return alerts
        }
        return alerts
    }

    @Test fun pedestrianDartingIntoRoad() {
        val a = runScenario(egoMs = 40 / 3.6, startD = 25.0, startX = 3.0, vxMs = -2.0, height = 1.7, cls = ObjClass.PERSON)
        assertTrue(a.any { it.type == AlertType.PEDESTRIAN && it.level == AlertLevel.URGENT })
    }

    @Test fun childDetected() {
        val a = runScenario(egoMs = 40 / 3.6, startD = 22.0, startX = 2.8, vxMs = -2.5, height = 1.1, cls = ObjClass.PERSON)
        assertTrue(a.any { it.type == AlertType.PEDESTRIAN_CHILD })
    }

    @Test fun pedestrianStandingOnSidewalkNoAlert() {
        val a = runScenario(egoMs = 40 / 3.6, startD = 25.0, startX = 4.0, vxMs = 0.0, height = 1.7, cls = ObjClass.PERSON)
        assertTrue(a.isEmpty())
    }

    @Test fun pedestrianIgnoredWhenParked() {
        val a = runScenario(egoMs = 0.0, startD = 10.0, startX = 0.0, vxMs = 0.0, height = 1.7, cls = ObjClass.PERSON, relClosing = 0.0)
        assertTrue(a.isEmpty())
    }

    @Test fun forwardCollision() {
        val a = runScenario(egoMs = 60 / 3.6, startD = 22.0, startX = 0.0, vxMs = 0.0, height = 1.5, cls = ObjClass.CAR, relClosing = 15.0)
        assertTrue(a.any { it.type == AlertType.FORWARD_COLLISION })
    }

    @Test fun followingAtSameSpeedNoFcw() {
        val a = runScenario(egoMs = 60 / 3.6, startD = 20.0, startX = 0.0, vxMs = 0.0, height = 1.5, cls = ObjClass.CAR, relClosing = 0.0)
        assertTrue(a.isEmpty())
    }

    @Test fun leadMovedAway() {
        val tracker = Tracker { g }
        val eval = VisionRiskEvaluator()
        var d = 6.0
        var fired = 0
        var t = 0L
        repeat(60) {
            val tr = tracker.update(t, listOf(Detection(ObjClass.CAR, 0.9f, boxFor(d, 0.0, 1.5, 1.8))))
            fired += eval.evaluate(t, tr, g, 0.0, 45.0, false).count { it.type == AlertType.LEAD_MOVED }
            t += 100; if (it > 10) d += 0.15
        }
        assertEquals(1, fired)
    }

    @Test fun claheBrightensDarkImage() {
        val w = 64; val h = 48
        val img = ByteArray(w * h) { ((it % w) / 4 + 10).toByte() } // 10..25 qorong'u gradient
        val before = LumaOps.meanLuma(img, w, h)
        LumaOps.clahe(img, w, h)
        val after = LumaOps.meanLuma(img, w, h)
        assertTrue("before=$before after=$after", after > before + 40)
        assertTrue(LumaOps.isNight(before))
    }

    @Test fun glareMasked() {
        val img = ByteArray(100) { if (it < 10) 255.toByte() else 50 }
        assertEquals(10, LumaOps.maskGlare(img, 10, 10))
        assertTrue(img.all { (it.toInt() and 0xFF) < 250 })
    }
}

class AuthTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test fun rfc8032Vector1() {
        val pk = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val sig = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        assertTrue(Ed25519.verify(pk, ByteArray(0), sig))
        sig[10] = (sig[10].toInt() xor 1).toByte()
        assertFalse(Ed25519.verify(pk, ByteArray(0), sig))
    }

    @Test fun rfc8032Vector2() {
        val pk = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        val sig = hex("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00")
        assertTrue(Ed25519.verify(pk, hex("72"), sig))
        assertFalse(Ed25519.verify(pk, hex("73"), sig))
    }

    private fun jdkKeys(): Pair<java.security.PrivateKey, ByteArray> {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val enc = kp.public.encoded
        return kp.private to enc.copyOfRange(enc.size - 32, enc.size)
    }

    private fun sign(priv: java.security.PrivateKey, msg: String): String {
        val s = Signature.getInstance("Ed25519"); s.initSign(priv); s.update(msg.toByteArray())
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign())
    }

    @Test fun matchesJdkOnRandomMessages() {
        val (priv, pub) = jdkKeys()
        repeat(20) { i ->
            val m = "xabar-$i-" + "x".repeat(i * 7)
            val s = Signature.getInstance("Ed25519"); s.initSign(priv); s.update(m.toByteArray())
            assertTrue(Ed25519.verify(pub, m.toByteArray(), s.sign()))
        }
    }

    @Test fun challengeFlow() {
        val (priv, pub) = jdkKeys()
        var n = 0
        val v = OwnerVerifier(pub) { size -> ByteArray(size) { (it + n++).toByte() } }
        val c = v.newChallenge(1_000, "1.0.0", "ab12")
        assertEquals(c, Challenge.parse(c.text))
        val sig = sign(priv, c.text)
        val r = v.verify(c, sig, 2_000)
        assertTrue(r is AuthResult.Verified)
        assertEquals(OwnerVerifier.fingerprintOf(pub), (r as AuthResult.Verified).fingerprint)
        assertEquals(AuthResult.UnknownChallenge, v.verify(c, sig, 3_000)) // bir martalik
    }

    @Test fun challengeRejections() {
        val (priv, pub) = jdkKeys()
        val (otherPriv, _) = jdkKeys()
        val v = OwnerVerifier(pub) { ByteArray(it) { 7 } }
        val c = v.newChallenge(0, "1.0.0", "ab12")
        assertEquals(AuthResult.BadSignature, v.verify(c, sign(otherPriv, c.text), 1_000))
        assertEquals(AuthResult.Malformed, v.verify(c, "salom", 1_000))
        assertEquals(AuthResult.Expired, v.verify(c, sign(priv, c.text), 11 * 60_000L))
        val tampered = c.copy(appVersion = "9.9")
        assertEquals(AuthResult.UnknownChallenge, v.verify(tampered, sign(priv, tampered.text), 1_000))
        assertEquals(AuthResult.NoOwnerKey, OwnerVerifier(null) { ByteArray(it) }.verify(c, "x", 0))
    }

    @Test fun parsesKeyFormats() {
        val (_, pub) = jdkKeys()
        val hexKey = pub.joinToString("") { "%02x".format(it) }
        assertTrue(OwnerVerifier.parsePublicKey(pub)!!.contentEquals(pub))
        assertTrue(OwnerVerifier.parsePublicKey(hexKey.toByteArray())!!.contentEquals(pub))
        assertTrue(OwnerVerifier.parsePublicKey(java.util.Base64.getEncoder().encode(pub))!!.contentEquals(pub))
        assertEquals(null, OwnerVerifier.parsePublicKey("PLACEHOLDER".toByteArray()))
    }

    @Test fun testOnlyKeyFromResources() {
        // tools/ tomonidan yaratilgan test kaliti va imzosi (Python cryptography) — o'zaro moslik tekshiruvi
        val pub = javaClass.getResourceAsStream("/TEST_ONLY_owner.pub")!!.readBytes()
        val vec = javaClass.getResourceAsStream("/TEST_ONLY_vector.txt")!!.bufferedReader().readLines()
        val key = OwnerVerifier.parsePublicKey(pub)!!
        assertTrue(Ed25519.verify(key, vec[0].toByteArray(), OwnerVerifier.decodeSignature(vec[1])!!))
    }
}

class ReplayTest {
    @Test fun schoolZoneMorning() {
        val school = Geo.destination(TASHKENT, 90.0, 1_400.0)
        val tb = TrackBuilder(TASHKENT, 90.0).clock(1, 8 * 60).poi("s1", "SCHOOL", school).drive(140, 52.0)
        val r = TrackReplay.run(tb.build())
        val zone = r.events.firstOrNull { (it.second as? EngineEvent.Raise)?.alert?.type == AlertType.SCHOOL_ZONE }
        assertNotNull(zone)
        val alert = (zone!!.second as EngineEvent.Raise).alert
        assertEquals(AlertLevel.URGENT, alert.level)
        // ogohlantirish hududga (150 m) kamida 6 s qolganda
        val metersToZoneEdge = 1_400.0 - 150.0 - 52 / 3.6 * (zone.first / 1000.0)
        assertTrue("edge in $metersToZoneEdge m", metersToZoneEdge / (52 / 3.6) >= 6.0)
        assertTrue(r.alerts.any { it.type == AlertType.SPEEDING })
        assertEquals(1, r.alerts.count { it.type == AlertType.SCHOOL_ZONE })
    }

    @Test fun schoolZoneOutsideHoursIsVoiceNotUrgent() {
        val school = Geo.destination(TASHKENT, 90.0, 1_400.0)
        val r = TrackReplay.run(TrackBuilder(TASHKENT, 90.0).clock(1, 10 * 60).poi("s1", "SCHOOL", school).drive(140, 45.0).build())
        assertEquals(AlertLevel.VOICE, r.alerts.first { it.type == AlertType.SCHOOL_ZONE }.level)
        assertFalse(r.alerts.any { it.type == AlertType.SPEEDING })
    }

    @Test fun highwayCameraAtLeast8sAhead() {
        val cam = Geo.destination(TASHKENT, 45.0, 3_000.0)
        val tb = TrackBuilder(TASHKENT, 45.0).poi("c1", "SPEED_CAMERA", cam, 70).drive(120, 100.0)
        val r = TrackReplay.run(tb.build())
        val e = r.events.first { (it.second as? EngineEvent.Raise)?.alert?.type == AlertType.SPEED_CAMERA }
        val traveled = 100 / 3.6 * (e.first / 1000.0)
        val secondsLeft = (3_000 - traveled) / (100 / 3.6)
        assertTrue("seconds left $secondsLeft", secondsLeft >= 7.99)
        assertEquals("70", (e.second as EngineEvent.Raise).alert.args["limit"])
        assertTrue(r.alerts.any { it.type == AlertType.SPEEDING && it.level == AlertLevel.VOICE })
    }

    @Test fun cameraBehindIsIgnored() {
        val cam = Geo.destination(TASHKENT, 225.0, 200.0)
        val r = TrackReplay.run(TrackBuilder(TASHKENT, 45.0).poi("c1", "SPEED_CAMERA", cam, 70).drive(100, 80.0).build())
        assertFalse(r.alerts.any { it.type == AlertType.SPEED_CAMERA })
    }

    @Test fun crashTriggersSos() {
        val tb = TrackBuilder(TASHKENT, 0.0).drive(70, 50.0).imu(6.0).still(12).still(32).tick()
        val r = TrackReplay.run(tb.build())
        val crash = r.events.mapNotNull { (it.second as? EngineEvent.Crash)?.event }
        assertTrue(crash.any { it is CrashSosMachine.Event.ProtectClip })
        assertTrue(crash.any { it is CrashSosMachine.Event.CountdownStarted })
        assertTrue(crash.any { it is CrashSosMachine.Event.SosTriggered })
        assertNotNull(r.events.first { it.second is EngineEvent.Crash }.second.let { (it as EngineEvent.Crash).at })
    }

    @Test fun potholeOnRoad() {
        val tb = TrackBuilder(TASHKENT, 0.0).drive(70, 40.0, imuHz = 50) { t ->
            when (t) { 65_000L -> -5.0; 65_040L -> 6.0; else -> 0.0 }
        }
        val r = TrackReplay.run(tb.build())
        val potholes = r.events.mapNotNull { it.second as? EngineEvent.Road }.filter { it.event.type == RoadEventType.POTHOLE }
        assertEquals(1, potholes.size)
        assertNotNull(potholes[0].at)
    }

    @Test fun replayFilesInTestdata() {
        val dir = java.io.File(System.getProperty("testdata.dir") ?: "../../testdata/tracks")
        val files = dir.listFiles { f -> f.name.endsWith(".csv") }
        assertTrue("testdata topilmadi: ${dir.absolutePath}", files != null && files.isNotEmpty())
        for (f in files!!.sortedBy { it.name }) {
            val lines = f.readLines()
            val result = TrackReplay.run(f.readText())
            val gotAlerts = result.alerts.map { it.type.name }.toSet()
            val gotEvents = result.events.mapNotNull { (_, e) ->
                when (e) {
                    is EngineEvent.Crash -> e.event::class.simpleName
                    is EngineEvent.Road -> e.event.type.name
                    else -> null
                }
            }.toSet()
            fun check(prefix: String, got: Set<String?>) {
                lines.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).trim() }.forEach { e ->
                    if (e.startsWith("!")) assertFalse("${f.name}: ${e.drop(1)} bo'lmasligi kerak, bor: $got", e.drop(1) in got)
                    else assertTrue("${f.name}: $e kutilgan, bor: $got", e in got)
                }
            }
            check("# EXPECT ", gotAlerts)
            check("# EXPECT_EVENT ", gotEvents)
        }
    }
}
