package net.internetisalie.lunar.lang.insight.hint

class LuaReturnTypeInlayHintsTest : LuaInlayHintsTestCase() {
    fun testReturnLiteral() {
        doLuaTestProvider("test.lua", """
            local function double()/*<# : number #>*/
                return 42
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testReturnMultiple() {
        doLuaTestProvider("test.lua", """
            local function pair()/*<# : number, string #>*/
                return 1, "one"
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }
}
