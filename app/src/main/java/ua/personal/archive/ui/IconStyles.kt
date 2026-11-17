package ua.personal.archive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.personal.archive.security.IconStyle
import kotlin.random.Random

@Composable
fun currentIconStyle(): IconStyle {
    val style by LocalApp.current.settings.iconStyle.collectAsStateWithLifecycle()
    return style
}

/** Фон плитки для стилю. [accent] — власний колір розділу / типу файлу. */
private fun tileBrush(style: IconStyle, accent: Color): Brush = when (style) {
    IconStyle.CLASSIC -> Brush.verticalGradient(listOf(Color(0xFF16305E), Color(0xFF0B1A36)))
    IconStyle.NEON -> Brush.verticalGradient(listOf(Color(0xFF0E0B24), Color(0xFF07061A)))
    IconStyle.DARK -> Brush.verticalGradient(listOf(Color(0xFF34363C), Color(0xFF1C1D21)))
    IconStyle.LIGHT -> Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE6EBF3)))
    IconStyle.COLORFUL -> Brush.linearGradient(listOf(lighten(accent, 0.25f), accent, darken(accent, 0.25f)))
    IconStyle.GRADIENT -> Brush.linearGradient(listOf(Color(0xFF5B5BF7), Color(0xFFC026D3), Color(0xFFFB7185)))
    IconStyle.METAL -> Brush.linearGradient(listOf(Color(0xFFE6E8EC), Color(0xFF8E939B), Color(0xFFD1D4D9), Color(0xFF6B7078)))
    IconStyle.CAMO -> Brush.verticalGradient(listOf(Color(0xFF5B6340), Color(0xFF3E452B)))
}

/** Колір/градієнт самого символу. */
private fun glyphBrush(style: IconStyle, accent: Color): Brush = when (style) {
    IconStyle.CLASSIC -> Brush.verticalGradient(listOf(lighten(accent, 0.25f), accent))
    IconStyle.NEON -> Brush.verticalGradient(listOf(lighten(accent, 0.45f), lighten(accent, 0.15f)))
    IconStyle.DARK -> Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFF9CA3AF)))
    IconStyle.LIGHT -> Brush.verticalGradient(listOf(lighten(accent, 0.1f), darken(accent, 0.15f)))
    IconStyle.COLORFUL, IconStyle.GRADIENT -> Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F5F9)))
    IconStyle.METAL -> Brush.verticalGradient(listOf(Color(0xFF4B5058), Color(0xFF1F2328)))
    IconStyle.CAMO -> Brush.verticalGradient(listOf(Color(0xFFE3D9A6), Color(0xFFB5A86A)))
}

private fun borderColor(style: IconStyle, accent: Color): Color = when (style) {
    IconStyle.CLASSIC -> accent.copy(alpha = 0.45f)
    IconStyle.NEON -> lighten(accent, 0.2f)
    IconStyle.DARK -> Color.White.copy(alpha = 0.10f)
    IconStyle.LIGHT -> Color(0xFFD5DCE8)
    IconStyle.COLORFUL, IconStyle.GRADIENT -> Color.White.copy(alpha = 0.25f)
    IconStyle.METAL -> Color.White.copy(alpha = 0.6f)
    IconStyle.CAMO -> Color(0xFF2B301D)
}

fun lighten(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, c.alpha)
fun darken(c: Color, f: Float) = Color(c.red * (1 - f), c.green * (1 - f), c.blue * (1 - f), c.alpha)

/** Камуфляжні плями (фіксований візерунок, щоб не «стрибав» між кадрами). */
@Composable
private fun CamoPattern() {
    Canvas(Modifier.fillMaxSize()) {
        val rnd = Random(7)
        val colors = listOf(Color(0xFF2F3522), Color(0xFF7A7650), Color(0xFF4C5534), Color(0xFF8C8458))
        repeat(14) {
            drawCircle(
                colors[it % colors.size].copy(alpha = 0.85f),
                radius = size.minDimension * (0.12f + rnd.nextFloat() * 0.16f),
                center = Offset(size.width * rnd.nextFloat(), size.height * rnd.nextFloat()),
            )
        }
    }
}

/** Символ, залитий кистю (градієнтом) замість однотонного кольору. */
@Composable
fun BrushIcon(vector: ImageVector, brush: Brush, size: Dp) {
    Icon(
        vector, null, tint = Color.White,
        modifier = Modifier.size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush, blendMode = BlendMode.SrcAtop)
            },
    )
}

/**
 * Іконка-плитка у вибраному стилі. Вміст — векторний символ [vector] або текстова позначка [label]
 * (наприклад, «W» для Word).
 */
@Composable
fun StyledIcon(
    vector: ImageVector?,
    accent: Color,
    size: Dp,
    style: IconStyle = currentIconStyle(),
    label: String? = null,
    caption: String? = null,
) {
    val shape = RoundedCornerShape(size * 0.26f)
    val glow = style == IconStyle.NEON || style == IconStyle.CLASSIC || style == IconStyle.COLORFUL
    Box(
        Modifier.size(size)
            .then(if (glow) Modifier.shadow(size * 0.18f, shape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(shape)
            .background(tileBrush(style, accent))
            .border(if (style == IconStyle.NEON) 1.5.dp else 1.dp, borderColor(style, accent), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (style == IconStyle.CAMO) CamoPattern()
        // Відблиск зверху — «скляний» ефект.
        if (style != IconStyle.CAMO) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = if (style == IconStyle.LIGHT) 0.0f else 0.14f), 0.5f to Color.Transparent),
                ),
            )
        }
        val brush = glyphBrush(style, accent)
        when {
            label != null -> Box(contentAlignment = Alignment.Center) {
                Text(
                    label, fontWeight = FontWeight.Black, fontSize = (size.value * if (label.length <= 1) 0.46f else 0.26f).sp,
                    color = Color.White, maxLines = 1,
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent { drawContent(); drawRect(brush, blendMode = BlendMode.SrcAtop) },
                )
            }
            vector != null -> BrushIcon(vector, brush, size * 0.56f)
        }
        caption?.let {
            Text(
                it, color = Color.White, fontSize = (size.value * 0.15f).sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
            )
        }
    }
}
