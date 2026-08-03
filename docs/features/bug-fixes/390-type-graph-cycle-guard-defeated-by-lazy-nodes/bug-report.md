---
id: "BUG-390"
title: "StackOverflowError: the type graph's cycle guard is reset by every LazyValueElement hop"
type: "bug"
parent_id: "BUG"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-390: StackOverflowError — the type graph's cycle guard is reset by every `LazyValueElement` hop

> **RESOLVED 2026-08-03.** `ValueNode` gained `writeWith(visited)`, which `LazyValueElement`
> overrides to forward the guard into `compute` and `VariableElement` overrides to continue via
> `resolveWrite(visited)`. `resolveWrite`'s `when` collapsed to a single `is ValueNode ->
> it.writeWith(visited)` branch — the old `is VariableElement` special case sitting above a generic
> `is ValueNode -> it.write` is exactly what let the lazy hop escape, so removing the split makes
> the defect harder to reintroduce. `LuaTypeGraph.lazyValue` and its one caller
> (`LuaTypesVisitor.seedSubscriptElement`) thread the set through.
>
> Verified on the corpus: `highlightFailures` fell from **131 files to 1** (luacheck 41→0,
> luarocks 59→1, ZeroBrane 31→0). The survivor is a different defect in a different file — see
> BUG-392. Regression test: `LuaTypeGraphCycleGuardTest`.
>
> **Consequence worth noting:** with a third of files no longer aborting mid-highlight, the
> inspection counts rose sharply (ZeroBrane `LuaUndeclaredVariable` 1009 → 3202). The earlier
> false-positive figures were under-reported, not improved.

`VariableElement.resolveWrite` carries a `visited` set to break cycles, but the set is **dropped**
whenever resolution passes through a lazy node. Any write-cycle that routes through a subscript
seed therefore recurses until the stack is exhausted, taking down whatever triggered it.

Found by the MAINT-33 corpus sweep on its first run with inspections enabled — it kills the sweep
of **both** corpus projects (upstream luacheck v1.2.0 and luarocks v3.12.2).

## 1. Reproduction

Run any analysis that drives the type engine (`LuaTypeAssignabilityInspection` or
`LuaReturnTypeMismatchInspection`, both `enabledByDefault="true"`, `level="ERROR"`) over a file
containing a self-referential subscript write. The corpus sweep reproduces it reliably:

```bash
tooling/gce-builder/gce-builder.sh run "test --tests *Corpus* -PwithCorpus"
```

Both `testLuacheckCorpus` and `testLuarocksCorpus` fail with `java.lang.StackOverflowError`.

## 2. Expected vs Actual Behavior

- **Expected**: cyclic write edges resolve to `LuaGraphType.Undefined` via the existing guard, as
  they already do for the pure-`VariableElement` path.
- **Actual**: `java.lang.StackOverflowError`. In an IDE this surfaces as a failed inspection pass
  (and a very large `idea.log` entry) rather than a clean diagnostic.

Repeating frame cycle from the failure:

```
VariableElement.getWrite                         (LuaTypeNodes.kt:84)
  VariableElement.resolveWrite                   (LuaTypeNodes.kt:103)
    LazyValueElement.getWrite                    (LuaTypeNodes.kt:67)
      LuaTypesVisitor.seedSubscriptElement$lambda$0 (LuaTypesVisitor.kt:728)
        VariableElement.getWrite                 (LuaTypeNodes.kt:84)   ← cycle
```

## 3. Context / Environment

- **Confidence**: high — root-caused in code, with a reproducing gate.
- **Root cause**: the guard is per-call, and only one of the two branches threads it through.
  - `LuaTypeNodes.kt:87-88` — `resolveWrite(visited)` opens with
    `if (!visited.add(this)) return LuaGraphType.Undefined`. Correct in isolation.
  - `LuaTypeNodes.kt:102` — the `is VariableElement` branch **passes `visited` on**:
    `it.resolveWrite(visited)`.
  - `LuaTypeNodes.kt:103` — the `is ValueNode` branch **does not**: it calls `it.write`, which for
    a `LazyValueElement` (`:67`) is `compute()`.
  - That `compute()` is `LuaTypesVisitor.seedSubscriptElement`'s lambda
    (`LuaTypesVisitor.kt:728`), which evaluates `receiverNode.write`. When the receiver is a
    `VariableElement`, `:84` starts a **fresh** `resolveWrite(mutableSetOf())` — an empty visited
    set.
  - So every hop through a lazy node forgets everything seen so far, and a cycle
    `variable → lazy subscript → same variable` never terminates.
- `resolveRead` (`:117`) has the same *shape*, but is **not** currently reachable: `UseElement` is
  the only `UseNode` implementation and it holds its type outright, so there is no lazy hop to
  escape the guard. Latent, not live — an earlier draft of this report overstated it as "very
  likely affected". Any future lazy `UseNode` must override a `readWith` the same way.
- Origin: the lazy-subscript seeding was introduced so a later-added seed edge into the receiver
  would be visible after traversal (`LuaTypesVisitor.kt:722-723`). The laziness is what defeats the
  guard — the cycle is only reachable once resolution is deferred past the point where `visited`
  lives.

## 4. Other Notes

- **Fix direction**: the visited set must survive the lazy hop. Either thread it through the
  `ValueNode` branch (give `ValueNode` a `write(visited)` overload, with `LazyValueElement`
  forwarding it into `compute`), or hold the recursion guard outside the call chain — a
  per-resolution `ThreadLocal`/context object that lazy nodes join rather than reset. The first is
  narrower and keeps the guard explicit; the second also covers any future lazy node type.
- `resolveRead` needs no change today (see §3); revisit only if a lazy `UseNode` is ever added.
- **Regression test**: a unit test is possible without the corpus — construct a variable whose
  subscript seed resolves back to itself and assert `write == LuaGraphType.Undefined` instead of
  overflowing. Add it alongside the existing type-graph tests, then re-run the corpus gate.
- **Blocks MAINT-33 Phase 3**: the sweep cannot record an inspection baseline until this is fixed
  or the sweep tolerates per-file failures. Note the two type inspections were not previously
  exercised over a corpus — earlier sweeps measured only parse errors and `require` resolution,
  which is why this surfaced only now.
