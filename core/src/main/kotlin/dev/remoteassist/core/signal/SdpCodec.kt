package dev.remoteassist.core.signal

import dev.remoteassist.core.protocol.ProtocolException
import java.util.Base64

/**
 * 把多行 SDP 文本编码成"聊天友好"的单行 Base64（带 RSDP1: 前缀），
 * 以及从聊天窗口粘贴回来的容错解码。
 * 只用于 M1 阶段手动交换连接信息；引入信令服务器后由 MQTT 传输 SDP 原文。
 */
object SdpCodec {
    private const val PREFIX = "RSDP1:"

    fun encode(sdp: String): String {
        val base64 = Base64.getEncoder().encodeToString(sdp.toByteArray(Charsets.UTF_8))
        return PREFIX + base64
    }

    fun decode(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX)) {
            throw ProtocolException("SDP 聊天码格式错误：缺少 RSDP1: 前缀")
        }
        // 聊天软件可能在长行中间折行插入空白，Base64 解析前全部剔除
        val payload = trimmed.removePrefix(PREFIX).replace(Regex("\\s+"), "")
        val bytes = try {
            Base64.getDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            throw ProtocolException("SDP 聊天码格式错误：Base64 无效")
        }
        return String(bytes, Charsets.UTF_8)
    }
}
