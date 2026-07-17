package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.LuaRocksTreeLocator
import java.nio.file.Path

/**
 * Pure builder for canonical `luarocks install` / `remove` argument lists (ROCKS-16-02, design §3.1).
 *
 * Every install/uninstall targets the project rock tree resolved by [LuaRocksTreeLocator.treeRoot]
 * via an explicit `--tree <root>` pair, so the result is visible to [LuaRocksLibraryProvider],
 * [LuaRocksDependencyResolver], and [RockspecRunPathProvider] (which all read the same tree). The
 * tree-blind `LuaRocksActionHandler` this replaces produced installs invisible to the rest of the
 * plugin.
 *
 * All methods are pure — callable from any thread; [resolveTargetTree] reads only `project.basePath`.
 */
object LuaRocksInstallCommand {

    /**
     * Resolves the canonical install/uninstall target tree for [project], or `null` when no
     * project rock tree exists (the caller then renders the no-tree hint and disables Install).
     */
    fun resolveTargetTree(project: Project): Path? = LuaRocksTreeLocator.treeRoot(project)

    /**
     * Builds `["install", "--tree", <root>, <name>, <version?>]`. The `--tree <root>` pair precedes
     * the package name; [version] is appended last only when non-null and non-blank (design §3.1).
     */
    fun buildInstallArgs(treeRoot: Path, name: String, version: String?): List<String> = buildList {
        add("install")
        add("--tree")
        add(treeRoot.toString())
        add(name)
        if (!version.isNullOrBlank()) add(version)
    }

    /** Builds `["remove", "--tree", <root>, <name>]` (design §3.1). */
    fun buildRemoveArgs(treeRoot: Path, name: String): List<String> =
        listOf("remove", "--tree", treeRoot.toString(), name)
}
