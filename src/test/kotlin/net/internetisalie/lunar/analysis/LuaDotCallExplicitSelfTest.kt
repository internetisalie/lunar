package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-8. `c.add(c, "x")` passes the receiver that `function C:add(a)` leaves implicit, so `c` fills
 * `self` and `"x"` fills `a`. The reverse holds too: `c:ex("x")` fills `function C.ex(self, a)`'s
 * explicit `self`. Each method case runs on the stub arm (the method is in another file) and, where
 * the report pins it, the AST arm (the method is in the open file).
 */
@RunWith(JUnit4::class)
class LuaDotCallExplicitSelfTest : IndexedBasePlatformTestCase() {
    private val defs =
        """
        ---@class C
        ---@field super C
        local C = {}
        ---@param a string
        function C:add(a) end
        ---@param a string
        ---@param b string
        function C.dot(a, b) end
        ---@param self C
        ---@param a string
        function C.ex(self, a) end
        return C
        """.trimIndent()

    private fun hits(text: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { "arguments" in it || "not assignable" in it }
            .distinct()
            .sorted()
    }

    private fun addStubBackedDefs() {
        myFixture.addFileToProject("defs.lua", defs)
        val stub =
            runReadAction {
                StubIndex
                    .getElements(
                        LuaGlobalDeclarationIndex.KEY,
                        "C:add",
                        project,
                        GlobalSearchScope.allScope(project),
                        LuaFuncDecl::class.java,
                    ).single()
                    .stub
            }
        assertNotNull("STUB arm: defs.lua's method must be stub-backed before highlighting", stub)
    }

    private fun stubArmHits(call: String): List<String> {
        addStubBackedDefs()
        return hits("---@type C\nlocal c\n$call\n")
    }

    private fun astArmHits(call: String): List<String> =
        hits("${defs.replace("return C", "")}\n---@type C\nlocal c\n$call\n")

    @Test
    fun testDotCallWithExplicitSelfIsSilentCrossFile() =
        assertEquals(emptyList<String>(), stubArmHits("c.add(c, \"x\")"))

    @Test
    fun testDotCallWithExplicitSelfIsSilentInFile() = assertEquals(emptyList<String>(), astArmHits("c.add(c, \"x\")"))

    @Test
    fun testColonCallOfColonMethodIsSilentCrossFile() = assertEquals(emptyList<String>(), stubArmHits("c:add(\"x\")"))

    @Test
    fun testColonCallOfColonMethodIsSilentInFile() = assertEquals(emptyList<String>(), astArmHits("c:add(\"x\")"))

    @Test
    fun testDotCallOfDotFunctionIsSilentCrossFile() =
        assertEquals(emptyList<String>(), stubArmHits("c.dot(\"x\", \"y\")"))

    @Test
    fun testDotCallOfDotFunctionIsSilentInFile() = assertEquals(emptyList<String>(), astArmHits("c.dot(\"x\", \"y\")"))

    @Test
    fun testDotCallWithExplicitSelfCountsWrittenArgumentsCrossFile() =
        assertEquals(listOf("Too many arguments: expected at most 2, got 3"), stubArmHits("c.add(c, \"x\", \"y\")"))

    @Test
    fun testDotCallWithExplicitSelfCountsWrittenArgumentsInFile() =
        assertEquals(listOf("Too many arguments: expected at most 2, got 3"), astArmHits("c.add(c, \"x\", \"y\")"))

    @Test
    fun testColonCallWithTooManyArgumentsIsReportedCrossFile() =
        assertEquals(listOf("Too many arguments: expected at most 1, got 2"), stubArmHits("c:add(\"x\", \"y\")"))

    @Test
    fun testDotCallOmittingReceiverIsTooFewCrossFile() =
        assertEquals(listOf("Too few arguments: expected at least 2, got 1"), stubArmHits("c.add(\"x\")"))

    @Test
    fun testDotCallOmittingReceiverIsTooFewInFile() =
        assertEquals(listOf("Too few arguments: expected at least 2, got 1"), astArmHits("c.add(\"x\")"))

    @Test
    fun testColonCallOfExplicitSelfFunctionIsSilentCrossFile() =
        assertEquals(emptyList<String>(), stubArmHits("c:ex(\"x\")"))

