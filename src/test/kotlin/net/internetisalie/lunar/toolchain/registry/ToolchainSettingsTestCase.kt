package net.internetisalie.lunar.toolchain.registry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import java.util.UUID

/**
 * Base for TOOLING-02 mutator/event tests. The light-test fixture shares one in-memory app and
 * project across methods/classes, so the app-level [LuaToolchainRegistry] and the project-level
 * [LuaToolchainProjectSettings] must be reset in both [setUp] and [tearDown] (pattern:
 * EnvSettingsTestCase). Tools are seeded directly into the registry state with explicit health, so
 * no disk probing runs.
 */
abstract class ToolchainSettingsTestCase : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        resetState()
    }

    override fun tearDown() {
        try {
            resetState()
        } finally {
            super.tearDown()
        }
    }

    private fun resetState() {
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
    }

    protected val registry: LuaToolchainRegistry
        get() = LuaToolchainRegistry.getInstance()

    protected val settings: LuaToolchainProjectSettings
        get() = LuaToolchainProjectSettings.getInstance(project)

    protected fun seedTool(
        kindId: String,
        usable: Boolean = true,
        environmentId: String? = null,
        runtime: LuaRuntimeInfo? = null
    ): LuaRegisteredTool {
        val id = UUID.randomUUID().toString()
        val health = LuaToolHealth(
            fileExists = usable,
            executable = usable,
            probeOk = usable,
            probedAtMtime = 1L,
            reason = null
        )
        val model = LuaRegisteredTool(
            id = id,
            kindId = kindId,
            path = "/seed/$kindId/$id",
            version = "1.0.0",
            luaVersion = null,
            runtime = runtime,
            origin = Origin.MANUAL,
            environmentId = environmentId,
            health = health
        )
        registry.registerProvisioned(model)
        return model
    }

    protected fun runtimeInfo(
        platform: LuaPlatform,
        version: String,
        level: LuaLanguageLevel
    ): LuaRuntimeInfo = LuaRuntimeInfo(
        product = platform.name,
        version = version,
        languageLevel = level,
        platform = platform,
        banner = ""
    )

    protected fun recordEvents(): MutableList<LuaToolchainEvent> {
        val events = mutableListOf<LuaToolchainEvent>()
        val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(
            LuaToolchainListener.TOPIC,
            object : LuaToolchainListener {
                override fun toolchainChanged(event: LuaToolchainEvent) {
                    synchronized(events) { events.add(event) }
                }
            }
        )
        return events
    }
}
