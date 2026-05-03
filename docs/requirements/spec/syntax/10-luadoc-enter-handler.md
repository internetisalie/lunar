# Specification: SYNTAX-10 Enter Handler for Comments

This document defines the requirements for the Enter handler inside LuaDOC comments in the Lunar editor, which enhances the documentation authoring experience by automatically continuing comment blocks.

## 1. Scope

This specification applies specifically to LuaDOC comment blocks, which begin with `---`. It defines the editor's behavior when the user presses the `Enter` key within or at the end of these blocks. Standard Lua comments starting with `--` may be excluded from this specific behavior depending on IDE configuration, but `---` must be strictly handled.

## 2. Editor Behavior

### 2.1 Auto-continuation
When the cursor is positioned within a LuaDOC line (e.g., `--- Some documentation`), pressing `Enter` should automatically insert a new line that is prefixed with `--- ` (including the trailing space), preserving the indentation of the previous line.

### 2.2 Line Splitting
If the user presses `Enter` in the middle of a text segment inside a LuaDOC line, the editor must split the text. The text after the cursor should be moved to the newly created line, and that new line must be properly prefixed with `--- `.

### 2.3 Block Termination (Empty Line)
To allow users to easily exit a documentation block, pressing `Enter` on an empty LuaDOC line (a line containing only `---` or `--- `) should terminate the block. The prefix on the empty line should ideally be cleared, or the new line should not be prefixed, matching standard JetBrains IDE behavior for JavaDoc/KDoc.

## 3. Implementation Status

| ID | Requirement | Status |
| :--- | :--- | :--- |
| `SYNTAX-10-01` | **Auto-continue Prefix** | **Implemented** |
| `SYNTAX-10-02` | **Split Text** | **Implemented** |
| `SYNTAX-10-03` | **Block Termination** | **Implemented** |

---

## 4. Requirements Details

## 4. Test Cases

### Test Case: Simple Auto-continuation
```lua
--- This is a documentation block|
```
*(where `|` is the cursor)*
*Expected*: Pressing Enter creates a new line with the same indentation, starting with `--- `.
```lua
--- This is a documentation block
--- |
```

### Test Case: Line Splitting
```lua
--- This is some |text
```
*(where `|` is the cursor)*
*Expected*: Pressing Enter splits the text.
```lua
--- This is some 
--- |text
```

### Test Case: Block Termination
```lua
--- This is a documentation block
--- |
```
*(where `|` is the cursor)*
*Expected*: Pressing Enter on the empty LuaDOC line removes the `--- ` prefix on the current line so the user can continue typing normal Lua code.
```lua
--- This is a documentation block

|
```

### Test Case: Simple Comment Suppression
```lua
-- Single line comment|
```
*(where `|` is the cursor)*
*Expected*: Pressing Enter starts a new line WITHOUT a `-- ` prefix.
```lua
-- Single line comment
|
```

### Test Case: Indentation Preservation
```lua
    --- Nested documentation|
```
*(where `|` is the cursor, 4 spaces indentation)*
*Expected*: Pressing Enter creates a new line with the same 4-space indentation and the `--- ` prefix.
```lua
    --- Nested documentation
    --- |
```

## 6. Identified Issues with Existing Simple Comment Continuation

The current `LuaEnterHandlerDelegate` provides basic auto-continuation for standard Lua comments (`--`), but it has several limitations that must be addressed to meet professional standards:

- **Over-triggering**: Auto-continuation currently triggers for all `PsiComment` types, including short, single-line comments where continuation is undesirable.
- **Indentation Fragility**: The current implementation uses `insertStringAtCaret` without calculating or maintaining the correct indentation level relative to the previous line.
- **Lack of Termination Logic**: There is no logic to allow users to "break out" of a comment block gracefully (e.g., when pressing Enter on an empty comment line).
- **Style Inconsistency**: The handler does not distinguish between standard `--` comments and documentation-style `---` comments, applying the same aggressive behavior to both.

### Required Improvements
1. **Context-Aware Triggering**: Implement logic to only auto-continue if the comment block spans multiple lines or explicitly uses a documentation marker (e.g., `---`).
2. **Indentation Preservation**: Use proper editor action utilities to ensure the new line's indentation matches the previous line.
3. **Graceful Exit**: Detect empty comment lines (lines containing only the marker) to stop auto-continuation and return to normal code insertion.
