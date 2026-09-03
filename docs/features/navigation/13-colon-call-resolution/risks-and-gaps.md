---
id: NAVIGATION-13-RISKS
title: "13: Colon Call Site Resolution — Risks & Gaps"
type: risk
parent_id: NAVIGATION-13
folders:
  - "[[features/navigation/13-colon-call-resolution/requirements|requirements]]"
---

# NAV-13: Risks and Gaps

Everything below was executed on the gce builder against the working tree at `main` `f1ac26cc`,
through throwaway probes and a throwaway prototype of `design.md` §2–§3. Every probe and every
production edit was reverted; the artifacts carry the output, not the code.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| `NAV-13-00-DR-01` | Measure reach **before** designing: how many of the corpus's colon-method declarations gain a resolving call site, which receiver-handle rule maximises that, and what a colon member name resolves to today. | the disposition of the whole feature; `design.md` §3.3, §3.4 | **done — result below** |
| `NAV-13-00-DR-02` | Prototype the design end to end: where the resolution hangs, whether `ReferencesSearch` returns a usage set, what each clause's mutation does, and what it costs. | `design.md` §2, §3.5, §3.6, §3.7; Risk 1.1, Risk 1.2 | **done — result below** |
| `NAV-13-00-DR-03` | Does TYPE-10's expected-callback seeding fire for a colon call today, and does the guard withdraw it? Run it; do not read it. | `design.md` §3.6 decision 3, §5, §7; `NAV-13-05`, `NAV-13-07`; Gap 2.4 | **done — result below** |
| `NAV-13-00-DR-04` | Bound the cross-file fan-out of the per-file guard with a measurement that grows the file count, rather than a wall-clock. | Gap 2.3; `implementation-plan.md` Phase 3 | **done — result below** |
| `NAV-13-00-DR-05` | Enumerate the downstream consumer set **by execution** — instrument the branch and record who reaches it — instead of reading each consumer's gate for a missing `LuaMethodExpr` case, and measure each one's observable output on both sides. | `design.md` §7; `NAV-13-05`, `NAV-13-08`; Risk 1.3 | **done — result below** |
| `NAV-13-00-DR-06` | Drive every element-taking surface at the **other** end of the binding this feature breaks — the same-named declaration, not the call site — and derive the surface set from `plugin.xml` rather than from a hand-written list, so the enumeration is systematic rather than recalled. | `design.md` §7; `NAV-13-05`, `NAV-13-08` | **done — result below** |
| `NAV-13-00-DR-07` | DR-06's derivation selects consumers that **call** a resolve API. Widen it to consumers that **receive** the resolved element from a platform data rule and call nothing, drive every one it adds on both sides, and state concretely what the widened rule still cannot see. | `design.md` §7; `NAV-13-08`; Risk 1.3 | **done — result below** |

---

### DR-01 result — reach, measured first

**Why this comes before the design.** [[REFACT-09]] was planned to the Planning Bar across two Step 9
rounds and then measured to accept **0 of 941** colon-method declarations on real code. Its predicate
was sound; its reach was never measured until the end. This feature's first act was the reach
measurement.

**Method — stated so a third party gets the same figure.** Throwaway `BasePlatformTestCase` probes
transcribed `design.md` §3.3–§3.5 and asked the type engine, at every colon call site, for the
member's declaration. Each choice below fixes the number, and each is a choice rather than an
obvious default:

1. **Scope**: each pinned checkout's whole `.lua` tree — the same four checkouts and the same
   whole-tree scope [[REFACT-09]] Gap 2.3 used, **not** the sweep's narrower `roots`.
2. **Fixture-project granularity: one fixture project per corpus, and it is load-bearing.**
   `LuaTypeManagerImpl.resolveType` searches `GlobalSearchScope.allScope`, so a single combined
   734-file project lets a global name declared in one checkout answer a lookup made in another.
   Measured on the same probe in the same run: combined, the arm-wise rule reaches the same **372**
   call sites but only **73** distinct declarations against the isolated **84** — the three
   module-style corpora's 11 declarations collapse onto zerobrane's. Every figure in table 1 is the
   isolated one, which is also the shape a user is in: one project open.
3. **The declaration denominator**: an IDENTIFIER leaf whose `LuaDeclarationSite.kindOf` is
   `METHOD_FUNCTION` — the same one [[REFACT-09]] used.
4. **The call-site denominator**: a `LuaMethodExpr` whose parent is a `LuaNameAndArgs` whose parent
   is a **`LuaFuncCall`** — i.e. exactly the sites `design.md` §3.3 steps 2–3 can accept. Counting
   every `LuaMethodExpr` instead gives **14 191**, because `varSuffix ::= nameAndArgs* indexExpr`
   ([lua.bnf:294](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf)) puts 75
   further colon sites — 74 in zerobrane, 1 in luacheck — under a `LuaVarSuffix` rather than a call.
   `receiverOf` refuses all 75 at step 3, so they are correctly outside the denominator.
5. **Agreement rule**: a site counts as resolving when `declarationLeaves(...).singleOrNull()` is
   non-null, matching `design.md` §3.6. Dropping to "non-empty" changes nothing on the pinned
   corpus — measured, `singleOrNull` and non-empty both give 372 sites and 84 declarations.

**Reproducibility protocol.** The probe measures the same copied tree **twice** in one test method
and the two passes must agree; a run whose passes disagree is discarded rather than reported.
Executed on zerobrane, the two passes were identical, and every further independent measurement
returned the same figures — in-class, and with the zerobrane method run alone.
Assert the denominators (`files`, `declLeaves`, `underFuncCall`) before reading the numerator: they
reproduce exactly and a change in them means the scope, not the algorithm, moved.

```
files=734   colon-method declaration leaves=941   colon call sites=14116
```

**Table 1 — reach on the pinned corpus.** `plain` is `LuaType.resolveMember` alone; `arm-wise` adds
`design.md` §3.4's union-arm loop and its single-answer rule. "Declarations gaining a call site" is
the number of distinct `function X:m()` declarations for which at least one call site resolves.

| corpus | files | colon-method decls | colon call sites | bare-name first-segment sites | sites resolving (arm-wise) | decls gaining a call site (plain) | decls gaining a call site (arm-wise) |
| :-- | --: | --: | --: | --: | --: | --: | --: |
| luacheck | 135 | 142 | 805 | 490 | 14 | 0 | 3 |
| luarocks | 160 | 80 | 1 952 | 1 389 | 101 | 0 | 4 |
| penlight | 114 | 147 | 984 | 777 | 12 | 0 | 4 |
| zerobrane | 325 | 572 | 10 375 | 8 755 | 245 | 68 | 73 |
| **total** | **734** | **941** | **14 116** | **11 411** | **372** | **68** | **84** |

**So the headline is: 84 of 941 colon-method declarations — 8.9% — gain a resolving call site on the
pinned corpus, and 372 of 14 116 call sites — 2.6% — resolve.** That is not the 0 of 941
[[REFACT-09]] measured, and it is not a large number either. It is also **concentrated**: 73 of the
84 are in one project, ZeroBrane Studio, whose OO is written as a file-local `local Pack = {}` plus
`function Pack:method()` plus in-file `p:method()` calls — the one shape [[TYPE-13]] reaches. The
other three projects are module-style (`local M = {}` … `return M`), so their call sites live in a
different file from the declaration and reach nothing (Gap 2.2).

**One earlier run of this probe reported zerobrane at 187 sites / 55 declarations**, and it is
recorded because it is the reason the protocol above exists rather than as an alternative result.
Every subsequent measurement — in-class, with the zerobrane method run alone, and the two-pass
control — returned 245 / 73, on unchanged probe logic, and the outlier has no identified cause.
Read the number as: 84 is what this instrument returns when its two passes agree, and a run that
does not reproduce the denominators or its own second pass is not evidence.

**The protocol does not cover the anomaly it was written for, and it is kept anyway.** The outlier
was a *between-run* disagreement; the protocol tests *within-run* agreement between two passes over
one copied tree. A self-consistent bad run passes it. Two things make that acceptable rather than
merely admitted. First, the syntactic denominators — `files`, `declLeaves`, `rawMethodExpr`,
`underFuncCall`, `bareNameFirst` — have since reproduced byte for byte under an independent
re-execution by a third party, and those are what an outlier of this size would have to move.
Second, Risk 1.1's disposition turns on the reach being **non-zero and concentrated**, not on the
value 84: at 55 or at 84 the conclusion — small on un-annotated code, and the annotated shape is
where the value is — is the same. A protocol that caught between-run drift would have to re-run the
whole probe from a cold JVM some number of times and compare, which prices a stronger guarantee than
any decision here needs.

**Table 2 — which receiver-handle rule, and why the design's is the one.** The rules named here were
measured against each other on one run over all 14 116 sites: `A` is `design.md` §3.3's (the bare
`nameRef` of a suffix-free `var` in the first segment); `B` is the raw `LuaFuncCall.varOrExp`; `C` is
the last `LuaNameRef` anywhere inside the receiver. This table is a *plain* `resolveMember` comparison, and
the plain rule is insensitive to the fixture-project granularity of the Method's point 2 —
executed, the combined and per-corpus projects both give A = 153 sites / 68 declarations — so the
comparison holds under either. The granularity matters only to the arm-wise rule of table 1.

| receiver shape | sites | A: sites reaching a declaration | B | C |
| :-- | --: | --: | --: | --: |
| bare name (`t:m()`) | 11 411 | 153 (plain lookup), giving 68 distinct declarations | 0 | 153, giving 68 |
| suffixed (`a.b:m()`) | 667 | refused by A | 0 | 0 |
| a `varOrExp` with no `var` — `("s"):m()`, `(a or b):m()` | 744 | refused by A | 0 | 0 |
| a `var` with no `nameRef` — `('(' expr ')' varSuffix+)` | 2 | refused by A | 0 | 0 |
| chain segment (`x:m1():m2()`, and `f():m()`) | 1 292 | refused by A | 0 | 4, giving 4 further declarations |

