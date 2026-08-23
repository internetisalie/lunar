package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaNameRef
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicInteger

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
 *
 * [testCancellationIsCheckedPerIndexHitNotPerCall],
 * [testCancellationIsCheckedPerUsageNotPerCollisionsCall] and
 * [testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall] are about the detector's *cost*
 * rather than its verdicts, and live here because the subject is the same object. Between them
 * they pin the three guarded iteration blocks over three independent dimensions — stub hits,
 * usages, and the file's name refs — which is one dimension per block and no fewer.
 */
@RunWith(JUnit4::class)
class LuaRenameConflictTest : BasePlatformTestCase() {
    /**
     * TC-14 — C1. `print(x)` sits where `local y = 2` is already visible, so rewriting it to `y`
     * would bind it to that declaration instead. The conflict is anchored on the *capturing*
     * declaration rather than on `print(x)` because that is the element the user must look at —
     * not, as this KDoc said through Phase 4, because anchoring on a usage would skip rewriting it
     * (see [testCollisionAnchoredOnAUsageIsStillRewritten]: it does not).
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
     * TC-37 — C4 for a **dotted** function, the case TC-31's `config = {}` fixture cannot reach.
     *
     * Same defect as TC-31 and a different key. `LuaFuncStubElementType.indexStub` files
     * `function M.run() end` under the FUNC_NAME text `"M.run"` and, via `substringBefore('.')`,
     * under the receiver `"M"` — **never** under `"run"`, which is the last segment
     * `LuaDeclarationSite.identifierLeafOf` hands rename as the target. So while C3/C4 searched
     * `target.identifier.text` they searched a key nothing writes, and every dotted rename passed
     * conflict detection in silence: measured on this exact fixture, `ReferencesSearch` finds
     * **0** references (two `M.run` decls make `multiResolve` ambiguous and `resolve()` null), so
     * the declaration is rewritten and `M.run()` is left behind with no warning — BUG-457's shape
     * inside the feature that closed BUG-457.
     *
     * The assertion names `"M.run"` rather than `"run"` because the qualified key is what was
     * searched and what is ambiguous; a message naming `run` would be reporting a name that is not
     * declared anywhere. Mutation-proved: restoring either rule's bare `target.identifier.text`
     * lookup drops this to zero conflicts and the rename applies silently.
     */
    @Test
    fun testDottedFunctionDeclaredTwiceIsReported() {
        myFixture.addFileToProject("b.lua", "function M.run() end\n")
        myFixture.addFileToProject("c.lua", "M.run()\n")
        myFixture.configureByText("a.lua", "function M.r<caret>un() end\n")

        val reported = conflictsFromRenamingTo("start")

        assertReports(LuaBundle.message("refactoring.rename.conflict.ambiguousGlobal", "M.run", 2), reported)
    }

    /**
     * TC-41 / **BUG-466** — a dotted function beside a same-named *field assignment*. C4's third
     * candidate source, and the one whose absence let a measured data-loss path ship.
     *
     * `LuaNameReference.doMultiResolve`'s qualified branch consults **two** sources for `M.run`:
     * `LuaGlobalDeclarationIndex` (the stub of `function M.run()`) and
     * `LuaMemberFieldNavigation.find` (the assignment in `b.lua`). With both present `multiResolve`
     * yields two results, `resolve()` is null, and `isReferenceTo` is false — so `c.lua`'s call
     * site is not a findable reference. Measured by *printing* each reference rather than counting
     * them: `ReferencesSearch` returns **one** either way, but with `b.lua` present that one is
     * `b.lua`'s assignment target and the call site is gone. A count-based assertion cannot see
     * this, which is why the assertion is on the rule that must fire.
     *
     * Before the fix C4 read the stub index alone, counted one declaration, returned early, and the
     * rename rewrote both declarations while `c.lua` kept calling a name that now resolves to
     * nothing. Mutation-proved: dropping the `LuaMemberFieldNavigation.find` term from
     * `globalDeclarationsNamed` makes this case apply silently and `conflictsFromRenamingTo` fails
     * with its own "applied silently" message.
     */
    @Test
    fun testDottedFunctionBesideAFieldAssignmentIsReported() {
        myFixture.addFileToProject("b.lua", "M.run = function() end\n")
        myFixture.addFileToProject("c.lua", "M.run()\n")
        myFixture.configureByText("a.lua", "function M.r<caret>un() end\n")

        val reported = conflictsFromRenamingTo("start")

        assertReports(LuaBundle.message("refactoring.rename.conflict.ambiguousGlobal", "M.run", 2), reported)
    }

