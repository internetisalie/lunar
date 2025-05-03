package net.internetisalie.lunar.analysis.luacheck

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import net.internetisalie.lunar.LuaBundle

class LuaCheckSettingsPanel : BoundConfigurable(
    LuaBundle.message("luacheck.name")
) {
    override fun createPanel(): DialogPanel {
        val settings = LuaCheckSettings.getInstance()

        return panel {
            group(LuaBundle.message("luacheck.settings.execution")) {
                row {
                    textFieldWithBrowseButton(LuaBundle.message("luacheck.executable")) { chosenFile -> chosenFile.path }
                        .bindText(settings::executablePath)
                        .gap(RightGap.SMALL)
                        .label(LuaBundle.message("luacheck.executable"))
                }
                row {
                    link(LuaBundle.message("luacheck.download")) {}
                }
                row {
                    expandableTextField { it.joinToString(" ") }
                        .bindText(settings::arguments)
                        .gap(RightGap.SMALL)
                        .label(LuaBundle.message("luacheck.arguments"))
                }
            }
        }
    }
}