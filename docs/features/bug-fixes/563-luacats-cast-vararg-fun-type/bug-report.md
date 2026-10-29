---
id: "BUG-563"
title: "A vararg `fun(...)` type in a `@cast` is read as a class named `fun`"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-563: A vararg `fun(...)` type in a `---@cast` is read as a class named `fun`

Found while implementing [[BUG-514]], which added `...` to the `@type`/`@field` lexer states but not to `@cast`'s. A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BaseDocumentTest`), `configureByText`, then the comment's PSI read. Executed 2026-10-28 on `20e318c4` with a throwaway probe test (not committed).

```lua
local x
---@cast x fun(...)
print(x)
```

## 2. Expected vs Actual
- **Expected**: the cast type is the vararg function type `fun(...)`, as under `@type` since BUG-514.
- **Actual**: no `PsiErrorElement`, but the cast type is the name `fun` and `(...)` is prose.

## 3. Context
- **Possibly relevant, not established**: `<TAG_CAST>` (`luacats.flex`) has no `{ELLIPSIS}` rule.
