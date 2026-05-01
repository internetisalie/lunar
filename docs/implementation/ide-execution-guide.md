# Implementing Full IDE Lua Program Execution in Integration Tests

## Overview

The integration test infrastructure is now fully set up with proper IDE launcher and test runner support. The current test suite includes:

- **ProjectOpenIntegrationTest** (1 test): Validates project opening and structure
- **LuaProgramExecutionIntegrationTest** (10 tests): Infrastructure tests for Lua program scenarios
- **LuaProgramExecutionWithIDEIntegrationTest** (10 new tests): Framework-ready for full IDE execution

## Current Architecture

### Test Execution Flow

```
gradle integrationTest
  └─> buildPlugin
      └─> plugin distribution created in build/distributions/lunar-*.zip
  └─> prepareSandbox  
      └─> IDE sandbox prepared at build/idea-sandbox/
  └─> compileIntegrationTests
      └─> Kotlin tests compiled
  └─> runIdeWithDriver equivalent (via IDE Starter framework)
      └─> IDE instance launched with plugin
      └─> Tests executed
      └─> Output captured
```

### IDE Starter Framework

The IDE Starter framework (version 253.29346.240) provides:

- **IDE Launcher**: Starts a real IDE instance in headless mode
- **Plugin Management**: Automatically installs the Lunar plugin from build artifacts
- **Test Environment**: Provides access to IDE objects and APIs
- **Sandbox Isolation**: Each test gets isolated IDE instance
- **Driver Integration**: UI automation capabilities (ready for future use)

## How to Implement Actual Execution

### Phase 2: Running Lua Programs Inside IDE

To implement actual Lua program execution with output capture, follow these steps:

#### 1. Add IDE Launcher Context

```kotlin
import com.intellij.tools.ide.starter.bus.LocalEventBus
import com.intellij.tools.ide.starter.bus.starter.StarterContext

class LuaProgramExecutionWithIDEIntegrationTest {
    
    @Test
    fun `execute simple print program through IDE`() {
        // Create a starter context that will launch the IDE
        val context: StarterContext = withContext()
        
        context.runIdeWithDriver().useDriverAndCloseIde {
            // Inside this block, IDE is running with Lunar plugin installed
            val project = it.project
            // ... execution code here
        }
    }
}
```

#### 2. Create Lua File in Project

```kotlin
// Get project base directory
val projectDir = project.baseDir

// Create Lua file
val luaFile = projectDir.createChild("main.lua")
luaFile.setBinaryContent("""
    print("Hello from Lunar!")
    print("IDE Integration Test")
""".trimIndent().toByteArray())

// Verify file was created
require(luaFile.exists())
```

#### 3. Create LuaRunConfiguration

```kotlin
import net.internetisalie.lunar.run.LuaRunConfiguration
import net.internetisalie.lunar.run.LuaRunConfigurationFactory
import net.internetisalie.lunar.run.LuaRunConfigurationType
import com.intellij.execution.RunManager

// Get run manager
val runManager = RunManager.getInstance(project)

// Create configuration factory
val configType = LuaRunConfigurationType()
val configFactory = LuaRunConfigurationFactory(configType)

// Create template configuration
val runConfig = configFactory.createTemplateConfiguration(project) as LuaRunConfiguration

// Configure it
runConfig.name = "Test Program"
runConfig.scriptName = luaFile.path  // or relative path "main.lua"
runConfig.workingDirectory = projectDir.path  // optional
runConfig.environmentVariables = mutableMapOf()  // optional

// Optional: add interpreter arguments
// runConfig.interpreterArguments = "-O2"

// Optional: add program arguments
// runConfig.programArguments = "arg1 arg2 arg3"

// Save configuration to run manager
runManager.addConfiguration(
    RunConfigurationFactory.createSettings(runConfig)
)
```

#### 4. Execute Through IDE

```kotlin
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.impl.DefaultExecutionTarget

// Get executor
val executor = DefaultRunExecutor.getRunExecutorInstance()

// Build execution environment
val environment = ExecutionEnvironmentBuilder(project, executor)
    .runConfiguration(runConfig)
    .target(DefaultExecutionTarget.INSTANCE)
    .build()

// Execute
val runner = environment.runner ?: throw Exception("No runner found")
val executionResult = runner.execute(environment)

// Get process handler
val processHandler = executionResult?.processHandler
    ?: throw Exception("Failed to start process")
```

