package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder

/**
 * TYPE-11 Phase 2 — **the doors record what they consume**.
 *
 * Phase 2 is inert by construction: nothing reads a [LuaTypeSourceRecorder.SourceFrame] until
 * `forFile` becomes conditional in Phase 3, so no user-visible behaviour moves and the suite cannot
 * see the wiring at all. That is an argument for the phase being safe, **not** for it being
 * unasserted — the plan called it "inert, so there is nothing to test", which is the same reasoning
 * that let Phase 1 ship two conservative markers with a `?: return` that granted the pin they exist
 * to deny. A frame can be opened here exactly as `forFile` will open one, so every claim in design
 * §3.1, §3.5 and §3.6 about what the type manager reports is directly assertable now.
 *
 * Each test names the single-line mutation that turns it red; the observed reds are in
 * `docs/features/type/11-library-snapshot-invalidation/risks-and-gaps.md`'s ledger.
 */
class LuaTypeManagerRecordingTest : TypeElevenDefinitionLibraryTestCase() {
    private fun <T> recordingRead(body: () -> T): Pair<T, LuaTypeSourceRecorder.SourceFrame> =
        runReadAction { LuaTypeSourceRecorder.recording(body) }

    private fun urlOf(psiFile: PsiFile): String =
        checkNotNull(psiFile.virtualFile?.url) { "fixture file ${psiFile.name} has no VFS url" }

    private fun manager(): LuaTypeManager = LuaTypeManager.getInstance(project)

    /**
     * Design §3.5, `typeOfGlobalIn` row: the file a global resolution read is a recorded source.
     *
     * Mutation: delete `.onEach { LuaTypeSourceRecorder.reportFile(it) }` from `typeOfGlobalIn` →
     * red, `urls` empty.
     */
    fun testResolvingAGlobalRecordsTheFileItWasReadFrom() {
        val declaring = myFixture.addFileToProject("declaring.lua", "recordedGlobal = { field = 1 }\n")
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val (resolved, frame) = recordingRead { manager().resolveGlobal("recordedGlobal", consumer) }

        assertNotNull("the fixture must actually resolve, or an empty frame proves nothing", resolved)
        assertTrue(
            "the declaring file must be a recorded source, but the frame holds ${frame.urls}",
            frame.urls.contains(urlOf(declaring)),
        )
    }

    /**
     * Design §3.1 step 5 / §1.8 B1: a resolution that answered nothing records an **absence**, not
     * silence. The `urls` assertion is the other half — the frame is empty of sources precisely
     * because the answer was unknown, which is the inversion the absence set exists to name.
     *
     * Mutation: drop the `.also { if (it == null) reportAbsence(…) }` from `resolveGlobal`'s
     * `recordInto` body → red, `absences` empty.
     */
    fun testAGlobalNothingDeclaresIsRecordedAsAnAbsence() {
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val (resolved, frame) = recordingRead { manager().resolveGlobal("nothingDeclaresThisName", consumer) }

        assertNull("the fixture must resolve to nothing, or this is not the absence path", resolved)
        assertEquals(setOf("global:nothingDeclaresThisName"), frame.absences)
        assertTrue("no file was read, so the frame is empty of sources", frame.urls.isEmpty())
    }

    /**
     * Design §3.1 step 5b / §1.10 V2: the project-scope pass answered nothing and the all-scope
     * fallback answered, so the **call succeeds** and §3.1 step 5 never fires — yet the answer is
     * exactly the one a project declaration written later out-ranks (BUG-427 ordering).
     *
     * Mutation: drop the `?.also { reportRescuedGlobal(…) }` from `doResolveGlobal`'s fallback →
     * red, `rescuedGlobals` empty while everything else stays green.
     */
    fun testAGlobalOnlyALibraryAnswersIsRecordedAsRescued() {
        installDefinitionLibrary(
            "luassert",
            mapOf("lib.lua" to "---@meta\n\nsharedByLibrary = { beforeEdit = 1 }\n"),
        )
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val (resolved, frame) = recordingRead { manager().resolveGlobal("sharedByLibrary", consumer) }

        assertNotNull("only the all-scope fallback can answer here, and it must", resolved)
        assertEquals(setOf("global:sharedByLibrary"), frame.rescuedGlobals)
        assertTrue(
            "the call succeeded, so step 5's absence must NOT fire — that is what makes 5b necessary",
            frame.absences.isEmpty(),
        )
    }

