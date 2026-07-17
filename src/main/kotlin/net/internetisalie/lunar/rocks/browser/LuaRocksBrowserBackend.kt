package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * The CLI + threading seam [LuaRocksBrowserModel] talks to (design §2.5). Production wires the real
 * services ([ProjectBackend]); model tests inject a synchronous fake so state transitions are
 * verifiable headlessly with no real `luarocks` and no EDT.
 *
 * All [search]/[listInstalled] calls run on a background thread; [onEdt] marshals the result back.
 * [search] throws [BrowserCliError] on unresolved binary / non-zero exit (design §3.5).
 */
interface LuaRocksBrowserBackend {
    fun resolveTree(): Path?
    fun search(query: String, treeRoot: Path?): List<LuaRockPackage>
    fun listInstalled(treeRoot: Path): List<InstalledRockRow>
    fun runInBackground(task: () -> Unit)
    fun onEdt(action: () -> Unit)
}

/** Production backend delegating to the real LuaRocks services against [project]. */
class ProjectBackend(private val project: Project) : LuaRocksBrowserBackend {
    override fun resolveTree(): Path? = LuaRocksInstallCommand.resolveTargetTree(project)

    override fun search(query: String, treeRoot: Path?): List<LuaRockPackage> =
        LuaRocksSearchService.search(query, project, treeRoot)

    override fun listInstalled(treeRoot: Path): List<InstalledRockRow> =
        LuaRocksInstalledService.list(project, treeRoot)

    override fun runInBackground(task: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(task)
    }

    override fun onEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action)
    }
}
