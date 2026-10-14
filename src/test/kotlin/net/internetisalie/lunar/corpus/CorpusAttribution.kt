package net.internetisalie.lunar.corpus

import java.io.File

/**
 * Which arm of an attribution pair a member-alone sweep is (MAINT-37 design §2.6, §3.8): `bare`
 * sweeps without the member's pins, `pinned` with them. Selected by `-PcorpusAttribution`.
 */
enum class CorpusAttributionMode(
    val dirName: String,
) {
    BARE("bare"),
    PINNED("pinned"),
    ;

    companion object {
        const val PROPERTY = "lunar.corpus.attribution"

        fun fromProperty(value: String?): CorpusAttributionMode? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.dirName == value.trim() }
                ?: error("lunar.corpus.attribution must be 'bare' or 'pinned', got '$value'")
        }

        fun forRun(
            attribution: String?,
            record: String?,
        ): CorpusAttributionMode? =
            fromProperty(attribution).also { mode ->
                check(mode == null || record != "true") {
                    "-PcorpusAttribution and -PrecordCorpusBaseline are mutually exclusive: " +
                        "an attribution run never writes a baseline"
                }
            }
    }
}

/**
 * Writes an attribution arm's tally — the baseline lines, the refresh observation and the uncapped
 * symbol hits — beside the build, never over a baseline (MAINT-37 design §2.6, §4.4).
 */
object CorpusAttribution {
    const val ALL_SYMBOL_PREFIX = "allsymbol."

    fun tallyFile(
        repoRoot: File,
        mode: CorpusAttributionMode,
        name: String,
    ): File = File(repoRoot, "build/corpus-attribution/${mode.dirName}/$name.tally")

    fun checkArm(
        mode: CorpusAttributionMode,
        metrics: CorpusMetrics,
    ) {
        if (mode == CorpusAttributionMode.PINNED) return
        check(metrics.libraries.isEmpty()) {
            "the bare arm registered libraries ${metrics.libraries}: the enable list was not emptied"
        }
        check(metrics.allSymbolHits.isNotEmpty()) {
            "the bare arm recorded no symbol hits: allSymbolHits is not filled from the sweep's sink"
        }
    }

    fun renderTally(
        metrics: CorpusMetrics,
        refresh: RefreshObservation,
    ): String =
        buildString {
            append(CorpusBaseline.render(metrics))
            appendLine("settingsBroadcasts=${refresh.settingsBroadcasts}")
            appendLine("rootsTicks=${refresh.rootsTicks}")
            appendLine("physicalPsiChanges=${refresh.physicalPsiChanges}")
            metrics.allSymbolHits.toSortedMap().forEach { (key, count) -> appendLine("$ALL_SYMBOL_PREFIX$key=$count") }
        }

    fun write(
        tally: File,
        metrics: CorpusMetrics,
        refresh: RefreshObservation,
    ) {
        val text = renderTally(metrics, refresh)
        tally.parentFile.mkdirs()
        tally.writeText(text)
        println("[corpus-attribution] wrote ${tally.path}:\n$text")
    }
}