    /**
     * TC-42 — the platform fact [testDottedFunctionBesideAFieldAssignmentIsReported] rests on,
     * pinned directly, because the belief that it was the *opposite* fact is what kept BUG-466 open.
     *
     * Phase 4 recorded, in five documents and two KDocs, that anchoring a collision on an element
     * that is also a usage would make the platform skip rewriting that usage on Continue, and
     * deferred BUG-466 for that reason alone. It is false.
     * `RenameUtil.removeConflictUsages` (`RenameUtil.java:297-307`) iterates the usage set and
     * removes only `usageInfo instanceof UnresolvableCollisionUsageInfo` — collision *objects*, not
     * every info sharing an anchor element — and `UsageInfo.equals` (`UsageInfo.java:348-359`)
     * opens with `!getClass().equals(o.getClass())`, so a real usage and a collision on the same
     * element are never equal and cannot displace one another in the `LinkedHashSet`.
     *
     * This is TC-41's fixture with the conflict acknowledged rather than asserted:
     * `withIgnoredConflicts` is the platform's own "user pressed Continue" path
     * (`BaseRefactoringProcessor.java:539`).
     *
     * **The anchor is asserted before the rename, not assumed**, which is also what keeps this case
     * from being green on the parent commit: without the member-field candidate there are *zero*
     * collisions, `withIgnoredConflicts` suppresses nothing, and a test that only checked the files
     * afterwards would pass while proving nothing about anchoring at all. `c.lua` is asserted
     * **unchanged** on purpose: it is the residual BUG-466 reports and does not repair, and quietly
     * expecting it to be rewritten would be asserting a fix nobody wrote.
     */
    @Test
    fun testCollisionAnchoredOnAUsageIsStillRewritten() {
        val fieldAssignment = myFixture.addFileToProject("b.lua", "M.run = function() end\n")
        val callSite = myFixture.addFileToProject("c.lua", "M.run()\n")
        myFixture.configureByText("a.lua", "function M.r<caret>un() end\n")
        val anchors = LuaRenameConflictDetector.collisions(renameTargetAtCaret("start"), emptyList())
        assertEquals(
            "the conflict must be anchored on b.lua's field assignment for this case to mean anything",
            listOf("b.lua"),
            anchors.mapNotNull { it.element?.containingFile?.name },
        )

        BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts<RuntimeException> {
            myFixture.renameElementAtCaret("start")
        }

        assertEquals("the collision anchor was skipped", "M.start = function() end\n", fieldAssignment.text)
        myFixture.checkResult("function M.start() end\n")
        assertEquals("BUG-466's residual is reported, not repaired", "M.run()\n", callSite.text)
    }

    /**
     * TC-38 — C3 for a dotted function: the *new* name is what has to carry the receiver prefix.
     *
     * `function M.run()` renamed to `go` becomes `M.go`, so the name that would merge is `M.go`
     * and not `go`. A bare-`newName` lookup finds nothing here — measured: `"go"` has 0 stub hits
     * while `"M.go"` has 1 — which is the same blindness as TC-37 arriving through the other rule,
     * and it is why [searchKeyOf][LuaRenameConflictDetector] is applied to both names rather than
     * only to the old one.
     */
    @Test
    fun testExistingDottedFunctionOfTheNewNameIsReported() {
        myFixture.addFileToProject("b.lua", "function M.go() end\n")
        myFixture.configureByText("a.lua", "function M.r<caret>un() end\n")

        val reported = conflictsFromRenamingTo("go")

        assertReports(LuaBundle.message("refactoring.rename.conflict.globalExists", "M.go"), reported)
    }

