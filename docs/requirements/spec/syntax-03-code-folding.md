# Specification: SYNTAX-03 Code Folding

This document defines the requirements for code folding in the Lunar editor, allowing users to collapse and expand sections of Lua code for better navigation and readability.

## 1. Scope

This specification applies to structural blocks and multi-line literals in Lua files, including standard Lua 5.1-5.4 syntax.

## 2. Foldable Regions

The following regions must be foldable:

### 2.1 Control Structures
Regions starting with a block-initiating keyword and ending with `end`.
- **Functions**: `function ... end`, `local function ... end`
- **Do Blocks**: `do ... end`
- **If Statements**: `if ... then ... [elseif ... then ...] [else ...] end`
  - Each `if`, `elseif`, and `else` block should be individually foldable or the entire `if` block can be folded.
- **While Loops**: `while ... do ... end`
- **For Loops**: `for ... do ... end`
- **Repeat Loops**: `repeat ... until ...` (folded from `repeat` to `until`)

### 2.2 Table Constructors
Multi-line table definitions using braces `{}`.
- **Syntax**: 
  ```lua
  local t = {
      key = "value",
      ...
  }
  ```

### 2.3 Comments
- **Multi-line Comments**: `[[ ... ]]` with any number of equals signs (e.g., `--[[ ... ]]`, `--[=[ ... ]=]`).
- **Documentation Comments**: Sequential blocks of documentation comments (e.g., LuaCats `---@` or `---`).

### 2.4 Multi-line Strings
- **Block Strings**: `[[ ... ]]` with any number of equals signs.

## 3. Folding Behavior

### 3.1 Placeholders
When a region is folded, a placeholder should be shown.
- **Default Placeholder**: `...`
- **Blocks**: Show the start of the block (e.g., `function(...) ... end`).
- **Tables**: `{...}`
- **Comments/Strings**: `[[...]]` or `--[[...]]`

### 3.2 Nested Folding
Regions within other foldable regions must be independently foldable.
- Folding a parent region collapses all children visually, but their individual fold states should be preserved when the parent is expanded.

### 3.3 Default State
By default, all regions should be expanded when a file is opened, except potentially for large file-header comments if configured by the user.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `SYNTAX-03-01` | **Function Folding** | **M** | Fold anonymous and named functions from header to `end`. |
| `SYNTAX-03-02` | **Block Folding** | **M** | Fold `do`, `while`, `for`, and `if` blocks. |
| `SYNTAX-03-03` | **Table Folding** | **M** | Fold multi-line table constructors `{ ... }`. |
| `SYNTAX-03-04` | **Multi-line Comment Folding** | **M** | Fold long comments `--[[ ... ]]`. |
| `SYNTAX-03-05` | **Block String Folding** | **S** | Fold multi-line string literals `[[ ... ]]`. |
| `SYNTAX-03-06` | **Doc Comment Grouping** | **S** | Fold sequential documentation comments (`---`) as a single unit. |
| `SYNTAX-03-07` | **Region Folding** | **C** | Support custom folding regions via `--#region` and `--#endregion`. |

## 5. Test Cases

### Test Case: Function Folding
```lua
function long_function()
    -- many lines
end
```
*Expected*: Foldable from `function` to `end`.

### Test Case: If-Else Folding
```lua
if condition then
    -- block 1
else
    -- block 2
end
```
*Expected*: The entire `if...end` block is foldable. Optionally, `if` and `else` blocks are separately foldable.

### Test Case: Nested Table
```lua
local config = {
    servers = {
        "alpha",
        "beta"
    },
    enabled = true
}
```
*Expected*: `config` table is foldable; `servers` sub-table is also foldable.

### Test Case: Multi-line Comment
```lua
--[[
    Line 1
    Line 2
]]
```
*Expected*: Foldable from `--[[` to `]]`.

## 6. Implementation Notes

### IntelliJ Platform Integration
- Implement `FoldingBuilderEx`.
- Use `buildFoldRegions` to traverse the PSI tree and identify foldable elements.
- Return `FoldingDescriptor` objects for each foldable region.
- Use `getPlaceholderText` to provide concise summaries for collapsed regions.

### Performance
- Folding building happens on the EDT for the visible area but is generally fast.
- Avoid heavy computations during PSI traversal. Use existing PSI structure.

### Edge Cases
- **Single-line blocks**: Do not provide folding for blocks that are already on a single line.
- **Empty blocks**: No folding needed for `function() end`.
- **Mismatched delimiters**: Handle cases where the parser has error elements (don't attempt to fold broken structures).
