package ua.personal.archive.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.personal.archive.data.BackupManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val backup = remember { BackupManager(app, app.repo, app.passwords) }
    val docs by app.repo.documents.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // ── створення ──
    var allSections by remember { mutableStateOf(true) }
    val sections = rememberSections()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(-1f) }
    var message by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    // ── відновлення ──
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var manifest by remember { mutableStateOf<BackupManager.Manifest?>(null) }
    var restoreSelected by remember { mutableStateOf(setOf<String>()) }

    val chosen: Set<String>? = if (allSections) null else selected
    val count = docs.count { chosen == null || it.sectionId in chosen }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pwd = p1.toCharArray()
        p1 = ""; p2 = ""
        progress = 0f
        scope.launch {
            message = try {
                val n = backup.export(uri, pwd, chosen) { progress = it }
                "Резервну копію створено: документів — $n.\n\n" +
                    "На Windows відкрийте файл у 7-Zip або WinRAR і введіть пароль (вбудований провідник Windows такі архіви не відкриває). " +
                    "Усередині буде «Архів.zip» з папками за розділами.\n\n" +
                    "Щоб перенести документи на інший телефон, відкрийте цей файл у додатку «Мої документи» через «Відновити» — знадобиться той самий пароль."
            } catch (e: Exception) {
                "Не вдалося створити копію: ${e.message}"
            } finally {
                progress = -1f
            }
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; restorePassword = ""; manifest = null }
    }

    fun startCreate() {
        formError = when {
            !allSections && selected.isEmpty() -> "Виберіть хоча б один розділ"
            count == 0 -> "У вибраних розділах немає документів"
            p1.length < BackupManager.MIN_PASSWORD -> "Пароль копії — мінімум ${BackupManager.MIN_PASSWORD} символів"
            p1 != p2 -> "Паролі не збігаються"
            else -> null
        }
        if (formError == null) {
            app.lock.expectingExternalActivity = true
            createFile.launch(backup.suggestedName(chosen))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Резервні копії") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (progress >= 0f) {
                Text("Зачекайте…")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Section("Створити копію") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = allSections, onClick = { allSections = true }, label = { Text("Уся база (${docs.size})") })
                    FilterChip(selected = !allSections, onClick = { allSections = false }, label = { Text("Вибрані розділи") })
                }
                if (!allSections) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sections.forEach { c ->
                            val n = docs.count { it.sectionId == c.id }
                            FilterChip(
                                selected = c.id in selected,
                                onClick = { selected = if (c.id in selected) selected - c.id else selected + c.id },
                                label = { Text("${c.title} ($n)") },
                                leadingIcon = { Icon(c.iconVector, null, Modifier.size(18.dp), tint = c.tint) },
                            )
                        }
                    }
                }
                Text(
                    "Придумайте пароль для цієї копії. Без нього архів не відкрити ні на Windows, ні на іншому телефоні. Відновити забутий пароль неможливо.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordField(p1, { p1 = it }, "Пароль копії", ImeAction.Next) {}
                PasswordField(p2, { p2 = it }, "Повторіть пароль", ImeAction.Done) { startCreate() }
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = ::startCreate, enabled = progress < 0f, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Зберегти копію ($count док.)")
                }
            }

            Section("Відновити з копії") {
                Text(
                    "Виберіть файл резервної копії (наприклад, отриманий від іншої людини) і введіть пароль, який встановив автор копії. " +
                        "Документи, які вже є в додатку, буде пропущено.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    app.lock.expectingExternalActivity = true
                    openFile.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }, enabled = progress < 0f) {
                    Icon(Icons.Filled.Upload, null); Spacer(Modifier.width(8.dp)); Text("Вибрати файл копії")
                }
            }
        }
    }

    // Крок 1: пароль до вибраної копії
    if (restoreUri != null && manifest == null) {
        var err by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        fun check() {
            val uri = restoreUri ?: return
            checking = true
            scope.launch {
                try {
                    val m = backup.inspect(uri, restorePassword.toCharArray())
                    manifest = m
                    restoreSelected = m.documents.map { it.sectionId }.toSet()
                } catch (e: BackupManager.WrongPasswordException) {
                    err = "Неправильний пароль"
                } catch (e: Exception) {
                    err = e.message ?: "Не вдалося прочитати файл"
                } finally {
                    checking = false
                }
            }
        }
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Пароль копії") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PasswordField(restorePassword, { restorePassword = it; err = null }, "Пароль", ImeAction.Done) { check() }
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = ::check, enabled = !checking) { Text("Відкрити") } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Скасувати") } },
        )
    }

    // Крок 2: що імпортувати
    manifest?.let { m ->
        val byCat = m.documents.groupBy { it.sectionId }
        fun titleOf(id: String) = m.sections.firstOrNull { it.id == id }?.title ?: app.repo.section(id).title
        AlertDialog(
            onDismissRequest = { manifest = null; restoreUri = null; restorePassword = "" },
            title = { Text("Відновити документи") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Створено ${formatDate(m.createdAt)}. Документів у копії: ${m.documents.size}.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        byCat.keys.forEach { c ->
                            FilterChip(
                                selected = c in restoreSelected,
                                onClick = { restoreSelected = if (c in restoreSelected) restoreSelected - c else restoreSelected + c },
                                label = { Text("${titleOf(c)} (${byCat.getValue(c).size})") },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = restoreSelected.isNotEmpty(), onClick = {
                    val uri = restoreUri!!
                    val pwd = restorePassword.toCharArray()
                    val cats = restoreSelected
                    manifest = null; restoreUri = null; restorePassword = ""
                    progress = 0f
                    scope.launch {
                        message = try {
                            val r = backup.restore(uri, pwd, cats) { progress = it }
                            "Відновлено документів: ${r.imported} (файлів: ${r.files})." +
                                (if (r.passwords > 0) "\nВідновлено паролів: ${r.passwords}." else "") +
                                if (r.skipped > 0) "\nПропущено (вже є в додатку): ${r.skipped}." else ""
                        } catch (e: Exception) {
                            "Не вдалося відновити: ${e.message}"
                        } finally {
                            progress = -1f
                        }
                    }
                }) { Text("Відновити") }
            },
            dismissButton = { TextButton(onClick = { manifest = null; restoreUri = null; restorePassword = "" }) { Text("Скасувати") } },
        )
    }

    message?.let {
        AlertDialog(onDismissRequest = { message = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
}
