---
id: "ROCKS-01-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ROCKS-01"
priority: "high"
folders:
  - "[[features/rocks/01-project-initialization/requirements|requirements]]"
---

# Risks & Design Gaps: Project Initialization & Setup (ROCKS-01)

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `ROCKS-01-R-01` | **Wizard EP per IDE** | Medium | `DirectoryProjectGenerator` is the correct EP for the GoLand build target; IDEA would need a `ModuleBuilder` adapter (deferred). Validated by running the wizard in the GoLand sandbox (`ROCKS-01-DR-01`). |
| `ROCKS-01-R-02` | **`luarocks init` variability** | Low | Enrichment is optional; the `Must` outputs come from templates, so absence/oddities of the binary do not break the project. |
| `ROCKS-01-R-03` | **Run-config template timing** | Low | Patching the template before any Lua config exists ensures inheritance; existing configs are not retro-patched (documented). |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `ROCKS-01-G-01` | **IDEA support** | Whether to ship a `ModuleBuilder` adapter for IntelliJ IDEA. | `ROCKS-01-DR-02` |
| `ROCKS-01-G-02` | **Neovim-plugin template** | The requirements mention a Neovim layout; v1 ships Library/Application only. | `ROCKS-01-DR-03` |

## De-risking Tasks (DR)

- [ ] `ROCKS-01-DR-01`: Run the generator in the GoLand 2026.1 sandbox; confirm it appears in
      New Project and produces the expected tree.
- [ ] `ROCKS-01-DR-02`: Decide whether to add a `ModuleBuilder` adapter for IDEA.
- [ ] `ROCKS-01-DR-03`: Decide whether to add the Neovim-plugin layout template (extra
      `RockType` + `LuaRocksTemplates` variants).
