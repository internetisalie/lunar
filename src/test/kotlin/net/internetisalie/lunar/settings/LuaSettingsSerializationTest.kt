package net.internetisalie.lunar.settings

import com.intellij.util.xmlb.XmlSerializer
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaInterpreter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MAINT-12 Phase 1: settings state serialization and defaults (MAINT-12-01/-02/-03).
 *
 * Plain-JUnit coverage of the uncovered [LuaProjectSettings.State] fields and the
 * [LuaApplicationSettings.State] collections/booleans.
 * Target migration / `TargetState` (already in `LuaProjectSettingsTest.kt`) and
 * `toolInventory` standalone round-trip (already in `LuaToolManagerTest`) are not re-tested.
 */
class LuaSettingsSerializationTest {
    @Test
    fun projectStateRoundTripsThroughLoadState() {
        val settings = LuaProjectSettings()
        val state = LuaProjectSettings.State()
        state.rocksServerUrl = "redis://x"
        state.projectToolBindings["LUACHECK"] = "uuid-1"
        state.sourcePath = "/p"
        state.additionalGlobals = mutableListOf("G")
        state.suppressUnderscorePrefixedGlobals = false

        settings.loadState(state)
        val restored = settings.getState()

        assertEquals("redis://x", restored.rocksServerUrl)
        assertEquals("uuid-1", restored.projectToolBindings["LUACHECK"])
        assertEquals("/p", restored.sourcePath)
        assertEquals(listOf("G"), restored.additionalGlobals)
        assertFalse(restored.suppressUnderscorePrefixedGlobals)
    }

    @Test
    fun projectStateRoundTripsThroughXmlSerializer() {
        val state = LuaProjectSettings.State()
        state.rocksServerUrl = "redis://x"
        state.projectToolBindings["LUACHECK"] = "uuid-1"
        state.sourcePath = "/p"
        state.additionalGlobals = mutableListOf("G")
        state.suppressUnderscorePrefixedGlobals = false

        val element = XmlSerializer.serialize(state)
        val restored = XmlSerializer.deserialize(element, LuaProjectSettings.State::class.java)

        assertEquals("redis://x", restored.rocksServerUrl)
        assertEquals("uuid-1", restored.projectToolBindings["LUACHECK"])
        assertEquals("/p", restored.sourcePath)
        assertEquals(listOf("G"), restored.additionalGlobals)
        assertFalse(restored.suppressUnderscorePrefixedGlobals)
    }

    @Test
    fun projectStateHasExpectedDefaults() {
        val state = LuaProjectSettings.State()

        assertEquals("", state.rocksServerUrl)
        assertTrue(state.projectToolBindings.isEmpty())
        assertEquals(PathConfiguration.DEFAULT_SOURCE_PATH, state.sourcePath)
        assertTrue(state.suppressUnderscorePrefixedGlobals)
        assertTrue(state.additionalGlobals.isEmpty())
    }

    @Test
    fun applicationStateRoundTripsThroughLoadState() {
        val settings = LuaApplicationSettings()
        val state = LuaApplicationSettings.State()
        state.interpreters = listOf(LuaInterpreter(path = "/usr/bin/lua"))
        state.globalToolBindings["LUACHECK"] = "uuid-2"
        state.includeAllFieldsInCompletions = true
        state.enableTypeInference = false

        settings.loadState(state)
        val restored = settings.getState()

        assertEquals(1, restored.interpreters.size)
        assertEquals("/usr/bin/lua", restored.interpreters[0].path)
        assertEquals("uuid-2", restored.globalToolBindings["LUACHECK"])
        assertTrue(restored.includeAllFieldsInCompletions)
        assertFalse(restored.enableTypeInference)
    }

    @Test
    fun applicationStateRoundTripsThroughXmlSerializer() {
        val state = LuaApplicationSettings.State()
        state.interpreters = listOf(LuaInterpreter(path = "/usr/bin/lua"))
        state.globalToolBindings["LUACHECK"] = "uuid-2"
        state.includeAllFieldsInCompletions = true
        state.enableTypeInference = false
        state.toolInventory.add(net.internetisalie.lunar.tool.LuaTool(id = "tool-1"))

        val element = XmlSerializer.serialize(state)
        val restored = XmlSerializer.deserialize(element, LuaApplicationSettings.State::class.java)

        assertEquals("/usr/bin/lua", restored.interpreters[0].path)
        assertEquals(1, restored.globalToolBindings.size)
        assertTrue(restored.includeAllFieldsInCompletions)
        assertFalse(restored.enableTypeInference)
        assertEquals(1, restored.toolInventory.size)
    }

    @Test
    fun applicationStateHasExpectedDefaults() {
        val state = LuaApplicationSettings.State()

        assertFalse(state.includeAllFieldsInCompletions)
        assertTrue(state.enableTypeInference)
        assertTrue(state.interpreters.isEmpty())
        assertTrue(state.toolInventory.isEmpty())
        assertTrue(state.globalToolBindings.isEmpty())
    }

}
