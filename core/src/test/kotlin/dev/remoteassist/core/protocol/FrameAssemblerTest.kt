package dev.remoteassist.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameAssemblerTest {

    @Test fun `in-order chunks reassemble`() {
        val assembler = FrameAssembler()
        val frame = ByteArray(25_000) { it.toByte() }
        var result: ByteArray? = null
        for (chunk in VideoChunker.chunk(frameId = 1, jpeg = frame)) {
            result = assembler.accept(chunk)
        }
        assertArrayEquals(frame, result)
    }

    @Test fun `out-of-order chunks still reassemble`() {
        val assembler = FrameAssembler()
        val frame = ByteArray(25_000) { it.toByte() }
        var result: ByteArray? = null
        for (chunk in VideoChunker.chunk(frameId = 1, jpeg = frame).reversed()) {
            result = assembler.accept(chunk)
        }
        assertArrayEquals(frame, result)
    }

    @Test fun `missing chunk yields null`() {
        val assembler = FrameAssembler()
        val frame = ByteArray(25_000)
        val withHole = VideoChunker.chunk(frameId = 1, jpeg = frame).filter { it.chunkIndex != 1 }
        for (chunk in withHole) assertNull(assembler.accept(chunk))
    }

    @Test fun `new frame discards incomplete old frame`() {
        val assembler = FrameAssembler()
        val frame1 = ByteArray(25_000) { 1 }
        val frame2 = ByteArray(1_000) { 2 }
        val chunks1 = VideoChunker.chunk(frameId = 1, jpeg = frame1)
        val chunks2 = VideoChunker.chunk(frameId = 2, jpeg = frame2)
        assertNull(assembler.accept(chunks1[0]))              // 帧 1 只到一块
        var result: ByteArray? = null
        for (chunk in chunks2) result = assembler.accept(chunk) // 帧 2 完整到达
        assertArrayEquals(frame2, result)
        assertNull(assembler.accept(chunks1[1]))              // 帧 1 的迟到块被忽略
        assertNull(assembler.accept(chunks1[2]))
    }

    @Test fun `reset clears state`() {
        val assembler = FrameAssembler()
        val frame = ByteArray(25_000)
        val chunks = VideoChunker.chunk(frameId = 1, jpeg = frame)
        assertNull(assembler.accept(chunks[0]))
        assembler.reset()
        var result: ByteArray? = null
        for (chunk in chunks) result = assembler.accept(chunk)
        assertArrayEquals(frame, result)
    }
}