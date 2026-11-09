---
id: "MAINT-55-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-55"
folders:
  - "[[features/maint/55-luacats-conformance-suite/requirements|requirements]]"
---

# MAINT-55: Risks & Gaps

## Premises

| Premise | Kind | Settled by |
|---|---|---|
| LuaLS's diagnostic tests mark expected ranges inline and can be converted mechanically | runtime (what the upstream files contain) | DR-01 — yes, with the exclusions it lists |
| Which LuaLS codes are type checking can be read from LuaLS itself | runtime | DR-01 — its `diagnostic.lua` groups |
| Lunar's type findings are the snapshot's non-hypothesis errors, the same lines the two type inspections highlight | runtime | DR-02 |
| A line-level comparison fits where Lunar anchors its errors | runtime | DR-02 — only when a finding counts on every line its element spans; an argument error on a local function still lands elsewhere |
| A case's result does not depend on the language level | runtime | DR-03 |
| Cases can run one after another in one light project | runtime | DR-04 — **refuted** for distinct file names; holds for one fixed name |
| The `-- E` comment marker does not change what Lunar reports | runtime | DR-05 |
| The whole suite is cheap enough for the routine `run test` | runtime | DR-06 |
| `AuditKinds.classify` assigns a parameter-mismatch case to a MAINT-56 shape | runtime | DR-06 (the prototype used it) |

Constraints treated as chosen, not fixed:
- **LuaLS is the reference.** It effectively defines LuaCATS semantics, and its cases are MIT. Where
  Lunar disagrees on purpose, the ruling is recorded as a deviation (design §2.4), so LuaLS
  never silently overrides a Lunar policy.
- **Line granularity**, as Python's typing conformance suite uses. It is right here because Lunar's
  anchors differ from LuaLS's (DR-02); comparing ranges would make every anchor difference a
  disagreement, and LuaCATS specifies no anchor.
- **One language level (5.4).** LuaLS's own default (`script/config/template.lua:187`), and DR-03
  measured no difference.
- **A one-shot import at a pinned LuaLS commit.** The committed case files are the suite after
  import; moving the pin is a deliberate re-import, never a build step.
- **A ratchet that also tightens** (improvements must be recorded), so a committed table cannot go
  stale on the good side and let a fixed case slip back unnoticed.

## Executed De-risking

All builder runs: libvirt, 2026-11-08, worktree at `514b5cf7` plus the throwaway probe
`src/test/kotlin/.../analysis/Maint55ConformanceProbeTest.kt` (and, for DR-06, its generated
`src/test/resources/maint55probe/*.lua`), command
`~/.cache/claude-scratch/lunar/bin/builder-run libvirt "test --rerun --no-build-cache --tests *Maint55ConformanceProbe*"`,
results read from the JUnit XML's `system-out`. Every run reported `tests="1" failures="0" errors="0"`.
The probe files were deleted after the last run and never committed.

### DR-01 — LuaLS's test format and which codes are type checking (executed)
**How.** Sparse, shallow clone of `LuaLS/lua-language-server` at
`a58fc7b01e1746c46275c71b0e61bfed40a6296d` (2026-10-20): `LICENSE`, `script/proto/diagnostic.lua`,
`script/config/template.lua`, `test/catch.lua`, `test/diagnostics/`. Counted with Python regexes over
each file.

**Result.**
- Harness (`test/diagnostics/init.lua`): `TEST [[ … ]]` per case; `catch(script, '!')` strips
  `<!…!>` and records each range; only the file's own code is compared (`DIAG_CARE`); a case with no
  marker must produce no diagnostic of that code. Other codes on unmarked lines are not asserted.
  The test run sets `castNumberToInteger`, `weakUnionCheck`, `weakNilCheck` to `false`
  (`init.lua:13-15`); some cases run between `config.set(…, true)` and `…, false)`.
- `<?x?>` is not a marker to `catch(…, '!')`, so the three cases that contain it are syntax errors
  that pass vacuously upstream. One `TEST` block in `redundant-parameter.lua` (line 102) is commented
  out (`--TEST [[`) and must not be counted. One marker sits on a `---@cast` doc line.
- No in-scope `TEST` passes a version argument; the default runtime is `Lua 5.4`
  (`template.lua:187`).
