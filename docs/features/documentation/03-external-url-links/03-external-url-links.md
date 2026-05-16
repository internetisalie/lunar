---
folders:
  - "[[features/documentation/requirements|requirements]]"
priority: medium
status: done
vf_icon: ✅
title: "03: External URL Links"
---
# Specification: DOC-03 External URL Links

This document defines the requirements for supporting external URL links within Lua documentation in the Lunar editor.

## 1. Scope

External URL links allow users to navigate from the editor's documentation popups to web-based documentation (e.g., official Lua manual, library websites).

## 2. Link Types

### 2.1 Explicit Markdown Links
Links provided using standard Markdown syntax in comments.
- **Syntax**: `[Link Text](http://example.com)`

### 2.2 Plain URLs
Automatically detected URLs within comment text.
- **Syntax**: `http://example.com`, `https://lua.org`

### 2.3 `@see` Tag with URL
The `@see` tag can be used to point to an external resource.
- **Syntax**: `--- @see http://www.lua.org/manual/5.4/`

## 3. Editor Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DOC-03-01` | **URL Detection** | **M** | **Full** | Identify URLs within Lua comments and Quick Doc popups. |
| `DOC-03-02` | **Clickable Links** | **M** | **Full** | Render URLs as clickable hyperlinks in the Quick Documentation popup. |
| `DOC-03-03` | **Default Browser Integration** | **M** | **Full** | Open the system's default web browser when a link is clicked. |
| `DOC-03-04` | **Markdown Link Rendering** | **S** | **Full** | Support `[label](url)` syntax for descriptive links. |
| `DOC-03-05` | **Lua Standard Library Links** | **S** | **Full** | Provide automatic links to the official Lua manual for standard library functions (e.g., `print`, `table.insert`). |

## 4. Examples

### Example: Standard Library Documentation
```lua
--- @see http://www.lua.org/manual/5.4/manual.html#pdf-print
function print(...) end
```
**Expected Popup:**
- **See Also**: [http://www.lua.org/manual/5.4/manual.html#pdf-print](http://www.lua.org/manual/5.4/manual.html#pdf-print)

### Example: Markdown Link in Description
```lua
--- This function uses the [Bit32 library](http://www.lua.org/manual/5.2/manual.html#6.7).
function manipulate_bits() end
```
**Expected Popup:**
- **Description**: This function uses the [Bit32 library](http://www.lua.org/manual/5.2/manual.html#6.7). (with "Bit32 library" being clickable)
