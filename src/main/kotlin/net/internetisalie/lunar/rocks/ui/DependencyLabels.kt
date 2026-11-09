package net.internetisalie.lunar.rocks.ui

import net.internetisalie.lunar.rocks.deps.DependencyKind
import net.internetisalie.lunar.rocks.deps.DependencyNode

/** How the dependency tree and inspector name a node's version and scope (BUG-54). */
object DependencyLabels {
    fun versionText(node: DependencyNode): String =
        node.resolvedVersion?.raw ?: when (node.kind) {
            DependencyKind.RUNTIME -> "no runtime configured"
            DependencyKind.PROJECT -> "unversioned"
            DependencyKind.ROCK -> "not installed"
        }

    fun scopeText(node: DependencyNode): String =
        when {
            node.kind == DependencyKind.PROJECT -> "project rock"
            node.kind == DependencyKind.RUNTIME -> "runtime"
            node.isTransitive -> "transitive"
            else -> "direct"
        }

    /** Tree text: a project rock shows its rockspec path, so two roots of one name are told apart. */
    fun treeText(node: DependencyNode): String =
        buildString {
            append(node.packageName)
            if (node.resolvedVersion !=
                null
            ) {
                append(" ").append(versionText(node))
            } else {
                append(" (${versionText(node)})")
            }
            node.location?.let { append(" — ").append(it) }
            if (node.isCycle) append(" (cycle)")
        }
}
