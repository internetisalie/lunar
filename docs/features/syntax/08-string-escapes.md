---
id: "SYNTAX-08"
title: "08: String Escape Processing"
type: "feature"
parent_id: "SYNTAX"
status: "done"
priority: "medium"
folders:
  - "[[features/syntax/requirements|requirements]]"
---
# Specification: SYNTAX-08 String Escape Processing

This document defines the expected behavior for processing Lua escape sequences in string literals during syntax parsing and conversion.

## 1. Scope

This specification applies to:
- **Single-quoted strings**: `'hello\nworld'`
- **Double-quoted strings**: `"hello\nworld"`
- **NOT block strings**: `[[literal\ntext]]` (no escape processing)

## 2. Supported Escape Sequences

### 2.1 Single-Character Escapes
| Sequence | Unicode | Character Name | Example |
| :--- | :--- | :--- | :--- |
| `\a` | U+0007 | Alert (Bell) | `"alert\a"` → `alert<BEL>` |
| `\b` | U+0008 | Backspace | `"text\b"` → `text<BS>` |
| `\f` | U+000C | Form Feed | `"page\f"` → `page<FF>` |
| `\n` | U+000A | Line Feed (Newline) | `"line1\nline2"` |
| `\r` | U+000D | Carriage Return | `"text\r"` → `text<CR>` |
| `\t` | U+0009 | Horizontal Tab | `"col1\tcol2"` |
| `\v` | U+000B | Vertical Tab | `"text\v"` → `text<VT>` |
| `\\` | U+005C | Backslash | `"path\\file"` → `path\file` |
| `\"` | U+0022 | Double Quote | `"say \"hi\""` → `say "hi"` |
| `\'` | U+0027 | Single Quote | `'say \'hi\''` → `say 'hi'` |

### 2.2 Hex Escapes
**Syntax:** `\xHH` where `HH` is exactly 2 hexadecimal digits (0-9, a-f, A-F)

| Example | Result | Notes |
| :--- | :--- | :--- |
| `"\x41"` | `A` | ASCII 65 |
| `"\xff"` | `ÿ` | ASCII 255 (0xFF) |
| `"\x00"` | `<NUL>` | ASCII 0 |
| `"\xZZ"` | `\xZZ` | **Invalid** - treated as literal backslash + text |

**Constraints:**
- Must be exactly 2 hex digits
- Value range: `0x00` to `0xFF` (0-255)
- If invalid, treated as literal `\` followed by remaining characters

### 2.3 Decimal Escapes
**Syntax:** `\ddd` where `ddd` is 1-3 decimal digits (0-9)

| Example | Result | Notes |
| :--- | :--- | :--- |
| `"\65"` | `A` | ASCII 65 |
| `"\0"` | `<NUL>` | ASCII 0 (single digit) |
| `"\255"` | `ÿ` | ASCII 255 (maximum) |
| `"\256"` | `\256` | **Invalid** - out of range, treated as literal |

**Constraints:**
- Consumes 1-3 consecutive digits, maximum
- Value range: 0-255
- If value exceeds 255, treated as literal `\` followed by remaining characters

### 2.4 Unicode Escapes (Lua 5.3+)

#### Fixed 4-Digit: `\uHHHH`
**Syntax:** `\uHHHH` where `HHHH` is exactly 4 hexadecimal digits

| Example | Result | Notes |
| :--- | :--- | :--- |
| `"\u0041"` | `A` | ASCII 65 |
| `"\u00E9"` | `é` | Latin Small Letter E with Acute |
| `"\u0000"` | `<NUL>` | Null character |

#### Variable-Length: `\u{...}`
**Syntax:** `\u{HEX}` where `HEX` is 1-6 hexadecimal digits

| Example | Result | Notes |
| :--- | :--- | :--- |
| `"\u{41}"` | `A` | ASCII 65 |
| `"\u{1F600}"` | `😀` | Grinning Face emoji (U+1F600) |
| `"\u{10FFFF}"` | (max valid) | Highest valid Unicode code point |

**Constraints:**
- Value range: U+0000 to U+10FFFF (0 to 1,114,111)
- Characters outside BMP require surrogate pair representation in UTF-16
- If invalid, treated as literal `\` followed by remaining characters

## 3. Edge Cases and Error Handling

### 3.1 Invalid Escapes
- Unknown escape sequences (e.g., `\q`, `\@`) are treated as literal backslash followed by the character
- Example: `"invalid\q"` → `invalid\q`

### 3.2 Incomplete Escapes at String End
- Trailing backslash or incomplete escape at end of string is treated as literal
- Example: `"text\"` → syntax error (unterminated string)
- Example: `"text\x4"` → `text\x4` (only 1 hex digit, needs 2)

