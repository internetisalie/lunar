---
id: "BUG-502"
title: "A LuaCATS @param whose table type has an optional key (`{k?: T}`) silently becomes an LDoc tag, so the parameter disappears from the signature"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-502: `---@param opts? {indent?: number}` drops `opts`

Found while investigating [[BUG-501]] (lazy.nvim's `view/render.lua`, `M:markdown`).

## 1. Reproduction

One light fixture file (`BasePlatformTestCase`, `myFixture.addFileToProject`), one variant per line 2:

```lua
---@param a string
---@param opts? {indent?: number, prefix?: string}
---@param b string
function f0(a, opts, b) end
```

Read the attached comment with `LuaPsiImplUtil.getCatsComment(decl)` and list `paramTagList`, or dump
it with `DebugUtil.psiToString(comment, false, true)`.

Measured in BUG-501's probe P3 (`~/.cache/claude-scratch/lunar/bug-501/fix-run1/paramPsi.txt`, probe
source `Bug501ProbeTest.testParamPsi`):

| Line 2 | `paramTagList` |
| :-- | :-- |
| `---@param opts? {indent?: number, prefix?: string}` | `[a:string, b:string]` |
| `---@param opts {indent?: number}` | `[a:string, b:string]` |
| `---@param opts? {indent: number, prefix: string}` | `[a:string, opts:{indent: number, prefix: string}, b:string]` |
| `---@param opts? {indent: number}` | `[a:string, opts:{indent: number}, b:string]` |
| `---@param opts? table` | `[a:string, opts:table, b:string]` |

In the failing rows, the PSI dump shows the line parsed as `LDOC_PARAM_TAG` with a `DESCRIPTION` body
(`NAME('opts') SYMBOL('?') SYMBOL('{') NAME('indent') SYMBOL('?') SYMBOL(':') …`). No `PsiErrorElement`
is produced.

A user-visible case (lazy.nvim `v11.17.5`, `lua/lazy/view/render.lua:482`):

```lua
---@param str string
---@param hl? string
---@param opts? {indent?: number, prefix?: string, wrap?: boolean}
function M:markdown(str, hl, opts)
```

`self:markdown(msg.msg, hls[msg.level] or "LazyTaskOutput", { indent = 6 })` is then reported as
*Too many arguments: expected at most 2, got 3* (`LuaTypeAssignability`). The declared signature is
a contract (BUG-419), and it has only `str` and `hl`.

## 2. Expected vs Actual Behavior

- **Expected**: a LuaCATS `PARAM_TAG` for `opts` with the table type `{indent?: number, …}`. The
  function then declares three parameters, and the call above raises no diagnostic.
- **Actual**: the tag falls back to an LDoc `@param` with no error. `opts` is missing from the
  declared signature, and every call that passes it gets a false *Too many arguments* warning.

## 3. Context / Environment

- **Trigger**: an optional key `name?:` inside a table-literal type. The `?` on the parameter name is
  not involved (row 2 has none and still fails).
- **Before BUG-501's fix**, the false warning on `self:markdown(…)` came and went between JVMs,
  because the class sometimes lost `markdown` altogether. **After the fix it is reported on every
  run.** In BUG-501's probe P3, the reduced fixture reported it in 5 of 5 JVMs (that probe used an
  earlier version of the fix that resolves this case the same way). BUG-501's lazy.nvim sweep is now
  byte-identical across 24 runs. Whether this particular hit is in it was not dumped after the fix.
- **Possibly related, not established**: MAINT-39 DR-05 recorded 17 LuaCATS parse errors in lazy.nvim.
  The first class it lists is "optional `name?:` in a table-literal type or `fun` parameter"
  (`core/plugin.lua:16`, `pkg/rockspec.lua:13`, `view/float.lua:8`, `view/float.lua:28`,
  `view/init.lua:11`, `view/render.lua:19`). Those sites produce an error element, and this one does
  not. Whether one grammar gap explains both behaviours has not been checked.
- **Relevant files**: the LuaCATS grammar (`@param` tag and table-literal type rules),
  `LuaCatsDeclarations.paramTypes` (reads only `paramTagList`).
- **Lua version**: any. Seen at `LUA51`.
