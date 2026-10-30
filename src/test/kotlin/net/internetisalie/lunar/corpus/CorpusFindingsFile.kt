package net.internetisalie.lunar.corpus

import com.google.gson.GsonBuilder
import java.io.File
import java.util.UUID

/**
 * MAINT-40-01. Writes one member's findings to `build/corpus-findings/<member>.json` (design §4.1),
 * which `tooling/corpus/corpus_ctrf.py` assembles into the run's CTRF document.
 */
object CorpusFindingsFile {
    /** Once per test JVM: `corpus_ctrf.py` refuses to combine files written by different JVMs. */
    val JVM_TOKEN: String = UUID.randomUUID().toString()

    private const val SCHEMA = 1

    /** The serialised shape. `symbol` is omitted when null (Gson's default). */
    data class Payload(
        val schema: Int,
        val member: String,
        val jvmToken: String,
        val files: Int,
        val requires: Int,
        val libraries: Map<String, String>,
        val findings: List<CorpusFinding>,
    )

    fun file(
        repoRoot: File,
        name: String,
    ): File = File(repoRoot, "build/corpus-findings/$name.json")

    fun write(
        repoRoot: File,
        name: String,
        metrics: CorpusMetrics,
    ) {
        val payload =
            Payload(
                SCHEMA,
                name,
                JVM_TOKEN,
                metrics.files,
                metrics.requires,
                metrics.libraries.toSortedMap(),
                metrics.findings,
            )
        val target = file(repoRoot, name)
        target.parentFile.mkdirs()
        target.writeText(GsonBuilder().disableHtmlEscaping().create().toJson(payload), Charsets.UTF_8)
    }
}
