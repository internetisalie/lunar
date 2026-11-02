---
id: "BUG-28"
title: "A second `@class` tag in the same comment block gives its fields to the first class"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-28: A second `@class` tag in the same comment block gives its fields to the first class

Found while planning [[MAINT-52]] (its DR-04 probe `d1`).

## 1. Reproduction

```lua
---@class D1A
---@field a any
---@class D1B
---@field y string
local t = {}

---@type D1A | D1B
local v = { a = 1 }
```

## 2. Expected

`D1A` declares only `a`, and `D1B` declares only `y`. The table `{ a = 1 }` satisfies `D1A`, so no
error is reported.

## 3. Actual

`ERROR { ... } is not assignable to D1A | D1B; closest match 'D1A': missing field 'y'`: the field
declared after the second `---@class` tag is attached to `D1A`. The result is the same under every
MAINT-52 variant, so it is not caused by that work
(`~/.cache/claude-scratch/lunar/maint-52/r1/probes2/r2-*.txt`, case `d1-closest-any`).

Splitting the two tags into separate comment blocks (a `local` between them) gives each class its
own fields, as in MAINT-52's `e1`/`e4` fixtures.
