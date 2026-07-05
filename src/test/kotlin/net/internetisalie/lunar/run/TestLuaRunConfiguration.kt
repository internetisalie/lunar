package net.internetisalie.lunar.run

import com.intellij.openapi.ui.ComboBox
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.customizeLuaInterpreterComboBox
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestLuaRunConfiguration : BaseDocumentTest() {

    @Test
    fun testOptionsPersistence() {
        val project = myFixture.project
        val configType = LuaRunConfigurationType()
        val factory = LuaRunConfigurationFactory(configType)
        val config = LuaRunConfiguration(project, factory, "Test Config")

        config.scriptName = "myscript.lua"
        config.workingDirectory = "/home/user/work"
        config.programArguments = "--arg1 val1"

        assertEquals("myscript.lua", config.scriptName)
        assertEquals("/home/user/work", config.workingDirectory)
        assertEquals("--arg1 val1", config.programArguments)
    }

    /** ROCKS-16 follow-up: an unset config resolves to the project interpreter; explicit wins. */
    @Test
    fun testResolveInterpreterFallsBackToProjectInterpreter() {
        val project = myFixture.project
        val settings = LuaProjectSettings.getInstance(project)
        val original = settings.state.interpreter
        try {
            settings.state.interpreter = LuaInterpreter(path = "/env/.lua/bin/lua")
            val config = LuaRunConfiguration(project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")

            // No explicit interpreter on the config -> falls back to the project interpreter.
            assertNull(config.interpreter)
            assertEquals("/env/.lua/bin/lua", config.resolveInterpreter()?.path)

            // An explicit config interpreter takes precedence over the project one.
            config.interpreter = LuaInterpreter(path = "/usr/bin/lua")
            assertEquals("/usr/bin/lua", config.resolveInterpreter()?.path)
        } finally {
            settings.state.interpreter = original
        }
    }

    /** ROCKS-16 follow-up: the interpreter dropdown must list the project interpreter as an option. */
    @Test
    fun testInterpreterComboOffersProjectInterpreter() {
        val project = myFixture.project
        val settings = LuaProjectSettings.getInstance(project)
        val original = settings.state.interpreter
        try {
            settings.state.interpreter = LuaInterpreter(path = "/env/.lua/bin/lua")
            val combo = ComboBox<LuaInterpreter>()
            customizeLuaInterpreterComboBox(project, combo)
            val paths = (0 until combo.model.size).mapNotNull { combo.model.getElementAt(it)?.path }
            assertTrue(
                paths.contains("/env/.lua/bin/lua"),
                "dropdown must offer the project interpreter; model had: $paths",
            )
        } finally {
            settings.state.interpreter = original
        }
    }

    /** Uses the REAL LuaRunSettingsEditor end-to-end (construct + resetEditorFrom), like the IDE. */
    @Test
    fun testRealRunSettingsEditorOffersProjectInterpreter() {
        val project = myFixture.project
        val settings = LuaProjectSettings.getInstance(project)
        val app = LuaApplicationSettings.instance
        val origProj = settings.state.interpreter
        val origGlobals = app.state.interpreters
        // A real, executable, non-registered interpreter path (inside /tmp — an allowed test VFS
        // root) so the editor's background `identify` runs cleanly. Its being absent from the global
        // list is what drives the DocumentListener rebuild — the path that used to drop the project
        // interpreter (the bug reproduced live in the container).
        val fakeLua = java.io.File.createTempFile("lunar-fake-lua", "", java.io.File("/tmp")).apply {
            writeText("#!/bin/sh\necho \"Lua 5.1.5\"\n")
            setExecutable(true)
            deleteOnExit()
        }
        try {
            app.state.interpreters = emptyList()
            settings.state.interpreter = LuaInterpreter(path = "\$PROJECT_DIR\$/.lua/bin/lua")

            val editor = LuaRunSettingsEditor(project)
            val config = LuaRunConfiguration(project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")
            config.interpreter = LuaInterpreter(path = fakeLua.absolutePath)

            val reset = LuaRunSettingsEditor::class.java.getDeclaredMethod("resetEditorFrom", LuaRunConfiguration::class.java)
            reset.isAccessible = true
            reset.invoke(editor, config)

            val field = LuaRunSettingsEditor::class.java.getDeclaredField("interpreterField")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val combo = field.get(editor) as ComboBox<LuaInterpreter>
            val paths = (0 until combo.model.size).mapNotNull { combo.model.getElementAt(it)?.path }
            assertTrue(
                paths.contains("\$PROJECT_DIR\$/.lua/bin/lua"),
                "real editor dropdown must offer the project interpreter; model=$paths",
            )
        } finally {
            settings.state.interpreter = origProj
            app.state.interpreters = origGlobals
            fakeLua.delete()
        }
    }

    /** Replicates the real editor flow: customize, then the editor selects the config's stored global. */
    @Test
    fun testDropdownRetainsProjectInterpreterAfterEditorSelectsGlobal() {
        val project = myFixture.project
        val settings = LuaProjectSettings.getInstance(project)
        val app = LuaApplicationSettings.instance
        val origProj = settings.state.interpreter
        val origGlobals = app.state.interpreters
        try {
            val global = LuaInterpreter(path = "/usr/local/bin/lua")
            app.state.interpreters = listOf(global)
            settings.state.interpreter = LuaInterpreter(path = "\$PROJECT_DIR\$/.lua/bin/lua")

            val combo = ComboBox<LuaInterpreter>()
            customizeLuaInterpreterComboBox(project, combo)
            // Simulate SettingsEditor.resetEditorFrom selecting the config's stored (global) interpreter.
            combo.item = global

            val paths = (0 until combo.model.size).mapNotNull { combo.model.getElementAt(it)?.path }
            assertTrue(
                paths.contains("\$PROJECT_DIR\$/.lua/bin/lua"),
                "managed interpreter must stay selectable after the editor selects a global; model=$paths",
            )
        } finally {
            settings.state.interpreter = origProj
            app.state.interpreters = origGlobals
        }
    }
}
