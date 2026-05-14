package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.InlayHintsCustomSettingsProvider
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.LuaBundle
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Provides custom settings UI for Lua inlay hints, integrated into the platform's settings.
 */
class LuaInlayHintsCustomSettingsProvider : InlayHintsCustomSettingsProvider<Int> {
    private var thresholdField: JBTextField? = null

    override fun createComponent(project: Project, language: Language): JComponent {
        val currentThreshold = LuaInlayHintsSettings.instance.state.largeFileThreshold
        val field = JBTextField(currentThreshold.toString(), 10)
        field.toolTipText = LuaBundle.message("lua.type.hints.performance.threshold.desc")
        thresholdField = field

        val performancePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(LuaBundle.message("lua.type.hints.performance.threshold"), field)
            .panel
        performancePanel.border = IdeBorderFactory.createTitledBorder("Performance", false)

        val mainPanel = JPanel(java.awt.BorderLayout())
        mainPanel.add(performancePanel, java.awt.BorderLayout.NORTH)
        return mainPanel
    }

    override fun isDifferentFrom(project: Project, settings: Int): Boolean {
        val currentText = thresholdField?.text?.toIntOrNull() ?: 10000
        return currentText != settings
    }

    override fun getSettingsCopy(): Int {
        return LuaInlayHintsSettings.instance.state.largeFileThreshold
    }

    override fun putSettings(project: Project, settings: Int, language: Language) {
        // Not used by the platform for declarative hints
    }

    override fun persistSettings(project: Project, settings: Int, language: Language) {
        val newThreshold = thresholdField?.text?.toIntOrNull()?.coerceIn(100, 100000) ?: 10000
        LuaInlayHintsSettings.instance.state.largeFileThreshold = newThreshold
    }
}
