package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Phase 5: hererocks layout detection (TC-7/8). */
class HererocksEnvDetectorTest : BasePlatformTestCase() {

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
}
