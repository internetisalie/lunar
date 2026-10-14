package net.internetisalie.lunar.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BUG-456: the debuggee listener must bind loopback, never the wildcard address — the protocol
 * offers `EXEC` to whoever connects first.
 */
class TestLuaDebuggerListener {
    @Test
    fun testDebuggerListenerBindsLoopbackOnly() {
        LuaDebuggerController.openDebuggerListener(0).use { serverSocket ->
            val boundAddress = serverSocket.inetAddress
            assertFalse(boundAddress.isAnyLocalAddress, "listener bound the wildcard address $boundAddress")
            assertTrue(boundAddress.isLoopbackAddress, "listener bound non-loopback address $boundAddress")
        }
    }

    @Test
    fun testDebuggerListenerKeepsConnectTimeout() {
        LuaDebuggerController.openDebuggerListener(0).use { serverSocket ->
            assertEquals(LuaDebuggerController.CONNECT_TIMEOUT_MS, serverSocket.soTimeout)
        }
    }
}
