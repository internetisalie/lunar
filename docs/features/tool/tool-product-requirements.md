---
id: "TOOL-PRD"
title: "Product Requirements"
type: "spec"
parent_id: "TOOL"
status: "planned"
priority: "high"
folders:
  - "[[features/tool/requirements|TOOL]]"
---

# Product Requirements: Tool Inventory Management (TOOL Epic)

## 1. Feature Overview
The TOOL feature extends the IDE's capability to manage and inventory external tool binaries (e.g., `luarocks`, `lua-format`, `luacheck`) similar to how Lua interpreters are currently managed.

This epic is divided into three features:
1. **TOOL-01: Core Tool Registry & Discovery** - Foundational registry for discovery and validation of binaries.
2. **TOOL-02: Project Binding & Environment Integration** - Per-project overrides and PATH injection for terminals and run configs.
3. **TOOL-03: UI/UX & Health Monitoring** - User interface for configuration and background health/validation.

## 2. Stakeholders
- **End Developers**: Lua developers who use the IDE for Lua/LuaRocks projects.
- **DevOps/Build Engineers**: Individuals responsible for CI/CD pipelines that rely on consistent tool versions.
- **IDE Administrators**: Teams managing IDE configurations across organizations.
- **Product Team**: Responsible for prioritizing and delivering the feature.

## 3. User Stories
### As a developer, I want to...
- **TOOL-US1**: Register a `luarocks` binary in the IDE so that the ROCKS feature can use it for package operations.
- **TOOL-US2**: Bind a specific version of `luarocks` to my project to ensure reproducibility across team members and CI.
- **TOOL-US3**: Have the IDE automatically detect common Lua tool installations (like `luarocks`, `lua-format`, `luacheck`) to reduce setup time.
- **TOOL-US4**: Validate that a registered tool binary is functional and meets version requirements before use.
- **TOOL-US5**: See which tool version is being used by the IDE for transparency and debugging.
- **TOOL-US6**: Receive notifications if a required tool is missing or outdated.
- **TOOL-US7**: Use the IDE's integrated terminal with the correct tool binary in PATH for seamless command-line work.
- **TOOL-US8**: Run IDE actions (e.g., formatting, linting) using the project-bound tool version.

### As a DevOps engineer, I want...
- **TOOL-US9**: Ensure that the IDE uses the same tool versions as our CI/CD pipelines to avoid "works on my machine" issues.
- **TOOL-US10**: Enforce minimum tool version requirements across the team via IDE settings.

### As an IDE administrator, I want...
- **TOOL-US11**: Centrally manage tool configurations for multiple projects or users.
- **TOOL-US12**: Leverage existing IDE inventory patterns (like Lua interpreters) for consistency.

