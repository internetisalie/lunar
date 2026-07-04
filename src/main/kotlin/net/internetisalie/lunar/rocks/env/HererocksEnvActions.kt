package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import net.internetisalie.lunar.settings.LuaProjectSettings

/** Opens the create dialog and provisions a fresh environment (ROCKS-14-03). */
class CreateHererocksEnvAction : DumbAwareAction("Create Isolated Lua Environment…") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val dialog = CreateHererocksEnvDialog(project, initial = null)
        if (dialog.showAndGet()) {
            HererocksProvisioner.getInstance(project).provision(dialog.toSpec(), HererocksProvisioner.Mode.CREATE)
        }
    }
}

/** Re-provisions the bound environment with a changed Lua/LuaRocks version (ROCKS-14-06). */
class UpgradeHererocksEnvAction : DumbAwareAction("Change Lua/LuaRocks Version…") {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project?.let { boundEnv(it) != null } == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val current = boundEnv(project) ?: return
        val dialog = CreateHererocksEnvDialog(project, initial = current)
        if (dialog.showAndGet()) {
            HererocksProvisioner.getInstance(project).provision(dialog.toSpec(), HererocksProvisioner.Mode.UPGRADE)
        }
    }
}

/** Deletes and re-provisions the environment directory with the stored spec (ROCKS-14-07). */
class RecreateHererocksEnvAction : DumbAwareAction("Recreate Environment") {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project?.let { boundEnv(it) != null } == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val current = boundEnv(project) ?: return
        val confirmed = Messages.showYesNoDialog(
            project,
            "Delete and rebuild the environment at ${current.directory}?",
            "Recreate Lua Environment",
            Messages.getQuestionIcon(),
        ) == Messages.YES
        if (confirmed) {
            HererocksProvisioner.getInstance(project).provision(current, HererocksProvisioner.Mode.RECREATE)
        }
    }
}

/** Clears the bindings + descriptor and optionally deletes the directory (ROCKS-14-08). */
class RemoveHererocksEnvAction : DumbAwareAction("Remove Environment") {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project?.let { boundEnv(it) != null } == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val current = boundEnv(project) ?: return
        val choice = Messages.showYesNoCancelDialog(
            project,
            "Remove the Lua environment binding for ${current.directory}?\n" +
                "\"Delete\" also removes the directory from disk.",
            "Remove Lua Environment",
            "Delete",
            "Unbind Only",
            Messages.getCancelButton(),
            Messages.getQuestionIcon(),
        )
        when (choice) {
            Messages.YES -> HererocksEnvBinder.unbind(project, deleteDir = true)
            Messages.NO -> HererocksEnvBinder.unbind(project, deleteDir = false)
            else -> Unit
        }
    }
}

private fun boundEnv(project: Project): HererocksEnvState? =
    LuaProjectSettings.getInstance(project).activeEnv()
