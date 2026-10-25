---
id: "BUG-520"
title: "The require-based cross-file completion phase never returns anything, and its two tests cannot fail"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-520: the require-based cross-file completion phase is dead

Found in the third-pass review (`docs/review.md` §6, finding #100). Read and grepped, **not run**. It
reopens July review §1 #24 and MAINT-28 TC-24, which were closed on tests that cannot fail on this defect.

## 1. Reproduction (read, not run)

The phase is reached on every completion: `LuaCompletionContributor.kt:460` registers
`LuaCrossFileCompletionProvider`, which calls `addSymbolsFromFile` (`LuaCrossFileCompletionProvider.kt:189`)
for each required module.

Predicted: in `a.lua` write `local m = require("b")`, where `b.lua` declares a `function M.f()`
or a local function and returns it, then invoke completion. Nothing from the require phase is offered.

Mutation proof of the test claim: make `addSymbolsFromFile` a no-op. `LuaCrossFileCompletionOriginalFileTest`
("TC-24 cross-file require phase…") and `LuaCrossFileCompletionHeavyTest` ("…transitively required
globals") both stay green.

## 2. Expected vs actual

- **Expected:** symbols from modules the current file requires, including transitively required ones, are
  offered by the require phase; the two tests fail when that phase is removed.
- **Actual (by reading):** the phase produces no result at all, and `importedSymbolNames` is always empty.
  Both tests pass because they declare *global* `function` names, which phase 2
  (`GlobalSymbolRankingService.collectGlobalFunctions`, an allScope stub-index scan) offers regardless.

## 3. Root cause (read, not executed)

`LuaCrossFileCompletionScopeProcessor.kt:27-43` accepts only `LuaFuncDecl`, and it never receives one.
Two independent causes:

- **(a)** The provider calls `file.processDeclarations(processor, …, lastParent = file, place = file)`
  (`LuaCrossFileCompletionProvider.kt:189-196`). A file's `textOffset` is 0, so `LuaFile.processDeclarations`
  breaks on its first child (`LuaFile.kt:45-47`), and `LuaBlock.visibleBefore` (`LuaBlockExt.kt:27-31`)
  computes `indexOfFirst { textOffset >= 0 } == 0`, which hides every statement.
- **(b)** Even with a correct `lastParent`, `LuaBlock.processDeclarations` offers only `LuaLocalVarDecl`,
  `LuaLocalFuncDecl`, `LuaGlobalVarDecl` and `LuaGlobalFuncDecl` plus assignment-target `LuaVar`s
  (`LuaBlockExt.kt:68-87`), never `LuaFuncDecl`.

Cause (a) also empties `getLocalSymbolNames` (`LuaCrossFileCompletionProvider.kt:73-99`, the same `(file, file)`
call), so the deduplication in `GlobalSymbolRankingService.kt:156,231` never filters anything.

Adjacent, not the same defect: BUG-511 (global completion offers another file's class local), BUG-509 and
BUG-510 (locals indexed as globals) sit in the phase-2 code this phase feeds.

## 4. Not established

- **Self-`require` lead (unconfirmed).** Because `localSymbolNames` is always empty, a global `function foo()`
  defined in the *current* file reaches phase 2 as a lookup carrying
  `LuaAutoImportInsertHandler(targetFile = <current file>)`. Whether accepting it inserts a `require` of the
  file into itself depends on `LuaModulePathResolver` and `LuaDeduplicationChecker.isAlreadyRequired`. Neither
  guards `targetFile == context.file` that the reviewer could see, and no test covers it. If an executed test
  confirms a self-require, that is a destructive-edit P1 and becomes its own BUG.
- Whether the phase should be kept at all. The report recommends deleting it (see §5), but that is a decision
  for planning, not established here.

## 5. Fix strategy

Not planned. Plan with `plan-bug`. The review's verdict on the file is **delete**: its job is already done by
`GlobalSymbolRankingService`. The plan must make the "keep phase 1 or not" decision explicit, and must start
from the mutation proof in §1 (both existing tests green with the phase removed, a new test red).
