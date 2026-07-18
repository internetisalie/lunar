package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import javax.swing.DefaultComboBoxModel

private const val CONFIGURABLE_ID = "net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable"

/**
 * TOOLING-06 §2.1 / TOOLING-08 §3.5. The app-level *Toolchain* page under *Lua*: hosts the live
 * inventory table (§2.2), the buffered app-default kind-option fields (Luacheck arguments, LuaRocks
 * default server URL), and — new in TOOLING-08 — a *Global Default Bindings* group that drives
 * [LuaToolchainRegistry.setGlobalBinding] for each common kind. The DSL setters call the
 * notify-firing mutators; the binding combos apply through [applyGlobalBindings] on the panel's
 * apply callback. The table auto-refreshes off the [LuaToolchainListener.TOPIC] subscription (§3.1).
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

    private val globalBindingCombos: Map<String, ComboBox<LuaBindingItem>> =
        LuaToolKindClassifier.byTier()[LuaToolKindClassifier.Tier.COMMON].orEmpty()
            .associate { kind -> kind.id to newBindingCombo(kind.id) }

    override fun createPanel(): DialogPanel {
        subscribeToToolchainEvents()
        val builtPanel = panel {
            row {
                cell(inventoryTable.component).resizableColumn()
            }.resizableRow()
            buildGlobalBindings(this)
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
        resetGlobalBindings()
        return builtPanel
    }

    override fun isModified(): Boolean = super.isModified() || isGlobalBindingsModified()

    override fun reset() {
        super.reset()
        resetGlobalBindings()
    }

    override fun apply() {
        super.apply()
        applyGlobalBindings()
    }

    private fun buildGlobalBindings(panelBuilder: Panel) {
        panelBuilder.group("Global Default Bindings") {
            globalBindingCombos.forEach { (kindId, combo) ->
                row("${kindLabel(kindId)}:") { cell(combo) }
            }
            row { comment("Applied to any project with no project-level binding for that tool") }
        }
    }

    private fun resetGlobalBindings() {
        val globalBindings = registry.globalBindings()
        globalBindingCombos.forEach { (kindId, combo) ->
            val tools = registry.toolsOfKind(kindId)
            val items = listOf<LuaBindingItem>(LuaBindingItem.Inherit) + tools.map(LuaBindingItem::Tool)
            combo.model = DefaultComboBoxModel(items.toTypedArray())
            val boundId = globalBindings[kindId]
            combo.selectedItem = items.firstOrNull {
                it is LuaBindingItem.Tool && it.tool.id == boundId
            } ?: LuaBindingItem.Inherit
        }
    }

    private fun applyGlobalBindings() {
        globalBindingCombos.forEach { (kindId, combo) ->
            registry.setGlobalBinding(kindId, selectedToolId(combo))
        }
    }

    private fun isGlobalBindingsModified(): Boolean {
        val globalBindings = registry.globalBindings()
        return globalBindingCombos.any { (kindId, combo) ->
            selectedToolId(combo) != globalBindings[kindId]
        }
    }

    private fun selectedToolId(combo: ComboBox<LuaBindingItem>): String? =
        (combo.selectedItem as? LuaBindingItem.Tool)?.tool?.id

    private fun newBindingCombo(kindId: String): ComboBox<LuaBindingItem> =
        ComboBox<LuaBindingItem>().apply {
            renderer = SimpleListCellRenderer.create("") { bindingLabel(kindId, it) }
        }

    private fun bindingLabel(kindId: String, item: LuaBindingItem): String = when (item) {
        LuaBindingItem.Inherit -> "No default"
        is LuaBindingItem.Tool -> LuaProjectConfigurable.toolLabel(item.tool.path, item.tool.version)
    }

    private fun kindLabel(kindId: String): String =
        (LuaToolKindClassifier.byTier()[LuaToolKindClassifier.Tier.COMMON].orEmpty()
            .firstOrNull { it.id == kindId })?.displayName ?: kindId

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
