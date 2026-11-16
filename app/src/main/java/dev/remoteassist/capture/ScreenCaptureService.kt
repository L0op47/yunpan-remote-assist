package dev.remoteassist.capture

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * 录屏前台服务：前台化 + 拿到 MediaProjection，并驱动 [JpegFrameProducer] 采集画面。
 */
class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val NOTIFICATION_ID = 1
    }

    private var projection: MediaProjection? = null
    private var producer: JpegFrameProducer? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // 用户在系统界面点了停止共享
            stopCapture()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 重复点「开始采集」时先收掉上一次的会话，避免两个采集器同时跑
        stopCapture()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val newProjection = manager.getMediaProjection(resultCode, resultData)
            ?: run {
                stopSelf()
                return START_NOT_STICKY
            }
        newProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = newProjection

        CapturePreviewBus.onStarted()
        producer = JpegFrameProducer(this, newProjection) { CapturePreviewBus.onFrame(it) }
            .also { it.start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun stopCapture() {
        producer?.stop()
        producer = null
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
        CapturePreviewBus.onStopped()
    }
}
