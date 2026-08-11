package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiFile
import java.util.Collections
import java.util.WeakHashMap

/**
 * TYPE-11 §2.1 / §3.1 — records, for the duration of one `buildSnapshot`, every file whose content
 * was consumed to answer a cross-file type question **and every point at which the answer to that
 * question was unknown**.
 *
 * The second half is the whole reason this is not a set of URLs. A frame that is empty because a
 * resolution answered nothing is indistinguishable, to a reader of [SourceFrame.urls] alone, from a
 * frame that is empty because nothing was consumed — and the first was measured shipping a stale
 * type four separate ways (design §1.8 B1/B4, §1.10 V1/V2). Each of the four non-`urls` sets exists
 * for one of those measured shapes, so "sources unknown" can be told apart from "no sources".
 *
 * A frame holds `String` URLs only, never `VirtualFile` or `PsiFile` references (engineering
 * contract §4). [snapshotFrames] is keyed on a [LuaTypes] snapshot — not a framework object —
 * through a [WeakHashMap], so a discarded snapshot takes its frame with it.
 *
 * Threading: the frame **stack** is a [ThreadLocal] and needs no synchronization of its own.
 * [snapshotFrames] does: a read action is *shared*, not exclusive, so any number of pooled threads
 * hold one concurrently, and `WeakHashMap` mutates on lookup while expunging stale entries. It is
 * wrapped in [Collections.synchronizedMap] for exactly the reason the three sibling caches in
 * `LuaTypeManagerImpl` are (`LuaTypeManagerImpl.kt:39`, `:51`, `:63`).
 */
object LuaTypeSourceRecorder {
    /**
     * One `buildSnapshot`'s worth of recorded provenance.
     *
     * @property urls files whose content was consumed.
     * @property absences `"global:<name>"` / `"module:<name>"` — a resolution that answered nothing,
     *   so a declaration written later would change the answer (design §1.8 B1, §1.12).
     * @property unreplayedWarm a memoized answer served warm whose recorded frame was gone, so its
     *   sources cannot be replayed (design §1.8 B4, §3.6).
     * @property inProgressHits a nested `forFile` served from a build still in flight on this
     *   thread, whose frame is incomplete by construction (design §1.10 V1).
     * @property rescuedGlobals a global the project scope did not answer and the all-scope fallback
     *   did, which a later project declaration out-ranks (design §1.10 V2).
     */
    class SourceFrame {
        val urls: MutableSet<String> = mutableSetOf()
        val absences: MutableSet<String> = mutableSetOf()
        val unreplayedWarm: MutableSet<String> = mutableSetOf()
        val inProgressHits: MutableSet<String> = mutableSetOf()
        val rescuedGlobals: MutableSet<String> = mutableSetOf()

        fun absorb(other: SourceFrame) {
            urls.addAll(other.urls)
            absences.addAll(other.absences)
            unreplayedWarm.addAll(other.unreplayedWarm)
            inProgressHits.addAll(other.inProgressHits)
            rescuedGlobals.addAll(other.rescuedGlobals)
        }
    }

    private val openFrames = ThreadLocal.withInitial { ArrayDeque<SourceFrame>() }

    /**
     * Stands in for a URL that could not be determined, so an incompleteness mark can still be made.
     *
     * The null direction is **inverted** between [reportFile] and the two conservative markers, which
     * is why they cannot share `?: return`. [reportFile]'s null is safe: one fewer URL in
     * [SourceFrame.urls] only weakens §3.3 step 3, which is a test over URLs that *are* present.
     * [reportInProgressHit] and [reportWarmSnapshot]'s not-found branch exist to make the frame
     * **non-empty** so §3.3 steps 5 and 6 reject the pin — so a `return` there leaves a clean frame,
     * and a clean frame on a provisioned file clears steps 2–7 and **is pinned**. That is verbatim
     * the inversion design §3.4 names and that §1.8 B1/B4 and §1.10 V1/V2 each measured shipping a
     * stale type. Not being able to identify the file just served is *more* unknown, not less, and
     * §1.12 leaves no room to defer it: a pin must be correct at the moment it is taken.
     *
     * A sentinel rather than a frame-level `Boolean` because a frame holds `String` only
     * (engineering contract §4), and the sets it lands in are read for emptiness, never for
     * provenance — §3.3 step 3 iterates [SourceFrame.urls] alone, which no sentinel is written to.
     */
    private const val UNIDENTIFIED_SOURCE = "unidentified:"

    private const val UNIDENTIFIED_IN_PROGRESS = UNIDENTIFIED_SOURCE + "in-progress-hit"