#### 5. Capture Program Output

```kotlin
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Set up output capturing
val output = StringBuilder()
val latch = CountDownLatch(1)

processHandler.addProcessListener(object : ProcessListener {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        output.append(event.text)
    }
    
    override fun processTerminated(event: ProcessEvent) {
        latch.countDown()
    }
    
    override fun startNotified(event: ProcessEvent) {}
    override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {}
})

// Wait for process to finish (with timeout)
val completed = latch.await(30, TimeUnit.SECONDS)
require(completed) { "Process did not complete in 30 seconds" }

// Verify exit code
require(processHandler.exitCode == 0) {
    "Process exited with code ${processHandler.exitCode}: ${output}"
}

println("Program output:")
println(output.toString())
```

#### 6. Verify Results

```kotlin
val actualOutput = output.toString().trim()

// Verify output contains expected text
require(actualOutput.contains("Hello from Lunar!")) {
    "Output missing expected text. Got: $actualOutput"
}

// Or parse output for specific values
val lines = actualOutput.split("\n")
val firstLine = lines[0]
require(firstLine == "Hello from Lunar!") {
    "First line unexpected. Expected: 'Hello from Lunar!' Got: '$firstLine'"
}

println("✓ Test passed: Output verified")
```

## Complete Implementation Example

Here's a complete test method implementing all phases:

```kotlin
@Test
fun `execute simple print program through IDE - full implementation`() {
    val context: StarterContext = withContext()
    
    context.runIdeWithDriver().useDriverAndCloseIde { driver ->
        val project = driver.project
        
        // Phase 1: Create project structure
        val projectDir = project.baseDir
        val luaFile = projectDir.createChild("main.lua")
        luaFile.setBinaryContent("""
            print("Hello from Lunar!")
            local x = 42
            print("Answer: " .. x)
        """.trimIndent().toByteArray())
        
        // Phase 2: Create run configuration
        val runManager = RunManager.getInstance(project)
        val configType = LuaRunConfigurationType()
        val configFactory = LuaRunConfigurationFactory(configType)
        val runConfig = configFactory.createTemplateConfiguration(project) as LuaRunConfiguration
        
        runConfig.name = "Test"
        runConfig.scriptName = "main.lua"
        runConfig.workingDirectory = projectDir.path
        
        // Phase 3: Execute
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = ExecutionEnvironmentBuilder(project, executor)
            .runConfiguration(runConfig)
            .build()
        
        val processHandler = environment.runner
            ?.execute(environment)
            ?.processHandler
            ?: throw Exception("Failed to start process")
        
        // Phase 4: Capture output
        val output = StringBuilder()
        val latch = CountDownLatch(1)
        
        processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                output.append(event.text)
            }
            
            override fun processTerminated(event: ProcessEvent) {
                latch.countDown()
            }
            
            override fun startNotified(event: ProcessEvent) {}
            override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {}
        })
        
        latch.await(30, TimeUnit.SECONDS)
        
        // Phase 5: Verify
        val result = output.toString()
        require(result.contains("Hello from Lunar!")) { "Missing expected output" }
        require(result.contains("Answer: 42")) { "Missing calculated value" }
        require(processHandler.exitCode == 0) { "Process failed with exit code ${processHandler.exitCode}" }
        
        println("✓ Test passed")
    }
}
```

## Key Imports Required

```kotlin
// IDE Starter/Driver
import com.intellij.tools.ide.starter.bus.LocalEventBus
import com.intellij.tools.ide.starter.bus.starter.StarterContext
import com.intellij.tools.ide.starter.bus.starter.withContext

// Execution
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.impl.DefaultExecutionTarget

// Process handling
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.util.io.systemIndependentPath

// Lunar plugin
import net.internetisalie.lunar.run.LuaRunConfiguration
import net.internetisalie.lunar.run.LuaRunConfigurationFactory
import net.internetisalie.lunar.run.LuaRunConfigurationType

// Standard library
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
```

## Debugging Output Capture Issues

### If No Output is Captured

