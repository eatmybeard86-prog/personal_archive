package ua.personal.archive.security

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Тихе фото фронтальною камерою після неправильного пароля (якщо увімкнено й дано дозвіл на камеру). */
object IntruderCamera {
    /**
     * Надійніше фото: камера інколи ще не готова одразу після запуску — тоді пробуємо ще раз (до 3 спроб).
     */
    suspend fun captureReliable(activity: FragmentActivity): Pair<ByteArray, Int>? {
        repeat(3) { attempt ->
            val r = kotlinx.coroutines.suspendCancellableCoroutine<Pair<ByteArray, Int>?> { cont ->
                capture(activity) { jpeg, rot -> if (cont.isActive) cont.resumeWith(Result.success(jpeg?.let { it to rot })) }
            }
            if (r != null) return r
            kotlinx.coroutines.delay(1500L * (attempt + 1))
        }
        return null
    }

    fun permitted(activity: FragmentActivity) =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun capture(activity: FragmentActivity, onResult: (jpeg: ByteArray?, rotation: Int) -> Unit) {
        if (!permitted(activity)) { onResult(null, 0); return }
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener onResult(null, 0)
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            try {
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, capture)
            } catch (e: Exception) {
                onResult(null, 0); return@addListener
            }
            capture.takePicture(ContextCompat.getMainExecutor(activity), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val bytes = runCatching {
                        val buf = image.planes[0].buffer
                        ByteArray(buf.remaining()).also { buf.get(it) }
                    }.getOrNull()
                    image.close()
                    runCatching { provider.unbind(capture) }
                    onResult(bytes, rotation)
                }

                override fun onError(exception: ImageCaptureException) {
                    runCatching { provider.unbind(capture) }
                    onResult(null, 0)
                }
            })
        }, ContextCompat.getMainExecutor(activity))
    }
}
