---
id: "BUG-427"
title: "A global `function f() end` in another file resolves to no type, and the assignment form loses its annotations"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-427: cross-file global functions resolve to nothing, or to an un-annotated type

Split out of BUG-425, which fixed the *demand* half of "out-of-file signatures never reach the type
graph" and found on measurement that two shapes fail earlier than that, in resolution.

## Measured (2026-08-07, indexed fixture, declaration in a second file)

```
---@param n number
function count(n) end      ->  resolveGlobal("count") = null              <- no type at all
---@param n number
count = function(n) end    ->  resolveGlobal("count") = fun(n: unknown)   <- resolves, @param lost
Lib = {}
---@param n number
function Lib.count(n) end  ->  checked; Lib.count("s") errors correctly   <- works (BUG-425)
```

Forcing a stub-index rebuild (`IndexedBasePlatformTestCase`) changes nothing, so this is not a test
fixture artefact.

Two independent defects:

1. **`resolveGlobal` does not see a global function *declaration*.** It resolves the assignment form
   `f = function() end` but not `function f() end`, which is how Lua code overwhelmingly declares
   globals.
2. **The assignment form resolves without its LuaCATS annotations.** `fun(n: unknown)` rather than
   `fun(n: number)`, so even where resolution works there is no contract to check.

## Why it matters

Every consumer of `resolveGlobal` is affected, not just assignability: hover, inlay hints, parameter
info and completion all read the same type. A project that declares its API as bare global functions
is invisible to all of them across file boundaries.

For BUG-425's purposes it means the contract population is smaller than it looks: only the
library-member form (`function Lib.f() end`) carries a checkable signature today.

## Fix direction — not traced

Unknown whether `function f() end` is missing from the global index, or indexed under a key
`resolveGlobal` does not consult. `LuaGlobalDeclarationIndex` stores `funcName.text` for
`<Class>:<method>` / `<Class>.<fn>` forms (see `AGENTS.md`), which is a hint that the *bare* form may
simply have no entry — but that is a hypothesis, and this report deliberately asserts nothing it did
not measure.

Defect 2 is likely the narrower fix: whatever builds the function type for the assignment form does
not consult the attached cats comment, while `funcTypeFromStub` does for the member form.

## Verification

- All three shapes above resolve to a `fun(n: number)` and the first two error on `count("s")`,
  matching the member form.
- `LuaDeclaredContractErrorTest` gains the two shapes alongside the member-form test it has now.
- Corpus re-baseline: BUG-425 moved nothing because so few contracts were reachable. Fixing this
  makes bare-global APIs checkable and **is** expected to move the counts — sample before accepting,
  the way BUG-425's own 244-false-positive measurement did.
