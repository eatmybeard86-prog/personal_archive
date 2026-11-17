package ua.personal.archive.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream as AesZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream as AesZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import ua.personal.archive.util.Exporter
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Зашифровані резервні копії.
 *
 * Формат — звичайний ZIP із шифруванням AES-256 (WinZip AES), який відкривають
 * 7-Zip, WinRAR, Bandizip на Windows і будь-яка копія цього додатку — лише з паролем.
 *
 * Усередині зашифрованого ZIP лежить один файл «Архів.zip», а вже в ньому — папки
 * за розділами й документами. Так навіть назви файлів не видно без пароля
 * (у звичайному ZIP назви файлів не шифруються).
 *
 *   backup.zip  (AES-256, пароль)
 *   └─ Архів.zip
 *      ├─ ПРОЧИТАЙ.txt
 *      ├─ documents.json            — опис документів для імпорту в додаток
 *      ├─ Особисті/Паспорт/скан.jpg
 *      └─ Нерухомість/Квартира/витяг.pdf
 */
class BackupManager(private val context: Context, private val repo: VaultRepository) {

    @Serializable
    data class Manifest(
        val format: String = FORMAT,
        val version: Int = 1,
        val createdAt: Long,
        val documents: List<Document>,
        /** Розділи, до яких належать документи копії (щоб відновити й власні розділи). */
        val sections: List<Section> = emptyList(),
        /** id вкладення -> шлях у «Архів.zip» */
        val paths: Map<String, String>,
    )

    data class ImportResult(val imported: Int, val skipped: Int, val files: Int)

