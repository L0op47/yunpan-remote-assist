package dev.remoteassist.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.remoteassist.agent.ui.theme.RemoteassistTheme
import dev.remoteassist.core.protocol.MessageCodec
import dev.remoteassist.core.protocol.WireMessage
import dev.remoteassist.core.signal.SdpCodec
import dev.remoteassist.transport.WebRtcPeer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteassistTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AgentScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** M1 手动信令调试界面：生成邀请码 → 应用应答 → 发 PING 测往返。 */
@Composable
fun AgentScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var peer by remember { mutableStateOf<WebRtcPeer?>(null) }
    var offerCode by remember { mutableStateOf("") }
    var answerInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("未连接") }
    var pingRtt by remember { mutableStateOf("") }
    var pingSentAt by remember { mutableStateOf(0L) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("云畔 · 被控端", style = MaterialTheme.typography.headlineSmall)
        Text("状态：$status")

        Button(onClick = {
            peer?.close()
            offerCode = ""
            pingRtt = ""
            status = "正在生成邀请码…"
            peer = WebRtcPeer.asOfferer(context, object : WebRtcPeer.Listener {
                override fun onLocalSdpReady(sdp: String) {
                    offerCode = SdpCodec.encode(sdp)
                    status = "邀请码已生成，发到另一台手机"
                }

                override fun onDataConnected() { status = "通道已连接 ✅" }
                override fun onDisconnected() { status = "连接已断开" }

                override fun onMessage(bytes: ByteArray) {
                    val message = runCatching { MessageCodec.decode(bytes) }.getOrNull() ?: return
                    if (message is WireMessage.Pong) {
                        pingRtt = "${System.currentTimeMillis() - pingSentAt} ms"
                        status = "收到 PONG"
                    }
                }
            })
        }) { Text("① 生成邀请码") }

        if (offerCode.isNotEmpty()) {
            Text("把这行发到另一台手机：", style = MaterialTheme.typography.labelMedium)
            Text(offerCode, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { copyToClipboard(context, offerCode) }) { Text("复制邀请码") }
        }

        OutlinedTextField(
            value = answerInput,
            onValueChange = { answerInput = it },
            label = { Text("粘贴对方的应答码") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Button(onClick = {
            val current = peer
            if (current == null) {
                status = "请先生成邀请码"
                return@Button
            }
            val answer = runCatching { SdpCodec.decode(answerInput) }.getOrNull()
            if (answer == null) {
                status = "应答码无效，检查是否完整粘贴"
                return@Button
            }
            current.setAnswer(answer)
            status = "已应用应答，等待连接…"
        }) { Text("③ 应用应答") }

        Button(onClick = {
            val current = peer ?: return@Button
            pingSentAt = System.currentTimeMillis()
            val sent = current.send(MessageCodec.encode(WireMessage.Ping(pingSentAt)))
            status = if (sent) "已发送 PING，等待 PONG…" else "发送失败：通道未连接"
        }) { Text("发送 PING") }

        if (pingRtt.isNotEmpty()) {
            Text("往返时延：$pingRtt", style = MaterialTheme.typography.titleMedium)
        }

        Button(onClick = {
            peer?.close()
            peer = null
            status = "已断开"
        }) { Text("断开") }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("云畔邀请码", text))
}
