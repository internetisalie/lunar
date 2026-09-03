---
id: NAVIGATION-13
title: "13: Colon Call Site Resolution"
type: feature
parent_id: NAV
status: "planned"
priority: "high"
folders:
  - "[[features/navigation/requirements|requirements]]"
---

# NAV-13: a colon call site resolves to the method it calls

Completes [[NAV-12]]'s stated Non-Goal — *"Receiver type-narrowing resolution (resolving
`local p = package; p.path` via `p`'s inferred type)"* — for the colon form, and is the capability
[[REFACT-09]] is blocked on.

## Why this is filed now, with the measurement that forced it

`obj:m()` does not resolve to its method. `LuaNameReference` keys on the receiver's **text**, so
`local b = Builder; b:setName()` never reaches `function Builder:setName`
(`.agents/AGENTS.md`, "Type engine" §3). The consequence is not merely a missing Go-to-Declaration:

- **`ReferencesSearch.search(<colon declaration leaf>, allScope)` returns 0 in every receiver
  shape**, the `---@class`-annotated one included. First measured by `REFACT-09-00-DR-02`;
  re-measured for this feature by `NAV-13-00-DR-01` at `f1ac26cc` over every receiver shape it
  enumerates, all still 0.
  `LuaNameReferenceSearcher` does scan colon call sites, but gates on `isReferenceTo`, which
  resolves; a colon call site resolves to nothing, so the gate never passes.
- With no usage set, [[REFACT-09]] had to prove a rename's completeness **syntactically**. Its
  predicate is sound — a Step 9 reviewer attacked it across every aliasing shape they could
  construct without breaking it — and it **accepts 0 of 941** colon-method declarations across the 734-file corpus. 929 of
  those are refused by two or more clauses independently, so no single relaxation helps; deleting
  the whole escape set accepts 394 with no completeness evidence at all, which is
  `REFACT-01-00-DR-03`'s measured half-rename.

**So the missing capability is the reference direction, not the rename.** [[TYPE-13]] made a member
yield its declaration (`LuaMemberDeclarations.declarationOf`); what does not exist is a call site
that resolves *to* it.

### And what a colon member name resolves to today is worse than nothing

`NAV-13-00-DR-01` measured, over every colon call site in the pinned corpus and in the annotated
substitute, what `nameRef.reference.resolve()` returns for the member name today
(`risks-and-gaps.md` DR-01, table 3). It is non-null at **441 of the corpus's 14 116** colon call
sites and at **89 of the substitute's 2 446** — and every binding is to a *lexical* name: a local
variable, a local function, or a global function declared in another file. `t:m()` desugars to
`t.m(t)`; `m` is a table key and never a variable, so a lexical binding for it is unsound by the
language definition, not merely unhelpful. `NAV-13-04` and `NAV-13-05` below are written against
that measurement.

## Scope

### In Scope
- `obj:m()` resolving to the `function Obj:m()` that declares it, for the receiver shapes measured
  to reach a declaration (`risks-and-gaps.md` DR-01, table 1): a plain local table, an in-file
  global table, `setmetatable`-based OO through its supertype chain, and — the shape with by far
  the widest reach — a `---@class` / `---@type` **annotated** receiver, including its aliased form
  `local b = Builder`.
- `isReferenceTo` answering true for those sites, so `LuaNameReferenceSearcher`'s existing scan
  admits them and `ReferencesSearch` returns a usage set.
- Go to Declaration and Find Usages following from that, per NAV-01/NAV-02's existing routes.
- **Withdrawing** today's lexical bindings for a colon member name: a colon call site resolves to
  its method declaration or to nothing, never to a same-named variable or global function.

### Out of Scope
- The rename itself — that is [[REFACT-09]], which this unblocks.
- Receiver shapes measured as reporting **no** declaration: a `require`d module
  ([[TYPE-13]] Gap 2.11), a chain's second segment (Gap 2.12), an alias (`local u = t; u:m()`), a
  parameter receiver, and the empty-`upSet` shapes — factory-returned tables, `self` receivers and
  nested constructors (Gap 2.7). Each is an engine merge change [[TYPE-13]] design §8 puts out of
  scope. Each resolves to **nothing** here.
- Receivers that are not a bare name: `a.b:m()` and `("s"):m()`. Measured to reach no declaration
  under any candidate handle rule (`risks-and-gaps.md` DR-01, table 2). `f():m()` belongs with the
  chain rather than here — `funcCall ::= varOrExp nameAndArgs+` is greedy, so its `:m()` is a second
  segment (`design.md` §3.3 Refusal B).
- Widening `visitFuncCall` beyond `nameAndArgsList.firstOrNull()`.
- A member declared other than by `function Obj:m()` — a table-constructor field
  (`local t = { m = function() end }`) or a dotted assignment (`t.m = function() end`). Both
  resolve to a declaration in the type engine; neither is a `LuaDeclarationSite`, so admitting them
  would produce a Go-to target with no reachable usage set (`design.md` §3.4).
