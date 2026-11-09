package net.internetisalie.lunar.rocks

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Verifies the relocated bridge scripts under `src/main/resources/lua/` are on the classpath and that
 * [LuaRocksBridgeFiles.ensureExtracted] materializes them on disk — the ROCKS-03-DR-06 gate. Uses a
 * platform fixture so `PathManager.getTempPath()` is initialized.
 */
@RunWith(JUnit4::class)
class LuaRocksBridgeFilesTest : BasePlatformTestCase() {
    @Test
    fun testScriptsAreExtractedFromClasspath() {
        val dir = LuaRocksBridgeFiles.ensureExtracted()
        val rockspec = LuaRocksBridgeFiles.rockspecScript()
        assertTrue("rockspec.lua should exist", rockspec.isRegularFile())
        assertTrue("json.lua should exist", dir.resolve("lunar/json.lua").isRegularFile())
        val export = dir.resolve("lunar/export.lua")
        assertTrue("export.lua should exist", export.isRegularFile())
        // The export.lua latent-bug fix must be present in the packaged copy.
        assertTrue("export.lua must iterate ipairs(names)", export.readText().contains("ipairs(names)"))
    }

    @Test
    fun testFirstExtractionOfARunReplacesAStaleCopy() {
        val rockspec = LuaRocksBridgeFiles.rockspecScript()
        rockspec.writeText("-- a previous plugin version's script")
        LuaRocksBridgeFiles.forgetExtraction()

        val refreshed = LuaRocksBridgeFiles.rockspecScript().readText()

        val bundled =
            requireNotNull(javaClass.classLoader.getResourceAsStream("lua/rockspec.lua")).use {
                String(it.readAllBytes())
            }
        assertEquals("BUG-50: an upgraded plugin must not run the old script", bundled, refreshed)
    }

    @Test
    fun testLuaPathTemplateReferencesExtractionDir() {
        val template = LuaRocksBridgeFiles.luaPathTemplate()
        val dir = LuaRocksBridgeFiles.ensureExtracted().toString()
        assertTrue(template.contains(dir))
        assertTrue(template.contains("?.lua"))
        assertTrue(template.contains("?/init.lua"))
    }
}
