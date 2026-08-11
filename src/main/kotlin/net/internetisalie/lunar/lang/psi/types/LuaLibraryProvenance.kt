package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * TYPE-11 §2.2 / §3.2 — answers "did this plugin provision this file?".
 *
 * The provenance set is the two **plugin-provisioned immutable** sources and only those: the
 * bundled stdlib/platform stub tree from [RuntimeLibraryProvider], and the enabled-and-fetched
 * definition libraries reached through the **EP-registered** [LuaDefinitionLibraryProvider]
 * instances. Rocks trees are deliberately absent — they are mutable in place, so they are out of
 * v1 scope (requirements §Scope).
 *
 * Two identity traps decide how the match is made, both measured (design §1.3):
 *
 * - **`originalFile`, not `virtualFile`.** A `PsiFile.copy()` of a library file — what completion
 *   and intentions hand around — has `virtualFile == null`, so a predicate reading `virtualFile`
 *   silently classifies every copy as unprovisioned.
 * - **URL containment, not reference identity.** `PsiManager.findFile(vf).virtualFile === vf` is
 *   `false` for a file the index itself supplied, so `===` cannot be the test. The bundled runtime
 *   root also arrives over `jar://` while definition libraries arrive over `file://`; URL prefixes
 *   handle both.
 *
 * Reads settings and the VFS only — no PSI, no writes. The root list is memoized per project, so
 * the classloader resource lookup and the catalog load run once per generation rather than once per
 * snapshot build.
 */
@Service(Service.Level.PROJECT)
class LuaLibraryProvenance(
    private val targetProject: Project,
) {
    /**
     * The generation signal a provisioned file's snapshot may depend on instead of every keystroke.
     *
     * Roots only. The target axis is deliberately **not** composited in: `targetModificationTracker`
     * stays an explicit dependency of `forFile` in both branches, so two dependencies each have one
     * job rather than one tracker that must be remembered to cover two axes (design §3.3 step 9).
     */
    fun generationTracker(): ModificationTracker = ProjectRootModificationTracker.getInstance(targetProject)

    fun isProvisioned(psiFile: PsiFile): Boolean {
        val fileUrl = psiFile.originalFile.virtualFile?.url ?: return false
        return isProvisionedUrl(fileUrl)
    }

    /**
     * The `"$root/"` suffix is required, not cosmetic: without it a sibling root `…/luassert-abc`
     * would match every file under `…/luassert-abcdef`.
     */
    fun isProvisionedUrl(url: String): Boolean = rootUrls().any { url == it || url.startsWith("$it/") }

    private fun rootUrls(): List<String> =
        CachedValuesManager.getManager(targetProject).getCachedValue(targetProject) {
            CachedValueProvider.Result.create(
                computeRootUrls(),
                ProjectRootModificationTracker.getInstance(targetProject),
                LuaProjectSettings.getInstance(targetProject).state.targetModificationTracker,
            )
        }

    private fun computeRootUrls(): List<String> {
        val target = LuaProjectSettings.getInstance(targetProject).state.getTarget()
        val runtimeRoot = RuntimeLibraryProvider(targetProject).getLibraryRoot(target)
        val definitionRoots =
            AdditionalLibraryRootsProvider.EP_NAME.extensionList
                .filterIsInstance<LuaDefinitionLibraryProvider>()
                .flatMap { it.getRootsToWatch(targetProject) }
        return (listOfNotNull(runtimeRoot) + definitionRoots).map { it.url }
    }

    companion object {
        fun getInstance(project: Project): LuaLibraryProvenance = project.getService(LuaLibraryProvenance::class.java)
    }
}
