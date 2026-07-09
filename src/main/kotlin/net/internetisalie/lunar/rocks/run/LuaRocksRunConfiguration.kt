package net.internetisalie.lunar.rocks.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
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
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.rocks.LuaRocksEnvironment
import net.internetisalie.lunar.toolchain.exec.LuaExecutionEnvironmentBuilder
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Commands offered as presets in the LuaRocks run-config editor (ROCKS-04-02). The combo box is
 * editable, so any other subcommand is also accepted verbatim.
 */
val LUAROCKS_COMMANDS: List<String> = listOf(
    "make", "build", "install", "test", "upload", "list", "show", "remove"
)

/** Subcommands for which a trailing `.rockspec` path is appended (ROCKS-04-03, design §3.1.6). */
private val ROCKSPEC_COMMANDS = setOf("make", "build")

private const val DEFAULT_COMMAND = "make"

private const val LUAROCKS_NOT_CONFIGURED =
    "LuaRocks is not configured. Register or bind it under " +
        "Settings | Languages & Frameworks | Lua | Toolchain."

class LuaRocksRunConfigurationType : ConfigurationTypeBase(
    ID, "LuaRocks", "LuaRocks task run configuration",
    NotNullLazyValue.createValue { LuaIcons.ROCKET }) {
    init {
        addFactory(LuaRocksRunConfigurationFactory(this))
    }

    companion object {
        const val ID: String = "LuaRocksRunConfiguration"
    }
}

class LuaRocksRunConfigurationFactory(type: ConfigurationTypeBase) : ConfigurationFactory(type) {
    override fun getId(): String {
        return LuaRocksRunConfigurationType.ID
    }

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return LuaRocksRunConfiguration(project, this, "LuaRocks")
    }

    override fun getOptionsClass(): Class<out BaseState> {
        return LuaRocksRunConfigurationOptions::class.java
    }
}

class LuaRocksRunConfigurationOptions : RunConfigurationOptions() {
    private val myCommand: StoredProperty<String?> = string(DEFAULT_COMMAND).provideDelegate(
        this, "command"
    )
    private val myArguments: StoredProperty<String?> = string("").provideDelegate(
        this, "arguments"
    )
    private val myRockspecPath: StoredProperty<String?> = string("").provideDelegate(
        this, "rockspecPath"
    )
    private val myGlobalFlags: StoredProperty<String?> = string("").provideDelegate(
        this, "globalFlags"
    )
    private val myEnvironmentVariables: StoredProperty<MutableMap<String, String>> =
        map<String, String>().provideDelegate(
            this, "environmentVariables"
        )

    // Pass parent env by default (ROCKS-04-08): the system C toolchain (cc/make/PATH) must
    // reach `luarocks build` for C-module compilation.
    private val myEnvironmentProcess: StoredProperty<String?> = string("true").provideDelegate(
        this, "environmentProcess"
    )
    private val myEnvironmentFile: StoredProperty<String?> = string("").provideDelegate(
        this, "environmentFile"
    )

    var command: String?
        get() = myCommand.getValue(this)
        set(value) { myCommand.setValue(this, value) }

    var arguments: String?
        get() = myArguments.getValue(this)
        set(value) { myArguments.setValue(this, value) }

    var rockspecPath: String?
        get() = myRockspecPath.getValue(this)
        set(value) { myRockspecPath.setValue(this, value) }

    var globalFlags: String?
        get() = myGlobalFlags.getValue(this)
        set(value) { myGlobalFlags.setValue(this, value) }

    var environmentVariables: MutableMap<String, String>
        get() = myEnvironmentVariables.getValue(this)
        set(value) { myEnvironmentVariables.setValue(this, value) }

    var environmentProcess: String?
        get() = myEnvironmentProcess.getValue(this)
        set(value) { myEnvironmentProcess.setValue(this, value) }

    var environmentFile: String?
        get() = myEnvironmentFile.getValue(this)
        set(value) { myEnvironmentFile.setValue(this, value) }
}

