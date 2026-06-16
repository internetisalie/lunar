package net.internetisalie.lunar.tool

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Live-context tests for TOOL-02 resolution precedence in [LuaToolManager.getEffectiveTool]:
 * project binding > global default > first valid tool, with fallback when a bound id is stale
 * (TOOL-02-01/02/06). Uses a platform fixture so the application/project services exist; tool
 * binaries are created as real executable temp files so [LuaToolManager.getTools] keeps them valid.
 */
@RunWith(JUnit4::class)
class LuaToolBindingResolutionTest : BasePlatformTestCase() {

    private lateinit var luacheckA: File
    private lateinit var luacheckB: File

    override fun setUp() {
        super.setUp()
        luacheckA = newExecutable("luacheckA")
        luacheckB = newExecutable("luacheckB")

        val inventory = LuaApplicationSettings.instance.state.toolInventory
        inventory.clear()
        inventory.add(validTool("idA", luacheckA.absolutePath))
        inventory.add(validTool("idB", luacheckB.absolutePath))

        LuaApplicationSettings.instance.state.globalToolBindings.clear()
        LuaProjectSettings.getInstance(project).state.projectToolBindings.clear()
    }

    override fun tearDown() {
        try {
            LuaApplicationSettings.instance.state.toolInventory.clear()
            LuaApplicationSettings.instance.state.globalToolBindings.clear()
            LuaProjectSettings.getInstance(project).state.projectToolBindings.clear()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testFallsBackToFirstValidWhenNoBinding() {
        val tool = LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.LUACHECK)
        assertNotNull(tool)
        assertEquals("idA", tool!!.id)
    }

    @Test
    fun testGlobalBindingTakesPrecedenceOverFirstValid() {
        LuaToolManager.getInstance().setGlobalBinding(LuaToolType.LUACHECK, "idB")
        val tool = LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.LUACHECK)
        assertEquals("idB", tool!!.id)
    }

    @Test
    fun testProjectBindingOverridesGlobalBinding() {
        LuaToolManager.getInstance().setGlobalBinding(LuaToolType.LUACHECK, "idB")
        LuaProjectSettings.getInstance(project).state.projectToolBindings[LuaToolType.LUACHECK.name] = "idA"

        val tool = LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.LUACHECK)
        assertEquals("idA", tool!!.id)
    }

    @Test
    fun testStaleProjectBindingFallsBackToGlobal() {
        LuaToolManager.getInstance().setGlobalBinding(LuaToolType.LUACHECK, "idB")
        LuaProjectSettings.getInstance(project).state.projectToolBindings[LuaToolType.LUACHECK.name] = "does-not-exist"

        val tool = LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.LUACHECK)
        assertEquals("idB", tool!!.id)
    }

    @Test
    fun testTerminalEnvironmentServiceResolvesToolDirectory() {
        LuaToolManager.getInstance().setGlobalBinding(LuaToolType.LUACHECK, "idB")
        val dirs = LuaTerminalEnvironmentService.getInstance(project).getToolDirectories()
        assertTrue(dirs.any { it == luacheckB.parentFile.toPath() })
    }

    @Test
    fun testNullProjectResolvesGlobalOnly() {
        LuaToolManager.getInstance().setGlobalBinding(LuaToolType.LUACHECK, "idB")
        val tool = LuaToolManager.getInstance().getEffectiveTool(null, LuaToolType.LUACHECK)
        assertEquals("idB", tool!!.id)
    }

    private fun newExecutable(name: String): File {
        val f = File.createTempFile(name, "")
        f.writeText("#!/bin/sh\n")
        f.setExecutable(true)
        f.deleteOnExit()
        return f
    }

    private fun validTool(id: String, path: String) = LuaTool(
        id = id,
        type = LuaToolType.LUACHECK,
        name = "luacheck",
        path = path,
        version = "1.0.0",
        isValid = true,
    )
}