- Groups (`script/proto/diagnostic.lua`, `m.register { … } { group = … }`): `type-check` =
  need-check-nil, undefined-field, cast-local-type, assign-type-mismatch, param-type-mismatch,
  cast-type-mismatch, return-type-mismatch, inject-field; `unbalanced` = redundant-value,
  unbalanced-assignments, redundant-parameter, missing-parameter, missing-return-value,
  redundant-return-value, missing-return, missing-fields. Every other group (unused, luadoc,
  codestyle, global, ambiguity, await, redefined, …) is out of scope.

| code | `TEST` blocks | carrying a `---@` tag | config-dependent | foreign marker | doc-line marker |
|---|---|---|---|---|---|
| need-check-nil | 18 | 16 | 0 | 0 | 0 |
| undefined-field | 15 | 14 | 0 | 0 | 0 |
| cast-local-type | 42 | 34 | 4 | 1 | 0 |
| assign-type-mismatch | 52 | 48 | 8 | 2 | 0 |
| param-type-mismatch | 31 | 31 | 9 | 0 | 0 |
| cast-type-mismatch | 2 | 2 | 0 | 0 | 1 |
| return-type-mismatch | 16 | 16 | 0 | 0 | 0 |
| inject-field | 8 | 8 | 0 | 0 | 0 |
| redundant-parameter | 26 (25 live) | 7 | 0 | 0 | 0 |
| missing-parameter | 10 | 8 | 0 | 0 | 0 |
| missing-return-value | 5 | 5 | 0 | 0 | 0 |
| redundant-return-value | 4 | 4 | 0 | 0 | 0 |
| missing-return | 19 | 19 | 0 | 0 | 0 |
| missing-fields | 39 | 39 | 0 | 0 | 0 |
| redundant-value | 2 | 0 | 0 | 0 | 0 |
| unbalanced-assignments | 8 | 0 | 0 | 0 | 0 |

**Branch.** Scope = the whole `type-check` group plus the `unbalanced` codes whose cases carry LuaCATS
tags; `redundant-value` and `unbalanced-assignments` have none and are out (design §2.2). The importer
reads the groups from `diagnostic.lua` and refuses a pin whose groups no longer match (design §3.1),
excludes config-dependent, foreign-marker and doc-line-marker cases, and anchors `TEST` and
`config.set` at line start.

### DR-02 — Where Lunar's errors surface (executed, builder)
**How.** Fourteen LuaLS cases under probe-local ids (`ptm-01` = `param-type-mismatch.lua#1`, `rtm-01`/`-04` = `return-type-mismatch.lua#1`/`#4`, `mp-02` = `missing-parameter.lua#2`, `mf-01` = `missing-fields.lua#1`, …), each configured as its own file;
`LuaTypesSnapshot.forFile(file).getErrors()` printed with line, severity and element; and
`myFixture.doHighlighting()` with `LuaTypeAssignabilityInspection` and
`LuaReturnTypeMismatchInspection` enabled, filtered on their tool ids. Excerpt (5.4):
```
PROBE Lua 5.4 ptm-01 expected=[4]
PROBE   snapshot 2:ERROR:LuaLocalFuncDeclImpl[local function f(x) end]:boolean is not assignable to number
PROBE   snapshot 2:ERROR:LuaNameRefImpl[x]:boolean is not assignable to number
PROBE   shown 2:boolean is not assignable to number
PROBE Lua 5.4 rtm-01 expected=[3]
PROBE   snapshot 2:ERROR:LuaFuncDeclImpl[function F()\n    return true\ne]:boolean is not assignable to number
PROBE   shown 2:boolean is not assignable to number
PROBE Lua 5.4 rtm-04 expected=[8]
PROBE   snapshot 8:ERROR:LuaFuncCallImpl[f()]:number is not assignable to boolean
PROBE   snapshot 7:ERROR:LuaFuncDeclImpl[function F()\n    return f()\nen]:number is not assignable to boolean
PROBE   shown 7:number is not assignable to boolean
PROBE   shown 8:number is not assignable to boolean
PROBE Lua 5.4 mp-02 expected=[6]
PROBE   snapshot 6:WARNING:LuaFuncCallImpl[x(1)]:Too few arguments: expected at least 2, got 1
PROBE   shown 6:Too few arguments: expected at least 2, got 1
```
No case had a `HYPOTHESIS` error, and in every case the highlighted lines equal the snapshot's start
lines. A second run printed each finding's line span: `rtm-01` `2-4`, `rtm-04` `7-9` and `8-8`.

