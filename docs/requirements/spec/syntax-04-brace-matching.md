# Specification: SYNTAX-04 Brace Matching

This document defines the requirements for brace matching and structural highlighting in the Lunar editor, helping users identify matching pairs of punctuation and keywords.

## 1. Scope

This specification applies to standard Lua punctuation pairs and structural keywords used for block delimitation.

## 2. Supported Pairs

### 2.1 Punctuation Pairs
Standard paired delimiters used in expressions, table constructors, and function definitions.
- **Parentheses**: `(` and `)`
- **Brackets**: `[` and `]`
- **Curly Braces**: `{` and `}`

### 2.2 Keyword Pairs (Structural)
Lua uses keywords to define blocks. These should be matched visually to assist with navigation and structural integrity.
- `do` ... `end`
- `if` ... `end` (including intermediate `then`, `elseif`, `else`)
- `while` ... `do` ... `end`
- `for` ... `do` ... `end`
- `repeat` ... `until`
- `function` ... `end`

## 3. Editor Behavior

### 3.1 Highlighting
When the cursor is placed immediately before or after one half of a pair, the editor should:
- Highlight the matching delimiter.
- Provide a "mismatched brace" warning (usually a red highlight) if a closing delimiter does not have a corresponding opening delimiter.

### 3.2 Navigation
Users should be able to jump between matching delimiters using the standard IDE shortcut (e.g., `Ctrl + ]` or `Ctrl + M`).

### 3.3 Selection
The editor should support "Select to matching brace" or "Expand selection" based on these structural boundaries.

## 4. Requirements

| ID | Requirement | Priority | Description |
| :--- | :--- | :---: | :--- |
| `SYNTAX-04-01` | **Punctuation Matching** | **M** | Match `()`, `[]`, and `{}` using `PairedBraceMatcher`. |
| `SYNTAX-04-02` | **Keyword Matching** | **M** | Match structural keywords (e.g., `if`...`end`) using `CodeBlockSupportHandler`. |
| `SYNTAX-04-03` | **Nested Matching** | **M** | Correctly identify the innermost matching pair when multiple pairs are nested. |
| `SYNTAX-04-04` | **Error Highlighting** | **S** | Highlight unmatched closing braces or keywords to indicate syntax errors. |
| `SYNTAX-04-05` | **Jump to Match** | **S** | Support jumping between matching pairs via IDE navigation shortcuts. |

## 5. Test Cases

### Test Case: Simple Parentheses
```lua
print( (1 + 2) * 3 )
```
*Expected*: 
- Placing cursor at `(` highlights the corresponding `)`.
- Correctly differentiates between nested pairs.

### Test Case: Table Braces
```lua
local t = { a = 1, b = { 2 } }
```
*Expected*: Matches the inner `{ 2 }` separately from the outer table.

### Test Case: If-Then-Else-End
```lua
if true then
    print("hi")
else
    print("bye")
end
```
*Expected*: `if`, `then`, `else`, and `end` are recognized as a structural set.

### Test Case: Repeat-Until
```lua
repeat
    x = x + 1
until x > 10
```
*Expected*: `repeat` matches with `until`.

## 6. Implementation Notes

### IntelliJ Platform Integration
- **Punctuation**: Implement `PairedBraceMatcher`. Register via `com.intellij.lang.braceMatcher`.
- **Keywords**: Use `AbstractCodeBlockSupportHandler` (already implemented as `LuaCodeBlockSupportHandler`).
- **Token Types**: Use types defined in `LuaElementTypes`.

### Edge Cases
- **Escaped Strings**: Do not attempt to match braces inside string literals or comments.
- **Attributes**: In Lua 5.4, `<const>` uses angle brackets. These are **not** part of standard brace matching to avoid interference with comparison operators.
