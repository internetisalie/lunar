package net.internetisalie.lunar.toolchain

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TOOLING-00-06 spike (design §2.6): proves the contract-§7 clean-break persistence model.
 *
 * The new state classes round-trip through `com.intellij.util.xmlb.XmlSerializer` with deep equality,
 * a legacy `lunar.xml` fragment (today's real tags) deserializes into them with **no exception**, and
 * re-serialization emits **none** of the deleted legacy tag names.
 *
 * The state classes below are THROWAWAY spike copies (class + field names per design §2.6 / contract §7);
 * TOOLING-01 ships the production versions under this package. `var` + defaults are the sanctioned
 * XML-serializer exception (as on `LuaProjectSettings.State.hererocksEnvs`).
 */
class LuaToolchainSerializationSpikeTest {
    // --- Spike-local state classes (design §2.6 code block, verbatim field names) ---

    class RegisteredToolState {
        var id: String = ""
        var kindId: String = ""
        var path: String = ""
        var version: String? = null
        var origin: String = "DISCOVERED"
        var environmentId: String? = null
    }

    class LuaToolchainAppState {
        var tools: MutableList<RegisteredToolState> = mutableListOf()
        var globalBindings: MutableMap<String, String> = HashMap()
    }

    class ToolEnvironmentState {
        var id: String = ""
        var name: String = ""
        var rootDir: String = ""
        var toolIds: MutableList<String> = mutableListOf()
    }

    class LuaToolchainProjectState {
        var bindings: MutableMap<String, String> = HashMap()
        var environments: MutableList<ToolEnvironmentState> = mutableListOf()
        var activeEnvironmentId: String? = null
        var luacheckArguments: String = ""
        var rocksServerUrl: String = ""
    }

    // --- (a) round-trip with field-by-field deep equality ---

    @Test
    fun appStateRoundTripsDeepEqual() {
        val appState = LuaToolchainAppState()
        appState.tools.add(tool("t1", "lua", "/opt/lua/bin/lua", "5.4.8"))
        appState.tools.add(tool("t2", "luarocks", "/opt/lua/bin/luarocks", null))
        appState.globalBindings["LUACHECK"] = "t1"

        val element = XmlSerializer.serialize(appState)
        val restored = XmlSerializer.deserialize(element, LuaToolchainAppState::class.java)

        assertEquals(appState.tools.size, restored.tools.size)
        appState.tools.zip(restored.tools).forEach { (expected, actual) ->
            assertToolEquals(expected, actual)
        }
        assertEquals(appState.globalBindings, restored.globalBindings)
    }

    @Test
    fun projectStateRoundTripsDeepEqual() {
        val projectState = LuaToolchainProjectState()
        val environment = ToolEnvironmentState().apply {
            id = "env-1"
            name = "dev"
            rootDir = "/proj/.lunar/env-1"
            toolIds = mutableListOf("t1", "t2")
        }
        projectState.environments.add(environment)
        projectState.bindings["STYLUA"] = "t3"
        projectState.activeEnvironmentId = "env-1"
        projectState.luacheckArguments = "--std lua54"
        projectState.rocksServerUrl = "https://rocks.example/manifest"

        val element = XmlSerializer.serialize(projectState)
        val restored = XmlSerializer.deserialize(element, LuaToolchainProjectState::class.java)

        assertEquals(projectState.bindings, restored.bindings)
        assertEquals(1, restored.environments.size)
        val restoredEnv = restored.environments.first()
        assertEquals(environment.id, restoredEnv.id)
        assertEquals(environment.name, restoredEnv.name)
        assertEquals(environment.rootDir, restoredEnv.rootDir)
        assertEquals(environment.toolIds, restoredEnv.toolIds)
        assertEquals(projectState.activeEnvironmentId, restored.activeEnvironmentId)
        assertEquals(projectState.luacheckArguments, restored.luacheckArguments)
        assertEquals(projectState.rocksServerUrl, restored.rocksServerUrl)
    }

    // --- (b) legacy tolerance: today's tags load into the NEW classes without exception ---

    @Test
    fun legacyAppXmlLoadsWithoutExceptionAndToolsEmpty() {
        val legacyElement: Element = JDOMUtil.load(LEGACY_APP_XML)
        val restored = XmlSerializer.deserialize(legacyElement, LuaToolchainAppState::class.java)

        assertNotNull(restored)
        // No legacy tag shares a name with a new field (the app inventory was renamed to `tools`);
        // every legacy tag is silently ignored, so `tools` loads empty.
        assertTrue(restored.tools.isEmpty(), "legacy toolInventory/interpreters must not populate `tools`")
        assertTrue(restored.globalBindings.isEmpty(), "legacy globalToolBindings must not populate `globalBindings`")
    }

    @Test
    fun legacyProjectXmlLoadsWithoutExceptionAndEnvironmentsEmpty() {
        val legacyElement: Element = JDOMUtil.load(LEGACY_PROJECT_XML)
        val restored = XmlSerializer.deserialize(legacyElement, LuaToolchainProjectState::class.java)

        assertNotNull(restored)
        assertTrue(restored.environments.isEmpty(), "legacy hererocksEnvs must not populate `environments`")
        assertTrue(restored.bindings.isEmpty(), "legacy projectToolBindings must not populate `bindings`")
    }

    // --- (c) re-serialization drops every legacy tag name ---

    @Test
    fun reserializedAppXmlContainsNoLegacyTags() {
        val restored = XmlSerializer.deserialize(JDOMUtil.load(LEGACY_APP_XML), LuaToolchainAppState::class.java)
        val emitted = JDOMUtil.write(XmlSerializer.serialize(restored))

        LEGACY_TAG_NAMES.forEach { legacyTag ->
            assertTrue(
                !emitted.contains("\"$legacyTag\""),
                "re-serialized app XML must not contain legacy tag '$legacyTag'; was:\n$emitted",
            )
        }
    }

    @Test
    fun reserializedProjectXmlContainsNoLegacyTags() {
        val restored =
            XmlSerializer.deserialize(JDOMUtil.load(LEGACY_PROJECT_XML), LuaToolchainProjectState::class.java)
        val emitted = JDOMUtil.write(XmlSerializer.serialize(restored))

        LEGACY_TAG_NAMES.forEach { legacyTag ->
            assertTrue(
                !emitted.contains("\"$legacyTag\""),
                "re-serialized project XML must not contain legacy tag '$legacyTag'; was:\n$emitted",
            )
        }
    }

    // --- helpers ---

    private fun tool(id: String, kindId: String, path: String, version: String?): RegisteredToolState =
        RegisteredToolState().apply {
            this.id = id
            this.kindId = kindId
            this.path = path
            this.version = version
        }

    private fun assertToolEquals(expected: RegisteredToolState, actual: RegisteredToolState) {
        assertEquals(expected.id, actual.id)
        assertEquals(expected.kindId, actual.kindId)
        assertEquals(expected.path, actual.path)
        assertEquals(expected.version, actual.version)
        assertEquals(expected.origin, actual.origin)
        assertEquals(expected.environmentId, actual.environmentId)
    }

    companion object {
        /** Every deleted legacy tag name; none may survive re-serialization (design §2.6 step 4). */
        private val LEGACY_TAG_NAMES = listOf(
            "interpreters",
            "toolInventory",
            "globalToolBindings",
            "hererocksEnv",
            "hererocksEnvs",
            "interpreterMode",
            "interpreterModeMigrated",
            "explicitInterpreter",
            "explicitTarget",
            "activeEnvId",
            "projectToolBindings",
        )

        /**
         * Models today's real serialized app-level shape (LuaApplicationSettings.State:39-53):
         * `interpreters` (list of LuaInterpreter beans), `toolInventory` (old LuaTool shape),
         * `globalToolBindings` (Map<String,String>).
         */
        private val LEGACY_APP_XML =
            """
            <State>
              <option name="includeAllFieldsInCompletions" value="false" />
              <option name="enableTypeInference" value="true" />
              <option name="interpreters">
                <list>
                  <LuaInterpreter>
                    <option name="path" value="/usr/bin/lua" />
                  </LuaInterpreter>
                </list>
              </option>
              <option name="toolInventory">
                <list>
                  <LuaTool>
                    <option name="id" value="tool-1" />
                    <option name="path" value="/usr/local/bin/luacheck" />
                  </LuaTool>
                </list>
              </option>
              <option name="globalToolBindings">
                <map>
                  <entry key="LUACHECK" value="tool-1" />
                </map>
              </option>
            </State>
            """.trimIndent()

        /**
         * Models today's real serialized project-level shape (LuaProjectSettings.State:53-130):
         * `interpreter`, `interpreterMode`, `interpreterModeMigrated`, `explicitInterpreter`,
         * `explicitTarget`, `hererocksEnv` (singular deprecated), `hererocksEnvs`, `activeEnvId`,
         * `projectToolBindings`.
         */
        private val LEGACY_PROJECT_XML =
            """
            <State>
              <option name="languageLevel" value="LUA54" />
              <option name="interpreter">
                <LuaInterpreter>
                  <option name="path" value="/usr/bin/lua" />
                </LuaInterpreter>
              </option>
              <option name="interpreterMode" value="HEREROCKS_MANAGED" />
              <option name="interpreterModeMigrated" value="true" />
              <option name="explicitInterpreter">
                <LuaInterpreter>
                  <option name="path" value="/usr/bin/lua5.1" />
                </LuaInterpreter>
              </option>
              <explicitTarget>
                <option name="platform" value="STANDARD" />
                <option name="versionLabel" value="5.1" />
              </explicitTarget>
              <option name="hererocksEnv">
                <HererocksEnvState>
                  <option name="id" value="legacy-env" />
                </HererocksEnvState>
              </option>
              <option name="hererocksEnvs">
                <list>
                  <HererocksEnvState>
                    <option name="id" value="env-1" />
                    <option name="rootDir" value="/proj/.lunar/env-1" />
                  </HererocksEnvState>
                </list>
              </option>
              <option name="activeEnvId" value="env-1" />
              <option name="projectToolBindings">
                <map>
                  <entry key="LUACHECK" value="tool-1" />
                </map>
              </option>
            </State>
            """.trimIndent()
    }
}
