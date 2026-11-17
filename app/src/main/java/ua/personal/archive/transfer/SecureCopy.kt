package ua.personal.archive.transfer

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ua.personal.archive.crypto.VaultCrypto
import ua.personal.archive.data.Document
import ua.personal.archive.data.VaultRepository
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Захищена копія документа з обмеженням: відкривається лише N разів і/або до певної дати.
 *
 * Файл «.mydoc» відкривається тільки в додатку «Мої документи». Вміст зашифровано AES-256-GCM;
 * якщо задано пароль, ключ виводиться з нього (PBKDF2), інакше ключ лежить у самому файлі.
 * Лічильник відкриттів і строк перевіряє додаток отримувача, а після вичерпання копія стирається.
 */
class SecureCopy(private val context: Context, cryptoFactory: () -> VaultCrypto) {

    @Serializable
    class PackHeader(
        val id: String, val title: String, val maxOpens: Int?, val expiresAt: Long?,
        val salt: String? = null, val key: String? = null,
    )

    @Serializable
    data class Received(
        val id: String, val title: String, val maxOpens: Int?, val expiresAt: Long?,
        val opens: Int = 0, val key: String, val receivedAt: Long,
    ) {
        val remainingOpens: Int? get() = maxOpens?.let { (it - opens).coerceAtLeast(0) }
        fun expired(now: Long = System.currentTimeMillis()) = (expiresAt != null && now > expiresAt) || remainingOpens == 0
    }

    private val crypto by lazy(cryptoFactory)
    private val dir = File(context.filesDir, "copies").apply { mkdirs() }
    private val metaFile = File(dir, "meta.bin")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _items = MutableStateFlow<List<Received>>(emptyList())
    val items: StateFlow<List<Received>> = _items.asStateFlow()
    /** Скільки разів уже відкривали копії з певним id — не скидається повторним імпортом того самого файлу. */
    private val seen = context.getSharedPreferences("copies_seen", Context.MODE_PRIVATE)

    // ── Відправник ───────────────────────────────────────────

    suspend fun pack(repo: VaultRepository, doc: Document, sectionTitle: String, maxOpens: Int?, days: Int?, password: String?): File =
        withContext(Dispatchers.IO) {
            val rnd = SecureRandom()
            val salt = password?.let { ByteArray(16).also(rnd::nextBytes) }
            val key = if (password != null) derive(password, salt!!) else ByteArray(32).also(rnd::nextBytes)
            val header = PackHeader(
                id = UUID.randomUUID().toString(), title = doc.title, maxOpens = maxOpens,
                expiresAt = days?.let { System.currentTimeMillis() + it * 24L * 3600_000L },
                salt = salt?.let(::b64), key = if (password == null) b64(key) else null,
            )
            val out = File(File(context.cacheDir, "shared/" + UUID.randomUUID()).apply { mkdirs() }, ua.personal.archive.util.Exporter.safeName(doc.title) + ".mydoc")
            out.outputStream().buffered().use { raw ->
                val d = DataOutputStream(raw)
                d.write(MAGIC)
                val hb = json.encodeToString(PackHeader.serializer(), header).encodeToByteArray()
                d.writeInt(hb.size); d.write(hb); d.flush()
                QrTransfer.writeDoc(raw, key, repo, doc, sectionTitle)
                raw.flush()
            }
            out
        }

    // ── Отримувач ────────────────────────────────────────────

    fun isPack(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { i -> val b = ByteArray(MAGIC.size); DataInputStream(i).readFully(b); b.contentEquals(MAGIC) } ?: false
    }.getOrDefault(false)

    private fun readHeader(i: InputStream): PackHeader {
        val d = DataInputStream(i)
        val m = ByteArray(MAGIC.size).also { d.readFully(it) }
        require(m.contentEquals(MAGIC)) { "Це не захищена копія «Мої документи»" }
        val len = d.readInt(); require(len in 1..100_000)
        return json.decodeFromString(PackHeader.serializer(), ByteArray(len).also { d.readFully(it) }.decodeToString())
    }

