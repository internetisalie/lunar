# Terminal PATH Injection Design (`TOOL-02`)

## Overview
This document outlines the technical implementation for injecting Lua tool directories into the PATH of IntelliJ's built-in terminal, ensuring consistent tool availability across different shells and operating systems.

## Architecture

### 1. Extension Points

#### `TerminalCustomizer` Implementation
- Custom implementation of `com.intellij.terminal.TerminalCustomizer`
- Responsible for modifying the environment variables of terminal sessions
- Specifically targets PATH variable to prepend Lua tool directories

#### `LocalTerminalDirectRunner` Extension
- Extension of `com.intellij.terminal.LocalTerminalDirectRunner`
- Handles direct execution of terminal processes with customized environment
- Works in conjunction with TerminalCustomizer for complete PATH injection

### 2. Environment Management

#### PATH Construction Logic
1. Retrieve effective Lua tools via `LuaToolManager.getEffectiveTool()`
2. Extract directory paths from valid tool binaries
3. Deduplicate paths while preserving order (project overrides take precedence)
4. Construct PATH string: `[tool_dirs]:[original_path]`

#### Shell-Specific Handling
- **Windows CMD**: Modify PATH using `set PATH=new_path;%PATH%`
- **Windows PowerShell**: Modify PATH using `$env:PATH = "new_path;$env:PATH"`
- **POSIX Shells (Bash/Zsh)**: Modify PATH using `export PATH="new_path:$PATH"`

### 3. Services

#### `LuaTerminalEnvironmentService`
- Calculates the appropriate PATH modification for current project
- Handles caching of tool directory paths
- Provides shell-specific PATH modification strings
- Thread-safe for concurrent terminal sessions

## Implementation Details

### TerminalCustomizer Implementation
```kotlin
class LuaTerminalCustomizer : TerminalCustomizer {
    override fun customizeTerminal(
        terminalType: TerminalType,
        environment: Map<String, String>,
        initCommands: MutableList<String>
    ) {
        val project = getCurrentProject()
        if (project == null) return
        
        val luaToolsDirs = LuaTerminalEnvironmentService.getToolDirectories(project)
        if (luaToolsDirs.isEmpty()) return
        
        val pathModification = when (terminalType) {
            TerminalType.COMMAND -> buildCmdPathModification(luaToolsDirs, environment["PATH"])
            TerminalType.POWERSHELL -> buildPowerShellPathModification(luaToolsDirs, environment["PATH"])
            else -> buildPosixPathModification(luaToolsDirs, environment["PATH"])
        }
        
        environment["PATH"] = pathModification
        
        // Add init command to verify injection (optional, for debugging)
        initCommands.add("echo Lua tools PATH injected: $pathModification")
    }
    
    private fun buildCmdPathModification(toolDirs: List<String>, originalPath: String?): String {
        val toolsPath = toolDirs.joinToString(";")
        return if (originalPath.isNullOrBlank()) toolsPath else "$toolsPath;$originalPath"
    }
    
    private fun buildPowerShellPathModification(toolDirs: List<String>, originalPath: String?): String {
        val toolsPath = toolDirs.joinToString(";")
        return if (originalPath.isNullOrBlank()) toolsPath else "$toolsPath;$originalPath"
    }
    
    private fun buildPosixPathModification(toolDirs: List<String>, originalPath: String?): String {
        val toolsPath = toolDirs.joinToString(":")
        return if (originalPath.isNullOrBlank()) toolsPath else "$toolsPath:$originalPath"
    }
}
```

### LocalTerminalDirectRunner Extension
```kotlin
@ExtensionPointId("com.intellij.terminal.localTerminalDirectRunner")
class LuaTerminalDirectRunnerExtension : LocalTerminalDirectRunner.EpExtension {
    override fun createRunner(
        project: Project,
        session: LocalTerminalSession,
        originalRunner: LocalTerminalDirectRunner
    ): LocalTerminalDirectRunner {
        return object : LocalTerminalDirectRunner(project, session) {
            override fun createProcessExecutor(): ProcessExecutor {
                val originalExecutor = super.createProcessExecutor()
                return object : ProcessExecutor by originalExecutor {
                    override fun executeProcess(
                        commandLine: GeneralCommandLine,
                        processHandler: ProcessHandler
                    ): OSProcess {
                        // Ensure tool directories are in PATH for direct execution
                        val project = session.project
                        if (project != null) {
                            val env = commandLine.environment
                            val originalPath = env.get("PATH")
                            val luaToolsDirs = LuaTerminalEnvironmentService.getToolDirectories(project)
                            
                            if (luaToolsDirs.isNotEmpty()) {
                                val toolsPath = luaToolsDirs.joinToString(File.pathSeparator)
                                val newPath = if (originalPath.isNullOrBlank()) {
                                    toolsPath
                                } else {
                                    "$toolsPath${File.pathSeparator}$originalPath"
                                }
                                env.put("PATH", newPath)
                            }
                        }
                        return originalExecutor.executeProcess(commandLine, processHandler)
                    }
                }
            }
        }
    }
}
```

