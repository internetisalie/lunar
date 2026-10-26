package net.internetisalie.lunar.rocks.init

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JRadioButton

/**
 * BUG-530: the New Project page must build under the Kotlin UI DSL, which rejects a radio button
 * placed outside a `buttonsGroup`. Light fixture tests run on the EDT, as the DSL requires.
 */
class LuaRocksGeneratorPeerPanelTest : BasePlatformTestCase() {
    private fun buildPage(peer: LuaRocksGeneratorPeer): JComponent = peer.getComponent(TextFieldWithBrowseButton()) {}

    private fun radioButtons(page: JComponent): Map<String, JRadioButton> =
        UIUtil.findComponentsOfType(page, JRadioButton::class.java).associateBy { it.text }

    fun testPageBuildsWithBothProjectTypeRadioButtons() {
        val buttons = radioButtons(buildPage(LuaRocksGeneratorPeer()))

        assertEquals(setOf("Library", "Application"), buttons.keys)
        assertEquals("exactly one project type selected", 1, buttons.values.count { it.isSelected })
    }

    fun testProjectTypeRadioButtonsAreMutuallyExclusiveOnThePage() {
        val peer = LuaRocksGeneratorPeer()
        val buttons = radioButtons(buildPage(peer))
        val library = checkNotNull(buttons["Library"])
        val application = checkNotNull(buttons["Application"])

        application.doClick()

        assertTrue(application.isSelected)
        assertFalse("selecting Application deselects Library", library.isSelected)
        assertEquals(RockType.APPLICATION, peer.settings.type)
    }
}
