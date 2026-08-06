---
id: "BUG-404"
title: "Un-pinning a platform target to Auto never reflows to the runtime"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-404: Un-pinning a platform target to *Auto* never reflows to the runtime

Setting *Platform target* back to **Auto (from runtime)** drops the pin from `.idea/lunar.xml` but
leaves the whole project on the old pinned target — language level, stdlib and all. Re-running
Auto-Discover does not recover it.

Found re-running the TOOLING-08 VNC pass (2026-08-04), Scenario 1.3 **FAIL**.

## Reproduction

Observed live, then reproduced as a unit test with no IDE involved:

| step | target | language level | `explicitTarget` |
| :-- | :-- | :-- | :-- |
| 1. auto sync from a Lua 5.4.7 runtime | `Standard 5.4` | Lua 5.4 | false |
| 2. pin Redis `7+` | `Redis 7+` | Lua 5.1 | true |
| 3. un-pin to *Auto* | **`Redis 7+`** | **Lua 5.1** | false |
| 4. again, standing in for Auto-Discover | **`Redis 7+`** | **Lua 5.1** | false |
| 5. after clearing the guard (see below) | `Standard 5.4` | Lua 5.4 | false |

Step 3 is the defect; step 4 confirms it is **stuck, not stale**; step 5 identifies the mechanism.

The panel's *Resolved Runtime* block reports *Language level: Lua 5.4* under the caption "Reflects
applied settings", so the computed value and the applied state visibly disagree — that block is
recomputed by `recomputeRuntimeDisplay()`, while everything else reads `state.getTarget()`.

## Root cause

`LuaTargetSynchronizer.recompute()` short-circuits on a memoised runtime id:

```kotlin
val newId = resolved?.id
if (newId == lastAppliedRuntimeId) return
```

That guard asserts *"the runtime id has not changed since we last applied it"*. The thing it is used
to conclude is *"the applied target already reflects the runtime"* — and those are different claims.
While a platform target is pinned, the applied target diverges from the runtime **with the runtime id
untouched**, so on un-pin the guard suppresses precisely the recompute that was needed, and keeps
suppressing it on every subsequent request.

Confirmed behaviourally, not by reading: clearing `lastAppliedRuntimeId` and re-running the identical
call recovers the target (step 5 above), with nothing else changed.

Every listed symptom follows from this one cause — the disabled Version combo, the External Libraries
node and the language level all derive from `state.getTarget()`, which never reflowed.

## Why no test caught it

`LuaTargetSynchronizerTest` has seven tests covering this service, and **all of them clear the
guard**: `prepareBaseline()` and `pinExplicitTarget()` both end with `resetGuardForTest()`. The
sticky-guard path was untestable by construction — the helpers every test is built on removed the
state the defect lives in.

## Fix

`ensureSynchronized()` clears the guard before recomputing. The guard belongs to the *event* path,
where it debounces a stream of toolchain events; `ensureSynchronized` has exactly two production
callers — project startup (`LuaTargetSyncStartup`) and the settings panel's Auto branch
(`LuaProjectConfigurable:231`) — and both are explicit "make it so" requests, neither hot. The method
name already claimed this behaviour.

Rejected alternatives: resetting the guard from `LuaProjectConfigurable` (puts knowledge of the
synchronizer's internals in the UI layer), and dropping the guard entirely (it usefully avoids a
resolver call per toolchain event; `applyTarget` already no-ops on an unchanged target, so the guard
is a cheap early-out for the event path).

## Verification

- `LuaTargetSynchronizerTest."test un-pinning to auto reflows to the runtime"` — walks the pin →
  un-pin cycle and **never resets the guard**. Red before the fix, green after; the other six tests
  in the class pass throughout.
- Full suite green.
