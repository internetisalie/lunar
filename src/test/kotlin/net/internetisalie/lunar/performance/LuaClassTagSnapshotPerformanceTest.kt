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
 * They assert **correctness only**. A wall-clock assertion here would be a budget test, which is
 * exactly the instrument that failed to catch BUG-473 in the first place, and it would go red on the
 * fix as readily as on a regression. The timing is printed for the human reading the profile
 * alongside it; the pass/fail contract is that the annotated receiver still resolves.
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

    private fun callSiteFixture(annotated: Boolean): String {
        val classTag = if (annotated) "---@class Builder\n" else ""
        val callSites = (0 until CALL_SITE_COUNT).joinToString("\n") { "b:setName(\"a$it\")" }
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
    }
}
