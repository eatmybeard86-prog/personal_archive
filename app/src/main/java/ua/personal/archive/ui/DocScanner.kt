package ua.personal.archive.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

/**
 * Сканер документів через камеру (Google ML Kit Document Scanner).
 * Сам знаходить краї аркуша, відрізає фон, вирівнює перспективу й покращує зображення
 * (фільтри «Авто», «Ч/Б», прибирання тіней і плям). Кілька сторінок складаються в один PDF.
 * Камера й обробка працюють у Google Play Services на пристрої, без інтернету після першого завантаження модуля.
 */
object DocScanner {
    private val options = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setGalleryImportAllowed(true)
        .setPageLimit(50)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .build()

    fun start(activity: Activity, onReady: (IntentSender) -> Unit, onError: (String) -> Unit) {
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener(onReady)
            .addOnFailureListener { e ->
                onError("Не вдалося запустити сканер. Потрібні оновлені Сервіси Google Play. (${e.message ?: e.javaClass.simpleName})")
            }
    }

    fun resultPdf(resultCode: Int, data: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return GmsDocumentScanningResult.fromActivityResultIntent(data)?.pdf?.uri
    }

    /** Тимчасовий файл сканера (у кеші додатку) — видаляємо після шифрування. */
    fun localFile(uri: Uri): File? = if (uri.scheme == "file") uri.path?.let(::File) else null
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
