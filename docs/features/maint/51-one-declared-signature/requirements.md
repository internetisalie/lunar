---
id: "MAINT-51"
title: "51: One Declared Signature"
type: "feature"
parent_id: "MAINT"
status: "planned"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-51: One Declared Signature

## Overview

A declared parameter is checked by two paths. A call to a declaration-typed callee (a free global, a
global's member) goes through `LuaTypesVisitor.demandDeclaredParams`, which makes a per-call copy of
the parameter's demand. A call that reaches a materialized signature (a stub-backed class method)
goes through the parameter node `LuaGraphType.declaredParamNodeFor` builds. Each path, and the in-file
`LuaTypeGraphBridge.demandTypeFor`, has its own copy of the rule that a `?` parameter demands `T|nil`.
Separately, `LuaTypeGraph.checkCompatibility` forgives a union value's `nil` arm and an uncertain bare
`nil`, and `LuaTypeGraph.isCompatible`, which `checkCompatibility` consults for a union demand, does
neither. These disagreements caused [[BUG-11]], [[BUG-19]], [[BUG-425]] and [[BUG-566]], and DR-03
measured live false positives that come from the second.

This feature gives the parameter rule one function, the `nil` rule one shape in both checkers, and
the per-call copy one class that [[TYPE-14]]'s declared-path instantiation calls instead of writing a
second copy.

## Scope

### In Scope
- `LuaTypeAlgebra.parameterDemand`, the one place a `?` turns a declared type into `T|nil`. Called by
  `declaredParamNodeFor`, `LuaTypeGraphBridge.injectParamAnnotations` and the per-call copy.
- One `nil` rule for a value meeting a demand. A union value's `nil` arm is not evidence in either
  `checkCompatibility` or `isCompatible`. A bare `nil` value is reported against any demand that does
  not admit `nil`, union or not, only when it is certain.
- `LuaSignatureCopier`, which owns the per-call copy ([[BUG-19]]'s `detachedDemand` and its helpers,
  moved out of `LuaTypesVisitor`) and is the API [[TYPE-14]] design §5.8 calls.
- The matching edit to [[TYPE-14]]'s design §5.8 and implementation plan Phase 2 (design §5).

### Out of Scope
- **Table-typed declared parameters on the per-call path** (`---@param w Widget` on a free global).
  They stay unchecked. DR-06 measured copying them; design §8 records why it is deferred.
- **The field-optional predicate** `LuaTypeAlgebra.isOptional` and the `Undefined` clauses of the
  optional-parameter guards. [[MAINT-52]] owns them, and this feature builds on MAINT-52's code.
- **A cross-file `---@generic T` parameter** reports `string is not assignable to T` before and after
  this feature (DR-02, case C12x). [[TYPE-14]] case 3 owns it.
- **A declared callee's returns.** `seedDeclaredReturns` and TYPE-14's return copies are unchanged.
- **Positional alignment** (`positionsAreUnambiguous`) moves but keeps its rule.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-51-01 | **One parameter demand** | M | Not Implemented | A `?` parameter accepts `nil` and its declared type on all three paths, through `LuaTypeAlgebra.parameterDemand` alone. One mutation of that function turns the per-call, the materialized and the in-file TCs red together. |
| MAINT-51-02 | **A nil arm is not evidence in either checker** | M | Not Implemented | A value whose only mismatch is a `nil` arm, at any depth, is not reported against a union demand. A non-`nil` mismatch still is. |
| MAINT-51-03 | **An uncertain nil is not a defect against a union demand** | M | Not Implemented | A `nil` that is one of several reaching definitions is not reported against a union demand without `nil`. A certain `nil` is reported as `nil value is not assignable to <demand>`, whether the demand is a union or not. |
| MAINT-51-04 | **One per-call copy** | M | Not Implemented | A declaration-typed callee's argument is wired straight into a fresh `declaredDemand` use from `LuaSignatureCopier.parameterUse`, never into the memoized seed's node, so its error stays at ERROR tier, one call cannot rewrite another's signature, and each reaching definition of the argument is checked alone. |
| MAINT-51-05 | **TYPE-14 calls the copier** | M | Not Implemented | TYPE-14's design §5.8 and plan Phase 2 name `LuaSignatureCopier` for a non-generic parameter, with the text in design §5. |
| MAINT-51-06 | **The corpus does not move** | S | Not Implemented | The corpus baselines recorded in CI's shape equal MAINT-52's (DR-07). |
| MAINT-51-07 | **Table demands on the per-call path** | W | Future Work | Not this feature (Out of Scope, DR-06). |

## Test Cases

TC-01 to TC-16 are tests in the new
`net.internetisalie.lunar.lang.types.LuaDeclaredSignatureTest`, whose `defs.lua` and `errors` helper
design §4 gives. "Errors" is that helper's output: the sorted, distinct
`SEVERITY:message@element-text` of `LuaTypesSnapshot.forFile(t.lua).getErrors()`, every tier
included. Inputs are `t.lua`, with line breaks written ` / `. Mutations are design §6's. Every
expected value and mutation output was observed in DR-07, by the probe case of the same TC ID.

| TC | Req | Input (`t.lua`) | Expected errors | Mutation → errors |
|----|-----|-----------------|-----------------|-------------------|
| TC-01 | 01 | `opt2("x", nil)` | `[]` | M1 → `[ERROR:nil value is not assignable to string@nil]` |
| TC-02 | 01 | `---@type C` / `local c` / `c:m("x", nil)` | `[]` | M1 → `[ERROR:nil value is not assignable to string@nil]` |
| TC-03 | 01 | `---@param a string` / `---@param s? string` / `local function o(a, s) end` / `o("x", nil)` | `[]` | M1 → `[ERROR:nil value is not assignable to string@s]` |
| TC-04 | 01 | `opt2("x", 1)` | `[ERROR:number is not assignable to union nil \| string@1]` | M1 → `[ERROR:number is not assignable to string@1]` |
| TC-05 | 01 | `---@type C` / `local c` / `c:m("x", 1)` | `[ERROR:number is not assignable to union nil \| string@1]` | M1 → `[ERROR:number is not assignable to string@1]` |
| TC-06 | 02 | `---@return (string\|nil)[]` / `local function arr() end` / `---@param xs string[]\|number` / `local function h(xs) end` / `h(arr())` | `[]` | N1 → three `ERROR:nil \| string[] is not assignable to union number \| string[]` entries, at `arr()`, `local function h(xs)` and `xs` |
| TC-07 | 02 | `---@return (string\|nil)[]` / `local function arr() end` / `takeArrOrN(arr())` | `[]` | N1 → `[ERROR:nil \| string[] is not assignable to union number \| string[]@arr()]` |
| TC-08 | 02 | `---@return {a: string\|nil}` / `local function rec() end` / `---@param o {a: string}\|number` / `local function h(o) end` / `h(rec())` | `[]` | N1 → three `ERROR:{ ... } is not assignable to number \| { ... }; closest match '{ ... }': incompatible fields` entries |
| TC-09 | 02 | TC-06 with `(string\|boolean)[]` for `(string\|nil)[]` | three `ERROR:boolean \| string[] is not assignable to union number \| string[]` entries, at `arr()`, `local function h(xs)` and `xs` | N4 → `[]` |
| TC-10 | 03 | `---@param v string\|number` / `local function k(v) end` / `local v = nil` / `if os.time() then v = 1 end` / `k(v)` | `[]` | N2 → `ERROR:nil is not assignable to union number \| string` at `local function k(v) ` and at `v` |
| TC-11 | 03 | `local v = nil` / `if os.time() then v = 1 end` / `takeSN(v)` | `[]` | N2 → `[ERROR:nil is not assignable to union number \| string@v]` |
| TC-12 | 03 | `---@param v string\|number` / `local function k(v) end` / `k(nil)` | `[ERROR:nil value is not assignable to number \| string@v]` | N5 → `[]`; N2 → `ERROR:nil is not assignable to union number \| string` at `local function k(v) ` and at `v` |
| TC-13 | 03 | `takeSN(nil)` | `[ERROR:nil value is not assignable to number \| string@nil]` | N5 → `[]`; N2 → `[ERROR:nil is not assignable to union number \| string@nil]` |
| TC-14 | 04 | `take(1)` | `[ERROR:number is not assignable to fun()@1]` | M2 → `[HYPOTHESIS:number is not assignable to fun()@1]` |
| TC-15 | 04 | `local z = nil` / `takeNumber(z)` | `[ERROR:nil value is not assignable to number@z]` | M2 → `[HYPOTHESIS:nil value is not assignable to number@nil]`; N5 → `[]` |
| TC-16 | 04 | `---@param a string` / `local function f(a) end` / `take(f)` | `[WARNING:Too few arguments: expected at least 1, got 0@f]` | M2 → `[]` |
| TC-17 | 05 | after the design §5 edit: `git grep -n "carriesGraphNodes" -- docs/features/type/14-generics-baseline/`; `git grep -l "LuaSignatureCopier" -- docs/features/type/14-generics-baseline/`; `git grep -l "parameterUse" -- docs/features/type/14-generics-baseline/` | the first prints nothing; the second prints `design.md` and `implementation-plan.md`; the third prints `design.md` | not a code test: unedited, the first prints `design.md:421`, `design.md:512`, `implementation-plan.md:68` and `implementation-plan.md:73`, and the other two print nothing |
| TC-18 | 04, 06 | the corpus in CI's shape, `test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*'`, then the recording in implementation plan task 7 | no corpus test fails, and `git diff -- src/test/resources/corpus/` prints nothing | M7 → `testLuarocksCorpus` fails `inspection.LuaTypeAssignability: baseline 9 → observed 10`, and `testZerobraneCorpus` `baseline 7 → observed 8` |
| TC-19 | 04 | the existing `ExpectedCallbackResolverTest.testTableSortComparatorSlotResolves` and `LuaCrossFileFunctionDemandTest.testCallbackReturnValueIsNotAnError` | both pass | M6 → the first fails `expected:<boolean[]> but was:<boolean[ \| any]>`, the second `expected:<[]> but was:<[number is not assignable to nil]>` |

TC-01 (per-call path), TC-02 (materialized path) and TC-03 (in-file path) turning red under the one
mutation M1 is the evidence for MAINT-51-01's "one function".

The existing `LuaCrossFileFunctionDemandTest`, `LuaMethodArgumentTypeTest`,
`LuaOptionalParamExplicitNilTest`, `LuaDeclaredContractErrorTest` and `ExpectedCallbackResolverTest`
stay unmodified and green.

## Acceptance Criteria
- [ ] Every TC above passes, and every mutation in design §6 turns the TCs naming it red with the stated output.
- [ ] Full suite green on top of merged MAINT-52, with no exemptions: 0 failures, including MAINT-52's `LuaUnknownRuleTest`.
- [ ] The corpus passes in CI's shape and its recording changes no baseline (TC-18).
- [ ] `git grep -n "fun demandTypeOf\|fun demandTypeFor\|acceptsNil" -- src/main` prints nothing, and
  `git grep -l "fun detachedDemand\|fun carriesGraphNodes\|fun positionsAreUnambiguous" -- src/main`
  prints only `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaSignatureCopier.kt`.

## Non-Functional Requirements
- **Threading:** unchanged. Everything runs inside the read action that builds a `LuaTypesSnapshot`.
- **Performance:** no new graph walk and no new node. `parameterUse` builds the one use node per
  checked argument that `demandDeclaredParams` builds today (design §2.3).

## Dependencies
- **[[MAINT-52]] lands first.** This feature's code is written against MAINT-52's, and every DR row
  was measured with MAINT-52's design §2 applied (DR-01).
- **[[TYPE-14]] waits on this feature** (roadmap), and MAINT-51-05 edits its design.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
