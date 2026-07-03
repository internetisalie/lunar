package net.internetisalie.lunar.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultDebugExecutor
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
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.command.newLuaInterpreterCommandLine
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaInterpreterFamily
import net.internetisalie.lunar.platform.customizeLuaInterpreterComboBox
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.util.LuaFileUtil
import javax.swing.JComponent
import javax.swing.JPanel


class LuaRunConfigurationType : ConfigurationTypeBase(
    ID, "Lua", "Lua run configuration type",
    NotNullLazyValue.createValue { LuaIcons.FILE }) {
    init {
        addFactory(LuaRunConfigurationFactory(this))
    }

    companion object {
        const val ID: String = "LuaRunConfiguration"
    }
}

class LuaRunConfigurationFactory(type: ConfigurationTypeBase) : ConfigurationFactory(type) {
    override fun getId(): String {
        return LuaRunConfigurationType.ID
    }

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return LuaRunConfiguration(project, this, "Lua")
    }

    override fun getOptionsClass(): Class<out BaseState> {
        return LuaRunConfigurationOptions::class.java
    }
}

class LuaRunConfigurationOptions : RunConfigurationOptions() {
    private val myScriptName: StoredProperty<String?> = string("").provideDelegate(
        this, "scriptName"
    )

    private val myInterpreter: StoredProperty<String?> = string("").provideDelegate(
        this, "interpreter"
    )

    private val myWorkingDirectory: StoredProperty<String?> = string("").provideDelegate(
        this, "workingDirectory"
    )

    private val mySourcePath: StoredProperty<String?> = string("").provideDelegate(
        this, "sourcePath"
    )

    private val myEnvironmentVariables: StoredProperty<MutableMap<String, String>> =
        map<String, String>().provideDelegate(
            this, "environmentVariables"
        )
    private val myEnvironmentFile: StoredProperty<String?> = string("").provideDelegate(
        this, "environmentFile"
    )
    private val myEnvironmentProcess: StoredProperty<String?> = string("").provideDelegate(
        this, "environmentProcess"
    )

    private val myProgramArguments: StoredProperty<String?> = string("").provideDelegate(
        this, "programArguments"
    )

    private val myInterpreterArguments: StoredProperty<String?> = string("").provideDelegate(
        this, "interpreterArguments"
    )

    var interpreter: String?
        get() = myInterpreter.getValue(this)
        set(interpreter) {
            myInterpreter.setValue(this, interpreter)
        }

    var scriptName: String?
        get() = myScriptName.getValue(this)
        set(scriptName) {
            myScriptName.setValue(this, scriptName)
        }

    var workingDirectory: String?
        get() = myWorkingDirectory.getValue(this)
        set(workingDirectory) {
            myWorkingDirectory.setValue(this, workingDirectory)
        }

    var sourcePath: String?
        get() = mySourcePath.getValue(this)
        set(sourcePath) {
            mySourcePath.setValue(this, sourcePath)
        }

    var environmentVariables: MutableMap<String, String>
        get() = myEnvironmentVariables.getValue(this)
        set(environmentVariables) {
            myEnvironmentVariables.setValue(this, environmentVariables)
        }

    var environmentFile: String?
        get() = myEnvironmentFile.getValue(this)
        set(environmentFile) {
            myEnvironmentFile.setValue(this, environmentFile)
        }

    var environmentProcess: String?
        get() = myEnvironmentProcess.getValue(this)
        set(environmentProcess) {
            myEnvironmentProcess.setValue(this, environmentProcess)
        }

    var programArguments: String?
        get() = myProgramArguments.getValue(this)
        set(programArguments) {
            myProgramArguments.setValue(this, programArguments)
        }

    var interpreterArguments: String?
        get() = myInterpreterArguments.getValue(this)
        set(interpreterArguments) {
            myInterpreterArguments.setValue(this, interpreterArguments)
        }

}

