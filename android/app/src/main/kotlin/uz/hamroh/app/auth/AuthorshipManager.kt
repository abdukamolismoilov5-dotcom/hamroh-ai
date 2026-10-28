package uz.hamroh.app.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import uz.hamroh.app.BuildConfig
import uz.hamroh.core.auth.AuthResult
import uz.hamroh.core.auth.Challenge
import uz.hamroh.core.auth.OwnerVerifier
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * FR-AU.1–5: egasini tasdiqlash. Ochiq kalit assets/owner.pub dan, APK sertifikati PackageManager dan olinadi.
 * Ilovada yopiq kalit YO'Q va bo'lmaydi.
 */
class AuthorshipManager(private val context: Context) {
    private val rng = SecureRandom()
    private val ownerKey: ByteArray? = runCatching {
        context.assets.open("owner.pub").use { OwnerVerifier.parsePublicKey(it.readBytes()) }
    }.getOrNull()
    private val verifier = OwnerVerifier(ownerKey) { n -> ByteArray(n).also { rng.nextBytes(it) } }

    val ownerKeyInstalled get() = ownerKey != null
    val ownerFingerprint: String? get() = verifier.fingerprint()

    /** APK imzo sertifikatining SHA-256 izi (hex, kichik harf). */
    val certSha256: String by lazy { readCertSha256() }

    /** FR-AU.4: sertifikat kutilgan qiymatga mosmi. Kutilgan qiymat sozlanmagan (debug) bo'lsa — null. */
    val certMatches: Boolean? get() = BuildConfig.EXPECTED_CERT_SHA256.takeIf { it.length == 64 }?.let { it == certSha256 }

    /** FR-AU.5: qurilish belgisi mavjudmi (qiymatning o'zi hech qayerda ko'rsatilmaydi). */
    val buildTagPresent get() = BuildConfig.OWNER_BUILD_TAG.isNotEmpty()

    fun newChallenge(): Challenge = verifier.newChallenge(System.currentTimeMillis(), BuildConfig.VERSION_NAME, certSha256)

    fun verify(challenge: Challenge, signature: String): AuthResult = verifier.verify(challenge, signature, System.currentTimeMillis())

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun readCertSha256(): String = runCatching {
        val pm = context.packageManager
        val bytes: ByteArray? = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
        }
        bytes?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } } ?: "unknown"
    }.getOrDefault("unknown")
}
