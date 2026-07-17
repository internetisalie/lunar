package net.internetisalie.lunar.toolchain.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.panel
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.settings.LuaSettingsChangedListener
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.resolve.LuaTargetSynchronizer
import net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.registry.LuaToolchainEvent
import net.internetisalie.lunar.toolchain.registry.LuaToolchainListener
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolution
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import net.internetisalie.lunar.toolchain.resolve.ResolutionSource
import javax.swing.DefaultComboBoxModel

private const val CONFIGURABLE_ID = "net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable"

/**
 * TOOLING-06 §2.3. The rewritten project page: a buffered editor over TOOLING-02 project
 * toolchain state (active environment + per-kind bindings + project luacheck-args override) plus
 * the retained [LuaProjectSettings] fields (source path, underscore suppression, rocks server
 * URL override). Every changed field on [apply] flows through exactly one notify-firing mutator
 * (§3.6) — superseding the interim panel's silent writes. The resolved-runtime display reflects
 * applied state only (§3.5), recomputed on reset / apply / toolchain events.
 */
class LuaProjectConfigurable(private val project: Project) : BoundSearchableConfigurable(
    displayName = "Lua Project",
    helpTopic = "settings.lua.project",
    _id = CONFIGURABLE_ID
) {

    private val controls = ProjectControls()

    /** True while [resetTargetControls] drives the platform combo, so its action listener stays inert. */
    private var suppressPlatformEvents = false

    private val toolchainSettings: LuaToolchainProjectSettings
        get() = LuaToolchainProjectSettings.getInstance(project)

    private val projectSettings: LuaProjectSettings
        get() = LuaProjectSettings.getInstance(project)

    override fun createPanel(): DialogPanel {
        subscribeToToolchainEvents()
        val builtPanel = buildPanel()
        resetControls()
        return builtPanel
    }

    private fun buildPanel(): DialogPanel = panel {
        buildTargetGroup(this)
        group("Environment") {
            row("Active environment:") { cell(controls.environmentCombo) }
        }
        group("Toolchain Bindings") {
            orderedKinds().forEach { kind ->
                row("${kind.displayName}:") { cell(controls.bindingCombo(kind.id)) }
            }
        }
        group("Resolved Runtime") {
            row("Runtime:") { cell(controls.runtimeLabel) }
            row("Language level:") { cell(controls.languageLevelLabel) }
            row { comment("Reflects applied settings") }
        }
        group("Luacheck") {
            row(LuaBundle.message("luacheck.arguments")) { cell(controls.luacheckArgsField) }
        }
        group("LuaRocks") {
            row("Server URL (project override):") { cell(controls.rocksUrlField) }
        }
        group("Source & Completion") {
            row("Source path patterns:") { cell(controls.sourcePathField) }
            row { cell(controls.underscoreCheckBox) }
        }
    }

    override fun isModified(): Boolean {
        if (isTargetModified()) return true
        if (environmentSelectionId() != toolchainSettings.activeEnvironment()?.id) return true
        if (orderedKinds().any { selectedToolId(it.id) != normalizedBinding(it.id) }) return true
        val projectState = projectSettings.state
        if (controls.luacheckArgsField.text.trim() != savedProjectLuacheckArgs()) return true
        if (controls.rocksUrlField.text.trim() != projectState.rocksServerUrl.trim()) return true
        if (controls.sourcePathField.text != projectState.sourcePath) return true
        return controls.underscoreCheckBox.isSelected != projectState.suppressUnderscorePrefixedGlobals
    }

    override fun apply() {
        applyTarget()
        applyEnvironment()
        applyBindings()
        applyProjectLuacheckArgs()
        applyRetainedProjectFields()
        recomputeRuntimeDisplay()
    }

    override fun reset() = resetControls()

    private fun resetControls() {
        resetTargetControls()
        resetEnvironmentCombo()
        orderedKinds().forEach { resetBindingCombo(it) }
        controls.luacheckArgsField.text = savedProjectLuacheckArgs()
        val projectState = projectSettings.state
        controls.rocksUrlField.text = projectState.rocksServerUrl
        controls.sourcePathField.text = projectState.sourcePath
        controls.underscoreCheckBox.isSelected = projectState.suppressUnderscorePrefixedGlobals
        recomputeRuntimeDisplay()
    }

    private fun resetEnvironmentCombo() {
        val items = listOf<LuaEnvironmentItem>(LuaEnvironmentItem.None) +
            toolchainSettings.environments().map(LuaEnvironmentItem::Env)
        controls.environmentCombo.model = DefaultComboBoxModel(items.toTypedArray())
        val activeId = toolchainSettings.activeEnvironment()?.id
        controls.environmentCombo.selectedItem = items.firstOrNull {
            it is LuaEnvironmentItem.Env && it.env.id == activeId
        } ?: LuaEnvironmentItem.None
    }

    private fun resetBindingCombo(kind: LuaToolKind) {
        val combo = controls.bindingCombo(kind.id)
        val tools = LuaToolchainRegistry.getInstance().toolsOfKind(kind.id)
        val items = listOf<LuaBindingItem>(LuaBindingItem.Inherit) + tools.map(LuaBindingItem::Tool)
        combo.model = DefaultComboBoxModel(items.toTypedArray())
        val savedId = normalizedBinding(kind.id)
        combo.selectedItem = items.firstOrNull {
            it is LuaBindingItem.Tool && it.tool.id == savedId
        } ?: LuaBindingItem.Inherit
    }

    private fun buildTargetGroup(panelBuilder: com.intellij.ui.dsl.builder.Panel) {
        panelBuilder.group("Platform Target") {
            row("Platform target:") { cell(controls.platformCombo) }
            row("Version:") { cell(controls.versionCombo) }
            row { comment("Auto (from runtime) follows the discovered interpreter; pick a platform to pin it") }
        }
    }

    private fun resetTargetControls() {
        suppressPlatformEvents = true
        try {
            val state = projectSettings.state
            val platformItems = listOf<TargetItem>(TargetItem.Auto) +
                PlatformVersionRegistry.platforms().sortedBy { it.label }.map { TargetItem.Platform(it) }
            controls.platformCombo.model = DefaultComboBoxModel(platformItems.toTypedArray())
            if (state.explicitTarget) {
                val pinned = state.getTarget()
                controls.platformCombo.selectedItem = TargetItem.Platform(pinned.platform)
                repopulateVersionCombo(pinned.platform)
                selectVersionLabel(pinned.version.label)
            } else {
                controls.platformCombo.selectedItem = TargetItem.Auto
                repopulateVersionCombo(null)
            }
        } finally {
            suppressPlatformEvents = false
        }
    }

    private fun repopulateVersionCombo(platform: LuaPlatform?) {
        if (platform == null) {
            val autoVersion = projectSettings.state.getTarget().version
            controls.versionCombo.model = DefaultComboBoxModel(arrayOf(autoVersion))
            controls.versionCombo.selectedItem = autoVersion
            controls.versionCombo.isEnabled = false
            return
        }
        val previousLabel = (controls.versionCombo.selectedItem as? VersionEntry)?.label
        val versions = PlatformVersionRegistry.getVersions(platform)
        controls.versionCombo.model = DefaultComboBoxModel(versions.toTypedArray())
        controls.versionCombo.isEnabled = versions.isNotEmpty()
        val keep = versions.firstOrNull { it.label == previousLabel }
        controls.versionCombo.selectedItem = keep ?: PlatformVersionRegistry.defaultVersion(platform)
    }

    private fun selectVersionLabel(label: String) {
        val combo = controls.versionCombo
        val match = (0 until combo.itemCount).map { combo.getItemAt(it) }.firstOrNull { it?.label == label }
        combo.selectedItem = match ?: combo.getItemAt(0)
    }

    private fun isTargetModified(): Boolean {
        val state = projectSettings.state
        val selectedIsAuto = controls.platformCombo.selectedItem is TargetItem.Auto
        if (selectedIsAuto != !state.explicitTarget) return true
        if (selectedIsAuto) return false
        val selectedPlatform = (controls.platformCombo.selectedItem as? TargetItem.Platform)?.platform
        val selectedVersionLabel = (controls.versionCombo.selectedItem as? VersionEntry)?.label
        return selectedPlatform != state.getTarget().platform ||
            selectedVersionLabel != state.getTarget().version.label
    }

    private fun applyTarget() {
        if (!isTargetModified()) return
        val state = projectSettings.state
        val selected = controls.platformCombo.selectedItem
        if (selected is TargetItem.Platform) {
            val versionLabel = (controls.versionCombo.selectedItem as? VersionEntry)?.label ?: ""
            state.explicitTarget = true
            projectSettings.setTargetAndNotify(
                PlatformVersionRegistry.resolveTarget(selected.platform, versionLabel)
            )
        } else if (state.explicitTarget) {
            state.explicitTarget = false
            LuaTargetSynchronizer.getInstance(project).ensureSynchronized()
        }
    }

    private fun applyEnvironment() {
        val selectedId = environmentSelectionId()
        if (selectedId == toolchainSettings.activeEnvironment()?.id) return
        if (selectedId == null) {
            toolchainSettings.deactivateEnvironment()
        } else {
            toolchainSettings.activateEnvironment(selectedId)
        }
    }

    private fun applyBindings() {
        orderedKinds().forEach { kind ->
            val selectedId = selectedToolId(kind.id)
            if (selectedId != normalizedBinding(kind.id)) {
                toolchainSettings.setBinding(kind.id, selectedId)
            }
        }
    }

    private fun applyProjectLuacheckArgs() {
        val typed = controls.luacheckArgsField.text.trim()
        if (typed == savedProjectLuacheckArgs()) return
        toolchainSettings.setKindOption(LuaKindOptionKeys.LUACHECK_ARGUMENTS, typed.ifEmpty { null })
    }

    private fun applyRetainedProjectFields() {
        val projectState = projectSettings.state
        val newRocksUrl = controls.rocksUrlField.text.trim()
        val newSourcePath = controls.sourcePathField.text
        val newUnderscore = controls.underscoreCheckBox.isSelected
        val changed = newRocksUrl != projectState.rocksServerUrl.trim() ||
            newSourcePath != projectState.sourcePath ||
            newUnderscore != projectState.suppressUnderscorePrefixedGlobals
        if (!changed) return
        projectState.rocksServerUrl = newRocksUrl
        projectState.sourcePath = newSourcePath
        projectState.suppressUnderscorePrefixedGlobals = newUnderscore
        project.messageBus.syncPublisher(LuaSettingsChangedListener.TOPIC).onSettingsChanged()
    }

    private fun recomputeRuntimeDisplay() {
        val display = LuaProjectRuntimeDisplay.compute(project)
        controls.runtimeLabel.text = display.runtimeText
        controls.languageLevelLabel.text = display.languageLevelText
    }

    private fun subscribeToToolchainEvents() {
        val panelDisposable = disposable ?: return
        val connection = ApplicationManager.getApplication().messageBus.connect(panelDisposable)
        connection.subscribe(
            LuaToolchainListener.TOPIC,
            object : LuaToolchainListener {
                override fun toolchainChanged(event: LuaToolchainEvent) {
                    ApplicationManager.getApplication().invokeLater(
                        { recomputeRuntimeDisplay() },
                        ModalityState.any()
                    )
                }
            }
        )
    }

    private fun orderedKinds(): List<LuaToolKind> {
        val (runtimeKinds, otherKinds) = LuaToolKindRegistry.all().partition { it.isRuntime }
        return runtimeKinds + otherKinds
    }

    private fun environmentSelectionId(): String? =
        (controls.environmentCombo.selectedItem as? LuaEnvironmentItem.Env)?.env?.id

    private fun selectedToolId(kindId: String): String? =
        (controls.bindingCombo(kindId).selectedItem as? LuaBindingItem.Tool)?.tool?.id

    private fun normalizedBinding(kindId: String): String? {
        val savedId = toolchainSettings.binding(kindId) ?: return null
        val stillInInventory = LuaToolchainRegistry.getInstance().toolsOfKind(kindId).any { it.id == savedId }
        return if (stillInInventory) savedId else null
    }

    private fun savedProjectLuacheckArgs(): String =
        LuaToolchainProjectSettings.getInstance(project).state.kindOptions[LuaKindOptionKeys.LUACHECK_ARGUMENTS]
            ?.trim() ?: ""

    /** Holds the buffered Swing controls so the per-group / per-field helpers stay within the arg cap. */
    private inner class ProjectControls {
        val platformCombo = ComboBox<TargetItem>().apply {
            renderer = SimpleListCellRenderer.create("") { platformItemLabel(it) }
            addActionListener {
                if (!suppressPlatformEvents) {
                    repopulateVersionCombo((selectedItem as? TargetItem.Platform)?.platform)
                }
            }
        }

        val versionCombo = ComboBox<VersionEntry>().apply {
            renderer = SimpleListCellRenderer.create("") { it.label }
        }

        val environmentCombo = ComboBox<LuaEnvironmentItem>().apply {
            renderer = SimpleListCellRenderer.create("") { environmentLabel(it) }
        }

        val runtimeLabel = JBLabel()
        val languageLevelLabel = JBLabel()

        val luacheckArgsField = ExpandableTextField().apply { columns = 40 }

        val rocksUrlField = JBTextField().apply {
            columns = 40
            emptyText.text = "Empty = use app default or luarocks.org"
        }

        val sourcePathField = ExpandableTextField(
            { value -> value.split(PathConfiguration.TEMPLATE_SEPARATOR) },
            { entries -> entries.joinToString(PathConfiguration.TEMPLATE_SEPARATOR) }
        ).apply { columns = 60 }

        val underscoreCheckBox = JBCheckBox(
            "Hide symbols with an underscore prefix (_) from suggestions"
        )

        private val bindingCombos: Map<String, ComboBox<LuaBindingItem>> =
            orderedKinds().associate { kind -> kind.id to newBindingCombo(kind.id) }

        fun bindingCombo(kindId: String): ComboBox<LuaBindingItem> =
            bindingCombos[kindId] ?: newBindingCombo(kindId)

        private fun newBindingCombo(kindId: String): ComboBox<LuaBindingItem> =
            ComboBox<LuaBindingItem>().apply {
                renderer = SimpleListCellRenderer.create("") { bindingLabel(kindId, it) }
            }
    }

    private fun platformItemLabel(item: TargetItem): String = when (item) {
        TargetItem.Auto -> "Auto (from runtime)"
        is TargetItem.Platform -> item.platform.label
    }

    private fun environmentLabel(item: LuaEnvironmentItem): String = when (item) {
        LuaEnvironmentItem.None -> "None (use bindings)"
        is LuaEnvironmentItem.Env -> item.env.name.ifBlank { item.env.rootDir }
    }

    private fun bindingLabel(kindId: String, item: LuaBindingItem): String = when (item) {
        LuaBindingItem.Inherit -> "Inherit (${inheritLabel(kindId)})"
        is LuaBindingItem.Tool -> toolLabel(item.tool.path, item.tool.version)
    }

    private fun inheritLabel(kindId: String): String {
        val inherited = LuaToolResolver.getInstance().resolve(null, kindId) ?: return "none"
        return toolLabel(inherited.path, inherited.version)
    }

    companion object {
        fun toolLabel(path: String, version: String?): String = "$path — ${version ?: "-"}"
    }
}

