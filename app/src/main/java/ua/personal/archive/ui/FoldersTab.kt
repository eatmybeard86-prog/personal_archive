package ua.personal.archive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.personal.archive.data.Category
import ua.personal.archive.data.Document

/** Вкладка «Папки»: усі документи з пошуком і фільтром за розділом. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersTab(category: Category?, onCategory: (Category?) -> Unit, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    val docs by app.repo.documents.collectAsStateWithLifecycle()
    val loaded by app.repo.loaded.collectAsStateWithLifecycle()
    val loadError by app.repo.loadError.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(docs, query, category) {
        val q = query.trim().lowercase()
        docs.filter { d ->
            (category == null || d.category == category) &&
                (q.isEmpty() || d.title.lowercase().contains(q) || d.number.lowercase().contains(q) ||
                    d.note.lowercase().contains(q) || d.attachments.any { it.name.lowercase().contains(q) })
        }.sortedByDescending { it.updatedAt }
    }
    val expiring = remember(docs) { docs.count { it.expiresAt != null && daysLeft(it.expiresAt) <= 60 } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Папки") }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Пошук за назвою, номером, нотатками") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = category == null, onClick = { onCategory(null) }, label = { Text("Усі (${docs.size})") })
                Category.entries.forEach { c ->
                    val n = docs.count { it.category == c }
                    FilterChip(
                        selected = category == c,
                        onClick = { onCategory(if (category == c) null else c) },
                        label = { Text(if (n > 0) "${c.title} ($n)" else c.title) },
                        leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp)) },
                    )
                }
            }
            if (expiring > 0 && category == null && query.isEmpty()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Строк дії спливає або сплив: $expiring",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (loadError != null || !loaded) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (loadError != null) Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    else CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (docs.isEmpty()) "Тут поки порожньо.\nНатисніть «Додати», щоб зберегти перший документ: сфотографуйте його або виберіть файл."
                        else "Нічого не знайдено",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { d -> DocumentCard(d) { onOpen(d.id) } }
                }
            }
        }
    }
}

@Composable
fun DocumentCard(doc: Document, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, cardBorder()),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileBadge(doc.attachments.firstOrNull())
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val size = doc.attachments.sumOf { it.size }
                val sub = listOfNotNull(
                    doc.category.title,
                    if (size > 0) ua.personal.archive.util.PdfConverter.formatSize(size) else null,
                    relativeDate(doc.updatedAt),
                ).joinToString("  •  ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                doc.expiresAt?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        expiryLabel(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daysLeft(it) <= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (doc.attachments.isNotEmpty()) {
                Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${doc.attachments.size}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
