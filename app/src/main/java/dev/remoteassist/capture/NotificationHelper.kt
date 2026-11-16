package dev.remoteassist.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/** 录屏前台服务的常驻通知（Android 规定录屏必须带可见通知）。 */
object NotificationHelper {
    private const val CHANNEL_ID = "screen_capture"

    fun build(context: Context): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "屏幕共享", NotificationManager.IMPORTANCE_LOW).apply {
                description = "远程协助期间保持屏幕共享"
            }
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("云畔 · 屏幕共享中")
            .setContentText("正在采集屏幕（测试阶段，画面不会发送）")
            .setOngoing(true)
            .build()
    }
}
