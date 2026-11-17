package ua.personal.archive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.personal.archive.data.Category

/** Головна: шапка з гербом-щитом, статус захисту, плитки розділів, останні документи. */
@Composable
fun HomeTab(
    onOpen: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onAll: () -> Unit,
    onSecurity: () -> Unit,
    onSettings: () -> Unit,
) {
    val app = LocalApp.current
    val docs by app.repo.documents.collectAsStateWithLifecycle()
    val loadError by app.repo.loadError.collectAsStateWithLifecycle()
    val recent = remember(docs) { docs.sortedByDescending { it.updatedAt }.take(6) }
    val expiring = remember(docs) { docs.count { it.expiresAt != null && daysLeft(it.expiresAt) <= 60 } }
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().background(cs.background).verticalScroll(rememberScrollState()).padding(bottom = 96.dp),
    ) {
        // ── Шапка ─────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().height(230.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF0E2350), cs.background))),
        ) {
            GhostShield(Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 40.dp).size(150.dp, 175.dp))
            FlagWave(Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.7f).height(130.dp))
            Row(
                Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 24.dp).fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                ShieldLogo(72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text("Мої документи", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("Надійне зберігання\nважливих файлів", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFB4C3DE))
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                ) { Icon(Icons.Filled.Settings, "Налаштування", tint = Color(0xFFDCE6F7)) }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Статус захисту ───────────────────────────────
            StatusCard(
                ok = loadError == null,
                title = if (loadError == null) "Ваші документи захищено" else "Сховище недоступне",
                subtitle = loadError ?: if (expiring > 0) "Шифрування увімкнено · спливає строк: $expiring" else "Шифрування AES-256 увімкнено",
                onClick = onSecurity,
            )

            // ── Розділи ──────────────────────────────────────
            Category.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        CategoryTile(c, docs.count { it.category == c }, Modifier.weight(1f)) { onCategory(c) }
                    }
                }
            }

            // ── Останні документи ────────────────────────────
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Останні документи", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onAll) {
                    Text("Усі")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
            if (recent.isEmpty()) {
                Text(
                    "Поки що порожньо. Натисніть «+», щоб сфотографувати документ або додати файл.",
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            recent.forEach { d -> DocumentCard(d) { onOpen(d.id) } }
        }
    }
}

@Composable
private fun StatusCard(ok: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    val color = if (ok) SafeGreen else MaterialTheme.colorScheme.error
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.10f))))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.45f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldBadge(40.dp, color) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryTile(c: Category, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val tint = Color(c.color)
    Column(
        modifier.aspectRatio(1.15f).clip(shape)
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer)))
            .border(1.dp, cardBorder(), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(c.icon, null, tint = tint, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(c.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("($count)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
