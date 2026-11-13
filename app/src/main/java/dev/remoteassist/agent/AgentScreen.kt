package dev.remoteassist.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.remoteassist.core.protocol.MessageCodec
import dev.remoteassist.core.protocol.WireMessage
import dev.remoteassist.core.signal.SdpCodec
import dev.remoteassist.transport.WebRtcPeer
import dev.remoteassist.ui.FeedbackButton
import dev.remoteassist.ui.FeedbackTextButton

/** M1 被控端调试界面：生成邀请码 → 应用应答 → 发 PING 测往返。 */
@Composable
fun AgentScreen(modifier: Modifier = Modifier, onSwitchRole: () -> Unit = {}) {
    val context = LocalContext.current
    var peer by remember { mutableStateOf<WebRtcPeer?>(null) }
    var offerCode by remember { mutableStateOf("") }
    var answerInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("未连接") }
    var pingRtt by remember { mutableStateOf("") }
    var pingSentAt by remember { mutableStateOf(0L) }
    var generating by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("云畔 · 被控端", style = MaterialTheme.typography.headlineSmall)
        Text("状态：$status")

        FeedbackButton(
            enabled = !generating,
            onClick = {
                peer?.close()
                offerCode = ""
                pingRtt = ""
                generating = true
                status = "正在生成邀请码…"
                peer = WebRtcPeer.asOfferer(context, object : WebRtcPeer.Listener {
                    override fun onLocalSdpReady(sdp: String) {
                        generating = false
                        offerCode = SdpCodec.encode(sdp)
                        status = "邀请码已生成，发到另一台手机"
                    }

                    override fun onDataConnected() { status = "通道已连接 ✅" }
                    override fun onDisconnected() {
                        generating = false
                        status = "连接已断开"
                    }

                override fun onMessage(bytes: ByteArray) {
                    val message = runCatching { MessageCodec.decode(bytes) }.getOrNull() ?: return
                    if (message is WireMessage.Pong) {
                        pingRtt = "${System.currentTimeMillis() - pingSentAt} ms"
                        status = "收到 PONG"
                    }
                }
                })
        }) {
            if (generating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("① 生成邀请码")
            }
        }

        if (offerCode.isNotEmpty()) {
            Text("把这行发到另一台手机：", style = MaterialTheme.typography.labelMedium)
            Text(offerCode, style = MaterialTheme.typography.bodySmall)
            FeedbackButton(onClick = { copyToClipboard(context, offerCode) }) { Text("复制邀请码") }
        }

        OutlinedTextField(
            value = answerInput,
            onValueChange = { answerInput = it },
            label = { Text("粘贴对方的应答码") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        FeedbackButton(onClick = {
            val current = peer
            if (current == null) {
                status = "请先生成邀请码"
                return@FeedbackButton
            }
            val answer = runCatching { SdpCodec.decode(answerInput) }.getOrNull()
            if (answer == null) {
                status = "应答码无效，检查是否完整粘贴"
                return@FeedbackButton
            }
            current.setAnswer(answer)
            status = "已应用应答，等待连接…"
        }) { Text("③ 应用应答") }

        FeedbackButton(onClick = {
            val current = peer ?: return@FeedbackButton
            pingSentAt = System.currentTimeMillis()
            val sent = current.send(MessageCodec.encode(WireMessage.Ping(pingSentAt)))
            status = if (sent) "已发送 PING，等待 PONG…" else "发送失败：通道未连接"
        }) { Text("发送 PING") }

        if (pingRtt.isNotEmpty()) {
            Text("往返时延：$pingRtt", style = MaterialTheme.typography.titleMedium)
        }

        FeedbackTextButton(onClick = {
            peer?.close()
            peer = null
            onSwitchRole()
        }) { Text("切换角色") }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("云畔邀请码", text))
}
