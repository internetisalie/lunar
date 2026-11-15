---
id: "MAINT-66"
title: "66: Root-Independent Inference Inside require Cycles"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-66: Root-Independent Inference Inside `require` Cycles

## Overview
Inside a genuine `require` cycle, a module built during another module's build reads that module's
half-built result, sees `any`, and the answer is cached as final: in the module's own snapshot
(`LuaTypes.kt:312`, `:317`) and in `LuaTypeManagerImpl`'s `moduleCache`
(`LuaTypeManagerImpl.kt:172`, `:281`, `:316`). A file's findings can therefore depend on which file
of the cycle was analysed first. BUG-102 §1.3 has a fixture that reproduces it, and BUG-102 §6
lists the design decisions: a cut marker scoped to the open build, the same rule at the four stores
and the `ResolveCache` guard sites, a per-root memo, and which view of a cycle member is the answer.

No measured corpus file shows this once BUG-102's resolver fix lands. MAINT-63's after-edit oracle
is the gate that would catch it there.

## Requirements
To be written with the `plan-feature` skill. BUG-102 §1.3 is the first acceptance fixture.
