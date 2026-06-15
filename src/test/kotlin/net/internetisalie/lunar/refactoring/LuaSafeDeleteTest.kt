package net.internetisalie.lunar.refactoring

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider
import net.internetisalie.lunar.lang.psi.LuaAttName
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [LuaSafeDeleteProcessor] and the [isSafeDeleteAvailable] hook in
 * [net.internetisalie.lunar.lang.insight.LuaRefactoringSupportProvider] (REFACT-03).
 *
 * TC-REFACT-03-01 — unused local deleted: drives [SafeDeleteHandler.invoke] in unit-test mode
 *   (no dialog) and asserts the full `local x = 1` statement is removed from the file.
 *
 * TC-REFACT-03-02 — used local → usages discovered: calls [LuaSafeDeleteProcessor.findUsages]
 *   directly on the IDENTIFIER leaf to avoid the interactive conflict dialog; asserts that at
 *   least one usage is returned for `print(x)`.
 *
 * TC-REFACT-03-03 — unavailable target: asserts [LuaFindUsagesProvider.canFindUsagesFor] and
 *   [LuaSafeDeleteProcessor.handlesElement] return false for a keyword / literal element.
 */
@RunWith(JUnit4::class)
class LuaSafeDeleteTest : BasePlatformTestCase() {

    private val processor = LuaSafeDeleteProcessor()
    private val findUsagesProvider = LuaFindUsagesProvider()

    // -------------------------------------------------------------------------
    // TC-REFACT-03-01: unused local is silently deleted (no prompt)
    // -------------------------------------------------------------------------

    @Test
    fun testUnusedLocalIsDeleted() {
        myFixture.configureByText("test.lua", "local x = 1\n")
        val file = myFixture.file

        // Locate the IDENTIFIER leaf for `x` inside the LuaAttName.
        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val xLeaf = attName.nameRef.identifier

        // In unit-test mode SafeDeleteHandler skips the dialog and runs the
        // refactoring synchronously.  With checkDelegates=true, the handler
        // picks up LuaSafeDeleteProcessor, elevates the leaf to LuaLocalVarDecl,
        // finds 0 usages, and deletes the declaration without prompting.
        SafeDeleteHandler.invoke(project, arrayOf(xLeaf), true)

        // The whole `local x = 1` statement should now be gone.
        val remaining = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java)
        assertTrue(
            "Expected `local x = 1` to be deleted, but file is: ${myFixture.file.text}",
            remaining.isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-REFACT-03-02: used local → findUsages returns ≥1 usage
    //
    // Calling SafeDeleteHandler here would throw ConflictsInTestsException
    // (the platform's unit-test behaviour when unsafe usages are present).
    // Instead we test the processor directly: findUsages must report the
    // `print(x)` reference so the platform's conflict path is exercised.
    // -------------------------------------------------------------------------

    @Test
    fun testUsedLocalReturnsUsages() {
        myFixture.configureByText("test.lua", "local x = 1\nprint(x)\n")
        val file = myFixture.file

        val attName = PsiTreeUtil.findChildOfType(file, LuaAttName::class.java)!!
        val xLeaf = attName.nameRef.identifier

        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        processor.findUsages(xLeaf, arrayOf(xLeaf), usages)

        assertTrue(
            "Expected ≥1 usage of `x` (the print(x) reference), got ${usages.size}",
            usages.isNotEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // TC-REFACT-03-03: Safe Delete not offered on keyword / literal targets
    // -------------------------------------------------------------------------

    @Test
    fun testUnavailableOnKeyword() {
        // `print` is a usage-site name ref, not a declaration leaf.
        myFixture.configureByText("test.lua", "print(x)")

        // Find the element at the caret-position for `print` (usage ref, not decl).
        val element = requireNotNull(myFixture.file.findElementAt(0)) {
            "Expected an element at offset 0"
        }

        assertFalse(
            "isSafeDeleteAvailable must be false for a usage-site identifier",
            findUsagesProvider.canFindUsagesFor(element),
        )
        assertFalse(
            "handlesElement must be false for a usage-site identifier",
            processor.handlesElement(element),
        )
    }

    @Test
    fun testHandlesElevatedDeclaration() {
        // getElementsToSearch elevates the caret leaf to its LuaLocalVarDecl, and the platform
        // re-dispatches handlesElement on that elevated node before calling findUsages. If the
        // delegate did not handle the elevated node, the platform would fall back to the default
        // delete and remove the declaration WITHOUT a usage search (silently orphaning references).
        myFixture.configureByText("test.lua", "local x = 1")
        val decl = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaLocalVarDecl::class.java)) {
            "Expected a LuaLocalVarDecl in test.lua"
        }
        assertTrue(
            "handlesElement must be true for the elevated LuaLocalVarDecl, or Safe Delete skips usage search",
            processor.handlesElement(decl),
        )
    }

    // -------------------------------------------------------------------------
    // TC-REFACT-03-03 (integration): Safe Delete of a USED local must NOT delete
    // silently — the platform must surface a conflict. In unit-test mode an
    // unresolved conflict throws ConflictsInTestsException. This is the test that
    // catches the regression where the elevated decl was not handlesElement-ed.
    // -------------------------------------------------------------------------

    @Test
    fun testUsedLocalRaisesConflict() {
        myFixture.configureByText("test.lua", "local x = 1\nprint(x)\n")
        val attName = PsiTreeUtil.findChildOfType(myFixture.file, LuaAttName::class.java)!!
        val xLeaf = attName.nameRef.identifier

        try {
            SafeDeleteHandler.invoke(project, arrayOf(xLeaf), true)
            fail("Safe Delete of a used local must raise a conflict, not delete silently: ${myFixture.file.text}")
        } catch (expected: BaseRefactoringProcessor.ConflictsInTestsException) {
            // expected: the print(x) usage is reported as a conflict
        }

        assertFalse(
            "The used local must survive when a conflict is raised: ${myFixture.file.text}",
            PsiTreeUtil.findChildrenOfType(myFixture.file, LuaLocalVarDecl::class.java).isEmpty(),
        )
    }

    @Test
    fun testLabelDeclarationIsAvailable() {
        myFixture.configureByText("test.lua", "::done::\ngoto done")
        val labelName = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaLabelName::class.java)) {
            "Expected a LuaLabelName in test.lua"
        }
        assertTrue(
            "isSafeDeleteAvailable must be true for a label declaration (LuaLabelName)",
            findUsagesProvider.canFindUsagesFor(labelName),
        )
        assertTrue(
            "handlesElement must be true for a label declaration",
            processor.handlesElement(labelName),
        )
    }
}
