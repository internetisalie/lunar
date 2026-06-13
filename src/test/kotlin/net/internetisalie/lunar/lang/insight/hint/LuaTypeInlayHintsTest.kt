package net.internetisalie.lunar.lang.insight.hint

class LuaTypeInlayHintsTest : LuaInlayHintsTestCase() {
    fun testLocalVariable() {
        doLuaTestProvider("test.lua", """
            local x/*<# : number #>*/ = 42
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testArrayType() {
        doLuaTestProvider("test.lua", """
            ---@type string[]
            local tags = { "lua", "intellij", "lunar" }
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionWithExplicitType() {
        doLuaTestProvider("test.lua", """
            ---@type number
            local x = 42
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionWithClass() {
        doLuaTestProvider("test.lua", """
            ---@class User
            local User = {}
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testSuppressionWithAlias() {
        doLuaTestProvider("test.lua", """
            ---@alias MyString string
            local x = "hi"
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testInferredArrayInAssignment() {
        doLuaTestProvider("test.lua", """
            ---@type string[]
            local tags = {}
            local other/*<# : { ... } | string[] #>*/ = tags
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testFunctionParameters() {
        doLuaTestProvider("test.lua", """
            local function greet(name/*<# : string #>*/, age/*<# : number #>*/)
            end
            greet(/*<# name: #>*/"John", /*<# age: #>*/30)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testUserReportedBug() {
        // This should show NO inlay hints on current_user because of @type User
        // and no errors.
        doLuaTestProvider("test.lua", """
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
        doLuaTestProvider("test.lua", """
            local is_active/*<# : boolean #>*/ = true
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testMultipleAssignment() {
        doLuaTestProvider("test.lua", """
            local a/*<# : number #>*/, b/*<# : string #>*/ = 10, "hello"
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testTypePropagation() {
        doLuaTestProvider("test.lua", """
            local name/*<# : string #>*/ = "Lunar"
            local another_name/*<# : string #>*/ = name
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testFunctionReturnValue() {
        doLuaTestProvider("test.lua", """
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
        doLuaTestProvider("test.lua", """
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
        doLuaTestProvider("test.lua", """
            ---@param input string | number
            local function handle(input)
                local x/*<# : number | string #>*/ = input
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testParameterizedTable() {
        doLuaTestProvider("test.lua", """
            ---@type table<str, num>
            local scores = { player1 = 100 }
            local s/*<# : { ... } | table<str, num> #>*/ = scores
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testHigherOrderFunction() {
        doLuaTestProvider("test.lua", """
            ---@param callback fun(msg: string): boolean
            local function process(callback)/*<# : boolean #>*/
                return callback("data")
            end

            process(function(m/*<# : string #>*/)/*<# : boolean #>*/
                return #m > 0
            end)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testInferredReturnArithmetic() {
        doLuaTestProvider("test.lua", """
            local function double(n/*<# : number #>*/)/*<# : number #>*/
                return n * 2
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testHigherOrderFunctionInferred() {
        doLuaTestProvider("test.lua", """
            local function process(callback/*<# : fun(m) #>*/)/*<# : boolean #>*/
                return callback("data")
            end

            process(function(m/*<# : string #>*/)/*<# : boolean #>*/
                return #m > 0
            end)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testGenericClassUsage() {
        doLuaTestProvider("test.lua", """
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
        doLuaTestProvider("test.lua", """
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
            function User:getDisplayName()/*<# : string #>*/
                return self.username .. " (#" .. self.id .. ")"
            end

            -- To trigger graph build and checkTypes
            local x/*<# : { ... } | User #>*/ = current_user
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testFunctionReturnType() {
        doLuaTestProvider("test.lua", """
            local function f()/*<# : number #>*/
                return 1
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testFunctionMultipleReturnTypes() {
        doLuaTestProvider("test.lua", """
            local function f()/*<# : number, string #>*/
                return 1, "two"
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testFunctionReturnTypeSuppression() {
        doLuaTestProvider("test.lua", """
            ---@return number
            local function f()
                return 1
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }
}
