package net.internetisalie.lunar.redis.connection

import com.intellij.execution.ExecutionException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

/**
 * TC-LAUNCH-1..3 (design §3.9, requirements AC-3): command-line assembly for binary and Docker
 * provisioning, and the "neither available" error path, verified without launching real processes.
 *
 * TC-LAUNCH-1: `LocalBinary` with a resolved path and a fixed port → correct binary command line.
 * TC-LAUNCH-2: `Docker` with docker on PATH and a fixed port → correct docker run command line.
 * TC-LAUNCH-3: `LocalBinary` with an unresolved binary AND no docker on PATH → [ExecutionException]
 *   whose message names both the missing binary (Settings path) and Docker as alternatives.
 *
 * The [LaunchSeams] injection prevents real process spawning; [buildBinaryCommandLine] and
 * [buildDockerCommandLine] are tested directly for TC-LAUNCH-1/2 to pin the exact argument list.
 * TC-LAUNCH-3 drives [LuaRedisServerLauncher.launch] end-to-end through seams that return null
 * for both resolution paths.
 */
class TestLuaRedisServerLauncher : BasePlatformTestCase() {

    /** TC-LAUNCH-1: binary command line is `redis-server --port 12345 --save ""` (design §3.9). */
    fun testBinaryCommandLineAssembly() {
        val commandLine = buildBinaryCommandLine("/usr/bin/redis-server", 12345)

        assertEquals("/usr/bin/redis-server", commandLine.exePath)
        assertEquals(
            listOf("--port", "12345", "--save", ""),
            commandLine.parametersList.list,
        )
    }

    /** TC-LAUNCH-2: docker command line is `docker run --rm -d -p 12345:6379 redis:8` (design §3.9). */
    fun testDockerCommandLineAssembly() {
        val commandLine = buildDockerCommandLine("/usr/bin/docker", "redis:8", 12345)

        assertEquals("/usr/bin/docker", commandLine.exePath)
        assertEquals(
            listOf("run", "--rm", "-d", "-p", "12345:6379", "redis:8"),
            commandLine.parametersList.list,
        )
    }

    /**
     * TC-LAUNCH-3: when the server binary cannot be resolved AND Docker is not on PATH, [launch]
     * throws [ExecutionException] whose message mentions both the Settings path and Docker (design §3.9).
     */
    fun testNeitherBinaryNorDockerThrowsExecutionException() {
        val neitherAvailableSeams = LaunchSeams(
            resolveToolPath = { _, _ -> null },
            resolveDockerPath = { null },
            allocatePort = { 12345 },
        )
        val launcher = LuaRedisServerLauncher(myFixture.project, neitherAvailableSeams)
        val provisioning = LuaRedisProvisioning.LocalBinary("redis-server")

        try {
            runBlocking { launcher.launch(provisioning) }
            fail("Expected ExecutionException when binary is unresolved")
        } catch (ex: ExecutionException) {
            val message = ex.message.orEmpty()
            assertTrue(
                "Message should mention Settings/Toolchain path",
                message.contains("Settings") || message.contains("Toolchain"),
            )
            assertTrue(
                "Message should mention Docker as an alternative",
                message.contains("Docker"),
            )
        }
    }
}
