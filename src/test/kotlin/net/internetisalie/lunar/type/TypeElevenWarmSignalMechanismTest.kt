package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * TYPE-11 §3.7 steps 2–4, **as the platform actually behaves** — the Phase 3 review's F1.
 *
 * §3.7 used to describe a `var computed = false` set by the provider as `forFile`'s warm signal, and
 * called it "a *conservative* warm signal, not an exact one". It is neither: it is not a signal at
 * all after the first call. `CachedValuesManager.getCachedValue`'s `PsiElement` overload reads the
 * stored `ParameterizedCachedValue` out of user data and returns `value.getValue(context)` **before
 * it looks at the provider it was passed** (`CachedValuesManager.java:216-224`), so the lambda built
 * by call *n* is discarded for every `n > 1` — including the calls that recompute. The flag was
 * therefore `true` on exactly one call per file per session and `false` on every other, hit or
 * recompute alike, which is the warm branch unconditionally.
 *
 * Both halves of the deletion are measured here: [testAProviderPassedToALaterCallIsNeverTheOneThatRuns]
 * that the flag could not work, and [testAColdNestedBuildLeavesTheOuterFrameExactlyTheUnion] that
 * removing it changes nothing observable, because the unconditional replay on the cold path is a
 * set-wise no-op.
 */
class TypeElevenWarmSignalMechanismTest : TypeElevenDefinitionLibraryTestCase() {
    private val probeKey: Key<CachedValue<String>> = Key.create("lunar.test.type11.warmSignalProbe")

    private val invalidationTracker = SimpleModificationTracker()

    /** The probe's own memoized value, recomputed whenever [invalidationTracker] is ticked. */
    private fun computeThrough(
        probeFile: PsiFile,
        onCompute: () -> Unit,
    ): String =
        runReadAction {
            CachedValuesManager.getCachedValue(probeFile, probeKey) {
                onCompute()
                CachedValueProvider.Result.create("memoized", invalidationTracker)
            }
        }

    /**
     * The platform half of F1, on the real API rather than on `forFile` — the flag's failure has
     * nothing to do with Lua and everything to do with which provider instance the cache keeps.
     *
     * Mutation that would make this red: the platform starting to honour the provider handed to the
     * current call. That is exactly the day §3.7's old wording would become true again, so a red
     * here is a signal to re-read it, not a flake.
     */
    fun testAProviderPassedToALaterCallIsNeverTheOneThatRuns() {
        val probeFile = myFixture.configureByText("warm-signal-probe.lua", "local pad = 1\n")
        val computations = AtomicInteger()
        computeThrough(probeFile) { computations.incrementAndGet() }
        val afterFirstCall = computations.get()

        invalidationTracker.incModificationCount()
        val secondProviderRan = AtomicBoolean()
        computeThrough(probeFile) {
            secondProviderRan.set(true)
            computations.incrementAndGet()
        }

        assertTrue(
            "the fixture must force a real recompute, or it says nothing about which provider ran " +
                "(computations=${computations.get()}, after the first call=$afterFirstCall)",
            computations.get() > afterFirstCall,
        )
        assertFalse(
            "the recompute ran the FIRST call's provider: a `providerRan` flag set inside the lambda " +
                "of the current call cannot distinguish a recompute from a cache hit",
            secondProviderRan.get(),
        )
    }

    /**
     * The behavioural half of F1: with the flag gone, a **cold** nested `forFile` replays the frame
     * it has just registered into the frame that asked, and that replay is a no-op.
     *
     * It has to be, because [LuaTypeSourceRecorder.report] and every sibling reporter write to
     * *every* open frame, not the innermost — so whatever the inner build recorded is already in the
     * outer frame by the time the inner frame is registered. Asserting the union rather than "no
     * crash" is the point: it is what makes the deletion behaviour-preserving rather than merely
     * green. The project URL assertion keeps the equalities from holding vacuously over empty sets.
     *
     * Mutation: delete `snapshotFrames[builtTypes] = sourceFrame` (§3.7 step 1) from `forFile` → red,
     * because the unconditional report then finds no frame and marks `unreplayedWarm`.
     */
    fun testAColdNestedBuildLeavesTheOuterFrameExactlyTheUnion() {
        val libraryRoot =
            installDefinitionLibrary("luassert", mapOf("b.lua" to "---@meta\n\nbGlobal = projectSeed\n"))
        val projectDeclaration = myFixture.addFileToProject("p.lua", "projectSeed = { beforeEdit = 1 }\n")
        announceRootsChange()
        val innerFile = checkNotNull(libraryRoot.findChild("b.lua"))
        val projectUrl = checkNotNull(projectDeclaration.virtualFile?.url)

        val outerFrame =
            runReadAction {
                LuaTypeSourceRecorder.recording { LuaTypesSnapshot.forFile(psiFileOf(innerFile)) }.second
            }
        assertTrue(
            "a build that registered its own frame moments ago must never be reported as an " +
                "unreplayable warm hit (${outerFrame.unreplayedWarm})",
            outerFrame.unreplayedWarm.isEmpty(),
        )
        assertTrue(
            "the nested build must have reported something outward, or every equality below holds " +
                "over empty sets (${outerFrame.urls})",
            outerFrame.urls.contains(projectUrl),
        )

        val innerFrame = frameOf(snapshotOf(innerFile))
        assertEquals(
            "the cold nested build already reported into the open outer frame, so replaying its " +
                "registered frame on top must add nothing",
            innerFrame.urls,
            outerFrame.urls,
        )
        assertEquals("absences must match for the same reason", innerFrame.absences, outerFrame.absences)
        assertEquals("as must in-progress hits", innerFrame.inProgressHits, outerFrame.inProgressHits)
        assertEquals("as must rescued globals", innerFrame.rescuedGlobals, outerFrame.rescuedGlobals)
    }
}
