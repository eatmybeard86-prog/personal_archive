package ua.personal.archive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.personal.archive.data.Attachment
import ua.personal.archive.data.Document
import ua.personal.archive.util.PdfConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(docId: String, onBack: () -> Unit, onEdit: () -> Unit, onView: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val docs by app.repo.documents.collectAsStateWithLifecycle()
    val doc = docs.firstOrNull { it.id == docId }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (doc == null) {
        // Документ видалено — повертаємось до списку.
        Box(Modifier.fillMaxSize())
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    fun run(label: String, block: suspend () -> Unit) {
        busy = label
        scope.launch {
            try { block() } catch (e: Exception) { error = "$label: ${e.message ?: e.javaClass.simpleName}" } finally { busy = null }
        }
    }

    val ex = app.exporter
    fun shareOriginals(list: List<Attachment>) = run("Підготовка файлів") {
        val files = list.map { ex.decryptToShared(it) }
        app.lock.expectingExternalActivity = true
        ex.shareFiles(files, doc.title)
    }
    fun sharePdf(list: List<Attachment>) = run("Створення PDF") {
        val pdf = ex.buildPdf(pdfDoc(doc, list), list)
        app.lock.expectingExternalActivity = true
        ex.shareFiles(listOf(pdf), doc.title)
    }
    fun print(list: List<Attachment>) = run("Підготовка до друку") {
        val pdf = ex.buildPdf(pdfDoc(doc, list), list)
        app.lock.expectingExternalActivity = true
        ex.print(context, pdf, doc.title)
    }
    fun openExternal(a: Attachment) = run("Відкриття") {
        val f = ex.decryptToShared(a)
        app.lock.expectingExternalActivity = true
        ex.openExternally(f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Редагувати") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Видалити") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            busy?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val section = app.repo.section(doc.sectionId)
                        Icon(section.iconVector, null, tint = section.tint)
                        Spacer(Modifier.width(8.dp))
                        Text(section.title, style = MaterialTheme.typography.titleSmall)
                    }
                    if (doc.number.isNotBlank()) Field("Номер / серія", doc.number)
                    doc.expiresAt?.let {
                        Text(
                            expiryLabel(it),
                            color = if (daysLeft(it) <= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (doc.note.isNotBlank()) Field("Нотатки", doc.note)
                    Text(
                        "Додано ${formatDate(doc.createdAt)} · змінено ${formatDate(doc.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (doc.attachments.isNotEmpty()) {
                Text("Увесь документ", style = MaterialTheme.typography.titleSmall)
                Button(onClick = { sharePdf(doc.attachments) }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Надіслати як один PDF")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { shareOriginals(doc.attachments) }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(4.dp)); Text("Оригінали")
                    }
                    OutlinedButton(onClick = { print(doc.attachments) }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Print, null); Spacer(Modifier.width(4.dp)); Text("Друк")
                    }
                }
                HorizontalDivider()
            }

            Text("Файли (${doc.attachments.size})", style = MaterialTheme.typography.titleSmall)
            if (doc.attachments.isEmpty()) {
                Text("Файлів немає. Додайте їх через «Редагувати».", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            doc.attachments.forEach { a ->
                AttachmentRow(
                    a,
                    onView = { onView(a.id) },
                    onShare = { shareOriginals(listOf(a)) },
                    onSharePdf = { sharePdf(listOf(a)) },
                    onPrint = { print(listOf(a)) },
                    onOpenExternal = { openExternal(a) },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Видалити документ?") },
            text = { Text("«${doc.title}» і всі його файли (${doc.attachments.size}) буде остаточно видалено з пристрою.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { app.repo.delete(doc.id); onBack() }
                }) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
        )
    }
    error?.let {
        AlertDialog(onDismissRequest = { error = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    }
}

/** Назва PDF: для одного файлу — назва документа і файлу. */
private fun pdfDoc(doc: Document, list: List<Attachment>): Document =
    if (list.size == 1 && doc.attachments.size > 1) doc.copy(title = "${doc.title} - ${list[0].name.substringBeforeLast('.')}") else doc

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun AttachmentRow(
    a: Attachment,
    onView: () -> Unit,
    onShare: () -> Unit,
    onSharePdf: () -> Unit,
    onPrint: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onView).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (PdfConverter.kindOf(a) == PdfConverter.Kind.IMAGE) Icons.Outlined.Image else Icons.Outlined.Description, null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(PdfConverter.formatSize(a.size), style = MaterialTheme.typography.bodySmall)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Дії") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Переглянути") }, leadingIcon = { Icon(Icons.Outlined.Description, null) }, onClick = { menu = false; onView() })
                DropdownMenuItem(text = { Text("Надіслати оригінал") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Надіслати як PDF") }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) }, onClick = { menu = false; onSharePdf() })
                DropdownMenuItem(text = { Text("Друк") }, leadingIcon = { Icon(Icons.Outlined.Print, null) }, onClick = { menu = false; onPrint() })
                DropdownMenuItem(text = { Text("Відкрити в іншому додатку") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) }, onClick = { menu = false; onOpenExternal() })
            }
        }
    }
}
