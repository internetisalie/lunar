package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import java.nio.file.Path

/** One installed rock in the canonical tree, from `luarocks list --porcelain --tree <root>`. */
data class InstalledRockRow(val name: String, val version: String)

/**
 * Lists installed rocks from the canonical project tree (ROCKS-16-03, design §2.3 / §4.1).
 *
 * Runs `luarocks list --porcelain --tree <root>` and parses the tab-separated rows. Unlike the old
 * silent-empty path, an unresolved binary or non-zero exit throws [BrowserCliError] so the model can
 * render an honest error state (design §3.5). An exit-0 empty stdout is a valid empty tree.
 *
 * Background only — [LuaToolExecutionService.capture] blocks.
 */
object LuaRocksInstalledService {

    /** Lists installed rocks in [treeRoot]. Throws [BrowserCliError] on unresolved binary / non-zero exit. */
    fun list(project: Project, treeRoot: Path): List<InstalledRockRow> {
        val command = LuaRocksEnvironment.command(project, listOf("list", "--porcelain", "--tree", treeRoot.toString()))
            ?: throw BrowserCliError(BrowserCliError.LUAROCKS_NOT_CONFIGURED)
        val output = LuaToolExecutionService.getInstance().capture(command, LuaExecTimeout.COMMAND)
        if (output.exitCode != 0) {
            throw BrowserCliError(output.stderr.trim().ifEmpty { "luarocks list exited ${output.exitCode}" })
        }
        return parseInstalled(output.stdout)
    }

    /**
     * Parses `luarocks list --porcelain` stdout into [InstalledRockRow]s. Each non-blank line is
     * `<name>\t<version>\t<status>\t<install-path>`; lines with < 2 fields are skipped (design §4.1).
     */
    internal fun parseInstalled(stdout: String): List<InstalledRockRow> = stdout.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.split(Regex("\\s+")) }
        .filter { it.size >= 2 }
        .map { InstalledRockRow(it[0], it[1]) }
        .toList()
}
