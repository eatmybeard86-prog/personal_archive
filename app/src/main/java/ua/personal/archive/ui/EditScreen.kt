package ua.personal.archive.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.outlined.DocumentScanner
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
import androidx.compose.runtime.LaunchedEffect
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
fun EditScreen(docId: String?, initialCategory: String? = null, autoScan: Boolean = false, sharedUris: List<Uri> = emptyList(), onDone: (String?) -> Unit, onBack: () -> Unit) {
    val app = LocalApp.current
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val original = remember(docId) { docId?.let { repo.find(it) } }

    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    val sections = rememberSections()
    val docsForTags by repo.documents.collectAsStateWithLifecycle()
    val defaultSection = original?.sectionId ?: initialCategory ?: sections.firstOrNull()?.id ?: Category.OTHER.name
    var category by remember { mutableStateOf(defaultSection) }
    var number by remember { mutableStateOf(original?.number.orEmpty()) }
    var note by remember { mutableStateOf(original?.note.orEmpty()) }
    var tagsText by remember { mutableStateOf(original?.tags.orEmpty().joinToString(", ")) }
    var expiresAt by remember { mutableStateOf(original?.expiresAt) }
    val attachments = remember { mutableStateListOf<Attachment>().apply { original?.attachments?.let(::addAll) } }
    val added = remember { mutableStateListOf<Attachment>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun importUris(uris: List<Uri>, deleteAfter: File? = null, name: String? = null) {
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

    // Файли, надіслані з іншого додатку («Поділитися» / «Відкрити за допомогою»).
    LaunchedEffect(sharedUris) { if (sharedUris.isNotEmpty()) importUris(sharedUris) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importUris(it) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { importUris(it) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null && f.length() > 0) {
            val n = "Фото ${java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss", java.util.Locale.ROOT).format(java.util.Date())}.jpg"
            importUris(listOf(Uri.fromFile(f)), deleteAfter = f, name = n)
        } else f?.delete()
    }

    // Сканер документів (Google ML Kit): сам знаходить аркуш, прибирає фон, вирівнює й покращує зображення.
    val scanDoc = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val pdf = DocScanner.resultPdf(res.resultCode, res.data) ?: return@rememberLauncherForActivityResult
        val n = "Скан ${java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss", java.util.Locale.ROOT).format(java.util.Date())}.pdf"
        importUris(listOf(pdf), deleteAfter = DocScanner.localFile(pdf), name = n)
    }
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    fun startScan() {
        val act = activity
        if (act == null) { error = "Сканер недоступний"; return }
        busy = true
        DocScanner.start(act,
            onReady = { sender ->
                busy = false
                app.lock.expectingExternalActivity = true
                scanDoc.launch(IntentSenderRequest.Builder(sender).build())
            },
            onError = { msg -> busy = false; error = msg },
        )
    }
    // Відкрито кнопкою «Сканувати» з головного екрана — одразу запускаємо камеру (лише раз).
    var autoScanDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (autoScan && !autoScanDone) { autoScanDone = true; startScan() } }

    val dirty = title != original?.title.orEmpty() || added.isNotEmpty() || category != defaultSection ||
        number != original?.number.orEmpty() || note != original?.note.orEmpty() || expiresAt != original?.expiresAt ||
        parseTags(tagsText) != original?.tags.orEmpty() || attachments.map { it.id } != original?.attachments.orEmpty().map { it.id } ||
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
            val doc = (original ?: Document(id = UUID.randomUUID().toString(), title = "", section = category, createdAt = now, updatedAt = now))
                .copy(title = title.trim(), section = category, number = number.trim(), note = note.trim(), expiresAt = expiresAt, attachments = attachments.toList(), tags = parseTags(tagsText))
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
                sections.forEach { c ->
                    FilterChip(
                        selected = category == c.id, onClick = { category = c.id },
                        label = { Text(c.title) }, leadingIcon = { Icon(c.iconVector, null, Modifier.size(18.dp), tint = c.tint) },
                    )
                }
            }
            if (app.repo.section(category).hint.isNotBlank()) Text(app.repo.section(category).hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
            OutlinedTextField(
                tagsText, { tagsText = it },
                label = { Text("Теги") },
                placeholder = { Text("через кому: сім'я, оригінал, авто") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            // Підказки: теги, які вже є в інших документах.
            val allTags = remember(docsForTags) { docsForTags.flatMap { it.tags }.distinctBy { it.lowercase() }.sorted() }
            val current = parseTags(tagsText).map { it.lowercase() }.toSet()
            val suggestions = allTags.filter { it.lowercase() !in current }
            if (suggestions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { t ->
                        FilterChip(selected = false, onClick = {
                            tagsText = (parseTags(tagsText) + t).joinToString(", ")
                        }, label = { Text("+ $t") })
                    }
                }
            }

            HorizontalDivider()
            Text("Файли (${attachments.size})", style = MaterialTheme.typography.titleSmall)
            // Головна кнопка — будь-які файли: PDF, Word, Excel, PowerPoint, фото, текст тощо.
            Button(
                onClick = {
                    app.lock.expectingExternalActivity = true
                    pickFiles.launch(arrayOf("*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp))
                Text("Додати файли (PDF, Word, Excel…)")
            }
            OutlinedButton(
                onClick = {
                    startScan()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.DocumentScanner, null); Spacer(Modifier.width(8.dp))
                Text("Сканувати документ")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val (file, uri) = app.exporter.cameraUri()
                    cameraFile = file
                    app.lock.expectingExternalActivity = true
                    takePhoto.launch(uri)
                }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(4.dp)); Text("Камера") }
                OutlinedButton(onClick = {
                    app.lock.expectingExternalActivity = true
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(4.dp)); Text("Галерея") }
            }
            Text(
                "Можна вибрати кілька файлів одразу. Також файл можна надіслати сюди з будь-якого додатку через «Поділитися» → «Мої документи».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (attachments.size > 1) Text(
                "Порядок файлів — це порядок сторінок (стрілками ↑↓). Наприклад, усі сторінки паспорта можна зберегти в одному документі.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            attachments.forEachIndexed { i, a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (attachments.size > 1) Text("${i + 1}.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(22.dp))
                    FileBadge(a, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PdfConverter.formatSize(a.size), style = MaterialTheme.typography.bodySmall)
                    }
                    if (attachments.size > 1) {
                        IconButton(onClick = { attachments.add(i - 1, attachments.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Вище") }
                        IconButton(onClick = { attachments.add(i + 1, attachments.removeAt(i)) }, enabled = i < attachments.size - 1) { Icon(Icons.Outlined.KeyboardArrowDown, "Нижче") }
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
