---
id: "MAINT-54"
title: "54: Type Checking False-Negative Audit"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-54: Type Checking False-Negative Audit

## Overview

Bug reports record wrong diagnostics, not missing ones, so nothing measures what the checker silently
skips (seeds: `detachedDemand` skips table-typed parameters; extra arguments to a declared global are
unreported, [[BUG-19]] §5). This feature injects known type errors into copies of corpus sites, counts
how many are reported per shape, and states whether the result reopens the redesign question in the
[type system survey](../../../type-system-survey.md) (Pass 3, "What would change this verdict").

**The verdict pools every member and every callee path**, bundled runtime stubs (`LIBRARY`) included;
it is also reported per path and per member. Scope and evidence: design §3.6, risks-and-gaps DR-03.

**Out of scope:** fixing any false negative (each becomes a BUG, MAINT-54-08); any change under
`src/main`; parsing LDoc; definition libraries (none enabled, so their calls are unresolved); callees
that do not resolve to a commented `function` declaration, and calls that do not fill every declared
position — both counted (MAINT-54-09), not injected.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-54-01 | **Opt-in run** | M | The audit runs only under `-PwithTypeAudit`. Neither `run test` nor `test -PwithCorpus` runs it. |
| MAINT-54-02 | **Declared-kind classification** | M | Each `---@param`/`---@return`/`---@field` type text is classified into one kind by design §3.1, from the tag text, never from the engine's inferred signature (DR-01). |
| MAINT-54-03 | **Site enumeration and selection** | M | Every eligible site of every shape (design §3.2) is enumerated; at most `CAP` per cell are selected by the even-spacing rule (design §3.3). |
| MAINT-54-04 | **Injection and outcome** | M | Each selected site gets a wrong edit and a compatible control edit, each re-analysed and reverted; the outcome follows the differential and anchor rules of design §3.4; the file text after each revert equals the text before. |
| MAINT-54-05 | **Per-member output** | M | Each corpus member writes `build/type-audit/<member>.json` in the one schema of design §4. |
| MAINT-54-06 | **Summary and verdict** | M | `tooling/corpus/type_audit_summary.py` pools members and prints the tables and the verdict of design §3.6. |
| MAINT-54-07 | **Results document** | M | `audit-results.md` in this folder holds the summary output, the run's commit, and the verdict. The survey's Pass 3 bullet cites it. |
| MAINT-54-08 | **Findings filed** | M | Every cell with a `SILENT` site gets a BUG report and a roadmap row; the results document maps cell → BUG. |
| MAINT-54-09 | **Coverage counts** | S | Per member and pooled, the `AuditCoverage` counters of design §3.2, whose call counters sum to `calls`; LDoc `-- @param` lines are counted and printed as not read by the checker. |
| MAINT-54-10 | **Anchor reporting** | S | Every new error is recorded with its anchor (design §3.4 step 5); the summary counts `REPORTED` sites with no `AT_SITE` entry. |

## Behavior Rules

- Lenient-by-design sites are measured but are not false negatives (design §3.1, §3.6).
- **A hypothesis is not a report**: `ErrorSeverity.HYPOTHESIS` errors are dropped, as both inspections do.
- Mutations happen in the fixture's `temp://` copy, never the on-disk checkout.

## Test Cases

`DEFS` and `KDEFS` are the fixtures in risks-and-gaps DR-01 and DR-01b, added as `defs.lua`/`kdefs.lua`
(OTHER) or prepended to the caller (SAME); `svc.lua` and `lbox.lua` are DR-01d's. "Site `x`→`w`/`c`"
names the edited text, wrong and control. TC-2 to TC-19 are `TypeAuditHarnessTest`; TC-20 is
`test_type_audit_summary.py`.

