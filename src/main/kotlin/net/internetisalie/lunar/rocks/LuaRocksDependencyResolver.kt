package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.rocks.deps.DependencyKind
import net.internetisalie.lunar.rocks.deps.DependencyNode
import net.internetisalie.lunar.rocks.deps.DependencySpec
import net.internetisalie.lunar.rocks.deps.LuaRocksVersion
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import java.nio.file.Path

/**
 * Builds the project's dependency forest from every discovered rockspec plus the installed tree.
 *
 * One resolved root per discovered rock (ROCKS-09-05), each with a fresh [ResolutionContext] so the
 * `seen`/`visiting` state is per-root; the installed-rock index is computed once and shared.
 * Transitive dependencies are expanded recursively against each installed rock's own rockspec, with
 * a `visiting` set breaking cycles and a `seen` map sharing a single node per package. Background
 * only — every [RockspecBridge.read] call blocks.
 */
object LuaRocksDependencyResolver {
    /** Resolves one [DependencyNode] root per discovered rockspec (ROCKS-09-05). */
    fun resolveAll(project: Project): List<DependencyNode> {
        val installed =
            LuaRocksTreeLocator
                .installedRocks(project)
                .groupBy { it.packageName.lowercase() }
        return LuaRockspecDiscoveryService
            .getInstance(project)
            .discoverRockspecPaths()
            .mapNotNull { resolveOne(project, it.rockspec, installed) }
    }

    /** Back-compat shim for the existing single-root callers and TC parity. */
    fun resolve(project: Project): DependencyNode? = resolveAll(project).firstOrNull()

    private fun resolveOne(
        project: Project,
        rockspec: Path,
        installed: Map<String, List<InstalledRock>>,
    ): DependencyNode? {
        val rootData = RockspecBridge.read(project, rockspec) ?: return null
        val context = ResolutionContext(project, installed)
        val rootKey = rootData.packageName.lowercase()
        val root =
            DependencyNode(
                rootData.packageName,
                isTransitive = false,
                resolvedVersion = rootData.version?.let { LuaRocksVersion.parse(it) },
                kind = DependencyKind.PROJECT,
                location = projectRelative(project, rockspec),
            )
        context.seen[rootKey] = root
        expand(root, rootData.dependencies, context, visiting = setOf(rootKey), parentIsRoot = true)
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
        if (key == RUNTIME_PACKAGE) return context.seen.getOrPut(key) { runtimeNode(spec, context, isTransitive) }
        if (key in visiting) {
            return DependencyNode(spec.packageName, isTransitive, isCycle = true)
        }
        context.seen[key]?.let { return it }
        val installed = context.installed[key].orEmpty()
        val resolved =
            installed
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
        val rock =
            context.installed[key]
                .orEmpty()
                .firstOrNull { it.version == child.resolvedVersion } ?: return
        val data = RockspecBridge.read(context.project, rock.rockspec) ?: return
        expand(child, data.dependencies, context, visiting + key, parentIsRoot = false)
    }

    /** `lua` names the interpreter, not a rock: its version is the configured runtime's (BUG-54). */
    private fun runtimeNode(
        spec: DependencySpec,
        context: ResolutionContext,
        isTransitive: Boolean,
    ): DependencyNode {
        val version = LuaToolResolver.getInstance().resolveRuntime(context.project)?.luaVersion
        return DependencyNode(
            spec.packageName,
            isTransitive,
            resolvedVersion = version?.let { LuaRocksVersion.parse(it) },
            kind = DependencyKind.RUNTIME,
        )
    }

    /** The rockspec's path relative to its content root, or its absolute path outside the project. */
    private fun projectRelative(
        project: Project,
        rockspec: Path,
    ): String {
        val file = LocalFileSystem.getInstance().findFileByNioFile(rockspec) ?: return rockspec.toString()
        val root =
            ReadAction.compute<VirtualFile?, RuntimeException> {
                ProjectFileIndex.getInstance(project).getContentRootForFile(file)
            } ?: return rockspec.toString()
        return VfsUtilCore.getRelativePath(file, root) ?: rockspec.toString()
    }

    private const val RUNTIME_PACKAGE = "lua"

    private class ResolutionContext(
        val project: Project,
        val installed: Map<String, List<InstalledRock>>,
        val seen: MutableMap<String, DependencyNode> = mutableMapOf(),
    )
}
