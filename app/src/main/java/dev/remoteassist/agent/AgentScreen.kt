package dev.remoteassist.agent

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.remoteassist.capture.CapturePreviewBus
import dev.remoteassist.capture.ScreenCaptureService
import dev.remoteassist.core.protocol.MessageCodec
import dev.remoteassist.core.protocol.WireMessage
import dev.remoteassist.core.signal.SdpCodec
import dev.remoteassist.transport.WebRtcPeer
import dev.remoteassist.ui.FeedbackButton
import dev.remoteassist.ui.FeedbackTextButton
import dev.remoteassist.ui.theme.RemoteassistTheme

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
    var captureStatus by remember { mutableStateOf("未采集") }

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

        // 录屏：授权 → 前台服务采集 → 本地预览（切片 2-3）
        val captureState by CapturePreviewBus.state.collectAsState()
        var hasRun by remember { mutableStateOf(false) }
        LaunchedEffect(captureState.running) {
            if (captureState.running) {
                hasRun = true
            } else if (hasRun) {
                captureStatus = "已停止"
            }
        }
        val captureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val intent = Intent(context, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                }
                ContextCompat.startForegroundService(context, intent)
                captureStatus = "采集已启动（看状态栏通知）"
            } else {
                captureStatus = "已取消授权，可重试"
            }
        }

        fun launchCapture() {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
            captureLauncher.launch(manager.createScreenCaptureIntent())
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { launchCapture() }

        FeedbackButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchCapture()
            }
        }) { Text("开始采集") }
        Text(
            "采集状态：${if (captureState.running) "运行中" else captureStatus}",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (captureState.running) {
            captureState.frame?.let { frame ->
                Image(
                    bitmap = frame,
                    contentDescription = "本机屏幕预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.Black),
                )
            }
            Text(
                "帧率：${"%.1f".format(captureState.fps)} fps（画面静止时变低是正常现象）",
                style = MaterialTheme.typography.bodyMedium,
            )
            FeedbackButton(onClick = {
                context.stopService(Intent(context, ScreenCaptureService::class.java))
            }) { Text("停止采集") }
        }

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


@Preview(showBackground = true, widthDp = 393, heightDp = 851)
@Composable
private fun AgentScreenPreview() {
    RemoteassistTheme {
        AgentScreen()
    }
}