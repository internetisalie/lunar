---
id: "BUG-439"
title: "A global's members declared in a *sibling* file are never offered — love2d's whole submodule API is unreachable"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-439: cross-file one-segment members of a global receiver are not enumerated

Found by COMP-09's **live IDE verification** (2026-08-14), while running human-checklist
scenarios 1.1 and 2.3 against real definition libraries. Neither scenario asked about this;
it was visible the moment a real library was loaded, and **no fixture in the feature could
have shown it** — see "Why every gate missed it".

## Reproduce

Two files in the same root:

```lua
-- probe_a.lua
---@class Probe
Probe = {}
function Probe.sameFileFn() end
Probe.sameFileVal = 1
```
```lua
-- probe_b.lua
Probe.otherFileVal = 2
function Probe.otherFileFn() end
Probe.nested = {}
```

`Probe.<caret>` offers **only `sameFileFn` and `sameFileVal`** — the two declared in the file
that also declares `Probe`. The three from `probe_b.lua` are absent.

The control that makes this attributable to the *file boundary* rather than to the assignment
form: `ngx.` **does** offer same-file `ngx.X = …` assignments (`AGAIN`, `CRIT`, `DEBUG`, …).
So plain assignment enumerates fine; crossing a file is what loses it.

## Consequence on shipped material

Measured on `LuaCATS/love2d` (pinned in `lunar-definitions-catalog.json`, sha256-verified):

- `love.` offers exactly the **40** members declared in `love.lua`, and **none of the 19
  submodules** — `love.graphics`, `love.audio`, `love.filesystem`, … each of which is a plain
  `love.graphics = {}` assignment in a sibling file.
- Typing `love.gr` collapses the popup to empty.
- `love.graphics.` yields nothing either, consistent with scenario 2.2's "`Foo.bar.` stays empty".

**So love2d's 100-function `love.graphics` API is unreachable by completion from either
direction**, on a library the product ships in its own catalog.

## Why every gate missed it

Scenario 2.2 expects `Foo.` to offer `[bar, direct]` and passes — but its fixture keeps every
declaration in **one** library file, so the cross-file case is untested. The same uniformity
that hid [[BUG-436]] (every fixture used `.lua`, so a non-`.lua` filter was invisible) hides
this: every fixture declares a receiver and its members together.

A subset defect is also invisible to the instruments this feature relies on — the golden diffs
for *added* rows, the corpus ratchet stops on *movement*, and COMP-09-06's acceptance is "if any
baseline moves, stop". Members that quietly fail to exist pass all three.

## Open question — regression or pre-existing? MEASURE IT, do not infer

**Do not take the following as settled.** The documentary evidence says pre-existing:
COMP-09 design §4.5's selection rule is *first-declaring-file-only within a scope-precedence
chain*, chosen explicitly to reproduce `typeOfGlobalIn`, after DR-09 measured that a flat
`membersOf(receiver, allScope)` union returned a **superset** (`[alsoPrivate,
privateToThisFile, real]` against a golden of `[real]`). On that reading the narrow answer is
deliberate and predates the feature.

But this feature has been burned three times by exactly that kind of reasoning. Settle it the
way [[BUG-435]] was settled: build a detached worktree at the **pre-COMP-09** commit
(`fb79c038` is before Phase 2; `87875c9f` is before Phase 3), load the same two-file probe, and
compare the offered set. If it is pre-existing, say so with the output pasted. If COMP-09
narrowed it, that is a regression against COMP-09-07 and the phase that did it must own the fix.

## What a fix has to reckon with

The narrow rule exists for a reason — DR-09's superset was real, and widening naively
re-introduces it, which COMP-09-06 forbids outright (BUG-395/397 reverted exactly that once).
So this is not "use `allScope`". A fix needs a rule that admits sibling declarations **within
the same library root / declaring scope** while still excluding the unrelated file-local `wx`
that DR-09 caught. Related: [[BUG-430]] (`a.b.c = v` flattens onto the root) is the nested-
qualifier half of the same area and may share a root cause.

Relevant: `LuaReceiverMemberIndex` (the receiver-keyed index), design §4.5/§4.5a, and
`LuaGlobalAssignmentIndex`, which selects the declaring file and is itself `.lua`-only
([[BUG-436]]).
