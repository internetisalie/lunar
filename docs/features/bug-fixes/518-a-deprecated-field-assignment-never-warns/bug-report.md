---
id: "BUG-518"
title: "A deprecated field assignment never warns: the Deprecated API inspection reads no tag on pkg.x = value"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-518: `---@deprecated` on a field assignment is ignored

Found while implementing [[BUG-513]] (its review round 1, finding M1). Run on the libvirt builder
with `test --rerun --no-build-cache`; probe `Bug513FieldDeprecationProbeTest`, output in
`~/.cache/claude-scratch/lunar/bug-513/xml/r3-red/`.

## 1. Reproduction (executed)

`LuaDeprecatedApiInspection` enabled, three fixtures, each reading the deprecated name afterwards:

| Fixture | Declaration | Warnings on the read |
| :-- | :-- | :-- |
| global field | `pkg = {}` / `---@deprecated` / `pkg.x = 1` | `[]` |
| local field | `local pkg = {}` / `---@deprecated` / `pkg.x = 1` | `[]` |
| control | `---@deprecated` / `function f() end` | `[f@55]` |

```
[probe:513dep] global-field warned=[]
[probe:513dep] local-field warned=[]
[probe:513dep] global-function-control warned=[f@55]
```

## 2. Expected vs actual

- **Expected:** a read of `pkg.x` warns *Deprecated API*, as a call of a deprecated function does.
- **Actual:** no warning for a field assignment, whether global or local.

## 3. Root cause (read, not executed)

`LuaDeprecatedApiInspection.getDeprecatedTag` takes the tag from the `LuaCatsCommentOwner` that holds
the resolved element. An assignment statement is not one of the PSI types that owns a LuaCATS
comment, so a `---@deprecated` above `pkg.x = 1` is never found (BUG-513 review round 2, R2-A1).

## 4. Fix strategy

Not planned. Plan with `plan-bug`. It has to state which declaration forms count: a field
assignment, a `---@field` in a `---@class`, and a table-constructor key.
