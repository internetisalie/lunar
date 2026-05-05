# Static Analysis Requirements (`ANALYSIS`)

Lunar integrates with external static analysis tools to catch errors and provide code quality feedback.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `ANALYSIS-01` | **Luacheck Integration** | **M** | Detect undefined variables, unused locals, and style issues via Luacheck. |
| `ANALYSIS-02` | **Settings Panel Integration** | **M** | Provide a settings UI to configure Luacheck options. |
| `ANALYSIS-03` | **External Annotator** | **M** | Display Luacheck warnings as inline annotations in real time. |
| `ANALYSIS-04` | **Luacheck Output Parsing** | **M** | Parse Luacheck output and convert it into IDE diagnostics. |
| `ANALYSIS-05` | **Custom Rules Support** | **S** | Support project-specific Luacheck configuration files (`.luacheckrc`). |

---

## Detailed Implementation Status

### ANALYSIS-01: Luacheck Integration
- **Status**: **Implemented** (`LuaCheckAnnotator`)

### ANALYSIS-02: Settings Panel Integration
- **Status**: **Implemented** (`LuaCheckSettingsPanel`)

### ANALYSIS-03: External Annotator
- **Status**: **Implemented** (`LuaCheckAnnotator` extends `ExternalAnnotator`)

