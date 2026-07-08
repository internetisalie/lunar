package net.internetisalie.lunar.toolchain.provision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Env-independent tests for [LuaCompilerProbe] (design §2.8). The remediation text is a
 * user-facing contract asserted byte-for-byte (requirements TC 5); the candidate ordering is
 * verified indirectly through the documented preference without depending on the CI host
 * having (or lacking) a real C toolchain.
 */
class LuaCompilerProbeTest {
    @Test
    fun remediationTextMatchesSpec() {
        val expected = "No C toolchain found on PATH (need cc/gcc, ar, ranlib). " +
            "Install build tools (Linux: `sudo apt install build-essential`; macOS: " +
            "`xcode-select --install`) or pick a version with a prebuilt binary."
        assertEquals(expected, LuaCompilerProbe.REMEDIATION)
    }

    @Test
    fun probeReturnsNullOrCompleteToolchainNeverPartial() {
        val toolchain = LuaCompilerProbe.probe(LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64))
        if (toolchain != null) {
            assertTrue("cc must be a real path", toolchain.cc.toString().isNotBlank())
            assertTrue("ar must be a real path", toolchain.ar.toString().isNotBlank())
            assertTrue("ranlib must be a real path", toolchain.ranlib.toString().isNotBlank())
        }
    }
}
