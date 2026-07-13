package net.internetisalie.lunar.redis.debug

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.connection.LuaRedisProvisioning
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration
import net.internetisalie.lunar.redis.run.LuaRedisRunConfigurationType

/**
 * Pure-decision coverage of [LuaLdbSyncGuard] (design §3.8, TC-LDB-SYNC-1).
 *
 * Exercises the sync-confirmation decision table without a live dialog: SYNC + Remote requires
 * confirmation; FORKED (any provisioning) and SYNC + session-local (`LocalBinary`/`Docker`) do not.
 * `bannerText` copy is asserted so the fork/sync consequence wording is pinned.
 */
class TestLuaLdbSyncGuard : BasePlatformTestCase() {

    private fun configWith(mode: LuaRedisDebugMode): LuaRedisRunConfiguration {
        val type = LuaRedisRunConfigurationType.getInstance()
        val factory = type.configurationFactories[0]
        val config = factory.createTemplateConfiguration(project) as LuaRedisRunConfiguration
        config.debugMode = mode
        return config
    }

    private fun connectionWith(provisioning: LuaRedisProvisioning): LuaRedisServerConnection =
        LuaRedisServerConnection(
            id = "c1",
            name = "test",
            host = "127.0.0.1",
            port = 6379,
            tls = false,
            database = 0,
            username = null,
            provisioning = provisioning,
        )

    /** TC-LDB-SYNC-1: SYNC against a Remote (non-session-local) connection requires confirmation. */
    fun testSyncRemoteRequiresConfirmation() {
        val config = configWith(LuaRedisDebugMode.SYNC)
        val remote = connectionWith(LuaRedisProvisioning.Remote)
        assertTrue(LuaLdbSyncGuard.requiresConfirmation(config, remote))
    }

    /** TC-LDB-SYNC-1: FORKED never requires confirmation, even against a Remote connection. */
    fun testForkedRemoteDoesNotRequireConfirmation() {
        val config = configWith(LuaRedisDebugMode.FORKED)
        val remote = connectionWith(LuaRedisProvisioning.Remote)
        assertFalse(LuaLdbSyncGuard.requiresConfirmation(config, remote))
    }

    /** TC-LDB-SYNC-1: SYNC against a session-local (`LocalBinary`/`Docker`) connection does not prompt. */
    fun testSyncSessionLocalDoesNotRequireConfirmation() {
        val config = configWith(LuaRedisDebugMode.SYNC)
        val localBinary = connectionWith(LuaRedisProvisioning.LocalBinary("redis"))
        val docker = connectionWith(LuaRedisProvisioning.Docker("redis:8"))
        assertFalse(LuaLdbSyncGuard.requiresConfirmation(config, localBinary))
        assertFalse(LuaLdbSyncGuard.requiresConfirmation(config, docker))
    }

    fun testBannerTextStatesConsequence() {
        assertTrue(LuaLdbSyncGuard.bannerText(LuaRedisDebugMode.FORKED).contains("rolled back"))
        assertTrue(LuaLdbSyncGuard.bannerText(LuaRedisDebugMode.SYNC).contains("BLOCKED"))
        assertTrue(LuaLdbSyncGuard.bannerText(LuaRedisDebugMode.SYNC).contains("COMMITTED"))
    }
}
