---
id: INSP-04-DESIGN
title: Unreachable Code Design
type: design
parent_id: INSP-04
folders:
  - "[[features/inspections/04-unreachable-code/requirements|requirements]]"
---

# Technical Design: Unreachable Code (INSP-04)

A `LocalInspectionTool` that flags statements which can never execute — code after a
`return`, `break`, or `goto`, and dead branches the control-flow graph proves are
unreachable. It is the **first consumer** of the ANALYSIS-06 control-flow graph
(`net.internetisalie.lunar.analysis.controlflow`, already shipped); it adds **no** new CFG
machinery and depends only on the public accessors quoted in §3.

## 1. Architecture Overview

- **Inspection class** (to create): `net.internetisalie.lunar.analysis.inspections.LuaUnreachableCodeInspection`,
  extending `com.intellij.codeInspection.LocalInspectionTool`.
- **Quick-fix class** (to create): `net.internetisalie.lunar.analysis.inspections.LuaRemoveUnreachableCodeQuickFix`,
  implementing `com.intellij.codeInspection.LocalQuickFix`.
- Both live in the existing inspections package alongside `LuaGlobalCreationInspection`
  (`src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaGlobalCreationInspection.kt:1`),
  whose registration/override shape this design copies verbatim.
- The CFG is obtained per *scope owner* and cached; the inspection does no graph
  construction of its own.

### Grounding map (every external symbol → evidence)

| Symbol used by this design | Kind | Evidence (`file:line`) |
|---|---|---|
| `LocalInspectionTool` (class), `getShortName/getGroupDisplayName/getDisplayName/isEnabledByDefault/getDefaultLevel`, `buildVisitor` | base API / override shape | `analysis/inspections/LuaGlobalCreationInspection.kt:24` (class), `:26,28,30,32,34` (overrides), `:36` (buildVisitor) |
| `ProblemsHolder.registerProblem(...)`, `ProblemHighlightType` | report API | import `:7`; `registerProblem` call `analysis/inspections/LuaGlobalCreationInspection.kt:55` |
| `ProblemHighlightType.LIKE_UNUSED_SYMBOL`, `checkFile(file, manager, isOnTheFly): Array<ProblemDescriptor>?`, `manager.createProblemDescriptor(anchor, msg, isOnTheFly, EMPTY_ARRAY, type)` | file-level report API (prior art) | `analysis/inspections/LuaUnusedLocalInspection.kt:50` (`checkFile` nullable), `:77-83` (`createProblemDescriptor`), `:82` (`LIKE_UNUSED_SYMBOL`) |
| `LocalQuickFix` (class), `getFamilyName`, `applyFix`, `WriteCommandAction.runWriteCommandAction(project, name, null) { … }` | quick-fix API | `analysis/inspections/LuaGlobalCreationInspection.kt:82` (class), `:83` (familyName), `:85` (applyFix), `:89` (4-arg+lambda wrapper) |
| `LuaVisitor` (base visitor) | PSI visitor | import `analysis/inspections/LuaGlobalCreationInspection.kt:18`; subclass `:37` |
| `ControlFlowCache.getControlFlow(owner: ScopeOwner): LuaControlFlow` | CFG entry point | `analysis/controlflow/ControlFlowCache.kt:7` |
| `typealias ScopeOwner = PsiElement` | owner type | `analysis/controlflow/LuaControlFlow.kt:8` |
| `LuaControlFlow.isReachable(instruction): Boolean` | reachability test | `analysis/controlflow/LuaControlFlow.kt:11,17` |
| `ControlFlow.getInstructions(): Array<Instruction>` (`flow.instructions`) | node list | used `analysis/controlflow/LuaControlFlowBuilder.kt:66`; consumed `test/.../LuaControlFlowTest.kt:32` |
| `Instruction.getElement(): PsiElement?` (`inst.element`) | node→PSI mapping | platform `com/intellij/codeInsight/controlflow/Instruction` `getElement()`; used `test/.../LuaControlFlowTest.kt:32,42` |
| Owners the builder accepts: `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaFuncDef`, `LuaFile`, `LuaBlock` | valid `getControlFlow` arguments | `analysis/controlflow/LuaControlFlowBuilder.kt:23-49` |
| `LuaFinalStatement` (return), `LuaBreakStatement`, `LuaGotoStatement`, `LuaLabel`, `LuaStatement`, `LuaBlock` | PSI statement types | `src/main/gen/.../lang/psi/LuaFinalStatement.java:8`, `.../LuaBreakStatement.java`, `.../LuaGotoStatement.java`, `.../LuaLabel.java`, `.../LuaStatement.java`, `.../LuaBlock.java` |
| `LuaFile` | root PSI | `src/main/kotlin/.../lang/psi/LuaFile.kt` |
| `PsiTreeUtil.getParentOfType`, `findChildrenOfType` | tree walking | `analysis/inspections/LuaGlobalCreationInspection.kt:13,87` |

