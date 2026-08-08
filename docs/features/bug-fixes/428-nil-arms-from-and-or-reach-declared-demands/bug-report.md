---
id: "BUG-428"
title: "A `nil` arm produced by `and`/`or` is reported against a demand instead of being treated as optionality"
type: "bug"
parent_id: "BUG"
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
