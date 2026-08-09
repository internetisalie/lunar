package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.definitions.LibraryRootTestCase

/**
 * COMP-09 §4.9 — the `ProgressManager.checkCanceled()` at the head of each `processValues` callback,
 * **gated**.
 *
 * Phase 1 recorded this line as ungateable. That measurement was real but its conclusion was too
 * wide: what cannot be gated is *a test asserting the throw*. `FileBasedIndexImpl.ensureUpToDate`
 * calls `checkCanceled()` before the value iterator is opened (`FileBasedIndexImpl:893`), so under an
 * already-cancelled indicator `processValues` throws before any callback runs and the plugin's line
 * is invisible. Deleting it leaves such a test green.
 *
 * What *is* observable is the probe itself, and where it falls. The platform checks cancellation
 * **after** each callback (`FileBasedIndexEx:424`, `:455`); the plugin checks **before**. So with the
 * line, two probes straddle one `recordVisit` and see the same file count; without it, every probe is
 * separated from the next by a visit and the counts strictly increase. That is a difference of
 * exactly one probe per callback, and it does not depend on the cancellation ever firing.
 *
 * The instrument is a `CheckCanceledHook`, not a `ProgressIndicator`: `EmptyProgressIndicator` and
 * `ProgressIndicatorBase` declare `isCanceled`/`checkCanceled` final, and a non-standard indicator is
 * pinged from a background thread every `CHECK_CANCELED_DELAY_MILLIS` (`CoreProgressManager:143`),
 * which would corrupt the count. Registering a hook flips `ourCheckCanceledBehavior` to
 * `ONLY_HOOKS` (`CoreProgressManager:872`) so that **every** `ProgressManager.checkCanceled()` on
 * this thread reaches it — which is precisely the call the plugin makes.
 *
 * Measured on gce-builder 2026-08-09, three declaring files, probes filtered to those taken after the
 * first visit: **`[1, 1, 2, 2, 3]` with the line, `[1, 2, 3]` without it**. The platform alone
 * produces no repeat, so the repeat is not another guard standing in for this one.
 *
 * **This gates the union door only, and the asymmetry is measured rather than assumed.** The same
 * test written against `globalMembership` **survives** deleting `membersInFile`'s `checkCanceled()`
 * (`LuaReceiverMemberIndex:510`) — measured: `[1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,3]` with the line,
 * `[1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,2,3]` without. `membershipOver` reads one file per
 * `processValues` call, so each call brings its own run of platform probes at an unchanged file
 * count; a repeat is produced whether or not the plugin adds one, and the totals overlap run to run.
 * That method was written, mutation-tested, found vacuous and **removed** rather than kept. The line
 * at `:510` is therefore present, required by §4.9, and **not** gated — which is the narrow version
 * of the claim Phase 1 made about both.
 */
class LuaReceiverMemberCancellationTest : LibraryRootTestCase() {
    /** The union door, `membersIn` — `LuaReceiverMemberIndex:405`. */
    fun testTheUnionDoorProbesCancellationBeforeEachCallbackAndNotOnlyAfterIt() {
        seedThreeDeclaringFiles()
        assertProbedTwiceWithinOneCallback("membersIn", probeUnionDoor())
    }

    /**
     * [Probed.trace] carries the visited-file count at every `checkCanceled()` on this thread. The
     * leading run of zeroes is the platform's own work before the first callback — its length moves
     * run to run, which is why the assertion is on the shape of the tail and never on a probe index.
     */
    private fun assertProbedTwiceWithinOneCallback(
        door: String,
        probed: Probed,
    ) {
        val duringWork = probed.trace.filter { it >= 1 }
        println("COMP-09 §4.9 $door: files=${probed.files} probes-after-first-visit=$duringWork")
        assertEquals("the fixture must give $door more than one file to read", DECLARING_FILES, probed.files)
        assertTrue(
            "$door: every cancellation probe fell between two `recordVisit`s ($duringWork), so the " +
                "only checks running are the platform's post-callback ones — §4.9's " +
                "`ProgressManager.checkCanceled()` at the head of the callback is gone",
            duringWork.size > duringWork.distinct().size,
        )
    }

    private data class Probed(
        val files: Int,
        val trace: List<Int>,
    )

    /** Runs the union door in a read action with a counting hook installed, and returns what it saw. */
    private fun probeUnionDoor(): Probed {
        val manager = ProgressManager.getInstance()
        assertTrue("this platform build has no hookable ProgressManager", manager is ProgressManagerImpl)
        val hookable = manager as ProgressManagerImpl
        val hook = TracingHook()
        hookable.addCheckCanceledHook(hook)
        val files =
            try {
                runReadAction {
                    LuaReceiverMemberIndex.membersIn(RECEIVER, project, GlobalSearchScope.allScope(project))
                    LuaReceiverMemberWork.files
                }
            } finally {
                hookable.removeCheckCanceledHook(hook)
            }
        return Probed(files, hook.trace)
    }

    /** Counts probes on the test thread only — hooks are global and every thread's checks reach them. */
    private class TracingHook : CoreProgressManager.CheckCanceledHook {
        private val owner = Thread.currentThread()
        private val recorded = mutableListOf<Int>()

        val trace: List<Int> get() = recorded.toList()

        override fun runHook(indicator: ProgressIndicator?): Boolean {
            if (Thread.currentThread() === owner) recorded.add(LuaReceiverMemberWork.files)
            return false
        }
    }

    /** Three declaring files, so the union door makes three callbacks and the shape has room to show. */
    private fun seedThreeDeclaringFiles() {
        registerLibraryRoot(
            (0 until DECLARING_FILES).associate { file ->
                val text = StringBuilder("---@meta\n\n$RECEIVER = {}\n\n")
                repeat(MEMBERS_PER_FILE) { i -> text.append("function $RECEIVER.f$file$i() end\n\n") }
                "cancel-$file.lua" to text.toString()
            },
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
    }

    private companion object {
        const val RECEIVER = "Cancellable"
        const val DECLARING_FILES = 3
        const val MEMBERS_PER_FILE = 2
    }
}