| # | Req | Given | When | Then | Red under |
|---|-----|-------|------|------|-----------|
| 1 | 01 | clean tree | `run test`; the design §5 command with `-PwithCorpus` in place of `-PwithTypeAudit`; the design §5 command | `TEST-*FalseNegativeAudit*.xml` absent after the first two, present after the third (check timestamps) | delete the exclusion (first run red); move it inside `if (!project.hasProperty("withCorpus"))` (second run red) |
| 2 | 02 | one comment with `---@generic T : string` and params typed `string`, `boolean`, `table`, `table<string, number>`, `{a: number}`, `Box` (declared), `Unknown`, `number[]`, `string\|number[]`, `string\|nil`, `string?`, `fun(x: number)`, `function`, `T`, `any` | `AuditKinds.classify` | PRIMITIVE, PRIMITIVE, TABLE, TABLE, RECORD, CLASS, null, ARRAY, UNION, PRIMITIVE optional, PRIMITIVE optional, FUNCTION, FUNCTION, GENERIC (bound `string`), null | test step 5 before step 3: `string\|number[]` becomes ARRAY |
| 3 | 03 | — | `AuditSelection.pick(10, 4)`, `pick(3, 4)` | `[0, 2, 5, 7]`, `[0, 1, 2]` | take the first `cap`: `[0, 1, 2, 3]` |
| 4 | 03 | OTHER `DEFS`, caller `pTwo('a', 'b')` | `AuditSiteFinder.scan` | exactly `arg.primitive` `'a'`→`true`/`"ctl"`, same for `'b'`, `arity.tooMany` insert `, true` before `)`, `arity.tooFew` delete `, 'b'`; all PROJECT_FILE, GLOBAL | (a) step 3 accepting only a target inside the name element: no sites; (b) no comma in the `tooFew` range |
| 5 | 03 | OTHER `DEFS` + `---@param f fun(a: number, b: number)` `function pFun2(f) end`; caller `pUnion(1)`, `pArray({1})`, `pFun(function() end)`, `pFun2(print)`, `pGen('s')`, `pOpt('s')`, `pClass(Box)`, then `---@return string` `local function r() return 'x' end` and `---@param b Box` `local function f(b) b.n = 1 end` | `scan`, ignoring `arity.*` | `arg.union` `1`→`true`/`7`; `arg.array` `{1}`→`true`/`{}`; `arg.function` `function() end`→`42`/`function() end`; `arg.function` `print`→`42`/`function(p1, p2) end`; `arg.generic` `'s'`→`true`/`"ctl"`; `arg.optional` `'s'`→`true`/`"ctl"`; `arg.class` `Box`→`function() end`/original; `lenient.primitiveForTable` `Box`→`true`/original; `return.mismatch` `'x'`→`true`/`"ctl"` with `site` the `return` statement and `declRange` the function; `field.assign` `1`→`true`/`7` with `site` the assignment | removing any one shape's emission drops its row; union control by first arm: `"ctl"`; fixed `function() end` control for `pFun2` |
| 6 | 03 | OTHER `DEFS` and `svc.lua`; caller `Box:m('a')` and `Svc:run(1, 'a')` | `scan`, ignoring `arity.*` | `arg.primitive` sites `'a'`, `1`, `'a'`, all PROJECT_FILE, METHOD; `unresolvedMethod` 0 | drop step 3's `resolveGlobal` route: no sites, `unresolvedMethod` 2 |
| 7 | 03 | file `lbox.lua`; `m = LuaTypeMember("n", LuaPrimitiveType.NUMBER, sourceElement = <its LuaLocalVarDecl>)`; then caller `---@param b LBox` `local function f(b) b.n = 1 end` | `fieldTag(m, "n")`; `scan` on the caller | the `@field n number` tag; one `field.assign` site, PROJECT_FILE | accept only `sourceElement as? LuaCatsFieldTag`: `fieldTag` null |
| 8 | 09 | file: `pStr 'a'`, `pTable {}`, `string.upper('a')`, `nope(1)`, `pTwo('a')`, `pStr('a')`, `-- @param x number`, plus OTHER `DEFS` | `scan` | `calls` 6 = `stringOrTableArgs` 2 + `unresolvedCallee` 2 + `arityGuard` 1 + `eligible` 1; `ldocParam` 1 | drop the `stringOrTableArgs` count: the sum is 4 |
| 9 | 04 | OTHER `DEFS`, caller `pStr('okay')\npNum('x')` | measure `'okay'`→`true`/`"ctl"` | `REPORTED`; one entry, *boolean is not assignable to string*, `AT_SITE`; text restored | (a) no `commitDocument` after the edit: `SILENT`; (b) `Shift.NONE` for the edited file: `delta` gains the shifted `pNum('x')` error as a `BODY` entry, two entries |
| 10 | 04 | as TC-9 | site `'okay'`→`'other'`/`"ctl"` | `SILENT`, `delta` empty | report every after-error: not `SILENT` |
| 11 | 04 | OTHER `DEFS`, caller `pNum('x')` | measure `'x'`→`true`/`7` | `SKIPPED_PREEXISTING` | drop the step 2 check: `REPORTED` |
| 12 | 04 | OTHER `DEFS`, caller `pTable({})` | `lenient.primitiveForTable` `{}`→`true`/original | `LENIENT_BY_DESIGN` | map silent lenient sites to `SILENT` |
| 13 | 04, 10 | SAME `KDEFS` + `kNum(1)` | `arg.primitive` `1`→`true`/`7` | `REPORTED`; `delta` anchors exactly `AT_DECL`, `AT_PARAM`, `BODY` (`n + 1`) | anchor every non-site entry `AT_PARAM`: no `BODY` |
| 14 | 04 | (a) as TC-13 with control `'x'`; (b) a site and `outcome(site, delta = [AT_SITE, expected rule], noise = [BODY])` | measure; `outcome` | (a) `NOISY`; (b) `REPORTED` | (a) ignore `noise`: `REPORTED`; (b) any `noise` entry is `NOISY` |
| 15 | 04 | OTHER `DEFS` with a trailing `pNum('x')` line in `defs.lua`; caller `pStr('okay')` | measure `'okay'`→`true`/`"ctl"` | `REPORTED`; no `DECLARING_FILE` entry | apply the edited file's `Shift` to the declaring file: a `DECLARING_FILE` entry |
| 16 | 04 | OTHER `DEFS`, caller `pArray(list)` (DR-01e: a table argument already errors) | `list`→`function() end`/original | `REPORTED_OTHER_RULE` (DR-01: only a `ret=true` error) | ignore the rule: `REPORTED` |
| 17 | 04 | OTHER `DEFS`, caller `pStr('ok')` | site with `edit = TextRange(10_000, 10_001)` | `FAILED` (from `doc.getText` in step 1, inside the `try`); text unchanged | step 1 outside the `try`: the test errors |
| 18 | 04 | a document holding `"a"` | `verifyReverted(doc, "b")` | `RevertMismatch` | make it a no-op; throw a plain `IllegalStateException` (then mapped to `FAILED`) |
| 19 | 05 | a fixed `AuditPayload`: one record of each outcome, the `REPORTED` record's only entry anchored `AT_PARAM`, one entry message *boolean is not assignable to union number \| string* | `AuditOutput.render` | equals `tooling/corpus/testdata/type_audit_sample.json` | rename any data-class field; render entries as joined strings |
| 20 | 05, 06 | `type_audit_sample.json`; cell cases as (shape, path, form, population, outcomes). A: `x`=(arg.class, PROJECT_FILE, GLOBAL, 30, 4×SILENT), `y`=(arg.class, SAME_FILE, LOCAL, 70, 4×REPORTED). B: `x`=(arg.primitive, PROJECT_FILE, MEMBER, 30, 2×SILENT), `y`=(arg.primitive, PROJECT_FILE, GLOBAL, 70, 4×REPORTED). C: A with populations 20, 80. D: C plus (lenient.primitiveForTable, PROJECT_FILE, GLOBAL, 100, 4×LENIENT). E: two members with `x`=(arg.primitive, PROJECT_FILE, GLOBAL), (10, 4×SILENT) and (30, 4×REPORTED) | `load` the sample; `aggregate`; `verdict`; `main` on a missing directory and on a copy with `schema` 2 | sample loads, every entry read as an object with its full `message`; the sample's `REPORTED`-without-`AT_SITE` count is 1; `main` returns 2 both times; A: F 0.30, R 1.00, `REOPEN`; B: F 0.30, R 0.00, `HOLD`; C: F 0.20, `HOLD`; D: F 0.20; E: F 0.50 | rename a key read by `load`; split entries on `\|`; count `REPORTED` records that have an `AT_SITE` entry (0); return 1 on a bad input; drop the R condition (B `REOPEN`); drop `measured ≥ 3` (B `REOPEN`); count lenient as silent (D 0.60); weight per-member rates instead of pooling (E 0.25) |
| 21 | 05, 07, 08 | Phase 2 output | review | `audit-results.md` holds the tables verbatim, commit SHA, verdict; each cell with `silent ≥ 1` names a BUG present under `docs/features/bug-fixes/` and in `docs/roadmap.md` | a silent cell missing from the BUG table |