- A **cross-file un-annotated** receiver. `LuaTypesSnapshot` is per file, so `Obj:m()` in another
  file than `function Obj:m()` reaches no declaration; measured in `risks-and-gaps.md` Gap 2.2.
  An annotated receiver *does* resolve cross-file, because `LuaTypeManagerImpl` searches
  `GlobalSearchScope.allScope`.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `NAV-13-01` | **A colon call site resolves** | **M** | Not Implemented | `t:m()` resolves to the method-name leaf of the `function t:m()` that declares it, for every in-scope receiver shape. |
| `NAV-13-02` | **`isReferenceTo` admits it** | **M** | Not Implemented | The declaration leaf and the call site agree, so `LuaNameReferenceSearcher`'s gate passes with no change to the searcher. |
| `NAV-13-03` | **`ReferencesSearch` returns the usage set** | **M** | Not Implemented | The measured 0 becomes the call sites that bind. This is the row [[REFACT-09]] consumes. |
| `NAV-13-04` | **An unreachable receiver resolves to nothing, not to something wrong** | **M** | Not Implemented | Every Out-of-Scope shape returns null rather than a plausible-but-wrong target. [[TYPE-13]] Gap 2.12 measured the chain case returning a *silently wrong* value, which is the failure mode to avoid. |
| `NAV-13-05` | **No resolution regression, and the lexical bindings are withdrawn deliberately** | **M** | Not Implemented | Dotted and qualified-name resolution (NAV-01/02/12) is untouched — the change is confined to a `LuaNameRef` whose parent is a `LuaMethodExpr`. Within that set, today's 441 corpus / 89 substitute lexical bindings are withdrawn, and that withdrawal is the requirement, not a side effect. Its downstream consequences are **enumerated by execution** in `design.md` §7 — every route that reaches a colon member name was recorded from inside the branch rather than inferred from a gate, and every element-taking API was then driven at **both ends** of the binding the withdrawal breaks: at the call site and at the same-named declaration that used to own it. Every consequence is scoped in: by `NAV-13-07`'s exception where it is an inference change, and by `NAV-13-08` where it is user-visible. |
| `NAV-13-06` | **Cost** | **M** | Not Implemented | Resolution runs on every reference and the type engine itself resolves colon member names mid-build. `LuaTypeGraphRootResolutionBudgetTest`'s committed budgets must hold **unchanged**, and the new un-annotated gate must hold at the value it is committed with. A budget regression is disqualifying, not tunable. |
| `NAV-13-07` | **No inference change, with one scoped exception** | **M** | Not Implemented | Every diagnostic the type engine reports before, it reports after, and the corpus ratchet reports no `Corpus regression:` line. **The one exception**: TYPE-10's expected-callback seeding stops firing where it fired only because the pre-feature resolve returned a whole `LuaFuncDecl` node nested inside another one, seeding a lambda parameter from the *enclosing* function's `---@param`. That seeding is unsound, its withdrawal is a fix, and it is pinned by case 19 rather than the ratchet — the corpus carries 0 `---@` tags and structurally cannot observe it. No other inference change is permitted. |
| `NAV-13-08` | **Every user-visible consumer change is enumerated by execution, in both directions, and scoped in** | **M** | Not Implemented | The consumer set is drawn by instrumenting the branch and recording callers, not by reading gates for a missing `LuaMethodExpr` case; and every **element-taking** API is then driven at **every name leaf of the fixture**, not only at the call site and the colon-method declaration leaf. `design.md` §7 carries both halves. Each observed effect is stated as intended or prevented, and every user-visible one is pinned by a unit fixture, because the corpus carries 0 `---@` tags and can observe none of them. **Gained at the call site**, and intended: `LuaDeprecatedApiInspection` **withdrawing** a call-site warning that named a lexical binding the call does not make, and **raising** one where the call now resolves to a `---@deprecated` method; `LuaRenameProcessor` refusing a colon call site instead of retargeting a same-named declaration; Quick Documentation **gaining** the method's doc where the site had none **and retargeting** from a same-named local function / parameter / global function's doc to the method's where it had one; and parameter-name inlay hints appearing where a same-named stdlib global suppressed them. **Received rather than requested**, and equally user-visible: three registered extensions are *handed* the resolved element by `CommonDataKeys.PSI_ELEMENT` and name no resolve API at all. <kbd>Shift+F6</kbd> at a colon call site no longer starts `LuaInplaceRenameHandler`'s **inline rename template** on a same-named file-local declaration — the platform now hands the handler a `METHOD_FUNCTION` leaf, which its `isFileLocal` gate declines, so `PsiElementRenameHandler` and the colon-method refusal take over; **Type Hierarchy** at a colon call site declines instead of opening on a `---@class` local that merely shares the member's name; and `LuaTargetElementEvaluator` is unchanged, answering `UNSURE` on both sides. A fourth, `LuaRenameConflictDetector`, is reached from `LuaRenameProcessor.findCollisions` and consumes the *usage list*: a rename conflict raised by the colon call site is withdrawn with the usage. **Withdrawn from the same-named declaration**, also intended and equally user-visible: that declaration — of any kind `LuaDeclarationSite` classifies, measured on `LOCAL_VARIABLE`, `LOCAL_FUNCTION`, `GLOBAL_FUNCTION`, `PARAMETER` and on a receiver sharing the member's name — loses the colon call site from its `ReferencesSearch` result, so **Find Usages returns one fewer usage**; `LuaSafeDeleteProcessor`, which searches the same references, stops blocking on it; **Shift+F6 on that declaration stops rewriting `t:m()`**, which today is a half-applied rename in the [[BUG-457]] class; and `LuaUnusedLocalInspection` reports it unused where the colon member name was its only use (`LOCAL_VARIABLE` and generic-`for` variables by default, `PARAMETER` when `checkParameters` is enabled). The withdrawal happens even where the call resolves to **nothing**, so the usage is lost with no counterpart gained — case 29. `LuaFindUsagesProvider.canFindUsagesFor` is unchanged at every leaf. **This list is what `risks-and-gaps.md` DR-06's sweep drove, and its surface set is derived from `plugin.xml` by a two-half rule — consumers that *call* a resolve API and consumers that *receive* the resolved element from a platform data rule** — plus the residue of helper files that rule reaches through. `design.md` §7 states the rule, drives every consumer it yields, and names the three classes of consumer it still cannot see. The **fixture** set remains the open half. |

