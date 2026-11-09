---
id: "MAINT-55"
title: "55: LuaCATS Type-Checking Conformance Suite"
type: "feature"
parent_id: "MAINT"
status: "planned"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-55: LuaCATS Type-Checking Conformance Suite

## Overview

The [[MAINT-54]] audit measured how often the type checker misses deliberate type errors in corpus
code. It cannot show whether the checker matches what LuaCATS annotations *mean*, and it does not
measure false positives. This suite answers that per case, in both directions. Each case is a short
Lua snippet whose lines are marked "must report", "may report" or, unmarked, "must stay silent".
The cases are adapted from lua-language-server (LuaLS), whose diagnostic tests effectively define
LuaCATS semantics and are MIT-licensed. Running every case gives a committed table of where Lunar
agrees and where it does not; a ratchet keeps agreement from slipping. Conformance **rules** are
ranked by [[MAINT-56]]'s real-world exposure, and bugs are weighted only through the rules they
explain.

The format follows Python's typing conformance suite (`python/typing`, `conformance/`), the one
cross-implementation standard.

## Scope

### In Scope
- The case format and a light-fixture runner (design §2.1, §3.4).
- A one-shot importer from a pinned LuaLS commit, its scope check and import record (design §3.1).
- Scoring, the committed results table and the ratchet (design §3.2, §3.5).
- The deviations record: the rulings where Lunar disagrees with LuaLS on purpose (design §2.4).
- Filing the remaining disagreements as bugs (design §3.7).
- The rule ranking from MAINT-56's weights (design §3.6).

### Out of Scope
- Fixing any disagreement (the bugs Phase 4 files are planned separately).
- LuaLS codes outside design §2.2: style, unused code, doc-comment validity, globals.
- Hand-written cases and other suites (risks-and-gaps, Technical Debt).
- Any change under `src/main` or to `plugin.xml`.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-55-01 | Case format | M | Not Implemented | Case files parse into cases with must, may and silent lines by design §2.1; malformed headers and duplicate ids are errors. |
| MAINT-55-02 | Seed cases from LuaLS | M | Not Implemented | `import_luals.py` converts every in-scope LuaLS case (design §2.2) at the pinned commit, records each case's source and every exclusion with its reason, and refuses a pin whose diagnostic groups no longer match. |
| MAINT-55-03 | Results table | M | Not Implemented | Every case is run alone at 5.4 and scored `agree`, `missed`, `false-report` or `misplaced` (design §3.2) under a rule (design §2.3); `results.json` is committed. |
| MAINT-55-04 | Ratchet, not gate | S | Not Implemented | The run fails on the conditions of design §3.5 only — never because a known disagreement persists or changes kind. |
| MAINT-55-05 | Disagreements become bugs | S | Not Implemented | Every disagreeing case is filed under exactly one BUG by design §3.7, recorded in `bugs.json`, and the run checks it. |
| MAINT-55-06 | Deviations record | M | Not Implemented | `deviations.json` records each ruling (case, rule, policy, expected outcome, reason, reference, who decided, when); a ruled case scores `deviation`, or `deviation-broken` when Lunar's behaviour no longer matches the ruling. |
| MAINT-55-07 | Isolation and level | M | Not Implemented | Each case runs as the only case file in the light project, at language level 5.4 whatever the project held before (risks-and-gaps DR-03, DR-04). |
| MAINT-55-08 | Attribution | M | Not Implemented | LuaLS's copyright and MIT notice ship with the cases, and `NOTICE` and `THIRD-PARTY.md` name them (design §5.4). |
| MAINT-55-09 | Rule ranking | S | Not Implemented | `conformance_rank.py` ranks rules by MAINT-56 exposure × disagreement rate and weights bugs only through their rules (design §3.6). |

## Behavior Rules
- A finding counts on every line its element spans (design §3.2).
- The findings are the type snapshot's non-hypothesis errors — what the two type inspections show.
- A deviation is added only with a recorded ruling — who ruled and when, in risks-and-gaps "Deviations and rulings";
  a proposal is not a ruling.
- Imported cases are never edited by hand; a re-import at a new pin replaces them. Where Lunar
  disagrees on purpose, the ruling goes in `deviations.json`, not into the case. `-- E?` is for
  hand-written cases (risks-and-gaps, Technical Debt).

## Test Cases

Kotlin cases are in the test classes of design §4; Python cases in `tooling/conformance/test_*.py`.
Each row names the mutation that must turn it red.