## 4. Acceptance Criteria
| ID | Feature | Expected Behavior | Priority | Status |
| :--- | :--- | :--- | :--- | :--- |
| **TOOL-01-01** | **Global Tool Registration** | Users can register a tool binary by providing its file path and optionally a label/version. | M | Not Implemented |
| **TOOL-01-02** | **Auto-Detection** | The IDE auto-detects known Lua tools (luarocks, lua-format, luacheck) in standard installation paths on first run or via manual trigger. | M | Not Implemented |
| **TOOL-01-03** | **Manual Registration** | Manual registration via file picker is supported for custom tool locations. | M | Not Implemented |
| **TOOL-01-04** | **Version Checking** | The IDE validates a tool binary by checking its version output (e.g., `luarocks --version`) and parses the version string. | M | Not Implemented |
| **TOOL-01-05** | **Minimum Version Validation** | For the ROCKS feature, the IDE ensures the `luarocks` binary meets the minimum version required (e.g., >= 3.0.0). | M | Not Implemented |
| **TOOL-01-06** | **Compatibility Matrix** | The IDE checks compatibility between the bound Lua interpreter and the tool (e.g., LuaRocks 3.x supports Lua 5.1-5.5). | M | Not Implemented |
| **TOOL-02-01** | **Per-Project Tool Binding** | Users can bind a registered tool to a specific project or set it as global. | M | Not Implemented |
| **TOOL-02-02** | **Context-Aware Invocation** | When running ROCKS feature operations (e.g., package search, install), the IDE uses the project-bound `luarocks` binary. | M | Not Implemented |
| **TOOL-02-03** | **PATH Augmentation** | Project-bound tool binary directories are prepended to PATH for subprocesses. | M | Not Implemented |
| **TOOL-02-04** | **Terminal Integration** | Integrated terminals automatically have the project-bound tool binary directories prepended to PATH. | M | Not Implemented |
| **TOOL-02-05** | **IDE Action Integration** | IDE actions like "Format Document" or "Run Luacheck" use the project-bound tool versions. | M | Not Implemented |
| **TOOL-02-06** | **Fallback Mechanism** | If a project-bound tool is unavailable, the IDE falls back to the global/default tool and logs a warning. | M | Not Implemented |
| **TOOL-03-01** | **Settings UI** | A dedicated "Tools" section exists in IDE preferences, mirroring the Lua interpreters UI. | M | Not Implemented |
| **TOOL-03-02** | **Project Settings Overlay** | Project properties include a tab to view and bind tool versions for that project. | M | Not Implemented |
| **TOOL-03-03** | **Health Checks** | Invalid or non-functional tool binaries are marked with an error in the inventory via background checks. | S | Not Implemented |
| **TOOL-03-04** | **Notifications** | Users receive non-intrusive notifications for missing required tools, outdated tool versions, or invalid tool executables. | M | Not Implemented |
| **TOOL-03-05** | **Diagnostic Reporting** | The IDE surfaces tool version and path information in settings panels and diagnostic logs. | M | Not Implemented |

## 5. Technical Details
- Reuses existing UI patterns and storage mechanisms from Lua interpreter inventory
- Implements tool validation via `--version` output parsing
- Integrates with ROCKS feature for `luarocks` binary resolution
- Augments PATH for embedded terminals and build tasks
- Supports per-project and global tool bindings
- Assumes tools follow standard `--version` output for version detection

## 6. Dependencies
- Depends on: Existing IDE infrastructure for managing Lua interpreter inventories (UI components, storage mechanisms)
- Enables: The ROCKS feature (LuaRocks integration) by providing reliable access to the `luarocks` binary
- Related Features: Future enhancements for other Lua tools (e.g., ldoc, luacov) will reuse this framework

## 7. Risks and Mitigations
| Risk | Mitigation |
| :--- | :--- |
| Users may register incompatible tool versions (e.g., LuaRocks 2.x with Lua 5.4) | Implement version compatibility checks during registration and binding; warn users of known incompatibilities. |
| Tool binaries may be removed or changed after registration (breaking binding) | Validate tool existence and functionality before each use; provide clear error messages and re-prompt for resolution. |
| Auto-detection may fail in non-standard environments | Provide robust manual registration and clear guidance in UI for adding custom paths. |
| Global tool fallback may cause inconsistencies if not communicated | Clearly indicate in UI when a global fallback is being used; recommend project-bound tools for reproducibility. |
| Version parsing may fail for unconventional `--version` outputs | Use flexible regex patterns and allow users to manually specify version if auto-detection fails (edge case). |

## 8. Resolved Decisions
1. **Version Update Checks**: Deferred to v2.0. v1.0 focuses on management and execution of locally installed tools.
2. **Tool Type Extensibility**: Support for "Unknown" version state is included. If version extraction fails, tools can still be registered as `GENERIC`.
3. **Interpreter Awareness**: Best-effort detection of the tool's linked Lua version will be implemented. Mismatches will trigger warnings rather than hard blocks.
4. **Cache Invalidation**: Tool metadata will be cached in settings. Background health checks will use file modification time (`mtime`) to determine when to re-validate via CLI.
5. **Enterprise Management**: Handled via standard IntelliJ project settings synchronization. Dedicated JSON import/export is out of scope for v1.0.
