package net.internetisalie.lunar.rocks.init

import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.ProjectGeneratorPeer
import net.internetisalie.lunar.lang.LuaIcons
import javax.swing.Icon

/**
 * LuaRocks project generator for the New Project wizard (small-IDE EP).
 *
 * Appears in the wizard as "LuaRocks" with [LuaIcons.ROCKET]. Delegates all
 * file generation to [LuaRocksScaffolder], which runs inside a [WriteAction].
 *
 * Registered via `directoryProjectGenerator` EP in plugin.xml (see §7 of the design doc).
 */
class LuaRocksProjectGenerator : DirectoryProjectGeneratorBase<LuaRocksProjectSettings>() {

    override fun getName(): String = "LuaRocks"

    override fun getLogo(): Icon = LuaIcons.ROCKET

    override fun createPeer(): ProjectGeneratorPeer<LuaRocksProjectSettings> =
        LuaRocksGeneratorPeer()

    override fun validate(baseDirPath: String): ValidationResult {
        // Additional path-level validation; name validation is in the peer.
        return ValidationResult.OK
    }

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: LuaRocksProjectSettings,
        module: Module,
    ) {
        WriteAction.run<Throwable> {
            LuaRocksScaffolder.scaffold(project, baseDir, settings)
        }
        // Apply the wizard's interpreter choice (target + Explicit/Managed), then — if requested —
        // provision the isolated env once the project has opened (ROCKS-17).
        LuaRocksInterpreterInitializer.applySettings(project, settings)
        LuaRocksInterpreterInitializer.scheduleProvision(project, baseDir.path, settings)
    }
}
