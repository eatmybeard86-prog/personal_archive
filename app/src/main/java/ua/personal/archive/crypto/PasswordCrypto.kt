package ua.personal.archive.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Окремий ключ для розділу «Паролі» — не той самий, що для документів.
 *
 * Набір ключів Tink (AES-256-GCM) лежить у власних SharedPreferences і зашифрований
 * окремим головним ключем Android Keystore («personal_archive_passwords»), який не можна
 * вивантажити з пристрою. Навіть якщо колись буде скомпрометовано ключ документів,
 * паролі ним не розшифруються.
 */
class PasswordCrypto(context: Context) {
    private val aead: Aead

    init {
        AeadConfig.register()
        aead = AndroidKeysetManager.Builder()
            .withSharedPref(context, "passwords_keyset", PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    fun seal(plain: ByteArray, label: String): ByteArray = aead.encrypt(plain, label.toByteArray())
    fun open(cipher: ByteArray, label: String): ByteArray = aead.decrypt(cipher, label.toByteArray())

    companion object {
        private const val PREFS = "passwords_keys"
        private const val MASTER_KEY_URI = "android-keystore://personal_archive_passwords"
    }
}
