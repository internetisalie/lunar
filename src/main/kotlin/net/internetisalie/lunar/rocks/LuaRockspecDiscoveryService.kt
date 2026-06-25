package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.nio.file.Path

/** A discovered source rockspec and its parsed identity (ROCKS-09-03). */
data class DiscoveredRockspec(
    val rockspec: Path,
    val packageName: String?,
)

/**
 * The sole project-rockspec scanner (ROCKS-09): recursive, exclusion-aware, cached discovery.
 *
 * Discovery is index-backed via [FilenameIndex.getAllFilesByExt] (never a raw `nio` tree walk),
 * scoped to the project, filtered by [RockspecExclusionFilter], and cached behind a
 * [PsiModificationTracker] so repeated calls in one edit-cycle do not re-enumerate. Read-only;
 * callers must be off the EDT. Holds only [Project] — never a `VirtualFile`.
 *
 * ROCKS-05 / ROCKS-10 consume this service; they must not define their own scanner.
 */
@Service(Service.Level.PROJECT)
class LuaRockspecDiscoveryService(private val project: Project) {

    private val discoveryCache: CachedValue<List<DiscoveredRockspec>> =
        CachedValuesManager.getManager(project).createCachedValue(
            {
                CachedValueProvider.Result.create(
                    compute(),
                    PsiModificationTracker.getInstance(project),
                )
            },
            /* trackValue = */ false,
        )

    /**
     * Cached, sorted (by project-relative path, case-insensitive); one entry per discovered source
     * rockspec (path + parsed identity). The single discovery method consumers call.
     */
    fun discoverRockspecPaths(): List<DiscoveredRockspec> = discoveryCache.value

    private fun compute(): List<DiscoveredRockspec> {
        if (DumbService.isDumb(project)) return emptyList()
        val settingsState = LuaProjectSettings.getInstance(project).state
        val includeGlobs = settingsState.rockspecIncludeGlobs.toList()
        val excludeGlobs = settingsState.rockspecExcludeGlobs.toList()

        val includedRockspecs = ReadAction.nonBlocking<List<IncludedRockspec>> {
            enumerateIncluded(includeGlobs, excludeGlobs)
        }.expireWith(project).executeSynchronously()

        return includedRockspecs
            .map { DiscoveredRockspec(it.path, RockspecBridge.read(project, it.path)?.packageName) }
    }

    private fun enumerateIncluded(
        includeGlobs: List<String>,
        excludeGlobs: List<String>,
    ): List<IncludedRockspec> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        return FilenameIndex.getAllFilesByExt(project, ROCKSPEC_EXT, scope)
            .mapNotNull { vf -> includedEntry(vf, fileIndex, includeGlobs, excludeGlobs) }
            .sortedBy { it.relativePath.lowercase() }
    }

    private fun includedEntry(
        rockspecFile: VirtualFile,
        fileIndex: ProjectFileIndex,
        includeGlobs: List<String>,
        excludeGlobs: List<String>,
    ): IncludedRockspec? {
        ProgressManager.checkCanceled()
        val contentRoot = fileIndex.getContentRootForFile(rockspecFile) ?: return null
        val relativePath = VfsUtilCore.getRelativePath(rockspecFile, contentRoot) ?: return null
        if (!RockspecExclusionFilter.isIncluded(relativePath, includeGlobs, excludeGlobs)) return null
        val nioPath = rockspecFile.fileSystem.getNioPath(rockspecFile) ?: Path.of(rockspecFile.path)
        return IncludedRockspec(nioPath, relativePath)
    }

    private data class IncludedRockspec(val path: Path, val relativePath: String)

    companion object {
        private const val ROCKSPEC_EXT = "rockspec"

        fun getInstance(project: Project): LuaRockspecDiscoveryService =
            project.getService(LuaRockspecDiscoveryService::class.java)
    }
}
