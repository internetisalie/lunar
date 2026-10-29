---
id: "BUG-550"
title: "The string argument of a call chained on `require(…)` is recognised as a require: `require(\"lazy.stats\").track(\"LazyDone\")` requires `LazyDone`"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-550: `require("a").f("text")` is counted as two requires, the second of `"text"`

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim` (MAINT-39 risks-and-gaps, DR-06).

## 1. Reproduction (executed in MAINT-39 DR-06)

The corpus sweep's require tally, with a temporary dump of each recognised require and the file it
resolved to, over `lazy.nvim` `v11.17.5` (`85c7ff37`) `lua/`:

```lua
require("lazy.stats").track("LazyDone")                       -- lua/lazy/init.lua:116
require("lazy.core.util").warn("You need to set `vim.g.mapleader` **BEFORE** loading lazy")
                                                              -- lua/lazy/manage/reloader.lua:74 (and :81)
```

- `"LazyDone"` is recognised as a required module name and resolves to
  `lua/lazy/manage/init.lua`, the first `init.lua` in the fixture ([[BUG-500]]'s fallback).
- The two `warn(…)` messages are recognised as required module names and stay unresolved.

## 2. Expected vs Actual
- **Expected**: one require per statement — `lazy.stats`, `lazy.core.util`. The argument of the
  call chained on the `require(…)` result is an ordinary string.
- **Actual**: the chained call's string argument is also recognised as a require. Of the 202
  requires the sweep recognises, 5 are unresolved, two of them these `warn` messages.

## 3. Context
- **Committed evidence**: `src/test/resources/corpus/lazynvim.baseline` records `requires=202` and
  `unresolvedRequires=5`, the counts DR-06 tallied.
- The editor's require reference (Go to Declaration on the string) and the corpus tally share one
  recognition (§4.1).
- **Corpus effect**: `requires` is identity-checked by the ratchet, so a fix re-records
  `lazynvim.baseline` in the fixing change.

## 4. Root Cause

### 4.1 Where a string is recognised as a require

| Site | Consumer | Recognition |
|------|----------|-------------|
| `lang/LuaRequireReferenceContributor.kt:56-63` (`isRequireCall`) | Go to Declaration / Find Usages / file-rename on the string (`plugin.xml:357-359`) **and** the corpus tally: `CorpusSweep.kt:343-359` counts the `LuaRequireReference`s this contributor hangs on every `LuaTerminalExpr` and `LuaArgs` | own copy: nearest `LuaFuncCall` ancestor whose `varOrExp.var.nameRef` is `require` |
| `lang/indexing/LuaFileBindingsIndex.kt:311-357` | `LuaFileBindingsRecord.requires` → `fileRequires` (`LuaRequireExtraction.kt:21`) → name resolution (`LuaNameReference.kt:92`) and cross-file completion (`LuaCrossFileCompletionProvider.kt:50,122`) | own copy: a `LuaFuncCall` whose `var.nameRef` is `require`, string taken from `nameAndArgsList[0]` |
| `lang/completion/LuaDeduplicationChecker.kt:40-46` | auto-import duplicate check (`LuaAutoImportInsertHandler.kt:43`) | own copy, same rule as the index |
| `lang/psi/types/LuaTypesVisitor.kt:126,912-913` | module type of a `require` call | own copy: the whole `varOrExp` text equals `require` |
| `lang/completion/LuaImportInserter.kt:113-121` | header placement of inserted imports | own copy: statement value is a `LuaFuncCall` on `require` |

The editor reference and the corpus tally share one recognition; the other four sites each have
their own. There is no module-name completion inside a require string: `LuaRequireReference`
overrides no `getVariants`, and neither registered `completion.contributor` (`plugin.xml:371-376`)
mentions `require`.

### 4.2 Why the chained argument matches

`funcCall ::= varOrExp nameAndArgs+`, `var ::= nameRef varSuffix*`,
`varSuffix ::= nameAndArgs* indexExpr` (`lang/psi/lua.bnf:299-312`). `LuaSuffixedExprParser`
(`lang/parser/LuaSuffixedExprParser.kt:15-21,114-160`) puts every call that is followed by an index
inside a `VAR_SUFFIX` of the `VAR`, and only the calls that end the chain directly under
`FUNC_CALL`. So `require("x").f("y")` is one node:

```
FUNC_CALL
  VAR_OR_EXP
    VAR
      NAME_REF  require
      VAR_SUFFIX
        NAME_AND_ARGS  ("x")
        INDEX_EXPR     .f
  NAME_AND_ARGS  ("y")
