---
id: "BUG-18"
title: "The rename detector's cancellation count depends on what other threads are doing"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-18: The rename detector's cancellation count depends on what other threads are doing

`LuaRenameConflictTest.testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall` fails
intermittently. The fault is in the test's counter. The production detector is correct.

## 1. Reproduction

Natural, on the libvirt builder, with no test code changed:

```bash
~/.cache/claude-scratch/lunar/bin/builder-run libvirt test --rerun --no-build-cache \
  --tests 'net.internetisalie.lunar.refactoring.*'
```

Forced, every time: hold a cancelled indicator on any other thread while the count is taken. On the
code before the fix, the detector's count falls from 4 to 0, and from 14 to 0 on the larger file.

## 2. Expected vs Actual
- **Expected**: the detector makes 4 checks on the small file and 14 on the large one, so the
  difference is 10 on every run.
- **Actual**: the difference is sometimes below 10 and the assertion at `LuaRenameConflictTest.kt:359`
  fails. CI's log gives only `junit.framework.AssertionFailedError at LuaRenameConflictTest.kt:359`.
  The message, and so the failing difference, is in the stored CTRF record and not in the log. It was
  not read for this report, because that store needs credentials this work did not have.

## 3. Context

| Where | Measure | Failed |
|---|---|---|
| `lunar-ci` | runs 31849 and 32066 failed this test; 168 `build-plugin` runs since 2026-09-03 include 16 failures of any cause | 2 known |
| Builder, unmodified `refactoring.*` tests | whole runs, this test failed | 1 of 6 |
| Builder, probe of 200 test instances, 4 vCPU | count taken straight after `configureByText`, as this test does | 10 of 100 |
| Builder, same probe | count taken after a first count | 17 of 100 |
| Builder, same probe with the JVM pinned to one core by `taskset` | either order | 0 of 200 |

The probe's failing counts were partial as well as zero: `4,1`, `4,4`, `4,6`, `4,13` and `1,14`. Pinning
hid the failure although the cancelled state was seen more often (731 sampler hits against 380), which
fits a race: the test thread and a background thread must run at the same time.

The other leads did not hold. A `StackWalker` reports virtual frames, and the count taken with the same
walker under an indicator was `4,14` on all 400 instances, so the stack is not what varies. Test order
and the two `configureByText` calls matter only as the source of the background work described below.

## 4. Root Cause
`ProgressManagerImpl`'s hook is reached directly only on one branch of
`CoreProgressManager.doCheckCanceled`, and the branch is chosen by `ourCheckCanceledBehavior`, a
JVM-wide static. While **any** thread is under a cancelled indicator it is `INDICATOR_PLUS_HOOKS`, and
that branch runs only the current thread's `progress.checkCanceled()`
(`CoreProgressManager.java:213-219`). The test thread had no indicator, so `progress` was null and the
check went uncounted. The same dispatch was read from GoLand 2026.2.3's bytecode, not only from the
`intellij-community` source.

The helper's KDoc assumed that only a cancelled indicator *on this thread* selected the other branch.

The fixture produces that state itself. A sampler of the static during the probe, recording the threads
`CoreProgressManager.isCanceledThread` reported, saw `CodeVisionHost.executeOnPooledThread`,
non-blocking read actions on `GoManualImportDetector`'s pool, inlay hint computations and the indexing
scanner. What cancels each of them was not traced. Each window is short, which is why the test usually
passed.

## 5. Fix Strategy
`detectorCancellationChecks` runs `collisions` under a live `StandardProgressIndicatorBase`.
`AbstractProgressIndicatorBase.checkCanceled` runs the hooks itself, so each check reaches the hook once
on either branch. `EmptyProgressIndicator` does not work: its `checkCanceled` is final and runs no
hooks. The helper also serves the two sibling `testCancellationIsChecked…` cases, and all three
thresholds are unchanged.

## 6. Test Strategy
`testCheckCountIsUnchangedByACancelledIndicatorOnAnotherThread` forces the race deterministically, by
holding a cancelled indicator on a pooled thread.

| Mutant | Result |
|---|---|
| Remove `ProgressManager.checkCanceled()` from `shadows`' filter | `testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall` fails: *…but cost 0, so the shadow rule scans a whole file's references…* |
| Remove the indicator from `detectorCancellationChecks` | `testCheckCountIsUnchangedByACancelledIndicatorOnAnotherThread` fails: *expected:<4> but was:<0>* |

In the 200-instance probe, the count under an indicator was `4,14` every time, in the same tests where
the old count failed 27 times. Eight natural runs of `refactoring.*` passed with the fix.

## 7. Same Exposure Elsewhere
Not changed here; BUG-20 fixes all three. `LuaColonMethodRenameTest.scanCancellationChecks` has the same shape as the old helper
(a hook, no indicator, a differential threshold), so it can fail the same way.
`LuaReceiverMemberCancellationTest.probeUnionDoor` also counts with a hook and no indicator. Neither
has been seen to fail. `LuaRenameTest.renameUnderHook` also uses a hook, and was not analysed.
