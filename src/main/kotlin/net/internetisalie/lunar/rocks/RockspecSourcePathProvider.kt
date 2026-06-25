package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.AppExecutorUtil
import net.internetisalie.lunar.lang.path.SourcePathPattern

@Service(Service.Level.PROJECT)
class RockspecSourcePathProvider(private val project: Project) {

    private val forceRefreshTracker = SimpleModificationTracker()
    private val isComputing = ThreadLocal.withInitial { false }

    private val cache: CachedValue<Pair<List<SourcePathPattern>, List<CModuleRock>>> =
        CachedValuesManager.getManager(project).createCachedValue({
            val app = ApplicationManager.getApplication()
            if (app.isDispatchThread && !app.isUnitTestMode) {
                // Return empty and prime in background
                ReadAction.nonBlocking<Unit> {
                    forceRefreshTracker.incModificationCount()
                    cache.value // Evaluates off-EDT and caches the real result
                }.submit(AppExecutorUtil.getAppExecutorService())
                
                CachedValueProvider.Result.create(
                    emptyList<SourcePathPattern>() to emptyList<CModuleRock>(),
                    PsiModificationTracker.getInstance(project),
                    forceRefreshTracker
                )
            } else {
                CachedValueProvider.Result.create(
                    compute(),
                    PsiModificationTracker.getInstance(project),
                    forceRefreshTracker
                )
            }
        }, /* trackValue = */ false)

    /** Cached, deduplicated derived source-root patterns across all project rockspecs. */
    fun derivedPatterns(): List<SourcePathPattern> = cache.value.first

    /** Per-rockspec C-module info for the run-side LUA_CPATH (ROCKS-05-05). */
    fun cModuleRockspecs(): List<CModuleRock> = cache.value.second

    private fun compute(): Pair<List<SourcePathPattern>, List<CModuleRock>> {
        if (isComputing.get()) return emptyList<SourcePathPattern>() to emptyList<CModuleRock>()
        isComputing.set(true)
        try {
            val discovered = testDiscoverySeam?.invoke(project)
                ?: LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()
            val allPatterns = mutableListOf<SourcePathPattern>()
            val cRocks = mutableListOf<CModuleRock>()

            for (disco in discovered) {
                val data = RockspecBridge.read(project, disco.rockspec) ?: continue
                val dir = disco.rockspec.parent?.toString()?.replace('\\', '/') ?: continue
                
                val patterns = RockspecModuleDerivation.derive(dir, data.luaModules)
                allPatterns.addAll(patterns)
                
                val hasCModules = data.buildType == "builtin" && data.cModules.isNotEmpty()
                cRocks.add(CModuleRock(dir, hasCModules))
            }

            val distinctPatterns = allPatterns.distinctBy { it.spec }
            return distinctPatterns to cRocks
        } finally {
            isComputing.set(false)
        }
    }

    companion object {
        @org.jetbrains.annotations.TestOnly
        var testDiscoverySeam: ((Project) -> List<DiscoveredRockspec>)? = null

        @org.jetbrains.annotations.TestOnly
        fun invalidateCache(project: Project) {
            getInstance(project).forceRefreshTracker.incModificationCount()
        }

        fun getInstance(project: Project): RockspecSourcePathProvider =
            project.getService(RockspecSourcePathProvider::class.java)
    }
}

/** A rockspec that declares at least one builtin C module. */
data class CModuleRock(val rockspecDir: String, val hasCModules: Boolean)
