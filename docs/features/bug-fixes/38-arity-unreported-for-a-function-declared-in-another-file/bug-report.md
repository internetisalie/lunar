---
id: "BUG-38"
title: "Too many or too few arguments are not reported when the callee is a function declared in another file"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-38: Too many or too few arguments are not reported when the callee is a function declared in another file

Found by the [[MAINT-54]] false-negative audit (harness `28dfb5a2`, libvirt builder; cells and sites in [audit-results.md](../../maint/54-type-false-negative-audit/audit-results.md)). "Silent" means no `ERROR`/`WARNING` from `LuaTypesSnapshot.forFile(file).getErrors()` once `HYPOTHESIS` entries are dropped, which is what both type inspections show.

## 1. Reproduction
The snippets were run in a throwaway light-fixture probe (`IndexedBasePlatformTestCase`, `addFileToProject`, every error printed with its tier) on `28dfb5a2`, then deleted.

```lua
-- caller.lua, bundled stdlib callees: no diagnostic at any tier
local a = math.floor()
local b = require()
for k in ipairs({}, true) do end
local c = os.getenv('A', true)
```
```lua
-- d.lua
---@param s string
function g1(s) end
U = {}
---@param s string
function U.f(s) end
-- caller.lua: no diagnostic at any tier for any of the four lines
g1()
g1('a', true)
U.f()
U.f('a', true)
```
Corpus sites (audit edit, silent): luacheck `src/luacheck/config.lua:326` `math.floor(x)` → `math.floor()`;
luacheck `spec/cli_spec.lua:43` `type(code51)` → `type(code51, true)`.

## 2. Expected vs Actual Behavior
- **Expected**: *Too few arguments* / *Too many arguments* at the call, as for a `local function` in the
  same file or a method in another file.
- **Actual**: nothing at any tier. In the same probe `U.f(true)` does report *boolean is not assignable to
  string*, so the callee is resolved and its parameter types are applied; only the count is not checked.
  Audit: 0 of 77 measured `arity.*` sites on bundled-stub functions reported, and 0 of 16 on
  `require`d-module members; `SAME_FILE` `LOCAL`/`METHOD` and `PROJECT_FILE` `METHOD` arity sites do report.

## 3. Context / Environment
- **Lua Version**: any (corpus members pinned to their manifest targets, Lua 5.1–5.4 and LuaJIT 2.1).
- **Relevant Files**: `LuaTypeGraph.checkFunctionCompatibility` emits the arity messages; its tier depends on
  `declaredSignature`. That is where the message is built, not an established cause.
- **Other Notes**: the "extra argument to a declared global" case left open by [[BUG-19]] §5 is one instance.
  Cells: `arity.tooFew`/`arity.tooMany` × `LIBRARY` `GLOBAL`/`MEMBER`, `PROJECT_FILE`/`CHECKOUT_FILE` `MEMBER`.
