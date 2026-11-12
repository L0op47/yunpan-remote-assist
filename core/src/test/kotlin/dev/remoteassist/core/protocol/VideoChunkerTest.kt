package dev.remoteassist.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoChunkerTest {
    @Test fun `small frame fits in one chunk`() {
        val chunks = VideoChunker.chunk(frameId = 1, jpeg = ByteArray(1000))
        assertEquals(1,chunks.size)
        assertEquals(1, chunks[0].frameId)
        assertEquals(0, chunks[0].chunkIndex)
        assertEquals(1, chunks[0].chunkCount)
    }

    @Test fun `large frame splits into ordered chunks that reassemble`(){
        val jpeg = ByteArray(25_000){it.toByte()}
        val chunks = VideoChunker.chunk(frameId = 2, jpeg = jpeg)
        assertEquals(3, chunks.size)
        assertEquals(listOf(0, 1, 2), chunks.map { it.chunkIndex })
        assertEquals(listOf(3, 3, 3), chunks.map { it.chunkCount })
        assertEquals(25_000, chunks.sumOf { it.payload.size })
        val joined = chunks.flatMap { it.payload.toList() }.toByteArray()
        assertArrayEquals(jpeg, joined)
    }


    @Test fun `exact multiple has no empty tail chunk`() {
        val chunks = VideoChunker.chunk(frameId = 3, jpeg = ByteArray(24_000), maxChunkBytes = 12_000)
        assertEquals(2, chunks.size)
    }
}
