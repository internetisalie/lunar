package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.project.guessProjectDir
import net.internetisalie.lunar.settings.LuaProjectSettings

/** Phase 5: hererocks layout detection (TC-7/8) + set-based skip-check (ROCKS-15 remediation). */
class HererocksEnvDetectorTest : EnvSettingsTestCase() {

    fun testDetectsHererocksLayout() {
        myFixture.addFileToProject(".lua/bin/lua", "#!/bin/sh\n")
        myFixture.addFileToProject(".lua/bin/luarocks", "#!/bin/sh\n")

        val detected = HererocksEnvDetector.detect(project)
        val base = project.guessProjectDir()?.path
        assertEquals("$base/.lua", detected)
    }

    fun testNoLayoutReturnsNull() {
        myFixture.addFileToProject("src/main.lua", "return {}\n")
        assertNull(HererocksEnvDetector.detect(project))
    }

    /**
     * ROCKS-15 remediation defect B: a directory already present in [resolveAllEnvs] — even when the
     * legacy [State.hererocksEnv] field is null — is "known" and must not be re-offered a Bind on
     * reopen. This is exactly the check [HererocksDetectStartup] uses to suppress the repeat prompt.
     */
    @Suppress("DEPRECATION")
    fun testKnownDirectoryWhenInSetWithNullLegacyField() {
        myFixture.addFileToProject(".lua/bin/lua", "#!/bin/sh\n")
        myFixture.addFileToProject(".lua/bin/luarocks", "#!/bin/sh\n")
        val detected = HererocksEnvDetector.detect(project) ?: error("layout should detect")

        val settings = LuaProjectSettings.getInstance(project)
        settings.state.hererocksEnvs = mutableListOf(HererocksEnvState(id = "A", directory = detected))
        settings.state.hererocksEnv = null

        assertTrue("env in set must count as known", HererocksEnvDetector.isKnownDirectory(project, detected))
    }

    fun testUnknownDirectoryWhenSetEmpty() {
        assertFalse(HererocksEnvDetector.isKnownDirectory(project, "/nowhere/.lua"))
    }
}
