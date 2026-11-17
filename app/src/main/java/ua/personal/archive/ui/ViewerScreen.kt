package ua.personal.archive.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import ua.personal.archive.ArchiveApp
import ua.personal.archive.data.Attachment
import ua.personal.archive.reader.Document
import ua.personal.archive.reader.InlineImageItem
import ua.personal.archive.reader.PageItem
import ua.personal.archive.reader.ReaderLoader
import ua.personal.archive.reader.ReaderTheme
import ua.personal.archive.reader.TextItem
import ua.personal.archive.util.Exporter
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
private val renderDispatcher = Dispatchers.IO.limitedParallelism(1)

/**
 * Вбудована читалка (на основі doc_reader): PDF, фото, Word (DOCX/DOC), RTF, Excel, ODT, текст —
 * усе відкривається прямо в додатку, без сторонніх програм.
 * Розшифрована копія живе лише в тимчасовій теці, поки відкрито перегляд.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(docId: String, attachmentId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val doc = remember(docId) { app.repo.find(docId) }
    val attachment = doc?.attachments?.firstOrNull { it.id == attachmentId }
    var error by remember { mutableStateOf<String?>(null) }
    val themeName by app.settings.readerTheme.collectAsStateWithLifecycle()
    val theme = ReaderTheme.byName(themeName)
    var themeMenu by remember { mutableStateOf(false) }
    var textScale by remember { mutableFloatStateOf(1f) }

    val reader by produceState<Document?>(null, attachment) {
        if (attachment == null) return@produceState
        value = try {
            open(app, attachment)
        } catch (e: Throwable) {
            error = e.message ?: "Не вдалося відкрити файл"
            null
        }
    }
    DisposableEffect(reader) { onDispose { reader?.close() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(attachment?.name ?: "Перегляд", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
                actions = {
                    if (reader?.paged == false) {
                        IconButton(onClick = { textScale = (textScale / 1.15f).coerceAtLeast(0.6f) }) { Icon(Icons.Outlined.TextDecrease, "Менший шрифт") }
                        IconButton(onClick = { textScale = (textScale * 1.15f).coerceAtMost(3f) }) { Icon(Icons.Outlined.TextIncrease, "Більший шрифт") }
                    }
                    Box {
                        IconButton(onClick = { themeMenu = true }) { Icon(Icons.Outlined.Palette, "Колір сторінки") }
                        DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                            ReaderTheme.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.title) },
                                    leadingIcon = { RadioButton(selected = t == theme, onClick = null) },
                                    onClick = { app.settings.setReaderTheme(t.name); themeMenu = false },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize().background(Color(theme.outer)),
            contentAlignment = Alignment.Center,
        ) {
            val r = reader
            when {
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                attachment == null -> Text("Файл не знайдено")
                r == null -> CircularProgressIndicator()
                r.paged -> Pages(r, theme)
                else -> TextPages(r, theme, textScale) { textScale = it }
            }
        }
    }
}

private suspend fun open(app: ArchiveApp, a: Attachment): Document = withContext(Dispatchers.IO) {
    val dir = File(app.exporter.workDir, "view-" + UUID.randomUUID()).apply { mkdirs() }
    val file = File(dir, Exporter.safeName(a.name))
    try {
        app.repo.openAttachment(a).use { input -> file.outputStream().use { input.copyTo(it) } }
    } catch (e: Throwable) {
        dir.deleteRecursively()
        throw e
    }
    ReaderLoader.open(file, a.name, a.mime, dir)
}

private fun ReaderTheme.composeFilter(): ColorFilter? = matrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

/** PDF і фото: сторінки гортаються вбік, кожну можна збільшити щипком або подвійним дотиком. */
@Composable
private fun Pages(doc: Document, theme: ReaderTheme) {
    val pages = remember(doc) { doc.items.filterIsInstance<PageItem>() }
    val pager = rememberPagerState { pages.size }
    val zoomStates = remember(doc) { List(pages.size) { ZoomState() } }
    LaunchedEffect(pager.currentPage) { zoomStates.forEachIndexed { i, z -> if (i != pager.currentPage) z.reset() } }
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val filter = theme.composeFilter()
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            userScrollEnabled = !zoomStates[pager.currentPage].isZoomed,
            beyondViewportPageCount = 1,
        ) { index ->
            val page = pages[index]
            // Рендер із запасом (2.5× ширини екрана), щоб текст лишався чітким при збільшенні.
            val bmp by produceState<Bitmap?>(null, doc, index) {
                value = withContext(renderDispatcher) {
                    runCatching { doc.renderPage(page, (widthPx * 2.5f).toInt().coerceAtMost(3000)) }.getOrNull()
                }
            }
            Box(Modifier.fillMaxSize().zoomable(zoomStates[index]), contentAlignment = Alignment.Center) {
                val b = bmp
                if (b == null) CircularProgressIndicator()
                else Image(
                    b.asImageBitmap(), null,
                    Modifier.fillMaxWidth().aspectRatio(page.width.toFloat() / page.height).padding(4.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (doc.isPhoto) null else filter,
                )
            }
        }
        Text(
            if (pages.size > 1) "Сторінка ${pager.currentPage + 1} з ${pages.size} · гортайте вбік, збільшуйте двома пальцями"
            else "Збільшуйте двома пальцями або подвійним дотиком",
            style = MaterialTheme.typography.bodySmall,
            color = Color(theme.barText),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp),
        )
    }
}

/** Word, RTF, таблиці, текст: абзаци з форматуванням; щипок змінює розмір шрифту. */
@Composable
private fun TextPages(doc: Document, theme: ReaderTheme, scale: Float, onScale: (Float) -> Unit) {
    val maxWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }.toInt()
    val filter = theme.composeFilter()
    var current by remember { mutableFloatStateOf(scale) }
    current = scale
    LazyColumn(
        Modifier.fillMaxSize().background(Color(theme.page)).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1) {
                        val z = event.calculateZoom()
                        if (z != 1f) onScale((current * z).coerceIn(0.6f, 3f))
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        itemsIndexed(doc.items) { _, item ->
            when (item) {
                is TextItem -> AndroidView(
                    factory = { ctx -> TextView(ctx).apply { setLineSpacing(0f, 1.25f); setTextIsSelectable(true) } },
                    update = { v ->
                        val density = v.resources.displayMetrics.density
                        val padH = (18 * density).toInt()
                        val padV = ((if (item.heading > 0) 10 else 4) * density).toInt()
                        v.setPadding(padH, padV, padH, padV)
                        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale * if (item.tableRow) 0.9f else 1f)
                        v.typeface = if (item.tableRow) Typeface.MONOSPACE else Typeface.SERIF
                        v.setTextColor(theme.ink)
                        v.gravity = when (item.align) {
                            TextItem.ALIGN_CENTER -> Gravity.CENTER_HORIZONTAL
                            TextItem.ALIGN_END -> Gravity.END
                            else -> Gravity.START
                        }
                        v.justificationMode =
                            if (item.align == TextItem.ALIGN_JUSTIFY) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE
                        v.text = item.text
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                is InlineImageItem -> {
                    val bmp by produceState<Bitmap?>(null, doc, item.key) {
                        value = withContext(renderDispatcher) { runCatching { doc.loadInlineImage(item.key, maxWidth) }.getOrNull() }
                    }
                    bmp?.let {
                        Image(
                            it.asImageBitmap(), null,
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            contentScale = ContentScale.FillWidth, colorFilter = filter,
                        )
                    }
                }
                is PageItem -> Unit
            }
        }
    }
}
