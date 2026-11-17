package ua.personal.archive.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Власний пароль додатку. Зберігається лише хеш PBKDF2-HMAC-SHA256 (210 000 ітерацій, сіль 16 байт).
 * Після 5 невдалих спроб вхід блокується на час, що зростає з кожною наступною помилкою.
 */
class PasswordStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_password", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains("hash")

    fun set(password: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("salt", b64(salt))
            .putString("hash", b64(hash(password, salt, ITERATIONS)))
            .putInt("iter", ITERATIONS)
            .putInt("fails", 0)
            .putLong("blocked_until", 0)
            .apply()
    }

    fun clear() {
        // Тривожний PIN зберігаємо окремо — він не залежить від основного пароля.
        prefs.edit().remove("salt").remove("hash").remove("iter").remove("fails").remove("blocked_until").apply()
    }

    // ── «Тривожний» PIN: відкриває окреме порожнє сховище ──────────────────────

    val duressSet: Boolean get() = prefs.contains("duress_hash")

    fun setDuress(pin: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("duress_salt", b64(salt))
            .putString("duress_hash", b64(hash(pin, salt, ITERATIONS)))
            .apply()
    }

    fun clearDuress() { prefs.edit().remove("duress_salt").remove("duress_hash").apply() }

    private fun matchesDuress(input: CharArray): Boolean {
        val salt = unb64(prefs.getString("duress_salt", null) ?: return false)
        val expected = unb64(prefs.getString("duress_hash", null) ?: return false)
        return MessageDigest.isEqual(expected, hash(input, salt, ITERATIONS))
    }

    /** Чи збігається [input] з основним паролем (без лічильника спроб). */
    fun matchesMain(input: CharArray): Boolean {
        val salt = unb64(prefs.getString("salt", null) ?: return false)
        val expected = unb64(prefs.getString("hash", null) ?: return false)
        return MessageDigest.isEqual(expected, hash(input, salt, prefs.getInt("iter", ITERATIONS)))
    }

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
        val ok = MessageDigest.isEqual(expected, hash(password, salt, prefs.getInt("iter", ITERATIONS)))
        if (ok) {
            prefs.edit().putInt("fails", 0).putLong("blocked_until", 0).apply()
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

    private fun hash(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
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
        const val MIN_LENGTH = 6
    }
}
