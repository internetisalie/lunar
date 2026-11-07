package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.hierarchy.LuaHierarchyUtil
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-14 Phase 5: a generic class is keyed by its head and instantiated by a parameterized name,
 * and a parameterized parent is inherited with substitution. Cases 15-18 and 22 of the feature's
 * requirements; case 18 is BUG-420's measurement.
 */
@RunWith(JUnit4::class)
class Type14GenericClassTest : IndexedBasePlatformTestCase() {
    private fun boxSource(name: String) =
        """
        ---@class $name<T>
        ---@field value T
        local $name = {}
        """.trimIndent()

    private fun nameOf(
        file: PsiFile,
        variable: String,
    ): PsiElement =
        PsiTreeUtil
            .findChildrenOfType(file, LuaLocalVarDecl::class.java)
            .first { it.text.startsWith("local $variable ") || it.text == "local $variable" }
            .attNameList
            .first()
            .nameRef

    private fun typeOf(
        file: PsiFile,
        variable: String,
    ): String = LuaTypesSnapshot.forFile(file).getValueType(nameOf(file, variable)).displayName()

    private fun declOf(name: String): LuaLocalVarDecl =
        StubIndex
            .getElements(
                LuaClassNameIndex.KEY,
                name,
                project,
                GlobalSearchScope.allScope(project),
                LuaLocalVarDecl::class.java,
            ).single()

    /** Case 15 on one arm: keyed by the head, its parameters recorded, its fields indexed under the head. */
    private fun assertKeyedByHead(
        name: String,
        context: PsiFile,
        expectStub: Boolean,
    ) {
        val decl = declOf(name)
        assertEquals("$name: stub-backed", expectStub, decl.stub != null)
        val resolved = LuaTypeManager.getInstance(project).resolveType(name, context)
        assertTrue("$name must resolve to a class, was $resolved", resolved is LuaClassType)
        assertEquals("[T]", (resolved as LuaClassType).typeParameters.map { it.name }.toString())
        assertEquals(name, LuaHierarchyUtil.className(decl))
        val members = LuaReceiverMemberIndex.membersIn(name, project, GlobalSearchScope.allScope(project))
        assertTrue("$name: membersIn must hold 'value', was $members", members.any { it.name == "value" })
    }

    @Test
    fun testCase15AGenericClassIsKeyedByItsHead() {
        myFixture.addFileToProject("stubbox.lua", boxSource("StubBox"))
        val usage = myFixture.configureByText("astbox.lua", boxSource("AstBox"))
        runReadAction {
            val keys = StubIndex.getInstance().getAllKeys(LuaClassNameIndex.KEY, project)
            assertFalse("no raw key StubBox<T>: $keys", "StubBox<T>" in keys)
            assertFalse("no raw key AstBox<T>: $keys", "AstBox<T>" in keys)
            assertKeyedByHead("StubBox", usage, expectStub = true)
            assertKeyedByHead("AstBox", usage, expectStub = false)
        }
    }

    @Test
    fun testCase16AParameterizedNameIsAnInstantiation() {
        myFixture.addFileToProject("box.lua", boxSource("Box"))
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@type Box<string>
                local b
                local v = b.value
                """.trimIndent(),
            )
        assertEquals("string", typeOf(file, "v"))
        runReadAction {
            val resolved = LuaTypeManager.getInstance(project).resolveType("Box<string>", file)
            assertTrue("Box<string> must resolve to a class, was $resolved", resolved is LuaClassType)
            assertEquals("Box<string>", resolved?.name)
            assertEquals("string", resolved?.resolveMember("value")?.type?.name)
        }
    }

    @Test
    fun testCase17AnInstantiationIsAssignableToItsRawClass() {
        val file =
            myFixture.configureByText(
                "test.lua",
                boxSource("Box") + "\n" +
                    """
                    ---@type Box<string>
                    local b
                    ---@type Box
                    local raw
                    raw = b
                    """.trimIndent(),
            )
        val errors = LuaTypesSnapshot.forFile(file).getErrors().map { it.message }
        assertEquals("no errors expected", "[]", errors.toString())
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val instance = requireNotNull(manager.resolveType("Box<string>", file))
            val raw = requireNotNull(manager.resolveType("Box", file))
            val other = requireNotNull(manager.resolveType("Box<number>", file))
            assertTrue("Box<string> must be assignable to Box", instance.isAssignableTo(raw))
            assertFalse("Box<string> must not be assignable to Box<number>", instance.isAssignableTo(other))
        }
    }

    @Test
    fun testCase18AParameterizedParentIsInheritedWithSubstitution() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@class PBase<T>
                ---@field inherited T
                local PBase = {}
                ---@class Kid : PBase<string>
                ---@field own number
                local Kid = {}
                """.trimIndent(),
            )
        runReadAction {
            val kid = LuaTypeManager.getInstance(project).resolveType("Kid", file)
            assertEquals(
                "[inherited, own]",
                kid
                    ?.getMembers()
                    ?.keys
                    ?.sorted()
                    .toString(),
            )
            assertEquals("string", kid?.resolveMember("inherited")?.type?.name)
        }
    }

    /** BUG-420's own probe fixture (requirements Appendix A, J): the parent is not generic, and is inherited all the same. */
    @Test
    fun testBug420ParameterizedNonGenericParentIsInherited() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ---@class ProbeBase
                ---@field inherited string
                local ProbeBase = {}
                ---@class ProbeKid : ProbeBase<string, number>
                ---@field own number
                local ProbeKid = {}
                """.trimIndent(),
            )
        runReadAction {
            val kid = LuaTypeManager.getInstance(project).resolveType("ProbeKid", file)
            assertEquals(
                "[inherited, own]",
                kid
                    ?.getMembers()
                    ?.keys
                    ?.sorted()
                    .toString(),
            )
        }
    }

    @Test
    fun testCase22AGenericReturnOfAParameterizedClassInstantiatesIt() {
        val file =
            myFixture.configureByText(
                "test.lua",
                boxSource("Box") + "\n" +
                    """
                    ---@generic T
                    ---@param v T
                    ---@return Box<T>
                    local function make(v) end
                    local b = make("x")
                    local val = b.value
                    """.trimIndent(),
            )
        assertEquals("Box<string>", typeOf(file, "b"))
        assertEquals("string", typeOf(file, "val"))
    }
}
