package net.internetisalie.lunar.rocks.init

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.provision.LuaProvisionItem
import net.internetisalie.lunar.toolchain.provision.LuaProvisionRequest
import net.internetisalie.lunar.toolchain.provision.LuaToolProvisioner
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry

/**
 * Applies the New Project wizard's runtime choice to a freshly generated project (TOOLING-05 §2.8).
 *
 * Splits the work so the pure settings mutation is unit-testable independently of the background
 * provisioning:
 * - [applySettings] sets the project [Target] and, when NOT provisioning, binds the chosen existing
 *   runtime tool via a TOOLING-02 project RUNTIME binding.
 * - [scheduleProvision] activates the wizard-provisioned environment and queues a TOOLING-04
 *   [LuaToolProvisioner] request (kind `lua`/`luajit` + `luarocks`) once the project has opened, so it
 *   never races project initialization.
 */
object LuaRocksInterpreterInitializer {

    /** Directory (relative to the project base) of the wizard-provisioned isolated environment. */
    const val ENV_DIR_NAME = ".lua"

    private const val LUAROCKS_KIND_ID = "luarocks"
    private const val LUAROCKS_LATEST = "latest"

    fun applySettings(project: Project, settings: LuaRocksProjectSettings) {
        val projectState = LuaProjectSettings.getInstance(project).state
        projectState.setTarget(targetFor(settings))
        if (!settings.provisionEnvironment) {
            bindExplicitRuntime(project, settings.interpreterPath.trim())
        }
    }

    fun scheduleProvision(project: Project, baseDirPath: String, settings: LuaRocksProjectSettings) {
        if (!settings.provisionEnvironment) return
        val rootDir = "$baseDirPath/$ENV_DIR_NAME"
        val environmentName = "${settings.kindId} ${settings.luaVersion}"
        val request = LuaProvisionRequest(
            environmentName = environmentName,
            rootDir = rootDir,
            items = listOf(
                LuaProvisionItem(settings.kindId, settings.luaVersion),
                LuaProvisionItem(LUAROCKS_KIND_ID, LUAROCKS_LATEST),
            ),
        )
        LuaToolchainProjectSettings.getInstance(project)
            .upsertEnvironmentAndActivate(LuaEnvironmentState(name = environmentName, rootDir = rootDir))
        StartupManager.getInstance(project).runAfterOpened {
            LuaToolProvisioner.getInstance().provision(project, request)
        }
    }

    /** Binds the registered runtime tool at [interpreterPath] as the project's RUNTIME kind (§2.8). */
    private fun bindExplicitRuntime(project: Project, interpreterPath: String) {
        if (interpreterPath.isBlank()) return
        val tool = LuaToolchainRegistry.getInstance().findByPath(interpreterPath) ?: return
        LuaToolchainProjectSettings.getInstance(project).setBinding(WizardRuntimeKinds.LUA, tool.id)
    }

    /** Maps the wizard's chosen kind + version to a project [Target] (design §2.8). */
    private fun targetFor(settings: LuaRocksProjectSettings): Target {
        val platform =
            if (settings.kindId == WizardRuntimeKinds.LUAJIT) LuaPlatform.LUAJIT else LuaPlatform.STANDARD
        return PlatformVersionRegistry.resolveTarget(platform, normalizedVersionLabel(settings.luaVersion))
    }

    private fun normalizedVersionLabel(luaVersion: String): String =
        Regex("""(\d+\.\d+)""").find(luaVersion)?.groupValues?.get(1) ?: luaVersion
}
