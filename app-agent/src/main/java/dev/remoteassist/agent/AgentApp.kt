package dev.remoteassist.agent

import android.app.Application
import dev.remoteassist.transport.WebRtcPeer

class AgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WebRtcPeer.initOnce(this)
    }
}
