package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLabelRef
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-04 Phase 3 — [LuaLabelConflictDetector] and [LuaLabelRenameProcessor] (REFACT-04-07, -08,
 * -03). Every test states the mutation that would turn it red, per `implementation-plan.md`.
 */
@RunWith(JUnit4::class)
class LuaLabelConflictTest : BasePlatformTestCase() {
    /**
     * TC-04-D (`REFACT-04-07`, `-08`) — a duplicate-label rename is refused at 5.4.
     *
     * **Mutation:** delete `LuaLabelConflictDetector.collides`'s §3.2 clause-2 test and replace it
     * with `return false` — no conflict, no exception, and the rename silently produces the file
     * 5.4 refuses to load.
     */
    @Test
    fun testDuplicateLabelRenameRefusedAt54() {
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA54
        myFixture.configureByText(
            "test.lua",
            """
            local n = 0
            ::a::
            n = n + 1
            do
              if n < 2 then goto a end
              ::<caret>b::
            end
            print("n="..n)
            """.trimIndent(),
        )

        val conflicts = conflictsFromRenamingTo("a")

        assertTrue(myFixture.file.text.contains("::b::"))
        assertTrue(
            "expected an 'already defined' conflict, got: $conflicts",
            conflicts.any { it.contains("already defined") },
        )
    }

    /**
     * TC-04-E (`REFACT-04-08`) — the same rename is reported differently at 5.3: a rebind warning,
     * not a duplicate-label refusal.
     *
     * **Mutation:** flip design §3.4 step 3's comparison to `level < LuaLanguageLevel.LUA54`, or
     * replace the level read with a constant — the 5.4 wording appears at 5.3.
     */
    @Test
    fun testSameRenameReportedDifferentlyAt53() {
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA53
        myFixture.configureByText(
            "test.lua",
            """
            local n = 0
            ::a::
            n = n + 1
            do
              if n < 2 then goto a end
              ::<caret>b::
            end
            print("n="..n)
            """.trimIndent(),
        )

        val conflicts = conflictsFromRenamingTo("a")

        assertTrue(
            "expected a 'jump to the nearer label' conflict, got: $conflicts",
            conflicts.any { it.contains("jump to the nearer label") },
        )
        assertFalse(
            "the 5.4 wording must not appear at 5.3: $conflicts",
            conflicts.any { it.contains("already defined") },
        )
    }

