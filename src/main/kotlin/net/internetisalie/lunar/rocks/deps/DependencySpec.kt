package net.internetisalie.lunar.rocks.deps

/**
 * A parsed dependency entry from a rockspec: a package name plus an `AND` list of constraints.
 *
 * Example inputs: `"luasocket >= 2.0, < 4.0"`, `"penlight"`, `"copas ~> 2"`. The package name may be
 * `scope/name`. A spec with no constraints is satisfied by any version.
 */
data class DependencySpec(
    val packageName: String,
    val constraints: List<VersionConstraint>,
    val raw: String,
) {
    fun isSatisfiedBy(version: LuaRocksVersion): Boolean = constraints.all { it.isSatisfiedBy(version) }

    companion object {
        private val NAME = Regex("^\\s*([A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)?)\\s*(.*)$")

        fun parse(raw: String): DependencySpec? {
            val match = NAME.matchEntire(raw.trim()) ?: return null
            val packageName = match.groupValues[1]
            val remainder = match.groupValues[2]
            val constraints = remainder
                .split(',')
                .mapNotNull { VersionConstraint.parse(it) }
            return DependencySpec(packageName, constraints, raw)
        }
    }
}
