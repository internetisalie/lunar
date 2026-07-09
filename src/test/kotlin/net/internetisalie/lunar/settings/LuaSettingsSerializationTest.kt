package net.internetisalie.lunar.settings

import com.intellij.util.xmlb.XmlSerializer
import net.internetisalie.lunar.lang.path.PathConfiguration
import org.jdom.Element
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Settings state serialization + defaults (MAINT-12) and the TOOLING-05-09 clean-break load
 * tolerance (TC 14).
 *
 * TOOLING-05 Phase 4 dropped the deleted-field round-trips (`LuaApplicationSettings.State.interpreters`).
 * The stale-XML tests verify the design §3.7 mechanism: `XmlSerializer.deserialize` skips
 * `<option>` children that no longer bind to a property, so a `lunar.xml` written by an older build
 * loads without exception and the stale tags vanish on the next save.
 */
class LuaSettingsSerializationTest {

    private fun staleOption(name: String, value: String): Element =
        Element("option").apply {
            setAttribute("name", name)
            setAttribute("value", value)
        }

    @Test
    fun projectStateRoundTripsThroughLoadState() {
        val settings = LuaProjectSettings()
        val state = LuaProjectSettings.State()
        state.rocksServerUrl = "redis://x"
        state.sourcePath = "/p"
        state.additionalGlobals = mutableListOf("G")
        state.suppressUnderscorePrefixedGlobals = false

        settings.loadState(state)
        val restored = settings.getState()

        assertEquals("redis://x", restored.rocksServerUrl)
        assertEquals("/p", restored.sourcePath)
        assertEquals(listOf("G"), restored.additionalGlobals)
        assertFalse(restored.suppressUnderscorePrefixedGlobals)
    }

    @Test
    fun projectStateRoundTripsThroughXmlSerializer() {
        val state = LuaProjectSettings.State()
        state.rocksServerUrl = "redis://x"
        state.sourcePath = "/p"
        state.additionalGlobals = mutableListOf("G")
        state.suppressUnderscorePrefixedGlobals = false

        val element = XmlSerializer.serialize(state)
        val restored = XmlSerializer.deserialize(element, LuaProjectSettings.State::class.java)

        assertEquals("redis://x", restored.rocksServerUrl)
        assertEquals("/p", restored.sourcePath)
        assertEquals(listOf("G"), restored.additionalGlobals)
        assertFalse(restored.suppressUnderscorePrefixedGlobals)
    }

    @Test
    fun projectStateHasExpectedDefaults() {
        val state = LuaProjectSettings.State()

        assertEquals("", state.rocksServerUrl)
        assertEquals(PathConfiguration.DEFAULT_SOURCE_PATH, state.sourcePath)
        assertTrue(state.suppressUnderscorePrefixedGlobals)
        assertTrue(state.additionalGlobals.isEmpty())
    }

    /** TC 14: a project `lunar.xml` with stale (unbound) tags loads cleanly + current fields survive. */
    @Test
    fun projectStateToleratesStaleXmlTags() {
        val current = LuaProjectSettings.State().apply {
            languageLevel = net.internetisalie.lunar.lang.LuaLanguageLevel.LUA53
            sourcePath = "/src/?.lua"
            rocksServerUrl = "https://rocks.example"
        }
        val element = XmlSerializer.serialize(current)
        // Inject tags that no longer bind to any accessor (simulating a stale older-build lunar.xml).
        // Field names must be genuinely unbound: `interpreterMode`/`hererocksEnvs` still exist on the
        // State in Phase 4 (deferred to Phase 5), so ghost names are used to exercise §3.7 for real.
        element.addContent(staleOption("legacyInterpreterMode", "HEREROCKS_MANAGED"))
        element.addContent(
            Element("option").apply {
                setAttribute("name", "legacyHererocksEnvs")
                addContent(Element("list").apply { addContent(Element("HererocksEnvState").apply { setAttribute("id", "e1") }) })
            },
        )
        element.addContent(
            Element("option").apply {
                setAttribute("name", "legacyExplicitInterpreter")
                addContent(Element("LuaInterpreter").apply { setAttribute("path", "/old/lua") })
            },
        )

        val restored = XmlSerializer.deserialize(element, LuaProjectSettings.State::class.java)

        assertEquals(net.internetisalie.lunar.lang.LuaLanguageLevel.LUA53, restored.languageLevel)
        assertEquals("/src/?.lua", restored.sourcePath)
        assertEquals("https://rocks.example", restored.rocksServerUrl)

        val reserialized = XmlSerializer.serialize(restored)
        val optionNames = reserialized.getChildren("option").mapNotNull { it.getAttributeValue("name") }
        assertFalse(optionNames.contains("legacyInterpreterMode"), "stale scalar tag dropped on re-serialize")
        assertFalse(optionNames.contains("legacyHererocksEnvs"), "stale list tag dropped on re-serialize")
        assertFalse(
            optionNames.contains("legacyExplicitInterpreter"),
            "stale nested-bean tag dropped on re-serialize",
        )
    }

    @Test
    fun applicationStateRoundTripsThroughLoadState() {
        val settings = LuaApplicationSettings()
        val state = LuaApplicationSettings.State()
        state.globalToolBindings["LUACHECK"] = "uuid-2"
        state.includeAllFieldsInCompletions = true
        state.enableTypeInference = false

        settings.loadState(state)
        val restored = settings.getState()

        assertEquals("uuid-2", restored.globalToolBindings["LUACHECK"])
        assertTrue(restored.includeAllFieldsInCompletions)
        assertFalse(restored.enableTypeInference)
    }

    @Test
    fun applicationStateRoundTripsThroughXmlSerializer() {
        val state = LuaApplicationSettings.State()
        state.globalToolBindings["LUACHECK"] = "uuid-2"
        state.includeAllFieldsInCompletions = true
        state.enableTypeInference = false
        state.toolInventory.add(net.internetisalie.lunar.tool.LuaTool(id = "tool-1"))

        val element = XmlSerializer.serialize(state)
        val restored = XmlSerializer.deserialize(element, LuaApplicationSettings.State::class.java)

        assertEquals(1, restored.globalToolBindings.size)
        assertTrue(restored.includeAllFieldsInCompletions)
        assertFalse(restored.enableTypeInference)
        assertEquals(1, restored.toolInventory.size)
    }

    /** TC 14 (app scope): the Phase-4-deleted `interpreters` tag no longer binds — tolerated on load. */
    @Test
    fun applicationStateToleratesStaleInterpretersTag() {
        val current = LuaApplicationSettings.State().apply { enableTypeInference = false }
        val element = XmlSerializer.serialize(current)
        element.addContent(
            Element("option").apply {
                setAttribute("name", "interpreters")
                addContent(Element("LuaInterpreter").apply { setAttribute("path", "/usr/bin/lua") })
            },
        )

        val restored = XmlSerializer.deserialize(element, LuaApplicationSettings.State::class.java)

        assertFalse(restored.enableTypeInference)
        val optionNames = XmlSerializer.serialize(restored)
            .getChildren("option").mapNotNull { it.getAttributeValue("name") }
        assertFalse(optionNames.contains("interpreters"), "stale interpreters tag dropped")
    }

    @Test
    fun applicationStateHasExpectedDefaults() {
        val state = LuaApplicationSettings.State()

        assertFalse(state.includeAllFieldsInCompletions)
        assertTrue(state.enableTypeInference)
        assertTrue(state.toolInventory.isEmpty())
        assertTrue(state.globalToolBindings.isEmpty())
    }
}
