package uz.hamroh.core.auth

import uz.hamroh.core.Thresholds
import java.security.MessageDigest
import java.util.Base64

/**
 * FR-AU.2: so'rov-javob orqali egani tasdiqlash.
 *
 * So'rov matni (egasi aynan shu baytlarni imzolaydi, UTF-8):
 *   HAMROH-AUTH-v1|<nonce hex 32>|<issuedAtMs>|<appVersion>|<apkCertSha256 hex>
 * Javob — 64 baytli Ed25519 imzosi, base64 (oddiy yoki URL-safe, "=" bilan yoki siz).
 */
data class Challenge(val nonceHex: String, val issuedAtMs: Long, val appVersion: String, val certSha256: String) {
    val text: String get() = "$PREFIX|$nonceHex|$issuedAtMs|$appVersion|$certSha256"

    companion object {
        const val PREFIX = "HAMROH-AUTH-v1"

        fun parse(text: String): Challenge? {
            val p = text.trim().split('|')
            if (p.size != 5 || p[0] != PREFIX) return null
            if (!p[1].matches(Regex("[0-9a-f]{32}"))) return null
            val t = p[2].toLongOrNull() ?: return null
            return Challenge(p[1], t, p[3], p[4])
        }
    }
}

sealed interface AuthResult {
    /** [fingerprint] — ochiq kalitning SHA-256 izi (birinchi 32 hex belgi). */
    data class Verified(val fingerprint: String, val challenge: Challenge) : AuthResult
    data object BadSignature : AuthResult
    data object Expired : AuthResult
    data object Reused : AuthResult
    data object UnknownChallenge : AuthResult
    data object Malformed : AuthResult
    data object NoOwnerKey : AuthResult
}

/**
 * Ilova tomonidagi tekshiruvchi. [ownerPublicKey] — `owner.pub` (32 bayt) yoki null (kalit joylanmagan).
 * [random] — 16 bayt tasodifiy son manbai (Android'da SecureRandom).
 */
class OwnerVerifier(
    private val ownerPublicKey: ByteArray?,
    private val random: (Int) -> ByteArray,
) {
    private val issued = HashMap<String, Challenge>()
    private val used = HashSet<String>()

    fun fingerprint(): String? = ownerPublicKey?.let { fingerprintOf(it) }

    fun newChallenge(nowMs: Long, appVersion: String, certSha256: String): Challenge {
        val nonce = random(16).joinToString("") { "%02x".format(it) }
        val c = Challenge(nonce, nowMs, appVersion, certSha256)
        issued[nonce] = c
        return c
    }

    fun verify(challenge: Challenge, signatureText: String, nowMs: Long): AuthResult {
        val key = ownerPublicKey ?: return AuthResult.NoOwnerKey
        val known = issued[challenge.nonceHex] ?: return AuthResult.UnknownChallenge
        if (known != challenge) return AuthResult.UnknownChallenge
        if (challenge.nonceHex in used) return AuthResult.Reused
        if (nowMs - challenge.issuedAtMs > Thresholds.CHALLENGE_TTL_MS || nowMs < challenge.issuedAtMs) return AuthResult.Expired
        val sig = decodeSignature(signatureText) ?: return AuthResult.Malformed
        if (!Ed25519.verify(key, challenge.text.toByteArray(Charsets.UTF_8), sig)) return AuthResult.BadSignature
        used += challenge.nonceHex
        issued.remove(challenge.nonceHex)
        return AuthResult.Verified(fingerprintOf(key), challenge)
    }

    companion object {
        fun fingerprintOf(key: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(key).joinToString("") { "%02x".format(it) }.take(32)

        fun decodeSignature(text: String): ByteArray? {
            val t = text.trim().replace("\\s".toRegex(), "")
            if (t.matches(Regex("[0-9a-fA-F]{128}"))) return ByteArray(64) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val norm = t.replace('-', '+').replace('_', '/').trimEnd('=')
            val padded = norm + "=".repeat((4 - norm.length % 4) % 4)
            return try {
                Base64.getDecoder().decode(padded).takeIf { it.size == 64 }
            } catch (e: IllegalArgumentException) { null }
        }

        /** `owner.pub` fayli: 32 xom bayt yoki 64 hex belgi yoki base64. */
        fun parsePublicKey(bytes: ByteArray): ByteArray? {
            if (bytes.size == 32) return bytes
            val t = String(bytes, Charsets.US_ASCII).trim()
            if (t.matches(Regex("[0-9a-fA-F]{64}"))) return ByteArray(32) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            return try { Base64.getDecoder().decode(t).takeIf { it.size == 32 } } catch (e: IllegalArgumentException) { null }
        }
    }
}
