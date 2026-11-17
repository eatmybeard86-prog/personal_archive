package ua.personal.archive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.personal.archive.data.Section
import java.util.UUID

/** Керування розділами: створити, перейменувати, змінити іконку й колір, упорядкувати, видалити. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val sections = rememberSections()
    val docs by app.repo.documents.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Section?>(null) }
    var deleting by remember { mutableStateOf<Section?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Розділи") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Section(id = UUID.randomUUID().toString(), title = "", icon = "folder", color = SectionColors.first()) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Новий розділ") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sections, key = { _, s -> s.id }) { i, s ->
                val n = docs.count { it.sectionId == s.id }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(s.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(s.iconVector, null, tint = s.tint)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Документів: $n", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { scope.launch { app.repo.moveSection(s.id, -1) } }, enabled = i > 0) {
                            Icon(Icons.Outlined.KeyboardArrowUp, "Вище")
                        }
                        IconButton(onClick = { scope.launch { app.repo.moveSection(s.id, 1) } }, enabled = i < sections.lastIndex) {
                            Icon(Icons.Outlined.KeyboardArrowDown, "Нижче")
                        }
                        IconButton(onClick = { editing = s }) { Icon(Icons.Outlined.Edit, "Змінити") }
                        IconButton(onClick = { deleting = s }, enabled = sections.size > 1) { Icon(Icons.Outlined.Delete, "Видалити") }
                    }
                }
            }
        }
    }

    editing?.let { s ->
        SectionEditor(
            initial = s,
            isNew = sections.none { it.id == s.id },
            onDismiss = { editing = null },
            onSave = { updated -> editing = null; scope.launch { app.repo.saveSection(updated) } },
        )
    }

    deleting?.let { s ->
        val n = docs.count { it.sectionId == s.id }
        val others = sections.filter { it.id != s.id }
        var target by remember(s.id) { mutableStateOf<String?>(others.firstOrNull { it.id == "OTHER" }?.id ?: others.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Видалити розділ «${s.title}»?") },
            text = {
                if (n == 0) Text("Розділ порожній.")
                else Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("У розділі документів: $n. Що з ними зробити?")
                    Spacer(Modifier.size(8.dp))
                    others.forEach { o ->
                        Row(Modifier.fillMaxWidth().clickable { target = o.id }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = target == o.id, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Перенести в «${o.title}»")
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable { target = null }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = target == null, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Видалити разом із документами", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { app.repo.deleteSection(s.id, if (n == 0) null else target) }
                }) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Скасувати") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionEditor(initial: Section, isNew: Boolean, onDismiss: () -> Unit, onSave: (Section) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var hint by remember { mutableStateOf(initial.hint) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новий розділ" else "Змінити розділ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Назва") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hint, { hint = it }, label = { Text("Опис (необов'язково)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Іконка", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionIcons.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(color).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(if (selected) 2.dp else 0.dp, if (selected) Color(color) else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, null, tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Text("Колір", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionColors.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) { if (c == color) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(initial.copy(title = title.trim(), hint = hint.trim(), icon = icon, color = color)) },
            ) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}
