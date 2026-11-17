package ua.personal.archive.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import kotlinx.coroutines.launch
import ua.personal.archive.data.Attachment
import ua.personal.archive.data.Category
import ua.personal.archive.data.Document
import ua.personal.archive.util.PdfConverter
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(docId: String?, initialCategory: Category? = null, onDone: (String?) -> Unit, onBack: () -> Unit) {
    val app = LocalApp.current
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val original = remember(docId) { docId?.let { repo.find(it) } }

    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var category by remember { mutableStateOf(original?.category ?: initialCategory ?: Category.PERSONAL) }
    var number by remember { mutableStateOf(original?.number.orEmpty()) }
    var note by remember { mutableStateOf(original?.note.orEmpty()) }
    var expiresAt by remember { mutableStateOf(original?.expiresAt) }
    val attachments = remember { mutableStateListOf<Attachment>().apply { original?.attachments?.let(::addAll) } }
    val added = remember { mutableStateListOf<Attachment>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun import(uris: List<Uri>, deleteAfter: File? = null, name: String? = null) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            try {
                uris.forEach { uri ->
                    val a = repo.importUri(uri, name)
                    attachments += a
                    added += a
                }
                if (title.isBlank() && attachments.isNotEmpty()) title = attachments.first().name.substringBeforeLast('.')
            } catch (e: Exception) {
                error = "Не вдалося додати файл: ${e.message}"
            } finally {
                deleteAfter?.delete()
                busy = false
            }
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { import(it) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { import(it) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null && f.length() > 0) {
            val n = "Фото ${java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss", java.util.Locale.ROOT).format(java.util.Date())}.jpg"
            import(listOf(Uri.fromFile(f)), deleteAfter = f, name = n)
        } else f?.delete()
    }

    val dirty = title != original?.title.orEmpty() || added.isNotEmpty() || category != (original?.category ?: initialCategory ?: Category.PERSONAL) ||
        number != original?.number.orEmpty() || note != original?.note.orEmpty() || expiresAt != original?.expiresAt ||
        attachments.size != (original?.attachments?.size ?: 0)

    fun cancel() {
        scope.launch { repo.discardAttachments(added.toList()) }
        onBack()
    }
    BackHandler { if (dirty) confirmExit = true else cancel() }

    fun save() {
        if (title.isBlank()) { error = "Вкажіть назву документа"; return }
        busy = true
        scope.launch {
            val now = System.currentTimeMillis()
            val doc = (original ?: Document(id = UUID.randomUUID().toString(), title = "", category = category, createdAt = now, updatedAt = now))
                .copy(title = title.trim(), category = category, number = number.trim(), note = note.trim(), expiresAt = expiresAt, attachments = attachments.toList())
            try {
                repo.save(doc)
                // Файли, які прибрали з документа, видаляються зі сховища.
                val removed = original?.attachments.orEmpty().filter { o -> attachments.none { it.id == o.id } }
                repo.discardAttachments(removed)
                onDone(if (original == null) doc.id else null)
            } catch (e: Exception) {
                error = "Не вдалося зберегти: ${e.message}"
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Новий документ" else "Редагування") },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmExit = true else cancel() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад")
                    }
                },
                actions = { TextButton(onClick = ::save, enabled = !busy) { Text("Зберегти") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it }, label = { Text("Назва *") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Розділ", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = category == c, onClick = { category = c },
                        label = { Text(c.title) }, leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp)) },
                    )
                }
            }
            Text(category.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(number, { number = it }, label = { Text("Номер / серія") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickDate = true }) {
                    Icon(Icons.Outlined.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(expiresAt?.let { "Дійсний до ${formatDate(it)}" } ?: "Строк дії (необов'язково)")
                }
                if (expiresAt != null) IconButton(onClick = { expiresAt = null }) { Icon(Icons.Outlined.Close, "Прибрати дату") }
            }

            OutlinedTextField(note, { note = it }, label = { Text("Нотатки") }, minLines = 3, modifier = Modifier.fillMaxWidth())

            HorizontalDivider()
            Text("Файли (${attachments.size})", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val (file, uri) = app.exporter.cameraUri()
                    cameraFile = file
                    app.lock.expectingExternalActivity = true
                    takePhoto.launch(uri)
                }) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(4.dp)); Text("Камера") }
                OutlinedButton(onClick = {
                    app.lock.expectingExternalActivity = true
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(4.dp)); Text("Галерея") }
                OutlinedButton(onClick = {
                    app.lock.expectingExternalActivity = true
                    pickFiles.launch(arrayOf("*/*"))
                }) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(4.dp)); Text("Файл") }
            }
            attachments.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (PdfConverter.kindOf(a) == PdfConverter.Kind.IMAGE) Icons.Outlined.Image else Icons.Outlined.Description, null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PdfConverter.formatSize(a.size), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { attachments.remove(a) }) { Icon(Icons.Outlined.Close, "Прибрати") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Зберегти") }
            Text(
                "Файли шифруються одразу під час додавання й зберігаються лише всередині додатку. " +
                    "Оригінал у галереї чи «Завантаженнях» можна видалити.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = expiresAt)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { expiresAt = state.selectedDateMillis; pickDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Скасувати") } },
        ) { DatePicker(state) }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Вийти без збереження?") },
            text = { Text("Зміни та щойно додані файли буде втрачено.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; cancel() }) { Text("Вийти") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Залишитися") } },
        )
    }
    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}
