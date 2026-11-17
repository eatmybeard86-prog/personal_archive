package ua.personal.archive

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
    val accessLog by lazy { ua.personal.archive.security.AccessLog(this) { VaultCrypto(this) } }
    val repo by lazy { VaultRepository(this) { VaultCrypto(this) } }
    val exporter by lazy { Exporter(this, repo) }
    /** Фонове розпізнавання тексту в сканах для пошуку. */
    val copies by lazy { ua.personal.archive.transfer.SecureCopy(this) { VaultCrypto(this) } }
    /** Отримана захищена копія (.mydoc), що чекає на імпорт після розблокування. */
    val pendingCopy = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    val ocr by lazy { ua.personal.archive.ocr.OcrIndexer(this, repo) }

    /** Файли, отримані з інших додатків; забираються після розблокування. */
    val incoming = kotlinx.coroutines.flow.MutableStateFlow<List<android.net.Uri>>(emptyList())

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        lock = AppLock(this, settings, PasswordStore(this))
        ProcessLifecycleOwner.get().lifecycle.addObserver(lock)
        // Успішні входи пишемо одразу; неправильні — з екрана блокування (там же робиться фото).
        lock.onAccess = { kind, method ->
            when (kind) {
                "MAIN" -> accessLog.add(ua.personal.archive.security.AccessLog.Kind.UNLOCK, method)
                "DURESS" -> accessLog.add(ua.personal.archive.security.AccessLog.Kind.DURESS, method)
            }
        }
        // Екран погас (тайм-аут або кнопка живлення) — блокуємо одразу, навіть якщо відкрита камера.
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (settings.lockOnScreenOff.value) lock.lockNow()
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Залишки тимчасових розшифрованих файлів з минулого запуску.
        cacheDir.resolve("shared").deleteRecursively()
        cacheDir.resolve("work").deleteRecursively()
        cacheDir.resolve("camera").deleteRecursively()
    }
}
