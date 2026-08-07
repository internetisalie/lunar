package net.internetisalie.lunar.definitions.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.AppExecutorUtil
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
class LuaDefinitionLibrariesConfigurable(
    private val project: Project,
) : Configurable {
    private val enabler = LuaDefinitionLibraryEnabler(project)
    private val checkBoxes = LinkedHashMap<String, JBCheckBox>()
    private val statusLabels = LinkedHashMap<String, JBLabel>()

    override fun getDisplayName(): String = "Definition Libraries"

    override fun createComponent(): JComponent =
        panel {
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
                    // Deliberately a placeholder: learning whether a library is cached costs disk
                    // access, and `createComponent` runs on the EDT where that is a prohibited slow
                    // operation (BUG-396). `loadStatuses()` fills these in from a pooled thread.
                    val status =
                        JBLabel(CHECKING).apply {
                            foreground = JBUI.CurrentTheme.Label.disabledForeground()
                        }
                    statusLabels[entry.entry.id] = status
                    cell(status)
                    cell(
                        JBLabel(entry.entry.license).apply {
                            foreground = JBUI.CurrentTheme.Label.disabledForeground()
                        },
                    )
                    cell(
                        HyperlinkLabel(entry.entry.attributionUrl).apply {
                            setHyperlinkTarget(entry.entry.attributionUrl)
                        },
                    )
                }
            }
        }

    /**
     * Fills in the Fetched/Not fetched column off the EDT, then updates the labels back on it.
     *
     * The page is fully usable while this runs — the checkboxes carry the decision; the status is
     * advisory. Guarded by [project] disposal so a settings page closed mid-scan cannot touch a
     * dead component tree.
     */
    private fun loadStatuses() {
        AppExecutorUtil.getAppExecutorService().execute {
            val fetched = runCatching { enabler.fetchedIds() }.getOrElse { emptySet() }
            ApplicationManager.getApplication().invokeLater({
                statusLabels.forEach { (id, label) ->
                    label.text = if (id in fetched) "Fetched" else "Not fetched"
                }
            }, project.disposed)
        }
    }

    private fun selectedIds(): List<String> = checkBoxes.filterValues { it.isSelected }.keys.toList()

    override fun isModified(): Boolean =
        selectedIds().toSet() != LuaProjectSettings.getInstance(project).enabledDefinitionLibraries.toSet()

    override fun apply() {
        enabler.apply(selectedIds())
    }

    override fun reset() {
        loadStatuses()
        val enabled = LuaProjectSettings.getInstance(project).enabledDefinitionLibraries.toSet()
        checkBoxes.forEach { (id, box) -> box.isSelected = id in enabled }
    }

    private companion object {
        /** Shown until the off-EDT cache scan reports back. */
        const val CHECKING = "checking…"
    }
}
