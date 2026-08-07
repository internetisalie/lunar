package net.internetisalie.lunar.lang.insight.hint

class CrossFileInlayHintsTest : IndexedDeclarativeInlayHintsTest() {
    fun testCrossFileParameterHints() {
        myFixture.addFileToProject(
            "other.lua",
            """
            function setup_config(speed, force) end

            ---@param speed number
            ---@param force number
            function setup_config2(speed, force) end
            """.trimIndent(),
        )

        doLuaTestProvider(
            "test.lua",
            """
            setup_config(/*<# speed: #>*/5000, /*<# force: #>*/3)
            setup_config2(/*<# speed: #>*/5000, /*<# force: #>*/3)
            """.trimIndent(),
            LuaTypeInlayHintProvider(),
        )
    }
}