class LuaRunConfiguration(project: Project, factory: ConfigurationFactory?, name: String?) :
    RunConfigurationBase<LuaRunConfigurationOptions?>(project, factory, name) {

    override fun getOptions(): LuaRunConfigurationOptions {
        return super.getOptions() as LuaRunConfigurationOptions
    }

    var scriptName: String?
        get() = options.scriptName
        set(scriptName) {
            options.scriptName = scriptName
        }

    var interpreter: LuaInterpreter?
        get() {
            val interpreterPath = options.interpreter ?: return null
            if (interpreterPath.isEmpty()) return null
            return LuaApplicationSettings.findInterpreter(interpreterPath)
                ?: LuaInterpreter(path = interpreterPath, product = LuaInterpreterFamily.UNKNOWN_PRODUCT)
        }
        set(interpreter) {
            options.interpreter = interpreter?.path
        }

    var workingDirectory: String?
        get() = options.workingDirectory
        set(workingDirectory) {
            options.workingDirectory = workingDirectory
        }

    var sourcePath: String?
        get() = options.sourcePath
        set(sourcePath) {
            options.sourcePath = sourcePath
        }

    var environmentVariables: EnvironmentVariablesData?
        get() = EnvironmentVariablesData.create(
            options.environmentVariables,
            options.environmentProcess.toBoolean(),
            options.environmentFile
        )
        set(environmentVariables) {
            if (environmentVariables != null) {
                options.environmentVariables = environmentVariables.envs.toMutableMap()
                options.environmentProcess = environmentVariables.isPassParentEnvs.toString()
                options.environmentFile = environmentVariables.environmentFile
            } else {
                options.environmentVariables = mutableMapOf()
                options.environmentProcess = null
                options.environmentFile = null
            }
        }

    var programArguments: String?
        get() = options.programArguments
        set(programArguments) {
            options.programArguments = programArguments
        }

    var interpreterArguments: String?
        get() = options.interpreterArguments
        set(interpreterArguments) {
            options.interpreterArguments = interpreterArguments
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        return LuaRunSettingsEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val interpreter = interpreter
                    ?: throw ExecutionException("Interpreter is not defined")
                val commandLine = newLuaInterpreterCommandLine(interpreter)
                    ?: throw ExecutionException("Interpreter is not found")

                val interpreterArguments = ParametersListUtil.parse(interpreterArguments.orEmpty())
                commandLine.withParameters(interpreterArguments)

                val scriptName = options.scriptName.orEmpty()
                if (!scriptName.isEmpty()) commandLine.withParameters(scriptName)
                else commandLine.withParameters("-v", "-i")

                val programArguments = ParametersListUtil.parse(programArguments.orEmpty())
                commandLine.withParameters(programArguments)

                commandLine
                    .withWorkDirectory(workingDirectory)

                environmentVariables?.configureCommandLine(commandLine, true)

                // Debugging support
                if (executor.getId() == DefaultDebugExecutor.EXECUTOR_ID) {
                    val pluginLuaPath = LuaFileUtil.getPluginVirtualDirectoryChild("lua")
                        ?: throw ExecutionException("Failed to locate plugin directory")
                    val debuggerPreloaderFile = pluginLuaPath.findChild(DEBUGGER_PRELOADER_FILE)
                        ?: throw ExecutionException("Failed to locate debugger preloader")

                    commandLine.withEnvironment(ENV_LUNAR_LUA_PATH_TEMPLATE,
                        pluginLuaPath.path + "/?/init.lua;" + pluginLuaPath.path + "/?.lua")
                    commandLine.withEnvironment(ENV_LUNAR_DEBUGGER_PACKAGE, DEBUGGER_PACKAGE)
                    commandLine.withEnvironment(ENV_LUA_INIT, "@${debuggerPreloaderFile.path}")
                }

                val sourcePath = sourcePath ?: ""
                if (sourcePath.isNotEmpty()) {
                    commandLine.withEnvironment("LUA_PATH", sourcePath)
                } else {
                    // Fallback to project source path
                    val settingsState = LuaProjectSettings.getInstance(project).state
                    val projectPath = settingsState.expandSourcePath(project)
                    val prefix = net.internetisalie.lunar.rocks.RockspecRunPathProvider.luaPathPrefix(project)
                    val union = (prefix + projectPath).trimEnd(';') + ";;"
                    if (union != ";;") commandLine.withEnvironment("LUA_PATH", union)
                    net.internetisalie.lunar.rocks.RockspecRunPathProvider.luaCPath(project)?.let { commandLine.withEnvironment("LUA_CPATH", it) }
                }

                val processHandler = ProcessHandlerFactory.getInstance()
                    .createColoredProcessHandler(commandLine)

                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }
        }
    }

    companion object {
        const val DEBUGGER_PRELOADER_FILE = "debug.lua"
        const val DEBUGGER_PACKAGE = "mobdebug"
        const val ENV_LUA_INIT = "LUA_INIT"
        const val ENV_LUNAR_LUA_PATH_TEMPLATE = "LUNAR_LUA_PATH_TEMPLATE"
        const val ENV_LUNAR_DEBUGGER_PACKAGE = "LUNAR_DEBUGGER_PACKAGE"
    }
}

