package net.internetisalie.lunar.lang.insight.hint

class LuaParameterInlayHintsTest : LuaInlayHintsTestCase() {
    fun testBasicParameterHints() {
        doLuaTestProvider("test.lua", """
            local function move(posX/*<# : number #>*/, posY/*<# : number #>*/) end
            move(/*<# posX: #>*/10, /*<# posY: #>*/20)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testColonCallSuppressesSelf() {
        doLuaTestProvider("test.lua", """
            local obj/*<# : { ... } #>*/ = {}
            function obj:method(value) end
            obj:method(5) -- suppressed because only one param after self
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testMultipleParametersColonCall() {
        doLuaTestProvider("test.lua", """
            local obj/*<# : { ... } #>*/ = {}
            function obj:move(posX, posY) end
            obj:move(/*<# posX: #>*/10, /*<# posY: #>*/20)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionWhenNameMatches() {
        doLuaTestProvider("test.lua", """
            local function move(posX/*<# : number #>*/, posY/*<# : number #>*/) end
            local posX/*<# : number #>*/, posY/*<# : number #>*/ = 1, 2
            move(posX, posY)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionForSingleParameter() {
        doLuaTestProvider("test.lua", """
            local function log(message/*<# : string #>*/) end
            log("hello")
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testLuaCatsParameterNames() {
        doLuaTestProvider("test.lua", """
            ---@param speed number
            ---@param force number
            local function apply(s/*<# : number #>*/, f/*<# : number #>*/) end
            apply(/*<# speed: #>*/10, /*<# force: #>*/20)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testUnionFunctionParameterHints() {
        doLuaTestProvider("test.lua", """
            ---@type fun(posX: number, posY: number) | nil
            local callback
            if callback then
                callback(/*<# posX: #>*/10, /*<# posY: #>*/20)
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testUnionMethodParameterHints() {
        doLuaTestProvider("test.lua", """
            ---@class Handler
            local Handler = {}
            
            ---@param posX number
            ---@param posY number
            function Handler:move(posX, posY) end
            
            ---@type Handler | nil
            local obj
            if obj then
                obj:move(/*<# posX: #>*/10, /*<# posY: #>*/20)
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testStdlibAssertDoesNotShowHints() {
        doLuaTestProvider("test.lua", """
            assert(true, "error message")
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testStdlibTableInsertDoesNotShowHints() {
        doLuaTestProvider("test.lua", """
            table.insert({}, 1, "value")
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testCustomFunctionShowsHints() {
        doLuaTestProvider("test.lua", """
            local function myAssert(valToCheck/*<# : boolean #>*/, message/*<# : string #>*/) end
            myAssert(/*<# valToCheck: #>*/true, /*<# message: #>*/"error message")
        """.trimIndent(), LuaTypeInlayHintProvider())
    }
}
