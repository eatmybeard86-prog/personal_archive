package ua.personal.archive.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.personal.archive.data.Attachment
import ua.personal.archive.util.PdfConverter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

val Gold = Color(0xFFFFC928)
val UaBlue = Color(0xFF0057B7)
val UaYellow = Color(0xFFFFD700)
val SafeGreen = Color(0xFF22C55E)

fun shieldPath(size: Size): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(w * 0.5f, 0f)
        lineTo(w, h * 0.17f)
        lineTo(w, h * 0.48f)
        cubicTo(w, h * 0.76f, w * 0.76f, h * 0.92f, w * 0.5f, h)
        cubicTo(w * 0.24f, h * 0.92f, 0f, h * 0.76f, 0f, h * 0.48f)
        lineTo(0f, h * 0.17f)
        close()
    }
}

/** Логотип: синій щит із золотою облямівкою та замком. */
@Composable
fun ShieldLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size, size * 1.15f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val path = shieldPath(this.size)
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFF2E6BD8), Color(0xFF0B2A66))))
            drawPath(
                path,
                Brush.linearGradient(listOf(Color(0xFFFFE27A), Gold, Color(0xFFC98A00))),
                style = Stroke(width = this.size.width * 0.09f),
            )
        }
        Icon(Icons.Filled.Lock, null, tint = Gold, modifier = Modifier.size(size * 0.48f))
    }
}

/** Малий щит-позначка (наприклад, зелений «захищено»). */
@Composable
fun ShieldBadge(size: Dp, color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(size, size * 1.15f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val path = shieldPath(this.size)
            drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.6f))))
            drawPath(path, Color.White.copy(alpha = 0.25f), style = Stroke(width = this.size.width * 0.05f))
        }
        content()
    }
}

/** Синьо-жовта хвиля прапора для шапки. */
@Composable
fun FlagWave(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun band(top: (Float) -> Float, bottom: (Float) -> Float): Path = Path().apply {
            val steps = 40
            moveTo(0f, top(0f))
            for (i in 1..steps) { val x = w * i / steps; lineTo(x, top(x)) }
            for (i in steps downTo 0) { val x = w * i / steps; lineTo(x, bottom(x)) }
            close()
        }
        val thick = h * 0.22f
        fun center(x: Float): Float {
            val t = x / w
            return h * (0.95f - 0.75f * t) + h * 0.07f * sin(t * 2.2f * PI.toFloat())
        }
        drawPath(
            band({ center(it) - thick * (0.3f + 0.7f * it / w) }, { center(it) }),
            Brush.horizontalGradient(listOf(UaBlue.copy(alpha = 0f), UaBlue.copy(alpha = 0.85f)), startX = 0f, endX = w * 0.6f),
        )
        drawPath(
            band({ center(it) }, { center(it) + thick * (0.3f + 0.7f * it / w) }),
            Brush.horizontalGradient(listOf(UaYellow.copy(alpha = 0f), UaYellow.copy(alpha = 0.9f)), startX = 0f, endX = w * 0.6f),
        )
    }
}

/** Велика напівпрозора декоративна емблема-щит у шапці. */
@Composable
fun GhostShield(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val path = shieldPath(size)
            drawPath(path, Brush.verticalGradient(listOf(Color(0x332F80FF), Color(0x0A2F80FF))))
            drawPath(path, Color(0x552F80FF), style = Stroke(width = size.width * 0.03f))
            // «цифрові» крапки
            val step = size.width / 9
            for (i in 1..8) for (j in 1..3) {
                drawCircle(Color(0x332F80FF), radius = 2.2f, center = Offset(step * i, step * j * 0.6f))
            }
        }
        Icon(Icons.Filled.Lock, null, tint = Color(0x662F80FF), modifier = Modifier.fillMaxSize(0.42f))
    }
}

/** Мініатюри зображень тримаємо лише в пам'яті (на диск не пишемо). */
private val thumbCache = LruCache<String, Bitmap>(40)

fun clearThumbnails() = thumbCache.evictAll()

/** Значок файлу: мініатюра для фото, кольорова плашка з типом для решти. */
@Composable
fun FileBadge(a: Attachment?, size: Dp = 48.dp) {
    val app = LocalApp.current
    val isImage = a != null && PdfConverter.kindOf(a) == PdfConverter.Kind.IMAGE
    val thumb by produceState(a?.let { thumbCache.get(it.id) }, a?.id) {
        if (a == null || !isImage || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { PdfConverter.decodeBitmap(app.repo.readAttachment(a), maxSide = 256) }.getOrNull()
        }?.also { thumbCache.put(a.id, it) }
    }
    val t = thumb
    if (t != null) {
        Image(t.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)), contentScale = ContentScale.Crop)
    } else {
        val type = fileType(a)
        StyledIcon(type.vector, type.color, size, label = type.label)
    }
}

/** Значок типу файлу (з набору іконок): символ або літера та фірмовий колір. */
data class FileType(val vector: ImageVector?, val label: String?, val color: Color)

fun fileType(a: Attachment?): FileType {
    if (a == null) return FileType(Icons.Filled.MoreHoriz, null, Color(0xFF22B8CF))
    val ext = a.name.substringAfterLast('.', "").lowercase()
    val mime = a.mime.lowercase()
    return when {
        ext in setOf("xls", "xlsx", "ods", "csv") -> FileType(null, "X", Color(0xFF1FA35B))
        ext in setOf("ppt", "pptx", "odp") -> FileType(null, "P", Color(0xFFF97316))
        ext in setOf("zip", "rar", "7z", "tar", "gz") -> FileType(Icons.Filled.FolderZip, null, Color(0xFF0EA5E9))
        mime.startsWith("video/") || ext in setOf("mp4", "mov", "avi", "mkv", "3gp", "webm") -> FileType(Icons.Filled.PlayArrow, null, Color(0xFF6366F1))
        mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "wav", "ogg", "aac", "flac") -> FileType(Icons.Filled.MusicNote, null, Color(0xFFEC4899))
        else -> when (PdfConverter.kindOf(a)) {
            PdfConverter.Kind.PDF -> FileType(Icons.Filled.PictureAsPdf, null, Color(0xFFE5322D))
            PdfConverter.Kind.DOCX, PdfConverter.Kind.DOC, PdfConverter.Kind.RTF, PdfConverter.Kind.ODT -> FileType(null, "W", Color(0xFF2B6FF3))
            PdfConverter.Kind.XLSX -> FileType(null, "X", Color(0xFF1FA35B))
            PdfConverter.Kind.PPTX -> FileType(null, "P", Color(0xFFF97316))
            PdfConverter.Kind.IMAGE -> FileType(Icons.Filled.Image, null, Color(0xFF8B5CF6))
            PdfConverter.Kind.TEXT, PdfConverter.Kind.HTML -> FileType(Icons.Filled.Description, null, Color(0xFF94A3B8))
            PdfConverter.Kind.OTHER -> FileType(Icons.Filled.MoreHoriz, null, Color(0xFF22B8CF))
        }
    }
}

/** «Сьогодні, 18:45», «Вчора, 21:17», «14 лист. 2026». */
fun relativeDate(ms: Long): String {
    val uk = Locale("uk", "UA")
    val time = SimpleDateFormat("HH:mm", uk).format(Date(ms))
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(then, now)) return "Сьогодні, $time"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(then, now)) return "Вчора, $time"
    return SimpleDateFormat("d MMM yyyy", uk).format(Date(ms))
}

@Composable
fun cardBorder() = MaterialTheme.colorScheme.outlineVariant
