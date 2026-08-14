---
id: "BUG-440"
title: "Quick Doc returns \"No documentation found\" for openresty library members, while love2d works"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-440: Quick Doc finds nothing for `ngx.*`, and the inferred types are degraded too

Found by COMP-09's live IDE verification (2026-08-14) while judging checklist scenario 2.3.
It is filed separately because it is **not** about the type text that scenario is about — it
is the fallback that scenario's verdict silently depends on.

## Reproduce

Enable `LuaCATS/openresty` and `LuaCATS/love2d` from the shipped catalog
(`src/main/resources/definitions/lunar-definitions-catalog.json`; both sha256-verified in the
run below). In a Lua file, complete each receiver and press **Ctrl+Q** on a member.

| Member | Declared as | `Ctrl+Q` result |
| :-- | :-- | :-- |
| `love.getVersion` | `function love.getVersion()` in `love.lua` | ✅ `function love.getVersion() : number, number, number, string`, **with per-return docs** |
| `ngx.status` | `---@field status ngx.http.status_code` in `ngx.lua` | ❌ **"No documentation found."** |
| `ngx.say` | `function ngx.say` in `ngx.lua` | ❌ **"No documentation found."** |

love2d is the control: same mechanism, same catalog, same session — it works. Every openresty
member tried failed.

## The corroborating symptom, which points at the layer

The inferred type after insertion is degraded on exactly the same members:

```lua
local b = ngx.status   -- inlay: nil | string     (declared ngx.http.status_code)
local z = ngx.say      -- inlay: fun(...)         (a real function declaration)
```

So this is **probably not a documentation-rendering defect**. Quick Doc says "No documentation
found" when it has no resolved target to document, and the inlays say the target is not
resolving properly either. **Treat that as a hypothesis and measure it** — find out whether
`resolveGlobal`/`resolveType` returns the openresty declaration at all before touching any doc
provider. This feature has been burned repeatedly by plausible readings of the wrong layer.

A lead worth checking first: `ngx.status`'s declared type is `ngx.http.status_code` — a
**nested-qualified** type name. Nested qualifiers are exactly the shape [[BUG-430]] (`a.b.c = v`
flattens onto the root) and [[BUG-439]] (sibling-file members never enumerated) live in, and
`ngx.lua` mixes all three declaration forms: 28 `---@field`, 44 `function ngx.X`, 78 `ngx.X =`.
Establish which form fails before assuming all of them do.

## Why it matters beyond one library

COMP-09 deliberately serves index-arm members with **no type text** (design §4.13, declared in
the CHANGELOG). The live verification accepted that trade — but explicitly on the grounds that
**one keystroke restores the signature**. Where Quick Doc returns nothing, the user is left with
a bare name and no way to recover the type: the row reads `status`, the right column is blank,
and `Ctrl+Q` is empty. The trade is only cheap while this works.

That does **not** argue for putting a type back into the index value — it argues for fixing
this. See scenario 2.3's recorded result in
[human-verification-checklists.md](../../completion/09-member-enumeration/human-verification-checklists.md).

## Blast radius: unmeasured

Two libraries were tried. `love2d` works, `openresty` does not. Nothing establishes which of the
other catalog entries behave which way, or whether the split follows declaration form, file
layout, or something else. **Measure the catalog rather than generalising from n=2.**
