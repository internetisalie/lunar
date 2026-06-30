---
id: "DOC"
title: "DOC: Documentation & LuaCATS"
type: "epic"
status: "done"
vf_icon: ✅
priority: "medium"
folders:
  - "[[features]]"
---

# Documentation Requirements (`DOC`)

Lunar provides rich integration with LuaCATS and LuaDoc to document code and provide real-time assistance.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| [`DOC-01`](01-quick-documentation/requirements.md) | **Quick Documentation (Ctrl+Q)** | **M** | Display formatted documentation in a popup for any symbol at the caret, including types, parameter descriptions, and interactive type links with class inheritance support. |
| [`DOC-02`](02-luacats-highlighting/requirements.md) | **LuaCATS Syntax Highlighting** | **M** | **Full** | Provide syntax highlighting for LuaCATS tags (e.g., `@param`, `@class`) and highlight associated types within Lua doc comments. |
| [`DOC-03`](03-external-url-links/requirements.md) | **External URL Links** | **S** | **Full** | Link to external documentation (e.g., standard Lua docs) from the Quick Doc popup. |
| [`DOC-04`](04-documentation-generation/requirements.md) | **Documentation Generation** | **C** | **Full** | Automatically generate LuaCATS documentation comments (LuaDoc) for functions and classes derived from their signatures and inferred types. |
| [`DOC-05`](05-markdown-support/requirements.md) | **Markdown Support** | **S** | **Full** | Enable Markdown formatting within Lua comments to produce rendered documentation with tables, lists, code highlights and other standard Markdown features. |
| [`DOC-06`](06-documentation-indexing/requirements.md) | **Documentation Indexing** | **S** | Index LuaDoc and LuaCATS comments for full-text search and quick documentation retrieval. Class, alias, and function names, as well as platform library symbols, are stub-indexed with LuaCATS metadata. |
| [`DOC-07`](07-parameter-info/requirements.md) | **Parameter Info** | **S** | Display parameter names, types, and descriptions in a popup when calling a function (e.g., `Ctrl+P`). |
| [`DOC-08`](08-luacats-annotation-parsing/requirements.md) | **Comprehensive LuaCATS Parsing** | **M** | All 19 LuaCATS tags, complex types, and multi-line support fully implemented and tested (18/18 tests passing). |

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
| `DOC-02-01` | Comment Detection | `LuaCatsAnnotator` targets `---` doc comments |
| `DOC-02-02` | Tag Highlighting | `LuaCatsAnnotator` handles `LCATS_TAG` tokens |
| `DOC-02-03` | Type Highlighting | `LuaCatsAnnotator` handles builtin and named types |
| `DOC-02-04` | Parameter Alignment | `LuaCatsAnnotator` handles `LuaCatsArgName` in `@param` |
| `DOC-02-05` | Field Identification | `LuaCatsAnnotator` handles `LuaCatsFieldNameDescriptor` |
| `DOC-02-06` | Deprecated/Since Tags | `LuaCatsAnnotator` handles `LuaCatsDeprecatedTag` with custom attributes |

### DOC-03: External URL Links

| ID | Requirement | Implementation | Status |
| :--- | :--- | :--- | :--- |
| `DOC-03-01` | URL Detection | Via GFM autolinks and `@see` tag combining | **Full** |
| `DOC-03-02` | Clickable Links | Rendered as `<a href>` tags | **Full** |
| `DOC-03-03` | Default Browser Integration | Handled by IntelliJ Platform for non-PSI links | **Full** |
| `DOC-03-04` | Markdown Link Rendering | Supported via GFM flavour | **Full** |
| `DOC-03-05` | Lua Standard Library Links | `buildStdlibSection` generates links to lua.org | **Full** |

### DOC-04: Documentation Generation

| ID | Requirement | Implementation |
| :--- | :--- | :--- |
| `DOC-04-01` | Boilerplate Insertion | `LuaDocGenerator` creates `Template` for functions and class tables |
| `DOC-04-02` | Parameter Extraction | `LuaPsiImplUtil.getParameters` extracts names including varargs |
| `DOC-04-03` | Template Editing | `TemplateManager` starts interactive session with tab stops |
| `DOC-04-04` | Type Inference (Basic) | `inferTypeByName` maps common names to types (e.g., `count` -> `number`) |
| `DOC-04-05` | Return Tag Detection | `hasReturnStatement` scans function block for return statements |

### DOC-05: Markdown Support

| ID | Requirement | Implementation | Status |
| :--- | :--- | :--- | :--- |
| `DOC-05-01` | Markdown Rendering | `org.intellij.markdown` | **Full** |
| `DOC-05-02` | Syntax Highlighting in Code Blocks | `LuaDocumentationRenderer.highlightLuaCode` | **Full** |
| `DOC-05-03` | Escape Character Support | Supported via Markdown parser | **Full** |
| `DOC-05-04` | Paragraph Handling | Supported via Markdown parser | **Full** |

### DOC-06: Documentation Indexing

| ID          | Requirement                                                                                                                 | Implementation                                                                                                                                                                                             |
| :---------- |:----------------------------------------------------------------------------------------------------------------------------| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DOC-06-01` | [Stub Indexing](06-documentation-indexing/design-stub-indexing.md)                                    | Indexed `@class`/`@alias` names from `LuaLocalVarStub`, and global/local functions with LuaCATS metadata via `LuaFuncStub` and `LuaLocalFuncStub`                                                          |
| `DOC-06-02` | [Type Map Construction](06-documentation-indexing/design-type-map.md)                    | Object-oriented type system with dynamic stub materialization, class merging, and inheritance; `TypeParser` fully implemented including function signatures |
| `DOC-06-03` | Incremental Updates                                                                                                         | IntelliJ stub infrastructure handles this automatically                                                                                                                                                    |
| `DOC-06-04` | Full-Text Search                                                                                                            | `LuaDocSearchEverywhereContributor`                                                                                                                                                                        |
| `DOC-06-05` | Cross-File Resolution                                                                                                       | General bindings via `LuaFileBindingsIndex`; class, alias, and function stubs via `StubIndex`                                                                                                              |
| `DOC-06-06` | [Platform Symbol Documentation](spec/documentation/06-documentation-indexing/platform-symbol-documentation/requirements.md) | Lookup and display documentation for symbols from platform/library files (e.g., Lua standard library)                                                                                                      |

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
