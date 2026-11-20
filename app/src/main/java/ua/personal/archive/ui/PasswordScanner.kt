package ua.personal.archive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import ua.personal.archive.passwords.ScanFields
import ua.personal.archive.passwords.ScanParser
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private enum class Target(val title: String) { LOGIN("Логін"), PASSWORD("Пароль"), SITE("Сайт"), TITLE("Назва") }

/**
 * Сканер логіна й пароля камерою: наводите на папірець або екран іншого пристрою —
 * у кадрі розпізнаються текст і QR-коди (усе на телефоні, без інтернету).
 * Підписані рядки («Логін: …», «Пароль: …») і e-mail підставляються самі,
 * будь-який інший рядок можна торкнутися й вибрати, куди його вставити.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasswordScanner(onDone: (ScanFields) -> Unit, onClose: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it; denied = !it
    }
    LaunchedEffect(Unit) {
        if (!granted) { app.lock.expectingExternalActivity = true; askCamera.launch(Manifest.permission.CAMERA) }
    }
    BackHandler(onBack = onClose)

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var qr by remember { mutableStateOf<String?>(null) }
    var paused by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var fields by remember { mutableStateOf(ScanFields()) }
    var choose by remember { mutableStateOf<String?>(null) }
    // Поки вибирають, куди вставити рядок, кадр не змінюється.
    val frozen = remember { AtomicBoolean(false) }
    frozen.set(paused || choose != null)

    fun onFrame(code: String?, found: List<String>) {
        if (!frozen.get()) {
            if (code != null) qr = code
            if (found.isNotEmpty()) {
                lines = found
                // Автоматично заповнюємо лише порожні поля — вибір користувача не перетираємо.
                val g = ScanParser.guess(found)
                fields = fields.copy(
                    login = fields.login.ifEmpty { g.login },
                    password = fields.password.ifEmpty { g.password },
                    site = fields.site.ifEmpty { g.site },
                    title = fields.title.ifEmpty { g.title },
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) CameraPreview(frozen, onCamera = { camera = it }, onFrame = { c, l -> onFrame(c, l) })
        else Text(
            if (denied) "Немає дозволу на камеру. Його можна дати в налаштуваннях Android → Додатки → Мої документи → Дозволи."
            else "Потрібен дозвіл на камеру…",
            color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp),
        )

        // ── верхня панель ──
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val btn = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White)
            IconButton(onClick = onClose, colors = btn) { Icon(Icons.Outlined.Close, "Закрити") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { paused = !paused }, colors = btn) {
                Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (paused) "Продовжити" else "Зупинити кадр")
            }
            if (camera?.cameraInfo?.hasFlashUnit() == true) IconButton(onClick = {
                torch = !torch; camera?.cameraControl?.enableTorch(torch)
            }, colors = btn) { Icon(if (torch) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn, "Ліхтарик") }
        }

        // ── нижня панель з результатом ──
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 460.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ) {
            Column(
                Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                qr?.let { code ->
                    val parsed = remember(code) { ScanParser.parseQr(code) }
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.QrCode2, null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Знайдено QR-код", fontWeight = FontWeight.SemiBold)
                            Text(
                                parsed.title.ifBlank { parsed.login.ifBlank { "текст" } },
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onDone(parsed) }) { Text("Використати") }
                    }
                }
                Text(
                    if (lines.isEmpty()) "Наведіть камеру на логін і пароль — на папері чи екрані іншого пристрою."
                    else "Торкніться рядка, щоб вставити його в потрібне поле:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldRow("Назва", fields.title) { fields = fields.copy(title = "") }
                FieldRow("Логін", fields.login) { fields = fields.copy(login = "") }
                FieldRow("Пароль", fields.password, mono = true) { fields = fields.copy(password = "") }
                FieldRow("Сайт", fields.site) { fields = fields.copy(site = "") }
                if (lines.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEach { l ->
                        AssistChip(onClick = { choose = l }, label = { Text(l, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
                Button(onClick = { onDone(fields) }, enabled = !fields.isEmpty, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
                Text(
                    "Розпізнаний пароль обов'язково перевірте: камера може сплутати схожі символи (0 і O, 1, l та I).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    choose?.let { line ->
        val value = ScanParser.stripLabel(line)
        AlertDialog(
            onDismissRequest = { choose = null },
            title = { Text("Куди вставити?") },
            text = {
                Column {
                    Text(value, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
                    Target.entries.forEach { t ->
                        Text(
                            t.title, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().clickable {
                                fields = when (t) {
                                    Target.LOGIN -> fields.copy(login = value)
                                    Target.PASSWORD -> fields.copy(password = value)
                                    Target.SITE -> fields.copy(site = value, title = fields.title.ifEmpty { ScanParser.titleFor(value) })
                                    Target.TITLE -> fields.copy(title = value)
                                }
                                choose = null
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choose = null }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, mono: Boolean = false, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(
            value.ifEmpty { "—" }, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
        if (value.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Очистити") }
    }
}

/** Живий перегляд задньої камери + розпізнавання тексту й кодів у кожному кадрі. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(frozen: AtomicBoolean, onCamera: (Camera) -> Unit, onFrame: (String?, List<String>) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val frame by rememberUpdatedState(onFrame)

    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(ctx)
        val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val codes = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_PDF417)
                .build(),
        )
        val closed = AtomicBoolean(false)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // Дрібний шрифт на папірці погано читається з 640×480, тож беремо HD.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            if (media == null || frozen.get() || closed.get()) { proxy.close(); return@setAnalyzer }
            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            val t1 = codes.process(image)
            val t2 = text.process(image)
            Tasks.whenAllComplete(t1, t2).addOnCompleteListener(main) {
                proxy.close()
                if (closed.get()) return@addOnCompleteListener
                val code = if (t1.isSuccessful) t1.result.firstNotNullOfOrNull { it.rawValue } else null
                val found = if (t2.isSuccessful) t2.result.textBlocks.flatMap { b -> b.lines.map { it.text.trim() } }.filter { it.isNotEmpty() } else emptyList()
                frame(code, found)
            }
        }
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            if (closed.get()) return@addListener
            runCatching {
                val p = future.get()
                provider = p
                onCamera(p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            }
        }, main)
        onDispose {
            closed.set(true)
            runCatching { provider?.unbind(preview, analysis) }
            analysis.clearAnalyzer()
            executor.shutdown()
            text.close()
            codes.close()
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}
