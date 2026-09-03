package net.internetisalie.lunar.refactoring.rename

import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * `REFACT-08` Phase 6: [LuaCatsTypeRenameProcessor.findCollisions] (`REFACT-08-11`, design.md
 * §2.9, §3.10).
 */
@RunWith(JUnit4::class)
class LuaCatsTypeRenameConflictTest : BasePlatformTestCase() {
    /**
     * TC-14 — renaming `Widget` to a `Gadget` that another file already declares raises
     * `ConflictsInTestsException` carrying the message.
     *
     * Mutation H (mutation-proved by hand, `temporary-edits` skill) — return from `findCollisions`
     * before its lookup: the rename applies silently and `LuaTypeManagerImpl.materializeClass`
     * merges the two declarations' members into one type, with nothing reported.
     */
    @Test
    fun testRenamingOntoAnExistingTypeNameIsReported() {
        myFixture.configureByText("types.lua", "--- @class Wid<caret>get\nlocal W = {}\n")
        myFixture.addFileToProject("other.lua", "--- @class Gadget\nlocal G = {}\n")

        val conflicts = conflictsFromRenamingTo("Gadget")

        assertTrue(
            "expected a conflict naming the rival declaration, got: $conflicts",
            conflicts.any { it.contains("Gadget") && it.contains("already declared") },
        )
    }

    /**
     * The falsifier for TC-14: renaming to a name **nothing** declares raises no conflict, so
     * `findCollisions` is not a rule that always fires.
     */
    @Test
    fun testRenamingToAnUndeclaredNameRaisesNoConflict() {
        myFixture.configureByText("types.lua", "--- @class Wid<caret>get\nlocal W = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", "--- @type Widget\n")

        myFixture.renameElementAtCaret("Gadget")

        myFixture.checkResult("--- @class Gadget\nlocal W = {}\n")
        assertEquals("--- @type Gadget\n", uses.text)
    }

    private fun conflictsFromRenamingTo(newName: String): Collection<String> {
        try {
            myFixture.renameElementAtCaret(newName)
        } catch (conflicts: BaseRefactoringProcessor.ConflictsInTestsException) {
            return conflicts.messages
        }
        throw AssertionError(
            "renaming to '$newName' applied silently; two declarations merging without warning " +
                "is the defect REFACT-08-11 exists against. File is now:\n${myFixture.file.text}",
        )
    }
}