> **Foreign-symbol guard:** this design uses **none** of EmmyLua's names. There is no
> `Reachability` enum, no `getControlFlow(scopeOwner)` returning a generic `ControlFlow`
> with `.unreachable`, and no `Instruction`-level reachability flag — reachability is a
> *graph query* (`LuaControlFlow.isReachable`), exactly as the shipped CFG exposes it.

## 2. Core Components

### 2.1 `LuaUnreachableCodeInspection : LocalInspectionTool`

Override shape mirrors `LuaGlobalCreationInspection` (`…:24-36`):

```kotlin
class LuaUnreachableCodeInspection : LocalInspectionTool() {
    override fun getShortName(): String = "LuaUnreachableCode"
    override fun getGroupDisplayName(): String = "Lua"
    override fun getDisplayName(): String = "Unreachable code"
    override fun isEnabledByDefault(): Boolean = true
    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : LuaVisitor() {
            // visit each scope owner exactly once (see §3.2)
            override fun visitFuncDecl(o: LuaFuncDecl) = analyze(o, holder)
            override fun visitLocalFuncDecl(o: LuaLocalFuncDecl) = analyze(o, holder)
            override fun visitFuncDef(o: LuaFuncDef) = analyze(o, holder)
        }

    // File-level (top-level) statements. Signature & idiom copied verbatim from
    // LuaUnusedLocalInspection.kt:50 (NULLABLE return; null when file !is LuaFile or no problems).
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean):
        Array<ProblemDescriptor>?  // file-level owner; see §3.2
}
```

`checkFile` body (mirrors `LuaUnusedLocalInspection.kt:50-86`):

```kotlin
override fun checkFile(file, manager, isOnTheFly): Array<ProblemDescriptor>? {
    if (file !is LuaFile) return null
    val heads = unreachableHeads(file)              // §3.3, owner = file (LuaFile)
    if (heads.isEmpty()) return null
    return heads.map { stmt ->
        manager.createProblemDescriptor(
            stmt, "Unreachable code", isOnTheFly,
            arrayOf(LuaRemoveUnreachableCodeQuickFix()),   // LocalQuickFix[]
            ProblemHighlightType.LIKE_UNUSED_SYMBOL)
    }.toTypedArray()
}
```

`buildVisitor`'s three function-owner branches each call `analyze(owner, holder)`, which uses
`holder.registerProblem(...)` (function bodies are reported via the visitor; file-level via
`checkFile`). Both code paths share the same reachability core (§3.3) — only the report sink
differs (`ProblemsHolder.registerProblem` vs `manager.createProblemDescriptor`), exactly as the
two prior-art inspections do.

Private helpers (≤30 logic lines / ≤3 args each, per the engineering contract):

```kotlin
private fun analyze(owner: ScopeOwner, holder: ProblemsHolder)          // visitor path: registerProblem
private fun unreachableHeads(owner: ScopeOwner): List<LuaStatement>     // shared core, returns head statements (§3.3)
private fun isFirstUnreachableInItsBlock(stmt: LuaStatement, flow: LuaControlFlow): Boolean  // §3.3
```

### 2.2 `LuaRemoveUnreachableCodeQuickFix : LocalQuickFix`

```kotlin
class LuaRemoveUnreachableCodeQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Remove unreachable code"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor)  // deletes the flagged statement
}
```

`applyFix` resolves `descriptor.psiElement` to its enclosing `LuaStatement`
(`PsiTreeUtil.getParentOfType(element, LuaStatement::class.java)` — guard for the case where
the element *is* the statement) and deletes it inside
`WriteCommandAction.runWriteCommandAction(project, "Remove unreachable code", null) { stmt.delete() }`
(same wrapper used at `LuaGlobalCreationInspection.kt:89`).

## 3. Algorithms

### 3.1 What the shipped CFG already proves (and what it does NOT)