| # | Req | Given | When | Then | Mutation that turns it red |
|---|-----|-------|------|------|----------------------------|
| TC-1 | 01 | text: `-- preamble` / `-- ==== case a-001 rule=arg source=a.lua#1` / `local x = 1` / `f(x) -- E` / `g() -- E?` / `` / `-- ==== case a-002 rule=nil.check source=a.lua#2` / `print(1)` | `ConformanceCaseParser.parse(text, "a.lua")` | two cases; `a-001` body of 3 lines, `must = {2}`, `may = {3}`, `text` ends `g() -- E?\n`; `a-002` `must = {}`, `may = {}` | `E?` read as `E` (`must = {2, 3}`) |
| TC-2 | 01 | text with line 3 `-- ==== case b-001 rule=` | `parse(text, "b.lua")` | `IllegalArgumentException` whose message contains `b.lua:3` | a non-matching header line kept as body |
| TC-19 | 01 | text with line 1 `-- ==== case b-002 rule=bogus source=b.lua#2` | `parse(text, "b.lua")` | `IllegalArgumentException` whose message contains `b.lua:1` and `bogus` | rule check removed (parses) |
| TC-3 | 01 | two files under a temp dir, each with a case `c-001` | `ConformanceCaseParser.loadAll(dir)` | `IllegalArgumentException` naming `c-001` | ids collected without a duplicate check |
| TC-4 | 03 | cases and findings: (i) must `{4}`, findings `[2-2]`; (ii) must `{3}`, `[2-4]`; (iii) must `{}`, `[1-1]`; (iv) must `{2}`, `[]`; (v) may `{3}`, `[3-3]` | `ConformanceScorer.observed` | (i) `MISPLACED`, (ii) `AGREE`, (iii) `FALSE_REPORT`, (iv) `MISSED`, (v) `AGREE` | (a) match on `startLine` only: (ii) `MISPLACED`; (b) may lines ignored: (v) `FALSE_REPORT`; (c) both-sets case folded into `MISSED`: (i) `MISSED` |
| TC-5 | 06 | deviation with `expect = MISSED` | `ConformanceScorer.outcome(MISSED, d)`, `outcome(AGREE, d)`, `outcome(AGREE, null)` | `DEVIATION`, `DEVIATION_BROKEN`, `AGREE` | a deviation returning `DEVIATION` unconditionally (second is `DEVIATION`) |
| TC-20 | 06 | deviations `{d1: rule nil.check, d2: rule arity.tooMany}`; resolved rules `{d1: nil.check, d3: arity.tooMany}` | `ConformanceDeviations.check(deviations, resolved)` | in order: `deviation d2 is for an unknown case` | (a) unknown-case check removed (no line); (b) rule compared before the case lookup (a rule line for `d2` instead) |
| TC-21 | 06 | deviations `{d1: rule nil.check}`; resolved `{d1: arity.tooMany}` | `check` | `deviation d1 has rule nil.check but the case resolves to arity.tooMany` | rule check removed (no line) |
| TC-6 | 06 | `deviations.json` fixtures: (i) one entry with `decidedBy: ""`; (ii) the same case twice; (iii) `expect: "agree"` | `ConformanceDeviations.load` | each throws `IllegalArgumentException`, (i) naming `decidedBy`, (ii) the case id, (iii) `expect` | blank check removed: (i) loads |
| TC-7 | 03 | `ConformanceResults` with cases inserted `b` then `a`, one finding `2-4:x` | `render`, then `parse(render(r))` | `"a"` appears before `"b"` in the text; the text ends with `\n`; `parse(render(r)) == r` | `cases` built as a `LinkedHashMap` (`b` first) |
| TC-8 | 04 | baseline `{a: agree, b: missed, c: missed, d: deviation, f: missed, g: agree}`; actual `{a: missed, b: agree, c: false-report, d: deviation-broken (observed agree), e: agree, f: deviation}` | `ConformanceRatchet.violations`, `notes` | violations, in order: `regressed a: agree -> missed`, `improved b: missed -> agree; record`, `deviation broken d: observed agree`, `new case e; record`, `ruling not recorded f; record`, `removed case g; record`; notes: `changed c: missed -> false-report` | (a) improvement row dropped (no `b` line); (b) any change a violation (`c` in violations); (c) removed-case row dropped (no `g` line) |
| TC-9 | 02 | fixture checkout `tooling/conformance/testdata/luals/` with `test/diagnostics/param-type-mismatch.lua`: `TEST [[` block 1 with `f(<!true!>)` on its 4th line; `config.set(nil, 'Lua.type.weakUnionCheck', true)`, block 2, `config.set(nil, 'Lua.type.weakUnionCheck', false)`; `--TEST [[ … ]]`; block 3 containing `<?x?>`; block 4 with `---@cast t <!number!>`; `script/proto/diagnostic.lua` with DR-01's group lists, and a one-block file for each other §2.2 code | `main(["--luals", fixture, "--out", tmp])` | `cases/param-type-mismatch.lua` holds one case, `param-type-mismatch-001 rule=arg source=param-type-mismatch.lua#1`, whose 4th body line ends ` -- E` with no `<!`; `import.json` excluded = `#2 config`, `#3 foreign-marker`, `#4 marker-on-doc-line`, `blocks` 4; a second run gives byte-identical output | `TEST` regex without `^`/MULTILINE (the commented block counts: `blocks` 5, exclusions renumbered) |
| TC-10 | 02 | assign-type-mismatch fixture blocks: (i) `<!t.x!> = true`; (ii) `local <!x!> = 'a'`; (iii) unmarked, last assignment `a[1] = 2` | import | rules `field.assign`, `assign.local`, `field.assign` | field regex without the `\[…\]` alternative ((iii) `assign.local`) |
| TC-11 | 02 | fixture `script/proto/diagnostic.lua` whose `type-check` group adds `'new-check'` | `main([...])` | exit 2, stderr `scope: new-check …` | scope check skipped (exit 0) |
| TC-12 | 07 | `POISON` = `---@class Poison\nlocal P = {}\n`, `QUERY` = `---@type Poison\nlocal q = function() end\n` as cases `p-001`, `q-001`; control case `pq-001` = `POISON + QUERY` | `ConformanceRunner.findings` for `p-001` then `q-001`; then `pq-001` | `q-001` → `[]`; `pq-001` → findings on `4-4` with message `fun() is not assignable to Poison` | file name derived from the case id (`q-001` reports on `2-2`) |
| TC-13 | 07 | `CorpusTarget.pin(project, LUA51)` | `ConformanceRunner.pin(project)` | `PlatformLibraryIndex.getPlatformLibraryFolder(project)?.name == "lua-5.4"` | `pin` writing only `languageLevel` (stays `lua-5.1`, BUG-515) |
| TC-14 | 03 | files: `---@param x number?` / `local function f(x) end` / `f(true)`; `---@param x string[]` / same; `local function f(x) end` / `f(true)` | `ConformanceRules.argRule` | `arg.optional`, `arg.array`, `arg.other` | optional branch removed (first gives `arg.primitive`) |
| TC-15 | 03, 04, 06 | the committed suite and baseline | `run test` | `LuaCatsConformanceTest` green | at `LuaTypeGraph.kt:860` make the tier always `ErrorSeverity.HYPOTHESIS`: `missing-parameter-002` regresses `agree -> missed`, red |
| TC-16 | 08 | the committed resources | `ConformanceAttributionTest` | `LICENSE-LuaLS` contains `Copyright (c) 2018 最萌小汐` and `Permission is hereby granted`; line 1 of every `cases/*.lua` matches `^-- LuaCATS conformance cases, adapted from LuaLS/lua-language-server test/diagnostics/([a-z-]+)\.lua @ [0-9a-f]{8} \(MIT\); see LICENSE-LuaLS$` and group 1 equals the file's base name | importer writes no header line |
| TC-17 | 05 | results `{a: missed, b: agree, c: misplaced}`; bugs `{BUG-1: [a, b]}` | `ConformanceBugs.problems` | `b is filed under BUG-1 but is agree`, `unfiled c` | unfiled check removed (no `c` line) |
| TC-18 | 09 | weights `shapes`: `arity.tooMany` pooled `{exposurePerKloc 10.0, silentRate 0.2, missedPerKloc 2.0}`, `arg.table` `{4.0, 0.5, 2.0}`; results: `arity.tooMany` cases `t1`, `t2` missed and `t3`, `t4` agree; `arg.table` `a1`, `a2` agree; `nil.check` `n1`–`n3` missed; bugs `BUG-1: [t1]`, `BUG-2: [t2, n1, n2, n3]` | `main([...])` | rules in order `arity.tooMany` (rate 0.5000, score 5.0000), `arg.table` (0.0000, 0.0000), `nil.check` (1.0000, `-`); bugs `BUG-1` 2.5000, `BUG-2` 2.5000 | (a) `missedPerKloc` read instead of `exposurePerKloc` (`arity.tooMany` score 1.0000); (b) bug share not divided by `|D_r|` (`BUG-1` 5.0000) |

## Acceptance Criteria
- [ ] Every TC passes; each named mutation, applied by hand, turns its case red, then is reverted.
- [ ] `cases/`, `import.json`, `LICENSE-LuaLS`, `deviations.json` and `results.json` are committed, and
      `results.json` is the recording of the committed cases.
- [ ] Every row of risks-and-gaps "Deviations and rulings" has a ruling, accepted or rejected.

## Non-Functional Requirements
- The suite runs in the routine `run test` (cost: risks-and-gaps DR-06).
- Kotlin follows `docs/engineering-contract.md` (≤ 30 lines/function, ≤ 3 args, no `!!`).

## Dependencies
- [[MAINT-54]] (done): `AuditKinds`, the shape names.
- [[MAINT-56]] (planned): `tooling/corpus/rule-weights.json`, for MAINT-55-09 only.

## See Also
- Design: [design.md](design.md) · Plan: [implementation-plan.md](implementation-plan.md) ·
  Risks: [risks-and-gaps.md](risks-and-gaps.md)
