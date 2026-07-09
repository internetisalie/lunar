package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry

private const val CONFIGURABLE_ID = "net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable"

/**
 * TOOLING-06 §2.1. The app-level *Toolchain* page under *Lua*: hosts the live inventory table
 * (§2.2) plus the buffered app-default kind-option fields (Luacheck arguments, LuaRocks default
 * server URL) bound to [LuaToolchainRegistry.kindOption]/[LuaToolchainRegistry.setKindOption].
 * The DSL setters call the notify-firing mutators — no manual topic fire; unchanged fields stay
 * silent (§3.6). The table auto-refreshes off the [LuaToolchainListener.TOPIC] subscription (§3.1).
 */
class LuaToolchainConfigurable : BoundSearchableConfigurable(
    displayName = "Toolchain",
    helpTopic = "settings.lua.toolchain",
    _id = CONFIGURABLE_ID
) {

    private val registry: LuaToolchainRegistry
        get() = LuaToolchainRegistry.getInstance()

    private val inventoryTable = LuaToolchainInventoryTable()

    private var luacheckArguments = registry.kindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS)
    private var luaRocksServerUrl = registry.kindOption(LuaKindOptionKeys.LUAROCKS_SERVER_URL)

    override fun createPanel(): DialogPanel {
        subscribeToToolchainEvents()
        return panel {
            row {
                cell(inventoryTable.component).resizableColumn()
            }.resizableRow()
            group(LuaBundle.message("luacheck.name")) {
                row(LuaBundle.message("luacheck.arguments")) {
                    expandableTextField { it.joinToString(" ") }
                        .bindText({ luacheckArguments }, { luacheckArguments = it })
                        .gap(RightGap.SMALL)
                        .onApply {
                            registry.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, luacheckArguments)
                        }
                }
            }
            group("LuaRocks") {
                row("Default server URL:") {
                    textField()
                        .bindText({ luaRocksServerUrl }, { luaRocksServerUrl = it })
                        .gap(RightGap.SMALL)
                        .comment("Empty = luarocks.org default. Overridable per project.")
                        .onApply {
                            registry.setKindOption(LuaKindOptionKeys.LUAROCKS_SERVER_URL, luaRocksServerUrl)
                        }
                }
            }
        }
    }

    private fun subscribeToToolchainEvents() {
        val panelDisposable = disposable ?: return
        val connection = ApplicationManager.getApplication().messageBus.connect(panelDisposable)
        connection.subscribe(
            LuaToolchainListener.TOPIC,
            object : LuaToolchainListener {
                override fun toolchainChanged(event: LuaToolchainEvent) {
                    ApplicationManager.getApplication().invokeLater(
                        { inventoryTable.refresh() },
                        ModalityState.any()
                    )
                }
            }
        )
    }
}
