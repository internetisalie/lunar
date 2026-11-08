---
id: "BUG-47"
title: "A member read through an `any`-typed member takes the type of the root table's member of the same name"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-47: A member read through an `any`-typed member takes the type of the root table's member of the same name

Found while fixing [[BUG-46]]. It is the false finding behind [[BUG-541]]'s two flickering zerobrane hits.

## 1. Reproduction
Light fixture, default target, `LuaSuspiciousConcatenationInspection` enabled:

```lua
-- cfg.lua
return { default = { interpreter = 'luadeb' } }
-- main.lua
ide = { config = dofile('cfg.lua'), interpreter = nil }
-- user.lua
local ide = ide
print('a' .. ide.config.default.interpreter)
```

`ide.config.default.interpreter` is reported as *Suspicious concatenation: operand of type 'nil'*.

| `main.lua` declares | Finding on `ide.config.default.<x>` |
|---|---|
| `interpreter = nil` | `interpreter`: nil |
| no `interpreter` member | none |
| `interpreter = 'x'` | none (typed string) |
| `name = nil`, read `ide.config.default.name` | `name`: nil |

## 2. Expected vs Actual Behavior
- **Expected**: `ide.config` is `any` (`dofile` returns `any`), so every member read through it is
  `any` and nothing is reported.
- **Actual**: the last segment takes the type of the **root** table's member with the same name
  (`ide.interpreter`), as if the `any` segments between them were skipped.

## 3. Context / Environment
- In zerobrane, `ide.interpreter = nil` (`src/main.lua:88`) and `ide.config = dofile("src/config.lua")`,
  so `ide.config.default.interpreter` (`src/editor/menu_project.lua:96`, `:136`) is typed nil although
  `src/config.lua:185` sets it to `'luadeb'`.
- A probe run after the zerobrane sweep printed this nil both with the 53ec4288 stubs and with BUG-46's,
  while only one of those sweeps reported it. Whether the inspection observes it varies between sweeps:
  that is [[BUG-541]]. The order dependence itself is not explained.
- Root cause not investigated. Candidate: member resolution along a receiver chain (MAINT-53's
  `LuaMemberResolution.typedTargets` or the graph's member read) treating an `any`/undefined segment as
  transparent.
