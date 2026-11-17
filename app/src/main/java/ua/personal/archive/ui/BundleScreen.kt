package ua.personal.archive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Пакет документів (наприклад, «для візи»): кілька документів одним PDF з обкладинкою та змістом. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val docs by app.repo.documents.collectAsStateWithLifecycle()
    val sorted = remember(docs) { docs.sortedWith(compareByDescending<ua.personal.archive.data.Document> { it.favorite }.thenBy { it.title.lowercase() }) }
    val selected = remember { mutableStateListOf<String>() }
    var title by remember { mutableStateOf("Пакет документів для візи") }
    var watermark by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Пакет документів") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                Button(
                    enabled = !busy && selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                // Порядок — як позначено користувачем.
                                val list = selected.mapNotNull { id -> docs.firstOrNull { it.id == id } }
                                val pdf = app.exporter.buildBundle(title, list, watermark.ifBlank { null })
                                app.lock.expectingExternalActivity = true
                                app.exporter.shareFiles(listOf(pdf), title)
                            } catch (e: Exception) {
                                error = "Не вдалося створити PDF: ${e.message ?: e.javaClass.simpleName}"
                            } finally { busy = false }
                        }
                    },
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(8.dp))
                    Text(if (selected.isEmpty()) "Позначте документи" else "Створити PDF (${selected.size}) і надіслати")
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                OutlinedTextField(title, { title = it }, label = { Text("Назва на обкладинці") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    watermark, { watermark = it }, label = { Text("Водяний знак (необов'язково)") },
                    placeholder = { Text("Копія для посольства, 17.11.2026") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Позначте документи в потрібному порядку — так вони й підуть у PDF. Першою сторінкою буде обкладинка зі змістом.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(sorted, key = { it.id }) { d ->
                val idx = selected.indexOf(d.id)
                Row(
                    Modifier.fillMaxWidth().clickable { if (idx >= 0) selected.remove(d.id) else selected.add(d.id) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = idx >= 0, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (d.favorite) "★ " else "") + d.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.repo.section(d.sectionId).title + " · файлів: ${d.attachments.size}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (idx >= 0) Text("${idx + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    error?.let {
        AlertDialog(onDismissRequest = { error = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    }
}
