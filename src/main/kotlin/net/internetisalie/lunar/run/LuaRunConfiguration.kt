package net.internetisalie.lunar.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.command.findLuaInterpreter
import net.internetisalie.lunar.command.newLuaInterpreterCommandLine
import net.internetisalie.lunar.lang.LuaIcons
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.ChangeEvent


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

    private val myEnvironmentVariables: StoredProperty<MutableMap<String, String>> = map<String, String>().provideDelegate(
        this, "environmentVariables"
    )
    private val myEnvironmentFile: StoredProperty<String?> = string("").provideDelegate(
        this, "environmentFile"
    )
    private val myEnvironmentProcess: StoredProperty<String?> = string("").provideDelegate(
        this, "environmentProcess"
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

    var interpreter: String?
        get() = options.interpreter
        set(interpreter) {
            options.interpreter = interpreter
        }

    var workingDirectory: String?
        get() = options.workingDirectory
        set(workingDirectory) {
            options.workingDirectory = workingDirectory
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

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        return LuaRunSettingsEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val interpreter =
                    findLuaInterpreter(options.interpreter!!) ?: throw ExecutionException("Interpreter not found")

                val commandLine = newLuaInterpreterCommandLine(interpreter)
                    .withWorkDirectory(workingDirectory)
                    .withParameters(options.scriptName!!)

                environmentVariables?.configureCommandLine(commandLine, true)

                val processHandler = ProcessHandlerFactory.getInstance()
                    .createColoredProcessHandler(commandLine)

                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }
        }
    }
}

class LuaRunSettingsEditor(project: Project) : SettingsEditor<LuaRunConfiguration>() {
    private val myPanel: JPanel
    private val interpreterField = JTextField()
    private val scriptPathField = TextFieldWithBrowseButton()
    private val workingDirectoryField = TextFieldWithBrowseButton()
    private val environmentVariablesField = EnvironmentVariablesTextFieldWithBrowseButton()
    // TODO: Source Paths

    init {
        // TODO: interpreters dropdown

        scriptPathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
        )

        workingDirectoryField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Interpreter", interpreterField)
            .addLabeledComponent("Script file", scriptPathField)
            .addLabeledComponent("Working directory", workingDirectoryField)
            .addLabeledComponent("Environment", environmentVariablesField)
            .panel
    }

    override fun resetEditorFrom(runConfiguration: LuaRunConfiguration) {
        scriptPathField.text = runConfiguration.scriptName ?: ""
        interpreterField.text = runConfiguration.interpreter ?: ""
        workingDirectoryField.text = runConfiguration.workingDirectory ?: ""
        environmentVariablesField.data = runConfiguration.environmentVariables ?: EnvironmentVariablesData.DEFAULT
    }

    override fun applyEditorTo(runConfiguration: LuaRunConfiguration) {
        runConfiguration.scriptName = scriptPathField.text
        runConfiguration.interpreter = interpreterField.text
        runConfiguration.workingDirectory = workingDirectoryField.text
        runConfiguration.environmentVariables = environmentVariablesField.data
    }

    override fun createEditor(): JComponent {
        return myPanel
    }
}