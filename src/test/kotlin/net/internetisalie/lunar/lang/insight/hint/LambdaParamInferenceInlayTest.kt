package net.internetisalie.lunar.lang.insight.hint

/**
 * TYPE-10-05 (TC 9): user-visible surfacing. The expected-type → lambda-parameter inference is
 * observable through the type inlay hint on the lambda parameter (which renders on a `LuaNameRef`
 * whose parent is a `LuaNameList` inside a `LuaParList`). A lambda passed into a `fun(x: string)`-
 * typed callee slot shows `x: string`, proving the propagated type reaches a real editor surface
 * — not only `getValueType`. Uses the LuaCATS-local callee form (no target/index fixture needed);
 * the live Redis `keys`/`keys[1]:` surface is covered by human-verification-checklists.
 */
class LambdaParamInferenceInlayTest : LuaInlayHintsTestCase() {

    fun testSeededLambdaParamShowsTypeHint_TC9() {
        doLuaTestProvider(
            "test.lua",
            """
            ---@param cb fun(item: string)
            local function run(cb) end
            run(function(item/*<# : string #>*/)/*<# : nil #>*/ end)
            """.trimIndent(),
            LuaTypeInlayHintProvider(),
        )
    }
}
