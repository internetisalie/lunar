package net.internetisalie.lunar.lang.insight.hint

import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase

class LuaParameterInlayHintsTest : DeclarativeInlayHintsProviderTestCase() {
    fun testBasicParameterHints() {
        doTestProvider("test.lua", """
            local function move(posX/*<# : number #>*/, posY/*<# : number #>*/) end
            move(/*<# posX: #>*/10, /*<# posY: #>*/20)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testColonCallSuppressesSelf() {
        doTestProvider("test.lua", """
            local obj/*<# : { ... } #>*/ = {}
            function obj:method(value) end
            obj:method(5) -- suppressed because only one param after self
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testMultipleParametersColonCall() {
        doTestProvider("test.lua", """
            local obj/*<# : { ... } #>*/ = {}
            function obj:move(posX, posY) end
            obj:move(/*<# posX: #>*/10, /*<# posY: #>*/20)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionWhenNameMatches() {
        doTestProvider("test.lua", """
            local function move(posX/*<# : number #>*/, posY/*<# : number #>*/) end
            local posX/*<# : number #>*/, posY/*<# : number #>*/ = 1, 2
            move(posX, posY)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionForSingleParameter() {
        doTestProvider("test.lua", """
            local function log(message/*<# : string #>*/) end
            log("hello")
        """.trimIndent(), LuaTypeInlayHintProvider())
    }
}
