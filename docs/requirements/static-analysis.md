# Static Analysis Requirements (`ANALYSIS`)

Lunar integrates with external static analysis tools to catch errors and provide code quality feedback.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `ANALYSIS-01` | **Luacheck Integration** | **M** | **Full** | Detect undefined variables, unused locals, and style issues via Luacheck. |
| `ANALYSIS-02` | **Settings Panel Integration** | **M** | **Full** | Provide a settings UI to configure Luacheck options. |
| `ANALYSIS-03` | **External Annotator** | **M** | **Full** | Display Luacheck warnings as inline annotations in real time. |
| `ANALYSIS-04` | **Luacheck Output Parsing** | **M** | **Full** | Parse Luacheck output and convert it into IDE diagnostics. |
| `ANALYSIS-05` | **Custom Rules Support** | **S** | **Full** | Support project-specific Luacheck configuration files (`.luacheckrc`). |
