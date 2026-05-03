# Formatting Requirements (`FORMAT`)

Lunar provides a highly configurable code formatter to ensure consistency across Lua projects.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `FORMAT-01` | **Basic Indentation** | **M** | **Full** | Correctly indent blocks (`function`, `if`, `while`, etc.) and align table fields. |
| `FORMAT-02` | **Configurable Code Style** | **M** | **Full** | Provide a settings UI to configure tab size, use of tabs/spaces, and placement of spaces. |
| `FORMAT-03` | **Blank Line Management** | **S** | **Not Implemented** | Automatically insert or remove blank lines between functions and at the end of files. |
| `FORMAT-04` | **Expression Wrapping** | **S** | **Not Implemented** | Wrap long expressions, function arguments, and table constructors based on a configurable right margin. |
| `FORMAT-05` | **Alignment Logic** | **S** | **Not Implemented** | Align consecutive assignments and table field keys for better readability. |
| `FORMAT-06` | **Comment Formatting** | **C** | **Future Work** | Wrap and align long comments based on code style settings. |
| `FORMAT-07` | **Stylua Compatibility** | **S** | **Not Implemented** | Review and optionally align formatting rules with `stylua`. |

---

## Detailed Implementation Status

### FORMAT-01: Basic Indentation
- **Status**: **Implemented** (`LuaFormattingModelBuilder`)

### FORMAT-02: Configurable Code Style
- **Status**: **Implemented** (`LuaCodeStyleSettingsProvider`, `LuaLanguageCodeStyleSettingsProvider`)

