package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * A browser backend for panel tests: runs every task inline and never touches the network or the
 * `luarocks` CLI. The tree is the real project tree, so tree-creation tests see the directory they make.
 */
internal class SyncBrowserBackend(
    private val project: Project,
    private val popular: List<PopularEntry> = emptyList(),
) : LuaRocksBrowserBackend {
    override fun resolveTree(): Path? = LuaRocksInstallCommand.resolveTargetTree(project)

    override fun search(
        query: String,
        treeRoot: Path?,
    ): List<LuaRockPackage> = emptyList()

    override fun listInstalled(treeRoot: Path): List<InstalledRockRow> = emptyList()

    override fun fetchPopular(): List<PopularEntry> = popular

    override fun runInBackground(task: () -> Unit) = task()

    override fun onEdt(action: () -> Unit) = action()
}
