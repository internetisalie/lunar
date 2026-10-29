package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-566. A call to a method declared in another file is checked against the method's declared
 * `---@param` types, and each mismatch is reported at the offending argument. Every case asserts
 * that `C:mix` is stub-backed before highlighting, so the method cannot silently become in-file.
 */
@RunWith(JUnit4::class)
class LuaMethodArgumentTypeTest : IndexedBasePlatformTestCase() {
    private val defs =
        """
        ---@class C
        local C = {}
        ---@param a string
        ---@param b? string
        function C:m(a, b) end
        ---@param a string
        function C.f(a) end
        ---@param a string
        ---@param b number
        function C:mix(a, b) end
        ---@param cb fun(x: string)
        function C:each(cb) end
        ---@param opts? vim.api.keyset.create_autocmd | {buffer: false, win?:boolean}
        function C:on(opts) end
        ---@param opts vim.api.keyset.create_autocmd | {buffer: false, win?:boolean}
        function C:onRequired(opts) end
        ---@class D : C
        local D = {}
        ---@param a string
        function gg(a) end
        return C
        """.trimIndent()

    private fun mixStubOrNull() =
        runReadAction {
            StubIndex
                .getElements(
                    LuaGlobalDeclarationIndex.KEY,
                    "C:mix",
                    project,
                    GlobalSearchScope.allScope(project),
                    LuaFuncDecl::class.java,
                ).single()
                .stub
        }

    private fun assignabilityHits(caller: String): List<String> {
        myFixture.addFileToProject("defs.lua", defs)
        assertNotNull("defs.lua's C:mix must be stub-backed before highlighting", mixStubOrNull())
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("t.lua", caller)
        val callerText = myFixture.file.text
        return myFixture
            .doHighlighting()
            .filter { it.description?.let { text -> "is not assignable" in text || "arguments:" in text } == true }
            .map { "${it.description}@${callerText.substring(it.startOffset, it.endOffset)}" }
            .distinct()
    }

    private fun onC(call: String) = assignabilityHits("---@type C\nlocal c\n$call\n")

    /** BUG-11: the caller shape that reaches the user as an ERROR (lazynvim `view/init.lua:81`). */
    private fun onSubclassSelf(call: String) =
        assignabilityHits(
            "---@class E : C\nlocal E = {}\nfunction E.create()\n  local self = setmetatable({}, { __index = E })\n" +
                "  ---@cast self E\n  $call\nend\n",
        )

    /** Every tier, HYPOTHESIS included: the `---@type C` receiver files its collapse there. */
    private fun nilDemandsAnyTier(call: String): List<String> {
        assignabilityHits("---@type C\nlocal c\n$call\n")
        return LuaTypesSnapshot
            .forFile(myFixture.file as LuaFile)
            .getErrors()
            .map { "${it.severity}:${it.message}" }
            .filter { it.endsWith("is not assignable to nil") }
    }

    @Test
    fun testColonCallReportsEachWrongArgument() =
        assertEquals(
            listOf("number is not assignable to string@2", "string is not assignable to number@\"x\""),
            onC("c:mix(2, \"x\")"),
        )

    @Test
    fun testColonCallSelfOffsetIsSilent() = assertEquals(emptyList<String>(), onC("c:mix(\"x\", 2)"))

    @Test
    fun testWrongOptionalArgumentIsReported() =
        assertEquals(listOf("number is not assignable to union nil | string@1"), onC("c:m(\"x\", 1)"))

    @Test
    fun testExplicitNilToOptionalIsSilent() = assertEquals(emptyList<String>(), onC("c:m(\"x\", nil)"))

    @Test
    fun testOmittedOptionalIsSilent() = assertEquals(emptyList<String>(), onC("c:m(\"x\")"))

    @Test
    fun testDotCallReportsWrongArgument() = assertEquals(listOf("number is not assignable to string@1"), onC("c.f(1)"))

    @Test
    fun testInheritedMethodReportsWrongArgument() =
        assertEquals(
            listOf("number is not assignable to string@2", "string is not assignable to number@\"x\""),
            assignabilityHits("---@type D\nlocal d\nd:mix(2, \"x\")\n"),
        )

    @Test
    fun testCallbackSlotIsSilent() = assertEquals(emptyList<String>(), onC("c:each(function(x) end)"))

    @Test
    fun testFreeGlobalStillReports() =
        assertEquals(listOf("number is not assignable to string@1"), assignabilityHits("gg(1)\n"))

    @Test
    fun testOptionalParamOfUnresolvedTypeAcceptsATable() =
        assertEquals(emptyList<String>(), onSubclassSelf("self:on({ pattern = \"x\" })"))

    @Test
    fun testOptionalParamOfUnresolvedTypeDemandsNoNil() =
        assertEquals(emptyList<String>(), nilDemandsAnyTier("c:on({ pattern = \"x\" })"))

    @Test
    fun testRequiredParamOfUnresolvedTypeAcceptsATable() =
        assertEquals(emptyList<String>(), onSubclassSelf("self:onRequired({ pattern = \"x\" })"))

    @Test
    fun testWrongArgumentToSubclassOptionalStillReports() =
        assertEquals(listOf("number is not assignable to union nil | string@1"), onSubclassSelf("self:m(\"x\", 1)"))
}
