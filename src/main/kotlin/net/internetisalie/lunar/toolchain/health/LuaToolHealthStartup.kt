package net.internetisalie.lunar.toolchain.health

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Project-open bootstrap (design §2.3): starts the [LuaToolHealthMonitor] (VFS listener + topic
 * subscription) and runs an initial revalidation pass. Registered declaratively in `plugin.xml` by
 * TOOLING-07 Phase 3 (this phase leaves it unregistered by design).
 *
 * **Not in unit-test mode (BUG-422).** [LuaToolHealthMonitor.revalidateAll] re-probes every tool in
 * the *application-level* registry against the real filesystem and writes the results back. Tests
 * seed synthetic tools at paths that do not exist and assert their health directly — the fixtures
 * say so explicitly ("resolution never touches disk, so a nonexistent tool path is fine") — so a
 * pass landing mid-test rewrites that health to `Binary missing`, and every resolver tier then
 * rejects the tool the test just bound. Because the registry is shared across the whole suite, one
 * project open could strip the tools of whatever test happened to be running: the symptom was two
 * tests that failed roughly once per full suite and passed on an immediate re-run, whose odds moved
 * when unrelated test classes were added.
 *
 * The monitor is not left untested by this. Its behaviour is driven deterministically through the
 * `@TestOnly` seams it already exposes for the purpose — `revalidateNow`, `rebuildWatchSetNow` and
 * `prepareChangeNow` — so the automatic pass contributed no coverage here, only interference.
 */
class LuaToolHealthStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return
        val monitor = LuaToolHealthMonitor.getInstance(project)
        monitor.start()
        monitor.revalidateAll()
    }
}
