package net.internetisalie.lunar.rocks.build

import com.intellij.openapi.progress.ProgressManager

/**
 * Builds the sibling DAG and runs Kahn's topological sort with cycle detection.
 * Pure; no platform access.
 */
object WorkspaceBuildGraph {

    /**
     * Edges: A depends-on B (A→B) iff A.dependencyNames contains B.packageName (normalized),
     * A != B, and B is in [rocks]. External names are ignored.
     */
    fun topoSort(rocks: List<WorkspaceRock>): BuildPlan {
        if (rocks.isEmpty()) return BuildPlan.Empty

        val byName = rocks.associateBy { it.packageName.lowercase() }
        val nodes = byName.keys

        val (outDeg, dependents) = buildAdjacency(byName)

        val ready = nodes.filter { outDeg.getValue(it) == 0 }.sorted().toMutableList()
        val result = mutableListOf<String>()

        while (ready.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val n = ready.removeAt(0)
            result.add(n)

            dependents.getValue(n).forEach { a ->
                val deg = outDeg.getValue(a) - 1
                outDeg[a] = deg
                if (deg == 0) {
                    ready.add(a)
                    ready.sort()
                }
            }
        }

        return buildResultPlan(result, nodes, byName)
    }

    private fun buildAdjacency(
        byName: Map<String, WorkspaceRock>,
    ): Pair<MutableMap<String, Int>, Map<String, MutableSet<String>>> {
        val nodes = byName.keys
        val outDeg = mutableMapOf<String, Int>()
        val dependents = nodes.associateWith { mutableSetOf<String>() }

        for (node in nodes) {
            ProgressManager.checkCanceled()
            val rock = byName.getValue(node)
            val deps = rock.dependencyNames
                .map { it.lowercase() }
                .filter { it != node && it in byName }
                .toSet()

            outDeg[node] = deps.size
            for (dep in deps) {
                dependents.getValue(dep).add(node)
            }
        }
        return Pair(outDeg, dependents)
    }

    private fun buildResultPlan(
        result: List<String>,
        nodes: Set<String>,
        byName: Map<String, WorkspaceRock>,
    ): BuildPlan {
        if (result.size < nodes.size) {
            val cyclePackages = (nodes - result.toSet()).map { byName.getValue(it).packageName }.toSet()
            return BuildPlan.Cycle(cyclePackages)
        }
        return BuildPlan.Ordered(result.map { byName.getValue(it) })
    }
}