1. **Check Process Started**: Log process creation
   ```kotlin
   println("Process created: ${processHandler.commandLine}")
   ```

2. **Verify Lua Installation**: Ensure Lua interpreter is available
   - Check IDE settings → Languages & Frameworks → Lua
   - Path should point to valid lua/luau/mlua executable

3. **Check Working Directory**: Verify it's set correctly
   ```kotlin
   runConfig.workingDirectory = projectDir.path
   ```

4. **Enable Process Output**: Ensure ProcessListener is attached
   ```kotlin
   processHandler.addProcessListener(...)  // Must be before process starts
   ```

5. **Check Exit Code**: Process might have failed
   ```kotlin
   println("Exit code: ${processHandler.exitCode}")
   ```

### If Output is Incomplete

1. **Increase Timeout**: Process might need more time
   ```kotlin
   val completed = latch.await(60, TimeUnit.SECONDS)  // 60 seconds instead of 30
   ```

2. **Check for Buffering**: Output might be buffered
   ```kotlin
   processHandler.destroyProcess()  // Force flush
   ```

3. **Use System.out Redirection**: Capture all output
   ```kotlin
   val output = String(processHandler.getProcessOutput().readBytes())
   ```

## Next Steps

1. **Convert Placeholder Tests**: Update test methods to use full implementation
2. **Add Output Parsing**: Create utilities to parse Lua program output
3. **Add Verification Helpers**: Create assertion methods for common checks
4. **Test Error Cases**: Verify error output capture and handling
5. **Test Debugging**: Verify breakpoint handling and variable inspection
6. **Performance Testing**: Measure startup time and resource usage
7. **Parallel Execution**: Enable parallel test execution if performance allows

## Testing Checklist

- [ ] Simple print statements execute and output is captured
- [ ] Arithmetic operations compute correctly
- [ ] String concatenation works
- [ ] Table creation and access works
- [ ] Function definitions and calls work
- [ ] Control flow (if/else) executes correctly
- [ ] Loops (for/while) execute correctly
- [ ] Error handling (pcall) works
- [ ] Module loading (require) works
- [ ] Program arguments are passed correctly
- [ ] Environment variables are set correctly
- [ ] Working directory is respected
- [ ] Exit codes are correct
- [ ] Lua version compatibility (5.1, 5.2, 5.3, 5.4)
- [ ] Error messages are captured on stderr

## Configuration and Dependency Details

### Required Dependencies (Already in build.gradle.kts)

```gradle
// IDE Starter framework
testImplementation("com.jetbrains.intellij.plugins:ide-starter:253.29346.240")

// Process handling
testImplementation("com.jetbrains.intellij.tools:junit5:$intellijPlatformVersion")

// Coroutines for async operations
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinVersion")
```

### System Properties Available During Tests

```kotlin
// Plugin path (automatically set by build)
System.getProperty("path.to.build.plugin")  // e.g., "/path/to/lunar-2024.1.zip"

// IDE home (set by Starter framework)
System.getProperty("idea.home.path")

// Test project directories
System.getProperty("plugin.testing.base.path")  // e.g., "build/test-projects/"
```

## Troubleshooting

### IDE Won't Start
- Check IDE version compatibility in gradle.properties
- Verify plugin builds successfully: `./gradlew build`
- Check sandbox isn't corrupted: `rm -rf build/idea-sandbox/`

### Lua Interpreter Not Found
- Ensure lua/lua5.4/mlua/luau installed on system
- Configure in IDE settings or test setup
- Check PATH environment variable

### Tests Timeout
- Increase latch.await() timeout
- Check if IDE is still loading
- Monitor build/idea-sandbox/GO-*/log/idea.log

### Output Not Captured
- Verify ProcessListener attached before execution
- Check outputType in onTextAvailable callback
- Use RedirectingInputStream wrapper if needed

## References

- IDE Starter Docs: https://plugins.jetbrains.com/docs/intellij/ide-starter.html
- Execution API: https://plugins.jetbrains.com/docs/intellij/run-configurations.html
- Process Handling: https://plugins.jetbrains.com/docs/intellij/basic-run-configuration.html
- Lua 5.4 Manual: https://www.lua.org/manual/5.4/
