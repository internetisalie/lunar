package net.internetisalie.lunar.redis.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.redis.connection.LuaRedisConnectionSettings
import net.internetisalie.lunar.redis.connection.LuaRedisServerConnection
import net.internetisalie.lunar.redis.debug.LuaRedisDebugMode
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Script execution mode for a Redis run configuration (design §2.8).
 *
 * [EVAL] sends the script body each run; [EVALSHA] loads once and runs by SHA (design §3.8).
 * [FCALL] is a reserved slot for REDIS-05 (Functions) — rejected in [LuaRedisRunConfiguration.checkConfiguration]
 * until that feature lands (design §3.7), keeping the enum-slot contract for downstream work.
 */
enum class LuaRedisExecMode { EVAL, EVALSHA, FCALL }

class LuaRedisRunConfigurationType : ConfigurationTypeBase(
    ID,
    "Redis Script",
    "Run a Lua script against a Redis/Valkey server",
    NotNullLazyValue.createValue { LuaIcons.ROCKET },
) {
    init {
        addFactory(LuaRedisRunConfigurationFactory(this))
    }

    companion object {
        const val ID: String = "LuaRedisRunConfiguration"

        fun getInstance(): LuaRedisRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(LuaRedisRunConfigurationType::class.java)
    }
}

class LuaRedisRunConfigurationFactory(type: ConfigurationTypeBase) : ConfigurationFactory(type) {
    override fun getId(): String = LuaRedisRunConfigurationType.ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        LuaRedisRunConfiguration(project, this, "Redis Script")

    override fun getOptionsClass(): Class<out BaseState> = LuaRedisRunConfigurationOptions::class.java
}

/**
 * Persisted options for [LuaRedisRunConfiguration] (design §2.8).
 *
 * Every field is a `string()` [StoredProperty] delegate — the only scalar delegate the repo's run
 * configs use besides `map()` (cf. `rocks/run/LuaRocksRunConfiguration`). KEYS/ARGV are `List<String>`
 * at the model level but persist as newline-joined [keysRaw]/[argvRaw] strings, since the repo has no
 * `list()` delegate.
 */
class LuaRedisRunConfigurationOptions : RunConfigurationOptions() {
    private val myScriptPath: StoredProperty<String?> = string("").provideDelegate(this, "scriptPath")
    private val myConnectionId: StoredProperty<String?> = string("").provideDelegate(this, "connectionId")
    private val myExecMode: StoredProperty<String?> = string("EVAL").provideDelegate(this, "execMode")
    private val myDebugMode: StoredProperty<String?> = string("FORKED").provideDelegate(this, "debugMode")
    private val myReadOnly: StoredProperty<String?> = string("false").provideDelegate(this, "readOnly")
    private val myKeysRaw: StoredProperty<String?> = string("").provideDelegate(this, "keysRaw")
    private val myArgvRaw: StoredProperty<String?> = string("").provideDelegate(this, "argvRaw")

    var scriptPath: String?
        get() = myScriptPath.getValue(this)
        set(value) = myScriptPath.setValue(this, value)

    var connectionId: String?
        get() = myConnectionId.getValue(this)
        set(value) = myConnectionId.setValue(this, value)

    var execMode: String?
        get() = myExecMode.getValue(this)
        set(value) = myExecMode.setValue(this, value)

    var debugMode: String?
        get() = myDebugMode.getValue(this)
        set(value) = myDebugMode.setValue(this, value)

    var readOnly: String?
        get() = myReadOnly.getValue(this)
        set(value) = myReadOnly.setValue(this, value)

    var keysRaw: String?
        get() = myKeysRaw.getValue(this)
        set(value) = myKeysRaw.setValue(this, value)

    var argvRaw: String?
        get() = myArgvRaw.getValue(this)
        set(value) = myArgvRaw.setValue(this, value)
}

