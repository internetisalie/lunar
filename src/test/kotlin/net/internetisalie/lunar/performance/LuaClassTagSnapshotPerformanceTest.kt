package net.internetisalie.lunar.performance

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * MAINT-38-05 — BUG-473's reproduction, kept as a profiling target rather than as a budget.
 *
 * The defect is that one `---@class` makes [LuaTypesSnapshot.forFile] superlinear in colon-call-site
 * count (measured ×2.2/×4.4/×9.3 per doubling; 12 807 ms at n=80, flat without the tag). Attributing
 * that cost needs a recording, not another wall-clock number, so these two cases exist to be run
 * under `-PjfrProfile`:
 *
 * ```
 * ./gradlew test -PwithPerf -PjfrProfile --tests '*LuaClassTagSnapshotPerformanceTest*' --rerun
 * ```
 *
 * The two n = 80 cases assert **correctness only** — a wall-clock budget is exactly the instrument
 * that failed to catch BUG-473, and it would go red on the fix as readily as on a regression. Their
 * timing is printed for the human reading the profile alongside them; the pass/fail contract is that
 * the annotated receiver still resolves.
 *
 * `testAnnotatedSnapshotStaysWithinRatioOfTheUnannotatedControl` is the one timing gate, and it
 * asserts a ratio against an in-JVM control rather than a millisecond figure. The deterministic
 * half of BUG-473's coverage — root-resolution counts, which need no host calibration at all —
 * lives in `LuaTypeGraphRootResolutionBudgetTest` and runs in the routine loop.
 *
 * Named `*Performance*` so the routine loop's filter excludes it — a 13-second case has no place in
 * the default gate. See `docs/profiling.md`.
 */
class LuaClassTagSnapshotPerformanceTest : BaseDocumentTest() {
    @Test
    fun testAnnotatedReceiverSnapshotAtEightyCallSites() {
        assertReceiverResolves(annotated = true)
    }

    @Test
    fun testUnannotatedReceiverSnapshotAtEightyCallSites() {
        assertReceiverResolves(annotated = false)
    }

    private fun assertReceiverResolves(annotated: Boolean) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText(callSiteFixture(annotated))
            val caretLeaf =
                checkNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) { "no element at the caret" }

            val startedAt = System.nanoTime()
            val types = LuaTypesSnapshot.forFile(myFixture.file)
            val receiverType = types.getValueType(caretLeaf.parent)
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            println("[MAINT-38] forFile annotated=$annotated n=$CALL_SITE_COUNT elapsed=${elapsedMillis}ms")
            val resolved =
                if (annotated) {
                    receiverType.displayName().contains("Builder")
                } else {
                    receiverType != LuaGraphType.Undefined
                }
            assertTrue(resolved, "receiver resolved to ${receiverType.displayName()} (annotated=$annotated)")
        }
    }

    /**
     * BUG-473 — the timing assertion, and the only one here, expressed as a RATIO rather than a
     * budget. The control is the same fixture with the `---@class` line removed, measured in the
     * same JVM immediately afterwards, so machine speed, CI contention and JIT state divide out;
     * what remains is a property of the engine. An absolute millisecond budget is the instrument
     * that failed to catch this defect and would go red on the fix as readily as on a regression.
     *
     * Measured at n = 160: 676× before Phase 1, 100× after, 20× after the gated Phase 2. The
     * threshold sits 2× above the Phase 1 result and 3.4× below the defect, so a regression
     * restoring a third of it trips and ordinary noise does not. Tighten to 40 when Phase 2 lands;
     * not before.
     */
    @Test
    fun testAnnotatedSnapshotStaysWithinRatioOfTheUnannotatedControl() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            snapshotMillis(annotated = true, callSiteCount = WARMUP_CALL_SITE_COUNT)
            snapshotMillis(annotated = false, callSiteCount = WARMUP_CALL_SITE_COUNT)

            val annotatedMillis = snapshotMillis(annotated = true, callSiteCount = RATIO_CALL_SITE_COUNT)
            val controlMillis = snapshotMillis(annotated = false, callSiteCount = RATIO_CALL_SITE_COUNT)
            val ratio = annotatedMillis.toDouble() / maxOf(controlMillis, 1L)

            println(
                "[BUG-473] n=$RATIO_CALL_SITE_COUNT annotated=${annotatedMillis}ms " +
                    "control=${controlMillis}ms ratio=$ratio",
            )
            assertTrue(
                ratio <= MAX_ANNOTATED_CONTROL_RATIO,
                "an annotated file cost ${ratio}x its unannotated control at $RATIO_CALL_SITE_COUNT " +
                    "call sites (${annotatedMillis}ms vs ${controlMillis}ms), over the $MAX_ANNOTATED_CONTROL_RATIO x limit",
            )
        }
    }

    private fun snapshotMillis(
        annotated: Boolean,
        callSiteCount: Int,
    ): Long {
        configureByText(callSiteFixture(annotated, callSiteCount))
        val startedAt = System.nanoTime()
        LuaTypesSnapshot.forFile(myFixture.file)
        return (System.nanoTime() - startedAt) / 1_000_000
    }

    private fun callSiteFixture(
        annotated: Boolean,
        callSiteCount: Int = CALL_SITE_COUNT,
    ): String {
        val classTag = if (annotated) "---@class Builder\n" else ""
        val callSites = (0 until callSiteCount).joinToString("\n") { "b:setName(\"a$it\")" }
        return """
            |${classTag}local Builder = {}
            |function Builder:setName(n) end
            |
            |local <caret>b = Builder
            |$callSites
            """.trimMargin()
    }

    private companion object {
        const val CALL_SITE_COUNT = 80

        const val WARMUP_CALL_SITE_COUNT = 10
        const val RATIO_CALL_SITE_COUNT = 160
        const val MAX_ANNOTATED_CONTROL_RATIO = 200.0
    }
}
