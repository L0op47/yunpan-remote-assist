package dev.remoteassist.core.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

object MessageCodec {

    fun encode(message: WireMessage): ByteArray {
        val outputBytes = ByteArrayOutputStream()
        val out = DataOutputStream(outputBytes)
        when (message) {
            is WireMessage.Hello -> {
                val nameBytes = message.deviceName.toByteArray(Charsets.UTF_8)
                out.writeByte(1)                    // 类型码：Hello
                out.writeInt(message.version)
                out.writeInt(message.screenWidth)
                out.writeInt(message.screenHeight)
                out.writeInt(message.rotation)
                out.writeInt(nameBytes.size)        // 名字长度前缀
                out.write(nameBytes)
            }
            is WireMessage.VideoChunk -> {
                out.writeByte(2)                    // 类型码：VideoChunk
                out.writeInt(message.frameId)
                out.writeInt(message.chunkIndex)
                out.writeInt(message.chunkCount)
                out.write(message.payload)          // payload 吃到结尾，不写长度
            }
            is WireMessage.Tap -> {
                out.writeByte(3)
                out.writeFloat(message.x)
                out.writeFloat(message.y)
            }
            is WireMessage.Swipe ->{
                out.writeByte(4)
                out.writeFloat(message.x1)
                out.writeFloat(message.y1)
                out.writeFloat(message.x2)
                out.writeFloat(message.y2)
                out.writeInt(message.durationMs)
            }
            is WireMessage.LongPress -> {
                out.writeByte(5)
                out.writeFloat(message.x)
                out.writeFloat(message.y)
                out.writeInt(message.durationMs)
            }
            is WireMessage.Key -> {
                out.writeByte(6)
                out.writeByte(message.key.ordinal)
            }
            is WireMessage.Text -> {
                val textBytes = message.text.toByteArray(Charsets.UTF_8)
                out.writeByte(7)
                out.writeInt(textBytes.size)
                out.write(textBytes)
            }
            is WireMessage.Ping -> {
                out.writeByte(8)
                out.writeLong(message.timestampMs)
            }
            is WireMessage.Pong -> {
                out.writeByte(9)
                out.writeLong(message.timestampMs)
            }
            is WireMessage.Bye -> {
                out.writeByte(10)
                out.writeInt(message.reasonCode)
            }
            else -> throw ProtocolException("未实现的消息类型")
        }
        return outputBytes.toByteArray()            // 桶里有多少就是多少，精确且无尾零
    }

    fun decode(bytes: ByteArray): WireMessage {
        try {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            when (input.readUnsignedByte()) {       // 0~255，分支里可以直接写 1、2
                1 -> {
                    val version = input.readInt()
                    val screenWidth = input.readInt()
                    val screenHeight = input.readInt()
                    val rotation = input.readInt()
                    val len = input.readInt()
                    if (len < 0 || len > input.available()) {
                        throw ProtocolException("名字长度非法")
                    }
                    val nameBytes = ByteArray(len)
                    input.readFully(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_8)
                    return WireMessage.Hello(version, screenWidth, screenHeight, rotation, name)
                }
                2 -> {
                    val frameId = input.readInt()
                    val chunkIndex = input.readInt()
                    val chunkCount = input.readInt()
                    val payload = ByteArray(input.available())  // 剩下的字节数
                    input.readFully(payload)
                    return WireMessage.VideoChunk(frameId, chunkIndex, chunkCount, payload)
                }
                3 ->{
                    val x = input.readFloat()
                    val y = input.readFloat()
                    return WireMessage.Tap(x, y)
                }
                4 ->{
                    val x1 = input.readFloat()
                    val y1 = input.readFloat()
                    val x2 = input.readFloat()
                    val y2 = input.readFloat()
                    val durationMs = input.readInt()
                    return WireMessage.Swipe(x1, y1,x2,y2,durationMs)
                }
                5 -> {
                    val x = input.readFloat()
                    val y = input.readFloat()
                    val durationMs = input.readInt()
                    return WireMessage.LongPress(x, y, durationMs)
                }
                6 -> {
                    val idx = input.readUnsignedByte()
                    if (idx !in GlobalKey.entries.indices) {
                        throw ProtocolException("非法按键")
                    }
                    return WireMessage.Key(GlobalKey.entries[idx])
                }
                7 -> {
                    val len = input.readInt()
                    if (len < 0 || len > input.available()) {
                        throw ProtocolException("文本长度非法")
                    }
                    val textBytes = ByteArray(len)
                    input.readFully(textBytes)
                    return WireMessage.Text(String(textBytes, Charsets.UTF_8))
                }
                8 -> return WireMessage.Ping(input.readLong())
                9 -> return WireMessage.Pong(input.readLong())
                10 -> return WireMessage.Bye(input.readInt())
                else -> throw ProtocolException("未知类型码")
            }
        } catch (e: EOFException) {                 // 字节读不够时 DataInputStream 抛这个
            throw ProtocolException("消息不完整")
        }
    }
}