```

The hypothesis holds, and the defect goes further than the tally:
- **Contributor (the reported symptom).** `isRequireCall` checks only the chain's head name, so every
  string whose nearest `LuaFuncCall` is the chain matches: `"x"`, `"y"`, and any string in the
  chained call's table-constructor or function-body arguments (`track({ start = "startuptime" })`,
  `lua/lazy/stats.lua:24`). The test never asks which call segment holds the string.
- **Contributor, missed requires.** A chain that ends in an index (`local t = require("x").f`,
  `penlight/spec/permute_spec.lua:2`), or that sits inside another call's arguments
  (`lua/lazy/stats.lua:58`), has no `FUNC_CALL` of its own, so `"x"` gets no reference at all.
- **Index and dedup checker.** `nameAndArgsList[0]` is the last segment's `("y")`, so they record `y`,
  never `x`. lazy.nvim `lua/lazy/init.lua:116` puts `LazyDone`, not `lazy.stats`, in the file's
  `requires`.
- `require("x"):m("y")` holds both calls under `FUNC_CALL` (no index between them), so the
  contributor matches `"y"` there too. The index and the dedup checker pick `"x"` there, by luck.

`LuaTypesVisitor` compares the whole `varOrExp` text, so `require("x").f(...)` does not match there.
`LuaImportInserter` only decides where an import goes. Both are out of scope (§5.3).

## 5. Fix Strategy

### 5.1 One shared predicate

Add to the `LuaRequireReference` companion (`lang/LuaRequireReference.kt`, next to `moduleStringOf`):

```kotlin
/** True when [args] is the argument list of a call applied directly to the bare name `require`. */
fun isRequireArgs(args: LuaArgs): Boolean
```

It returns true when every one of the following holds:
1. `args.parent` is a `LuaNameAndArgs` whose `methodExpr` is null.
2. That `LuaNameAndArgs` is the first element of its parent's `nameAndArgsList`. The parent is a
   `LuaFuncCall` or a `LuaVarSuffix`.
3. There is a `LuaVar` whose `nameRef?.identifier?.text == "require"`, and:
   - when the parent is a `LuaFuncCall`, that `LuaVar` is `parent.varOrExp?.var` and its
     `varSuffixList` is empty;
   - when the parent is a `LuaVarSuffix`, that `LuaVar` is `parent.parent` and `parent` is
     `var.varSuffixList.first()`.

The rule needs no `LuaFuncCall` ancestor, which is why it also finds the missed requires in §4.2.

### 5.2 Callers

| Caller | Change |
|--------|--------|
| `LuaRequireReferenceContributor` | Replace `isRequireCall(element)` with `isRequireArgs(args)`, where `args = element as? LuaArgs ?: PsiTreeUtil.getParentOfType(element, LuaArgs::class.java)`. A null `args` gives `EMPTY_ARRAY`. Strings that already count inside the first call's arguments are unchanged. Delete the private `isRequireCall`. |
| `LuaFileBindingsIndexer.extractRequiresFromStatement` | Visit `LuaArgs` instead of `LuaFuncCall`, keep the call only when `isRequireArgs(args)`, then apply the existing string rule (`args.string`, or a single-element `exprList` holding a `LuaTerminalExpr`). Bump `LuaFileBindingsIndex.VERSION` from 4 to 5 (`LuaFileBindingsIndex.kt:126`), because the indexed values change. |
| `LuaDeduplicationChecker.collectRequirePaths` / `extractRequirePath` | The same change: visit `LuaArgs`, gate on `isRequireArgs`, keep `args.string ?: singleExprListString(args)`. |
| `CorpusSweep.tally` | No change. It consumes the contributor's references. |

All three run in the caller's existing read context (reference contributor, indexer, completion
insert handler), so no threading changes.

### 5.3 Out of scope
- `LuaTypesVisitor.isRequireCall` (`:126`) types `require("x"):m("y")` as module `x`, because its
  callee text is `require`. That is a type-flow question; this report does not establish it.
- `LuaImportInserter.isRequireCall` (`:113-121`) only places imports.

### 5.4 Expected corpus effect

`requires` is identity-checked (`CorpusGuards.kt:78-87`), so every member whose count moves has its
baseline re-recorded in the fixing change. `unresolvedRequires` is gated
(`CorpusMetrics.kt:290`), so a rise there must be explained, not just re-recorded.

| Member | Today | Direction |
|--------|-------|-----------|
| lazynvim | `requires=202`, `unresolvedRequires=5` | Down: the strings in chained-call arguments go (`LazyDone`, `LazyStart` at `lua/lazy/init.lua:77`, both `warn` messages, table-field strings). Up: index-ending or nested chains (`stats.lua:58,75`, `init.lua:140,146`, `view/render.lua:756`, …). `grep` cannot net the two; Phase 0 measures it. `unresolvedRequires` should drop by at least the 2 `warn` messages. |
| penlight | `requires=100` | Up: index-ending chains in swept roots (`lua/pl/stringx.lua:12`, `seq.lua:13`, `pretty.lua:15`, `utils.lua:441`, `spec/permute_spec.lua:2`, `spec/utils-enum_spec.lua:7`). Down: `"return "` in `spec/stringx_spec.lua:634`. |
| luacheck | `requires=152` | Up by 2 (`spec/format_spec.lua:1`, `spec/filter_spec.lua:1`, `require "m".f`). |
| luarocks | `requires=606` | Up by about 1 (`src/luarocks/vendor/dkjson.lua:72`). |
| zerobrane | `requires=19`, `unresolvedRequires=19` | Could rise (`src/util.lua:80`). Any new require there is probably unresolved too, because every current one is. |

Inspection counts can also move, because `fileRequires` feeds name resolution. They stay advisory
while `highlightFailures` is non-zero (`CorpusMetrics.kt:274-281`).

## 6. Test Strategy

**New light test** `src/test/kotlin/net/internetisalie/lunar/lang/LuaRequireChainedCallReferenceTest.kt`
(`BasePlatformTestCase`, JUnit4 runner like `LuaRequireStringCallReferenceTest`). It collects the
`canonicalText` of every `LuaRequireReference` in the file via `PsiTreeUtil.collectElements`, in
document order:

| Input | Expected module names |
|-------|-----------------------|
| `require("x")` | `[x]` (control) |
| `local m = require("x")` | `[x]` (control) |
| `require "x"` | `[x]` (control) |
| `require("x").f("y")` | `[x]` |
| `require "x".f "y"` | `[x]` |
| `require("x"):m("y")` | `[x]` |
| `require("x").f({ k = "y" })` | `[x]` |
| `local t = require("x").f` | `[x]` |
| `print(require("x").f)` | `[x]` |
| `require("x")("y")` | `[x]` |
| `t.require("x")` | `[]` (control) |

**Index test**: add to `LuaFileBindingsIndexTest`, which needs the heavy fixture because of
`file:`-scheme indexing (see its KDoc). `require("x").f("y")` must record exactly `[x]`.

**Dedup test**: in a light fixture, `LuaDeduplicationChecker.isAlreadyRequired(file, "x")` must be
true and `isAlreadyRequired(file, "y")` false for `require("x").f("y")`.

The existing `LuaRequireStringCallReferenceTest`, `LuaRequireRenameTest` and the
`LuaFileBindingsIndexTest` cases must stay green. The corpus gate runs with `-PwithCorpus`, and the
affected baselines are re-recorded with `-PrecordCorpusBaseline`.

## 7. Phase 0 probe (before any fix)

Add a temporary `BasePlatformTestCase` under `src/test/kotlin/net/internetisalie/lunar/lang/`, using
the `temporary-edits` skill. For each of `require("x").f("y")`, `require("x"):m("y")` and
`local t = require("x").f`, `configureByText` it and print two things:
`DebugUtil.psiToString(myFixture.file, false, true)` (`com.intellij.psi.impl.DebugUtil`), and the
`canonicalText` of every `LuaRequireReference` in the file. Run it alone through `gce-builder`
(`test --tests *<ProbeClass>*`).

Expected on unfixed code:
- The first snippet's tree has the §4.2 shape, and its names are `[x, y]`.
- The second snippet has both `NAME_AND_ARGS` directly under `FUNC_CALL`, and its names are `[x, y]`.
- The third snippet has no `FUNC_CALL`, and its names are `[]`.

**Stop rule:** stop and report back without fixing if any of the following happens:
- `"x"` sits under a nested `FUNC_CALL`;
- the chained names are not `[x, y]`;
- the index-ending snippet already yields `[x]`.

Any of these means §4.2 is wrong. When the probe matches, delete it before the fix.