Rule B reaches nothing anywhere — it misses at every one of the 14 116 sites. ([[TYPE-13]] case 17
records why: `varOrExp` types as `Undefined` while the `LuaNameRef` at the same offset carries the
receiver's type. The measurement here is the miss, not the type.) Rule C's four extra sites are all
chain segments, and rule C's handle is a `LuaNameRef` *inside `varOrExp`* — that is, inside the
**receiver** — so what it resolves there is a member of the receiver rather than of the chain's
value, which is [[TYPE-13]] Gap 2.12's silently-wrong direction by construction. **Refusing every
non-bare shape costs nothing measurable and removes the only route by which this feature could
produce a plausible-but-wrong target.**

**Table 3 — what a colon member name resolves to TODAY**, over every colon call site in both trees,
measured on the unmodified plugin:

The columns are the categories the probe printed, unmerged: `LuaDeclarationSite.kindOf` of the
resolved element where it is a declaration leaf, and the element's PSI class where `kindOf` is null
(a whole-declaration node returned by the stub-index phase).

| tree | colon call sites | nothing | local variable | local function | global variable | global function | a `LuaFuncDecl` node | a `LuaLocalVarDecl` node | a `METHOD_FUNCTION` |
| :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| luacheck | 805 | 749 | 28 | 3 | 0 | 14 | 11 | 0 | 0 |
| luarocks | 1 952 | 1 923 | 3 | 10 | 0 | 0 | 16 | 0 | 0 |
| penlight | 984 | 837 | 77 | 10 | 8 | 2 | 50 | 0 | 0 |
| zerobrane | 10 375 | 10 166 | 67 | 15 | 0 | 14 | 113 | 0 | 0 |
| lua-language-server | 2 446 | 2 357 | 17 | 2 | 0 | 0 | 68 | 2 | 0 |

Sampled bindings, transcribed: `script/client.lua` `'find'` → a `LuaFuncDecl` in a **different**
file; `script/core/diagnostics/newline-call.lua` `'sub'` → a local variable `sub`;
`script/core/code-lens.lua` `'resolve'` → a local function `resolve`. **Not one non-null binding in the table above is a method declaration.** This is what `NAV-13-05`'s withdrawal
clause is written against.

**The `LuaFuncDecl` node column is also the population DR-03 is about.** Those bindings are whole
declaration nodes returned by the stub-index phase, not leaves, so `resolved.parent?.parent` is
whatever encloses the declaration — an enclosing `LuaFuncDecl` whenever the declaration is nested.
That is the shape in which TYPE-10's expected-callback seeding fires for a colon call today, and
withdrawing it is the inference change `NAV-13-07` scopes in.

**The annotated dimension, and what it is a substitute for.** The pinned corpus carries **0 `---@`
tags across all 734 files** (verified for this feature: `grep -rl -- "---@" test/corpus` returns
nothing; the same fact is recorded at `build.gradle.kts:322-326` and by BUG-473 DR-6), so the
annotated receiver shape — the one with the widest reach — **cannot be sized on it at all**. The
substitute is the one `REFACT-08-00-DR-01` used and had scrutinised in its own Step 9:
**`lua-language-server` 3.10.6 at `66141703`**, the 195 `.lua` files under `meta/`, `script/` and
`tools/` that carry a `---@` tag, staged into the out-of-repo `test/` tree and then deleted.

**To re-run it** you need that checkout at that commit — it is at
`~/Documents/src/lua/lua-language-server`, and `git describe --tags` must print
`3.10.6-6-g66141703` — plus the staging step, which is
`grep -rl -- '---@' meta script tools` piped into a copy that preserves the relative paths, into a
directory under `test/` (outside the repo, so it never enters git and the builder's `-L` rsync
carries it). Then run the same probe over that directory with every choice in the Method unchanged,
and delete the staging from **both** trees. **Reproduced on a second staging of that tree**: the row
below came back byte for byte, including `underFuncCall` = 2 446 against 2 453 raw `LuaMethodExpr`
sites — the call-site denominator of the Method's point 4, confirmed independently of the first run.

| | files | colon-method decls | colon call sites | bare-name sites | sites resolving | decls gaining a call site (plain) | decls gaining a call site (arm-wise) |
| :-- | --: | --: | --: | --: | --: | --: | --: |
| lua-language-server 3.10.6 | 195 | 268 | 2 446 | 1 312 | 443 | 50 | **121** |

**121 of 268 — 45.1% — of its colon-method declarations gain a resolving call site**, against 8.9%
on the un-annotated corpus.

**What the substitute is and is not representative of.** It is a large, actively maintained,
heavily-annotated Lua codebase written by people who use a Lua language server, so it is a fair model
of *a project that annotates*. It is **one** such project, by one team, in one style, so the 45.1% is
a single observation and not a distribution — it says the annotated shape is where this feature's
value is, not how much value an arbitrary annotated project gets. It is also the same substitute
`REFACT-08` used, which makes the two features' reach figures comparable and inherits that choice's
review. **The dimension that remains unmeasurable is how much Lua in the wild is annotated at all**;
the pinned corpus says 0 of its 734 files, and `lua-language-server` says **304 of the 432** `.lua`
files outside its vendored top-level `3rd/` tree. The **195** that appears above is a different,
narrower scope — the annotated files under `meta/`, `script/` and `tools/` only, which is the
subtree staged as the substitute — so the two numbers are not alternatives and neither is the
other's correction. Both measured at `66141703` with
`find <tree> -name '*.lua' -not -path '<tree>/3rd/*' -exec grep -l -- '---@' {} + | wc -l`, scoped
to the whole tree and to `meta script tools` respectively; the denominators are 432 and 275 by the
same command without `grep`. Neither is a sample of anything. That is stated rather than argued away, and Gap 2.1 carries the consequence.

---

### DR-02 result — where the resolution hangs, and what it costs

A throwaway prototype of `design.md` §2.1–§2.4 was applied to `f1ac26cc` and driven by throwaway
probes. All were reverted; `git status --porcelain` is clean in the working tree and the
builder tree carries no `Nav13*` file.

**Finding 1 — a `psi.referenceContributor` reaches a `LuaNameRef`, and is still the wrong route.**
`REFACT-08-00-DR-02` measured a contributor **inert** for cats elements. That does not carry over,
and it was executed rather than assumed: a throwaway contributor registered for `LuaNameRef` in
`plugin.xml` was measured attaching to every colon call's member name —

```
NAV13BASE[plainLocal] … refClass=LuaNameReference refsCount=2
                         refsClasses=LuaNameReference,Nav13MarkerReference
```

— because `LuaBaseElement.getReferences()` merges `ReferenceProvidersRegistry`'s output with its own
([LuaBaseElements.kt:36-47](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt)),
which `LuaCatsBaseElement` did not until REFACT-08 fixed it. **`refClass` is the finding**:
`getReference()` returns the `LuaNameReference` alone, and `LuaNameReferenceSearcher` reads
`nameRef.reference`
([:73](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt)),
as does every production `resolve()` caller. A contributed reference is therefore reachable through
`getReferences()` and invisible to everything that matters. It is REFACT-08's own finding —
*"[getReferences] alone leaves every consumer that reads `element.reference` … seeing null even
though a reference exists"*
([LuaCatsBaseElements.kt:21-23](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsBaseElements.kt))
— reached from the other side: here `getReference()` is an independent override that ignores the
registry, so the contributed reference is the one nothing reads. `design.md` §9 Alternative A records
this; the design branches inside `LuaNameReference` instead.

**Finding 2 — `ReferencesSearch` is 0 before and returns the call sites after, with no change to the
searcher.** Measured on the unmodified plugin at `f1ac26cc`, `ReferencesSearch.search(<declaration
leaf>, allScope(project))` returned `references=0` for the plain local table, the in-file global
table, `setmetatable` OO, the `---@class`-annotated receiver, the alias, the parameter receiver, the
factory, `self`, the chain and the suffixed receiver. With the prototype applied:

| fixture | `resolve()` | `ReferencesSearch` on the declaration leaf |
| :-- | :-- | :-- |
| `local t = {}` ; `function t:m() end` ; `t:m()` | `m@24` | `references=1`, `isReferenceTo=true` |
| `Obj = {}` ; `function Obj:m() end` ; `Obj:m()` | `m@22` | `references=1`, `isReferenceTo=true` |
| `setmetatable` OO | `m@54` | `references=1`, `isReferenceTo=true` |
| `---@class Builder` ; `local b = Builder` ; `b:setName("x")` | `setName@54` | `references=1`, `isReferenceTo=true` |
| `---@type Builder` ; `local b` ; `b:setName("x")` | `setName@54` | `references=1`, `isReferenceTo=true` |
| the same `---@type` receiver in a **second file** | `setName@54` in `cls.lua` | `references=1`, `isReferenceTo=true` — the usage is in `useb.lua` |
| two receivers with a same-named method | `m@24` and `m@56` | `references=1` each, neither seeing the other's site |
| `local m = 1` beside `t:m()` | `m@24` — the method, not the local `m@38` | `references=1` |
| `A:next():go()` (second segment) | null | `references=0` on both `go` declarations |
| `a.b:m()` | null | `references=0` |
| `local t = { m = function() end }` ; `t:m()` | null | — |
| `t.m = function() end` ; `t:m()` | null | — |
| `function m() end` ; `local function f(x) x:m() end` | null | — |
| `self:m()`, factory, alias, `require`d module, cross-file global table | null | `references=0` |

Corpus-wide after the change, over the same colon call sites DR-01 table 3 censused, **every**
non-null resolution is a `METHOD_FUNCTION` declaration leaf and **no** lexical binding remains. The
properties asserted here are the *kind* of every surviving binding, and that the per-tree site counts
equal DR-01 table 1's arm-wise column — the same figure
reached by two independent probes. Read the counts from table 1; repeating them here would be a
second source of truth that nothing keeps in sync. `implementation-plan.md` Phase 4 re-establishes
these properties against the shipped code.

**Finding 3 — every clause has an executed, reachable falsifier, except one, which is named.** A
`Nav13MutationProbe` transcribed `declarationLeafOf` and re-ran it per fixture with exactly one
clause removed; the unmutated transcription was control-checked against the production function on
every fixture and agreed on all of them.

| clause removed | fixture it changes | production | mutant |
| :-- | :-- | :-- | :-- |
| the first-segment test (`design.md` §3.3 Refusal A) | `A:next():go()`, second segment | null | `go@24` — `function A:go()`, the receiver's own member |
| the `varSuffixList.isEmpty()` test (Refusal C) | `a.b:m()` with `function a:m()` present | null | `m@33` — `function a:m()`, the head's own member |
| the `as? LuaFuncDecl` cast (§3.5 step 1) | `local t = { m = function() end }` | null | `LuaFieldImpl@12` |
| the same cast | `t.m = function() end` | null | `LuaAssignmentStatementImpl@13` |
| the same cast | `function t.m() end` ; `t:m()` | null | `LuaFuncDeclImpl@13` |
| the same cast | `local t = {}` ; `function t:m() end` ; `t:m()` | `m@24` (a leaf) | `LuaFuncDeclImpl@13` (a statement) |
| `singleOrNull` → `firstOrNull` (§3.4) | `---@type A\|B`, both arms declaring `m` | null | `m@36` — arm `A`'s |
| the union-arm loop (§3.4) | `---@class Builder` ; `local b = Builder` | `setName@54` | null — `LuaUnionType.resolveMember` misses because the anonymous arm has no `setName` |
| the `isSnapshotUnderConstruction` guard (§3.6) | the 80-call-site fixtures | budgets hold | `WRITE` 812 / 814 — the budget methods that predate this feature fail |
| the exclusivity (§3.6 decision 1) | `function m() end` ; `local function f(x) x:m() end` | null | the global `function m()` |
| `declaresMember = true` at the `funcNameMethod` mint ([LuaTypesVisitor.kt:848](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)) — a [[TYPE-13]] mutation, executed here | the plain local table, the in-file global table and `setmetatable` OO | `m@24` / `m@22` / `m@54` | null in all three — while the `---@class` receiver keeps resolving at `setName@54`, so this also separates the structural route from the nominal one |

**And one candidate mutation that does NOT redden the row it was written for.** `requirements.md`
case 2 pins the in-file global table. [[TYPE-13]] case 4's discriminator — `declareFileGlobals` as a
no-op — was applied and the fixtures re-run, and the global row **still resolved at `m@22`**, along
with every other row. That is [[TYPE-13]] Gap 2.9's third table entry reproduced at this feature's
level: the mutation collapses the *write-target* handle `Obj@0`, and a colon call site resolves
through the *call receiver* `Obj@30`, which survives it. Case 2's falsifier is therefore the mint
mutation above, which does not separate it from case 1 — recorded under "Test Case Gaps" rather than
papered over.

**The one clause with no reachable falsifier is `methodNameLeafOf`'s `takeIf { it.text ==
memberName }`.** Removing it changed **no** fixture, and across every measured call site in both trees the
declared method name never differed from the call's member name. `design.md` §3.5 keeps it and says
so; `requirements.md` records it as unfalsifiable rather than pairing it with an assertion that
cannot fail.

**Finding 4 — the un-guarded design is a cost regression, and it is disqualifying.** The type engine
resolves the member name of every colon call while building the snapshot
([LuaTypesVisitor.kt:1105](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt)
→ [LuaExpectedCallbackResolver.kt:47-51](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaExpectedCallbackResolver.kt)),
so an un-guarded branch answers the engine from the snapshot under construction. Measured on the two
80-call-site fixtures, with counters for how often the branch was entered and how often it consulted
the engine:

| fixture | variant | after `forFile` | after 160 resolutions | branch entries mid-build | engine consultations |
| :-- | :-- | :-- | :-- | --: | --: |
| annotated | un-guarded | `WRITE` 812 · `READ` 727 | `WRITE` 814 · `READ` 728 | 160 | 320 |
| annotated | guarded | `WRITE` 572 · `READ` 647 | `WRITE` 574 · `READ` 648 | 80 | 160 |
| un-annotated | un-guarded | `WRITE` 483 · `READ` 162 | `WRITE` 485 · `READ` 163 | 80 | 240 |
| un-annotated | guarded | `WRITE` 165 · `READ` 83 | `WRITE` 165 · `READ` 83 | 80 | 160 |

What follows, and each part is load-bearing:

1. **The guarded annotated numbers are 572 / 647 / 325 — exactly the values
   `LuaTypeGraphRootResolutionBudgetTest`'s KDoc records for the unmodified plugin**, and 574 / 648
   after conversion, exactly the values [[TYPE-13]] Phase 4 committed. The guard restores the
   pre-feature cost precisely rather than approximately, and that identity is the control which says
   the guard is complete.
2. **Un-guarded, the budget methods that predate this feature fail** — measured, `WRITE was re-derived at a walk
   root 812 times, over the budget of 600` and `814 times, over the budget of 620`. Under the
   un-guarded prototype those were the **only** failures in the full unit suite; the suite's total is
   not recorded here because it counts the throwaway probes' own methods.
3. **Bypassing `ResolveCache` is not the expensive part.** 160 consecutive uncached resolutions cost
   2 `WRITE` and 1 `READ` on the annotated fixture and **0** on the un-annotated one: the `RootMemo`
   BUG-473 introduced absorbs the repeats. The cache bypass is what stops the mid-build refusal being
   retained for the rest of the PSI generation, and it is affordable.

**Finding 5 — the full unit suite and the corpus lane are both green under the guarded prototype.**
The unit suite passes with the budget methods that predate this feature unchanged. The corpus lane
was then run — `test -PwithCorpus --rerun --no-build-cache`, 20 min 5 s, **BUILD SUCCESSFUL** — and
`LuaCorpusSweepTest`, `LuaTortureCorpusTest`, `LuaInspectionParityTest`,
`LuaAnnotatedFixtureSweepTest`, `BaselineRatchetTest`, `LexerInvariantsTest` and `ParseOracleTest`
each reported `failures="0" errors="0"`, with no `Corpus regression:` line and no `[corpus] IMPROVED`
line. The XML timestamps were checked against the run rather than assumed, because `--rerun` does not
clear `build/test-results/test/`. That run is `NAV-13-07`'s evidence. The budget counters it printed:

```
NAV13BUDGET WRITE=165 budget=180              (the new un-annotated method)
NAV13BUDGET READ=83   budget=92
NAV13BUDGET WRITE=574 budget=620              (conversionPath…, unchanged from TYPE-13)
NAV13BUDGET READ=648  budget=700
NAV13BUDGET WRITE=572 budget=600              (annotatedClassFixture…, unchanged from BUG-473)
NAV13BUDGET READ=647  budget=1000
NAV13BUDGET DECLARED_DEMAND=325 budget=500
```

Test totals are deliberately not recorded: they count the throwaway probes' own methods and cannot be
reproduced from a clean tree.

**Finding 6 — the refusals that have no NAV-13-side falsifier were confirmed to have none, by
executing the candidate.** `requirements.md` case 15 pins `self:m()`, a factory-returned receiver, an
alias, a parameter receiver and a `require`d module as resolving to nothing. The candidate falsifier
is [[TYPE-13]] case 6's mutation — make `declaringNodeOf` return the start node when the walk finds
nothing. It was **applied to `LuaMemberDeclarations.declaringNodeOf` and the whole fixture set
re-run**, and every NAV-13 outcome was identical to the unmutated run: `selfRecv`, `factory`,
`alias`, `paramCollide` and the cross-file global all still `resolve=null`, and every accepted
fixture still resolved to the same leaf. The mutation's start node is a `LuaMethodExpr`, which
`methodNameLeafOf`'s `as? LuaFuncDecl` cast refuses, so it cannot reach these rows. Reverted.

---

### DR-03 result — TYPE-10's seeding DOES fire for a colon call, and this feature withdraws it

Executed on the **unmodified** tree at `30052d62`, then again under the guarded prototype. The
fixture puts a nested global declaration inside an annotated function, which is the shape DR-01
table 3's `LuaFuncDecl` node column counts:

```lua
---@param cb fun(a: string)
function outer(cb)
  function m(q) end
