package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.psi.types.LuaTypeGraph
import net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * TYPE-11 §3.1 / §3.7 — the recorder's own **algebra**, asserted directly.
 *
 * Phase 1 shipped this object with no caller and therefore no test, on the argument that its
 * behaviour is only observable through Phase 2's integration. That was wrong twice over: nine of its
 * eleven members are a plain Kotlin `object` with no `Project` and no PSI, and the one defect the
 * phase actually shipped — both conservative markers dropping their mark when the URL was null,
 * inverting the safe direction — is exactly what [testAnInProgressHitWithNoUrlStillMarksTheFrame]
 * and [testAWarmSnapshotWithNoUrlStillMarksTheFrame] catch in five lines each.
 *
 * `BasePlatformTestCase` is used only for the three members that take a `PsiFile`; the rest need no
 * fixture at all. Every method states the mutation that turns it red.
 */
class LuaTypeSourceRecorderTest : BasePlatformTestCase() {
    private val servedSnapshots = mutableListOf<LuaTypes>()

    override fun setUp() {
        super.setUp()
        assertEquals("a previous test leaked an open frame onto this thread", 0, LuaTypeSourceRecorder.depth())
    }

    override fun tearDown() {
        try {
            servedSnapshots.forEach { LuaTypeSourceRecorder.snapshotFrames.remove(it) }
            assertEquals("this test leaked an open frame onto the shared thread", 0, LuaTypeSourceRecorder.depth())
        } finally {
            super.tearDown()
        }
    }

    /** A snapshot instance to key [LuaTypeSourceRecorder.snapshotFrames] on; never queried. */
    private fun servedSnapshot(): LuaTypes =
        LuaTypesSnapshot(LuaTypeGraph(), emptyMap()).also { servedSnapshots.add(it) }

    /**
     * A non-physical file: `PsiFileImpl.getVirtualFile` returns `null` when the view provider has no
     * event system, and a file created this way is its own `originalFile`. That is the shape the
     * review named as the exposed one (`DummyHolder` / code-fragment containing files), and every
     * case that uses it asserts the null inside the test, so none of them can pass for an unrelated
     * reason.
     */
    private fun fileWithNoUrl(): PsiFile =
        PsiFileFactory.getInstance(project).createFileFromText("unidentified.lua", LuaFileType, "return 1\n")

    /**
     * Design §3.1 step 3, "the whole correctness of nesting": `forFile(libraryA)` nesting inside
     * `forFile(libraryB)` must leave `libraryB` knowing it consumed `libraryA`'s sources.
     *
     * Mutation: `report`'s `openFrames.get().forEach` → `openFrames.get().last().let` → red on the
     * outer frame.
     */
    fun testReportReachesEveryOpenFrameNotOnlyTheInnermost() {
        val (inner, outer) =
            LuaTypeSourceRecorder.recording {
                LuaTypeSourceRecorder.recording {
                    LuaTypeSourceRecorder.report(listOf("file:///lib/a.lua"))
                }
            }
        assertEquals(
            "the innermost frame must record what it consumed",
            setOf("file:///lib/a.lua"),
            inner.second.urls,
        )
        assertEquals("the enclosing frame must record it too", setOf("file:///lib/a.lua"), outer.urls)
    }

    /**
     * Design §3.1 step 2. A cancelled or failing build must leave no frame behind, or every later
     * build on this pooled thread reports into a frame nobody will ever read.
     *
     * Mutation: drop the `finally` in `recording` (pop on the success path only) → red.
     */
    fun testRecordingPopsItsFrameWhenTheBodyThrows() {
        try {
            LuaTypeSourceRecorder.recording<Unit> { throw IllegalStateException("build cancelled") }
            fail("the exception must propagate; a swallowed body would make the depth assertion vacuous")
        } catch (expected: IllegalStateException) {
            assertEquals("build cancelled", expected.message)
        }
        assertEquals("a throwing build must leave no frame open", 0, LuaTypeSourceRecorder.depth())
    }

    /**
     * `depth()` is what §3.7 step 3 reads to decide a warm hit is nested rather than top-level, so
     * it has to count frames, not merely detect them.
     *
     * Mutation: `depth()` → `if (openFrames.get().isEmpty()) 0 else 1` → red on the nested count.
     */
    fun testDepthCountsEveryOpenFrameAndIsZeroOutsideABuild() {
        assertEquals("no snapshot build is in flight", 0, LuaTypeSourceRecorder.depth())
        LuaTypeSourceRecorder.recording {
            assertEquals(1, LuaTypeSourceRecorder.depth())
            LuaTypeSourceRecorder.recording {
                assertEquals(
                    "a nested build must be distinguishable from a top-level one",
                    2,
                    LuaTypeSourceRecorder.depth(),
                )
            }
            assertEquals("the nested frame is popped on the way out", 1, LuaTypeSourceRecorder.depth())
        }
        assertEquals(0, LuaTypeSourceRecorder.depth())
    }

