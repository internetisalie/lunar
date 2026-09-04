---
id: NAVIGATION-13-DESIGN
title: "13: Colon Call Site Resolution — Design"
type: design
parent_id: NAVIGATION-13
folders:
  - "[[features/navigation/13-colon-call-resolution/requirements|requirements]]"
---

# Technical Design: NAV-13 — Colon Call Site Resolution

## 1. Architecture Overview

### Current State

A colon call `t:m()` parses as `funcCall ::= varOrExp nameAndArgs+`, `nameAndArgs ::= methodExpr? args`,
`methodExpr ::= ':' nameRef`
([lua.bnf:297-300](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)). So the
member name `m` is an ordinary `LuaNameRef`. The only reference `LuaNameRef.getReference()` returns
is a `LuaNameReference`
([LuaBaseElements.kt:129-136](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt)),
and — since no `psi.referenceContributor` is registered for `LuaNameRef` — it is also the only one
`LuaBaseElement.getReferences()` returns
([:36-47](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt), which
merges `ReferenceProvidersRegistry`'s output with it).

`LuaNameReference.doMultiResolve` treats that name as a *lexical* name: a `LuaScopeProcessor` walk
up the block chain, then `LuaClassNameIndex` / `LuaAliasIndex` / `LuaGlobalDeclarationIndex` /
`LuaGlobalAssignmentNavigation` lookups keyed on the bare text
([LuaNameReference.kt:43-166](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt)).
The receiver is never consulted. Measured consequences, both at `f1ac26cc` (`risks-and-gaps.md` DR-01):

- `ReferencesSearch.search(<a colon-method declaration leaf>, allScope)` returns **0** for every
  receiver shape, including the `---@class`-annotated one.
- The member name is nevertheless non-null at **441 of the pinned corpus's 14 116** colon call sites
  and **89 of the annotated substitute's 2 446** — bound to a local variable, a local function or an
  unrelated global function.

### Prior Art in This Repo

| Component | file:line | This design |
| :-- | :-- | :-- |
| `LuaNameReference` | [LuaNameReference.kt:29](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt) | **EXTENDED** — one branch at the head of `multiResolve` (§3.6). No second reference class, no change to `isReferenceTo`, `handleElementRename` or either resolution phase. |
| `LuaNameReferenceSearcher` | [LuaNameReferenceSearcher.kt:44](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt) | **UNCHANGED**, and measured to need no change: it already scans every `LuaNameRef` of the right text and gates on `isReferenceTo` (§3.7). |
| `LuaDeclarationSite` | [LuaDeclarationSite.kt:41](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt) | **REUSED unchanged** as the searcher's target gate; `METHOD_FUNCTION` is already a kind it classifies ([:242](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)). |
| `LuaMemberDeclarations.declarationOf` | [LuaMemberDeclarations.kt:48](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaMemberDeclarations.kt) | **CONSUMED** — [[TYPE-13]] made it `public` for exactly this consumer. Not reimplemented and not wrapped. |
| `LuaCatsTypeReference` / `LuaCatsTypeReferenceContributor` / `LuaCatsTypeReferenceSearcher` | [LuaCatsTypeReference.kt:28](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaCatsTypeReference.kt), [LuaCatsTypeReferenceContributor.kt:28](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaCatsTypeReferenceContributor.kt), [LuaCatsTypeReferenceSearcher.kt:36](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaCatsTypeReferenceSearcher.kt) | **NOT followed** — REFACT-08's reference-plus-contributor-plus-searcher shape is right for a leaf that carries no reference at all. A `LuaNameRef` already carries one. §9 Alternative A records the executed measurement. |
| `LuaInferredTypeAnnotator.receiverOf` | [LuaInferredTypeAnnotator.kt:88-102](../../../../src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaInferredTypeAnnotator.kt) | **UNCHANGED, deliberately not shared.** It derives a receiver for *highlighting* and accepts a chain segment and a suffixed head; §3.3's derivation refuses both. Two callers with different admissibility rules, not one rule copied. |
| `LuaTypeInlayHintProvider.unwrapExpression` | [LuaTypeInlayHintProvider.kt:20](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaTypeInlayHintProvider.kt) | **NOT used** — §3.3 states the handle directly from the grammar. Measured equivalent on the accepted shape and strictly wider on the refused ones (§9 Alternative C). |
| `LuaMemberFieldNavigation` (NAV-12) | [LuaMemberFieldNavigation.kt](../../../../src/main/kotlin/net/internetisalie/lunar/lang/navigation/LuaMemberFieldNavigation.kt) | **UNTOUCHED.** It serves the *dotted* member route through `getQualifiedName`; NAV-13 completes NAV-12's stated Non-Goal for the colon form and never enters that branch (§3.6). |
| `LuaTypeGraphRootResolutionBudgetTest` | [LuaTypeGraphRootResolutionBudgetTest.kt:40](../../../../src/test/kotlin/net/internetisalie/lunar/lang/types/LuaTypeGraphRootResolutionBudgetTest.kt) | **EXTENDED** with one method on an un-annotated fixture (§2.4). Its pre-existing methods and their committed budgets are unchanged, and must stay green. |

### Target State

One new stateless object and one branch:

```
LuaNameReference.multiResolve
  ├─ isColonCallMemberName(host)? ── yes ─→ LuaColonCallResolution.declarationLeafOf(host)
  │                                            ├─ receiverOf        (§3.3) — the refusals
  │                                            ├─ in-progress guard (§3.6)
  │                                            ├─ LuaTypesSnapshot.forFile + graphTypeToLuaType
  │                                            ├─ declarationLeaves (§3.4) — union arms, agreement
  │                                            └─ methodNameLeafOf  (§3.5) — LuaFuncDecl → leaf
  └─ no ──→ the existing ResolveCache / two-phase path, byte-for-byte unchanged
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.LuaColonCallResolution` (new)

- **Responsibility**: map a colon call's member-name `LuaNameRef` to the IDENTIFIER leaf of the
  `function Receiver:member()` that declares it, or to null.
- **Threading**: read action, inherited from `PsiReference.resolve()`. Pure PSI plus a
  `CachedValuesManager`-backed `LuaTypesSnapshot.forFile`; no I/O, no index read, no lock. Stateless
  `object`, so no `Project` / `PsiFile` / `Editor` is retained.
- **Collaborators**: `LuaTypesSnapshot` (`forFile`, `getValueType`, `graphTypeToLuaType`),
  `LuaTypesVisitor.isSnapshotUnderConstruction` (§2.3), `LuaMemberDeclarations.declarationOf`,
  `LuaUnionType`, `LuaDeclarationSite` (indirectly, through the searcher).
- **Package**: `lang/psi/`, beside `LuaDeclarationSite` — the same kind of member: a pure-PSI
  classifier over declaration/call shapes, not a PSI element.
- **Key API**:

```kotlin
package net.internetisalie.lunar.lang.psi

object LuaColonCallResolution {
    /** True for the `m` of `t:m()` — a LuaNameRef whose parent is a LuaMethodExpr. O(1). */
    fun isColonCallMemberName(element: PsiElement): Boolean

    /** The method-name IDENTIFIER leaf this call site names, or null. */
    fun declarationLeafOf(element: PsiElement): PsiElement?

    private fun receiverOf(nameRef: LuaNameRef): LuaNameRef?
    private fun declarationLeaves(receiverType: LuaType, memberName: String): List<PsiElement>
    private fun methodNameLeafOf(member: LuaTypeMember, memberName: String): PsiElement?
}
```

Imports are explicit (no wildcards): `com.intellij.psi.PsiElement`, and from
`net.internetisalie.lunar.lang.psi.types`: `LuaMemberDeclarations`, `LuaType`, `LuaTypeMember`,
`LuaTypesSnapshot`, `LuaTypesVisitor`, `LuaUnionType`. The PSI types the body names —
`LuaNameRef`, `LuaMethodExpr`, `LuaNameAndArgs`, `LuaFuncCall`, `LuaFuncDecl`, `LuaFuncName`,
`LuaElementTypes` — are in **this object's own package** (`net.internetisalie.lunar.lang.psi`) and
must not be imported.

### 2.2 `net.internetisalie.lunar.lang.LuaNameReference` (edited)

One branch, in `multiResolve` **before** the `ResolveCache` call (§3.6 explains why there and not in
`doMultiResolve`). Nothing else in the class changes — in particular `isReferenceTo`,
`declarationIdentifier`, `handleElementRename`, `shadowsRatherThanUses` and both resolution phases
are untouched.

```kotlin
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val hostElement = myElement ?: return ResolveResult.EMPTY_ARRAY
        if (LuaColonCallResolution.isColonCallMemberName(hostElement)) {
            val target = LuaColonCallResolution.declarationLeafOf(hostElement)
                ?: return ResolveResult.EMPTY_ARRAY
            return arrayOf(PsiElementResolveResult(target))
        }
        return ResolveCache
            .getInstance(hostElement.project)
            .resolveWithCaching(this, RESOLVER, /* needToPreventRecursion = */ false, incompleteCode)
    }
```

Add `import net.internetisalie.lunar.lang.psi.LuaColonCallResolution` to the existing import block.

### 2.3 `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor` (one added member)

```kotlin
        /** Is a snapshot for [file] under construction on this thread? O(1) map probe, builds nothing. */
        internal fun isSnapshotUnderConstruction(file: PsiFile): Boolean =
            inProgressBuilds.get().containsKey(file)
```

Added beside the existing `inProgressSnapshot`
([LuaTypesVisitor.kt:1551-1552](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)),
which reads the same `ThreadLocal<MutableMap<PsiFile, LuaTypesVisitor>>` but **builds a snapshot** to
answer. §3.6 needs only the predicate, and calling `inProgressSnapshot` for it would do the work the
guard exists to avoid.

### 2.4 `LuaTypeGraphRootResolutionBudgetTest` (one added method)

```kotlin
    @Test
    fun colonCallSiteResolutionStaysWithinItsRootResolutionBudget()

    private fun unannotatedCallSiteFixture(): String   // local t = {} / function t:m(n) end / 80 × t:m("aN")
```

No budget method that predates this feature reaches this path: each uses
`annotatedCallSiteFixture()`, whose receiver takes the nominal route. Budgets are committed by
`implementation-plan.md` Phase 3 from the run it performs; the prototype measured `WRITE` = 165 and
`READ` = 83 here and passed at `COLON_WRITE_BUDGET = 180L` / `COLON_READ_BUDGET = 92L`
(`risks-and-gaps.md` DR-02 Finding 5). The method must resolve every call site **twice** and assert
that all of them resolve, so it can neither pass vacuously nor hide a per-resolution cost.

## 3. Algorithms

### 3.1 `isColonCallMemberName(element): Boolean`

- **Input → Output**: `PsiElement` → `Boolean`.
- **Steps**: return `element is LuaNameRef && element.parent is LuaMethodExpr`.
- **Rules**: `methodExpr ::= ':' nameRef` is the only rule with a `LuaMethodExpr`
  ([lua.bnf:300](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)), so the test
  is exact. The **declaration** side `function t:m()` spells its name under `LuaFuncNameMethod`
  ([lua.bnf:166](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)), a different
  type, so a declaration's own name is never taken by this branch and keeps today's resolution.
- **Complexity**: two `instanceof`s. This runs on every Lua name resolution, which is why the branch
  is gated on it rather than on anything that reads the tree.

### 3.2 Where a colon member name may be resolved from — the entry contract

`declarationLeafOf` is total over any `PsiElement`: it returns null unless the element is a
`LuaNameRef` under a `LuaMethodExpr` satisfying §3.3. No caller needs to pre-check.

### 3.3 `receiverOf(nameRef): LuaNameRef?` — the receiver, and what it refuses

- **Input → Output**: the member-name `LuaNameRef` → the receiver's `LuaNameRef`, or null.
- **Steps**:
  1. `methodExpr = nameRef.parent as? LuaMethodExpr ?: return null`
  2. `nameAndArgs = methodExpr.parent as? LuaNameAndArgs ?: return null`
  3. `call = nameAndArgs.parent as? LuaFuncCall ?: return null`
  4. **Refusal A (chain)** — `if (call.nameAndArgsList.firstOrNull() !== nameAndArgs) return null`
  5. `receiverVar = call.varOrExp.getVar() ?: return null` — **Refusal B (a parenthesised head)**
  6. **Refusal C (suffixed)** — `if (receiverVar.varSuffixList.isNotEmpty()) return null`
  7. `return receiverVar.nameRef`  (null for the `'(' expr ')' varSuffix+` alternative of `var`)
- **Why each refusal, with its measurement**:
  - **A.** `x:m1():m2()` is **one** `LuaFuncCall` with a two-element `nameAndArgsList`; there is no
    PSI node for the value of `x:m1()`. [[TYPE-13]] Gap 2.12 measured `visitFuncCall` seeding the
    whole call from the *first* segment's declared return, so the engine reports a silently wrong
    value for the chain. Resolving the second segment against the **receiver** would offer the
    receiver's own same-named member. Executed on `local A = {} / function A:go() end / local B = {} /
    function B:go() end / function A:next() return B end / A:next():go()`: with the clause,
    `resolve = null`; without it, `resolve = LeafPsiElement@24 'go'` — `function A:go()`, while the
    call's real target is `function B:go()`.
  - **B.** `varOrExp ::= var | '(' expr ')'`
    ([lua.bnf:298](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)), so
    `("s"):m()` and `(a or b):m()` have no `var` at all and `getVar()` is null. Measured over the
    corpus: 744 such sites, **0** of them reaching a declaration under any candidate handle rule
    (`risks-and-gaps.md` DR-01, table 2). Step 7's null return covers the sibling case — `var`'s own
    `'(' expr ')' varSuffix+` alternative, which has no `nameRef` — measured at 2 sites.
    **`f():m()` is not this case**: `funcCall ::= varOrExp nameAndArgs+` is greedy, so it parses as
    one call with `varOrExp = f` and a two-element `nameAndArgsList`, and Refusal A takes it.
  - **C.** [[TYPE-13]] Gap 2.8: the graph anchors **every** suffix of a `var` on that `var`'s bare
    head, so `a.b:m()`'s head `a` may carry a member `m` that belongs to `a.b`. Executed on
    `local a = {} / a.b = {} / function a:m() end / function a.b:m() end / a.b:m()`: with the clause,
    `resolve = null`; without it, `LeafPsiElement@33 'm'` — `function a:m()`, the head's own member.
    Corpus: 667 suffixed sites, **0** reaching a declaration under any candidate rule.
- **No `@NotNull`-getter hazard on this path.** The SYNTAX-18 shape — a generated `@NotNull` getter
  raising `TestLoggerAssertionError` on a partially parsed node — needs an unpinned child of a
  **pinned** rule ([LuaDeclarationSite.kt:210-229](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)).
  `funcCall`, `nameAndArgs`, `methodExpr`, `var` and `varOrExp` declare **no pin**
  ([lua.bnf:292-300](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)), so a
  failed sub-rule rolls the whole section back and the node is never built. `LuaVar.getNameRef()` is
  `@Nullable` and `LuaVar.getVarSuffixList()` is a list, so neither can raise.

  **The `@NotNull` getters this feature dereferences, and why each is safe.** The safety argument
  must cover §3.5's path as well as §3.3's, because the `LuaFuncDecl` §3.5 reads comes from an
  arbitrary file the type engine reached and is not under the caller's control.

  | Getter | Called from | Its rule | Pin? |
  | :-- | :-- | :-- | :-- |
  | `LuaFuncCall.getNameAndArgsList()` | §3.3 step 4 | `funcCall ::= varOrExp nameAndArgs+` ([lua.bnf:297](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) | none |
  | `LuaFuncCall.getVarOrExp()` | §3.3 step 5 | `funcCall ::= varOrExp nameAndArgs+` ([lua.bnf:297](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) | none |
  | `LuaFuncNameMethod.getNameRef()` | §3.5 step 3 | `funcNameMethod ::= ':' nameRef` ([lua.bnf:166](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) | none |
  | `LuaNameRef.getIdentifier()` | §3.5 step 3 | `nameRef ::= IDENTIFIER` ([lua.bnf:169](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) | none |

  An unpinned rule cannot produce a node missing a mandatory child: `exit_section_` rolls the builder
  back on failure and the node is never built. `funcDecl` *is* pinned (`pin = 1`,
  [lua.bnf:189-190](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)), which is
  exactly why §3.5 step 2 reaches `FUNC_NAME` through the node rather than through
  `LuaFuncDecl.getFuncName()` — and `funcNameMethod` and `nameRef`, being unpinned, are safe to
  dereference once a `LuaFuncName` exists. Executed control: the fixture
  `local t = {} / function t:m() end / function / t:m()` — a bare `function` keyword above the call —
  resolves normally and raises nothing (`risks-and-gaps.md` DR-02).

### 3.4 `declarationLeaves(receiverType, memberName): List<PsiElement>` — union arms and agreement

- **Input → Output**: the receiver's converted `LuaType` and the member name → the distinct accepted
  declaration leaves.
- **Steps**:
  1. `members = mutableListOf<LuaTypeMember>()`
  2. `receiverType.resolveMember(memberName)?.let { members += it }`
  3. `if (receiverType is LuaUnionType) receiverType.types.forEach { arm -> arm.resolveMember(memberName)?.let { members += it } }`
  4. `return members.mapNotNull { methodNameLeafOf(it, memberName) }.distinct()`
- **Why step 3 exists.** `LuaUnionType.resolveMember` returns null unless **every** arm carries the
  name ([LuaComplexTypes.kt:10-24](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaComplexTypes.kt)),
  and a `---@class`-annotated receiver aliased as `local b = Builder` types as
  `{ … } | Builder` — the anonymous table arm has no `setName`. Executed on
  `---@class Builder / local Builder = {} / function Builder:setName(n) end / local b = Builder /
  b:setName("x")`: `receiverType.resolveMember("setName")` is a **MISS**, and the arm loop finds
  `setName@54`. On the corpus and the annotated substitute the arm loop raises the declarations that
  gain a resolving call site from 68 to 84 (of 941) and from 50 to 121 (of 268) respectively. Those
  are the prototype's totals; against the shipped code the corpus figure is 51 of 941 (67 before excluding the plugin's own bundled stdlib stub)
  (`risks-and-gaps.md`, "The shipped-code re-measurement"), and the arm loop's *contribution* is
  independently confirmed rather than inferred — `implementation-plan.md` Phase 2 executed the
  drop-the-arm-loop mutation and it reddened `requirements.md` case 4 and **nothing else**.
- **Why `.distinct()` here and `singleOrNull` in `declarationLeafOf` (§3.6), not `firstOrNull`.**
  Arms are ordered by `LuaTypeAlgebra.canonicalize`'s `displayName()` sort
  ([LuaTypeAlgebra.kt:58-61](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeAlgebra.kt)),
  so `firstOrNull` *is* deterministic — and it is deterministically arbitrary. A `---@type A|B`
  receiver whose two arms each declare `m` has two equally good targets and no ground for choosing;
  refusing is the direction §"Behaviour Rules" fixes. Executed on
  `---@class A / local A = {} / function A:m() end / ---@class B / local B = {} / function B:m() end /
  ---@type A|B / local u / u:m()`: with `singleOrNull`, null; with `firstOrNull`,
  `LeafPsiElement@36 'm'`. Measured frequency: **12 of 1 312** bare-name sites in the annotated
  substitute, **0 of 11 411** in the pinned corpus.
- **Complexity**: `O(arms)` `resolveMember` calls, each already memoised by the snapshot; no walk of
  the member map.

### 3.5 `methodNameLeafOf(member, memberName): PsiElement?` — the declaration normalisation

- **Input → Output**: a `LuaTypeMember` and the expected name → the declaration's IDENTIFIER leaf, or
  null.
- **Steps**:
  1. `declaration = LuaMemberDeclarations.declarationOf(member) as? LuaFuncDecl ?: return null`
  2. `funcName = declaration.node.findChildByType(LuaElementTypes.FUNC_NAME)?.psi as? LuaFuncName ?: return null`
  3. `return funcName.funcNameMethod?.nameRef?.identifier?.takeIf { it.text == memberName }`
- **Why step 1 refuses everything that is not a `LuaFuncDecl`.** `declarationOf` also returns a
  `LuaField` (a table-constructor entry) and a `LuaAssignmentStatement` (`t.m = f`)
  ([LuaMemberDeclarations.kt:50-56](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaMemberDeclarations.kt)).
  Neither can be a search target: `field ::= … | IDENTIFIER '=' expr`
  ([lua.bnf:319](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) puts the
  name leaf directly under `LuaField`, and `LuaDeclarationSite.kindOf` of that leaf is **null**
  (executed), so `LuaNameReferenceSearcher` refuses it at
  [:58](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt);
  and `LuaDeclarationSite.identifierLeafOf` of a `LuaAssignmentStatement` is the **receiver**'s leaf
  ([:68-72](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)),
  i.e. `t`, not `m`. Admitting either would give a Go-to target with no usage set — the asymmetry
  `NAV-13-02`/`NAV-13-03` exist to prevent. Executed: dropping the cast yields `LuaFieldImpl@12` and
  `LuaAssignmentStatementImpl@13` on their fixtures.
- **Why step 2 reads the node, not `LuaFuncDecl.getFuncName()`.** `getFuncName()` is
  `findNotNullChildByClass` and `funcDecl ::= FUNCTION funcName funcBody` carries `pin = 1`
  ([lua.bnf:189-190](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)), so a
  `LuaFuncDecl` node exists with no `FUNC_NAME` child whenever a keyword sits in the name slot, and
  the getter raises `TestLoggerAssertionError` — the SYNTAX-18 hazard
  [LuaDeclarationSite.kt:210-229](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)
  already closes for its own rows and `REFACT-09` design §3.8 for its traversal. The `LuaFuncDecl`
  here comes from `declarationOf`, i.e. from an arbitrary file the type engine reached, so it is not
  under the caller's control.
- **Step 3's `takeIf` is a refusal that is measurably never taken.** Over the 14 116 corpus and 2 446
  substitute colon call sites, the declared method name differed from the call's member name **0**
  times, and none of the mutations in `risks-and-gaps.md` DR-02 reddens a fixture through it. It is
  kept for the same reason `LuaNameReferenceSearcher` keeps its unreachable `LuaLabelName` guard
  ([:50-56](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt)):
  it is the one place the returned leaf's text is tied to the reference's name, and `isReferenceTo`
  compares `element.text != name` before resolving — so a divergence would produce a Go-to target
  that Find Usages cannot see. **`requirements.md` records it as having no reachable falsifier
  rather than pairing it with a test that cannot fail.**

### 3.6 `declarationLeafOf` — the branch, its placement, and the in-progress guard

```kotlin
    fun declarationLeafOf(element: PsiElement): PsiElement? {
        val nameRef = element as? LuaNameRef ?: return null
        val receiver = receiverOf(nameRef) ?: return null
        val file = nameRef.containingFile
        if (LuaTypesVisitor.isSnapshotUnderConstruction(file)) return null
        val types = LuaTypesSnapshot.forFile(file)
        val receiverType = types.graphTypeToLuaType(types.getValueType(receiver))
        return declarationLeaves(receiverType, nameRef.text).singleOrNull()
    }
```

**The placement decisions, each measured.**

1. **The branch is exclusive: it never falls through to the two-phase path.** A colon member name is
   a table key, not a variable (`requirements.md` "Behaviour Rules"), so a lexical answer for it is
   unsound. Executed: on `function m() end / local function f(x) x:m() end` the fall-through form
   offers the global `function m()` for a parameter receiver's method; on
   `local t = {} / function t:m() end / local m = 1 / t:m()` the pre-change code resolved to the
   local `m@38`. Corpus-wide, the exclusive form leaves **0** non-`METHOD_FUNCTION` resolutions
   where there were 441 (`risks-and-gaps.md` DR-02 Finding 2).

2. **The branch sits in `multiResolve`, above `ResolveCache`, not inside `doMultiResolve`.** The
   guard below makes the answer depend on *when* it is asked; `ResolveCache` would retain the
   mid-build refusal for the rest of the PSI generation, so every colon call in a file whose
   snapshot was built during that generation would keep resolving to nothing. Bypassing the cache
   costs, measured on the 80-call-site fixtures, **2 `WRITE` and 1 `READ` root resolutions for 160
   consecutive resolutions** — the `RootMemo` BUG-473 introduced absorbs the repeats.

3. **The guard is what keeps `NAV-13-06` true and confines `NAV-13-07` to its stated exception.** `LuaTypesVisitor` resolves the
   member name of **every** colon call while building the snapshot:
   `propagateExpectedLambdaParams` → `LuaExpectedCallbackResolver.resolveCalleeType` →
   `resolveMethodCalleeType` → `nameRef.reference?.resolve()`
   ([LuaTypesVisitor.kt:1105](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt),
   [LuaExpectedCallbackResolver.kt:47-51](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaExpectedCallbackResolver.kt)).
   Answering that from the snapshot under construction is a cycle the engine's fixpoint does not
   model. Measured on the existing `annotatedCallSiteFixture()` (`risks-and-gaps.md` DR-02 Finding 4):

   | | `WRITE` | `READ` | `DECLARED_DEMAND` |
   | :-- | --: | --: | --: |
   | guarded, after `forFile` | 572 | 647 | 325 |
   | un-guarded, after `forFile` | 812 | 727 | 325 |

   572/647/325 are exactly the values `LuaTypeGraphRootResolutionBudgetTest`'s KDoc records for the
   unmodified plugin, so the guard restores the pre-feature cost precisely; un-guarded, the budget methods that
   predate this feature fail (`WRITE` 812 > 600 and 814 > 620).

   **The guard does not restore what the engine infers, and one inference change is in scope.**
   This branch's answer cannot feed `resolveMethodCalleeType`: the leaf it returns has a
   `LuaFuncNameMethod` for its `parent.parent`, and the required cast is
   `resolved.parent?.parent as? LuaFuncDecl` — executed, `castMatches=false`. **But the cast does not
   fail today.** The pre-feature resolution of a colon member name can return a whole
   `LuaFuncDecl` **node** from the stub-index phase
   ([LuaNameReference.kt:135-144](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt)
   asks `StubIndex` for `LuaFuncDecl::class.java` elements), and a *nested* declaration's
   `parent.parent` is the enclosing `LuaFuncDecl` — so the cast succeeds and TYPE-10 seeds the lambda
   from the **enclosing** function's `---@param`. Executed at `30052d62` (`risks-and-gaps.md` DR-03):

   | | pre-feature | under the guarded branch |
   | :-- | :-- | :-- |
   | `resolve()` on the call's `m` | `LuaFuncDeclImpl@49` | null |
   | `resolved.parent.parent` | `LuaFuncDeclImpl` | — |
   | inferred type of the lambda's `z` | `string` | `unknown` |
   | the same fixture spelled with a dot (control) | `unknown` | `unknown` |

   The withdrawn seeding is unsound — `t:m(...)` calls `m`, and `outer`'s annotation describes
   `outer`'s parameter — so `requirements.md` scopes it in under `NAV-13-05` and states it as
   `NAV-13-07`'s single exception, pinned by test case 19. It costs no sound seeding: with the
   genuine spelling `---@param cb fun(a: string)` on `function t:m(cb)`, the member name resolves to
   nothing pre-feature (the method's stub is keyed `t:m`, not `m`) and is refused by this guard
   after, so `z` is `unknown` on both sides — executed.

### 3.7 Why `isReferenceTo` and `LuaNameReferenceSearcher` need no change

`isReferenceTo` ends in `resolved === element || declarationIdentifier(resolved) === element`
([LuaNameReference.kt:260-272](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt)).
§3.5 returns the declaration's IDENTIFIER **leaf**, which is exactly what
`LuaNameReferenceSearcher` normalises its target to
([:57](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt)),
so the first disjunct holds. The two guards above it are both false for a colon call site:
`self.identifier === element` compares the call site's own leaf with the declaration's, and
`shadowsRatherThanUses` reads `LuaDeclarationSite.kindOf` of the call site's leaf, which is null
because `kindFromNameRefGrandParent` has no `LuaMethodExpr` row
([LuaDeclarationSite.kt:234-248](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)).

Executed end to end (`risks-and-gaps.md` DR-02 Finding 2): `ReferencesSearch.search(<declaration
leaf>, allScope)` returns exactly one reference, with `isReferenceTo == true`, for the plain local
table, the in-file global table, `setmetatable` OO, the aliased `---@class` receiver, the `---@type`
receiver, the cross-file `---@type` receiver, and each of two same-named members on different
receivers; and 0 for every out-of-scope shape.

## 4. External Data & Parsing

**None.** This feature consumes no CLI output, file text or network response. Its only inputs are
PSI and the in-memory type graph.

## 5. Data Flow

### Example 1 — plain local table, Go to Declaration

`local t = {}` / `function t:m() end` / `t:m()`, caret on the call's `m`.
`LuaNameRefBaseImpl.getReference()` → `LuaNameReference.multiResolve` → §3.1 true → §3.3 gives the
receiver `t@32` → snapshot not under construction → `getValueType(t)` → `graphTypeToLuaType` →
`resolveMember("m")` hits with `sourceElement = LuaFuncNameMethod@23` ([[TYPE-13]] case 1) →
`declarationOf` → `LuaFuncDecl@13` → §3.5 → `m@24`. `resolve()` returns `m@24`; Go to Declaration
lands on the method name.

### Example 2 — Find Usages on the declaration

Caret on the `m` of `function t:m()`. `LuaFindUsagesProvider.canFindUsagesFor` accepts it
(`kindOf == METHOD_FUNCTION`) → `ReferencesSearch` → `LuaNameReferenceSearcher` narrows candidate
files by the word `m`, scans their `LuaNameRef`s, and for the call site's `m` calls
`isReferenceTo(m@24)`, which resolves through Example 1 and matches by identity. One usage.

### Example 3 — the type engine asking mid-build

`LuaTypesSnapshot.forFile(f)` → `LuaTypesVisitor` visits `t:m()` → `propagateExpectedLambdaParams` →
`resolveMethodCalleeType(m)` → `LuaNameReference.multiResolve` → §3.1 true → §3.3 gives `t` →
`isSnapshotUnderConstruction(f)` is **true** → null, and nothing is cached. The snapshot completes at
its pre-feature cost, and the next resolution of that same site, after the build, takes the full path.

The engine does **not** read the same value it read before this feature. Where the pre-feature
resolution returned a `LuaFuncDecl` node from the stub-index phase, TYPE-10's
`propagateExpectedLambdaParams` seeded the lambda's parameters from that node's *enclosing*
declaration; the guard's null withdraws that seeding. §3.6 decision 3 carries the executed before/after
values and `requirements.md` case 19 pins them.

## 6. Edge Cases

| Case | Behaviour | Evidence |
| :-- | :-- | :-- |
| `t:m1():m2()` — second segment | null | §3.3 Refusal A; executed |
| `a.b:m()` | null | §3.3 Refusal C; executed |
| `("s"):m()`, `(a or b):m()` | null | §3.3 Refusal B |
| `f():m()` | null | §3.3 Refusal A — greedy `nameAndArgs+` makes it a chain, not a parenthesised head |
| `self:m()`, factory-returned receiver, `local u = t; u:m()`, parameter receiver, `require`d module | null | [[TYPE-13]] Gaps 2.7 / 2.11 report no declaration; executed per shape |
| `local t = { m = function() end }; t:m()` | null | §3.5 step 1; executed |
| `t.m = function() end; t:m()` | null | §3.5 step 1; executed |
| `function t.m() end; t:m()` — dotted declaration called with a colon | null (a deliberate over-refusal) | §3.5 step 3's `funcNameMethod` is absent; executed. Admitting it would widen the feature to the dotted spelling, which resolves through `getQualifiedName`/`LuaGlobalDeclarationIndex` — a different mechanism (`REFACT-09` risks, "Technical Debt") |
| `---@type A\|B` where both arms declare `m` | null | §3.4 agreement rule; executed |
| Cross-file, un-annotated (`Obj:m()` in another file) | null | `LuaTypesSnapshot` is per file; executed, `references=0` |
| Cross-file, annotated (`---@type Builder`) | resolves, and Find Usages crosses the file | executed, `references=1` |
| A local shadowing the method name (`local m = 1; t:m()`) | resolves to the method, not the local | §3.6 decision 1; executed |
| A malformed `function` header above the call | resolves normally, raises nothing | §3.3's pin analysis; executed |
| Two receivers with a same-named method | each resolves to its own | executed, one reference each |

Measured cost on the two 80-call-site fixtures, guarded (`risks-and-gaps.md` DR-02 Finding 4):

| fixture | after `forFile` | after 160 resolutions |
| :-- | :-- | :-- |
| un-annotated | `WRITE` 165 · `READ` 83 · `DECLARED_DEMAND` 2 | `WRITE` 165 · `READ` 83 |
| annotated | `WRITE` 572 · `READ` 647 · `DECLARED_DEMAND` 325 | `WRITE` 574 · `READ` 648 |

(The un-annotated column's post-resolution figures are the ones `implementation-plan.md` Phase 3
commits a budget against; the annotated ones equal the values the two existing budget methods
already assert.)

## 7. Integration Points

**`plugin.xml` requires no change, and that is a finding rather than an omission.** The reference is
minted by `LuaNameRefBaseImpl.getReference()`, and the searcher and Find Usages provider are already
registered:

```xml
<!-- src/main/resources/META-INF/plugin.xml — EXISTING, unchanged by this feature -->
<referencesSearch implementation="net.internetisalie.lunar.lang.insight.LuaNameReferenceSearcher"/>
<lang.findUsagesProvider language="Lua"
        implementationClass="net.internetisalie.lunar.lang.insight.LuaFindUsagesProvider"/>
<targetElementEvaluator language="Lua"
        implementationClass="net.internetisalie.lunar.lang.insight.LuaTargetElementEvaluator"/>
```

`LuaTargetElementEvaluator` is quoted here because it sits directly on the route this feature
changes, not merely because it is nearby: `TargetElementUtilBase` consults it before accepting a
referenced element, and it is what could have blocked or redirected the two flips below. It does
neither — driven, it answers `UNSURE` on both sides (§"The receive-the-element consumers").

No new `psi.referenceContributor` is added — §9 Alternative A. No index, no settings key, no bundle
message: this feature adds no user-visible string.

### The downstream consumer set, enumerated by execution

Reading each consumer's gate and asking whether it lets a colon member name through is not a sound
way to draw this set: a gate can admit one without naming it, and the reading is silent when it does.
The set below is produced by **instrumenting the branch itself**. A throwaway `recordCaller()` inside
`LuaColonCallResolution.isColonCallMemberName` captured `Thread.currentThread().stackTrace`, filtered
to `net.internetisalie.lunar` frames, on every call whose element *is* a colon member name; the
fixtures were then driven through every user-facing surface with the branch off and on. One
instrument covers `resolve()` and `multiResolve` alike, because `LuaNameReference.resolve()` is
implemented on top of `multiResolve`
([LuaNameReference.kt:275-277](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt)).
`risks-and-gaps.md` DR-05 carries the instrument, the fixtures and the raw output.

**Surfaces driven, and from where.** One sweep per end of the binding, because a surface that takes
an *element* has two ends and driving one says nothing about the other.

- **DR-05 — file-wide and call-site surfaces**: `doHighlighting` with every inspection `plugin.xml`
  registers, `findAllGutters`, `availableIntentions`, `completeBasic`,
  `LuaParameterInfoHandler.findElementForParameterInfo`,
  `LuaDocumentationTargetProvider.documentationTargets`,
  `LuaRenameProcessor.substituteElementToRename`, and `ReferencesSearch.search` on each colon-method
  declaration leaf.
- **DR-06 — every element-taking API at every name leaf**: `ReferencesSearch.search`,
  `LuaSafeDeleteProcessor.findUsages`, `LuaRenameProcessor.substituteElementToRename`, the full
  `renameElementAtCaret`, `LuaFindUsagesProvider.canFindUsagesFor` and
  `LuaDocumentationTargetProvider.documentationTargets`, driven at **every** `LuaNameRef` identifier
  leaf of each fixture rather than at the call site alone. This is what records the **withdrawal**
  half — what the same-named declaration loses — and it is the sweep that found
  `LuaSafeDeleteProcessor`, an element-taking consumer DR-05 drove in neither direction. The
  subsection "The mirror direction" below carries the result.

**Every route observed reaching a colon member name.** The `Route` column is the recorded entry
frame, not a grep hit.

| Route (recorded entry frame) | API | Effect of this change, executed |
| :-- | :-- | :-- |
| [`LuaExpectedCallbackResolver.resolveMethodCalleeType:48`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaExpectedCallbackResolver.kt) ← `LuaTypesVisitor.propagateExpectedLambdaParams:1106` | `resolve()` | **a deliberate inference change** — the pre-feature resolve could return a `LuaFuncDecl` node whose `parent.parent` is the enclosing `LuaFuncDecl`, seeding a lambda parameter from that enclosing declaration's `---@param`; the guard withdraws it. §3.6 decision 3, `requirements.md` case 19 |
| [`LuaNameReferenceSearcher.processQuery:76`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt) → `LuaNameReference.isReferenceTo:280` (its `resolve()` call at `:287`) → `resolve:276` | `resolve()` | **the enabling change, and it is a transfer rather than a gain** — §3.7. Executed on `local t = {}` / `function t:m(alpha, beta) end` / `t:m(1, 2)`: `REFSEARCH on 'm' -> 0 []` before, `-> 1 [45]` after. On a fixture where a same-named declaration is in scope the same call site simultaneously **leaves** that declaration's usage set: on `local t = {}` / `function t:m() end` / `local m = 1` / `t:m()`, the method leaf `m@24` moves `[] → [46]` while the local `m@38` moves `[46] → []`. Every consumer built on `ReferencesSearch` inherits both halves — see "The mirror direction" below |
| [`LuaDeprecatedApiInspection$buildVisitor$1.visitNameRef:37`](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt) | `multiResolve` | **a warning is withdrawn at some call sites and appears at others** — both directions are in scope; the subsection below carries each measurement. `NAV-13-08` governs them |
| [`LuaUnusedLocalInspection.collectUsedDeclarations:147`](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnusedLocalInspection.kt) ← `checkFile:82` | `multiResolve` | a declaration kept alive **only** by a same-named colon member name is now reported unused, in every kind `classify` records ([:106-113](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnusedLocalInspection.kt)). Executed: on `local t = {}` / `function t:m() end` / `local m = 1` / `t:m()`, no warning before, `Unused local variable 'm'` at `38..39` after; on `for m in pairs(t) do t:m() end` with the same declarations, `Unused local variable 'm'` at `36`; and on a parameter `function f(m)` whose only use is `t:m()`, `Unused parameter 'm'` at `49` — the last only with `checkParameters = true`, which defaults to `false` and has no settings UI ([:36](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnusedLocalInspection.kt)). Those are DR-01 table 3's local-variable bindings — 175 across the pinned corpus, 17 across the substitute |
| [`LuaRenameProcessor.resolvedDeclarationLeaf:378`](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt) ← `substituteElementToRename:107` | `resolve()` | **rename on a colon call site stops retargeting a same-named local and is refused instead**, at the existing `METHOD_FUNCTION → refuse(…, "refactoring.rename.colonMethod")` clause ([:111-112](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)) — no change to the processor. Executed on the same fixture, **as an API call**: `substituteElementToRename` returned `LeafPsiElement@38 'm'` (the local) before and throws `RefactoringErrorHintException: Cannot perform refactoring.` after. **Pre-feature the IDE does not take this route at that caret** — `RenameHandlerRegistry` selects `LuaInplaceRenameHandler` and the processor is never consulted; the API measurement is what the processor *would* answer, and the route the user actually gets is in §"The receive-the-element consumers". Post-feature the registry selects `PsiElementRenameHandler`, which is what makes this row the route the IDE takes. The refusal's **message** states a reason this feature falsifies — `risks-and-gaps.md` Technical Debt carries it, and NAV-13 does not reword it because it adds no user-visible string |
| [`LuaDocumentationTargetProvider.resolveDocumentationTarget:152`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationTargetProvider.kt) ← `documentationTargets:70` | `resolve()` | Quick Documentation on a colon call site **gains** the method's own doc where it had none, and **retargets** to it where a same-named declaration supplied one. Executed on `local t = {}` / `---@deprecated gone` / `function t:m() end` / `t:m()`: `DOC n=0 []` before, `DOC n=1 [LuaCatsDocumentationTarget]` after. Executed on `function m() end` / `local t = {}` / `function t:m() end` / `t:m()`, reading the target's anchored element: `LuaCatsDocumentationTarget@0 'function m() end'` before, `@30 'function t:m() end'` after — same target class, different declaration documented |
| [`LuaParameterInlayHintsProvider.isStdlibCall:191`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaParameterInlayHintsProvider.kt) ← `collectParameterHints:50` | `resolve()` | parameter-name hints appear where a same-named stdlib global suppressed them. Executed on `local t = {}` / `function t:print(alpha, beta) end` / `t:print(1, 2)`: inline inlay offsets `[7]` before, `[7, 55, 58]` after. The same fixture spelled `t:emit` is the control — `[7, 53, 56]` on both sides |
| [`LuaParameterInlayHintsProvider.resolveMethodCall:127`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaParameterInlayHintsProvider.kt) ← `resolveFunctionType:107` | `resolve()` | none — it requires `resolved.parent?.parent as? LuaFuncDecl`, and a method-name leaf's `parent.parent` is a `LuaFuncNameMethod`. Executed: the `t:emit` control's hints are identical on both sides |
| [`LuaParameterInfoHandler.resolveCandidates:68`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/hint/LuaParameterInfoHandler.kt) ← `findElementForParameterInfo:40` | `resolve()` | none — the handler reaches its candidate by an independent route and is unaffected by what the member name resolves to. Executed on `t:m(1<caret>, 2)`: `LuaParameterInfoCandidate(name=t:m, params=[self, alpha, beta], types=[null, null, null], isMethod=true)` on both sides, while `resolve()` moves from `null` to `LeafPsiElement@24 'm'` |

**Not observed on any driven surface**, each with the clause that keeps it out. This is a negative
result over the surfaces above and the fixtures DR-05 lists, not a proof; the clause is what makes it
structural.

| Site | Clause |
| :-- | :-- |
| [LuaGlobalCreationInspection.kt:49-50](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaGlobalCreationInspection.kt) | it visits only `LuaVarList` entries of a `LuaAssignmentStatement` |
| [LuaUndeclaredNames.isUnresolvedNonGlobal:23](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUndeclaredNames.kt) via [LuaUndeclaredVariableInspection](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUndeclaredVariableInspection.kt) | `isReadUse` returns false when `isMemberName(parent)`, which lists `LuaMethodExpr` ([:76-80](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUndeclaredVariableInspection.kt)) |
| the same, via [LuaCreateFunctionIntention.kt:39](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaCreateFunctionIntention.kt) | `calleeOf` returns the *receiver*'s `nameRef` ([:76-81](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaCreateFunctionIntention.kt)) and the guard `callee !== ref` then bails |
| the same, via [LuaCreateLocalVariableIntention.kt:38](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaCreateLocalVariableIntention.kt) | `isSimpleWriteTarget` requires `ref.parent is LuaVar` ([:70-73](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaCreateLocalVariableIntention.kt)); a colon member name's parent is a `LuaMethodExpr` |
| [LuaLineMarkerProvider.kt:45](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaLineMarkerProvider.kt) | it resolves the *callee* `LuaNameRef` of a `LuaFuncCall`'s `varOrExp`, which for `t:m()` is the receiver `t` |
| [LuaInferredTypeAnnotator.kt:67](../../../../src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaInferredTypeAnnotator.kt) | `:67` is the annotator's only `resolve()` and it resolves **`ref` itself**, so the clause that keeps a colon member name out is the guard above it: `classifyCall` returns at `if (!isCalleePosition(ref)) return null` ([:66](../../../../src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaInferredTypeAnnotator.kt)), and `isCalleePosition` opens with `ref.parent as? LuaVar ?: return false` ([:109-110](../../../../src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaInferredTypeAnnotator.kt)) — a colon member name's parent is a `LuaMethodExpr`. Its own `receiverOf` ([:88-102](../../../../src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaInferredTypeAnnotator.kt)) calls no `resolve` at all. The colon member name keeps its `LUA_INFERRED_METHOD` attribute unchanged — executed, `54..55 'm'` on both sides |

### The mirror direction — what the same-named declaration loses

An element-taking API has two ends, and driving one says nothing about the other. Every row of the
route table above is the **call site's** end. The withdrawal `NAV-13-05` scopes in has a second end:
the same-named declaration the call site used to bind to, which **loses** the call site from its
usage set at the same moment the method declaration gains it. DR-06 drives every element-taking API
at **every** `LuaNameRef` identifier leaf of each fixture and diffs the two runs field by field.

**`LuaSafeDeleteProcessor.findUsages` returned the same offsets as `ReferencesSearch` on every leaf
of every fixture, off and on** — it is `ReferencesSearch.search(leaf, leaf.useScope)`
([LuaSafeDeleteProcessor.kt:89](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/LuaSafeDeleteProcessor.kt)) —
so one column carries both.

| Fixture | Declaration leaf | Its `ReferencesSearch` / Safe Delete usages | The method leaf's, for comparison | Shift+F6 on the declaration leaf |
| :-- | :-- | :-- | :-- | :-- |
| `local t = {}` / `function t:m() end` / `local m = 1` / `t:m()` | `m@38` `LOCAL_VARIABLE` | `[46]` → `[]` | `m@24`: `[]` → `[46]` | `t:RENAMED()` → **`t:m()`** |
| `local function m() end` / `local t = {}` / `function t:m() end` / `t:m()` / `m()` | `m@15` `LOCAL_FUNCTION` | `[47, 57, 61]` → `[47, 61]` | `m@47`: `[]` → `[57]` | `t:RENAMED()` → **`t:m()`** |
| `function m() end` / `local t = {}` / `function t:m() end` / `t:m()` | `m@9` `GLOBAL_FUNCTION` | `[41, 51]` → `[41]` | `m@41`: `[]` → `[51]` | `t:RENAMED()` → **`t:m()`** |
| `local t = {}` / `function t:m() end` / `local function f(m)` / `t:m()` / `return m` / `end` | `m@49` `PARAMETER` | `[54, 65]` → `[65]` | `m@24`: `[]` → `[54]` | `t:RENAMED()` → **`t:m()`** |
| `---@class Builder` / `local Builder = {}` / `function Builder:setName(n) end` / `local setName = 7` / `---@type Builder` / `local b` / `b:setName("x")` | `setName@75` `LOCAL_VARIABLE` | `[114]` → `[]` | `setName@54`: `[]` → `[114]` | `b:RENAMED("x")` → **`b:setName("x")`** |
| `local m = {}` / `function m:m() end` / `m:m()` | `m@6` `LOCAL_VARIABLE` — the **receiver** | `[32, 34]` → `[32]` | `m@24`: `[]` → `[34]` | `RENAMED:RENAMED()` → **`RENAMED:m()`** |
| `local t = {}` / `local zz = 1` / `t:zz()` | `zz@19` `LOCAL_VARIABLE` | `[28]` → `[]` | **none** — the member is unresolvable | `t:RENAMED()` → **`t:zz()`** |

**What this table says that the call-site rows could not.**

1. **Find Usages on a same-named declaration returns one fewer usage**, and the kinds affected are
   every kind `LuaDeclarationSite` classifies that can carry a bare name — measured on
   `LOCAL_VARIABLE`, `LOCAL_FUNCTION`, `GLOBAL_FUNCTION` and `PARAMETER`, and on a receiver that
   happens to share its member's name. Nothing about the withdrawal is specific to a local variable.
2. **Rename on that declaration stops rewriting the colon call**, and today's behaviour is a defect
   of the [[BUG-457]] kind rather than a feature being removed. The receiver row is the clearest:
   renaming `local m = {}` today produces `local RENAMED = {}` / `function m:m() end` /
   `RENAMED:RENAMED()` — it rewrites the *member name of the call* while leaving the declaration
   `function m:m()` untouched, so the file no longer compiles as it did. After the change it produces
   `RENAMED:m()`.
3. **The unresolvable-member row is a withdrawal with no counterpart.** `t:zz()` resolves to nothing after the
   change, so the usage leaves `zz`'s set and joins nobody's; `zz` becomes unused. Every other row
   is a transfer, and a reader who saw only those would take the transfer for the rule.

**`LuaFindUsagesProvider.canFindUsagesFor` is unchanged at every leaf of every fixture**, off and on
— it reads `LuaDeclarationSite.kindOf`, which this feature does not touch. Find Usages remains
*available* on exactly the elements it was available on; what changes is what it returns.

### The surface set is derived from `plugin.xml`, not from a list

DR-05's instrument is systematic in one direction — it records every caller that reaches the branch
— but only over the surfaces something drove. The surface list itself was written by hand, and
`LuaSafeDeleteProcessor` is what that costs: an element-taking consumer DR-05 drove in **neither**
direction, found only when DR-06 enumerated consumers from the registry instead.

The enumeration below is that registry pass, and it is reproducible. **The rule has two halves,
because a consumer can reach this feature's output either by asking the reference or by being handed
the answer.**

1. Take every `implementation=` / `implementationClass=` / `class=` / `factoryClass=` / `instance=` /
   `serviceImplementation=` attribute and every `<className>` element in
   `src/main/resources/META-INF/plugin.xml` naming a `net.internetisalie.lunar.*` class.
2. Resolve each to its declaring Kotlin file: by path first, and where the file is named after a
   different declaration, by searching `src/main/kotlin` for `class|object|interface <Simple>`.
3. Keep every class whose declaring file names **either**
   - a **call** spelling — `.resolve()`, `multiResolve` or `ReferencesSearch` — the consumer asks the
     reference itself; **or**
   - a **receive** spelling — `CommonDataKeys.PSI_ELEMENT`, `TargetElementUtil` or
     `PsiElementRenameHandler.getElement` — the consumer never calls a resolve API and is *handed*
     the resolved element by the platform.

**The receive half is load-bearing, not defensive.** `CommonDataKeys.PSI_ELEMENT`'s editor data rule
follows the caret's reference, so a consumer can consume this feature's output while naming no call
spelling anywhere in its own source — which is exactly what all three receive-half consumers below
do. Executed rather than read: at the colon call site of HV-8's fixture,
`CommonDataKeys.PSI_ELEMENT` and `TargetElementUtil.findTargetElement` returned the **same** element
as `reference.resolve()` on both sides of the change — `LeafPsiElement@53 'm'` off,
`LeafPsiElement@85 'm'` on (§"The receive-the-element consumers" below).

Executed over `plugin.xml` at `7a1dc387`: **186** registered `net.internetisalie.lunar.*` classes,
every one resolved to a declaring file with no ambiguity and none unresolved; **16** match the call
half, **3** match the receive half, **0** match both, and the rest match neither. A sweep matching
`service\w*` rather than the six attribute names above reaches 188, the extra two being the
`serviceInterface=` values `LuaToolProbe` and `LuaTypeManager`; both match neither half, as do the
plugin `<id>`, `LuaBundle`, `LuaIcons.ROCKET`, `LuaSchemaFileProvider` and `lunar-terminal.xml`'s one
class — so **every reading of this input between 186 and 193 yields the same 16 / 3 / 0**, and a
count that differs is a consumer added or removed, not a difference of sweep. Every one of the
19 is accounted for below.

| Registered consumer | Status in this design |
| :-- | :-- |
| `LuaNameReferenceSearcher`, `LuaDeprecatedApiInspection`, `LuaUnusedLocalInspection`, `LuaDocumentationTargetProvider`, `LuaRenameProcessor`, `LuaParameterInlayHintsProvider`, `LuaParameterInfoHandler` | **observed** — the route table above, with each effect measured |
| `LuaGlobalCreationInspection`, `LuaUndeclaredVariableInspection`, `LuaLineMarkerProvider`, `LuaInferredTypeAnnotator` | **not observed**, each with its structural clause — the table above |
| `LuaSafeDeleteProcessor` | **observed by DR-06** — the mirror table above. Its usages are `ReferencesSearch` on the leaf's `useScope`, so it moves with the searcher in both directions |
| `LuaFindUsagesProvider` | **driven by DR-06, unchanged** — `canFindUsagesFor` reads `LuaDeclarationSite.kindOf`, which this feature does not touch |
| `LuaCatsTypeReferenceSearcher` | **structurally excluded** — `processQuery` returns unless `LuaCatsTypeDeclarations.isDeclarationLeaf(target)` ([:42](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaCatsTypeReferenceSearcher.kt)), and a colon-method name leaf is Lua PSI, not LuaCats |
| `LuaCatsTypeRenameProcessor` | **structurally excluded** — every disjunct of `canProcessElement` is a LuaCats predicate ([:44-47](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaCatsTypeRenameProcessor.kt)) |
| `LuaLabelRenameProcessor` | **structurally excluded** — `canProcessElement` is `element is LuaLabelName \|\| element is LuaLabelRef` ([:42](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaLabelRenameProcessor.kt)) |
| every `<intentionAction>` class registered for Lua — `LuaCreateFunctionIntention`, `LuaCreateLocalVariableIntention`, `LuaGenerateDocIntention`, `LuaInvertIfIntention`, `LuaStringConversionIntention` | **none names a call spelling or a receive spelling**; `LuaCreateFunctionIntention` and `LuaCreateLocalVariableIntention` are additionally covered by the not-observed table's clauses |
| `LuaInplaceRenameHandler` — **receive half** | **observed, and a user-visible flip** — <kbd>Shift+F6</kbd> at a colon call site changes which rename handler the registry selects. Driven below |
| `LuaTypeHierarchyProvider` — **receive half** | **observed, and a user-visible flip** — Type Hierarchy at a colon call site stops opening on a coincidentally-named `---@class` local. Driven below |
| `LuaTargetElementEvaluator` — **receive half** | **driven, unchanged** — it answers `UNSURE` on both sides, because its gate reads `LuaDeclarationSite.kindOf` of the **caret leaf**, which is null for a colon member name. Driven below |

### The receive-the-element consumers, driven on both sides

Every registered extension in the derivation's **receive** half reaches a colon member name without
naming a single resolve API. Each is driven below at the caret it would be invoked at, with the
branch off and on, and the recorded values are given. `LuaColonCallResolution.isColonCallMemberName` carries a probe kill switch for the run, so
"off" is the pre-feature build exactly — `requirements.md`'s mutation #1.

**1. `LuaInplaceRenameHandler` — <kbd>Shift+F6</kbd> at a colon call site changes which handler runs.**
Registered `<renameHandler implementation="net.internetisalie.lunar.refactoring.rename.LuaInplaceRenameHandler"/>`
([plugin.xml:403-404](../../../../src/main/resources/META-INF/plugin.xml)). Its gate is
`declaringNameRefOf` ([:130-135](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameHandler.kt)),
whose middle step is `if (LuaDeclarationSite.kindOf(leaf)?.isFileLocal != true) return null` — and
the leaf it tests is what `PsiElementRenameHandler.getElement(dataContext)` returns, i.e. what the
platform resolved. Executed on **HV-8 step 1's own fixture**, caret on the `m` of `t:m()` (offset 95):

| | pre-feature (branch off) | under the branch |
| :-- | :-- | :-- |
| the caret leaf, and its `kindOf` | `LeafPsiElement@95 'm'`, `kind=null` | the same |
| `reference.resolve()` | `LeafPsiElement@53 'm'` `LOCAL_FUNCTION` `isFileLocal=true` | `LeafPsiElement@85 'm'` `METHOD_FUNCTION` `isFileLocal=false` |
| `CommonDataKeys.PSI_ELEMENT` | `LeafPsiElement@53 'm'` | `LeafPsiElement@85 'm'` |
| `TargetElementUtil.findTargetElement` | `LeafPsiElement@53 'm'` | `LeafPsiElement@85 'm'` |
| `LuaInplaceRenameHandler.isAvailableOnDataContext` | **`true`** | **`false`** |
| `RenameHandlerRegistry.getRenameHandlers` | **`[LuaInplaceRenameHandler]`** | **`[PsiElementRenameHandler]`** |
| <kbd>Shift+F6</kbd>, driven through the handler the registry selects | **an inline template starts**, `range=(95,96) text='m'` | throws `RefactoringErrorHintException: Cannot perform refactoring.` |
| `LuaRenameProcessor.substituteElementToRename` on the element the context supplies | `LeafPsiElement@53 'm'` — **and the IDE never asks it**, because the registry chose the in-place handler | throws `RefactoringErrorHintException` |

**What the pre-feature template does when committed** is the sharp end, and it is worse than a
mis-targeted rename. Typing `RENAMED` into the template and committing it produced, executed:

```lua
---@deprecated Use the method instead
local function RENAMED() end
local t = {}
function t:RENAMED() end
t:RENAMED()
RENAMED()
```

**Every `m` in the file** rewritten from a caret the user put on a **method call** — the local
function's declaration, the method's own declaration, the call site and the plain call. Under the branch the
same keystroke refuses and the file is byte-identical afterwards.

**The `function t:RENAMED()` line is not this feature's to fix and does not become one.** That leaf
is a `LuaNameRef` under a `LuaFuncNameMethod`, not a `LuaMethodExpr`, so §3.1's gate never takes it
and its lexical resolution to the same-named local is unchanged on both sides — which is why the
mirror table's `LOCAL_FUNCTION` row keeps offset `47` in the declaration's usage set off **and** on.
What NAV-13 withdraws at that caret is the call site, not the method declaration's name.

**2. `LuaTypeHierarchyProvider` — Type Hierarchy stops opening on a coincidentally-named class.**
Registered `<typeHierarchyProvider language="Lua" implementationClass="net.internetisalie.lunar.lang.hierarchy.LuaTypeHierarchyProvider"/>`
([plugin.xml:757-759](../../../../src/main/resources/META-INF/plugin.xml)). `elementAtCaret` reads
`CommonDataKeys.PSI_ELEMENT` first and only falls back to `findElementAt`
([:37-42](../../../../src/main/kotlin/net/internetisalie/lunar/lang/hierarchy/LuaTypeHierarchyProvider.kt)),
then walks to a `LuaLocalVarDecl` carrying a class name. No fixture elsewhere in this feature spells
the shape it needs — a `---@class` local whose name coincides with a colon member name — so one was
written for it. Executed on `---@class m` / `local m = {}` / `local t = {}` / `function t:m() end` /
`t:m()`, caret on the member name (offset 59):

| | pre-feature | under the branch |
| :-- | :-- | :-- |
| `reference.resolve()` and `CommonDataKeys.PSI_ELEMENT` | `LeafPsiElement@18 'm'` — the `local m = {}` name leaf | `LeafPsiElement@49 'm'` — the method's name leaf |
| `LuaTypeHierarchyProvider.getTarget` | **`LuaLocalVarDeclImpl@12 'local m = {}'`** — the hierarchy opens on class `m` | **`null`** — the action declines |

The withdrawal is correct for the same reason every other one is: `t:m()` names a table key on `t`,
never the class `m`. It is nonetheless a user-visible action that used to do something and now does
nothing, so `NAV-13-08` scopes it in.

**3. `LuaTargetElementEvaluator` — driven, and genuinely unaffected.** Registered
`<targetElementEvaluator language="Lua" implementationClass="net.internetisalie.lunar.lang.insight.LuaTargetElementEvaluator"/>`
([plugin.xml:389-391](../../../../src/main/resources/META-INF/plugin.xml)). Its gate is
`referenceOrReferencedElement !== element && LuaDeclarationSite.kindOf(element)?.isFileLocal == true`
([:35-45](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaTargetElementEvaluator.kt)),
and `element` is the caret **leaf**, whose `kindOf` is null for a colon member name because
`kindFromNameRefGrandParent` has no `LuaMethodExpr` row
([LuaDeclarationSite.kt:234-248](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt)).
Executed at the same caret as case 1: `element=LeafPsiElement@95 'm' kind=null`,
`referenced=LeafPsiElement@53 'm'` off and `LeafPsiElement@85 'm'` on, and the answer is **`UNSURE`
on both sides**. It neither blocks nor redirects the two flips above; it is what lets the platform's
own answer stand, and it needs no change.

### The residue the derivation leaves, and it is driven too

The rule greps the **declaring file** of a registered class, so a registered consumer that delegates
its resolve to a helper elsewhere is caught only through that helper's own registration. The residue
is enumerable rather than hypothetical: every file under `src/main/kotlin` naming a call or receive
spelling that is **not** the declaring file of any registered class. At `7a1dc387` there were six.
Re-derived against the **shipped** code there are **seven**: the branch's own
`LuaColonCallResolution`, which did not exist at `7a1dc387`, is itself residue by this rule.

| Residue file | Reached from | Status |
| :-- | :-- | :-- |
| [`LuaUndeclaredNames`](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUndeclaredNames.kt) | `LuaUndeclaredVariableInspection` | already in the not-observed table, with its `isMemberName` clause |
| [`LuaExpectedCallbackResolver`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaExpectedCallbackResolver.kt) | `LuaTypesVisitor.propagateExpectedLambdaParams` | the route table's first row — the inference change `NAV-13-07` scopes as its exception |
| [`LuaNameReference`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt) | the reference itself | the class §2.2 edits |
| [`LuaCatsTypeReference`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaCatsTypeReference.kt) | LuaCATS type-name leaves | structurally excluded — a colon member name is Lua PSI, not LuaCats |
| [`LuaLabelReference`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaLabelReference.kt) | `LuaLabelName` / `LuaLabelRef` | structurally excluded — a colon member name is neither |
| [`LuaRenameConflictDetector`](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt) | `LuaRenameProcessor.findCollisions:178` | **a user-visible flip** — driven below |
| [`LuaColonCallResolution`](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaColonCallResolution.kt) | `LuaNameReference.multiResolve` | **this feature's own class**, added by Phase 1 and absent at `7a1dc387`. It matches the rule on a **KDoc mention** of `PsiReference.resolve()` (`:23`), not on a call — it resolves members through the type engine and calls no resolve API. Nothing downstream consumes it except the branch §2.2 adds |

**`LuaRenameConflictDetector` — a rename conflict is withdrawn together with the usage that raised
it.** Clause C1 `captures` ([:154-172](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt))
scans the **usage list** `ReferencesSearch` produced, so the mirror direction reaches it: a site that
stops being a usage stops being a capture candidate. Executed through the real refactoring —
`myFixture.renameElementAtCaret("n")` — on `local t = {}` / `function t:m() end` / `local m = 1` /
`print(m)` / `do` / `local n = 2` / `t:m()` / `end`, caret on `local m`:

| | pre-feature | under the branch |
| :-- | :-- | :-- |
| outcome | `ConflictsInTestsException`: *"Renaming to 'n' would bind a usage of 'm' to a different declaration that is already visible here."* | the rename **applies**, giving `local n = 1` / `print(n)`, with `t:m()` and the inner `local n = 2` untouched |

The fixture is shaped so the colon call is the **only** capturing site — `print(m)` sits outside the
`do` block and sees no `n`. That shape is required, not incidental: `distinctByAnchor`
([:359](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt))
collapses collisions by capturing declaration, so a fixture where another usage also saw `n` would
report one conflict on **both** sides and hide the flip entirely. Withdrawing the conflict is correct
— the site was never a usage of the local — and `NAV-13-08` scopes it in.

### What bounds this enumeration, and what would not

Each instrument that gates `NAV-13-07`/`NAV-13-08` has a stated blind spot.

- **The corpus ratchet cannot see this feature's characteristic effect.** The pinned corpus carries
  0 `---@` tags across its 734 files, so no annotated-receiver resolution and no `---@param`-seeded
  inference change is observable on it at all. `NAV-13-07`'s no-change half rests on it anyway,
  because it is the only instrument that covers the corpus's *un-annotated* breadth; the exception
  it cannot see is pinned by case 19 instead, and cases 21-33 pin the user-visible effects for the
  same reason.
- **The surface half is closed by the two-half derivation, and here is what that rule still cannot
  see.** The set above is derived from `plugin.xml` by the call/receive rule rather than recalled,
  and its residue — helper files reached from a registered class — is enumerated and driven in its
  own subsection. These classes of consumer remain outside it, stated concretely rather than as a
  disclaimer:
  - **A surface with no Lunar class at all.** Go to Declaration, <kbd>Ctrl</kbd>+click, the Find
    Usages tool window, Search Everywhere's preview and the navigation bar are the *platform's*
    classes acting on `LuaNameReference`; nothing under `src/main/` names them (`GotoDeclarationAction`:
    0 hits in `src/main` and `src/test`), so no derivation over Lunar source can list them. They
    inherit the route table's first row — each consumes `resolve()`/`multiResolve` and moves exactly
    as it does — but that is an argument, not a measurement, which is why

    **Which of them a checklist actually drives, precisely:** Go to Declaration, <kbd>Ctrl</kbd>+click
    and the Find Usages window are driven live by HV-1 through HV-6 and HV-9 step 2. **Search
    Everywhere's preview and the navigation bar are driven by no checklist step.** Both are read-only
    render surfaces — neither can write to a file, which is why Shift+F6 was the one singled out for
    a live step — so they rest on the argument alone. Recorded here rather than left inside a
    sentence that names all five together.
  - **A registered consumer that receives the element by a data key the rule does not name.**
    `LangDataKeys.PSI_ELEMENT_ARRAY`, `PlatformCoreDataKeys.SELECTED_ITEMS`,
    `UsageView.USAGE_TARGETS_KEY` and `CommonDataKeys.SYMBOLS` all carry resolved elements and none
    is a receive spelling above. Checked at `7a1dc387`: each has **0 hits** across `src/main/kotlin`
    and `src/test/kotlin`, so the omission costs nothing today — and it is the exact shape that
    would cost something silently if a future consumer used one. A consumer added on one of those
    keys must be added to the receive half at the same time.
  - **A consumer outside `net.internetisalie.lunar`.** The rule is scoped to Lunar's own
    registrations, so a dependency plugin registering over Lua PSI is invisible to it.
- **The fixture half stays open** — an effect that needs a receiver or declaration shape no fixture
  spells is still invisible, and no instrument this feature has would report it. `LuaTypeHierarchyProvider`
  is what that costs when the surface half is fixed and the fixture half is not: the shape it needs
  (a `---@class` local whose name coincides with a colon member name) appeared in no fixture this
  feature had, and driving it required writing one.

**What would close the fixture half, and why it is not done here.** An annotated corpus lane — the
`lua-language-server` substitute of DR-01, promoted from a one-off measurement to a ratchet — would
let the corpus observe `---@`-dependent effects instead of being structurally blind to them. That is
a test-infrastructure feature, not a clause of this one: it needs a vendoring decision, a licence
review and a baseline of its own, and `risks-and-gaps.md` files it as `MAINT` work rather than
smuggling it into NAV-13's scope. Until it exists, **the pinned set is the set somebody drove**, and
this design says so rather than implying the enumeration is exhaustive.

### `LuaDeprecatedApiInspection` moves in both directions, and both are in scope

Its `isDeclaration` gate ([:62-72](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt))
tests `LuaLocalFuncDecl` / `LuaAttName` / `LuaFuncName` / `LuaNameList` and has **no `LuaMethodExpr`
branch**, so every colon member name reaches its `multiResolve` at [:36-37](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt).
Because `getDeprecatedTag` ([:74-105](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt))
walks `PsiTreeUtil.getParentOfType(resolved, LuaCatsCommentOwner)` on whatever leaf comes back, the
change moves the warning both ways.

**Direction 1 — withdrawn, and the withdrawal is the correct answer.** Executed on
`---@deprecated Use the method instead` / `local function m() end` / `local t = {}` /
`function t:m() end` / `t:m()` / `m()`:

| | `Deprecated API: Use the method instead` warnings |
| :-- | :-- |
| pre-feature | `85..86 'm'` (the `function t:m()` declaration's method name), `95..96 'm'` (**the `t:m()` call site**), `99..100 'm'` (the `m()` call) |
| under the branch | `85..86`, `99..100` — **`95..96` is withdrawn** |

`resolve()` on the call site's `m@95` moves from `LeafPsiElement@53 'm'` — the deprecated *local
function* — to `LeafPsiElement@85 'm'`, the method's own name leaf. `t:m()` does not call the local
`m`, so the withdrawn warning was asserting a call that does not exist. The warning at `85..86`
survives because the declaration's `m` has a `LuaFuncNameMethod` parent, not a `LuaMethodExpr`, so
§3.1's gate never sees it and its lexical resolution is untouched.

**Direction 2 — added, and much narrower than the shape suggests.** Where the call now resolves to a
`---@deprecated` **method**, `getDeprecatedTag`'s `LuaFuncDecl` guard
([:81-86](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt))
compares the resolved leaf against `commentOwner.funcName.nameRef.identifier` — and for
`function t:m()` that accessor yields the **receiver** `t`, not the method `m`. So the tag is
returned only where the receiver's name text equals the member's. Executed, with the guard's own
state recorded:

| fixture | pre-feature | under the branch | recorded guard state |
| :-- | :-- | :-- | :-- |
| `local t = {}` / `---@deprecated gone` / `function t:m() end` / `t:m()` | no warning | **no warning** | `owner=LuaFuncDeclImpl deprecatedTags=1 funcName.nameRef.identifier='t'@42 identityEq=false textEq=false` |
| `local m = {}` / `---@deprecated gone` / `function m:m() end` / `m:m()` | `44..45 'm'` | `44..45` **and `54..55 'm'`** — the call site | `owner=LuaFuncDeclImpl deprecatedTags=1 funcName.nameRef.identifier='m'@42 identityEq=false textEq=true` |
| `---@class Builder` … `---@deprecated gone in 2.0` / `function Builder:setName(n) end` / `---@type Builder` / `local b` / `b:setName("x")` | no warning | no warning | receiver `Builder` ≠ member `setName` |

**Both directions are admitted, neither is prevented, and the reason differs.** Direction 1 is a
correctness fix and is admitted for that reason. Direction 2 is *also* correct where it fires — the
method really carries `---@deprecated` and the call site really calls it — so suppressing it inside
NAV-13 would mean hiding a true warning to keep a diff small. What is wrong is that it fires only on
a name coincidence, and that is a defect in `getDeprecatedTag`'s guard (it compares against
`funcName.nameRef`, which is the receiver for a colon declaration), not in this feature.
`risks-and-gaps.md` "Technical Debt" carries it as the inspection's to fix.

**The corpus cannot see either direction**: it carries 0 `---@` tags across its 734 files, so
`NAV-13-07`'s ratchet is structurally blind here in exactly the way DR-03 found for TYPE-10.
`NAV-13-08` governs both directions and `requirements.md` cases 21 and 22 pin them as unit fixtures.


## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
|---|---|---|
| `NAV-13-01` | M | §2.1, §2.2, §3.1, §3.3, §3.4, §3.5, §3.6 |
| `NAV-13-02` | M | §3.5 (leaf normalisation), §3.7 |
| `NAV-13-03` | M | §3.7, §5 Example 2 |
| `NAV-13-04` | M | §3.3 Refusals A, B and C, §3.4 agreement rule, §3.5 step 1, §6 |
| `NAV-13-05` | M | §3.1 (the gate confines the change), §3.6 decision 1, §7's two consumer tables |
| `NAV-13-06` | M | §2.3, §2.4, §3.6 decisions 2 and 3; the cross-file fan-out bounded by `risks-and-gaps.md` DR-04 |
| `NAV-13-07` | M | §3.6 decision 3. The no-change half is gated by the corpus ratchet; the scoped exception is stated in §3.6 decision 3 and §7 and pinned by `requirements.md` case 19, because the annotation-free corpus cannot observe it |
| `NAV-13-08` | M | §7 — the executed consumer enumeration over **both** halves of the `plugin.xml` derivation (call and receive) plus its residue, the two `LuaDeprecatedApiInspection` directions, the rename, documentation and inlay-hint rows, and the three receive-half consumers driven on both sides. Pinned by `requirements.md` cases 21-33 — cases 21-24 and 31-32 on the call site's side, cases 25-30 and 33 on the same-named declaration's; the annotation-free corpus can observe none of them |

## 9. Alternatives Considered

- **A — a `psi.referenceContributor` on `LuaNameRef`, as REFACT-08 did for LuaCATS type names.**
  Rejected on executed evidence. `REFACT-08-00-DR-02` found a contributor **inert** for cats elements
  because `LuaCatsBaseElement` never consulted `ReferenceProvidersRegistry`; the same does **not**
  hold here — `LuaBaseElement.getReferences()` merges contributed references with its own
  ([LuaBaseElements.kt:36-47](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt)).
  A throwaway contributor registered for `LuaNameRef` was measured producing
  `refsCount=2 refsClasses=LuaNameReference,Nav13MarkerReference` — so it *is* reached. It is still
  the wrong route: `getReference()` is an independent override on `LuaNameRefBaseImpl`
  ([:129-136](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt)) that
  returns the `LuaNameReference` alone, and both `LuaNameReferenceSearcher`
  ([:73](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt))
  and every production `resolve()` caller read `.reference`, not `.references`. The contributed
  reference was measured invisible to all of them. Adopting it would mean overriding `getReference()`
  as well — REFACT-08's two-override fix — which is a larger change than the one branch §2.2 needs,
  and would leave two references on one element competing for `findReferenceAt`.
- **B — a second `PsiReference` class plus its own `referencesSearch` executor.** Same objection as A
  plus a redundant searcher: `LuaNameReferenceSearcher` already scans every `LuaNameRef` of the right
  text. `CachesBasedRefSearcher` cannot serve either shape (the target is a bare leaf, not a
  `PsiNamedElement`), which is why that searcher exists at all — a second copy would duplicate it.
- **C — `LuaTypeInlayHintProvider.unwrapExpression(call.varOrExp)` as the receiver handle**, which is
  what `REFACT-09` design §3.6 specifies. Measured equivalent for the accepted shape — it returns the
  same `LuaNameRef` for a bare-name receiver — but for `a.b:m()` it returns the `LuaVar`, which types
  and resolves a member, so the refusal would have to be re-derived downstream instead of being a
  property of the handle. §3.3 states the rule from the grammar, where the two step kinds
  `varSuffix ::= nameAndArgs* indexExpr` allows are both refused by one clause.
- **D — `receiverType.getMembers()[memberName]` instead of the arm loop.** `LuaUnionType.getMembers`
  merges across arms with `putIfAbsent`
  ([LuaComplexTypes.kt:26-35](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaComplexTypes.kt)),
  so it would find the aliased-class member too — but it enumerates every member of every arm to
  answer one name, and it hard-codes "first arm wins" where §3.4 refuses a disagreement.
- **E — admitting a `LuaField` or `LuaAssignmentStatement` declaration.** Rejected: §3.5 step 1's
  measurement shows neither is a `LuaDeclarationSite`, so the site would navigate somewhere Find
  Usages can never point back from.
- **F — falling through to the two-phase path when the colon route finds nothing.** Rejected on the
  measurement in §3.6 decision 1: it is what produces today's 441 lexical bindings.
- **G — widening `visitFuncCall` to model each `nameAndArgs` segment, so a chain's second segment
  could resolve.** Out of scope by [[TYPE-13]] design §8 and Gap 2.12; it is a visitor change with
  the member-map blast radius [[TYPE-13]] Risk 1.1 describes. `risks-and-gaps.md` "Technical Debt"
  carries it.

## 10. Open Questions

_None — feature has cleared the planning bar._
