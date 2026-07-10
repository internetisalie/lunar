package net.internetisalie.lunar.redis.resp

import com.intellij.openapi.progress.ProgressIndicator
import java.io.InputStream

/**
 * Wraps a socket [InputStream] so every read checks cancellation first (engineering-contract
 * CANCELLATION EXHAUSTIVENESS).
 *
 * A cancelled [ProgressIndicator] throws `ProcessCanceledException` before the blocking read starts,
 * aborting an in-flight [RespClient.command]/`open` or handshake read without completing it
 * (TC-CANCEL-1). Adds no buffering — [RespCodec] owns framing.
 */
internal class CancellationAwareInputStream(
    private val delegate: InputStream,
    private val indicator: ProgressIndicator?,
) : InputStream() {

    override fun read(): Int {
        indicator?.checkCanceled()
        return delegate.read()
    }

    override fun read(destination: ByteArray, off: Int, len: Int): Int {
        indicator?.checkCanceled()
        return delegate.read(destination, off, len)
    }

    override fun available(): Int = delegate.available()

    override fun close() = delegate.close()
}
