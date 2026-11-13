package dev.remoteassist

import android.app.Application
import dev.remoteassist.transport.WebRtcPeer

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        WebRtcPeer.initOnce(this)
    }
}
