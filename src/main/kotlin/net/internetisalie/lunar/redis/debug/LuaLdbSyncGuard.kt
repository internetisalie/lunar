package net.internetisalie.lunar.redis.debug

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.redis.connection.LuaRedisProvisioning
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration

/**
 * Guards `SCRIPT DEBUG SYNC` against a shared/remote server (design §2.13, §3.8, epic RISK-R03).
 *
 * SYNC mode blocks the whole server event loop while paused and commits writes, so debugging a
 * non-session-local ([LuaRedisProvisioning.Remote]) server in SYNC is an outage-shaped hazard. The
 * decision ([requiresConfirmation]) and the warning copy ([bannerText]) are pure and unit-tested
 * (TC-LDB-SYNC-1); only [confirm] touches the EDT (a `Messages.showYesNoDialog`, grounded at
 * `toolchain/provision/LuaToolchainActions.kt:55`). Holds no `Project`/`Editor` field — [confirm]
 * takes the project as a parameter (contract §4).
 */
object LuaLdbSyncGuard {

    /**
     * Pure decision (design §3.8): a confirmation is required only for SYNC against a `Remote`
     * (non-session-local) connection. FORKED is always safe; a session-local `LocalBinary`/`Docker`
     * server is disposable and ours, so no prompt.
     */
    fun requiresConfirmation(config: LuaRedisRunConfiguration, connection: LuaRedisServerConnection): Boolean {
        if (config.debugMode != LuaRedisDebugMode.SYNC) return false
        return connection.provisioning is LuaRedisProvisioning.Remote
    }

    /** Pure banner copy (design §2.13, §3.6): states the write/lifecycle consequence of [mode]. */
    fun bannerText(mode: LuaRedisDebugMode): String = when (mode) {
        LuaRedisDebugMode.FORKED ->
            "Forked debug session: all writes are rolled back when the session ends."
        LuaRedisDebugMode.SYNC ->
            "SYNC debug session: the server event loop is BLOCKED while paused and writes are COMMITTED."
    }

    /**
     * Shows the sync-on-remote confirmation on the EDT (design §3.8); returns `true` on Yes. Marshals
     * onto [Dispatchers.EDT] since the caller runs on the session scope (off-EDT).
     */
    suspend fun confirm(project: Project, connection: LuaRedisServerConnection): Boolean =
        withContext(Dispatchers.EDT) {
            Messages.showYesNoDialog(
                project,
                "Debugging \"${connection.name}\" in SYNC mode will BLOCK the entire server event loop " +
                    "while paused and COMMIT all writes. Continue?",
                "Redis Sync Debugging",
                Messages.getWarningIcon(),
            ) == Messages.YES
        }
}
