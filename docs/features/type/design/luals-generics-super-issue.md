# Generics Implementation - Super Issue Summary

**GitHub Issue:** [LuaLS/lua-language-server#1861](https://github.com/LuaLS/lua-language-server/issues/1861)  
**Status:** Open (Last updated: May 2, 2026)  
**Type:** Meta-issue (collects and organizes related generic implementation issues)  
**Reactions:** 29 👍, 12 ❤️ (41 total)  
**Comments:** 21 discussions documenting open questions and edge cases

---

## Overview

This is a "super issue" that serves to organize and track all issues relating to the implementation of generics in the Lua Language Server (lua-language-server). The issue consolidates duplicates, links related problems, and documents open questions and unimplemented features related to generic type support.

---

## Current Implementation Status

### Completed Tasks
- ✅ Issue #1853 - Resolved
- ✅ Issue #1863 - Resolved  
- ✅ Issue #1929 - Resolved
- ✅ Issue #1935 - Resolved

### Pending Issues (High Priority)
- ⏳ Issue #723 - Generic constraint resolution
- ⏳ Issue #734 - Generic type propagation
- ⏳ Issue #911 - Generic method support
- ⏳ Issue #1000 - Generic table fields
- ⏳ Issue #1170 - Generic class inheritance
- ⏳ Issue #1272 - Generic function parameters
- ⏳ Issue #1322 - Generic type narrowing
- ⏳ Issue #1341 - Generic constraint validation
- ⏳ Issue #1532 - Generic variance
- ⏳ Issue #1856 - Generic overloading
- ⏳ Issue #1883 - Generic scope binding

---

## Linked Issues - Detailed Summaries

### Closed Issues

#### ✅ Issue #1929: Generic Class Inheritance
**Title:** Cannot extend generic class  
**Status:** CLOSED (Jan 19, 2026)  
**Resolution:** FIXED

**Problem:**
- Inheritance from generic classes didn't work
- No type checking or diagnostic errors were provided
- Example: `---@class Bar: Foo<integer>` would not resolve generic `Foo<T>`

**Code Example:**
```lua
--- @class Foo<t>: { a: t; }
--- @class Bar: Foo<integer>
local x --- @type Bar
local what = x.a -- Expected: integer, Got: unknown
```

**Resolution Details:**
- Fixed in commit resolving generic class inheritance
- Generic syntax in inheritance clauses now properly handled
- Type propagation from generic parent to child classes now works

**Impact:** This was a foundational fix enabling all generic class inheritance patterns

---

#### ✅ Issue #1863: Generic Class Inheritance
**Title:** Generic Class Inheritance  
**Status:** CLOSED (Jan 19, 2026)  
**Resolution:** FIXED

**Problem:**
- Generic class inheritance partially broken
- Methods of generic base classes not visible in derived classes
- Return type inference in methods failed for generic class instances

**Code Example:**
```lua
---@class LinQ<T>: { [integer]: T }, tablelib
local linq = {};

---@generic T
---@param t T[]
---@param query fun(a: T): boolean
---@return LinQ<T>
function linq.where(t, query) end

---@type LinQ<string>
local tab = {};
tab:where(function (a) return a == "" end)  -- `a` type was not recognized as string
```

**Resolution Details:**
- Generic method inheritance now properly chains types
- Method visibility for inherited generic classes fixed
- Return type inference for generic class instances corrected

**Related to:** Issue #1929 (closely related - part of same generic inheritance fix)

---

#### ✅ Issue #1853: Recursive Expansion on Hover of Generic Type
**Title:** Recursive expansion on hover of generic type  
**Status:** CLOSED (Jan 19, 2026)  
**Resolution:** FIXED

**Problem:**
- Hovering over generic types caused infinite recursive expansion
- Type hover information became unreadable and extremely long
- Example showed hover text expanding to thousands of characters with nested type information

**Code Example:**
```lua
---@class store<T>: {set:fun(self:store<T>, key:integer, value:T), get:fun(self:store<T>, key:integer):T}

local string_store ---@type store<string>
-- Hovering showed: store<string>|{ set: fun(self: store<<T>>|{ set: fun(self: store<<T>>|{ set: unknown, ...
```

**Root Cause:**
- Type display logic didn't detect and prevent recursive type definitions
- Generic parameters were being re-expanded indefinitely

**Resolution Details:**
- Implemented cycle detection in type display
- Hover information now stops at reasonable depth
- Recursive generic types display cleanly without infinite expansion

**Impact:** Critical UX improvement for users working with complex generic types

---

#### ✅ Issue #1935: Allow to Set Named Fields for Generic Class
**Title:** Allow to set named fields for generic class  
**Status:** CLOSED (Feb 22, 2023, same day resolution)  
**Resolution:** FIXED

**Problem:**
- Field declarations were conflicting with generic type declarations
- Named fields should have priority over generic index types
- Type resolution for fields failed when both were specified

**Code Example:**
```lua
---@class gener: { [string]: number}
---@field field string

local x ---@type gener
-- Expected: x.field has type string
-- Got: x.field has ambiguous type (both number and string)
```

**Resolution Details:**
- Field declaration priority established: explicit `@field` annotations override generic index types
- Type resolution order: explicit fields > generic index types
- Disambiguation rules implemented

---

### Open Issues

#### ⏳ Issue #1883: Type Inferencing of Filter Style Functions
**Title:** Type inferencing of filter style functions  
**Status:** OPEN (since Feb 6, 2023)  
**Related to:** Super issue #9 (Indexed Generic Types)

**Problem:**
- Function parameters with generic constraints don't infer types from caller context
- When generic parameter `T` appears in both function parameter type and parameter signature, type inference fails
- Anonymous function parameters lose type information

**Code Example:**
```lua
---@generic T
---@param f fun(a: T)
---@param t table<any, T>
---@return T[]
local function tbl_filter(f, t)
  return t
end

---@type string[]
local s = {'a', 'b', 'c'}

local r1 = tbl_filter(function(a) end, s)
-- Expected: r1 is string[]
-- Got: r1 is unknown[] (because `a` inferred as unknown, not string)
```

**Workaround Found:**
- Removing type annotation from anonymous function parameter forces inference from table parameter
- Works with `table<any, T>` but NOT with `T[]` notation
- Pattern works with primitive types but unclear for complex types

**Technical Challenge:**
- Requires multi-source type inference: combining function signature types with argument types
- Currently type inference doesn't backpropagate from structured parameters to anonymous function params

**Designer Notes:**
- @sumneko (maintainer): "I don't know how to design a strict rule"
- @lewis6991: Suggests looking at all sources of T and ensuring consistency
- Not yet assigned to ongoing work

---

#### ⏳ Issue #1856: Generic Class Instanced in Function Should Return as Concrete Type
**Title:** Generic class instanced in function should return as a concrete type  
**Status:** OPEN (since Jan 29, 2023)

**Problem:**
- Generic class instances created via function with backtick capture show expanded generic types in hover
- Expected: `list<string>`
- Actual: `list<<T>>|{ [integer]: string }` (shows expanded recursive definition)

**Code Example:**
```lua
---@class list<T>: {[integer]:T}

---@generic T
---@param class `T`
---@return list<T>
local function new_list(class)
  return {}
end

local strings = new_list('string')
-- Hover shows: local strings: list<<T>>|{ [integer]: string }
-- Expected:   local strings: list<string>
```

**Root Cause:**
- Type display logic shows fully expanded generic types instead of compact generic notation
- Related to (but distinct from) #1853 - this is about display preference, not recursive expansion

**UX Impact:**
- User confusion: IDE shows too much implementation detail
- Makes it hard to understand type at a glance
- Particularly problematic when generic classes have complex field definitions

**Related to:** Super issue #6 (Generic Type Reuse) - affects how captured types are displayed

---

## Key Open Questions & Discussion Points

### 1. **Generic Parameter Type Resolution in Function Scope**
**Problem:** Generic constrained parameters don't resolve their concrete types within function bodies.

**Example:**
```lua
---@class A
---@field k string

---@generic T: A
---@param p1 T
function f(p1) 
  local m = p1.k  -- ERROR: p1 has type unknown, not A
end
```

**Discussion:**
- The concrete type `A` is directly available in the function signature but not recognized within the function body
- Currently requires a workaround: passing string literals at runtime to trigger type capture (only works with base Lua types)
- This is fundamentally about type substitution: `p1 -> T -> A` (one layer of indirection)
- Should be straightforward to implement since the type information is directly available in the signature

**Links:** Comments from @tmillr (Jan 2024), @checkraisefold (Jun-Jul 2024)

---

### 2. **Scoped Generics in Local Function Context**
**Problem:** No support for scoped generic parameters within function implementations.

**Feature Request:**
```lua
---@generic T
---@param x T
local function f(x)
   ---@type T
   local y = some_any_type_value
end
```

**Discussion:**
- This is a major feature in TypeScript and Haskell
- Enables local variable type constraints based on generic parameters
- Multiple useful applications exist but are currently not supported

**Links:** Comment from @aiya000 (Oct 2025)

---

### 3. **Generic Method Chaining with New Type Parameters**
**Problem:** Cannot define methods that return new generic instances with different type parameters.

**Example (TypeScript equivalent):**
```typescript
interface A<T> {
  next<K>(callback: (value: T) => K): A<K>;
}
```

**Lua Equivalent (not currently supported):**
```lua
---@class A<T>
---@field next fun<K>(callback: fun(value: T): K): A<K>
```

**Discussion:**
- Users ask if this pattern can be implemented in Lua
- No confirmed working solutions yet
- Requires support for method-level generic parameters independent of class generics

**Links:** Questions from @AserJoker (Sep 2024), @brunoti (Feb 2025)

---

### 4. **Vararg Generics Support**
**Problem:** Cannot express generic parameter types in varargs contexts.

**Use Cases:**

**a) Multiple Return Type Capture:**
```lua
--- @generic Ta
--- @generic Tr
--- @param f async fun(...: Ta): ...: Tr
--- @param msgh fun(error: string)
--- @param ... Ta
--- @return Tr ...
function xpcall(f, msgh, ...) end
```

