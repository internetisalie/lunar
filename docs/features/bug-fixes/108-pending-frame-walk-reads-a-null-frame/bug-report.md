---
id: "BUG-108"
title: "Highlighting intermittently throws a NullPointerException from LuaSourceSetTracker.collectPending"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-108: Highlighting intermittently throws a NullPointerException from the pending-frame walk

## 1. Reproduction
Intermittent; seen three times on the zerobrane corpus member, each as
`inspection.highlightFailures: baseline 0 → observed 1`: PR 252's first CI run, a BUG-81 builder run
and a BUG-75 builder run (`cfg/eclipse-keys.lua`). The BUG-75 run's test stdout carries the trace:

```
PluginException: Inspection tool 'LuaSuspiciousConcatenation' … thrown exception from its buildVisitor()
Caused by: java.lang.NullPointerException: Cannot invoke "…LuaTypeSourceRecorder$SourceFrame.getUnreplayedWarm()" because "frame" is null
	at …LuaSourceSetTracker.collectPending(LuaSourceChangeTracker.kt:132)
	at …LuaSourceSetTracker.getModificationCount(LuaSourceChangeTracker.kt:119)
	at …LuaTypesSnapshot$Companion.forFile(LuaTypes.kt:333)
```

## 2. Expected vs Actual
- **Expected**: reading an unpinned snapshot's modification count never throws.
- **Actual**: the walk occasionally takes a `null` frame from its work queue, and the whole
  highlighting pass for the file fails with an IDE error.

## 3. Root Cause
`LuaSourceSetTracker.collectPending` (`LuaSourceChangeTracker.kt`) queues each frame's
`pendingFrames` with `work.addAll(frame.pendingFrames)`. `pendingFrames` is a concurrent set that
another build adds to while it is open (`LuaTypeSourceRecorder.kt:217`). Kotlin's
`ArrayDeque.addAll` copies the elements through an iterator and then grows its size by
`elements.size`, read again after the copy (`ArrayDeque.copyCollectionElements`). When the set grows
between the two, the deque counts slots it never filled, and `removeLast()` returns `null`.

## 4. Fix Strategy
Queue a snapshot: `work.addAll(frame.pendingFrames.toList())`. A concurrent set's `toList()`
iterates once and sizes the list from what it copied, so the count and the contents agree. The
initial queue is already built from a list (`LuaSourceChangeTracker.kt:87`).

## 5. Test Strategy
The defect needs another thread to add to the set inside one `addAll` call, which a unit test cannot
schedule deterministically. The corpus sweep (`test -PwithCorpus`) is where it surfaced; the fix is
verified by full corpus runs with `highlightFailures` at its baseline.
