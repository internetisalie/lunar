---
id: "MAINT-54-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-54"
folders:
  - "[[features/maint/54-type-false-negative-audit/requirements|requirements]]"
---

# MAINT-54: Risks & Gaps

## Executed De-risking

The probes were a throwaway `Maint54ProbeTest : IndexedBasePlatformTestCase` on `9d4bf784`, libvirt
builder, `test --rerun --tests '*Maint54ProbeTest*'`, deleted afterwards. Placements: fixture in
`defs.lua` + a caller file (OTHER), in one file with globals renamed per case (SAME), and the same with
`local function` (LOCAL). Errors printed unfiltered, verbatim excerpts below.

### DR-01 — known-answer matrix (done; 7 tests, 0 failures)
`DEFS` (also the TC fixture):

```lua
---@class Box
---@field n number
Box = {}
---@param s string
function Box:m(s) end
---@param s string
function pStr(s) end
---@param n number
function pNum(n) end
---@param b boolean
function pBool(b) end
---@param t table
function pTable(t) end
---@param t {a: number}
function pRecord(t) end
---@param b Box
function pClass(b) end
---@param xs number[]
function pArray(xs) end
---@param u string|number
function pUnion(u) end
---@param f fun()
function pFun(f) end
---@param s? string
function pOpt(s) end
---@generic T : string
---@param v T
function pGen(v) end
---@param a string
---@param b string
function pTwo(a, b) end
```

| Call | OTHER | SAME (global) and LOCAL |
|---|---|---|
| `pStr(true)` | `ERROR 'true' boolean is not assignable to string` | `ERROR ret=true 'function pStr_0(s) end' …`, `ERROR 's' boolean is not assignable to string` |
| `pTable(function() end)` | none | `ERROR 't' fun() is not assignable to { ... }` (plus the declaration and the literal) |
| `pClass(function() end)` | none | `ERROR 'b' fun() is not assignable to Box_8` |
| `pTable(true)`, `pClass(true)`, `pRecord(function() end)`, `pRecord(true)` | none | none |
| `pArray(function() end)` | `ERROR ret=true 'function() end' fun() is not assignable to number[]` | decl, literal (both `ret=true`), `'xs'` |
| `pArray(true)`, `pUnion(true)`, `pFun(42)`, `pOpt(true)`, `pGen(true)` | `ERROR` at the argument | `ERROR` at the parameter |
| `pTwo('a', 'b', true)` / `pTwo('a')` | none | `WARNING … Too many arguments: expected at most 2, got 3` / `… Too few …` |
| `Box:m(true)` | `ERROR 'true' boolean is not assignable to string` | none |
| `require('mod')` then `m.f(true)` (`---@param s string` `function M.f(s)` in `mod.lua`) | none | — |
| `string.upper(true)`, `math.floor('x')`, `table.concat(true)` | none | — |
| `---@param b Box` `local function f(b) b.n = true end` | `HYPOTHESIS` only | — |
| `---@return string` `function rStr() return true end` | — | `ERROR ret=true 'function rStr() return true end' boolean is not assignable to string` |

Also: `resolveCalleeType` gave `declaredSignature=false`, and on SAME the argument inside the parameter
type; mutate-and-revert on a 401-line file went `[]` → one error (370 ms) → `[]`, same `PsiFile`.

### DR-01b — knock-ons, controls, resolution (done; 4 tests, 0 failures)
`KDEFS`, each call run as baseline B, wrong W and control C:

```lua
---@class Box
---@field n number
Box = {}
---@param s string
function Box:m(s) return s:upper() end
---@param s string
function kStr(s) return s:upper() end
---@param t table
function kTab(t) return t.k end
---@param n number
function kNum(n) return n + 1 end
---@param s string
function kCat(s) local x = s .. "!" return #s + #x end
---@param b Box
function kBox(b) return b.n + 1 end
```

