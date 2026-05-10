# Specification: SYNTAX-07 Inlay Hints

This document defines the requirements for inlay hints in the Lunar editor, which surface implicit information directly in the code editor without modifying the source text.

## 1. Scope

This specification applies to inlay hints for Lua files. Inlay hints are small, non-interactive text annotations rendered inline by the IDE alongside source code. They expose information that is implicit in the code — such as inferred types, parameter names at call sites, and return types — to improve readability without requiring the user to hover or navigate away.

Hints are produced by an `InlayHintsProvider` implementation registered with the IntelliJ Platform and are updated incrementally as the file is edited.

## 2. Hint Categories

### 2.1 Type Hints for Local Variables

When a local variable's type can be inferred from its initializer, the inferred type is shown immediately after the variable name.

**Trigger**: A `local` declaration with an initializer whose type can be determined statically.

**Format**: `: <type>` appended after the variable name.

**Sources of type information** (in descending priority):
1. An explicit `---@type <T>` LuaCATS annotation on the preceding line.
2. A literal initializer (`string`, `number`, `integer`, `boolean`, `nil`).
3. A table constructor (`{}`), inferred as `table`.
4. A function expression, inferred as `function`.
5. A call to a stdlib function with a known return type (e.g., `io.open` → `file?`).
6. A call to a user-defined function annotated with `---@return`.

**Examples**:
```lua
local name = "Alice"        -- hint: : string
local count = 42            -- hint: : integer
local ratio = 3.14          -- hint: : number
local flag = true           -- hint: : boolean
local data = {}             -- hint: : table
local fn = function() end   -- hint: : function

---@return string
local function greet() return "hi" end
local msg = greet()         -- hint: : string
```

**No hint** when the type cannot be determined (e.g., result of an untyped function call, a global reference, or `...`).

### 2.2 Parameter Name Hints at Call Sites

When a function is called with positional arguments, parameter names from the function's definition (or LuaCATS `---@param` annotations) are shown before each argument.

**Trigger**: A function call expression where the called function is locally resolvable and has named parameters.

**Format**: `<param>:` prepended before each argument value.

**Examples**:
```lua
local function move(x, y, speed) end
move(10, 20, 5)
-- hints: move(x: 10, y: 20, speed: 5)

string.rep("ha", 3)
-- hints: string.rep(s: "ha", n: 3)
```

**Suppression rules** — hints are **not** shown when:
- The argument is already a named variable whose name matches the parameter (e.g., `move(x, y, speed)` — no noise).
- The function has only one parameter.
- The call uses a method-style colon call (the implicit `self` argument is always suppressed).
- The parameter name is a single letter or is `_` (considered non-descriptive).
- The function has no resolvable definition or LuaCATS annotations.

### 2.3 Return Type Hints for Function Definitions

When a function's return type(s) can be inferred but are not explicitly annotated, the inferred type(s) are shown after the closing `)` of the parameter list.

**Trigger**: A function definition (local, global, or method) without an explicit `---@return` annotation where at least one `return` statement returns a literal value or a typed expression.

**Format**: `: <type>` (or `: <type1>, <type2>` for multiple returns) shown after the parameter list closing `)`.

**Examples**:
```lua
local function double(n)    -- hint: : number
    return n * 2
end

local function pair()       -- hint: : integer, string
    return 1, "one"
end
```

**No hint** when:
- An explicit `---@return` annotation is present (the annotation itself is already visible).
- The function has no `return` statement, or all returns are empty (`return`).
- The return expression(s) cannot be typed (e.g., untyped function calls).

### 2.4 Method Chaining Return Type Hints

When method calls are chained across multiple lines, the inferred return type is shown at the end of each intermediate method call expression.

**Trigger**: A method call expression (using `:`) where the return type is known and the call is not the final expression in a statement.

**Format**: `: <type>` shown at the end of the method call line.

**Examples**:
```lua
local result = obj:method1()        -- hint: : MyClass
  :method2()                        -- hint: : MyClass
  :method3()                        -- hint: : MyClass
  :getValue()                       -- hint: : string
```

**No hint** when:
- An explicit `---@return` annotation is present on the method definition.
- The method's return type cannot be determined.
- The chain is collapsed onto a single line (return type is often self-evident in compact form).

## 3. Configuration

All hint categories must be individually configurable. Users must be able to toggle each category on or off from the IDE settings:

| Setting | Location | Default |
| :--- | :--- | :---: |
| Show type hints for local variables | Settings → Editor → Inlay Hints → Types → Lua | On |
| Show parameter name hints at call sites | Settings → Editor → Inlay Hints → Parameters → Lua | On |
| Show return type hints for functions | Settings → Editor → Inlay Hints → Types → Lua | Off |
| Show method chaining return type hints | Settings → Editor → Inlay Hints → Method Chaining → Lua | On |

The **return type hints** and **method chaining hints** default to off/on respectively to balance clarity with visual density.