class LuaRedisRunConfiguration(project: Project, factory: ConfigurationFactory?, name: String?) :
    RunConfigurationBase<LuaRedisRunConfigurationOptions?>(project, factory, name) {

    public override fun getOptions(): LuaRedisRunConfigurationOptions =
        super.getOptions() as LuaRedisRunConfigurationOptions

    var scriptPath: String?
        get() = options.scriptPath
        set(value) { options.scriptPath = value }

    var connectionId: String?
        get() = options.connectionId
        set(value) { options.connectionId = value }

    /** Resolves the selected connection by id from the project settings, or `null` when absent (design §2.5 seam). */
    val connection: LuaRedisServerConnection?
        get() {
            val id = options.connectionId?.takeIf { it.isNotBlank() } ?: return null
            return LuaRedisConnectionSettings.getInstance(project).findById(id)
        }

    var execMode: LuaRedisExecMode
        get() = runCatching { LuaRedisExecMode.valueOf(options.execMode ?: "EVAL") }.getOrDefault(LuaRedisExecMode.EVAL)
        set(value) { options.execMode = value.name }

    /**
     * LDB debug session mode (design §2.9 / §11 amendment A2). Additive: only the Debug executor
     * (REDIS-02) reads it; the Run executor ignores it entirely, so Run behavior is unchanged.
     */
    var debugMode: LuaRedisDebugMode
        get() = runCatching { LuaRedisDebugMode.valueOf(options.debugMode ?: "FORKED") }.getOrDefault(LuaRedisDebugMode.FORKED)
        set(value) { options.debugMode = value.name }

    var readOnly: Boolean
        get() = options.readOnly.toBoolean()
        set(value) { options.readOnly = value.toString() }

    /** KEYS as a read-only list; the `\n`-joined [LuaRedisRunConfigurationOptions.keysRaw] bridge (design §2.8). */
    var keys: List<String>
        get() = splitLines(options.keysRaw)
        set(value) { options.keysRaw = joinLines(value) }

    /** ARGV as a read-only list; the `\n`-joined [LuaRedisRunConfigurationOptions.argvRaw] bridge (design §2.8). */
    var argv: List<String>
        get() = splitLines(options.argvRaw)
        set(value) { options.argvRaw = joinLines(value) }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> = LuaRedisSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        LuaRedisRunProfileState(this, environment)

    /** Validates the configuration at edit time (design §3.7). Live-connection checks are deferred to run time (§3.8). */
    override fun checkConfiguration() {
        if (options.scriptPath.isNullOrBlank()) {
            throw RuntimeConfigurationException("Script path is not defined")
        }
        if (connection == null) {
            throw RuntimeConfigurationException("No Redis connection selected")
        }
        if (execMode == LuaRedisExecMode.FCALL) {
            throw RuntimeConfigurationException("FCALL mode is not available until REDIS-05")
        }
    }

    private companion object {
        fun splitLines(raw: String?): List<String> =
            raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        fun joinLines(values: List<String>): String =
            values.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}

class LuaRedisSettingsEditor(private val project: Project) : SettingsEditor<LuaRedisRunConfiguration>() {
    private val myPanel: JPanel
    private val scriptPathField = TextFieldWithBrowseButton()
    private val connectionCombo = ComboBox<LuaRedisConnectionItem>()
    private val execModeCombo = ComboBox(arrayOf(LuaRedisExecMode.EVAL, LuaRedisExecMode.EVALSHA))
    private val debugModeCombo = ComboBox(arrayOf(LuaRedisDebugMode.FORKED, LuaRedisDebugMode.SYNC))
    private val readOnlyCheckbox = com.intellij.ui.components.JBCheckBox("Read-only (EVAL_RO / EVALSHA_RO)")
    private val keysField = RawCommandLineEditor()
    private val argvField = RawCommandLineEditor()

    init {
        scriptPathField.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFile())
        debugModeCombo.renderer = SimpleListCellRenderer.create("") { debugModeLabel(it) }
        reloadConnections()

        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Script", scriptPathField)
            .addLabeledComponent("Connection", connectionCombo)
            .addLabeledComponent("Execution mode", execModeCombo)
            .addLabeledComponent("Debug mode", debugModeCombo)
            .addComponent(readOnlyCheckbox)
            .addLabeledComponent("KEYS (space-separated)", keysField)
            .addLabeledComponent("ARGV (space-separated)", argvField)
            .panel
    }

    private fun reloadConnections() {
        connectionCombo.removeAllItems()
        LuaRedisConnectionSettings.getInstance(project).connections()
            .forEach { connectionCombo.addItem(LuaRedisConnectionItem(it.id, it.name)) }
    }

    override fun resetEditorFrom(config: LuaRedisRunConfiguration) {
        scriptPathField.text = config.scriptPath ?: ""
        reloadConnections()
        connectionCombo.item = selectConnection(config.connectionId)
        execModeCombo.item = if (config.execMode == LuaRedisExecMode.EVALSHA) LuaRedisExecMode.EVALSHA else LuaRedisExecMode.EVAL
        debugModeCombo.item = config.debugMode
        readOnlyCheckbox.isSelected = config.readOnly
        keysField.text = config.keys.joinToString(" ")
        argvField.text = config.argv.joinToString(" ")
    }

    override fun applyEditorTo(config: LuaRedisRunConfiguration) {
        config.scriptPath = scriptPathField.text
        config.connectionId = connectionCombo.item?.id
        config.execMode = execModeCombo.item ?: LuaRedisExecMode.EVAL
        config.debugMode = debugModeCombo.item ?: LuaRedisDebugMode.FORKED
        config.readOnly = readOnlyCheckbox.isSelected
        config.keys = keysField.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        config.argv = argvField.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun debugModeLabel(mode: LuaRedisDebugMode): String = when (mode) {
        LuaRedisDebugMode.FORKED -> "Forked"
        LuaRedisDebugMode.SYNC -> "Sync (danger)"
    }

    private fun selectConnection(connectionId: String?): LuaRedisConnectionItem? {
        val id = connectionId?.takeIf { it.isNotBlank() } ?: return null
        return (0 until connectionCombo.itemCount)
            .map { connectionCombo.getItemAt(it) }
            .firstOrNull { it.id == id }
    }

    override fun createEditor(): JComponent = myPanel
}

/** Combo-box row for a connection: its stable id plus a display name. */
data class LuaRedisConnectionItem(val id: String, val name: String) {
    override fun toString(): String = name
}