**Result.** The snapshot is the findings source (the inspections partition it exactly,
`TypeErrorClassification.kt:23`). Return errors are anchored on the whole function declaration;
an argument error against a same-file local function is anchored on the parameter, not the call;
one mistake can yield several errors.

**Branch.** A finding counts on every line its element spans (design §3.2). That makes `rtm-01` and
`rtm-04` agree. An error reported only away from the marked line, with an unmarked line reported
instead, is its own outcome, `misplaced`, rather than a miss plus a false report.

### DR-03 — Language level (executed, builder)
**How.** The DR-02 cases run twice in one test, after `CorpusTarget.pin(project, LUA54)` and after
`CorpusTarget.pin(project, LUA51)`. Control: the stdlib root name, printed after each pin.

**Result.** `PROBE-LEVEL Lua 5.4 stdlib=lua-5.4` and `PROBE-LEVEL Lua 5.1 stdlib=lua-5.1`; every
case's snapshot and highlight lines were identical at both levels, including the stdlib call
`table.insert({}, 1, 2, 3)` (silent at both).

**Branch.** No per-case level. Every case runs at 5.4, pinned through `CorpusTarget` because the shared
light project may hold another stored target (`CorpusTarget.kt` KDoc, BUG-515).

### DR-04 — Isolation between cases (executed, builder)
**How.** `POISON` = `---@class Poison\nlocal P = {}\n`; `QUERY` = `---@type Poison\nlocal q = function() end\n`.
Configure `POISON` then `QUERY` under one file name, then under two names; control: both in one file.
```
PROBE2 contamination same=[]
PROBE2 contamination distinct=[2-2:fun() is not assignable to Poison, 2-2:fun() is not assignable to Poison]
PROBE2 contamination control=[4-4:fun() is not assignable to Poison, 4-4:fun() is not assignable to Poison]
```
**Result.** Files configured earlier stay in the light project and their classes resolve in later
files. It changed an outcome: `mf-01` (`---@type A` / `local t = {}` against a class with required
fields) reported nothing when run after other cases that declare a class `A`, and reported
`Missing required field 'x'` and `'z'` when alone.

**Branch.** Every case is configured under the one name `conformance.lua` (design §3.4).

### DR-05 — The comment marker is inert (executed, builder)
**How.** Each DR-02 case configured twice under `form.lua`: with markers deleted, and with markers
deleted and ` -- E` appended to each marked line; finding spans and messages compared.

**Result.** `same=true` for all fourteen.

**Branch.** Case files use trailing `-- E` / `-- E?` comments (design §2.1); they stay valid Lua.

### DR-06 — The whole import, prototyped (executed, builder)
**How.** A scratch converter implementing design §3.1 over the DR-01 checkout wrote one case file per
in-scope code; the probe parsed them (design §2.1), ran each under `conformance.lua` at 5.4, resolved
`arg` by design §3.3, and scored by design §3.2 without deviations.

**Result.** `PROBE3 timing cases=261 ms=11214`, and:
```
PROBE3 TALLY arg.class {agree=3}
PROBE3 TALLY arg.function {agree=2}
PROBE3 TALLY arg.generic {agree=1, missed=1}
PROBE3 TALLY arg.other {agree=2, missed=4}
PROBE3 TALLY arg.primitive {agree=2, misplaced=2}
PROBE3 TALLY arg.record {agree=2, missed=1}
PROBE3 TALLY arg.union {agree=2}
PROBE3 TALLY arity.tooFew {agree=6, false-report=3, missed=1}
PROBE3 TALLY arity.tooMany {agree=14, missed=11}
PROBE3 TALLY assign.local {agree=16, false-report=3, missed=6}
PROBE3 TALLY cast.local {agree=20, misplaced=5, missed=12}
PROBE3 TALLY cast.mismatch {agree=1}
PROBE3 TALLY field.assign {agree=6, false-report=7, missed=4}
PROBE3 TALLY field.inject {agree=3, missed=5}
PROBE3 TALLY field.missing {agree=35, false-report=1, missed=3}
PROBE3 TALLY field.undefined {agree=4, false-report=1, missed=10}
PROBE3 TALLY nil.check {agree=12, missed=6}
PROBE3 TALLY return.count {agree=2, false-report=1, missed=6}
PROBE3 TALLY return.mismatch {agree=12, false-report=3, missed=1}
PROBE3 TALLY return.missing {agree=11, missed=8}
PROBE3 TOTAL {agree=156, missed=79, misplaced=7, false-report=19}
```
Disagreements read case by case (the mechanisms the deviations below and Phase 4's bugs rest
on): unannotated callees are not arity-checked (`redundant-parameter-001` …); a local whose type
was only inferred is not checked on reassignment (`cast-local-type-001`); a possibly-nil value is
used unchecked (`need-check-nil-*`); `{}` against an array type is reported ([[BUG-481]]:
`assign-type-mismatch-001` and others); an assignment to a typed field is silent ([[BUG-44]]:
`assign-type-mismatch-003`); `---@param b integer|nil` is treated as required
(`missing-parameter-009`); `---@return number?` with `return nil` is reported
(`return-type-mismatch-003`); a vararg or multi-value last argument is counted as one
(`missing-parameter-005`, `-006`); stdlib arity is silent (`redundant-parameter-013`, `-018`).

