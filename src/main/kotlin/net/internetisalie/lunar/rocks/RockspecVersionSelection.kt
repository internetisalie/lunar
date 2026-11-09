package net.internetisalie.lunar.rocks

import net.internetisalie.lunar.rocks.deps.LuaRocksVersion

/**
 * Keeps one rockspec per package (BUG-53). A project often holds several rockspecs of one rock — a
 * development rockspec beside a `rockspecs/` archive of releases — and they are versions of one
 * rock, not separate rocks. The highest version wins; LuaRocks orders `dev` and `scm` above every
 * release, so a development rockspec is chosen whenever one exists.
 */
object RockspecVersionSelection {
    private val FILE_NAME = Regex("^(.+)-([^-]+-\\d+)\\.rockspec$")

    /** One entry per package, in the input's order; a rockspec whose package cannot be named stays. */
    fun onePerPackage(discovered: List<DiscoveredRockspec>): List<DiscoveredRockspec> {
        val chosen =
            discovered
                .groupBy { packageOf(it) ?: it.rockspec.toString() }
                .values
                .map { group -> group.maxBy { versionOf(it) } }
                .toSet()
        return discovered.filter { it in chosen }
    }

    private fun packageOf(rockspec: DiscoveredRockspec): String? =
        rockspec.packageName ?: FILE_NAME.matchEntire(fileName(rockspec))?.groupValues?.get(1)

    private fun versionOf(rockspec: DiscoveredRockspec): LuaRocksVersion =
        LuaRocksVersion.parse(FILE_NAME.matchEntire(fileName(rockspec))?.groupValues?.get(2) ?: "0")

    private fun fileName(rockspec: DiscoveredRockspec): String = rockspec.rockspec.fileName.toString()
}
