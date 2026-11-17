package ua.personal.archive.reader

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File

/** Елемент, що відображається у списку. */
sealed interface Item

/** Растрова сторінка (сторінка PDF або картинка). [width]/[height] задають пропорції. */
data class PageItem(val index: Int, val width: Int, val height: Int) : Item

/** Абзац тексту (DOC/DOCX/RTF). */
data class TextItem(
    val text: CharSequence,
    val align: Int = ALIGN_START,
    val heading: Int = 0,
    val tableRow: Boolean = false,
) : Item {
    companion object {
        const val ALIGN_START = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_END = 2
        const val ALIGN_JUSTIFY = 3
    }
}

/** Вбудоване зображення в текстовому документі. */
data class InlineImageItem(val key: String) : Item

/** Відкритий документ. */
abstract class Document(val title: String, private val workDir: File?) : Closeable {
    abstract val items: List<Item>

    /** true – документ складається з растрових сторінок (PDF, фото). */
    abstract val paged: Boolean

    /** Чи є зображення фотографіями (тему для них можна вимкнути окремо). */
    open val isPhoto: Boolean = false

    /** Вихідний PDF-файл (для друку без перерендеру), якщо документ є PDF. */
    open val pdfFile: File? = null

    /** Оригінальні файли з іменами та MIME-типом – для «Поділитися». */
    var sources: List<Source> = emptyList()

    class Source(val file: File, val name: String, val mime: String)

    open fun renderPage(page: PageItem, targetWidth: Int): Bitmap? = null

    open fun loadInlineImage(key: String, maxWidth: Int): Bitmap? = null

    override fun close() {
        workDir?.deleteRecursively()
    }
}

class DocException(message: String) : Exception(message)
