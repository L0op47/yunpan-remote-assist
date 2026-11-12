package dev.remoteassist.core.protocol

object VideoChunker {
    const val DEFAULT_MAX_CHUNK_BYTES = 12_000

    fun chunk(
        frameId: Int,
        jpeg: ByteArray,
        maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES
        ):List<WireMessage.VideoChunk>{
        val chunkCount = (jpeg.size + maxChunkBytes - 1) / maxChunkBytes
        val chunks = mutableListOf<WireMessage.VideoChunk>()
        for (i in 0 until chunkCount){
            val start = i * maxChunkBytes
            val end = minOf(jpeg.size,start+maxChunkBytes)
            val payload = jpeg.copyOfRange(start,end)
            chunks.add(WireMessage.VideoChunk(frameId,i,chunkCount,payload))
        }
        return chunks
    }
}