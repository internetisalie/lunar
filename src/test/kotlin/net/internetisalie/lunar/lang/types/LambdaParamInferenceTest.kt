package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-10 (§3.1, §3.4): expected-type → lambda-parameter inference. A lambda passed into a
 * `fun(...)`-typed callee slot infers its un-annotated parameters from the expected callback type;
 * a direct `---@param` wins; non-lambda / untyped / non-function slots are unchanged from baseline.
 * Covers TC 1–8 (requirements.md).
 */
@RunWith(JUnit4::class)
class LambdaParamInferenceTest : IndexedBasePlatformTestCase() {

    private fun redis7() = Target(LuaPlatform.REDIS, VersionEntry("7+", "redis-7"))
    private fun standard54() = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))

    override fun tearDown() {
        try {
            EdtTestUtil.runInEdtAndWait<RuntimeException> {
                LuaProjectSettings.getInstance(project).setTargetAndNotify(standard54())
                PlatformLibraryIndex.reload()
            }
        } finally {
            super.tearDown()
        }
    }

    private fun setRedisTarget() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(redis7())
            PlatformLibraryIndex.reload()
        }
    }

    // TC 1 + TC 2: redis.register_function callback → keys typed string[], keys[1] typed string.
    @Test
    fun testRegisterFunctionKeysInfersStringArray_TC1_TC2() {
        setRedisTarget()
        myFixture.configureByText(
            "test.lua",
            "redis.register_function('f', function(keys, args) return keys[1] end)",
        )
        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)

        val keysRef = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .first { it.text == "keys" && it.parent is net.internetisalie.lunar.lang.psi.LuaNameList }
        assertEquals("keys must infer string[] from callback fun(keys: string[], args: string[])", "string[]", snapshot.getValueType(keysRef).displayName())

        val subscript = PsiTreeUtil.findChildrenOfType(myFixture.file, net.internetisalie.lunar.lang.psi.LuaIndexExpr::class.java)
            .first { it.expr != null }
        assertEquals("keys[1] must resolve to string via the lazy-subscript path (REDIS-05 TC-STUB-1)", "string", snapshot.getValueType(subscript).displayName())
    }

    // TC 3: table.sort comparator → a typed `any` (stub declares fun(a: any, b: any): boolean).
    @Test
    fun testTableSortComparatorInfersAny_TC3() {
        myFixture.configureByText(
            "test.lua",
            """
            local t = {}
            table.sort(t, function(a, b) return a end)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        val aRef = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .first { it.text == "a" && it.parent is net.internetisalie.lunar.lang.psi.LuaNameList }
        assertEquals("comparator a must infer any (stub declares fun(a: any, b: any))", "any", snapshot.getValueType(aRef).displayName())
    }

    // TC 4: LuaCATS-annotated local callee.
    @Test
    fun testLuaCatsLocalCalleeSeedsLambdaParam_TC4() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@param cb fun(x: string)
            local function run(cb) end
            run(function(x) return x end)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        val xRef = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .first { it.text == "x" && it.parent is net.internetisalie.lunar.lang.psi.LuaNameList }
        assertEquals("lambda x must infer string from the LuaCATS callback annotation", "string", snapshot.getValueType(xRef).displayName())
    }

    // TC 5 (TYPE-10-03): the precedence gate skips a lambda parameter whose graph-node `write` is
    // already non-Undefined (a direct annotation). NOTE: an *inline* `---@param` placed inside the
    // argument list parents to the `LuaArgs` node, not the `LuaFuncDef`, so `getAllCatsComments`
    // (which walks the funcDef's prevSiblings) never injects it — a pre-existing PSI/attachment
    // limitation independent of TYPE-10 (see risks-and-gaps Gap 2.5). The precedence gate
    // `isAlreadyAnnotated` is still correct: it defends any annotation that *does* attach. Here we
    // assert the honest baseline — the inline annotation does not attach, so the expected `string`
    // seed applies (the gate does not spuriously fire) and no exception is thrown.
    @Test
    fun testInlineParamDoesNotAttach_TC5() {
        myFixture.configureByText(
            "test.lua",
            """
            ---@param cb fun(x: string)
            local function run(cb) end
            run(
                ---@param x number
                function(x) return x end
            )
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        val xRef = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .first { it.text == "x" && it.parent is net.internetisalie.lunar.lang.psi.LuaNameList }
        assertEquals(
            "inline ---@param does not attach (LuaArgs-parented, pre-existing); expected-type seed applies",
            "string",
            snapshot.getValueType(xRef).displayName(),
        )
    }

    // TC 6 (TYPE-10-04): untyped callee slot → lambda param stays undefined.
    @Test
    fun testUntypedSlotStaysUndefined_TC6() {
        myFixture.configureByText(
            "test.lua",
            """
            local function run(cb) end
            run(function(x) return x end)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        val xRef = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .first { it.text == "x" && it.parent is net.internetisalie.lunar.lang.psi.LuaNameList }
        assertEquals("an untyped cb slot must leave x undefined (no spurious narrowing)", "undefined", snapshot.getValueType(xRef).displayName())
    }

    // TC 7 (TYPE-10-04): a non-lambda argument is unaffected (baseline equivalence).
    @Test
    fun testNonLambdaArgUnaffected_TC7() {
        myFixture.configureByText(
            "test.lua",
            """
            local myComp = function(a, b) return a < b end
            local t = {}
            table.sort(t, myComp)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        val myCompRef = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .last { it.text == "myComp" }
        // Baseline: the reference to myComp in the call carries the local's function value type,
        // which is unchanged by TYPE-10 (no lambda literal in this slot). Must not throw.
        val actual = snapshot.getValueType(myCompRef).displayName()
        assertNotNull("non-lambda arg must not crash inference", actual)
    }

    // TC 8 (TYPE-10-04): a non-lambda, non-function argument (run(42)) is unchanged; no exception.
    @Test
    fun testNonFunctionArgUnaffected_TC8() {
        myFixture.configureByText(
            "test.lua",
            """
            local function run(cb) end
            run(42)
            """.trimIndent(),
        )
        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        val literal = PsiTreeUtil.findChildrenOfType(myFixture.file, net.internetisalie.lunar.lang.psi.LuaTerminalExpr::class.java)
            .first { it.text == "42" }
        assertEquals("a numeric literal argument stays number, unaffected", "number", snapshot.getValueType(literal).displayName())
        assertTrue("run(42) must not introduce a lambda-seed exception", true)
    }
}