| B / W / C | SAME and LOCAL: W | C | OTHER: W, C |
|---|---|---|---|
| `kNum(1)` / `kNum(true)` / `kNum(7)` | decl `ret=true`, `'n + 1'` `ret=true`, `'n'` — all *boolean is not assignable to number* | none | `'true'` only; none |
| `kCat('a')` / `kCat(true)` / `kCat('ctl')` | decl, `'s .. "!"'`, `'s'` | none | `'true'` only; none |
| `kStr('a')` / `kStr(true)` / `kStr('ctl')` | decl, `'s'` | none | `'true'` only; none |
| `kTab({})` / `kTab(function() end)` / `kTab({})` | decl, `HYPOTHESIS '.k'`, literal, `'t'` | none | none; none |
| `kBox(Box)` / `kBox(function() end)` / `kBox({})` | decl, `HYPOTHESIS '.n'`, literal, `'b'` | `ERROR '{}' Missing required field 'n'` | none; none |
| `Box:m('a')` / `Box:m(true)` / `Box:m('ctl')` | none | none | `'true'` only; none |

- **Knock-ons exist and the control does not remove them:** `'n + 1'` and `'s .. "!"'` appear in W only.
  The control removes literal-change noise (none seen) and exposes a noisy control (`{}` against a class,
  hence "original" for class controls); the anchor rule (design §3.4 steps 5–7) is what drops the body
  entries. In OTHER, `kdefs.lua` had no errors after the wrong calls.
- **Resolution** (`target` parent chain): GLOBAL `kStr` and MEMBER `m.f`/`M.g` →
  `LuaFuncDeclImpl>LuaBlockImpl>LuaFile`; LOCAL → `LeafPsiElement>LuaNameRefImpl>LuaLocalFuncDeclImpl`;
  same-file METHOD → `LeafPsiElement>LuaNameRefImpl>LuaFuncNameMethodImpl`; OTHER `Box:m` and
  `string.upper` → null; the caller's snapshot types the receiver `Box` as a classless
  `LuaTableLiteralType({  })`, so the snapshot cannot classify them, but `resolveGlobal` can (DR-01d).
- Same-file MEMBER `local M = {} … function M.g(s) end M.g(true)`: no error.

### DR-01c — controls against bodies that use the parameter (done; 3 tests, 0 failures)
`CDEFS`, placed OTHER, SAME and LOCAL as above:

```lua
---@param f fun(a: number, b: number): number
function cFun(f) return f(1, 2) + 1 end
---@param g fun(s: string)
function cFun1(g) g("x") end
---@param u string|number
function cUni(u) return u .. "!" end
---@param w number|boolean
function cUni2(w) if w then return 1 end return 0 end
```

| B → W | W: SAME and LOCAL | W: OTHER | C (every placement) |
|---|---|---|---|
| `cFun(function(a, b) return a + b end)` → `cFun(42)` | decl `ret=true`, `'f'` | `'42'` | `function(a, b) end`: none; `function() end`: `HYPOTHESIS` *Too many arguments: expected at most 0, got 2* only |
| `cFun1(print)` → `cFun1(42)` | decl, `'g'` | `'42'` | `function(s) end`: none |
| `cUni(1)`, `cUni('a')` → `cUni(true)` | decl, `'u .. "!"'`, `'u'`, *boolean is not assignable to union number \| string* | `'true'` | `7`, `"ctl"`: none |
| `cUni2(1)`, `cUni2(true)` → `cUni2("audit")` | decl, `'w'` | `'"audit"'` | `7`, `false`: none |

No control raised a non-`HYPOTHESIS` error, so none is `NOISY`. A zero-arity control left an arity
`HYPOTHESIS` in the body, so the control follows the declared arity (design §3.1). The first-arm control
`"ctl"` on `cUni(1)` was clean too; matching the argument's kind is a precaution.

### DR-01d — cross-file methods via `resolveGlobal`, local-hosted fields (done; 2 tests, 0 failures)
Callee files: `kdefs.lua` (`KDEFS`), `svc.lua` (`---@class Svc` `Svc = {}`, `---@param n number`
`---@param s string` `function Svc:run(n, s)`, `---@param x number` `function Svc.stat(x)`) and `lbox.lua`
(`---@class LBox` `---@field n number` `local LBox = {}`, `---@param s string` `function LBox:lm(s) end`,
`return LBox`). Route = `resolveGlobal(receiver).resolveMember(name).sourceElement`, then its enclosing
`LuaFuncDecl`'s tags.

