package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.settings.InterpreterMode
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import net.internetisalie.lunar.tool.LuaToolType
import java.io.File
import java.nio.file.Files

/** Phase 4 + ROCKS-16: bind/unbind against a fixture env dir, mode-aware (TC-6). */
class HererocksEnvBinderTest : EnvSettingsTestCase() {

    private lateinit var envDir: File

    override fun setUp() {
        super.setUp()
        val root = Files.createTempDirectory("hererocks-bind-test").toFile()
        envDir = File(root, ".lua")
        val bin = File(envDir, "bin").also { it.mkdirs() }
        writeScript(File(bin, "lua"))
        writeScript(File(bin, "luarocks"))
    }

    override fun tearDown() {
        try {
            envDir.parentFile?.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun writeScript(file: File) {
        file.writeText("#!/bin/sh\necho fake\n")
        file.setExecutable(true)
    }

    private fun spec(luaVersion: String = "5.4") =
        HererocksEnvState(directory = envDir.absolutePath, flavor = HererocksFlavor.PUC, luaVersion = luaVersion)

    private fun luaPath() = File(envDir, "bin/lua").absolutePath

    /** ROCKS-16: in Managed mode a bind repoints the interpreter, the LUAROCKS tool, AND the target. */
    fun testBindManagedWiresInterpreterToolAndTarget() {
        if (SystemInfo.isWindows) return
        val settings = LuaProjectSettings.getInstance(project)
        settings.state.interpreterMode = InterpreterMode.HEREROCKS_MANAGED

        var changeCount = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() { changeCount++ }
            },
        )

        HererocksEnvBinder.bind(project, spec(luaVersion = "5.1"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertTrue("bind must fire LuaSettingsChangedListener.TOPIC", changeCount >= 1)
        assertNotNull(
            "LUAROCKS binding must be set",
            settings.state.projectToolBindings[LuaToolType.LUAROCKS.name],
        )
        assertEquals(luaPath(), settings.state.interpreter?.path)
        // Cascade: PUC 5.1 → STANDARD/5.1 → LUA51 language level.
        assertEquals(LuaPlatform.STANDARD, settings.state.getTarget().platform)
        assertEquals("5.1", settings.state.getTarget().version.label)
        assertEquals(LuaLanguageLevel.LUA51, settings.state.languageLevel)
    }

    /** ROCKS-16: in Explicit mode a bind binds only the LUAROCKS tool; interpreter/target untouched. */
    fun testBindExplicitBindsToolButNotInterpreterOrTarget() {
        if (SystemInfo.isWindows) return
        val settings = LuaProjectSettings.getInstance(project)
        // Explicit (default via reset); pre-set a manual interpreter the bind must not clobber.
        val manual = LuaInterpreter(path = "/usr/bin/lua")
        settings.state.interpreter = manual
        val levelBefore = settings.state.languageLevel

        HererocksEnvBinder.bind(project, spec(luaVersion = "5.1"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertNotNull(
            "LUAROCKS binding must be set even in Explicit mode",
            settings.state.projectToolBindings[LuaToolType.LUAROCKS.name],
        )
        assertEquals("manual interpreter must survive an Explicit bind", "/usr/bin/lua", settings.state.interpreter?.path)
        assertEquals("target/language level must not move in Explicit mode", levelBefore, settings.state.languageLevel)
    }

    /** ROCKS-16: a manually chosen interpreter survives bind AND unbind while in Explicit mode. */
    fun testExplicitInterpreterSurvivesBindAndUnbind() {
        if (SystemInfo.isWindows) return
        val settings = LuaProjectSettings.getInstance(project)
        val manual = LuaInterpreter(path = "/usr/bin/lua")
        settings.state.interpreter = manual

        settings.upsertAndActivate(project, spec().copy(id = "A"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEquals("interpreter survives bind", "/usr/bin/lua", settings.state.interpreter?.path)

        HererocksEnvBinder.unbind(project, deleteDir = false)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("interpreter survives unbind in Explicit mode", "/usr/bin/lua", settings.state.interpreter?.path)
        assertNull(settings.state.projectToolBindings[LuaToolType.LUAROCKS.name])
        assertTrue("env removed from set on unbind", settings.resolveAllEnvs().isEmpty())
    }

    /** ROCKS-16: unbinding in Managed mode restores the stashed explicit overlay and flips to Explicit. */
    fun testUnbindManagedRestoresExplicitOverlay() {
        if (SystemInfo.isWindows) return
        val settings = LuaProjectSettings.getInstance(project)
        // Simulate having entered Managed mode from an explicit STANDARD/5.2 + manual interpreter.
        settings.state.interpreterMode = InterpreterMode.HEREROCKS_MANAGED
        settings.state.explicitInterpreter = LuaInterpreter(path = "/usr/bin/lua")
        settings.state.explicitTarget =
            LuaProjectSettings.TargetState.from(net.internetisalie.lunar.platform.target.Target(
                LuaPlatform.STANDARD,
                net.internetisalie.lunar.platform.target.PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, "5.2")!!,
            ))

        settings.upsertAndActivate(project, spec(luaVersion = "5.1").copy(id = "A"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        // Managed bind derived the env interpreter + 5.1 target.
        assertEquals(luaPath(), settings.state.interpreter?.path)
        assertEquals(LuaLanguageLevel.LUA51, settings.state.languageLevel)

        HererocksEnvBinder.unbind(project, deleteDir = false)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("interpreter restored to explicit overlay", "/usr/bin/lua", settings.state.interpreter?.path)
        assertEquals("target restored to explicit overlay", "5.2", settings.state.getTarget().version.label)
        assertEquals(LuaLanguageLevel.LUA52, settings.state.languageLevel)
        assertEquals("mode flips back to Explicit on unbind", InterpreterMode.EXPLICIT, settings.state.interpreterMode)
        assertNull(settings.state.projectToolBindings[LuaToolType.LUAROCKS.name])
    }

    /** ROCKS-16: Managed unbind with no stashed overlay clears the interpreter (project default). */
    fun testUnbindManagedWithoutOverlayClearsInterpreter() {
        if (SystemInfo.isWindows) return
        val settings = LuaProjectSettings.getInstance(project)
        settings.state.interpreterMode = InterpreterMode.HEREROCKS_MANAGED

        settings.upsertAndActivate(project, spec().copy(id = "A"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEquals(luaPath(), settings.state.interpreter?.path)

        HererocksEnvBinder.unbind(project, deleteDir = false)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertNull(settings.state.interpreter)
        assertNull(settings.state.projectToolBindings[LuaToolType.LUAROCKS.name])
        assertTrue(settings.resolveAllEnvs().isEmpty())
        assertEquals(InterpreterMode.EXPLICIT, settings.state.interpreterMode)
    }

    /** ROCKS-16: toggling Explicit→Managed with no active env stashes the overlay; Managed→Explicit restores. */
    fun testModeToggleStashesAndRestoresOverlay() {
        val settings = LuaProjectSettings.getInstance(project)
        val manual = LuaInterpreter(path = "/usr/bin/lua")
        settings.state.interpreter = manual
        settings.state.setTarget(net.internetisalie.lunar.platform.target.Target(
            LuaPlatform.STANDARD,
            net.internetisalie.lunar.platform.target.PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, "5.2")!!,
        ))

        settings.setInterpreterModeAndNotify(project, InterpreterMode.HEREROCKS_MANAGED)
        assertEquals(InterpreterMode.HEREROCKS_MANAGED, settings.state.interpreterMode)
        assertEquals("/usr/bin/lua", settings.state.explicitInterpreter?.path)
        assertEquals("5.2", settings.state.explicitTarget?.versionLabel)

        settings.setInterpreterModeAndNotify(project, InterpreterMode.EXPLICIT)
        assertEquals(InterpreterMode.EXPLICIT, settings.state.interpreterMode)
        assertEquals("/usr/bin/lua", settings.state.interpreter?.path)
        assertEquals("5.2", settings.state.getTarget().version.label)
    }

    /** ROCKS-16: flavor + version → Target mapping (authoritative, with normalization + fallback). */
    fun testEnvToTargetMapping() {
        assertEquals(LuaPlatform.STANDARD, HererocksEnvState(luaVersion = "5.1").toTarget().platform)
        assertEquals("5.1", HererocksEnvState(luaVersion = "5.1").toTarget().version.label)
        assertEquals("5.4", HererocksEnvState(luaVersion = "5.4.6").toTarget().version.label)
        assertEquals(
            LuaPlatform.LUAJIT,
            HererocksEnvState(flavor = HererocksFlavor.LUAJIT, luaVersion = "2.1").toTarget().platform,
        )
        assertEquals(
            "2.1",
            HererocksEnvState(flavor = HererocksFlavor.LUAJIT, luaVersion = "2.1.0-beta3").toTarget().version.label,
        )
        // Unknown PUC version falls back to the STANDARD platform default (first registered entry).
        assertEquals(LuaPlatform.STANDARD, HererocksEnvState(luaVersion = "9.9").toTarget().platform)
    }
}
