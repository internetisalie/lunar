package net.internetisalie.lunar.analysis.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** BUG-57: config files that declare their content as top-level globals are not flagged. */
class LuaGlobalCreationConfigFileTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaGlobalCreationInspection())
    }

    fun testOrdinaryLuaFileIsFlagged() {
        assertEquals(listOf("version", "build"), flaggedNames("main.lua"))
    }

    fun testRockspecIsNotFlagged() {
        assertEquals(emptyList<String>(), flaggedNames("vendored_rock-1.0-1.rockspec"))
    }

    fun testLuacheckrcIsNotFlagged() {
        assertEquals(emptyList<String>(), flaggedNames(".luacheckrc"))
    }

    fun testBustedConfigIsNotFlagged() {
        assertEquals(emptyList<String>(), flaggedNames(".busted"))
    }

    private fun flaggedNames(fileName: String): List<String> {
        myFixture.configureByText(fileName, "version = \"1.0-1\"\nbuild = { type = \"builtin\" }\n")
        return myFixture
            .doHighlighting()
            .filter { it.description?.startsWith("Global creation") == true }
            .map { it.description.removePrefix("Global creation '").removeSuffix("'") }
    }
}
