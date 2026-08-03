---
id: "BUG-389"
title: "`require \"mod\"` (string-call form) contributes no reference — no Go to Definition"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-389: `require "mod"` (string-call form) contributes no reference — no Go to Definition

Lua's paren-less call sugar `require "mod"` / `require [[mod]]` produces **no**
`LuaRequireReference`, so Go to Definition, Ctrl-click, Find Usages and everything else built on
that reference silently do nothing on the module name. Only `require("mod")` works.

The indexer sees the string-call form, so **cross-file completion works while navigation does
not** — the two require extractors disagree.

## 1. Reproduction

1. Open a Lua file containing both call shapes, with `other.lua` present in the project:
   ```lua
   local a = require "other"
   local b = require("other")
   ```
2. Ctrl-click (or Go to Definition) on `"other"` in each line.

Line 2 navigates to `other.lua`. Line 1 does nothing — there is no reference at the caret at all.

## 2. Expected vs Actual Behavior

- **Expected**: both call shapes contribute a `LuaRequireReference` and resolve identically.
  Lua treats `f "s"`, `f [[s]]` and `f("s")` as the same call.
- **Actual**: only the parenthesized form contributes a reference. The string-call form is inert
  for navigation, while `LuaFileBindingsIndexer` *does* record it — so completion and the require
  graph see a module that navigation cannot reach.

## 3. Context / Environment

- **Confidence**: high — root-caused in code, and measured across a real corpus.
- **Root cause**: the grammar admits three argument shapes —
  `args ::= '(' [exprList] ')' | tableConstructor | STRING`
  (`src/main/kotlin/net/internetisalie/lunar/lang/parser/lua.bnf:303`). In the string-call form the
  argument is a **bare `STRING` token under `args`**, never wrapped in an `expr` /
  `LuaTerminalExpr`.
  - `src/main/kotlin/net/internetisalie/lunar/lang/LuaRequireReferenceContributor.kt:23` bails
    immediately: `if (element !is LuaTerminalExpr) return PsiReference.EMPTY_ARRAY`, then reads
    `terminal.string`. It can therefore only ever match the `exprList` (parenthesized) shape.
  - By contrast `src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaFileBindingsIndex.kt:326`
    checks `args.string` **first** and only falls back to the `exprList` walk — so the index
    handles both shapes.
- Origin: MAINT-30-03 introduced `LuaRequireExtraction` as "the single canonical require extractor
  for a consumer", but the consolidation covered `LuaNameReference` and
  `LuaCrossFileCompletionProvider` — not `LuaRequireReferenceContributor`, which kept its own
  narrower AST assumption.

### Measured impact

Found by the MAINT-33 corpus sweep on its first run. Over upstream luacheck v1.2.0 (132 files),
Lunar recognised **3** require references; luacheck writes `require "x"` at ~152 sites and
`require("x")` at 3. Over luarocks v3.12.2 (159 files), which uses the parenthesized form, it
recognised 606. The string-call form is idiomatic and widespread — this is not an edge case.

## 4. Other Notes

- **Fix direction**: register the reference provider for the bare `STRING` element under `args` as
  well (guarding on the same `require` callee check), and route both shapes through one extractor
  so the contributor and the indexer cannot drift again. `LuaRequireExtraction`'s stated intent
  already covers this.
- Cover `[[long bracket]]` strings too — `args ::= … | STRING` admits them, and
  `LuaRequireReferenceContributor` already trims `[`/`]`/`=` when building the module name.
- **Verification**: unit tests for `require "m"`, `require [[m]]`, `require("m")`, then re-run the
  corpus ratchet (`tooling/corpus/fetch-corpus.sh`;
  `tooling/gce-builder/gce-builder.sh run "test --tests *Corpus* -PwithCorpus"`). luacheck's
  `requires` should rise from 3 to ~155. Because `requires` is an identity-checked metric, the
  baselines in `src/test/resources/corpus/` must be re-recorded with `-PrecordCorpusBaseline`.
