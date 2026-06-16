package net.internetisalie.lunar.rocks.deps

/**
 * A node in the resolved dependency graph. Mutable while the resolver builds it, then read by the
 * conflict engine and tree UI. A node with [resolvedVersion] `null` is a missing dependency.
 */
class DependencyNode(
    val packageName: String,
    val isTransitive: Boolean,
    var resolvedVersion: LuaRocksVersion? = null,
    var isCycle: Boolean = false,
    val requiredBy: MutableList<DependencyNode> = mutableListOf(),
    val requiredConstraints: MutableList<VersionConstraint> = mutableListOf(),
    val children: MutableList<DependencyNode> = mutableListOf(),
    val conflicts: MutableList<ConflictInfo> = mutableListOf(),
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()

    override fun toString(): String {
        val version = resolvedVersion?.raw ?: "(missing)"
        return "$packageName $version"
    }
}