**b) `assert()` Function:**
- Current type definitions for `assert(v, message, ...)` can only return the type of the first argument
- Cannot properly type additional varargs that are returned

**c) `unpack()` Function:**
- Type annotations don't work correctly for `unpack(list, i, j)`
- Should preserve element type information through unpacking

**Discussion:**
- Vararg generics would be useful for function chaining and multi-value returns
- Needs syntax extensions to support generic parameters in `...` position

**Links:** Comments from @CelDaemon (Dec 2024 - Jan 2025)

---

### 5. **Backtick Capture Behavior and Documentation**
**Problem:** Backtick capture (`\`T\``) is poorly documented and has surprising/inconsistent behavior.

**Background on Backtick Capture:**
- Backticks capture the **literal string parameter value** as a **type name**
- Requires a corresponding `@class` definition for the captured type

**Example:**
```lua
---@generic T
---@param arg `T`
---@return T
local foo = function(arg) return {} end

local r = foo("Test")  -- r: Test (not string)
-- Requires @class Test to exist
```

**Advanced Usage - Combined Type Names:**
```lua
---@generic T
---@param arg Object.`T`
---@return T
local foo3 = function(arg) return {} end

local r3 = foo3("Test")  -- r3: Object.Test
-- Combines captured "Test" with "Object." prefix
```

