package net.internetisalie.lunar.luacats

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsComment
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsDeclarations
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * MAINT-34-01: direct coverage of the shared `@field` extraction.
 *
 * The parity harness proves the two paths *agree*; these prove they agree on the right answer. Both
 * matter — two copies of a wrong rule are in perfect parity.
 */
@RunWith(JUnit4::class)
class LuaCatsDeclarationsTest : BasePlatformTestCase() {
    /** The `@field` members declared by [source], read straight through the extractor. */
    private fun fieldsOf(source: String): List<LuaCatsDeclarations.FieldMember> {
        val file = myFixture.configureByText("fields.lua", source)
        return runReadAction {
            val comment = PsiTreeUtil.findChildOfType(file, LuaCatsComment::class.java)
            LuaCatsDeclarations.fieldMembers(requireNotNull(comment) { "no LuaCATS comment in fixture" })
        }
    }

    @Test
    fun testPlainFieldKeepsItsNameAndType() {
        val fields = fieldsOf("---@class C\n---@field a string\nlocal C = {}\n")
        assertEquals(1, fields.size)
        assertEquals("a", fields[0].name)
        assertEquals("string", fields[0].typeName)
    }

    /** BUG-401: the marker leaves the name AND widens the type — not one or the other. */
    @Test
    fun testOptionalFieldDropsTheMarkerAndAdmitsNil() {
        val fields = fieldsOf("---@class C\n---@field beta? number\nlocal C = {}\n")
        assertEquals("beta", fields[0].name)
        assertEquals("(number) | nil", fields[0].typeName)
    }

    /**
     * A keyed field has no `argName` at all — the descriptor lands in `argType`
     * (`fieldKeyDescriptor ::= '[' type ']'`), which is why the fallback between the two exists.
     */
    @Test
    fun testKeyedFieldIsNamedByItsKey() {
        val fields = fieldsOf("---@class C\n---@field [string] number\nlocal C = {}\n")
        assertEquals("[string]", fields[0].name)
        assertEquals("number", fields[0].typeName)
    }

    /** `fieldScope` is a separate `argKeyword` child, so it is excluded without any stripping. */
    @Test
    fun testScopeKeywordDoesNotLeakIntoTheName() {
        val fields = fieldsOf("---@class C\n---@field private p string\n---@field public q number\nlocal C = {}\n")
        assertEquals(listOf("p", "q"), fields.map { it.name })
        assertEquals(listOf("string", "number"), fields.map { it.typeName })
    }

    /** Quick-doc renders what was written; the engine renders what it resolved. */
    @Test
    fun testDisplayNameKeepsTheMarkerThatTheMemberNameDrops() {
        val file = myFixture.configureByText("disp.lua", "---@class C\n---@field beta? number\nlocal C = {}\n")
        runReadAction {
            val tag =
                requireNotNull(PsiTreeUtil.findChildOfType(file, LuaCatsComment::class.java))
                    .fieldTagList
                    .first()
            assertEquals("beta?", LuaCatsDeclarations.fieldDisplayName(tag))
            assertEquals("beta", LuaCatsDeclarations.fieldMember(tag).name)
        }
    }

    /** The tag is carried so callers can preserve `LuaTypeMember.sourceElement` on the PSI path. */
    @Test
    fun testFieldMemberCarriesItsTag() {
        val fields = fieldsOf("---@class C\n---@field a string\nlocal C = {}\n")
        assertNotNull("a PSI-read field must carry its tag", fields[0].tag)
    }
}
