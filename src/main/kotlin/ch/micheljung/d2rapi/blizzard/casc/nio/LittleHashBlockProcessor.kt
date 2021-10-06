package ch.micheljung.d2rapi.blizzard.casc.nio

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LittleHashBlockProcessor {
    /**
     * @return The size of the block
     * @throws MalformedCASCStructureException If file is malformed.
     */
    fun processBlock(encoded: ByteBuffer): Int {
        encoded.order(ByteOrder.LITTLE_ENDIAN)
        val length: Int
        val expectedHash: Int
        try {
            length = encoded.int
            expectedHash = encoded.int
        } catch (e: BufferUnderflowException) {
            throw MalformedCASCStructureException("little hash block header out of bounds")
        }
        return if (expectedHash != expectedHash) {
            -length
        } else length
    }

    /**
     * Get a little hash guarded block from the source buffer.
     *
     * @param sourceBuffer Buffer to retrieve block from.
     * @return Guarded block.
     * @throws MalformedCASCStructureException If the file is malformed.
     * @throws HashMismatchException           If the block is corrupt.
     */
    fun getBlock(sourceBuffer: ByteBuffer): ByteBuffer {
        val workingBuffer = sourceBuffer.slice()
        workingBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val length: Int
        val expectedHash: Int
        try {
            length = workingBuffer.int
            expectedHash = workingBuffer.int
        } catch (e: BufferUnderflowException) {
            throw MalformedCASCStructureException("little hash block header out of bounds")
        }
        if (workingBuffer.remaining() < length) {
            throw MalformedCASCStructureException("little hash block out of bounds")
        }
        workingBuffer.limit(workingBuffer.position() + length)
        val blockBuffer = workingBuffer.slice()
        workingBuffer.position(workingBuffer.limit())
        workingBuffer.limit(workingBuffer.capacity())
        if (expectedHash != expectedHash) {
            throw HashMismatchException("little hash block")
        }
        sourceBuffer.position(sourceBuffer.position() + workingBuffer.position())
        return blockBuffer
    }
}