**Documentation Gaps:**
- Documentation is too terse and lacks side-by-side comparison examples
- Users struggle to understand practical use cases
- The difference between `T` and `\`T\`` is not intuitive without detailed explanation

**Related PR:** [#3149](https://github.com/LuaLS/lua-language-server/pull/3149) proposes enhancements to capture the actual parameter type as a type name (not merged, maintainer disagreement)

**Links:** Comments from @nullromo (Nov 2025), @tomlau10 (Nov 2025), @litoj (Nov 2025)

---

### 6. **Generic Type Reuse from Classes to Functions**
**Problem:** Generic type constraints from classes don't properly propagate when used in function parameters.

**Example:**
```lua
---@class BasicOpts {test:string}
---@class Preset<T> T|{preset:string}

---@generic T: BasicOpts
---@param preset Preset<T>
---@return boolean
local function hasValue(preset)
  return preset.test ~= nil  -- WARNING: accessing non-existent field
end
```

**Observed Type Info:**
- IDE reports: `(parameter) preset: Preset<<T:BasicOpts>>`
- Extra surrounding `<>` indicates a type resolution issue
- Type constraint information is lost in nested generic contexts

**Links:** Comment from @litoj (Nov 2025)

---

### 7. **Generic Tables with Complex Key/Value Relationships**
**Problem:** Cannot express complex type relationships between table keys and values.

**Feature Request:**
```lua
---@generic T: string[]
---@param p1 `T`[]
---@return { [T]: string }
function foo(p1) end

local result = foo({ "bar", "quux" })
print(result.bar)    -- Type: string
print(result.quux)   -- Type: string
```

**Discussion:**
- Requires capturing generic array element type as a table key type
- Not currently supported

**Links:** Question from @FunctionDJ (Dec 2025)

---

### 8. **Generic Functions Returning Generic Functions**
**Problem:** Generic type information doesn't propagate when returning higher-order functions.

**Example:**
```lua
---@generic T
---@param t T
---@return fun(t:T): T
function instance_of(t)
    return getmetatable(t).__call
end

kingdom = entity {
    gold = 10,
    prestige = 10,
    glory = 100
}

another_kingdom = instance_of(kingdom) {
  -- ERROR: autocomplete doesn't suggest gold, glory, prestige
  -- Treats table as opaque instead of type T
  gold = 100,
  glory = 50,
  prestige = 1
}
```

**Expected Behavior:**
- When a generic function returns another function with generic parameter `T`
- And that returned function is called with a table literal
- The table literal should be typed as `T` and provide field autocompletion

**Actual Behavior:**
- Table is treated as opaque
- Simple returns work correctly with instance fields
- Issue only occurs with higher-order generic functions

**Links:** Comment from @chismar (Feb 2026)

---

### 9. **Indexed Generic Types (Table Index Generics)**
**Problem:** Cannot express that a return type is indexed by a generic parameter.

**Feature Request:**
```lua
---@generic T
---@param obj T
---@param key string
---@return T[key]
function table.get(obj, key) end
```

**Challenge:**
- Requires expressing type indexing where the index type is a generic parameter
- Needed for proper typing of table accessor patterns

**Links:** Question from @Curve (May 2026, most recent)

---

## Implementation Challenges

### 1. Type Substitution in Function Scopes
- **Current:** Generics are substituted in function signatures but not in function bodies
- **Challenge:** Need to maintain generic binding context throughout function execution scope
- **Related:** Issues #723, #734, #911

### 2. Constraint Propagation
- **Current:** Generic constraints work at declaration sites
- **Challenge:** Constraints don't propagate through type compositions and nested generics
- **Related:** Issues #1170, #1322, #1532

### 3. Syntax Limitations
- **Current:** No method-level generics independent of class generics
- **Challenge:** Lua annotation syntax needs extension or creative workaround
- **Related:** Issue #2355, method chaining requests

### 4. Varargs Support
- **Current:** Generic parameters don't support `...` syntax
- **Challenge:** Requires parser/type system extension
- **Related:** Requirements for xpcall, unpack, assert patterns

### 5. Documentation and UX
- **Current:** Backtick capture behavior is underdocumented
- **Challenge:** Users struggle to understand practical applications
- **Related:** PR #3149, multiple documentation questions

---

## Community Priority Assessment

Based on discussion frequency and reaction counts:

### High Priority (Multiple requests, blocking workflows)
1. Generic parameter type resolution in function scope (Issue #1) - **Most discussed**
2. Generic method chaining (Issue #3) - **Multiple requests**
3. Vararg generics (Issue #4) - **Practical use cases identified**

### Medium Priority (Feature requests with specific use cases)
1. Scoped generics in local functions (Issue #2)
2. Higher-order generic functions (Issue #8)
3. Generic type reuse from classes (Issue #6)

### Lower Priority (Edge cases, advanced use cases)
1. Complex key/value relationships (Issue #7)
2. Indexed generic types (Issue #9)

### Documentation Priority
1. Backtick capture documentation - **Immediate** (prevents user confusion)
2. Generic examples in wiki

---

## Related Infrastructure

### Active Development
- PR #3149: Enhanced backtick capture (pending maintainer review)
- @ian-pascoe: Open PR addressing several high-priority issues (Jan 2026)

### Lua Version Considerations
- Most use cases apply to Lua 5.1+
- Some patterns (e.g., varargs) heavily used in Lua standard library functions
- LuaCATS/LuaDoc integration may provide annotation syntax extensions

---

## Next Steps for Contributors

1. **Start with Issue #1** (function scope type resolution)
   - Most impactful for users
   - Most discussed in community
   - Relatively bounded scope

2. **Documentation improvements**
   - Add practical examples for backtick capture
   - Side-by-side comparisons of `T` vs `\`T\``
   - Use cases for parameter capture

3. **Varargs support**
   - Would unblock several standard library annotations
   - Clear requirements from std lib functions

4. **Method-level generics**
   - Research syntax possibilities
   - Determine if requires annotation syntax extension

---

## References

- **Main Issue:** https://github.com/LuaLS/lua-language-server/issues/1861
- **Related PR:** https://github.com/LuaLS/lua-language-server/pull/3149
- **LuaLS Documentation:** https://luals.github.io/
- **LuaCATS Spec:** https://github.com/LuaCATS/LuaCATS

---

## Archived Issues from sumneko/lua-language-server

The following issues are referenced in the super issue but are from the original `sumneko/lua-language-server` repository which was archived and functionality migrated to `LuaLS/lua-language-server`. These are noted for historical reference:

### Historical Issue References (sumneko archive)
- **#723** - Generic type inference
- **#734** - Generic constraint handling  
- **#911** - Generic type propagation
- **#1000** - Generic field access
- **#1170** - Generic class inheritance patterns
- **#1272** - Generic function overloading
- **#1322** - Generic type narrowing
- **#1341** - Generic constraint validation
- **#1532** - Generic type variance

**Note:** These issues have been consolidated into the LuaLS repository or addressed through the recent generic fixes (1853, 1863, 1929, 1935). New issues should be filed against:
- **Active Repository:** https://github.com/LuaLS/lua-language-server/issues

---

## Summary of Fixes (Jan 19, 2026 - Most Recent Batch)

Recent maintenance work (Jan 19, 2026) closed 4 major generic-related issues in a coordinated effort:

| Issue | Title | Status | Impact |
|-------|-------|--------|--------|
| #1929 | Cannot extend generic class | ✅ FIXED | Enables generic class inheritance |
| #1863 | Generic Class Inheritance | ✅ FIXED | Methods and types now properly inherited |
| #1853 | Recursive expansion on hover | ✅ FIXED | Hover information now readable |
| #1935 | Named fields for generic class | ✅ FIXED | Field priority rules established |

**Commit Timeline:** All closed on Jan 19, 2026 (same maintainer session)
- Suggests coordinated fix addressing foundational generic infrastructure
- Likely prerequisite for addressing remaining open issues

---

*Summary compiled from 21 community discussions spanning Jan 2023 - May 2026*
