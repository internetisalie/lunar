---
id: "TYPE-12-RISKS"
title: "12: Risks and gaps"
type: "risk"
parent_id: "TYPE-12"
folders:
  - "[[features/type/12-member-demand-clique/requirements|requirements]]"
---

# TYPE-12 Risks and Gaps

## Critical Risks

### Risk 1.1 — Equivalence is gated on the golden set only [Medium]

- **Impact**: a file shape outside the golden set could infer or report something different under
  the hub, and no standing test would see it.
- **Likelihood**: low. DR-04's corpus-wide differential found no error-list difference under A6, and
  its type differences match its own control's; the row states what was not established.
- **Mitigation**: the golden gate (design §4.6) fixes errors, element types, globals and file return
  types over the shape catalogue, the in-repo annotated fixtures and one real file per corpus member,
  recorded from today's engine before the change. The anchored fixtures in the requirements pin the
  mechanisms the variants were seen to break (DR-02, DR-06).

### Risk 1.2 — The replication rule emulates an artifact of the representation it replaces [Medium]

- **Impact**: design §5.3 reproduces diagnostics the clique produced *because* every site member sat
  in the class member's up-set. Any later change to where `reportIncompatible` anchors a value-side
  error (e.g. a fix that stops the synthetic declared use being file-wide) will move the clique's
  output and the hub's output differently, and the difference will look like a TYPE-12 regression.
- **Likelihood**: medium over the engine's life.
- **Mitigation**: the source of every counted replication is DR-08's finding, and design §5.3 states
  it. Whoever changes
  that anchoring should re-run requirements cases 2 and 4 and the golden tests, and expect to
  re-derive Appendix A/B and re-record the goldens that move.

### Risk 1.3 — Other shapes of the same clique remain [Low for this feature, Medium for users]

- **Impact**: `self:m()` called from many methods, and repeated dot reads `b.name` on a class
  receiver, still grow quadratically (DR-05). A real annotated module with many methods calling `self:` helpers is the first case.
- **Likelihood**: certain; it is out of scope by design (§2).
- **Mitigation**: recorded below as future work; DR-05's curves are where a follow-up starts.

### Risk 1.4 — `addError`'s linear de-duplication scan [Low]

- **Impact**: replication adds `(sites − 1)` `addError` calls per hub-anchored error, and `addError`
  scans `_errors` linearly (`LuaTypeGraph.kt:272`). The clique already produced the same number of
  errors through the same scan, so this is no worse than today; it is not better either.
- **Mitigation**: none needed for this feature; the whole n = 160 snapshot is fast under A6 (DR-02).

### Risk 1.5 — A golden that is not deterministic is not a gate [Medium — observed in DR-09, mitigated by design, mitigation measured in DR-11]

- **Impact**: if a golden's rendering depends on JVM, test order or project state leaked by another
  test (the light project is shared; BUG-496 is a sweep leaking `sourcePath`), the golden tests fail
  spuriously or have to be loosened, and the gate stops meaning anything.
- **Evidence**: DR-09 step 3 refuted determinism for `fileUnion`, a shape whose `---@class File`
  merges with the bundled stubs' `File` (engine defect BUG-498).
- **Mitigation**: the rendering is **not** normalised (design §2). The shape is changed instead.
  - `fileUnion` is replaced by `pipeUnion`, the same text with a class name no stub declares.
  - The stub-collision shape is excluded until BUG-498 is fixed, with a recipe to re-enable it
    (design Appendix C).
  - `noShapeDeclaresAStubTypeName` (design §5.6, requirements case 11) keeps any future shape from
    reintroducing a collision.
  - DR-11 steps 5–6 measured the response holding.
- **Residual: one member file overlaps a stub name in a way the guard cannot see.**
  `penlight/lua/pl/Date.lua` carries LDoc `-- @class function` lines inside `---` blocks (lines
  143–398), and the stubs declare `---@class function` (e.g. `runtime/standard/lua-5.4/builtin.lua:47`).
  `lua.flex:51` lexes a `---` line and the `--` lines that follow it as one LuaCATS comment, so those
  lines may reach the tag index as un-hosted `@class function` tags. Whether they do is read, not
  measured. Its golden's stability across JVMs is measured (DR-11 steps 5–6).
  **If it ever renders differently between JVMs**, with the change confined to `[elements]` hashes:
  1. Remove it from `memberFiles()`.
  2. Record the observation in BUG-498 as a second reproduction, on the un-hosted
     `materializeUnhostedClass` path (`LuaTypeManagerImpl.kt:333`).
  3. Re-enable it together with `fileUnion`.

### Risk 1.6 — The flag-input change of design §5.3 is not shown to be inert [Low]

- **Impact**: design §5.3 changes the up-set over which `certain` and `unknownProvenance` are computed
  for a later site's visit. If that ever changes whether `checkCompatibility` reports, the engine's
  output moves on some shape that no golden contains.
- **What is shown**: equal output on every shape run (DR-02, DR-10, DR-11 step 8 A6) and in every
  build of DR-04 run 2. **What is not shown**: that any of those fixtures reaches the change. No
  variant that was run alters the flags alone, so equal output there is consistent with the change
  never being exercised.
- **Why it is accepted rather than proven** (reasoning from `LuaTypeGraph.kt:431,438`, not
  measured):
  - *Receiver with no wired class member.* A later site's up-set goes from empty to `{hub}`.
    Under today's engine that visit checks no pair at all. Under the hub it checks one, `(hub, the
    site's own use)`, and only that pair's result is passed the changed flags. Any difference is
    therefore that one pair's output, which equalled today's on the shapes of this kind that were
    run, `nilReceiver` and `functionReceiver` (DR-02), and in DR-04's corpus builds.
  - *Receiver with a wired class member.* The up-set loses only the **other later site members**.
    The hub, the class member and every write reaching them remain. `certain` needs exactly one
    non-declared value, and both up-sets hold at least the hub and the class member, so it is false
    either way. `unknownProvenance` can only change if a removed site member is the sole carrier of
    an unknown write. A later site member has no write of its own; its `write` derives from writes
    reaching the class member. Those remain in the up-set through the hub. This is the case of
    `unannotated`, `callBeforeDecl`, `nilMember`, `unknownMember` and `declaredFieldNil`, which a
    member declaration or assignment wires.
- **Mitigation**: the golden gate and the anchored cases fix the output on every shape that was
  measured.
- **How to prove it, if it ever matters**: build variant A6XF. It is A6 with `checkNodePairs`
  computing `certain` and `unknownProvenance`, for a visit to a later site member, over the up-set
  with the hub removed, which is today's flag inputs on the hub graph. A fixture that A6XF turns red
  against A6 reaches the change. Adding that fixture to the golden set, with A6XF as its mutation,
  turns this risk into a tested claim. Not done for this feature: no measured shape motivates it.

## Design Gaps

None open. DR-09's refutation is answered by design §2, §5.5, §5.6 and Appendix C, and DR-11
measured the answer. DR-10's limit is Risk 1.6.

## Technical Debt & Future Work