**Branch.** The suite runs in the routine `run test` (design §5.3). The DR-06 table is not the
baseline: Phase 3 records the baseline from the committed suite.

### DR-07 — What each deviation costs on the corpora (executed, builder)
**How.** libvirt builder, 2026-11-09, worktree at `71263c16`. Two runs of
`builder-run libvirt "test -PwithCorpus --rerun --no-build-cache --tests *CorpusSweep*"`, each copying
`build/corpus-findings/<member>.json` off the builder: a control on the unmodified engine, then one run
with all four policies switched to LuaLS's rule. Each switch emits a new finding at `WARNING` whose
message starts with its own tag, so one run separates them; the control below shows they did not
interact. The switches, each following the LuaLS diagnostic at the DR-01 pin:
- `undeclared-arity` (`redundant-parameter.lua`): in `LuaTypeGraph.reportArity`, a call (`LuaFuncCall`
  use) passing more arguments than an undeclared signature's maximum is reported, except when the
  last argument is a call and the maximum is above 0. Too few stays silent, because LuaLS treats an
  unannotated parameter as nullable.
- `inferred-vs-inferred` (`cast-local-type.lua`, `compiler.lua` `hasMarkValue`): a `local` with a
  non-nil initializer and no LuaCATS comment records the initializer's node; after `checkTypes`, each
  later `x = v` whose `v`, minus its nil arms, is not assignable to the initializer's type is
  reported. Unknown types on either side are skipped, and so is assigning `nil`.
- `uncertain-nil` (`need-check-nil.lua`): for a `.f`, `:m` or call whose receiver is a bare local
  (not a file global, a declaration-typed global or an ambient stub global), a reaching nil is
  reported, whether it is a union's nil arm or an uncertain or certain `Nil` value. Lunar's existing
  nil narrowing (`x ~= nil`, `type(x) ==`) applies; truthiness is not narrowed.
- missing `self` (`missing-parameter.lua`, `countParamsOfNode`: `self` is required): a dot call
  with no arguments to a function with an implicit `self` is reported unless a too-few warning
  already stands. It is separate from `undeclared-arity`. `minArity` returns 0 when no required
  parameter follows the receiver (BUG-14), whether or not the signature is declared, so changing
  the tier alone never reaches it.

A builder probe on hand-written snippets checked each switch before the sweep:
`local x = 0; x = true` gave `PROBE-P2 cast-local-type: boolean -> number`;
`---@type table?` with `if t then print(t.x) end` gave `PROBE-P3`, showing truthiness is not narrowed.
For arity, a `local t = {}` with no `@class` never connects `function t:init()` /
`function t.f(a)` to the call `t.init()` / `t.f(1, 2)`, so neither arity rule reaches it. Under
`---@class T` the member reads as `fun(a) | fun()`, and `t.f(1, 2)` is reported twice: once against
`fun()` (at most 0) and once against `fun(a)` (at most 1).

**Result.** Control: 5 tests, 0 failures. In every member, the probe run's non-probe findings were
identical to the control's (luacheck 126, luarocks 215, zerobrane 2389, penlight 100, lazynvim 98).
New reports, counted as distinct sites (`path`, offset). Swept lines from the roots in
`tooling/corpus/corpus.json`: luacheck 21.7k, luarocks 34.3k, zerobrane 60.9k, penlight 17.5k,
lazynvim 13.2k; 147.6k in total.

