package net.internetisalie.lunar.analysis.luacheck

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaCheckInspectionGroupingTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaCheckInspection())
    }

    private fun resolveWrapper(): InspectionToolWrapper<*, *> {
        val ep =
            LocalInspectionEP.LOCAL_INSPECTION.extensionList
                .firstOrNull { it.shortName == LuaCheckInspection.SHORT_NAME }
                ?: error("LuaCheck localInspection EP not registered")
        return LocalInspectionToolWrapper(ep)
    }

    fun testInspectionToolIsRegistered() {
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        assertNotNull(
            "LuaCheck inspection tool must be registered in the current profile",
            profile.getInspectionTool(LuaCheckInspection.SHORT_NAME, project),
        )
    }

    fun testGroupPathIsLuaLuacheck() {
        assertOrderedEquals(resolveWrapper().groupPath.asList(), listOf("Lua", "Luacheck"))
    }

    fun testAnnotatorPairedShortName() {
        assertEquals("LuaCheck", LuaCheckAnnotator().pairedBatchInspectionShortName)
    }

    fun testInspectionShortName() {
        assertEquals("LuaCheck", LuaCheckInspection().shortName)
    }

    fun testDefaultLevelWarningAndEnabled() {
        val wrapper = resolveWrapper()
        assertEquals("WARNING", wrapper.defaultLevel.severity.name)
        assertSame(HighlightDisplayLevel.WARNING, wrapper.defaultLevel)

        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        assertNotNull(
            "LuaCheck must resolve in the current profile",
            profile.getInspectionTool(LuaCheckInspection.SHORT_NAME, project),
        )
        val displayKey =
            HighlightDisplayKey.find(LuaCheckInspection.SHORT_NAME)
                ?: error("HighlightDisplayKey for LuaCheck must exist after profile init")
        assertTrue("LuaCheck inspection must be enabled by default", profile.isToolEnabled(displayKey))
    }
}
