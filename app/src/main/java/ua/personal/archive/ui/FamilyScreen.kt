package ua.personal.archive.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.personal.archive.data.Section
import ua.personal.archive.transfer.FamilySync
import ua.personal.archive.transfer.QrTransfer
import java.util.UUID

/** Сімейний розділ: спільні документи родини, синхронізація телефонів через Wi-Fi поруч. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val sections = rememberSections()
    val family = sections.firstOrNull { it.family }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<String?>(null) } // "host" | "join"
    var message by remember { mutableStateOf<String?>(null) }
    val count = app.repo.documents.value.count { it.sectionId == family?.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сімейний розділ") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Спільні документи родини (паспорти дітей, свідоцтва, поліси) є на кожному телефоні. " +
                    "Коли телефони в одній мережі Wi-Fi, один показує QR-код, інший сканує — і документи оновлюються в обидва боки. " +
                    "Нічого не зберігається в хмарі чи на сервері; дорогою все зашифровано.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (family == null) {
                Button(onClick = {
                    scope.launch {
                        app.repo.saveSection(Section(id = "family-" + UUID.randomUUID(), title = "Родина", icon = "family", color = 0xFFF472B6, hint = "Спільні документи родини", family = true))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FamilyRestroom, null); Text("  Створити сімейний розділ") }
                OutlinedButton(onClick = { mode = "join" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCodeScanner, null); Text("  Приєднатися (сканувати QR родича)")
                }
            } else {
                Text("Розділ «${family.title}»: документів — $count", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { mode = "host" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCode2, null); Text("  Синхронізувати: показати QR")
                }
                OutlinedButton(onClick = { mode = "join" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCodeScanner, null); Text("  Синхронізувати: сканувати QR")
                }
                Text(
                    "Видалення документа не передається на інші телефони — видаліть його там окремо.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (mode) {
        "host" -> SyncHostDialog(onDone = { r -> mode = null; r?.let { message = it } })
        "join" -> SyncJoinDialog(onDone = { r -> mode = null; r?.let { message = it } })
    }
    message?.let { AlertDialog(onDismissRequest = { message = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
}

private fun summary(r: FamilySync.Result) = "Синхронізовано. Надіслано: ${r.sent}, отримано: ${r.received}."

@Composable
private fun SyncHostDialog(onDone: (String?) -> Unit) {
    val app = LocalApp.current
    var sender by remember { mutableStateOf<QrTransfer.Sender?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val s = withContext(Dispatchers.IO) { QrTransfer.openSender() }
            sender = s
            val r = FamilySync.host(s, app.repo) { status = it }
            app.ocr.indexMissing()
            onDone(summary(r))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { onDone("Не вдалося: ${e.message ?: e.javaClass.simpleName}") }
    }
    DisposableEffect(Unit) { onDispose { runCatching { sender?.server?.close() } } }
    val qr = remember(sender) { sender?.let { QrTransfer.qrBitmap(it.offer.encode()) } }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Синхронізація з родиною") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    status != null -> { CircularProgressIndicator(); Text(status.orEmpty()) }
                    qr != null -> {
                        Image(qr.asImageBitmap(), "QR-код", Modifier.fillMaxWidth().aspectRatio(1f))
                        Text("На іншому телефоні: «Ще» → «Сімейний розділ» → «Сканувати QR». Обидва телефони — в одній мережі Wi-Fi.", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> CircularProgressIndicator()
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(null) }) { Text("Скасувати") } },
    )
}

@Composable
private fun SyncJoinDialog(onDone: (String?) -> Unit) {
    val app = LocalApp.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var offer by remember { mutableStateOf<QrTransfer.Offer?>(null) }
    var status by remember { mutableStateOf("Відскануйте QR-код на телефоні родича") }
    LaunchedEffect(Unit) {
        app.lock.expectingExternalActivity = true
        scanCode(context, onResult = { t ->
            offer = QrTransfer.parse(t)
            if (offer == null) onDone("Це не QR-код синхронізації «Мої документи».")
        }, onError = { onDone(it) })
    }
    LaunchedEffect(offer) {
        val o = offer ?: return@LaunchedEffect
        try {
            val r = FamilySync.join(o, app.repo) { status = it }
            app.ocr.indexMissing()
            onDone(summary(r))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            onDone("Не вдалося синхронізувати: ${e.message ?: e.javaClass.simpleName}. Перевірте, що телефони в одній мережі Wi-Fi.")
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Синхронізація з родиною") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (offer != null) CircularProgressIndicator(); Text(status) } },
        confirmButton = { TextButton(onClick = { onDone(null) }) { Text("Скасувати") } },
    )
}
