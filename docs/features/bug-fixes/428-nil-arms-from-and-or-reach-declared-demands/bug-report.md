---
id: "BUG-428"
title: "A `nil` arm produced by `and`/`or` is reported against a demand instead of being treated as optionality"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-428: `nil` arms from `and`/`or` reach demands as certain nils

Split out of BUG-427, which fixed `and`/`or` to carry the correct arms — `a and b` yields a's falsy
value or b — and left this residue.

## Measured (2026-08-07, zerobrane, per-site dump against a control)

BUG-427 removed 74 emissions and added 37. The additions are dominated by one class:

```
18  nil value is not assignable to any[]
 5  nil value is not assignable to { ... }
 5  nil value is not assignable to string
 1  nil value is not assignable to number
 8  Too few arguments: expected at least 2, got 1
```

```lua
local instances = value and indicateFindInstances(editor, value, pos+1)
navigateToPosition(editor, pos, instances[0]-1, #value)     -- <- reported here
```

`value and f(...)` is correctly `nil | <f's return>` now. The use site then reports the `nil` arm as
a *certain* nil.

Two further sites of the same family were confirmed on penlight
(`pl/lapp.lua:114`, `boolean | nil | number is not assignable to string`), so the boolean arm also
survives some paths — `falsyPart` keeps `boolean` when the left operand is a bare boolean, which is
correct in isolation and wrong once it flows into a declared return.

## RE-MEASURED 2026-08-20 — fixed as a side effect of [[BUG-441]], with a named residual

The 2026-08-07 numbers below predate **two** changes to this exact code path — BUG-419's
declared-demand gate and BUG-441's unknown-provenance gate — so they were re-taken before any work
was planned against them. Per-site dump across all four corpora, `LuaCorpusSweepTest` instrumented to
print `file:line`, message and source line, run twice with only BUG-441's two source files reverted
between the runs.

| | sites |
| :-- | --: |
| before BUG-441 (`dc712238`) | **100** |
| after BUG-441 (`d6ce62e0`) | **11** |
| removed | **89** |

The removed set is this report's two families, and nothing else:

| count | message |
| --: | :-- |
| 20 | `nil value is not assignable to any[] \| string \| { ... }` |
| 18 | `boolean is not assignable to string` |
| 12 | `nil value is not assignable to string` |
| 9 | `nil \| number \| boolean is not assignable to string` |
| 9 | `boolean \| nil \| number is not assignable to string` |
| 8 | `boolean \| string is not assignable to string` |
| 5 | `nil value is not assignable to { ... }` |
| 4+2+1+1 | `boolean`/`string`/`boolean \| number` against `number` |

**Both of this report's own cited reproductions are in the removed set, by file and line**:
`editor.lua:1355` (`local instances = value and indicateFindInstances(editor, value, pos+1)`) and
`lapp.lua:114` (`boolean | nil | number is not assignable to string`).

### Why it was fixed by a change that never mentions `and`/`or`

This report guessed the collapse happened at `Union.create` dropping `Undefined` arms and said so
explicitly as an untested suspicion. That was close: BUG-441 found the unknown was never
*represented* in the first place, and gated the emission on whether the value's provenance is
accountable at all. Read in the corpus source, the suppressed sites are exactly that shape —
`penlight/lua/pl/config.lua:131` is `local val = cnfg[var]`, an index into an unmodellable table
whose result unions every call site's `def`, so `list_delim` typed as `boolean | string` where the
value is plainly a string; `stringx.lua:233` is `last = last or #s` over an unannotated parameter,
with `falsyPart` keeping `boolean` — this report's second family verbatim.

### Residual — 2 sites, and the family is NOT completely gone

`config.lua:131` and `stringx.lua:231` still emit, anchored on the **function-declaration line**
rather than the use site: `boolean is not assignable to string` / `... to number`, the same
call-site-union imprecision reported against the parameter itself. Small, and worth a follow-up only
if it recurs; recorded here rather than left for someone to rediscover.

### The negative control

The 11 survivors are dominated by a **different** family, which is what says checking did not simply
stop: `dkjson.lua`'s LPeg patterns (`P"\\" * g.C(...)` — `__mul` on a pattern object) and
`stringx_spec.lua`'s `'%s = %d' % {...}` (`__mod`). Operator metamethods on library types, untouched.

## Why it is a false positive

BUG-416 established the rule: a `nil` among several reaching definitions is *optionality* — the
branch that did not run — and flagging it produced 959 false positives on one member. The same
reasoning applies to a `nil` arm produced by `and`: the programmer's `value and f(...)` is
idiomatically followed by a guard, and the engine flags the guarded use.

The emissions arrive on the **certain nil** path (`valueType == Nil && certain`), which means the
union collapsed to a bare `Nil` somewhere before the check rather than arriving as `nil | T`. Where
that collapse happens is **not traced** — `Union.create` dropping `Undefined` arms is the obvious
suspect, since that is exactly what made the boolean arm survive in BUG-427, but this report asserts
nothing it has not measured.

## Fix direction

Trace the collapse first. If a `nil | T` union is reaching the check intact, the fix belongs in the
informative-arms filter (BUG-416's rule already forgives a nil arm — find why it does not here). If
the union has already collapsed to `Nil`, the fix belongs wherever that happens, and the union must
be preserved instead.

Defect 1 of **BUG-419** — unknown writes are omitted rather than represented — is a plausible
contributor and is still unimplemented; that deferral was justified when almost nothing reached the
declared-demand path, and BUG-425/427 have changed that premise.

## Verification

- The zerobrane per-site dump against a control shows no `nil value is not assignable to …`
  additions; the harness is a throwaway modelled on `CorpusSweep` (see BUG-427's history).
- A unit fixture: `local x = cond and f()` followed by a use of `x`, with `f` declared to return a
  table, reports nothing.
- The guarded form must keep working — `if x then use(x) end` was never the problem.
