package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TC 15: the `Lunar.Toolchain.EnvironmentGroup` resolves with its five provisioning actions plus
 * the migrated `Lunar.Toolchain.RunMatrix`, and the old hererocks action ids are unregistered.
 */
class LuaToolchainActionRegistrationTest : BasePlatformTestCase() {

    fun `test provisioning actions are registered`() {
        val manager = ActionManager.getInstance()
        val ids = listOf(
            "Lunar.Toolchain.Provision",
            "Lunar.Toolchain.ChangeVersions",
            "Lunar.Toolchain.Recreate",
            "Lunar.Toolchain.Remove",
            "Lunar.Toolchain.BatchProvision",
        )
        ids.forEach { id -> assertNotNull("action $id must resolve", manager.getAction(id)) }
    }

    fun `test group contains the five actions and RunMatrix`() {
        val manager = ActionManager.getInstance()
        val group = manager.getAction("Lunar.Toolchain.EnvironmentGroup") as? DefaultActionGroup
        assertNotNull("group must resolve", group)
        val childIds = group!!.getChildren(null).mapNotNull { manager.getId(it) }.toSet()
        listOf(
            "Lunar.Toolchain.Provision",
            "Lunar.Toolchain.ChangeVersions",
            "Lunar.Toolchain.Recreate",
            "Lunar.Toolchain.Remove",
            "Lunar.Toolchain.BatchProvision",
            "Lunar.Toolchain.RunMatrix",
        ).forEach { id -> assertTrue("group must contain $id", id in childIds) }
    }

    fun `test old hererocks env actions are not registered`() {
        val manager = ActionManager.getInstance()
        listOf(
            "Lunar.Hererocks.Create",
            "Lunar.Hererocks.Upgrade",
            "Lunar.Hererocks.Recreate",
            "Lunar.Hererocks.Remove",
            "Lunar.Hererocks.BatchProvision",
            "Lunar.Hererocks.RunMatrix",
        ).forEach { id -> assertNull("action $id must be unregistered", manager.getAction(id)) }
    }
}
