package net.internetisalie.lunar.lang.insight.hint

import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType

class LuaReturnTypeInlayHintsTest : DeclarativeInlayHintsProviderTestCase() {
    fun testReturnLiteral() {
        doTestProvider("test.lua", """
            local function double()/*<# : number #>*/
                return 42
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testReturnMultiple() {
        doTestProvider("test.lua", """
            local function pair()/*<# : number, string #>*/
                return 1, "one"
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }
}
