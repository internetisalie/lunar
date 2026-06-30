---
id: INSP-04
title: Unreachable Code Requirements
type: feature
parent_id: INSP
status: "done"
vf_icon: ✅
folders:
  - "[[features/inspections/requirements|requirements]]"
  - "[[features/inspections/04-unreachable-code/design|design]]"
  - "[[features/inspections/04-unreachable-code/implementation-plan|implementation-plan]]"
---

# Unreachable Code Requirements

A `LocalInspectionTool` (`LuaUnreachableCodeInspection`) that flags statements the
ANALYSIS-06 control-flow graph proves can never execute — code after `return`, `break`, or
`goto`, and dead `if`/`else` branches. Reachability is a **graph query**
(`LuaControlFlow.isReachable(instruction): Boolean`, `analysis/controlflow/LuaControlFlow.kt:11`)
— there is **no** `Reachability` enum in this repo (that is an EmmyLua name; do not use it).

## Scope

**In scope (v1):** the unreachability the shipped CFG already models — statements following
`return` (`LuaFinalStatement`), `break` (`LuaBreakStatement`), `goto` (`LuaGotoStatement`),
and branches where every arm abrupts flow. Reporting greys out the dead statement
(`ProblemHighlightType.LIKE_UNUSED_SYMBOL`) at inspection level `WARNING`, with a
"Remove unreachable code" quick fix.

**Out of scope (v1 — Could/Future):** treating `error()`/`os.exit()`/`assert(false)` as
terminators, and `while true do … end` (no `break`) as non-terminating. The shipped CFG does
not model these (design §3.1); they require editing `LuaControlFlowBuilder` and are deferred
to DR-1 (`risks-and-gaps.md`).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| INSP-04-01 | CFG-based reachability | Must | Full | Determine reachability via `ControlFlowCache.getControlFlow(owner)` + `LuaControlFlow.isReachable(inst)`; never re-implement the traversal (design §3.3). |
| INSP-04-02 | Unreachable-statement highlighting | Must | Full | A statement-level instruction (`Instruction.element is LuaStatement`) for which `isReachable` is `false` is reported via `ProblemHighlightType.LIKE_UNUSED_SYMBOL` at level `WARNING`, message `"Unreachable code"`, range = the statement's text range (design §2.1, §3.3). |
| INSP-04-03 | Remove-unreachable quick fix | Must | Full | `LuaRemoveUnreachableCodeQuickFix` deletes the flagged statement inside a `WriteCommandAction` (design §2.2). |
| INSP-04-04 | Single head per dead run | Must | Full | For a contiguous run of unreachable statements, highlight only the first (`isFirstUnreachableInItsBlock`, design §3.3); do not emit N overlapping warnings. |
| INSP-04-05 | goto/label correctness | Must | Full | A statement after a `return`/`goto` that is the target of a reachable `goto` (via a `::label::`) must NOT be flagged — inherited from the CFG's goto→label edges (design §3.5). |
| INSP-04-06 | Owner-scoped once-only analysis | Must | Full | Each statement is judged by exactly one CFG (its smallest enclosing owner). A single `checkFile` pass analyzes the `LuaFile` owner plus every `LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaFuncDef` (collected via `PsiTreeUtil.findChildrenOfType`); per-owner graphs are disjoint so no statement is judged twice (design §3.2). *(Implementation note: a single `checkFile` entry point is used rather than a `buildVisitor`+`checkFile` split — overriding both would suppress the default `buildVisitor`-driving `checkFile`.)* |
| INSP-04-C1 | `error()`/`os.exit()` terminators | Could | Future Work | Treat `error(...)`/`os.exit(...)`/`assert(false)` as flow terminators. Deferred — requires CFG-builder change (DR-1). |
| INSP-04-C2 | `while true` infinite-loop terminator | Could | Future Work | Treat `while true do … end` (no `break`) as non-terminating so trailing code is dead. Deferred — requires CFG-builder change (DR-1). |
| INSP-04-C3 | Statement-level inline suppression | Could | Future Work | A `--- @diagnostic`/`luacheck: ignore`-style statement-granular suppression. Deferred — `LuaInspectionSuppression` (`analysis/inspections/LuaInspectionSuppression.kt:43`) keys on `LuaNameRef`, not a statement. Platform "Suppress for statement/file" intentions work for free via `shortName` (risks §3). |

## Test Cases

Verification idiom: `myFixture.configureByText("a.lua", …)` with `<warning descr="Unreachable code">…</warning>`
markup + `myFixture.enableInspections(LuaUnreachableCodeInspection())` + `myFixture.checkHighlighting()`
(or `doHighlighting()` asserting `.description == "Unreachable code"` and the highlighted range).
Test class `LuaUnreachableCodeInspectionTest`, package `net.internetisalie.lunar.lang.insight`
(mirrors `LuaGlobalCreationInspectionTest`).

> **Lua syntax constraint (verified during implementation).** A `return`/`break` must be the **last**
> statement of its block — `return 1` immediately followed by more code is a *parse error* (the trailing
> code becomes a `PsiErrorElement`, not a `LuaStatement`), so it can never be flagged as unreachable.
> Genuine, syntactically-valid unreachable code therefore arises only when control abrupts inside a
> **nested** block and code follows at the enclosing level: `do return end` / `if … then return else
> return end` / a `goto` that skips a statement. The inputs below use those valid shapes.

