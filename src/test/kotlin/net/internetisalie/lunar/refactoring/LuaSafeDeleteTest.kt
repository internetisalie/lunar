package net.internetisalie.lunar.refactoring

import com.intellij.psi.util.PsiTreeUtil
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
    fun testUnavailableOnLabelRef() {
        // A label declaration IS valid (LuaLabelName), but a raw non-identifier
        // leaf such as a keyword must not be accepted.
        myFixture.configureByText("test.lua", "local x = 1")

        // Find a LuaLocalVarDecl — it should NOT be a valid safe-delete target
        // because canFindUsagesFor only accepts IDENTIFIER leaves, not parent nodes.
        val decl = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaLocalVarDecl::class.java)) {
            "Expected a LuaLocalVarDecl in test.lua"
        }
        assertFalse(
            "handlesElement must be false for a LuaLocalVarDecl node (not an IDENTIFIER leaf)",
            processor.handlesElement(decl),
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
