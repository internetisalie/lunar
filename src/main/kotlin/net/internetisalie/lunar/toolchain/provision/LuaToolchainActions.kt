package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import java.io.File
import java.nio.file.Path

/** Opens the provision dialog and queues a fresh environment (design §2.11). */
class LuaProvisionToolchainAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val targetProject = event.project ?: return
        val dialog = LuaProvisionDialog(targetProject, initial = null)
        if (dialog.showAndGet()) {
            LuaToolProvisioner.getInstance().provision(targetProject, dialog.toRequest())
        }
    }
}

/** Re-provisions the active environment with different versions in the same rootDir (design §2.11). */
class LuaChangeToolchainVersionsAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project?.let(::readableManifest) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val targetProject = event.project ?: return
        val manifest = readableManifest(targetProject) ?: return
        val dialog = LuaProvisionDialog(targetProject, initial = manifest.request)
        if (dialog.showAndGet()) {
            LuaToolProvisioner.getInstance().provision(targetProject, dialog.toRequest())
        }
    }
}

/** Deletes the active environment directory then re-provisions its stored request (design §2.11). */
class LuaRecreateToolchainAction : DumbAwareAction("Recreate Environment") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project?.let(::readableManifest) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val targetProject = event.project ?: return
        val manifest = readableManifest(targetProject) ?: return
        val confirmed = Messages.showYesNoDialog(
            targetProject,
            "Delete and rebuild the environment at ${manifest.request.rootDir}?",
            "Recreate Environment",
            Messages.getQuestionIcon(),
        ) == Messages.YES
        if (confirmed) {
            recreate(targetProject, manifest.request)
        }
    }

    private fun recreate(targetProject: Project, request: LuaProvisionRequest) {
        ApplicationManager.getApplication().executeOnPooledThread {
            FileUtil.delete(File(request.rootDir))
            LuaToolProvisioner.getInstance().provision(targetProject, request)
        }
    }
}

/** Unregisters the active environment's tools and optionally deletes its directory (design §2.11). */
class LuaRemoveToolchainAction : DumbAwareAction("Remove Environment") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project?.let(::activeEnvironment) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val targetProject = event.project ?: return
        val environment = activeEnvironment(targetProject) ?: return
        val choice = Messages.showYesNoCancelDialog(
            targetProject,
            "Remove the Lua toolchain environment at ${environment.rootDir}?\n" +
                "\"Delete\" also removes the directory from disk.",
            "Remove Environment",
            "Delete",
            "Unbind Only",
            Messages.getCancelButton(),
            Messages.getQuestionIcon(),
        )
        when (choice) {
            Messages.YES -> remove(targetProject, environment.id, deleteDir = true)
            Messages.NO -> remove(targetProject, environment.id, deleteDir = false)
            else -> Unit
        }
    }

    private fun remove(targetProject: Project, envId: String, deleteDir: Boolean) {
        LuaToolchainProjectSettings.getInstance(targetProject).removeEnvironment(envId, deleteDir)
    }
}

/** Opens the matrix dialog and queues one provision per row (design §2.11, §2.13, §3.10). */
class LuaBatchProvisionToolchainsAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val targetProject = event.project ?: return
        val dialog = LuaBatchProvisionDialog(targetProject)
        if (dialog.showAndGet()) {
            val provisioner = LuaToolProvisioner.getInstance()
            dialog.toRequests().forEach { provisioner.provision(targetProject, it) }
        }
    }
}

private fun activeEnvironment(targetProject: Project): LuaEnvironmentState? =
    LuaToolchainProjectSettings.getInstance(targetProject).activeEnvironment()

private fun readableManifest(targetProject: Project): LuaEnvManifest? {
    val environment = activeEnvironment(targetProject) ?: return null
    if (environment.rootDir.isBlank()) return null
    return LuaEnvManifest.read(Path.of(environment.rootDir))
}
