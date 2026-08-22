package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaNameRef
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-457: rename must be refused for Lua elements other than labels, because the platform admits
 * any [PsiNamedElement] and the resulting rename silently rewrites only the declaration.
 *
 * The label case is the one that must NOT be claimed — label rename genuinely works, and a
 * processor that over-claims would break the only working refactoring in the plugin.
 */
@RunWith(JUnit4::class)
class LuaUnsupportedRenameProcessorTest : BasePlatformTestCase() {
    private val processor = LuaUnsupportedRenameProcessor()

    @Test
    fun testClaimsAnOrdinaryDeclaration() {
        myFixture.configureByText("t.lua", "local counter = 0\nprint(counter)\n")
        val nameRef = PsiTreeUtil.findChildOfType(myFixture.file, LuaNameRef::class.java)

        assertNotNull("fixture should contain a LuaNameRef", nameRef)
        assertTrue(
            "a Lua name reference is a PsiNamedElement, which is exactly why the platform admits it",
            nameRef is PsiNamedElement,
        )
        assertTrue("the processor must claim it", processor.canProcessElement(nameRef!!))
    }

    /**
     * Headlessly, `CommonRefactoringUtil.showErrorHint` throws rather than painting a balloon, so
     * the refusal surfaces as [CommonRefactoringUtil.RefactoringErrorHintException] and the method
     * never reaches its `return null`. Asserting the throw is the stronger claim of the two: it
     * proves the user is TOLD, where a null return would only prove the rename aborted.
     */
    @Test
    fun testRefusesWithAnExplanation() {
        myFixture.configureByText("t.lua", "local counter = 0\nprint(counter)\n")
        val nameRef = PsiTreeUtil.findChildOfType(myFixture.file, LuaNameRef::class.java)!!

        val refusal: CommonRefactoringUtil.RefactoringErrorHintException? =
            try {
                processor.substituteElementToRename(nameRef, null)
                null
            } catch (thrown: CommonRefactoringUtil.RefactoringErrorHintException) {
                thrown
            }

        assertNotNull("rename must be refused, not silently allowed", refusal)
        assertTrue(
            "the refusal must name the reason rather than merely aborting: " + refusal?.message,
            refusal?.message.orEmpty().contains("::labels::"),
        )
    }

    @Test
    fun testDoesNotClaimALabel() {
        myFixture.configureByText("t.lua", "::retry::\ngoto retry\n")
        val label = PsiTreeUtil.findChildOfType(myFixture.file, LuaLabelName::class.java)

        assertNotNull("fixture should contain a LuaLabelName", label)
        assertFalse(
            "labels rename correctly today; claiming them here would break REFACT-04",
            processor.canProcessElement(label!!),
        )
    }

    @Test
    fun testDoesNotClaimTheFileItself() {
        myFixture.configureByText("t.lua", "local x = 1\n")

        assertFalse(
            "renaming a .lua file is a different refactoring and must stay available",
            processor.canProcessElement(myFixture.file),
        )
    }

    @Test
    fun testIsTheProcessorThePlatformSelects() {
        myFixture.configureByText("t.lua", "local counter = 0\nprint(counter)\n")
        val nameRef = PsiTreeUtil.findChildOfType(myFixture.file, LuaNameRef::class.java)!!

        assertTrue(
            "registration must actually take effect — without this the unit assertions above " +
                "would pass while the real rename still corrupted the file",
            RenamePsiElementProcessor.forElement(nameRef) is LuaUnsupportedRenameProcessor,
        )
    }

    /**
     * `forPsiElement` skips a processor that is not usable in the current context
     * (`RenamePsiElementProcessorBase:156`), so without this marker the refusal disappears while
     * the project indexes and rename falls back to the platform default — BUG-457 again, in a
     * window nobody would think to test.
     */
    @Test
    fun testSurvivesDumbMode() {
        assertTrue(
            "without DumbAware the refusal evaporates during indexing",
            processor is DumbAware,
        )
    }
}