    class WrongPasswordException : Exception("Неправильний пароль")
    class NotABackupException : Exception("Це не резервна копія «Моїх документів»")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun suggestedName(categories: Set<String>?): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val part = when {
            categories == null -> "усі документи"
            categories.size <= 2 -> categories.joinToString(", ") { Exporter.safeName(repo.section(it).title) }
            else -> "${categories.size} розділи"
        }
        return "Мої документи - $part - $date.zip"
    }

    /** Створює копію документів вибраних розділів (null — усі) у [target]. */
    suspend fun export(
        target: Uri,
        password: CharArray,
        categories: Set<String>?,
        onProgress: (Float) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val docs = repo.documents.value.filter { categories == null || it.sectionId in categories }
        val total = docs.sumOf { d -> d.attachments.sumOf { it.size } }.coerceAtLeast(1)
        var done = 0L

        val paths = LinkedHashMap<String, String>()
        val usedDirs = HashSet<String>()
        docs.forEach { d ->
            var dir = "${Exporter.safeName(repo.section(d.sectionId).title)}/${Exporter.safeName(d.title)}"
            if (!usedDirs.add(dir)) dir = "$dir (${d.id.take(6)})".also { usedDirs.add(it) }
            val usedNames = HashSet<String>()
            d.attachments.forEach { a ->
                var name = Exporter.safeName(a.name)
                if (!usedNames.add(name)) name = "${a.id.take(6)}_$name".also { usedNames.add(it) }
                paths[a.id] = "$dir/$name"
            }
        }
        val manifest = Manifest(
            createdAt = System.currentTimeMillis(),
            documents = docs.map { it.copy(section = it.sectionId) },
            sections = docs.map { it.sectionId }.distinct().map { repo.section(it) },
            paths = paths,
        )

        val out = context.contentResolver.openOutputStream(target, "w") ?: error("Не вдалося створити файл")
        out.use { raw ->
            val outer = AesZipOutputStream(raw, password)
            val params = ZipParameters().apply {
                fileNameInZip = INNER_NAME
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.FASTER
            }
            outer.putNextEntry(params)
            // Внутрішній ZIP пишеться прямо в зашифрований потік — без тимчасових файлів.
            val inner = ZipOutputStream(NonClosing(outer))
            inner.putNextEntry(ZipEntry("ПРОЧИТАЙ.txt"))
            inner.write(readme(manifest).toByteArray())
            inner.closeEntry()
            inner.putNextEntry(ZipEntry(MANIFEST))
            inner.write(json.encodeToString(Manifest.serializer(), manifest).toByteArray())
            inner.closeEntry()
            docs.forEach { d ->
                d.attachments.forEach { a ->
                    inner.putNextEntry(ZipEntry(paths.getValue(a.id)).apply { time = a.addedAt })
                    repo.openAttachment(a).use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            inner.write(buf, 0, n)
                            done += n
                            onProgress(done.toFloat() / total)
                        }
                    }
                    inner.closeEntry()
                }
            }
            inner.finish()
            outer.closeEntry()
            outer.close()
        }
        password.fill('\u0000')
        docs.size
    }

    /**
     * Перевіряє пароль і читає опис копії (без імпорту), щоб показати, що в ній є.
     */
    suspend fun inspect(source: Uri, password: CharArray): Manifest = withContext(Dispatchers.IO) {
        var manifest: Manifest? = null
        readInner(source, password) { name, stream ->
            if (name == MANIFEST) {
                manifest = json.decodeFromString(Manifest.serializer(), stream.readBytes().decodeToString())
                false
            } else true
        }
        manifest ?: throw NotABackupException()
    }

    /** Імпортує документи з копії. Документи, які вже є в сховищі (той самий id), пропускаються. */
    suspend fun restore(
        source: Uri,
        password: CharArray,
        categories: Set<String>?,
        onProgress: (Float) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        val manifest = inspect(source, password.copyOf())
        val existing = repo.documents.value.map { it.id }.toSet()
        val wanted = manifest.documents.filter { (categories == null || it.sectionId in categories) && it.id !in existing }
        val skipped = manifest.documents.count { (categories == null || it.sectionId in categories) && it.id in existing }
        val pathToAttachment = HashMap<String, Pair<Document, Attachment>>()
        wanted.forEach { d -> d.attachments.forEach { a -> manifest.paths[a.id]?.let { pathToAttachment[it] = d to a } } }
        val total = pathToAttachment.values.sumOf { it.second.size }.coerceAtLeast(1)
        var done = 0L

        // Кожне вкладення отримує новий id і шифрується ключем цього пристрою.
        val newAttachments = HashMap<String, Attachment>()
        try {
            readInner(source, password) { name, stream ->
                val (_, a) = pathToAttachment[name] ?: return@readInner true
                val counting = object : FilterInputStream(stream) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int =
                        super.read(b, off, len).also { if (it > 0) { done += it; onProgress(done.toFloat() / total) } }
                }
                val imported = repo.importStream(counting, a.name, a.mime)
                newAttachments[a.id] = imported.copy(addedAt = a.addedAt)
                true
            }
            // Розділи, яких ще немає на цьому телефоні, додаються з копії.
            val known = repo.sections.value.map { it.id }.toSet()
            wanted.map { it.sectionId }.distinct().filter { it !in known }.forEach { id ->
                repo.saveSection(manifest.sections.firstOrNull { it.id == id } ?: repo.section(id))
            }
            var files = 0
            wanted.forEach { d ->
                val atts = d.attachments.mapNotNull { newAttachments[it.id] }
                files += atts.size
                repo.save(d.copy(attachments = atts))
            }
            ImportResult(imported = wanted.size, skipped = skipped, files = files)
        } catch (e: Exception) {
            repo.discardAttachments(newAttachments.values.toList())
            throw e
        } finally {
            password.fill('\u0000')
        }
    }

    /** Проходить файли внутрішнього архіву; [visit] повертає false, щоб зупинитися. */
    private suspend fun readInner(source: Uri, password: CharArray, visit: suspend (String, InputStream) -> Boolean) {
        val input = context.contentResolver.openInputStream(source) ?: error("Не вдалося відкрити файл")
        input.use { raw ->
            try {
                val outer = AesZipInputStream(raw, password)
                while (true) {
                    val header = outer.nextEntry ?: throw NotABackupException()
                    if (header.fileName != INNER_NAME) continue
                    if (!header.isEncrypted) throw NotABackupException()
                    val inner = ZipInputStream(outer)
                    while (true) {
                        val e = inner.nextEntry ?: break
                        if (e.isDirectory) continue
                        if (!visit(e.name, NonClosingInput(inner))) break
                    }
                    break
                }
            } catch (e: ZipException) {
                if (e.type == ZipException.Type.WRONG_PASSWORD || e.message?.contains("password", true) == true) throw WrongPasswordException()
                throw e
            } finally {
                password.fill('\u0000')
            }
        }
    }

    private fun readme(m: Manifest): String = buildString {
        appendLine("Резервна копія «Мої документи»")
        appendLine("Створено: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date(m.createdAt))}")
        appendLine("Документів: ${m.documents.size}, файлів: ${m.paths.size}")
        appendLine()
        appendLine("Файли розкладено по папках: Розділ / Документ / файли.")
        appendLine("Щоб повернути копію в додаток, відкрийте «Налаштування → Резервні копії → Відновити»")
        appendLine("і виберіть зовнішній зашифрований файл (не цей Архів.zip).")
        appendLine()
        m.documents.groupBy { it.sectionId }.forEach { (c, list) ->
            appendLine("== ${m.sections.firstOrNull { it.id == c }?.title ?: repo.section(c).title} ==")
            list.forEach { d ->
                append("• ${d.title}")
                if (d.number.isNotBlank()) append(" — № ${d.number}")
                d.expiresAt?.let { append(" — дійсний до ${SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(Date(it))}") }
                appendLine()
                if (d.note.isNotBlank()) appendLine("  ${d.note.replace("\n", "\n  ")}")
            }
            appendLine()
        }
    }

    private class NonClosing(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }

    private class NonClosingInput(input: InputStream) : FilterInputStream(input) {
        override fun close() {}
    }

    companion object {
        const val FORMAT = "personal-archive-backup"
        const val INNER_NAME = "Архів.zip"
        const val MANIFEST = "documents.json"
        const val MIN_PASSWORD = 8

        @Suppress("unused")
        private fun newId() = UUID.randomUUID().toString()
    }
}
