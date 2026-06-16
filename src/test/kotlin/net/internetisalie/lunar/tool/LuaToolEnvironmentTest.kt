package net.internetisalie.lunar.tool

import com.intellij.execution.configurations.GeneralCommandLine
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-logic unit tests for TOOL-02 PATH augmentation ([LuaToolEnvironment]) and the
 * project/global binding storage. These avoid a live IntelliJ application context by exercising
 * the test-visible pure overloads and the `State` data holders directly; resolution precedence
 * that requires app/project services is covered by the platform-fixture integration paths.
 */
class LuaToolEnvironmentTest {

    private val sep = File.pathSeparator

    @Test
    fun `prepend with empty tool dirs leaves PATH untouched`() {
        val cmd = GeneralCommandLine("echo")
        cmd.environment["PATH"] = "/orig/bin"
        LuaToolEnvironment.prependToolDirsToPath(cmd, emptyList())
        assertEquals("/orig/bin", cmd.environment["PATH"])
    }

    @Test
    fun `prepend places tool dirs ahead of existing PATH preserving order`() {
        val cmd = GeneralCommandLine("echo")
        cmd.environment["PATH"] = "/orig/bin"
        val dirs = listOf(Path.of("/opt/tools/bin"), Path.of("/opt/more/bin"))

        LuaToolEnvironment.prependToolDirsToPath(cmd, dirs)

        assertEquals("/opt/tools/bin$sep/opt/more/bin$sep/orig/bin", cmd.environment["PATH"])
    }

    @Test
    fun `prepend with no preset PATH override falls back to process PATH`() {
        val cmd = GeneralCommandLine("echo")
        // No explicit PATH override on the command line -> uses System.getenv("PATH").
        val dirs = listOf(Path.of("/opt/tools/bin"))

        LuaToolEnvironment.prependToolDirsToPath(cmd, dirs)

        val result = cmd.environment["PATH"]!!
        assertTrue(result.startsWith("/opt/tools/bin"))
        val processPath = System.getenv("PATH")
        if (!processPath.isNullOrBlank()) {
            assertTrue(result.endsWith(processPath))
        }
    }

    @Test
    fun `prepend single dir to blank PATH yields just the dir`() {
        val cmd = GeneralCommandLine("echo")
        cmd.environment["PATH"] = ""
        LuaToolEnvironment.prependToolDirsToPath(cmd, listOf(Path.of("/opt/x/bin")))
        assertEquals("/opt/x/bin", cmd.environment["PATH"])
    }
}

/**
 * Storage tests for the TOOL-02 binding maps on the settings `State` holders.
 */
class LuaToolBindingStateTest {

    @Test
    fun `global tool bindings start empty and round-trip by type name`() {
        val state = LuaApplicationSettings.State()
        assertTrue(state.globalToolBindings.isEmpty())

        state.globalToolBindings[LuaToolType.LUAROCKS.name] = "tool-id-1"
        assertEquals("tool-id-1", state.globalToolBindings[LuaToolType.LUAROCKS.name])
    }

    @Test
    fun `global bindings survive loadState round-trip`() {
        val settings = LuaApplicationSettings()
        val state = LuaApplicationSettings.State()
        state.globalToolBindings[LuaToolType.STYLUA.name] = "stylua-id"

        settings.loadState(state)

        assertEquals("stylua-id", settings.getState().globalToolBindings[LuaToolType.STYLUA.name])
    }

    @Test
    fun `project tool bindings start empty and store by type name`() {
        val state = LuaProjectSettings.State()
        assertTrue(state.projectToolBindings.isEmpty())

        state.projectToolBindings[LuaToolType.LUACHECK.name] = "luacheck-id"
        assertEquals("luacheck-id", state.projectToolBindings[LuaToolType.LUACHECK.name])
    }
}
