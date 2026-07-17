package net.internetisalie.lunar.rocks

import net.internetisalie.lunar.rocks.deps.ConflictInfo
import net.internetisalie.lunar.rocks.deps.ConflictType
import net.internetisalie.lunar.rocks.deps.ConstraintOp
import net.internetisalie.lunar.rocks.deps.DependencyNode
import net.internetisalie.lunar.rocks.deps.VersionConstraint

/**
 * Annotates a resolved dependency tree with [ConflictInfo] markers.
 *
 * Detects three problems: a missing install (no resolved version), an installed version that
 * violates a required constraint, and a constraint set that is internally unsatisfiable even when
 * nothing is installed (e.g. `>= 2.0` together with `< 1.5`).
 */
object VersionConflictEngine {
    fun annotate(root: DependencyNode) {
        collectNodes(root, mutableSetOf()).forEach { annotateNode(it) }
    }

    private fun annotateNode(node: DependencyNode) {
        if (node.isCycle) return
        val constraints = node.requiredConstraints
        val resolved = node.resolvedVersion
        if (resolved == null) {
            node.conflicts += ConflictInfo(
                ConflictType.MISSING_DEPENDENCY,
                "'${node.packageName}' is required but not installed",
                constraints,
            )
            flagUnsatisfiable(node)
            return
        }
        val violated = constraints.filterNot { it.isSatisfiedBy(resolved) }
        if (violated.isNotEmpty()) {
            val detail = violated.joinToString(", ") { "${it.op.token} ${it.version.raw}" }
            node.conflicts += ConflictInfo(
                ConflictType.VERSION_MISMATCH,
                "installed ${resolved.raw} violates required $detail",
                violated,
            )
        }
    }

    /**
     * Flags a constraint set that is internally unsatisfiable.
     *
     * A lower/upper pair is unsatisfiable when:
     * - the lower bound version is strictly greater than the upper bound version, OR
     * - the bounds are equal but at least one side is exclusive
     *   (e.g. `>= 2.0` + `< 2.0` cannot be satisfied by any version).
     */
    private fun flagUnsatisfiable(node: DependencyNode) {
        val lowers = node.requiredConstraints.filter { it.op == ConstraintOp.GE || it.op == ConstraintOp.GT }
        val uppers = node.requiredConstraints.filter { it.op == ConstraintOp.LE || it.op == ConstraintOp.LT }
        for (lower in lowers) {
            for (upper in uppers) {
                val versionsEqual = lower.version.compareTo(upper.version) == 0
                val unsatisfiable = lower.version > upper.version ||
                    (versionsEqual && (lower.op == ConstraintOp.GT || upper.op == ConstraintOp.LT))
                if (unsatisfiable) {
                    node.conflicts += ConflictInfo(
                        ConflictType.VERSION_MISMATCH,
                        "no version can satisfy ${lower.op.token} ${lower.version.raw} and ${upper.op.token} ${upper.version.raw}",
                        listOf(lower, upper),
                    )
                    return
                }
            }
        }
    }

    private fun collectNodes(node: DependencyNode, seen: MutableSet<DependencyNode>): List<DependencyNode> {
        if (!seen.add(node)) return emptyList()
        val result = mutableListOf(node)
        node.children.forEach { result += collectNodes(it, seen) }
        return result
    }
}
