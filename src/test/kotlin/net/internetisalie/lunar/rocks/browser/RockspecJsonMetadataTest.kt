package net.internetisalie.lunar.rocks.browser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.rocks.RockspecBridge
import net.internetisalie.lunar.rocks.RockspecRuntimeTestSupport
import java.nio.file.Files

/** BUG-68: a server rockspec that computes its fields is still described. */
class RockspecJsonMetadataTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            RockspecRuntimeTestSupport.reset(project)
        } finally {
            super.tearDown()
        }
    }

    fun testAComputedRockspecIsDescribedThroughTheBridge() {
        assertTrue("needs a real Lua at ~/bin/lua", RockspecRuntimeTestSupport.registerRealLuaRuntime(project))
        val text =
            """
            local package_name = "luacheck"
            local package_version = "1.2.0"
            package = package_name
            version = package_version .. "-1"
            description = { summary = "A static analyzer", license = "MIT",
                homepage = "https://github.com/lunarmodules/" .. package_name }
            dependencies = { "lua >= 5.1", "argparse >= 0.6.0" }
            if package_version == "dev" then version = "dev-1" end
            build = { type = "builtin", modules = { ["luacheck.main"] = "src/luacheck/main.lua" } }
            """.trimIndent()
        val file = Files.createTempFile("bug68", ".rockspec").also { Files.writeString(it, text) }

        assertNull("the literal reader cannot follow it", RockspecMetadataReader.read(project, text))
        val metadata = RockspecBridge.evaluate(project, file)?.let { RockspecJsonMetadata.read(it) }

        assertEquals("luacheck", metadata?.name)
        assertEquals("1.2.0-1", metadata?.version)
        assertEquals("MIT", metadata?.license)
        assertEquals("https://github.com/lunarmodules/luacheck", metadata?.homepage)
        assertEquals(listOf("lua >= 5.1", "argparse >= 0.6.0"), metadata?.dependencies)
        assertEquals(listOf("luacheck.main"), metadata?.modules)
    }

    fun testJsonThatIsNotARockspecIsNull() {
        assertNull(RockspecJsonMetadata.read("""{"version": "1.0-1"}"""))
        assertNull(RockspecJsonMetadata.read("not json"))
    }

    fun testInstalledRowsReadAsNameAndVersion() {
        assertEquals("inspect  3.1.2-0", installedRowText(InstalledRockRow("inspect", "3.1.2-0")))
    }
}