    /**
     * TC-04-F (`REFACT-04-07`, negative) — sibling blocks do not collide.
     *
     * **Mutation:** weaken design §3.2 clause 2 to "same function and same name" — a false conflict
     * is reported on legal code (executed legal on 5.2.4, 5.3.6 and 5.4.7 — design §1 row P-c).
     */
    @Test
    fun testSiblingBlocksDoNotCollide() {
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA54
        myFixture.configureByText(
            "test.lua",
            """
            do ::a:: end
            do ::<caret>b:: end
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("a")

        myFixture.checkResult(
            """
            do ::a:: end
            do ::a:: end
            """.trimIndent(),
        )
    }

    /**
     * TC-04-G (`REFACT-04-07`, negative) — an earlier label in a *closed* block does not collide.
     * The guard on `risks-and-gaps.md` RD-1: `REFACT-04-07`'s own wording ("ancestor-or-self, or
     * descendant") is wrong, and this fixture is the one it would have wrongly rejected (executed
     * legal on 5.4.7 — design §1 row P-b).
     *
     * **Mutation:** drop the `before(renamed, other)` test from the second bullet of design §3.2
     * clause 2 — `block(b)` (the file block) encloses `block(a)` (the `do` block) regardless of
     * order, so without the order test this fixture wrongly collides.
     */
    @Test
    fun testEarlierLabelInAClosedBlockDoesNotCollide() {
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA54
        myFixture.configureByText(
            "test.lua",
            """
            do ::a:: end
            ::<caret>b::
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("a")

        myFixture.checkResult(
            """
            do ::a:: end
            ::a::
            """.trimIndent(),
        )
    }

    /**
     * TC-04-H (`REFACT-04-04`, `-07`) — a label in a *nested* function never collides. The nesting
     * is deliberate: a sibling function's label is out of `PsiTreeUtil.findChildrenOfType`'s reach
     * whether `labelsInFunctionScope`'s filter is present or not, so only a nested fixture exercises
     * the filter at all.
     *
     * **Mutation:** remove the `functionScopeOf(it) === scope` filter from
     * `LuaLabelScopes.labelsInFunctionScope` — `::a::` is a descendant of `f`'s scope and is found
     * unfiltered, falsely colliding. Also turns red if `LuaFuncDef` is dropped from
     * `LuaLabelScopes.isFunctionBoundary` — `functionScopeOf(a)` then climbs past the anonymous
     * function to `f` and the filter passes `a` through.
     */
    @Test
    fun testLabelInANestedFunctionNeverCollides() {
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA54
        myFixture.configureByText(
            "test.lua",
            """
            function f()
              ::<caret>b::
              local g = function()
                ::a::
              end
            end
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("a")

        myFixture.checkResult(
            """
            function f()
              ::a::
              local g = function()
                ::a::
              end
            end
            """.trimIndent(),
        )
    }

    /**
     * TC-04-L (`REFACT-04-07` wiring) — the label processor is the one the platform selects, and
     * only for labels.
     *
     * The EP is deliberately not registered by this test — `plugin.xml`'s declaration is what is
     * under test. A schema-engine EP-registration bug in this repo was masked for months by tests
     * that hand-registered the extension point (`LuaRenameTest.testIsTheProcessorThePlatformSelects`
     * is the existing example of this assertion done correctly).
     *
     * **Mutation:** remove the `<renamePsiElementProcessor>` line for `LuaLabelRenameProcessor`
     * from `plugin.xml` — `forElement` no longer returns a `LuaLabelRenameProcessor`. Also turns
     * red if the `DumbAware` marker is dropped from the class declaration — the assertion on the
     * selected instance's type still holds outside dumb mode, but the second assertion (`is
     * DumbAware`) catches the marker's removal directly.
     */
    @Test
    fun testLabelProcessorIsTheOneThePlatformSelects() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ::top::
                local x = 1
                """.trimIndent(),
            )

        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!
        val localDecl = PsiTreeUtil.findChildOfType(file, LuaLocalVarDecl::class.java)!!

        val selectedForLabel = RenamePsiElementProcessor.forElement(labelName)
        assertTrue("a label must be claimed by LuaLabelRenameProcessor", selectedForLabel is LuaLabelRenameProcessor)
        assertTrue("the selected processor must be DumbAware", selectedForLabel is DumbAware)
        assertFalse(
            "a non-label declaration must not be claimed by LuaLabelRenameProcessor",
            RenamePsiElementProcessor.forElement(localDecl) is LuaLabelRenameProcessor,
        )
    }

    /**
     * TC-04-N (`REFACT-04-03`) — renaming from a `goto`, both halves. Calls
     * [LuaLabelRenameProcessor] directly so the assertion does not depend on extension registration
     * order (design §6 E-1, the `LuaUnsupportedRenameProcessor` overlap window).
     *
     * **Mutation (a):** design §3.1 step 3 returns `element` instead of the resolved label — the
     * returned element is a `LuaLabelRef`, not a `LuaLabelName`.
     * **Mutation (b):** step 4 is replaced by `return element` — no exception is thrown and the
     * platform would rename a dangling `goto` in place.
     */
    @Test
    fun testRenameFromAGoto() {
        val resolvableFile =
            myFixture.configureByText(
                "resolvable.lua",
                """
                ::top::
                goto top
                """.trimIndent(),
            )
        val resolvableRef = PsiTreeUtil.findChildOfType(resolvableFile, LuaLabelRef::class.java)!!
        val processor = LuaLabelRenameProcessor()

        assertTrue("a goto to a real label must be claimed", processor.canProcessElement(resolvableRef))
        val substituted = processor.substituteElementToRename(resolvableRef, null)
        assertTrue("substitution must resolve to the LuaLabelName, not the goto", substituted is LuaLabelName)
        assertEquals("top", (substituted as LuaLabelName).name)

        val unresolvedFile = myFixture.configureByText("unresolved.lua", "goto nosuch")
        val unresolvedRef = PsiTreeUtil.findChildOfType(unresolvedFile, LuaLabelRef::class.java)!!

        val refusal =
            try {
                processor.substituteElementToRename(unresolvedRef, null)
                null
            } catch (thrown: CommonRefactoringUtil.RefactoringErrorHintException) {
                thrown
            }
        assertNotNull("a dangling goto must be refused, not renamed in place", refusal)
        assertTrue(
            "the refusal must name the unresolved label: ${refusal?.message}",
            refusal?.message.orEmpty().contains("nosuch"),
        )
    }

    /**
     * The messages the conflicts dialog would have shown. A rename that applies instead of
     * reporting fails here rather than at an assertion further down, because "the file changed
     * silently" is the defect REFACT-04-07 exists against.
     */
    private fun conflictsFromRenamingTo(newName: String): Collection<String> {
        try {
            myFixture.renameElementAtCaret(newName)
        } catch (conflicts: BaseRefactoringProcessor.ConflictsInTestsException) {
            return conflicts.messages
        }
        throw AssertionError(
            "renaming to '$newName' applied silently; a rename that duplicates or rebinds without " +
                "warning is the defect REFACT-04-07 exists against. File is now:\n${myFixture.file.text}",
        )
    }
}
