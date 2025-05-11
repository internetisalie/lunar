package net.internetisalie.lunar.lang.format

import com.intellij.application.options.*
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.SpacingOption
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.LuaLanguage

class LuaCodeStyleSettings(
    codeStyleSettings: CodeStyleSettings,
) : CustomCodeStyleSettings(
    LuaLanguage.id,
    codeStyleSettings,
) {
    companion object {
        fun getInstance(settings : CodeStyleSettings) : LuaCodeStyleSettings? {
            return settings.getCustomSettings(LuaCodeStyleSettings::class.java)
        }
    }
}

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
            SettingsType.SPACING_SETTINGS -> {
                val customizableOptions = CodeStyleSettingsCustomizableOptions.getInstance()
                consumer.showStandardOptions(
                    SpacingOption.SPACE_AFTER_COMMA.name,
                    SpacingOption.SPACE_WITHIN_PARENTHESES.name,
                    SpacingOption.SPACE_WITHIN_BRACKETS.name,
                    SpacingOption.SPACE_WITHIN_BRACES.name,

                    SpacingOption.SPACE_AROUND_ASSIGNMENT_OPERATORS.name,
                    SpacingOption.SPACE_AROUND_LOGICAL_OPERATORS.name,
                    SpacingOption.SPACE_AROUND_EQUALITY_OPERATORS.name,
                    SpacingOption.SPACE_AROUND_RELATIONAL_OPERATORS.name,
                    SpacingOption.SPACE_AROUND_BITWISE_OPERATORS.name,
                    SpacingOption.SPACE_AROUND_ADDITIVE_OPERATORS.name,
                    SpacingOption.SPACE_AROUND_MULTIPLICATIVE_OPERATORS.name,
                    SpacingOption.SPACE_AROUND_SHIFT_OPERATORS.name,
                    SpacingOption.SPACE_AROUND_UNARY_OPERATOR.name
                )
//                consumer.showCustomOption(
//                    LuaCodeStyleSettings::class.java,
//                    SpacingOption.SPACE_BEFORE_SEMICOLON.name,
//                    LuaBundle.message("codeStyle.spacing.beforeSemicolon"),
//                    customizableOptions.SPACES_OTHER,
//                )
//                consumer.showCustomOption(
//                    LuaCodeStyleSettings::class.java,
//                    SpacingOption.SPACE_AFTER_SEMICOLON.name,
//                    LuaBundle.message("codeStyle.spacing.afterSemicolon"),
//                    customizableOptions.SPACES_OTHER,
//                )
            }
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
