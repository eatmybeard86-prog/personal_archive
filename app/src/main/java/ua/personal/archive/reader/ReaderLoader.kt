package ua.personal.archive.reader

import ua.personal.archive.util.PdfConverter
import java.io.File
import java.util.zip.ZipFile

/**
 * Відкриває розшифрований файл у вбудованій читалці (код читалки перенесено з проєкту doc_reader).
 *
 *  - PDF і фото — растрові сторінки;
 *  - DOCX — текст із форматуванням, заголовками, таблицями й картинками;
 *  - DOC (Word 97–2003) і RTF — текст і таблиці;
 *  - XLSX, ODT, TXT, CSV, HTML — текст, витягнутий конвертером;
 *  - інші формати — повідомлення, що вміст показати неможливо.
 *
 * [dir] — тимчасова тека з розшифрованим файлом; видаляється разом із документом у close().
 */
object ReaderLoader {

    enum class Kind { PDF, DOCX, DOC, RTF, IMAGE, OTHER }

    fun open(file: File, name: String, mime: String, dir: File): Document {
        try {
            return when (detect(file, name)) {
                Kind.PDF -> PdfDoc(name, file, dir)
                Kind.IMAGE -> ImageDoc(name, listOf(file), dir)
                Kind.DOCX -> when (val k = PdfConverter.kindOf(name, mime)) {
                    PdfConverter.Kind.XLSX, PdfConverter.Kind.ODT -> textDoc(name, PdfConverter.extractText(k, file.readBytes()), dir, table = k == PdfConverter.Kind.XLSX)
                    else -> {
                        val zip = ZipFile(file)
                        try { TextDoc(name, DocxParser.parse(zip), zip, dir) } catch (e: Throwable) { zip.close(); throw e }
                    }
                }
                Kind.DOC -> TextDoc(name, DocParser.parse(file.readBytes()), null, dir)
                Kind.RTF -> TextDoc(name, RtfParser.parse(file.readBytes()), null, dir)
                Kind.OTHER -> {
                    val k = PdfConverter.kindOf(name, mime)
                    if (k == PdfConverter.Kind.OTHER) throw DocException("Формат «$mime» не можна показати в додатку. Його можна надіслати або відкрити в іншому додатку.")
                    textDoc(name, PdfConverter.extractText(k, file.readBytes()), dir, table = false)
                }
            }
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun textDoc(name: String, text: String, dir: File, table: Boolean): TextDoc {
        val items = text.split('\n').map { TextItem(it, tableRow = table && it.contains('\t')) }
        return TextDoc(name, items.ifEmpty { listOf(TextItem("(порожньо)")) }, null, dir)
    }

    fun detect(file: File, name: String): Kind {
        val head = ByteArray(16)
        val n = file.inputStream().use { it.read(head) }
        fun starts(vararg b: Int) = n >= b.size && b.indices.all { head[it].toInt() and 0xFF == b[it] }
        return when {
            starts(0x25, 0x50, 0x44, 0x46) -> Kind.PDF
            starts(0x50, 0x4B, 0x03, 0x04) -> Kind.DOCX // будь-який ZIP (DOCX, XLSX, ODT)
            starts(0xD0, 0xCF, 0x11, 0xE0) -> Kind.DOC
            starts(0x7B, 0x5C, 0x72, 0x74, 0x66) -> Kind.RTF
            starts(0xFF, 0xD8, 0xFF) || starts(0x89, 0x50, 0x4E, 0x47) || starts(0x47, 0x49, 0x46) || starts(0x42, 0x4D) -> Kind.IMAGE
            n >= 12 && starts(0x52, 0x49, 0x46, 0x46) && head[8].toInt() == 0x57 -> Kind.IMAGE
            else -> when (name.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif" -> Kind.IMAGE
                else -> Kind.OTHER
            }
        }
    }
}
