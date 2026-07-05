package net.internetisalie.lunar.rocks.init

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.rocks.env.HererocksFlavor

/** ROCKS-17: New Project wizard peer — flavor/version/provision interactions and getSettings. */
class LuaRocksGeneratorPeerTest : BasePlatformTestCase() {

    private fun versionLabels(peer: LuaRocksGeneratorPeer): List<String> =
        (0 until peer.versionCombo.itemCount).map { peer.versionCombo.getItemAt(it).label }

    fun testDefaults() {
        val s = LuaRocksGeneratorPeer().settings
        assertEquals(HererocksFlavor.PUC, s.flavor)
        assertEquals("5.4", s.luaVersion)
        assertFalse(s.provisionHererocks)
        assertEquals("", s.interpreterPath)
    }

    fun testPucVersionsIncludeLua55() {
        val peer = LuaRocksGeneratorPeer()
        assertTrue("PUC version list must offer 5.5", versionLabels(peer).contains("5.5"))
    }

    fun testProvisionToggleDisablesInterpreterAndSetsFlag() {
        val peer = LuaRocksGeneratorPeer()
        assertTrue("existing-interpreter combo enabled when not provisioning", peer.interpreterCombo.isEnabled)

        peer.provisionCheck.doClick()

        assertFalse("provisioning disables the existing-interpreter combo", peer.interpreterCombo.isEnabled)
        assertTrue(peer.settings.provisionHererocks)
    }

    fun testFlavorSwitchRepopulatesVersions() {
        val peer = LuaRocksGeneratorPeer()

        peer.flavorCombo.selectedItem = HererocksFlavor.LUAJIT

        val labels = versionLabels(peer)
        assertTrue("LuaJIT versions offered", labels.contains("2.1"))
        assertFalse("PUC versions cleared when LuaJIT selected", labels.contains("5.4"))
        assertEquals("2.1", peer.settings.luaVersion)
    }
}
