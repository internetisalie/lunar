package net.internetisalie.lunar.rocks.env.matrix

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.rocks.env.HererocksEnvState
import net.internetisalie.lunar.rocks.env.HererocksFlavor
import net.internetisalie.lunar.rocks.env.HererocksProvisioner
import java.util.UUID

/** One row of the batch-provision request: a Lua flavor + version to provision (ROCKS-15-05). */
data class BatchRow(val flavor: HererocksFlavor, val luaVersion: String)

/**
 * Provisions a whole version matrix in one action (ROCKS-15-05, design §2.7, §3.5): each row yields
 * one [HererocksEnvState] under `<baseDir>/<flavor>-<version>` and one ROCKS-14
 * [HererocksProvisioner.provision] call (each guarded on its own background task by the provisioner).
 */
class BatchProvisionAction : DumbAwareAction("Provision Version Matrix…") {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        try {
            val dialog = BatchProvisionDialog(project)
            if (!dialog.showAndGet()) return
            provisionAll(project, dialog.baseDir(), dialog.rows())
        } catch (throwable: Throwable) {
            LOG.warn("Batch provisioning failed", throwable)
        }
    }

    private fun provisionAll(project: Project, baseDir: String, rows: List<BatchRow>) {
        val provisioner = HererocksProvisioner.getInstance(project)
        deriveSpecs(baseDir, rows).forEach { provisioner.provision(it, HererocksProvisioner.Mode.CREATE) }
    }

    companion object {
        private val LOG = Logger.getInstance(BatchProvisionAction::class.java)

        /** Pure spec derivation: one [HererocksEnvState] per row with a fresh id (design §3.5). */
        fun deriveSpecs(baseDir: String, rows: List<BatchRow>): List<HererocksEnvState> =
            rows.map { row ->
                HererocksEnvState(
                    id = UUID.randomUUID().toString(),
                    directory = "$baseDir/${row.flavor.name}-${row.luaVersion}",
                    flavor = row.flavor,
                    luaVersion = row.luaVersion,
                    luarocksVersion = "latest",
                )
            }
    }
}
