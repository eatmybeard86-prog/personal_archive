package ua.personal.archive.security

import android.content.Context
import android.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Власний пароль (майстер-пароль) додатку. Сам пароль ніде не зберігається — лише його хеш
 * Argon2id (32 МБ пам'яті, 3 проходи) з індивідуальною випадковою сіллю 16 байт.
 * Старі хеші PBKDF2-HMAC-SHA256 з попередніх версій перераховуються в Argon2id при першому вдалому вході.
 * Після 5 невдалих спроб вхід блокується на час, що зростає з кожною наступною помилкою.
 */
class PasswordStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_password", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains("hash")

    /** Збережений секрет — числовий PIN (а не пароль). */
    val isNumeric: Boolean get() = prefs.getBoolean("numeric", false)

    fun set(password: CharArray) {
        prefs.edit().putBoolean("numeric", password.isNotEmpty() && password.all { it.isDigit() }).apply()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("salt", b64(salt))
            .putString("hash", b64(argon2(password, salt)))
            .putString("kdf", KDF_ARGON2ID)
            .remove("iter")
            .putInt("fails", 0)
            .putLong("blocked_until", 0)
            .apply()
    }

    fun clear() {
        // Тривожний PIN зберігаємо окремо — він не залежить від основного пароля.
        prefs.edit().remove("numeric").remove("salt").remove("hash").remove("iter").remove("kdf").remove("fails").remove("blocked_until").apply()
    }

    // ── «Тривожний» PIN: відкриває окреме порожнє сховище ──────────────────────

    val duressSet: Boolean get() = prefs.contains("duress_hash")

    fun setDuress(pin: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("duress_salt", b64(salt))
            .putString("duress_hash", b64(argon2(pin, salt)))
            .putString("duress_kdf", KDF_ARGON2ID)
            .apply()
    }

    fun clearDuress() { prefs.edit().remove("duress_salt").remove("duress_hash").remove("duress_kdf").apply() }

    private fun matchesDuress(input: CharArray): Boolean {
        val salt = unb64(prefs.getString("duress_salt", null) ?: return false)
        val expected = unb64(prefs.getString("duress_hash", null) ?: return false)
        val legacy = prefs.getString("duress_kdf", null) != KDF_ARGON2ID
        val ok = MessageDigest.isEqual(expected, if (legacy) pbkdf2(input, salt, ITERATIONS) else argon2(input, salt))
        if (ok && legacy) setDuress(input) // перехід зі старого PBKDF2 на Argon2id
        return ok
    }

    /** Чи збігається [input] з основним паролем (без лічильника спроб). */
    fun matchesMain(input: CharArray): Boolean {
        val salt = unb64(prefs.getString("salt", null) ?: return false)
        val expected = unb64(prefs.getString("hash", null) ?: return false)
        return MessageDigest.isEqual(expected, mainHash(input, salt))
    }

    private fun mainHash(input: CharArray, salt: ByteArray): ByteArray =
        if (prefs.getString("kdf", null) == KDF_ARGON2ID) argon2(input, salt) else pbkdf2(input, salt, prefs.getInt("iter", ITERATIONS))

    enum class Check { MAIN, DURESS, WRONG }

    /**
     * Перевіряє введене: основний пароль → справжнє сховище, тривожний PIN → порожнє.
     * Успішний вхід тривожним PIN-ом теж скидає лічильник помилок, щоб нічим не відрізнятися.
     */
    fun check(input: CharArray): Check {
        if (waitMs() > 0) return Check.WRONG
        if (isSet && verify(input)) return Check.MAIN
        if (duressSet && matchesDuress(input)) {
            prefs.edit().putInt("fails", 0).putLong("blocked_until", 0).apply()
            return Check.DURESS
        }
        if (!isSet) registerFail()
        return Check.WRONG
    }

    private fun registerFail() {
        val fails = prefs.getInt("fails", 0) + 1
        val edit = prefs.edit().putInt("fails", fails)
        if (fails >= 5) edit.putLong("blocked_until", System.currentTimeMillis() + (30_000L shl (fails - 5).coerceAtMost(7)).coerceAtMost(3_600_000L))
        edit.apply()
    }

    /** Скільки мілісекунд ще чекати перед наступною спробою (0 — можна вводити). */
    fun waitMs(): Long = (prefs.getLong("blocked_until", 0) - System.currentTimeMillis()).coerceAtLeast(0)

    fun verify(password: CharArray): Boolean {
        if (!isSet || waitMs() > 0) return false
        val salt = unb64(prefs.getString("salt", null) ?: return false)
        val expected = unb64(prefs.getString("hash", null) ?: return false)
        val legacy = prefs.getString("kdf", null) != KDF_ARGON2ID
        val ok = MessageDigest.isEqual(expected, mainHash(password, salt))
        if (ok) {
            prefs.edit().putInt("fails", 0).putLong("blocked_until", 0).apply()
            if (legacy) set(password) // перехід зі старого PBKDF2 на Argon2id з новою сіллю
        } else {
            val fails = prefs.getInt("fails", 0) + 1
            val edit = prefs.edit().putInt("fails", fails)
            if (fails >= 5) {
                // 30 с, 1 хв, 2 хв, 4 хв ... до 1 години
                val delay = (30_000L shl (fails - 5).coerceAtMost(7)).coerceAtMost(3_600_000L)
                edit.putLong("blocked_until", System.currentTimeMillis() + delay)
            }
            edit.apply()
        }
        return ok
    }

    /** Argon2id (RFC 9106), версія 1.3. */
    private fun argon2(password: CharArray, salt: ByteArray): ByteArray = argon2id(password, salt)

    /** Старий формат (до Argon2id) — лише для перевірки й міграції. */
    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val ITERATIONS = 210_000
        private const val KDF_ARGON2ID = "argon2id-v1"
        const val ARGON2_MEMORY_KB = 32_768
        const val ARGON2_ITERATIONS = 3
        const val ARGON2_PARALLELISM = 1

        fun argon2id(password: CharArray, salt: ByteArray, memoryKb: Int = ARGON2_MEMORY_KB, iterations: Int = ARGON2_ITERATIONS): ByteArray {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKb)
                .withIterations(iterations)
                .withParallelism(ARGON2_PARALLELISM)
                .withSalt(salt)
                .build()
            val out = ByteArray(32)
            Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out)
            return out
        }
        const val MIN_LENGTH = 6
        const val MIN_PIN = 4
    }
}
