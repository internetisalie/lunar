package net.internetisalie.lunar.definitions

import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.provision.LuaProvisionException
import net.internetisalie.lunar.toolchain.provision.LuaProvisionNotifier
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * TARGET-08-06/-07/-08. Exercises the settings page's behaviour without Swing —
 * `LuaDefinitionLibrariesConfigurable` is a shell over this, so these assertions cover what the
 * page actually does.
 */
class LuaDefinitionLibraryEnablerTest : BasePlatformTestCase() {

    private lateinit var cacheRoot: Path

    private val settings get() = LuaProjectSettings.getInstance(project)

    override fun setUp() {
        super.setUp()
        cacheRoot = Files.createTempDirectory("lunar-defs-enabler")
        // The cached-state check is VFS-based and EDT-safe by design, so the fixture must make the
        // temp tree visible to the VFS — writing files with java.io alone is invisible to it.
        VfsRootAccess.allowRootAccess(testRootDisposable, cacheRoot.toString())
        settings.state.enabledDefinitionLibraries = mutableListOf()
    }

    override fun tearDown() {
        try {
            settings.state.enabledDefinitionLibraries = mutableListOf()
        } finally {
            super.tearDown()
        }
    }

    private val offlineSource = LuaDefinitionArchiveSource { _, _ -> throw LuaProvisionException("offline") }

    private fun enabler(source: LuaDefinitionArchiveSource = offlineSource) =
        LuaDefinitionLibraryEnabler(project, LuaDefinitionLibraryFetcher(cacheRoot, source))

    private fun seedCache(id: String) {
        val entry = checkNotNull(LuaDefinitionCatalogLoader.load().entry(id))
        val dir = File(cacheRoot.toFile(), "${entry.id}-${entry.version}")
        dir.mkdirs()
        File(dir, "$id.lua").writeText("---@meta\n")
        // Without this the VFS never learns the files exist and `cachedRoot` reports nothing.
        VfsUtil.markDirtyAndRefresh(false, true, true, VfsUtil.findFileByIoFile(cacheRoot.toFile(), true))
    }

    /** TARGET-08-06/-08: every catalog entry is offered, with the attribution data the UI shows. */
    fun testRowsCoverTheWholeCatalogWithAttribution() {
        val rows = enabler().rows()
        assertEquals(LuaDefinitionCatalogLoader.load().libraries.size, rows.size)
        rows.forEach {
            assertFalse("${it.entry.id}: nothing enabled yet", it.enabled)
            assertTrue("${it.entry.id}: license must be shown", it.entry.license.isNotBlank())
            assertTrue("${it.entry.id}: attribution must be shown", it.entry.attributionUrl.isNotBlank())
        }
    }

    fun testRowsReflectEnabledState() {
        settings.state.enabledDefinitionLibraries = mutableListOf("busted")
        val rows = enabler().rows().associateBy { it.entry.id }
        assertTrue("busted is enabled", rows.getValue("busted").enabled)
        assertFalse("luassert is not enabled", rows.getValue("luassert").enabled)
    }

    /**
     * BUG-396: cache state is reported by a separate, disk-touching call so the settings page can
     * do it off the EDT. `rows()` must NOT report it — a `rows()` that quietly stats the cache is
     * exactly the defect, and it only ever surfaced in a running IDE.
     */
    fun testRowsDoNotReportFetchedStateAndFetchedIdsDoes() {
        seedCache("luassert")
        assertTrue("rows() must not claim anything is fetched", enabler().rows().none { it.fetched })
        assertEquals(setOf("luassert"), enabler().fetchedIds())
    }

    /** Dependencies must be fetched too, or busted lands without luassert and half-resolves. */
    fun testMissingEntriesIncludesDependencies() {
        val missing = enabler().missingEntries(listOf("busted")).map { it.id }
        assertEquals(listOf("busted", "luassert"), missing)
    }

    fun testAlreadyCachedNeedsNoFetch() {
        seedCache("busted")
        seedCache("luassert")
        assertEmpty(enabler().missingEntries(listOf("busted")))
    }

    /**
     * TARGET-08-07, through `apply` — the actual entry point.
     *
     * An earlier version of this asserted its own write: it set the enable list by hand and then
     * checked the value came back, so stripping failed ids inside `apply` would have left it green.
     * This drives `apply` and asserts the persisted state afterwards, which is the real contract:
     * a library ticked while offline stays ticked and retries next time.
     */
    fun testApplyKeepsIdsEnabledWhenTheFetchFails() {
        enabler().apply(listOf("busted"))
        assertEquals(
            "a failed fetch must not silently drop the user's choice",
            listOf("busted"),
            settings.enabledDefinitionLibraries,
        )
    }

    /** `apply` reports the failure to the user — TARGET-08-07's user-visible half. */
    fun testApplyBalloonsOnFailure() {
        val notifier = RecordingNotifier()
        LuaDefinitionLibraryEnabler(project, LuaDefinitionLibraryFetcher(cacheRoot, offlineSource), notifier)
            .apply(listOf("busted"))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("exactly one balloon per failed apply", 1, notifier.messages.size)
        val message = notifier.messages.single()
        assertTrue("the balloon must name what failed, got: $message", message.contains("busted"))
        assertTrue("the balloon must say they stay enabled, got: $message", message.contains("stay"))
        assertEquals(NotificationType.ERROR, notifier.types.single())
    }

    /** A fully-cached apply must not balloon — success is silent. */
    fun testApplyDoesNotBalloonWhenEverythingIsCached() {
        seedCache("busted")
        seedCache("luassert")
        val notifier = RecordingNotifier()
        LuaDefinitionLibraryEnabler(project, LuaDefinitionLibraryFetcher(cacheRoot, offlineSource), notifier)
            .apply(listOf("busted"))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEmpty(notifier.messages)
    }

    /** Captures balloons instead of posting them — the seam design §3.2 exists for. */
    private class RecordingNotifier : LuaProvisionNotifier {
        val messages = mutableListOf<String>()
        val types = mutableListOf<NotificationType>()

        override fun notify(project: Project, message: String, type: NotificationType) {
            messages += message
            types += type
        }
    }

    /** A partial outage reports only what actually failed, not the whole batch. */
    fun testOnlyFailedIdsAreReported() {
        seedCache("luassert")
        val enabler = enabler()
        val failures = enabler.fetchAll(enabler.missingEntries(listOf("busted")), indicator())
        assertEquals(listOf("busted"), failures)
    }

    private fun indicator(): ProgressIndicator = EmptyProgressIndicator()
}
