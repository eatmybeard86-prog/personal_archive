package ua.personal.archive.reader

import android.graphics.Bitmap
import java.io.File
import java.util.zip.ZipFile

/** Текстовий документ (DOCX/DOC/RTF), розбитий на абзаци. */
class TextDoc(
    title: String,
    override val items: List<Item>,
    private val zip: ZipFile?,
    workDir: File,
) : Document(title, workDir) {
    override val paged = false

    override fun loadInlineImage(key: String, maxWidth: Int): Bitmap? = synchronized(this) {
        val z = zip ?: return null
        val entry = z.getEntry(key) ?: return null
        return try {
            Bitmaps.decodeSampled({ z.getInputStream(entry) }, maxWidth)
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        synchronized(this) { try { zip?.close() } catch (_: Exception) {} }
        super.close()
    }
}
