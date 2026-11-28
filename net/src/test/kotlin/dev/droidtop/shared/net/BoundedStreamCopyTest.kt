package dev.droidtop.shared.net

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class BoundedStreamCopyTest {
    private class Input(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }
    private class Output : ByteArrayOutputStream() {
        var closed = false
        var flushed = false
        override fun close() { closed = true; super.close() }
        override fun flush() { flushed = true; super.flush() }
    }

    @Test fun `exact and unknown lengths preserve bytes without taking ownership`() {
        for (expected in listOf(-1L, 3L)) {
            val input = Input(byteArrayOf(1, 2, 3))
            val output = Output()
            val progress = mutableListOf<Long>()
            assertEquals(3L, BoundedStreamCopy.copy(input, output, 10, expected, progress = { progress += it }))
            assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
            assertEquals(3L, progress.last())
            assertFalse(input.closed); assertFalse(output.closed); assertFalse(output.flushed)
        }
        assertEquals(0L, BoundedStreamCopy.copy(Input(byteArrayOf()), Output(), 0, 0))
    }

    @Test fun `a short declared body and excess bytes fail instead of succeeding`() {
        for ((max, expected) in listOf(10L to 4L, 10L to 2L, 2L to -1L, 0L to -1L)) {
            val input = Input(byteArrayOf(1, 2, 3))
            val output = Output()
            assertThrows(IOException::class.java) { BoundedStreamCopy.copy(input, output, max, expected) }
            assertFalse(input.closed); assertFalse(output.closed); assertFalse(output.flushed)
            assertTrue(output.size().toLong() <= max)
            if (expected >= 0) assertTrue(output.size().toLong() <= expected)
        }
    }

    @Test fun `caller cancellation stops further reads and preserves its exception`() {
        val input = Input(ByteArray(128 * 1024) { it.toByte() })
        val output = Output()
        val cancelled = CancellationException("caller cancelled")
        var received = 0L
        val failure = assertThrows(CancellationException::class.java) {
            BoundedStreamCopy.copy(input, output, 128 * 1024L,
                beforeRead = { if (received > 0) throw cancelled },
                progress = { received = it },
            )
        }
        assertSame(cancelled, failure)
        assertTrue(received > 0 && received < 128 * 1024L)
        assertEquals(received, output.size().toLong())
        assertFalse(input.closed); assertFalse(output.closed)
    }

    @Test fun `a destination policy can refuse before any bytes are written`() {
        val output = Output()
        val refusal = IllegalStateException("destination is not available")
        assertSame(refusal, assertThrows(IllegalStateException::class.java) {
            BoundedStreamCopy.copy(Input(byteArrayOf(1, 2, 3)), output, 3, beforeWrite = { throw refusal })
        })
        assertEquals(0, output.size())
        assertFalse(output.closed)
    }
}