### 3.3 Multiple Consecutive Escapes
- Multiple escapes should be processed sequentially
- Example: `"\\n\\t"` → `\n\t` (two escapes: backslash, then n literal, then backslash, then t literal)
- Example: `"\n\t"` → `<LF><TAB>` (newline then tab)

## 4. Block String Behavior

Block strings use `[[` and `]]` delimiters (with optional `=` levels for nesting).

- **No escape processing**: `[[hello\nworld]]` → `hello\nworld` (literal backslash-n)
- **Newline trimming**: Leading newline after opening `[[` is stripped
  - `[[`<br>`hello]]` → `hello`
  - `[[hello]]` → `hello`

## 5. Functional Requirements

| ID | Requirement | Priority |
| :--- | :--- | :---: |
| `SYNTAX-08-01` | Process all 10 single-character escapes | **M** |
| `SYNTAX-08-02` | Process hex escapes (`\xHH`, 0-255) | **M** |
| `SYNTAX-08-03` | Process decimal escapes (`\ddd`, 0-255) | **M** |
| `SYNTAX-08-04` | Process fixed-length Unicode (`\uHHHH`) | **M** |
| `SYNTAX-08-05` | Process variable-length Unicode (`\u{...}`, U+0-U+10FFFF) | **M** |
| `SYNTAX-08-06` | Handle invalid escapes as literals | **M** |
| `SYNTAX-08-07` | Apply escapes to quoted strings only | **M** |
| `SYNTAX-08-08` | Skip escape processing for block strings | **M** |
| `SYNTAX-08-09` | Use table-driven approach for simple escapes | **S** |

## 6. Test Cases

### Test Case: Basic Single-Character Escapes
```lua
assert("\n" == string.char(10))
assert("\t" == string.char(9))
assert("\\" == "\\")
assert("\"" == '"')
assert("\'" == "'")
```

### Test Case: Hex Escapes
```lua
assert("\x41" == "A")
assert("\xff" == "\255")
assert("\x00\x01" == string.char(0, 1))
```

### Test Case: Decimal Escapes
```lua
assert("\65" == "A")
assert("\0" == string.char(0))
assert("\255" == "\xff")
```

### Test Case: Unicode Escapes
```lua
assert("\u0041" == "A")
assert("\u{41}" == "A")
assert("\u{1F600}" == "😀")
```

### Test Case: Block Strings (No Processing)
```lua
assert("[[hello\nworld]]" == "hello\\nworld")  -- literal backslash
assert("[[\nhello]]" == "hello")                -- leading newline trimmed
```

### Test Case: Invalid Escapes
```lua
-- Unknown escape treated as literal
assert("\q" == "\\q" or processedValue == "\\q")
```

## 7. Implementation Notes

### Approach: Table-Driven for Simple Escapes
- Use a map/table for the 10 single-character escapes to reduce code verbosity
- Handle complex escapes (hex, decimal, unicode) with explicit parsing logic
- Validate bounds on numeric escapes (0-255 for hex/decimal, 0-0x10FFFF for unicode)
- Use `Character.toChars()` for proper surrogate pair handling of characters outside BMP

### Integration Points
- `extractLuaString()` in `LuaLiterals.kt` is the main entry point
- Called by folding builders, bindings visitors, and other syntax analysis components
- Must not process block strings (`[[...]]`)

### Performance Considerations
- String processing is linear O(n) in the string length
- Table lookup for simple escapes is O(1)
- No precompilation or caching needed as strings are typically small
