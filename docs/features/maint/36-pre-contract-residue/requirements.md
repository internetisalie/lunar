---
id: "MAINT-36"
title: "36: Pre-Contract Residue — Correctness, Dead Code and Contract Compliance"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-36: Pre-Contract Residue

Executes findings **#76–#92** of [`docs/review.md`](../../../review.md) §4 — the second-pass
review of files carrying substantial pre-engineering-contract code that the July 2026 sweep never
opened. The three P1s from that pass are filed separately as **BUG-412**, **BUG-413** and
**BUG-414**; everything else is here because it is the same handful of files touched over and
over, and splitting it further would produce seventeen one-line diffs across twelve files.

**Why these files:** `git blame` against the contract's landing date (2026-05-25) shows the
codebase is 27.5% pre-contract by surviving line, and that files the first review cited are 48.8%
pre-contract against 16.1% for everything else. The residue is the set of files ≥100 pre-contract
lines that the first pass did not name. See review §4.1 for the measurement and the command to
reproduce it.

## Scope

Twelve files: `lang/psi/types/{TypeParser,LuaStructuredTypes,LuaTypeGraphBridge}.kt`,
`lang/psi/{LuaFunctionExt,LuaFileElementType}.kt`, `lang/insight/LuaDocGenerator.kt`,
`lang/doc/LuaDocumentationTargetProvider.kt`, `lang/syntax/LuaLiterals.kt`,
`run/{LuaValue,LuaStackFrame,LuaDebugRunner,LuaDebugVariable}.kt`.

Explicitly **out of scope** — re-checked and clean, do not touch: `lang/syntax/LuaSyntax.kt`,
`lang/syntax/LuaHighlight.kt` (its `TextAttributesKey`s are already `val`),
`lang/syntax/LuaColorSettingsPage.kt`, `lang/format/LuaCodeStyleSettings.kt` (`@JvmField var` is
the mandatory `CustomCodeStyleSettings` idiom).

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-36-01 | Correct the `@param` name-mismatch fallback | M | Not Implemented | #76 — `LuaTypeGraphBridge:114-122` falls back to the tag's positional index when the name matches no parameter, so `---@param typo string` on `f(a, b)` types `a`. Skip the tag instead (the KDoc already claims it is ignored), or drop the claim if positional binding is intended |
| MAINT-36-02 | Fix union assignability in `LuaClassType` | S | Not Implemented | #77 — `isAssignableToInternal:54-71` seeds `visited` with `this.name` then recurses on `this` for each union arm, so the guard trips and **`LuaClassType.isAssignableTo(union)` is unconditionally false**. Latent today (no caller outside `lang/psi/types/`), which is exactly why it must be fixed before one arrives. The same shared-set flaw loses the second path of a diamond in `resolveMemberInternal:32-48` |
| MAINT-36-03 | Resolve references before project-wide type lookup | M | Not Implemented | #78 — `LuaDocumentationTargetProvider:45-56` runs `findTypeElement` **before** reference resolution, so hovering `local config = {}` documents an unrelated `---@class config` from another file. Precedence, not scoping: resolve first, fall back to the type index only on failure |
| MAINT-36-04 | Stop reporting expected failures as IDE fatal errors | S | Not Implemented | #79 — `LuaDebugRunner:78` raises `log.error` for a caught `ExecutionException` (bad interpreter, port in use) *in addition to* the notification the same handler shows. Downgrade to `warn`. Same pattern as #13/#14, missed because the file was never cited |
| MAINT-36-05 | Remove PSI retention from debugger values | M | Not Implemented | #80 — `LuaValue` is a `data class` whose `psiElement` participates in `equals`/`hashCode`, and it keys `LuaTable.named`. Session-lifetime tables retain hard `PsiElement` refs (contract rule 4) and key equality touches PSI a reparse can invalidate. Key on `String`, or hold a `SmartPsiElementPointer` if the element is needed at all |
| MAINT-36-06 | Dead-code removal | S | Not Implemented | #81 identical `if`/`else` arms ×2 in `LuaTypeGraphBridge:127-132,173-178`; #82 `LuaDebugVariable.isIndex`/`parent`/commented-out `evaluationExpression` *(coordinate with BUG-414)*; #83 the unused `indent` threaded through four `LuaDocGenerator` functions, and `buildFuncTemplate` ≡ `buildLocalFuncTemplate`; #89 `LuaTable`'s redundant secondary constructor; #90 three null checks on the non-null `PsiElement.text`, `arrayListOf` for immutable returns, the twice-inlined member-segment predicate |
| MAINT-36-07 | Performance pass | C | Not Implemented | #84 `LuaValue.compareTo` uses `entries.indexOf` where `ordinal` is free, inside `LuaTable.pairs()`'s comparator; #85 `TypeParser.parse` builds a throwaway `LuaFile` per call with no cache, per non-simple annotation per graph build; #86 `LuaFileElementType.extractExportedType` walks the whole file, calls `.text` per candidate, and walks `prevSibling` to the start of the file per root return without stopping at the first match — paid on every index pass; #87 `LuaStackFrame:59-85` takes a read action around a block that touches no PSI *(coordinate with BUG-414, which is the same subsystem's inverse defect)* |
| MAINT-36-08 | Contract compliance | C | Not Implemented | #92 — wildcard imports in `TypeParser:9`, `LuaDocGenerator:10`, `LuaFileElementType:12` (which also wildcard-imports its own package), `LuaDocumentationTargetProvider:27`, `LuaDebugVariable:27`; parameter cap exceeded in `LuaTypeGraphBridge.injectTypeAnnotation` (5), `injectParamAnnotations` (5), `injectReturnAnnotations` (4) and `LuaStackFrame`'s constructor (6); 30-logic-line cap exceeded by `resolveDocumentationTarget` (~50) and `extractExportedType` (~40), both of which also mix raw PSI traversal with orchestration |
| MAINT-36-09 | Deduplicate `processDeclarations` | C | Not Implemented | #88 — three near-identical implementations in `LuaFunctionExt.kt:18,59,95` differing only in receiver type and whether the declaration itself is executed last |

## Verification

Behaviour-preserving except where a requirement names a behaviour change (36-01, 36-02, 36-03,
36-05). Full suite green with no baseline regression; the corpus gate (MAINT-33/35) is the
backstop for 36-03 and 36-07, since both touch resolution paths the corpus exercises broadly.

**Each requirement needs a test that can fail.** 36-02 in particular is currently unreachable
from production code, so a fix without a direct unit test on `LuaClassType.isAssignableTo` proves
nothing.

## Follow-on

Review §4.6 argues the durable fix is enforcement, not another review pass: `Logger.error` on a
caught `ExecutionException` (36-04), PSI access outside a read action (BUG-414), and `PsiElement`
inside a `data class` (36-05) are each mechanically detectable. A detekt/lint rule per pattern
closes the class permanently; consider it a successor feature rather than scope here.
