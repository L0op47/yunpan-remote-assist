package dev.remoteassist.core.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class FrameAssemblerTest {
    @Test fun `identical size maps to itself`(){
        val result = CoordinateMapper.map(
            containerWidth = 1000f,
            containerHeight = 2000f,
            videoWidth = 1000f,
            videoHeight = 2000f,
            touchX = 100f,
            touchY = 200f,
        )
        assertEquals(100f, result!!.first, 0.01f)
        assertEquals(200f, result!!.second, 0.01f)
    }

    @Test fun `scaled to half maps center point`() {
        val result = CoordinateMapper.map(
            containerWidth = 500f,
            containerHeight = 1000f,
            videoWidth = 1000f,
            videoHeight = 2000f,
            touchX = 250f,
            touchY = 500f,
        )
        assertEquals(500f, result!!.first, 0.01f)
        assertEquals(1000f, result!!.second, 0.01f)
    }
    @Test fun `vertical letterbox maps inside and rejects bars`() {
        val result = CoordinateMapper.map(
            containerWidth = 500f,
            containerHeight = 1200f,
            videoWidth = 1000f,
            videoHeight = 1000f,
            touchX = 250f,
            touchY = 600f,
        )
        assertEquals(500f, result!!.first, 0.01f)
        assertEquals(500f, result!!.second, 0.01f)

        assertNull(
            CoordinateMapper.map(
                containerWidth = 500f,
                containerHeight = 1200f,
                videoWidth = 1000f,
                videoHeight = 1000f,
                touchX = 250f,
                touchY = 100f,
            )
        )
    }
    @Test fun `horizontal letterbox maps inside and rejects bars`() {
        val result = CoordinateMapper.map(
            containerWidth = 1000f,
            containerHeight = 1000f,
            videoWidth = 500f,
            videoHeight = 1000f,
            touchX = 500f,
            touchY = 500f,
        )
        assertEquals(250f, result!!.first, 0.01f)
        assertEquals(500f, result!!.second, 0.01f)

        assertNull(
            CoordinateMapper.map(
                containerWidth = 1000f,
                containerHeight = 1000f,
                videoWidth = 500f,
                videoHeight = 1000f,
                touchX = 100f,
                touchY = 500f,
            )
        )
    }
}
