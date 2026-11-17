package ua.personal.archive.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ua.personal.archive.crypto.VaultCrypto
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Зашифроване сховище документів.
 *
 * Структура на диску (внутрішня пам'ять додатку, недоступна іншим додаткам):
 *   files/vault/index.bin       — зашифрований JSON зі списком документів
 *   files/vault/blobs/<id>.bin  — зашифровані вкладення
 *
 * Відкритий текст ніколи не пишеться на диск, окрім тимчасових копій для
 * надсилання/друку в cache/shared, які видаляються при блокуванні додатку.
 */
class VaultRepository(private val context: Context, cryptoFactory: () -> VaultCrypto) {

    // Ключі з Android Keystore завантажуються ліниво й лише у фоновому потоці.
    private val crypto by lazy(cryptoFactory)

    private val root = File(context.filesDir, "vault").apply { mkdirs() }
    private val blobs = File(root, "blobs").apply { mkdirs() }
    private val indexFile = File(root, "index.bin")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_loaded.value) return@withLock
            try {
                _documents.value = readIndex().documents
                _loaded.value = true
                _loadError.value = null
            } catch (e: Exception) {
                _loadError.value = "Не вдалося відкрити сховище: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun find(id: String): Document? = _documents.value.firstOrNull { it.id == id }

    /** Зберігає документ (новий або змінений). */
    suspend fun save(doc: Document) = mutate { list ->
        val updated = doc.copy(updatedAt = System.currentTimeMillis())
        if (list.any { it.id == doc.id }) list.map { if (it.id == doc.id) updated else it } else list + updated
    }

    suspend fun delete(docId: String) {
        val doc = find(docId) ?: return
        mutate { list -> list.filterNot { it.id == docId } }
        withContext(Dispatchers.IO) { doc.attachments.forEach { blobFile(it.id).delete() } }
    }

    /** Шифрує вміст за [uri] у сховище й повертає опис вкладення (ще не прив'язаний до документа). */
    suspend fun importUri(uri: Uri, fallbackName: String? = null): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryName(resolver, uri) ?: fallbackName ?: "файл"
        val mime = resolver.getType(uri) ?: guessMime(name)
        val input = resolver.openInputStream(uri) ?: error("Не вдалося відкрити файл")
        input.use { importStream(it, name, mime) }
    }

    suspend fun importStream(input: InputStream, name: String, mime: String): Attachment =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val target = blobFile(id)
            var size = 0L
            try {
                crypto.encryptingStream(target, id).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        size += n
                    }
                }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            Attachment(id = id, name = name, mime = mime, size = size, addedAt = System.currentTimeMillis())
        }

    /** Видаляє зашифровані файли вкладень, які так і не потрапили в жоден документ. */
    suspend fun discardAttachments(attachments: List<Attachment>) = withContext(Dispatchers.IO) {
        val used = _documents.value.flatMap { d -> d.attachments.map { it.id } }.toSet()
        attachments.filterNot { it.id in used }.forEach { blobFile(it.id).delete() }
    }

    fun openAttachment(attachment: Attachment): InputStream =
        crypto.decryptingStream(blobFile(attachment.id), attachment.id)

    suspend fun readAttachment(attachment: Attachment): ByteArray = withContext(Dispatchers.IO) {
        openAttachment(attachment).use { it.readBytes() }
    }

    private fun blobFile(id: String) = File(blobs, "$id.bin")

    private suspend fun mutate(change: (List<Document>) -> List<Document>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = change(_documents.value)
            writeIndex(VaultIndex(documents = next))
            _documents.value = next
        }
    }

    private fun readIndex(): VaultIndex {
        if (!indexFile.exists()) return VaultIndex()
        val plain = crypto.open(indexFile.readBytes(), INDEX_LABEL)
        return json.decodeFromString(VaultIndex.serializer(), plain.decodeToString())
    }

    /** Атомарний запис: спершу у тимчасовий файл, потім перейменування. */
    private fun writeIndex(index: VaultIndex) {
        val plain = json.encodeToString(VaultIndex.serializer(), index).encodeToByteArray()
        val tmp = File(root, "index.tmp")
        tmp.writeBytes(crypto.seal(plain, INDEX_LABEL))
        if (!tmp.renameTo(indexFile)) {
            indexFile.delete()
            check(tmp.renameTo(indexFile)) { "Не вдалося зберегти індекс" }
        }
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    companion object {
        private const val INDEX_LABEL = "vault-index-v1"

        fun guessMime(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