| policy | total | per kloc | luacheck | luarocks | zerobrane | penlight | lazynvim |
|---|---|---|---|---|---|---|---|
| `undeclared-arity` | 129 | 0.87 | 4 | 20 | 5 | 13 | 87 |
| `inferred-vs-inferred` | 5 | 0.03 | 0 | 0 | 3 | 1 | 1 |
| `uncertain-nil` | 1180 | 7.99 | 33 | 339 | 619 | 86 | 103 |
| missing `self` | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

Samples: `random.Random(55).sample` over the sorted sites, up to 15 each. Classes: real (a defect in
the code), intentional (the code means it, and the report is true under LuaLS's rule), false (Lunar
wrong: LuaLS would not report it, or the claim is untrue).

| policy | sampled | real | intentional | false | precision (real + intentional) |
|---|---|---|---|---|---|
| `undeclared-arity` | 15 | 1 | 1 | 13 | 2/15 |
| `inferred-vs-inferred` | 5 (all) | 0 | 4 | 1 | 4/5 |
| `uncertain-nil` | 15 | 1 | 0 | 14 | 1/15 |
| missing `self` | 0 | — | — | — | — |

Examples:
- `undeclared-arity`. Real: `zerobrane/src/editor/output.lua:521`, `getInputLine(-1)` against
  `local function getInputLine()`. Intentional: `luacheck/spec/samples/unused_secondaries.lua:13`,
  `f(t)` on `local function f() end` (a lint sample). False:
  `lazynvim/lua/lazy/core/handler/event.lua:82`, `M.get_state(ev.event, ev.buf, ev.data)` against
  `function M.get_state(event, buf, data)` ("at most 0"). 113 of the 129 sites read "expected at most
  0", the same `fun()` arm the snippet probe shows. Among the other 16, `penlight/lua/pl/Date.lua:456`
  `parse_date(str, self.us)` against `function parse_date (s)` drops an argument for real. The
  `compat53/init.lua` calls are false: they pass the optional leading thread to locals bound to
  `debug.traceback` / `debug.getinfo`, which LuaLS's stdlib signatures accept.
- `inferred-vs-inferred`. Intentional: `zerobrane/src/editor/findreplace.lua:1315`
  `mismatch = lnum` on `local mismatch = false`; `zerobrane/src/editor/style.lua:520`
  `cfgfn, err = pcall(cfgfn, style)` on `local cfgfn, err = loadfile(config)`. False:
  `penlight/lua/pl/input.lua:133`, `max_id = ids` on `local max_id = 0`, where `ids` is an
  unannotated parameter in the `else` of `type(ids) == 'table'`.
- `uncertain-nil`. Real: `luarocks/src/luarocks/admin/index.lua:180`, `out:write` on
  `local out = io.open(…)` with no check. False: `luarocks/src/luarocks/fetch/git.lua:71`,
  `pd:read` after `if not pd then return nil end`; `zerobrane/src/editor/menu_edit.lua:196`,
  `editor and editor:GetLength()`; `zerobrane/src/editor/findreplace.lua:526`, `self.toolbar`.
  Of the 1180 sites, 281 have `self` as the receiver. Each false sample site has one of these causes: a
  truthiness or early-return guard; a `self` receiver; a nil that reaches an unannotated parameter
  from an in-file caller (LuaLS types that parameter `any`); a local initialized from a field read
  (`local findReplace = ide.findReplace`); an `a and b or c` value; or a local copied from a
  multiple assignment of globals.
- missing `self`. Engine: 0. Syntactic cross-check over the swept roots: no file has a `A.m()` with
  no arguments next to a `function A:m(`. Matching only the method name across each member gives
  six sites (`metric.get()`, `os.time()`, …), and each one resolves to a different function.

**Branch.** The numbers inform the rulings in "Deviations and rulings"; they do not change them.
What they show:
- `undeclared-arity`: switching it adds mostly noise. Most new reports come from the `fun()` arm, so
  this measures that arm rather than the policy.
- `inferred-vs-inferred`: costs almost nothing and finds intentional reuse rather than bugs.
- `uncertain-nil`: adds noise at about eight per kloc unless truthiness and early-return narrowing,
  `self` and parameter typing come first.
- missing `self`: has no instance in the corpora.

## Deviations and rulings