### LuaTerminalEnvironmentService
```kotlin
@Service
class LuaTerminalEnvironmentService @Autowired constructor(
    private val luaToolManager: LuaToolManager
) {
    @Volatile private var cachedToolDirectories: Map<Project, List<String>> = mapOf()
    @Volatile private var lastCacheUpdate: Map<Project, Long> = mapOf()
    
    fun getToolDirectories(project: Project): List<String> {
        val now = System.currentTimeMillis()
        val lastUpdate = lastCacheUpdate[project] ?: 0L
        
        // Refresh cache every 30 seconds or if not cached
        if (now - lastUpdate > 30_000 || !cachedToolDirectories.containsKey(project)) {
            val directories = luaToolManager.getAllValidTools(project)
                .mapNotNull { it.path }
                .map { File(it).parent }
                .distinct()
                .sortedBy { it } // Consistent ordering
            
            cachedToolDirectories[project] = directories
            lastCacheUpdate[project] = now
            return directories
        }
        
        return cachedToolDirectories[project] ?: emptyList()
    }
    
    fun invalidateCache(project: Project) {
        cachedToolDirectories.remove(project)
        lastCacheUpdate.remove(project)
    }
}
```

## Registration in plugin.xml
```xml
<!-- Terminal Customizer -->
<extension points="com.intellij.terminal.TerminalCustomizer"
          class="net.internetisalie.lunar.tool.terminal.LuaTerminalCustomizer"/>

<!-- Terminal Direct Runner Extension -->
<extension points="com.intellij.terminal.localTerminalDirectRunner"
          class="net.internetisalie.lunar.tool.terminal.LuaTerminalDirectRunnerExtension"/>
```

## Validation and Compatibility

### Shell Compatibility Matrix
| Shell | Windows | Linux | macOS | Notes |
|-------|---------|-------|-------|-------|
| CMD | ✅ Supported | ❌ N/A | ❌ N/A | Default Windows command shell |
| PowerShell | ✅ Supported | ❌ N/A | ❌ N/A | Windows PowerShell 5.1+ |
| Bash | ✅ Supported (WSL) | ✅ Supported | ✅ Supported | Bash 3.2+ |
| Zsh | ✅ Supported (WSL) | ✅ Supported | ✅ Supported | Zsh 5.0+ |
| Fish | ⚠️ Limited | ✅ Supported | ✅ Supported | Requires custom implementation |

### PATH Injection Verification
- Validate that Lua tool directories appear at the beginning of PATH
- Ensure no duplicate entries are introduced
- Verify original PATH is preserved after tool directories
- Check that modifications persist throughout terminal session

## Testing Strategy

### Unit Tests
- Test `LuaTerminalEnvironmentService` path construction logic
- Verify shell-specific PATH modification formulas
- Test path deduplication and ordering preservation
- Validate cache invalidation timing

### Integration Tests
- Spawn terminal sessions in test IDE instance
- Verify PATH contains Lua tool directories after initialization
- Test across different shell types (CMD, PowerShell, Bash, Zsh)
- Validate that tool execution works from terminal (e.g., `luarocks --version`)
- Test PATH persistence across multiple commands in same session

### Cross-Platform E2E Tests (using Docker containers)
- **Windows Container**: Test CMD and PowerShell PATH injection
- **Linux Container**: Test Bash and Zsh PATH injection
- Verify tool discovery and execution in both environments
- Test scenario switching between different shell types
- Validate that IDE restart preserves terminal PATH modifications

## Security Considerations
- Validate tool directory paths to prevent directory traversal attacks
- Ensure only verified, registered tools contribute to PATH modification
- Limit PATH length to prevent environment variable overflow issues
- Sanitize tool paths before injection to prevent injection attacks

## Performance Considerations
- Cache tool directory paths with 30-second TTL to minimize computation
- Invalidate cache only when tool registration changes occur
- Use efficient string builders for PATH construction
- Avoid blocking EDT during terminal customization

## Logging and Diagnostics
- Log PATH modification decisions at DEBUG level
- Warn if no Lua tools are found for injection
- Error if PATH modification fails due to invalid paths
- Provide diagnostic action to view current terminal environment