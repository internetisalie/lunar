package net.internetisalie.lunar.redis.run

import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.connection.LuaRedisConnectionSettings
import net.internetisalie.lunar.redis.connection.LuaRedisProvisioning
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection

/**
 * Option round-trip + `checkConfiguration` coverage for [LuaRedisRunConfiguration] (design §2.8, §3.7).
 *
 * TC-RC-1: options persist all fields (KEYS/ARGV survive the `\n`-joined `keysRaw`/`argvRaw` bridge);
 * a blank script path makes `checkConfiguration` throw; FCALL is rejected until REDIS-05. A light
 * platform fixture provides the project the config resolves its connection against.
 */
class TestLuaRedisRunConfiguration : BasePlatformTestCase() {

    private fun newConfig(): LuaRedisRunConfiguration {
        val type = LuaRedisRunConfigurationType.getInstance()
        val factory = type.configurationFactories[0]
        return factory.createTemplateConfiguration(project) as LuaRedisRunConfiguration
    }

    private fun seedConnection(id: String = "u1") {
        LuaRedisConnectionSettings.getInstance(project).upsert(
            LuaRedisServerConnection(
                id = id,
                name = "local",
                host = "127.0.0.1",
                port = 6379,
                tls = false,
                database = 0,
                username = null,
                provisioning = LuaRedisProvisioning.Remote,
            ),
        )
    }

    /** TC-RC-1: every field round-trips, and KEYS/ARGV survive the newline-joined raw bridge. */
    fun testOptionsRoundTrip() {
        seedConnection("u1")
        val config = newConfig()
        config.scriptPath = "a.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.EVALSHA
        config.readOnly = true
        config.keys = listOf("k1", "k2")
        config.argv = listOf("v1")

        assertEquals("a.lua", config.scriptPath)
        assertEquals("u1", config.connectionId)
        assertEquals(LuaRedisExecMode.EVALSHA, config.execMode)
        assertTrue(config.readOnly)
        assertEquals(listOf("k1", "k2"), config.keys)
        assertEquals(listOf("v1"), config.argv)

        // The raw persisted fields are newline-joined.
        assertEquals("k1\nk2", config.options.keysRaw)
        assertEquals("v1", config.options.argvRaw)
        // The connection resolves by id from project settings.
        assertNotNull(config.connection)
        assertEquals("u1", config.connection?.id)
    }

    /** TC-RC-1: a blank script path makes checkConfiguration throw the exact message. */
    fun testCheckConfigurationRejectsBlankScript() {
        seedConnection("u1")
        val config = newConfig()
        config.connectionId = "u1"
        config.scriptPath = ""

        val failure = runCatching { config.checkConfiguration() }.exceptionOrNull()
        assertTrue("expected RuntimeConfigurationException, got $failure", failure is RuntimeConfigurationException)
        assertEquals("Script path is not defined", failure?.message)
    }

    /** checkConfiguration rejects a missing connection. */
    fun testCheckConfigurationRejectsMissingConnection() {
        val config = newConfig()
        config.scriptPath = "a.lua"
        config.connectionId = "does-not-exist"

        val failure = runCatching { config.checkConfiguration() }.exceptionOrNull()
        assertTrue(failure is RuntimeConfigurationException)
        assertEquals("No Redis connection selected", failure?.message)
    }

    /** checkConfiguration rejects the reserved FCALL mode until REDIS-05 (design §3.7). */
    fun testCheckConfigurationRejectsFcallUntilRedis05() {
        seedConnection("u1")
        val config = newConfig()
        config.scriptPath = "a.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.FCALL

        val failure = runCatching { config.checkConfiguration() }.exceptionOrNull()
        assertTrue(failure is RuntimeConfigurationException)
        assertEquals("FCALL mode is not available until REDIS-05", failure?.message)
    }

    /** A valid EVALSHA read-only config passes checkConfiguration (the version gate is a run-time check). */
    fun testValidConfigurationPasses() {
        seedConnection("u1")
        val config = newConfig()
        config.scriptPath = "a.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.EVALSHA
        config.readOnly = true

        config.checkConfiguration()
    }
}