Verified by reading `LuaControlFlowBuilder.kt` and the passing
`LuaControlFlowTest.kt`. The graph **abrupts flow** (so following statements become
unreachable) at exactly these constructs:

| Terminator | PSI type | Builder evidence |
|---|---|---|
| `return …` (incl. bare `return`) | `LuaFinalStatement` | `visitFinalStatement` sets `isAbrupted=true`, `flowAbrupted()` — `LuaControlFlowBuilder.kt:250-256` |
| `break` | `LuaBreakStatement` | `visitBreakStatement` — `…:243-248` |
| `goto label` | `LuaGotoStatement` | `visitGotoStatement` — `…:258-264` |
| dead `if`/`else` branch (all arms abrupt) | `LuaIfStatement` arms | `visitIfStatement` propagates `isAbrupted` — `…:90-141`; covered by `LuaControlFlowTest.testSimpleBranchingReachability` |
| code after a loop with no fall-through *and* no fall-through after `break` | loop bodies | `LuaControlFlowTest.testWhileLoopWithBreak` |

**Out of scope for v1 — NOT modeled by the shipped CFG** (do not assume otherwise; verified
by the absence of any `visitFuncCall`/`error`/`os.exit` handling and the unconditional loop
exit edge):

- `error(...)` / `os.exit(...)` / `assert(false)` do **not** abrupt flow. `visitFuncCall`
  is not overridden, so a call statement is an ordinary node with successors. Treating these
  as terminators would require editing the shipped CFG builder and is deferred (see
  requirements INSP-04-C1 *Could* and `risks-and-gaps.md` §3, de-risking task DR-1).
- `while true do … end` with no `break` is **not** treated as non-terminating: the builder
  always adds the exit edge `addPendingEdge(whileStatement, condInst)`
  (`LuaControlFlowBuilder.kt:160`), so code after such a loop is reported *reachable*.
  Deferred identically (INSP-04-C2 *Could*, DR-1).

The inspection therefore reports **exactly** the unreachability the CFG models — no more,
no less. This keeps v1 grounded and zero-false-positive against the shipped engine.

### 3.2 Choosing scope owners (one CFG per owner, no double analysis)

`getControlFlow` accepts `LuaFile`, `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaFuncDef`,
`LuaBlock` (`LuaControlFlowBuilder.kt:23-49`). A function's body is analyzed by its **own**
owner CFG, and the builder's `visitFuncDef`/`visitFuncDecl` node-handlers stop flow from
descending into nested function *bodies* of the enclosing graph. To analyze every statement
exactly once and avoid nested-owner double counting:

1. **File-level statements** (top level, not inside any function): analyze via
   `checkFile`, passing the `LuaFile` as owner.
2. **Function bodies**: analyze via `buildVisitor` on each `LuaFuncDecl` /
   `LuaLocalFuncDecl` / `LuaFuncDef` (these are the owner types the builder branches on).

Because each owner's CFG only contains its own block's statements (nested function bodies
get their own owner pass), every reachable/unreachable decision is made by the smallest
enclosing owner — no statement is judged twice.

### 3.3 Core reachability pass (`analyze`)

Both report paths (visitor `analyze` and `checkFile`) call the shared core
`unreachableHeads(owner)`, which returns the *head* statement of each dead run:

```
fun unreachableHeads(owner) -> List<LuaStatement>:
    flow = ControlFlowCache.getControlFlow(owner)          # ControlFlowCache.kt:7
    heads = mutableListOf<LuaStatement>()
    seen  = mutableSetOf<LuaStatement>()
    for inst in flow.instructions:                          # ControlFlow.getInstructions(): Array<Instruction>
        element = inst.element ?: continue                  # Instruction.getElement()
        stmt = element as? LuaStatement ?: continue         # only statement-level nodes
        if stmt in seen: continue
        seen += stmt
        if flow.isReachable(inst): continue                 # LuaControlFlow.kt:11,17 (BFS from instructions[0])
        if isFirstUnreachableInItsBlock(stmt, flow):        # §3.3 head rule
            heads += stmt
    return heads

fun analyze(owner, holder):                                 # visitor path (function owners)
    for stmt in unreachableHeads(owner):
        holder.registerProblem(
            stmt,
            "Unreachable code",
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,          # greys out dead code, prior art LuaUnusedLocalInspection.kt:82
            LuaRemoveUnreachableCodeQuickFix())
```

