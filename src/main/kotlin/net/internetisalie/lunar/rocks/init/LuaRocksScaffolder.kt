package net.internetisalie.lunar.rocks.init

import com.intellij.execution.RunManager
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import net.internetisalie.lunar.run.LuaRunConfiguration
import net.internetisalie.lunar.run.LuaRunConfigurationType

/**
 * Deterministically scaffolds a LuaRocks project under [baseDir].
 *
 * Must be called inside a WriteAction (VFS writes). The optional `luarocks init`
 * invocation (step 9 of the design) is omitted here — it requires process I/O
 * outside a WriteAction and can be added as a follow-up background task.
 */
object LuaRocksScaffolder {

    fun scaffold(project: Project, baseDir: VirtualFile, s: LuaRocksProjectSettings) {
        scaffoldSingleRock(project, baseDir, s)
    }

    // ------------------------------------------------------------------ single rock

    private fun scaffoldSingleRock(project: Project, baseDir: VirtualFile, s: LuaRocksProjectSettings) {
        val name = s.name.ifBlank { "my-project" }

        // 1. rockspec
        writeText(baseDir, "$name-scm-1.rockspec", LuaRocksTemplates.rockspec(name, s.type))

        // 2. src/ + main module
        val srcDir = baseDir.createChildDirectory(this, "src")
        val mainFileName = if (s.type == RockType.LIBRARY) "$name.lua" else "main.lua"
        writeText(srcDir, mainFileName, LuaRocksTemplates.mainModule(name, s.type))

        // 3. loader setup (Application only)
        if (s.loaderSetup && s.type == RockType.APPLICATION) {
            writeText(srcDir, "setup.lua", LuaRocksTemplates.setupLua())
        }

        // 4. busted spec
        if (s.bustedConfig) {
            val specDir = baseDir.createChildDirectory(this, "spec")
            writeText(specDir, "${name}_spec.lua", LuaRocksTemplates.bustedSpec(name))
        }

        // 5. Makefile
        if (s.makefile) {
            writeText(baseDir, "Makefile", LuaRocksTemplates.makefile(name))
        }

        // 6. lua_modules/ dir
        baseDir.createChildDirectory(this, "lua_modules")

        // 7. .gitignore
        writeText(baseDir, ".gitignore", LuaRocksTemplates.gitignore())

        // 8. patch run-config template LUA_INIT
        if (s.loaderSetup) {
            patchRunConfigTemplate(project, baseDir)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun writeText(dir: VirtualFile, name: String, content: String) {
        val file = dir.findChild(name) ?: dir.createChildData(this, name)
        VfsUtil.saveText(file, content)
    }

    private fun patchRunConfigTemplate(project: Project, baseDir: VirtualFile) {
        val configType = ConfigurationTypeUtil.findConfigurationType(LuaRunConfigurationType::class.java)
        val factory = configType.configurationFactories.first()
        val template = RunManager.getInstance(project).getConfigurationTemplate(factory)
        val cfg = template.configuration as? LuaRunConfiguration ?: return
        cfg.environmentVariables = EnvironmentVariablesData.create(
            mapOf("LUA_INIT" to "@${baseDir.path}/src/setup.lua"),
            true,
            null,
        )
    }
}
