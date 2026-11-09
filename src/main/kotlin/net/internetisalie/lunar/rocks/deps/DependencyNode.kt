package net.internetisalie.lunar.rocks.deps

/** What a dependency node stands for (BUG-54). */
enum class DependencyKind {
    /** A rock developed in the project: its version is its rockspec's, and it is never missing. */
    PROJECT,

    /** A rock looked up among the installed rocks. */
    ROCK,

    /** The `lua` entry: its version is the configured runtime's, or `null` when none is configured. */
    RUNTIME,
}

/**
 * A node in the resolved dependency graph. Mutable while the resolver builds it, then read by the
 * conflict engine and tree UI. A [DependencyKind.ROCK] node with [resolvedVersion] `null` is a
 * missing dependency.
 */
class DependencyNode(
    val packageName: String,
    val isTransitive: Boolean,
    var resolvedVersion: LuaRocksVersion? = null,
    val kind: DependencyKind = DependencyKind.ROCK,
    /** For a [DependencyKind.PROJECT] node, its rockspec's path relative to the project. */
    val location: String? = null,
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
