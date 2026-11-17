package ua.personal.archive.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.personal.archive.data.Attachment
import ua.personal.archive.data.Document
import ua.personal.archive.data.VaultRepository
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Надсилання, відкриття в інших додатках, друк і конвертація в PDF.
 *
 * Для передачі іншому додатку файл доводиться тимчасово розшифрувати в cache/shared.
 * Ці копії видаляються під час кожного розблокування та запуску додатку
 * (до того часу додаток-одержувач уже встигає їх прочитати).
 */
class Exporter(private val context: Context, private val repo: VaultRepository) {

    private val sharedDir get() = File(context.cacheDir, "shared")
    val workDir: File get() = File(context.cacheDir, "work").apply { mkdirs() }
    val cameraDir: File get() = File(context.cacheDir, "camera").apply { mkdirs() }

    fun clearTemp() {
        listOf(sharedDir, File(context.cacheDir, "work"), File(context.cacheDir, "camera")).forEach { it.deleteRecursively() }
    }

    private fun freshDir(): File = File(sharedDir, UUID.randomUUID().toString()).apply { mkdirs() }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Розшифровує вкладення у тимчасову копію з оригінальною назвою. */
    suspend fun decryptToShared(attachment: Attachment): File = withContext(Dispatchers.IO) {
        val target = File(freshDir(), safeName(attachment.name))
        repo.openAttachment(attachment).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        target
    }

    /** Будує один PDF з усіх (або вибраних) вкладень документа. */
    suspend fun buildPdf(doc: Document, attachments: List<Attachment> = doc.attachments, watermark: String? = null): File = withContext(Dispatchers.IO) {
        val out = File(freshDir(), safeName(doc.title.ifBlank { "документ" }) + ".pdf")
        val items = attachments.map { a -> PdfConverter.Item(a) { repo.openAttachment(a).use { it.readBytes() } } }
        PdfConverter(workDir).convert(items, out, doc.title, watermark)
        out
    }

    /** Пакет документів (наприклад, «для візи») одним PDF з обкладинкою і змістом. */
    suspend fun buildBundle(title: String, docs: List<Document>, watermark: String?): File = withContext(Dispatchers.IO) {
        val out = File(freshDir(), safeName(title.ifBlank { "Пакет документів" }) + ".pdf")
        val date = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ROOT).format(java.util.Date())
        val cover = buildList {
            add(ua.personal.archive.reader.TextItem("\n\n\n"))
            add(ua.personal.archive.reader.TextItem(title.ifBlank { "Пакет документів" }, align = ua.personal.archive.reader.TextItem.ALIGN_CENTER, heading = 1))
            add(ua.personal.archive.reader.TextItem("Сформовано $date", align = ua.personal.archive.reader.TextItem.ALIGN_CENTER))
            add(ua.personal.archive.reader.TextItem("\n"))
            add(ua.personal.archive.reader.TextItem("Зміст", heading = 2))
            docs.forEachIndexed { i, d ->
                val extra = listOfNotNull(d.number.ifBlank { null }, "файлів: ${d.attachments.size}").joinToString(" · ")
                add(ua.personal.archive.reader.TextItem("${i + 1}. ${d.title}  ($extra)"))
            }
        }
        val items = docs.flatMap { d -> d.attachments.map { a -> PdfConverter.Item(a) { repo.openAttachment(a).use { it.readBytes() } } } }
        PdfConverter(workDir).convert(items, out, title, watermark, cover)
        out
    }

    fun shareFiles(files: List<File>, subject: String) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map(::uriFor))
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = VaultRepository.guessMime(files[0].name)
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.clipData = ClipData.newRawUri(subject, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(intent, "Надіслати").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    fun openExternally(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(file), VaultRepository.guessMime(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Відкрити за допомогою").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Друк через системний сервіс Android (будь-який формат друкується через PDF). */
    fun print(activityContext: Context, pdf: File, jobName: String) {
        val manager = activityContext.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(jobName, FilePrintAdapter(pdf, jobName), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }

    fun cameraUri(): Pair<File, Uri> {
        val file = File(cameraDir, "photo_${System.currentTimeMillis()}.jpg")
        return file to uriFor(file)
    }

    private class FilePrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback, extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            val info = PrintDocumentInfo.Builder("$name.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    companion object {
        fun safeName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().take(120).ifBlank { "файл" }
    }
}
