package net.internetisalie.lunar.rocks.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Headless model tests for [LuaRocksBrowserModel] (design §2.5 / §3.3 / §3.4). A fake backend
 * supplies deterministic CLI outcomes and lets the test drive the background/EDT hops manually, so
 * transitions, in-place refresh, and the monotonic-requestId staleness guard are verified with no
 * real `luarocks` and no platform.
 *
 * Covers TC-ROCKS-16-07 (error on non-zero), TC-ROCKS-16-08 (unresolved binary), TC-ROCKS-16-09
 * (in-place install refresh + cache invalidate + single row event), plus the staleness drop (§6).
 */
class LuaRocksBrowserModelTest {

    private val tree: Path = Path.of("/proj/lua_modules")

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class RecordingListener : LuaRocksBrowserModel.Listener {
        val states = mutableListOf<BrowserState>()
        val rowChanges = mutableListOf<Int>()
        override fun onState(state: BrowserState) { states += state }
        override fun onRowChanged(index: Int) { rowChanges += index }
    }

    private class FakeBackend(
        var treeRoot: Path?,
        var searchResult: () -> List<LuaRockPackage>,
        var installedResult: () -> List<InstalledRockRow> = { emptyList() },
    ) : LuaRocksBrowserBackend {
        val backgroundTasks = ArrayDeque<() -> Unit>()
        val edtActions = ArrayDeque<() -> Unit>()
        override fun resolveTree(): Path? = treeRoot
        override fun search(query: String, treeRoot: Path?): List<LuaRockPackage> = searchResult()
        override fun listInstalled(treeRoot: Path): List<InstalledRockRow> = installedResult()
        override fun runInBackground(task: () -> Unit) { backgroundTasks += task }
        override fun onEdt(action: () -> Unit) { edtActions += action }
        fun drain() {
            while (backgroundTasks.isNotEmpty()) backgroundTasks.removeFirst().invoke()
            while (edtActions.isNotEmpty()) edtActions.removeFirst().invoke()
        }
    }

    private fun pkg(name: String, version: String = "1.0-1", installed: Boolean = false) =
        LuaRockPackage(name, version, "rockspec", "https://luarocks.org", "", installed)

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `TC-ROCKS-16-07 non-zero CLI exit transitions to Error not empty Results`() {
        val backend = FakeBackend(tree, { throw BrowserCliError("network unreachable") })
        val listener = RecordingListener()
        LuaRocksBrowserModel(backend, listener).runMarketplaceSearch("inspect")
        backend.drain()

        assertEquals(BrowserState.Loading, listener.states.first())
        val terminal = listener.states.last()
        assertTrue("expected Error, got $terminal", terminal is BrowserState.Error)
        assertEquals("network unreachable", (terminal as BrowserState.Error).message)
    }

    @Test
    fun `TC-ROCKS-16-08 unresolved binary yields the not-configured error`() {
        val backend = FakeBackend(tree, { throw BrowserCliError(BrowserCliError.LUAROCKS_NOT_CONFIGURED) })
        val listener = RecordingListener()
        LuaRocksBrowserModel(backend, listener).runMarketplaceSearch("inspect")
        backend.drain()

        val terminal = listener.states.last() as BrowserState.Error
        assertEquals(BrowserCliError.LUAROCKS_NOT_CONFIGURED, terminal.message)
    }

    @Test
    fun `TC-ROCKS-16-09 install flips the row in place with a single row event`() {
        LuaRocksSearchCache.put("inspect", null, listOf(pkg("inspect")), 1L)
        val backend = FakeBackend(tree, { listOf(pkg("inspect", installed = false)) })
        val listener = RecordingListener()
        val model = LuaRocksBrowserModel(backend, listener)
        model.runMarketplaceSearch("inspect")
        backend.drain()

        model.onInstallSucceeded("inspect")

        assertEquals(listOf(0), listener.rowChanges)
        assertTrue(model.currentRows[0].installed)
        assertEquals(null, LuaRocksSearchCache.get("inspect", null, 2L))
    }

    @Test
    fun `blank query posts Idle without a background task`() {
        val backend = FakeBackend(tree, { emptyList() })
        val listener = RecordingListener()
        LuaRocksBrowserModel(backend, listener).runMarketplaceSearch("   ")

        assertEquals(listOf(BrowserState.Idle), listener.states)
        assertTrue(backend.backgroundTasks.isEmpty())
    }

    @Test
    fun `loadInstalled with no tree posts NoTree`() {
        val backend = FakeBackend(treeRoot = null, searchResult = { emptyList() })
        val listener = RecordingListener()
        LuaRocksBrowserModel(backend, listener).loadInstalled()

        assertEquals(listOf(BrowserState.NoTree), listener.states)
    }

    @Test
    fun `a stale search result is dropped in favor of the newer one`() {
        val backend = FakeBackend(tree, { listOf(pkg("first")) })
        val listener = RecordingListener()
        val model = LuaRocksBrowserModel(backend, listener)

        // First search: run its background fetch (queues an EDT publish) but do NOT flush the EDT yet.
        model.runMarketplaceSearch("first")
        backend.backgroundTasks.removeFirst().invoke()

        // Second search supersedes it before the first publishes.
        backend.searchResult = { listOf(pkg("second")) }
        model.runMarketplaceSearch("second")
        backend.backgroundTasks.removeFirst().invoke()

        // Now flush both queued EDT publishes in order: the FIRST is stale and must be dropped.
        while (backend.edtActions.isNotEmpty()) backend.edtActions.removeFirst().invoke()

        val results = listener.states.filterIsInstance<BrowserState.Results>()
        assertEquals(1, results.size)
        assertEquals("second", results.single().rows.single().pkg.name)
    }
}
