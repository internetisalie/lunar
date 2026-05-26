---
id: "TOOL"
title: "TOOL: Tool Inventory Management"
type: "epic"
status: "planned"
priority: "high"
folders:
  - "[[features]]"
---

# Tool Inventory Management Requirements (`TOOL`)

Lunar provides a comprehensive registry for managing external Lua tool binaries (e.g., `luarocks`, `lua-format`, `luacheck`), ensuring consistent behavior across projects and environments.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`TOOL-00`](00-de-risking/requirements.md) | **De-risking & Technical Spikes** | **M** | **Not Implemented** | Critical technical validations and architectural spikes. |
| [`TOOL-01`](01-inventory-management/requirements.md) | **Core Tool Registry & Discovery** | **M** | **Not Implemented** | Foundational registry for discovery and validation of binaries. |
| [`TOOL-02`](02-project-binding/requirements.md) | **Project Binding & Environment Integration** | **M** | **Not Implemented** | Per-project overrides and PATH injection for terminals and run configs. |
| [`TOOL-03`](03-ui-and-health-checks/requirements.md) | **UI/UX & Health Monitoring** | **M** | **Not Implemented** | User interface for configuration and background health/validation. |

---

## Motivation
The IDE maintains an inventory of configured Lua interpreter binaries but lacks equivalent support for other essential Lua ecosystem tools. A centralized tool inventory prevents inconsistent behavior and failed operations when tools are not in the system PATH.

## Benefits
- **Reproducibility**: Ensures consistent tool versions across developer machines and CI/CD.
- **Productivity**: Reduces setup time via auto-discovery of common tools.
- **Safety**: Prevents accidental use of incompatible tool versions.
- **Consistency**: Aligns tool management with existing Lua interpreter workflows.

## Detailed Implementation Status

### TOOL-01: Core Tool Registry & Discovery
| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `TOOL-01-01` | Global Tool Registration | `LuaToolManager`, `LuaApplicationSettings` |
| `TOOL-01-02` | Auto-Detection | `LuaToolDiscoveryService` |
| `TOOL-01-03` | Manual Registration | `LuaToolInventoryPanel` (File Picker) |
| `TOOL-01-04` | Version Checking | `LuaToolValidator` |
| `TOOL-01-05` | Min Version Validation | `LuaToolValidator` |

### TOOL-02: Project Binding & Environment Integration
| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `TOOL-02-01` | Per-Project Tool Binding | `LuaProjectSettings` |
| `TOOL-02-02` | Context-Aware Invocation | `LuaToolManager.getEffectiveTool` |
| `TOOL-02-03` | PATH Augmentation | `LuaToolEnvironmentProvider` |
| `TOOL-02-04` | Terminal Integration | `LocalTerminalDirectRunner` / `TerminalCustomizer` |

### TOOL-03: UI/UX & Health Monitoring
| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `TOOL-03-01` | Settings UI | `LuaToolInventoryPanel` |
| `TOOL-03-02` | Project Settings Overlay | `LuaProjectToolPanel` |
| `TOOL-03-03` | Health Checks | `LuaToolHealthCheckActivity` |
| `TOOL-03-04` | Notifications | `NotificationGroupManager` |
