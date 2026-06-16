package net.internetisalie.lunar.rocks

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.deps.DependencyNode
import net.internetisalie.lunar.rocks.deps.DependencySpec

/**
 * Builds the project's dependency graph from its rockspec plus the installed rock tree.
 *
 * Transitive dependencies are expanded recursively against each installed rock's own rockspec, with
 * a `visiting` set breaking cycles and a `seen` map sharing a single node per package. Background
 * only — every [RockspecBridge.read] call blocks.
 */
object LuaRocksDependencyResolver {
    fun resolve(project: Project): DependencyNode? {
        val rockspecPath = LuaRocksTreeLocator.projectRockspec(project) ?: return null
        val rootData = RockspecBridge.read(project, rockspecPath) ?: return null

        val installed = LuaRocksTreeLocator.installedRocks(project)
            .groupBy { it.packageName.lowercase() }
        val context = ResolutionContext(project, installed)

        val root = DependencyNode(rootData.packageName, isTransitive = false)
        context.seen[rootData.packageName.lowercase()] = root
        expand(root, rootData.dependencies, context, visiting = setOf(rootData.packageName.lowercase()), parentIsRoot = true)
        return root
    }

    private fun expand(
        parent: DependencyNode,
        rawDependencies: List<String>,
        context: ResolutionContext,
        visiting: Set<String>,
        parentIsRoot: Boolean,
    ) {
        for (raw in rawDependencies) {
            val spec = DependencySpec.parse(raw) ?: continue
            val child = childFor(spec, context, visiting, isTransitive = !parentIsRoot)
            child.requiredBy += parent
            child.requiredConstraints += spec.constraints
            parent.children += child
            if (!child.isCycle && child.resolvedVersion != null && child.children.isEmpty()) {
                recurseInto(child, spec, context, visiting)
            }
        }
    }

    private fun childFor(
        spec: DependencySpec,
        context: ResolutionContext,
        visiting: Set<String>,
        isTransitive: Boolean,
    ): DependencyNode {
        val key = spec.packageName.lowercase()
        if (key in visiting) {
            return DependencyNode(spec.packageName, isTransitive, isCycle = true)
        }
        context.seen[key]?.let { return it }
        val installed = context.installed[key].orEmpty()
        val resolved = installed
            .filter { spec.isSatisfiedBy(it.version) }
            .maxByOrNull { it.version }
            ?: installed.maxByOrNull { it.version }
        val node = DependencyNode(spec.packageName, isTransitive, resolvedVersion = resolved?.version)
        context.seen[key] = node
        return node
    }

    private fun recurseInto(
        child: DependencyNode,
        spec: DependencySpec,
        context: ResolutionContext,
        visiting: Set<String>,
    ) {
        val key = spec.packageName.lowercase()
        val rock = context.installed[key].orEmpty()
            .firstOrNull { it.version == child.resolvedVersion } ?: return
        val data = RockspecBridge.read(context.project, rock.rockspec) ?: return
        expand(child, data.dependencies, context, visiting + key, parentIsRoot = false)
    }

    private class ResolutionContext(
        val project: Project,
        val installed: Map<String, List<InstalledRock>>,
        val seen: MutableMap<String, DependencyNode> = mutableMapOf(),
    )
}
