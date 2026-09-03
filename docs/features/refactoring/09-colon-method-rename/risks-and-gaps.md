---
id: "REFACT-09-RISKS"
title: "09: Risks & Gaps"
type: "risk"
parent_id: "REFACT-09"
folders:
  - "[[features/refactoring/09-colon-method-rename/requirements|requirements]]"
---

# REFACT-09: Risks and Gaps

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| `REFACT-09-00-DR-01` | Measure, against the shipped code, how many colon-method declarations can be renamed **completely** — on the pinned corpus and on the annotated substitute — and what blocks the rest. | the whole feature; Risk 1.1 | **done — result below** |
| `REFACT-09-00-DR-02` | Drive the *unchanged* rename machinery with the blanket refusal lifted: which hooks already work, which produce a half-rename, which produce a false conflict. | design §1, §2.2, §5 | **done — result below** |
| `REFACT-09-00-DR-03` | Transcribe the completeness predicate onto real PSI, validate it on fixtures in both directions, and time it. | the report-don't-refuse decision (design §9 Alternative B); design §3 | **done — result below** |
| `REFACT-09-00-DR-04` | Can a colon call resolve to a declaration this project must not rewrite? | `REFACT-09-05`; design §3.6 | **done — result below** |
| `REFACT-09-00-DR-05` | Find a handle that answers "does this receiver already have a member called *n*", and measure in which receiver shapes each candidate answers at all. | `REFACT-09-08`; design §3.7, §9 Alternative F | **done — result below** |
| `REFACT-09-00-DR-06` | Drive a second `function t:m()` on the same receiver and record which declaration its call sites bind to. | the `funcNameMethod` exclusion; design §3.3, §6 | **done — result below** |

DR-01 through DR-04 ran at `2a15cfcd`; DR-01's reach figures were **re-measured at `b232639c`**
together with DR-05 and DR-06, after `design.md` §3.3's occurrence set was closed over the grammar.
Every action above ran on the gce builder. Every probe was a throwaway test class, and DR-02's
one production edit — a system-property branch replacing `substituteElementToRename`'s
`METHOD_FUNCTION` refusal — was reverted with `git show HEAD:<path> > <path>` and verified
md5-identical to `HEAD`. `git status --porcelain` is empty in the working tree.

---

### DR-01 result — reach, measured before the design

**Why this comes first.** The artifacts this replan supersedes were planned to the Planning Bar
across two Step 9 rounds and then measured to accept **0 of 941** colon-method declarations on real
code. [[NAV-13]] avoided repeating that by measuring reach in its own DR-01 before writing a design;
this feature does the same.

**Method — stated so a third party gets the same figure.** Each choice fixes the number:

1. **Scope**: each pinned checkout's whole `.lua` tree — the same checkouts and the same
   whole-tree scope [[NAV-13]] DR-01 used, **not** the sweep's narrower `roots`.
2. **Fixture-project granularity**: one `BasePlatformTestCase` method per corpus, so each corpus is
   its own project. `LuaTypeManagerImpl.resolveType` searches `GlobalSearchScope.allScope`, so a
   combined project would let a global declared in one checkout answer a lookup made in another.
3. **The declaration denominator**: an IDENTIFIER leaf under a `LuaFuncNameMethod` whose
   `LuaDeclarationSite.kindOf` is `METHOD_FUNCTION` — exactly the rename's admissible input.
4. **The call-site denominator**: a `LuaMethodExpr` whose parent is a `LuaNameAndArgs` whose parent
   is a `LuaFuncCall`. Counting every `LuaMethodExpr` instead gives 14 191, because
   `varSuffix ::= nameAndArgs* indexExpr` puts further colon sites under a `LuaVarSuffix`.
5. **The usage set**: for the reach instrument, the forward map *site → `LuaColonCallResolution.declarationLeafOf(site)`*;
   for the predicate instrument (DR-03), `ReferencesSearch.search(declarationLeaf, allScope)`. The
   two are compared below and Gap 2.5 records where they disagree.
6. **The annotated substitute**: `lua-language-server` 3.10.6 at `66141703` — `git describe --tags`
   prints `3.10.6-6-g66141703` — staged as the files under `meta/`, `script/` and `tools/` carrying
   a `---@` tag (`grep -rl -- '---@' meta script tools`, 195 files) into the out-of-repo `test/`
   tree, measured, and deleted from both trees. This is the same substitute [[NAV-13]] DR-01 and
   `REFACT-08-00-DR-01` used, which makes those features' reach figures comparable with this one's.

**Reproducibility protocol.** The instrument measures the same copied tree **twice in one test
method** and asserts the two passes are equal field for field; a run whose passes disagree is
discarded rather than reported. The assertion passed on every corpus and on the substitute. Assert
the denominators before reading the numerator: they reproduce exactly, and a change in them means
the scope moved, not the algorithm.

**The denominators reproduce [[NAV-13]] DR-01 cell for cell**, which is what makes this instrument
trustworthy before its own numbers are read:

| corpus | files | colon-method decls | raw `LuaMethodExpr` | colon call sites | bare-name first-segment |
| :-- | --: | --: | --: | --: | --: |
| luacheck | 135 | 142 | 806 | 805 | 490 |
| luarocks | 160 | 80 | 1 952 | 1 952 | 1 389 |
| penlight | 114 | 147 | 984 | 984 | 777 |
| zerobrane | 325 | 572 | 10 449 | 10 375 | 8 755 |
| **corpus total** | **734** | **941** | **14 191** | **14 116** | **11 411** |
| lua-language-server 3.10.6 | 195 | 268 | 2 453 | 2 446 | 1 312 |

#### Finding 1 — [[NAV-13]]'s "67 of 941" counts declarations outside the corpus, and 51 is the figure this feature inherits