- **TBD: dot-access member clique** — `visitIndexExpr` mints a per-access member node
  (`LuaTypesVisitor.kt:1198`), wired bidirectionally like the call-site member. Measured quadratic
  (DR-05). Needs its own argument because index members can be assignment targets
  (`declaresMember = isAssignmentTarget(o)`), so "the site member has no writes" does not hold.
- **TBD: `self:` across methods** — keyed on node identity, each method's `self` is a new receiver
  (DR-05). Deduplicating would need a key that survives the per-function `self` node, e.g. the
  `SelfBinding.receiver` it is seeded from (`LuaTypesVisitor.kt:49`, `pendingSelf`), which is a
  different design question.
- **TBD: the per-site HYPOTHESIS duplicates themselves** — kept by TYPE-12-03 (design §2). They are
  the class member's own `write` reported against its own declared type once per call site; whether
  they should exist at all is a BUG-419-style question, separate from this representation change.

## Pre-Implementation De-risking Tasks

DR-01 to DR-08 ran on the libvirt builder (`debian13`) at `e762dc74` plus a throwaway prototype,
through `tooling/gce-builder/gce-builder.sh run "<gradle args>"`. The first probe run overlapped
another agent's unlocked build in the same VM directory and nothing from it is cited: every probe
number below comes from a later run that reproduced it. The prototype was reverted before this
commit; `git diff --stat -- src/` is empty.

**The prototype, so a reader can judge what the numbers mean.** A `Type12Probe` object held a
variant name in a `ThreadLocal`. `LuaTypesVisitor.visitFuncCall`'s method branch switched on it
between today's code (`BASE`) and each variant below; `checkTableCompatibility` and `checkNodePairs`
consulted it for the redirect and replication. A probe test (`Type12ProbeTest`, `BaseDocumentTest`)
built each fixture once per variant in one JVM and compared against `BASE`. It set the variant
explicitly on its own thread before every build, and switched the corpus differential off for its
own duration. It appended `\n-- salt N\n` to every fixture so that consecutive builds of one shape
never shared a file text. It compared:

- **errors**: the sorted list of `SEVERITY|message|textRange|PSI class|element text` from
  `getErrors()`;
- **types**: for every element in the snapshot's `elementNodes`, `getValueType(el)` rendered two
  levels deep (table members with their `write`/`read`, function parameters with `write`/`read`,
  returns) plus its `graphTypeToLuaType` rendering (type name and each member's name, type name and
  `sourceElement` range);
- **closure**: `edgeCount()` read immediately after `forFile`, before any rendering;
- root-resolution counts and in-JVM wall-clock of `forFile`.

For corpus-wide runs (DR-04) `LuaTypesVisitor.buildSnapshot` built every snapshot once with `BASE`,
then once per listed variant, compared each with the same renderings plus `getGlobalType` for
every global, counted mismatches per variant, and **returned the `BASE` snapshot** — so the suite and
the sweeps ran on today's engine and their verdicts are about today's engine.

**Runs cited below.** Every probe run used the same `Type12ProbeTest`, with a growing variant list.

| run | command (`tooling/gce-builder/gce-builder.sh run "…"`) | outcome | variants beyond `BASE` |
| :-- | :-- | :-- | :-- |
| 2 | `test --rerun --tests '*Type12ProbeTest*'` | `BUILD SUCCESSFUL` | A2, A3, A4, V3 |
| 4 | same | `BUILD SUCCESSFUL` | A3, A4, A5, A5MC |
| 5 | same | `BUILD SUCCESSFUL` | A5, A6, A6MC, A6NF |
| 7 | `test -PwithCorpus --rerun --tests '*LuaCorpusSweepTest.testZerobraneCorpus*' --tests '*Type12ProbeTest*'` | **`BUILD FAILED`** — 2 tests, 1 failed: `testZerobraneCorpus` at `LuaCorpusSweepTest.kt:101` | A1, A2, A3, A4, A5, V3, A6, A6MC, A6NF, A6NE, A6TH |

**Run 7's failure does not affect the probe numbers cited from it.** `Type12ProbeTest` passed in
run 7. The failing test is the zerobrane sweep, run alone, failing its count ratchet; it shares no
fixture with the probe. That failure is a BUG-496 / MAINT-37 observation, not TYPE-12 scope, and is
recorded there (branch `plan-maint-37`, `docs/features/maint/37-pinned-definition-libraries/risks-and-gaps.md`,
"Unconfirmed lead: zerobrane's `unresolvedRequires` depends on the invocation shape"). Every probe
comparison sets its own variant before its own build, so the co-run test cannot change which
representation built a probe fixture. Run 7's `BASE` closure and error counts equal runs 2, 4 and 5
at every size (DR-01), and A6's `annotated(160)` row equals run 5's. Two kinds of run-7 number are exposed to the co-run test:
wall-clock, which is evidence only, and the JVM-wide replication counter (DR-08). Variants A6NE and
A6TH ran only in run 7.

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| DR-01 | Re-measure today's engine on the reproduction | requirements "Measured" | done |
| DR-02 | Variant matrix on the shape catalogue | design §2, §10; TYPE-12-01/03/04/06 | done |
| DR-03 | M-C under the chosen design | TYPE-12-05 | done |
| DR-04 | Corpus-wide differential, with BASE-vs-BASE control | Risk 1.1; TYPE-12-03/04 | done |
| DR-05 | Residual shapes | Risk 1.3; scope | done |
| DR-06 | Every single-fixture test-case mutation, executed | requirements test cases | done |
| DR-07 | The chosen design as the engine, whole suite + corpus | TYPE-12-05 | done |
| DR-08 | Where replicated errors come from | design §5.3; Risk 1.2 | done |
| DR-09 | The golden gate on today's engine: determinism, salt, reach | TYPE-12-03/04 gate; Risk 1.5; requirements cases 9, 10 | done — **step 3 refuted** (`fileUnion` not deterministic); steps 5–6 hold; step 7 superseded by DR-11 step 8 |
| DR-10 | The flags' inputs at later sites | design §5.3 | done — equal output on its shapes; reach not shown (Risk 1.6) |
| DR-11 | The response to DR-09: `pipeUnion`, the `build/` recording path, the full red sets | Risk 1.5; requirements cases 5, 9, 10, 11; design §5.5 | done — every deciding observation holds |

### DR-01 — today's engine, re-measured

Run 7, `BASE` rows, `annotated(n)` fixture (closure and error counts identical in runs 2, 4 and 5):

| n | errors | closure | `forFile` ms |
| ---: | ---: | ---: | ---: |
| 20 | 83 | 1 799 | 27 |
| 40 | 163 | 5 959 | 114 |
| 80 | 323 | 21 479 | 576 |
| 160 | 643 | 81 319 | 3 829 |

Closure ×3.3 / ×3.6 / ×3.8 per doubling. The error counts are BUG-473's `4n + 3`, reproduced.
Wall-clock varies run to run (n = 160 read 3 458, 3 829, 4 008 and 5 195 ms in runs 4, 7, 2 and 5);
the closure and error counts did not vary at all across them.

### DR-02 — the variant matrix

