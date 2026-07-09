package net.internetisalie.lunar.run.test

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.ui.RawCommandLineEditor
import com.intellij.util.ui.FormBuilder
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.run.resolveConfiguredRuntime
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.run.adHocRuntime
import net.internetisalie.lunar.toolchain.ui.LuaRuntimeComboBox
import javax.swing.JComponent
import javax.swing.JPanel

enum class LuaTestFramework {
    BUSTED,
    LUNITY
}

class LuaTestRunConfigurationType : ConfigurationTypeBase(
    ID, "Lua Tests", "Run Lua test suites",
    NotNullLazyValue.createValue { LuaIcons.TEST }
) {
    init {
        addFactory(LuaTestRunConfigurationFactory(this))
    }

    companion object {
        const val ID = "LuaTestRunConfiguration"
        fun getInstance(): LuaTestRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(LuaTestRunConfigurationType::class.java)
    }
}

class LuaTestRunConfigurationFactory(type: ConfigurationTypeBase) : ConfigurationFactory(type) {
    override fun getId(): String = "LuaTestScript"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        LuaTestRunConfiguration(project, this, "Lua Tests")

    override fun getOptionsClass(): Class<out BaseState> =
        LuaTestRunConfigurationOptions::class.java
}

class LuaTestRunConfigurationOptions : RunConfigurationOptions() {
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
    private val myInterpreterArguments: StoredProperty<String?> = string("").provideDelegate(
        this, "interpreterArguments"
    )
    private val myTestFramework: StoredProperty<String?> = string("BUSTED").provideDelegate(
        this, "testFramework"
    )
    private val myTestTarget: StoredProperty<String?> = string("").provideDelegate(
        this, "testTarget"
    )
    private val myTestTargetType: StoredProperty<String?> = string("FILE").provideDelegate(
        this, "testTargetType"
    )
    private val myExtraTestArguments: StoredProperty<String?> = string("").provideDelegate(
        this, "extraTestArguments"
    )
    private val myFailedTestNames: StoredProperty<String?> = string("").provideDelegate(
        this, "failedTestNames"
    )

    var interpreter: String?
        get() = myInterpreter.getValue(this)
        set(value) = myInterpreter.setValue(this, value)

    var workingDirectory: String?
        get() = myWorkingDirectory.getValue(this)
        set(value) = myWorkingDirectory.setValue(this, value)

    var sourcePath: String?
        get() = mySourcePath.getValue(this)
        set(value) = mySourcePath.setValue(this, value)

    var environmentVariables: MutableMap<String, String>
        get() = myEnvironmentVariables.getValue(this)
        set(value) = myEnvironmentVariables.setValue(this, value)

    var environmentFile: String?
        get() = myEnvironmentFile.getValue(this)
        set(value) = myEnvironmentFile.setValue(this, value)

    var environmentProcess: String?
        get() = myEnvironmentProcess.getValue(this)
        set(value) = myEnvironmentProcess.setValue(this, value)

    var interpreterArguments: String?
        get() = myInterpreterArguments.getValue(this)
        set(value) = myInterpreterArguments.setValue(this, value)

    var testFramework: String?
        get() = myTestFramework.getValue(this)
        set(value) = myTestFramework.setValue(this, value)

    var testTarget: String?
        get() = myTestTarget.getValue(this)
        set(value) = myTestTarget.setValue(this, value)

    var testTargetType: String?
        get() = myTestTargetType.getValue(this)
        set(value) = myTestTargetType.setValue(this, value)

    var extraTestArguments: String?
        get() = myExtraTestArguments.getValue(this)
        set(value) = myExtraTestArguments.setValue(this, value)

    var failedTestNames: String?
        get() = myFailedTestNames.getValue(this)
        set(value) = myFailedTestNames.setValue(this, value)
}

