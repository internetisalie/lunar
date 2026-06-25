package net.internetisalie.lunar.rocks.run

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

/**
 * Application Settings page for LuaRocks (ROCKS-06-08).
 *
 * Surfaces the `luarocks` executable path and the default server URL from
 * [LuaRocksSettings]. Registered under Settings → Tools in `plugin.xml`.
 * Follows the [net.internetisalie.lunar.analysis.luacheck.LuaCheckSettingsPanel] pattern
 * (a [BoundConfigurable] with Kotlin UI DSL `panel { }` + `bindText`).
 */
class LuaRocksSettingsConfigurable : BoundConfigurable("LuaRocks") {

    override fun createPanel(): DialogPanel {
        val settings = LuaRocksSettings.getInstance()

        return panel {
            group("Executable") {
                row {
                    textFieldWithBrowseButton("LuaRocks executable") { chosenFile -> chosenFile.path }
                        .bindText(settings::executablePath)
                        .gap(RightGap.SMALL)
                        .label("luarocks executable:")
                }
            }
            group("Registry") {
                row {
                    textField()
                        .bindText(settings::serverUrl)
                        .gap(RightGap.SMALL)
                        .label("Default server URL:")
                        .comment("Empty = luarocks.org default. Overridable per project.")
                }
            }
        }
    }
}