Run 7. Every variant that also ran in run 2, 4 or 5 reported the same error count and closure size
there, on every shape and size. Each variant is a
complete alternative representation; "equal" means the error list and every element's type rendering
are identical to `BASE` on that fixture.

| variant | what it does | result |
| :-- | :-- | :-- |
| A1 | one node per `(receiver, name)`; later sites reuse it, no per-site nodes | errors differ on every multi-site shape (e.g. `annotated(20)` 83 → 26) |
| A2 | one member per `(receiver, name)`, per-site use nodes | errors differ (`annotated(20)` 83 → 45; `inheritance` loses `Too few arguments` at `d:say()`'s `:say`, `arity` loses `Too many arguments` at `x:two(1, 2, 3)`'s `:two`, `twoReceivers` loses `fun(n) \| fun(n) is not assignable to fun(p)` at `a:go('y')`'s `:go`) |
| A3 | fresh hub node, per-site members downstream, `Table` names the hub | errors differ: `annotated(n)` loses `n − 1` entries (`fun() \| fun(n) is not assignable to fun()` at every site but the first); `n = 1` closure 82 vs 70 |
| V3 | per-site members, reverse edge dropped for call-site members only | types differ on `chain` (25 elements; the class member's `write` loses a `fun(n)` arm) and `unionReceiver`; errors differ |
| A4 | A3 + replication (design §5.3) | equal on every shape in the catalogue — **but see DR-04**: one corpus file differs, reproduced by the `fileUnion` shape (4 errors vs 3) |
| A5 | A4, `Table` names the site member, wiring redirected to the hub | equal everywhere; **`selfN` regresses**: closure 87 243 → 140 043 at n = 160 (the extra hub node per single-site receiver joins the clique); wall-clock 3 088 → 3 947 ms in run 4 |
| **A6** | A5 with the first site member serving as hub (design §5) | **equal on every shape and size**; `annotated(1)` identical closure (70); `selfN`/`dotN` identical to `BASE` |

Shape catalogue (all in the probe, all equal under A6): `param` (requirements' BUG-419 fixture),
`selfCalls`, `nilReceiver`, `functionReceiver`, `unannotated`, `callBeforeDecl`, `inheritance`,
`fieldFunction`, `chain`, `twoReceivers`, `generic`, `arity`, `unionReceiver`, `fileUnion`,
`fileUnionStdlib` (`io.open` from the bundled stubs), `builderCorpus` (the DR-6 corpus fixture's
shape; `builderFixture` in design Appendix C), `annotated(n)` and `paramN(n)` (`---@param n string`,
alternating string/number arguments) at n = 20 / 40 / 80 / 160, `annotated(1..3)`, `selfN(n)`,
`dotN(n)`.

A6 on `annotated(n)`, same run as DR-01's `BASE` rows:

| n | closure A6 | ms A6 | errors vs `BASE` |
| ---: | ---: | ---: | :-- |
| 20 | 583 | 13 | equal (83) |
| 40 | 1 123 | 15 | equal (163) |
| 80 | 2 203 | 20 | equal (323) |
| 160 | 4 363 | 50 | equal (643) |

A6 closure ×1.93 / ×1.96 / ×1.98 per doubling. `paramN(160)`: 83 272 → 6 316, 3 293 → 46 ms, 568
errors equal; its ERROR-tier entries, identical under both, are `number is not assignable to string`
at `(100,101)` (`LuaNameRefImpl` `n`) and, at `(75,118)` (`LuaFuncDeclImpl`), `number is not
assignable to string`, `string | number is not assignable to string` and `{ ... } | Builder is not
assignable to Builder`; each per-argument conflict (e.g. `(163,164)`) is HYPOTHESIS. Node count is
identical to `BASE` at every size (2 107 at n = 160): A6 mints no extra node. On multi-site shapes
with little or no class component, A6's closure is slightly **higher** than `BASE`'s (design §7):
`nilReceiver` 8 → 11, `functionReceiver` 12 → 15, `unannotated` 38 → 44, `callBeforeDecl` 76 → 79,
`fileUnion` 171 → 177, `fileUnionStdlib` 499 → 511; errors and types are equal on each. The BUG-473 budget fixtures
(`annotated(80)` and the un-annotated `colon80`, measured on a fresh `forFile` without rendering) read
572 / 647 / 325 and 163 / 82 / 2 root resolutions under both `BASE` and A6 in runs 5 and 7 —
unchanged, which is why TYPE-12-07 is not done.

**Control**: the variants that must differ did — A1, A2, A3, V3 on the shapes above, and every
mutation in DR-06. A comparator that could not see a difference would have reported A3 equal.

### DR-03 — M-C under the chosen design

Variant A6MC = A6 with `checkTableCompatibility`'s reverse edge dropped (runs 5 and 7). `param` (the
`LuaAnnotatedClassDiagnosticsTest` fixture): 13 errors → 3; `annotated(20)` 83 → 60; `chain` 36 → 5,
with 27 elements' types changed. M-C stays red under the hub.

### DR-04 — corpus-wide differential

`tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"`, the whole
ordinary suite plus the corpus sweeps, the annotated fixture lane and the parity test, each snapshot
built several ways:

| run | variants built after `BASE` | builds compared per variant | error-list mismatches | type mismatches |
| :-- | :-- | ---: | :-- | :-- |
| 1 | A4, A3 | 4 517 / 4 515 | **A4: 8 builds** (all `luacheck/cache.lua`), A3: 23 builds | 0 / 0 |
| 2 | A6, A6NF | 4 695 / 4 693 | **A6: 0**, A6NF: 13 builds | 3 / 3 (2 builds each) |
| 3 | BASE2, A6, BASE3 | 4 863 / 4 861 / 4 861 | **all 0** | BASE2 402 (1 build), **A6 402 (1 build)**, BASE3 513 (3 builds) |

- Run 1's A4 mismatch is one extra `HYPOTHESIS|File | nil is not assignable to { ... }` on
  `fh:read("*a")` at `luacheck/src/luacheck/cache.lua:120` (offset 3375–3380), the third call on `fh`
  after two `fh:read()`. A4's per-site `Table` named the hub, whose `read` is the first site's
  0-argument call; the third site's 1-argument shape was checked against it. That is what A5/A6 fixed
  (design §5.1 step 5), and the `fileUnion` shape reproduces it in the unit lane (DR-02, A4 row). Run 1's A3
  mismatches include `src/test/resources/corpus/annotated/builder.lua` (87 → 48 errors, the later
  `:setName` sites' value-anchored entries missing) — A3 has no replication, so this is the loss
  §5.3 restores.
- **Run 2's type mismatches are one cross-file global.** The sampled A6 differences are:
  `StylesGetMarker` in `zerobrane/src/editor/commands.lua` (offset 381) and in
  `zerobrane/src/editor/package.lua` (offset 47425), `undefined` in the `BASE` build and
  `fun(marker)` in the A6 build; and `function ide:GetMarker(marker)` in `package.lua`, whose
  parameter `marker`'s `read` goes from `any` to `undefined`. `StylesGetMarker` is a global defined
  in another file (`zerobrane/src/editor/style.lua:177`), and `ide:GetMarker`'s whole body is
  `return StylesGetMarker(marker)` (`package.lua:1254`), a declaration this design does not change,
  whose parameter's `read` follows that global's resolution. So every sampled difference is the
  global resolving differently in the first build of a file (`BASE`) and in a later one (A6), the
  same direction as run 3's control.
- **Run 3 is the control.** `BASE2` and `BASE3` build today's code again. They report type
  mismatches too, and `BASE2`'s count and sampled elements (`commands.lua` `local ide = ide` and its
  references, typed `any` in the first build and as the `ide` table in later ones) are identical to
  A6's. The type mismatches are the harness's own artifact — a cross-file global resolves differently
  in the first of several consecutive builds of one file — not the representation. A6's type-mismatch
  count equals the control's in the same run; its error-list mismatch count is zero in both runs.
- The harness reports the samples as the first three mismatching elements per build, so "identical"
  above is established for the counts and the sampled elements, not element-by-element for all 402.
- These tests fail in every differential run and pass in DR-07, which runs the same suite without
  the harness: every `LuaTypeGraphRootResolutionBudgetTest` method,
  `TargetTenDrSpikeTest.testDr04cSizeVersusCandidateCount` and `testDr04dNamespaceMembersOnlyInRoot`,
  and `LuaColonCallCrossFileFanOutTest.crossFileFanOutStaysLinearInCallSites`. The likely reason —
  not separately measured — is that the comparison renders every element's type, resolving
  `write`/`read` at roots the budget tests count, and multiplies snapshot work for the size-sensitive
  tests.
- The prototype counted colon call sites and distinct `(receiver, name)` keys on every build of run 3
  (623 689 / 424 631, each build of a file counted once per variant). The ratio is what matters: about
  a third of all colon call sites the suite and corpus build are a later site of some hub.

### DR-05 — residual shapes

Run 7. `selfN(n)`: n methods each calling `self:inc(i)`. `dotN(n)`: n `print(b.name)` on a
`---@class` with `---@field name string`.

| n | `selfN` closure (`BASE` = A6) | `selfN` ms | `dotN` closure (`BASE` = A6) | `dotN` ms |
| ---: | ---: | ---: | ---: | ---: |
| 20 | 2 543 | 23 | 1 035 | 31 |
| 40 | 7 443 | 83 | 3 655 | 24 |
| 80 | 24 443 | 494 | 13 695 | 125 |
| 160 | 87 243 | 3 844 | 52 975 | 1 027 |

Both quadratic, both untouched by A6, both collapsed by M-C (`selfN(160)` 5 144, `dotN(160)` 1 293) —
i.e. the same reverse-edge clique. Out of scope (design §2).

### DR-06 — single-fixture test-case mutations, executed

Probe variants, run 7 (`probe7.txt`), plus the DR-11 step 8 goldens where a row says so. For each
mutation of implementation plan Phase 1, the value each single-fixture case's fixture produced. A
case is red when that value differs from its Then. *P*, *T*, *F* are `LuaAnnotatedClassDiagnosticsTest`'s
three methods, whose fixtures are `param`, `annotated(20)` and `annotated(40)` and which pin today's
per-`severity|message` counts: a method is red when its fixture's error total differs from today's
(the counts cannot then all match), green when its error list equals today's. Those three verdicts
are derived from measured lists, not from running the test class under the variant; Phase 1 runs it.

| mutation | variant | observed on the cases' fixtures | red |
| :-- | :-- | :-- | :-- |
| (a) per-site minting (today's code) | BASE | DR-01's closures at n = 40 / 80 / 160; every other value is today's | case 1 |
| (b) no replication | A6NF | `annotated(3)` 15 → 13; `chain` 36 → 32 (the entries at `(139,143)`, `(167,171)`), types equal; closure 1 123 / 2 203 / 4 363; `annotated(1)` 70; `annotated(20)` 83 → 64, `annotated(40)` 163 → 124; `param`, `pipeUnion` unchanged (DR-11 step 8) | cases 2, 3, 4; *T*, *F* |
| (c) M-C | A6MC | closure 581 / 1 141 / 2 261; `annotated(3)` 15 → 9; `chain` 36 → 5, and `q`, `r`, `r:set(1)` all `Bd`; `annotated(1)` closure 70 → 35; `param` 13 → 3; `annotated(20)` 83 → 60; `annotated(40)` 163 → 120; `pipeUnion` `[errors]` unchanged (DR-11 step 8) | cases 1, 2, 3, 4, 6, 7; *P*, *T*, *F* |
| (d) `Table` names `hub ?: siteMember` | A6TH | `fileUnion` gains `HYPOTHESIS\|File \| nil is not assignable to { ... }\|(278,283)` (on `pipeUnion`: DR-11 step 8); closure 1 123 / 2 203 / 4 363; `annotated(1)` 70; `chain` errors and types equal; `annotated(20)`, `annotated(40)` equal; `annotated3`, `param` goldens green (DR-11 step 8) | case 5 |
| (e) no `addEdge(hub, siteMember)` | A6NE | `chain`: `q`, `local q = …`, `b:set('z')` and `b:set('y'):set(2)` become `undefined`, errors equal; closure 226 / 386 / 706; `annotated(1)` 70; `annotated3` golden `[errors]` −4 (DR-11 step 8); `annotated(20)` 83 → 45, `annotated(40)` 163 → 85; `param`, `pipeUnion` goldens green (DR-11 step 8) | cases 1, 2, 3, 6; *T*, *F* |
| (f) separate hub node even for a first site | A5 | closure 1 135 / 2 215 / 4 375; `annotated(1)` closure 82 (A6 and `BASE`: 70); errors and types equal on every catalogue shape (DR-02) | cases 1, 7 |

### DR-07 — the chosen design as the engine

`tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"` (run 9) with A6
as the engine for every snapshot (the prototype's `ThreadLocal` default set to A6) and the
differential off: `BUILD SUCCESSFUL in 15m 36s`; result XMLs sum to **3 121 tests, 0 failures,
0 errors, 1 skipped**, all timestamped by this run. Present and green among them:
`LuaCorpusSweepTest` (4), `LuaTortureCorpusTest`, `LuaInspectionParityTest`,
`LuaAnnotatedFixtureSweepTest` (2), `LuaAnnotatedClassDiagnosticsTest` (3),
`LuaTypeGraphRootResolutionBudgetTest` (3), `LuaTypePairWriteDifferentialTest` (2) — and the tests
that fail only under the differential harness (DR-04) pass. No `IMPROVED` or `REGRESSED` line in the
log; the corpus ratchet compares against the committed `.baseline` files in-test and never rewrites
them without `-PrecordCorpusBaseline`.

**What this does not establish.** No test in the suite distinguishes A6 from today's code, so the
run does not by itself prove A6 was the engine. Corroboration: the prototype source kept from the
run (`…/type-12/Type12Probe.kt`) sets the default to A6 and `diffVariant = null`, its modification
time equals the end of run 9, and run 9's log shows `:compileKotlin` re-executed. The result XMLs
reproduce the totals above, all timestamped 17:03–17:18Z.

### DR-08 — where replicated errors come from

The prototype counted, for every replication event, the use node's class, its element's class and
whether that element is file-wide. The counter is JVM-wide. It fires in any variant that records hub
sites — A4, A5, A6, A6MC, A6NE and A6TH, not A6NF — and in any build in the same JVM, not only the
probe's. Run 7 read `{UseElement/LuaFile/fileWide=true=90}`. That total covers every catalogue shape
and size under those variants. It may also include `testZerobraneCorpus`'s builds in the same JVM, if
the prototype's default variant was A6-family during run 7, which the run does not record. The
conclusion holds over the union either way: every counted replication had one source kind, a use
anchored on the `LuaFile`. The design attributes that use to the synthetic declared use
`LuaGraphType.memberNodeFor` mints on `graph.firstNodeElement()`; that attribution is from reading,
because the counter did not record the mint site.

### DR-09 — the golden gate on today's engine (executed: step 3 refuted)

**Why**: the gate (design §4.6, §5.5) is only a gate if it is deterministic, matches the
requirements' unsalted fixtures, and turns red under the mutations requirements cases 9 and 10 name.

**Steps**, in a scratch branch off `40ae01de`, snapshotting and restoring per skill
`temporary-edits`; each step's command is in the result below. "Design §5.5" and "Appendix C" here
mean the design at `40ae01de`, which recorded into `src/` and listed `fileUnion` among the shapes.
Step 7 applied `…/type-12/prototype.diff` and copied `…/type-12/Type12Probe.kt` into
`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/` (`diffVariant = null`, default variant
`A6` via `ThreadLocal.withInitial`).

**Deciding observations**: steps 3 and 4 green; step 5 identical; step 6 holds; step 7 shows `A6`:
every golden green; `A6NF`: `annotated3`, `annotated20`, `chain`, `annotated-builder` red in
`[errors]`; `A6TH`: `fileUnion` and `luacheck-cache` red; `A6NE`: `chain` red in `[elements]`.

#### DR-09 result

Executed on the libvirt builder (`debian13`) on `plan-type-12` at `40ae01de`, uncommitted. Every
log (`…/type-12/dr09-*.log`) opens with `git rev-parse HEAD` = `40ae01de03f39b30388a3fbece69b55293339e96`
and a `git status --short` listing ` M build.gradle.kts`, ` M …/LuaTypes.kt` and `??` for the four
Phase 0 test sources; later logs add `?? src/test/resources/types-golden/`, and step 7's add the
prototype's files.

**Step 1.** Phase 0 built as design §4.4, §4.6, §5.5 and Appendix C specify: the two snapshot
accessors, the four test sources, the two `build.gradle.kts` additions. Nothing else. 31 goldens: 27
shapes (Appendix C's 25 plus `annotated-builder`, `annotated-shapes`) and 4 member files.

**Step 2.** `tooling/gce-builder/gce-builder.sh run "test --rerun -PrecordTypeGolden --tests '*LuaTypeGolden*'"`
→ `BUILD SUCCESSFUL in 34s`. Both result XMLs report `failures="0"`, and their stdout lists
`[type-golden] recorded <path>` for each golden. The first recording attempt was lost before the copy
back. Another session synced its worktree into the builder's checkout between the run and the copy,
and the untracked `types-golden/` was deleted (design §2, recording row). The recording cited here was copied back
with no other build on the builder in between.

**Step 3 — red.** `tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*LuaTypeGolden*'"`:

```
LuaTypeGoldenShapesTest > everyShapeMatchesItsGolden() FAILED
2 tests completed, 1 failed
BUILD FAILED in 32s
```

```
golden shapes/fileUnion.golden: line 9: expected '(15,30)|LuaLocalVarDeclImpl|{ ... } | File|5b2b446e729c9856' actual '(15,30)|LuaLocalVarDeclImpl|{ ... } | File|cd5e4ba67529a21c'
```

`LuaTypeGoldenMemberFilesTest` passed, and so did every other shape. The rendered files (written to
`build/type-golden/actual/` by `mismatch`) were copied back and diffed with the goldens. Only
`fileUnion.golden` differs, and only in `[elements]`. 15 lines change their hash: every element whose
type contains `File` (`(15,30)` and `(21,25)` typed `{ ... } | File`, and 13 lines typed `File | nil`). Every
`displayName()` column, `[errors]`, `[globals]` and `[fileReturn]` are identical.

With a diagnostic file of the unhashed `renderType(t, 2) + " :: " + renderLua(snapshot, t)` per
element written next to `actual/` (the golden and its comparison unchanged), two more runs gave one
pass and one fail; `File`'s members are the same set in a different order (`(15,30)`, abridged):

```
pass: File[read:…,write:…,lines:…,flush:…,seek:…,close:w=fun() | fun()/r=fun(),iseof:…]
fail: File[read:…,close:w=fun() | fun()/r=fun(),write:…,lines:…,flush:…,seek:…,iseof:…]
```

The `renderLua` half has the same reordering. `write`, `lines`, `flush`, `seek`, `iseof` and their
`sourceElement` ranges (`(1523,1551)` …) come from the bundled `io.lua` stubs' `---@class File`, so
`File` is two classes merged across files: the fixture is configured alone but does not resolve
alone. `materializeClass` (`LuaTypeManagerImpl.kt:344`) inserts members into a `LinkedHashMap` in
`decls` order; that the index returns the two declarations in a JVM-dependent order is from reading,
not instrumented (BUG-498).

| JVM | command | test class order | `fileUnion` |
| :-- | :-- | :-- | :-- |
| step 2 (recording) | record | — | the golden (stdlib order) |
| step 3, run 1 | step 3 | not kept | **differs** |
| step 3, run 2 | step 3 | shapes first | **differs** |
| step 3, run 3 | step 3 | shapes first | equal |
| step 4 | full suite | member files first | **differs** |
| step 7 A6 | step 3 | shapes first | equal |
| step 7 A6NF | step 3 | member files first | **differs** (`[elements]` only) |
| step 7 A6TH | step 3 | shapes first | equal in `[elements]` |
| step 7 A6NE | step 3 | member files first | **differs** (the same alternative rendering) |

Each time, the alternative rendering is byte-identical to step 4's. The order column is correlation
only.

**Step 4 — red, for the same golden only.** `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`:

```
LuaTypeGoldenShapesTest > everyShapeMatchesItsGolden() FAILED
3112 tests completed, 1 failed, 1 skipped
BUILD FAILED in 8m 24s
```

The one failure is the step-3 `fileUnion` message, verbatim. `LuaTypeGoldenMemberFilesTest`
passed (`failures="0"`). Of the 31 renderings copied back, 30 are byte-equal to their goldens, and
`fileUnion` is the alternative rendering above. No other test in the ordinary suite failed. So
nothing the shared light project leaks from earlier tests moved any golden here.

**Step 5 — identical.** The `[errors]` lines with their last field (the PSI class) dropped are equal
to the requirements, line for line and in the same order. `annotated3`'s 15 lines match Appendix A.
`chain`'s 36 lines match Appendix B. `fileUnion` has exactly:

```
HYPOTHESIS|File | nil is not assignable to { ... }|(178,180)
HYPOTHESIS|File | nil is not assignable to { ... }|(183,192)
HYPOTHESIS|File | nil is not assignable to { ... }|(224,229)
```

Requirements case 5 lists the same three. The unsalted recordings agree with the salted probe on
all three.

**Step 6 — holds.** `member-files/luacheck-cache.golden` `[errors]` has four lines. None is at
`(3375,3380)`:

```
HYPOTHESIS|File | nil is not assignable to { ... }|(3110,3112)|LuaNameRefImpl
HYPOTHESIS|File | nil is not assignable to { ... }|(3115,3144)|LuaFuncCallImpl
HYPOTHESIS|File | nil is not assignable to { ... }|(3193,3198)|LuaMethodExprImpl
HYPOTHESIS|File | nil is not assignable to { ... }|(3270,3275)|LuaMethodExprImpl
```

The text at `3375–3380` is `:read`, on line 120.

**Step 7 — superseded by DR-11 step 8**, which ran the same variants, and A6MC and A5, on the
current shape set. Logs: `dr09-7-*.log`, renderings `out-7-*`.

**Control.** In step 3 the gate failed on today's engine with no prototype applied, so the
comparison is live without a variant.

**Verdict.**

- Step 3 and step 4 are red → **the determinism premise is refuted** for `fileUnion`. The design
  changes the fixture, not the rendering (design §2; Risk 1.5), and DR-11 measures that change.
  `fileUnion`'s `[errors]` matched the recording in every run of today's engine and under A6, A6NF
  and A6NE. Only its `[elements]` hashes are unstable.
- Step 5 identical: Appendix A, Appendix B and case 5 stand as written.
- Step 6 holds.

The probe, the harness, the prototype and the recorded goldens were removed after the run
(`git status --short` empty before this file was edited).

### DR-10 — the flags' inputs at later sites (executed: equal output; reach not shown)

**Why**: design §5.3 changes the up-set over which `certain` and `unknownProvenance` are computed for
a later site (Risk 1.6). DR-02 measured the receivers with no class member. The shapes that put a nil
or unknown write in the member's reaching definitions — `nilMember`, `unknownMember`,
`declaredFieldNil` (design Appendix C) — are what this task adds.

**Steps**: DR-09 step 7 with default `A6` covers them — they are in the golden set.

**Deciding observation**: `shapes/nilMember.golden`, `shapes/unknownMember.golden` and
`shapes/declaredFieldNil.golden` green under the prototype's A6.

#### DR-10 result

Decided by DR-09 step 7's A6 run (same commit and log header as DR-09):
`tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*LuaTypeGolden*'"` with the
prototype's default at `A6`:

```
> Task :compileKotlin
BUILD SUCCESSFUL in 43s
```

Both result XMLs report `tests="1" skipped="0" failures="0"`. The copied-back renderings of
`shapes/nilMember.golden`, `shapes/unknownMember.golden` and `shapes/declaredFieldNil.golden` are
byte-equal to the goldens recorded from today's engine, and so are the other 28.

DR-11 step 8's A6 run, at `c06a71a4`, again rendered all 31 goldens byte-equal.

**Control**: the A6NF, A6TH and A6NE runs of DR-09 step 7 (`dr09-7-*.log`) use the same prototype
and default mechanism and redden other goldens, so the A6 run had the hub as its engine. None of
those variants reddened any of the three shapes.

**Verdict: equal output, reach not shown.** On these three fixtures, A6's errors, element types,
globals and file return type equal today's. Whether they exercise the `certain`/`unknownProvenance`
change is unproven; Risk 1.6 accepts that with reasoning and names the variant (A6XF) that would
test it. DR-10 does not block implementation.

### DR-11 — the response to DR-09 (executed: every deciding observation holds)

**Why**: the design's response to DR-09 — `pipeUnion`, the `build/` recording path, the stub-name
guard — had not run on the builder, and neither had A6MC and A5 on the goldens.

**Part A — executed locally: what `cmd_sync`'s rsync deletes.** The same options as
`gce-builder.sh:141-142`, without the ssh transport, `rsync 3.4.1`. The sender is a tree holding
`.gitignore` (`/build/`) and `src/test/resources/keep.txt`. The receiver holds
`build/type-golden/recorded/shapes/fileUnion.golden` and
`src/test/resources/types-golden/shapes/fileUnion.golden`. Scratch:
`/home/mini/.cache/claude-scratch/lunar/6726d4f8/type-12/rsync-probe/`.

```
$ rsync -az --delete --itemize-changes --filter=':- .gitignore' --exclude='/.git/' --exclude='/test' src/ dst/
# receiver has no .gitignore yet (output abridged: the deletions of the intermediate directories are omitted):
*deleting   build/type-golden/recorded/shapes/fileUnion.golden
*deleting   build/
>f+++++++++ .gitignore
*deleting   src/test/resources/types-golden/shapes/fileUnion.golden
# receiver already has .gitignore (the builder checkout's steady state), build/ file recreated:
.d..t...... ./
$ find dst -type f
dst/.gitignore
dst/src/test/resources/keep.txt
dst/build/type-golden/recorded/shapes/fileUnion.golden
# untracked src/test/resources/types-golden/shapes/fileUnion.golden recreated, same command:
*deleting   src/test/resources/types-golden/shapes/fileUnion.golden
```

So the receiver's own `.gitignore` decides what `--delete` spares. Once the receiver has one, an
ignored `build/` survives and an untracked path under `src/` does not. That untracked case is the
control, and it reproduces DR-09's lost recording. The builder checkout always has a `.gitignore`,
because every sync ships one. Part B step 3 confirms this on the builder itself. Also executed:
`rsync` from a missing source directory exits 23 (`change_dir … failed: No such file or directory`),
which is design §5.5 check 2.

**Part B — steps**, on the builder, in a scratch branch off `c06a71a4`, snapshotting and restoring
per skill `temporary-edits`. Step 1 implements Phase 0's code tasks as the design at `c06a71a4`
specifies; steps 2–9 are named, with their commands, in the result.

**Deciding observations**: step 4, design §5.5 checks 1–3 hold after step 3's sync; steps 5 and 6,
every run green; step 7, exactly case 5's three lines; step 8, `A6` and `A5` redden nothing, and
each other variant reddens at least the goldens of the single-fixture cases it mutates (`A6NF`:
`annotated3`, `chain` `[errors]`; `A6NE`: `chain` `[elements]`; `A6TH`: `pipeUnion`,
`luacheck-cache`; `A6MC`: `param`, `annotated3`, `annotated20`, `chain` `[errors]`), its full set
recorded here; step 9, `noShapeDeclaresAStubTypeName` red with `shapes/fileUnion.golden: File`.

#### DR-11 result

Executed on the libvirt builder (`debian13`) on `plan-type-12` at `c06a71a4`, uncommitted. Every
run held the builder lock from its sync to its last copy back, and steps 2–4 ran under one hold.
Logs and copied-back renderings: `/home/mini/.cache/claude-scratch/lunar/6726d4f8/type-12/dr11-*`;
the recording, the harness and its tracked-file diff are kept in `…/type-12/dr11-kept/`. Every log
opens with `git rev-parse HEAD` = `c06a71a468253693e85c432faeef367de3129ecd` and a
`git status --short` listing the same Phase 0 files as DR-09's; from step 4 on it adds
`?? src/test/resources/types-golden/`, and step 8's add the prototype's files.

**Step 1.** Phase 0 built as the design at `c06a71a4` specifies: the two snapshot accessors (§4.4),
the four test sources and the two `build.gradle.kts` additions (§4.6). `mismatch` records into
`build/type-golden/recorded/` (§5.5). `noShapeDeclaresAStubTypeName`, `declaredTypeNames` and
`stubTypeNames` follow §4.6 and §5.6. `NAMED_SHAPES` has `pipeUnion` in place of `fileUnion`
(Appendix C). 31 goldens: 27 shapes and 4 member files. Design §5.6's `TYPE_TAG` regex, run with
`grep -P` over `src/main/resources/runtime`: the stubs declare `any bit bit32 boolean cjson cmsgpack
coroutine debug File function _G io math nil number os package redis self server string struct table
thread userdata utf8 void`, and no `@alias` at all. Over the shapes: `Ar Base Bd Builder C D G K LB P
Pipe U1 U2` (Appendix C) and `LunarFixtureBox LunarFixtureBuilder LunarFixtureShape
LunarFixtureUnit` (`corpus/annotated/*.lua`). The intersection is empty.

**Step 2 — recorded.** `tooling/gce-builder/gce-builder.sh run "test --rerun -PrecordTypeGolden --tests '*LuaTypeGolden*'"`
→ `BUILD SUCCESSFUL in 39s`. Shapes XML `tests="2" skipped="0" failures="0"`, member-files XML
`tests="1" skipped="0" failures="0"`. The two XMLs hold 31 `[type-golden] recorded` lines. The log
holds 0, so design §5.5 check 1 reads the XMLs.

**Step 3 — `build/` survives a sync on the builder.** On the builder: `.gitignore:2` is `/build/`.
An untracked control file `src/test/resources/dr11-control.txt` was created there as user
`builder`. Then `tooling/gce-builder/gce-builder.sh sync` ran from this worktree, which has no
`build/`:

```
== before sync: recorded file count; control present?
31
-rw-r--r-- 1 builder builder 0 Oct 10 18:39 /home/builder/lunar/src/test/resources/dr11-control.txt
== sync exit 0
== after sync: recorded file count
31
== after sync: control file (expected deleted)
ls: cannot access '/home/builder/lunar/src/test/resources/dr11-control.txt': No such file or directory
```

The 31 `sha256sum` lines under `build/type-golden/recorded/` before and after the sync are
byte-identical (`dr11-3-before.sha`, `dr11-3-after.sha`). The control shows the sync's `--delete`
ran and removes untracked paths under `src/`, which is how DR-09 lost its recording.

**Step 4 — checks 1–3 hold, check 1 read from the XMLs.**
`rsync -az builder:/home/builder/lunar/build/type-golden/recorded/ src/test/resources/types-golden/`
→ exit 0 (check 2). `git status --short -uall src/test/resources/types-golden` lists exactly the 31
`goldenPath`s and nothing else (check 3). The local files' SHA-256s equal the builder's. Check 1:
`BUILD SUCCESSFUL` and 31 recorded lines, counted in the XMLs because the log has none. The 30
goldens shared with DR-09's recording are byte-identical to it. `pipeUnion.golden` equals DR-09's
`fileUnion.golden` with `File` → `Pipe` in every line except the source header and the
`[elements]` hashes, which hash the class name.

**Steps 5 and 6 — green in every JVM.** Four invocations of
`tooling/gce-builder/gce-builder.sh run "test --rerun --tests '*LuaTypeGolden*'"`, then
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"`:

| run | verdict | shapes XML | member-files XML | test class order | renderings vs goldens |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 5, 1 | `BUILD SUCCESSFUL in 33s` | `tests="2" failures="0"` | `tests="1" failures="0"` | member files first | 31/31 byte-equal |
| 5, 2 | `BUILD SUCCESSFUL in 32s` | `tests="2" failures="0"` | `tests="1" failures="0"` | member files first | 31/31 byte-equal |
| 5, 3 | `BUILD SUCCESSFUL in 33s` | `tests="2" failures="0"` | `tests="1" failures="0"` | member files first | 31/31 byte-equal |
| 5, 4 | `BUILD SUCCESSFUL in 33s` | `tests="2" failures="0"` | `tests="1" failures="0"` | member files first | 31/31 byte-equal |
| 6 (full suite) | `BUILD SUCCESSFUL in 8m 11s` | `tests="2" failures="0"` | `tests="1" failures="0"` | member files first | 31/31 byte-equal |

Order is from the XMLs' `timestamp`; member files first is the order under which `fileUnion`
diverged in every DR-09 JVM that ran it. "Renderings vs goldens" compares the copied-back
`build/type-golden/actual/` with the pulled-back goldens, per section.
`member-files/penlight-Date.golden` (Risk 1.5's residual) has SHA-256 `d625ce03…` in this recording,
in DR-09's (`dr09-kept/`), and in every kept `actual/` rendering of both DRs (`out-3b`, `out-3c`,
`out-4`, `out-7-*`, `dr11-*-out`) except A6MC's (`57b291bd…`).

**Step 7 — identical.** `shapes/pipeUnion.golden` `[errors]`, verbatim:

```
HYPOTHESIS|Pipe | nil is not assignable to { ... }|(178,180)|LuaNameRefImpl
HYPOTHESIS|Pipe | nil is not assignable to { ... }|(183,192)|LuaFuncCallImpl
HYPOTHESIS|Pipe | nil is not assignable to { ... }|(224,229)|LuaMethodExprImpl
```

With the last field dropped, these are requirements case 5's three lines, in order.

**Step 8.** `git apply …/prototype.diff`, `Type12Probe.kt` copied in, and its
`ThreadLocal.withInitial` default set in turn to each variant. Each variant ran step 5's command
once, and `build/type-golden/actual/` and both XMLs were copied back in the same lock hold.
`compileKotlin` ran each time (`FROM-CACHE` for the four variants DR-09 compiled, executed for
A6MC and A5), never `UP-TO-DATE`. Counts are changed lines, −removed/+added. The failure messages
name exactly the goldens listed. This table is the home of requirements cases 9 and 10's red sets.

| variant | verdict | goldens red, section (lines) | deciding observation |
| :-- | :-- | :-- | :-- |
| A6 | `BUILD SUCCESSFUL in 37s`; both XMLs `failures="0"` | none: 31/31 byte-equal | holds |
| A6NF | `3 tests completed, 1 failed` (shapes) | `annotated20` errors −19; `annotated3` errors −2; `annotated-builder` errors −39; `builderFixture` errors −11; `chain` errors −4; `paramN20` errors −19; `unionReceiver` errors −3. Member files green | holds |
| A6NE | `3 tests completed, 1 failed` (shapes) | `chain` elements −4/+4 only; `annotated3` errors −4; `annotated20` errors −38; `twoReceivers` errors −3; `annotated-builder` elements −39/+39; `builderFixture` elements −11/+11; `arity` errors −8, elements −16/+16; `generic` errors −6/+1, elements −21/+21; `inheritance` errors −3, elements −12/+12; `paramN20` errors −33, elements −71/+71; `selfCalls` errors −10, elements −14/+14; `unionReceiver` errors −15, elements −20/+20. Member files green | holds |
| A6TH | `3 tests completed, 2 failed` | `pipeUnion` errors +1; `fileUnionStdlib` errors +1; `luacheck-cache` errors +1 | holds |
| A6MC | `3 tests completed, 2 failed` | see "A6MC's full set" below: 18 shapes and 2 member files | holds |
| A5 | `BUILD SUCCESSFUL in 37s`; both XMLs `failures="0"` | none: 31/31 byte-equal | holds |

The lines the cases name, verbatim:

- A6NF `annotated3` loses `HYPOTHESIS|fun() | fun(n) is not assignable to fun()|(105,113)|LuaMethodExprImpl`
  and the same at `(121,129)`. `chain` loses `fun(n) | fun(n) is not assignable to fun(n)` and
  `fun(n) | fun(n) | fun(n) is not assignable to fun(n)`, each at `(139,143)` and `(167,171)`.
- A6NE turns `chain`'s `(138,155)|LuaFuncCallImpl`, `(156,176)|LuaLocalVarDeclImpl`,
  `(162,163)|LuaNameRefImpl` and `(166,176)|LuaFuncCallImpl` from `Bd | Bd | { ... }` to `undefined`.
  `chain`'s `[errors]` does not change.
- A6TH adds `HYPOTHESIS|Pipe | nil is not assignable to { ... }|(278,283)|LuaMethodExprImpl` to
  `pipeUnion` (case 5), `HYPOTHESIS|File | nil is not assignable to { ... }|(162,167)|LuaMethodExprImpl`
  to `fileUnionStdlib`, and `HYPOTHESIS|File | nil is not assignable to { ... }|(3375,3380)|LuaMethodExprImpl`
  to `luacheck-cache` (case 10).

**A6MC's full set.** `[errors]` counts for the four named shapes: `param` 13 → 3, `annotated3`
15 → 9, `annotated20` 83 → 60, `chain` 36 → 5. Every red golden, by section:

| golden | `[errors]` | `[elements]` | `[fileReturn]` |
| :-- | :-- | :-- | :-- |
| `shapes/param` | −10 | −14/+14 | |
| `shapes/annotated1` | −4 | −10/+10 | |
| `shapes/annotated3` | −6 | −14/+14 | |
| `shapes/annotated20` | −23 | −48/+48 | |
| `shapes/chain` | −31 | −27/+27 | |
| `shapes/paramN20` | −47 | −71/+71 | |
| `shapes/annotated-builder` | −44 | −130/+130 | |
| `shapes/annotated-shapes` | −10 | −25/+25 | −1/+1 |
| `shapes/arity` | −7 | −16/+16 | |
| `shapes/builderFixture` | −16 | −46/+46 | |
| `shapes/generic` | −10 | −21/+21 | |
| `shapes/selfCalls` | −7 | −14/+14 | |
| `shapes/twoReceivers` | −7 | −20/+20 | |
| `shapes/unionReceiver` | −38 | −23/+23 | |
| `shapes/declaredFieldNil` | | −13/+13 | |
| `shapes/inheritance` | | −2/+2 | |
| `shapes/pipeUnion` | | −2/+2 | |
| `shapes/selfN20` | | −42/+42 | |
| `member-files/penlight-Date` | −3 | −109/+109 | |
| `member-files/luarocks-zip` | | −49/+49 | |

Green under A6MC: shapes `callBeforeDecl`, `dotN20`, `fieldFunction`, `fileUnionStdlib`,
`functionReceiver`, `nilMember`, `nilReceiver`, `unannotated`, `unknownMember`; member files
`luacheck-cache`, `zerobrane-commands`. `penlight-Date` loses
`HYPOTHESIS|{ ... } | nil | { ... } is not assignable to { ... }` at `(15299,15301)|LuaNameRefImpl`,
`(17998,18000)|LuaIndexExprImpl` and `(18069,18071)|LuaIndexExprImpl`. `annotated-shapes`'
`[fileReturn]` keeps `{ ... }` and changes its hash (`6f7b60a6a9131755` → `7588b5252e50f8eb`).

**Step 9 — red as case 11 says.** Prototype reverted with `git apply -R` and `Type12Probe.kt`
deleted (`git diff --stat`: only `build.gradle.kts` and `LuaTypes.kt`, 6 lines each). The excluded
`fileUnion` entry (Appendix C) was added to `NAMED_SHAPES` without re-enable step 2. Step 5's
command:

```
LuaTypeGoldenShapesTest > noShapeDeclaresAStubTypeName() FAILED
LuaTypeGoldenShapesTest > everyShapeMatchesItsGolden() FAILED
3 tests completed, 2 failed
BUILD FAILED in 36s
```

`noShapeDeclaresAStubTypeName`'s failure message is `shapes/fileUnion.golden: File`.
`everyShapeMatchesItsGolden` fails with `no golden shapes/fileUnion.golden; record with -PrecordTypeGolden`,
because no `fileUnion` golden exists. `LuaTypeGoldenMemberFilesTest` passed.

**Control.** A6 and A5 green prove nothing alone. A6NF, A6NE, A6TH and A6MC came from the same
prototype through the same default and reddened goldens, so the default reached the engine.
Between them they redden `[errors]`, `[elements]` and `[fileReturn]`, and member-files goldens
(A6TH, A6MC). Step 9 shows the guard fails on a collision. Step 3's control file shows the sync's
`--delete` ran.

**Verdict.**

- Step 4 holds: `build/type-golden/recorded/` survived a sync on the builder, and the copy back was
  a separate command. Design §2's recording row stands.
- Steps 5 and 6 green: `pipeUnion` and the other 30 goldens rendered byte-equal in five fresh JVMs.
  The design's response to DR-09 (Risk 1.5) holds on every run made.
- Step 7 identical: case 5 stands as written.
- Step 8: every variant met its deciding observation; the table above and "A6MC's full set" are
  the red sets requirements cases 9 and 10 cite. A6TH reddens `pipeUnion`, so case 5's mutation is
  reachable on its own fixture. A5 and A6 redden nothing.
- Step 9 red with `shapes/fileUnion.golden: File`: design §5.6's guard sees the collision.

The harness, the prototype and the recording were removed after the run, by `git show HEAD:<path> >
<path>` for the two tracked files and deletion of the new ones (`git status --short` empty before
this file was edited).

## Test Case Gaps

- No unit fixture reproduces DR-04's cross-file build-order artifact on purpose, and none should: it
  is a property of building one file several times in a row, which production never does.
- Cross-file inference is outside the golden gate (each golden file is configured alone).
- Requirements case 1 asserts the closure on one receiver only; the residual shapes have no growth
  assertion because they are out of scope.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
