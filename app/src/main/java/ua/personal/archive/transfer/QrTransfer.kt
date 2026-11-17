package ua.personal.archive.transfer

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ua.personal.archive.data.Attachment
import ua.personal.archive.data.Document
import ua.personal.archive.data.VaultRepository
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Передача документа на інший телефон.
 *
 * QR-код містить лише адресу в локальній мережі та одноразовий ключ AES-256. Сам документ
 * (усі файли, назва, номер, нотатки, теги) йде напряму між телефонами через Wi-Fi, зашифрований
 * цим ключем блоками AES-256-GCM. Без QR-коду (тобто без ключа) перехоплений трафік прочитати неможливо.
 * Обидва телефони мають бути в одній мережі Wi-Fi (або один роздає точку доступу іншому).
 */
object QrTransfer {
    private const val PREFIX = "MYDOCS1"
    private const val CHUNK = 64 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    class Offer(val host: String, val port: Int, val key: ByteArray, val token: ByteArray) {
        fun encode(): String = listOf(PREFIX, host, port.toString(), b64(key), b64(token)).joinToString("|")
    }

    fun parse(text: String): Offer? {
        val p = text.trim().split('|')
        if (p.size != 5 || p[0] != PREFIX) return null
        return runCatching { Offer(p[1], p[2].toInt(), unb64(p[3]), unb64(p[4])) }.getOrNull()
            ?.takeIf { it.key.size == 32 && it.token.size == 16 }
    }

    @Serializable
    private class Header(
        val title: String, val number: String, val note: String, val expiresAt: Long?,
        val tags: List<String>, val section: String, val sectionTitle: String,
        val files: List<FileInfo>,
    )

    @Serializable
    private class FileInfo(val name: String, val mime: String, val size: Long)