The resolving call sites reproduce [[NAV-13]]'s shipped measurement exactly — 315 of 14 116 on the
corpus, 443 of 2 446 on the substitute — and so does the count of *distinct declarations reached*,
67 and 121. But 16 of the corpus's 67 are **not corpus declarations at all**: they are inside the
plugin's own bundled stdlib, and the probe printed their paths rather than inferring them:

```
R09[zerobrane] outOfTreeSample=[…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua:1747,
  …:2209, …:1537, …:1420, …:1818]
R09[luacheck]  a.declsWithAUsageOutOfTree=3
R09[luarocks]  a.declsWithAUsageOutOfTree=4
R09[penlight]  a.declsWithAUsageOutOfTree=4
R09[zerobrane] a.declsWithAUsageOutOfTree=5
R09[lls]       a.declsWithAUsageOutOfTree=0
```

`runtime/standard/<level>/io.lua` declares `File:read`, `File:write`, `File:lines`, `File:flush`,
`File:seek`, `File:close` and `File:iseof` — every colon-method **name** in the bundled stdlib — and a
corpus call such as `f:close()` binds to one of them. They exist once per bundled level:
`git grep -l 'function File:' src/main/resources` returns `runtime/standard/lua-5.1` … `lua-5.4`, so
the same seven names are declared four times over. There is **no** `runtime/standard/lua-5.5`
directory, so no 5.5 stub contributes a colon method.

So **51 of the corpus's own 941 colon-method declarations (5.4%) gain a resolving call site**, not
67. On the substitute all 121 are in-tree. [[NAV-13]]'s figure is not wrong about what it measured —
distinct declarations reached — but it is not a subset of its own denominator, and this feature's
reach must be read against 51. DR-04 turns the same observation into `REFACT-09-05`.

#### Finding 2 — the completeness predicate accepts a usable fraction, and the denominators say which fraction

**Re-measured at `b232639c`** over the occurrence set `design.md` §3.3 now closes over the grammar.
The instrument computes, for every declaration, the verdict under the **old** occurrence set (colon /
dotted / bracket) and under the **new** one (those plus the table-constructor key) in a single pass,
so the two columns differ by the clause and by nothing else.

**The control that makes the new numbers readable: the old set reproduces the superseded table.**
Every denominator reproduces cell for cell (files, colon-method declarations, raw `LuaMethodExpr`,
colon call sites — the table above), and so do the two verdict columns that do not depend on
classification order:

| tree | `accepted` (old set) | previously | `acceptedNoCallSites` (old set) | previously | declarations with a bound call site | previously |
| :-- | --: | --: | --: | --: | --: | --: |
| luacheck | 0 | 0 | 29 | 29 | 0 | — |
| luarocks | 0 | 0 | 5 | 5 | 0 | — |
| penlight | 0 | 0 | 16 | 16 | 0 | — |
| zerobrane | 20 | 20 | 78 | 78 | 51 | 51 |
| **corpus total** | **20** | **20** | **128** | **128** | **51** | **51** |
| lua-language-server | 50 | 50 | 48 | 48 | 121 | 121 |

`h.blocked.byDottedOnly` reproduces too: 2 of ZeroBrane's 51 and 10 of the substitute's 121 under
the old set. Three runs of the instrument produced identical histograms.

**The dotted / undecidedColonCall split of the superseded table is not reproducible, and is
replaced.** It classified a blocked declaration by the spelling of its *first* undecided occurrence,
which depends on the order `CacheManager.getFilesWithWord` returns candidate files: the instrument's
first-occurrence tally lands within a few declarations of the published one on every tree (luacheck
18/95 identical; zerobrane 58/416 against 67/407) without converging on it. The table below therefore
classifies by the **set** of clauses that block a declaration, which is order-independent, and quotes
the *sole* blocker where one clause alone is responsible.

**Verdicts under the new occurrence set** (`projectScope`, which is the scope §3.3 scans; the
`allScope` variant produced the same histogram on every tree):

| tree | decls | `accepted` | `acceptedNoCallSites` | blocked | sole blocker: colon | sole: dotted | sole: bracket | sole: constructor key |
| :-- | --: | --: | --: | --: | --: | --: | --: | --: |
| luacheck | 142 | 0 | 27 | 115 | 87 | 16 | 0 | 2 |
| luarocks | 80 | 0 | 5 | 75 | 61 | 0 | 0 | 0 |
| penlight | 147 | 0 | 13 | 134 | 37 | 1 | 0 | 3 |
| zerobrane | 572 | 20 | 75 | 477 | 341 | 16 | 0 | 3 |
| **corpus total** | **941** | **20** | **120** | **801** | **526** | **33** | **0** | **8** |
| lua-language-server | 268 | 50 | 44 | 174 | 78 | 32 | 0 | 4 |

Against the superseded figures, read three ways:

- **Of the declarations the IDE can resolve a call site for**, the predicate accepts **20 of 51** on
  the corpus and **50 of 121** on the substitute — **unchanged**. Every declaration the constructor
  key blocks is in the `acceptedNoCallSites` bucket, because a table whose constructor names the
  member is a table nothing in the project calls the member on.
- **Of all colon-method declarations**, it accepts **140 of 941** (14.9%) and **94 of 268** (35.1%),
  against 148 and 98 under the old set. The closure costs **8 corpus declarations and 4 substitute
  declarations**, each of which the constructor key is the *sole* blocker for.
- **What blocks the rest** is still dominated by a same-named colon call the engine cannot bind: it
  is the sole blocker for 526 of 941 and 78 of 268. The constructor key participates in blocking
  **171 of 941** corpus and **36 of 268** substitute declarations; it is the *only* blocker for 8
  and 4 of them.

**That is not the 0 of 941 the superseded predicate measured, and it is the number the design is
built on.** It is also concentrated: every corpus declaration in the `accepted` bucket is ZeroBrane's,
whose OO is a file-local `local Pack = {}` plus `function Pack:method()` plus in-file `p:method()`
calls — the one shape [[TYPE-13]] reaches. Every other pinned checkout is module-style
(`local M = {}` … `return M`), so their call sites live in another file and resolve to nothing
([[NAV-13]] Gap 2.2).

