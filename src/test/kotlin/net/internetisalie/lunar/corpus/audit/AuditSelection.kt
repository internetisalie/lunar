package net.internetisalie.lunar.corpus.audit

/** MAINT-54 design §3.3: at most [CAP] sites per cell, spaced evenly through the cell's sorted sites. */
object AuditSelection {
    const val CAP = 4

    private val SITE_ORDER = compareBy<AuditSite>({ it.file }, { it.edit.startOffset })

    /** `k = min(n, cap)` indices `i * n / k` (integer division). */
    fun pick(
        n: Int,
        cap: Int,
    ): List<Int> {
        val k = minOf(n, cap)
        return (0 until k).map { it * n / k }
    }

    fun select(sites: List<AuditSite>): List<AuditSite> =
        grouped(sites).flatMap { (_, cellSites) -> pick(cellSites.size, CAP).map { cellSites[it] } }

    /** Each cell's population: every site enumerated for it, selected or not. */
    fun populations(sites: List<AuditSite>): List<AuditPopulation> =
        grouped(sites).map { (cell, cellSites) -> AuditPopulation(cell, cellSites.size) }

    private fun grouped(sites: List<AuditSite>): List<Pair<AuditCell, List<AuditSite>>> =
        sites
            .groupBy { it.cell }
            .map { (cell, cellSites) -> cell to cellSites.sortedWith(SITE_ORDER) }
            .sortedWith(compareBy({ it.first.shape }, { it.first.path }, { it.first.form }))
}
