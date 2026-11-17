package ua.personal.archive.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class PdfDoc(title: String, private val file: File, workDir: File) : Document(title, workDir) {
    override val pdfFile: File get() = file

    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(fd)
    } catch (e: SecurityException) {
        fd.close()
        throw DocException("PDF захищений паролем – такі файли поки не підтримуються.")
    } catch (e: Exception) {
        fd.close()
        throw DocException("Не вдалося відкрити PDF: файл пошкоджений або має непідтримуваний формат.")
    }

    override val paged = true

    override val items: List<Item> = (0 until renderer.pageCount).map { i ->
        renderer.openPage(i).use { p -> PageItem(i, p.width.coerceAtLeast(1), p.height.coerceAtLeast(1)) }
    }

    override fun renderPage(page: PageItem, targetWidth: Int): Bitmap? = synchronized(renderer) {
        var w = targetWidth.coerceAtLeast(1)
        var h = (w.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
        while (w.toLong() * h > Bitmaps.MAX_PIXELS || h > Bitmaps.MAX_SIDE * 2) {
            w = w * 3 / 4; h = h * 3 / 4
        }
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            w /= 2; h /= 2
            Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        bmp.eraseColor(Color.WHITE)
        renderer.openPage(page.index).use { p ->
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        bmp
    }

    override fun close() {
        synchronized(renderer) {
            try { renderer.close() } catch (_: Exception) {}
            try { fd.close() } catch (_: Exception) {}
        }
        super.close()
    }
}
