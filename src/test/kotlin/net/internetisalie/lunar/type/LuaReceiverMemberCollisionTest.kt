package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaFunctionType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-501: a `---@class` hosted on `local M = {}` gets its methods from `LuaReceiverMemberIndex`
 * entries for receiver `M`, which every module named `M` contributes to. The index keeps one entry
 * per member name, and which file supplies it follows a per-JVM hash order, so a sibling's
 * `M.name` function or `M.name = 1` field must not decide whether the host's `M:name` is a member.
 *
 * Sixteen siblings each collide on one name, so before the fix the host keeps every method only
 * when its own entry happens to be visited first: about one JVM in seventeen.
 */
@RunWith(JUnit4::class)
class LuaReceiverMemberCollisionTest : BasePlatformTestCase() {
    private val methodCount = 16

    private fun hostText(): String {
        val methods =
            (1..methodCount).joinToString("\n") { i ->
                "---@param a string\n---@param b table\n---@param c string\nfunction M:m$i(a, b, c) end"
            }
        return "---@class Host\nlocal M = {}\n$methods\nreturn M\n"
    }

    private fun siblingText(index: Int): String {
        val member =
            if (index % 2 == 0) {
                "M.m$index = 1"
            } else {
                "---@param x string\nfunction M.m$index(x) end"
            }
        return "local M = {}\n$member\nreturn M\n"
    }

    private fun resolveClass(
        className: String,
        context: PsiFile,
    ): LuaClassType? = LuaTypeManagerImpl(project).resolveType(className, context) as? LuaClassType

    /** "m<i>" for each method that is not a three-parameter function declared in [host]. */
    private fun wrongHostMethods(host: PsiFile): List<String> =
        runReadAction {
            val hostClass = resolveClass("Host", host)
            (1..methodCount).map { "m$it" }.filterNot { name ->
                val member = hostClass?.resolveMember(name)
                val function = member?.type as? LuaFunctionType
                function?.params?.size == 3 && member.sourceElement?.containingFile == host
            }
        }

    @Test
    fun testClassHostedMethodsSurviveSameNamedSiblingMembers() {
        val host = myFixture.addFileToProject("host.lua", hostText())
        (1..methodCount).forEach { myFixture.addFileToProject("sibling$it.lua", siblingText(it)) }

        assertEquals("Host methods dropped or replaced", emptyList<String>(), wrongHostMethods(host))
    }

    @Test
    fun testClassHostedMethodsWithoutSiblings() {
        val host = myFixture.addFileToProject("host.lua", hostText())

        assertEquals("Host methods dropped or replaced", emptyList<String>(), wrongHostMethods(host))
    }

    @Test
    fun testSiblingClassSeesOnlyItsOwnMethod() {
        myFixture.addFileToProject("host.lua", hostText())
        val sibling =
            myFixture.addFileToProject(
                "sib.lua",
                "---@class Sib\nlocal M = {}\n---@param x string\nfunction M.own(x) end\nreturn M\n",
            )

        runReadAction {
            val sibClass = resolveClass("Sib", sibling)
            val own = sibClass?.resolveMember("own")
            assertEquals(1, (own?.type as? LuaFunctionType)?.params?.size)
            assertEquals(sibling, own?.sourceElement?.containingFile)
            val leaked = (1..methodCount).map { "m$it" }.filter { sibClass?.resolveMember(it) != null }
            assertEquals("Host methods leaked into Sib", emptyList<String>(), leaked)
        }
    }
}
