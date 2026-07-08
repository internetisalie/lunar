package net.internetisalie.lunar.toolchain.provision

import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeedLoader
import java.nio.file.Path
import java.util.UUID

/**
 * The order-independent inputs to one [LuaProvisionEngine.execute] call (design §3.1), bundled so
 * the engine's helpers stay within the ≤3-argument tripwire (engineering-contract §2.1). The
 * [request]'s `rootDir` is already canonicalized by [LuaToolProvisioner].
 */
data class LuaProvisionJob(
    val project: Project,
    val request: LuaProvisionRequest,
    val indicator: ProgressIndicator,
)

/**
 * The orchestration pipeline (design §3.1 steps 4–10), extracted from [LuaToolProvisioner] so it is
 * directly callable in unit tests with fake strategies, a spy [LuaProvisionResultSink] and a spy
 * [LuaProvisionNotifier] — no live `Task.Backgroundable`, subprocess or network required.
 *
 * All collaborators are injected. Runs only on a background thread (blocking I/O / subprocesses);
 * holds no `Project`/PSI/VFS reference — `job.project` is used transiently, never retained.
 */
class LuaProvisionEngine(
    private val strategies: List<LuaProvisioningStrategy> = defaultStrategies(),
    private val sink: LuaProvisionResultSink = RegistryProvisionResultSink(),
    private val notifier: LuaProvisionNotifier = BalloonProvisionNotifier(),
) {
    /**
     * Runs the pipeline for [job]. A [LuaProvisionException] surfaces as an ERROR balloon;
     * a [ProcessCanceledException] surfaces as a "cancelled" balloon and re-propagates so the
     * platform task machinery observes the cancellation.
     */
    fun execute(job: LuaProvisionJob) {
        run(job, LuaHostPlatform.current(), LuaToolchainFeedLoader.load())
    }

    /**
     * The full pipeline against an explicit [platform] and [feed] (design §3.1 steps 4–10). Split
     * from [execute] so unit tests can inject a fake feed and a chosen platform without the bundled
     * resource or the host's real OS. Failures surface as balloons; cancellation re-propagates.
     */
    internal fun run(job: LuaProvisionJob, platform: LuaHostPlatform, feed: LuaToolchainFeed) {
        try {
            runPipeline(RunInputs(job, platform, feed))
        } catch (cancelled: ProcessCanceledException) {
            notifier.notify(job.project, "Provisioning cancelled", NotificationType.WARNING)
            throw cancelled
        } catch (failure: LuaProvisionException) {
            notifier.notify(job.project, failure.message.orEmpty(), NotificationType.ERROR)
        }
    }

    private data class RunInputs(val job: LuaProvisionJob, val platform: LuaHostPlatform, val feed: LuaToolchainFeed)

    private fun runPipeline(inputs: RunInputs) {
        val rootDir = Path.of(inputs.job.request.rootDir)
        val manifest = LuaEnvManifest.read(rootDir)
        val ordered = orderItems(inputs.job.request.items, manifest)
        resolveAll(ordered, inputs.platform, inputs.feed)
        val toolchain = preflight(Preflight(ordered, inputs.platform, inputs.feed))
        provisionAll(LuaProvisionSetup(inputs.job, inputs.platform, inputs.feed, toolchain), ordered)
    }

    // --- §3.1 step 4: fail-fast version resolution before any work ---

    private fun resolveAll(items: List<LuaProvisionItem>, platform: LuaHostPlatform, feed: LuaToolchainFeed) {
        items.forEach { LuaToolchainFeedLoader.resolveVersion(feed, it.kindId, it.versionSpec, platform) }
    }

    // --- §3.1 step 5: ordering + forced-LuaRocks rule ---

    private fun orderItems(items: List<LuaProvisionItem>, manifest: LuaEnvManifest?): List<LuaProvisionItem> {
        val ordered = items.sortedBy(::orderClass)
        requireLuaRocksForRocks(ordered, manifest)
        return ordered
    }

    private fun orderClass(item: LuaProvisionItem): Int =
        when {
            item.kindId in LuaProvisioningPlan.RUNTIME_KINDS -> 0
            item.kindId == "luarocks" -> 1
            LuaProvisioningPlan.strategyIdsFor(item.kindId).firstOrNull() == "release-binary" -> 2
            else -> 3
        }

    private fun requireLuaRocksForRocks(items: List<LuaProvisionItem>, manifest: LuaEnvManifest?) {
        val rocks = items.filter { LuaProvisioningPlan.strategyIdsFor(it.kindId) == listOf("luarocks-install") }
        if (rocks.isEmpty()) return
        val hasLuaRocks = items.any { it.kindId == "luarocks" } || manifest?.components?.containsKey("luarocks") == true
        if (!hasLuaRocks) {
            val names = rocks.joinToString(", ") { it.kindId }
            throw LuaProvisionException("Installing $names requires LuaRocks in the environment.")
        }
    }

    // --- §3.1 step 6: C-toolchain preflight ---

    private data class Preflight(val items: List<LuaProvisionItem>, val platform: LuaHostPlatform, val feed: LuaToolchainFeed)

    private fun preflight(preflight: Preflight): LuaCompilerProbe.Toolchain? {
        if (preflight.platform.os == LuaOs.WINDOWS || !needsCToolchain(preflight)) return null
        return LuaCompilerProbe.probe(preflight.platform) ?: throw LuaProvisionException(LuaCompilerProbe.REMEDIATION)
    }

    private fun needsCToolchain(preflight: Preflight): Boolean =
        preflight.items.any { selectableAsSource(it, preflight) || nativeRock(it, preflight) }

    private fun selectableAsSource(item: LuaProvisionItem, preflight: Preflight): Boolean {
        if (item.kindId !in LuaProvisioningPlan.SOURCE_BUILD_KINDS) return false
        return strategyById("source-build")?.supports(item, preflight.platform, preflight.feed) == true
    }

    private fun nativeRock(item: LuaProvisionItem, preflight: Preflight): Boolean {
        if (LuaProvisioningPlan.strategyIdsFor(item.kindId) != listOf("luarocks-install")) return false
        val rock = runCatching {
            LuaToolchainFeedLoader.resolveVersion(preflight.feed, item.kindId, item.versionSpec, preflight.platform).rock
        }.getOrNull()
        return rock?.needsCToolchain == true
    }

    // --- §3.1 steps 7–10: per-item skip/execute loop + registration ---

    private fun provisionAll(setup: LuaProvisionSetup, items: List<LuaProvisionItem>) {
        val rootDir = Path.of(setup.job.request.rootDir)
        var manifest = manifestFor(setup, rootDir)
        val components = mutableListOf<LuaProvisionedComponent>()
        var skippedAll = true
        items.forEachIndexed { index, item ->
            setup.job.indicator.fraction = index.toDouble() / items.size
            val outcome = provisionOne(ItemRun(setup, item, manifest))
            components += outcome.component
            if (!outcome.skipped) {
                skippedAll = false
                manifest = mergeManifest(manifest, item.kindId, outcome.component)
                LuaEnvManifest.write(rootDir, manifest)
            }
        }
        register(setup, RunResult(manifest, components, skippedAll))
    }

    private data class RunResult(
        val manifest: LuaEnvManifest,
        val components: List<LuaProvisionedComponent>,
        val skippedAll: Boolean,
    )

    private fun manifestFor(setup: LuaProvisionSetup, rootDir: Path): LuaEnvManifest {
        val existing = LuaEnvManifest.read(rootDir)
        val environmentId = existing?.environmentId ?: UUID.randomUUID().toString()
        return LuaEnvManifest(
            manifestVersion = 1,
            environmentId = environmentId,
            environmentName = setup.job.request.environmentName,
            request = setup.job.request,
            components = existing?.components.orEmpty(),
        )
    }

    private data class ItemRun(val setup: LuaProvisionSetup, val item: LuaProvisionItem, val manifest: LuaEnvManifest)

    private data class ItemOutcome(val component: LuaProvisionedComponent, val skipped: Boolean)

    private fun provisionOne(run: ItemRun): ItemOutcome {
        val indicator = run.setup.job.indicator
        indicator.checkCanceled()
        val skip = trySkip(run)
        if (skip != null) return ItemOutcome(skip, skipped = true)
        indicator.text = "Provisioning ${run.item.kindId} ${run.item.versionSpec}"
        val component = runStrategies(run)
        indicator.checkCanceled()
        return ItemOutcome(component, skipped = false)
    }

    /** Returns a reusable component when the recorded one is up to date, else null (design §3.1 step 8). */
    private fun trySkip(run: ItemRun): LuaProvisionedComponent? {
        val recorded = run.manifest.components[run.item.kindId] ?: return null
        val rootDir = Path.of(run.setup.job.request.rootDir)
        val fresh = supporting(run).firstNotNullOfOrNull { freshHash(it, run) } ?: return null
        if (fresh != recorded.identifiersHash) return null
        if (!LuaManifestSkipRule.shouldSkip(LuaSkipCheck(recorded, fresh, rootDir))) return null
        run.setup.job.indicator.text = "${run.item.kindId} ${recorded.resolvedVersion} — up to date"
        return reusedComponent(run.item.kindId, recorded, rootDir)
    }

    private fun freshHash(strategy: LuaProvisioningStrategy, run: ItemRun): String? =
        runCatching { strategy.identityHash(contextFor(run), run.item) }.getOrNull()

    private fun reusedComponent(kindId: String, recorded: LuaManifestComponent, rootDir: Path): LuaProvisionedComponent {
        val binaries = recorded.binaries.map { rootDir.resolve(it) }
        return LuaProvisionedComponent(
            kindId = kindId,
            resolvedVersion = recorded.resolvedVersion,
            strategyId = recorded.strategyId,
            primaryBinary = binaries.first(),
            extraBinaries = binaries.drop(1),
            identifiersHash = recorded.identifiersHash,
        )
    }

    private fun runStrategies(run: ItemRun): LuaProvisionedComponent {
        val context = contextFor(run)
        val errors = mutableListOf<String>()
        for (strategy in supporting(run)) {
            try {
                return strategy.provision(context, run.item)
            } catch (cancelled: ProcessCanceledException) {
                throw cancelled
            } catch (failure: LuaProvisionException) {
                errors += "${strategy.id}: ${failure.message}"
            }
        }
        throw noMethod(run.item, run.setup.platform, errors)
    }

    private fun supporting(run: ItemRun): List<LuaProvisioningStrategy> =
        LuaProvisioningPlan.strategyIdsFor(run.item.kindId).mapNotNull(::strategyById)
            .filter { it.supports(run.item, run.setup.platform, run.setup.feed) }

    private fun contextFor(run: ItemRun): LuaProvisionContext =
        LuaProvisionContext(
            project = run.setup.job.project,
            request = run.setup.job.request,
            platform = run.setup.platform,
            feed = run.setup.feed,
            rootDir = Path.of(run.setup.job.request.rootDir),
            indicator = run.setup.job.indicator,
            toolchain = run.setup.toolchain,
        )

    private fun noMethod(item: LuaProvisionItem, platform: LuaHostPlatform, errors: List<String>): LuaProvisionException {
        val prefix = "${item.kindId} ${item.versionSpec} failed:"
        if (errors.isEmpty()) {
            val where = "${platform.os.name.lowercase()}-${platform.arch.name.lowercase()}"
            return LuaProvisionException("$prefix No provisioning method for ${item.kindId} on $where")
        }
        return LuaProvisionException("$prefix ${errors.joinToString("\n")}")
    }

    private fun mergeManifest(manifest: LuaEnvManifest, kindId: String, component: LuaProvisionedComponent): LuaEnvManifest {
        val rootDir = Path.of(manifest.request.rootDir)
        val record = LuaManifestComponent(
            resolvedVersion = component.resolvedVersion,
            strategyId = component.strategyId,
            identifiersHash = component.identifiersHash,
            binaries = (listOf(component.primaryBinary) + component.extraBinaries).map { relativize(rootDir, it) },
            provisionedAtEpochMs = System.currentTimeMillis(),
        )
        return manifest.copy(components = manifest.components + (kindId to record))
    }

    private fun relativize(rootDir: Path, binary: Path): String =
        runCatching { rootDir.relativize(binary).toString().replace('\\', '/') }.getOrDefault(binary.toString())

    private fun register(setup: LuaProvisionSetup, run: RunResult) {
        val result = LuaProvisionResult(
            environmentId = run.manifest.environmentId,
            environmentName = setup.job.request.environmentName,
            rootDir = setup.job.request.rootDir,
            components = run.components,
        )
        sink.register(setup.job.project, result)
        val label = if (run.skippedAll) {
            "Lua toolchain '${result.environmentName}' already up to date"
        } else {
            "Provisioned Lua toolchain '${result.environmentName}'"
        }
        notifier.notify(setup.job.project, "$label (${run.components.size} tools)", NotificationType.INFORMATION)
    }

    private fun strategyById(id: String): LuaProvisioningStrategy? = strategies.firstOrNull { it.id == id }

    private companion object {
        fun defaultStrategies(): List<LuaProvisioningStrategy> =
            listOf(ReleaseBinaryStrategy(), SourceBuildStrategy(), LuaRocksInstallStrategy())
    }
}

/**
 * The resolved per-run state shared across the per-item loop (design §3.1 steps 7–10), bundled so
 * the loop helpers stay within the ≤3-argument tripwire.
 */
internal data class LuaProvisionSetup(
    val job: LuaProvisionJob,
    val platform: LuaHostPlatform,
    val feed: LuaToolchainFeed,
    val toolchain: LuaCompilerProbe.Toolchain?,
)
