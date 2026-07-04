package net.internetisalie.lunar.rocks.env

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings

/** Phase 3: widget label + popup model (TC-6). */
class LuaEnvStatusBarWidgetTest : BasePlatformTestCase() {

    fun testTextForActiveEnv() {
        val settings = LuaProjectSettings.getInstance(project)
        val b = HererocksEnvState(id = "B", directory = "/p/b", flavor = HererocksFlavor.PUC, luaVersion = "5.3")
        settings.loadState(
            LuaProjectSettings.State().also {
                it.hererocksEnvs = mutableListOf(b)
                it.activeEnvId = "B"
            },
        )
        assertEquals("PUC 5.3", LuaEnvStatusBarWidget(project).getText())
    }

    fun testTextForEmptySet() {
        val settings = LuaProjectSettings.getInstance(project)
        settings.loadState(LuaProjectSettings.State())
        assertEquals(LuaEnvStatusBarWidget.NO_ENV_TEXT, LuaEnvStatusBarWidget(project).getText())
    }

    fun testPopupModelListsAllEnvsPlusAddItem() {
        val a = HererocksEnvState(id = "A", directory = "/p/a", luaVersion = "5.3")
        val b = HererocksEnvState(id = "B", directory = "/p/b", luaVersion = "5.4")
        val items = LuaEnvStatusBarWidget.popupItems(listOf(a, b))

        assertEquals(3, items.size)
        assertEquals(listOf(a, b), items.dropLast(1))
        assertEquals(LuaEnvStatusBarWidget.ADD_ENV_TEXT, items.last())
        assertFalse(LuaEnvStatusBarWidget.isActive(a, "B"))
        assertTrue(LuaEnvStatusBarWidget.isActive(b, "B"))
        assertFalse(LuaEnvStatusBarWidget.isActive(LuaEnvStatusBarWidget.ADD_ENV_TEXT, "B"))
    }
}
