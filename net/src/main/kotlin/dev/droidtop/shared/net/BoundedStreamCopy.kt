package dev.droidtop.shared.net

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Copies an already-owned stream without closing/flushing either side, retrying or opening a connection.
 * Callers own timeouts and interruption of blocking reads. Hooks may cancel by throwing; their exception propagates.
 */
object BoundedStreamCopy {
    @Throws(IOException::class)
    fun copy(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        expectedBytes: Long = -1,
        beforeRead: () -> Unit = {},
        beforeWrite: (Int) -> Unit = {},
        progress: (Long) -> Unit = {},
    ): Long {
        require(maxBytes >= 0 && expectedBytes >= -1 && expectedBytes <= maxBytes)
        val bytes = ByteArray(32 * 1024)
        var done = 0L
        while (true) {
            beforeRead()
            val count = input.read(bytes)
            if (count < 0) break
            if (count == 0) continue
            if (done > maxBytes - count) throw IOException("The response exceeds its byte limit")
            if (expectedBytes >= 0 && done > expectedBytes - count) throw IOException("The response exceeds its declared length")
            beforeWrite(count)
            output.write(bytes, 0, count)
            done += count
            progress(done)
        }
        if (expectedBytes >= 0 && done != expectedBytes) throw IOException("The response ended before all declared bytes arrived")
        return done
    }
}
