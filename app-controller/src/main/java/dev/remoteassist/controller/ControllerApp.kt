package dev.remoteassist.controller

import android.app.Application
import dev.remoteassist.transport.WebRtcPeer

class ControllerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WebRtcPeer.initOnce(this)
    }
}
