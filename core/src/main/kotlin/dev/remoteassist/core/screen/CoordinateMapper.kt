package dev.remoteassist.core.screen

object CoordinateMapper {
    fun map(
        containerWidth: Float, containerHeight: Float,
        videoWidth: Float, videoHeight: Float,
        touchX: Float, touchY: Float
    ): Pair<Float, Float>? {
        // 等比缩放比：取两个方向里较小的比例
        val scale = minOf(containerWidth / videoWidth, containerHeight / videoHeight)

        // 缩放后画面实际多大、居中后上下左右的黑边偏移是多少
        val displayWidth = videoWidth * scale
        val displayHeight = videoHeight * scale
        val offsetX = (containerWidth - displayWidth) / 2
        val offsetY = (containerHeight - displayHeight) / 2

        // 如果触摸点落在了黑边上
        if(touchX < offsetX || touchX > offsetX + displayWidth) return null
        if(touchY < offsetY || touchY > offsetY + displayHeight) return null

        // 触摸点落在画面上
        val videoX = (touchX - offsetX) / scale
        val videoY = (touchY - offsetY) / scale
        return videoX to videoY
    }
}