    /**
     * The engineering contract's cancellation rule, pinned **differentially** — the one property
     * `LuaRenameConflictDetector.globalDeclarationsNamed`'s loop-body check exists for, which is
     * that a rename over a large project can be cancelled *between* stub hits rather than only
     * between calls. Each hit costs a `getNode()` parse of one file, so "once per call" is
     * unbounded latency.
     *
     * **Why the obvious test is not written instead: it cannot fail.** Cancelling an indicator and
     * asserting `ProcessCanceledException` stays green with the loop-body check deleted, because
     * `globalDeclarationsNamed`'s entry check and `LuaGlobalAssignmentNavigation.find`'s own two
     * checks catch a cancelled indicator regardless. Asserting an absolute count is the opposite
     * error: it couples to every upstream check and reddens on any neighbouring refactor.
     *
     * **The delta has neither problem.** Both runs share one project, one target and one empty
     * usage list, so `captures` and both entry checks contribute the *same* constant and cancel
     * out of the subtraction; only the number of stub hits differs, 2 versus 7. Attribution is by
     * immediate caller — a `StackWalker` frame test, not a raw total — because the total is
     * dominated by the platform's own checks *underneath* the detector (measured: 386 versus 1095,
     * of which 5 versus 10 are the detector's own), and those scale with file count too, which
     * would have made a total-based delta insensitive to the mutant.
     *
     * Mutation-proved: deleting the `ProgressManager.checkCanceled()` inside
     * `globalDeclarationsNamed`'s `map` collapses both counts to 3 and the delta to 0.
     */
    @Test
    fun testCancellationIsCheckedPerIndexHitNotPerCall() {
        myFixture.addFileToProject("b1.lua", "function shared() end\n")
        myFixture.configureByText("a.lua", "function sha<caret>red() end\n")
        val target = renameTargetAtCaret("aNameNothingElseDeclares")

        val withTwoDeclarations = detectorCancellationChecks(target, emptyList())
        (2..6).forEach { myFixture.addFileToProject("b$it.lua", "function shared() end\n") }
        val withSevenDeclarations = detectorCancellationChecks(target, emptyList())

        assertTrue(
            "five more stub hits must cost five more cancellation checks; got " +
                "$withTwoDeclarations then $withSevenDeclarations, i.e. the detector checks once " +
                "per CALL and a cancelled rename waits for every hit to be parsed",
            withSevenDeclarations - withTwoDeclarations >= 5,
        )
    }

    /**
     * The seventh iteration block — `captures`' `usages.mapNotNull { it.element }`, which the
     * Phase-3 audit omitted while describing itself as covering "the remaining six".
     *
     * It is the one block there whose body reaches PSI and VFS: `UsageInfo.getElement()` derefs a
     * **soft** `SmartPsiElementPointer`, and on a miss `SelfElementInfo.restoreElement` ends at
     * `PsiManager.findFile(vfile)` — a parse. Guarding it was cheap; leaving it unpinned would
     * have made it deletable by the next refactor with the whole suite still green.
     *
     * Same differential shape as the case above, over a different dimension: one project, one
     * target, one declaration count — so the two `globalDeclarationsNamed` calls contribute an
     * identical constant — with only the usage-list length differing, 1 versus 6. `captures`
     * iterates `usages` **twice**, once here and once over `sites`, so the guarded delta is
     * `2 x 5` and deleting this block's check halves it to 5. That is why the bound is 10 and not
     * 5: a bound of 5 would be satisfied by the `sites` loop alone and could not fail.
     */
    @Test
    fun testCancellationIsCheckedPerUsageNotPerCollisionsCall() {
        myFixture.configureByText(
            "a.lua",
            "function sha<caret>red() end\n" + "print(shared)\n".repeat(6),
        )
        val target = renameTargetAtCaret("aNameNothingElseDeclares")
        val reads = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaNameRef::class.java).map { UsageInfo(it) }

        val withOneUsage = detectorCancellationChecks(target, reads.take(1))
        val withSixUsages = detectorCancellationChecks(target, reads.take(6))

