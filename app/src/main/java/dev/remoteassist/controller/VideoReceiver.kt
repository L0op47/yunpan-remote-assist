package dev.remoteassist.controller

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.remoteassist.core.protocol.FrameAssembler
import dev.remoteassist.core.protocol.WireMessage

/** 一帧接收结果：解码后的画面 + 累计接收/坏帧计数。 */
class ReceivedFrame(val image: ImageBitmap, val receivedFrames: Int, val droppedFrames: Int)

/**
 * 控制端接收端：VideoChunk → FrameAssembler 重组 → JPEG 解码。
 * 线程：WebRtcPeer 的回调已切到主线程，这里也在主线程执行（M1 可接受，优化记为欠账）。
 */
class VideoReceiver {
    private val assembler = FrameAssembler()
    private var receivedFrames = 0
    private var droppedFrames = 0

    /** 非视频块或还没拼完整时返回 null。 */
    fun onMessage(message: WireMessage): ReceivedFrame? {
        if (message !is WireMessage.VideoChunk) return null
        val jpeg = assembler.accept(message) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        if (bitmap == null) {
            droppedFrames++
            return null
        }
        receivedFrames++
        return ReceivedFrame(bitmap.asImageBitmap(), receivedFrames, droppedFrames)
    }

    fun reset() {
        assembler.reset()
        receivedFrames = 0
        droppedFrames = 0
    }
}
