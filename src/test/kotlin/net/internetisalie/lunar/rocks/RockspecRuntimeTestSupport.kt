package net.internetisalie.lunar.rocks

import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import java.io.File
import java.util.UUID

/**
 * TOOLING-05 Phase 3: [RockspecBridge.read] now resolves the runtime via `LuaToolResolver` instead
 * of the former hardcoded `"lua"` default. Tests that need the bridge to actually launch and parse a
 * rockspec must register a real Lua runtime (the builder wires `~/bin/lua`, mirroring TOOLING-01
 * PATH discovery). Returns `true` when a usable runtime was registered + globally bound.
 */
object RockspecRuntimeTestSupport {

    fun registerRealLuaRuntime(project: Project): Boolean {
        val luaBinary = File(System.getProperty("user.home"), "bin/lua")
        if (!luaBinary.canExecute()) return false

        val registry = LuaToolchainRegistry.getInstance()
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = "lua",
            path = luaBinary.absolutePath,
            version = "5.4",
            luaVersion = "5.4",
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = 1L, reason = null),
        )
        registry.registerProvisioned(tool)
        registry.setGlobalBinding("lua", tool.id)
        return true
    }

    /** Clears the app registry + project toolchain state so a seeded runtime never leaks. */
    fun reset(project: Project) {
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
    }
}
