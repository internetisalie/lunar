package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Path

/** Creates the project rock tree the browser installs into (BUG-55). */
fun interface LuaRocksTreeInitializer {
    /** Starts the creation; [onDone] is invoked on the EDT with the success flag. */
    fun initialize(onDone: (Boolean) -> Unit)

    companion object {
        /**
         * Runs `luarocks init` in the project root with the resolved LuaRocks, then refreshes the VFS
         * so the new `lua_modules`, `.luarocks` and wrapper files appear in the project view.
         */
        fun forProject(project: Project): LuaRocksTreeInitializer =
            LuaRocksTreeInitializer { onDone ->
                val root = project.basePath?.let { Path.of(it) }
                if (root == null) {
                    onDone(false)
                } else {
                    LuaRocksInstallExecutor(project).initialize(root) { success ->
                        LocalFileSystem
                            .getInstance()
                            .findFileByNioFile(
                                root,
                            )?.let { VfsUtil.markDirtyAndRefresh(true, true, true, it) }
                        onDone(success)
                    }
                }
            }
    }
}
