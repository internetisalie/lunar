---
id: "RUN-08-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "RUN-08"
folders:
  - "[[features/debug/run-05-test-runner/requirements|requirements]]"
---

# Technical Design: RUN-08 — Lua Test Runner Integration

## 1. Architecture Overview

### Current State

The plugin has a working Lua run/debug infrastructure:
- [LuaRunConfigurationType](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt) — single `ConfigurationType` (ID `"LuaRunConfiguration"`) with one factory
- [LuaRunConfiguration](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt#L156) — `RunConfigurationBase<LuaRunConfigurationOptions?>` with fields for script, interpreter, args, env, working dir
- Process launch via inline anonymous `CommandLineState` in `getState()` using [newLuaInterpreterCommandLine](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/command/LuaCommandLine.kt#L32)
- [LuaDebugRunner](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/run/LuaDebugRunner.kt) — `GenericProgramRunner` creating XDebugSession
- Tool management via [LuaToolManager](file:///home/mini/Documents/src/lua/lunar/src/main/kotlin/net/internetisalie/lunar/tool/LuaToolManager.kt) (app-level service)

**What's missing**: No test-specific run configuration, no SMTestRunner integration, no coverage engine, no test framework detection, no `RunConfigurationProducer` of any kind (gap confirmed — zero producers in codebase).

### Prior Art in This Repo

| Component | File | Disposition |
|-----------|------|-------------|
| `LuaRunConfigurationType` | `run/LuaRunConfiguration.kt:L19` | EXTEND pattern — new `LuaTestRunConfigurationType` follows same structure |
| `LuaRunConfigurationOptions` | `run/LuaRunConfiguration.kt:L64` | EXTEND pattern — new `LuaTestRunConfigurationOptions` adds framework/target fields |
| Inline `CommandLineState` in `getState()` | `run/LuaRunConfiguration.kt:L226` | EXTEND pattern — new config uses same `newLuaInterpreterCommandLine()` helper |
| `LuaDebugRunner` | `run/LuaDebugRunner.kt` | REUSE — debug works for test configs via `canRun()` matching `LuaTestRunConfiguration` |
| `LuaToolType` enum | `tool/LuaToolDescriptor.kt:L19` | EXTEND — add `LUACOV`, `BUSTED` entries |
| `LuaToolDescriptor.DESCRIPTORS` | `tool/LuaToolDescriptor.kt:L44` | EXTEND — add new descriptors |
| `LuaToolManager.inferType()` | `tool/LuaToolManager.kt:L204` | EXTEND — add `"luacov"`, `"busted"` cases |
| `LuaToolValidator.patternFor()` | `tool/LuaToolValidator.kt:L154` | EXTEND — add version patterns |
| `LuaEditorHighlighter` (layered) | `lang/syntax/LuaEditorHighlighter.kt` | REUSE pattern — report highlighter follows same `LayeredLexerEditorHighlighter` approach |
| `LuaToolEditorNotificationProvider` | `tool/health/LuaToolEditorNotificationProvider.kt` | REUSE pattern — coverage report banner follows same `EditorNotificationProvider` pattern |
| Notification balloons | `LuaRocksActionHandler.kt:L82`, `LuaDebugRunner.kt:L120` | REUSE — same `NotificationGroupManager` pattern for LuaRocks install prompt |

### Target State

The feature adds six subsystems:

1. **Test Run Configuration** — new `ConfigurationType` with framework/target selection
2. **Test Output Parsing** — SMTestRunner event translation from Busted/Lunity JSON
3. **Test Navigation** — `SMTestLocator` + `RunConfigurationProducer` + gutter line markers
4. **Coverage Engine** — IntelliJ `CoverageEngine`/`CoverageRunner` integration with `luacov`
5. **Coverage Report Viewer** — custom `FileType` + syntax highlighter + editor banner for `luacov.report.out`
6. **Tool Registration** — `luacov` + `busted` in the `TOOL` inventory

## 2. Core Components

### 2.1 `net.internetisalie.lunar.run.test.LuaTestRunConfigurationType`
- **Responsibility**: Register the "Lua Tests" configuration type in the IDE.
- **Threading**: EDT (registration only)
- **Collaborators**: `ConfigurationTypeBase`, `LuaTestRunConfigurationFactory`
- **Key API**:
  ```kotlin
  class LuaTestRunConfigurationType : ConfigurationTypeBase(
      "LuaTestRunConfiguration",
      "Lua Tests",
      "Run Lua test suites",
      LuaIcons.TEST  // new icon constant
  ) {
      init { addFactory(LuaTestRunConfigurationFactory(this)) }
      companion object {
          fun getInstance(): LuaTestRunConfigurationType =
              ConfigurationTypeUtil.findConfigurationType(LuaTestRunConfigurationType::class.java)
      }
  }
  ```

### 2.2 `net.internetisalie.lunar.run.test.LuaTestRunConfigurationFactory`
- **Responsibility**: Factory for creating `LuaTestRunConfiguration` instances.
- **Threading**: EDT
- **Collaborators**: `LuaTestRunConfigurationType`
- **Key API**:
  ```kotlin
  class LuaTestRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
      override fun getId(): String = "LuaTestScript"
      override fun createTemplateConfiguration(project: Project): RunConfiguration =
          LuaTestRunConfiguration(project, this, "Lua Tests")
      override fun getOptionsClass(): Class<out BaseState> =
          LuaTestRunConfigurationOptions::class.java
  }
  ```

### 2.3 `net.internetisalie.lunar.run.test.LuaTestRunConfigurationOptions`
- **Responsibility**: Persist test-specific configuration state via `BaseState` property delegation.
- **Threading**: Any (data holder)
- **Collaborators**: `RunConfigurationOptions`
- **Key API**:
  ```kotlin
  class LuaTestRunConfigurationOptions : RunConfigurationOptions() {
      // Inherited from LuaRunConfiguration pattern:
      var interpreter by string("").provideDelegate(this, "interpreter")
      var workingDirectory by string("").provideDelegate(this, "workingDirectory")
      var sourcePath by string("").provideDelegate(this, "sourcePath")
      var environmentVariables by map<String, String>().provideDelegate(this, "environmentVariables")
      var interpreterArguments by string("").provideDelegate(this, "interpreterArguments")

      // Test-specific fields:
      var testFramework by string("BUSTED").provideDelegate(this, "testFramework")  // "BUSTED" | "LUNITY"
      var testTarget by string("").provideDelegate(this, "testTarget")              // file/dir/pattern
      var testTargetType by string("FILE").provideDelegate(this, "testTargetType")  // "FILE" | "DIRECTORY" | "PATTERN"
      var extraTestArguments by string("").provideDelegate(this, "extraTestArguments")
      var failedTestNames by string("").provideDelegate(this, "failedTestNames")    // comma-separated, for rerun
  }
  ```

### 2.4 `net.internetisalie.lunar.run.test.LuaTestRunConfiguration`
- **Responsibility**: "Lua Tests" run configuration. Holds state, builds command line, provides editor.
- **Threading**: EDT for editor; pooled for `getState()`
- **Collaborators**: `LuaTestRunConfigurationOptions`, `LuaTestCommandLineState`, `LuaTestSettingsEditor`, `LuaToolManager`, `newLuaInterpreterCommandLine()`
- **Key API**:
  ```kotlin
  class LuaTestRunConfiguration(
      project: Project,
      factory: ConfigurationFactory?,
      name: String?
  ) : RunConfigurationBase<LuaTestRunConfigurationOptions?>(project, factory, name) {

      // Property accessors delegating to options (same pattern as LuaRunConfiguration)
      var testFramework: LuaTestFramework
          get() = LuaTestFramework.valueOf(options?.testFramework ?: "BUSTED")
          set(v) { options?.testFramework = v.name }

      var testTarget: String?
          get() = options?.testTarget
          set(v) { options?.testTarget = v ?: "" }

      // ... interpreter, workingDirectory, etc. same pattern as LuaRunConfiguration

      override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
          LuaTestSettingsEditor(project)

      override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
          LuaTestCommandLineState(this, environment)

      override fun checkConfiguration() {
          // Validate interpreter exists, test target is set
      }
  }

  enum class LuaTestFramework { BUSTED, LUNITY }
  ```

### 2.5 `net.internetisalie.lunar.run.test.LuaTestCommandLineState`
- **Responsibility**: Build the test process command line and attach SMTestRunner console.
- **Threading**: Pooled (called from execution framework)
- **Collaborators**: `GeneralCommandLine`, `SMTestRunnerConnectionUtil`, `LuaTestConsoleProperties`, `newLuaInterpreterCommandLine()`
- **Key API**:
  ```kotlin
  class LuaTestCommandLineState(
      private val config: LuaTestRunConfiguration,
      environment: ExecutionEnvironment
  ) : CommandLineState(environment) {

      override fun startProcess(): ProcessHandler {
          val commandLine = buildCommandLine()
          return KillableColoredProcessHandler(commandLine)
      }

      override fun createConsole(executor: Executor): ConsoleView? {
          val properties = LuaTestConsoleProperties(config, executor)
          return SMTestRunnerConnectionUtil.createAndAttachConsole(
              "LuaTest", processHandler, properties
          )
      }

      private fun buildCommandLine(): GeneralCommandLine {
          // See §3.1 for the algorithm
      }
  }
  ```

### 2.6 `net.internetisalie.lunar.run.test.LuaTestConsoleProperties`
- **Responsibility**: Configure SMTestRunner behavior and provide test locator.
- **Threading**: EDT
- **Collaborators**: `SMTRunnerConsoleProperties`, `LuaTestLocator`
- **Key API**:
  ```kotlin
  class LuaTestConsoleProperties(
      config: LuaTestRunConfiguration,
      executor: Executor
  ) : SMTRunnerConsoleProperties(config, "LuaTest", executor) {

      init {
          setIdBasedTestTree(true)       // use test IDs, not suite paths
          isPrintTestingStartedTime = true
      }

      override fun getTestLocator(): SMTestLocator = LuaTestLocator.INSTANCE
  }
  ```

### 2.7 `net.internetisalie.lunar.run.test.LuaTestOutputToEventsConverter`
- **Responsibility**: Parse JSON-line output from Busted/Lunity and translate to SMTestRunner events.
- **Threading**: Pooled (process output reader thread)
- **Collaborators**: `OutputToGeneralTestEventsConverter`, `GeneralTestEventsProcessor`
- **Key API**:
  ```kotlin
  class LuaTestOutputToEventsConverter(
      testFrameworkName: String,
      consoleProperties: TestConsoleProperties
  ) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {

      override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
          // See §3.2 and §3.3 for parsing algorithms
      }
  }
  ```

### 2.8 `net.internetisalie.lunar.run.test.LuaTestLocator`
- **Responsibility**: Resolve test location URLs to PSI locations for navigation.
- **Threading**: Read action
- **Collaborators**: `SMTestLocator`, `PsiManager`, `VfsUtil`
- **Key API**:
  ```kotlin
  object LuaTestLocator : SMTestLocator {
      const val PROTOCOL = "lua"

      override fun getLocation(
          protocol: String,
          path: String,
          project: Project,
          scope: GlobalSearchScope
      ): List<Location<PsiElement>> {
          // See §3.4 for algorithm
      }
  }
  ```

### 2.9 `net.internetisalie.lunar.run.test.LuaTestRunConfigurationProducer`
- **Responsibility**: Create test configurations from context (right-click on file/directory/test function).
- **Threading**: EDT (read action)
- **Collaborators**: `LazyRunConfigurationProducer`, `LuaTestRunConfigurationType`, PSI tree
- **Key API**:
  ```kotlin
  class LuaTestRunConfigurationProducer : LazyRunConfigurationProducer<LuaTestRunConfiguration>() {
      override fun getConfigurationFactory(): ConfigurationFactory =
          LuaTestRunConfigurationType.getInstance().configurationFactories[0]

      override fun setupConfigurationFromContext(
          configuration: LuaTestRunConfiguration,
          context: ConfigurationContext,
          sourceElement: Ref<PsiElement>
      ): Boolean {
          // See §3.5 for algorithm
      }

      override fun isConfigurationFromContext(
          configuration: LuaTestRunConfiguration,
          context: ConfigurationContext
      ): Boolean {
          // See §3.5 for algorithm
      }
  }
  ```

### 2.10 `net.internetisalie.lunar.run.test.LuaTestRunLineMarkerProvider`
- **Responsibility**: Show green "Play" gutter icons next to `describe`/`it`/`test_` declarations.
- **Threading**: Read action
- **Collaborators**: `RunLineMarkerContributor`, PSI tree, `LuaFuncCall`
- **Key API**:
  ```kotlin
  class LuaTestRunLineMarkerProvider : RunLineMarkerContributor() {
      override fun getInfo(element: PsiElement): Info? {
          // See §3.6 for algorithm
      }
  }
  ```

### 2.11 `net.internetisalie.lunar.run.test.LuaRerunFailedTestsAction`
- **Responsibility**: Rerun only the subset of tests that failed.
- **Threading**: EDT
- **Collaborators**: `AbstractRerunFailedTestsAction`, `LuaTestRunConfiguration`
- **Key API**:
  ```kotlin
  class LuaRerunFailedTestsAction(
      consoleView: ConsoleView,
      properties: ConsoleProperties
  ) : AbstractRerunFailedTestsAction(consoleView) {

      override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile? {
          // See §3.7 for algorithm
      }
  }
  ```

### 2.12 `net.internetisalie.lunar.run.test.LuaTestSettingsEditor`
- **Responsibility**: Configuration editor UI for "Lua Tests" run configuration.
- **Threading**: EDT
- **Collaborators**: `SettingsEditor`, `FormBuilder`, `LuaTestRunConfiguration`
- **Key API**:
  ```kotlin
  class LuaTestSettingsEditor(private val project: Project) :
      SettingsEditor<LuaTestRunConfiguration>() {

      private val frameworkCombo: ComboBox<LuaTestFramework>
      private val targetTypeCombo: ComboBox<String>  // "FILE", "DIRECTORY", "PATTERN"
      private val testTargetField: TextFieldWithBrowseButton
      private val interpreterField: ComboBox<LuaInterpreter>
      private val workingDirectoryField: TextFieldWithBrowseButton
      private val extraArgsField: RawCommandLineEditor
      private val envVarsComponent: EnvironmentVariablesComponent

      override fun resetEditorFrom(config: LuaTestRunConfiguration) { /* ... */ }
      override fun applyEditorTo(config: LuaTestRunConfiguration) { /* ... */ }
      override fun createEditor(): JComponent { /* FormBuilder panel */ }
  }
  ```

### 2.13 `net.internetisalie.lunar.coverage.LuaCoverageEngine`
- **Responsibility**: Integrate Lua coverage with IntelliJ's coverage framework.
- **Threading**: Mixed (EDT for UI, pooled for data loading)
- **Collaborators**: `CoverageEngine`, `LuaCoverageRunner`, `LuaCoverageAnnotator`, `LuaFileType`
- **Key API**:
  ```kotlin
  class LuaCoverageEngine : CoverageEngine() {
      override fun isApplicableTo(conf: RunConfigurationBase<*>): Boolean =
          conf is LuaTestRunConfiguration

      override fun canHavePerTestCoverage(conf: RunConfigurationBase<*>): Boolean = false

      override fun createCoverageSuite(
          runner: CoverageRunner,
          name: String,
          fileProvider: CoverageFileProvider,
          filters: Array<String>?,
          lastCovered: Long,
          suiteToMerge: String?,
          coverageByTestEnabled: Boolean,
          branchCoverage: Boolean,
          trackTestFolders: Boolean,
          project: Project
      ): CoverageSuite? = BaseCoverageSuite(
          name, fileProvider, lastCovered, coverageByTestEnabled,
          branchCoverage, trackTestFolders, runner, this, project
      )

      override fun getCoverageAnnotator(project: Project): CoverageAnnotator =
          LuaCoverageAnnotator.getInstance(project)

      override fun coverageEditorHighlightingApplicableTo(file: PsiFile): Boolean =
          file.fileType == LuaFileType

      override fun getQualifiedNames(sourceFile: PsiFile): Set<String> =
          setOf(sourceFile.virtualFile.path)

      override fun acceptedByFilters(psiFile: PsiFile, bundle: CoverageSuitesBundle): Boolean =
          psiFile.fileType == LuaFileType

      override fun recompileProjectAndRerunAction(
          project: Project, suite: CoverageSuitesBundle, configuration: RunConfiguration
      ): Boolean = false  // Lua is interpreted

      override fun getPresentableText(): String = "Lua Coverage"
      override fun createCoverageViewExtension(
          project: Project, suite: CoverageSuitesBundle, stateBean: CoverageViewManager.StateBean
      ): CoverageViewExtension? = null  // Use default
  }
  ```

### 2.14 `net.internetisalie.lunar.coverage.LuaCoverageRunner`
- **Responsibility**: Parse luacov stats/report files into `ProjectData`.
- **Threading**: Pooled (called from coverage framework background thread)
- **Collaborators**: `CoverageRunner`, `ProjectData`, `ClassData`, `LineData`
- **Key API**:
  ```kotlin
  class LuaCoverageRunner : CoverageRunner() {
      override fun loadCoverageData(sessionDataFile: File, baseCoverageSuite: CoverageSuite?): ProjectData? {
          // See §3.8 for algorithm
      }
      override fun getPresentableName(): String = "LuaCov"
      override fun getId(): String = "LuaCov"
      override fun getDataFileExtension(): String = "out"
      override fun acceptsCoverageEngine(engine: CoverageEngine): Boolean =
          engine is LuaCoverageEngine
  }
  ```

### 2.15 `net.internetisalie.lunar.coverage.LuaCoverageAnnotator`
- **Responsibility**: Provide coverage annotation strings for files/directories in the project tree.
- **Threading**: Pooled for computation, EDT for display
- **Collaborators**: `SimpleCoverageAnnotator`
- **Key API**:
  ```kotlin
  class LuaCoverageAnnotator private constructor(project: Project) :
      SimpleCoverageAnnotator(project) {

      companion object {
          fun getInstance(project: Project): LuaCoverageAnnotator =
              project.getService(LuaCoverageAnnotator::class.java)
      }

      override fun getFileCoverageInformationString(
          file: PsiFile, bundle: CoverageSuitesBundle, manager: CoverageDataManager
      ): String? = super.getFileCoverageInformationString(file, bundle, manager)

      override fun getDirCoverageInformationString(
          dir: PsiDirectory, bundle: CoverageSuitesBundle, manager: CoverageDataManager
      ): String? = super.getDirCoverageInformationString(dir, bundle, manager)
  }
  ```

### 2.16 `net.internetisalie.lunar.coverage.LuaCoverageProgramRunner`
- **Responsibility**: Intercept "Run with Coverage" to inject `-lluacov` and set up the data file.
- **Threading**: EDT → pooled
- **Collaborators**: `GenericProgramRunner`, `CoverageDataManager`, `LuaToolManager`
- **Key API**:
  ```kotlin
  class LuaCoverageProgramRunner : GenericProgramRunner<RunnerSettings>() {
      override fun getRunnerId(): String = "LuaCoverageProgramRunner"

      override fun canRun(executorId: String, profile: RunProfile): Boolean =
          executorId == CoverageExecutor.EXECUTOR_ID && profile is LuaTestRunConfiguration

      override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
          // See §3.9 for algorithm
      }
  }
  ```

### 2.17 `net.internetisalie.lunar.coverage.LuaCovReportImportAction`
- **Responsibility**: Action to import a `luacov.report.out` file and render coverage overlays.
- **Threading**: EDT → pooled for parsing
- **Collaborators**: `AnAction`, `FileChooser`, `LuaCovReportParser`, `CoverageDataManager`
- **Key API**:
  ```kotlin
  class LuaCovReportImportAction : AnAction(
      "Import LuaCov Report...",
      "Load coverage data from a luacov.report.out file",
      LuaIcons.COVERAGE
  ) {
      override fun actionPerformed(e: AnActionEvent) {
          // See §3.10 for algorithm
      }
      override fun update(e: AnActionEvent) {
          e.presentation.isEnabledAndVisible = e.project != null
      }
  }
  ```

### 2.18 `net.internetisalie.lunar.coverage.LuaCovReportParser`
- **Responsibility**: Parse `luacov.report.out` files into per-file line coverage data.
- **Threading**: Pooled (never on EDT)
- **Collaborators**: `ProjectData`, `ClassData`, `LineData`
- **Key API**:
  ```kotlin
  object LuaCovReportParser {
      data class FileCoverage(
          val filePath: String,
          val lineHits: Map<Int, Int>  // 1-indexed line → hit count (0 = uncovered)
      )

      fun parse(reportFile: File): List<FileCoverage> {
          // See §3.11 for algorithm
      }

      fun toProjectData(coverages: List<FileCoverage>): ProjectData {
          // See §3.12 for algorithm
      }
  }
  ```

### 2.19 `net.internetisalie.lunar.coverage.LuaCovStatsParser`
- **Responsibility**: Parse `luacov.stats.out` files into per-file line coverage data.
- **Threading**: Pooled (never on EDT)
- **Collaborators**: `ProjectData`, `ClassData`, `LineData`
- **Key API**:
  ```kotlin
  object LuaCovStatsParser {
      fun parse(statsFile: File): List<LuaCovReportParser.FileCoverage> {
          // See §3.13 for algorithm
      }
  }
  ```

### 2.20 `net.internetisalie.lunar.coverage.report.LuaCovReportLanguage`
- **Responsibility**: Minimal language singleton for `luacov.report.out` file type.
- **Threading**: N/A (singleton)
- **Key API**:
  ```kotlin
  object LuaCovReportLanguage : Language("LuaCovReport")
  ```

### 2.21 `net.internetisalie.lunar.coverage.report.LuaCovReportFileType`
- **Responsibility**: File type for `luacov.report.out` files.
- **Threading**: N/A (singleton)
- **Key API**:
  ```kotlin
  object LuaCovReportFileType : LanguageFileType(LuaCovReportLanguage) {
      override fun getName(): String = "LuaCov Report"
      override fun getDescription(): String = "LuaCov code coverage report"
      override fun getDefaultExtension(): String = ""
      override fun getIcon(): Icon = LuaIcons.COVERAGE_REPORT
  }
  ```

### 2.22 `net.internetisalie.lunar.coverage.report.LuaCovReportLexer`
- **Responsibility**: Hand-written lexer for the coverage report format. Produces tokens for headers, paths, hit prefixes, and Lua code segments.
- **Threading**: Any (stateless once started)
- **Collaborators**: `LexerBase`
- **Key API**:
  ```kotlin
  class LuaCovReportLexer : LexerBase() {
      override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int)
      override fun getState(): Int
      override fun getTokenType(): IElementType?
      override fun getTokenStart(): Int
      override fun getTokenEnd(): Int
      override fun advance()
  }
  ```
  **Token types** (defined in companion object):
  - `HEADER_BOUNDARY` — `=====...=====` lines
  - `FILE_PATH` — the file path line between boundaries
  - `HIT_COVERED` — prefix like `   1 `, `  10 ` (non-zero hit count)
  - `HIT_UNCOVERED` — prefix `***0 `
  - `HIT_NONE` — prefix `     ` (non-executable line, 5 spaces)
  - `LUA_CODE` — the remainder of each code line after the prefix
  - `NEWLINE` — line terminator
  - `WHITESPACE` — other whitespace

### 2.23 `net.internetisalie.lunar.coverage.report.LuaCovReportSyntaxHighlighter`
- **Responsibility**: Map report tokens to text attributes.
- **Threading**: Any
- **Collaborators**: `SyntaxHighlighterBase`, `LuaCovReportLexer`
- **Key API**:
  ```kotlin
  class LuaCovReportSyntaxHighlighter : SyntaxHighlighterBase() {
      override fun getHighlightingLexer(): Lexer = LuaCovReportLexer()
      override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
          return when (tokenType) {
              LuaCovReportLexer.HEADER_BOUNDARY -> pack(LuaCovReportHighlight.HEADER)
              LuaCovReportLexer.FILE_PATH -> pack(LuaCovReportHighlight.FILE_PATH)
              LuaCovReportLexer.HIT_COVERED -> pack(LuaCovReportHighlight.COVERED)
              LuaCovReportLexer.HIT_UNCOVERED -> pack(LuaCovReportHighlight.UNCOVERED)
              else -> EMPTY_KEYS
          }
      }
  }
  ```

### 2.24 `net.internetisalie.lunar.coverage.report.LuaCovReportHighlight`
- **Responsibility**: Define text attribute keys for coverage report highlighting.
- **Threading**: N/A (constants)
- **Key API**:
  ```kotlin
  object LuaCovReportHighlight {
      val HEADER: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
          "LUACOV_HEADER", DefaultLanguageHighlighterColors.METADATA
      )
      val FILE_PATH: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
          "LUACOV_FILE_PATH", DefaultLanguageHighlighterColors.STRING
      )
      val COVERED: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
          "LUACOV_COVERED"  // green foreground, configured in color scheme
      )
      val UNCOVERED: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
          "LUACOV_UNCOVERED"  // red foreground, configured in color scheme
      )
  }
  ```

### 2.25 `net.internetisalie.lunar.coverage.report.LuaCovReportEditorHighlighterProvider`
- **Responsibility**: Provide the layered editor highlighter for report files (embeds Lua syntax in code segments).
- **Threading**: EDT
- **Collaborators**: `EditorHighlighterProvider`, `LayeredLexerEditorHighlighter`, `LuaSyntaxHighlighter`
- **Key API**:
  ```kotlin
  class LuaCovReportEditorHighlighterProvider : EditorHighlighterProvider {
      override fun getEditorHighlighter(
          project: Project?, fileType: FileType, virtualFile: VirtualFile?, colors: EditorColorsScheme
      ): EditorHighlighter {
          return LuaCovReportEditorHighlighter(colors)
      }
  }

  class LuaCovReportEditorHighlighter(scheme: EditorColorsScheme) :
      LayeredLexerEditorHighlighter(LuaCovReportSyntaxHighlighter(), scheme) {
      init {
          val luaLayer = LayerDescriptor(LuaSyntaxHighlighter(), "\n")
          registerLayer(LuaCovReportLexer.LUA_CODE, luaLayer)
      }
  }
  ```

### 2.26 `net.internetisalie.lunar.coverage.report.LuaCovReportNotificationProvider`
- **Responsibility**: Show editor banner when `luacov.report.out` files are opened.
- **Threading**: EDT
- **Collaborators**: `EditorNotificationProvider`, `DumbAware`, `EditorNotificationPanel`, `LuaCovReportImportAction`
- **Key API**:
  ```kotlin
  class LuaCovReportNotificationProvider : EditorNotificationProvider, DumbAware {
      override fun collectNotificationData(
          project: Project,
          file: VirtualFile
      ): Function<in FileEditor, out JComponent?>? {
          if (file.fileType != LuaCovReportFileType) return null
          return Function { fileEditor ->
              EditorNotificationPanel(fileEditor).apply {
                  text = "This is a LuaCov coverage report."
                  createActionLabel("Load Coverage onto Project Files") {
                      // Trigger LuaCovReportImportAction logic with this file
                  }
                  createActionLabel("Dismiss") {
                      // Remove panel
                  }
              }
          }
      }
  }
  ```

### 2.27 `net.internetisalie.lunar.coverage.report.LuaCovReportSyntaxHighlighterFactory`
- **Responsibility**: Provide the syntax highlighter for the `LuaCovReport` language.
- **Threading**: Any
- **Key API**:
  ```kotlin
  class LuaCovReportSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
      override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
          LuaCovReportSyntaxHighlighter()
  }
  ```

## 3. Algorithms

### 3.1 Command Line Construction (`LuaTestCommandLineState.buildCommandLine()`)
- **Input**: `LuaTestRunConfiguration` with framework, target, interpreter
- **Output**: `GeneralCommandLine`
- **Steps**:
  1. Resolve interpreter via `config.interpreter` → `newLuaInterpreterCommandLine(interpreter)` → `GeneralCommandLine`.
  2. Set `workDirectory` from `config.workingDirectory` (or project basePath).
  3. Configure environment variables from `config.environmentVariables`.
  4. **If framework = BUSTED**:
     a. Resolve `busted` binary path via `LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.BUSTED)?.path`.
     b. If found: set `cmd.exePath = bustedPath` (Busted is its own executable, not a Lua script loaded via `lua`).
     c. Add `--output=json` argument (Busted's JSON output reporter).
     d. If `testTargetType == FILE`: add test file path as positional argument.
     e. If `testTargetType == DIRECTORY`: add `--recursive` + directory path.
     f. If `testTargetType == PATTERN`: add `--filter=<pattern>`.
     g. If `failedTestNames` is non-empty: add `--filter=<escaped-names-OR-joined>`.
     h. Add `extraTestArguments`.
  5. **If framework = LUNITY**:
     a. Use interpreter as `exePath` (Lunity tests are just Lua scripts).
     b. Add `config.interpreterArguments`.
     c. Add test file path (the Lua script that imports Lunity).
     d. Add `extraTestArguments`.
  6. Return the `GeneralCommandLine`.

### 3.2 Busted JSON Output Parsing
- **Input**: stdout line (string)
- **Output**: SMTestRunner events emitted to `GeneralTestEventsProcessor`
- **Format**: Busted with `--output=json` produces **one final JSON blob** on stdout (not streaming). The JSON structure:
  ```json
  {
    "successes": [ { "name": "suite → test name", "trace": { "source": "@file.lua", "currentline": 10 }, "duration": 0.001 } ],
    "failures": [ { "name": "suite → test name", "message": "Expected 4 but got 5", "trace": { ... }, "duration": 0.002 } ],
    "errors": [ { "name": "...", "message": "...", "trace": { ... } } ],
    "pendings": [ { "name": "..." } ],
    "duration": 0.5
  }
  ```
- **Steps**:
  1. Buffer all stdout until process exits (Busted dumps JSON at end).
  2. Parse JSON using `com.google.gson.JsonParser.parseString()` (Gson is bundled with IntelliJ).
  3. Decompose `name` field by ` → ` separator into suite/test hierarchy: e.g. `"math utils → addition → adds two numbers"` → suites `["math utils", "addition"]` + test `"adds two numbers"`.
  4. Emit `testSuiteStarted` for each unique suite prefix (track opened suites to avoid duplicates).
  5. For each entry in `successes`: emit `testStarted(name, locationUrl)` → `testFinished(name, duration_ms)`.
  6. For each entry in `failures`: emit `testStarted(name, locationUrl)` → `testFailed(name, message, stackTrace)` → `testFinished(name, duration_ms)`.
  7. For each entry in `errors`: emit `testStarted(name, locationUrl)` → `testFailed(name, message, stackTrace, isTestError=true)` → `testFinished(name, duration_ms)`.
  8. For each entry in `pendings`: emit `testIgnored(name)`.
  9. Close all open suites in reverse order.
  10. **Location URL**: construct from `trace.source` (strip leading `@`) + `trace.currentline` → `"lua://<file>:<line>"`.
- **Edge cases**: If JSON parse fails, fall through to raw console output (requirement: fallback to console).

### 3.3 Lunity JSON Line Parsing
- **Input**: single line from stdout
- **Output**: SMTestRunner events
- **Format**: each line is a self-contained JSON object (spec from RUN-08-04):
  ```json
  {"event": "suite_start", "name": "math tests"}
  {"event": "test_start", "name": "test_addition", "suite": "math tests"}
  {"event": "test_pass", "name": "test_addition", "duration": 12}
  ```
- **Steps**:
  1. For each line: attempt `JsonParser.parseString(line)`.
  2. If parse fails: skip (not a JSON event line — may be raw output).
  3. Extract `event` field as string.
  4. Map to SMTestRunner events:
     - `"suite_start"` → `processor.onSuiteStarted(TestSuiteStartedEvent(name, null))`
     - `"suite_end"` → `processor.onSuiteFinished(TestSuiteFinishedEvent(name))`
     - `"test_start"` → `processor.onTestStarted(TestStartedEvent(name, locationUrl))` where `locationUrl = "lua://${file}:${line}"` if file/line present
     - `"test_pass"` → `processor.onTestFinished(TestFinishedEvent(name, duration ?: 0))`
     - `"test_fail"` / `"test_error"` → `processor.onTestFailure(TestFailedEvent(name, message, trace))` then `processor.onTestFinished(TestFinishedEvent(name, duration ?: 0))`
     - `"test_ignore"` → `processor.onTestIgnored(TestIgnoredEvent(name, ""))`
  5. Unknown event types: skip silently.

### 3.4 Test Location Resolution (`LuaTestLocator.getLocation()`)
- **Input**: `protocol="lua"`, `path="/path/to/file.lua:15"`
- **Output**: `List<Location<PsiElement>>`
- **Steps**:
  1. Split `path` on last `:` → `filePath` + `lineStr`.
  2. Parse `lineStr` to `Int` (default to 1 on failure).
  3. Find `VirtualFile` via `VfsUtil.findFile(Path.of(filePath), true)` or `LocalFileSystem.getInstance().findFileByPath(filePath)`.
  4. If not found: return `emptyList()`.
  5. Find `PsiFile` via `PsiManager.getInstance(project).findFile(virtualFile)`.
  6. If not found: return `emptyList()`.
  7. Get `Document` via `PsiDocumentManager.getInstance(project).getDocument(psiFile)`.
  8. Compute offset: `document.getLineStartOffset(line - 1)` (0-indexed document lines).
  9. Find `PsiElement` at offset: `psiFile.findElementAt(offset)`.
  10. Return `listOf(PsiLocation(psiElement))`.

### 3.5 Context-Based Configuration (`LuaTestRunConfigurationProducer`)

#### `setupConfigurationFromContext()`
- **Input**: `ConfigurationContext` (contains `PsiElement`, `VirtualFile`, `Project`)
- **Output**: `Boolean` (true if configuration was set up)
- **Steps**:
  1. Get `virtualFile` from `context.location?.virtualFile`. If null or not `.lua` → return `false`.
  2. **Check if file is a test file**: scan file name for patterns `_spec.lua`, `_test.lua`, `test_*.lua`, or scan PSI for `describe`/`it` function calls (Busted) or `test_` prefixed functions (Lunity).
  3. If not a test file → return `false`.
  4. Determine framework:
     - If PSI contains `describe(` / `it(` calls → `BUSTED`.
     - Else if PSI contains functions starting with `test_` or `require("lunity")` → `LUNITY`.
     - Else → `BUSTED` (default).
  5. Set `configuration.testFramework`.
  6. **Check if context is on a specific test**:
     - Walk up from `context.psiLocation` to find an enclosing `LuaFuncCall` whose function name is `it` or `describe`.
     - If found on `it(...)`: set `testTargetType = PATTERN`, `testTarget = <test name from first string argument>`.
     - If found on `describe(...)`: set `testTargetType = PATTERN`, `testTarget = <describe block name>`.
     - Else: set `testTargetType = FILE`, `testTarget = virtualFile.path`.
  7. Set `configuration.name` = `"Lua Tests: <filename or test name>"`.
  8. Resolve interpreter: `LuaProjectSettings.getInstance(project).state.interpreter?.path` or fallback to `LuaApplicationSettings.getInstance().state.interpreters.firstOrNull()?.path`.
  9. Set `configuration.workingDirectory` = `virtualFile.parent?.path ?: project.basePath`.
  10. `sourceElement.set(context.psiLocation)`.
  11. Return `true`.

#### `isConfigurationFromContext()`
- **Steps**:
  1. Get `virtualFile` from `context.location?.virtualFile`.
  2. Return `true` if `configuration.testTarget == virtualFile?.path` and `configuration.testTargetType == FILE`, or if the pattern matches.

### 3.6 Test Gutter Icons (`LuaTestRunLineMarkerProvider.getInfo()`)
- **Input**: `PsiElement`
- **Output**: `Info?` (null if not a test marker position)
- **Steps**:
  1. Only process `LeafPsiElement` to avoid double-marking parent elements.
  2. Check if the element is the `NAME` token (identifier) of a `LuaFuncCall`.
  3. Get the function name text.
  4. **Busted markers**: If name is `"describe"` or `"it"` or `"context"`:
     - Return `Info(AllIcons.RunConfigurations.TestState.Run, executorActions, tooltipProvider)`.
  5. **Lunity markers**: If name starts with `"test_"` and element is the name of a function declaration:
     - Return `Info(AllIcons.RunConfigurations.TestState.Run, executorActions, tooltipProvider)`.
  6. Otherwise: return `null`.

### 3.7 Rerun Failed Tests (`LuaRerunFailedTestsAction.getRunProfile()`)
- **Input**: Failed test names from the test tree
- **Output**: `RunProfile` with only failed tests
- **Steps**:
  1. Extract failed test names from the test results tree: iterate `getFailedTests(environment.project)`.
  2. Join names into a filter pattern: `name1|name2|name3` (for Busted `--filter`).
  3. Clone the original `LuaTestRunConfiguration`.
  4. Set `failedTestNames` on the clone.
  5. Return a `MyRunProfile` wrapping the modified configuration.

### 3.8 Stats File Parsing (`LuaCoverageRunner.loadCoverageData()`)
- **Input**: `sessionDataFile: File` pointing to `luacov.stats.out`
- **Output**: `ProjectData`
- **Steps**:
  1. Check file extension / name. If `luacov.stats.out` → use `LuaCovStatsParser.parse()`. If `luacov.report.out` → use `LuaCovReportParser.parse()`.
  2. Convert parsed `List<FileCoverage>` → `ProjectData` via `LuaCovReportParser.toProjectData()`.
  3. Return `ProjectData`.

### 3.9 Coverage Program Runner (`LuaCoverageProgramRunner.doExecute()`)
- **Input**: `RunProfileState`, `ExecutionEnvironment`
- **Output**: `RunContentDescriptor`
- **Steps**:
  1. Check that `luacov` is available: call `LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.LUACOV)`.
  2. If not available:
     - Show balloon notification: `"Code coverage library 'luacov' is not installed. [Install via LuaRocks]"` using `NotificationGroupManager.getInstance().getNotificationGroup("notification.group.lunar.tools")`.
     - The action link triggers `LuaRocksActionHandler` to run `luarocks install luacov` in a `Task.Backgroundable`.
     - Return `null` (abort the run).
  3. Cast `state` to `LuaTestCommandLineState` and inject coverage arguments:
     - Add `-lluacov` to the interpreter arguments (before the script/busted command).
     - Set environment variable `LUACOV_CONFIG` to point to a temp `.luacov` config file if needed.
  4. Determine the expected stats output file: `workingDirectory/luacov.stats.out`.
  5. Execute the process normally via `state.execute(executor, this)`.
  6. After process completes, create a `CoverageSuite` with the stats file path and register it with `CoverageDataManager.getInstance(project).addSuite(suite)`.
  7. Return the `RunContentDescriptor`.

### 3.10 Report Import Action (`LuaCovReportImportAction.actionPerformed()`)
- **Steps**:
  1. Show file chooser dialog filtered to `*.out` files.
  2. User selects a `luacov.report.out` file.
  3. Parse in background via `Task.Backgroundable`:
     - Call `LuaCovReportParser.parse(selectedFile)`.
     - Convert to `ProjectData` via `LuaCovReportParser.toProjectData(coverages)`.
     - Resolve relative file paths against `project.basePath`.
  4. Create a `BaseCoverageSuite` with the data.
  5. Call `CoverageDataManager.getInstance(project).chooseSuitesBundle(suite)` to display.

### 3.11 Report File Parsing (`LuaCovReportParser.parse()`)
- **Input**: `File` containing `luacov.report.out` content
- **Output**: `List<FileCoverage>`
- **Format** (real sample from `/home/mini/Documents/Kernel/v0/luacov.report.out`):
  ```
  ==============================================================================
  initrd/usr/bin/tests.lua
  ==============================================================================
       -- comment line (5 leading spaces = non-executable)
     1 local test_modules = {}
  ***0 local fs = require("runtime.fs")
    10 for line in p:lines() do
  ```
- **State machine**:
  ```
  State: SEARCH_HEADER → PARSE_PATH → EXPECT_BOUNDARY → PARSE_LINES
  ```
- **Steps**:
  1. Initialize: `state = SEARCH_HEADER`, `results = mutableListOf()`, `currentFile = null`, `currentHits = mutableMapOf()`, `lineNumber = 0`.
  2. Read file line by line.
  3. **SEARCH_HEADER**: Match line against regex `^={10,}$` (10 or more `=`). If match → transition to `PARSE_PATH`.
  4. **PARSE_PATH**: Read the next line. Trim whitespace. This is the file path. Store as `currentFile`. Transition to `EXPECT_BOUNDARY`.
  5. **EXPECT_BOUNDARY**: Match against `^={10,}$`. If match → `lineNumber = 0`, transition to `PARSE_LINES`. If not → error (malformed, skip to SEARCH_HEADER).
  6. **PARSE_LINES**: For each line:
     a. `lineNumber++`.
     b. If line matches `^={10,}$` → flush current file to results, transition to `PARSE_PATH`.
     c. If line matches `^\*\*\*0\s` → line is uncovered: `currentHits[lineNumber] = 0`.
     d. If line matches `^\s*(\d+)\s` → line is covered: `currentHits[lineNumber] = capturedDigits.toInt()`.
     e. If line matches `^\s{5}` → non-executable (no hit data recorded).
     f. Otherwise → treat as non-executable.
  7. At EOF: flush remaining file to results.
  8. Return `results`.

- **Regex patterns** (compiled once as companion vals):
  ```kotlin
  private val BOUNDARY = Regex("^={10,}$")
  private val UNCOVERED = Regex("^\\*\\*\\*0\\s")
  private val COVERED = Regex("^\\s*(\\d+)\\s")
  ```

### 3.12 FileCoverage to ProjectData Conversion
- **Input**: `List<FileCoverage>`
- **Output**: `ProjectData`
- **Steps**:
  1. Create `ProjectData()`.
  2. For each `FileCoverage(filePath, lineHits)`:
     a. `val classData = projectData.getOrCreateClassData(filePath)`.
     b. Find max line number: `val maxLine = lineHits.keys.maxOrNull() ?: 0`.
     c. Create `Array<LineData?>(maxLine + 1) { null }`.
     d. For each `(line, hits)` in `lineHits`:
        - `val lineData = LineData(line - 1, null)` (LineData uses 0-indexed).
        - `lineData.setHits(hits)`.
        - `lineDataArray[line] = lineData`.
     e. `classData.setLines(lineDataArray)`.
  3. Return `projectData`.

### 3.13 Stats File Parsing (`LuaCovStatsParser.parse()`)
- **Input**: `File` containing `luacov.stats.out` content
- **Format** (from real sample):
  ```
  53:initrd/usr/bin/tests.lua
  0 1 0 0 1 0 1 1 2 1 1 1 0 1 1 1 0 0 0 0 ...
  44:initrd/usr/share/lua/5.5/adt/orderedmap.lua
  0 0 0 0 0 0 0 0 1 0 1 6 6 0 6 7 0 1 ...
  ```
  - Header line: `<totalLines>:<filePath>`
  - Data lines: space-separated integers (hit counts per line, 1-indexed, may span multiple lines until totalLines values read)
- **Steps**:
  1. Read all lines from file.
  2. Initialize `results = mutableListOf()`, `i = 0`.
  3. While `i < lines.size`:
     a. Parse header: match `^(\d+):(.+)$`. Extract `totalLines` and `filePath`.
     b. `i++`.
     c. Collect hit values: split subsequent lines on whitespace, collect integers until `totalLines` values accumulated. Advance `i` as needed.
     d. Build `lineHits: Map<Int, Int>`: for each value at index `j` (0-based), if `value > 0` → `lineHits[j + 1] = value`; if `value == 0` → `lineHits[j + 1] = 0`.
     e. Filter out lines where hit count is 0 AND the line is likely non-executable (heuristic: keep `0` entries as potentially uncovered — the stats file doesn't distinguish executable from non-executable, so **all** lines with `0` are treated as uncovered for conservative reporting).
     f. Add `FileCoverage(filePath, lineHits)` to results.
  4. Return `results`.
  - **Note**: The stats file treats ALL lines as potentially executable (0 = not executed). This differs from the report file which only marks executable lines. For more accurate display, prefer parsing `luacov.report.out` when available.

### 3.14 Assertion Diff Parsing (RUN-08-10, Could priority)
- **Input**: Failure message string from test output
- **Output**: Expected/Actual pair for diff viewer (or null)
- **Steps**:
  1. Match against common assertion patterns:
     - Busted: `"Expected objects to be equal.\nPassed in:\n<actual>\nExpected:\n<expected>"`
     - Pattern: `Regex("Expected:?\s*\n?(.*?)\n\s*(?:Passed in|Got|Actual):?\s*\n?(.*)", DOTALL)`
  2. If match: return `Pair(expected, actual)`.
  3. If no match: return `null` (no diff shown, just the message).

## 4. External Data & Parsing

### 4.1 Busted JSON Output (`--output=json`)
- **Format**: Single JSON object dumped to stdout after all tests complete (see §3.2 for structure).
- **Parse strategy**: Buffer entire stdout; parse with Gson's `JsonParser.parseString()` once process exits.
- **Maps to**: SMTestRunner events via `GeneralTestEventsProcessor`
- **Failure handling**: If JSON parse throws `JsonSyntaxException`, log warning and dump raw output to console tab (fallback requirement).

### 4.2 Lunity JSON Lines
- **Format**: One JSON object per line on stdout (see §3.3 for structure).
- **Parse strategy**: Line-by-line `JsonParser.parseString()` as stdout is streamed.
- **Maps to**: SMTestRunner events via `GeneralTestEventsProcessor`
- **Failure handling**: Non-JSON lines are ignored (passed through to raw console). Malformed JSON on a line: log debug, skip.

### 4.3 `luacov.stats.out`
- **Format**: `<lineCount>:<filePath>` header + space-separated hit integers (see §3.13).
- **Parse strategy**: Line-by-line state machine (see §3.13).
- **Maps to**: `List<FileCoverage>` → `ProjectData`
- **Failure handling**: Malformed header line → skip to next header. Missing values → pad with 0.

### 4.4 `luacov.report.out`
- **Format**: `====...====` boundaries + file paths + prefixed code lines (see §3.11).
- **Parse strategy**: State machine with 4 states (see §3.11).
- **Maps to**: `List<FileCoverage>` → `ProjectData`
- **Failure handling**: Unexpected format → skip to next boundary. Empty file → return empty list.

## 5. Data Flow

### Example 1: User Runs Busted Tests

```
User clicks "Run" on "Lua Tests" config (Busted, file target)
  → LuaTestRunConfiguration.getState() → LuaTestCommandLineState
  → buildCommandLine():
      exePath = /usr/bin/busted
      args = [--output=json, /path/to/spec_file.lua]
      workDir = /project/root
  → startProcess() → KillableColoredProcessHandler
  → createConsole() → SMTestRunnerConnectionUtil.createAndAttachConsole()
      → LuaTestConsoleProperties (test locator = LuaTestLocator)
  → Process runs, stdout is buffered
  → Process exits, LuaTestOutputToEventsConverter.processServiceMessages() called
      → Parses JSON → emits testSuiteStarted/testStarted/testFinished/testFailed
  → Test Results window populates tree
  → User clicks test node → LuaTestLocator resolves "lua:///path/to/file.lua:15"
      → Editor opens at line 15
```

### Example 2: User Runs with Coverage

```
User clicks "Run with Coverage" on "Lua Tests" config
  → CoverageExecutor matches → LuaCoverageProgramRunner.canRun() = true
  → doExecute():
      1. Check luacov available via LuaToolManager
      2. Inject -lluacov into interpreter args
      3. Execute test process
      4. Process exits → luacov.stats.out created in workDir
      5. LuaCoverageRunner.loadCoverageData(statsFile)
          → LuaCovStatsParser.parse() → FileCoverage list
          → toProjectData() → ProjectData with ClassData/LineData
      6. CoverageDataManager.addSuite(suite)
      7. Editor gutters show green/red indicators
      8. Project tree shows coverage percentages via LuaCoverageAnnotator
```

### Example 3: User Imports Report File

```
User: Analyze → Import LuaCov Report...
  → LuaCovReportImportAction.actionPerformed()
  → FileChooser → user selects luacov.report.out
  → Task.Backgroundable:
      LuaCovReportParser.parse(file)
      → State machine: SEARCH_HEADER → PARSE_PATH → EXPECT_BOUNDARY → PARSE_LINES
      → Returns List<FileCoverage>
      → toProjectData() → ProjectData
      → Resolve paths relative to project root
  → CoverageDataManager.chooseSuitesBundle(suite)
  → Editor gutters refresh with coverage data
```

## 6. Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Busted not installed | `buildCommandLine()` throws `ExecutionException` with message: "Busted not found. Install via LuaRocks: `luarocks install busted`" |
| Busted produces no JSON (crash/segfault) | `LuaTestOutputToEventsConverter` catches `JsonSyntaxException`, raw stdout dumped to console tab |
| Lunity emits mixed JSON/text | Non-JSON lines skipped by line parser, passed to raw console |
| Empty test file (no tests) | Empty test tree shown, no errors |
| Test name contains special characters | Names passed through as-is; `--filter` pattern escaped via `Regex.escape()` |
| `luacov.stats.out` with multi-line hit data | Parser collects values across line boundaries until `totalLines` reached |
| Report file with relative paths | Resolved against `project.basePath`; if resolution fails, path used as-is |
| Report file with paths not in project | `ClassData` created with full path; no gutter highlighting (file not open) |
| Coverage run without `luacov` | Balloon notification shown, run aborted; user can click "Install via LuaRocks" |
| `luarocks install` fails | Error notification balloon with stderr message |
| Process cancelled by user | `KillableProcessHandler` sends SIGTERM/SIGKILL; test tree shows "Terminated" |
| Very large test suites (>10k tests) | Stream processing (`O(1)` memory for Lunity); Busted JSON buffered (may use significant memory for extreme cases) |

## 7. Integration Points

```xml
<!-- plugin.xml additions -->
<extensions defaultExtensionNs="com.intellij">
    <!-- Test Run Configuration -->
    <configurationType
            implementation="net.internetisalie.lunar.run.test.LuaTestRunConfigurationType"/>
    <runConfigurationProducer
            implementation="net.internetisalie.lunar.run.test.LuaTestRunConfigurationProducer"/>
    <runLineMarkerContributor
            language="Lua"
            implementationClass="net.internetisalie.lunar.run.test.LuaTestRunLineMarkerProvider"/>

    <!-- Coverage -->
    <coverageEngine
            implementation="net.internetisalie.lunar.coverage.LuaCoverageEngine"/>
    <coverageRunner
            implementation="net.internetisalie.lunar.coverage.LuaCoverageRunner"/>
    <programRunner
            implementation="net.internetisalie.lunar.coverage.LuaCoverageProgramRunner"/>
    <projectService
            serviceImplementation="net.internetisalie.lunar.coverage.LuaCoverageAnnotator"/>

    <!-- Coverage Report Viewer -->
    <fileType
            name="LuaCov Report"
            implementationClass="net.internetisalie.lunar.coverage.report.LuaCovReportFileType"
            fieldName="INSTANCE"
            fileNames="luacov.report.out"/>
    <lang.syntaxHighlighterFactory
            language="LuaCovReport"
            implementationClass="net.internetisalie.lunar.coverage.report.LuaCovReportSyntaxHighlighterFactory"/>
    <editorHighlighterProvider
            filetype="LuaCov Report"
            implementationClass="net.internetisalie.lunar.coverage.report.LuaCovReportEditorHighlighterProvider"/>
    <editorNotificationProvider
            implementation="net.internetisalie.lunar.coverage.report.LuaCovReportNotificationProvider"/>
</extensions>

<actions>
    <action id="Lunar.ImportLuaCovReport"
            class="net.internetisalie.lunar.coverage.LuaCovReportImportAction"
            text="Import LuaCov Report..."
            description="Load coverage data from a luacov.report.out file">
        <add-to-group group-id="AnalyzePlatformMenu" anchor="last"/>
    </action>
</actions>
```

**Menu-group binding contract (BINDING — do not substitute `AnalyzeMenu`).**
The action MUST be registered against the platform group **`AnalyzePlatformMenu`**, NOT
`AnalyzeMenu`. Rationale and grounding:

- `AnalyzeMenu` is defined **only** in the Java plugin's resources
  (`intellij-community/java/java-backend/resources/META-INF/JavaActions.xml:90`,
  `<group id="AnalyzeMenu" popup="true">`). It is absent from GoLand (the default test/target
  IDE, which ships no Java plugin) and from the headless platform test fixtures. An
  `<add-to-group group-id="AnalyzeMenu">` therefore fails to resolve the group at plugin
  load, raising `PluginException` / a "group not found" error. This is the root cause of the
  RUN-08 ABORT_REPLAN (see `risks-and-gaps.md` Risk 1.5).
- `AnalyzePlatformMenu` is defined in **platform-resources**
  (`intellij-community/platform/platform-resources/src/idea/LangActions.xml:380`,
  `<group id="AnalyzePlatformMenu">`). Because it ships with the platform, the group id
  resolves in **every** IntelliJ-based IDE (GoLand, IDEA, PyCharm, CLion, …) and in headless
  test fixtures. It is the platform-level analog of `AnalyzeMenu` — host IDEs surface it under
  their "Analyze" menu, so the action still lands under **Analyze ▸ Import LuaCov Report…** in
  GoLand. `anchor="last"` is retained (no `relative-to-action` needed; the group's only
  built-in member is `Unscramble`).

**Threading (unchanged):** `LuaCovReportImportAction.actionPerformed` runs on the EDT; per
design §2.17 / §3.10 it MUST off-load file parsing to a `Task.Backgroundable` (no I/O on the
EDT). `update()` only toggles `isEnabledAndVisible` from `e.project` (cheap, EDT-safe).

**Fallback (documented, NOT part of the MUST path).** `AnalyzePlatformMenu`'s *visibility* in
GoLand's main menu is provided by the host IDE surfacing the group; this is tracked for live
verification by de-risking task **DR-04** (`risks-and-gaps.md`). If DR-04 shows the action is
not reachable from a visible menu in GoLand, the documented fallback is to ALSO register the
action on `ToolsMenu` (a guaranteed-present, always-visible platform group already used by this
plugin — see `plugin.xml` `Lua.Console` → `<add-to-group group-id="ToolsMenu" anchor="last"/>`):

```xml
<!-- fallback only — add ALONGSIDE the AnalyzePlatformMenu registration, do not replace it -->
<add-to-group group-id="ToolsMenu" anchor="last"/>
```

Do not add the `ToolsMenu` registration pre-emptively; keep the MUST path single-group until
DR-04 establishes whether it is needed.

**Tool registration** (code changes, not plugin.xml):

| File | Change |
|------|--------|
| `tool/LuaToolDescriptor.kt:L19-23` | Add `LUACOV`, `BUSTED` to `LuaToolType` enum |
| `tool/LuaToolDescriptor.kt:L44-48` | Add `LuaToolDescriptor(LuaToolType.LUACOV, "luacov")`, `LuaToolDescriptor(LuaToolType.BUSTED, "busted")` to `DESCRIPTORS` |
| `tool/LuaToolManager.kt:L204-214` | Add `"luacov" -> LuaToolType.LUACOV`, `"busted" -> LuaToolType.BUSTED` to `inferType()` |
| `tool/LuaToolManager.kt:L217-221` | Add `LUACOV -> "LuaCov"`, `BUSTED -> "Busted"` to `displayNameFor()` |
| `tool/LuaToolValidator.kt:L154-158` | Add version patterns for luacov and busted |
| `tool/LuaToolValidator.kt:L160-164` | Add `--version` flag for busted; `--help` for luacov (no `--version` flag) |

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| RUN-08-01 | M | §2.1–§2.4, §2.12, §7 (configurationType) |
| RUN-08-02 | M | §2.5, §3.1 |
| RUN-08-03 | M | §2.6, §2.7 |
| RUN-08-04 | M | §2.7, §3.3 |
| RUN-08-05 | M | §2.7, §3.2 |
| RUN-08-06 | M | §2.6, §2.7 (SMTestRunner tree is automatic) |
| RUN-08-07 | S | §2.8, §3.4 |
| RUN-08-08 | S | §2.9, §2.10, §3.5, §3.6, §7 (runConfigurationProducer, runLineMarkerContributor) |
| RUN-08-09 | S | §2.11, §3.7 |
| RUN-08-10 | C | §3.14 |
| RUN-08-11 | S | §2.16, §3.9 |
| RUN-08-12 | S | §2.14, §2.18, §2.19, §3.8, §3.11, §3.13 |
| RUN-08-13 | S | §2.13, §2.15, §7 (coverageEngine, coverageRunner, projectService) |
| RUN-08-14 | S | §7 tool registration table |
| RUN-08-15 | S | §2.16 step 2 (balloon + LuaRocks install) |
| RUN-08-16 | S | §2.17, §2.18, §3.10, §3.11 |
| RUN-08-17 | C | §2.20–§2.25, §7 (fileType, syntaxHighlighterFactory, editorHighlighterProvider) |
| RUN-08-18 | C | §2.26, §7 (editorNotificationProvider) |

## 9. Alternatives Considered

### Test Run Configuration: Separate type vs. extending LuaRunConfiguration
- **Option A (chosen)**: New `LuaTestRunConfigurationType` with its own factory/options. This gives a clean separation of concerns — test-specific fields don't pollute the general run config, and the IDE shows "Lua Tests" as a distinct config type.
- **Option B**: Add a "mode" field to `LuaRunConfiguration` (run script vs. run tests). Rejected because it couples two distinct concerns and makes the editor UI more complex.

### Coverage parsing: stats vs. report file
- **Option A (chosen)**: Support both. Stats file is primary (generated automatically by `luacov`). Report file is secondary (for manual import). Report file is more accurate (distinguishes executable from non-executable lines).
- **Option B**: Only support report file. Rejected because it requires running `luacov` CLI post-hoc, adding complexity.

### Report syntax highlighting: Layered lexer vs. custom composite
- **Option A (chosen)**: `LayeredLexerEditorHighlighter` with `LuaSyntaxHighlighter` for `LUA_CODE` tokens. This is the exact pattern used by `LuaEditorHighlighter` for embedded LuaCATS highlighting — proven in this codebase.
- **Option B**: Hand-color Lua tokens in the report highlighter. Rejected because it duplicates highlighting logic and misses future token additions.

### Busted output: JSON vs. TeamCity service messages
- **Option A (chosen)**: Use `--output=json` (Busted's built-in JSON reporter). Simpler parsing, no custom reporter needed.
- **Option B**: Bundle a custom Lua reporter that emits TeamCity service messages. More complex to maintain, requires distributing Lua resources.

## 10. Open Questions

_None — feature has cleared the planning bar._
