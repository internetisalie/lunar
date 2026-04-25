# Formatting Requirements (`FORMAT`)

Lunar provides a highly configurable code formatter to ensure consistency across Lua projects.

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `FORMAT-01` | **Basic Indentation** | **M** | Correctly indent blocks (`function`, `if`, `while`, etc.) and align table fields. |
| `FORMAT-02` | **Configurable Code Style** | **M** | Provide a settings UI to configure tab size, use of tabs/spaces, and placement of spaces (e.g., around operators). |
| `FORMAT-03` | **Blank Line Management** | **S** | Automatically insert or remove blank lines between functions and at the end of files. |
| `FORMAT-04` | **Expression Wrapping** | **S** | Wrap long expressions, function arguments, and table constructors based on a configurable right margin. |
| `FORMAT-05` | **Alignment Logic** | **S** | Align consecutive assignments and table field keys for better readability. |
| `FORMAT-06` | **Comment Formatting** | **C** | Wrap and align long comments based on code style settings. |
| `FORMAT-07` | **Stylua Compatibility** | **S** | Review and optionally align formatting rules with `stylua` for consistency with other Lua projects. |