    /**
     * Design §2.1: `absorb` is the union of **all five** sets. A set omitted here is an
     * incompleteness that stops propagating, which is a pin granted.
     *
     * Mutation: delete any one of the five `addAll` calls in `absorb` → red on that set.
     */
    fun testAbsorbUnionsAllFiveSets() {
        val target = LuaTypeSourceRecorder.SourceFrame()
        target.absorb(populatedFrame())

        assertEquals(setOf("file:///lib/a.lua"), target.urls)
        assertEquals(setOf("global:wx"), target.absences)
        assertEquals(setOf("file:///lib/warm.lua"), target.unreplayedWarm)
        assertEquals(setOf("file:///lib/busy.lua"), target.inProgressHits)
        assertEquals(setOf("global:rescued"), target.rescuedGlobals)
    }

    /**
     * Design §3.1 step 6: a replayed frame lands in **every** open frame, all five sets. This is
     * what makes a cache hit deep inside a build cost the outermost file its pin.
     *
     * Mutation: `replay`'s `forEach` → `last()` → red on the outer frame.
     */
    fun testReplayPropagatesAllFiveSetsIntoEveryOpenFrame() {
        val (_, outer) =
            LuaTypeSourceRecorder.recording {
                LuaTypeSourceRecorder.recording {
                    LuaTypeSourceRecorder.replay(populatedFrame())
                }
            }
        assertEquals(setOf("file:///lib/a.lua"), outer.urls)
        assertEquals(setOf("global:wx"), outer.absences)
        assertEquals(setOf("file:///lib/warm.lua"), outer.unreplayedWarm)
        assertEquals(setOf("file:///lib/busy.lua"), outer.inProgressHits)
        assertEquals(setOf("global:rescued"), outer.rescuedGlobals)
    }

    /**
     * Design §3.1 steps 5, 5b and §3.6: three doors, three sets. Crossing them would make a
     * `resolveType` absence — deliberately *not* recorded (§3.1 step 5) — indistinguishable from a
     * global one, and §3.3 reads each set for a different reason.
     *
     * Mutation: point `reportRescuedGlobal` at `absences` → red on `rescuedGlobals`.
     */
    fun testEachConservativeMarkWritesItsOwnSet() {
        val (_, frame) =
            LuaTypeSourceRecorder.recording {
                LuaTypeSourceRecorder.reportAbsence("global:wx")
                LuaTypeSourceRecorder.reportRescuedGlobal("global:rescued")
                LuaTypeSourceRecorder.reportUnreplayableHit("global:evicted")
            }
        assertEquals(setOf("global:wx"), frame.absences)
        assertEquals(setOf("global:rescued"), frame.rescuedGlobals)
        assertEquals(
            "an evicted cache entry is judged like any other unreplayable hit",
            setOf("global:evicted"),
            frame.unreplayedWarm,
        )
        assertTrue("no source was consumed, so no URL may be claimed", frame.urls.isEmpty())
    }

    /**
     * Design §3.1: "an empty stack is a no-op — the type manager is called from many places that are
     * not snapshot builds, and those must cost nothing."
     *
     * Mutation: `report`'s `openFrames.get().first()` (any non-total accessor) → red with
     * `NoSuchElementException`.
     */
    fun testReportingOutsideAnyBuildIsANoOp() {
        LuaTypeSourceRecorder.report(listOf("file:///lib/a.lua"))
        LuaTypeSourceRecorder.reportAbsence("global:wx")
        LuaTypeSourceRecorder.reportUnreplayableHit("global:evicted")
        assertEquals("reporting outside a build must not open one", 0, LuaTypeSourceRecorder.depth())
    }

    /**
     * Design §3.7 step 4, found branch: the inner file's whole frame propagates, so the outer file
     * inherits its incompleteness rather than a bare URL.
     *
     * Mutation: delete the `if (storedFrame != null)` branch → red, `unreplayedWarm` gains the
     * served file's URL and `absences` stays empty.
     */
    fun testAWarmSnapshotWithAStoredFrameIsReplayedRatherThanMarkedUnreplayable() {
        val servedTypes = servedSnapshot()
        LuaTypeSourceRecorder.snapshotFrames[servedTypes] = populatedFrame()
        val libraryFile = myFixture.configureByText("library.lua", "return {}\n")

        val (_, frame) =
            runReadAction {
                LuaTypeSourceRecorder.recording {
                    LuaTypeSourceRecorder.reportWarmSnapshot(libraryFile, servedTypes)
                }
            }
        assertEquals("the stored frame's sources must propagate", setOf("file:///lib/a.lua"), frame.urls)
        assertEquals("and so must its absences", setOf("global:wx"), frame.absences)
        assertEquals(
            "a replayed hit is not itself unreplayable",
            setOf("file:///lib/warm.lua"),
            frame.unreplayedWarm,
        )
    }