| Caller | `resolve()` | Route | Engine |
|---|---|---|---|
| `Box:m(true)` | null | `LuaFuncNameMethod`, kdefs.lua → `s:string` | `ERROR` at `'true'` |
| `Svc:run(true, 'a')`, `Svc:run(1, true)` | null | svc.lua → `n:number, s:string` | `ERROR` at `'true'` |
| `Svc.stat(true)` | `LuaFuncDecl`, svc.lua | same → `x:number` | `ERROR` at `'true'` |
| `---@param b Box` `local function g(b) b:m(true) end` | leaf > `LuaNameRef` > `LuaFuncNameMethod`, kdefs.lua | — | `ERROR` at `'true'` |
| `local L = require('lbox')` `L:lm(true)` | leaf > `LuaNameRef` > `LuaFuncNameMethod`, lbox.lua | — | `HYPOTHESIS` only, *LBox \| { ... } is not assignable to { ... }* — silent |
| `string.upper(true)` | null | global table, no member | none |

Fields: `b.n = 1` with `b` a `Box`, or an `LBox` with or without the `require`: `LuaClassType`, member
`number`, `sourceElement` a `LuaCatsFieldTagImpl` each time, so the fixture never reached the stub branch.
The host `LuaLocalVarDecl`'s `catsComment.fieldTagList`, read directly, gives `n:number`.

### DR-01e — table arguments to `number[]` (Phase 1, measured; corrects TC-16's fixture)
TC-16 as planned (`pArray({1})`, `{1}`→`function() end`/`{}`) read `SKIPPED_PREEXISTING`: DR-01 never ran
the baseline. `AuditInjector.keys`, OTHER `DEFS`, HEAD `066057d5`:

| Caller | Non-`HYPOTHESIS` errors |
|---|---|
| `pArray({1})`, `pArray({})`, `pArray({1, 2})` | `ERROR` at the literal, *{ ... } is not assignable to number[]* |
| `---@type number[]` `local xs = {}` `pArray(xs)` | the same at `{}` in the declaration and at `xs` |
| `local ys = {1}` `pArray(ys)` | the same at `ys` |
| `pArray(function() end)` | `ERROR ret=true` *fun() is not assignable to number[]* only (as DR-01) |

So every table argument to `number[]`, `{}` included, already errors: a false positive, outside this
audit's scope. Consequences: a table-literal `arg.array` site is `SKIPPED_PREEXISTING`, and any other
`arg.array` site's `{}` control raises a direct error, so it is `NOISY`. Both fall out of `F` by the
design's own rules, so the design stands; `arg.array` is reported unmeasured rather than silent. TC-16
now uses `pArray(list)` (an untyped name) with control original, which still isolates the rule check.

### DR-03 — the bundled stdlib carries the sites (Phase 1 DR-02 run; resolved)
Design §3.6 rests on "only lazynvim carries LuaCATS `---@param` tags … so only lazynvim yields injection
sites; the others contribute coverage counters only". The DR-02 run refutes it: every member's calls
resolve to tagged declarations outside both the temp tree and the checkout, i.e. `LIBRARY`. The sampled
sites are standard-library calls (luacheck `spec/helper.lua:78` `error(…, 0)`, `src/luacheck/init.lua:42`
`assert(…, msg)`, `spec/check_spec.lua:6` `table.remove(warnings, i)`), and the bundled stdlib stubs under
`src/main/resources/runtime/standard/` carry `---@param` tags. The payload does not record a `LIBRARY`
callee's file, so "every `LIBRARY` site is a bundled stub" is inferred from that sample, not counted.
Why DR-01b saw `string.upper` unresolved while these resolve is not established.

Population by callee path, pooled over the five members (non-`lenient.` shapes; `lenient.` in brackets):

| Path | Population | Measured (silent) | Of which lazynvim |
|---|---|---|---|
| `LIBRARY` | 8 418 [1 172] | 234 (143) | 1 663 of lazynvim's 2 572 total population |
| `SAME_FILE` | 469 [67] | 69 (30) | all |
| `PROJECT_FILE` | 258 [26] | 53 (43) | all |
| `CHECKOUT_FILE` | 80 [9] | 20 (20) | all |