`NAV-13-06` is a `Must`, not the `Should` this feature was filed with: `NAV-13-00-DR-02` measured
the un-guarded design taking `WRITE` from 572 to 812 on the existing BUG-473 fixture and reddening
every budget method that predates it, so the budget is a correctness gate for this feature rather than a
performance preference.

## Behaviour Rules

- **A colon member name is a table key, never a variable.** `t:m(...)` is sugar for `t.m(t, ...)`
  (Lua 5.4 manual §3.4.10), and the grammar spells it `methodExpr ::= ':' nameRef`
  ([lua.bnf:300](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)). So the
  resolution of that name is a *member* lookup on the receiver's type, exclusively. This is what
  makes `NAV-13-05`'s withdrawal a correctness fix rather than a trade.
- **Failure is null, and null is the default.** Every clause is written *accept only if*; a shape
  nobody enumerated resolves to nothing. Refusing is the direction in which a mistake costs a
  missing feature rather than a wrong navigation target or a half-rename.
- **The engine must never be answered from the snapshot under construction.** `LuaTypesVisitor`
  resolves every colon call's member name while building the snapshot
  ([LuaExpectedCallbackResolver.kt:48](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaExpectedCallbackResolver.kt)).
  Answering that call from the snapshot under construction is a cycle the engine's fixpoint does not
  model, and it is the entire measured cost regression. `design.md` §3.6 refuses it. The refusal is
  **not** value-preserving: where the pre-feature resolve returned a nested `LuaFuncDecl` node, the
  engine seeded a lambda parameter from the enclosing declaration's `---@param`, and the refusal
  withdraws that. `NAV-13-07` scopes the withdrawal in; case 19 pins it.
- **Resolution never crosses a file for an un-annotated receiver, and always may for an annotated
  one.** The two routes differ in scope by construction, not by accident; both are pinned.

## Test Cases

Every row names the mutation that turns it red and states where that mutation was executed. The
`Then` column is transcribed output from the prototype run recorded in `risks-and-gaps.md` DR-02,
not an expectation. Offsets are the ones that run printed on each row's own fixture.

**One `configureByText` per test method.** `LuaTypeManagerImpl` searches
`GlobalSearchScope.allScope(project)`, so a sibling fixture declaring the same class name binds an
arm to the wrong file and manufactures a false result ([[TYPE-13]] requirements, case 17).

