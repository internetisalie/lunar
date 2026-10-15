---
id: "BUG-510"
title: "A `@class K` tag on `local K = {}` makes the variable `K` resolve in every file, so an undeclared `K` elsewhere goes unreported"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-510: a bare-name read resolves through the class-name index to another file's `local`

Found by the TARGET-11 prototype (DR-11), after BUG-509's fix. Blocks TARGET-11 for the same
reason: a name declared `local` in a file is never a global anywhere else. A class *type* `K` is
project-wide; the *variable* `K` is not. `$S` = `~/.cache/claude-scratch/lunar/target-11`; every run
on the libvirt builder with `test --rerun --no-build-cache`, source in `$S/r3/results/<run>/tree.diff`.

## 1. Reproduction (executed)

`LuaUndeclaredVariableInspection` on (probe `Target11R3Bug510ProbeTest`, a `LibraryRootTestCase`).
Project `cls.lua` = `---@class K` / `local K = {}` / `return K`; library root `mod/iter.lua` =
`---@class Iter` / `local Iter = {}` / `return Iter`; `user_code.lua` = `local a = K` /
`local b = Iter`. Unfixed (`m-510-base`):

```
[probe:b510] undeclared=[]
```

The same holds with BUG-509's fix, which does not touch this path (`l-509fix`: `K` and a
library `Iter` silent).

On the real libraries with BUG-509's fix, Neovim's `uv`, `Iter`, `ArrayIter`, `CompletionResolver`
and openresty's `cjson` stay silent, each resolving to a `LuaLocalVarDeclImpl` (`local uv = {}`
under `---@class uv` in `uv/_meta.lua`) with `indexKey(uv)=0` in `LuaGlobalDeclarationIndex`
(`g3-proto-full/probe.txt`).

The `---@alias` form leaks the same way: `modes.lua` = `---@alias Mode string` / `local Mode = {}`
leaves a bare `Mode` in another file unreported (§5, `testALocalAliasHostIsNotAGlobal`, red on the
unfixed tree).

## 2. Expected vs actual

- **Expected:** `K`, `Iter` and `Mode` are reported undeclared in `user_code.lua`. `C`, from
  `gcls.lua` = `---@class C` / `C = {}` (a global), stays declared. `K` stays usable as a type in
  another file's `---@type K` and `---@param x K`.
- **Actual:** `K`, `Iter` and `Mode` are not reported.

## 3. Root cause (executed)

`LuaNameReference.doMultiResolve`'s bare-name phase looked the read name up in `LuaClassNameIndex`
and `LuaAliasIndex` (`src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt:134-154` at
`cb05af0f`) and returned the `LuaLocalVarDecl` that hosts the tag, in any file. The only sink into
either index is `LuaLocalVarStubElementType.indexStub` (`:131-132`), so **every** entry of both
indexes is a `local`; neither lookup could ever return a genuine global. With the class block
deleted, §1's fixture gives
`[probe:b510] undeclared=[Undeclared variable 'K', Undeclared variable 'Iter']` (`n-510-fix`).
On the real libraries, with the block deleted on top of BUG-509's fix (`i-mut-classidx`), Neovim's
silent set falls to `_ENV`, `_G`, `performResume`, `vim`, `assert` and openresty's loses `cjson`.
`C` stays declared through the global-assignment lookup (`:167-183` at `cb05af0f`, BUG-391), not
the class index.

Both blocks were added together by [[DOC-06]]'s **DOC-06-05 "Cross-File Resolution"**
(`c980948d`; `docs/features/documentation/06-documentation-indexing/requirements.md`, status Full:
"Resolve types and classes defined in other files"). That requirement is about *types*, and it
still holds without the blocks: the type engine (`LuaTypeManagerImpl.doResolveType`), Go to Class,
the type hierarchy and quick documentation all read `LuaClassNameIndex`/`LuaAliasIndex` directly.
DOC-06-05's own tests (`TestLuaTypeResolution.testResolveClassCrossFile`, `testClassMerging`,
`testInheritanceResolution`, named in `design-type-map.md` M2/M4) go through
`LuaTypeManagerImpl.resolveType` and stay green. Only the *variable* resolution goes.

## 4. Fix strategy

Delete both lookups (`LuaClassNameIndex` and `LuaAliasIndex`) from `LuaNameReference.doMultiResolve`'s
bare-name phase, leaving a comment that says why they are absent. No other code changes.

## 5. Test strategy

`net.internetisalie.lunar.definitions.LuaLocalClassIsNotGlobalTest : LibraryRootTestCase`, with
`LuaUndeclaredVariableInspection` enabled.

| Case | Expected | Mutation that turns it red (executed) |
| :-- | :-- | :-- |
| `testALocalClassTableIsNotAGlobal` | §1's `cls.lua` and library `mod/iter.lua`: `Undeclared variable 'K'` and `'Iter'` both present | restore the `LuaClassNameIndex` lookup |
| `testALocalAliasHostIsNotAGlobal` | `---@alias Mode string` / `local Mode = {}`: exactly `Undeclared variable 'Mode'` | restore the `LuaAliasIndex` lookup |
| `testAGlobalClassTableStaysDeclared` (control) | `---@class C` / `C = {}` read as `C` in another file: no warning | disable the BUG-391 global-assignment lookup |
| `testALocalClassStaysATypeInAnotherFile` (control) | `---@type K` and `---@param x K` in another file complete `K`'s field `name` | stop `LuaTypeManagerImpl.doResolveType` resolving `K` |

## 6. Resolution

Fixed as §4. The two defect cases were red on the unfixed tree (`K`/`Iter` case: `[]`; `Mode` case:
`expected:<[Undeclared variable 'Mode']> but was:<[]>`) with both controls green, and each of the
four mutations in §5 turned exactly its own case red. Full suite with the fix (`test --rerun
--no-build-cache`): 3179 tests, 0 failures, 4 skipped. Corpus gate (`-PwithCorpus`): unchanged, no
`IMPROVED` or regression line — the pinned corpus carries no LuaCATS tags (MAINT-39 adds one), so it does
not reach this path.

**User-visible impact.** New, correct *Undeclared variable* warnings appear wherever code reads
another file's `---@class`/`---@alias` local as a bare name without `require`-ing it — for example
openresty's `cjson` and Neovim's `uv`, `Iter`, `ArrayIter` and `CompletionResolver`. The class or
alias stays usable as a type everywhere.

The pattern `---@class K` / `local K = {}` / `_G.K = K` used to resolve in other files by accident
and is now reported undeclared too, because an `_G.x =` assignment is not modelled as declaring a
global.

**Not covered here.** A `local helper` later assigned by `function helper() end` is a different code
path (`LuaFuncStubElementType`); BUG-509 owns it (`testBareFunctionAfterLocalIsNotAGlobal`). Global
completion still offers another file's class local as a bare value with no `require`, so accepting
it now raises this warning at once; BUG-511 owns that.
