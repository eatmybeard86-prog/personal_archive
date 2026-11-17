package ua.personal.archive.reader

import android.graphics.Bitmap
import java.io.File

/** Одне або кілька зображень, показаних як сторінки. */
class ImageDoc(title: String, files: List<File>, workDir: File) : Document(title, workDir) {
    private class Img(val file: File, val rotation: Int)

    private val images = ArrayList<Img>()
    override val items: List<Item>
    override val paged = true
    override val isPhoto = true

    init {
        val list = ArrayList<Item>()
        for (f in files) {
            val rot = Bitmaps.exifRotation(f)
            val (w, h) = Bitmaps.bounds { f.inputStream() }
            if (w <= 0 || h <= 0) continue
            val (rw, rh) = if (rot % 180 != 0) h to w else w to h
            list += PageItem(images.size, rw, rh)
            images += Img(f, rot)
        }
        if (list.isEmpty()) throw DocException("Не вдалося прочитати зображення.")
        items = list
    }

    override fun renderPage(page: PageItem, targetWidth: Int): Bitmap? {
        val img = images.getOrNull(page.index) ?: return null
        return Bitmaps.decodeSampled({ img.file.inputStream() }, targetWidth, img.rotation)
    }
}
