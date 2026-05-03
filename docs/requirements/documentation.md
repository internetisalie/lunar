# Documentation Requirements (`DOC`)

Lunar provides rich integration with LuaCATS and LuaDoc to document code and provide real-time assistance.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| [`DOC-01`](spec/documentation/01-quick-documentation.md) | **Quick Documentation (Ctrl+Q)** | **M** | **Partial** | Display formatted documentation in a popup for any symbol at the caret, including types and parameter descriptions. |
| [`DOC-02`](spec/documentation/02-luacats-highlighting.md) | **LuaCATS Syntax Highlighting** | **M** | **Full** | Provide highlighting for tags (`@param`, `@class`, etc.) and types within Lua comments. |
| [`DOC-03`](spec/documentation/03-external-url-links.md) | **External URL Links** | **S** | **Partial** | Support linking to external documentation (e.g., standard Lua docs) within the Quick Doc popup. |
| [`DOC-04`](spec/documentation/04-documentation-generation.md) | **Documentation Generation** | **C** | **Partial** | Generate boilerplate LuaCATS comments for functions and classes based on their signature. |
| [`DOC-05`](spec/documentation/05-markdown-support.md) | **Markdown Support** | **S** | **Partial** | Support Markdown formatting within Lua comments for rich documentation rendering. |
| [`DOC-06`](spec/documentation/06-documentation-indexing.md) | **Documentation Indexing** | **S** | **Partial** | Index LuaDoc and LuaCATS comments for full-text search and quick documentation retrieval. |
| [`DOC-07`](spec/documentation/07-parameter-info.md) | **Parameter Info** | **S** | **Future Work** | Display parameter names, types, and descriptions in a popup when calling a function (e.g., `Ctrl+Shift+Space`). |
