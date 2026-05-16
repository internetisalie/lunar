---
folders:
  - "[[features/rocks/04-task-execution/requirements|requirements]]"
title: "Technical Design"
---

# Technical Design: Task Execution & Run Configurations (ROCKS-04)

## Overview
This feature implements a specialized Run Configuration type for LuaRocks commands, enabling repeatable task execution within the IDE.

## Architecture

### 1. Run Configuration Type
- **LuaRocksRunConfigurationType**: Registers the configuration type in \`plugin.xml\`.
- **LuaRocksRunConfiguration**: Stores the command, arguments, rockspec path, and environment variables.
- **LuaRocksRunConfigurationEditor**: Provides the UI for editing these settings.

### 2. Execution Engine
- **LuaRocksRunProfileState**: Handles the actual execution.
- **Workflow**:
  1. Resolve the \`luarocks\` binary via \`LuaToolManager\`.
  2. Construct a \`GeneralCommandLine\` with the working directory set to the project root.
  3. Prepend the tool directory to \`PATH\`.
  4. Attach a \`TextConsoleBuilder\` to show output in the "Run" tool window.

## Implementation Details

### Configuration Persistence
Settings are serialized into XML via the \`PersistentStateComponent\` mechanism within the run configuration instance.

### Command Presets
A predefined list of commands (\`make\`, \`build\`, \`install\`, \`test\`, \`upload\`) will be available in a dropdown, but the user can also enter free-text arguments.

## Testing Strategy
- **Unit Tests**: Verify that \`GeneralCommandLine\` is constructed correctly based on various configuration inputs.
- **Manual Verification**: Create and run a "make" configuration and verify it successfully builds the project.