class LuaRunSettingsEditor(project: Project) : SettingsEditor<LuaRunConfiguration>() {
    private val myPanel: JPanel
    private val interpreterField = ComboBox<LuaInterpreter>()
    private val scriptPathField = TextFieldWithBrowseButton()
    private val workingDirectoryField = TextFieldWithBrowseButton()
    private val sourcePathField = ExpandableTextField(
        { value -> value.split(PathConfiguration.TEMPLATE_SEPARATOR) },
        { entries -> entries.joinToString(PathConfiguration.TEMPLATE_SEPARATOR) },
    )
    private val environmentVariablesField = EnvironmentVariablesTextFieldWithBrowseButton()
    private val interpreterArgumentsField = RawCommandLineEditor()
    private val programArgumentsField = RawCommandLineEditor()


    init {
        customizeLuaInterpreterComboBox(project, interpreterField)

        scriptPathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleFileOrDir()
        )

        workingDirectoryField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleDir()
        )

        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Interpreter", interpreterField)
            .addLabeledComponent("Script file", scriptPathField)
            .addLabeledComponent("Working directory", workingDirectoryField)
            .addLabeledComponent("Source path templates", sourcePathField)
            .addLabeledComponent("Environment", environmentVariablesField)
            .addLabeledComponent("Interpreter arguments", interpreterArgumentsField)
            .addLabeledComponent("Program arguments", programArgumentsField)
            .panel
    }

    override fun resetEditorFrom(runConfiguration: LuaRunConfiguration) {
        scriptPathField.text = runConfiguration.scriptName ?: ""
        interpreterField.item = runConfiguration.interpreter
        workingDirectoryField.text = runConfiguration.workingDirectory ?: ""
        sourcePathField.text = runConfiguration.sourcePath ?: ""
        environmentVariablesField.data = runConfiguration.environmentVariables ?: EnvironmentVariablesData.DEFAULT
        interpreterArgumentsField.text = runConfiguration.interpreterArguments ?: ""
        programArgumentsField.text = runConfiguration.programArguments ?: ""
    }

    override fun applyEditorTo(runConfiguration: LuaRunConfiguration) {
        runConfiguration.scriptName = scriptPathField.text
        runConfiguration.interpreter = interpreterField.item
        runConfiguration.workingDirectory = workingDirectoryField.text
        runConfiguration.environmentVariables = environmentVariablesField.data
        runConfiguration.interpreterArguments = interpreterArgumentsField.text
        runConfiguration.programArguments = programArgumentsField.text
    }

    override fun createEditor(): JComponent {
        return myPanel
    }
}
