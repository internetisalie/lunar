package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.launch
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.path.SourcePathPattern
import net.internetisalie.lunar.util.LunarCoroutineScopeService
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class RockspecSourcePathProvider(private val project: Project) {

    private val forceRefreshTracker = SimpleModificationTracker()
    private val prewarmInFlight = AtomicBoolean(false)
    private val cachedFull = AtomicReference<Pair<List<SourcePathPattern>, List<CModuleRock>>>(null)

    private val cache: CachedValue<Pair<List<SourcePathPattern>, List<CModuleRock>>> =
        CachedValuesManager.getManager(project).createCachedValue({
            CachedValueProvider.Result.create(
                resolvePatterns(),
                PsiModificationTracker.getInstance(project),
                forceRefreshTracker,
            )
        }, /* trackValue = */ false)

    /** Cached, deduplicated derived source-root patterns across all project rockspecs. */
    fun derivedPatterns(): List<SourcePathPattern> = cache.value.first

    /** Per-rockspec C-module info for the run-side LUA_CPATH (ROCKS-05-05). */
    fun cModuleRockspecs(): List<CModuleRock> = cache.value.second

    /**
     * Diverts read-lock callers (the #11 freeze path — reference resolution on a background
     * read-lock thread) to degraded static patterns + a deduplicated off-read-lock prewarm, so the
     * `RockspecBridge.read` subprocess NEVER executes under a read lock (MAINT-32-02). Non-read-lock
     * callers compute the full patterns synchronously, as before.
     */
    private fun resolvePatterns(): Pair<List<SourcePathPattern>, List<CModuleRock>> {
        val app = ApplicationManager.getApplication()
        val fenced = app.isReadAccessAllowed && (!app.isUnitTestMode || testForceReadLockGuard)
        if (!fenced) return computeSynchronously()

        cachedFull.get()?.let { return it }
        prewarm()
        return PathConfiguration.getStaticSourcePathPatterns(project) to emptyList()
    }

    private fun computeSynchronously(): Pair<List<SourcePathPattern>, List<CModuleRock>> {
        cachedFull.get()?.let { return it }
        val discovered = testDiscoverySeam?.invoke(project)
            ?: LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()
        return computePatternsFromPaths(discovered)
    }

    private fun prewarm() {
        if (cache.hasUpToDateValue()) return
        if (!prewarmInFlight.compareAndSet(false, true)) return
        LunarCoroutineScopeService.getInstance(project).scope.launch {
            try {
                val discovered = readAction {
                    testDiscoverySeam?.invoke(project)
                        ?: LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()
                }
                cachedFull.set(computePatternsFromPaths(discovered))
                forceRefreshTracker.incModificationCount()
            } finally {
                prewarmInFlight.set(false)
            }
        }
    }

    private fun computePatternsFromPaths(
        discovered: List<DiscoveredRockspec>,
    ): Pair<List<SourcePathPattern>, List<CModuleRock>> {
        val allPatterns = mutableListOf<SourcePathPattern>()
        val cRocks = mutableListOf<CModuleRock>()

        for (disco in discovered) {
            val data = RockspecBridge.read(project, disco.rockspec) ?: continue
            val dir = disco.rockspec.parent?.toString()?.replace('\\', '/') ?: continue

            allPatterns.addAll(RockspecModuleDerivation.derive(dir, data.luaModules))

            val hasCModules = data.buildType == "builtin" && data.cModules.isNotEmpty()
            cRocks.add(CModuleRock(dir, hasCModules))
        }

        return allPatterns.distinctBy { it.spec } to cRocks
    }

    companion object {
        @TestOnly
        var testDiscoverySeam: ((Project) -> List<DiscoveredRockspec>)? = null

        /** Forces the read-lock fence even in unit-test mode so TC-03/04/05 exercise the degraded+prewarm path. */
        @TestOnly
        var testForceReadLockGuard: Boolean = false

        @TestOnly
        fun invalidateCache(project: Project) {
            val provider = getInstance(project)
            provider.cachedFull.set(null)
            provider.forceRefreshTracker.incModificationCount()
        }

        /** True once the off-lock prewarm has published full patterns (test await seam; no compute). */
        @TestOnly
        fun isPrewarmComplete(project: Project): Boolean = getInstance(project).cachedFull.get() != null

        fun getInstance(project: Project): RockspecSourcePathProvider =
            project.getService(RockspecSourcePathProvider::class.java)
    }
}

/** A rockspec that declares at least one builtin C module. */
data class CModuleRock(val rockspecDir: String, val hasCModules: Boolean)
