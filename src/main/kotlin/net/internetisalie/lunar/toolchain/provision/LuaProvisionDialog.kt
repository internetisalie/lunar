package net.internetisalie.lunar.toolchain.provision

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeedLoader
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Collects a [LuaProvisionRequest] for native toolchain provisioning (design §2.12).
 *
 * EDT-only; reads only the bundled feed (no I/O beyond the cached resource). Pure derivation and
 * validation live in [LuaProvisionFormState]; this class is the Swing binding. When [initial] is
 * given (Change Versions), the fields are pre-filled from that request and the rootDir is fixed.
 */
class LuaProvisionDialog(
    private val targetProject: Project,
    private val initial: LuaProvisionRequest?,
) : DialogWrapper(targetProject) {

    private val feed: LuaToolchainFeed = LuaToolchainFeedLoader.load()
    private val platform = LuaHostPlatform.current()

    private val nameField = JBTextField()
    private val rootDirField = TextFieldWithBrowseButton()
    private val runtimeCombo = ComboBox(LuaToolCatalog.RUNTIME_KINDS.toTypedArray())
    private val runtimeVersionCombo = ComboBox<String>()
    private val includeLuaRocksBox = JBCheckBox("Include LuaRocks", true)
    private val luaRocksVersionCombo = versionCombo("luarocks")
    private val toolBoxes = LuaToolCatalog.TOOL_KINDS.associateWith { JBCheckBox(it, false) }
    private val toolVersionCombos = LuaToolCatalog.TOOL_KINDS.associateWith { versionCombo(it) }

    private var userEditedName = false

    init {
        title = "Provision Lua Toolchain"
        configureRuntime()
        configureName()
        configureRootDir()
        configureLuaRocksForcing()
        initial?.let(::prefill)
        init()
    }

    private fun configureRuntime() {
        runtimeCombo.addActionListener { repopulateRuntimeVersions() }
        repopulateRuntimeVersions()
    }

    private fun repopulateRuntimeVersions() {
        val kindId = runtimeCombo.selectedItem as? String ?: LuaToolCatalog.RUNTIME_KINDS.first()
        runtimeVersionCombo.setModelItems(LuaToolCatalog.visibleVersions(feed, kindId))
        runtimeVersionCombo.selectedItem = LuaToolCatalog.defaultVersion(feed, kindId, platform)
        refreshAutoName()
    }

    private fun configureName() {
        refreshAutoName()
        nameField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = flagEdit()
                override fun removeUpdate(event: DocumentEvent) = flagEdit()
                override fun changedUpdate(event: DocumentEvent) = flagEdit()

                private fun flagEdit() {
                    if (!settingNameProgrammatically) userEditedName = true
                }
            },
        )
        runtimeVersionCombo.addActionListener { refreshAutoName() }
    }

    private var settingNameProgrammatically = false

    private fun refreshAutoName() {
        if (userEditedName) return
        val version = runtimeVersionCombo.selectedItem as? String ?: return
        settingNameProgrammatically = true
        nameField.text = "lua-$version"
        settingNameProgrammatically = false
    }

    private fun configureRootDir() {
        rootDirField.text = "${targetProject.guessProjectDir()?.path.orEmpty()}/.lua"
        rootDirField.addBrowseFolderListener(
            targetProject,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Environment Directory")
                .withDescription("Select the toolchain environment directory"),
        )
    }

    private fun configureLuaRocksForcing() {
        toolBoxes.filterKeys { it in LuaToolCatalog.ROCK_TOOL_KINDS }.values.forEach { box ->
            box.addActionListener { enforceLuaRocks() }
        }
        enforceLuaRocks()
    }

    private fun enforceLuaRocks() {
        val rockSelected = LuaToolCatalog.ROCK_TOOL_KINDS.any { toolBoxes[it]?.isSelected == true }
        if (rockSelected) {
            includeLuaRocksBox.isSelected = true
            includeLuaRocksBox.isEnabled = false
        } else {
            includeLuaRocksBox.isEnabled = true
        }
    }

    private fun prefill(request: LuaProvisionRequest) {
        userEditedName = true
        settingNameProgrammatically = true
        nameField.text = request.environmentName
        settingNameProgrammatically = false
        rootDirField.text = request.rootDir
        prefillRuntime(request)
        prefillLuaRocks(request)
        prefillTools(request)
        enforceLuaRocks()
    }

    private fun prefillRuntime(request: LuaProvisionRequest) {
        val runtime = request.items.firstOrNull { it.kindId in LuaToolCatalog.RUNTIME_KINDS } ?: return
        runtimeCombo.selectedItem = runtime.kindId
        repopulateRuntimeVersions()
        runtimeVersionCombo.selectedItem = runtime.versionSpec
    }

    private fun prefillLuaRocks(request: LuaProvisionRequest) {
        val luaRocks = request.items.firstOrNull { it.kindId == "luarocks" }
        includeLuaRocksBox.isSelected = luaRocks != null
        luaRocks?.let { luaRocksVersionCombo.selectedItem = it.versionSpec }
    }

    private fun prefillTools(request: LuaProvisionRequest) {
        LuaToolCatalog.TOOL_KINDS.forEach { kindId ->
            val item = request.items.firstOrNull { it.kindId == kindId } ?: return@forEach
            toolBoxes[kindId]?.isSelected = true
            toolVersionCombos[kindId]?.selectedItem = item.versionSpec
        }
    }

    override fun createCenterPanel(): JComponent =
        panel {
            row("Name:") { cell(nameField).align(AlignX.FILL) }
            row("Root directory:") { cell(rootDirField).align(AlignX.FILL) }
            row("Runtime:") { cell(runtimeCombo); cell(runtimeVersionCombo) }
            row { cell(includeLuaRocksBox); cell(luaRocksVersionCombo) }
            LuaToolCatalog.TOOL_KINDS.forEach { kindId ->
                row { cell(toolBoxes.getValue(kindId)); cell(toolVersionCombos.getValue(kindId)) }
            }
        }

    override fun doValidate(): ValidationInfo? {
        val existingNames = existingEnvironmentNames()
        val outcome = formState().validate(existingNames) ?: return null
        return ValidationInfo(outcome.message, componentFor(outcome.field))
    }

    private fun componentFor(field: LuaProvisionField): JComponent =
        when (field) {
            LuaProvisionField.NAME -> nameField
            LuaProvisionField.ROOT_DIR -> rootDirField
            LuaProvisionField.RUNTIME_VERSION -> runtimeVersionCombo
        }

    private fun existingEnvironmentNames(): Set<String> =
        LuaToolchainProjectSettings.getInstance(targetProject)
            .environments()
            .filter { it.name != initial?.environmentName }
            .map { it.name }
            .toSet()

    /** Builds the pure form snapshot from the current widget state (design §2.12). */
    fun formState(): LuaProvisionFormState =
        LuaProvisionFormState(
            name = nameField.text.orEmpty(),
            rootDir = rootDirField.text.orEmpty(),
            runtime = LuaToolChoice(selectedRuntimeKind(), selectedRuntimeVersion()),
            luaRocks = LuaRocksChoice(includeLuaRocksBox.isSelected, selectedLuaRocksVersion()),
            selectedTools = selectedToolChoices(),
        )

    private fun selectedRuntimeKind(): String =
        runtimeCombo.selectedItem as? String ?: LuaToolCatalog.RUNTIME_KINDS.first()

    private fun selectedRuntimeVersion(): String = runtimeVersionCombo.selectedItem as? String ?: ""

    private fun selectedLuaRocksVersion(): String = luaRocksVersionCombo.selectedItem as? String ?: "latest"

    private fun selectedToolChoices(): List<LuaToolChoice> =
        LuaToolCatalog.TOOL_KINDS.mapNotNull { kindId ->
            if (toolBoxes[kindId]?.isSelected != true) return@mapNotNull null
            LuaToolChoice(kindId, toolVersionCombos[kindId]?.selectedItem as? String ?: "latest")
        }

    fun toRequest(): LuaProvisionRequest = formState().toRequest()

    private fun versionCombo(kindId: String): ComboBox<String> {
        val combo = ComboBox<String>()
        combo.setModelItems(LuaToolCatalog.visibleVersions(feed, kindId))
        combo.selectedItem = LuaToolCatalog.defaultVersion(feed, kindId, platform)
        return combo
    }

    private fun ComboBox<String>.setModelItems(items: List<String>) {
        model = DefaultComboBoxModel(items.toTypedArray())
    }
}
