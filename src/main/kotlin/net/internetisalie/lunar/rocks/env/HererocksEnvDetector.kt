package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Detects an existing hererocks-shaped directory in a project (ROCKS-14-05).
 *
 * A directory is hererocks-shaped when it holds both an executable Lua and a `luarocks` launcher.
 * Detection scans the project base's immediate children plus a set of conventional env dir names.
 */
object HererocksEnvDetector {
    private val CONVENTIONAL_NAMES = listOf(".lua", "lua_env", "hererocks", ".hererocks", "_lua")

    /** Returns the path of the first hererocks-shaped directory, or `null`. VFS-reads internally. */
    fun detect(project: Project): String? = ApplicationManager.getApplication().runReadAction<String?> {
        val base = project.guessProjectDir() ?: return@runReadAction null
        val candidates = base.children.filter { it.isDirectory } +
            CONVENTIONAL_NAMES.mapNotNull { base.findChild(it) }
        candidates.distinct().firstOrNull { hasLua(it) && hasLuarocks(it) }?.path
    }

    /** Infers a bindable descriptor from a detected dir; versions are informational (§3.4). */
    fun descriptorFromDir(dir: String): HererocksEnvState {
        val flavor = if (File(dir).let { HererocksEnvState(directory = it.path, flavor = HererocksFlavor.LUAJIT).luaExeExists() }) {
            HererocksFlavor.LUAJIT
        } else {
            HererocksFlavor.PUC
        }
        return HererocksEnvState(directory = dir, flavor = flavor, luaVersion = "", luarocksVersion = "latest")
    }

    private fun HererocksEnvState.luaExeExists(): Boolean = File(luaExe()).exists()

    private fun hasLua(dir: VirtualFile): Boolean =
        dir.findFileByRelativePath("bin/lua") != null || dir.findChild("lua.exe") != null ||
            dir.findFileByRelativePath("bin/luajit") != null || dir.findChild("luajit.exe") != null

    private fun hasLuarocks(dir: VirtualFile): Boolean =
        dir.findFileByRelativePath("bin/luarocks") != null || dir.findChild("luarocks.bat") != null
}
