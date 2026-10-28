package uz.hamroh.core.vision

import uz.hamroh.core.Thresholds

/**
 * FR-4.11: Y (yorug'lik) tekisligi ustida tungi rejim amallari. Bitmap yaratilmaydi, massiv joyida o'zgartiriladi.
 * [rowStride] — kamera buferidagi qator qadami (kenglikdan katta bo'lishi mumkin).
 */
object LumaOps {

    /** O'rtacha yorug'lik (har 4-piksel bo'yicha tezkor baho), 0..255. */
    fun meanLuma(y: ByteArray, width: Int, height: Int, rowStride: Int = width): Int {
        var sum = 0L
        var n = 0
        var r = 0
        while (r < height) {
            var c = 0
            val base = r * rowStride
            while (c < width) { sum += y[base + c].toInt() and 0xFF; n++; c += 4 }
            r += 4
        }
        return if (n == 0) 0 else (sum / n).toInt()
    }

    fun isNight(meanLuma: Int) = meanLuma < Thresholds.NIGHT_MEAN_LUMA

    /** Qarshi faralar: [Thresholds.GLARE_LUMA] dan yorqin piksellarni atrofdagi o'rtacha darajaga tushiradi. */
    fun maskGlare(y: ByteArray, width: Int, height: Int, rowStride: Int = width, replace: Int = 128): Int {
        var masked = 0
        for (r in 0 until height) {
            val base = r * rowStride
            for (c in 0 until width) {
                if ((y[base + c].toInt() and 0xFF) >= Thresholds.GLARE_LUMA) { y[base + c] = replace.toByte(); masked++ }
            }
        }
        return masked
    }

    /**
     * CLAHE (Contrast Limited Adaptive Histogram Equalization), [tiles]×[tiles] katakli, bilinear interpolyatsiya bilan.
     * [clipLimit] — gistogramma ustunining o'rtachaga nisbatan maksimal balandligi.
     */
    fun clahe(y: ByteArray, width: Int, height: Int, rowStride: Int = width, tiles: Int = 8, clipLimit: Double = 2.5) {
        val tw = (width + tiles - 1) / tiles
        val th = (height + tiles - 1) / tiles
        val luts = Array(tiles * tiles) { IntArray(256) }
        val hist = IntArray(256)
        for (ty in 0 until tiles) for (tx in 0 until tiles) {
            java.util.Arrays.fill(hist, 0)
            val x0 = tx * tw; val x1 = minOf(width, x0 + tw)
            val y0 = ty * th; val y1 = minOf(height, y0 + th)
            val area = (x1 - x0) * (y1 - y0)
            if (area <= 0) { for (i in 0..255) luts[ty * tiles + tx][i] = i; continue }
            for (r in y0 until y1) { val b = r * rowStride; for (c in x0 until x1) hist[y[b + c].toInt() and 0xFF]++ }
            val limit = maxOf(1, (clipLimit * area / 256).toInt())
            var excess = 0
            for (i in 0..255) if (hist[i] > limit) { excess += hist[i] - limit; hist[i] = limit }
            val add = excess / 256
            var rem = excess % 256
            for (i in 0..255) { hist[i] += add; if (rem > 0) { hist[i]++; rem-- } }
            var cdf = 0
            val lut = luts[ty * tiles + tx]
            for (i in 0..255) { cdf += hist[i]; lut[i] = (cdf * 255L / area).toInt().coerceIn(0, 255) }
        }
        for (r in 0 until height) {
            val gy = (r.toDouble() / th - 0.5).coerceIn(0.0, (tiles - 1).toDouble())
            val ty0 = gy.toInt(); val ty1 = minOf(tiles - 1, ty0 + 1); val fy = gy - ty0
            val b = r * rowStride
            for (c in 0 until width) {
                val gx = (c.toDouble() / tw - 0.5).coerceIn(0.0, (tiles - 1).toDouble())
                val tx0 = gx.toInt(); val tx1 = minOf(tiles - 1, tx0 + 1); val fx = gx - tx0
                val v = y[b + c].toInt() and 0xFF
                val top = luts[ty0 * tiles + tx0][v] * (1 - fx) + luts[ty0 * tiles + tx1][v] * fx
                val bot = luts[ty1 * tiles + tx0][v] * (1 - fx) + luts[ty1 * tiles + tx1][v] * fx
                y[b + c] = (top * (1 - fy) + bot * fy).toInt().coerceIn(0, 255).toByte()
            }
        }
    }
}