    @Test
    fun testDotCallOfExplicitSelfFunctionIsSilentCrossFile() =
        assertEquals(emptyList<String>(), stubArmHits("c.ex(c, \"x\")"))

    @Test
    fun testSuperChainDotCallWithExplicitSelfIsSilent() {
        addStubBackedDefs()
        val caller =
            """
            ---@class K : C
            local K = {}
            ---@param a string
            function K:add(a)
              self.super.add(self, a)
            end
            """.trimIndent()
        assertEquals(emptyList<String>(), hits(caller))
    }

    @Test
    fun testColonCallOfParameterlessFunFieldIsSilent() {
        val caller = "---@class G\n---@field f fun()\nlocal G = {}\n---@type G\nlocal g\ng:f()\ng:f(1)\ng.f()\n"
        assertEquals(emptyList<String>(), hits(caller))
    }

    @Test
    fun testColonCallOfParameterlessDotFunctionIsSilent() {
        myFixture.addFileToProject(
            "h.lua",
            "---@class H\nlocal H = {}\n---@return string\nfunction H.f() end\nreturn H\n",
        )
        assertEquals(emptyList<String>(), hits("---@type H\nlocal h\nh:f()\nh.f()\n"))
    }

    /**
     * A method value against a union demand goes through the dry-run `isFunctionCompatible`, which
     * picks the union member. `c.add`'s `a` lines up with the callback's `x`, not its `self`.
     */
    private val unionCallback = "---@param cb string|fun(self: C, x: string)\nlocal function take(cb) end\ntake(c.add)"

    @Test
    fun testMethodMatchesUnionCallbackWithExplicitSelfInFile() =
        assertEquals(emptyList<String>(), astArmHits(unionCallback))

    @Test
    fun testMethodMatchesUnionCallbackWithExplicitSelfCrossFile() =
        assertEquals(emptyList<String>(), stubArmHits(unionCallback))

    @Test
    fun testMethodMatchesCallbackWithExplicitSelfInFile() =
        assertEquals(
            emptyList<String>(),
            astArmHits("---@param cb fun(self: C, x: string)\nlocal function take(cb) end\ntake(c.add)"),
        )

    private data class CallbackShape(
        val fnTag: String,
        val call: String,
        val close: String,
        val wire: String,
    )

    private fun callbackHits(shape: CallbackShape): List<String> =
        hits(
            "---@class F\nlocal M = {}\n\n${shape.close}\n\n---@param ${shape.fnTag}\nfunction M:on_key(fn)\n" +
                "  ${shape.call}\nend\n\nfunction M:setup()\n  ${shape.wire}\nend\n",
        )

    private val colonClose = "---@param n? number\nfunction M:close(n)\n  return n\nend"
    private val dotClose = "---@param n? number\nfunction M.close(n)\n  return n\nend"

    /**
     * BUG-10. A colon method wired in as a callback and called `fn(self)` aligns by `implicitSelf`
     * in the reporting path `checkFunctionCompatibility`, so `self` does not land on `n`. These pin
     * that path; the dry-run `isFunctionCompatible` is pinned by the union-callback tests above.
     */
    @Test
    fun testColonMethodValueCalledWithSelfIsSilent() =
        assertEquals(
            emptyList<String>(),
            callbackHits(CallbackShape("fn fun(self?)", "fn(self)", colonClose, "self:on_key(self.close)")),
        )

    @Test
    fun testOptionalColonMethodFallbackCalledWithSelfIsSilent() =
        assertEquals(
            emptyList<String>(),
            callbackHits(
                CallbackShape("fn? fun(self)", "if fn then fn(self) end", colonClose, "self:on_key(self.close)"),
            ),
        )

    @Test
    fun testUnwiredSelfCallbackIsSilent() =
        assertEquals(
            emptyList<String>(),
            callbackHits(CallbackShape("fn fun(self?)", "fn(self)", colonClose, "self:on_key(function() end)")),
        )

    @Test
    fun testDotFunctionValueCalledWithSelfStillReports() {
        val found = callbackHits(CallbackShape("fn fun(self?)", "fn(self)", dotClose, "self:on_key(M.close)"))
        assertTrue(found.toString(), "{ ... } | F is not assignable to nil | number" in found)
    }
}
