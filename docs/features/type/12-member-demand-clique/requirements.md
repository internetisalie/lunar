---
id: "TYPE-12"
title: "12: A method call site mints its own member demand, and every one is wired into a clique"
type: "feature"
status: "planned"
priority: "high"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-12: The member-demand clique

## Overview

`LuaTypesVisitor.kt:924-931` mints a **structurally identical** member demand for every method call
site on a receiver, and `checkTableCompatibility` (`LuaTypeGraph.kt:858-859`) wires each one
bidirectionally into the receiver's class member. N call sites on one `---@class` therefore produce N
mutually reachable nodes — an N-way clique — and the fixed-point checker walks the resulting Θ(n²)
cross-product.

This is the residual [[BUG-473]] left after both of its phases, and it is deliberately **not** filed
as a bug: nothing here is incorrect. The engine infers what it should; it just represents one demand
N times.

## Measured (BUG-473 DR-8 and Phase 2, libvirt builder `debian13`)

BUG-473's two phases removed the *redundant re-derivation* over that cross-product — a walk-root
memo (Phase 1) and a per-visit `write` hoist (Phase 2) — taking an annotated 160-call file from
145 s to 3.6 s. What they did not remove is the cross-product itself:

| quantity | after both phases |
| :-- | :-- |
| n = 160 annotated | 3 556 ms, ~15x its tag-free control |
| growth per doubling | ×5.9 — still above linear |
| cost attributable to the clique | ~2.8 s at n = 160 (DR-8's frozen-memo arm) |
| live IDE, 320 call sites | highlighting completes in 20–45 s (DR-7) |

This plan re-measured today's engine as a closure series (risks-and-gaps DR-01). The closure is the
sum of every variable node's up- and down-set sizes, and it is the quantity case 1 gates.

## The constraint any design must clear

**Every cheap bound on the clique that BUG-473 measured changes what the engine infers.** The
mildest — dropping `checkTableCompatibility`'s reverse member edge — is also the fastest fix the
report found (240 ms at n = 160) and it **deletes the user-visible `---@param` violation on every
method call**, reintroducing BUG-419's defect. That candidate is pinned as a red test in
`LuaAnnotatedClassDiagnosticsTest` (mutation M-C).

So this is a representation change, not an edge-pruning optimisation. The chosen representation
([design.md](design.md)) keeps every per-site node and every edge kind, and changes only *which*
node of a receiver's call sites is wired to the class member: the first one, with the others hanging
off it.

## Scope

### In Scope

- Colon method calls (`recv:m(...)`) minted in `LuaTypesVisitor.visitFuncCall`, deduplicated per
  `(receiver node, method name)`.
- Preserving, exactly, the anchored diagnostic list and the inferred types over the **golden set**
  (TYPE-12-03, TYPE-12-04), with HYPOTHESIS entries included.
- A golden-output gate recorded from today's engine before the change, so the preservation claim is
  checked by a test that can fail.
- A deterministic growth gate (closure size) and pinned anchored-diagnostic fixtures.

### Out of Scope

- **Dot-access members** (`b.name`, `LuaTypesVisitor.visitIndexExpr`). They form the same kind of
  clique — measured quadratic (DR-05) — but are also assignment targets,
  so the premise this design rests on does not carry over unargued. Recorded as future work.
- **`self:m()` across many methods.** Each method's `self` is a distinct node, so these are distinct
  receivers and are not deduplicated (DR-05). Recorded as future work.
- **Removing the per-site HYPOTHESIS duplicates** the clique produces. They are kept on purpose
  (TYPE-12-03); deleting them is a behavioural change for a separate decision.
- **BUG-474** (the host-dependent ratio assertion). Nothing here uses that ratio.
- **Cross-file inference equivalence as a standing gate.** The golden set configures each file
  alone (design §2). Cross-file equivalence rests on DR-04's one-time differential only.

## Requirements

| ID | Requirement | Priority | Status | Description |
| --- | --- | --- | --- | --- |
| TYPE-12-01 | **Structurally identical member demands on one receiver are wired into the class member through one node** | M | Not Implemented | For each `(receiver node, method name)`, only the first call site's member node is ever wired (bidirectionally) to a class member; later sites hang off it by one forward edge. Per-site member nodes remain, because each carries its own call's shape (DR-02 measured the loss when they are removed). |
| TYPE-12-02 | **Closure growth is linear on one receiver** | M | Not Implemented | On the one-receiver annotated fixture, closure size grows ≤ ×2.2 per doubling of call sites from n = 40 to n = 160 and is ≤ 5 000 at n = 160. Today's values: DR-01. |
| TYPE-12-03 | **The anchored diagnostic list is unchanged on the golden set** | M | Not Implemented | For every file in the golden set, the sorted list of `(severity, message, anchor range, anchor PSI class)` from `LuaTypesSnapshot.getErrors()` — HYPOTHESIS entries included — equals the list recorded from today's engine. |
| TYPE-12-04 | **Inferred types are unchanged on the golden set** | M | Not Implemented | For every file in the golden set: the type of every element the snapshot maps (`getValueType`), of every global it declares (`getGlobalType`), and its file return type (`getFileReturnType`) render identically to the recording from today's engine. |
| TYPE-12-05 | **Every BUG-473 instrument stays green unmodified** | M | Not Implemented | `LuaAnnotatedClassDiagnosticsTest` (M-C still red), `LuaTypeGraphRootResolutionBudgetTest`, `LuaTypePairWriteDifferentialTest`, `LuaAnnotatedFixtureSweepTest`, and the `-PwithCorpus` sweep with the `.baseline` files byte-identical. |
| TYPE-12-06 | **The annotated n = 160 snapshot is materially faster** | S | Not Implemented | Recorded, not asserted (BUG-474). Evidence: DR-01, DR-02. |
| TYPE-12-07 | **Tighter root-resolution budgets** | C | Not Implemented | Not done: DR-02 measured the budget fixtures' root-resolution counts unchanged by this design, so there is nothing to re-baseline. Kept as a row so nobody re-derives that. |

## Detailed Specifications

### TYPE-12-01

A **hub** is the member node minted by the first colon call site of a given method name on a given
receiver node. A **later site** is any subsequent call site with the same key; its member node is
attached to the hub. "Receiver node" means the node `firstNode(calleeUnwrapped)` returns — the same
object for every reference to one binding (`LuaScope.lookup`, `LuaScope.kt:36`). Exact minting order
and wiring are design §5.1–§5.2.

### The golden set (TYPE-12-03, TYPE-12-04)

The golden set is every golden file under `src/test/resources/types-golden/`, recorded from today's
engine in Phase 0's commit, before any engine change (design §4.6, format §5.5):

- **Shapes** (`types-golden/shapes/`, ordinary suite and CI): every fixture in design Appendix C, plus
  the in-repo annotated corpus fixtures `src/test/resources/corpus/annotated/builder.lua` and
  `src/test/resources/corpus/annotated/shapes.lua`. No shape declares a class or alias name that a
  bundled stub declares (design §5.6, case 11). A shape that collides with a stub class is excluded
  until BUG-498 is fixed, because the engine merges the two declarations in an order that varies
  between JVMs (design Appendix C, "Excluded shape").
- **Member files** (`types-golden/member-files/`, ordinary suite on the builder; excluded in CI by
  `-PexcludeExternalFixtureTests`, a deliberate choice, design §2): one real file per
  pinned corpus member, each configured **alone** in the light fixture —
  `luacheck/src/luacheck/cache.lua`, `zerobrane/src/editor/commands.lua`,
  `luarocks/src/luarocks/tools/zip.lua`, `penlight/lua/pl/Date.lua` (paths under `test/corpus/`).

**What is guaranteed, and what is not.** TYPE-12-03 and TYPE-12-04 are guaranteed by a standing test
for the golden set only. For any other file, the evidence is DR-04's one-time corpus-wide differential,
whose row states its extent and limits. That is evidence, not a gate. Cross-file
inference (a file's types as influenced by other files in its project) is not in any golden, because
each golden file is configured alone. The corpus `.baseline` files are not part of this guarantee:
they count non-HYPOTHESIS inspection findings per inspection, and HYPOTHESIS entries never reach them
(`LuaTypeAssignabilityInspection.kt:32,38`, `LuaReturnTypeMismatchInspection.kt:30,36`).

### TYPE-12-03

"Anchor range" is `ElementError.element.textRange`. The list includes HYPOTHESIS entries: they are
user-observable as the silent annotation `LuaTypeHypothesisAnnotator` hangs its intention on. Order
within the list is not part of the requirement (no consumer depends on it); the multiset is.

## Behavior Rules

- A receiver with **one** call site per method name is represented node-for-node and edge-for-edge
  as today.
- Diagnostics anchored on a hub's own element *because the hub was the value* of a pair are reported
  at every later site of that hub as well (design §5.3). Diagnostics anchored on a use are never
  replicated.

## Test Cases

Fixtures are configured with `myFixture.configureByText` in a `BaseDocumentTest` (a JUnit 5 base
over `IdeaTestFixtureFactory`'s light fixture), with the **exact** text given by design Appendix C's
Kotlin builders — no appended comment. `forFile` returns `LuaTypes`; read meters through
`LuaTypesSnapshot.forFile(file) as LuaTypesSnapshot`, as
`LuaTypeGraphRootResolutionBudgetTest.annotatedClassFixtureStaysWithinItsRootResolutionBudget` does
(`LuaTypeGraphRootResolutionBudgetTest.kt:49` on `main` at `a90adac1`). "Errors" means `getErrors()`
rendered as `SEVERITY|message|(start,end)` and sorted.

**Evidence status of each mutation.** Every mutation in the table was executed and observed red —
the single-fixture cases in DR-06, the golden cases in DR-11 step 8, case 11's first mutation in
DR-11 step 9 — except the one case 11 marks *predicted*, which Phase 0 executes.

**Cases 9 and 10 name every golden a mutation turns red, not a sample.** The golden tests' failure
message has one line per mismatching golden, so the red set is observable from one run, and Phase
1's mutation task fails on a difference in either direction. Each set is DR-11 step 8's row for that
variant; the cases cite it rather than restate it.

`annotated(n)`, `paramN(n)`, `chain` and `pipeUnion` are design Appendix C's builders and
`NAMED_SHAPES` entries, which are their only definition; every `(start,end)` below is an offset into
that exact text.

| # | Requirement | Given | When | Then | Mutation (observed red unless marked *predicted*) |
|---|-------------|-------|------|------|--------------------------|
| 1 | 12-01, 12-02 | `annotated(40)`, `annotated(80)`, `annotated(160)` | `closureSize()` after `forFile` on each | 1 123, 2 203, 4 363; each ratio ≤ 2.2; n = 160 value ≤ 5 000 | replace `methodSiteMember` with today's per-site minting → 5 959, 21 479, 81 319 (×3.6, ×3.8) |
| 2 | 12-03 | `annotated(3)` | errors | exactly the 15 entries listed in [Appendix A](#appendix-a--annotated3-anchored-errors) | delete the `replicateHubAnchoredErrors` call → 13 entries: `fun() \| fun(n) is not assignable to fun()` missing at `(105,113)` and `(121,129)` |
| 3 | 12-03 | `annotated(3)` | errors | as case 2 | M-C (drop `addEdge(target, valueNode)` in `checkTableCompatibility`) → 9 entries |
| 4 | 12-03 | `chain` | errors | exactly the 36 entries listed in [Appendix B](#appendix-b--chain-anchored-errors) | delete the `replicateHubAnchoredErrors` call → 32 entries (those at `(139,143)` and `(167,171)` with messages `fun(n) \| fun(n) is not assignable to fun(n)` and `fun(n) \| fun(n) \| fun(n) is not assignable to fun(n)` missing) |
| 5 | 12-03 | `pipeUnion` | errors | exactly: `HYPOTHESIS\|Pipe \| nil is not assignable to { ... }\|(178,180)`, `…\|(183,192)`, `…\|(224,229)` — three entries (DR-11 step 7) | in design §5.1 step 5 put `hub ?: siteMember` in the `Table` instead of the site member → a fourth entry `HYPOTHESIS\|Pipe \| nil is not assignable to { ... }\|(278,283)` on `fh:read('*a')`'s `:read` (A6TH; DR-11 step 8) |
| 6 | 12-04 | `chain` | `getValueType(...).displayName()` for the name ref `q` (162,163), the name ref `r` (114,115) and the call `r:set(1)` (129,137) | `Bd \| Bd \| { ... }`, `Bd \| Bd \| { ... }`, `Bd \| { ... } \| Bd` | (a) delete design §5.1 step 7 (`addEdge(hub, siteMember)`) → `q` is `undefined` (`r` unaffected: it is the first site); (b) M-C → all three are `Bd` |
| 7 | 12-01 | `annotated(1)` | `closureSize()` | 70 — today's value on the same fixture (DR-02) | mint a separate hub node even for a key's first site (variant A5: the hub is a fresh `graph.variable` and every site member, the first included, hangs downstream of it; `Table` still names the site member and wiring is redirected to the hub) → 82 (DR-06) |
| 8 | 12-05 | — | run `LuaAnnotatedClassDiagnosticsTest`, `LuaTypeGraphRootResolutionBudgetTest`, `LuaTypePairWriteDifferentialTest` in the ordinary suite, and `LuaAnnotatedFixtureSweepTest` + the corpus sweep under `-PwithCorpus` | all green, unmodified; `sha256sum` of `src/test/resources/corpus/*.baseline` unchanged | M-C still reddens `LuaAnnotatedClassDiagnosticsTest.declaredParamViolationKeepsItsFourErrorTierDiagnostics` (13 → 3, DR-03) |
| 9 | 12-03, 12-04 | every shapes golden (design Appendix C, plus `corpus/annotated/builder.lua` and `shapes.lua`), recorded from today's engine | `LuaTypeGoldenShapesTest.everyShapeMatchesItsGolden`: render each fixture's snapshot (design §5.5) and compare with its golden file | every file byte-equal to its golden | Each mutation reddens **exactly** the shapes goldens of its risks-and-gaps DR-11 step 8 row, in the sections that row lists, and no other: (a) delete the `replicateHubAnchoredErrors` call — row A6NF; (b) delete design §5.1 step 7 (`addEdge(hub, siteMember)`) — row A6NE; (c) design §5.1 step 5 names `hub ?: siteMember` — row A6TH; (d) M-C — the shapes rows of "A6MC's full set" |
| 10 | 12-03, 12-04 | every member-files golden, recorded from today's engine | `LuaTypeGoldenMemberFilesTest`: same comparison | every file byte-equal to its golden | Case 9's (c) reddens exactly `luacheck-cache.golden`, whose `[errors]` gains `HYPOTHESIS\|File \| nil is not assignable to { ... }\|(3375,3380)\|LuaMethodExprImpl` (`fh:read("*a")`, `cache.lua:120`; DR-11 step 8 row A6TH); (d) reddens exactly the member-files rows of DR-11 step 8's "A6MC's full set"; (a) and (b) leave every member-files golden green. `File` here is the stubs' class (`io.open`), not a fixture declaration |
| 11 | 12-03, 12-04 (gate integrity) | the shapes of case 9 and the bundled stubs under `src/main/resources/runtime` | `LuaTypeGoldenShapesTest.noShapeDeclaresAStubTypeName` (design §5.6) | green: no shape declares a `---@class`/`---@alias` name a stub declares, and the stub name set is non-empty | put the excluded `fileUnion` text (`---@class File`) back into `NAMED_SHAPES` without step 2 of design Appendix C's re-enable recipe → red with `shapes/fileUnion.golden: File` (DR-11 step 9). *Predicted, not yet executed* (Phase 0 executes it): point `stubTypeNames()` at a non-existent directory → red on the non-empty assertion |

### Appendix A — `annotated(3)` anchored errors

Today's engine and the prototype agree (DR-02); the unsalted recording agrees (DR-09 step 5):

```
HYPOTHESIS|Too few arguments: expected at least 1, got 0|(53,61)
HYPOTHESIS|Too many arguments: expected at most 0, got 1|(104,119)
HYPOTHESIS|Too many arguments: expected at most 0, got 1|(105,113)
HYPOTHESIS|Too many arguments: expected at most 0, got 1|(120,135)
HYPOTHESIS|Too many arguments: expected at most 0, got 1|(121,129)
HYPOTHESIS|Too many arguments: expected at most 0, got 1|(88,103)
HYPOTHESIS|Too many arguments: expected at most 0, got 1|(89,97)
HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(105,113)
HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(121,129)
HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(53,61)
HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(89,97)
HYPOTHESIS|fun(n) | fun() is not assignable to fun()|(53,61)
HYPOTHESIS|{ ... } | Builder is not assignable to { ... }|(105,113)
HYPOTHESIS|{ ... } | Builder is not assignable to { ... }|(121,129)
HYPOTHESIS|{ ... } | Builder is not assignable to { ... }|(89,97)
```

### Appendix B — `chain` anchored errors

Same provenance as Appendix A:

```
ERROR|number is not assignable to string|(60,94)
ERROR|number is not assignable to string|(76,77)
ERROR|string | number is not assignable to string|(60,94)
ERROR|string | number is not assignable to string|(76,77)
ERROR|{ ... } | Bd is not assignable to Bd|(60,94)
HYPOTHESIS|Bd | Bd | { ... } is not assignable to { ... }|(114,115)
HYPOTHESIS|Bd | Bd | { ... } is not assignable to { ... }|(118,128)
HYPOTHESIS|Bd | Bd | { ... } is not assignable to { ... }|(130,134)
HYPOTHESIS|Bd | { ... } | Bd is not assignable to Bd|(60,94)
HYPOTHESIS|Bd | { ... } | Bd is not assignable to { ... }|(114,115)
HYPOTHESIS|Bd | { ... } | Bd is not assignable to { ... }|(118,128)
HYPOTHESIS|Bd | { ... } | Bd is not assignable to { ... }|(130,134)
HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)|(119,123)
HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)|(139,143)
HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)|(167,171)
HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(n)|(71,75)
HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(p)|(118,128)
HYPOTHESIS|fun(n) | fun(n) is not assignable to fun(p)|(119,123)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(119,123)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(130,134)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(139,143)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(167,171)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(n)|(71,75)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(p)|(118,128)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(p)|(119,123)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(p)|(129,137)
HYPOTHESIS|fun(n) | fun(n) | fun(n) is not assignable to fun(p)|(130,134)
HYPOTHESIS|number is not assignable to string|(135,136)
HYPOTHESIS|{ ... } | Bd is not assignable to Bd|(19,21)
HYPOTHESIS|{ ... } | Bd is not assignable to Bd|(72,75)
HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(114,115)
HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(118,128)
HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(119,123)
HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(130,134)
HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(139,143)
HYPOTHESIS|{ ... } | Bd is not assignable to { ... }|(167,171)
```

## Acceptance Criteria

- [ ] Every case in the table above whose Requirement column names TYPE-12-01, -02, -03 or -04 passes
      (`LuaMemberDemandHubTest`, `LuaTypeGoldenShapesTest`, `LuaTypeGoldenMemberFilesTest`), and each
      case's named mutation was executed and observed red.
- [ ] The golden files were recorded from the implementation base **before** any engine edit, in
      their own commit, and no later commit in the feature modifies them.
- [ ] Case 8: ordinary suite and `-PwithCorpus` both `--rerun --no-build-cache`, 0 failures, corpus
      `.baseline` files byte-identical, no `IMPROVED`/`REGRESSED` line.
- [ ] `ktlintCheck` clean, run alone.

## Non-Functional Requirements

- **Threading**: unchanged. All new code runs inside `LuaTypesSnapshot.forFile`'s cached-value
  computation, on whatever thread computes it today (DR-7 of BUG-473: pooled `JobScheduler` threads
  under the daemon, never the EDT).
- **Memory**: the hub registry's identity maps, one registry per graph, holding nodes and PSI the graph already holds.
- **Performance**: case 1 is the gate. Wall-clock is evidence only (BUG-474).

## Dependencies

- BUG-473 (done) — its instruments are this feature's gates.
- None blocking.

## See Also

- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