end
local t = {}
t:m(function(z) return z end)
```

| probe | pre-feature (`30052d62`) | guarded prototype |
| :-- | :-- | :-- |
| `resolve()` on the call's `m` | `LuaFuncDeclImpl@49` — `function m(q) end` | `null` |
| `resolved.parent` / `.parent.parent` | `LuaBlockImpl` / `LuaFuncDeclImpl` | — |
| `resolved.parent?.parent as? LuaFuncDecl` matches | **true** | false |
| inferred type of the lambda's `z` | **`string`** | `unknown` |
| the same fixture spelled `t.m2(function(z2) …)` (control) | `unknown` | `unknown` |

**So `LuaExpectedCallbackResolver.resolveMethodCalleeType` is not dead code today.** The cast it
requires succeeds whenever the pre-feature resolution returns a whole `LuaFuncDecl` **node** — which
the stub-index phase does, keyed on the bare member name
([LuaNameReference.kt:135-144](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt)) —
and that declaration is nested inside another `LuaFuncDecl`. TYPE-10 then seeds the lambda parameter
from the **enclosing** function's `---@param`, for a call the enclosing function does not make.

**The seeding is unsound, so the withdrawal is scoped in as intended behaviour** rather than
prevented. `t:m(...)` calls `m`; `outer`'s annotation describes `outer`'s own parameter, and the two
are related only by the accident that one declaration encloses the other. `requirements.md` states it
as `NAV-13-07`'s single exception, and test case 19 pins the withdrawn value alongside the
dot-call control.

**It costs no sound seeding.** The genuine spelling was executed on both sides:

```lua
local t = {}
---@param cb fun(a: string)
function t:m(cb) end
t:m(function(z) return z end)
```

Pre-feature `resolve()` is **null** — a colon method's stub is keyed `t:m`, not `m`, so the bare-name
`LuaGlobalDeclarationIndex` lookup misses — and `z` is `unknown`. Under the prototype the member name
resolves to `LeafPsiElement@52`, whose `parent.parent` is a `LuaFuncNameMethodImpl`, so the cast is
false and `z` is still `unknown`. The seeding TYPE-10 was written for has never fired for a colon
call and does not start firing here; only the accidental one goes away.

**Why neither stated gate could see this.** The corpus ratchet cannot: the pinned corpus carries 0
`---@` tags across all 734 files, so no expected-callback type exists to seed from.
`ExpectedCallbackResolverTest` cannot either: it carries no colon-call fixture at all — every method
in it targets a dotted callee (`redis.register_function`, `table.sort`). Case 19 is a unit fixture
for exactly that reason.

---

### DR-04 result — the cross-file fan-out is bounded, and the bound is +1 per resolved site

Gap 2.3's residual was that `design.md` §3.6's guard is per file while an annotated receiver can
build **another** file's snapshot, and that no measurement bounded the cost of that. The wall-clock
pair that stood in for one bounds nothing. This does.

**Instrument.** `LuaTypeGraph`'s root-resolution counter is per graph, hence per snapshot
([LuaTypeGraph.kt:42](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeGraph.kt),
surfaced by [LuaTypes.kt:73](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt)),
which is why a per-file budget cannot see a fan-out. Summing it over **every** file's snapshot can,
and needs no production change: `LuaTypesSnapshot.forFile` is `CachedValuesManager`-backed, so
re-asking after the resolutions returns the same instances the fan-out charged.

**Fixture.** A ring of `K` files. File *i* declares `---@class Ci` with one method `mi`, and carries
10 colon call sites whose receiver is `---@type C(i+1 mod K)` — so **every** site's class is declared
in another file and every site must cross one. Executed at `30052d62`, baseline against the guarded
prototype:

| K | sites | resolved (base → proto) | summed `WRITE` base → proto | Δ | summed `READ` base → proto | Δ |
| --: | --: | :-- | :-- | --: | :-- | --: |
| 2 | 20 | 0 → 20 | 198 → 218 | +20 | 94 → 114 | +20 |
| 4 | 40 | 0 → 40 | 396 → 436 | +40 | 188 → 228 | +40 |
| 8 | 80 | 0 → 80 | 792 → 872 | +80 | 376 → 456 | +80 |
| 16 | 160 | 0 → 160 | 1 584 → 1 744 | +160 | 752 → 912 | +160 |

**The fan-out is exactly one extra `WRITE` and one extra `READ` root resolution per resolved
cross-file call site, and it does not depend on `K`.** Each proto/base ratio is flat across every `K`
measured — `WRITE` 1.101 and `READ` 1.213 at all of them — rather than growing, so the cost is linear
in call sites and constant in project size. It is not the superlinear blow-up the per-file guard left
open. The `resolved` column is the anti-vacuity control: none of the sites resolve pre-feature and
all of them resolve under the prototype, so the fixture exercises the path it claims to.

**What the `K`-independence does and does not establish — read this before quoting the result.** The
ring pins each site's receiver in **exactly one other file**, so per-site fan-out is one hop *by
construction*. Growing `K` therefore grows the project the resolutions happen in; it does not grow
the dependency depth any single resolution traverses. What is measured is that the per-site cost does
not rise as the surrounding project gets bigger — the superlinear blow-up Gap 2.3 left open — and
that is the property the guard's per-file scope put in doubt. A receiver whose class resolution
itself crosses several files is a **different** shape and this fixture says nothing about it; the
argument that it cannot diverge is still Gap 2.3's caching one, which is structural rather than
measured. Stated rather than papered over.

`implementation-plan.md` Phase 3 commits this measurement against the shipped code, as a test, in
place of the wall-clock note.

---

### DR-05 result — the consumer set, enumerated from inside the branch

**Why this is not a grep.** Drawing the consumer set by grepping the two resolution APIs and then
reading each hit's gate is unsound in the direction that matters: a gate that *admits* a colon
member name contains no mention of one, so there is nothing in the reading to see.
`LuaDeprecatedApiInspection` is the worked example — its `isDeclaration` gate lists
`LuaLocalFuncDecl` / `LuaAttName` / `LuaFuncName` / `LuaNameList`
([:62-72](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt)),
and a reader looking for a `LuaMethodExpr` row finds an absence, which is indistinguishable from a
gate that never needed one. Only running it separates the two.

**Instrument.** A throwaway `recordCaller()` was added inside
`LuaColonCallResolution.isColonCallMemberName`, called on every invocation whose element *is* a colon
member name — independently of whether the branch was enabled, so the same instrument reports the
pre-change and post-change consumer sets. It captured `Thread.currentThread().stackTrace`, filtered
to `net.internetisalie.lunar` frames excluding the recorder's own and `multiResolve` itself, and kept
the first three as the route. One instrument covers **both** APIs, because
`LuaNameReference.resolve()` is implemented as `multiResolve(false)` and a single-result unwrap
([LuaNameReference.kt:255-257](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaNameReference.kt)) —
which is also why a grep over the two resolution spellings cannot be complete: a `resolve()` caller
that does not spell `.reference?.resolve()` (`LuaRenameProcessor` and `LuaDocumentationTargetProvider`
both bind the reference to a local first) is invisible to both.

**Method.** Five fixtures — a plain colon call with two arguments, a `---@deprecated` colon method, a
colon call with a same-named local in scope, an annotated `---@class` / `---@type` receiver, and an
unresolvable member — each configured twice, with the branch off and on. Each was driven through
`doHighlighting` with every inspection `plugin.xml` registers, `findAllGutters`,
`availableIntentions`, `completeBasic`, `LuaParameterInfoHandler.findElementForParameterInfo`,
`LuaDocumentationTargetProvider.documentationTargets`, `LuaRenameProcessor.substituteElementToRename`
and `ReferencesSearch.search` on every colon-method declaration leaf. Executed on the prototype of
`design.md` §2.1-§2.4 applied to `95f60358`; every edit reverted afterwards and the working tree left
clean.

**The element-taking surfaces here are driven from one end only**, and that is this instrument's
limit rather than a property of the change: `ReferencesSearch` is driven on colon-method declaration
leaves, and `substituteElementToRename` on the call site. What the *same-named declaration* loses is
not observable from either. **DR-06** drives the other end and is where that half is recorded; the
findings below are the call site's.

**Result — the recorded routes.** Identical off and on, as expected: the instrument records the
*call*, not the answer.

| Route (recorded entry frame) | API | Observed on |
| :-- | :-- | :-- |
| `LuaDeprecatedApiInspection$buildVisitor$1.visitNameRef:37` | `multiResolve` | every fixture |
| `LuaUnusedLocalInspection.collectUsedDeclarations:147` ← `checkFile:82` | `multiResolve` | only the fixture with a local of the member's name |
| `LuaNameReferenceSearcher.processQuery:76` → `LuaNameReference.isReferenceTo:260` (its `resolve()` call at `:267`) → `resolve:255` | `resolve()` | every fixture containing a `function t:m()` |
| `LuaDocumentationTargetProvider.resolveDocumentationTarget:152` ← `documentationTargets:70` | `resolve()` | every fixture |
| `LuaRenameProcessor.resolvedDeclarationLeaf:378` ← `substituteElementToRename:107` | `resolve()` | every fixture |
| `LuaParameterInlayHintsProvider.isStdlibCall:191` ← `collectParameterHints:50` | `resolve()` | every fixture |
| `LuaParameterInlayHintsProvider.resolveMethodCall:127` ← `resolveFunctionType:107` | `resolve()` | the unresolvable-member fixture |
| `LuaParameterInfoHandler.resolveCandidates:68` ← `findElementForParameterInfo:40` | `resolve()` | the fixtures whose callee resolves |
| `LuaExpectedCallbackResolver.resolveMethodCalleeType:48` ← `resolveCalleeType:31` ← `LuaTypesVisitor.propagateExpectedLambdaParams:1106` | `resolve()` | every fixture |

**Finding 1 — a grep over `.reference?.resolve()` and `as? PsiPolyVariantReference` does not
reproduce this table.** `LuaRenameProcessor.resolvedDeclarationLeaf` and
`LuaDocumentationTargetProvider.resolveDocumentationTarget` both bind the reference to a local before
resolving, so neither spelling reaches them; and `LuaDeprecatedApiInspection` is returned by the
poly-variant grep but has no gate text a reader could use to classify it, per "Why this is not a
grep" above. The route table is the enumeration; the greps are not a check on it.

**Finding 2 — the `LuaNameReferenceSearcher` route is real and is the feature's own.** It arrives via
the platform's word-scan requestor calling `PsiReference.isReferenceTo`, so every frame between it
and `multiResolve` is inside `LuaNameReference`. `design.md` §3.7 already describes this path; DR-05
confirms it is exercised rather than argued.

**Finding 3 — the per-route effects, measured off against on.** Every figure below is transcribed
from the run; `design.md` §7 carries the same numbers in its consumer table and its
`LuaDeprecatedApiInspection` subsection.

| Route | off | on |
| :-- | :-- | :-- |
| `ReferencesSearch` on the declaration leaf of `function t:m(alpha, beta)` | `0 []` | `1 [45]` |
| `LuaDeprecatedApiInspection`, `t:m()` call site with a deprecated local `m` in scope | warning at `95..96` | **withdrawn** |
| `LuaDeprecatedApiInspection`, `function t:m()` carrying `---@deprecated` | no warning | no warning (`textEq=false`) |
| `LuaDeprecatedApiInspection`, `function m:m()` carrying `---@deprecated` | warning at `44..45` | `44..45` **and** `54..55` (`textEq=true`) |
| `LuaUnusedLocalInspection`, `local m = 1` used only by `t:m()` | no warning | `Unused local variable 'm'` at `38..39` |
| `LuaRenameProcessor.substituteElementToRename` on the same fixture's call-site leaf | `LeafPsiElement@38 'm'` — the **local** | `RefactoringErrorHintException: Cannot perform refactoring.` |
| `LuaDocumentationTargetProvider.documentationTargets` on a colon call site | `n=0 []` | `n=1 [LuaCatsDocumentationTarget]` |
| inline inlay offsets, `t:print(1, 2)` with `function t:print(alpha, beta)` | `[7]` | `[7, 55, 58]` |
| inline inlay offsets, the `t:emit(1, 2)` control | `[7, 53, 56]` | `[7, 53, 56]` |
| `LuaParameterInfoHandler` items on `t:m(1<caret>, 2)` | `LuaParameterInfoCandidate(name=t:m, params=[self, alpha, beta], types=[null, null, null], isMethod=true)` | identical |
| `LUA_INFERRED_METHOD` text attribute on the member name | `54..55 'm'` | `54..55 'm'` |

**Finding 3 is the call site's half.** Each row above reads a surface at the call site or at the
colon-method declaration leaf. The same fixtures read from the *other* end — the same-named
declaration — move in the opposite direction on every reference-backed surface, and DR-06 carries
those rows.

**Finding 4 — the routes that did not appear.** `LuaGlobalCreationInspection`,
`LuaUndeclaredVariableInspection` / `LuaUndeclaredNames`, `LuaCreateFunctionIntention`,
`LuaCreateLocalVariableIntention`, `LuaLineMarkerProvider` and `LuaInferredTypeAnnotator` were
recorded on **no** fixture, on any driven surface. That is a negative result over this instrument's
scope, not a proof of impossibility; `design.md` §7's second table gives the structural clause for
each, and the clause is what carries the claim. The instrument's value here is that a wrong clause
would have shown up as an unexpected route, and none did.

**Finding 5 — what makes this reproducible against the shipped code.** The instrument is three lines
in `isColonCallMemberName` and needs no other production change, so `requirements.md`'s acceptance
criterion — re-enumerate against the shipped code and confirm no route is missing from §7 — is a
cheap re-run rather than a re-derivation. Reading gates again does not discharge it.

### DR-06 result — the mirror direction, and the surface set derived from `plugin.xml`

**The question.** DR-05 answered *which consumers reach a colon member name* and *what the call site
gains*. It could not answer what the **same-named declaration** the call site used to bind to
**loses**, because every element-taking surface in it was driven from one end: `ReferencesSearch` on
colon-method declaration leaves, `substituteElementToRename` on the call site. DR-06 drives the other
end, and then asks whether the surface list itself was complete.

**Instrument.** The prototype of `design.md` §2.1-§2.4, with one added `probeEnabled` flag so a
single run drives the branch off and on, applied to `8566d566`. A `BasePlatformTestCase` probe
enumerates **every** `LuaNameRef` identifier leaf of a fixture — not the call site, not the
declaration, all of them — and at each leaf calls `ReferencesSearch.search(leaf, allScope)`,
`LuaSafeDeleteProcessor.findUsages(leaf, …)`, `LuaRenameProcessor.substituteElementToRename(leaf,
null)`, `LuaFindUsagesProvider.canFindUsagesFor(leaf)` and
`LuaDocumentationTargetProvider.documentationTargets(file, leaf.textOffset)` — the last reading the
target's *anchored element* by reflection, because two different declarations produce the same target
class. A second pass reconfigures the fixture once per leaf and runs the full
`myFixture.renameElementAtCaret("RENAMED")`, recording the resulting file text. The two passes are
diffed field by field, and only differing fields are reported.

**One `configureByText` name, reused for every fixture, so the fixture under measurement is the
project's only file.** Giving each fixture its own file name leaves every earlier one in the project
and `allScope` searches cross them — measured: a global function's usage set came back carrying
offsets belonging to unrelated fixtures. Every figure below is from a run in which each fixture is
the project's only file.

**Fixtures.** A colon call with a same-named local variable; the same with two arguments and no
shadow; an annotated `---@class` receiver with a same-named local; a same-named local *function*; a
same-named *global* function; an **unresolvable** member with a same-named local; a receiver whose
name equals its member's; and a same-named *parameter*.

**Finding 1 — the withdrawal is symmetric, and it reaches every declaration kind.** Each row is
`ReferencesSearch` on the leaf, off → on. `LuaSafeDeleteProcessor.findUsages` returned the same
offsets as `ReferencesSearch` on every leaf of every fixture in both runs, so it is not a separate
column.

| Fixture | Same-named declaration leaf | Its usages, off → on | The method leaf's, off → on |
| :-- | :-- | :-- | :-- |
| `local t = {}` / `function t:m() end` / `local m = 1` / `t:m()` | `m@38` `LOCAL_VARIABLE` | `[46]` → `[]` | `m@24`: `[]` → `[46]` |
| `local function m() end` / `local t = {}` / `function t:m() end` / `t:m()` / `m()` | `m@15` `LOCAL_FUNCTION` | `[47, 57, 61]` → `[47, 61]` | `m@47`: `[]` → `[57]` |
| `function m() end` / `local t = {}` / `function t:m() end` / `t:m()` | `m@9` `GLOBAL_FUNCTION` | `[41, 51]` → `[41]` | `m@41`: `[]` → `[51]` |
| `local t = {}` / `function t:m() end` / `local function f(m)` / `t:m()` / `return m` / `end` | `m@49` `PARAMETER` | `[56, 69]` → `[69]` | `m@24`: `[]` → `[56]` |
| `---@class Builder` / `local Builder = {}` / `function Builder:setName(n) end` / `local setName = 7` / `---@type Builder` / `local b` / `b:setName("x")` | `setName@75` `LOCAL_VARIABLE` | `[114]` → `[]` | `setName@54`: `[]` → `[114]` |
| `local m = {}` / `function m:m() end` / `m:m()` | `m@6` `LOCAL_VARIABLE` — the receiver | `[32, 34]` → `[32]` | `m@24`: `[]` → `[34]` |
| `local t = {}` / `local zz = 1` / `t:zz()` | `zz@19` `LOCAL_VARIABLE` | `[28]` → `[]` | none — the member is unresolvable |

**Finding 2 — the unresolvable-member row is not a transfer.** Where the member resolves to nothing, the usage
leaves the declaration's set and joins no other, and `resolve()` on `t:zz()`'s `zz@28` moves from
`LeafPsiElement@19 'zz'` to null. This is the shape in which the change is a pure loss of a binding
Lua never made, and it is what makes `LuaUnusedLocalInspection`'s new report correct rather than a
side effect to tolerate.

**Finding 3 — rename from the declaration side stops corrupting the call, and today's behaviour is a
[[BUG-457]]-class defect.** Full `renameElementAtCaret("RENAMED")` at the same-named declaration's
caret, off → on:

| Fixture | Caret | Off | On |
| :-- | :-- | :-- | :-- |
| the `local m = 1` fixture | `38` | `local RENAMED = 1` / **`t:RENAMED()`** | `local RENAMED = 1` / **`t:m()`** |
| the `local function m()` fixture | `15` | `t:RENAMED()` | `t:m()` |
| the `function m()` global fixture | `9` | `t:RENAMED()` | `t:m()` |
| the parameter fixture | `49` | `t:RENAMED()` | `t:m()` |
| the annotated fixture | `75` | `b:RENAMED("x")` | `b:setName("x")` |
| `local m = {}` / `function m:m() end` / `m:m()` | `6` | `local RENAMED = {}` / `function m:m() end` / **`RENAMED:RENAMED()`** | `local RENAMED = {}` / `function m:m() end` / **`RENAMED:m()`** |
| the unresolvable fixture | `19` | `t:RENAMED()` | `t:zz()` |

The receiver row is the sharpest: renaming the receiver today rewrites the *member name of the call*
while leaving the declaration `function m:m()` alone — the declaration and the call disagree
afterwards, which is BUG-457's shape in a case BUG-457 did not cover.

**Finding 4 — rename from the call site is refused on every shape, not just the one DR-05 drove.**
`substituteElementToRename` at the call site's member leaf moves from returning the same-named
declaration's leaf to throwing `RefactoringErrorHintException` on every fixture above, and
`renameElementAtCaret` likewise. DR-05 measured this on one fixture; it holds for the local-function,
global-function, parameter, annotated, receiver-same-name and unresolvable shapes as well.

**Finding 5 — Quick Documentation retargets as well as gains.** Reading the target's anchored
element, off → on: `[]` → `LuaCatsDocumentationTarget@13 'function t:m() end'` on the plain and
local-variable fixtures — the gain DR-05 recorded — but
`@0 'function m() end'` → `@30 'function t:m() end'` on the global fixture,
`@0 'local function m()'` → `@36 'function t:m() end'` on the local-function fixture, and
`@32 'local function f(m'` → `@13 'function t:m() end'` on the parameter fixture. Same target class,
different declaration documented. Recording only the class is what hid this in DR-05.

**Finding 6 — `LuaUnusedLocalInspection` reaches every kind its `classify` records, not only local
variables.** Enabling the inspection and reading `doHighlighting()` descriptions, off → on:

| Fixture | Off | On |
| :-- | :-- | :-- |
| `local t = {}` / `function t:m() end` / `local m = 1` / `t:m()` | none | `Unused local variable 'm'@38` |
| `local t = {}` / `function t:m() end` / `for m in pairs(t) do t:m() end` | none | `Unused local variable 'm'@36` |
| `local t = {}` / `function t:m() end` / `local function f(m)` / `t:m()` / `end` / `f(1)` | none | `Unused parameter 'm'@49` |

The parameter row needs `checkParameters = true`; it defaults to `false` and has no settings UI
([LuaUnusedLocalInspection.kt:36](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnusedLocalInspection.kt),
`:48` records the missing toggle as a TODO), so with stock settings only the local-variable and
generic-`for` rows are reachable.

**Finding 7 — `LuaFindUsagesProvider.canFindUsagesFor` is unchanged at every leaf.** It reads
`LuaDeclarationSite.kindOf`, which this feature does not touch. Find Usages stays *available* on
exactly the elements it was available on; only its result changes.

**Finding 8 — the surface list was the hole, and it is now derived rather than recalled.** DR-05's
instrument is systematic over consumers *given* a driving surface, but its surface list was written
by hand. Enumerating instead from the registry — every
`implementation=` / `implementationClass=` / `<className>` naming a `net.internetisalie.lunar` class
in `src/main/resources/META-INF/plugin.xml` whose implementation source mentions `.resolve()`,
`multiResolve` or `ReferencesSearch` — yields `LuaSafeDeleteProcessor`, which DR-05 drove in
**neither** direction; `LuaCatsTypeReferenceSearcher`, `LuaCatsTypeRenameProcessor` and
`LuaLabelRenameProcessor`, whose exclusion was never stated; and `LuaFindUsagesProvider`, driven but
unrecorded. `design.md` §7 carries the full pass with each one's status. **No `<intentionAction>`
class registered for Lua names any of the call half's three spellings**, which is consistent with the
structural clauses §7 already gives `LuaCreateFunctionIntention` and
`LuaCreateLocalVariableIntention`.

**That rule selects consumers that *call* a resolve API, and a consumer that is *handed* the
resolved element matches none of its three spellings.** DR-07 widens it and drives what it adds;
`design.md` §7 states the widened rule, and this finding's derivation is its call half alone.

**Finding 9 — what remains unbounded, stated rather than closed.** The surface half of the
enumeration is derivable, and DR-07 completes the derivation with its receive half. The **fixture**
half is not: an effect that needs a receiver or
declaration shape no fixture spells is invisible to every instrument this feature has, and the
corpus ratchet cannot
compensate because it carries 0 `---@` tags across 734 files and so cannot observe any
annotation-dependent behaviour at all. Closing it needs an annotated corpus lane — the
`lua-language-server` substitute of DR-01 promoted from a one-off measurement to a ratchet — which is
test-infrastructure work with its own vendoring, licence and baseline decisions. It is filed under
"Technical Debt" below rather than added to NAV-13's scope. **Until it exists the pinned set is the
set somebody drove, and both artifacts say so.**

**Reproduction.** The probe and the prototype are throwaway: the prototype is `design.md` §2.1-§2.4
plus a `probeEnabled` flag, and the probe is one `BasePlatformTestCase`. Every edit was reverted with
`git show HEAD:<path> > <path>` and the untracked files deleted from **both** the working tree and
the builder's copy, which was then re-synced; `git status` is clean and neither tree contains
`LuaColonCallResolution` or the probe.

### DR-07 result — the consumers that receive the element instead of resolving it

**The question.** DR-06's derivation keeps a registered class whose source names `.resolve()`,
`multiResolve` or `ReferencesSearch`. All three are things a consumer *does*. A consumer that is
**handed** the resolved element by a platform data rule matches none of them — and
`CommonDataKeys.PSI_ELEMENT`'s editor data rule follows the caret's reference, so such a consumer
sits directly downstream of this feature while naming nothing the rule looks for.

**The widened rule.** Attributes `implementation=` / `implementationClass=` / `class=` /
`factoryClass=` / `instance=` / `serviceImplementation=` and every `<className>` element in
`src/main/resources/META-INF/plugin.xml` naming a `net.internetisalie.lunar.*` class; each resolved
to its declaring `.kt` file by path, falling back to a search for `class|object|interface <Simple>`
where the file carries a different name (Lunar allows multi-declaration files, and at `7a1dc387`
seventeen registered classes resolve only that way); then kept if the file names **either** a call spelling (`.resolve()`, `multiResolve`,
`ReferencesSearch`) **or** a receive spelling (`CommonDataKeys.PSI_ELEMENT`, `TargetElementUtil`,
`PsiElementRenameHandler.getElement`).

**Executed at `7a1dc387`**: 186 registered classes, 0 unresolved; 16 match the call half — exactly
the set `design.md` §7 already tabulated, no residue in either direction — 3 match the receive half,
0 match both. The attribute forms DR-06's rule omitted — `class=`, `factoryClass=`, `instance=`,
`serviceImplementation=` and the rest — widen the registered set from 154 classes to 186 at
`7a1dc387`, and **not one of the additions matches either half**. That omission was therefore
harmless; the receive half is the one that was not.

| Receive-half consumer | Registration | Spellings it names | Outcome |
| :-- | :-- | :-- | :-- |
| `LuaInplaceRenameHandler` | `<renameHandler>` ([plugin.xml:403-404](../../../../src/main/resources/META-INF/plugin.xml)) | `CommonDataKeys.PSI_ELEMENT`, `TargetElementUtil`, `PsiElementRenameHandler.getElement` | **user-visible flip** |
| `LuaTypeHierarchyProvider` | `<typeHierarchyProvider>` ([plugin.xml:757-759](../../../../src/main/resources/META-INF/plugin.xml)) | `CommonDataKeys.PSI_ELEMENT` | **user-visible flip** |
| `LuaTargetElementEvaluator` | `<targetElementEvaluator>` ([plugin.xml:389-391](../../../../src/main/resources/META-INF/plugin.xml)) | `CommonDataKeys.PSI_ELEMENT`, `TargetElementUtil` | unchanged — `UNSURE` both sides |

**Instrument.** The `design.md` §2.1-§2.4 prototype with a kill switch on
`LuaColonCallResolution.isColonCallMemberName`, so one run drives both sides and "off" is the
pre-feature build exactly. A `BasePlatformTestCase` probe builds its data context with
`DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)` and injects **nothing**
into it, so `PsiElementRenameHandler.getElement` returns what `TargetElementUtil` computes rather
than what the probe assumed. Each fixture is configured fresh per side.

**Finding 1 — `CommonDataKeys.PSI_ELEMENT` tracks the reference, executed.** At the colon call site
of HV-8's fixture (offset 95), `reference.resolve()`, `CommonDataKeys.PSI_ELEMENT` and
`TargetElementUtil.findTargetElement` all returned `LeafPsiElement@53 'm'` off and
`LeafPsiElement@85 'm'` on. This is the mechanism the receive half exists for, measured rather than
read off `TextEditorPsiDataRule`.

**Finding 2 — `LuaInplaceRenameHandler` flips, and the pre-state is an inline template, not a
dialog.** `LuaDeclarationSite.kindOf` of the element the context supplies moves from `LOCAL_FUNCTION`
(`isFileLocal=true`) to `METHOD_FUNCTION` (`isFileLocal=false`), so `declaringNameRefOf`'s
`isFileLocal != true` gate turns `isAvailableOnDataContext` from `true` to `false` and
`RenameHandlerRegistry.getRenameHandlers` from `[LuaInplaceRenameHandler]` to
`[PsiElementRenameHandler]`. Driven under `TemplateManagerImpl.setTemplateTesting`: off, a template
starts on `range=(95,96) text='m'` and committing `RENAMED` into it rewrites **four** occurrences —

```lua
---@deprecated Use the method instead
local function RENAMED() end
local t = {}
function t:RENAMED() end
t:RENAMED()
RENAMED()
```

— on, the same keystroke throws `RefactoringErrorHintException: Cannot perform refactoring.` and the
file is unchanged. `LuaRenameProcessor.substituteElementToRename` on the element the context supplies
returns `LeafPsiElement@53 'm'` off and throws on; **off, the IDE never asks it**, because the
registry chose the in-place handler.

**Finding 3 — the `function t:RENAMED()` line is not NAV-13's.** That leaf is a `LuaNameRef` under a
`LuaFuncNameMethod`, which `design.md` §3.1's gate does not take, so its lexical resolution to the
same-named local is identical on both sides. It is consistent with DR-06's mirror table, whose
`LOCAL_FUNCTION` row keeps offset `47` — the method declaration's own name — in the local's usage
set off **and** on. NAV-13 withdraws the call site, not the declaration's name.

**Finding 4 — `LuaTypeHierarchyProvider` declines where it used to open.** Its `elementAtCaret` reads
`CommonDataKeys.PSI_ELEMENT` first and only then falls back to `findElementAt`
([:37-42](../../../../src/main/kotlin/net/internetisalie/lunar/lang/hierarchy/LuaTypeHierarchyProvider.kt)).
On `---@class m` / `local m = {}` / `local t = {}` / `function t:m() end` / `t:m()` with the caret at
offset 59: off, `PSI_ELEMENT` is `LeafPsiElement@18 'm'` and `getTarget` returns
`LuaLocalVarDeclImpl@12 'local m = {}'`; on, `PSI_ELEMENT` is `LeafPsiElement@49 'm'` and `getTarget`
returns **null**. **No fixture this feature had spelled that shape** — a `---@class` local whose name
coincides with a colon member name — so one was written for it. That is Finding 9's open fixture
half costing something concrete.

**Finding 5 — `LuaTargetElementEvaluator` is genuinely unaffected, and driving it is what shows it.**
`TargetElementUtilBase` passes the caret **leaf** as `element`, and `kindOf` of a colon member name
leaf is null (`kindFromNameRefGrandParent` has no `LuaMethodExpr` row), so the `isFileLocal == true`
conjunct is false. Executed: `element=LeafPsiElement@95 'm' kind=null`,
`referenced=LeafPsiElement@53 'm'` off / `@85` on, `isAcceptableReferencedElement=UNSURE` **on both
sides**. It neither blocks nor redirects Findings 2 and 4.

**Finding 6 — the rule's residue is enumerable, and one member of it moves.** The rule greps the
declaring file of a *registered* class, so a helper reached from one is caught only through its own
registration. Enumerated mechanically — every `src/main/kotlin` file naming any of the six spellings
that is not the declaring file of a registered class — there are six at `7a1dc387`:
`LuaUndeclaredNames`, `LuaExpectedCallbackResolver`, `LuaNameReference`, `LuaCatsTypeReference`,
`LuaLabelReference` and `LuaRenameConflictDetector`. The first three are already accounted for in
`design.md` §7 and the next two are structurally excluded. The last moves: its C1 `captures` clause
scans the **usage list**, so a site that stops being a usage stops being a capture candidate.
Executed through `myFixture.renameElementAtCaret("n")` on `local t = {}` / `function t:m() end` /
`local m = 1` / `print(m)` / `do` / `local n = 2` / `t:m()` / `end`:

| | off | on |
| :-- | :-- | :-- |
| outcome | `ConflictsInTestsException`: *"Renaming to 'n' would bind a usage of 'm' to a different declaration that is already visible here."* | applies, giving `local n = 1` / `print(n)` with `t:m()` and the inner `local n = 2` untouched |

`print(m)` sits outside the `do` block deliberately: `distinctByAnchor` collapses collisions by
capturing declaration, so a second usage that also saw `n` would report one conflict on both sides
and the difference would not be observable from that fixture at all.

**Finding 7 — what the widened rule still cannot see, concretely.** These classes of consumer, none
of which a derivation over Lunar source can reach:

1. **Surfaces with no Lunar class.** Go to Declaration, <kbd>Ctrl</kbd>+click, the Find Usages tool
   window, Search Everywhere's preview and the navigation bar are platform classes acting on
   `LuaNameReference` (`GotoDeclarationAction`: 0 hits across `src/main` and `src/test`). They
   inherit `design.md` §7's first route row by argument, and `human-verification-checklists.md`
   HV-8/HV-9 are what observe them.
2. **A registered consumer receiving the element by a data key the rule does not name.**
   `LangDataKeys.PSI_ELEMENT_ARRAY`, `PlatformCoreDataKeys.SELECTED_ITEMS`,
   `UsageView.USAGE_TARGETS_KEY` and `CommonDataKeys.SYMBOLS` each carry resolved elements. Checked
   at `7a1dc387`: **0 hits each** across `src/main/kotlin` and `src/test/kotlin`, so the omission
   costs nothing today and would cost silently the moment a consumer used one.
3. **Consumers outside `net.internetisalie.lunar`** — a dependency plugin registering over Lua PSI is
   outside the rule's scope entirely.

   Of these, Go to Declaration, `Ctrl`+click and the Find Usages window are driven live by HV-1
   through HV-6 and HV-9 step 2; **Search Everywhere's preview and the navigation bar are driven by
   no checklist step** and rest on the argument alone. Both are read-only render surfaces.

**Scoping.** Findings 2, 4 and 6 are user-visible and every one is correct — a colon member name is a
table key, so none of the three withdrawn behaviours was ever about the symbol the user's caret was
on. `requirements.md` scopes them under `NAV-13-08` and pins them as cases 31, 32 and 33;
`human-verification-checklists.md` HV-8 step 3 records the corrected pre-state, HV-8 step 5 the Type
Hierarchy check and HV-9 step 6 the conflict withdrawal.

**Reproduction.** The prototype is `design.md` §2.1-§2.4 plus the kill switch, and the probe is one
`BasePlatformTestCase`; both are throwaway. Every tracked edit was reverted with
`git show HEAD:<path> > <path>` and every untracked file deleted from **both** the working tree and
the builder's copy; `git status --porcelain` is clean and neither tree contains
`LuaColonCallResolution` or the probe.


## Critical Risks

### Risk 1.1 — Reach is small on un-annotated code, and the feature could be judged not worth its surface [Medium]

- **Impact**: 84 of 941 declarations on the pinned corpus, 73 of them in one project. A reader who
  sizes the feature by that number alone will conclude it is not worth a branch in the single hottest
  resolution path in the plugin.
- **Why it is not High**: the number that matters for [[REFACT-09]] is not 84 but *non-zero* — that
  feature is blocked on the existence of a usage set, not on its size, and DR-01 table 3 shows the
  set is now correct where it exists. And the un-annotated corpus is the *worst* case: the annotated
  substitute reaches 45.1%, and the annotated shape is the one the IDE's own users are most likely to
  be in, since annotating is what one does when one has a language server.
- **Mitigation**: the two reach numbers are stated together and neither is presented without the
  other. `requirements.md` scopes the feature to the shapes measured to reach a declaration and
  refuses the rest explicitly, so the surface is one branch and one stateless object rather than a
  new reference type, a contributor and a searcher (`design.md` §9 A, B).
- **Residual**: whether the annotated fraction of real Lua is large is **not measured and is not
  measurable from anything on hand** — see DR-01's closing paragraph.

### Risk 1.2 — The type engine resolves colon member names mid-build, so this feature can feed itself [High, mitigated and measured]

- **Impact**: a resolution path that consults the type engine, reached *from* the type engine. The
  measured cost is `WRITE` root resolutions on the BUG-473 fixture rising from 572 to 812, taking the
  budget methods that predate this feature red;
  the un-measured hazard is that the engine's fixpoint does not model the dependency at all.
- **Mitigation**: `design.md` §3.6's `isSnapshotUnderConstruction` refusal, whose completeness is
  evidenced by the exact restoration of 572 / 647 / 325 (DR-02 Finding 4.1) and whose falsifier is
  the budget methods that predate this feature (`requirements.md` case 17).
- **Residual — the guard is per file, not per graph.** `LuaTypeManagerImpl.resolveType` reaches
  *another* file's snapshot for an annotated receiver, and building **that** file's snapshot while
  the first is under construction is not refused. It cannot recurse indefinitely — `forFile` is
  `CachedValuesManager`-backed per file and `LuaTypesVisitor`'s in-progress map is keyed by file, so
  a cycle A→B→A would be caught by A's own guard — but the cost of that fan-out is unmeasured,
  because every fixture in DR-02 that crosses a file has exactly two files in it. Gap 2.3 carries it.

### Risk 1.3 — Withdrawing today's lexical bindings changes what other consumers see [Low]

- **Impact**: the consumer set is `design.md` §7's, drawn by DR-05's instrument rather than by
  reading gates. Every route that sees a change, with its measured before/after, is in DR-05
  Finding 3; the ones that matter as risk are:
  - **`LuaDeprecatedApiInspection` moves in both directions.** A call-site warning that named a
    same-named deprecated *local* is withdrawn — correct, since `t:m()` does not call it — and a new
    one appears where the call now resolves to a `---@deprecated` method, but only where the
    receiver's name text equals the member's, because `getDeprecatedTag`'s `LuaFuncDecl` guard
    compares against `funcName.nameRef.identifier`, which for a colon declaration is the receiver.
    Both are scoped in under `NAV-13-08`; the guard's asymmetry is filed under Technical Debt as the
    inspection's to fix.
  - **`LuaRenameProcessor` stops retargeting.** Rename invoked on a colon call site today resolves
    through `resolvedDeclarationLeaf` ([:378](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt))
    to a same-named local and renames **that**; after the change the resolved leaf classifies
    `METHOD_FUNCTION` and the processor's existing refusal at
    [:111-112](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt)
    fires. The processor is unchanged; only what it is handed changes. Executed: `LeafPsiElement@38 'm'`
    before, `RefactoringErrorHintException` after.
  - **`LuaUnusedLocalInspection.collectUsedDeclarations`**
    ([:138-152](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnusedLocalInspection.kt))
    sees colon member names — `classify`'s final `else -> usages.add(nameRef)`
    ([:116](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaUnusedLocalInspection.kt))
    has no `LuaMethodExpr` branch above it — so a local kept alive only by a same-named colon member
    name is now reported unused. That is the 175 corpus / 17 substitute local-variable bindings of
    DR-01 table 3.
  - **`LuaParameterInlayHintsProvider.isStdlibCall`** classifies a call as stdlib by whether the
    member name resolves to a stdlib element, so parameter hints appear where a same-named global
    suppressed them. Executed on `t:print(1, 2)`: offsets `[7]` → `[7, 55, 58]`.
  - **`LuaDocumentationTargetProvider`** gains a target where it had none: `n=0` → `n=1`.
  - **`LuaExpectedCallbackResolver`** — the seeding withdrawal, scoped in under Gap 2.4 and DR-03
    rather than treated as a risk.
  - **`LuaInplaceRenameHandler`** and **`LuaTypeHierarchyProvider`** — the receive-the-element
    consumers DR-07 drove that move: <kbd>Shift+F6</kbd> at a colon call site stops starting an inline rename
    template on a same-named file-local declaration, and Type Hierarchy stops opening on a
    coincidentally-named `---@class` local.
  - **`LuaRenameConflictDetector`** — a C1 capture conflict raised by the colon call site is
    withdrawn with the usage (DR-07 Finding 6). `LuaTargetElementEvaluator` was driven and is
    unchanged.
  Recorded on no fixture and on no driven surface: `LuaUndeclaredVariableInspection` /
  `LuaUndeclaredNames`, `LuaGlobalCreationInspection`, `LuaCreateFunctionIntention`,
  `LuaCreateLocalVariableIntention`, `LuaLineMarkerProvider` and `LuaInferredTypeAnnotator`.
  `design.md` §7 gives the structural clause that keeps each out; DR-05 Finding 4 states the limits
  of the negative result.
- **Mitigation**: the full unit suite is green under the prototype (DR-02 Finding 5); the
  unused-local effect is gated empirically by the corpus ratchet's per-tool inspection counts; and
  the effects the ratchet **cannot** see — every `---@`-dependent one, on a corpus with 0 `---@`
  tags — are pinned by `requirements.md` cases 21-33 under `NAV-13-08` instead. That split is the
  mitigation: a change the ratchet is blind to does not get the ratchet as its gate.
- **Why Low rather than Medium**: the bindings being withdrawn are unsound by the language
  definition — `t:m()` is `t.m(t)` and `m` is a table key — so any consumer relying on them was
  relying on a coincidence of names. Every change above follows from that, and each is now observed
  rather than predicted.

## Design Gaps

### Gap 2.1 — The annotated fraction of real Lua is unmeasurable from anything on hand [accepted, stated]

The pinned corpus has 0 annotated files. `lua-language-server` at `66141703` has **304 of the 432**
`.lua` files outside its top-level `3rd/` tree, and **195 of the 275** under `meta/`, `script/` and
`tools/` — the subtree DR-01 stages as the substitute. Each figure covers the scope named beside
it; quoting either without its scope is what made them look like a single disputed number.
Neither is a sample.
So "how much does this feature help a typical project" has **no** answer here, and the artifacts give
two bracketing measurements instead of a single number. Recorded rather than resolved: acquiring a
representative sample of Lua-in-the-wild is a corpus-selection project, not this feature's.

### Gap 2.2 — A cross-file un-annotated receiver reaches nothing, and that is structural [accepted]

`Obj = {}` / `function Obj:m()` in one file and `Obj:m()` in another resolves to null: measured,
`references=0`. `LuaTypesSnapshot` is built per file and `declareFileGlobals`
([LuaTypesVisitor.kt:358](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt))
seeds only the file's own globals, so the other file's `Obj` has no members. This is the same limit
[[TYPE-13]] Gap 2.11 records for a `require`d module, in the global spelling.

- **Consequence for [[REFACT-09]]**: a usage set built from this feature is *file-local* for an
  un-annotated receiver and project-wide for an annotated one. A rename that treats the two the same
  would half-rename the global case, which is `REFACT-01-00-DR-03`'s failure. `requirements.md`
  Out of Scope states the split so REFACT-09 must decide it explicitly.
- **Action**: none here. Widening it means giving a cross-file global's type its members, an engine
  change with [[TYPE-13]] Risk 1.1's blast radius.

### Gap 2.3 — The in-progress guard is per file, and the cross-file fan-out is unmeasured [open, sized]

`design.md` §3.6's guard asks whether *this* file's snapshot is under construction. An annotated
receiver resolves through `LuaTypeManagerImpl.resolveType`, which searches
`GlobalSearchScope.allScope` and can build another file's snapshot. No fixture in DR-02 has more than
two files, so the cost of that fan-out on a project-sized graph is not measured.

- **Why it is not a blocker**: it cannot diverge (per-file `CachedValuesManager` caching plus the
  per-file in-progress map), and the budget instrument is per snapshot, so a fan-out would show up as
  cost in the *other* file's counters rather than as a wrong answer.
- **The fan-out is now bounded — DR-04.** A per-file budget cannot see it, so the measurement sums
  the per-snapshot counter over **every** file, on a K-file ring in which every colon call site's
  annotated receiver is declared in another file. Executed at K = 2, 4, 8 and 16: the branch costs
  **exactly one extra `WRITE` and one extra `READ` root resolution per resolved cross-file site**,
  with the ratio flat at 1.101 across all four K. Linear in call sites, constant in project size.
  That is a bound, and it is the bound the guard's per-file scope left open.
- **The wall-clock pair is retained as context and bounds nothing.** The `-PwithCorpus` lane took
  **20 min 5 s** under the guarded prototype (DR-02 Finding 5) against [[TYPE-13]] Phase 3's
  **24 min 49 s**. Two uncontrolled runs on a shared builder; the direction is harmless and the
  measurement is not evidence. DR-04 replaces it as the instrument.
- **Action**: `implementation-plan.md` Phase 3 commits DR-04's ring measurement against the shipped
  code, as a test rather than a recorded number, so a later regression reddens rather than needing to
  be noticed. If the per-site delta ever grows with K, the narrowing is to gate the annotated route on
  `isSnapshotUnderConstruction` for the *resolved* file too — a one-line change with its own
  measurement, not a redesign.

### Gap 2.4 — this feature makes `LuaExpectedCallbackResolver.resolveMethodCalleeType` dead, and that is an inference change [scoped in, pinned]

`resolveMethodCalleeType` resolves a colon call's member name and then requires
`resolved.parent?.parent as? LuaFuncDecl`
([LuaExpectedCallbackResolver.kt:47-51](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaExpectedCallbackResolver.kt)).
A method-name leaf's `parent.parent` is a `LuaFuncNameMethod`, so **after** this feature the cast can
never succeed for a colon call. **Before** it, the cast succeeds for a whole `LuaFuncDecl` node
returned by the stub-index phase whose declaration is nested inside another `LuaFuncDecl` — DR-03
executed it, and TYPE-10 seeds the lambda parameter from the *enclosing* function's `---@param`. So
the dead-code property is one this feature **creates**, not one it inherits, and creating it removes
a real seeding.

- **Disposition — scoped in as intended behaviour, not prevented.** The withdrawn seeding takes an
  annotation belonging to a function the call does not call, which is unsound rather than merely
  imprecise. Preserving it would mean keeping a wrong inference to protect a no-change requirement.
- **Its acceptance check**: `requirements.md` case 19, a unit fixture asserting the lambda parameter
  now infers `unknown` where it inferred `string`, with the dot-call spelling as the control. The
  corpus ratchet structurally cannot observe this class of change — 0 of the corpus's 734 files carry
  a `---@` tag — which is why the check is a fixture and not the ratchet.
- **What is NOT withdrawn**: seeding from a genuine `---@param cb fun(...)` on the method itself.
  That never fired for a colon call before this feature and still does not (DR-03, "It costs no sound
  seeding"), so the change removes only the accidental case.
- **Residual**: making the *sound* colon-call seeding fire is still open, and is TYPE-10's subject
  matter rather than this feature's. Recorded as future work below.

## Technical Debt & Future Work

- **TBD: a chain's second segment.** `x:m1():m2()` refuses because there is no PSI node for the value
  of `x:m1()` and `visitFuncCall` models `nameAndArgsList.firstOrNull()` only ([[TYPE-13]] Gap 2.12).
  Admitting it means minting a node per intermediate `nameAndArgs` step — TYPE-12's neighbourhood.
- **TBD: `self:m()` and factory-returned receivers.** [[TYPE-13]] Gap 2.7's empty-`upSet` shapes.
  These are the dominant un-annotated OO forms, so this is where the corpus reach in DR-01 table 1
  would actually move.
- **TBD: the dotted spelling.** `function t.m()` called as `t:m()` refuses (DR-02 Finding 3). The
  dotted declaration resolves through `getQualifiedName`/`LuaGlobalDeclarationIndex`, a different
  mechanism; unifying the two is [[REFACT-09]] risks' "dotted access to a colon-declared member".
- **TBD: TYPE-10's expected-callback seeding for colon calls** — Gap 2.4. After this feature the
  seeding cannot fire for a colon call at all: the branch returns a method-name leaf, whose
  `parent.parent` is a `LuaFuncNameMethod`, and mid-build the guard returns null. Making the *sound*
  case fire — `---@param cb fun(...)` on `function t:m(cb)` — means teaching
  `resolveMethodCalleeType` to accept a method-name leaf, which changes what the engine infers for
  every colon call taking a lambda and is TYPE-10's subject matter.

- **TBD: `refactoring.rename.colonMethod`'s wording states a reason this feature falsifies.** The
  bundle string is *"Renaming a `function Obj:method()` declaration is not supported yet: calls
  written `obj:method()` are not resolved, so they would be left bound to the old name"*
  ([LuaBundle.properties:153](../../../../src/main/resources/net/internetisalie/lunar/LuaBundle.properties)).
  After NAV-13 those calls **are** resolved, so the refusal stays correct while its stated reason
  becomes false — a user is told something untrue about the tool. NAV-13 does not reword it, for two
  reasons: it adds no user-visible string of its own (design §7), and [[REFACT-09]] is the feature
  that removes the refusal outright, at which point the string is deleted rather than reworded.
  If REFACT-09 slips behind a shipped NAV-13, the wording is a one-line fix and belongs to whoever
  ships that gap.

- **TBD (not this feature's): `LuaDeprecatedApiInspection`'s `LuaFuncDecl` guard is wrong for a colon
  declaration.** [:81-86](../../../../src/main/kotlin/net/internetisalie/lunar/analysis/inspections/LuaDeprecatedApiInspection.kt)
  compares the resolved leaf against `commentOwner.funcName.nameRef.identifier`, which for
  `function t:m()` is the **receiver** `t`, not the method `m`. So a `---@deprecated` colon method
  warns at its call sites only when the receiver happens to be named the same as the method —
  executed, `textEq=false` for `function t:m()` and `textEq=true` for `function m:m()` (DR-05).
  NAV-13 does not change the guard: doing so would raise warnings across a surface this feature has
  not scoped, and the guard is equally wrong today for the declaration position. `requirements.md`
  case 22 pins the current behaviour on **both** sides so that fixing the guard is a visible,
  deliberate diff rather than a silent widening.

- **TBD (test infrastructure, not this feature): an annotated corpus lane.** The pinned corpus carries
  0 `---@` tags across its 734 files, so the ratchet is structurally blind to every
  annotation-dependent effect — which is the class of effect this feature makes. `NAV-13-07`'s
  no-change half therefore rests on an instrument that cannot see the change, and the compensation is
  a fixture set: cases 19 and 21-33. DR-06 Finding 9 states what that leaves open — the pinned set is
  bounded by the shapes somebody thought to spell. Closing it means promoting DR-01's
  `lua-language-server` substitute (195 annotated files under `meta/`, `script/`, `tools/` at
  `66141703`) from a one-off measurement into a second ratchet lane, which needs a vendoring
  decision, a licence review and a baseline of its own. That is `MAINT` work with its own
  requirements, not a clause of NAV-13, and no NAV-13 acceptance criterion may be written as though
  it existed.

## Test Case Gaps

- **The in-file global-table row has no mutation that separates it from the plain-local one.**
  Executed above: every candidate reddens both or neither. The two shapes share the whole path a
  colon call site takes — `scope.lookup` → a shared `VariableNode` → the `funcNameMethod` mint — and
  the one handle that distinguishes them ([[TYPE-13]] Gap 2.9's `Obj@0` write target) is not a handle
  any colon call site uses. The row is kept as a *reach pin*: it says the global spelling is in scope
  and would show a diff if a later change dropped it. It is not independent acceptance, and an
  implementer must not "simplify" it away on the grounds that it duplicates the local row — the two
  fixtures differ in what they cover even though no mutation separates them.
- **No IDE-level verification is automated.** Go to Declaration and Find Usages are exercised through
  `resolve()` and `ReferencesSearch` in unit fixtures; that the gutter, the popup and the Find
  Usages tool window render is `human-verification-checklists.md`'s business.
- **The reach measurements are not a regression gate.** DR-01's tables were produced by a throwaway
  probe over out-of-repo trees; nothing re-runs them. `implementation-plan.md` Phase 4 re-runs the
  corpus half against the shipped code once, and records the number; a permanent gate would need the
  probe committed and the substitute tree pinned, which is a corpus-management change.
- **Cases 15 and 18 of `requirements.md` have no falsifying mutation**, each for a stated reason.
  They are pins, not acceptance.
- **Case 19's mutation is the branch itself, not a clause of it.** The seeding withdrawal follows
  from the branch returning null mid-build, so the mutation that reddens it — delete the colon branch
  — is case 1's. That is a real, reachable falsifier for case 19: with the branch deleted the lambda
  parameter infers `string` again (DR-03's pre-feature column). It is not an independent clause
  falsifier, and no clause of `declarationLeafOf` separates case 19 from case 1.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- [[TYPE-13]] risks and gaps — Gaps 2.7, 2.11 and 2.12 are the engine limits this feature refuses around.
- [[REFACT-09]] risks and gaps — Gap 2.3 is the measurement this feature exists to change, and Gap 2.4
  named this route as its natural predecessor.