    /**
     * Design §3.1 step 5d / §1.12 — B1 in the module door. The `ANY` assertion is the whole point:
     * the answer is **non-null**, so the visitor embeds it and the caller records nothing at all
     * unless the absence is stated, and a library whose `require` resolves to nothing would be
     * pinned with an empty frame.
     *
     * Mutation: drop the `.also { reportAbsence("module:…") }` from `doResolveModule`'s `ANY`
     * fall-through → red, `absences` empty.
     */
    fun testAModuleThatResolvesToNothingIsRecordedAsAnAbsence() {
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val (resolved, frame) = recordingRead { manager().resolveModule("nosuchmodulename", consumer) }

        assertSame(
            "the fall-through answer is non-null, which is why the absence is needed",
            LuaPrimitiveType.ANY,
            resolved,
        )
        assertEquals(setOf("module:nosuchmodulename"), frame.absences)
    }

    /**
     * Design §3.6 step 4: a memoized answer replays the sources that produced it, or the second
     * build to ask gets the type with **no** sources and is judged pinnable while depending on the
     * declaring file.
     *
     * `assertSame` is what makes this non-vacuous: `materializeClass` constructs a fresh
     * [net.internetisalie.lunar.lang.psi.types.LuaClassType] on every computation, so an identical
     * instance is proof the second call was served from the cache rather than recomputed — without
     * it, a recompute would refill the frame and the test would pass with replay deleted.
     *
     * Mutation: delete `LuaTypeSourceRecorder.replay(it.sourceFrame)` from `resolveType`'s cache-hit
     * branch → red, the warm frame's `urls` empty.
     */
    fun testAMemoizedTypeReplaysItsSourcesIntoALaterBuild() {
        val declaring =
            myFixture.addFileToProject("declaring.lua", "---@class RecordedClass\nlocal RecordedClass = {}\n")
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val cold = runReadAction { manager().resolveType("RecordedClass", consumer) }
        val (warm, frame) = recordingRead { manager().resolveType("RecordedClass", consumer) }

        assertNotNull("the fixture must resolve the class, or neither call records anything", cold)
        assertSame("the second call must be the memoized answer, or this asserts nothing", cold, warm)
        assertTrue(
            "a cache hit must replay the sources of the answer it serves, but the frame holds ${frame.urls}",
            frame.urls.contains(urlOf(declaring)),
        )
    }

    /**
     * Design §3.6, "the stored value is the whole frame": an answer of `null` recorded an absence,
     * and if replay carried only URLs a cache hit on that null would replay as "no sources at all"
     * and re-create B1 through the cache.
     *
     * The absence reaches the warm frame by **one** mechanism, not two. Design §3.6 also had the
     * cache-hit path re-report the absence directly, which the co-located cache shape makes
     * redundant — an entry cannot exist without its frame — and a conjunction of two sufficient
     * mechanisms is exactly the shape Phase 1's review rejected, because neither member can be shown
     * to carry the behaviour. Only replay is kept, so this test can attribute its red.
     *
     * Mutation: delete `LuaTypeSourceRecorder.replay(it.sourceFrame)` from `resolveGlobal`'s
     * cache-hit branch → red, the warm frame's `absences` empty. (A red here also proves the second
     * call was a cache hit: a recompute would have re-recorded the absence and stayed green.)
     */
    fun testAMemoizedAbsenceReplaysAsAnAbsence() {
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val cold = runReadAction { manager().resolveGlobal("nothingDeclaresThisEither", consumer) }
        val (warm, frame) = recordingRead { manager().resolveGlobal("nothingDeclaresThisEither", consumer) }

        assertNull("the cold call must answer nothing, or there is no absence to replay", cold)
        assertNull(warm)
        assertEquals(setOf("global:nothingDeclaresThisEither"), frame.absences)
    }
}
