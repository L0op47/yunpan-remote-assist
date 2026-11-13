package dev.remoteassist.transport

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

/**
 * libwebrtc 的最小封装：一台设备一条 PeerConnection + 一个可靠有序 DataChannel。
 *
 * 用法（M1 手动信令）：
 *   offerer  = WebRtcPeer.asOfferer(context, listener)          // 生成邀请
 *   allowee  = WebRtcPeer.asAnswerer(context, offerSdp, listener) // 生成应答
 *   offerer.setAnswer(answerSdp)                                 // 完成握手
 * 之后 onDataConnected() 触发，send()/onMessage() 收发消息。
 *
 * 设计约定：所有 Listener 回调都抛到主线程，UI 层可直接更新 Compose 状态。
 */
class WebRtcPeer private constructor(private val listener: Listener) {

    interface Listener {
        fun onLocalSdpReady(sdp: String)
        fun onDataConnected()
        fun onMessage(bytes: ByteArray)
        fun onDisconnected()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private val localCandidates = mutableListOf<String>()
    private var sdpDelivered = false
    private var gatherTimeout: Runnable? = null

    /** offerer 侧：对方发回应答后调用，完成握手。 */
    fun setAnswer(answerSdp: String) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) = postDisconnected()
        }, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
    }

    /** 发送一条消息；通道未就绪返回 false。 */
    fun send(bytes: ByteArray): Boolean {
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), true))
    }

    /** 待发送字节数，供上层丢弃视频帧的背压判断用。 */
    fun bufferedAmount(): Long = dataChannel?.bufferedAmount() ?: 0L

    fun close() {
        gatherTimeout?.let { mainHandler.removeCallbacks(it) }
        dataChannel?.close()
        dataChannel = null
        localCandidates.clear()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }

    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun postDisconnected() = post { listener.onDisconnected() }

    private fun attachDataChannel(channel: DataChannel) {
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                when (channel.state()) {
                    DataChannel.State.OPEN -> post { listener.onDataConnected() }
                    DataChannel.State.CLOSED -> postDisconnected()
                    else -> {}
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                post { listener.onMessage(bytes) }
            }
        })
    }

    /** 非 trickle：等 ICE 收集完成再交付本地 SDP，最多等 GATHER_TIMEOUT_MS。 */
    private fun scheduleGatherTimeout() {
        val timeout = Runnable { deliverLocalSdp() }
        gatherTimeout = timeout
        mainHandler.postDelayed(timeout, GATHER_TIMEOUT_MS)
    }

    private fun deliverLocalSdp() {
        if (sdpDelivered) return
        val base = peerConnection?.localDescription?.description ?: return
        val sdp = buildString {
            append(base.trimEnd())
            for (line in localCandidates.distinct()) {
                append("\r\n").append(line)
            }
            append("\r\n")
        }
        val candidateLines = sdp.lines().count { it.startsWith("a=candidate") }
        Log.i("WebRtcPeer", "deliverLocalSdp: candidates=$candidateLines gathering=${peerConnection?.iceGatheringState()}")
        sdpDelivered = true
        gatherTimeout?.let { mainHandler.removeCallbacks(it) }
        post { listener.onLocalSdpReady(sdp) }
    }

    private val connectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state == PeerConnection.IceConnectionState.FAILED ||
                state == PeerConnection.IceConnectionState.CLOSED
            ) {
                postDisconnected()
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.i("WebRtcPeer", "iceGathering=$state")
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                // 该版本 libwebrtc 在 COMPLETE 之后才回调候选地址，稍等再取 SDP
                mainHandler.postDelayed({ deliverLocalSdp() }, 300)
            }
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            val candidateSdp = candidate?.sdp
            if (candidateSdp != null) {
                localCandidates.add("a=$candidateSdp")
                Log.i("WebRtcPeer", "ICE candidate: mid=${candidate.sdpMid} $candidateSdp")
            }
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel) = attachDataChannel(channel)
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
    }

    private fun createPeerConnection(): PeerConnection {
        val iceServers = defaultIceServers().map { url ->
            val builder = PeerConnection.IceServer.builder(url)
            if (url.startsWith("turn")) {
                builder.setUsername("openrelayproject").setPassword("openrelayproject")
            }
            builder.createIceServer()
        }
        val configuration = PeerConnection.RTCConfiguration(iceServers)
        val pc = factoryOrThrow().createPeerConnection(configuration, connectionObserver)
            ?: throw IllegalStateException("PeerConnection 创建失败")
        peerConnection = pc
        return pc
    }

    companion object {
        private const val GATHER_TIMEOUT_MS = 8_000L

        @Volatile
        private var factory: PeerConnectionFactory? = null
        private val initLock = Any()

        /** 每个进程调用一次（Application.onCreate 里）。 */
        fun initOnce(context: Context) {
            synchronized(initLock) {
                if (factory != null) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                        .builder(context.applicationContext)
                        .createInitializationOptions()
                )
                org.webrtc.Logging.enableLogToDebugOutput(org.webrtc.Logging.Severity.LS_INFO)
                factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            }
        }

        fun defaultIceServers(): List<String> = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun.miwifi.com:3478",
            // 公共 TURN 方案待定：openrelay.metered.ca 在国内被 DNS 污染（解析到
            // 192.0.2.1/100::1），无法使用。后续在 M3 换成自建 coturn 或其他可达的
            // 免费 TURN；换好后在此追加 "turn:..." 并在 createPeerConnection 里带凭据。
        )

        fun asOfferer(context: Context, listener: Listener): WebRtcPeer {
            factoryOrThrow()
            val peer = WebRtcPeer(listener)
            val pc = peer.createPeerConnection()
            peer.attachDataChannel(pc.createDataChannel("app", DataChannel.Init()))
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description == null) return peer.postDisconnected()
                    pc.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() = peer.scheduleGatherTimeout()
                        override fun onSetFailure(error: String?) = peer.postDisconnected()
                    }, description)
                }

                override fun onCreateFailure(error: String?) = peer.postDisconnected()
            }, MediaConstraints())
            return peer
        }

        fun asAnswerer(context: Context, offerSdp: String, listener: Listener): WebRtcPeer {
            factoryOrThrow()
            val peer = WebRtcPeer(listener)
            val pc = peer.createPeerConnection()
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    pc.createAnswer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(description: SessionDescription?) {
                            if (description == null) return peer.postDisconnected()
                            pc.setLocalDescription(object : SimpleSdpObserver() {
                                override fun onSetSuccess() = peer.scheduleGatherTimeout()
                                override fun onSetFailure(error: String?) = peer.postDisconnected()
                            }, description)
                        }

                        override fun onCreateFailure(error: String?) = peer.postDisconnected()
                    }, MediaConstraints())
                }

                override fun onSetFailure(error: String?) = peer.postDisconnected()
            }, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
            return peer
        }

        private fun factoryOrThrow(): PeerConnectionFactory =
            factory ?: throw IllegalStateException("请先在 Application 中调用 WebRtcPeer.initOnce()")
    }
}

/** SdpObserver 的空实现，子类只覆盖关心的回调。 */
private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
