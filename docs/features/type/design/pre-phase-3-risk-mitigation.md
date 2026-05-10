# Pre-Phase 3 Risk Mitigation (TYPE-03)

This document captures the upfront tasks and risk mitigation strategies completed before beginning the full implementation of Phase 3 (TYPE-03) Function Signature Matching.

## 1. Test Scenarios for TYPE-03 (Draft)
A comprehensive test suite prevents regressions and defines the exact bounds of our type checker. We will need tests for:
*   **Basic Compatibility:**
    *   Exact match (e.g. `number` -> `number`).
    *   Implicit `Any` match (`any` -> `string`, `string` -> `any`).
    *   Incompatible primitives (`string` -> `number`, `boolean` -> `nil`).
*   **Arity & Optionals:**
    *   Correct number of arguments.
    *   Too few arguments (triggers arity error).
    *   Too many arguments (triggers arity error, unless `...` is present).
    *   Too few arguments, but missing ones are optional (`?`) -> OK.
    *   Passing `nil` explicitly to an optional parameter -> OK.
*   **Vararg Matching:**
    *   Passing 0, 1, or N arguments to an untyped vararg (`...`) -> OK.
    *   Passing wrong types to a typed vararg (e.g., `---@param ... string` and passing `1`) -> Error.
*   **Contravariance (Functions passed as arguments):**
    *   Passing a callback with fewer parameters than expected -> OK (Lua drops extra args).
    *   Passing a callback with a broader parameter type -> OK.
    *   Passing a callback with a stricter parameter type -> Error.
*   **Edge Cases:**
    *   Calling a function whose type is `UNKNOWN` or `ANY` -> OK (no errors).
    *   Calling a non-function (e.g. `local x = 5; x()`) -> "Attempt to call a number value" error.

## 2. Contravariance Semantics (Concrete Lua Examples)
When passing a function as an argument, its parameters flow *in reverse* (contravariance).
```lua
---@param callback fun(name: string, age: number): boolean
local function doWork(callback) end

-- ✅ OK: We expect the callback to handle a string and a number. 
-- The provided callback takes 'any' and 'number', which is broader (safe).
doWork(function(n, a) return true end)

-- ✅ OK: Lua allows functions to ignore trailing arguments.
-- The provided callback takes only 'name', ignoring 'age'.
doWork(function(n) return true end)

-- ❌ ERROR: The caller will pass a 'string' to the first argument, 
-- but the provided callback strictly requires a 'number'.
doWork(function(n, a) 
    ---@type number
    local forced = n
    return true 
end)
```
*Architecture translation:* During `checkTypes(UseNode(callback), ValueNode(provided_func))`, we will call `flow(provided_param, expected_param)` for parameters, reversing the usual flow direction.

## 3. LuaScope Architecture Review
After reviewing `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaScope.kt`, the parameter binding is solid but flat.
*   `LuaScope` represents a lexical boundary. `createFunctionScope(returnNodes)` isolates inner bindings from the parent.
*   In `LuaTypesVisitor.visitFunctionBody`, we currently populate `paramNodes: MutableMap<String, VariableNode>` and call `scope.define()`.
*   **Gap identified:** `LuaScope` drops the *ordering* of parameters. `FunctionType` relies on ordered `List<LuaParameter>`. We must ensure that when we synthesize `FunctionType` from a function declaration, we use the AST `LuaParList` to preserve order, or map them correctly to the types gathered from LuaCATS via `LuaTypeGraphBridge.injectParamAnnotations`.

## 4. `visitFuncCall` Integration Architecture
`LuaTypesVisitor` currently lacks `visitFuncCall`. We will need to implement it as follows:
1.  **Resolve Callee:** Retrieve the `ValueNode` of the expression being called (e.g., `elementNodes[unwrapExpression(o.expr)]`).
2.  **Synthesize Call Node:** Create a fresh `VariableNode` representing the result of the function call.
3.  **Synthesize FunctionType Demand:** Create a `FunctionType` constraint matching the call arguments.
    *   For each argument expression, grab its `ValueNode`.
    *   Build a temporary `FunctionType` holding these arguments as its `params`.
4.  **Flow:** Call `graph.flow(callee_value, call_demand_use_node)`.
5.  **checkTypes:** When `checkTypes` encounters `FunctionType` (value) flowing into `FunctionType` (demand), it will zip the parameter lists and validate them index-by-index, emitting arity or type mismatch errors if they violate the rules.

## 5. `TypeParser` Optional/Varargs Support Check
Reviewed `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/TypeParser.kt`.
*   **Current State:** It defines `data class LuaParameter(val name: String, val type: LuaType)`.
*   **Missing:** It **does not** capture optionals (`?`) or typed varargs (`...`). The grammar parsing logic (L92-97) just reads `arg.argName.text` and sets `type = LuaPrimitiveType.ANY` if undefined.
*   **Required Fix:** Update `LuaParameter` to `data class LuaParameter(val name: String, val type: LuaType, val isOptional: Boolean, val isVararg: Boolean)`. Then, in `TypeParser.kt`, check if `argName.text` ends with `?` or equals `...` to set these flags appropriately.