    suspend fun needsPassword(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)!!.use { readHeader(BufferedInputStream(it)).key == null }
    }

    /** Імпортує файл: перевіряє пароль, зберігає копію (зашифрованою ключем сховища) у список отриманих. */
    suspend fun import(uri: Uri, password: String?): Received = withContext(Dispatchers.IO) {
        val target = File(dir, "in-" + UUID.randomUUID())
        context.contentResolver.openInputStream(uri)!!.use { i -> target.outputStream().use { i.copyTo(it) } }
        try {
            val header = target.inputStream().buffered().use { readHeader(it) }
            val key = header.key?.let(::unb64) ?: derive(password ?: throw IllegalArgumentException("Потрібен пароль"), unb64(header.salt!!))
            // Перевірка ключа: заголовок вмісту має розшифруватися.
            target.inputStream().buffered().use { i -> readHeader(i); QrTransfer.readDoc(i, key) { _, _ -> } }
            val r = Received(header.id, header.title, header.maxOpens, header.expiresAt, opens = seen.getInt(header.id, 0), key = b64(key), receivedAt = System.currentTimeMillis())
            val final = file(header.id)
            final.delete(); check(target.renameTo(final))
            save(loadAll().filterNot { it.id == r.id } + r)
            r
        } catch (e: Throwable) {
            target.delete()
            if (e is javax.crypto.AEADBadTagException || e.cause is javax.crypto.AEADBadTagException) throw IllegalArgumentException("Неправильний пароль")
            throw e
        }
    }

    /**
     * Відкриває копію: зараховує відкриття й розпаковує файли в [workDir].
     * Повертає список (файл, назва, MIME) або кидає помилку, якщо копія вичерпана.
     */
    suspend fun open(id: String, workDir: File): List<Triple<File, String, String>> = withContext(Dispatchers.IO) {
        val r = loadAll().firstOrNull { it.id == id } ?: throw IllegalStateException("Копію не знайдено")
        if (r.expired()) { remove(id); throw IllegalStateException("Строк дії копії минув або вичерпано кількість відкриттів. Копію видалено.") }
        val updated = r.copy(opens = r.opens + 1)
        seen.edit().putInt(id, updated.opens).apply()
        save(loadAll().map { if (it.id == id) updated else it })
        val files = ArrayList<Triple<File, String, String>>()
        file(id).inputStream().buffered().use { i ->
            readHeader(i)
            QrTransfer.readDoc(i, unb64(r.key)) { info, data ->
                val f = File(workDir, "${files.size}-" + ua.personal.archive.util.Exporter.safeName(info.name))
                f.outputStream().use { data.copyTo(it) }
                files += Triple(f, info.name, info.mime)
            }
        }
        files
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        // Прострочені копії стираються.
        val all = loadAll()
        all.filter { it.expired() }.forEach { file(it.id).delete() }
        save(all.filterNot { it.expired() })
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        file(id).delete()
        save(loadAll().filterNot { it.id == id })
    }

    private fun file(id: String) = File(dir, "$id.mydoc")

    private fun loadAll(): List<Received> = runCatching {
        if (!metaFile.exists()) emptyList()
        else json.decodeFromString(ListSerializer(Received.serializer()), crypto.open(metaFile.readBytes(), LABEL).decodeToString())
    }.getOrDefault(emptyList())

    private fun save(list: List<Received>) {
        val tmp = File(dir, "meta.tmp")
        tmp.writeBytes(crypto.seal(json.encodeToString(ListSerializer(Received.serializer()), list).encodeToByteArray(), LABEL))
        if (!tmp.renameTo(metaFile)) { metaFile.delete(); tmp.renameTo(metaFile) }
        _items.value = list
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 210_000, 256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private val MAGIC = "MYDOCCOPY1".encodeToByteArray()
        private const val LABEL = "secure-copies-v1"
    }
}
