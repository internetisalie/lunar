# Specification: DOC-05 Markdown Support

This document defines the requirements for supporting Markdown formatting within Lua documentation in the Lunar editor.

## 1. Scope

Markdown support allows developers to use standard Markdown syntax within Lua comments to create rich, well-formatted documentation that is rendered in the Quick Documentation popup.

## 2. Supported Markdown Features

The following CommonMark-compatible features should be supported:

### 2.1 Basic Formatting
- **Bold**: `**text**` or `__text__`
- **Italic**: `*text*` or `_text_`
- **Strikethrough**: `~~text~~`

### 2.2 Lists
- **Unordered Lists**: Using `-`, `*`, or `+`.
- **Ordered Lists**: Using `1.`, `2.`, etc.

### 2.3 Code Elements
- **Inline Code**: `` `code` ``
- **Code Blocks**:
  ```
  ```lua
  local x = 10
  ```
  ```

### 2.4 Headers
- Using `#`, `##`, etc. (rendered appropriately within the popup).

### 2.5 Blockquotes
- Using `>`.

## 3. Editor Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DOC-05-01` | **Markdown Rendering** | **M** | **Full** | Correctly parse and render Markdown syntax in the Quick Documentation popup. |
| `DOC-05-02` | **Syntax Highlighting in Code Blocks** | **S** | **Full** | Provide syntax highlighting for Lua code blocks within documentation. |
| `DOC-05-03` | **Escape Character Support** | **M** | **Full** | Correctly handle escaped characters (e.g., `\@param` should not be treated as a tag). |
| `DOC-05-04` | **Paragraph Handling** | **M** | **Full** | Properly preserve line breaks and paragraphs as defined in Markdown. |

## 4. Examples

### Example: Rich Description
```lua
--- This function performs a **complex** calculation.
--- 
--- ### Usage
--- 1. Initialize the system.
--- 2. Call this function with a `config` table.
--- 
--- > Note: This is an experimental feature.
function do_magic(config) end
```

**Expected Popup Rendering:**
- This function performs a **complex** calculation.
- **Usage**
  1. Initialize the system.
  2. Call this function with a `config` table.
- > Note: This is an experimental feature.
