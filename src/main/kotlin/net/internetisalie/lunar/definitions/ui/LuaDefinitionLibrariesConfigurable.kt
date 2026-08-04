package net.internetisalie.lunar.definitions.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryEnabler
import net.internetisalie.lunar.settings.LuaProjectSettings
import javax.swing.JComponent

/**
 * Settings page listing the bundled definition catalog, one row per library (TARGET-08-06/-08).
 *
 * A deliberately thin shell: every decision — which rows exist, what is fetched, what a change
 * implies — lives in [LuaDefinitionLibraryEnabler], which is unit-tested. This class only builds
 * Swing and reads checkbox state, so nothing here needs a UI test to be trustworthy.
 *
 * Layout runs on the EDT and is cheap; the fetch [apply] triggers is dispatched to a background
 * task by the enabler (engineering contract §1).
 */
class LuaDefinitionLibrariesConfigurable(private val project: Project) : Configurable {

    private val enabler = LuaDefinitionLibraryEnabler(project)
    private val checkBoxes = LinkedHashMap<String, JBCheckBox>()

    override fun getDisplayName(): String = "Definition Libraries"

    override fun createComponent(): JComponent = panel {
        row {
            comment(
                "Type definitions for community Lua libraries, fetched on demand. Nothing is " +
                    "bundled with the plugin — each library is downloaded from its upstream " +
                    "project and cached per user. Licenses are the upstream projects' own.",
            )
        }
        enabler.rows().forEach { entry ->
            row {
                val box = JBCheckBox(entry.entry.displayName, entry.enabled)
                checkBoxes[entry.entry.id] = box
                cell(box)
                // TARGET-08-08: license + attribution are shown for every row, not just enabled
                // ones, so the obligation is visible before the user opts in.
                cell(JBLabel(if (entry.fetched) "Fetched" else "Not fetched").apply {
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                })
                cell(JBLabel(entry.entry.license).apply {
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                })
                cell(
                    HyperlinkLabel(entry.entry.attributionUrl).apply {
                        setHyperlinkTarget(entry.entry.attributionUrl)
                    },
                )
            }
        }
    }

    private fun selectedIds(): List<String> =
        checkBoxes.filterValues { it.isSelected }.keys.toList()

    override fun isModified(): Boolean =
        selectedIds().toSet() != LuaProjectSettings.getInstance(project).enabledDefinitionLibraries.toSet()

    override fun apply() {
        enabler.apply(selectedIds())
    }

    override fun reset() {
        val enabled = LuaProjectSettings.getInstance(project).enabledDefinitionLibraries.toSet()
        checkBoxes.forEach { (id, box) -> box.isSelected = id in enabled }
    }
}
