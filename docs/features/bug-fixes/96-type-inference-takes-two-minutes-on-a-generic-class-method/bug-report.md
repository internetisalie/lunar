---
id: "BUG-96"
title: "Type inference of one method of a generic class takes about two minutes, in a 40-line file"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-96: Type inference of one method of a generic class takes about two minutes, in a 40-line file

Found by MAINT-56's representative run. Its `fallo` member is one 514-line file and took 6 821 s to
measure (`[type-corpus:fallo] files=1 population=149 sites=53 elapsedMs=6821246`). MAINT-54's harness
reads the file's type snapshot up to three times per site.

## 1. Reproduction
The corpus member `fallo` (`tooling/corpus/type-corpus.json`): `NTBBloodbath/fallo` at commit
`b95e4996477e4f023bf1b86190c946476cca210e`, file `lua/fallo/init.lua`, target Standard Lua 5.4. The
file below is its lines 1–10 followed by lines 430–459, unchanged:

```lua
---Rust-like Result type for structured error handling

---@alias Error { message: string?, source: string?, stack: string? }
---
---@generic T, E: string|Error
---@class Result<T, E>
---@field ok boolean|function True for success (Ok), false for error (Err)
---@field value T Contained value when ok=true
---@field error E Contained error when ok=false
local Result = {}
---Add context message to an error
---The context is prepended to the existing error message, creating a chain of contexts
---For success Results, returns self unchanged
---@generic T, E
---@param self Result<T, E>
---@param message string Context message to attach
---@return Result<T, E> New Result with enriched error message or self if Ok
function Result:with_context(message)
   if self.ok then return self end

   local err = self.error
   if type(err) == "table" then
      -- Shallow-copy the error to keep immutability
      local copy = {}
      for k, v in pairs(err) do
         copy[k] = v
      end

      -- Prepend context to the existing message
      local original_message = err.message or tostring(err)
      copy.message = message .. ": " .. original_message

      return Result.err(copy)
   else
      -- For string errors, structure and add context
      local structured = Result.structure_error(err, 2)
      structured.message = message .. ": " .. (structured.message or tostring(err))
      return Result.err(structured)
   end
end
```

In a `BasePlatformTestCase` (light fixture, builder `libvirt`, GoLand 2026.2.3 test platform), pin the
target to Standard Lua 5.4. Add the file with `myFixture.addFileToProject("v.lua", text)`, then time
`LuaTypesSnapshot.forFile(file).getErrors()`.

## 2. Expected vs Actual Behavior
- **Expected**: a 40-line file is analysed in well under a second.
- **Actual**: 128 648 ms, 13 errors. Measurements on the whole file and on its parts (one run each):

| input | ms | errors |
|---|---|---|
| whole `init.lua` (514 lines), first read | 130 286 | 246 |
| whole `init.lua` after appending a newline, three times | 124 904, 123 663, 128 421 | 246 |
| `init.lua` cut after line 436, before `with_context` | 1 906 | 70 |
| `init.lua` cut after line 459, after `with_context` | 111 205 | 234 |
| the 40-line file above | 128 648 | 13 |
| lines 1–429, then `with_context` without its doc comment | 72 616 | 137 |
| lines 1–436, then `with_context` keeping only the `then` branch | 99 373 | 168 |
| lines 1–436, then `with_context` keeping only the `else` branch | 108 498 | 168 |
| lines 1–436, then `with_context` without the `for k, v in pairs(err)` loop | 120 197 | 176 |
| whole `init.lua` with every `---@generic` line removed and `Result<…>` written `Result` | > 900 000, stopped | — |

- EDT stack 25 s into the whole-file read: `VariableElement.resolveWrite` → `writeWith` → `resolveWrite`
  repeated (`LuaTypeNodes.kt:215`, `:148`), deepest frame `VariableElement.resolveWrite$flatten`
  (`LuaTypeNodes.kt:206`) adding to a `HashSet`, which calls `LuaGraphType$Table.hashCode` →
  `AbstractMap.hashCode`.

## 3. Related
[[BUG-97]]: the same frames in a whole-directory slowdown. MAINT-56's `results.md` records `fallo` as
timed out, so its rule weights are to be re-measured after this bug is fixed.
