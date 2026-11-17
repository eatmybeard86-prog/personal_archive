package ua.personal.archive.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize

/** Стан масштабу: щипок двома пальцями (1×–8×), перетягування збільшеного, подвійний дотик. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed get() = scale > 1.01f

    fun reset() { scale = 1f; offset = Offset.Zero }

    fun transform(zoom: Float, pan: Offset, centroid: Offset, size: IntSize) {
        val newScale = (scale * zoom).coerceIn(1f, MAX)
        val k = newScale / scale
        // Точка під пальцями залишається на місці.
        val newOffset = centroid - (centroid - offset) * k + pan
        scale = newScale
        offset = clamp(newOffset, size)
    }

    fun toggle(tap: Offset, size: IntSize) {
        if (isZoomed) reset() else transform(DOUBLE_TAP / scale, Offset.Zero, tap, size)
    }

    private fun clamp(o: Offset, size: IntSize): Offset {
        val minX = size.width - size.width * scale
        val minY = size.height - size.height * scale
        return Offset(o.x.coerceIn(minX, 0f), o.y.coerceIn(minY, 0f))
    }

    companion object {
        const val MAX = 8f
        const val DOUBLE_TAP = 2.5f
    }
}

@Composable
fun rememberZoomState() = remember { ZoomState() }

/**
 * Масштабування будь-якого вмісту. Поки вміст не збільшено, одиночний свайп
 * передається далі (гортання сторінок), а щипок завжди масштабує.
 */
fun Modifier.zoomable(state: ZoomState): Modifier = this
    .pointerInput(state) {
        detectTapGestures(onDoubleTap = { state.toggle(it, size) })
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed > 1 || state.isZoomed) {
                    state.transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(useCurrent = true), size)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .graphicsLayer {
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
    }