Non-lazynvim members contribute population only through `LIBRARY`: luacheck 1 399, luarocks 3 992,
penlight 655, zerobrane 1 881. So `LIBRARY` is 91 % of the non-lenient population `F` is weighted by,
and `arg.table` on `LIBRARY` (e.g. `table.insert`) counts as node-carrying under §3.6's rule (path ≠
`SAME_FILE`). The pre-stated verdict would therefore measure the bundled stdlib's declared demand, not
lazynvim's, which contradicts the verdict's stated scope ("a lazynvim verdict", requirements Overview).
Computing it anyway and reading it as the survey's answer would be a verdict on a different question;
reshaping `F` now (dropping `LIBRARY`, scoping to lazynvim) would be choosing the rule after seeing
the data. Neither is an implementer's call.

Phase 1 (the harness) stands: every count above is the design's own classification, and the
per-member JSON keeps `path` per cell.

**Resolved (supervisor, 2026-11-07).** Design §3.6 is amended to match:
1. The verdict rule is unchanged: `F`, `R`, the 0.25 threshold, the half-from-mechanisms-A/B
   threshold, and pooling across members. Only the population premise was wrong. The population is
   every non-`lenient.` tagged site in **all five members on all callee paths** (`LIBRARY`,
   `SAME_FILE`, `PROJECT_FILE`, `CHECKOUT_FILE`), pooled. Mechanisms A and B are engine checks that do
   not depend on where the callee is declared, so `LIBRARY` sites exercise the mechanism the redesign
   question is about.
2. Every figure is also reported stratified by callee path and by member, so the `LIBRARY` weight is
   visible. The results record where `LIBRARY` callees live, from a sample of their declarations; they
   are called the bundled stdlib stubs only as far as the sample shows it.
3. `CHECKOUT_FILE` counts as `PROJECT_FILE` does.
4. `arg.array` is reported as unmeasurable on this engine: every table argument to `number[]` already
   errors (DR-01e). The false positive is tracked as a BUG.

No other rule changes after the data was seen.

## Pending De-risking, Risks and Gaps

| ID | Risk, gap or action | Handling / status |
|---|---|---|
| DR-02 | Phase 1 exit: run the audit command (design §5); lazynvim, the only member with LuaCATS sites, carries the injection cost. Read the `[type-audit:<member>] … elapsedMs=` lines; measured = their sum, with lazynvim's reported on its own | **done.** Libvirt builder, `CAP = 4`, the design §5 command on `066057d5` plus the Phase 1 harness: luacheck 49 684 ms, luarocks 208 120 ms, penlight 26 009 ms, lazynvim 376 288 ms, zerobrane 114 362 ms; sum 774 463 ms (12.9 min, Gradle wall 13 m 29 s) — under 30 min, so `CAP` stays 4 and the NFR stands. (Rule as planned: sum > 30 min → `CAP = 2` and re-measure; still over → a per-member bound equal to lazynvim's time.) Lazynvim did not carry the cost alone: see DR-03 |
| Risk 1.1 [Low] | `CAP` sites miss a sub-shape of a large cell | cells split by path and form; `population` beside every rate |
| Risk 1.2 [Medium] | runtime: snapshots B, W and C per site | DR-02 |
| Risk 1.3 [Low] | a mutation leaks into later sites | `verifyReverted` aborts the member; TC-18 |
| Gap 2.1 | errors placed in the declaring file | read for PROJECT_FILE callees (anchor `DECLARING_FILE`, counted, never direct); CHECKOUT_FILE and LIBRARY declaring files are not read |
| Gap 2.2 | callees neither `resolve()` nor `resolveGlobal` reaches: library members (`string.upper`) and receiver forms DR-01d did not try (e.g. a method on a call result) | counted (`unresolvedCallee`, `unresolvedMethod`), printed beside the verdict as unmeasured; a non-zero count leaves cells unmeasured, and a `REOPEN`/`HOLD` is read with that count |
| Gap 2.3 | the same-file anchor set `AT_PARAM`/`AT_DECL` is what DR-01/01b show; an engine that reports elsewhere reads `SILENT` | entries are kept in `newErrors`, so a re-read is possible |

## Premises Examined
All chosen, none fixed: `-PwithTypeAudit` not `-PwithCorpus` (BUG-418; routine run must not grow); no
definition libraries (shown in coverage); exact arity (shorter calls are ambiguous); JSON plus Python (the
verdict pools test methods, in the lane's `unittest` tooling); thresholds fixed before the run.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
