---
folders:
  - "[[features/syntax/requirements|requirements]]"
title: "Parsing Test Cases"
---

# Exhaustive Lua Parsing Tests Collection

This document collects Lua parsing test cases from various sources to be used for generating tests for Lunar.

## Sources
- **Official Lua Test Suite**: `errors.lua`, `literals.lua`, `constructs.lua`, `attrib.lua`.
- **Luacheck**: `spec/parser_spec.lua`, `spec/lexer_spec.lua`.
- **Lunar Current Tests**: `TestLuaAttributesParser.kt`.
- **IntelliJ-EmmyLua / Luanalysis**: `ParsingTest.kt`.

---

## 1. Statements

### 1.1 Assignments
| Code | Notes |
| :--- | :--- |
| `a = 1` | Simple assignment |
| `a, b = 1, 2` | Multiple assignment |
| `a.x, b[1] = 1, 2` | Complex assignment |
| `a, b = f()` | Assignment from function return |
| `(f()).x = 1` | Assignment to expression result (if it returns a table) |
| `a = 1; b = 2` | Semicolon separator |
| `a = 1 b = 2` | No separator (valid) |

### 1.2 Local Declarations
| Code | Notes |
| :--- | :--- |
| `local a` | Uninitialized local |
| `local a = 1` | Initialized local |
| `local a, b = 1, 2` | Multiple locals |
| `local x <const> = 10` | Lua 5.4 attribute |
| `local f <close> = io.open('t')` | Lua 5.4 attribute |
| `local function f() end` | Local function |

### 1.3 Control Flow
| Code | Notes |
| :--- | :--- |
| `if true then end` | Simple if |
| `if true then elseif false then else end` | Full if-elseif-else |
| `while true do break end` | While loop with break |
| `repeat until true` | Repeat loop |
| `for i=1,10 do end` | Numeric for |
| `for i=1,10,2 do end` | Numeric for with step |
| `for k,v in pairs(t) do end` | Generic for |
| `do end` | Block |
| `::label:: goto label` | Goto and label |

---

## 2. Expressions

### 2.1 Literals
| Code | Notes |
| :--- | :--- |
| `nil`, `true`, `false` | Keywords |
| `123`, `0xff`, `0.1e-2` | Numbers |
| `'str'`, `"str"`, `[[long str]]` | Strings |
| `...` | Vararg |
| `function() end` | Anonymous function |

### 2.2 Table Constructors
| Code | Notes |
| :--- | :--- |
| `{}` | Empty table |
| `{1, 2, 3}` | List-like |
| `{a = 1, b = 2}` | Record-like |
| `{["a"] = 1, [1+1] = 2}` | General keys |
| `{1, 2; a = 3, b = 4,}` | Mixed with semicolons and trailing comma |

### 2.3 Operators & Precedence
| Code | Notes |
| :--- | :--- |
| `-1 + 2 * 3 ^ 4` | Arithmetic precedence |
| `not a or b and c` | Logical precedence |
| `a .. b .. c` | Concat right-associativity |
| `a ^ b ^ c` | Pow right-associativity |
| `1 << 2 | 3 >> 1` | Bitwise operators |
| `5 // 2` | Integer division |

---

## 3. Lexical Edge Cases

### 3.1 String Escapes
- `"\n\r\t\"\'\\"`
- `"\xAF\x00"` (Hex)
- `"\123"` (Decimal)
- `"\u{1234}"` (UTF-8, Lua 5.3+)
- `"\z\n  next line"` (Zap, Lua 5.2+)

### 3.2 Long Brackets
- `[[ multi-line ]]`
- `[=[ nested [[]] ]=]`
- `[==[ [=[ ]=] ]==]`

### 3.3 Newline Ambiguities
- `a = b \n (c):d()`: Is it `a = b(c):d()` or `a = b; (c):d()`? (Lua rules: it's one statement if it *can* be).
- `return \n 1`: Returns `nil` then has a statement `1` (invalid) or returns `1`? (Lua manual: `return` can take an optional expression list).

---

## 4. Error Cases (Invalid Code)

| Code | Expected Error |
| :--- | :--- |
| `local a = {4` | `expected '}'` |
| `function a(, ...) end` | `expected <name>` |
| `while << do end` | `syntax error` |
| `break fail` | `expected '='` (if interpreted as assignment) or syntax error |
| `return break` | `expected expression` |
| `local x <XXX> = 1` | `unknown attribute` (Semantic, but often caught by parser) |
| `a:b` | `expected '('` (Missing call args) |

---

## 5. Mailing List & Forum Highlights

### 5.1 The "Ambiguous Statement" Problem
```lua
a = b
(f or g)(x)
```
In Lua, this is parsed as `a = b(f or g)(x)`. To separate them, a semicolon is required:
```lua
a = b;
(f or g)(x)
```

### 5.2 `return` Position
In Lua 5.1, `return` must be the last statement in a block. In 5.2+, it can be followed by semicolons but still must be at the end of the block.
Invalid in 5.1:
```lua
function f()
  return 1
  print("hi")
end
```

### 5.3 Empty Blocks
Valid but sometimes tricky for simple parsers:
```lua
while true do end
if a then else end
```

### 5.4 Labels and Scope
Labels are visible in the entire block where they are defined.
```lua
::redo::
goto redo
```
Invalid (goto into inner scope):
```lua
goto inner
do
  ::inner::
end
```
