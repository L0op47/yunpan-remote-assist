package dev.remoteassist.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

/** 一帧采集结果：JPEG（Task 7 起发给对端）+ 已缩放位图（本地预览）+ 平滑帧率。 */
class CapturedFrame(val jpeg: ByteArray, val preview: Bitmap, val fps: Float)

/**
 * 屏幕 → JPEG 帧的生产者。
 * 数据流：MediaProjection → VirtualDisplay → ImageReader → 位图 → 缩到 ≤960 宽 → JPEG(q60)。
 * 所有像素处理都在专属后台线程（HandlerThread）上执行，主线程不参与。
 */
class JpegFrameProducer(
    private val context: Context,
    private val projection: MediaProjection,
    private val onFrame: (CapturedFrame) -> Unit,
) {
    companion object {
        private const val TARGET_MAX_WIDTH = 960
        private const val FRAME_INTERVAL_MS = 100L // 限速 ~10fps
        private const val JPEG_QUALITY = 60
    }

    @Volatile
    private var running = false
    private var thread: HandlerThread? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var previousEmitAt = 0L
    private var smoothedFps = 0f

    fun start() {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi

        val captureThread = HandlerThread("yunpan-capture").also { it.start() }
        val handler = Handler(captureThread.looper)

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader.setOnImageAvailableListener({ onImageAvailable(it) }, handler)
        val display = projection.createVirtualDisplay(
            "yunpan-screen",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler,
        )

        thread = captureThread
        reader = imageReader
        virtualDisplay = display
        running = true
    }

    fun stop() {
        val captureThread = thread ?: return
        val imageReader = reader
        val display = virtualDisplay
        running = false
        thread = null
        reader = null
        virtualDisplay = null
        // 收尾放到采集线程上执行：与出图回调同线程，避免关流时正好在处理一帧
        Handler(captureThread.looper).post {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
            display?.release()
            captureThread.quitSafely()
        }
    }

    private fun onImageAvailable(source: ImageReader) {
        if (!running) return
        source.acquireLatestImage()?.use { image ->
            val now = SystemClock.elapsedRealtime()
            if (previousEmitAt != 0L) {
                val dt = now - previousEmitAt
                if (dt < FRAME_INTERVAL_MS) return // 限速：这帧丢掉
                val instantFps = 1000f / dt
                smoothedFps =
                    if (smoothedFps == 0f) instantFps else smoothedFps * 0.7f + instantFps * 0.3f
            }
            previousEmitAt = now

            val full = image.toBitmap()
            val scaled = scaleToMaxWidth(full)
            onFrame(CapturedFrame(jpeg = scaled.toJpeg(), preview = scaled, fps = smoothedFps))
        }
    }

    /** RGBA_8888 像素拷贝，并去掉行的填充字节（rowStride 通常大于实际宽度）。 */
    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val paddedWidth = width + rowPadding / plane.pixelStride
        val bitmap = createBitmap(paddedWidth, height)
        bitmap.copyPixelsFromBuffer(plane.buffer)
        if (paddedWidth == width) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        bitmap.recycle()
        return cropped
    }

    private fun scaleToMaxWidth(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= TARGET_MAX_WIDTH) return bitmap
        val height = (bitmap.height * (TARGET_MAX_WIDTH.toFloat() / bitmap.width)).toInt()
        val scaled = bitmap.scale(TARGET_MAX_WIDTH, height)
        bitmap.recycle()
        return scaled
    }

    private fun Bitmap.toJpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }
}
