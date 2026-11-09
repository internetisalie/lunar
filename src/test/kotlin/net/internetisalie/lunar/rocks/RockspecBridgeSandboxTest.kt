package net.internetisalie.lunar.rocks

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

/** BUG-50: a rockspec is evaluated only in a trusted project, and only as declarative data. */
class RockspecBridgeSandboxTest : BasePlatformTestCase() {
    private lateinit var dir: Path

    override fun setUp() {
        super.setUp()
        assertTrue(
            "these tests need a real Lua at ~/bin/lua",
            RockspecRuntimeTestSupport.registerRealLuaRuntime(project),
        )
        dir = Files.createTempDirectory("lunar_bug50")
    }

    override fun tearDown() {
        try {
            TrustedProjects.setProjectTrusted(project, true)
            RockspecRuntimeTestSupport.reset(project)
        } finally {
            super.tearDown()
        }
    }

    fun testCleanRockspecEvaluatesInTrustedProject() {
        val data = RockspecBridge.read(project, rockspec("ok", "package = \"ok\"\nversion = \"1.0-1\""))

        assertEquals("ok", data?.packageName)
    }

    fun testUntrustedProjectEvaluatesNothing() {
        TrustedProjects.setProjectTrusted(project, false)

        assertNull(RockspecBridge.read(project, rockspec("ok", "package = \"ok\"\nversion = \"1.0-1\"")))
    }

    fun testRockspecCannotReachTheStandardLibrary() {
        val marker = dir.resolve("pwned")
        val body = "io.open([[$marker]], \"w\"):close()\npackage = \"evil\"\nversion = \"1.0-1\""

        val data = RockspecBridge.read(project, rockspec("evil", body))

        assertNull(data)
        assertFalse("the rockspec wrote a file", Files.exists(marker))
    }

    private fun rockspec(
        name: String,
        body: String,
    ): Path = dir.resolve("$name-1.0-1.rockspec").also { Files.writeString(it, body) }
}
