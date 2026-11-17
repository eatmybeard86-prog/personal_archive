package ua.personal.archive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Розбирає рядок «паспорт, сім'я, #авто» у список тегів без повторів. */
fun parseTags(text: String): List<String> =
    text.split(',', ';', '\n').map { it.trim().trimStart('#').trim() }.filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(tags: List<String>, onClick: ((String) -> Unit)? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { t ->
            AssistChip(
                onClick = { onClick?.invoke(t) },
                label = { Text(t) },
                leadingIcon = { Icon(Icons.Outlined.Sell, null, Modifier.size(16.dp)) },
            )
        }
    }
}
