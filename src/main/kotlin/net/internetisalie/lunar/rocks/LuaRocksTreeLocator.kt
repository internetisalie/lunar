package net.internetisalie.lunar.rocks

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.deps.LuaRocksVersion
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** An installed rock discovered in the project's rock tree. */
data class InstalledRock(
    val packageName: String,
    val version: LuaRocksVersion,
    val rockspec: Path,
)

/**
 * Locates the project-local LuaRocks tree and enumerates installed rocks by directory layout.
 *
 * v1 scope is project-local trees only (`lua_modules`, then `.luarocks`); system/global trees are
 * deferred (ROCKS-03-G-01). Installed rocks are read from `lib/luarocks/rocks-<X.Y>/<pkg>/<version>/`
 * — no manifest parsing required (ROCKS-03-DR-03 keeps that as a future fallback).
 */
object LuaRocksTreeLocator {
    private val log = logger<LuaRocksTreeLocator>()
    private val TREE_CANDIDATES = listOf("lua_modules", ".luarocks")

    fun treeRoot(project: Project): Path? {
        val base = project.basePath?.let { Path.of(it) } ?: return null
        return TREE_CANDIDATES
            .map { base.resolve(it) }
            .firstOrNull { it.isDirectory() }
    }

    /**
     * Every source rockspec in the project (ROCKS-09-08; also ROCKS-05's discovery hook).
     *
     * Delegates to the single [LuaRockspecDiscoveryService] scanner so the recursion/exclusion logic
     * lives in exactly one place; replaces the former single-root `projectRockspec`.
     */
    fun allProjectRockspecs(project: Project): List<Path> =
        LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths().map { it.rockspec }

    fun installedRocks(project: Project): List<InstalledRock> {
        val tree = treeRoot(project) ?: return emptyList()
        val luarocksDir = tree.resolve("lib").resolve("luarocks")
        if (!luarocksDir.isDirectory()) return emptyList()

        val result = mutableListOf<InstalledRock>()
        luarocksDir.listDirectoryEntries()
            .filter { it.isDirectory() && it.name.startsWith("rocks-") }
            .forEach { rocksDir ->
                rocksDir.listDirectoryEntries().filter { it.isDirectory() }.forEach { packageDir ->
                    packageDir.listDirectoryEntries().filter { it.isDirectory() }.forEach { versionDir ->
                        val packageName = packageDir.name
                        val versionText = versionDir.name
                        val rockspec = versionDir.resolve("$packageName-$versionText.rockspec")
                        if (rockspec.exists()) {
                            result += InstalledRock(packageName, LuaRocksVersion.parse(versionText), rockspec)
                        } else {
                            log.debug("No rockspec at expected path $rockspec")
                        }
                    }
                }
            }
        return result
    }
}
