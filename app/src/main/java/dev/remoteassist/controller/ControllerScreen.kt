package dev.remoteassist.controller

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

/** M1 控制端调试界面：粘贴邀请码 → 生成应答 → 自动回 PONG。 */
@Composable
fun ControllerScreen(modifier: Modifier = Modifier, onSwitchRole: () -> Unit = {}) {
    val context = LocalContext.current
    var peer by remember { mutableStateOf<WebRtcPeer?>(null) }
    var offerInput by remember { mutableStateOf("") }
    var answerCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("未连接") }
    var generating by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("云畔 · 控制端", style = MaterialTheme.typography.headlineSmall)
        Text("状态：$status")

        OutlinedTextField(
            value = offerInput,
            onValueChange = { offerInput = it },
            label = { Text("粘贴对方发来的邀请码") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        FeedbackButton(
            enabled = !generating,
            onClick = {
                val offer = runCatching { SdpCodec.decode(offerInput) }.getOrNull()
                if (offer == null) {
                    status = "邀请码无效，检查是否完整粘贴"
                    return@FeedbackButton
                }
                peer?.close()
                answerCode = ""
                generating = true
                status = "正在生成应答码…"
                peer = WebRtcPeer.asAnswerer(context, offer, object : WebRtcPeer.Listener {
                    override fun onLocalSdpReady(sdp: String) {
                        generating = false
                        answerCode = SdpCodec.encode(sdp)
                        status = "应答码已生成，发回给对方"
                    }

                    override fun onDataConnected() { status = "通道已连接 ✅" }
                    override fun onDisconnected() {
                        generating = false
                        status = "连接已断开"
                    }

                override fun onMessage(bytes: ByteArray) {
                    val message = runCatching { MessageCodec.decode(bytes) }.getOrNull() ?: return
                    if (message is WireMessage.Ping) {
                        peer?.send(MessageCodec.encode(WireMessage.Pong(message.timestampMs)))
                        status = "收到 PING，已回复 PONG"
                    }
                }
                })
        }) {
            if (generating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("② 生成应答")
            }
        }

        if (answerCode.isNotEmpty()) {
            Text("把这行发回给对方：", style = MaterialTheme.typography.labelMedium)
            Text(answerCode, style = MaterialTheme.typography.bodySmall)
            FeedbackButton(onClick = { copyToClipboard(context, answerCode) }) { Text("复制应答码") }
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
    manager.setPrimaryClip(ClipData.newPlainText("云畔应答码", text))
}
