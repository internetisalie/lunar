package net.internetisalie.lunar.rocks.init

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * TOOLING-05 §2.8: New Project wizard peer — runtime-kind/version/provision interactions and
 * getSettings on the toolchain shape (kindId + version + RUNTIME combo).
 */
class LuaRocksGeneratorPeerTest : BasePlatformTestCase() {

    private fun versionLabels(peer: LuaRocksGeneratorPeer): List<String> =
        (0 until peer.versionCombo.itemCount).map { peer.versionCombo.getItemAt(it).label }

    fun testDefaults() {
        val s = LuaRocksGeneratorPeer().settings
        assertEquals(WizardRuntimeKinds.LUA, s.kindId)
        assertEquals("5.4", s.luaVersion)
        assertFalse(s.provisionEnvironment)
        assertEquals("", s.interpreterPath)
    }

    fun testLuaVersionsIncludeLua55() {
        val peer = LuaRocksGeneratorPeer()
        assertTrue("Lua version list must offer 5.5", versionLabels(peer).contains("5.5"))
    }

    fun testProvisionToggleDisablesInterpreterAndSetsFlag() {
        val peer = LuaRocksGeneratorPeer()
        assertTrue("existing-interpreter combo enabled when not provisioning", peer.interpreterCombo.isEnabled)

        peer.provisionCheck.doClick()

        assertFalse("provisioning disables the existing-interpreter combo", peer.interpreterCombo.isEnabled)
        assertTrue(peer.settings.provisionEnvironment)
    }

    fun testKindSwitchRepopulatesVersions() {
        val peer = LuaRocksGeneratorPeer()

        peer.kindCombo.selectedItem = WizardRuntimeKinds.LUAJIT

        val labels = versionLabels(peer)
        assertTrue("LuaJIT versions offered", labels.contains("2.1"))
        assertFalse("Lua versions cleared when LuaJIT selected", labels.contains("5.4"))
        assertEquals("2.1", peer.settings.luaVersion)
    }
}
