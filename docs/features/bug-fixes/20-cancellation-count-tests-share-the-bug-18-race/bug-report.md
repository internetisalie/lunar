---
id: "BUG-20"
title: "The sibling cancellation-count tests share BUG-18's race"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-20: The sibling cancellation-count tests share BUG-18's race

BUG-18 §7 named three tests that count `ProgressManager.checkCanceled()` through a
`CheckCanceledHook` as `LuaRenameConflictTest` did. The fault is in the tests' counters. The
production code is correct, and no threshold changed.

## 1. Reproduction

Forced, every time: hold a cancelled indicator on a pooled thread while the count is taken. Each
site has a test that does this (§5).

| Site | Under a cancelled indicator elsewhere, before the fix |
|---|---|
| `LuaColonMethodRenameTest.scanCancellationChecks` | the check count falls to 0 |
| `LuaReceiverMemberCancellationTest.probeUnionDoor` | the trace is empty, so the shape assertion fails |
| `LuaRenameTest` processor counts | the count **doubles**, 3 becomes 6 |

None has been seen to fail naturally.

## 2. Expected vs Actual
- **Expected**: a site's count is the same whatever other threads are doing.
- **Actual**: it depends on the JVM-wide `ourCheckCanceledBehavior`, as in BUG-18 §4.

## 3. Root Cause
BUG-18 §4 holds for the first two sites: with a cancelled indicator on any thread, a thread with no
indicator of its own makes checks no hook sees.

`LuaRenameTest` fails the other way. The rename runs under a `PotemkinProgress` that it installs
itself, so a test cannot give it an indicator. On the `INDICATOR_PLUS_HOOKS` branch
`AbstractProgressIndicatorExBase.checkCanceled` runs the hooks once for itself and once per delegate
(`ProgressWindow$MyDelegate`), so each check reaches the hook twice. A stack dump of the first two hits
showed the second one under `AbstractProgressIndicatorExBase.delegate`. The doubling can fail
`testCancellationIsCheckedPerUsageNotPerRename` when its two counts are taken in different states, for
example 6 for 3 usages and 8 for 8.

## 4. Fix Strategy
- `CheckCanceledProbe` (new, `src/test/.../testutil`) holds the two shared helpers from BUG-18,
  `underLiveIndicator` and `whileAnotherThreadIsUnderACancelledIndicator`, plus
  `isRepeatByIndicatorDelegate`. `LuaRenameConflictTest` now uses them.
- `scanCancellationChecks` and `probeUnionDoor` run under `underLiveIndicator`.
- `LuaRenameTest.calledDirectlyByTheProcessor`, which all three of its hooks use, ignores a hit made
  through an indicator delegate.
- **`LuaReceiverMemberCancellationTest`'s assertion changed with its instrument.** Under a live
  indicator the platform adds one probe after the last callback, so the trace of the mutant became
  `[1, 2, 3, 3]` and the old `size > distinct().size` check passed with the production line deleted.
  The assertion now looks only at probes below the final file count. Every earlier count is separated
  from the next by a callback, and the platform alone makes no repeat among them.

## 5. Test Strategy
Each site gained one test that takes the count undisturbed and again under a cancelled indicator on
another thread, and asserts they agree (the receiver test asserts the shape instead):

- `LuaColonMethodRenameTest.theScanCheckCountIsUnchangedByACancelledIndicatorOnAnotherThread`
- `LuaReceiverMemberCancellationTest.testTheUnionDoorProbeIsUnchangedByACancelledIndicatorOnAnotherThread`
- `LuaRenameTest.testProcessorCheckCountIsUnchangedByACancelledIndicatorOnAnotherThread`

| Mutant | Result |
|---|---|
| Delete the three `checkCanceled()` calls in `LuaColonMethodRename` (`undecidedIn`, `bracketOccurrences`) | `cancellationIsCheckedPerOccurrenceNotPerScanCall` fails |
| Delete `checkCanceled()` from `LuaReceiverMemberIndex.membersIn`' callback | both receiver tests fail, trace `[1, 2]` |
| Delete `checkCanceled()` from `LuaRenameProcessor.preparedUsageRewrites` | `testCancellationIsCheckedPerUsageNotPerRename` and `testCancellingMidRenameLeavesTheFileUntouched` fail |
| Delete `checkCanceled()` from `shadows`' filter in `LuaRenameConflictDetector` | `testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall` fails |
| Replace `underLiveIndicator` with a plain call, at each of the three sites that use it | the three `...CancelledIndicatorOnAnotherThread` tests fail |
| Drop the `isRepeatByIndicatorDelegate` guard | `testProcessorCheckCountIsUnchangedByACancelledIndicatorOnAnotherThread` fails |

The deleted-check mutant for the receiver test went green under the first version of the fix. That is
why the assertion changed (§4).

## 6. Same Exposure Elsewhere
None. `git grep` of `src/test` for `CheckCanceledHook`, `runWithHook` and `addCheckCanceledHook` finds
only the four classes above.
