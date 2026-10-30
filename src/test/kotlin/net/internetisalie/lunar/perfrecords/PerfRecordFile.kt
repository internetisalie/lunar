package net.internetisalie.lunar.perfrecords

import com.google.gson.GsonBuilder
import java.io.File

internal data class PerfPassValues(
    val counts: Map<String, Long>,
    val outputs: Map<String, Long>,
)

internal data class PerfRecord(
    val schema: Int,
    val workload: String,
    val kind: String,
    val durationMs: Long,
    val fixture: String,
    val counts: Map<String, Long>,
    val outputs: Map<String, Long>,
    val warm: PerfPassValues,
)

/** MAINT-45-01. One file per workload under `build/perf-records/`, read by `tooling/ctrf/perf_ctrf.py`. */
internal object PerfRecordFile {
    fun write(
        repoRoot: File,
        record: PerfRecord,
    ) {
        val target = File(repoRoot, "build/perf-records/${record.workload}.json")
        target.parentFile.mkdirs()
        target.writeText(GsonBuilder().disableHtmlEscaping().create().toJson(record), Charsets.UTF_8)
    }
}