| # | Requirement | Given | When | Then | Mutation that turns it red |
|---|---|---|---|---|---|
| 1 | `NAV-13-01` | `local t = {}` ; `function t:m() end` ; `t:m()` | `nameRef.reference.resolve()` on the call site's `m` | the IDENTIFIER leaf `m@24` — the `funcNameMethod` name of `function t:m()` | delete the colon branch from `LuaNameReference.multiResolve` (design §3.6) → **executed**, `resolve = null` |
| 2 | `NAV-13-01` | `Obj = {}` ; `function Obj:m() end` ; `Obj:m()` | as #1 | the leaf `m@22` | mint the method member with `declaresMember = false` ([LuaTypesVisitor.kt:848](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)) → **executed**, `resolve = null`. **No mutation separates this row from #1** — `declareFileGlobals` as a no-op, [[TYPE-13]] case 4's discriminator, was executed here and left this row resolving at `m@22`, because a colon call site resolves through the *call receiver* and [[TYPE-13]] Gap 2.9 measured that handle surviving the mutation. The row is a reach pin for the global shape; `risks-and-gaps.md` "Test Case Gaps" records why it cannot be more |
| 3 | `NAV-13-01` | `local Class = {}` ; `Class.__index = Class` ; `function Class:m() end` ; `local o = setmetatable({}, Class)` ; `o:m()` | as #1 | the leaf `m@54` — the declaration, reached through the supertype chain | mint the method member with `declaresMember = false` → **executed**, `resolve = null`. The same mutation reddens #1 and #2 and leaves #4 green, so it also separates the structural route from the nominal one. **`m@54` cannot be the call-site `LuaMethodExpr@97` under any mutation**: `methodNameLeafOf`'s `as? LuaFuncDecl` cast refuses a `LuaMethodExpr` structurally, which `risks-and-gaps.md` DR-02 Finding 6 executed |
| 4 | `NAV-13-01` | `---@class Builder` ; `local Builder = {}` ; `function Builder:setName(n) end` ; `local b = Builder` ; `b:setName("x")` | as #1, for `setName` | the leaf `setName@54` | drop the union-arm loop from `declarationLeaves` (design §3.4) → the receiver's type is `{ … } \| Builder` and `LuaUnionType.resolveMember` requires **every** arm to carry the name ([LuaComplexTypes.kt:14-15](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaComplexTypes.kt)), so the plain call misses. **Executed**: the plain route reports `resolveMember=MISS` on this exact fixture |
| 5 | `NAV-13-01` | `---@class Builder` ; `local Builder = {}` ; `function Builder:setName(n) end` ; `---@type Builder` ; `local b` ; `b:setName("x")` | as #1 | the leaf `setName@54` | as #1. The row exists because a `---@type` receiver and #4's `local b = Builder` alias are different receiver spellings; only #4 was measured to need the union-arm loop, and this row pins that the declared-type spelling resolves too |
| 6 | `NAV-13-01` | `cls.lua` = `---@class Builder` ; `local Builder = {}` ; `function Builder:setName(n) end` ; `return Builder` — and `useb.lua` = `---@type Builder` ; `local b` ; `b:setName("x")` | as #1, on `useb.lua`'s call site | the leaf `setName@54` **in `cls.lua`** | as #1. This is the only row that crosses a file, and it does so through `LuaTypeManagerImpl`'s `allScope` lookup rather than through `LuaTypesSnapshot` |
| 7 | `NAV-13-02`, `NAV-13-03` | as #1 | `ReferencesSearch.search(m@24, allScope(project))` | one reference, whose element is the call site's `LuaNameRef`, and `isReferenceTo(m@24)` is true for it | as #1 → **executed pre-change at `f1ac26cc`**, `references=0` for this fixture and for every other in DR-01 table 3 |
| 8 | `NAV-13-03` | `local t = {}` ; `function t:m() end` ; `local u = {}` ; `function u:m() end` ; `t:m()` ; `u:m()` | `ReferencesSearch` on each declaration leaf | each declaration returns exactly its own call site (`m@24`→the `t:m()` site, `m@56`→the `u:m()` site); neither returns the other's | as #1 → **executed pre-change**, `references=0` for both. This row is a **pin against a future widening**, not independent acceptance: no mutation makes the two receivers merge, because the lookup starts from the receiver's own type node |
| 9 | `NAV-13-04` | `local A = {}` ; `function A:go() end` ; `local B = {}` ; `function B:go() end` ; `function A:next() return B end` ; `A:next():go()` | as #1, on the **second** segment `go` | null. (The first segment `next` resolves, to `next@77`.) | drop `if (call.nameAndArgsList.firstOrNull() !== nameAndArgs) return null` from `receiverOf` (design §3.3) → the receiver becomes `A`, whose own member `go` is `function A:go()` — a plausible-but-wrong target for a call whose real target is `function B:go()` |
| 10 | `NAV-13-04` | `local a = {}` ; `a.b = {}` ; `function a:m() end` ; `function a.b:m() end` ; `a.b:m()` | as #1 | null | drop `if (receiverVar.varSuffixList.isNotEmpty()) return null` from `receiverOf` → the receiver becomes the bare head `a`, whose own member `m` is `function a:m()` — again plausible and wrong |
| 11 | `NAV-13-04` | `local t = { m = function() end }` ; `t:m()` | as #1 | null | drop the `as? LuaFuncDecl` cast in `methodNameLeafOf` (design §3.5) and return `declarationOf`'s value → `LuaFieldImpl@12`. **Executed**: `LuaDeclarationSite.kindOf` of that field's name leaf is `null`, so `LuaNameReferenceSearcher` would refuse it as a search target and the site would navigate with no usage set |
| 12 | `NAV-13-04` | `local t = {}` ; `t.m = function() end` ; `t:m()` | as #1 | null | the same mutation as #11 → `LuaAssignmentStatementImpl@13`, whose `LuaDeclarationSite.identifierLeafOf` is the **receiver** `t`, not `m` |
| 13 | `NAV-13-04`, `NAV-13-05` | `function m() end` ; `local function f(x) x:m() end` | as #1 | null | make the colon branch fall through to the ordinary two-phase resolution when it finds nothing (design §3.6, Alternative F) → the global `function m()` is offered as the declaration of a parameter receiver's method |
| 14 | `NAV-13-05` | `local t = {}` ; `function t:m() end` ; `local m = 1` ; `t:m()` ; `print(m)` | as #1 | the leaf `m@24`, **not** the local `m@38` | as #13. **Executed pre-change**: this fixture resolved to `LeafPsiElement@38`, the local |
| 15 | `NAV-13-04` | `local C = {}` ; `function C:b() end` ; `function C:a() self:b() end` — and the factory, alias, parameter and `require`d-module fixtures of `risks-and-gaps.md` DR-01 table 1 | as #1 | null in every one | **no NAV-13-side mutation exists, deliberately** — see the note below the table |
| 16 | `NAV-13-06` | `local t = {}` ; `function t:m(n) end` and 80 × `t:m("aN")` | build the snapshot, then resolve every call site twice | all 80 resolve on both passes; `RootAccessor.WRITE` / `READ` stay within the budgets `implementation-plan.md` Phase 3 commits | remove the `isSnapshotUnderConstruction` guard from `declarationLeafOf` (design §3.6) — measured in DR-02 to take the *annotated* 80-site fixture's `WRITE` from 572 to 812 and redden the budget methods that predate this feature |
| 17 | `NAV-13-06` | the existing `annotatedCallSiteFixture()` in `LuaTypeGraphRootResolutionBudgetTest` | every budget method that predates this feature, unchanged | `WRITE` ≤ 600 / 620 and `READ` ≤ 1 000 / 700 as committed today | as #16 → **executed**, both methods failed at `WRITE` = 812 and 814 |
| 18 | `NAV-13-07` | the pinned corpus | `test -PwithCorpus` | no `Corpus regression:` line ([CorpusGuards.kt:53-54](../../../../src/test/kotlin/net/internetisalie/lunar/corpus/CorpusGuards.kt)) | none at fixture level — this is a no-change requirement whose gate is the ratchet, per the argument below |
| 19 | `NAV-13-05`, `NAV-13-07` | `---@param cb fun(a: string)` ; `function outer(cb)` ; `function m(q) end` ; `end` ; `local t = {}` ; `t:m(function(z) return z end)` — **plus**, as a separate method, the dot-call control `t.m2(function(z2) return z2 end)` with the same enclosing shape | build the snapshot, then read the inferred type of the lambda parameter via `LuaTypesSnapshot.forFile(file).graphTypeToLuaType(getValueType(zRef)).name` | `z` is **`unknown`** — the same value the dot-call control gives on both sides of the change | delete the colon branch from `LuaNameReference.multiResolve` (as #1) → **executed at `30052d62`**, `z` infers **`string`**, seeded from `outer`'s `---@param`, while the dot control stays `unknown` (`risks-and-gaps.md` DR-03) |
| 20 | `NAV-13-06` | the DR-04 ring fixture — `K` files, file *i* declaring `---@class Ci` with method `mi` and carrying 10 call sites on a `---@type C(i+1 mod K)` receiver — at `K` = 2, 4, 8, 16 | resolve every site, then sum `rootResolutionCount(WRITE)` and `(READ)` over **every** file's `LuaTypesSnapshot` | every site resolves, and the summed `WRITE` and `READ` each exceed the K-proportional baseline by **exactly one per resolved site**, at every K — so the per-site delta does not grow with `K` | remove the `isSnapshotUnderConstruction` guard (as #16); and separately, any change that makes the per-site delta a function of `K`. **Executed** pre-feature and under the prototype at every `K` listed (`risks-and-gaps.md` DR-04) |
| 21 | `NAV-13-08` | `---@deprecated Use the method instead` ; `local function m() end` ; `local t = {}` ; `function t:m() end` ; `t:m()` ; `m()` | enable `LuaDeprecatedApiInspection`, `myFixture.doHighlighting()`, read every `HighlightInfo` whose description is `Deprecated API: Use the method instead` | exactly the offsets `85..86` and `99..100`. The call site `95..96` carries **no** warning | delete the colon branch from `LuaNameReference.multiResolve` (as #1) → **executed**, a third warning appears at `95..96`, because `resolve()` on `m@95` reverts from `LeafPsiElement@85` (the method's name leaf) to `LeafPsiElement@53` (the deprecated local function) |
| 22 | `NAV-13-08` | two methods, one `configureByText` each: (a) `local t = {}` ; `---@deprecated gone` ; `function t:m() end` ; `t:m()` — and (b) the same with **both** names `m`: `local m = {}` ; `---@deprecated gone` ; `function m:m() end` ; `m:m()` | as #21, description `Deprecated API: gone` | (a) **no** warning at either the declaration or the call site; (b) warnings at `44..45` **and** `54..55` | (a) is the pin, and its mutation is **inside the inspection, not this feature**: delete the `LuaFuncDecl` name-equality guard at [LuaDeprecatedApiInspection.kt:81-86](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt) → (a) gains a call-site warning at `54..55`. (b) is the control that proves the guard, not the branch, is what silences (a) — **executed**: `funcName.nameRef.identifier='t'@42 textEq=false` in (a) against `'m'@42 textEq=true` in (b). Deleting the colon branch reddens (b) alone |
| 23 | `NAV-13-08`, `NAV-13-05` | `local t = {}` ; `function t:m() end` ; `local m = 1` ; `t:m()` — the local `m` has no use other than the colon member name | `LuaRenameProcessor().substituteElementToRename(<the call site's `m` leaf>, null)` | it throws `RefactoringErrorHintException` — the existing `METHOD_FUNCTION → refuse` clause at [LuaRenameProcessor.kt:111-112](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt), reached because `resolvedDeclarationLeaf` ([:378](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)) now returns the method's leaf | as #1 → **executed pre-change**, it returned `LeafPsiElement@38 'm'` — the *local* — so rename on the call site would have renamed the local instead. Enabling `LuaUnusedLocalInspection` on the same fixture also pins the unused-local half: `Unused local variable 'm'` at `38..39` appears only under the branch |
| 24 | `NAV-13-08` | `local t = {}` ; `function t:print(alpha, beta) end` ; `t:print(1, 2)` — plus, as a separate method, the control `function t:emit(alpha, beta) end` ; `t:emit(1, 2)` | `myFixture.doHighlighting()`, then read `editor.inlayModel.getInlineElementsInRange(0, file.textLength).map { it.offset }` | the `print` fixture has hints at the two argument offsets; the `emit` control has them on both sides of the change | as #1 → **executed**, the `print` fixture's offsets fall from `[7, 55, 58]` to `[7]` because `isStdlibCall` ([LuaParameterInlayHintsProvider.kt:191](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaParameterInlayHintsProvider.kt)) again classifies it stdlib by the same-named global, while the `emit` control stays `[7, 53, 56]` — so the control separates the stdlib clause from the hint machinery |
| 25 | `NAV-13-08`, `NAV-13-03` | three methods, one `configureByText` each: (a) case 23's fixture `local t = {}` ; `function t:m() end` ; `local m = 1` ; `t:m()` — (b) `local function m() end` ; `local t = {}` ; `function t:m() end` ; `t:m()` ; `m()` — (c) `function m() end` ; `local t = {}` ; `function t:m() end` ; `t:m()` | `ReferencesSearch.search(<the same-named NON-method declaration leaf>, allScope(project))` — `m@38` in (a), `m@15` in (b), `m@9` in (c) | (a) **no** references; (b) exactly `[47, 61]`; (c) exactly `[41]` — the colon call site is in none of them | delete the colon branch from `LuaNameReference.multiResolve` (as #1) → **executed**, the same searches return `[46]`, `[47, 57, 61]` and `[41, 51]`; the extra offset is the colon call site in each. This is case 7 driven from the other end of the same binding, and (b)/(c) are what make the row cover more than `LOCAL_VARIABLE` |
| 26 | `NAV-13-08` | two methods: (a) case 23's fixture, caret at offset 38 — (b) `local m = {}` ; `function m:m() end` ; `m:m()`, caret at offset 6 | `myFixture.renameElementAtCaret("RENAMED")`, then read `file.text` | (a) `local t = {}` / `function t:m() end` / `local RENAMED = 1` / **`t:m()`** — the call is untouched; (b) `local RENAMED = {}` / `function m:m() end` / **`RENAMED:m()`** — the receiver is rewritten and the member name is not | as #1 → **executed**, (a) becomes `t:RENAMED()` and (b) becomes `RENAMED:RENAMED()`. (b) is the sharper pin: pre-change, renaming the receiver rewrites the *member* name of the call while leaving the declaration `function m:m()` alone — a half-applied rename in the [[BUG-457]] class |
| 27 | `NAV-13-08` | case 23's fixture | `LuaSafeDeleteProcessor().findUsages(leaf, arrayOf(leaf), result)` on `m@38` and, as a second assertion, on `m@24` | `m@38` yields **no** usage; `m@24` yields exactly one, at offset 46 | as #1 → **executed**, the two swap: `m@38` yields `[46]` and `m@24` yields `[]`. Safe Delete searches `ReferencesSearch` on the leaf's `useScope` ([LuaSafeDeleteProcessor.kt:89](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/LuaSafeDeleteProcessor.kt)), so this row pins that the transfer reaches the refactoring and not only the search API |
| 28 | `NAV-13-08` | two methods: (a) `local t = {}` ; `function t:m() end` ; `for m in pairs(t) do t:m() end` — (b) `local t = {}` ; `function t:m() end` ; `local function f(m)` ; `t:m()` ; `end` ; `f(1)`, with the inspection's `checkParameters` set to `true` | enable `LuaUnusedLocalInspection`, `myFixture.doHighlighting()`, read every description | (a) `Unused local variable 'm'` at offset 36; (b) `Unused parameter 'm'` at offset 49 | as #1 → **executed**, neither warning appears. Case 23 pins the `LOCAL_VARIABLE` kind; this row covers every other kind `classify` records ([LuaUnusedLocalInspection.kt:106-113](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnusedLocalInspection.kt)). (b) needs `checkParameters` because it defaults to `false` and has no settings UI ([:36](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnusedLocalInspection.kt)) |
| 29 | `NAV-13-08`, `NAV-13-04` | `local t = {}` ; `local zz = 1` ; `t:zz()` — the member is **unresolvable** | `ReferencesSearch` on `zz@19`, and `resolve()` on the call site's `zz@28` | no references, and `resolve()` is null | as #1 → **executed**, `[28]` and `LeafPsiElement@19 'zz'`. This is the withdrawal with **no** counterpart gained: no declaration acquires the usage, so `zz` becomes unused. The row exists because every other mirror row is a transfer and could be mistaken for one |
| 30 | `NAV-13-08` | `function m() end` ; `local t = {}` ; `function t:m() end` ; `t:m()` | `LuaDocumentationTargetProvider().documentationTargets(file, 51)`, then read the target's anchored element | one `LuaCatsDocumentationTarget`, anchored on `function t:m() end` at offset 30 | as #1 → **executed**, one target anchored on `function m() end` at offset 0. Quick Documentation on a colon call site does not only *gain* a doc where it had none (case 23's fixture, `[]` → the method's): where a same-named global function, local function or parameter is in scope it **retargets** from that declaration's doc to the method's, and this row is the one that can tell the two apart |
| 31 | `NAV-13-08` | HV-8 step 1's fixture — `---@deprecated Use the method instead` ; `local function m() end` ; `local t = {}` ; `function t:m() end` ; `t:m()` ; `m()` — caret on the `m` of `t:m()` (offset 95) | with `ctx = DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)`, read `LuaInplaceRenameHandler().isAvailableOnDataContext(ctx)`, then `RenameHandlerRegistry.getInstance().getRenameHandlers(ctx)`, then invoke the first handler the registry offers under `TemplateManagerImpl.setTemplateTesting` | `isAvailableOnDataContext` is **false**; **no** offered handler is a `LuaInplaceRenameHandler` (executed: the list is `[PsiElementRenameHandler]`); invoking it throws `RefactoringErrorHintException` and `TemplateManagerImpl.getTemplateState(editor)` is never non-null | delete the colon branch from `LuaNameReference.multiResolve` (as #1) → **executed**: `isAvailableOnDataContext` is `true`, the list is `[LuaInplaceRenameHandler]`, and an inline template starts on `range=(95,96) text='m'` whose commit rewrites four occurrences. The assertion is on the handler, not on the platform class name, so it survives a platform that adds a second fallback handler |
| 32 | `NAV-13-08` | `---@class m` ; `local m = {}` ; `local t = {}` ; `function t:m() end` ; `t:m()` — caret on the `m` of `t:m()` (offset 59). No other fixture in this feature spells a `---@class` local whose name coincides with a colon member name | `LuaTypeHierarchyProvider().getTarget(ctx)`, with `ctx` as in #31 | **null** — the Type Hierarchy action declines | as #1 → **executed**, `getTarget` returns `LuaLocalVarDeclImpl@12 'local m = {}'` and Type Hierarchy opens on the class `m`. The `---@class m` annotation is load-bearing: without it `LuaHierarchyUtil.className` is null and `getTarget` is null on both sides, so the test could not fail |
| 33 | `NAV-13-08`, `NAV-13-05` | `local t = {}` ; `function t:m() end` ; `local m = 1` ; `print(m)` ; `do` ; `local n = 2` ; `t:m()` ; `end` — caret on the `m` of `local m = 1` | `myFixture.renameElementAtCaret("n")`, catching `BaseRefactoringProcessor.ConflictsInTestsException` | the rename **applies with no conflict**, giving `local n = 1` and `print(n)` with `t:m()` and the inner `local n = 2` unchanged | as #1 → **executed**, it throws `ConflictsInTestsException` with *"Renaming to 'n' would bind a usage of 'm' to a different declaration that is already visible here."* — `LuaRenameConflictDetector`'s C1 `captures` raised by the `t:m()` site. **`print(m)` must stay outside the `do` block**: `distinctByAnchor` collapses collisions by capturing declaration, so a second usage that also sees `n` reports one conflict on both sides and the mutation stops being reachable from this fixture |

**Case 15 has no falsifying mutation and is recorded as such rather than filled with an assertion
that cannot fail.** Those shapes resolve to nothing because [[TYPE-13]]'s `declarationOf` reports no
declaration for them (Gaps 2.7, 2.11), not because any NAV-13 clause refuses them. The obvious
candidate — [[TYPE-13]] case 6's "make `declaringNodeOf` return the start node when the walk finds
nothing" — was **applied to `LuaMemberDeclarations` and the fixtures re-run**, and every NAV-13
outcome was byte-identical to the unmutated run (`risks-and-gaps.md` DR-02 Finding 6): the start
node is a `LuaMethodExpr`, which `methodNameLeafOf`'s `as? LuaFuncDecl` cast already refuses, so
mutant and correct code both return null. The rows are kept as *reach pins*: if a later engine change
gives those shapes a declaration, this feature starts resolving them and the diff is visible.

**Case 18 likewise has no fixture-level falsifier, deliberately.** It is a no-change requirement,
and design §3.6's guard is what makes it true — the guard's own falsifier is case 16. Case 18 covers
the *unexcepted* part of `NAV-13-07`; case 19 covers the exception, and it is a fixture precisely
because the ratchet cannot see it (0 `---@` tags across the corpus's 734 files).

**Case 19's falsifier is case 1's mutation, and that is deliberate rather than a gap.** The
withdrawal follows from the branch answering null mid-build, so no clause of `declarationLeafOf`
separates the two. The mutation is reachable from case 19's own fixture and was executed on it: with
the branch deleted, `z` infers `string`. What case 19 adds over case 1 is the *observation* — case 1
reads `resolve()`, which cannot see an inference change at all.

## Acceptance Criteria

- [ ] Every de-risking action listed under "De-risking" below has run and its result is recorded in
      `risks-and-gaps.md`.
- [ ] Every `M` requirement has an executed test case with a named, reachable mutation — except the
      case-15 rows and case 18, each argued above. `NAV-13-07` is covered on both halves: case 18 for
      the no-change part and case 19 for the scoped exception.
- [ ] `LuaTypeGraphRootResolutionBudgetTest`'s methods that predate this feature pass **at their
      committed budgets, unchanged** (`NAV-13-06`).
- [ ] The new un-annotated budget method passes at the value `implementation-plan.md` Phase 3
      commits (`NAV-13-06`).
- [ ] The full unit suite is green (`NAV-13-05`).
- [ ] The corpus ratchet is run with `-PwithCorpus` and does not fail with `Corpus regression:`
      (`NAV-13-07`). The prototype's run is recorded in `risks-and-gaps.md` DR-02 Finding 5.
- [ ] Case 19 passes and its mutation was observed reddening it (`NAV-13-07`'s exception).
- [ ] Case 20 passes at every `K` it covers, so the cross-file fan-out is bounded rather than merely
      unobserved (`NAV-13-06`, `risks-and-gaps.md` Gap 2.3).
- [ ] The downstream consumer set was re-enumerated **by execution** against the shipped code, by the
      instrument `risks-and-gaps.md` DR-05 specifies, and it names no route `design.md` §7 omits
      (`NAV-13-08`). A route found only by reading a gate does not discharge this.
- [ ] The `plugin.xml` surface set was re-derived by **both** halves of `design.md` §7's rule — the
      call spellings and the receive spellings — and its residue list was recomputed, and neither
      adds a consumer §7 does not account for (`NAV-13-08`). A re-derivation over the call half alone
      reproduces the omission it exists to detect and does not discharge this.
- [ ] The mirror sweep `risks-and-gaps.md` DR-06 specifies was re-run against the shipped code — every
      element-taking API at **every** name leaf of every fixture — and it produces no off/on difference
      `design.md` §7 omits (`NAV-13-08`). Driving a surface at the call site alone does not discharge
      this: DR-06 exists because DR-05 drove `ReferencesSearch` on declaration leaves only, and the
      withdrawal from the same-named declaration's usage set went unrecorded as a result.
- [ ] Every `NAV-13-08` case passes — cases 21-24 and 31-32 on the call site's side, cases 25-30 and
      33 on the same-named declaration's — and each named mutation was observed reddening its own test.
      Case 22's mutation is inside `LuaDeprecatedApiInspection`, not this feature, and must be
      reverted with `git show HEAD:<path> > <path>`.

## Non-Functional Requirements

- **Cost.** [[TYPE-12]] records growth still at ×5.9 per doubling after BUG-473's two phases. This
  feature adds one `graphTypeToLuaType` conversion per colon call site actually resolved, and
  nothing during snapshot construction (design §3.6).
- **Threading.** `LuaColonCallResolution.declarationLeafOf` is pure PSI plus a `CachedValuesManager`
  -backed snapshot read; it requires a read action and takes no lock, performs no I/O and reads no
  index. It inherits its caller's context — `PsiReference.resolve()` is always called under a read
  action. [[BUG-473]] DR-7 established that the type engine runs off-EDT on the daemon path.
- **Memory.** No `Project`, `Editor`, `PsiFile` or `VirtualFile` is retained: `LuaColonCallResolution`
  is a stateless `object` and every element it touches is derived from its argument.

## De-risking

| ID | Question | Blocks | Status |
|----|----------|--------|--------|
| `NAV-13-00-DR-01` | How many of the corpus's 941 colon-method declarations gain a resolving call site, which receiver-handle rule maximises that, and what does a colon member name resolve to today? | the whole feature | **done — `risks-and-gaps.md` DR-01** |
| `NAV-13-00-DR-02` | Where does the resolution hang — a contributor, a second reference type, or a branch in `LuaNameReference` — and what does the chosen route cost? | design §2, §3.6 | **done — `risks-and-gaps.md` DR-02** |
| `NAV-13-00-DR-03` | Does TYPE-10's expected-callback seeding fire for a colon call today, and does the guard withdraw it? | `NAV-13-05`, `NAV-13-07`, design §3.6 decision 3 / §5 / §7 | **done — `risks-and-gaps.md` DR-03** |
| `NAV-13-00-DR-04` | Is the cross-file fan-out of the per-file guard bounded, and by what? | `NAV-13-06`, Gap 2.3 | **done — `risks-and-gaps.md` DR-04** |
| `NAV-13-00-DR-05` | Which consumers actually reach a colon member name, measured by execution rather than by reading each one's gate — and what does each one's observable output do on both sides of the change? | `NAV-13-08`, `NAV-13-05`, design §7 | **done — `risks-and-gaps.md` DR-05** |
| `NAV-13-00-DR-06` | Driving each surface from the call site records what the call site gains. What does the **same-named declaration** it used to bind to lose — across every element-taking API and every declaration kind — and is there an element-taking surface neither direction drove? | `NAV-13-08`, `NAV-13-05`, design §7 | **done — `risks-and-gaps.md` DR-06** |
| `NAV-13-00-DR-07` | DR-06's surface derivation selects consumers that **call** a resolve API. What does it miss — a consumer that *receives* the resolved element from a platform data rule and calls nothing — and what does each such consumer do on both sides? | `NAV-13-08`, design §7; Risk 1.3 | **done — `risks-and-gaps.md` DR-07** |

## Dependencies

- Requires [[TYPE-13]] (`LuaMemberDeclarations.declarationOf`, `public` for exactly this consumer).
- Enables [[REFACT-09]] (colon-method rename), whose `REFACT-09-00-DR-02` Finding 1 is the
  measurement this feature exists to change.
- Must not regress [[TYPE-12]] or [[BUG-473]]'s committed budgets.