## 4. Interaction with LuaCATS Annotations

When a LuaCATS annotation is already present, the corresponding hint **must not** be shown to avoid redundancy:

| Annotation | Suppresses |
| :--- | :--- |
| `---@type T` | Type hint on the variable below |
| `---@param name T` | Parameter name hint for that parameter |
| `---@return T` | Return type hint for the function and method chain calls |

## 5. Presentation

- Hints must use the IDE's built-in inlay hint text attributes (`InlayHintsColors` / `DefaultLanguageHighlighterColors.INLAY_DEFAULT`).
- Hints must not affect the document character count, cursor movement, or clipboard operations.
- Hints must be rendered inline (not as tooltips or popups).
- Colons and separators within hints should use a dimmer style than the type name itself where possible.

## 6. Performance

- The hints provider must run in a **background thread** (via `InlayHintsProvider.getCollectorFor`); it must never block the EDT.
- The provider must use the **PSI tree** as its source of truth; it must not reparse the file.
- Hints should be incremental — only recalculated for changed regions when possible.
- Avoid resolving types for files larger than a configurable threshold (default: 10,000 lines) to prevent degraded performance on generated or minified files.

## 7. Requirements & Implementation Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `SYNTAX-07-01` | **Local Variable Type Hints** | **M** | Pending | Show inferred types for `local` variable declarations. |
| `SYNTAX-07-02` | **Literal Type Inference** | **M** | Pending | Infer `string`, `number`, `integer`, `boolean`, `nil`, `table`, `function` from literal initializers. |
| `SYNTAX-07-03` | **LuaCATS Type Inference** | **S** | Pending | Use `---@return` and `---@type` annotations to infer types for hints. |
| `SYNTAX-07-04` | **Parameter Name Hints** | **S** | Pending | Show parameter names at call sites for locally resolved functions. |
| `SYNTAX-07-05` | **Parameter Hint Suppression** | **S** | Pending | Suppress hints when argument name matches parameter, or parameter is single-char / `_`. |
| `SYNTAX-07-06` | **Return Type Hints** | **C** | Pending | Show inferred return types after function parameter lists. |
| `SYNTAX-07-07` | **Method Chaining Hints** | **S** | Pending | Show return types for intermediate method calls in a fluent chain. |
| `SYNTAX-07-08` | **Annotation Suppression** | **M** | Pending | Do not show a hint when the corresponding LuaCATS annotation is already present. |
| `SYNTAX-07-09` | **Per-Category Settings** | **M** | Pending | Expose toggles for each hint category under respective settings paths. |
| `SYNTAX-07-10` | **Background Execution** | **M** | Pending | Compute all hints off the EDT to ensure editor responsiveness. |
| `SYNTAX-07-11` | **Large File Threshold** | **C** | Pending | Skip hint computation for files exceeding the configurable line-count threshold. |

## 8. Test Cases

### Type Hints — Literals
```lua
local s = "hello"      -- expected hint: : string
local n = 100          -- expected hint: : integer
local f = 1.5          -- expected hint: : number
local b = false        -- expected hint: : boolean
local t = {}           -- expected hint: : table
local fn = function()  -- expected hint: : function
end
```

### Type Hints — LuaCATS Annotated Return
```lua
---@return boolean
local function isReady() return true end
local ready = isReady()  -- expected hint: : boolean
```

### Type Hints — No Hint Cases
```lua
local x = someGlobal()  -- no hint: unresolvable call
local y                 -- no hint: no initializer

---@type number
local z = compute()     -- no hint: annotation already present
```

### Parameter Name Hints
```lua
local function createUser(name, age, admin) end
createUser("Bob", 30, false)
-- expected hints: createUser(name: "Bob", age: 30, admin: false)
```

### Parameter Hint — Suppressed (matching name)
```lua
local name = "Alice"
local age = 25
createUser(name, age, false)
-- expected hints: createUser(name, age, admin: false)
-- 'name' and 'age' suppressed; 'admin' shown because arg name differs
```

### Parameter Hint — Suppressed (single parameter)
```lua
local function print_value(x) end
print_value(42)  -- no hint: single parameter
```

### Method Chaining — Basic
```lua
---@return MyClass
local function MyClass:method1() return self end

---@return string
local function MyClass:getValue() return "result" end

local result = obj:method1()    -- expected hint: : MyClass
  :getValue()                   -- expected hint: : string
```

### Method Chaining — Multiple Returns
```lua
---@return Request
local function Request:param(key, value) return self end

local req = Request.new()
  :param("foo", "bar")          -- expected hint: : Request
  :param("baz", "qux")          -- expected hint: : Request
  :send()                        -- expected hint: : Response
```

### Method Chaining — No Hint (annotated)
```lua
---@return MyClass
local function MyClass:method() return self end

local result = obj:method()     -- no hint: annotation already present
```