### TC-04-01: Code after a returning `do` block (function body)
**Requirement:** INSP-04-01, INSP-04-02
**Input:**
```lua
function test()
    do return 1 end
    print("unreachable")
end
```
**Action:** Run `LuaUnreachableCodeInspection`.
**Expected:** Warning `"Unreachable code"` highlighting the statement `print("unreachable")`
(its full `LuaStatement`/`LuaExprStatement` text range). The `do return 1 end` block is NOT flagged.
The `print` statement is analyzed via the function's own owner CFG (design §3.2).

### TC-04-02: Reachable after `break` (no false positive)
**Requirement:** INSP-04-01
**Input:**
```lua
function test()
    while true do break end
    print("reachable")
end
```
**Action:** Run inspection.
**Expected:** **No** warning on `print("reachable")` — the loop's `break` adds a fall-through
edge to the statement after the loop, so the CFG reports it reachable
(`LuaControlFlowBuilder.kt:156-160`; proven by `LuaControlFlowTest.testWhileLoopWithBreak`).

### TC-04-03: Dead branch (all arms abrupt)
**Requirement:** INSP-04-01, INSP-04-02
**Input:**
```lua
function test(x)
    if x then return 1 else return 2 end
    print("unreachable")
end
```
**Action:** Run inspection.
**Expected:** Warning `"Unreachable code"` on `print("unreachable")` — both `if` arms abrupt
(`isAbrupted = branchAbruptedList.all { it }`, `LuaControlFlowBuilder.kt:140`), proven by
`LuaControlFlowTest.testSimpleBranchingReachability`.

### TC-04-04: Single head of a dead run
**Requirement:** INSP-04-04
**Input:**
```lua
function test()
    do return end
    print("a")
    print("b")
    print("c")
end
```
**Action:** Run inspection.
**Expected:** Exactly **one** warning, on `print("a")` (the head). `print("b")` and `print("c")`
are unreachable continuations and are NOT separately highlighted (design §3.3 head rule).

### TC-04-05: goto/label keeps target reachable
**Requirement:** INSP-04-05
**Input:**
```lua
goto target
print("skipped")
::target::
print("reached")
```
**Action:** Run inspection (file-level owner via `checkFile`).
**Expected:** Warning `"Unreachable code"` on `print("skipped")` (between `goto` and label);
**no** warning on `print("reached")` — the goto→label edge makes it reachable
(`LuaControlFlowBuilder.kt:58-63`; proven by `LuaControlFlowTest.testGotoAndLabel`).

### TC-04-06: Remove quick fix
**Requirement:** INSP-04-03
**Input:** TC-04-01's source (caret placed inside `print("unreachable")`).
**Action:** Invoke the "Remove unreachable code" quick fix on the warning.
**Expected:** `print("unreachable")` statement is deleted; `do return 1 end` and the surrounding
function remain syntactically valid.

### TC-04-07 (negative): `error()` is NOT a terminator in v1
**Requirement:** INSP-04-C1 (documents deferral)
**Input:**
```lua
function test()
    error("boom")
    print("after error")
end
```
**Action:** Run inspection.
**Expected:** **No** warning on `print("after error")` in v1 — `error()` is an ordinary call
node with successors in the shipped CFG (no `visitFuncCall` override). This is intentional
(design §3.1); flagging it is C1/DR-1 future work.

### TC-04-08: Owner-scoped once-only (nested function)
**Requirement:** INSP-04-06
**Input:**
```lua
local function outer()
    do return end
    local function inner()
        print("inner dead too")
    end
end
```
**Action:** Run inspection.
**Expected:** Exactly **one** warning, on the head statement after `do return end` (the
`local function inner() … end` declaration). The inner body is NOT separately flagged: the
outer owner's CFG does not descend into `inner`'s body, and `inner`'s own owner CFG is only
built/queried when it is itself reachable — the single outer head warning covers the dead run
(design §3.2, §3.3 head rule). Confirms no double-counting across owners.

### TC-04-09: Dead compound loop highlights the whole statement
**Requirement:** INSP-04-04 (highlight range — design §3.3 "Highlight range", DR-2)
**Input:**
```lua
local function f()
    do return end
    for i = 1, 10 do
        print(i)
    end
end
```
**Action:** Run inspection.
**Expected:** Exactly **one** warning, text `"Unreachable code"`, and its highlight range spans the
**entire** `for i = 1, 10 do … end` statement node (not just the `for` keyword and not the first
child `print(i)`). Confirms the decided uniform whole-`LuaStatement` range for a compound `for`/`repeat`
head greyed via `LIKE_UNUSED_SYMBOL`.

### TC-04-10: File-level unreachable code (checkFile owner)
**Requirement:** INSP-04-06
**Input:**
```lua
do return end
print("unreachable")
```
**Action:** Run inspection.
**Expected:** Exactly **one** warning on `print("unreachable")` — top-level statements are analyzed
via the `LuaFile` owner CFG (design §3.2). Confirms the `checkFile` path fires for file-level dead code.
