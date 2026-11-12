package dev.remoteassist.core.signal

import dev.remoteassist.core.protocol.ProtocolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SdpCodecTest {

    private val sampleSdp = """
        v=0
        o=- 1234567890 2 IN IP4 127.0.0.1
        s=-
        t=0 0
        a=group:BUNDLE 0
        m=application 9 UDP/DTLS/SCTP webrtc-datachannel
    """.trimIndent()

    @Test fun `round trip preserves multiline sdp`() {
        val encoded = SdpCodec.encode(sampleSdp)
        assertFalse(encoded.contains("\n"))
        assertTrue(encoded.startsWith("RSDP1:"))
        assertEquals(sampleSdp, SdpCodec.decode(encoded))
    }

    @Test fun `decode tolerates surrounding whitespace`() {
        val encoded = SdpCodec.encode(sampleSdp)
        assertEquals(sampleSdp, SdpCodec.decode("  \n$encoded \n "))
    }

    @Test fun `decode rejects invalid base64`() {
        assertThrows(ProtocolException::class.java) { SdpCodec.decode("RSDP1:这不是base64") }
    }

    @Test fun `decode rejects missing prefix`() {
        assertThrows(ProtocolException::class.java) { SdpCodec.decode("Zm9v") }
    }
}
