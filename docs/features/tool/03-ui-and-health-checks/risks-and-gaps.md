---
id: "TOOL-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TOOL-03"
status: "planned"
priority: "high"
folders:
  - "[[features/tool/03-ui-and-health-checks/requirements|requirements]]"
---

# Risks & Design Gaps: UI/UX & Health Monitoring (`TOOL-03`)

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `TOOL-03-R-01` | **TOOL-01/02 dependency** | High | This feature cannot ship before TOOL-01/02 (data model + bindings). Both are planned to the bar; sequence them first. |
| `TOOL-03-R-02` | **VFS coverage of tool paths** | Medium | Binaries outside the project content root may not raise VFS events; mitigated by lazy re-validation on access (§3.1) + eager checks on settings open/startup. `TOOL-03-DR-01`. |
| `TOOL-03-R-03` | **Banner noise** | Low | Banner shows only for invalid *bound* tools on Lua files; one balloon per project-open transition, not per check. |
| `TOOL-03-R-04` | **mtime precision** | Low | Some filesystems have coarse mtime; the gate only *skips* re-checks — a missed change is caught by the next VFS event or eager pass. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `TOOL-03-G-01` | **Non-VFS binaries** | Whether to also watch `$PATH` binaries outside the VFS (e.g. via a coarse on-focus recheck). | `TOOL-03-DR-01` |
| `TOOL-03-G-02` | **`--version` flag variance** | Some tools use `-v`/`--version`/`version`; TOOL-01's `LuaToolType` should carry the per-type version flag. | `TOOL-03-DR-02` |

## De-risking Tasks (DR)

- [ ] `TOOL-03-DR-01`: Confirm `addAsyncFileListenerBackgroundable` fires for binaries outside
      the project root; if not, add an on-window-focus recheck.
- [ ] `TOOL-03-DR-02`: Define per-`LuaToolType` version flags in TOOL-01 (luarocks `--version`,
      luacheck `--version`, stylua `--version`) so §3.1 uses the right argument.
