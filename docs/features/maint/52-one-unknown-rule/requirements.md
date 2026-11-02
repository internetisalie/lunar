---
id: "MAINT-52"
title: "52: One Unknown Rule"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "low"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-52: One Unknown Rule

## Overview

"The engine cannot say" has several spellings. `LuaTypeAlgebra.simplify` drops `Undefined` from a union
and keeps `Any`, so call sites convert one to the other before building a union: [[BUG-441]] in
`LuaTypesVisitor`, [[BUG-16]] in `LuaGraphType.fromLuaType`, and the optional-parameter guards of
[[BUG-11]] (`declaredParamNodeFor`) and [[BUG-22]] (`demandTypeOf`). The third optional-parameter
site, `LuaTypeGraphBridge.demandTypeFor`, has no guard, so `---@param o? unknown` on an in-file
function demands `nil` and reports every non-nil argument (DR-06). A field demand disagrees as well:
`---@field a any` is required while `---@field a Unres` and `---@field a unknown` are not (DR-01).

This feature puts the union rule in `LuaTypeAlgebra`, deletes the call-site conversions it makes
redundant, and makes an `any` field optional like the other unknown spellings. DR-02 compared
candidate rules and DR-03 measured the chosen one on the full suite and the corpus.

## Scope

### In Scope
- One union rule in `LuaTypeAlgebra.simplify`: an `Undefined` member beside other members is an `Any` arm.
- Deleting the conversions it makes redundant, at the sites design §2 marks *delete*. BUG-16's
  conversion is narrowed to the one case the rule cannot cover, a declared union whose members all
  fail to resolve (design §2.2, DR-07).
- One field-demand predicate, `LuaTypeAlgebra.isOptional`, which replaces the private copies in
  `LuaTypeGraph` and `LuaUnionDiagnostics`. An `Any` field is optional, like an `Undefined` one.
  This feature owns that predicate (design §5).

### Out of Scope
- **The reaching-definition merge** (`VariableElement.resolveWrite`). It drops `Undefined` because
  there `Undefined` is absence (an empty up-set, the cycle guard). Unknown-ness across reaching
  definitions stays with `LuaTypeGraph.isUnknown` ([[BUG-441]] RC-2). Gap 2.1 records a merge-side
  difference this feature leaves ([[BUG-25]]).
- **Which producers spell "cannot model" as `Undefined`.** Changing them is how DR-02 variants V1 and
  V3 failed (Risk 1.1).
- **[[MAINT-51]]** owns where the optional rule lives (`demandDeclaredParams` and
  `declaredParamNodeFor` merged into one function) and `detachedDemand`. This feature deletes only the
  `Undefined` clause inside the `declaredParamNodeFor` and `demandTypeOf` copies (design §5).
- **[[TYPE-14]]** generics. DR-02 V1 changed a generic method's diagnostics, which is one reason V1
  was rejected.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| MAINT-52-01 | **The union rule** | M | Full | `LuaTypeAlgebra.canonicalize` turns an `Undefined` member beside other members into an `Any` arm. `Undefined` alone stays `Undefined`. |
| MAINT-52-02 | **Conversions only where the rule cannot reach** | M | Full | The design §2 deletions are made, BUG-16's conversion covers only the all-unresolved union, and every TC naming this requirement holds. |
| MAINT-52-03 | **An `any` field is optional** | M | Full | A table missing a field the class declares as `any` gets no *Missing required field*, and a union's closest-match reason does not list that field as missing. |
| MAINT-52-04 | **Known demands still report** | M | Full | Resolved optional parameters, resolved unions and required fields of a known type report as before. |
| MAINT-52-05 | **Corpus moves only as measured** | S | Full | Baselines and type goldens move by DR-03's deltas and no others. |

## Test Cases

TC-01 is a unit test in `LuaTypeAlgebraTest`. TC-06 is the existing
`LuaMethodArgumentTypeTest.testOptionalParamOfUnresolvedTypeAcceptsATable`, reused rather than
duplicated: its stub-backed fixture is the [[BUG-11]] shape. TC-13 is the corpus gate. Every other TC
goes in a new `net.internetisalie.lunar.lang.types.LuaUnknownRuleTest` (design §4). Each fixture is a
probe case, named in `Run` (DR-02, DR-04, DR-06, DR-07). "Errors" means the distinct messages of
`LuaTypesSnapshot.forFile(t.lua).getErrors()`. "Types" means `getValueType(nameRef).displayName()` of
the named local. The mutations are design §6's.

