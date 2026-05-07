# End-User Walkthroughs: TYPE Epic Verification

This document provides step-by-step walkthroughs to verify the implementation of the **TYPE** epic in the Lunar plugin.

---

## Walkthrough 1: Basic Type Inference (`TYPE-01`)
**Goal**: Verify that Lunar infers types from literals and propagates them through assignments.

1.  **Open** a new Lua file.
2.  **Type** the following code:
    ```lua
    local name = "Lunar"
    local version = 1.0
    local is_active = true
    
    local another_name = name
    ```
3.  **Verify Inlay Hints**:
    *   `name` should show `: string`.
    *   `version` should show `: number`.
    *   `is_active` should show `: boolean`.
    *   `another_name` should show `: string`.
4.  **Type** a multiple assignment:
    ```lua
    local a, b = 10, "hello"
    ```
5.  **Verify**: `a` shows `: number`, `b` shows `: string`.

---

## Walkthrough 2: Class and Table Definitions (`TYPE-02`)
**Goal**: Verify that `@class` and `@field` annotations define structured types.

1.  **Type** a class definition:
    ```lua
    ---@class User
    ---@field id number
    ---@field username string
    local User = {}
    ```
2.  **Type** a variable using that class:
    ```lua
    ---@type User
    local admin = { id = 1, username = "root" }
    
    local name = admin.username
    ```
3.  **Verify**:
    *   `admin` shows **no inlay hint** (suppressed by explicit `@type`).
    *   `name` should show `: string` (inferred from `User.username`).
4.  **Type** an assignment to a new field:
    ```lua
    admin.email = "admin@example.com"
    local mail = admin.email
    ```
5.  **Verify**: `mail` should show `: string` (if `TYPE-02-05` implicit discovery is active).

---

## Walkthrough 3: Function Signature Matching (`TYPE-03`)
**Goal**: Verify call-site validation against `@param` signatures.

1.  **Type** an annotated function:
    ```lua
    ---@param count number
    ---@param msg string
    local function log(count, msg) end
    ```
2.  **Call** the function correctly:
    ```lua
    log(5, "starting") -- OK
    ```
3.  **Call** with mismatched types:
    ```lua
    log("many", 10)
    ```
4.  **Verify Diagnostics**:
    *   `"many"` should have a red underline (Warning/Error: `string is not assignable to number`).
    *   `10` should have a red underline (`number is not assignable to string`).

---

## Walkthrough 4: Union Types (`TYPE-04`)
**Goal**: Verify support for variables that can hold multiple types.

1.  **Type** a union annotation:
    ```lua
    ---@type string | number
    local id = "A1"
    id = 123
    ```
2.  **Verify**: No errors on either assignment.
3.  **Type** a logical OR assignment:
    ```lua
    local x = "default" or 0
    ```
4.  **Verify**: `x` shows inlay hint `: string | number`.

---

## Walkthrough 5: Generics Support (`TYPE-05`)
**Goal**: Verify that generic functions instantiate types correctly at call sites.

1.  **Type** a generic identity function:
    ```lua
    ---@generic T
    ---@param value T
    ---@return T
    local function wrap(value) return value end
    ```
2.  **Invoke** it with different types:
    ```lua
    local s = wrap("hello")
    local n = wrap(42)
    ```
3.  **Verify Inlay Hints**:
    *   `s` should show `: string`.
    *   `n` should show `: number`.

---

## Walkthrough 6: Return Type Checking (`TYPE-06`)
**Goal**: Verify that function bodies are validated against their `@return` tags.

1.  **Type** a function with a return mismatch:
    ```lua
    ---@return number
    local function getAge()
        return "25"
    end
    ```
2.  **Verify Diagnostics**:
    *   `"25"` should have a red underline (`string is not assignable to number`).
3.  **Type** a function with multiple returns:
    ```lua
    ---@return number, string
    local function getData()
        return 1, 2
    end
    ```
4.  **Verify**: `2` should be underlined (`number is not assignable to string`).

---

## Walkthrough 7: External API Stubs (`TYPE-07`)
**Goal**: Verify that `require` loads types from indexed stubs.

1.  **Ensure** standard library stubs are loaded (built-in).
2.  **Type** a require call:
    ```lua
    local m = require("math")
    local s = m.sin(1.0)
    ```
3.  **Verify**:
    *   `m` should resolve to the `math` library type.
    *   `s` should show inlay hint `: number`.
4.  **Try** an incorrect call:
    ```lua
    m.sin("not a number")
    ```
5.  **Verify**: `"not a number"` should be underlined (`string is not assignable to number`).