class LuaRocksRunConfiguration(project: Project, factory: ConfigurationFactory?, name: String?) :
    RunConfigurationBase<LuaRocksRunConfigurationOptions?>(project, factory, name) {

    public override fun getOptions(): LuaRocksRunConfigurationOptions {
        return super.getOptions() as LuaRocksRunConfigurationOptions
    }

    var command: String?
        get() = options.command
        set(value) { options.command = value }

    var arguments: String?
        get() = options.arguments
        set(value) { options.arguments = value }

    var rockspecPath: String?
        get() = options.rockspecPath
        set(value) { options.rockspecPath = value }

    var globalFlags: String?
        get() = options.globalFlags
        set(value) { options.globalFlags = value }

    var environmentVariables: EnvironmentVariablesData?
        get() = EnvironmentVariablesData.create(
            options.environmentVariables,
            options.environmentProcess.toBoolean(),
            options.environmentFile
        )
        set(value) {
            if (value != null) {
                options.environmentVariables = value.envs.toMutableMap()
                options.environmentProcess = value.isPassParentEnvs.toString()
                options.environmentFile = value.environmentFile
            } else {
                options.environmentVariables = mutableMapOf()
                options.environmentProcess = null
                options.environmentFile = null
            }
        }

    /** Working directory: the rockspec's parent folder if set, otherwise the project base. */
    private fun resolveWorkingDirectory(): String? {
        val rockspec = rockspecPath
        if (!rockspec.isNullOrBlank()) {
            val parent = File(rockspec).parentFile
            if (parent != null) return parent.path
        }
        return project.basePath
    }

    /** Builds the `luarocks` command line (design §3.1). Extracted for unit testing. */
    fun buildCommandLine(executablePath: String): GeneralCommandLine {
        val commandLine = GeneralCommandLine(executablePath)

        commandLine.withParameters(ParametersListUtil.parse(globalFlags.orEmpty()))

        val subcommand = command?.takeIf { it.isNotBlank() } ?: DEFAULT_COMMAND
        commandLine.withParameters(subcommand)

        commandLine.withParameters(ParametersListUtil.parse(arguments.orEmpty()))

        val rockspec = rockspecPath
        if (subcommand in ROCKSPEC_COMMANDS && !rockspec.isNullOrBlank()) {
            commandLine.withParameters(rockspec)
        }

        commandLine.withWorkDirectory(resolveWorkingDirectory())
        environmentVariables?.configureCommandLine(commandLine, true)
        return commandLine
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        return LuaRocksRunSettingsEditor(project)
    }

    /**
     * Resolves the `luarocks` binary via the toolchain, assembles the command line, and injects
     * the env-builder PATH / LUA_PATH triple (TOOLING-05 §2.4). Extracted for unit testing so the
     * resolution + PATH prepend can be asserted without launching a process.
     */
    fun resolveAndBuildCommandLine(): GeneralCommandLine {
        val executablePath = LuaRocksEnvironment.resolveExecutable(project)
            ?: throw ExecutionException(LUAROCKS_NOT_CONFIGURED)
        val commandLine = buildCommandLine(executablePath)
        LuaExecutionEnvironmentBuilder.getInstance(project).build().applyTo(commandLine)
        return commandLine
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val commandLine = resolveAndBuildCommandLine()
                val processHandler = ProcessHandlerFactory.getInstance()
                    .createColoredProcessHandler(commandLine)
                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }
        }
    }
}

class LuaRocksRunSettingsEditor(project: Project) : SettingsEditor<LuaRocksRunConfiguration>() {
    private val myPanel: JPanel
    private val commandField = ComboBox(LUAROCKS_COMMANDS.toTypedArray()).apply {
        isEditable = true
    }
    private val argumentsField = RawCommandLineEditor()
    private val rockspecField = TextFieldWithBrowseButton()
    private val globalFlagsField = RawCommandLineEditor()
    private val environmentVariablesField = EnvironmentVariablesTextFieldWithBrowseButton()

    init {
        rockspecField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleFileOrDir()
        )

        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Command", commandField)
            .addLabeledComponent("Arguments", argumentsField)
            .addLabeledComponent("Rockspec", rockspecField)
            .addLabeledComponent("Global flags", globalFlagsField)
            .addLabeledComponent("Environment", environmentVariablesField)
            .panel
    }

    override fun resetEditorFrom(runConfiguration: LuaRocksRunConfiguration) {
        commandField.item = runConfiguration.command ?: DEFAULT_COMMAND
        argumentsField.text = runConfiguration.arguments ?: ""
        rockspecField.text = runConfiguration.rockspecPath ?: ""
        globalFlagsField.text = runConfiguration.globalFlags ?: ""
        environmentVariablesField.data =
            runConfiguration.environmentVariables ?: EnvironmentVariablesData.DEFAULT
    }

    override fun applyEditorTo(runConfiguration: LuaRocksRunConfiguration) {
        runConfiguration.command = (commandField.item as? String)?.trim()
        runConfiguration.arguments = argumentsField.text
        runConfiguration.rockspecPath = rockspecField.text
        runConfiguration.globalFlags = globalFlagsField.text
        runConfiguration.environmentVariables = environmentVariablesField.data
    }

    override fun createEditor(): JComponent {
        return myPanel
    }
}
