# Documentation Requirements (`DOC`)

Lunar provides rich integration with LuaCATS and LuaDoc to document code and provide real-time assistance.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| [`DOC-01`](spec/documentation/01-quick-documentation.md) | **Quick Documentation (Ctrl+Q)** | **M** | **Partial** | Display formatted documentation in a popup for any symbol at the caret, including types and parameter descriptions. |
| [`DOC-02`](spec/documentation/02-luacats-highlighting.md) | **LuaCATS Syntax Highlighting** | **M** | **Full** | Provide highlighting for tags (`@param`, `@class`, etc.) and types within Lua comments. |
| [`DOC-03`](spec/documentation/03-external-url-links.md) | **External URL Links** | **S** | **Partial** | Support linking to external documentation (e.g., standard Lua docs) within the Quick Doc popup. |
| [`DOC-04`](spec/documentation/04-documentation-generation.md) | **Documentation Generation** | **C** | **Partial** | Generate boilerplate LuaCATS comments for functions and classes based on their signature. |
| [`DOC-05`](spec/documentation/05-markdown-support.md) | **Markdown Support** | **S** | **Partial** | Support Markdown formatting within Lua comments for rich documentation rendering. |
| [`DOC-06`](spec/documentation/06-documentation-indexing.md) | **Documentation Indexing** | **S** | **Partial** | Index LuaDoc and LuaCATS comments for full-text search and quick documentation retrieval. Class, alias, and function names are now stub-indexed with LuaCATS metadata. |
| [`DOC-07`](spec/documentation/07-parameter-info.md) | **Parameter Info** | **S** | **Not Implemented** | Display parameter names, types, and descriptions in a popup when calling a function (e.g., `Ctrl+Shift+Space`). |
| [`DOC-08`](spec/documentation/08-luacats-annotation-parsing.md) | **Comprehensive LuaCATS Parsing** | **M** | **Not Implemented** | Exhaustive support for all LuaCATS tags (overloads, generics, operators, multi-line enums) and the full type system. |

---

## Detailed Implementation Status
...
### DOC-08: Comprehensive LuaCATS Parsing
- `DOC-08-01` **Full Tag Support**: **Not Implemented** (Missing specialized tags like `@operator`, `@async`, `@cast`, etc.)
- `DOC-08-02` **Complex Type System**: **Partial** (Unions and arrays implemented; missing specialized generics and recursive types)
- `DOC-08-03` **Multi-line Enum/Alias Support**: **Not Implemented**
- `DOC-08-04` **Exhaustive Parser Validation**: **Not Implemented** (Currently lacks a dedicated parser test suite)

- `DOC-01-01` **Popup Trigger**: **Implemented** (`LuaDocumentationTargetProvider`)
- `DOC-01-02` **Rich Text Rendering**: **Implemented** (`LuaDocumentationRenderer` with Markdown)
- `DOC-01-03` **Symbol Resolution**: **Implemented** (`LuaDocumentationTargetProvider.resolveDocumentationTarget`)
- `DOC-01-04` **Type Formatting**: **Implemented** (`LuaCatsDocumentationRenderer` colors types)
- `DOC-01-05` **Interactive Links**: **Not Implemented** (No link generation for types)
- `DOC-01-06` **Inherited Documentation**: **Not Implemented**

### DOC-02: LuaCATS Syntax Highlighting
- `DOC-02-01` **Comment Detection**: **Implemented** (`LuaCatsAnnotator`)
- `DOC-02-02` **Tag Highlighting**: **Implemented**
- `DOC-02-03` **Type Parsing and Highlighting**: **Implemented**
- `DOC-02-04` **Parameter Alignment**: **Implemented**
- `DOC-02-05` **Field Identification**: **Implemented**
- `DOC-02-06` **Deprecated/Since Tags**: **Partial** (Parsed but no special highlighting/strikethrough)

### DOC-03: External URL Links
- `DOC-03-01` **URL Detection**: **Implemented** (via Markdown parser)
- `DOC-03-02` **Clickable Links**: **Implemented**
- `DOC-03-03` **Default Browser Integration**: **Implemented**
- `DOC-03-04` **Markdown Link Rendering**: **Implemented**
- `DOC-03-05` **Lua Standard Library Links**: **Not Implemented**

### DOC-04: Documentation Generation
- `DOC-04-01` **Boilerplate Insertion**: **Partial** (Only `--- ` prefix continuation in `LuaEnterHandlerDelegate`)
- `DOC-04-02` **Parameter Extraction**: **Not Implemented**
- `DOC-04-03` **Template Editing**: **Not Implemented**
- `DOC-04-04` **Type Inference (Basic)**: **Not Implemented**
- `DOC-04-05` **Return Tag Detection**: **Not Implemented**

### DOC-05: Markdown Support
- `DOC-05-01` **Markdown Rendering**: **Implemented** (`org.intellij.markdown`)
- `DOC-05-02` **Syntax Highlighting in Code Blocks**: **Not Implemented**
- `DOC-05-03` **Escape Character Support**: **Implemented**
- `DOC-05-04` **Paragraph Handling**: **Implemented**

### DOC-06: Documentation Indexing
- `DOC-06-01` **Stub Indexing**: **Full** (Indexed `@class`/`@alias` names from `LuaLocalVarStub`, and global/local functions with LuaCATS metadata via `LuaFuncStub` and `LuaLocalFuncStub`)
- `DOC-06-02` **Type Map Construction**: **Full** ([Design](spec/documentation/06-type-map-construction-design.md) - Object-oriented type system with dynamic stub materialization, class merging, and inheritance; `TypeParser` fully implemented including function signatures)
- `DOC-06-03` **Incremental Updates**: **N/A** (IntelliJ stub infrastructure handles this automatically)
- `DOC-06-04` **Full-Text Search**: **Not Implemented**
- `DOC-06-05` **Cross-File Resolution**: **Partial** (Via `LuaFileBindingsIndex` for general bindings; class, alias, and function stubs now queryable cross-file with metadata)

### DOC-07: Parameter Info
- `DOC-07-01` **Popup Trigger**: **Not Implemented**
- `DOC-07-02` **Active Parameter Tracking**: **Not Implemented**
- `DOC-07-03` **Overload Support**: **Not Implemented**
- `DOC-07-04` **Type Integration**: **Not Implemented**
- `DOC-07-05` **Vararg Support**: **Not Implemented**