    /**
     * Design §3.7 step 4, not-found branch: a snapshot that outlived its weak entry cannot have its
     * sources replayed, so the outer file is judged unpinnable.
     *
     * Mutation: `?: return` after the lookup (i.e. treat a missing frame as silence) → red.
     */
    fun testAWarmSnapshotWithNoStoredFrameMarksTheFileUnreplayable() {
        val libraryFile = myFixture.configureByText("library.lua", "return {}\n")
        val fileUrl = runReadAction { checkNotNull(libraryFile.virtualFile).url }

        val (_, frame) =
            runReadAction {
                LuaTypeSourceRecorder.recording {
                    LuaTypeSourceRecorder.reportWarmSnapshot(libraryFile, servedSnapshot())
                }
            }
        assertEquals("an unreplayable warm hit is recorded by URL", setOf(fileUrl), frame.unreplayedWarm)
    }

    /**
     * **The F1 regression.** Design §3.1 step 4: the null-URL no-op is `reportFile`'s alone. Here the
     * mark is the whole point — §3.3 step 5 reads `unreplayedWarm` for emptiness — so failing to
     * make it leaves a clean frame, and a clean frame on a provisioned file **is pinned**.
     *
     * Mutation: `?: UNIDENTIFIED_WARM` → `?: return` (the shipped `1be7cc0d` code) → red.
     */
    fun testAWarmSnapshotWithNoUrlStillMarksTheFrame() {
        runReadAction {
            val unidentified = fileWithNoUrl()
            assertNull(
                "this case only discriminates while the file has no URL of its own",
                unidentified.originalFile.virtualFile,
            )

            val (_, frame) =
                LuaTypeSourceRecorder.recording {
                    LuaTypeSourceRecorder.reportWarmSnapshot(unidentified, servedSnapshot())
                }
            assertFalse(
                "a file that cannot be identified is more unknown, not less — the frame must not be clean",
                frame.unreplayedWarm.isEmpty(),
            )
            assertTrue("the sentinel must not reach the provenance predicate via urls", frame.urls.isEmpty())
        }
    }

    /**
     * **The F1 regression, second door.** §3.7 states the invariant without an escape hatch:
     * whenever `inProgressSnapshot` answers non-null at `depth() > 0` the outer file is made
     * unpinnable. It cannot be, if the mark is conditional on a URL being available.
     *
     * Mutation: `?: UNIDENTIFIED_IN_PROGRESS` → `?: return` (the shipped `1be7cc0d` code) → red.
     */
    fun testAnInProgressHitWithNoUrlStillMarksTheFrame() {
        runReadAction {
            val unidentified = fileWithNoUrl()
            assertNull(
                "this case only discriminates while the file has no URL of its own",
                unidentified.originalFile.virtualFile,
            )

            val (_, frame) =
                LuaTypeSourceRecorder.recording {
                    LuaTypeSourceRecorder.reportInProgressHit(unidentified)
                }
            assertFalse(
                "an unidentifiable in-flight build must still cost the outer file its pin",
                frame.inProgressHits.isEmpty(),
            )
            assertTrue("the sentinel must not reach the provenance predicate via urls", frame.urls.isEmpty())
        }
    }

    /**
     * The other half of the asymmetry, asserted so it cannot be "fixed" by symmetry later:
     * `reportFile`'s null **is** a no-op. A sentinel in `urls` would be handed to `isProvisionedUrl`,
     * which would classify it as unprovisioned and cost every such file its pin.
     *
     * ⚠ **This case locks in a no-op that rests on an undischarged premise (DR-19).** An earlier
     * version of this KDoc justified the exemption by mechanism — "a missing URL only weakens §3.3
     * step 3, a test over the URLs that are present" — which is a restatement, not a safety proof,
     * and is the same shape as the argument F1 overturned. Losing the *last* URL leaves `urls`
     * empty, an empty `urls` clears step 3 **vacuously**, and on a provisioned file with the other
     * four sets empty the file **is pinned**. The exemption is safe only under design §3.1 step 4's
     * named premise: *a `PsiFile` reached as a consumed source always has a non-null
     * `originalFile.virtualFile`*. That premise is reasoned, not run — and this green test cements
     * it until Phase 2, which wires the six §3.5 `reportFile` sites and is where it must be gated or
     * the exemption dropped.
     *
     * Mutation: give `reportFile` the same sentinel treatment → red.
     */
    fun testReportFileWithNoUrlRecordsNothing() {
        runReadAction {
            val unidentified = fileWithNoUrl()
            val (_, frame) =
                LuaTypeSourceRecorder.recording {
                    LuaTypeSourceRecorder.reportFile(unidentified)
                    LuaTypeSourceRecorder.reportFile(null)
                }
            assertTrue("a source that cannot be named is simply not a recorded source", frame.urls.isEmpty())
        }
    }

    /** One entry in each of the five sets, so a dropped set is visible rather than merely smaller. */
    private fun populatedFrame(): LuaTypeSourceRecorder.SourceFrame =
        LuaTypeSourceRecorder.SourceFrame().apply {
            urls.add("file:///lib/a.lua")
            absences.add("global:wx")
            unreplayedWarm.add("file:///lib/warm.lua")
            inProgressHits.add("file:///lib/busy.lua")
            rescuedGlobals.add("global:rescued")
        }
}
