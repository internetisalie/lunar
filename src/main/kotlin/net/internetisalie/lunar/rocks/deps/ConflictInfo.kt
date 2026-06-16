package net.internetisalie.lunar.rocks.deps

/** The kind of dependency-graph problem flagged on a [DependencyNode]. */
enum class ConflictType { VERSION_MISMATCH, MISSING_DEPENDENCY }

/**
 * A single conflict annotation on a dependency node: a human-readable [description] plus the
 * constraints that produced it (for inspector display).
 */
data class ConflictInfo(
    val type: ConflictType,
    val description: String,
    val offendingConstraints: List<VersionConstraint>,
)