/**
 * TOOLING-06 §3.5. Pure derivation of the applied-state resolved-runtime labels. Kept out of the
 * configurable so the display never re-implements the resolver precedence over unsaved combo
 * edits — it reflects only what [LuaToolResolver.resolveRuntimeDetailed] returns for the project.
 */
private object LuaProjectRuntimeDisplay {

    data class Display(val runtimeText: String, val languageLevelText: String)

    fun compute(project: Project): Display {
        val resolution = LuaToolResolver.getInstance().resolveRuntimeDetailed(project)
        val resolved = resolution as? LuaToolResolution.Resolved
        val runtime = resolved?.tool?.runtime
        if (resolved == null || runtime == null) return fallback()
        val text = "${resolved.tool.path} — ${runtime.product} ${runtime.version}" +
            sourceSuffix(resolved.source)
        return Display(text, runtime.languageLevel.toString())
    }

    private fun fallback(): Display =
        Display("No runtime configured", "${Target.default().getImplicitLanguageLevel()} (default)")

    private fun sourceSuffix(source: ResolutionSource): String = when (source) {
        ResolutionSource.ACTIVE_ENVIRONMENT -> " (from active environment)"
        ResolutionSource.PROJECT_BINDING -> " (project binding)"
        ResolutionSource.GLOBAL_BINDING -> " (global binding)"
        ResolutionSource.INVENTORY_FALLBACK -> " (inventory fallback)"
    }
}