Note: the `owner is LuaFuncDecl/…` self-body guard is **unnecessary** — §3.2 proves each
owner's CFG contains only its own block's statement instructions (the `LuaFile` graph does not
descend into function bodies; `visitFuncDecl` at `LuaControlFlowBuilder.kt:301` adds only a
name-WRITE node, not the body), so no statement is ever judged by a non-owning graph.

**Why statement-level only.** The builder calls `builder.startNode(stat)` for plain
statements (`LuaControlFlowBuilder.kt:84`), and for `break`/`return`/`goto`/`for`/`repeat`
it starts a node whose `element` IS that statement (`…:166,189,215,244,252,259`). So the set
of instructions with `element is LuaStatement` is exactly the statement-granularity of the
flow. Sub-expression instructions (`LuaReadWriteInstruction`, condition exprs) are skipped by
the `as? LuaStatement` filter, so we never highlight a fragment of a live statement.

**`isFirstUnreachableInItsBlock(s, flow)`** — to report the *head* of a dead run only (matching
JetBrains "Unreachable code" UX), highlight a statement `s` only when its previous sibling
`LuaStatement` in the same `LuaBlock` is reachable (or `s` is the first statement after a
terminator). Implementation: get `s`'s `LuaBlock` parent
(`PsiTreeUtil.getParentOfType(s, LuaBlock::class.java)`); within that block's
`getStatementList()` (`LuaBlock.java:13`) find the immediately preceding `LuaStatement` `p`;
if there is no such `p`, OR `p`'s own instruction (`flow.instructions.first { it.element == p }`)
is `flow.isReachable(...)`, then `s` is a head. (A preceding *reachable* statement means `s`
begins a fresh dead run; a preceding *unreachable* statement means `s` is a continuation, so
it is suppressed.)
Trailing dead statements after the head are left unhighlighted but are still removed by the
quick fix range (the fix deletes the single flagged statement; consecutive dead statements
each get their own head test only when the run is broken — in practice a contiguous dead run
shares one head, so we highlight the first and stop). This avoids N overlapping warnings on
`return; a(); b(); c()`.

**Highlight range (decided — no per-construct special-casing).** The reported anchor is always
the *whole* head `LuaStatement` PSI node — `registerProblem(stmt, …)` / `createProblemDescriptor(stmt, …)`
pass `stmt` itself. This applies uniformly to simple statements **and** to compound heads whose
CFG node's `element` is the statement (`for`/`repeat` — `LuaControlFlowBuilder.kt:166,189`). We do
**not** restrict the range to a loop keyword token or to the first child statement. Rationale: the
highlight type is `ProblemHighlightType.LIKE_UNUSED_SYMBOL` (`LuaUnusedLocalInspection.kt:82`), which
*greys out* its range rather than drawing an error/warning underline, so greying an entire dead
`for`/`repeat`/`while` block is exactly the JetBrains "Unreachable code" presentation and reads
correctly even across multiple lines. Compound statements that the builder does **not** give a
statement-`element` node (`LuaIfStatement`, `LuaWhileStatement` — `LuaControlFlowBuilder.kt:90-141`)
never appear as an `inst.element is LuaStatement` head, so the only multi-line head that can occur is
a dead `for`/`repeat`, and greying its whole node is the intended behaviour. TC-04-09 (added in
Phase 3) pins this with a dead `for`-loop asserting the warning range spans the loop statement.

### 3.4 Cycle / loop handling

`LuaControlFlow.isReachable` is a **BFS from `instructions[0]`** over `allSucc()` with a
`visited` set (`LuaControlFlow.kt:17-34`), so loops/back-edges terminate naturally and a
node inside a live loop is reported reachable. The inspection adds no traversal of its own —
it only *queries* `isReachable`, so cycle handling is entirely the engine's concern and is
already correct (proven by `LuaControlFlowTest.testWhileLoopWithBreak`,
`testNumericForLoopVariableWrite`).

### 3.5 `goto` / label handling

The builder wires `goto → label` edges in `build()` (`LuaControlFlowBuilder.kt:58-63`), so a
label that is the target of any reachable `goto` is reachable even if textually preceded by a
terminator (`LuaControlFlowTest.testGotoAndLabel` proves `::target::`'s following statement
is reachable while the statement between `goto` and label is unreachable). The inspection
inherits this for free via `isReachable`.

## 4. External Data & Parsing

None. INSP-04 consumes only PSI and the in-memory CFG; it reads no files, settings, or
external formats. (Contrast INSP-05, which reads `LuaProjectSettings`.)

