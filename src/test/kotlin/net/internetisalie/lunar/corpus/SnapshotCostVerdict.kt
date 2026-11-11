package net.internetisalie.lunar.corpus

import java.io.File

/** One line of `build/snapshot-cost/sweep.jsonl` (MAINT-60 design §4.1), fields in its order. */
data class SweepRow(
    val member: String,
    val file: String,
    val lines: Int,
    val prebuilt: Boolean,
    val coldMs: Long,
    val coldStatus: String,
    val rootSelfMs: Long,
    val topDep: String,
    val topDepSelfMs: Long,
    val builds: Int,
    val editMs: Long,
    val editStatus: String,
    val editRootSelfMs: Long,
    val editTopDep: String,
    val editTopDepSelfMs: Long,
    val editBuilds: Int,
    val digest: Int,
)

/** One row of `exceptions.tsv` (design §4.2); its `ms` column is documentation and is not kept. */
data class CostException(
    val member: String,
    val file: String,
    val bound: String,
    val item: String,
)

/** Why a pass missed its bound, and the roadmap item it is filed under (design §3.3). */
data class Attribution(
    val cause: String,
    val item: String,
    val dependency: String,
    val dependencyMs: Long,
)

/** One pass of a row against its bound: the cold pass for `cold-250`, the edit pass for `edit-250`. */
private data class SweepPass(
    val row: SweepRow,
    val bound: String,
    val ms: Long,
    val status: String,
    val rootSelfMs: Long,
    val topDep: String,
    val topDepSelfMs: Long,
)

/**
 * MAINT-60's verdict over a snapshot cost sweep (design §3.2–§3.4, §4): which passes miss their bound,
 * which listings are stale or misfiled, whether every selected file has a row, and the release lines.
 * Pure: no fixture, PSI or clock, so `SnapshotCostVerdictTest` drives it from rows alone.
 */
object SnapshotCostVerdict {
    const val BOUND_MS = 250L
    const val STALE_BELOW_MS = 200L
    const val P99_MS = 100L
    const val RUN_LIMIT_MS = 45 * 60_000L
    const val COLD_DEADLINE_MS = 60_000L
    const val EDIT_DEADLINE_MS = 1_000L
    const val RUN_BUDGET_MS = 35 * 60_000L

    const val COLD_BOUND = "cold-250"
    const val EDIT_BOUND = "edit-250"
    const val OK = "ok"
    const val TIMEOUT = "timeout"
    const val SKIPPED = "skipped"
    const val NOT_RUN = "not-run"
    const val UNMEASURED = "unmeasured"
    const val EXCEPTION_PREFIX = "exception:"

    const val OWN_BUILD_ITEM = "MAINT-61"
    const val REACH_ITEM = "MAINT-62"
    const val EDIT_CASCADE_ITEM = "MAINT-63"
    val OWN_BUILD_ITEMS = mapOf("/lua/fallo/init.lua" to "BUG-96", "/script/parser/compile.lua" to "BUG-97")

    const val RANKED_HEADER = "member\tfile\tbound\tvalue\tcause\titem\tdependency\tdependencyMs\texcepted"

    private val BOUNDS = listOf(COLD_BOUND, EDIT_BOUND)
    private val NOT_READ = setOf(SKIPPED, NOT_RUN, UNMEASURED)
    private val ROADMAP_TABLE_HEADER = Regex("""^\|\s*ID\s*\|\s*Title\s*\|\s*Status\s*\|""")
    private val ROADMAP_ROW = Regex("""^\|\s*([A-Z]+-\d+)\s*\|""")

