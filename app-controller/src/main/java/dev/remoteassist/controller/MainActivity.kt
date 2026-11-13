package dev.remoteassist.controller

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
import dev.remoteassist.controller.ui.theme.RemoteassistTheme
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
                    ControllerScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** M1 手动信令调试界面：粘贴邀请码 → 生成应答 → 自动回 PONG。 */
@Composable
fun ControllerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var peer by remember { mutableStateOf<WebRtcPeer?>(null) }
    var offerInput by remember { mutableStateOf("") }
    var answerCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("未连接") }

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

        Button(onClick = {
            val offer = runCatching { SdpCodec.decode(offerInput) }.getOrNull()
            if (offer == null) {
                status = "邀请码无效，检查是否完整粘贴"
                return@Button
            }
            peer?.close()
            answerCode = ""
            status = "正在生成应答码…"
            peer = WebRtcPeer.asAnswerer(context, offer, object : WebRtcPeer.Listener {
                override fun onLocalSdpReady(sdp: String) {
                    answerCode = SdpCodec.encode(sdp)
                    status = "应答码已生成，发回给对方"
                }

                override fun onDataConnected() { status = "通道已连接 ✅" }
                override fun onDisconnected() { status = "连接已断开" }

                override fun onMessage(bytes: ByteArray) {
                    val message = runCatching { MessageCodec.decode(bytes) }.getOrNull() ?: return
                    if (message is WireMessage.Ping) {
                        peer?.send(MessageCodec.encode(WireMessage.Pong(message.timestampMs)))
                        status = "收到 PING，已回复 PONG"
                    }
                }
            })
        }) { Text("② 生成应答") }

        if (answerCode.isNotEmpty()) {
            Text("把这行发回给对方：", style = MaterialTheme.typography.labelMedium)
            Text(answerCode, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { copyToClipboard(context, answerCode) }) { Text("复制应答码") }
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
    manager.setPrimaryClip(ClipData.newPlainText("云畔应答码", text))
}
