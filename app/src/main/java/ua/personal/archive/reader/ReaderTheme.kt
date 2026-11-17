package ua.personal.archive.reader

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * Кольорова тема сторінки.
 *
 * [page] – колір сторінки (фон), [ink] – колір тексту,
 * [outer] – колір проміжків між сторінками, [bar] / [barText] – верхня панель.
 *
 * Для PDF та зображень тема застосовується через колірну матрицю:
 * світлі пікселі перефарбовуються в [page], темні – в [ink].
 */
enum class ReaderTheme(
    val title: String,
    val page: Int,
    val ink: Int,
    val outer: Int,
    val bar: Int,
    val barText: Int,
    val recolor: Boolean,
) {
    DAY("Звичайна: чорний текст на білому", 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFD5D5D5.toInt(), 0xFFF2F2F2.toInt(), 0xFF202020.toInt(), false),
    NIGHT("Нічна: білий текст на чорному", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF2A2A2A.toInt(), 0xFF101010.toInt(), 0xFFFFFFFF.toInt(), true),
    SEPIA("Сепія: коричневий на бежевому", 0xFFF4ECD8.toInt(), 0xFF4A3828.toInt(), 0xFFCFC2A3.toInt(), 0xFFE6DAC0.toInt(), 0xFF3B2C1F.toInt(), true),
    DUSK("Сутінки: світло-сірий на темно-сірому", 0xFF2E2E2E.toInt(), 0xFFD8D8D8.toInt(), 0xFF181818.toInt(), 0xFF202020.toInt(), 0xFFE0E0E0.toInt(), true),
    CONTRAST("Контрастна: жовтий на чорному", 0xFF000000.toInt(), 0xFFFFE135.toInt(), 0xFF333333.toInt(), 0xFF101010.toInt(), 0xFFFFE135.toInt(), true),
    GREEN("М'яка зелена: темний текст на зеленуватому", 0xFFCCE8CF.toInt(), 0xFF1B2B1C.toInt(), 0xFFA3C4A6.toInt(), 0xFFB6D6B9.toInt(), 0xFF1B2B1C.toInt(), true),
    BLUE("Синя: білий текст на темно-синьому", 0xFF0D1B2A.toInt(), 0xFFE6EEF6.toInt(), 0xFF050C14.toInt(), 0xFF08121D.toInt(), 0xFFE6EEF6.toInt(), true);

    val isDark: Boolean
        get() = luminance(page) < 0.5f

    /** Фільтр для растрових сторінок (PDF, фото). null – показувати оригінальні кольори. */
    fun bitmapFilter(): ColorMatrixColorFilter? = matrix()?.let { ColorMatrixColorFilter(ColorMatrix(it)) }

    /** Значення колірної матриці 4×5 (для Compose ColorFilter.colorMatrix). */
    fun matrix(): FloatArray? {
        if (!recolor) return null
        fun row(pageC: Int, inkC: Int): FloatArray {
            // out = ink + (page - ink) * яскравість
            val k = (pageC - inkC) / 255f
            return floatArrayOf(0.299f * k, 0.587f * k, 0.114f * k, 0f, inkC.toFloat())
        }
        val r = row(Color.red(page), Color.red(ink))
        val g = row(Color.green(page), Color.green(ink))
        val b = row(Color.blue(page), Color.blue(ink))
        val a = floatArrayOf(0f, 0f, 0f, 1f, 0f)
        return r + g + b + a
    }

    companion object {
        fun luminance(c: Int): Float =
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

        fun byName(name: String?): ReaderTheme = entries.firstOrNull { it.name == name } ?: DAY
    }
}