    /** Design §3.1 step 1: every `.lua` file under the member's roots, as `<root>/<relative path>`, sorted. */
    fun selectedFiles(
        repoRoot: File,
        entry: CorpusEntry,
    ): List<String> =
        entry.roots
            .flatMap { root ->
                val rootDir = File(repoRoot, "${CorpusManifest.CORPUS_DIR}/${entry.name}/$root")
                rootDir
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "lua" }
                    .map { "$root/${it.relativeTo(rootDir).invariantSeparatorsPath}" }
                    .toList()
            }.sorted()

    fun violations(
        rows: List<SweepRow>,
        exceptions: List<CostException>,
        roadmapIds: Set<String>,
    ): List<String> {
        val passes = rows.flatMap { it.passes() }
        val listed = exceptions.map { it.key() }.toSet()
        val sweepErrors =
            passes.filter { it.isSweepError() }.map { "${it.label()} ${it.status}: sweep error" }
        val unlisted =
            passes
                .filter { !it.isSweepError() && it.misses() && it.key() !in listed }
                .map { "${it.label()} ${it.value()}: no exception" }
        val unselected =
            exceptions
                .filter { listing -> rows.none { it.member == listing.member && it.file == listing.file } }
                .map { "${it.member}/${it.file}: exception for an unselected file" }
        val unknownItems = exceptions.filter { it.item !in roadmapIds }.map { "${it.item}: no roadmap row" }
        return sweepErrors + unlisted + unselected + unknownItems + exceptions.mapNotNull { misfiled(it, rows) }
    }

    fun staleExceptions(
        rows: List<SweepRow>,
        exceptions: List<CostException>,
    ): List<String> {
        val listed = exceptions.map { it.key() }.toSet()
        return rows
            .flatMap { it.passes() }
            .filter { it.key() in listed && it.clears() }
            .map { "${it.label()} ${it.value()}: stale exception: remove" }
    }

    fun coverage(
        selected: Map<String, List<String>>,
        rows: List<SweepRow>,
    ): List<String> {
        val rowKeys = rows.map { it.member to it.file }
        val selectedKeys = selected.flatMap { (member, files) -> files.map { member to it } }.toSet()
        val empty = selected.filterValues { it.isEmpty() }.keys.map { "$it: no selected files" }
        val noRow = selectedKeys.filter { it !in rowKeys }.map { "${it.first}/${it.second}: no row" }
        val unselected =
            rowKeys.filter { it !in selectedKeys }.map { "${it.first}/${it.second}: row for an unselected file" }
        val duplicates =
            rowKeys
                .filterIndexed { index, key -> key in rowKeys.subList(0, index) }
                .map { "${it.first}/${it.second}: duplicate row" }
        return empty + noRow + unselected + duplicates
    }

    fun runTimeFailures(elapsedMs: Long): List<String> =
        if (elapsedMs >= RUN_LIMIT_MS) listOf("sweep took ${elapsedMs / 60_000} min") else emptyList()

    fun rankedLines(
        rows: List<SweepRow>,
        exceptions: List<CostException>,
    ): List<String> {
        val listed = exceptions.map { it.key() }.toSet()
        val ranked =
            rows
                .flatMap { it.passes() }
                .filter { it.misses() || it.ms >= STALE_BELOW_MS }
                .sortedWith(
                    compareByDescending<SweepPass> { it.rankMs() }
                        .thenBy { it.row.member }
                        .thenBy { it.row.file }
                        .thenBy { it.bound },
                )
        return listOf(RANKED_HEADER) + ranked.map { rankedLine(it, rows, it.key() in listed) }
    }

    fun releaseLines(
        rows: List<SweepRow>,
        exceptions: List<CostException>,
        checkFailures: Int,
    ): List<String> {
        val missCount = rows.flatMap { it.passes() }.count { it.misses() }
        val gated =
            listOf(
                p99Line(rows, COLD_BOUND),
                p99Line(rows, EDIT_BOUND),
                "misses $missCount: ${verdictOf(missCount == 0)}",
                "checks $checkFailures: ${verdictOf(checkFailures == 0)}",
            )
        val removable =
            exceptions
                .filter { listing -> rowOf(listing, rows)?.pass(listing.bound)?.misses() == false }
                .map { "remove before tagging: ${it.member}/${it.file} ${it.bound}" }
        val release = if (gated.all { it.endsWith(PASS) }) "release READY" else "release HOLD"
        return gated + removable + release
    }

    /** Design §3.3: only for a pass that misses its bound. */
    fun attribute(
        row: SweepRow,
        bound: String,
        rows: List<SweepRow>,
    ): Attribution {
        val pass = row.pass(bound)
        return when {
            pass.status in NOT_READ -> Attribution(UNMEASURED, blockingItem(pass, rows), "", -1)
            pass.isSweepError() -> Attribution("sweep error", "", "", -1)
            pass.rootSelfMs >= BOUND_MS -> Attribution("own build", ownItem("/${row.member}/${row.file}"), "", -1)
            else -> reach(pass)
        }
    }

    fun parseExceptions(text: String): List<CostException> {
        val parsed = mutableListOf<CostException>()
        text.lines().forEachIndexed { index, line ->
            if (index == 0 || line.isBlank()) return@forEachIndexed
            val listing = parseException(line, index + 1)
            require(parsed.none { it.key() == listing.key() }) { "exceptions.tsv line ${index + 1}: duplicate key" }
            parsed += listing
        }
        return parsed
    }

    fun parseRoadmapIds(text: String): Set<String> {
        val ids = mutableSetOf<String>()
        var inWaveTable = false
        for (line in text.lines()) {
            if (ROADMAP_TABLE_HEADER.containsMatchIn(line)) {
                inWaveTable = true
                continue
            }
            if (!line.startsWith("|")) inWaveTable = false
            if (inWaveTable) ROADMAP_ROW.find(line)?.let { ids += it.groupValues[1] }
        }
        return ids
    }

    private const val PASS = "PASS"

    private fun verdictOf(passed: Boolean): String = if (passed) PASS else "FAIL"

    private fun parseException(
        line: String,
        lineNumber: Int,
    ): CostException {
        val fields = line.split('\t')
        require(fields.size == 5) { "exceptions.tsv line $lineNumber: ${fields.size} fields, expected 5" }
        require(fields[2] in BOUNDS) { "exceptions.tsv line $lineNumber: bound '${fields[2]}' is not one of $BOUNDS" }
        return CostException(fields[0], fields[1], fields[2], fields[3])
    }

    private fun p99Line(
        rows: List<SweepRow>,
        bound: String,
    ): String {
        val sorted = rows.map { it.pass(bound).rankMs() }.sorted()
        val value = if (sorted.isEmpty()) Long.MAX_VALUE else sorted[(99 * sorted.size + 99) / 100 - 1]
        val shown = if (value == Long.MAX_VALUE) UNMEASURED else "$value ms"
        return "p99 ${bound.substringBefore('-')} $shown: ${verdictOf(value < P99_MS)}"
    }

    private fun rankedLine(
        pass: SweepPass,
        rows: List<SweepRow>,
        excepted: Boolean,
    ): String {
        val attribution =
            if (pass.misses()) attribute(pass.row, pass.bound, rows) else Attribution("in band", "", "", -1)
        return listOf(
            pass.row.member,
            pass.row.file,
            pass.bound,
            pass.value(),
            attribution.cause,
            attribution.item,
            attribution.dependency,
            attribution.dependencyMs.toString(),
            if (excepted) "yes" else "no",
        ).joinToString("\t")
    }

    private fun misfiled(
        listing: CostException,
        rows: List<SweepRow>,
    ): String? {
        val row = rowOf(listing, rows) ?: return null
        if (!row.pass(listing.bound).misses()) return null
        val computed = attribute(row, listing.bound, rows).item
        if (computed.isEmpty() || computed == listing.item) return null
        return "${listing.member}/${listing.file} ${listing.bound}: " +
            "misfiled under ${listing.item}, attributed to $computed"
    }

    private fun blockingItem(
        pass: SweepPass,
        rows: List<SweepRow>,
    ): String {
        val blocker =
            when (pass.status) {
                NOT_RUN -> pass.row
                UNMEASURED -> rows.firstOrNull { it.member == pass.row.member && it.coldStatus == TIMEOUT }
                else -> rows.filter { it.coldStatus == OK || it.coldStatus == TIMEOUT }.maxByOrNull { it.coldMs }
            }
        return blocker?.let { attribute(it, COLD_BOUND, rows).item } ?: ""
    }

    private fun reach(pass: SweepPass): Attribution {
        val cascade = pass.bound == EDIT_BOUND
        val item =
            when {
                pass.topDepSelfMs >= BOUND_MS -> ownItem(pass.topDep)
                cascade -> EDIT_CASCADE_ITEM
                else -> REACH_ITEM
            }
        return Attribution(if (cascade) "edit cascade" else "reach", item, pass.topDep, pass.topDepSelfMs)
    }

    private fun ownItem(path: String): String =
        OWN_BUILD_ITEMS.entries.firstOrNull { path.endsWith(it.key) }?.value ?: OWN_BUILD_ITEM

    private fun rowOf(
        listing: CostException,
        rows: List<SweepRow>,
    ): SweepRow? = rows.firstOrNull { it.member == listing.member && it.file == listing.file }

    private fun SweepRow.pass(bound: String): SweepPass =
        if (bound == COLD_BOUND) {
            SweepPass(this, bound, coldMs, coldStatus, rootSelfMs, topDep, topDepSelfMs)
        } else {
            SweepPass(this, bound, editMs, editStatus, editRootSelfMs, editTopDep, editTopDepSelfMs)
        }

    private fun SweepRow.passes(): List<SweepPass> = BOUNDS.map { pass(it) }

    private fun CostException.key(): Triple<String, String, String> = Triple(member, file, bound)

    private fun SweepPass.key(): Triple<String, String, String> = Triple(row.member, row.file, bound)

    private fun SweepPass.label(): String = "${row.member}/${row.file} $bound"

    private fun SweepPass.isSweepError(): Boolean = status.startsWith(EXCEPTION_PREFIX)

    private fun SweepPass.misses(): Boolean = status != OK || ms >= BOUND_MS

    private fun SweepPass.clears(): Boolean = status == OK && ms < STALE_BELOW_MS

    private fun SweepPass.value(): String = if (status == OK) "$ms ms" else status

    private fun SweepPass.rankMs(): Long = if (status == OK) ms else Long.MAX_VALUE
}