| TC | Req | Input (`t.lua`, with `lib.lua` where given) | Expected | Mutation that turns it red | Run |
|----|-----|------|----------|----------------------------|-----|
| TC-01 | 01 | `canonicalize(listOf(String, Undefined))`; `canonicalize(listOf(Undefined))` | `Any`; `Undefined` | M1: the first gives `String` | DR-02 (A1, A2) |
| TC-02 | 02 | `---@param o string\|Unres` / `local function f(o) end` / `f(1)` | errors `[]` | M1: `number is not assignable to string` | c01 |
| TC-03 | 02 | `---@param o? Unres.ns \| {buffer: boolean}` / `local function f(o) end` / `f({ pattern = "x" })` | errors `[]` | M1: `{ ... } is not assignable to union nil \| { ... }` | c02 |
| TC-04 | 02 | `---@param o unknown \| {buffer: boolean}` / `local function f(o) end` / `f({ pattern = "x" })` | errors `[]` | M1: `Missing required field 'buffer'` | c21 |
| TC-05 | 02 | `lib.lua`: `---@param x? Unres` / `function take04(x) end`; `t.lua`: `take04({})` / `take04(1)` / `take04(nil)` | errors `[]` | M1: `{ ... } is not assignable to nil`, `number is not assignable to nil` | c04 |
| TC-06 | 02 | `LuaMethodArgumentTypeTest`'s `defs` as `defs.lua` (stub-backed); caller `onSubclassSelf("self:on({ pattern = \"x\" })")`, i.e. `t.lua` = `---@class E : C` / `local E = {}` / `function E.create()` / `  local self = setmetatable({}, { __index = E })` / `  ---@cast self E` / `  self:on({ pattern = "x" })` / `end` | `assignabilityHits` = `[]` | M1: `[{ ... } is not assignable to nil@{ pattern = "x" }]` | DR-02 |
| TC-07 | 02 | `local d = wx.thing or "s"` / `local m = wx.thing and {a = 1}` / `---@param n number` / `local function g(n) end` / `g(d)` | errors `[]`; types `d=any`, `m=any \| { ... }` | M1: `d=string`, `m={ ... }`, and `string is not assignable to number` | c12 |
| TC-08 | 02 | `local q = a:b():c() or "s"` / `---@param n number` / `local function g(n) end` / `g(q)` | errors `[]`; type `q=any` | M1: `q=string` and `string is not assignable to number` | c20 |
| TC-09 | 03 | `---@class C07` / `---@field a any` / `---@field b string` / (blank) / `---@param c C07` / `local function f(c) end` / `f({ b = "x" })` | errors `[]` | M2: `Missing required field 'a'` | c07 |
| TC-10 | 04 | as TC-09 with `---@field a string` (class `C19`) | errors `[Missing required field 'a']` | M4: `[]` | c19 |
| TC-11 | 04 | `---@param o? string \| {buffer: boolean}` / `local function f(o) end` / `f({ pattern = "x" })` | errors `[{ ... } is not assignable to union nil \| string \| { ... }]` | M4: `[]` | c16 |
| TC-12 | 04 | `lib.lua`: `---@param x? string` / `function take18(x) end`; `t.lua`: `take18(1)` / `take18(nil)` | errors `[number is not assignable to union nil \| string]` | M3: `[number is not assignable to string, nil value is not assignable to string]` | c18 |
| TC-13 | 05 | the `-PwithCorpus` gate (implementation plan, Phase 1) | baselines and goldens differ from `main` by DR-03's rows only | not a code test: the diff is the check, because `CorpusGuards.assertRatchet` fails on increases only | DR-03 |
| TC-14 | 02 | `---@type unknown` / `local v = 1` / `---@type Unres` / `local w` / `---@type Unres\|string` / `local z = 1` / `---@param n number` / `local function g(n) end` / `g(w)` / `g(z)` | errors `[]` | M1: `[number is not assignable to string, number \| string is not assignable to number, string is not assignable to number]` | c14 |
| TC-15 | 02 | `---@param o? unknown` / `local function f(o) end` / `f(1)` / `f({})` / `f(nil)` / `---@param p? unknown` / `function g27(p) end` / `g27(1)` | errors `[]` | M1: `[number is not assignable to nil, { ... } is not assignable to nil]` | c27 |
| TC-16 | 02 | `---@type Unres1\|Unres2` / `local w2` / `---@type Unres1\|Unres2` / `local z2 = 1` / `---@param n number` / `local function g(n) end` / `g(w2)` / `g(z2)` | errors `[]`; type `w2=any` | M5: `w2=undefined` | c24 |
| TC-17 | 02 | `---@return Unres1\|Unres2` / `local function h() end` / `local r = h()` / `---@param n number` / `local function g(n) end` / `g(r)` | errors `[]`; type `r=any` | M5: `r=number` | c25 |
| TC-18 | 03 | `---@class E1A` / `---@field x number` / `---@field a any` / `local E1A = {}` / `---@class E1B` / `---@field y string` / `local E1B = {}` / `---@param o E1A\|E1B` / `local function f(o) end` / `f({ x = "s" })` | errors `[{ ... } is not assignable to E1A \| E1B; closest match 'E1A': incompatible fields]` | M6: `[… closest match 'E1A': missing field 'a']` | e1 |

## Dependencies & Assumptions

- None on the roadmap. If [[MAINT-51]] lands first, the optional-rule copies are one function, and
  the deletion in design §2.3 is made there instead.
- `LuaTypeAlgebraTest`'s `undefined is dropped unless it is the only member` pins the rule this feature
  replaces. TC-01 replaces it.

## Non-Functional Requirements

- **Performance:** `simplify` gains one list filter; no new graph walks.
- **Threading:** unchanged. The type engine runs inside the read action that builds a snapshot.
