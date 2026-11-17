package ua.personal.archive.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import ua.personal.archive.data.Attachment
import ua.personal.archive.reader.DocParser
import ua.personal.archive.reader.DocxParser
import ua.personal.archive.reader.InlineImageItem
import ua.personal.archive.reader.PageItem
import ua.personal.archive.reader.RtfParser
import ua.personal.archive.reader.TextItem
import ua.personal.archive.reader.Item as ReaderItem
import java.util.zip.ZipFile
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Перетворює вкладення будь-якого формату на один PDF (сторінки A4).
 *
 *  - PDF                       — сторінки переносяться як є (якщо PDF один) або растеризуються;
 *  - зображення (JPG, PNG, WEBP, HEIC, …) — по одному на сторінку, з урахуванням повороту EXIF;
 *  - текст (TXT, CSV, JSON, XML, MD, HTML) — розбивається на сторінки;
 *  - DOCX, DOC, RTF             — через парсери читалки (жирний, курсив, заголовки, таблиці, картинки);
 *  - ODT, XLSX                 — витягується текст;
 *  - інші формати               — сторінка з назвою й розміром файлу (вміст показати неможливо).
 */
class PdfConverter(private val workDir: File) {

    fun interface Source {
        fun bytes(): ByteArray
    }

    data class Item(val attachment: Attachment, val source: Source)

