package net.internetisalie.lunar.lang.format

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaLanguage

class LuaCodeStyleSettings(
    codeStyleSettings: CodeStyleSettings,
) : CustomCodeStyleSettings(
    LuaLanguage.id,
    codeStyleSettings,
)

class LuaCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
        return LuaCodeStyleSettings(settings)
    }

    override fun getLanguage(): Language {
        return LuaLanguage
    }

    override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable {
        return object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel {
                return LuaCodeStyleMainPanel(currentSettings, settings)
            }
        }
    }

    class LuaCodeStyleMainPanel(
        current: CodeStyleSettings,
        settings: CodeStyleSettings,
    ) : TabbedLanguageCodeStylePanel(
        LuaLanguage,
        current,
        settings
    )
}

class LuaLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) {
        when (settingsType) {
            SettingsType.INDENT_SETTINGS -> consumer.showStandardOptions(
                CodeStyleSettingsCustomizable.IndentOption.INDENT_SIZE.name,
                CodeStyleSettingsCustomizable.IndentOption.CONTINUATION_INDENT_SIZE.name,
            )
            SettingsType.BLANK_LINES_SETTINGS -> consumer.showStandardOptions()
            SettingsType.SPACING_SETTINGS -> consumer.showStandardOptions(
                CodeStyleSettingsCustomizable.SpacingOption.SPACE_WITHIN_BRACES.name,
                CodeStyleSettingsCustomizable.SpacingOption.SPACE_WITHIN_BRACKETS.name,
            );
            SettingsType.WRAPPING_AND_BRACES_SETTINGS -> consumer.showStandardOptions(
                CodeStyleSettingsCustomizable.WrappingOrBraceOption.KEEP_LINE_BREAKS.name,
            )
            SettingsType.COMMENTER_SETTINGS -> consumer.showStandardOptions(
                CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_AT_FIRST_COLUMN.name,
                CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_ADD_SPACE.name,
                CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_ADD_SPACE_ON_REFORMAT.name
            )
            else -> consumer.showStandardOptions()
        }
    }

    override fun getIndentOptionsEditor(): IndentOptionsEditor? {
        return SmartIndentOptionsEditor(this)
    }

    override fun getCodeSample(settingsType: SettingsType): String {
        return CodeStyleAbstractPanel
            .readFromFile(LuaBundle::class.java, "codeStyle.lua")
            .substringAfter("---\n")
    }

    override fun getLanguage(): Language {
        return LuaLanguage
    }
}
