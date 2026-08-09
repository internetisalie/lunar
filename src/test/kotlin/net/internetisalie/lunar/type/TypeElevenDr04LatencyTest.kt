package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * TYPE-11 DR-04 — re-measure COMP-09 DR-20's 9 ms / 334 ms pair, on a library that is
 * **provenance-matched** (installed through the real definition-library path) rather than one behind
 * an anonymous test provider.
 *
 * Shape borrowed verbatim from `CompNineDr20Test`: distinct consumer text per sample so `forFile`'s
 * per-file-text memoization cannot serve a warm answer — every sample is "the user typed a
 * character, the snapshot rebuilt" — and medians of five.
 *
 * `requirements.md` states the success criterion: landing near the **9 ms no-library baseline**, not
 * the old warm ~1 ms. The warm figure was the consumer's own snapshot served from cache; after a
 * keystroke that snapshot correctly rebuilds and every free global re-runs `resolveGlobal` +
 * `graphTypeToLuaType`, which builds a fresh `visited` map per call.
 *
 * No arm here crosses a reindex boundary: the library is installed and indexed before the first
 * sample, and consumer files are project-scope text additions.
 */
class TypeElevenDr04LatencyTest : TypeElevenDefinitionLibraryTestCase() {
    private fun consumer(index: Int): PsiFile =
        myFixture.configureByText("consumer$index.lua", "local pad$index = $index\nwx.wxC_0 = $index\n")

    private fun timeForFileUs(file: PsiFile): Long {
        val startNs = System.nanoTime()
        LuaTypesSnapshot.forFile(file)
        return (System.nanoTime() - startNs) / 1000
    }

    private fun fiveColdSamples(): List<Long> =
        runReadAction { (0 until 5).map { timeForFileUs(consumer(it)) } }.sorted()

    /** Arm A — no definition library registered (the bundled stdlib root is present in both arms). */
    fun testDr04ForFileWithoutADefinitionLibrary() {
        val samples = fiveColdSamples()
        println("DR-04 arm A forFile(consumer), NO definition library: samples(us)=$samples median=${samples[2]}us")
    }

    /** Arm B — identical consumer text, one 123 KiB provenance-matched definition library. */
    fun testDr04ForFileWithAProvisionedDefinitionLibrary() {
        val text = bigLibrary()
        installDefinitionLibrary("luassert", mapOf("wx.lua" to text))
        println("DR-04 arm B library is ${text.length / 1024} KiB")
        val samples = fiveColdSamples()
        println("DR-04 arm B forFile(consumer), WITH definition library: samples(us)=$samples median=${samples[2]}us")
    }
}
