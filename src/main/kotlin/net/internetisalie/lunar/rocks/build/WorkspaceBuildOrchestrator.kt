package net.internetisalie.lunar.rocks.build

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.DiscoveredRockspec
import net.internetisalie.lunar.rocks.LuaRockspecDiscoveryService
import net.internetisalie.lunar.rocks.RockspecBridge
import net.internetisalie.lunar.rocks.RockspecData
import java.nio.file.Path

/**
 * Orchestrates turning the discovered rockspec set into a BuildPlan.
 * Background-only (no EDT calls).
 */
object WorkspaceBuildOrchestrator {
    private val log = logger<WorkspaceBuildOrchestrator>()

    @org.jetbrains.annotations.TestOnly
    var testDiscoverySeam: ((Project) -> List<DiscoveredRockspec>)? = null

    @org.jetbrains.annotations.TestOnly
    var testBridgeReaderSeam: ((Project, Path) -> RockspecData?)? = null

    /**
     * Computes the build order by reading discovered rockspecs via RockspecBridge
     * and sorting them topologically.
     */
    fun computeBuildOrder(project: Project): BuildPlan {
        val rocks = loadRocks(project)
        return WorkspaceBuildGraph.topoSort(rocks)
    }

    private fun loadRocks(project: Project): List<WorkspaceRock> {
        val discovered = testDiscoverySeam?.invoke(project)
            ?: LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()
        val rocks = mutableListOf<WorkspaceRock>()

        for (d in discovered) {
            ProgressManager.checkCanceled()
            val data = testBridgeReaderSeam?.invoke(project, d.rockspec)
                ?: RockspecBridge.read(project, d.rockspec)

            if (data == null) {
                log.warn("Dropped unparseable rockspec: ${d.rockspec}")
                continue
            }
            val name = data.packageName
            val deps = data.dependencies.mapNotNull { normalizeDepName(it) }
            rocks.add(WorkspaceRock(name, d.rockspec, deps))
        }

        return rocks
    }

    fun normalizeDepName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val match = Regex("^[^\\s<>=~,]+").find(trimmed)?.value
        return match?.lowercase()
    }
}
