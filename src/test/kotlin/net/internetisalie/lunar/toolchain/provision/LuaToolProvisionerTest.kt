package net.internetisalie.lunar.toolchain.provision

import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedKind
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedRock
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersion
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * Network- and subprocess-free tests for the provisioning orchestrator (design §3.1). They drive
 * the core [LuaProvisionEngine.run] pipeline directly with an injected fake feed / chosen platform,
 * fake [LuaProvisioningStrategy]s, and spy [LuaProvisionResultSink] / [LuaProvisionNotifier];
 * [LuaToolProvisioner]'s reservation seam is exercised for TC 2. Inherits [BasePlatformTestCase]
 * only for a live application + a [project] value to fill the pipeline context.
 */
class LuaToolProvisionerTest : BasePlatformTestCase() {
    private lateinit var rootDir: Path
    private val platform = LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64)

    override fun setUp() {
        super.setUp()
        rootDir = createTempDirectory("lunar-provision-test")
    }

    // --- fakes ---

    /**
     * A single strategy (unique [id], as in production) covering several kinds. Each kind maps to a
     * fixed identity hash and an optional `onProvision` side effect (to simulate failure /
     * cancellation); `provision` writes an executable `bin/<kind>` binary and counts its calls.
     */
    private class FakeStrategy(
        override val id: String,
        private val kinds: Map<String, KindBehavior>,
    ) : LuaProvisioningStrategy {
        data class KindBehavior(val hash: String, val onProvision: (() -> Unit)? = null)

        val provisionCalls = mutableMapOf<String, Int>()

        override fun supports(item: LuaProvisionItem, platform: LuaHostPlatform, feed: LuaToolchainFeed) =
            kinds.containsKey(item.kindId)

        override fun identityHash(context: LuaProvisionContext, item: LuaProvisionItem): String =
            behavior(item).hash

        override fun provision(context: LuaProvisionContext, item: LuaProvisionItem): LuaProvisionedComponent {
            provisionCalls.merge(item.kindId, 1, Int::plus)
            val behavior = behavior(item)
            behavior.onProvision?.invoke()
            val binary = context.rootDir.resolve("bin/${item.kindId}").also(::createExecutable)
            return LuaProvisionedComponent(item.kindId, item.versionSpec, id, binary, emptyList(), behavior.hash)
        }

        private fun behavior(item: LuaProvisionItem): KindBehavior =
            kinds[item.kindId] ?: error("FakeStrategy $id does not handle ${item.kindId}")
    }

    private fun release(vararg kinds: Pair<String, FakeStrategy.KindBehavior>): FakeStrategy =
        FakeStrategy("release-binary", kinds.toMap())

    private class SpySink : LuaProvisionResultSink {
        var registered: LuaProvisionResult? = null

        override fun register(project: Project, result: LuaProvisionResult) {
            registered = result
        }
    }

    private class SpyNotifier : LuaProvisionNotifier {
        val messages = mutableListOf<Pair<String, NotificationType>>()

        override fun notify(project: Project, message: String, type: NotificationType) {
            messages += message to type
        }
    }

    /** A feed whose every (kind, version) is provisionable (has a rock) so `resolveVersion` succeeds. */
    private fun feed(vararg kindToVersions: Pair<String, List<String>>): LuaToolchainFeed {
        val rock = LuaFeedRock("r", null, "tool", needsCToolchain = false)
        val kinds = kindToVersions.associate { (kind, versions) ->
            kind to LuaFeedKind(emptyMap(), versions.map { LuaFeedVersion(it, null, null, emptyList(), rock) })
        }
        return LuaToolchainFeed(1, kinds)
    }

    private fun job(vararg items: LuaProvisionItem): LuaProvisionJob =
        LuaProvisionJob(project, LuaProvisionRequest("env", rootDir.toString(), items.toList()), EmptyProgressIndicator())

    private fun engine(
        strategies: List<LuaProvisioningStrategy>,
        sink: LuaProvisionResultSink = SpySink(),
        notifier: LuaProvisionNotifier = SpyNotifier(),
    ): LuaProvisionEngine = LuaProvisionEngine(strategies, sink, notifier)

    // --- TC 2: per-directory serialization refusal ---

    fun testSecondConcurrentReserveIsRefusedAndFirstUnaffected() {
        val provisioner = LuaToolProvisioner()
        val canonical = rootDir.toString()
        assertTrue("first reserve succeeds", provisioner.tryReserve(canonical))
        assertFalse("second reserve for same dir refused", provisioner.tryReserve(canonical))
        provisioner.release(canonical)
        assertTrue("reserve succeeds again after release", provisioner.tryReserve(canonical))
    }

    fun testDistinctDirsReserveIndependently() {
        val provisioner = LuaToolProvisioner()
        assertTrue(provisioner.tryReserve("/env/a"))
        assertTrue("a different dir is not blocked by /env/a", provisioner.tryReserve("/env/b"))
    }

    // --- TC 11: identical re-run → all skip, env re-activated ---

    fun testIdenticalRerunSkipsAllAndReactivates() {
        val strategy = release("luacheck" to FakeStrategy.KindBehavior("hash-luacheck"))
        val theFeed = feed("luacheck" to listOf("1.0"))
        val item = LuaProvisionItem("luacheck", "1.0")

        engine(listOf(strategy)).run(job(item), platform, theFeed)
        assertEquals("first run provisions", 1, strategy.provisionCalls["luacheck"])

        val rerunSink = SpySink()
        val rerunNotifier = SpyNotifier()
        engine(listOf(strategy), rerunSink, rerunNotifier).run(job(item), platform, theFeed)

        assertEquals("second run makes zero further strategy calls", 1, strategy.provisionCalls["luacheck"])
        assertNotNull("env re-activated on skip-all run", rerunSink.registered)
        assertTrue(rerunNotifier.messages.any { it.first.contains("already up to date") })
    }

    // --- TC 12: changed runtime spec re-runs only the changed component ---

    fun testChangedRuntimeSpecRebuildsOnlyThatComponent() {
        val firstFeed = feed("lua" to listOf("5.4", "5.5"), "luarocks" to listOf("3.0"))
        val first = release(
            "lua" to FakeStrategy.KindBehavior("lua-hash-A"),
            "luarocks" to FakeStrategy.KindBehavior("rocks-hash"),
        )
        engine(listOf(first)).run(
            job(LuaProvisionItem("lua", "5.4"), LuaProvisionItem("luarocks", "3.0")), platform, firstFeed,
        )
        assertEquals(1, first.provisionCalls["lua"])
        assertEquals(1, first.provisionCalls["luarocks"])

        // Re-run: lua's hash changed (spec 5.4 → 5.5), luarocks' hash unchanged.
        val second = release(
            "lua" to FakeStrategy.KindBehavior("lua-hash-B"),
            "luarocks" to FakeStrategy.KindBehavior("rocks-hash"),
        )
        engine(listOf(second)).run(
            job(LuaProvisionItem("lua", "5.5"), LuaProvisionItem("luarocks", "3.0")), platform, firstFeed,
        )

        assertEquals("changed lua rebuilt", 1, second.provisionCalls["lua"])
        assertNull("unchanged luarocks skipped (never provisioned in 2nd run)", second.provisionCalls["luarocks"])
    }

    // --- TC 14: mid-pipeline failure → ERROR, manifest keeps completed, nothing registered ---

    fun testMidPipelineFailureRegistersNothingButKeepsCompleted() {
        val strategy = release(
            "lua" to FakeStrategy.KindBehavior("ok-hash"),
            "luarocks" to FakeStrategy.KindBehavior("boom", onProvision = { throw LuaProvisionException("build blew up") }),
        )
        val sink = SpySink()
        val notifier = SpyNotifier()
        val theFeed = feed("lua" to listOf("5.4"), "luarocks" to listOf("3.0"))

        engine(listOf(strategy), sink, notifier).run(
            job(LuaProvisionItem("lua", "5.4"), LuaProvisionItem("luarocks", "3.0")), platform, theFeed,
        )

        assertNull("nothing registered on failure", sink.registered)
        assertTrue("ERROR balloon raised", notifier.messages.any { it.second == NotificationType.ERROR })
        val manifest = LuaEnvManifest.read(rootDir)
        assertNotNull("manifest exists", manifest)
        assertTrue("completed lua kept in manifest", manifest!!.components.containsKey("lua"))
        assertFalse("failed luarocks not recorded", manifest.components.containsKey("luarocks"))
    }

    // --- TC 19: cancellation keeps completed components, registers nothing ---

    fun testCancellationKeepsCompletedComponentsAndRegistersNothing() {
        val strategy = release(
            "lua" to FakeStrategy.KindBehavior("ok-hash"),
            "luarocks" to FakeStrategy.KindBehavior("cxl", onProvision = { throw ProcessCanceledException() }),
        )
        val sink = SpySink()
        val notifier = SpyNotifier()
        val theFeed = feed("lua" to listOf("5.4"), "luarocks" to listOf("3.0"))

        try {
            engine(listOf(strategy), sink, notifier).run(
                job(LuaProvisionItem("lua", "5.4"), LuaProvisionItem("luarocks", "3.0")), platform, theFeed,
            )
            fail("cancellation should propagate")
        } catch (_: ProcessCanceledException) {
            // expected
        }

        assertNull("nothing registered on cancellation", sink.registered)
        val manifest = LuaEnvManifest.read(rootDir)
        assertTrue("completed lua kept after cancellation", manifest!!.components.containsKey("lua"))
        assertTrue("cancellation balloon shown", notifier.messages.any { it.first.contains("cancelled") })
    }

    private companion object {
        fun createExecutable(path: Path) {
            path.parent?.createDirectories()
            path.writeText("#!/bin/sh\n")
            runCatching { path.toFile().setExecutable(true) }
        }
    }
}
