package net.internetisalie.lunar.toolchain.health

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.probe.LuaToolProbe
import net.internetisalie.lunar.toolchain.probe.LuaToolProbeResult
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.util.UUID

@RunWith(JUnit4::class)
class LuaToolHealthMonitorTest : BasePlatformTestCase() {

    private lateinit var probeStub: StubProbe

    override fun setUp() {
        super.setUp()
        probeStub = StubProbe()
        ApplicationManager.getApplication().replaceService(LuaToolProbe::class.java, probeStub, testRootDisposable)
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
    }

    override fun tearDown() {
        try {
            LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
            LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
        } finally {
            super.tearDown()
        }
    }

    private class StubProbe : LuaToolProbe {
        override fun probe(kind: net.internetisalie.lunar.toolchain.model.LuaToolKind, binaryPath: java.nio.file.Path) =
            LuaToolProbeResult(ok = true, version = "1.0.0", luaVersion = null, runtime = null, failure = null)
    }

    private fun registerUsableBinary(kindId: String, environmentId: String? = null): Pair<LuaRegisteredTool, File> {
        val binary = File.createTempFile("lunar-$kindId", "").also {
            it.writeText("#!/bin/sh\n")
            it.setExecutable(true)
            it.deleteOnExit()
        }
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = kindId,
            path = binary.absolutePath,
            version = "1.0.0",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = environmentId,
            health = LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = binary.lastModified(), reason = "OK 1.0.0")
        )
        LuaToolchainRegistry.getInstance().registerProvisioned(tool)
        return tool to binary
    }

    private fun captureBalloons(): MutableList<Notification> {
        val balloons = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == "notification.group.lunar.tools") balloons.add(notification)
                }
            }
        )
        return balloons
    }

    private fun captureTopicEvents(): MutableList<LuaToolchainEvent> {
        val events = mutableListOf<LuaToolchainEvent>()
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(
            LuaToolchainListener.TOPIC,
            object : LuaToolchainListener {
                override fun toolchainChanged(event: LuaToolchainEvent) {
                    synchronized(events) { events.add(event) }
                }
            }
        )
        return events
    }

    private fun revalidate(monitor: LuaToolHealthMonitor) {
        monitor.revalidateNow(EmptyProgressIndicator())
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    // TC-TOOLING-07-06: one balloon per usable→unusable transition; second pass fires none.
    @Test
    fun testTransitionDedup_oneBalloonPerTransition() {
        val (_, binary) = registerUsableBinary("luacheck")
        val monitor = LuaToolHealthMonitor.getInstance(project)
        val balloons = captureBalloons()

        assertTrue(binary.delete())
        revalidate(monitor)
        assertEquals("first pass fires exactly one balloon", 1, balloons.size)
        assertTrue(balloons.first().content.contains("luacheck"))

        revalidate(monitor)
        assertEquals("persistently-broken tool re-notifies nothing", 1, balloons.size)
    }

    // TC-TOOLING-07-06: recover then break again fires a second balloon.
    @Test
    fun testTransitionDedup_recoverThenBreakNotifiesAgain() {
        val (tool, binary) = registerUsableBinary("luacheck")
        val monitor = LuaToolHealthMonitor.getInstance(project)
        val balloons = captureBalloons()

        assertTrue(binary.delete())
        revalidate(monitor)
        assertEquals(1, balloons.size)

        binary.writeText("#!/bin/sh\n")
        binary.setExecutable(true)
        binary.deleteOnExit()
        // Re-arm the mtime gate baseline by writing fresh usable health back.
        LuaToolchainRegistry.getInstance().updateToolCheck(
            tool.id,
            LuaToolHealth(true, true, true, binary.lastModified(), "OK 1.0.0"),
            "1.0.0",
            null,
            null
        )
        revalidate(monitor)
        assertEquals("recovery fires no broken balloon", 1, balloons.size)

        assertTrue(binary.delete())
        revalidate(monitor)
        assertEquals("break-after-recover fires a second balloon", 2, balloons.size)
    }

    // TC-TOOLING-07-07: env-root deletion — member tool reason overridden; one balloon across two passes.
    @Test
    fun testEnvironmentRootDeleted_oneBalloonAndReasonOverride() {
        val envRoot = java.nio.file.Files.createTempDirectory("lunar-env").toFile().also { it.deleteOnExit() }
        val (tool, binary) = registerUsableBinary("lua")
        assertTrue(binary.delete())
        val env = LuaEnvironmentState(
            id = UUID.randomUUID().toString(),
            name = "lua54",
            rootDir = envRoot.absolutePath,
            toolIds = mutableListOf(tool.id)
        )
        LuaToolchainProjectSettings.getInstance(project).upsertEnvironment(env)
        // re-associate the tool with the environment
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainRegistry.getInstance().registerProvisioned(tool.copy(environmentId = env.id))

        val monitor = LuaToolHealthMonitor.getInstance(project)
        val balloons = captureBalloons()

        assertTrue(envRoot.deleteRecursively())
        revalidate(monitor)
        revalidate(monitor)

        val reloaded = LuaToolchainRegistry.getInstance().tools().first { it.id == tool.id }
        assertFalse(reloaded.health.fileExists)
        assertEquals("Environment root missing: ${env.rootDir}", reloaded.health.reason)
        val envBalloons = balloons.filter { it.content.contains("was deleted from disk") }
        assertEquals("exactly one env balloon across both passes", 1, envBalloons.size)
        assertTrue(envBalloons.first().content.contains("lua54"))
    }

    // MAINT-32-03 TC-06: prepareChange reads the pre-computed @Volatile watchSet, not a fresh
    // buildWatchSet() — so no File.canonicalPath I/O runs on the listener path. Proven by mutating
    // the registry AFTER the rebuild: a fresh build would no longer match, the cached field still does.
    @Test
    fun testPrepareChangeUsesPrecomputedWatchSetWithoutIo() {
        val (tool, binary) = registerUsableBinary("lua")
        val monitor = LuaToolHealthMonitor.getInstance(project)
        monitor.rebuildWatchSetNow()

        val virtualBinary = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByIoFile(binary) ?: error("binary not in VFS")
        val deleteEvent = com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent(this, virtualBinary)

        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        assertTrue(
            "prepareChange must match via the cached watchSet, not a recomputed one",
            monitor.prepareChangeNow(listOf(deleteEvent)),
        )
        assertTrue(binary.delete())
    }

    // TC-TOOLING-07-09: registry reads are pure (no event); revalidation write fires exactly one, dedups a repeat.
    @Test
    fun testWritePathPurityAndTopicFiring() {
        val (_, binary) = registerUsableBinary("luacheck")
        val monitor = LuaToolHealthMonitor.getInstance(project)
        assertTrue(binary.delete())

        val readEvents = captureTopicEvents()
        repeat(3) { LuaToolchainRegistry.getInstance().tools() }
        synchronized(readEvents) { assertEquals("pure reads fire no events", 0, readEvents.size) }

        val writeEvents = captureTopicEvents()
        revalidate(monitor)
        synchronized(writeEvents) { assertEquals("first write fires exactly one topic event", 1, writeEvents.size) }

        revalidate(monitor)
        synchronized(writeEvents) { assertEquals("unchanged health fires no further event", 1, writeEvents.size) }
    }
}
