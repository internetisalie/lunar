# Documentation Requirements (`DOC`)

Lunar provides rich integration with LuaCATS and LuaDoc to document code and provide real-time assistance.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| [`DOC-01`](spec/documentation/01-quick-documentation.md) | **Quick Documentation (Ctrl+Q)** | **M** | Display formatted documentation in a popup for any symbol at the caret, including types, parameter descriptions, and interactive type links with class inheritance support. |
| [`DOC-02`](spec/documentation/02-luacats-highlighting.md) | **LuaCATS Syntax Highlighting** | **M** | Provide highlighting for tags (`@param`, `@class`, etc.) and types within Lua comments. |
| [`DOC-03`](spec/documentation/03-external-url-links.md) | **External URL Links** | **S** | Support linking to external documentation (e.g., standard Lua docs) within the Quick Doc popup. |
| [`DOC-04`](spec/documentation/04-documentation-generation.md) | **Documentation Generation** | **C** | Generate boilerplate LuaCATS comments for functions and classes based on their signature. |
| [`DOC-05`](spec/documentation/05-markdown-support.md) | **Markdown Support** | **S** | Support Markdown formatting within Lua comments for rich documentation rendering. |
| [`DOC-06`](spec/documentation/06-documentation-indexing.md) | **Documentation Indexing** | **S** | Index LuaDoc and LuaCATS comments for full-text search and quick documentation retrieval. Class, alias, and function names are now stub-indexed with LuaCATS metadata. |
| [`DOC-07`](spec/documentation/07-parameter-info.md) | **Parameter Info** | **S** | Display parameter names, types, and descriptions in a popup when calling a function (e.g., `Ctrl+P`). |
| [`DOC-08`](spec/documentation/08-luacats-annotation-parsing.md) | **Comprehensive LuaCATS Parsing** | **M** | All 19 LuaCATS tags, complex types, and multi-line support fully implemented and tested (18/18 tests passing). |

---

## Detailed Implementation Status

### DOC-01: Quick Documentation (Ctrl+Q)

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-01-01` | Popup Trigger | `LuaDocumentationTargetProvider` |
| `DOC-01-02` | Rich Text Rendering | `LuaDocumentationRenderer` with Markdown |
| `DOC-01-03` | Symbol Resolution | `LuaDocumentationTargetProvider.resolveDocumentationTarget` |
| `DOC-01-04` | Type Formatting | `LuaCatsDocumentationRenderer` colors types |
| `DOC-01-05` | Interactive Links | `buildTypeLink()` generates `psi_element://` links; `LuaDocumentationLinkHandler` resolves them |
| `DOC-01-06` | Inherited Documentation | `renderLuaLocalVarDecl()` renders inherited fields from parent classes via `lookupParentComment()` |

### DOC-02: LuaCATS Syntax Highlighting

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-02-01` | Comment Detection | `LuaCatsAnnotator` |
| `DOC-02-02` | Tag Highlighting | — |
| `DOC-02-03` | Type Parsing and Highlighting | — |
| `DOC-02-04` | Parameter Alignment | — |
| `DOC-02-05` | Field Identification | — |
| `DOC-02-06` | Deprecated/Since Tags | Parsed but no special highlighting/strikethrough |

### DOC-03: External URL Links

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-03-01` | URL Detection | Via Markdown parser |
| `DOC-03-02` | Clickable Links | — |
| `DOC-03-03` | Default Browser Integration | — |
| `DOC-03-04` | Markdown Link Rendering | — |
| `DOC-03-05` | Lua Standard Library Links | — |

### DOC-04: Documentation Generation

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-04-01` | Boilerplate Insertion | Only `--- ` prefix continuation in `LuaEnterHandlerDelegate` |
| `DOC-04-02` | Parameter Extraction | — |
| `DOC-04-03` | Template Editing | — |
| `DOC-04-04` | Type Inference (Basic) | — |
| `DOC-04-05` | Return Tag Detection | — |

### DOC-05: Markdown Support

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-05-01` | Markdown Rendering | `org.intellij.markdown` |
| `DOC-05-02` | Syntax Highlighting in Code Blocks | — |
| `DOC-05-03` | Escape Character Support | — |
| `DOC-05-04` | Paragraph Handling | — |

### DOC-06: Documentation Indexing

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-06-01` | Stub Indexing | Indexed `@class`/`@alias` names from `LuaLocalVarStub`, and global/local functions with LuaCATS metadata via `LuaFuncStub` and `LuaLocalFuncStub` |
| `DOC-06-02` | Type Map Construction | [Design](spec/documentation/06-type-map-construction-design.md) - Object-oriented type system with dynamic stub materialization, class merging, and inheritance; `TypeParser` fully implemented including function signatures |
| `DOC-06-03` | Incremental Updates | IntelliJ stub infrastructure handles this automatically |
| `DOC-06-04` | Full-Text Search | — |
| `DOC-06-05` | Cross-File Resolution | General bindings via `LuaFileBindingsIndex`; class, alias, and function stubs via `StubIndex` |

### DOC-07: Parameter Info

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-07-01` | Popup Trigger | `LuaParameterInfoHandler` registered in `plugin.xml` |
| `DOC-07-02` | Active Parameter Tracking | `updateParameterInfo` calculates index based on comma count |
| `DOC-07-03` | Overload Support | `resolveCandidates` extracts `@overload` tags from `LuaCatsComment` |
| `DOC-07-04` | Type Integration | Signature candidate includes types from LuaCATS tags |
| `DOC-07-05` | Vararg Support | Correctly handles `...` tokens in function signatures |


### DOC-08: Comprehensive LuaCATS Parsing

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-08-01` | Full Tag Support | All 19 tags |
| `DOC-08-02` | Complex Type System | Unions, arrays, tuples, dictionaries, function signatures, generics, literal types |
| `DOC-08-03` | Multi-line Enum Support | `---|` syntax fully supported |
| `DOC-08-04` | Parser Validation | 18/18 tests passing including edge cases |
