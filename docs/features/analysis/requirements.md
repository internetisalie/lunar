---
id: "ANALYSIS"
title: "ANALYSIS: Static Analysis"
type: "epic"
status: "in_progress"
vf_icon: 🚧
priority: "medium"
folders:
  - "[[features]]"
---

# Static Analysis Requirements (`ANALYSIS`)

Lunar integrates with external static analysis tools to catch errors and provide code quality
feedback (`ANALYSIS-01`…`-05`), and owns its **own** flow analysis (`ANALYSIS-06`…`-07`).

> The second half is not Luacheck work and the epic's original one-line framing did not cover it.
> `ANALYSIS-06` shipped a control-flow graph whose only consumer is `LuaUnreachableCodeInspection`;
> `ANALYSIS-07` exists because the type engine carries a rich value domain with no CFG, while that
> CFG carries no value domain — a split [[TYPE-08]] §9 recorded and declined to close.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `ANALYSIS-01` | **Luacheck Integration** | **M** | Detect undefined variables, unused locals, and style issues via Luacheck. |
| `ANALYSIS-02` | **Settings Panel Integration** | **M** | Provide a settings UI to configure Luacheck options. |
| `ANALYSIS-03` | **External Annotator** | **M** | Display Luacheck warnings as inline annotations in real time. |
| `ANALYSIS-04` | **Luacheck Output Parsing** | **M** | Parse Luacheck output and convert it into IDE diagnostics. |
| `ANALYSIS-05` | **Custom Rules Support** | **S** | Support project-specific Luacheck configuration files (`.luacheckrc`). |
| `ANALYSIS-06` | **Control Flow Graph** | **M** | Build and cache a per-`ScopeOwner` CFG with reachability and read/write instructions. |
| `ANALYSIS-07` | **Value Constraints over the CFG** | **M** | Give the CFG a value domain, or the type engine a CFG — today they are two flow analyses that never speak. |

---

## Detailed Implementation Status

### ANALYSIS-01: Luacheck Integration
- **Status**: **Implemented**

### ANALYSIS-02: Settings Panel Integration
- **Status**: **Implemented**

### ANALYSIS-03: External Annotator
- **Status**: **Implemented**

### ANALYSIS-04: Luacheck Output Parsing
- **Status**: **Implemented**

### ANALYSIS-05: Custom Rules Support
- **Status**: **Implemented**

### ANALYSIS-06: Control Flow Graph
- **Status**: **Implemented** — `src/main/kotlin/net/internetisalie/lunar/analysis/controlflow/`.
  Consumed by `LuaUnreachableCodeInspection` and nothing else.

### ANALYSIS-07: Value Constraints over the CFG
- **Status**: **Not Implemented** — filed 2026-08-20 from three independently-diagnosed defects
  ([[BUG-441]], [[BUG-435]], [[BUG-428]]) that turned out to share one shape. **Planned as far as
  its de-risking spike, and deliberately no further**: `ANALYSIS-07-01` is *"decide the direction
  from a measurement"*, so [[ANALYSIS-07-DESIGN|design.md]] §2–§4 specify the Phase 0 spike and the
  decision procedure in full, while the implementations of `-02`/`-03`/`-04` are marked DEFERRED
  against named DRs in [[ANALYSIS-07-RISKS|risks-and-gaps.md]]. The feature stays `todo` until
  §3.7 fires and design §5 is written from its output.

