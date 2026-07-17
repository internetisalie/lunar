package net.internetisalie.lunar.toolchain.provision

import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import com.intellij.testFramework.EdtTestUtil

/**
 * BUG-371: the "Change Versions" flow (prefill with initial != null) must disable rootDirField so
 * the user cannot silently re-root an existing environment. Tests the dialog's enable/disable state
 * by constructing it on the EDT inside the light-fixture project.
 */
class LuaProvisionDialogRootDirTest : ToolchainSettingsTestCase() {

    private fun makeRequest(): LuaProvisionRequest =
        LuaProvisionRequest(
            environmentName = "lua-5.4.7",
            rootDir = "/p/lua-5.4",
            items = listOf(LuaProvisionItem("lua", "5.4.7")),
        )

    fun `test rootDirField is disabled when initial is non-null (Change Versions BUG-371)`() {
        val request = makeRequest()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val dialog = LuaProvisionDialog(project, initial = request)
            val field = dialog.rootDirFieldForTest()
            assertFalse("rootDirField must be disabled when prefilled (BUG-371)", field.isEnabled)
            dialog.close(0)
        }
    }

    fun `test rootDirField is enabled when initial is null (fresh provision BUG-371)`() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val dialog = LuaProvisionDialog(project, initial = null)
            val field = dialog.rootDirFieldForTest()
            assertTrue("rootDirField must be enabled for fresh provision", field.isEnabled)
            dialog.close(0)
        }
    }

    override fun tearDown() {
        LuaToolchainProjectSettings.getInstance(project).loadState(LuaToolchainProjectState())
        super.tearDown()
    }
}
