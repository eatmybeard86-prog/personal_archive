package ua.personal.archive.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.InputStream

internal object Bitmaps {
    /** Максимальний розмір сторони, щоб не перевищити ліміти пам'яті та Canvas. */
    const val MAX_SIDE = 4096
    const val MAX_PIXELS = 16_000_000

    fun bounds(open: () -> InputStream): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, o) }
        return o.outWidth to o.outHeight
    }

    fun decodeSampled(open: () -> InputStream, targetWidth: Int, rotation: Int = 0): Bitmap? {
        val (w0, h0) = bounds(open)
        if (w0 <= 0 || h0 <= 0) return null
        // ширина після повороту
        val w = if (rotation % 180 != 0) h0 else w0
        val h = if (rotation % 180 != 0) w0 else h0
        var sample = 1
        while (w / (sample * 2) >= targetWidth) sample *= 2
        while (maxOf(w, h) / sample > MAX_SIDE || (w.toLong() / sample) * (h / sample) > MAX_PIXELS) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = try {
            open().use { BitmapFactory.decodeStream(it, null, o) }
        } catch (e: OutOfMemoryError) {
            o.inSampleSize = sample * 2
            open().use { BitmapFactory.decodeStream(it, null, o) }
        } ?: return null
        if (rotation == 0) return bmp
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    fun exifRotation(file: File): Int = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}