    private const val UNIDENTIFIED_WARM = UNIDENTIFIED_SOURCE + "warm-snapshot"

    /** Snapshot instance → the frame recorded when it was built. Weak keys (design §3.7). */
    val snapshotFrames: MutableMap<LuaTypes, SourceFrame> = Collections.synchronizedMap(WeakHashMap())

    /**
     * Runs [body] with a fresh frame open, and returns its result paired with what was recorded.
     * The frame is popped in a `finally`, so a cancelled or failing build leaves no frame behind.
     */
    fun <T> recording(body: () -> T): Pair<T, SourceFrame> {
        val frame = SourceFrame()
        val stack = openFrames.get()
        stack.addLast(frame)
        return try {
            Pair(body(), frame)
        } finally {
            stack.removeLast()
        }
    }

    /**
     * Adds [sourceUrls] to **every** open frame, not only the innermost (design §3.1 step 3).
     *
     * That is the whole correctness of nesting: `forFile(libraryA)` can nest inside
     * `forFile(libraryB)`, and filling only the innermost frame would judge `libraryB` pinnable
     * while it transitively depends on a project file through `libraryA`.
     */
    fun report(sourceUrls: Collection<String>) {
        if (sourceUrls.isEmpty()) return
        openFrames.get().forEach { it.urls.addAll(sourceUrls) }
    }

    /**
     * Reads the file's own URL through `originalFile`; a null at any step is a no-op.
     *
     * The no-op grant of design §3.1 step 4 is **this function's alone** — see [UNIDENTIFIED_SOURCE]
     * for why the two conservative markers must mark instead of returning.
     */
    fun reportFile(psiFile: PsiFile?) {
        val sourceUrl = psiFile?.originalFile?.virtualFile?.url ?: return
        report(listOf(sourceUrl))
    }

    /** A resolution that answered nothing — design §3.1 step 5 / 5d. */
    fun reportAbsence(resolutionKey: String) {
        openFrames.get().forEach { it.absences.add(resolutionKey) }
    }

    /** A global the project scope missed and the all-scope fallback answered — design §3.1 step 5b. */
    fun reportRescuedGlobal(resolutionKey: String) {
        openFrames.get().forEach { it.rescuedGlobals.add(resolutionKey) }
    }

    /**
     * A nested `forFile` served from a build still in flight on this thread — design §3.1 step 5c.
     *
     * Marks with [UNIDENTIFIED_IN_PROGRESS] when the URL is unavailable; §3.7 states the invariant
     * ("whenever it answers non-null at `depth() > 0` … §3.3 step 6 makes the outer file
     * unpinnable") without an escape hatch, and a `return` here would grant the pin instead.
     */
    fun reportInProgressHit(psiFile: PsiFile) {
        val sourceUrl = psiFile.originalFile.virtualFile?.url ?: UNIDENTIFIED_IN_PROGRESS
        openFrames.get().forEach { it.inProgressHits.add(sourceUrl) }
    }

    /**
     * A nested `forFile` served warm (design §3.7 step 4). Its recorded frame is replayed when one
     * survives, so the inner file's sources, absences and incompleteness all propagate outward;
     * when it does not, the URL lands in `unreplayedWarm` and the outer file is judged unpinnable —
     * or [UNIDENTIFIED_WARM] does, when the served file cannot be identified at all.
     */
    fun reportWarmSnapshot(
        psiFile: PsiFile,
        servedTypes: LuaTypes,
    ) {
        val storedFrame = snapshotFrames[servedTypes]
        if (storedFrame != null) {
            replay(storedFrame)
            return
        }
        val sourceUrl = psiFile.originalFile.virtualFile?.url ?: UNIDENTIFIED_WARM
        openFrames.get().forEach { it.unreplayedWarm.add(sourceUrl) }
    }

    /**
     * A `resolveX` cache **hit** whose stored frame is gone (design §3.6). String-keyed, because at
     * a cache hit there is neither a `PsiFile` nor a served [LuaTypes] — only `"global:wx"`. It
     * writes `unreplayedWarm`, so the pinnable decision judges it like any other unreplayable hit
     * rather than as silence.
     */
    fun reportUnreplayableHit(resolutionKey: String) {
        openFrames.get().forEach { it.unreplayedWarm.add(resolutionKey) }
    }

    /** Absorbs a stored frame — all five sets — into every open frame (design §3.1 step 6). */
    fun replay(sourceFrame: SourceFrame) {
        openFrames.get().forEach { it.absorb(sourceFrame) }
    }

    /** How many frames are open on this thread; `0` means no snapshot build is in flight. */
    fun depth(): Int = openFrames.get().size
}
