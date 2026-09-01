---
id: "TYPE-12"
title: "12: A method call site mints its own member demand, and every one is wired into a clique"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-12: The member-demand clique

## Overview

`LuaTypesVisitor.kt:926-929` mints a **structurally identical** member demand for every method call
site on a receiver, and `checkTableCompatibility` wires each one bidirectionally into the receiver's
node. N call sites on one `---@class` therefore produce N indistinguishable nodes and an N-way
clique, and the fixed-point checker walks the resulting Θ(n²) cross-product.

This is the residual [[BUG-473]] left after both of its phases, and it is deliberately **not** filed
as a bug: nothing here is incorrect. The engine infers what it should; it just represents one demand
N times.

## Measured (BUG-473 DR-8 and Phase 2, libvirt builder `debian13`)

BUG-473's two phases removed the *redundant re-derivation* over that cross-product — a walk-root
memo (Phase 1) and a per-visit `write` hoist (Phase 2) — taking an annotated 160-call file from
145 s to 3.6 s. What they did not remove is the cross-product itself:

| quantity | after both phases |
| :-- | :-- |
| n = 160 annotated | 3 556 ms, ~15x its tag-free control |
| growth per doubling | ×5.9 — still above linear |
| cost attributable to the clique | ~2.8 s at n = 160 (DR-8's frozen-memo arm) |
| live IDE, 320 call sites | highlighting completes in 20–45 s (DR-7) |

## The constraint any design must clear

**Every cheap bound on the clique that BUG-473 measured changes what the engine infers.** The
mildest — dropping `checkTableCompatibility`'s reverse member edge — is also the fastest fix the
report found (240 ms at n = 160) and it **deletes the user-visible `---@param` violation on every
method call**, reintroducing BUG-419's defect. That candidate is now pinned as a red test in
`LuaAnnotatedClassDiagnosticsTest` (mutation M-C).

So this is a representation change — deduplicating structurally identical member demands, or
resolving a member against the class once rather than per call site — not an edge-pruning
optimisation. It needs planning before implementation.

## Standing instruments inherited from BUG-473

Any change here is already gated: `LuaTypeGraphRootResolutionBudgetTest` (root-resolution budgets),
`LuaAnnotatedClassDiagnosticsTest` (the exact diagnostic multiset), `LuaTypePairWriteDifferentialTest`
(the per-visit hoist's equivalence), and the `-PwithCorpus` annotated fixture lane from
[[MAINT-39]] / BUG-473 DR-6.

## Requirements

| ID | Requirement | Priority | Status | Description |
| --- | --- | --- | --- | --- |
| TYPE-12-01 | Structurally identical member demands on one receiver must not each mint a distinct node | M | Not Implemented | The Θ(n²) cross-product's source (`LuaTypesVisitor.kt:926-929`) |
| TYPE-12-02 | Analysis cost of an annotated file must grow no faster than linearly in call-site count | M | Not Implemented | Currently ×5.9 per doubling |
| TYPE-12-03 | The diagnostic multiset must be unchanged at every fixture size | M | Not Implemented | Gated by `LuaAnnotatedClassDiagnosticsTest`; BUG-419's rule |
