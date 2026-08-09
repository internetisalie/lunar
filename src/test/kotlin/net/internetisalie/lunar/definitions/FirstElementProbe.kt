package net.internetisalie.lunar.definitions

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId

/** One completion run's timeline, in MICROseconds from the probe's entry. */
data class CompletionTimeline(
    val firstUs: Long,
    val lastUs: Long,
    val count: Int,
) {
    val firstMs get() = firstUs / 1000

    val lastMs get() = lastUs / 1000

    /** The gap COMP-09-04 was withdrawn on: 31 us of 777 us means there is no tail to stream. */
    val gapUs get() = lastUs - firstUs
}

/**
 * Times the **first** lookup element a real `completeBasic()` produces.
 *
 * Promoted out of COMP-09 DR-02a, which built it because `completeBasic()` returns only when
 * completion has *finished* — so before it existed, no time-to-first-result figure existed for any
 * fixture in the COMP-09 plan, and NFR-1 names exactly that quantity. COMP-09-08's gate
 * ([MemberEnumerationLatencyGateTest]) is asserted on it, so it is no longer throwaway.
 *
 * It is the platform's own filtering hook — `runRemainingContributors` — registered
 * [LoadingOrder.FIRST], so the numbers come from a real completion session rather than from calling
 * a contributor by hand with a synthesised parameter object. Nothing is added to production code:
 * the registration is scoped to `testRootDisposable`.
 */
class FirstElementProbe : CompletionContributor() {
    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val startNs = System.nanoTime()
        var firstNs = -1L
        var produced = 0
        result.runRemainingContributors(parameters) { completionResult ->
            produced++
            if (firstNs < 0) firstNs = System.nanoTime() - startNs
            result.passResult(completionResult)
        }
        val lastNs = System.nanoTime() - startNs
        recorded =
            CompletionTimeline(
                firstUs = if (firstNs < 0) -1 else firstNs / 1_000,
                lastUs = lastNs / 1_000,
                count = produced,
            )
    }

    companion object {
        @Volatile
        var recorded: CompletionTimeline? = null
    }
}

/**
 * A [LibraryRootTestCase] that can time the first lookup element, for anything asserting or
 * recording NFR-1's time-to-first-result.
 *
 * **Cold is the whole difficulty** (design §4.10a). Warm time-to-first is ~1 ms, so a measurement
 * that does not force a cold state reports nothing: every receiver whose latency matters must live
 * in its **own** library file, because the per-file `LuaTypesSnapshot` is memoized and a second
 * receiver in the same file rides the first one's build.
 */
abstract class TimedCompletionTestCase : LibraryRootTestCase() {
    protected fun installFirstElementProbe() {
        val extensionPoint = ExtensionPointName<CompletionContributorEP>("com.intellij.completion.contributor")
        val pluginDescriptor =
            DefaultPluginDescriptor(
                PluginId.getId(PROBE_PLUGIN_ID),
                FirstElementProbe::class.java.classLoader,
            )
        val contributorBean =
            CompletionContributorEP("Lua", FirstElementProbe::class.java.name, pluginDescriptor)
        extensionPoint.point.registerExtension(contributorBean, LoadingOrder.FIRST, testRootDisposable)
    }

    /** Runs completion at the caret in [text] and returns what the probe observed. */
    protected fun timeCompletion(text: String): CompletionTimeline? {
        FirstElementProbe.recorded = null
        myFixture.configureByText("consumer.lua", text)
        myFixture.completeBasic()
        return FirstElementProbe.recorded
    }

    /** Time-to-first in microseconds, or -1 when the probe saw no result at all. */
    protected fun timeToFirstUs(text: String): Long = timeCompletion(text)?.firstUs ?: -1

    private companion object {
        const val PROBE_PLUGIN_ID = "net.internetisalie.lunar.comp09.probe"
    }
}
