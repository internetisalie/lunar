package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import javax.swing.JComponent

/**
 * BUG-448 #6 and #7: both provisioning dialogs sized themselves from controls that declared no
 * width, so a normal project path scrolled out of view (`ome/mini/uiaudit/.lua`, `/.lua-matrix`) and
 * the matrix dialog came out narrower than its own title, which the window then rendered as
 * `Provision …on Matrix`.
 *
 * The assertions measure real rendered text through `FontMetrics` rather than restating the
 * constants the fix introduced — "a path fits" and "the title fits" are the claims being made.
 */
class LuaProvisionDialogWidthTest : ToolchainSettingsTestCase() {
    fun `test provision dialog root directory field fits a project path (BUG-448 #7)`() {
        checkProvisionDialogPathField()
    }

    fun `test matrix dialog base directory field fits a project path (BUG-448 #7)`() {
        checkMatrixDialogPathField()
    }

    fun `test matrix dialog is wider than its own title (BUG-448 #6)`() {
        checkMatrixDialogAdmitsTitle()
    }

    private fun checkProvisionDialogPathField() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val dialog = LuaProvisionDialog(project, initial = null)
            try {
                assertFitsSamplePath(dialog.rootDirFieldForTest())
            } finally {
                dialog.close(0)
            }
        }
    }

    private fun checkMatrixDialogPathField() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val dialog = LuaBatchProvisionDialog(project)
            try {
                assertFitsSamplePath(dialog.baseDirFieldForTest())
            } finally {
                dialog.close(0)
            }
        }
    }

    private fun checkMatrixDialogAdmitsTitle() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val dialog = LuaBatchProvisionDialog(project)
            try {
                assertAdmitsTitle(dialog.centerPanelForTest(), dialog.title ?: "")
            } finally {
                dialog.close(0)
            }
        }
    }

    private fun assertFitsSamplePath(field: TextFieldWithBrowseButton) {
        val editor = field.textField
        val needed = editor.getFontMetrics(editor.font).stringWidth(SAMPLE_PATH)
        assertTrue(
            "Path field is ${editor.preferredSize.width}px but `$SAMPLE_PATH` needs ${needed}px — " +
                "a path of ordinary length would scroll out of view",
            editor.preferredSize.width >= needed,
        )
    }

    private fun assertAdmitsTitle(
        panel: JComponent,
        title: String,
    ) {
        val needed = panel.getFontMetrics(panel.font).stringWidth(title) + JBUI.scale(TITLE_BAR_CHROME)
        assertTrue(
            "Center panel is ${panel.preferredSize.width}px but the title `$title` plus window " +
                "chrome needs ${needed}px — the title would be elided",
            panel.preferredSize.width >= needed,
        )
    }

    private companion object {
        /** A path of ordinary length; the audit's truncation was on one no longer than this. */
        const val SAMPLE_PATH = "/home/mini/projects/demo-lua/.lua-matrix"

        /** Icon, close button and frame insets a title bar spends before it reaches the text. */
        const val TITLE_BAR_CHROME = 120
    }
}
