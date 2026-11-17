package ua.personal.archive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.personal.archive.data.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** «Отримані копії»: документи з обмеженням відкриттів / строку, надіслані іншими. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopiesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    val copies = app.copies
    val list by copies.items.collectAsStateWithLifecycle()
    val pending by app.pendingCopy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var askPassword by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT) }

    fun doImport(password: String?) {
        val uri = pending ?: return
        scope.launch {
            try {
                val r = copies.import(uri, password)
                app.pendingCopy.value = null
                askPassword = false
                message = "Отримано: «${r.title}»"
            } catch (e: Exception) {
                if (password != null) message = e.message ?: "Не вдалося відкрити копію"
                else { app.pendingCopy.value = null; message = "Не вдалося відкрити копію: ${e.message}" }
            }
        }
    }

    LaunchedEffect(Unit) { copies.refresh() }
    LaunchedEffect(pending) {
        val uri = pending ?: return@LaunchedEffect
        val needs = runCatching { copies.needsPassword(uri) }.getOrElse { app.pendingCopy.value = null; message = "Файл пошкоджено"; return@LaunchedEffect }
        if (needs) askPassword = true else doImport(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отримані копії") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        if (list.isEmpty()) Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Тут з'являться захищені копії («.mydoc»), які вам надішлють. Відкрийте такий файл із месенджера через «Мої документи».")
        } else LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            val limits = listOfNotNull(
                                c.remainingOpens?.let { "залишилось відкриттів: $it" },
                                c.expiresAt?.let { "до ${fmt.format(Date(it))}" },
                            ).ifEmpty { listOf("без обмежень") }.joinToString(" · ")
                            Text(limits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { scope.launch { copies.remove(c.id) } }) { Icon(Icons.Outlined.Delete, "Видалити") }
                    }
                }
            }
        }
    }

    if (askPassword) {
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPassword = false; app.pendingCopy.value = null },
            title = { Text("Пароль копії") },
            text = { PasswordField(pwd, { pwd = it }, "Пароль від відправника", ImeAction.Done) { doImport(pwd) } },
            confirmButton = { TextButton(onClick = { doImport(pwd) }) { Text("Відкрити") } },
            dismissButton = { TextButton(onClick = { askPassword = false; app.pendingCopy.value = null }) { Text("Скасувати") } },
        )
    }
    message?.let { AlertDialog(onDismissRequest = { message = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
}

/** Відправник: налаштування обмежень і створення файлу «.mydoc». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecureCopyDialog(doc: Document, onClose: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var opens by remember { mutableStateOf<Int?>(3) }
    var days by remember { mutableStateOf<Int?>(7) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Копія з обмеженням") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Скільки разів можна відкрити:", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5, 10, null).forEach { n -> FilterChip(opens == n, { opens = n }, label = { Text(n?.toString() ?: "без обмеж.") }) }
                }
                Text("Скільки днів діє:", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 7, 30, null).forEach { n -> FilterChip(days == n, { days = n }, label = { Text(n?.let { "$it дн." } ?: "без обмеж.") }) }
                }
                PasswordField(password, { password = it }, "Пароль (рекомендовано)", ImeAction.Done) {}
                Text(
                    "Отримувач відкриє файл лише в додатку «Мої документи»; скріншоти там заборонені. Пароль надішліть окремо (іншим месенджером або голосом). " +
                        "Обмеження перевіряє додаток отримувача — від фото екрана іншою камерою вони не захищають.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val f = app.copies.pack(app.repo, doc, app.repo.section(doc.sectionId).title, opens, days, password.ifBlank { null })
                        app.lock.expectingExternalActivity = true
                        app.exporter.shareFiles(listOf(f), doc.title)
                        onClose()
                    } catch (e: Exception) {
                        error = e.message ?: "Помилка"
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Створення…" else "Надіслати") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Скасувати") } },
    )
}
