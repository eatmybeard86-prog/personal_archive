package ua.personal.archive.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.personal.archive.security.AccessLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Журнал доступу: коли відкривали додаток і хто помилився з паролем (з фото, якщо увімкнено). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessLogScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val log = app.accessLog
    val all by log.entries.collectAsStateWithLifecycle()
    val decoy by app.lock.decoy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var big by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) { log.load() }
    // У «тривожному» сховищі вхід тривожним PIN-ом виглядає як звичайний вхід.
    val entries = if (decoy) all.map { if (it.kind == AccessLog.Kind.DURESS) it.copy(kind = AccessLog.Kind.UNLOCK) else it } else all
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал доступу") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
                actions = { if (entries.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Очистити") } },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Записів поки немає") }
        } else LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val wrong = e.kind == AccessLog.Kind.WRONG
                        Icon(
                            if (wrong) Icons.Outlined.Warning else Icons.Outlined.CheckCircle, null,
                            tint = if (wrong) MaterialTheme.colorScheme.error else SafeGreen,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.kind.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                fmt.format(Date(e.at)) + if (e.method.isNotBlank()) " · ${e.method}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (e.photo) {
                            val bmp by produceState<Bitmap?>(null, e.id) { value = log.photo(e) }
                            bmp?.let { b ->
                                Image(
                                    b.asImageBitmap(), "Фото",
                                    Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).clickable { big = b },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    big?.let { b ->
        AlertDialog(
            onDismissRequest = { big = null },
            text = { Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) },
            confirmButton = { TextButton(onClick = { big = null }) { Text("Закрити") } },
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        text = { Text("Очистити журнал доступу разом із фото?") },
        confirmButton = { TextButton(onClick = { confirmClear = false; scope.launch { log.clear() } }) { Text("Очистити") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Скасувати") } },
    )
}