class LuaTestRunConfiguration(project: Project, factory: ConfigurationFactory?, name: String?) :
    RunConfigurationBase<LuaTestRunConfigurationOptions?>(project, factory, name),
    SMRunnerConsolePropertiesProvider {

    override fun createTestConsoleProperties(executor: Executor): SMTRunnerConsoleProperties =
        LuaTestConsoleProperties(this, executor)

    public override fun getOptions(): LuaTestRunConfigurationOptions =
        super.getOptions() as LuaTestRunConfigurationOptions

    var interpreter: LuaRegisteredTool?
        get() {
            val interpreterPath = options.interpreter ?: return null
            if (interpreterPath.isEmpty()) return null
            return LuaToolchainRegistry.getInstance().findByPath(interpreterPath)
                ?: adHocRuntime(interpreterPath)
        }
        set(interpreter) {
            options.interpreter = interpreter?.path
        }

    /**
     * The RUNTIME tool to actually run tests with (TOOLING-05 §3.2, ROCKS-16 follow-up): an
     * explicit stored path always wins, otherwise the project-resolved runtime. Execution-time
     * resolution, kept out of [interpreter] so an unset config tracks the project default
     * dynamically rather than freezing a snapshot into the run configuration.
     */
    fun resolveInterpreter(): LuaRegisteredTool? =
        resolveConfiguredRuntime(project, options.interpreter)

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

    var interpreterArguments: String?
        get() = options.interpreterArguments
        set(interpreterArguments) {
            options.interpreterArguments = interpreterArguments
        }

    var testFramework: LuaTestFramework
        get() = LuaTestFramework.valueOf(options.testFramework ?: "BUSTED")
        set(value) {
            options.testFramework = value.name
        }

    var testTarget: String?
        get() = options.testTarget
        set(value) {
            options.testTarget = value ?: ""
        }

    var testTargetType: String?
        get() = options.testTargetType
        set(value) {
            options.testTargetType = value ?: "FILE"
        }

    var extraTestArguments: String?
        get() = options.extraTestArguments
        set(value) {
            options.extraTestArguments = value ?: ""
        }

    var failedTestNames: String?
        get() = options.failedTestNames
        set(value) {
            options.failedTestNames = value ?: ""
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> =
        LuaTestSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        LuaTestCommandLineState(this, environment)

    override fun checkConfiguration() {
        if (options.interpreter.isNullOrEmpty()) {
            throw RuntimeConfigurationException("Interpreter is not defined")
        }
        if (options.testTarget.isNullOrEmpty()) {
            throw RuntimeConfigurationException("Test target is not defined")
        }
    }
}

class LuaTestSettingsEditor(private val project: Project) : SettingsEditor<LuaTestRunConfiguration>() {
    private val myPanel: JPanel
    private val frameworkCombo = ComboBox(LuaTestFramework.entries.toTypedArray())
    private val targetTypeCombo = ComboBox(arrayOf("FILE", "DIRECTORY", "PATTERN"))
    private val testTargetField = TextFieldWithBrowseButton()
    private val interpreterField = ComboBox<LuaRegisteredTool>()
    private val workingDirectoryField = TextFieldWithBrowseButton()
    private val extraArgsField = RawCommandLineEditor()
    private val environmentVariablesField = EnvironmentVariablesTextFieldWithBrowseButton()

    init {
        LuaRuntimeComboBox.customize(project, interpreterField)

        testTargetField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleFileOrDir()
        )

        workingDirectoryField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleDir()
        )

        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Test framework", frameworkCombo)
            .addLabeledComponent("Target type", targetTypeCombo)
            .addLabeledComponent("Test target", testTargetField)
            .addLabeledComponent("Interpreter", interpreterField)
            .addLabeledComponent("Working directory", workingDirectoryField)
            .addLabeledComponent("Extra arguments", extraArgsField)
            .addLabeledComponent("Environment", environmentVariablesField)
            .panel
    }

    override fun resetEditorFrom(config: LuaTestRunConfiguration) {
        frameworkCombo.item = config.testFramework
        targetTypeCombo.item = config.testTargetType ?: "FILE"
        testTargetField.text = config.testTarget ?: ""
        interpreterField.item = config.interpreter
        workingDirectoryField.text = config.workingDirectory ?: ""
        extraArgsField.text = config.extraTestArguments ?: ""
        environmentVariablesField.data = config.environmentVariables ?: EnvironmentVariablesData.DEFAULT
    }

    override fun applyEditorTo(config: LuaTestRunConfiguration) {
        config.testFramework = frameworkCombo.item ?: LuaTestFramework.BUSTED
        config.testTargetType = targetTypeCombo.item ?: "FILE"
        config.testTarget = testTargetField.text
        config.interpreter = interpreterField.item
        config.workingDirectory = workingDirectoryField.text
        config.extraTestArguments = extraArgsField.text
        config.environmentVariables = environmentVariablesField.data
    }

    override fun createEditor(): JComponent = myPanel
}
