# Documentation Requirements (`DOC`)

Lunar provides rich integration with LuaCATS and LuaDoc to document code and provide real-time assistance.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `DOC-01` | **Quick Documentation (Ctrl+Q)** | **M** | Display formatted documentation in a popup for any symbol at the caret, including types and parameter descriptions. |
| `DOC-02` | **LuaCATS Syntax Highlighting** | **M** | Provide highlighting for tags (`@param`, `@class`, etc.) and types within Lua comments. |
| `DOC-03` | **External URL Links** | **S** | Support linking to external documentation (e.g., standard Lua docs) within the Quick Doc popup. |
| `DOC-04` | **Documentation Generation** | **C** | Generate boilerplate LuaCATS comments for functions and classes based on their signature. |
| `DOC-05` | **Markdown Support** | **S** | Support Markdown formatting within Lua comments for rich documentation rendering. |
| `DOC-06` | **Documentation Indexing** | **S** | Index LuaDoc and LuaCATS comments for full-text search and quick documentation retrieval. |
| `DOC-07` | **Parameter Info** | **S** | Display parameter names, types, and descriptions in a popup when calling a function (e.g., `Ctrl+Shift+Space`). |
