package ua.personal.archive.reader

import android.graphics.Bitmap
import java.io.File

/**
 * Кілька файлів одного документа (наприклад, усі сторінки паспорта: фото й PDF),
 * показані як одна стрічка сторінок.
 */
class CompositeDoc(title: String, private val parts: List<Document>, workDir: File) : Document(title, workDir) {
    private class Ref(val doc: Document, val page: PageItem)

    private val refs = ArrayList<Ref>()
    override val items: List<Item>
    override val paged = true
    override val isPhoto = parts.all { it.isPhoto }

    init {
        val list = ArrayList<Item>()
        for (d in parts) for (p in d.items.filterIsInstance<PageItem>()) {
            list += PageItem(refs.size, p.width, p.height)
            refs += Ref(d, p)
        }
        if (list.isEmpty()) throw DocException("У документі немає сторінок для показу.")
        items = list
    }

    override fun renderPage(page: PageItem, targetWidth: Int): Bitmap? {
        val r = refs.getOrNull(page.index) ?: return null
        return r.doc.renderPage(r.page, targetWidth)
    }

    override fun close() {
        parts.forEach { runCatching { it.close() } }
        super.close()
    }
}
