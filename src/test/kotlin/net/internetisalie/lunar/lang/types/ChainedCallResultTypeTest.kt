package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-100. `a():b()` is one call with two segments, and the engine typed the whole chain as `a()`'s
 * result, so every demand on `a():b()` was checked against the wrong class.
 */
@RunWith(JUnit4::class)
class ChainedCallResultTypeTest : BasePlatformTestCase() {
    private val definitions =
        """
        ---@class Node
        local Node = {}
        Node.__index = Node
        function Node:each() end

        ---@return Holder
        function Node:owner() end

        ---@class Gen
        ---@field proto string
        local mt = {}
        mt.__index = mt

        ---@return Node
        function mt:resolve()
            return setmetatable({}, Node)
        end

        ---@class Holder
        ---@field gen Gen

        ---@param h Holder
        ---@return Gen
        function getGeneric(h)
            return h.gen
        end
        """.trimIndent()

    private fun header(paramType: String) =
        """
        ---@type Holder
        local h
        ---@param n $paramType
        local function useNode(n) end

        """.trimIndent()

    private var definitionsAdded = false

    private fun errorsOf(
        source: String,
        fileName: String = "use.lua",
    ): List<String> {
        if (!definitionsAdded) myFixture.addFileToProject("defs.lua", definitions)
        definitionsAdded = true
        myFixture.configureByText(fileName, source)
        return runReadAction { LuaTypesSnapshot.forFile(myFixture.file).getErrors().map { it.message } }
            .filter { it.contains("is not assignable") }
    }

    private fun chainClassNames(chainText: String): Set<String> =
        runReadAction {
            val chain =
                PsiTreeUtil
                    .findChildrenOfType(myFixture.file, LuaFuncCall::class.java)
                    .single { it.text == chainText }
            classNamesOf(LuaTypesSnapshot.forFile(myFixture.file).getValueType(chain))
        }

    private fun classNamesOf(type: LuaGraphType): Set<String> =
        when (type) {
            is LuaGraphType.Union -> type.types.flatMapTo(mutableSetOf()) { classNamesOf(it) }
            is LuaGraphType.Table -> setOfNotNull(type.className)
            else -> emptySet()
        }

    @Test
    fun testChainedMethodResultMeetsDeclaredParam() =
        assertEquals(emptyList<String>(), errorsOf(header("Node") + "\nuseNode(getGeneric(h):resolve())\n"))

    @Test
    fun testChainedMethodResultMemberDemand() {
        val source =
            """
            ---@param h Holder
            local function f(h)
                local resolved = getGeneric(h):resolve()
                resolved:each()
            end
            """.trimIndent()
        assertEquals(emptyList<String>(), errorsOf(source))
    }

    /**
     * The chain reports what its split form `local g = a(); g:b()` reports. Before the fix the chain
     * was typed as `Gen` and met `---@param n Gen` silently.
     */
    @Test
    fun testChainedMismatchStillReported() {
        val chained = errorsOf(header("Gen") + "\nuseNode(getGeneric(h):resolve())\n")
        val split = errorsOf(header("Gen") + "\nlocal g = getGeneric(h)\nuseNode(g:resolve())\n", "split.lua")
        assertTrue("a Node passed for a Gen must be reported", chained.isNotEmpty())
        assertEquals(split.toSet(), chained.toSet())
    }

    @Test
    fun testSplitChainControl() {
        val body = "\nlocal g = getGeneric(h)\nuseNode(g:resolve())\nlocal r = g:resolve()\nr:each()\n"
        assertEquals(emptyList<String>(), errorsOf(header("Node") + body))
    }

    @Test
    fun testThreeSegmentChainMeetsDeclaredParam() =
        assertEquals(emptyList<String>(), errorsOf(header("Holder") + "\nuseNode(getGeneric(h):resolve():owner())\n"))

    // The type checks read a chain no parameter demands, because a declared parameter flows its
    // type back into the argument and would supply the expected class by itself.
    @Test
    fun testThreeSegmentChainTakesLastSegmentType() {
        errorsOf("---@type Holder\nlocal h\nlocal y = getGeneric(h):resolve():owner()\n")
        assertEquals(setOf("Holder"), chainClassNames("getGeneric(h):resolve():owner()"))
    }

    @Test
    fun testUnresolvedMiddleSegmentIsNotTheFirstSegmentType() {
        val errors = errorsOf("---@type Holder\nlocal h\nlocal y = getGeneric(h):nope():resolve()\n")
        assertEquals(emptyList<String>(), errors.filter { it.endsWith("to Node") })
        assertEquals(emptySet<String>(), chainClassNames("getGeneric(h):nope():resolve()"))
    }
}
