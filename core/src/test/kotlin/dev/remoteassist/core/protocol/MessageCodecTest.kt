package dev.remoteassist.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageCodecTest {
    @Test fun `hello round trip`() {
        val msg = WireMessage.Hello(1, 1080, 2400, 0, "iQOO")
        val decoded = MessageCodec.decode(MessageCodec.encode(msg)) as WireMessage.Hello
        assertEquals(1, decoded.version)
        assertEquals(1080, decoded.screenWidth)
        assertEquals(2400, decoded.screenHeight)
        assertEquals(0, decoded.rotation)
        assertEquals("iQOO", decoded.deviceName)
    }

    @Test fun `video chunk round trip`() {
        val payload = ByteArray(100) { it.toByte() }
        val decoded = MessageCodec.decode(MessageCodec.encode(WireMessage.VideoChunk(7, 1, 3, payload))) as WireMessage.VideoChunk
        assertEquals(7, decoded.frameId)
        assertEquals(1, decoded.chunkIndex)
        assertEquals(3, decoded.chunkCount)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test fun `all small messages round trip`() {
        val msgs = listOf(
            WireMessage.Tap(1.5f, 2.5f),
            WireMessage.Swipe(0f, 0f, 100f, 200f, 300),
            WireMessage.LongPress(10f, 20f, 500),
            WireMessage.Key(GlobalKey.BACK),
            WireMessage.Text("你好，爸妈"),
            WireMessage.Ping(123456789L),
            WireMessage.Pong(123456789L),
            WireMessage.Bye(1),
        )
        for (m in msgs) assertEquals(m, MessageCodec.decode(MessageCodec.encode(m)))
    }

    @Test fun `unknown type throws`() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(byteArrayOf(0x7F)) }
    }

    @Test fun `empty input throws`() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(ByteArray(0)) }
    }

    @Test fun `truncated hello throws`() {
        val full = MessageCodec.encode(WireMessage.Hello(1, 1080, 2400, 0, "iQOO"))
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(full.copyOf(full.size - 2)) }
    }

    @Test fun `text with emoji round trip`() {
        val msg = WireMessage.Text("📱 帮爸妈")
        assertEquals(msg, MessageCodec.decode(MessageCodec.encode(msg)))
    }
}