package dev.remoteassist.capture

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 采集结果的界面状态：最新预览帧 + 帧率 + 是否运行中。 */
data class CapturePreviewState(
    val frame: ImageBitmap? = null,
    val fps: Float = 0f,
    val running: Boolean = false,
)

/**
 * 采集线程 → 界面的桥。
 * 调试期用进程内单例传递最新一帧；M3 接传输时 JPEG 走数据通道，这里只留本地预览。
 */
object CapturePreviewBus {
    private val _state = MutableStateFlow(CapturePreviewState())
    val state: StateFlow<CapturePreviewState> = _state

    fun onStarted() {
        _state.value = CapturePreviewState(running = true)
    }

    fun onFrame(frame: CapturedFrame) {
        _state.value = CapturePreviewState(
            frame = frame.preview.asImageBitmap(),
            fps = frame.fps,
            running = true,
        )
    }

    fun onStopped() {
        _state.value = CapturePreviewState()
    }
}
