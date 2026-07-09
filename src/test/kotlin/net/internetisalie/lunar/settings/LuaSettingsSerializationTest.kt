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
        // Inject the REAL legacy tags deleted in TOOLING-05 Phase 5 (TC 14): a `lunar.xml` written by
        // an older build still carries `interpreterMode`/`hererocksEnvs`/`explicitInterpreter`, which
        // no longer bind to any accessor. They must load without exception and vanish on re-serialize.
        element.addContent(staleOption("interpreterMode", "HEREROCKS_MANAGED"))
        element.addContent(staleOption("interpreterModeMigrated", "true"))
        element.addContent(staleOption("activeEnvId", "env-1"))
        element.addContent(
            Element("option").apply {
                setAttribute("name", "hererocksEnvs")
                addContent(Element("list").apply { addContent(Element("HererocksEnvState").apply { setAttribute("id", "e1") }) })
            },
        )
        element.addContent(
            Element("option").apply {
                setAttribute("name", "explicitInterpreter")
                addContent(Element("LuaInterpreter").apply { setAttribute("path", "/old/lua") })
            },
        )
        // TOOLING-05 Phase 6 deleted `projectToolBindings` — its stale tag must also be tolerated.
        element.addContent(
            Element("option").apply {
                setAttribute("name", "projectToolBindings")
                addContent(
                    Element("map").apply {
                        addContent(
                            Element("entry").apply {
                                setAttribute("key", "LUACHECK")
                                setAttribute("value", "uuid-3")
                            },
                        )
                    },
                )
            },
        )

        val restored = XmlSerializer.deserialize(element, LuaProjectSettings.State::class.java)

        assertEquals(net.internetisalie.lunar.lang.LuaLanguageLevel.LUA53, restored.languageLevel)
        assertEquals("/src/?.lua", restored.sourcePath)
        assertEquals("https://rocks.example", restored.rocksServerUrl)

        val reserialized = XmlSerializer.serialize(restored)
        val optionNames = reserialized.getChildren("option").mapNotNull { it.getAttributeValue("name") }
        assertFalse(optionNames.contains("interpreterMode"), "stale scalar tag dropped on re-serialize")
        assertFalse(optionNames.contains("hererocksEnvs"), "stale list tag dropped on re-serialize")
        assertFalse(
            optionNames.contains("explicitInterpreter"),
            "stale nested-bean tag dropped on re-serialize",
        )
        assertFalse(
            optionNames.contains("projectToolBindings"),
            "stale projectToolBindings tag dropped on re-serialize",
        )
    }

    @Test
    fun applicationStateRoundTripsThroughLoadState() {
        val settings = LuaApplicationSettings()
        val state = LuaApplicationSettings.State()
        state.includeAllFieldsInCompletions = true
        state.enableTypeInference = false

        settings.loadState(state)
        val restored = settings.getState()

        assertTrue(restored.includeAllFieldsInCompletions)
        assertFalse(restored.enableTypeInference)
    }

    @Test
    fun applicationStateRoundTripsThroughXmlSerializer() {
        val state = LuaApplicationSettings.State()
        state.includeAllFieldsInCompletions = true
        state.enableTypeInference = false

        val element = XmlSerializer.serialize(state)
        val restored = XmlSerializer.deserialize(element, LuaApplicationSettings.State::class.java)

        assertTrue(restored.includeAllFieldsInCompletions)
        assertFalse(restored.enableTypeInference)
    }

    /**
     * TC 14 (app scope): the TOOLING-05-deleted `interpreters` / `toolInventory` /
     * `globalToolBindings` tags no longer bind to any accessor — tolerated on load and dropped
     * on the next re-serialize (design §3.7).
     */
    @Test
    fun applicationStateToleratesStaleToolTags() {
        val current = LuaApplicationSettings.State().apply { enableTypeInference = false }
        val element = XmlSerializer.serialize(current)
        element.addContent(
            Element("option").apply {
                setAttribute("name", "interpreters")
                addContent(Element("LuaInterpreter").apply { setAttribute("path", "/usr/bin/lua") })
            },
        )
        element.addContent(
            Element("option").apply {
                setAttribute("name", "toolInventory")
                addContent(Element("LuaTool").apply { setAttribute("id", "tool-1") })
            },
        )
        element.addContent(
            Element("option").apply {
                setAttribute("name", "globalToolBindings")
                addContent(
                    Element("map").apply {
                        addContent(
                            Element("entry").apply {
                                setAttribute("key", "LUACHECK")
                                setAttribute("value", "uuid-2")
                            },
                        )
                    },
                )
            },
        )

        val restored = XmlSerializer.deserialize(element, LuaApplicationSettings.State::class.java)

        assertFalse(restored.enableTypeInference)
        val optionNames = XmlSerializer.serialize(restored)
            .getChildren("option").mapNotNull { it.getAttributeValue("name") }
        assertFalse(optionNames.contains("interpreters"), "stale interpreters tag dropped")
        assertFalse(optionNames.contains("toolInventory"), "stale toolInventory tag dropped")
        assertFalse(optionNames.contains("globalToolBindings"), "stale globalToolBindings tag dropped")
    }

    @Test
    fun applicationStateHasExpectedDefaults() {
        val state = LuaApplicationSettings.State()

        assertFalse(state.includeAllFieldsInCompletions)
        assertTrue(state.enableTypeInference)
    }
}