## Implementation Status

| ID | Status | Note |
|----|--------|------|
| MAINT-54-01 | Full | `build.gradle.kts` `withTypeAudit` filter; TC-1 runs 1–3 hold |
| MAINT-54-02 | Full | `AuditKinds`; TC-2 |
| MAINT-54-03 | Full | `AuditSiteFinder`, `AuditSelection`; TC-3 to TC-8 |
| MAINT-54-04 | Full | `AuditInjector`; TC-9 to TC-18 (TC-16 fixture per DR-01e) |
| MAINT-54-05 | Full | `AuditOutput`, golden sample; TC-19 |
| MAINT-54-06 | Full | `type_audit_summary.py`; TC-20 |
| MAINT-54-07 | Full | [audit-results.md](audit-results.md): verdict REOPEN, strata by path and member; scope per DR-03 (resolved) |
| MAINT-54-08 | Full | 45 silent cells → [[BUG-38]], [[BUG-40]]–[[BUG-45]], each with a roadmap row; `arg.array`'s false positive is [[BUG-481]] |
| MAINT-54-09 | Full | `AuditCoverage`; TC-8 |
| MAINT-54-10 | Full | anchors, summary `REPORTED` without `AT_SITE`; TC-13, TC-20 |

## Acceptance Criteria
- [x] TC-1 to TC-21 hold, and `git diff origin/main -- src/main` is empty.

## Non-Functional Requirements
- The routine `run test` gains only `TypeAuditHarnessTest`. The full audit finishes within 30 minutes on
  the libvirt builder, or within the relaxed bound DR-02 records. Thread and write rules: design §2.
- Depends on the [[MAINT-33]] corpus lane (`CorpusManifest`, `CorpusTarget`, `CorpusGuards`).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Results: [audit-results.md](audit-results.md)
