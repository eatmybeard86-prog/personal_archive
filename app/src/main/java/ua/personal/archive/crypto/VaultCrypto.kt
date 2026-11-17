package ua.personal.archive.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Усе шифрування сховища.
 *
 * Два набори ключів Tink лежать у SharedPreferences, але самі зашифровані
 * головним ключем AES-256-GCM, що живе в Android Keystore (за можливості — у
 * апаратному StrongBox/TEE) і не може бути вивантажений з пристрою.
 *
 *  - [files]: потокове AES-256-GCM-HKDF (сегменти по 4 КБ) для вкладень будь-якого розміру;
 *  - [aead]: AES-256-GCM для індексу документів.
 *
 * Кожен шифротекст прив'язаний до свого ідентифікатора (associated data),
 * тож підмінити один файл іншим непомітно не вийде.
 */
class VaultCrypto(context: Context) {

    private val files: StreamingAead
    private val aead: Aead

    init {
        AeadConfig.register()
        StreamingAeadConfig.register()

        files = AndroidKeysetManager.Builder()
            .withSharedPref(context, "files_keyset", PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)

        aead = AndroidKeysetManager.Builder()
            .withSharedPref(context, "index_keyset", PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    fun encryptingStream(target: File, id: String): OutputStream =
        files.newEncryptingStream(target.outputStream(), id.toByteArray())

    fun decryptingStream(source: File, id: String): InputStream =
        files.newDecryptingStream(source.inputStream(), id.toByteArray())

    fun seal(plain: ByteArray, label: String): ByteArray = aead.encrypt(plain, label.toByteArray())

    fun open(cipher: ByteArray, label: String): ByteArray = aead.decrypt(cipher, label.toByteArray())

    companion object {
        private const val PREFS = "vault_keys"
        private const val MASTER_KEY_URI = "android-keystore://personal_archive_master"
    }
}