        assertTrue(
            "five more usages must cost ten more cancellation checks — two loops over the same " +
                "list — but cost ${withSixUsages - withOneUsage}, so one of the two runs " +
                "unguarded, and it is the one that can restore a smart pointer by parsing a file",
            withSixUsages - withOneUsage >= 10,
        )
    }

    /**
     * TC-39 — the **third** guarded iteration block, [LuaRenameConflictDetector.shadows]' filter,
     * which neither cancellation case above can reach: both use a `GLOBAL_FUNCTION` target, and
     * `collisions` runs `shadows` only for a file-local kind, so between them they pinned three
     * blocks and left this one deletable with the whole suite green.
     *
     * It is a block that must be guarded on the invariant's own terms: it walks **every**
     * `LuaNameRef` in the file, and its body's `visibleDeclarationOf` runs a scope crawl over
     * loaded PSI per candidate. Cost scales with file size, which is precisely when a user cancels.
     *
     * Same differential shape as the two above, over the third dimension — file size. One usage
     * list (empty) and one file-local target in both runs, so `captures` contributes the same
     * single check and cancels out of the subtraction; only the `LuaNameRef` count differs, 3
     * versus 13. Measured: 4 checks then 14. Mutation-proved: deleting the
     * `ProgressManager.checkCanceled()` inside `shadows`' filter collapses both to 1 and the delta
     * to 0.
     */
    @Test
    fun testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall() {
        myFixture.configureByText("small.lua", "local <caret>x = 1\nprint(x)\n")
        val withThreeNameRefs = detectorCancellationChecks(renameTargetAtCaret("aNameNothingElseDeclares"), emptyList())
        myFixture.configureByText("large.lua", "local <caret>x = 1\n" + "print(x)\n".repeat(6))
        val withThirteenNameRefs =
            detectorCancellationChecks(renameTargetAtCaret("aNameNothingElseDeclares"), emptyList())

        assertTrue(
            "ten more name refs in the file must cost ten more cancellation checks, but cost " +
                "${withThirteenNameRefs - withThreeNameRefs}, so the shadow rule scans a whole " +
                "file's references without ever offering to stop",
            withThirteenNameRefs - withThreeNameRefs >= 10,
        )
    }

    /** The declaration under the caret, as the detector's own input record. */
    private fun renameTargetAtCaret(newName: String): LuaRenameTarget {
        val caretLeaf =
            requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
                "no leaf at caret in ${myFixture.file.text}"
            }
        val declaration =
            requireNotNull(LuaDeclarationSite.identifierLeafOf(caretLeaf)) {
                "the caret is not on a declaration site"
            }
        val kind =
            requireNotNull(LuaDeclarationSite.kindOf(declaration)) {
                "the declaration under the caret is unclassified"
            }
        return LuaRenameTarget(declaration, kind, newName)
    }

    /**
     * How many `ProgressManager.checkCanceled()` calls one `collisions` run makes **from the
     * detector itself**. `ProgressManagerImpl`'s check-canceled hook is the only observation point
     * that does not require the indicator to be cancelled: with no cancelled indicator on the
     * thread, `CoreProgressManager.doCheckCanceled` takes its `ONLY_HOOKS` branch and an ordinary
     * counting `ProgressIndicator` would never be consulted at all.
     */
    private fun detectorCancellationChecks(
        target: LuaRenameTarget,
        usages: List<UsageInfo>,
    ): Int {
        val checks = AtomicInteger()
        val hook =
            CoreProgressManager.CheckCanceledHook {
                if (calledDirectlyByTheDetector()) checks.incrementAndGet()
                false
            }
        val progressManager = ProgressManager.getInstance() as ProgressManagerImpl
        progressManager.runWithHook(hook) {
            LuaRenameConflictDetector.collisions(target, usages)
        }
        return checks.get()
    }

    /**
     * True when the innermost frame below this test's own hook and the platform's progress plumbing
     * is the detector — i.e. the detector called `checkCanceled` itself, rather than some platform
     * routine running underneath it doing so.
     */
    private fun calledDirectlyByTheDetector(): Boolean =
        StackWalker.getInstance().walk { frames ->
            frames
                .limit(FRAME_SEARCH_DEPTH)
                .filter { !it.className.startsWith(javaClass.name) && !it.className.startsWith(PROGRESS_PACKAGE) }
                .findFirst()
                .map { it.className == DETECTOR }
                .orElse(false)
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

    private companion object {
        const val DETECTOR = "net.internetisalie.lunar.refactoring.rename.LuaRenameConflictDetector"
        const val PROGRESS_PACKAGE = "com.intellij.openapi.progress"
        const val FRAME_SEARCH_DEPTH = 20L
    }
}
