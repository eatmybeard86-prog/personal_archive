package ua.personal.archive.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.personal.archive.data.Attachment
import ua.personal.archive.data.VaultRepository
import ua.personal.archive.util.PdfConverter
import java.io.File
import java.util.UUID

/**
 * Розпізнавання тексту (OCR) у фото й PDF-сканах, щоб документи можна було знайти за словами всередині.
 * Працює повністю на телефоні (Tesseract, українська + англійська), у фоні, по одному файлу.
 * Розпізнаний текст зберігається лише в зашифрованому індексі сховища.
 */
class OcrIndexer(private val context: Context, private val repo: VaultRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Запускає розпізнавання всіх ще не оброблених файлів (якщо вже йде — нічого не робить). */
    @Synchronized
    fun indexMissing() {
        if (job?.isActive == true) return
        job = scope.launch {
            _busy.value = true
            try {
                while (isActive) {
                    val next = repo.documents.value.asSequence()
                        .flatMap { d -> d.attachments.asSequence().map { d.id to it } }
                        .firstOrNull { (_, a) -> a.text == null && isScan(a) } ?: break
                    val (docId, a) = next
                    val text = runCatching { recognize(a) }.getOrElse { "" }
                    // Порожній рядок теж зберігаємо — щоб не розпізнавати той самий файл знову.
                    if (repo.find(docId)?.attachments?.any { it.id == a.id } == true) repo.setAttachmentText(docId, a.id, text)
                    else break
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private fun isScan(a: Attachment) = PdfConverter.kindOf(a).let { it == PdfConverter.Kind.IMAGE || it == PdfConverter.Kind.PDF }

    private fun dataPath(): String {
        val root = File(context.filesDir, "ocr")
        val dir = File(root, "tessdata").apply { mkdirs() }
        for (lang in LANGS) {
            val f = File(dir, "$lang.traineddata")
            if (!f.exists() || f.length() == 0L) {
                val tmp = File(dir, "$lang.tmp")
                context.assets.open("tessdata/$lang.traineddata").use { i -> tmp.outputStream().use { i.copyTo(it) } }
                tmp.renameTo(f)
            }
        }
        return root.absolutePath
    }

    private fun recognize(a: Attachment): String {
        val work = File(context.cacheDir, "work/ocr-" + UUID.randomUUID()).apply { mkdirs() }
        val tess = TessBaseAPI()
        try {
            check(tess.init(dataPath(), LANGS.joinToString("+"))) { "OCR недоступний" }
            val file = File(work, "src")
            repo.openAttachment(a).use { i -> file.outputStream().use { i.copyTo(it) } }
            val pages = ArrayList<String>()
            if (PdfConverter.kindOf(a) == PdfConverter.Kind.PDF) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { r ->
                        for (i in 0 until minOf(r.pageCount, MAX_PAGES)) {
                            val bmp = r.openPage(i).use { p ->
                                val w = TARGET_WIDTH
                                val h = (w.toLong() * p.height / p.width.coerceAtLeast(1)).toInt().coerceIn(1, w * 3)
                                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                }
                            }
                            pages += ocr(tess, bmp)
                            bmp.recycle()
                        }
                    }
                }
            } else {
                val bmp = PdfConverter.decodeBitmap(file.readBytes(), maxSide = 2400) ?: return ""
                pages += ocr(tess, bmp)
                bmp.recycle()
            }
            return pages.joinToString("\n").replace(Regex("[ \\t]+"), " ").trim()
        } finally {
            tess.recycle()
            work.deleteRecursively()
        }
    }

    private fun ocr(tess: TessBaseAPI, bmp: Bitmap): String {
        tess.setImage(bmp)
        return tess.utF8Text.orEmpty().also { tess.clear() }
    }

    companion object {
        private val LANGS = listOf("ukr", "eng")
        private const val TARGET_WIDTH = 2000
        private const val MAX_PAGES = 30
    }
}
