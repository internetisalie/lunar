---
id: "BUG-501"
title: "Type-inspection results on annotated code vary between identical runs — lazy.nvim's diagnostics come and go from one JVM to the next"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-501: the same annotated code gets different type diagnostics on different runs

Found by [[MAINT-39]]'s prototype (risks-and-gaps DR-07). It blocks MAINT-39, because a corpus gate
on a count that moves between identical runs either flakes or gets disabled.

## Reproduction

Sweep `lazy.nvim` at tag `v11.17.5` (commit `85c7ff37`) as a corpus member, repeatedly, on the same
tree and builder (libvirt). The member-alone and full-shape sweep runs are described in MAINT-39's
DR-04, DR-07 and DR-10.

## Expected

Identical inspection counts on every run.

## Actual

Thirteen identical runs on the libvirt builder:
- `inspection.LuaTypeAssignability` read 447 six times, 445 three times, 443 twice, and 439 and 442
  once each.
- Three of the runs also read `inspection.LuaReturnTypeMismatch=54`, against `56` in the rest.
- Every other baseline line was identical across runs.

A per-hit dump from runs 4–6 isolates one varying pair. Runs 5 and 6 add, over run 4:

```
lua/lazy/view/render.lua LuaTypeAssignability @15167-15241
lua/lazy/view/render.lua @15171-15180 Too many arguments: expected at most 2, got 3
```

on `self:markdown(msg.msg, hls[msg.level] or "LazyTaskOutput", { indent = 6 })`. `M:markdown(str, hl,
opts)` (`view/render.lua:482`) takes three parameters and is annotated
`---@param opts? {indent?: number, …}`.

This is user-visible as well as a corpus problem: the same file can show an error in one IDE
session and not in the next.

## Not established

- **Mechanism.** The hypothesis is that the engine sometimes resolves a two-parameter signature for
  `markdown` alongside the three-parameter Lua declaration, and which one wins depends on order.
  [[BUG-498]] shows the same pattern: a class merge that is last-write-wins over index order,
  observed across JVMs. Whether this is BUG-498 or a sibling is open. The fix must establish it, for
  example by dumping the candidate signatures for `markdown` on a run with each outcome.
- **The `opts?` annotation.** It uses the optional-key table-type construct that MAINT-39's DR-05
  found mis-parsed elsewhere. No parse error is reported on line 482.
- **A second instance?** In one of four full-shape records with `lazynvim` added, `zerobrane.baseline`
  also moved (`LuaSuspiciousConcatenation` 21 → 19, MAINT-39 DR-08). It never moved in two control
  records without the member. It may be the same order dependence, or something else.

## Done means

Repeated sweeps of `lazynvim`, enough to have caught the variation above (MAINT-39 Phase 1's
re-measure step), give identical tallies. A regression test pins the order-independence of the
resolved signature.
