package net.internetisalie.lunar.lang.types

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.RootAccessor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * TYPE-14 Phase 2: reading a generic call's result must not mint nodes into the file's graph
 * (BUG-473). A mint bumps the graph revision, which invalidates every walk-root memo, so a second
 * read of the same results would re-derive its roots. The fixture reaches the three lazy read paths
 * of `LuaGenericInstantiation`: an `Elem` projection, a substitution, and a type argument read out
 * of a parameterized name, whose argument is a class so a mint would have members to create.
 */
class Type14InstantiationBudgetTest : BaseDocumentTest() {
    @Test
    fun readingGenericResultsAgainDerivesNoRoot() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText(FIXTURE)
            val types = LuaTypesSnapshot.forFile(myFixture.file) as LuaTypesSnapshot

            val firstRead = readResults(types)
            val afterFirstRead = types.rootResolutionCount(RootAccessor.WRITE)
            val secondRead = readResults(types)

            assertEquals(listOf("string", "number[]", "Widget"), firstRead)
            assertEquals(firstRead, secondRead)
            assertEquals(
                afterFirstRead,
                types.rootResolutionCount(RootAccessor.WRITE),
                "a second read of the same generic results re-derived walk roots: a read minted into the graph",
            )
        }
    }

    private fun readResults(types: LuaTypesSnapshot): List<String> =
        RESULTS.map { name ->
            val reference =
                PsiTreeUtil
                    .findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java)
                    .first { it.text.startsWith("local $name ") }
                    .attNameList
                    .first()
                    .nameRef
            types.getValueType(reference).displayName()
        }

    private companion object {
        val RESULTS = listOf("a", "w", "v")

        val FIXTURE =
            """
            |---@class Widget
            |---@field size number
            |local Widget = {}
            |
            |---@generic T
            |---@param xs T[]
            |---@return T
            |local function first(xs) end
            |
            |---@generic T
            |---@param v T
            |---@return T[]
            |local function wrap(v) end
            |
            |---@generic K, V
            |---@param t table<K, V>
            |---@return V
            |local function get(t) end
            |
            |---@type string[]
            |local xs
            |---@type table<string, Widget>
            |local t
            |local a = first(xs)
            |local w = wrap(1)
            |local v = get(t)
            """.trimMargin()
    }
}
