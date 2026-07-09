package net.internetisalie.lunar.analysis.luacheck

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry

class LuaCheckSettingsPanel : BoundConfigurable(
    LuaBundle.message("luacheck.name")
) {
    override fun createPanel(): DialogPanel {
        val registry = LuaToolchainRegistry.getInstance()
        var arguments = registry.kindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS)

        return panel {
            group(LuaBundle.message("luacheck.settings.execution")) {
                row {
                    expandableTextField { it.joinToString(" ") }
                        .bindText({ arguments }, { arguments = it })
                        .gap(RightGap.SMALL)
                        .label(LuaBundle.message("luacheck.arguments"))
                        .onApply {
                            registry.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, arguments)
                        }
                }
            }
        }
    }
}