    /** Адреса телефону в Wi-Fi / точці доступу. */
    fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { n -> if (n.name.startsWith("wlan") || n.name.startsWith("ap") || n.name.startsWith("swlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /** Сервер відправника: чекає одного отримувача. Закрийте [server], щоб скасувати. */
    class Sender(val server: ServerSocket, val offer: Offer)

    fun openSender(): Sender {
        val host = localAddress() ?: throw IllegalStateException("Немає підключення до Wi-Fi. Підключіть обидва телефони до однієї мережі або увімкніть точку доступу.")
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(0))
        server.soTimeout = 10 * 60_000
        val rnd = SecureRandom()
        val key = ByteArray(32).also(rnd::nextBytes)
        val token = ByteArray(16).also(rnd::nextBytes)
        return Sender(server, Offer(host, server.localPort, key, token))
    }

    /** Надсилає документ першому, хто прийде з правильним токеном. */
    suspend fun send(sender: Sender, repo: VaultRepository, doc: Document, sectionTitle: String, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            sender.server.use { server ->
                while (true) {
                    val socket = server.accept()
                    socket.use { s ->
                        s.soTimeout = 60_000
                        val input = DataInputStream(s.getInputStream())
                        val got = ByteArray(16)
                        try { input.readFully(got) } catch (e: Exception) { return@use }
                        if (!MessageDigest.isEqual(got, sender.offer.token)) return@use
                        val out = EncryptingOutput(s.getOutputStream(), sender.offer.key)
                        val header = Header(
                            doc.title, doc.number, doc.note, doc.expiresAt, doc.tags, doc.sectionId, sectionTitle,
                            doc.attachments.map { FileInfo(it.name, it.mime, it.size) },
                        )
                        val hb = json.encodeToString(Header.serializer(), header).encodeToByteArray()
                        val d = DataOutputStream(out)
                        d.writeInt(hb.size); d.write(hb)
                        val total = doc.attachments.sumOf { it.size }.coerceAtLeast(1)
                        var sent = 0L
                        val buf = ByteArray(CHUNK)
                        for (a in doc.attachments) {
                            var left = a.size
                            repo.openAttachment(a).use { src ->
                                while (left > 0) {
                                    val n = src.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                    if (n < 0) throw EOFException("Файл ${a.name} пошкоджено")
                                    d.write(buf, 0, n); left -= n; sent += n
                                    onProgress(sent.toFloat() / total)
                                }
                            }
                        }
                        d.flush()
                        out.finish()
                        // Чекаємо підтвердження, що отримувач усе зберіг.
                        runCatching { input.readByte() }
                        return@withContext
                    }
                }
            }
        }

    /** Отримує документ і зберігає його у сховище. Повертає id нового документа. */
    suspend fun receive(offer: Offer, repo: VaultRepository, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        Socket().use { s ->
            s.connect(InetSocketAddress(offer.host, offer.port), 15_000)
            s.soTimeout = 60_000
            s.getOutputStream().apply { write(offer.token); flush() }
            val d = DataInputStream(DecryptingInput(s.getInputStream(), offer.key))
            val len = d.readInt()
            require(len in 1..1_000_000) { "Неправильні дані" }
            val hb = ByteArray(len).also { d.readFully(it) }
            val header = json.decodeFromString(Header.serializer(), hb.decodeToString())
            val total = header.files.sumOf { it.size }.coerceAtLeast(1)
            var got = 0L
            val atts = ArrayList<Attachment>()
            try {
                for (f in header.files) {
                    val limited = object : InputStream() {
                        var left = f.size
                        override fun read(): Int {
                            if (left <= 0) return -1
                            val b = d.read(); if (b < 0) throw EOFException(); left--; got++; return b
                        }
                        override fun read(b: ByteArray, off: Int, l: Int): Int {
                            if (left <= 0) return -1
                            val n = d.read(b, off, minOf(l.toLong(), left).toInt())
                            if (n < 0) throw EOFException("З'єднання обірвалося")
                            left -= n; got += n; onProgress(got.toFloat() / total); return n
                        }
                    }
                    atts += repo.importStream(limited, f.name, f.mime)
                }
                val sectionId = repo.sections.value.firstOrNull { it.id == header.section }?.id
                    ?: repo.sections.value.firstOrNull { it.title.equals(header.sectionTitle, ignoreCase = true) }?.id
                    ?: repo.sections.value.firstOrNull()?.id ?: "OTHER"
                val now = System.currentTimeMillis()
                val doc = Document(
                    id = UUID.randomUUID().toString(), title = header.title, section = sectionId,
                    number = header.number, note = header.note, expiresAt = header.expiresAt, tags = header.tags,
                    createdAt = now, updatedAt = now, attachments = atts,
                )
                repo.save(doc)
                runCatching { s.getOutputStream().apply { write(1); flush() } }
                doc.id
            } catch (e: Throwable) {
                repo.discardAttachments(atts)
                throw e
            }
        }
    }

    fun qrBitmap(text: String, size: Int = 720): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP or Base64.URL_SAFE)

    // ── Потік блоків AES-256-GCM: [довжина][шифротекст]; номер блоку й ознака кінця входять в AAD ──

    private fun nonce(counter: Long) = ByteArray(12).also { n -> for (i in 0 until 8) n[4 + i] = (counter ushr (8 * (7 - i))).toByte() }
    private fun aad(counter: Long, last: Boolean) = "mydocs-$counter-${if (last) 1 else 0}".encodeToByteArray()

    private class EncryptingOutput(private val raw: OutputStream, key: ByteArray) : OutputStream() {
        private val k = SecretKeySpec(key, "AES")
        private val out = DataOutputStream(raw.buffered(CHUNK + 64))
        private val buf = ByteArray(CHUNK)
        private var pos = 0
        private var counter = 0L

        override fun write(b: Int) { buf[pos++] = b.toByte(); if (pos == buf.size) frame(false) }
        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off; var l = len
            while (l > 0) {
                val n = minOf(l, buf.size - pos)
                System.arraycopy(b, o, buf, pos, n); pos += n; o += n; l -= n
                if (pos == buf.size) frame(false)
            }
        }

        private fun frame(last: Boolean) {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, nonce(counter)))
            c.updateAAD(aad(counter, last))
            val ct = c.doFinal(buf, 0, pos)
            out.writeInt(ct.size); out.writeBoolean(last); out.write(ct)
            counter++; pos = 0
        }

        fun finish() { frame(true); out.flush() }
        override fun flush() {}
    }

    private class DecryptingInput(raw: InputStream, key: ByteArray) : InputStream() {
        private val k = SecretKeySpec(key, "AES")
        private val inp = DataInputStream(raw.buffered(CHUNK + 64))
        private var buf = ByteArray(0)
        private var pos = 0
        private var counter = 0L
        private var ended = false

        private fun next(): Boolean {
            if (ended) return false
            val len = inp.readInt()
            require(len in 16..(CHUNK + 16)) { "Неправильні дані" }
            val last = inp.readBoolean()
            val ct = ByteArray(len).also { inp.readFully(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, nonce(counter)))
            c.updateAAD(aad(counter, last))
            buf = c.doFinal(ct) // кидає помилку, якщо дані підмінено
            pos = 0; counter++; ended = last
            return true
        }

        override fun read(): Int {
            while (pos >= buf.size) if (!next()) return -1
            return buf[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= buf.size) if (!next()) return -1
            val n = minOf(len, buf.size - pos)
            System.arraycopy(buf, pos, b, off, n); pos += n
            return n
        }
    }
}
