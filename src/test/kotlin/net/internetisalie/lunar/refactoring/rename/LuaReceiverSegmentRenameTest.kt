package net.internetisalie.lunar.refactoring.rename

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-476 — the receiver (head) segment of `function t:m()` / `function t.m()` READS the binding
 * `t`; it declares nothing. So renaming `t` rewrites it, Find Usages on `t` lists it, and the
 * function's own last segment is never part of that set.
 */
@RunWith(JUnit4::class)
class LuaReceiverSegmentRenameTest : BasePlatformTestCase() {
    @Test
    fun colonReceiverIsRenamedFromAUsageCaret() {
        assertRenamed(
            "local t = {}\nfunction t:m() end\n<caret>t:m()\n",
            "local renamedTable = {}\nfunction renamedTable:m() end\nrenamedTable:m()\n",
        )
    }

    @Test
    fun colonReceiverIsRenamedFromTheDeclarationCaret() {
        assertRenamed(
            "local <caret>t = {}\nfunction t:m() end\nt:m()\n",
            "local renamedTable = {}\nfunction renamedTable:m() end\nrenamedTable:m()\n",
        )
    }

    @Test
    fun dottedReceiverIsRenamedFromAUsageCaret() {
        assertRenamed(
            "local t = {}\nfunction t.m() end\n<caret>t.m()\n",
            "local renamedTable = {}\nfunction renamedTable.m() end\nrenamedTable.m()\n",
        )
    }

    @Test
    fun dottedReceiverIsRenamedFromTheDeclarationCaret() {
        assertRenamed(
            "local <caret>t = {}\nfunction t.m() end\nt.m()\n",
            "local renamedTable = {}\nfunction renamedTable.m() end\nrenamedTable.m()\n",
        )
    }

    @Test
    fun onlyTheHeadOfANestedNameIsRenamed() {
        assertRenamed(
            "local <caret>a = {}\na.b = {}\nfunction a.b:m() end\n",
            "local z = {}\nz.b = {}\nfunction z.b:m() end\n",
        )
    }

    @Test
    fun theReceiverIsRenamedInsideTheFunctionBodyToo() {
        assertRenamed(
            "local <caret>M = {}\nfunction M.run() M.x() end\n",
            "local Z = {}\nfunction Z.run() Z.x() end\n",
        )
    }

    @Test
    fun renamingTheReceiverLeavesTheMethodAlone() {
        assertRenamed(
            "local <caret>m = {}\nfunction m:m() end\nm:m()\n",
            "local q = {}\nfunction q:m() end\nq:m()\n",
        )
    }

    @Test
    fun aLocalSpellingTheFunctionsOwnNameDoesNotCaptureIt() {
        assertRenamed(
            "local <caret>run = 1\nlocal M = {}\nfunction M.run() return run end\n",
            "local go = 1\nlocal M = {}\nfunction M.run() return go end\n",
        )
    }

    @Test
    fun renamingTheMethodLeavesTheReceiverAlone() {
        assertRenamed(
            "local t = {}\nfunction t:m() end\nt:<caret>m()\n",
            "local t = {}\nfunction t:n() end\nt:n()\n",
        )
    }

    @Test
    fun anInnerShadowingReceiverIsNotRenamedWithTheOuterOne() {
        assertRenamed(
            "local <caret>t = {}\ndo local t = {} function t:m() end end\nt:m()\n",
            "local q = {}\ndo local t = {} function t:m() end end\nq:m()\n",
        )
    }

    @Test
    fun aParameterSpellingTheReceiverDoesNotCaptureTheHead() {
        assertRenamed(
            "local <caret>t = {}\nfunction t:m(t) return t end\n",
            "local q = {}\nfunction q:m(t) return t end\n",
        )
        assertRenamed(
            "local t = {}\nfunction t:m(<caret>t) return t end\n",
            "local t = {}\nfunction t:m(p) return p end\n",
        )
    }

    @Test
    fun aGlobalReceiverIsRenamedWithItsAssignment() {
        assertRenamed(
            "<caret>t = {}\nfunction t:m() end\nt:m()\n",
            "q = {}\nfunction q:m() end\nq:m()\n",
        )
    }

    @Test
    fun aReceiverThatBindsNothingIsStillRefused() {
        myFixture.configureByText("a.lua", "function <caret>M.run() end\nM.run()\n")

        val refusal =
            try {
                LuaRenameProcessor().substituteElementToRename(myFixture.elementAtCaret, null)
                null
            } catch (thrown: CommonRefactoringUtil.RefactoringErrorHintException) {
                thrown
            }

        assertTrue(
            "refused with the receiver-segment reason: ${refusal?.message}",
            refusal?.message.orEmpty().contains("receiver part of a function name"),
        )
    }

    @Test
    fun aCaretOnABoundReceiverRenamesTheBindingItReads() {
        assertRenamed(
            "local t = {}\nfunction <caret>t:m() end\nt:m()\n",
            "local q = {}\nfunction q:m() end\nq:m()\n",
        )
    }

    @Test
    fun recursionStillResolves() {
        assertRenamed("local function <caret>f() f() end\n", "local function g() g() end\n")
        assertRenamed("function <caret>f() f() end\nf()\n", "function g() g() end\ng()\n")
    }

    @Test
    fun findUsagesOfTheLocalListsTheReceiverSegment() {
        val source = "local t = {}\nfunction t:m() end\nt:m()\n"
        myFixture.configureByText("a.lua", source)
        val declaration = requireNotNull(myFixture.file.findElementAt(source.indexOf("t = {}")))

        val offsets =
            ReferencesSearch
                .search(declaration)
                .findAll()
                .map { it.element.textOffset }
                .sorted()

        assertEquals(listOf(source.indexOf("t:m() end"), source.lastIndexOf("t:m()")), offsets)
    }

    /** Renames at the caret to whatever name [expected] puts where [source] first differs. */
    private fun assertRenamed(
        source: String,
        expected: String,
    ) {
        val plain = source.replace("<caret>", "")
        val firstDifference = plain.zip(expected).indexOfFirst { (before, after) -> before != after }
        val newName = expected.substring(firstDifference).takeWhile { it.isLetterOrDigit() || it == '_' }
        myFixture.configureByText("a.lua", source)
        myFixture.renameElementAtCaret(newName)
        assertEquals(expected, myFixture.file.text)
    }
}
