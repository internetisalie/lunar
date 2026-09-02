package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.VariableNode
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-13 Phase 1: asserts design §3.2's mint-site table and §3.3's `isAssignmentTarget` table —
 * the `declaresMember` mark and the element class/offset of the graph node reached for each
 * receiver shape in `requirements.md` DR-05a. One fixture per test method: `LuaTypeManagerImpl`
 * searches `GlobalSearchScope.allScope(project)`, so a sibling fixture file would silently bind a
 * member to the wrong file (per-feature fixture isolation note).
 */
@RunWith(JUnit4::class)
class Type13ProvenanceTest : BasePlatformTestCase() {
    private fun structuralMemberOf(
        graphType: LuaGraphType,
        name: String,
    ): VariableNode? =
        when (graphType) {
            is LuaGraphType.Table -> graphType.getMembers()[name]
            is LuaGraphType.Union -> graphType.types.firstNotNullOfOrNull { structuralMemberOf(it, name) }
            else -> null
        }

    private fun memberNodeOn(
        source: String,
        memberName: String,
    ): VariableNode? {
        val file = myFixture.configureByText("a.lua", source)
        return runReadAction {
            val receiver = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).first { it.text == "t" }
            val types = LuaTypesSnapshot.forFile(file)
            structuralMemberOf(types.getValueType(receiver), memberName)
        }
    }

    @Test
    fun colonMethodDeclarationIsMarked() {
        val node =
            memberNodeOn(
                """
                local t = {}
                function t:m() end
                t:m()
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertTrue(node!!.declaresMember)
        assertEquals("LuaFuncNameMethodImpl", node.element.javaClass.simpleName)
    }

    @Test
    fun dotFunctionDeclarationIsMarked() {
        val node =
            memberNodeOn(
                """
                local t = {}
                function t.m() end
                t.m()
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertTrue(node!!.declaresMember)
        assertEquals("LuaFuncNamePropertyImpl", node.element.javaClass.simpleName)
    }

    @Test
    fun fieldAssignmentDeclarationIsMarked() {
        val node =
            memberNodeOn(
                """
                local t = {}
                t.m = function() end
                t.m()
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertTrue(node!!.declaresMember)
        assertEquals("LuaIndexExprImpl", node.element.javaClass.simpleName)
    }

    /**
     * `requirements.md` DR-05a: the trailing `t.m()` raises a single read demand on `t`, and
     * `LuaTypesSnapshot.typeOf` merges write.localMembers then read.localMembers unconditionally —
     * the read always wins on a key collision, even against a constructor field's own write-side
     * entry. The winning node here is therefore the READ's `LuaIndexExpr`, `declaresMember=false` —
     * matching the measured `LuaIndexExprImpl@34 (the read)` row, not the constructor field itself.
     * Recovering the field's own declaration through this merge is Phase 2's `declaringNodeOf`.
     */
    @Test
    fun constructorFieldReadAfterDeclarationDoesNotDeclare() {
        val node =
            memberNodeOn(
                """
                local t = { m = function() end }
                t.m()
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertFalse(node!!.declaresMember)
        assertEquals("LuaIndexExprImpl", node.element.javaClass.simpleName)
    }

    /**
     * The constructor field's own mint site (design §3.2, `LuaTypesVisitor.kt:754`), observed with
     * no subsequent read to raise a competing demand: `t`'s read side is empty, so
     * `LuaTypesSnapshot.typeOf` returns the write side unmerged and the field's own node wins.
     */
    @Test
    fun constructorFieldDeclarationIsMarked() {
        val node =
            memberNodeOn(
                """
                local t = { m = function() end }
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertTrue(node!!.declaresMember)
        assertEquals("LuaFieldImpl", node.element.javaClass.simpleName)
    }

    @Test
    fun bareColonCallDoesNotDeclare() {
        val node =
            memberNodeOn(
                """
                local t = {}
                t:m()
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertFalse(node!!.declaresMember)
        assertEquals("LuaMethodExprImpl", node.element.javaClass.simpleName)
    }

    @Test
    fun readBeforeAssignmentDoesNotDeclare() {
        val node =
            memberNodeOn(
                """
                local t = {}
                print(t.m)
                t.m = function() end
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertFalse(node!!.declaresMember)
        assertEquals("LuaIndexExprImpl", node.element.javaClass.simpleName)
    }

    @Test
    fun indexPrefixedAssignmentDoesNotDeclare() {
        val node =
            memberNodeOn(
                """
                local t = {}
                t.a = {}
                t.a.m = function() end
                t.a.m()
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertFalse(node!!.declaresMember)
        assertEquals("LuaIndexExprImpl", node.element.javaClass.simpleName)
    }

    @Test
    fun callPrefixedAssignmentDoesNotDeclare() {
        val node =
            memberNodeOn(
                """
                local t = {}
                t().m = function() end
                """.trimIndent(),
                "m",
            )
        assertNotNull(node)
        assertFalse(node!!.declaresMember)
        assertEquals("LuaIndexExprImpl", node.element.javaClass.simpleName)
    }
}
