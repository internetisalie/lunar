---
id: "TOOL-02-TERMINAL"
title: "Terminal PATH Injection Design"
type: "design"
parent_id: "TOOL-02"
status: "planned"
priority: "high"
folders:
  - "[[features/tool/02-project-binding/requirements|requirements]]"
---

# Terminal PATH Injection Design (`TOOL-02`)

> **⚠ Grounding correction (2026-06-16) — read before implementing.** The original draft of this
> document was built on symbols that do not exist in the 2026.1 platform: `com.intellij.terminal.TerminalCustomizer`,
> `customizeTerminal(terminalType, environment, initCommands)`, the `TerminalType` enum, the
> `LocalTerminalDirectRunner.EpExtension`/`ProcessExecutor`/`localTerminalDirectRunner` EP, and an
> invented `LUA_SETTINGS_TOPIC`/`LuaSettingsListener`. The sections below have been corrected to the
> real API: `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer` (preferred) /
> `org.jetbrains.plugins.terminal.LocalTerminalCustomizer` (deprecated fallback), with PATH injected
> via the environment map (no shell `export`/`set` init commands), and the existing
> `LuaSettingsChangedListener.TOPIC` for cache invalidation. The resolution logic, ordering, and
> caching intent are unchanged.
> See [planning-gaps.md](../../../planning-gaps.md#wave-10-grounding-audit-2026-06-16).

## Overview
This document outlines the technical implementation for injecting Lua tool directories into the PATH of IntelliJ's built-in terminal, ensuring consistent tool availability across different shells and operating systems.

## Architecture

### 1. Extension Points

#### `ShellExecOptionsCustomizer` Implementation (preferred)
- Custom implementation of `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer`
  (EP `org.jetbrains.plugins.terminal.shellExecOptionsCustomizer`; `@ApiStatus.Experimental`).
- Method `customizeExecOptions(project: Project, options: MutableShellExecOptions)` runs on a
  background thread (no EDT blocking).
- Responsible for modifying the environment variables of terminal sessions, specifically prepending
  Lua tool directories to PATH via `shellExecOptions.prependEntryToPATH(path)`.
- **SOURCE-VERIFIED (2026.1):** the interface method is
  `customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions)`.
  `MutableShellExecOptions.envs` is a **read-only `Map<String, String>`** — there is **no
  `options.environment` property and no map setter**. Mutate PATH only through `prependEntryToPATH(Path)`
  / `appendEntryToPATH(Path)` / `setEnvironmentVariable(name, value?)`. `prependEntryToPATH` already
  joins with the *remote* path separator and translates local→remote paths (WSL/Docker/SSH), so the
  customizer must **not** join with `File.pathSeparator` itself.

#### `LocalTerminalCustomizer` (deprecated fallback)
- `org.jetbrains.plugins.terminal.LocalTerminalCustomizer` (EP
  `org.jetbrains.plugins.terminal.localTerminalCustomizer`).
- **SOURCE-VERIFIED (2026.1):** the method actually used is
  `customizeCommandAndEnvironment(project, workingDirectory, shellCommand: List<String>, envs: MutableMap<String, String>, eelDescriptor: EelDescriptor): List<String>`
  — inject PATH by mutating the `envs` map (separator `eelDescriptor.osFamily.pathSeparator`) and
  returning `shellCommand` unchanged. (The older `Array<String>` overload exists but is itself
  deprecated and skips remote-path translation.)
- Use only if `ShellExecOptionsCustomizer` is unavailable in the target IDE build.

### 2. Environment Management

#### PATH Construction Logic
1. Retrieve effective Lua tools via `LuaToolManager.getEffectiveTool()`
2. Extract directory paths from valid tool binaries
3. Deduplicate paths while preserving order (project overrides take precedence)
4. Prepend to the existing PATH: `[tool_dirs]<sep>[original_path]`, joined with `File.pathSeparator`

#### Injection Mechanism
- PATH is injected **only via the environment map** of the exec options (or the `envs` map in the
  deprecated customizer) — there are **no** shell `export`/`set` init commands and no `initCommands`
  list. Mutating the env map applies to the spawned shell process directly, so there is no profile to
  clobber and no per-shell syntax to emit.
- Use `File.pathSeparator` (`:` on POSIX, `;` on Windows) when joining tool directories — the OS,
  not the shell flavor, determines the separator.

### 3. Services

#### `LuaTerminalEnvironmentService` (Project-level Service)
- **Project-level Service** (`@Service(Service.Level.PROJECT)`): scoped to the project to prevent
  memory leaks. This is the single, canonical service definition.
- Calculates the effective Lua tool directories for the current project.
- Subscribes to `LuaSettingsChangedListener.TOPIC` (`onSettingsChanged()`) on the project Message Bus
  for instant cache invalidation.

## Implementation Details

### ShellExecOptionsCustomizer Implementation (preferred)
```kotlin
class LuaShellExecOptionsCustomizer : ShellExecOptionsCustomizer {
    // Runs on a BACKGROUND thread with NO read action; safe to do tool resolution here.
    override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
        val toolDirs: List<Path> = project.service<LuaTerminalEnvironmentService>().getToolDirectories()
        // Prepend each tool dir; prependEntryToPATH inserts first, uses the REMOTE path separator,
        // and translates local->remote paths. envs is read-only — never assign to it directly.
        // Prepend in reverse so the first list entry ends up first on PATH.
        for (dir in toolDirs.asReversed()) {
            shellExecOptions.prependEntryToPATH(dir)
        }
    }
}
```

### LocalTerminalCustomizer (deprecated fallback)
```kotlin
// Use only if ShellExecOptionsCustomizer is unavailable in the target IDE build.
// Override the EEL-aware overload (List<String> + EelDescriptor), which is the one the
// platform actually dispatches to and which performs local->remote path translation.
class LuaLocalTerminalCustomizer : LocalTerminalCustomizer() {
    override fun customizeCommandAndEnvironment(
        project: Project,
        workingDirectory: String?,
        shellCommand: List<String>,
        envs: MutableMap<String, String>,
        eelDescriptor: EelDescriptor,
    ): List<String> {
        val toolDirs = project.service<LuaTerminalEnvironmentService>().getToolDirectories()
        if (toolDirs.isNotEmpty()) {
            val sep = eelDescriptor.osFamily.pathSeparator
            val originalPath = envs["PATH"]
            // toolDirs are already remote-environment strings here; join with the remote separator.
            val toolsPath = toolDirs.joinToString(sep)
            envs["PATH"] = if (originalPath.isNullOrBlank()) toolsPath else "$toolsPath$sep$originalPath"
        }
        return shellCommand // command unchanged; PATH injected via envs map
    }
}
```

### LuaTerminalEnvironmentService (single, project-level definition)
```kotlin
@Service(Service.Level.PROJECT)
class LuaTerminalEnvironmentService(private val project: Project) {
    // Note: Path (not String) — prependEntryToPATH takes a java.nio.file.Path and handles
    // local->remote translation. Resolve dirs eagerly off-EDT (see customizeExecOptions threading).
    @Volatile private var cachedToolDirectories: List<Path>? = null

    init {
        // Invalidate the cache when Lua settings change.
        project.messageBus.connect().subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    cachedToolDirectories = null
                }
            },
        )
    }

    fun getToolDirectories(): List<Path> {
        cachedToolDirectories?.let { return it }
        val directories = project.service<LuaToolManager>()
            .getAllValidTools(project)
            .mapNotNull { it.path }
            .map { Path.of(it).parent }
            .distinct()
        cachedToolDirectories = directories
        return directories
    }
}
```

## Registration in plugin.xml
```xml
<!-- Register inside an optional config-file gated on the terminal plugin:
       plugin.xml: <depends optional="true" config-file="lunar-terminal.xml">org.jetbrains.plugins.terminal</depends>
     lunar-terminal.xml (EP uses the `implementation` attribute, NOT `class`): -->
<extensions defaultExtensionNs="org.jetbrains.plugins.terminal">
  <!-- Preferred: shell exec options customizer (EP id verified against terminal.xml in 2026.1) -->
  <shellExecOptionsCustomizer
      implementation="net.internetisalie.lunar.tool.terminal.LuaShellExecOptionsCustomizer"/>

  <!-- Deprecated fallback: local terminal customizer (register only one) -->
  <!--
  <localTerminalCustomizer
      implementation="net.internetisalie.lunar.tool.terminal.LuaLocalTerminalCustomizer"/>
  -->
</extensions>
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