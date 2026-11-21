package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-110. A variable's `read` must depend only on the set of demands it reaches, never on the order
 * the walk meets them in. The fold used to take the first demand in down-set insertion order, so the
 * members of one cycle — which reach exactly the same demands — could read differently, and so could
 * one variable whose edges were added in a different order.
 */
@RunWith(JUnit4::class)
class LuaReadFoldOrderTest : BasePlatformTestCase() {
    private fun anchor(): PsiElement = myFixture.addFileToProject("anchor.lua", "local x = 1\n")

    /** Every member of a cycle reaches the same demands, so every member reads the same type. */
    @Test
    fun theMembersOfACycleReadTheSameType() {
        val graph = LuaTypeGraph()
        val element = anchor()
        val concat = graph.use(element, LuaGraphType.Trait.Stringable)
        val length = graph.use(element, LuaGraphType.Trait.Lengthable)
        val first = graph.variable(element)
        val second = graph.variable(element)
        graph.addEdge(first, concat)
        graph.addEdge(second, length)
        graph.addEdge(first, second)
        graph.addEdge(second, first)

        assertEquals(LuaGraphType.Trait.Stringable, first.read)
        assertEquals(LuaGraphType.Trait.Stringable, second.read)
    }

    /** The same demands, wired in either order, give the same read: the first-created one. */
    @Test
    fun theOrderEdgesAreAddedInDoesNotChangeTheRead() {
        val element = anchor()
        assertEquals(LuaGraphType.Trait.Stringable, readWiringInOrder(element, reversed = false))
        assertEquals(LuaGraphType.Trait.Stringable, readWiringInOrder(element, reversed = true))
    }

    /** BUG-553's precedence holds whatever the order: a trait admitting no table outranks a table demand. */
    @Test
    fun aTableExcludingTraitOutranksATableDemandCreatedBeforeIt() {
        val graph = LuaTypeGraph()
        val element = anchor()
        val table = graph.use(element, LuaGraphType.Table(localMembers = mapOf("sub" to graph.variable(element))))
        val concat = graph.use(element, LuaGraphType.Trait.Stringable)
        val variable = graph.variable(element)
        graph.addEdge(variable, concat)
        graph.addEdge(variable, table)

        assertEquals(LuaGraphType.Trait.Stringable, variable.read)
    }

    /**
     * Penlight `Date.lua`'s shape: `sec` cycles through `finish`'s parameter and return. `finish`'s
     * `..` is written before the caller's `#sec`, so both orders of the caller's statements read the
     * call result as a `string`. The second order used to read it as `#`'s `any[] | string | { ... }`.
     */
    @Test
    fun aValueCyclingThroughAParameterReadsTheSameWhicheverCallComesFirst() {
        assertEquals("string", callResultType(callBeforeLength = true))
        assertEquals("string", callResultType(callBeforeLength = false))
    }

    /**
     * Penlight `Date.lua`'s `ch`: `append = table.insert` converts `table.insert`, and its `pos:
     * integer` demand, before `ch` exists. That demand sits at the call passing `ch`, after the `..`,
     * so `ch` reads as the string the concatenation demands, not as an integer.
     */
    @Test
    fun aConvertedParameterDemandSitsAtTheCallNotWhereItWasConverted() {
        myFixture.configureByText(
            "fields.lua",
            "local append = table.insert\n" +
                "local function parse(fmt, i, patt)\n" +
                "  local ch = fmt:sub(i, i)\n" +
                "  local message = \"field: \" .. ch\n" +
                "  append(patt, ch)\n" +
                "  return <caret>ch\n" +
                "end\n",
        )
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        val nameRef = PsiTreeUtil.getParentOfType(leaf, LuaNameRef::class.java, false)
        assertNotNull("No name reference at the caret", nameRef)
        val type = nameRef?.let { LuaTypesSnapshot.forFile(myFixture.file).getValueType(it) }
        assertEquals("string", type?.displayName())
    }

    /**
     * The class member `id` converts `fun(x: T): T` before the method's own `---@param`/`---@return`
     * demands are recorded, and `return x` reaches the converted return. That copy must rank where the
     * declaration meets it, after the annotations, so `x` still reads its declared `T`.
     */
    @Test
    fun aConvertedSignatureRanksAfterTheAnnotationsItWasConvertedFrom() {
        myFixture.configureByText(
            "generic.lua",
            "---@class G\nlocal G = {}\n---@generic T\n---@param x T\n---@return T\n" +
                "function G:id(x) return x end\n" +
                "local g = G\nlocal n = g:id(1)\n",
        )
        val decl = PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java)
        assertNotNull("No method declaration", decl)
        val signature = decl?.let { LuaTypesSnapshot.forFile(myFixture.file).getValueType(it) }
        val param = (signature as? LuaGraphType.Function)?.params?.firstOrNull()
        assertEquals("T", param?.node?.read?.displayName())
    }

    private fun readWiringInOrder(
        element: PsiElement,
        reversed: Boolean,
    ): LuaGraphType {
        val graph = LuaTypeGraph()
        val demands =
            listOf(
                graph.use(element, LuaGraphType.Trait.Stringable),
                graph.use(element, LuaGraphType.Trait.Lengthable),
            )
        val variable = graph.variable(element)
        (if (reversed) demands.reversed() else demands).forEach { graph.addEdge(variable, it) }
        return variable.read
    }

    private fun callResultType(callBeforeLength: Boolean): String? {
        val earlyCall = if (callBeforeLength) "  sec = finish(p, sec)\n" else ""
        myFixture.configureByText(
            "date.lua",
            "local function finish(p, sec)\n" +
                "  if p then sec = sec .. p end\n" +
                "  return sec\n" +
                "end\n" +
                "local function parse(p, h)\n" +
                "  local sec\n" +
                earlyCall +
                "  sec = h:sub(5, 6)\n" +
                "  if #sec == 0 then sec = nil end\n" +
                "  sec = finish(p, sec)\n" +
                "  return sec\n" +
                "end\n",
        )
        val call =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, LuaFuncCall::class.java)
                .single { it.text == "h:sub(5, 6)" }
        return LuaTypesSnapshot.forFile(myFixture.file).getValueType(call).displayName()
    }
}
