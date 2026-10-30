package net.internetisalie.lunar.perfrecords

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import java.util.concurrent.ConcurrentHashMap

internal class CountingPsiBuilder(
    delegate: PsiBuilder,
) : PsiBuilderAdapter(delegate) {
    var marks = 0L
        private set
    var advances = 0L
        private set

    override fun mark(): PsiBuilder.Marker {
        marks++
        return super.mark()
    }

    override fun advanceLexer() {
        advances++
        super.advanceLexer()
    }
}

/** Per-index stub lookups since the last [drain], read from the platform's own aggregates (DR-03). */
internal object StubLookupCounter {
    private const val COLLECTOR =
        "com.intellij.util.indexing.diagnostic.IndexLookupTimingsReporting\$IndexOperationAggregatesCollector"
    private const val FIELD = "stubEntriesLookupDurationsMsByIndexId"

    private val aggregates: ConcurrentHashMap<*, *> by lazy {
        val field = Class.forName(COLLECTOR).getDeclaredField(FIELD)
        field.isAccessible = true
        field.get(null) as ConcurrentHashMap<*, *>
    }

    fun drain(): Map<String, Long> =
        aggregates.entries
            .associate { (key, histogram) -> "stub:$key" to intervalTotal(histogram) }
            .toSortedMap()

    private fun intervalTotal(histogram: Any?): Long {
        val owner = requireNotNull(histogram) { "a null histogram in $FIELD" }
        val interval = owner.javaClass.getMethod("getIntervalHistogram").invoke(owner)
        return interval.javaClass.getMethod("getTotalCount").invoke(interval) as Long
    }
}
