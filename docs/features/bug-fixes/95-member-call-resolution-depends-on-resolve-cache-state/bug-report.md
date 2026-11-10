---
id: "BUG-95"
title: "A member call whose name has two untagged declarations resolves to one target or to none, depending on which tests the run selects"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-95: A member call whose name has two untagged declarations resolves to one target or to none, depending on which tests the run selects

Found by MAINT-56's TC-8 (risks-and-gaps DR-14): the same test code classified one luacheck call
differently depending only on which test methods the invocation selected.

## 1. Reproduction
The relevant files of the luacheck corpus member (`test/corpus/luacheck`, commit
`cc089e3f65acdd1ef8716cc73a3eca24a6b845e4`, `tooling/corpus/corpus.json`):

`spec/samples/defined.lua`, where `foo` is a global table:
```lua
foo = {}

function foo.bar()
   baz()
end
```

`spec/samples/unused_code.lua`, where `foo` is a local table:
```lua
-- Foo is unused- only accessed circularly
local foo = {}

function foo.bar(baz)
   -- …
end
```

`spec/samples/defined2.lua`:
```lua
foo.bar()
```

Resolve the `bar` name reference of `foo.bar()` in `defined2.lua` with `reference.resolve()`. Do it once
in a JVM that has run the MAINT-54 audit's luacheck member alone, and once in a JVM that runs all five
members:
- `tooling/gce-builder/gce-builder.sh run "test -PwithTypeAudit --rerun --no-build-cache --tests '*FalseNegativeAudit*.testLuacheckMember'"`
- `tooling/gce-builder/gce-builder.sh run "test -PwithTypeAudit --rerun --no-build-cache --tests '*FalseNegativeAudit*'"`

Luacheck is the first test method in both runs. In `build/type-audit/luacheck.json`, the call is counted
under `noTag` when `resolve()` returned `defined.lua`'s `foo.bar`, and under `unresolvedCallee` when it
returned null.

## 2. Expected vs Actual Behavior
- **Expected**: `foo.bar()` resolves the same way in every run, whichever tests the run selects.
  The member also assigns the global `foo` elsewhere (`spec/samples/defined4.lua:2`,
  `spec/samples/global_inline_options.lua:2`, `spec/samples/read_globals_inline_options.lua:5`,
  `spec/globals_spec.lua:12`, `spec/parser_spec.lua:1482`), but only `defined.lua` declares a global
  `foo.bar`, so the call should resolve to it and never to the local table's `foo.bar` in
  `unused_code.lua`.
- **Actual**: the result depends on the run. Running luacheck alone gives `defined.lua`'s `foo.bar`
  (`unresolvedCallee` 1776, `noTag` 1976). The five-member run of the MAINT-56 Phase 2 test code gives
  nothing (1777, 1975) in two runs out of two. The five-member run of the base commit `5ee2660e` gives
  `defined.lua`'s `foo.bar` in two runs out of two.
- `LuaNameReference.doMultiResolve` asks `LuaMemberResolution.typedTargets` first. One target makes
  `resolve()` return it. With no typed target, the text path returns both `LuaGlobalDeclarationIndex`
  entries for `foo.bar`, and `resolve()` returns null for two results. The `multiResolve` KDoc already
  notes that a member name's answer can depend on when it is asked.

## 3. Related
[[BUG-418]]: corpus sweep counts shift with the invocation shape, deterministically. This is one
instance of the same effect inside a single member. MAINT-56 TC-8 excludes this call site
(risks-and-gaps DR-14).
