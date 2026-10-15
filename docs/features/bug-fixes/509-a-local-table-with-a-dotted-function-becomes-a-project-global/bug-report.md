---
id: "BUG-509"
title: "A name declared `local` in a file is indexed as a project-wide global by a later `function` statement"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-509: a `local` name is indexed as a global by a `function` statement on it

Found by TARGET-11's prototype (DR-11). Blocks TARGET-11: a shipped library must not hide real
errors in user code.

**Invariant: a name declared `local` in a file is never a project-wide global**, whatever form the
later `function` statement takes. The global-declaration index broke it for every form below.

## 1. Reproduction

`LuaUndeclaredVariableInspection` on. `decl.lua` declares, `user_code.lua` reads the name
(`local v0 = <name>`); the name must be reported undeclared.

| Form in `decl.lua` | Read name | Unfixed |
| :-- | :-- | :-- |
| `local f` / `function f() end` | `f` | silent (bare name) |
| `local function f() end` / `function f() end` | `f` | silent (bare name) |
| `local M = {}` / `function M.x() end` | `M` | silent (root of a dotted name) |
| `local N = {a={}}` / `function N.a.b() end` | `N` | silent |
| `local C = {}` / `function C:m() end` | `C` | reported |
| the dotted function inside an `if` block under a file-level `local S` | `S` | silent |
| `local function g(P)`, `function g(P)` or `function(P)` with `function P.x()` / `function P()` inside | `P` | silent |
| `local a, M = 1, {}` (second name), `local M <const> = {}` | `M` | silent |
| `for K in pairs(t) do function K.x() end end`, and `for I = 1, 2 do …` | `K`, `I` | silent |

Neovim's `coxpcall.lua:43/62` (`performResume`) and luassert's `mock`/`stub` are the real-library
instances of the bare-name form; `vim/_core/stringbuffer.lua` of the dotted form.

Controls that must stay global: `function early() end` **before** `local early`; `function E.x() end`
before `local E`; a `local B` in an unrelated earlier `do … end` block; a `local T = function()
function T.x() end end` (inside its own initialiser `T` is still the global); `function vim.x()`
with no `local vim`; `function D.f()` with `D` a plain global; a global assigned in another file.

## 2. Expected vs actual

- **Expected:** every name in the table above is reported undeclared in `user_code.lua`; every
  control stays declared.
- **Actual:** the `local` names are silent, because a project file's `function` statement made them
  global.

## 3. Root cause

`LuaFuncStubElementType.indexStub`
(`src/main/kotlin/net/internetisalie/lunar/lang/psi/stubs/impl/LuaFuncStubElementType.kt`) sinks
into `LuaGlobalDeclarationIndex` the declaration's **full name** (`:70`) and, for a dotted name,
also its **root** (`:73-75`), without asking whether that first segment is bound by a `local` in
the file. For a bare `function f()` the full name *is* the root, so the first sink alone makes `f`
global. `LuaNameReference.doMultiResolve`'s bare-name lookup then returns those declarations for a
read of the name in any other file, and `LuaUndeclaredNames.isUnresolvedNonGlobal` counts a
non-empty result as declared.

`function R:m()` sinks only the full name `R:m`, which a bare read of `R` does not match, so the
method form was already correct.

## 4. Fix strategy

Record on the stub whether the name's **first segment** is bound by a file-local declaration in
scope at the declaration, and decide the sinks from it:

| Name form | Full-name entry | Root entry |
| :-- | :-- | :-- |
| bare `f` | skipped when local | (same entry) |
| dotted `R.a…`, method `R:m` | kept | skipped when `R` is local |

The dotted and method full names (`R.a`, `R:m`) stay indexed: the type engine resolves class
members through them (`LuaClassType.resolveMember`), and a qualified lookup of them is
BUG-508's, not this report's.

- `LuaFuncStub.rootIsLocal` / `LuaFuncStubImpl`: a new last constructor parameter; serialized as a
  trailing boolean. `LuaFileElementType.getStubVersion()` `4` → `5`.
- The scope test is pure PSI and reads no index: a walk from the declaration up its ancestors. A
  `LuaBlock` binds the name when one of its statements **ending before the declaration starts** is a
  `local` / `local function` declaring it (so a function before the `local`, a `local` in a sibling
  block, and the `local`'s own initialiser are all unbound); a function body binds its parameters;
  a `for` statement binds its loop variables.
- **Linear per file:** each block's `name → end offset of its first local declaration` is computed
  once and cached on the block (`CachedValuesManager`), so a file of thousands of top-level
  `function vim.x()` declarations (Neovim's `vimfn.gen.lua`) is not rescanned per declaration.

**Do not use `LuaScopeProcessor` / `LuaResolveUtil.scopeCrawlUp`.** Executed in TARGET-11's
prototype: that walk calls `LuaFuncDeclImpl.getBlock()` (`@NotNull`) on a partially parsed
declaration and throws while indexing `runtime/standard/lua-5.1/debug.lua`, failing 652 tests; with
that guarded it binds `M` to a field write `M.new = …` before reaching `local M`.

## 5. Test strategy

`net.internetisalie.lunar.definitions.LuaLocalNameIsNotGlobalTest` (a `LibraryRootTestCase`): one
case per row of §1 and per control, plus a library-root case (`vim/a.lua` global, `mod/log.lua`
local). Each reads the names from a second file and compares the list of
`Undeclared variable '<name>'` descriptions. Mutations per branch are recorded in the
handoff.

## Resolution

Delivered as §4. `LuaFuncNameBinding.rootIsLocal` (pure PSI, per-block cached) feeds
`LuaFuncStub.rootIsLocal`; `LuaFuncStubElementType.indexStub` withholds the bare full name and the
dotted root when it is set. Stub version `4` → `5`. `LuaLocalNameIsNotGlobalTest` has 23 cases. The
first 17 were run against the unfixed tree: 9 red (the local-name forms), 8 green (the controls and
the already-correct method form). The other 6 were added afterwards and never ran unfixed; each is
shown falsifiable by a deletion mutation instead (parameters of a non-local function and of a
closure by `else -> false` in `bindsName`; a multi-name `local` by first-name-only in
`declaredNames`; the attributed `local M <const>` is a shape pin with no dedicated mutation). The
full suite passes: 3198 tests, 0 failures, 0 errors, 4 skipped. The corpus ratchet did not move.

**Not delivered by this fix:** `function self.x()` inside a method. `self` is an implicit
parameter and so a file-local binding, but `bindsName` reads only declared parameter names, so
`self` is still sunk as a root and a bare `self` read in another file resolves to it. Pre-existing
and not made worse; the invariant above is therefore delivered for every explicitly declared name,
not for implicit `self`.

## Out of scope

- A `---@class` local (BUG-510), a different lookup.
- A qualified `M.clear()` resolving to another file's `function M.clear` (BUG-508, the
  full-name key), unchanged by this fix.
- Implicit `self` as a root (see Resolution).
- Lua 5.5: a later `global M` after `local M = {}` makes `M` global again, but the stub still
  records it as local.
- The `for` arms bind the loop variable for the whole statement including its header expressions;
  Lua scopes it to the body only (pathological).
- `LuaDeclarationSite.boundName` and `computeFileScopeLocalNames` overlap with `directNameRef` /
  `collectLocals`; they are not reused because this rule is order-sensitive and per block, so the
  assignment index and the declaration index now disagree on `function early() end; local early`.