    fun convert(items: List<Item>, output: File, title: String) {
        // Один PDF без змін — віддаємо оригінал без втрати якості.
        if (items.size == 1 && kindOf(items[0].attachment) == Kind.PDF) {
            output.writeBytes(items[0].source.bytes())
            return
        }
        val pdf = PdfDocument()
        try {
            val writer = PageWriter(pdf)
            if (items.isEmpty()) writer.flow(listOf(TextItem("$title\n\n(документ не містить файлів)")), null)
            for (item in items) {
                val a = item.attachment
                when (val kind = kindOf(a)) {
                    Kind.PDF -> writer.pdfPages(item.source.bytes())
                    Kind.IMAGE -> writer.imagePage(item.source.bytes(), a.name)
                    // Word / RTF — через парсери читалки, зі збереженням жирного, курсиву, заголовків і картинок.
                    Kind.DOCX -> withTemp(item.source.bytes(), ".docx") { f ->
                        ZipFile(f).use { zip -> writer.flow(DocxParser.parse(zip), zip) }
                    }
                    Kind.DOC -> writer.flow(DocParser.parse(item.source.bytes()), null)
                    Kind.RTF -> writer.flow(RtfParser.parse(item.source.bytes()), null)
                    Kind.OTHER -> writer.flow(
                        listOf(
                            TextItem(a.name, heading = 1),
                            TextItem("Формат «${a.mime}» не можна відобразити у PDF. Розмір: ${formatSize(a.size)}."),
                            TextItem("Надішліть оригінал, якщо потрібен сам файл."),
                        ),
                        null,
                    )
                    else -> writer.flow(
                        extractText(kind, item.source.bytes()).split('\n').map { TextItem(it, tableRow = kind == Kind.XLSX && it.contains('\t')) },
                        null,
                    )
                }
            }
            output.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private fun <T> withTemp(bytes: ByteArray, suffix: String, block: (File) -> T): T {
        val f = File.createTempFile("conv", suffix, workDir)
        try {
            f.writeBytes(bytes)
            return block(f)
        } finally {
            f.delete()
        }
    }

    enum class Kind { PDF, IMAGE, TEXT, HTML, DOCX, DOC, ODT, XLSX, PPTX, RTF, OTHER }

    private inner class PageWriter(private val pdf: PdfDocument) {
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f

        private fun newPage(): PdfDocument.Page =
            pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNo).create())

        private fun endFlow() {
            page?.let { pdf.finishPage(it) }
            page = null
        }

        fun imagePage(bytes: ByteArray, name: String) {
            endFlow()
            val bmp = decodeBitmap(bytes)
            if (bmp == null) {
                flow(listOf(TextItem(name, heading = 1), TextItem("Не вдалося прочитати зображення.")), null)
                endFlow()
                return
            }
            val p = newPage()
            drawFitted(p.canvas, bmp)
            pdf.finishPage(p)
            bmp.recycle()
        }

        fun pdfPages(bytes: ByteArray) {
            endFlow()
            withTemp(bytes, ".pdf") { tmp ->
                ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { src ->
                                // ~150 dpi: читабельно і не роздуває файл.
                                val scale = 150f / 72f
                                val bmp = Bitmap.createBitmap(
                                    (src.width * scale).toInt().coerceAtLeast(1),
                                    (src.height * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888,
                                )
                                bmp.eraseColor(Color.WHITE)
                                src.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val p = newPage()
                                drawFitted(p.canvas, bmp, margin = 0f)
                                pdf.finishPage(p)
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
        }

        /** Текст і вбудовані картинки потоком: абзац за абзацом, з переносом на нові сторінки. */
        fun flow(items: List<ReaderItem>, zip: ZipFile?) {
            val width = (PAGE_W - 2 * MARGIN).toInt()
            val bottom = PAGE_H - MARGIN
            fun ensurePage() {
                if (page == null) { page = newPage(); y = MARGIN }
            }
            fun nextPage() { endFlow(); ensurePage() }
            ensurePage()
            for (item in items) {
                when (item) {
                    is TextItem -> {
                        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            textSize = when (item.heading) { 1 -> 18f; 2 -> 15f; 3 -> 13f; else -> if (item.tableRow) 9.5f else 11f }
                            typeface = if (item.tableRow) Typeface.MONOSPACE else Typeface.SERIF
                            isFakeBoldText = item.heading > 0
                        }
                        val text: CharSequence = item.text.ifEmpty { " " }
                        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                            .setAlignment(
                                when (item.align) {
                                    TextItem.ALIGN_CENTER -> Layout.Alignment.ALIGN_CENTER
                                    TextItem.ALIGN_END -> Layout.Alignment.ALIGN_OPPOSITE
                                    else -> Layout.Alignment.ALIGN_NORMAL
                                }
                            )
                            .setLineSpacing(1.5f, 1f)
                            .build()
                        val gap = if (item.heading > 0) 8f else 3f
                        if (y + gap > bottom) nextPage()
                        y += gap
                        var line = 0
                        while (line < layout.lineCount) {
                            if (y + (layout.getLineBottom(line) - layout.getLineTop(line)) > bottom) nextPage()
                            // скільки рядків уміщається на цій сторінці
                            val top = layout.getLineTop(line)
                            var last = line
                            while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - top) <= bottom) last++
                            val c = page!!.canvas
                            c.save()
                            c.translate(MARGIN, y - top)
                            c.clipRect(0f, top.toFloat(), width.toFloat(), layout.getLineBottom(last).toFloat())
                            layout.draw(c)
                            c.restore()
                            y += layout.getLineBottom(last) - top
                            line = last + 1
                        }
                    }
                    is InlineImageItem -> {
                        val z = zip ?: continue
                        val entry = z.getEntry(item.key) ?: continue
                        val bmp = runCatching { z.getInputStream(entry).use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: continue
                        val maxH = bottom - MARGIN
                        val s = min(width.toFloat() / bmp.width, maxH / bmp.height)
                        val h = bmp.height * s
                        if (y + h > bottom) nextPage()
                        val m = Matrix().apply { postScale(s, s); postTranslate(MARGIN + (width - bmp.width * s) / 2, y) }
                        page!!.canvas.drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
                        y += h + 6f
                        bmp.recycle()
                    }
                    is PageItem -> Unit
                }
            }
            endFlow()
        }
    }

    private fun drawFitted(canvas: Canvas, bmp: Bitmap, margin: Float = MARGIN) {
        val w = PAGE_W - 2 * margin
        val h = PAGE_H - 2 * margin
        val s = min(w / bmp.width, h / bmp.height)
        val dx = margin + (w - bmp.width * s) / 2
        val dy = margin + (h - bmp.height * s) / 2
        val m = Matrix().apply { postScale(s, s); postTranslate(dx, dy) }
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    companion object {
        private const val PAGE_W = 595 // A4 у пунктах
        private const val PAGE_H = 842
        private const val MARGIN = 36f

        fun kindOf(a: Attachment): Kind = kindOf(a.name, a.mime)

        fun kindOf(name: String, mimeType: String): Kind {
            val ext = name.substringAfterLast('.', "").lowercase()
            val mime = mimeType.lowercase()
            return when {
                mime == "application/pdf" || ext == "pdf" -> Kind.PDF
                mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif") -> Kind.IMAGE
                mime == "text/html" || ext in setOf("html", "htm") -> Kind.HTML
                ext == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Kind.DOCX
                ext == "doc" || mime == "application/msword" -> Kind.DOC
                ext in setOf("odt", "ods", "odp") || mime.startsWith("application/vnd.oasis.opendocument.") -> Kind.ODT
                ext == "pptx" || mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> Kind.PPTX
                ext == "xlsx" || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> Kind.XLSX
                ext == "rtf" || mime == "application/rtf" || mime == "text/rtf" -> Kind.RTF
                mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" ||
                    ext in setOf("txt", "csv", "json", "xml", "md", "log") -> Kind.TEXT
                else -> Kind.OTHER
            }
        }

        /** Простий текст для форматів без окремого парсера читалки. */
        fun extractText(kind: Kind, bytes: ByteArray): String = when (kind) {
            Kind.HTML -> htmlToText(bytes.decodeToString())
            Kind.ODT -> odtText(bytes)
            Kind.XLSX -> xlsxText(bytes)
            Kind.PPTX -> pptxText(bytes)
            Kind.RTF -> rtfText(bytes.decodeToString())
            else -> decodeText(bytes)
        }

        /** Декодує зображення з обмеженням розміру (щоб не вичерпати пам'ять) і поворотом за EXIF. */
        fun decodeBitmap(bytes: ByteArray, maxSide: Int = 2400): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val rotation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees
            }.getOrDefault(0)
            if (rotation == 0) return bmp
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            if (rotated !== bmp) bmp.recycle()
            return rotated
        }

        fun formatSize(size: Long): String = when {
            size >= 1024 * 1024 -> "%.1f МБ".format(size / 1024.0 / 1024.0)
            size >= 1024 -> "%.0f КБ".format(size / 1024.0)
            else -> "$size Б"
        }

        private fun decodeText(bytes: ByteArray): String {
            val utf8 = bytes.decodeToString()
            // Якщо файл не UTF-8 (типово для старих файлів з Windows) — пробуємо windows-1251.
            return if (utf8.contains('�')) String(bytes, charset("windows-1251")) else utf8
        }

        @Suppress("DEPRECATION")
        private fun htmlToText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()

        private fun zipEntry(bytes: ByteArray, name: String): ByteArray? =
            zipEntries(bytes) { it == name }[name]

        private fun zipEntries(bytes: ByteArray, accept: (String) -> Boolean): Map<String, ByteArray> {
            val out = LinkedHashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (accept(e.name)) out[e.name] = zip.readBytes()
                }
            }
            return out
        }

        private fun parser(xml: ByteArray): XmlPullParser =
            XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser().apply {
                setInput(ByteArrayInputStream(xml), "UTF-8")
            }

        fun odtText(bytes: ByteArray): String {
            val xml = zipEntry(bytes, "content.xml") ?: return "(не вдалося прочитати ODT)"
            val sb = StringBuilder()
            val p = parser(xml)
            var depth = 0
            while (true) {
                when (p.next()) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.START_TAG -> when (p.name) {
                        "office:body" -> depth++
                        "text:tab" -> sb.append('\t')
                        "text:line-break" -> sb.append('\n')
                        "text:s" -> sb.append(' ')
                    }
                    XmlPullParser.END_TAG -> when (p.name) {
                        "office:body" -> depth--
                        "text:p", "text:h" -> sb.append('\n')
                        "table:table-cell" -> sb.append('\t')
                    }
                    XmlPullParser.TEXT -> if (depth > 0) sb.append(p.text)
                }
            }
            return sb.toString()
        }

        /** Таблиці Excel: кожен аркуш — блок рядків, клітинки розділені табуляцією. */
        fun xlsxText(bytes: ByteArray): String {
            val entries = zipEntries(bytes) { it == "xl/sharedStrings.xml" || it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            val shared = ArrayList<String>()
            entries["xl/sharedStrings.xml"]?.let { xml ->
                val p = parser(xml)
                val cur = StringBuilder()
                var inT = false
                while (true) {
                    when (p.next()) {
                        XmlPullParser.END_DOCUMENT -> break
                        XmlPullParser.START_TAG -> if (p.name == "t") inT = true
                        XmlPullParser.END_TAG -> when (p.name) {
                            "t" -> inT = false
                            "si" -> { shared += cur.toString(); cur.setLength(0) }
                        }
                        XmlPullParser.TEXT -> if (inT) cur.append(p.text)
                    }
                }
            }
            val sb = StringBuilder()
            entries.keys.filter { it.startsWith("xl/worksheets/") }
                .sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
                .forEachIndexed { i, key ->
                    if (i > 0) sb.append("\n\n")
                    sb.append("Аркуш ${i + 1}\n")
                    val p = parser(entries.getValue(key))
                    var type: String? = null
                    var inValue = false
                    var firstCell = true
                    while (true) {
                        when (p.next()) {
                            XmlPullParser.END_DOCUMENT -> break
                            XmlPullParser.START_TAG -> when (p.name) {
                                "row" -> firstCell = true
                                "c" -> {
                                    type = p.getAttributeValue(null, "t")
                                    if (!firstCell) sb.append('\t')
                                    firstCell = false
                                }
                                "v", "t" -> inValue = true
                            }
                            XmlPullParser.END_TAG -> when (p.name) {
                                "row" -> sb.append('\n')
                                "v", "t" -> inValue = false
                            }
                            XmlPullParser.TEXT -> if (inValue) {
                                val v = p.text
                                sb.append(if (type == "s") shared.getOrNull(v.trim().toIntOrNull() ?: -1) ?: v else v)
                            }
                        }
                    }
                }
            return sb.toString()
        }

        /** Текст слайдів PowerPoint: кожен слайд — заголовок «Слайд N» і його абзаци. */
        fun pptxText(bytes: ByteArray): String {
            val slides = zipEntries(bytes) { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            val sb = StringBuilder()
            slides.keys.sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }.forEachIndexed { i, key ->
                if (i > 0) sb.append("\n")
                sb.append("— Слайд ${i + 1} —\n")
                val p = parser(slides.getValue(key))
                var inText = false
                while (true) {
                    when (p.next()) {
                        XmlPullParser.END_DOCUMENT -> break
                        XmlPullParser.START_TAG -> when (p.name) { "a:t" -> inText = true; "a:br" -> sb.append('\n') }
                        XmlPullParser.END_TAG -> when (p.name) { "a:t" -> inText = false; "a:p" -> sb.append('\n') }
                        XmlPullParser.TEXT -> if (inText) sb.append(p.text)
                    }
                }
            }
            return sb.toString().ifBlank { "(у презентації немає тексту)" }
        }

        /** Спрощене видалення керуючих слів RTF (кирилиця у форматі \'xx за cp1251). */
        fun rtfText(rtf: String): String {
            val sb = StringBuilder()
            val hex = java.io.ByteArrayOutputStream()
            fun flushHex() {
                if (hex.size() > 0) { sb.append(String(hex.toByteArray(), charset("windows-1251"))); hex.reset() }
            }
            var i = 0
            var depth = 0
            var skipDepth = -1
            while (i < rtf.length) {
                val ch = rtf[i]
                when {
                    ch == '{' -> { depth++; i++ }
                    ch == '}' -> { if (depth == skipDepth) skipDepth = -1; depth--; i++ }
                    ch == '\\' && i + 1 < rtf.length && rtf[i + 1] == '\'' && i + 3 < rtf.length -> {
                        rtf.substring(i + 2, i + 4).toIntOrNull(16)?.let { if (skipDepth < 0) hex.write(it) }
                        i += 4
                    }
                    ch == '\\' -> {
                        flushHex()
                        var j = i + 1
                        while (j < rtf.length && rtf[j].isLetter()) j++
                        val word = rtf.substring(i + 1, j)
                        while (j < rtf.length && (rtf[j].isDigit() || rtf[j] == '-')) j++
                        if (j < rtf.length && rtf[j] == ' ') j++
                        if (word.isEmpty() && i + 1 < rtf.length) { if (skipDepth < 0 && rtf[i + 1] != '*') sb.append(rtf[i + 1]); j = i + 2 }
                        if (word in setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer")) skipDepth = depth
                        if (skipDepth < 0) when (word) {
                            "par", "line" -> sb.append('\n')
                            "tab", "cell" -> sb.append('\t')
                        }
                        i = j
                    }
                    ch == '\r' || ch == '\n' -> i++
                    else -> { flushHex(); if (skipDepth < 0) sb.append(ch); i++ }
                }
            }
            flushHex()
            return sb.toString()
        }
    }
}