## 5. Threading & Performance

- `buildVisitor`/`checkFile` run under the platform's inspection read action; all PSI/CFG
  access is read-only — no `WriteCommandAction` except inside the quick fix's `applyFix`
  (per the engineering contract, EDT-safe write).
- The CFG is memoized by `ControlFlowCache` via `CachedValuesManager.getCachedValue(owner)`
  keyed on `owner.containingFile` (`ControlFlowCache.kt:8-12`), so repeated inspection passes
  on an unchanged file reuse the graph. No `Project`/`PsiFile` hard refs are retained.

## 6. Error Handling / Edge Cases

| Case | Behavior |
|---|---|
| `inst.element` is `null` | skipped (`?: continue`) |
| empty function / empty file | no instructions of interest → no problems |
| statement after `break` but inside a still-reachable branch | reachable per CFG → not flagged |
| `goto`-reached label after a `return` | reachable per CFG → following code not flagged |
| nested anonymous function inside dead code | the dead *outer* statement is flagged once; the inner body is its own owner but is unreachable transitively — only the outer head is highlighted (§3.3 head rule) |
| `error()`/`os.exit()` followed by code | **not flagged** in v1 (see §3.1) |

## 7. Integration Points (`plugin.xml`)

Add to `src/main/resources/META-INF/plugin.xml` next to the other inspections (after the
**last** existing entry `LuaDeprecatedApi`, which ends at `plugin.xml:191`; the inspection
block spans `plugin.xml:137-191`). Match the existing literal `displayName`/`groupName` house
style — the repo does **not** use `key`/`groupKey`/`bundle` for these (verified across all 8
sibling entries, e.g. `LuaGlobalCreation` `plugin.xml:162-169`, `LuaDeprecatedApi` `:184-191`):

```xml
<localInspection
        language="Lua"
        shortName="LuaUnreachableCode"
        displayName="Unreachable code"
        groupName="Lua"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="net.internetisalie.lunar.analysis.inspections.LuaUnreachableCodeInspection"/>
```

`shortName` `LuaUnreachableCode` matches `getShortName()` in §2.1 (same convention as
`LuaGlobalCreation`).

## 8. Requirement Coverage

| Requirement | Covered by |
|---|---|
| INSP-04-01 CFG-based reachability | §3.1, §3.3 (`ControlFlowCache.getControlFlow` + `isReachable`) |
| INSP-04-02 Unreachable statement highlighting | §2.1, §3.3 (`registerProblem`, `ProblemHighlightType.LIKE_UNUSED_SYMBOL`), §7 |
| INSP-04-03 Remove-unreachable quick fix | §2.2 |
| INSP-04-04 Single head per dead run | §3.3 (`isFirstUnreachableInItsBlock`) |
| INSP-04-05 goto/label correctness | §3.5 |
| INSP-04-06 Owner-scoped once-only | §3.2 (`checkFile` for `LuaFile`, `buildVisitor` per function owner; per-owner graphs are disjoint) |
| INSP-04-C1 error()/os.exit() terminators (Could/Future) | §3.1 (deferred), DR-1 |
| INSP-04-C2 `while true` infinite loop (Could/Future) | §3.1 (deferred), DR-1 |
| INSP-04-C3 Statement-level inline suppression (Could/Future) | risks §3 (deferred); platform Suppress intentions work via `shortName` |

## 9. Test Strategy

Real-flow only (DoD gate, per CLAUDE.md Wave-4 note): `myFixture.enableInspections(LuaUnreachableCodeInspection())`
+ `myFixture.doHighlighting()` (assert `.description`/range) or `configureByText` with
`<warning>`/`LIKE_UNUSED_SYMBOL` markup + `myFixture.checkHighlighting()`. Test class
`LuaUnreachableCodeInspectionTest` in package `net.internetisalie.lunar.lang.insight`
(same package/idiom as `LuaGlobalCreationInspectionTest`). No engine-snapshot-only tests.
Concrete cases live in `requirements.md` → Test Cases.

## 10. Open Questions

None.

<!-- The two deferred capabilities (error()/os.exit() terminators and `while true`
non-termination) are NOT open questions: they are scoped out of v1 as Could/Future
requirements (INSP-04-C1/C2) and tracked as de-risking task DR-1 in risks-and-gaps.md.
The v1 design is fully specified against the shipped CFG. -->

