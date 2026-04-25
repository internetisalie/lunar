# Static Analysis Requirements (`ANALYSIS`)

Lunar integrates with external static analysis tools to catch errors and provide code quality feedback.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `ANALYSIS-01` | **Luacheck Integration** | **M** | Integrate the Luacheck static analysis tool to detect undefined variables, unused locals, redefined variables, and code style issues. |
| `ANALYSIS-02` | **Settings Panel Integration** | **M** | Provide a settings UI to configure Luacheck options (ignore patterns, global whitelist, max warnings). |
| `ANALYSIS-03` | **External Annotator** | **M** | Display Luacheck warnings and errors as inline annotations in the editor in real time. |
| `ANALYSIS-04` | **Luacheck Output Parsing** | **M** | Parse Luacheck command-line output and convert it into IDE diagnostics. |
| `ANALYSIS-05` | **Custom Rules Support** | **S** | Allow users to define custom Luacheck configuration files (`.luacheckrc`) per project. |