#### Finding 3 — the bracket clause costs nothing measurable, the constructor-key clause does, and the dynamic index cannot be a clause at all

Each clause that was a candidate for "kept for soundness, free in practice" is listed below with
its measured cost, and not all of them are free:

- **`bracketSpelling` is free.** Over both trees it is the sole blocker for **0** declarations: every
  declaration it blocks is blocked by an earlier clause (`B` never appears alone in any tree's
  blocker histogram; the closest is ZeroBrane's single `DBF`). Kept for soundness at zero measured
  cost.
- **A `---@field` clause would also have been free** (`h.blocked.byCatsField` = 1 on the substitute,
  inside the blocked set) — and it is then dropped for a different reason (Gap 2.3).
- **`FIELD_KEY` is not free, and that is why it is not optional.** It is the sole blocker for 8 of
  941 corpus and 4 of 268 substitute declarations (Finding 2). A clause that changes verdicts is a
  clause that was deciding something the design previously got wrong: without it, `local t = { m = 1 }`
  beside `function t:m()` renames with no report at all (`requirements.md` row 23).

A **dynamic** index step is the opposite case. Steps whose index is not a plain literal:

| tree | non-literal bracket index steps |
| :-- | --: |
| luacheck | 771 |
| luarocks | 814 |
| penlight | 751 |
| zerobrane | 3 088 |
| **corpus total** | **5 424** |
| lua-language-server | 3 409 |

Treating any of them as an occurrence of the renamed member refuses every rename in the corpus.
Gap 2.2 records the residual.

#### Finding 4 — clause (a) of design §3.4 never fires on real code

For every declaration on every tree, no colon occurrence was simultaneously in the usage set and
unresolvable by `LuaColonCallResolution.declarationLeafOf`:

```
R09R[luacheck]  clauseAliveDecls project=0 all=0
R09R[luarocks]  clauseAliveDecls project=0 all=0
R09R[penlight]  clauseAliveDecls project=0 all=0
R09R[zerobrane] clauseAliveDecls project=0 all=0
R09R[lls]       clauseAliveDecls project=0 all=0
```

over 14 116 corpus and 2 446 substitute colon call sites. The clause is kept — Gap 2.5 records one
declaration on which the two instruments disagree, and clause (a) is what keeps that disagreement
costing a missing conflict rather than a false one — and `requirements.md` row 28 falsifies it with a
synthetic usage set rather than with a Lua fixture, because no Lua fixture on these trees can.

**Method for the re-measurement.** The instrument is `design.md` §3.3-§3.4 transcribed onto real PSI,
with the usage set from `ReferencesSearch.search(declarationLeaf, scope)` for `scope` in
{`projectScope`, `allScope`} and the occurrence scan over `projectScope`, exactly as §3.3 specifies.
Scope, granularity, denominators and the annotated substitute are staged as points 1-6 above; the
substitute is the same 195 files, and `git describe --tags` still prints `3.10.6-6-g66141703`.

---

---

### DR-02 result — what the unchanged machinery does once the refusal is lifted

A single throwaway branch in `substituteElementToRename` returned the leaf instead of refusing, under
a system property, so every other hook ran exactly as shipped. Fixtures driven through
`myFixture.renameElementAtCaret`; outcome and resulting file text transcribed.

**Finding 1 — `findReferences` already returns the usage set, with no change.**

```
R09PROBE[F01] kind=METHOD_FUNCTION refs=[34, 40]
```

on `local t = {}` / `function t:m() end` / `t:m()` / `t:m()`. `METHOD_FUNCTION.isFileLocal` is
`false`, so the method takes `ReferencesSearch.search(declarationLeaf, searchScope)` unchanged.
**This is the finding that removes the superseded design's `callSiteReferences`,
`declarationLeafOfCallSite` and `selfRouteDeclaration` entirely.**

**Finding 2 — the shapes that rename correctly.**

| fixture | outcome |
| :-- | :-- |
| plain local table, declaration caret | `R09PROBE[R01] RENAMED \| local t = {} / function t:n() end / t:n() / t:n()` |
| plain local table, **call-site** caret | `R09PROBE[R02] RENAMED \| local t = {} / function t:n() end / t:n() / t:n()` |
| two receivers sharing a method name | `R09PROBE[R06] RENAMED \| … function t:n() end / t:n() / local q = {} / function q:m() end / q:m()` |
| `---@class`-annotated receiver, aliased `local b = Builder` | `R09PROBE[R07] RENAMED \| … function Builder:withName(x) end / local b = Builder / b:withName("x")` |
| declaration with no call sites | `R09PROBE[R14] RENAMED \| local t = {} / function t:n() end` |

**Finding 3 — the shapes that half-rename, which is what the completeness scan exists to catch.**

| fixture | outcome |
| :-- | :-- |
| `function C:a() self:m() end` beside `function C:m()` | `R09PROBE[R05] RENAMED \| local C = {} / function C:n() end / function C:a() self:m() end / C:n()` |
| `print(t.m)` beside `function t:m()` | `R09PROBE[R08] RENAMED \| … function t:n() end / t:n() / print(t.m)` |
| global receiver called from `b.lua` | `R09PROBE[R10] RENAMED`, and `R09PROBE[R10] b.lua=Obj:m() /` |
| parameter receiver `x:m()` in `b.lua` | `R09PROBE[R09] RENAMED`, and `R09PROBE[R09] b.lua=local function f(x) x:m() end / f(nil) /` |

**Finding 4 — the caret-on-`self` hazard is live and the guard is required.**

```
R09PROBE[R04] RENAMED | local C = {} / function C:m() end / function C:n() self:m() end / C:m()
```

The caret was on `se<caret>lf`; the *enclosing* method `a` was renamed, because `LuaScopeProcessor`
resolves `self` to `funcName.funcNameMethod.nameRef.identifier`
([LuaScopeProcessor.kt:87-92](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)).
The superseded design's `caretRefusal` is kept verbatim (design §3.6).

**Finding 5 — a caret on the `m` of `self:m()` still refuses, and that is unchanged behaviour.**

```
R09PROBE[R03] THREW RefactoringErrorHintException: Cannot perform refactoring.
```

`self` reaches no declaration ([[NAV-13]] Out of Scope), so `resolvedDeclarationLeaf` refuses with
`refactoring.rename.unresolved` before the `METHOD_FUNCTION` branch is reached.

**Finding 6 — two of the pre-existing conflict rules fire for this kind, one of them falsely.**

```
R09PROBE[R12] THREW ConflictsInTestsException: A global named 't:n' already exists in this project;
  renaming would merge the two.
R09PROBE[R11] THREW ConflictsInTestsException: 't:m' is declared in 2 places; while more than one
  declaration exists its usages do not resolve, so they will not be rewritten.
```

R12 is the right verdict from `globalNameTaken` (C3), reached through the project-wide
`LuaGlobalDeclarationIndex` on the key `t:n`, which makes it a false positive one file away. R11 is
`ambiguousGlobal` (C4) on two files that each rename correctly and independently — its premise
("usages do not resolve") is exactly what [[NAV-13]] falsified. Design §5 replaces both for this
kind. `findCollisions` driven directly agrees: `R09PROBE[F03] collisions=1` on the two-file fixture,
`R09PROBE[F02] collisions=[n …]` on the member-collision fixture.

---

### DR-03 result — the predicate decides the fixtures both ways, and costs seconds in the tail

`design.md` §3.3-§3.4 transcribed onto real PSI, driven over fixtures and then over every corpus.

**Controls, both directions, one `configureByText` each unless a second file is named:**

| control | fixture | verdict |
| :-- | :-- | :-- |
| `c01` | plain local table with two call sites | `accepted` |
| `c02` | `function C:a() self:m() end` in the same file | `undecidedColonCall` |
| `c03` | `print(t.m)` in the same file | `dottedSpelling` |
| `c04` | two receivers sharing the member name | `accepted` |
| `c05` | global receiver, `b.lua` = `Obj:m()` | `undecidedColonCall` |
| `c06` | `b.lua` = `local function f(x) x:m() end` | `undecidedColonCall` |
| `c07` | `b.lua` = an identical `local t` / `function t:m()` / `t:m()` | `accepted` |
| `c08` | `print(t["m"])` in the same file | `bracketSpelling` |
| `c09` | `b.lua` = `---@class Other` / `---@field m fun()` | `accepted` — the tag is **not** seen; Gap 2.3 |
| `c10` | declaration with no call sites | `acceptedNoCallSites` |
| `c11` | `---@class Builder`, aliased `local b = Builder` | `accepted` |
| `c12` | `local M = {}` / `function M:m()` / `M:m()` / `return M` | `accepted` |
| `c13` | `local k = 'm'` / `print(t[k])` in the same file | `accepted` — a dynamic index is not an occurrence |
| `c14` | `function t.m() end` beside `function t:m()` | `dottedSpelling` |

`c12` and `c13` are the controls the superseded predicate decided differently: it refused `c12` as
an escaping receiver and refused `c13` as a bracket step. Both are accepted now, and Gaps 2.2 and 2.4
carry the residuals.

**Cost per rename**, timed around the whole predicate, over every declaration of every tree, on two
independent runs:

| tree | decls timed | p50 | p90 | p99 | max | slowest name |
| :-- | --: | --: | --: | --: | --: | :-- |
| luacheck | 142 | 7 ms | 63 ms | 241 ms | 305 ms | `put` |
| luarocks | 80 | 14 ms | 160 ms | 1 690 ms | 2 825 ms | `close` |
| penlight | 147 | 32 ms | 95 ms | 401 ms | 604 ms | `text` |
| zerobrane | 572 | 23 ms | 135 ms | 525 ms | 3 163 ms | `GetTabCtrl` |
| lua-language-server | 268 | 14 ms | 127 ms | 1 466 ms | 9 957 ms | `close` |

The first run gave the same shape — ZeroBrane max 3 128 ms, substitute max 10 645 ms, LuaRocks
2 825 → 3 002 ms — so the seconds-long tail reproduces. **This is the measurement that decides
design §9 Alternative B**: a predicate with a multi-second tail cannot run in
`substituteElementToRename`, which is on the EDT.

**Scope sensitivity, executed.** Running the same predicate over `allScope` and over the copied tree
only produced the **same verdict for every declaration on every tree**; they differ only in which
clause declines first, for the declarations the probe printed as `DIFF` lines — every one of them a
method whose name the bundled `io.lua` also spells dotted (`close`, `read`, `sort`, `setvbuf`), so
each reads `dottedSpelling` under the wide scan and `undecidedColonCall` under the narrow one. Design §3.3 therefore picks `projectScope` to
match the usage set's scope, and the reach figures hold under either.

---

### DR-04 result — a colon call can resolve into the plugin's own jar

```
R09PRED[j01] resolved=write
  file=…/plugins-test/lunar/lib/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua
  writable=false verdict=notWritable
R09PRED[j01] renameAtCallSite=THREW AssertionError: element not found in file Lua(…)
R09SCOPE resolvedFile=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua
  projectScopeContainsStub=false projectScopeContainsOwnFile=true
  stubWritable=false ownWritable=true
```

on `local f = io.open("x")` / `f:<caret>write("y")`. Two things follow:

1. The rename must refuse, and `GlobalSearchScope.projectScope(project).contains(virtualFile)` is
   the discriminator — false for the stub, true for the user's own file. It is also the rule
   `LuaCatsTypeRenameProcessor` already uses for a type declared outside the project
   ([LuaCatsTypeDeclarations.kt:206-209](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsTypeDeclarations.kt)).
2. **The `AssertionError` is a fixture artifact, not a production verdict.**
   `myFixture.renameElementAtCaret` looks the substituted element up in the fixture's own file, and
   the substitution handed back an element in another file. It is evidence that the substitution
   crosses files, and it is why an explicit refusal is specified instead of relying on the
   platform's read-only check, whose behaviour here was **not** measured.

---

### DR-05 result — which handle can answer "does this receiver already have member *n*"

Every candidate handle was driven over the same fixture set, one `configureByText` per test method,
and both the type and the verdict printed.

**Finding 1 — the declaration-side receiver has no type, in every shape.**

```
R09C[local]     receiver='t'       M1declSideValueType type='unknown' M2leaf/M2parent/M2grand type='unknown' M3global type='unknown'
R09C[annotated] receiver='Builder' M1declSideValueType type='unknown' M2leaf/M2parent/M2grand type='unknown' M3global type='unknown'
R09C[global]    receiver='Obj'     M1declSideValueType type='unknown' M3global type='{  }' plain=true decl='function Obj:n() end'
```

`M1` is `getValueType(funcName.nameRef)`; `M2` walks `LuaScopeProcessor`'s result and its parents;
`M3` is `getGlobalType(receiverText)`. Only `M3` answers, and only for a global receiver — so a rule
keyed on the declaration side is inert for every local and annotated receiver. `design.md` §9
Alternative F records why `M3` is not adopted as a fallback.

**Finding 2 — `LuaScopeProcessor` resolves a declaration-side receiver to ITSELF**, which is why the
`M2` handles are empty and why a receiver-identity rule cannot be built from it:

```
R09D[local] targetReceiverBinding='t'@22   (the `t` of `function t:m()`, not the `local t` at @6)
R09D[local] occurrence=LuaMethodExprImpl@58 binding='t'@6
```

`LuaScopeProcessor`'s `LuaFuncDecl` branch declares the function's own base name for recursion
([LuaScopeProcessor.kt:79-84](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)),
so the crawl stops at the enclosing declaration. This is the same property [[BUG-476]] records from
the other end.

**Finding 3 — a usage's receiver answers, and answers in every shape that has a usage.** The
mechanism `design.md` §3.7 specifies, driven end to end (`MECHANISM receiverAlreadyHasNewName`):

```
R09F[local]             usages=[LuaNameRefImpl@53'm']        MECHANISM=true
R09F[localNegative]     usages=[LuaNameRefImpl@34'm']        MECHANISM=false
R09F[annotated]         usages=[LuaNameRefImpl@110'setName'] MECHANISM=true
R09F[annotatedNegative] usages=[LuaNameRefImpl@77'setName']  MECHANISM=false
R09F[global]            usages=[LuaNameRefImpl@55'm']        MECHANISM=true
R09F[crossReceiver]     usages=[LuaNameRefImpl@72'm']        MECHANISM=false
R09F[fieldKey]          usages=[LuaNameRefImpl@41'm']        MECHANISM=true
R09F[fieldKeyOther]     usages=[LuaNameRefImpl@34'm']        MECHANISM=false
R09F[dotted]            usages=[LuaNameRefImpl@53'm']        MECHANISM=true
R09F[assigned]          usages=[LuaNameRefImpl@53'm']        MECHANISM=true
R09F[shadowed]          usages=[LuaNameRefImpl@34'm']        MECHANISM=false
R09F[aliasedUsage]      usages=[LuaNameRefImpl@122'setName'] MECHANISM=true
R09F[noCallSites]       usages=[]                            MECHANISM=false
R09F[crossFile]         usages=[LuaNameRefImpl@34'm']        MECHANISM=false
```

The usage element is a `LuaNameRef`, which is what `colonCallReceiver` casts to. The two `false`
rows that are misses rather than correct negatives — `noCallSites` and `crossFile` — are Gaps 2.8
and 2.9.

**Finding 4 — the union-arm loop is what makes an annotated receiver answer.**

```
R09E[annotated]         receiver='Builder' type='{  } | Builder' plain=false unionAware=true
R09E[annotatedNegative] receiver='Builder' type='{  } | Builder' plain=false unionAware=false
R09E[local]             receiver='t'       type='{  }'           plain=true  unionAware=true
```

`plain` is `LuaType.resolveMember`; `unionAware` adds the per-arm loop
`LuaColonCallResolution.declarationLeaves` already uses. Without the loop, `REFACT-09-08` would be
inert for every `---@class`-annotated receiver.

---

### DR-06 result — a second `function t:m()` on the same receiver binds its call to the FIRST declaration

```
R09H[localRedef]  decl#0@24 usages=[LuaNameRefImpl@53]  decl#1@43 usages=[]  callSite@53 resolvesTo=24
R09H[globalRedef] decl#0@22 usages=[LuaNameRefImpl@55]  decl#1@43 usages=[]  callSite@55 resolvesTo=22
```

on `local t = {}` / `function t:m() end` / `function t:m() end` / `t:m()` and its global twin. So
renaming the first declaration rewrites the call with it and leaves the second definition spelling
the old name, with no conflict reported — at runtime the *second* definition is the one that wins,
so the rename moves which body the call reaches. `requirements.md` row 27 pins the behaviour and
Gap 2.10 states the residual and its size.

---

## Critical Risks

### Risk 1.1 — An acknowledged conflict still produces a half-rename [Medium, accepted with a measured reason]

`design.md` §9 Alternative B: the incompleteness verdict is reported through the conflicts dialog,
which offers Continue. A user who continues gets exactly the rename DR-02 Finding 3 transcribes —
with every left-behind occurrence listed by file and line first.

- **Why not refuse.** Deciding the verdict costs a word-index read plus a resolve per candidate
  occurrence, measured at a p99 of 0.5-1.5 s and a max of 9 957 ms (DR-03). The only hook that can
  refuse — `substituteElementToRename` — is on the EDT. The platform's only channel for aborting
  after background analysis is the conflicts dialog (`RenameProcessor.java:166-188` →
  `BaseRefactoringProcessor.showConflicts`), and `ConflictsDialog.createActions` always offers the
  OK action to a caller that does not construct the dialog itself
  (`platform/lang-impl/src/com/intellij/refactoring/ui/ConflictsDialog.java:160-173`) — which
  `RenameProcessor`, not this plugin, does.
- **Precedent, with the same reasoning already written down.**
  `LuaRenameConflictDetector.ambiguousGlobal` takes the identical disposition for the identical
  reason ([:215-226](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt)).
- **Mitigation**: every undecided occurrence is its own `LuaRenameCollisionUsageInfo`, anchored on
  the occurrence, so "Show conflicts in view" lists them individually rather than reporting one
  summary line. `human-verification-checklists.md` §2 is where a human confirms the dialog reads
  usefully.
- **Residual, stated**: this is strictly better than the shipped blanket refusal and strictly worse
  than a refusal that could afford to run. If a future change makes the verdict cheap — an index of
  member-name occurrences would do it — moving it to `substituteElementToRename` is a contained
  change. Recorded under Technical Debt.

### Risk 1.2 — Reach is small on un-annotated code [Medium, sized]

20 of 51 resolvable declarations on the pinned corpus, and 140 of 941 counting the declaration-only
renames (DR-01 Finding 2, re-measured). Every accepted corpus declaration is ZeroBrane's.

- **Why it is still worth shipping**: the annotated substitute accepts 50 of 121 resolvable
  declarations and 94 of 268 overall, and annotation is the direction Lua tooling is moving. The
  alternative is the status quo, which refuses 941 of 941 with a message that is now false.
- **What would move it**: widening `upSet` reach to the factory / `self` / nested-constructor shapes
  ([[TYPE-13]] Gap 2.7) and giving a `require`d module's type its members (Gap 2.11). Both are
  engine changes with the member-map blast radius [[TYPE-13]] Risk 1.1 describes, and both raise
  what is *decidable*, which is the only direction that raises reach without giving up soundness.
- **Action**: recorded, not blocking. Unlike the superseded plan's Gap 2.3, no product decision is
  outstanding: the feature renames a measured, non-empty set and reports the rest.

### Risk 1.3 — The scan runs on every colon-method rename, including cancelled ones [Low]

It is the most expensive rule in `findCollisions`. Mitigated by the standing invariant of that
object: `ProgressManager.checkCanceled()` is the first statement of every iteration block — per
candidate file and per occurrence — because both loops resolve. `LuaRenameConflictTest`'s three
`testCancellationIsChecked…` cases are the existing differential gate for that invariant, and
`implementation-plan.md` Phase 1 adds a fourth over the occurrence count.

---

## Design Gaps

### Gap 2.1 — Renaming a table leaves the receiver segment of its `function t:m()` behind [pre-existing, not caused here]

Renaming the receiver `t` rewrites `local t` and `t:m()` but not the `t` of `function t:m()`.
Measured on the unmodified rename path and filed as [[BUG-476]]; `requirements.md` row 15 sits
beside it. This feature neither causes nor fixes it, and must not widen into it: the receiver rename
is a `LOCAL_VARIABLE` rename and takes no branch this design adds.

### Gap 2.2 — A dynamically indexed member is not decided, and cannot be [accepted, sized]

`t[k]`, `t[a .. b]` and `_G["x"]` name a member the PSI does not spell. Treating any non-literal
bracket step as an occurrence refuses every rename: 5 424 such steps on the corpus, 3 409 on the
substitute (DR-01 Finding 3). The disposition is the same as `REFACT-01-20`'s `Won't` for `_G["x"]`.

- **The cost is pinned, not merely admitted**: `requirements.md` row 20 asserts that `t[k]` beside a
  renamed method produces **no** conflict, so the residual is a visible property rather than a
  discovery. `R09PRED[c13] verdict=accepted` is the executed evidence.
- **What would close it**: nothing short of constant-folding the index expression, which is
  undecidable for a variable key. Not tracked as future work, because there is no work to do.

### Gap 2.3 — `---@field m` names the member in a spelling the scan cannot see [accepted, stated]

The occurrence scan walks `LuaNameRef` and `LuaIndexExpr`. A LuaCATS tag is neither: `LuaCatsFieldTag`
extends `PsiElement`
([LuaCatsFieldTag.java:8](../../../../src/main/gen/net/internetisalie/lunar/luacats/lang/psi/LuaCatsFieldTag.java)),
and the comment that holds it, `LuaCatsLazyCommentImpl`, extends `LazyParseablePsiElement`
([:20-23](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/impl/LuaCatsLazyCommentImpl.kt)) —
**not `PsiComment`**, so even a text scan over `PsiComment` children misses it. Executed:
`R09PRED[c09] verdict=accepted` with a `---@field m fun()` present in a sibling file.

- **Consequence**: the rename proceeds and leaves the annotation on the old name.
- **Measured cost**: a `---@field` clause would have been the sole blocker for no declaration on
  either tree (DR-01 Finding 3), so the gap costs one stale annotation in a case the scan already
  refuses for another reason far more often than not. This is what distinguishes it from the
  table-constructor key, which **is** a sole blocker for 8 corpus and 4 substitute declarations and
  is therefore in the occurrence set rather than in this gap.
- **What would close it**: a `FileBasedIndex` over tag PSI, which is what `.agents/AGENTS.md`
  prescribes for any "find all `@tag` by name" feature and what `LuaCatsTypeNameIndex` already does
  for `@class`/`@alias`. Recorded under Technical Debt.
- **Note the same-file case behaves differently and is not a gap**: with `---@field m` on the
  *receiver's own* class, the member resolves to the field rather than to the `LuaFuncDecl`, the
  call site reaches no declaration, and the scan reports it as an undecided colon call. Measured on
  `---@class T` / `---@field m fun()` / `local t = {}` / `function t:m() end` / `t:m()`.

### Gap 2.4 — A consumer outside the project can call a renamed method [accepted]

The completeness rule is closed over the refactoring scope. A module that `return M`s and is
consumed by code not in the project — a published rock, a plugin API — renames cleanly and breaks
its consumers. `R09PRED[c12] verdict=accepted` is that case.

- **Why accepted**: every rename in every language has this property, and the superseded design's
  refusal of the shape bought nothing measurable — it was part of the predicate that accepted 0 of
  941.
- **Not mitigated in code.** Stated here and in `requirements.md` row 21 so it is a known property.

### Gap 2.5 — `ReferencesSearch` and `declarationLeafOf` disagree for one declaration, and it is not located [open, bounded]

The reach instrument (forward map) and the predicate instrument (`ReferencesSearch`) agree on the
accepted set for luacheck, luarocks, penlight and the substitute, and differ by exactly **one**
ZeroBrane declaration: 99 accepted by the forward-map instrument against 98 by the
`ReferencesSearch` one.

> **Both figures are on the OLD occurrence set**, before §3.3's closure over `field`. Under the
> re-measured set ZeroBrane's accepted total is **95** (20 + 75, DR-01 Finding 2), so neither 99 nor
> 98 appears in the current table and the pair must not be read against it. They are kept as the
> **old-set** pair because the gap is a statement about the two instruments disagreeing *by exactly
> one*, which is a property of the comparison rather than of either total — and that property is
> what Phase 1's re-run task checks, on whichever set is current.

- **What is bounded**: both instruments used the same denominators, the same occurrence set and the
  same clauses; the only difference is how the usage set is obtained. So the disagreement is in
  `ReferencesSearch` returning a site the forward map attributes to that declaration, or the
  reverse.
- **What is not established**: *which* declaration, and in which direction. The probe did not print
  per-declaration identities and was reverted.
- **Why it does not block**: design §3.4 is written so that a site which resolves *at all* is
  decided, so a usage `ReferencesSearch` misses costs a missing conflict on a site that is renamed
  anyway — never a false conflict and never a silent half-rename through this clause.
- **Action**: `implementation-plan.md` Phase 1 adds a verification task that re-runs both instruments
  against the shipped code and names the differing declaration.

### Gap 2.6 — A user-narrowed refactoring scope makes the scan over-report [accepted]

`BaseRefactoringProcessor.myRefactoringScope` defaults to `GlobalSearchScope.projectScope`
(`BaseRefactoringProcessor.java:186`) but the rename dialog lets the user narrow it. The usage set
narrows with it; design §3.3's scan does not. Occurrences outside the chosen scope are then reported
as undecided.

- **Direction of the error**: conservative. It reports occurrences that will indeed be left behind,
  because the narrowed rename will not rewrite them.
- **Not mitigated in code**, because `findCollisions` receives no scope argument and threading one
  through would change `LuaRenameConflictDetector`'s signature for every kind.

### Gap 2.7 — The conflict arm changes which rules run for `METHOD_FUNCTION` [scoped, pinned]

Before this feature no `METHOD_FUNCTION` reached `findCollisions` — `substituteElementToRename`
refused first. Design §5 states each inherited rule's premise and why it is false here, and DR-02
Finding 6 measured C3 and C4 firing.

- **Risk of the arm**: `captures` (C1) no longer runs for this kind. Its premise is lexical capture
  of the renamed name, which a table member does not participate in — and since [[NAV-13]] a colon
  member name has no lexical binding at all.
- **Action**: `requirements.md` rows 12, 17-17d and 18 pin all three directions — C4 must not fire,
  the member collision must be reported by the new rule in each receiver shape it can decide, and C1
  must not fire.

### Gap 2.8 — `receiverAlreadyHasNewName` reports nothing for a declaration with no bound call site [accepted, pinned]

`design.md` §3.7 takes the receiver's type from a usage, because the declaration-side receiver has
none (DR-05 Finding 1). A declaration whose usage set is empty therefore has no handle:
`R09F[noCallSites] usages=[] MECHANISM receiverAlreadyHasNewName=false` on
`local t = {}` / `function t:m() end` / `function t:n() end`.

- **Consequence**: renaming `m` to `n` there produces two declarations of one member with no report.
  Nothing is orphaned — the rename rewrites no call site, because there is none — so the residual is
  a duplicate definition rather than a broken reference.
- **Direction of the error**: silent, which is why `requirements.md` row 29 asserts it rather than
  leaving it to be met in the field.
- **What would close it**: a scope-correct resolution from a declaration-side receiver to its
  variable's declaration. `LuaScopeProcessor` cannot supply one — it resolves that receiver to itself
  (DR-05 Finding 2) — so closing this needs a change in that object, which is [[BUG-476]]'s territory
  and not this feature's. Recorded under Technical Debt.

### Gap 2.9 — a member declared in another file, on a global receiver, is not seen by `receiverAlreadyHasNewName` [accepted, traded]

With `a.lua` = `Obj = {}` / `function Obj:m() end` / `Obj:m()` and `b.lua` = `function Obj:n() end`,
renaming `Obj:m` to `n` reports nothing: `R09F[crossFile] MECHANISM receiverAlreadyHasNewName=false`.
The receiver's type is built per file, and `b.lua`'s member is not in it.

- **This is the one case the replaced rule decided.** `globalNameTaken` searched
  `LuaGlobalDeclarationIndex` project-wide for the key `"Obj:n"` and would have reported it.
- **Why the trade is taken anyway**: the same project-wide key makes that rule report a merge between
  two *unrelated* receivers that merely share a spelling, which `requirements.md` row 12 forbids and
  DR-02 Finding 6 measured. A missing conflict on a cross-file global is the lesser error, and it is
  the direction every other rule in this design already errs in.
- **What would close it**: asking the same question of the receiver's type in each file that declares
  a member on it — i.e. an index of member declarations by receiver, which is the same missing index
  Gap 2.3 wants. Recorded under Technical Debt.

### Gap 2.10 — a redefinition of the same member on the same receiver is neither rewritten nor reported [accepted, sized, pinned]

`design.md` §3.3's `when` excludes `LuaFuncNameMethod` so that a *different* receiver's
`function q:m()` (row 3) and an identical shape in another file (row 12) are not reported. The price
is a second `function t:m()` on the **same** receiver: DR-06 measured the call site binding to the
first declaration, so renaming it rewrites the call and leaves the second definition on the old name.

- **Why it matters more than it looks**: at runtime the *second* definition is the one in force, so
  the rename changes which body the rewritten call reaches. It is the only residual in this feature
  that alters behaviour rather than leaving a stale name.
- **Size**: same-file, same-receiver-text redefinitions number 3 in luacheck and 0 in luarocks,
  penlight, zerobrane and the substitute (text-level scan of `function <R>:<m>(` per file — stated
  because it is a proxy, not a PSI-exact measurement, and because the receiver-identity question that
  would make it exact is the same one this gap is about).
- **Why it is not closed here**: distinguishing "the same receiver" from "a receiver spelled the
  same" needs the receiver-identity handle Gap 2.8 also wants, and reporting on receiver *text*
  instead is exactly `globalNameTaken`'s error. `requirements.md` row 27 pins the behaviour, and its
  falsifier states that the naive fix reddens rows 3 and 12.

---

## Technical Debt & Future Work

- **TBD: move the completeness verdict to a refusal.** Blocked on making it cheap enough for the
  EDT; an index of member-name occurrences by name would do it. Risk 1.1 is the record.
- **TBD: rewrite the dotted spelling alongside the colon one.** `t.m`, `function t.m()` and
  `t.m = f` name the same member and are reported, not rewritten. Rewriting them needs a
  decidability rule for dotted call sites, which resolve through `getQualifiedName` /
  `LuaGlobalDeclarationIndex` — a different mechanism, not a wider version of this one. Measured
  demand, under the occurrence set §3.3 now closes: the dotted spelling is the sole blocker for 33 of
  941 corpus and 32 of 268 substitute declarations, and among declarations that have a bound call
  site for 0 of ZeroBrane's 51 and 10 of the substitute's 121.
- **TBD: see `---@field` tags.** Needs a `FileBasedIndex` over tag PSI, as `LuaCatsTypeNameIndex`
  does for `@class`/`@alias`. Gap 2.3.
- **TBD: an index of member declarations by receiver.** Gaps 2.8, 2.9 and 2.10 want the same missing
  thing: a member-declaration lookup keyed on the receiver's *identity* rather than on its spelling,
  reaching across files. It would close the cross-file collision (2.9), the receiver-identity test a
  redefinition needs (2.10), and — with a scope-correct declaration-side resolution beside it — the
  no-call-site handle (2.8). None is closed here.
- **TBD: correct [[NAV-13]]'s reach headline.** Its "67 of 941" mixes corpus declarations with the
  plugin's bundled stdlib stubs; the corpus-only figure is 51 (DR-01 Finding 1). Not edited here —
  this feature does not own that artifact — and recorded so the two documents are not read as
  disagreeing by accident.

- **§3.7 duplicates `LuaColonCallResolution`'s receiver walk and union-arm lookup rather than widening
  either to `internal`.** The choice is deliberate and stated in §3.7 — a rename conflict rule
  reaching into a resolution object's privates couples two features that currently share only a
  measurement. But §3.7's own argument for correctness is that "already has this member" and "a call
  to this member resolves" are **one question asked of one element**, and that now rests on two
  copies staying identical, with nothing enforcing it. If either walk changes — a sixth receiver
  refusal in `receiverOf`, a change to how `LuaUnionType.resolveMember` treats arms — the copies
  diverge silently and the conflict rule answers a question the resolver no longer asks. Cheapest
  fix if it ever bites: make `receiverOf` and the arm loop `internal` and call them, deleting the
  copies. Raised in review round 3 as an observation rather than a plan defect.

## Test Case Gaps

- **The refusal balloons and the conflicts dialog are not exercised headlessly.**
  `CommonRefactoringUtil.showErrorHint` throws instead of painting under `BasePlatformTestCase`, and
  `RenameProcessor.preprocessUsages` throws `ConflictsInTestsException` instead of showing a dialog
  (`RenameProcessor.java:179-181`). So no automated test has ever seen either surface.
  `human-verification-checklists.md` covers both.
- **The corpus reach measurement was run against a transcription of design §3.3-§3.4 and §3.7, not
  against the shipped classes**, which do not exist yet. `implementation-plan.md` Phase 5 re-runs it
  against the shipped `LuaColonMethodRename` and requires DR-01 Finding 2's re-measured verdict table
  to reproduce.
- **In-place rename is not covered, because it is unreachable.** Both in-place gates require
  `kindOf(...)?.isFileLocal == true` and `METHOD_FUNCTION.isFileLocal` is `false`. If a later change
  makes a method kind file-local, that assumption dies silently — `design.md` §1's prior-art table
  is where it is written down.
- **The platform's own read-only handling for a jar-backed declaration was not measured** — only
  that the substitution reaches one (DR-04). `design.md` §3.6 refuses before that path is taken, so
  the untested behaviour is unreachable rather than relied upon.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- [[NAV-13]] risks and gaps — DR-01 is the reach measurement this feature's DR-01 extends and
  corrects; Gap 2.2 is why an un-annotated receiver cannot cross a file.
- [[TYPE-13]] risks and gaps — Gaps 2.7, 2.11 and 2.12 are the engine limits that leave a call site
  undecided.
