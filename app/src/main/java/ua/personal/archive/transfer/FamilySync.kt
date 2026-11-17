package ua.personal.archive.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ua.personal.archive.data.Attachment
import ua.personal.archive.data.Document
import ua.personal.archive.data.Section
import ua.personal.archive.data.VaultRepository
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * Синхронізація сімейного розділу між телефонами в одній мережі Wi-Fi, без сервера й хмари.
 *
 * Один телефон показує QR (адреса + одноразовий ключ), інший сканує. Далі телефони обмінюються
 * списками документів розділу (id + час зміни) і пересилають один одному те, чого в іншого немає
 * або що новіше. Усе йде зашифрованим AES-256-GCM (окремий ключ для кожного напрямку).
 * Видалення документа на одному телефоні на інший не передається — видаліть його там окремо.
 */
object FamilySync {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private class Manifest(val section: Section, val docs: Map<String, Long>)

    class Result(val sent: Int, val received: Int)

    private fun dirKey(key: ByteArray, label: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(key + label.encodeToByteArray())

    fun familySection(repo: VaultRepository): Section? = repo.sections.value.firstOrNull { it.family }

    /** Телефон, що показує QR. Розділ має вже існувати. */
    suspend fun host(sender: QrTransfer.Sender, repo: VaultRepository, onProgress: (String) -> Unit): Result = withContext(Dispatchers.IO) {
        val section = familySection(repo) ?: throw IllegalStateException("Спершу створіть сімейний розділ")
        sender.server.use { server ->
            while (true) {
                val s = server.accept()
                s.use {
                    s.soTimeout = 120_000
                    val rawIn = s.getInputStream()
                    val got = ByteArray(16)
                    try { DataInputStream(rawIn).readFully(got) } catch (e: Exception) { return@use }
                    if (!MessageDigest.isEqual(got, sender.offer.token)) return@use
                    val outEnc = QrTransfer.EncryptingOutput(s.getOutputStream(), dirKey(sender.offer.key, "host"))
                    val out = DataOutputStream(outEnc)
                    val inp = DataInputStream(QrTransfer.DecryptingInput(rawIn, dirKey(sender.offer.key, "guest")))
                    onProgress("Обмін списками…")
                    writeManifest(out, Manifest(section, manifestOf(repo, section.id))); out.flush(); outEnc.flushFrame()
                    val theirs = readManifest(inp)
                    onProgress("Отримання документів…")
                    val received = receiveDocs(inp, repo, section.id)
                    onProgress("Надсилання документів…")
                    val sent = sendDocs(out, repo, section.id, theirs.docs)
                    out.flush(); outEnc.finish()
                    runCatching { inp.read() }
                    return@withContext Result(sent, received)
                }
            }
            Result(0, 0)
        }
    }

    /** Телефон, що сканує QR. Якщо сімейного розділу ще немає — створюється такий самий. */
    suspend fun join(offer: QrTransfer.Offer, repo: VaultRepository, onProgress: (String) -> Unit): Result = withContext(Dispatchers.IO) {
        Socket().use { s ->
            s.connect(InetSocketAddress(offer.host, offer.port), 15_000)
            s.soTimeout = 120_000
            s.getOutputStream().apply { write(offer.token); flush() }
            val outEnc = QrTransfer.EncryptingOutput(s.getOutputStream(), dirKey(offer.key, "guest"))
            val out = DataOutputStream(outEnc)
            val inp = DataInputStream(QrTransfer.DecryptingInput(s.getInputStream(), dirKey(offer.key, "host")))
            onProgress("Обмін списками…")
            val theirs = readManifest(inp)
            val local = repo.sections.value.firstOrNull { it.id == theirs.section.id }
            if (local == null) repo.saveSection(theirs.section.copy(family = true))
            else if (!local.family) repo.saveSection(local.copy(family = true))
            writeManifest(out, Manifest(theirs.section, manifestOf(repo, theirs.section.id)))
            onProgress("Надсилання документів…")
            val sent = sendDocs(out, repo, theirs.section.id, theirs.docs)
            out.flush(); outEnc.flushFrame()
            onProgress("Отримання документів…")
            val received = receiveDocs(inp, repo, theirs.section.id)
            outEnc.finish()
            Result(sent, received)
        }
    }

    private fun manifestOf(repo: VaultRepository, sectionId: String) =
        repo.documents.value.filter { it.sectionId == sectionId }.associate { it.id to it.updatedAt }

    private fun writeManifest(out: DataOutputStream, m: Manifest) {
        val b = json.encodeToString(Manifest.serializer(), m).encodeToByteArray()
        out.writeInt(b.size); out.write(b)
    }

    private fun readManifest(inp: DataInputStream): Manifest {
        val len = inp.readInt(); require(len in 1..5_000_000) { "Неправильні дані" }
        return json.decodeFromString(Manifest.serializer(), ByteArray(len).also { inp.readFully(it) }.decodeToString())
    }

    private fun sendDocs(out: DataOutputStream, repo: VaultRepository, sectionId: String, theirs: Map<String, Long>): Int {
        val docs = repo.documents.value.filter { it.sectionId == sectionId && (theirs[it.id] ?: -1L) < it.updatedAt }
        out.writeInt(docs.size)
        docs.forEach { QrTransfer.writeDocBody(out, repo, it, "") }
        return docs.size
    }

    private suspend fun receiveDocs(inp: DataInputStream, repo: VaultRepository, sectionId: String): Int {
        val n = inp.readInt(); require(n in 0..100_000)
        repeat(n) {
            val atts = ArrayList<Attachment>()
            // Файли читаються з потоку по черзі й одразу шифруються у сховище.
            val header = QrTransfer.readDocBody(inp) { info, data -> importBlocking(repo, data, info, atts) }
            val old = repo.find(header.id)
            if (old != null && old.updatedAt >= header.updatedAt) { repo.discardAttachments(atts); return@repeat }
            val doc = Document(
                id = header.id, title = header.title, section = sectionId, number = header.number, note = header.note,
                expiresAt = header.expiresAt, tags = header.tags, createdAt = header.createdAt, updatedAt = header.updatedAt,
                attachments = atts, favorite = old?.favorite ?: false,
            )
            repo.saveExact(doc)
            old?.let { repo.discardAttachments(it.attachments) }
        }
        return n
    }

    private fun importBlocking(repo: VaultRepository, data: InputStream, info: QrTransfer.FileInfo, into: MutableList<Attachment>) {
        into += kotlinx.coroutines.runBlocking { repo.importStream(data, info.name, info.mime) }
    }
}
