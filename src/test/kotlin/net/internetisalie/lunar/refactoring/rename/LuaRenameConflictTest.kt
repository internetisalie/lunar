package net.internetisalie.lunar.refactoring.rename

import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.LuaBundle
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-01-14 — a rename that would silently **rebind** is reported before it is applied
 * (design §3.4).
 *
 * Lua has no compiler to catch this: after a capturing rename the file still parses, still runs,
 * and reads a different value. Phase 2 made rename work; without these four rules it works
 * *silently*, which for `x` → `y` in a file that already has a `y` is a worse outcome than the
 * refusal Phase 2 removed.
 *
 * **Every case names the rule that must fire, by its own message.** Asserting merely "a conflict
 * was raised" would be green for a detector that reports the wrong rule — and, for the two
 * fixtures where C1 and C2 both fire, green for a detector missing one of them entirely.
 * `ConflictsInTestsException.getMessages()` is what the conflicts dialog would have shown, HTML
 * stripped, so the expected strings are built from the same bundle keys the detector uses: the
 * assertion is about *which rule fired*, not about the copy.
 *
 * The counterweight is [testUnrelatedInnerDeclarationIsNotAConflict]: a detector that cries wolf
 * on every rename passes all four positive cases and fails only that one.
 */
@RunWith(JUnit4::class)
class LuaRenameConflictTest : BasePlatformTestCase() {
    /**
     * TC-14 — C1. `print(x)` sits where `local y = 2` is already visible, so rewriting it to `y`
     * would bind it to that declaration instead. Note the conflict is anchored on the *capturing*
     * declaration, never on `print(x)` itself: the platform deletes collision infos from the usage
     * set, so anchoring on a usage would skip rewriting it if the user pressed Continue.
     */
    @Test
    fun testCaptureOfRenamedUsageIsReported() {
        myFixture.configureByText("a.lua", "local <caret>x = 1\nlocal y = 2\nprint(x)\n")

        val reported = conflictsFromRenamingTo("y")

        assertReports(LuaBundle.message("refactoring.rename.conflict.capture", "y", "x"), reported)
    }

    /**
     * TC-15 — C2, the mirror image of TC-14: here the existing `print(y)` is the reference that
     * changes meaning, because the renamed declaration would shadow the `local y` it reads today.
     *
     * C1 also fires on this fixture (`local y = 1` is visible at `print(x)`), which is exactly why
     * the assertion is on the **shadow** message: "an exception was thrown" is green with C2
     * deleted.
     */
    @Test
    fun testExistingReferenceShadowedIsReported() {
        myFixture.configureByText("a.lua", "local y = 1\nlocal <caret>x = 2\nprint(y)\nprint(x)\n")

        val reported = conflictsFromRenamingTo("y")

        assertReports(LuaBundle.message("refactoring.rename.conflict.shadow"), reported)
    }

    /**
     * TC-16 — the negative case, and the only thing standing between this feature and a detector
     * that reports every rename.
     *
     * The `y` in `do local y = 3 end` is a **declaration** of `y`, not a reference to one: it
     * introduces its own binding, so the renamed `x` shadowing it changes nothing that is already
     * there. It is also invisible from `print(x)`, so C1 must stay silent too. Deleting C2's
     * declaration-site skip makes this red — mutation-proved, because with the skip removed the
     * inner `y` crawls up to the renamed `x` and reports a conflict that does not exist.
     */
    @Test
    fun testUnrelatedInnerDeclarationIsNotAConflict() {
        myFixture.configureByText("a.lua", "local <caret>x = 1\nprint(x)\ndo local y = 3 end\n")

        myFixture.renameElementAtCaret("y")

        myFixture.checkResult("local y = 1\nprint(y)\ndo local y = 3 end\n")
    }

    /**
     * TC-17 — C3. Two globals are one `_ENV` entry, so renaming `greet` to a name another file
     * already declares merges them rather than colliding, and nothing in Lua reports that.
     */
    @Test
    fun testExistingGlobalIsReported() {
        myFixture.addFileToProject("b.lua", "function hello() end\n")
        myFixture.configureByText("a.lua", "function <caret>greet() end\n")

        val reported = conflictsFromRenamingTo("hello")

        assertReports(LuaBundle.message("refactoring.rename.conflict.globalExists", "hello"), reported)
    }

    /**
     * TC-31 — C4, the silent-partial defect arriving through *resolution* rather than through
     * classification.
     *
     * With `config` declared in two files, `LuaNameReference.resolve()` returns null for every read
     * of it (`multiResolve` yields more than one result), so `isReferenceTo` is false and
     * `c.lua`'s `print(config)` is not a findable reference at all. The rename would rewrite the
     * declaration under the caret and nothing else — success reported, one file changed, the
     * consumer left bound to the old name. That is BUG-457's shape, and only C4 sees it coming.
     */
    @Test
    fun testGlobalDeclaredTwiceIsReported() {
        myFixture.addFileToProject("b.lua", "config = {}\n")
        myFixture.addFileToProject("c.lua", "print(config)\n")
        myFixture.configureByText("a.lua", "con<caret>fig = {}\n")

        val reported = conflictsFromRenamingTo("settings")

        assertReports(LuaBundle.message("refactoring.rename.conflict.ambiguousGlobal", "config", 2), reported)
    }

    /**
     * The messages the conflicts dialog would have shown. A rename that applies instead of
     * reporting fails here rather than at an assertion further down, because "the file changed
     * silently" is the defect itself and not a detail of it.
     */
    private fun conflictsFromRenamingTo(newName: String): Collection<String> {
        try {
            myFixture.renameElementAtCaret(newName)
        } catch (conflicts: BaseRefactoringProcessor.ConflictsInTestsException) {
            return conflicts.messages
        }
        throw AssertionError(
            "renaming to '$newName' applied silently; a rename that rebinds without warning is the " +
                "defect REFACT-01-14 exists against. File is now:\n${myFixture.file.text}",
        )
    }

    private fun assertReports(
        expected: String,
        reported: Collection<String>,
    ) = assertTrue(
        "expected the conflict\n  $expected\nbut the dialog would have shown\n  " +
            reported.joinToString("\n  "),
        expected in reported,
    )
}
