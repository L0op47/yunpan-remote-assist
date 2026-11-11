package dev.remoteassist.core.protocol


sealed interface WireMessage {
    data class Hello(
        val version: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val rotation: Int,
        val deviceName: String,
    ) : WireMessage

    data class VideoChunk(
        val frameId: Int,
        val chunkIndex: Int,
        val chunkCount: Int,
        val payload: ByteArray,
    ): WireMessage

    data class Tap(
        val x: Float, val y: Float
    ) : WireMessage

    data class Swipe(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val durationMs: Int,
    ) : WireMessage

    data class LongPress(
        val x: Float,
        val y: Float,
        val durationMs: Int,
    ) : WireMessage

    data class Key(
        val key: GlobalKey
    ) : WireMessage

    data class Text(
        val text: String
    ) : WireMessage

    data class Ping(
        val timestampMs: Long
    ) : WireMessage

    data class Pong(
        val timestampMs: Long
    ) : WireMessage

    data class Bye(
        val reasonCode: Int
    ) : WireMessage
}

enum class GlobalKey { BACK, HOME, RECENTS }

class ProtocolException(message: String) : Exception(message)