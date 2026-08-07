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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class RockspecSourcePathProvider(
    private val project: Project,
) {
    private val forceRefreshTracker = SimpleModificationTracker()
    private val prewarmInFlight = AtomicBoolean(false)
    private val prewarmLaunches = AtomicInteger(0)

    /**
     * Generation counter, bumped by [invalidateCache] (BUG-410).
     *
     * A prewarm captures it at launch and checks it before publishing, so a job whose inputs were
     * invalidated while it was computing retires quietly instead of overwriting fresher state.
     */
    private val prewarmEpoch = AtomicInteger(0)
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
        val discovered =
            testDiscoverySeam?.invoke(project)
                ?: LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()
        return computePatternsFromPaths(discovered)
    }

    private fun prewarm() {
        if (cache.hasUpToDateValue()) return
        if (!prewarmInFlight.compareAndSet(false, true)) return
        prewarmLaunches.incrementAndGet()
        val epoch = prewarmEpoch.get()
        LunarCoroutineScopeService.getInstance(project).scope.launch {
            try {
                val discovered =
                    readAction {
                        testDiscoverySeam?.invoke(project)
                            ?: LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths()
                    }
                val computed = computePatternsFromPaths(discovered)
                // BUG-410: publish only if nothing invalidated us while we were computing.
                // Otherwise this result describes inputs that are already gone, and publishing it
                // would both resurrect stale patterns and make the next caller believe its own
                // prewarm had completed.
                if (prewarmEpoch.get() == epoch) {
                    cachedFull.set(computed)
                    forceRefreshTracker.incModificationCount()
                }
            } finally {
                // Never clear a flag that now belongs to a newer generation — doing so would let
                // two prewarms run at once, which is the dedup this flag exists to provide.
                if (prewarmEpoch.get() == epoch) prewarmInFlight.set(false)
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
            val dir =
                disco.rockspec.parent
                    ?.toString()
                    ?.replace('\\', '/') ?: continue

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
            // BUG-410: retire any in-flight prewarm FIRST. Previously this cleared the counters but
            // left `prewarmInFlight` set, so a job launched before the reset kept the flag — the
            // next `prewarm()` bailed at the CAS without counting a launch — and then published into
            // `cachedFull`, which is all `isPrewarmComplete` looks at. A caller therefore saw an
            // await satisfied by somebody else's job with its own launch count still at zero.
            provider.prewarmEpoch.incrementAndGet()
            provider.prewarmInFlight.set(false)
            provider.cachedFull.set(null)
            provider.prewarmLaunches.set(0)
            provider.forceRefreshTracker.incModificationCount()
        }

        /** True once the off-lock prewarm has published full patterns (test await seam; no compute). */
        @TestOnly
        fun isPrewarmComplete(project: Project): Boolean = getInstance(project).cachedFull.get() != null

        /** Count of prewarm jobs this project's provider launched (project-local; dedup assertions). */
        @TestOnly
        fun prewarmLaunchCount(project: Project): Int = getInstance(project).prewarmLaunches.get()

        fun getInstance(project: Project): RockspecSourcePathProvider =
            project.getService(RockspecSourcePathProvider::class.java)
    }
}

/** A rockspec that declares at least one builtin C module. */
data class CModuleRock(
    val rockspecDir: String,
    val hasCModules: Boolean,
)
