package ua.personal.archive.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ua.personal.archive.crypto.VaultCrypto
import java.io.File
import java.util.UUID

/**
 * Журнал доступу: успішні входи й неправильні паролі / PIN-коди, за бажанням — з фото
 * фронтальної камери того, хто помилився. Журнал і фото зашифровані тим самим ключем сховища.
 */
class AccessLog(private val context: Context, cryptoFactory: () -> VaultCrypto) {
    enum class Kind(val title: String) { UNLOCK("Вхід"), WRONG("Неправильний пароль / PIN"), DURESS("Вхід тривожним PIN") }

    @Serializable
    data class Entry(val id: String, val at: Long, val kind: Kind, val method: String = "", val photo: Boolean = false, val rotation: Int = 0)

    private val crypto by lazy(cryptoFactory)
    private val dir = File(context.filesDir, "accesslog").apply { mkdirs() }
    private val file = File(dir, "log.bin")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    private var loaded = false

    private fun readAll(): List<Entry> {
        if (!loaded) {
            _entries.value = runCatching {
                if (!file.exists()) emptyList()
                else json.decodeFromString(ListSerializer(Entry.serializer()), crypto.open(file.readBytes(), LABEL).decodeToString())
            }.getOrDefault(emptyList())
            loaded = true
        }
        return _entries.value
    }

    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { readAll() } }

    fun add(kind: Kind, method: String = "", photoJpeg: ByteArray? = null, rotation: Int = 0) {
        scope.launch {
            mutex.withLock {
                val id = UUID.randomUUID().toString()
                if (photoJpeg != null) runCatching {
                    crypto.encryptingStream(photoFile(id), id).use { it.write(photoJpeg) }
                }
                val list = (listOf(Entry(id, System.currentTimeMillis(), kind, method, photoJpeg != null, rotation)) + readAll())
                val keep = list.take(MAX)
                list.drop(MAX).forEach { photoFile(it.id).delete() }
                write(keep)
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            readAll().forEach { photoFile(it.id).delete() }
            write(emptyList())
        }
    }

    suspend fun photo(e: Entry): Bitmap? = withContext(Dispatchers.IO) {
        if (!e.photo) return@withContext null
        runCatching {
            val bytes = crypto.decryptingStream(photoFile(e.id), e.id).use { it.readBytes() }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            if (e.rotation == 0) bmp
            else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(e.rotation.toFloat()) }, true)
        }.getOrNull()
    }

    private fun write(list: List<Entry>) {
        val tmp = File(dir, "log.tmp")
        tmp.writeBytes(crypto.seal(json.encodeToString(ListSerializer(Entry.serializer()), list).encodeToByteArray(), LABEL))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        _entries.value = list
    }

    private fun photoFile(id: String) = File(dir, "$id.bin")

    companion object {
        private const val LABEL = "access-log-v1"
        private const val MAX = 200
    }
}
