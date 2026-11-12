package dev.remoteassist.core.protocol

class FrameAssembler {
    private var currentFrameId = -1
    private var parts : Array<ByteArray?> = emptyArray()
    private var received = 0
    fun accept(chunk : WireMessage.VideoChunk): ByteArray?{
        // 迟到的块 忽略
        if(chunk.frameId < currentFrameId) return null

        if(chunk.frameId > currentFrameId){
            currentFrameId = chunk.frameId
            parts = arrayOfNulls(chunk.chunkCount)
            received = 0
        }

        if(chunk.chunkIndex !in parts.indices) return null
        if(parts[chunk.chunkIndex] != null) return null
        parts[chunk.chunkIndex] = chunk.payload
        received++

        if(received < parts.size) return null

        val total = parts.sumOf { it?.size?:0 }
        val result = ByteArray(total)
        var offset = 0
        for (part in parts){
            val bytes = part?:continue
            bytes.copyInto(result,offset)
            offset += bytes.size
        }

        parts = emptyArray()
        received = 0
        return result
    }

    fun reset(){
        currentFrameId = -1
        parts = emptyArray()
        received = 0
    }
}