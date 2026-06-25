package net.internetisalie.lunar.rocks.build

import java.nio.file.Path

/** One discovered, bridge-read rock relevant to build ordering. */
data class WorkspaceRock(
    val packageName: String,
    val rockspec: Path,
    val dependencyNames: List<String>,
)

/** Result of ordering. */
sealed interface BuildPlan {
    data class Ordered(val rocks: List<WorkspaceRock>) : BuildPlan
    data class Cycle(val packages: Set<String>) : BuildPlan
    data object Empty : BuildPlan
}
