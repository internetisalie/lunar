---
id: "ROCKS-04-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ROCKS-04"
priority: "high"
folders:
  - "[[features/rocks/04-task-execution/requirements|requirements]]"
---

# Risks & Design Gaps: Task Execution & Run Configurations (ROCKS-04)

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `ROCKS-04-R-01` | **C toolchain absent** | Medium | C-module builds need a system compiler; the design passes parent env but does not install one. Surface luarocks' own error; documented in `ROCKS-04-DR-01`. |
| `ROCKS-04-R-02` | **Interactive prompts** | Low | `upload` may prompt for credentials; `createColoredProcessHandler` gives a console but full PTY interactivity is best-effort (ROCKS-04-07, Could). |
| `ROCKS-04-R-03` | **Binary discovery** | Low | `LuaRocksSettings` defaults to `"luarocks"` on PATH; if not found, the platform shows a clear run-error. A discovery UX is deferred to the TOOL epic. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `ROCKS-04-G-01` | **Compiler provisioning** | Whether to detect/guide C-toolchain setup. | `ROCKS-04-DR-01` |
| `ROCKS-04-G-02` | **Per-config binary override** | Whether a config may override the global `luarocks` path. | `ROCKS-04-DR-02` |

## De-risking Tasks (DR)

- [ ] `ROCKS-04-DR-01`: Verify `luarocks make` builds a C `builtin` module with default
      pass-parent-env on Linux/macOS/Windows; document required env vars per platform.
- [ ] `ROCKS-04-DR-02`: Decide whether to add an optional per-config `luarocksPath` override
      field (defaulting to `LuaRocksSettings`).
