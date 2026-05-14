package net.internetisalie.lunar.lang.insight.hint

import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase

class LuaTypeInlayHintsTest : DeclarativeInlayHintsProviderTestCase() {
    fun testLocalVariable() {
        doTestProvider("test.lua", """
            local x/*<# : number #>*/ = 42
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testArrayType() {
        doTestProvider("test.lua", """
            ---@type string[]
            local tags = { "lua", "intellij", "lunar" }
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionWithExplicitType() {
        doTestProvider("test.lua", """
            ---@type number
            local x = 42
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionWithClass() {
        doTestProvider("test.lua", """
            ---@class User
            local User = {}
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionWithAlias() {
        doTestProvider("test.lua", """
            ---@alias MyString string
            local x = "hi"
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testInferredArrayInAssignment() {
        doTestProvider("test.lua", """
            ---@type string[]
            local tags = {}
            local other/*<# : { ... } | string[] #>*/ = tags
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testFunctionParameters() {
        doTestProvider("test.lua", """
            local function greet(name/*<# : string #>*/, age/*<# : number #>*/)
            end
            greet(/*<# name: #>*/"John", /*<# age: #>*/30)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testUserReportedBug() {
        // This should show NO inlay hints on current_user because of @type User
        // and no errors.
        doTestProvider("test.lua", """
            ---@class User
            ---@field id number
            ---@field username string
            ---@field email string | nil
            local User = {}

            ---@type User
            local current_user = { id = 1, username = "admin" }
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testBooleanLiteral() {
        doTestProvider("test.lua", """
            local is_active/*<# : boolean #>*/ = true
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testMultipleAssignment() {
        doTestProvider("test.lua", """
            local a/*<# : number #>*/, b/*<# : string #>*/ = 10, "hello"
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testTypePropagation() {
        doTestProvider("test.lua", """
            local name/*<# : string #>*/ = "Lunar"
            local another_name/*<# : string #>*/ = name
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testFunctionReturnValue() {
        doTestProvider("test.lua", """
            ---@param a number
            ---@param b number
            ---@return number
            local function add(a, b)
                return a + b
            end

            local sum/*<# : number #>*/ = add(5, 10)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testGenericFunction() {
        doTestProvider("test.lua", """
            ---@generic T
            ---@param value T
            ---@return T
            local function wrap(value)
                return value
            end

            local w1/*<# : string #>*/ = wrap("text")
            local w2/*<# : number #>*/ = wrap(123)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testUnionTypePropagation() {
        doTestProvider("test.lua", """
            ---@param input string | number
            local function handle(input)
                local x/*<# : string | number #>*/ = input
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testParameterizedTable() {
        doTestProvider("test.lua", """
            ---@type table<str, num>
            local scores = { player1 = 100 }
            local s/*<# : { ... } | table<str, num> #>*/ = scores
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testHigherOrderFunction() {
        doTestProvider("test.lua", """
            ---@param callback fun(msg: string): boolean
            local function process(callback)
                return callback("data")
            end

            process(function(m/*<# : string #>*/)
                return #m > 0
            end)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testGenericClassUsage() {
        doTestProvider("test.lua", """
            ---@generic K, V
            ---@class P<K, V>
            ---@field key K
            ---@field value V
            local P = {}

            ---@type P<str, num>
            local my_pair = { key = "age", value = 30 }

            local other/*<# : { ... } | P<str, num> #>*/ = my_pair
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testMemberFunctionConstructorIssue() {
        // This test specifically checks for the "Missing required field" error
        // on table constructors when a member function is defined on the class.
        doTestProvider("test.lua", """
            ---@class User
            ---@field id number
            ---@field username string
            ---@field email string | nil
            local User = {}

            ---@type User
            local current_user = {
                id = 1,
                username = "admin"
            }

            -- Member function using @self
            ---@param self User
            function User:getDisplayName()
                return self.username .. " (#" .. self.id .. ")"
            end

            -- To trigger graph build and checkTypes
            local x/*<# : { ... } | User #>*/ = current_user
        """.trimIndent(), LuaTypeInlayHintProvider())
    }
}
