package ua.personal.archive.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.personal.archive.data.Document
import ua.personal.archive.transfer.QrTransfer

/**
 * Сканер QR-кодів і штрихкодів (Google ML Kit, через Сервіси Google Play; дозвіл на камеру не потрібен).
 * Повертає вміст коду або null, якщо скасовано.
 */
fun scanCode(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { b -> b.rawValue?.takeIf { it.isNotBlank() }?.let(onResult) }
        .addOnFailureListener { e -> onError("Не вдалося запустити сканер кодів: ${e.message ?: e.javaClass.simpleName}") }
}

/** Відправник: показує QR-код, який треба відсканувати на іншому телефоні. */
@Composable
fun SendDialog(doc: Document, onClose: () -> Unit) {
    val app = LocalApp.current
    var sender by remember { mutableStateOf<QrTransfer.Sender?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val s = withContext(Dispatchers.IO) { QrTransfer.openSender() }
            sender = s
            QrTransfer.send(s, app.repo, doc, app.repo.section(doc.sectionId).title) { progress = it }
            done = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!done) error = e.message ?: "Передачу перервано"
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { sender?.server?.close() } } }
    val qr = remember(sender) { sender?.let { QrTransfer.qrBitmap(it.offer.encode()) } }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Передати на інший телефон") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    done -> Text("Готово! Документ «${doc.title}» передано.")
                    progress >= 0f -> {
                        Text("Передавання…")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    qr != null -> {
                        Image(qr.asImageBitmap(), "QR-код", Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(
                            "На іншому телефоні: «Мої документи» → «Ще» → «Отримати документ» і відскануйте цей код. " +
                                "Обидва телефони мають бути в одній мережі Wi-Fi (або один роздає точку доступу іншому). " +
                                "Код одноразовий, файли йдуть напряму між телефонами в зашифрованому вигляді.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> CircularProgressIndicator()
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(if (done || error != null) "Закрити" else "Скасувати") } },
    )
}

/** Отримувач: сканує QR-код відправника й зберігає документ у своє сховище. */
@Composable
fun ReceiveDialog(onClose: () -> Unit, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var offer by remember { mutableStateOf<QrTransfer.Offer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var resultId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        app.lock.expectingExternalActivity = true
        scanCode(context, onResult = { text ->
            offer = QrTransfer.parse(text)
            if (offer == null) error = "Це не QR-код передачі з «Мої документи»."
        }, onError = { error = it })
    }
    LaunchedEffect(offer) {
        val o = offer ?: return@LaunchedEffect
        try {
            resultId = QrTransfer.receive(o, app.repo) { progress = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не вдалося отримати документ: ${e.message ?: e.javaClass.simpleName}. Перевірте, що телефони в одній мережі Wi-Fi."
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Отримати документ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    resultId != null -> Text("Документ отримано й зашифровано у вашому сховищі.")
                    offer != null -> {
                        Text("Отримання…")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> Text("Відскануйте QR-код, показаний на іншому телефоні.")
                }
            }
        },
        confirmButton = {
            val id = resultId
            if (id != null) TextButton(onClick = { onClose(); onOpen(id) }) { Text("Відкрити") }
            else TextButton(onClick = onClose) { Text(if (error != null) "Закрити" else "Скасувати") }
        },
    )
}
