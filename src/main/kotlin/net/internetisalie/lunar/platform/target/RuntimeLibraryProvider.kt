package net.internetisalie.lunar.platform.target

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides access to platform-specific Lua library definitions from the unified `runtime/` resource tree.
 *
 * Library files are resolved via [Target.getLibraryRootPath], which maps a (platform, version) pair to a
 * resource subdirectory like `runtime/redis/redis-7/` or `runtime/standard/lua-5.4/`.
 *
 * If no resources are bundled for a target (e.g., future platforms), [getLibraryRoot] returns null.
 * Callers must handle null gracefully.
 */
class RuntimeLibraryProvider(private val project: Project) {

    /**
     * Returns the VirtualFile directory root for the given target's libraries, or null if not found.
     *
     * @param target The runtime target (platform + version).
     * @return The VirtualFile directory, or null if no resources are bundled for this target.
     */
    fun getLibraryRoot(target: Target): VirtualFile? {
        val path = target.getLibraryRootPath()
        return RuntimeLibraryProvider::class.java.classLoader.getResource(path)
            ?.let { VfsUtil.findFileByURL(it) }
    }

    /**
     * Returns a list of all `.lua` library files bundled for the given target.
     *
     * Returns an empty list if no resources are bundled for this target.
     *
     * @param target The runtime target (platform + version).
     * @return List of `.lua` VirtualFile objects, or empty if none are bundled.
     */
    fun getLibraryFiles(target: Target): List<VirtualFile> =
        getLibraryRoot(target)
            ?.children
            ?.filter { it.extension == "lua" }
            ?: emptyList()
}