Each row is a Lunar policy that DR-06 shows disagreeing with LuaLS on purpose, with its ruling
(2026-11-09, on DR-07's numbers). Michael Crawford ruled the product questions and delegated the rest
to the supervisor (`lunar.supervisor`). Phase 3 writes one `deviations.json` entry per case id of each
**accepted** row, with that row's `rule`, `expect`, `reason`, `ref` and `decidedBy`, `policy` = the
row's policy and `decidedOn` = `2026-11-09`. A rejected row writes nothing: its cases stay scored as
disagreements and go to Phase 4.

| policy | cases (DR-06 ids) | `rule` | `expect` | `reason` | `ref` | ruling | `decidedBy` |
|---|---|---|---|---|---|---|---|
| `undeclared-arity` | redundant-parameter-001, -004, -005, -006, -008, -009, -010, -011, -021 | `arity.tooMany` | `missed` | arity against a signature nobody declared is a hypothesis, never shown | `LuaTypeGraph.kt:860` | accepted | `lunar.supervisor` |
| `undeclared-arity` (self) | missing-parameter-010 (`t.init()` on `function t:init()`) | `arity.tooFew` | — | — | — | rejected | `Michael Crawford` |
| `inferred-vs-inferred` | cast-local-type-001, -036 | `cast.local` | `missed` | a local whose type was only inferred may be reassigned any type | `BUG-419` | accepted | `Michael Crawford` |
| `uncertain-nil` | need-check-nil-001, -002, -005, -006, -009 | `nil.check` | `missed` | a value that is only possibly nil is used without a check | `BUG-416` | accepted | `Michael Crawford` |
| `luals-syntax` | need-check-nil-017 | `nil.check` | `missed` | `?.` is a LuaLS opt-in nonstandard symbol, not standard Lua | `TARGET-13` | accepted | `lunar.supervisor` |

Evidence and reasons for each ruling:
- `undeclared-arity`: the tier line `LuaTypeGraph.kt:855-860`, and BUG-419 defect 4
  (`LuaInferredArityTierTest` KDoc). 113 of its 129 DR-07 reports are BUG-74 and the rest sampled at
  2/15; re-measure after BUG-74 if the question comes back.
- `undeclared-arity` (self): listed apart because a missing `self` is a likelier bug. A missing `self` is
  an authoring-time assist: shipped code has already had it fixed, so the corpora's zero says nothing
  about its value while typing. Its fix ships with a quick fix that rewrites `t.init()` to `t:init()`
  (design §3.7).
- `inferred-vs-inferred`: BUG-419 (`LuaTypeHypothesisTest` KDoc).
- `uncertain-nil`: BUG-416 (`LuaTypeGraph.kt` "only a CERTAIN nil is a defect"). LuaLS's rule adds 7.99
  reports per kloc at 1/15 precision, about 40% on top of the corpora's current findings. BUG-70..BUG-73
  cover four of the six causes in the false samples; the other two (a local initialized from a field
  read, a local copied from a multiple assignment of globals) are unfiled. Re-measure after the four.
- `luals-syntax`: `x.upper()?.field` uses `?.` safe navigation, an opt-in `Lua.runtime.nonstandardSymbol`
  (`template.lua:228-230`) that LuaLS labels LuaJIT 3.0 syntax; Lunar parses standard Lua only.
  TARGET-13 withdraws it.

## Risks

### R-1: The reference moves
- **Impact:** LuaLS changes a semantic; a re-import flips cases.
- **Likelihood:** medium over a year.
- **Mitigation:** the pin is a commit; re-import is a reviewed change whose diff shows every flipped
  case.

### R-2: Span-based matching over-credits wide anchors
- **Impact:** an error anchored on a whole function counts on any marked line inside it.
- **Likelihood:** medium (return errors, DR-02).
- **Mitigation:** results keep each finding's span, so a reviewer sees what matched.

### R-3: Ranking waits on MAINT-56
- **Impact:** Phase 5 cannot run before `tooling/corpus/rule-weights.json` exists.
- **Likelihood:** certain until MAINT-56 Phase 4 lands.
- **Mitigation:** Phase 5 is gated on that file in the plan; Phases 1–4 do not read it.

## Technical Debt & Future Work
- **Hand-written cases** for LuaCATS features LuaLS's tests do not exercise (generics, operators,
  `@overload` resolution) — a later feature, using `-- E?` where LuaCATS leaves a report optional.
- **Other suites** (EmmyLua analyzer, Luau) — out of scope; Luau's semantics differ.

## See Also
- Requirements: [requirements.md](requirements.md) · Design: [design.md](design.md) ·
  Plan: [implementation-plan.md](implementation-plan.md)
