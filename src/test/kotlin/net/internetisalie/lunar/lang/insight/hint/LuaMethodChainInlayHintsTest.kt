package net.internetisalie.lunar.lang.insight.hint

/**
 * SYNTAX-07-07 method-chaining inlay hints. Covers requirement test cases TC-01..TC-09.
 *
 * Inline expected hints are encoded as `/*<# : Type #>*/` immediately after the call's
 * closing paren, matching the [LuaInlayHintsTestCase] dump harness.
 */
class LuaMethodChainInlayHintsTest : LuaInlayHintsTestCase() {

    override fun setUp() {
        super.setUp()
        // Isolate the method-chain provider: the shared harness runs every enabled provider, so
        // silence type/parameter hints to keep the expected dumps focused on chain hints only.
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, LuaTypeInlayHintProvider.PROVIDER_ID, false)
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, LuaTypeInlayHintProvider.PROVIDER_ID, false)
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, false)
        setOptionEnabled(LuaMethodChainInlayHintProvider.METHOD_CHAIN_OPTION_ID, LuaMethodChainInlayHintProvider.PROVIDER_ID, true)
    }

    /** TC-01 / REQ-01,02,03: a multi-line builder chain shows the return type after each call. */
    fun testMultiLineBuilderChain() {
        doLuaTestProvider("test.lua", """
            ---@class Builder
            local Builder = {}

            ---@return Builder
            function Builder:setName(n) end

            ---@return Builder
            function Builder:setAge(a) end

            local b = Builder
            b:setName("foo")
                :setAge(30)/*<# : Builder #>*/
        """.trimIndent(), LuaMethodChainInlayHintProvider())
    }

    /** TC-02 / REQ-05: a single-line chain produces no hints. */
    fun testSingleLineChainSuppressed() {
        doLuaTestProvider("test.lua", """
            ---@class Builder
            local Builder = {}

            ---@return Builder
            function Builder:m1() end

            ---@return Builder
            function Builder:m2() end

            local obj = Builder
            obj:m1():m2()
        """.trimIndent(), LuaMethodChainInlayHintProvider())
    }

    /** TC-05 / REQ-04: an unresolvable method on a new line shows no hint. */
    fun testUnresolvedMethodSuppressed() {
        doLuaTestProvider("test.lua", """
            ---@class Builder
            local Builder = {}

            ---@return Builder
            function Builder:known() end

            local obj = Builder
            obj:known()
                :unknown()
        """.trimIndent(), LuaMethodChainInlayHintProvider())
    }

    /** TC-07 / REQ-08: a `self` return on a new line resolves to the concrete receiver class. */
    fun testSelfResolvesToConcreteClass() {
        doLuaTestProvider("test.lua", """
            ---@class B
            local B = {}

            ---@return self
            function B:set() end

            B:set()
                :set()/*<# : B #>*/
        """.trimIndent(), LuaMethodChainInlayHintProvider())
    }

    /** TC-09 / REQ-05: only steps that start a new line are annotated in a partial chain. */
    fun testPartialSingleLineChain() {
        doLuaTestProvider("test.lua", """
            ---@class Builder
            local Builder = {}

            ---@return Builder
            function Builder:on() end

            ---@return Builder
            function Builder:one() end

            ---@return Builder
            function Builder:line() end

            ---@return Builder
            function Builder:finally() end

            local long = Builder
            long:on():one():line()
                :finally()/*<# : Builder #>*/
        """.trimIndent(), LuaMethodChainInlayHintProvider())
    }
}
