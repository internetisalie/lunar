package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainChange
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.util.concurrent.atomic.AtomicInteger

/**
 * BUG-534: when a project's environment set changes, Lunar must ask the platform to re-evaluate
 * [LuaEnvStatusBarWidgetFactory.isAvailable]. The platform evaluates it once at frame creation, so a
 * project whose first environment arrives after opening (New Project → LuaRocks) never showed the
 * widget. A light fixture has no status bar, so the re-check request itself is what is asserted.
 */
class LuaEnvWidgetAvailabilityTest : ToolchainSettingsTestCase() {
    private val rechecks = AtomicInteger()

    override fun setUp() {
        super.setUp()
        val recorder =
            object : LuaEnvWidgetAvailability {
                override fun recheck() {
                    rechecks.incrementAndGet()
                }
            }
        project.replaceService(LuaEnvWidgetAvailability::class.java, recorder, testRootDisposable)
    }

    private fun publish(event: LuaToolchainEvent) {
        ApplicationManager
            .getApplication()
            .messageBus
            .syncPublisher(LuaToolchainListener.TOPIC)
            .toolchainChanged(event)
    }

    fun testFirstEnvironmentRequestsARecheck() {
        settings.upsertEnvironmentAndActivate(LuaEnvironmentState(name = "lua 5.4", rootDir = "/p/.lua"))

        assertEquals("adding the first environment re-checks widget availability", 1, rechecks.get())
    }

    fun testRemovingTheEnvironmentRequestsARecheck() {
        val environment =
            settings.upsertEnvironmentAndActivate(LuaEnvironmentState(name = "lua 5.4", rootDir = "/p/.lua"))
        rechecks.set(0)

        settings.removeEnvironment(environment.id, deleteDir = false)

        assertEquals("removing the last environment re-checks widget availability", 1, rechecks.get())
    }

    fun testUnrelatedChangesDoNotRecheck() {
        settings.setKindOption("luacheck.args", "--std max")
        publish(LuaToolchainEvent(LuaToolchainChange.ENVIRONMENT_ADDED, project = null, environmentId = "E"))

        assertEquals("an unrelated change, or an event with no project, does not re-check", 0, rechecks.get())
    }
}
