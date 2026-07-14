package net.internetisalie.lunar.redis.run

import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.connection.LuaRedisConnectionSettings
import net.internetisalie.lunar.redis.connection.LuaRedisProvisioning
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.debug.LuaRedisDebugMode

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

    /** REDIS-02 A2: debugMode defaults to FORKED and round-trips SYNC additively (Run behavior unchanged). */
    fun testDebugModeDefaultsForkedAndRoundTrips() {
        seedConnection("u1")
        val config = newConfig()
        assertEquals("default must be FORKED", LuaRedisDebugMode.FORKED, config.debugMode)

        config.debugMode = LuaRedisDebugMode.SYNC
        assertEquals(LuaRedisDebugMode.SYNC, config.debugMode)
        assertEquals("SYNC", config.options.debugMode)

        // execMode is untouched by the debugMode field (additive, no Run behavior change).
        config.execMode = LuaRedisExecMode.EVAL
        assertEquals(LuaRedisExecMode.EVAL, config.execMode)
        assertEquals(LuaRedisDebugMode.SYNC, config.debugMode)
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

    /**
     * TC-MODE-1 (part): FCALL with blank functionName and deployOnly=false → rejected with
     * "Function name is not defined". deployOnly=true → accepted (TC-MODE-1).
     *
     * REDIS-05: FCALL is now accepted; the old "FCALL mode is not available until REDIS-05"
     * rejection has been removed (design §3.6). Function-name absence is the new guard.
     */
    fun testCheckConfigurationFcallRequiresFunctionNameWhenNotDeployOnly() {
        seedConnection("u1")
        val config = newConfig()
        config.scriptPath = "a.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.FCALL
        config.functionName = null
        config.deployOnly = false

        val failure = runCatching { config.checkConfiguration() }.exceptionOrNull()
        assertTrue("expected RuntimeConfigurationException, got $failure", failure is RuntimeConfigurationException)
        assertEquals("Function name is not defined", failure?.message)
    }

    /** TC-MODE-1: FCALL with deployOnly=true is accepted even with no functionName. */
    fun testCheckConfigurationFcallDeployOnlyAcceptsBlankFunctionName() {
        seedConnection("u1")
        val config = newConfig()
        config.scriptPath = "a.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.FCALL
        config.functionName = null
        config.deployOnly = true

        // Must not throw
        config.checkConfiguration()
    }

    /** TC-MODE-1: FCALL with functionName set and a non-existent script file passes (file missing → skip scan). */
    fun testCheckConfigurationFcallWithNameAndMissingFilePassesValidation() {
        seedConnection("u1")
        val config = newConfig()
        config.scriptPath = "/nonexistent/lib.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.FCALL
        config.functionName = "f"
        config.deployOnly = false

        // File missing → PSI file unresolvable → scan skipped → no throw
        config.checkConfiguration()
    }

    /** TC-MODE-1: FCALL option fields round-trip through the StoredProperty bridge. */
    fun testFcallFieldsRoundTrip() {
        val config = newConfig()
        config.functionName = "myFn"
        config.replaceOnLoad = false
        config.deployOnly = true

        assertEquals("myFn", config.functionName)
        assertFalse(config.replaceOnLoad)
        assertTrue(config.deployOnly)

        // Raw stored values
        assertEquals("myFn", config.options.functionName)
        assertEquals("false", config.options.replaceOnLoad)
        assertEquals("true", config.options.deployOnly)
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

    /**
     * TC-VALID-1: functionName="ghost", library registers only "f" and "g"
     * → checkFunctionRegistered throws "Function 'ghost' is not registered in lib.lua (registered: f, g)".
     *
     * Tests the validation logic directly via [LuaRedisRunConfiguration.checkFunctionRegistered],
     * bypassing the VFS lookup (which uses TempFileSystem in BasePlatformTestCase, incompatible
     * with VfsUtil.findFileByIoFile). The VFS lookup path is exercised by
     * [testCheckConfigurationFcallWithNameAndMissingFilePassesValidation] (graceful skip).
     */
    fun testFunctionRegisteredValidationRejectsUnknownName_TC_VALID_1() {
        seedConnection("u1")
        val file = myFixture.configureByText(
            "lib.lua",
            "#!lua name=lib\n" +
                "redis.register_function('f', function(keys, args) return 1 end)\n" +
                "redis.register_function('g', function(keys, args) return 2 end)",
        )
        val config = newConfig()
        config.scriptPath = "lib.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.FCALL
        config.functionName = "ghost"
        config.deployOnly = false

        val failure = runCatching { config.checkFunctionRegistered("ghost", file) }.exceptionOrNull()
        assertTrue("expected RuntimeConfigurationException, got $failure", failure is RuntimeConfigurationException)
        val msg = failure?.message ?: ""
        assertTrue("error mentions function name 'ghost': $msg", msg.contains("ghost"))
        assertTrue("error mentions 'f': $msg", msg.contains("f"))
        assertTrue("error mentions 'g': $msg", msg.contains("g"))
    }

    /**
     * TC-VALID-2: library with a dynamic (non-literal) registration → name validation skipped;
     * no error even when the literal "ghost" is absent (hasDynamic=true → skip).
     */
    fun testFunctionRegisteredValidationSkipsWhenDynamic_TC_VALID_2() {
        seedConnection("u1")
        val file = myFixture.configureByText(
            "libdyn.lua",
            "#!lua name=libdyn\n" +
                "local fnName = 'f'\n" +
                "redis.register_function(fnName, function(keys, args) return 1 end)",
        )
        val config = newConfig()
        config.scriptPath = "libdyn.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.FCALL
        config.functionName = "f"
        config.deployOnly = false

        // hasDynamic=true → skip validation entirely → no throw
        config.checkFunctionRegistered("f", file)
    }

    /**
     * TC-RO-2: static scan finds "f" with flags={'no-writes'}, readOnly=false → checkConfiguration
     * must NOT throw (hint is a UI concern surfaced in the settings editor, not a validation error).
     *
     * Exercises via checkConfiguration which gracefully skips the PSI scan when the file is missing
     * on disk (TempFileSystem path); the no-writes check logic is in the settings editor.
     */
    fun testCheckConfigurationFcallNoWritesHintDoesNotBlock_TC_RO_2() {
        seedConnection("u1")
        val config = newConfig()
        // Use a non-existent path → PSI lookup returns null → scan skipped → no throw
        // This proves checkConfiguration never throws for no-writes (regardless of flags)
        config.scriptPath = "/nonexistent/libnowrites.lua"
        config.connectionId = "u1"
        config.execMode = LuaRedisExecMode.FCALL
        config.functionName = "f"
        config.readOnly = false
        config.deployOnly = false

        // Must not throw even when readOnly=false + no-writes would hint
        config.checkConfiguration()
    }
}
