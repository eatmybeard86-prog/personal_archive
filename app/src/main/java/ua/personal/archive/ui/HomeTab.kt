package ua.personal.archive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Add
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
import ua.personal.archive.data.Section

/** Головна: шапка з гербом-щитом, статус захисту, плитки розділів, останні документи. */
@Composable
fun HomeTab(
    onOpen: (String) -> Unit,
    onCategory: (Section) -> Unit,
    onManageSections: () -> Unit,
    onAll: () -> Unit,
    onSecurity: () -> Unit,
    onSettings: () -> Unit,
) {
    val app = LocalApp.current
    val docs by app.repo.documents.collectAsStateWithLifecycle()
    val loadError by app.repo.loadError.collectAsStateWithLifecycle()
    val recent = remember(docs) { docs.sortedByDescending { it.updatedAt }.take(6) }
    val favorites = remember(docs) { docs.filter { it.favorite }.sortedBy { it.title.lowercase() } }
    val expiring = remember(docs) { docs.count { it.expiresAt != null && daysLeft(it.expiresAt) <= 60 } }
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().background(cs.background).verticalScroll(rememberScrollState()).padding(bottom = 96.dp),
    ) {
        // ── Шапка ─────────────────────────────────────────────
        // Висота шапки визначається її вмістом, тож великий шрифт у системі нічого не ламає.
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0E2350), cs.background))),
        ) {
            GhostShield(Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 36.dp).size(110.dp, 128.dp).alpha(0.7f))
            FlagWave(Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.6f).height(70.dp).alpha(0.75f))
            Row(
                Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 28.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShieldLogo(56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Мої документи", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Надійне зберігання важливих файлів", style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB4C3DE), maxLines = 2,
                    )
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
                subtitle = loadError ?: if (expiring > 0) "Спливає строк: $expiring" else "Шифрування увімкнено",
                onClick = onSecurity,
            )

            // ── Обране ───────────────────────────────────────
            if (favorites.isNotEmpty()) {
                Text("★ Обране", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                favorites.forEach { d -> DocumentCard(d) { onOpen(d.id) } }
            }

            // ── Розділи ──────────────────────────────────────
            val sections = rememberSections()
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Розділи", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onManageSections) { Text("Змінити") }
            }
            Text(
                "Утримуйте плитку пальцем і перетягніть, щоб змінити порядок",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            SectionGrid(sections, docs, onCategory, onManageSections)

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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldBadge(32.dp, color) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Плитки розділів, які можна перетягувати пальцем: довге натискання — і плитка «піднімається»,
 * її можна перенести на місце іншої. Новий порядок зберігається після відпускання.
 */
@Composable
private fun SectionGrid(
    sections: List<Section>,
    docs: List<ua.personal.archive.data.Document>,
    onCategory: (Section) -> Unit,
    onManageSections: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Локальний порядок під час перетягування; синхронізується зі сховищем, коли нічого не тягнуть.
    var order by remember { mutableStateOf(sections) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragCurrent by remember { mutableIntStateOf(-1) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    if (dragFrom == null && order != sections) order = sections

    // При великому системному шрифті — дві плитки в ряд, щоб назви вміщались.
    val columns = if (LocalDensity.current.fontScale > 1.15f) 2 else 3
    val slots: List<Section?> = order + null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slots.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEachIndexed { colIndex, c ->
                    val slot = rowIndex * columns + colIndex
                    if (c == null) {
                        AddSectionTile(Modifier.weight(1f), onManageSections)
                        return@forEachIndexed
                    }
                    val dragged = dragFrom != null && slot == dragCurrent
                    val tileModifier = Modifier.weight(1f)
                        .onGloballyPositioned { slotBounds[slot] = it.boundsInRoot() }
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer {
                            if (dragged) {
                                val from = slotBounds[dragFrom ?: slot]
                                val here = slotBounds[slot]
                                if (from != null && here != null) {
                                    translationX = from.left + dragTotal.x - here.left
                                    translationY = from.top + dragTotal.y - here.top
                                }
                                scaleX = 1.07f; scaleY = 1.07f
                                shadowElevation = 16f
                            }
                        }
                        .pointerInput(slot) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragFrom = slot; dragCurrent = slot; dragTotal = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragTotal += amount
                                    val from = slotBounds[slot] ?: return@detectDragGesturesAfterLongPress
                                    val finger = from.center + dragTotal
                                    val target = slotBounds.entries.firstOrNull { (i, r) -> i < order.size && r.contains(finger) }?.key
                                    if (target != null && target != dragCurrent) {
                                        order = order.toMutableList().apply { add(target, removeAt(dragCurrent)) }
                                        dragCurrent = target
                                    }
                                },
                                onDragEnd = {
                                    val changed = order.map { it.id } != sections.map { it.id }
                                    val ids = order.map { it.id }
                                    dragFrom = null; dragCurrent = -1; dragTotal = Offset.Zero
                                    if (changed) scope.launch { app.repo.reorderSections(ids) }
                                },
                                onDragCancel = {
                                    dragFrom = null; dragCurrent = -1; dragTotal = Offset.Zero
                                    order = sections
                                },
                            )
                        }
                    CategoryTile(c, docs.count { it.sectionId == c.id }, tileModifier) { onCategory(c) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(c: Section, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val tint = c.tint
    Column(
        modifier.fillMaxHeight().heightIn(min = 92.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainer)))
            .border(1.dp, cardBorder(), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StyledIcon(c.iconVector, tint, 50.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            c.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text("($count)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddSectionTile(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxHeight().heightIn(min = 92.dp).clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text("Новий розділ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, textAlign = TextAlign.Center)
    }
}
