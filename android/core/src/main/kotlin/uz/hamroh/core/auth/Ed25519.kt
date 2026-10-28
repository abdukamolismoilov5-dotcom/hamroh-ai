package uz.hamroh.core.auth

import java.math.BigInteger
import java.security.MessageDigest

/**
 * RFC 8032 bo'yicha Ed25519 imzosini tekshirish (faqat tekshirish; imzolash ilovada yo'q va bo'lmasligi kerak).
 * Tashqi kutubxonasiz, Android 8 (API 26) da ham ishlaydi. Tezlik muhim emas: bir martalik tekshiruv uchun.
 */
object Ed25519 {
    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val L: BigInteger = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))
    private val D: BigInteger = BigInteger.valueOf(-121665).multiply(inv(BigInteger.valueOf(121666))).mod(P)
    private val I: BigInteger = BigInteger.valueOf(2).modPow(P.subtract(BigInteger.ONE).shiftRight(2), P)

    /** Kengaytirilgan koordinatalar (X:Y:Z:T), x = X/Z, y = Y/Z, xy = T/Z. */
    private class Pt(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    private val B: Pt = run {
        val by = BigInteger.valueOf(4).multiply(inv(BigInteger.valueOf(5))).mod(P)
        val bx = recoverX(by, 0) ?: error("base point")
        Pt(bx, by, BigInteger.ONE, bx.multiply(by).mod(P))
    }
    private val ZERO_PT = Pt(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    private fun inv(a: BigInteger) = a.modPow(P.subtract(TWO), P)

    private fun recoverX(y: BigInteger, sign: Int): BigInteger? {
        if (y >= P) return null
        val y2 = y.multiply(y)
        val xx = y2.subtract(BigInteger.ONE).multiply(inv(D.multiply(y2).add(BigInteger.ONE))).mod(P)
        if (xx.signum() == 0) return if (sign == 0) BigInteger.ZERO else null
        var x = xx.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P)
        if (x.multiply(x).subtract(xx).mod(P).signum() != 0) x = x.multiply(I).mod(P)
        if (x.multiply(x).subtract(xx).mod(P).signum() != 0) return null
        if (x.testBit(0) != (sign == 1)) x = P.subtract(x)
        return x
    }

    private fun add(p: Pt, q: Pt): Pt {
        val a = p.y.subtract(p.x).multiply(q.y.subtract(q.x)).mod(P)
        val b = p.y.add(p.x).multiply(q.y.add(q.x)).mod(P)
        val c = TWO.multiply(p.t).multiply(q.t).multiply(D).mod(P)
        val d = TWO.multiply(p.z).multiply(q.z).mod(P)
        val e = b.subtract(a); val f = d.subtract(c); val g = d.add(c); val h = b.add(a)
        return Pt(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P))
    }

    private fun mul(s: BigInteger, p: Pt): Pt {
        var q = ZERO_PT
        var r = p
        var k = s
        while (k.signum() > 0) {
            if (k.testBit(0)) q = add(q, r)
            r = add(r, r)
            k = k.shiftRight(1)
        }
        return q
    }

    private fun equal(p: Pt, q: Pt): Boolean =
        p.x.multiply(q.z).subtract(q.x.multiply(p.z)).mod(P).signum() == 0 &&
            p.y.multiply(q.z).subtract(q.y.multiply(p.z)).mod(P).signum() == 0

    private fun leInt(b: ByteArray, from: Int = 0, len: Int = b.size): BigInteger {
        val be = ByteArray(len) { b[from + len - 1 - it] }
        return BigInteger(1, be)
    }

    private fun decodePoint(b: ByteArray): Pt? {
        if (b.size != 32) return null
        val copy = b.copyOf()
        val sign = (copy[31].toInt() ushr 7) and 1
        copy[31] = (copy[31].toInt() and 0x7F).toByte()
        val y = leInt(copy)
        val x = recoverX(y, sign) ?: return null
        return Pt(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    /** [publicKey] — 32 bayt, [signature] — 64 bayt. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val a = decodePoint(publicKey) ?: return false
        val rBytes = signature.copyOfRange(0, 32)
        val r = decodePoint(rBytes) ?: return false
        val s = leInt(signature, 32, 32)
        if (s >= L) return false
        val md = MessageDigest.getInstance("SHA-512")
        md.update(rBytes); md.update(publicKey); md.update(message)
        val h = leInt(md.digest()).mod(L)
        val sB = mul(s, B)
        val hA = mul(h, a)
        return equal(sB, add(r, hA))
    }
}
