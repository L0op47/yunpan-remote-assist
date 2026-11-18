package dev.remoteassist.agent

import dev.remoteassist.capture.CapturedFrame
import dev.remoteassist.core.protocol.MessageCodec
import dev.remoteassist.core.protocol.VideoChunker
import dev.remoteassist.core.protocol.WireMessage
import dev.remoteassist.transport.WebRtcPeer
import java.util.concurrent.atomic.AtomicInteger

/**
 * 被控端发送端：JPEG → VideoChunker 分片 → MessageCodec 编码 → DataChannel。
 * 背压：通道积压超过 512KB 时整帧丢弃（frameId 不递增）。
 * 线程：onFrame 在采集线程被调用；帧计数用原子变量，界面按秒轮询。
 */
class StreamSender(private val peer: WebRtcPeer) {

    companion object {
        const val PROTOCOL_VERSION = 1
        private const val MAX_BUFFERED_BYTES = 512 * 1024L
    }

    private val frameId = AtomicInteger(0)
    private val sentCounter = AtomicInteger(0)

    val sentFrames: Int get() = sentCounter.get()

    fun sendHello(screenWidth: Int, screenHeight: Int, rotation: Int, deviceName: String) {
        peer.send(
            MessageCodec.encode(
                WireMessage.Hello(PROTOCOL_VERSION, screenWidth, screenHeight, rotation, deviceName)
            )
        )
    }

    fun onFrame(frame: CapturedFrame) {
        if (peer.bufferedAmount() > MAX_BUFFERED_BYTES) return // 积压太多：整帧丢弃
        val id = frameId.getAndIncrement()
        var allSent = true
        for (chunk in VideoChunker.chunk(id, frame.jpeg)) {
            if (!peer.send(MessageCodec.encode(chunk))) {
                allSent = false
                break
            }
        }
        if (allSent) sentCounter.incrementAndGet()
    }
}
