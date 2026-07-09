package net.internetisalie.lunar.rocks.run

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry

/**
 * Application Settings page for LuaRocks (ROCKS-06-08).
 *
 * The executable is now resolved via the TOOLING-01/02 toolchain stack (see the Toolchain
 * page), so this interim page only surfaces the default registry server URL, bound to the
 * TOOLING-02 kind-scoped [LuaKindOptionKeys.LUAROCKS_SERVER_URL] option. The page itself is
 * folded into the consolidated settings tree by TOOLING-06.
 */
class LuaRocksSettingsConfigurable : BoundConfigurable("LuaRocks") {

    override fun createPanel(): DialogPanel {
        val registry = LuaToolchainRegistry.getInstance()
        var serverUrl = registry.kindOption(LuaKindOptionKeys.LUAROCKS_SERVER_URL)

        return panel {
            group("Registry") {
                row {
                    textField()
                        .bindText({ serverUrl }, { serverUrl = it })
                        .gap(RightGap.SMALL)
                        .label("Default server URL:")
                        .comment("Empty = luarocks.org default. Overridable per project.")
                        .onApply {
                            registry.setKindOption(LuaKindOptionKeys.LUAROCKS_SERVER_URL, serverUrl)
                        }
                }
            }
        }
    }
}
