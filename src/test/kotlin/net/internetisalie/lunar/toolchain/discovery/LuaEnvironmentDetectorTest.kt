package net.internetisalie.lunar.toolchain.discovery

import com.intellij.openapi.project.guessProjectDir
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase

/** TOOLING-02-14 / TC 17: descriptor-driven env detection + recorded-dir dedupe (design §3.6). */
class LuaEnvironmentDetectorTest : ToolchainSettingsTestCase() {

    fun testDetectsEnvShapedLayout() {
        myFixture.addFileToProject(".lua/bin/lua", "#!/bin/sh\n")
        myFixture.addFileToProject(".lua/bin/luarocks", "#!/bin/sh\n")

        val detected = LuaEnvironmentDetector.detect(project)
        val base = project.guessProjectDir()?.path
        assertEquals("$base/.lua", detected)
    }

    fun testEnvShapedRequiresRuntimeAndPackageManager() {
        myFixture.addFileToProject(".lua/bin/lua", "#!/bin/sh\n")
        assertNull("runtime alone is not env-shaped", LuaEnvironmentDetector.detect(project))
    }

    fun testNoLayoutReturnsNull() {
        myFixture.addFileToProject("src/main.lua", "return {}\n")
        assertNull(LuaEnvironmentDetector.detect(project))
    }

    fun testKnownDirectoryWhenRecorded() {
        myFixture.addFileToProject(".lua/bin/lua", "#!/bin/sh\n")
        myFixture.addFileToProject(".lua/bin/luarocks", "#!/bin/sh\n")
        val detected = LuaEnvironmentDetector.detect(project) ?: error("layout should detect")

        settings.upsertEnvironment(LuaEnvironmentState(id = "A", name = ".lua", rootDir = detected))

        assertTrue(
            "recorded env dir must count as known",
            LuaEnvironmentDetector.isKnownDirectory(project, detected)
        )
    }

    fun testUnknownDirectoryWhenNoneRecorded() {
        assertFalse(LuaEnvironmentDetector.isKnownDirectory(project, "/nowhere/.lua"))
    }
}
