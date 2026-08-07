---
id: "BUG-422"
title: "`LuaInterpreterCommandLinesTest` PATH-prepend test is flaky under the full suite"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-422: PATH-prepend assertion fails intermittently

`LuaInterpreterCommandLinesTest.testForProjectResolvesRuntimeAndAppliesEnvironment` failed once
during the ktlint adoption gate (2026-08-07) with:

```
junit.framework.AssertionFailedError: expected the runtime dir prepended to PATH
```

then passed on an immediate re-run with **no code change**.

## Why it is not the change it appeared during

It surfaced in the middle of a 905-file reformat, which is exactly when a real regression would be
suspected. It is not one:

| run | change since previous | result |
| :-- | :-- | :-- |
| A — immediately after the reformat | — | 2435 / 0 failures |
| B — after 10 hand-fixes for residual lint | none near `toolchain/exec` | **1 failure** |
| C — re-run | **none at all** | 2435 / 0 failures |

And the three files that actually implement this behaviour — `LuaInterpreterCommandLines.kt`,
`LuaExecutionEnvironmentBuilder.kt`, `LuaLaunchEnvironment.kt` — are **token-identical** to their
pre-reformat versions (compared with all whitespace and commas stripped), so the reformat changed
their formatting and nothing else.

## Suspected mechanism

`LuaLaunchEnvironment.applyPath` returns early when `pathPrependDirs` is empty:

```kotlin
private fun applyPath(commandLine: GeneralCommandLine) {
    if (pathPrependDirs.isEmpty()) return
    …
}
```

so an empty prepend list leaves PATH as whatever the parent process carries, and the assertion
`path.startsWith("/opt/lua/bin")` fails rather than erroring. The prepend list comes from
`LuaExecutionEnvironmentBuilder`, which is **cached per project** — the test's own `setUp` calls
`invalidate()`, which is itself evidence that stale cached state is reachable here. That points at
cross-test state or ordering: the test passes in isolation and nearly always in the suite, and fails
when something earlier leaves the builder or the toolchain registry in a state its `setUp` does not
fully reset.

**Unverified**: the specific interaction has not been reproduced deliberately. Recorded as a
hypothesis, not a conclusion.

## Hardening applied 2026-08-07 — but this bug stays OPEN

While fixing [BUG-410](../../../roadmap.md) (a prewarm publishing after the invalidation that was
meant to retire it), the same shape turned up here and has been guarded the same way:

```kotlin
cachedPathPrependDirs?.let { return it }   // an EMPTY list is non-null — so it is SERVED
val dirs = …resolve every tool kind…       // invalidate() can land in here
cachedPathPrependDirs = dirs                // …and this stale write then wins
```

`pathPrependDirs()` is an unsynchronized read-compute-write. A caller that began resolving before
an invalidation published its result afterwards, and because an empty list is non-null it was then
returned as a cached *answer* rather than recomputed — so a resolve that happened to see no bound
toolchain could pin "no PATH entries" until the next invalidation. `applyPath` returns early on an
empty list, which is exactly the observed symptom. `LuaExecutionEnvironmentBuilder` now carries a
generation counter bumped by `invalidate()`, and a resolve publishes only if its generation is still
current.

**This is not a claim that the flake is fixed.** The race above was found by reading, not by
reproducing — unlike BUG-410, whose reproduction was forced with a latch and which failed with the
original signature before the fix and passed after. There is no seam to pause a resolve mid-flight,
and adding one solely to demonstrate my own hypothesis would prove nothing about the original
failure. So:

- the guard is justified on its own merits (a stale write here is *served*, not merely missed);
- whether it removes the observed flake is **unverified**;
- this stays open until the test either runs clean over a meaningful number of full-suite runs, or
  fails again — in which case the recorded evidence here is the starting point, not folklore.

## Fix direction (if it recurs)

Reproduce first — run the suite with a fixed seed / ordering until it fails, then bisect the
preceding class. Do not "fix" it by adding another `invalidate()` call until the leak is identified;
that would hide the ordering dependency rather than remove it.

## Why file it

Same reasoning as **BUG-410** (`RockspecSourcePathProviderTest`, recorded in
[`docs/roadmap.md`](../../../roadmap.md)), which is the other known flake: a suite that fails once in
a while trains people to re-run instead of read, which is
exactly how a real regression gets waved through. Two independent flakes now argue for a broader
look at test isolation rather than two separate patches.
