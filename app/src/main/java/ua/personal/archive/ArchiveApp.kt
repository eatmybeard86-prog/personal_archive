package ua.personal.archive

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import ua.personal.archive.crypto.VaultCrypto
import ua.personal.archive.data.VaultRepository
import ua.personal.archive.security.AppLock
import ua.personal.archive.security.PasswordStore
import ua.personal.archive.security.SettingsStore
import ua.personal.archive.util.Exporter

class ArchiveApp : Application() {
    lateinit var settings: SettingsStore
    lateinit var lock: AppLock
    val repo by lazy { VaultRepository(this) { VaultCrypto(this) } }
    val exporter by lazy { Exporter(this, repo) }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        lock = AppLock(this, settings, PasswordStore(this))
        ProcessLifecycleOwner.get().lifecycle.addObserver(lock)
        // Залишки тимчасових розшифрованих файлів з минулого запуску.
        cacheDir.resolve("shared").deleteRecursively()
        cacheDir.resolve("work").deleteRecursively()
        cacheDir.resolve("camera").deleteRecursively()
    }
}
