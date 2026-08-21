---
id: "BUG-444"
title: "`loadState` replaces the toolchain without retiring the cached PATH-prepend list"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-444: a reloaded toolchain keeps serving the previous toolchain's PATH

Found 2026-08-21 while investigating [[BUG-422]]. It is **not** that bug's cause — that turned out to
be the project-open health pass — but it is a real defect, independently reproducible, and it was
found by the same reading, so it is filed rather than discarded.

## Mechanism

`LuaExecutionEnvironmentBuilder` caches `pathPrependDirs()` and documents the contract: the cache "is
invalidated by the app-level `LuaToolchainListener.TOPIC`". Every mutator on `LuaToolchainRegistry`
and `LuaToolchainProjectSettings` honours that — `setBinding` and `registerProvisioned` publish on
*change*, which is correct, since no change means no staleness.

`loadState` does not. It replaces the entire state — every tool, every binding — under the state lock
and publishes nothing:

```kotlin
override fun loadState(state: LuaToolchainAppState) {
    synchronized(stateLock) { myState = state }
}
```

So a list computed before the reload survives it and is **served**. An empty list is a legitimate
value, so the caller gets "no PATH entries" as an *answer* rather than a recomputation, and
`LuaLaunchEnvironment.applyPath` then returns early and leaves `PATH` untouched entirely.

## Impact

`loadState` is the platform's own state-loading entry point: project open, an external `.idea` edit,
a VCS branch switch, a settings import or sync. After any of those, everything downstream of the
builder — `LuaRunConfiguration`, `LuaTestCommandLineState`, `LuaRocksRunConfiguration`,
`LuaInterpreterCommandLines.forProject`, and the terminal customizer — is handed a `PATH` built from
the *previous* toolchain, or no `PATH` entry at all.

It also weakened the test suite's isolation: `ToolchainSettingsTestCase.resetState` and
`LuaTestRunnerTest.resetToolchain` reset the toolchain **exclusively** through `loadState`, so the
reset whose whole purpose is to isolate one test class from the next could not clear this cache.

## Fix — a generation stamp, not another event

`LuaToolchainRegistry` and `LuaToolchainProjectSettings` each expose a monotonic `stateGeneration()`
bumped by `loadState`; the builder stamps its cached list with the pair and serves it only while both
still match. The existing topic subscription and the `cacheEpoch` guard stay — they cover the
in-flight-resolve race, which the stamp does not.

**Publishing from `loadState` was considered and rejected.** It is the obvious fix and it is unsafe:
this topic's listeners include `LuaToolHealthMonitor` and `LuaTargetSynchronizer`, and firing into
them while the platform is loading persisted component state invites background probing and settings
reentrancy at project-open time. The stamp has no such reach, and it is strictly more robust — it
retires the cache after *any* unpublished mutation, including ones added later.

## Verification

`LuaExecutionEnvironmentStaleStateTest`, both halves confirmed red before the fix and green after:

| test | without the fix |
| :-- | :-- |
| `testLoadStateRetiresTheCachedPathPrependDirs` | `AssertionFailedError` — the stale empty list is served |
| `testAStaleEmptyListWouldLeavePathUnset` | `AssertionFailedError` on `assertNotNull` — `PATH` is null |

Note the first of those, run under the **full suite** before [[BUG-422]] was fixed, failed at a
*different* assertion than it does in isolation — that discrepancy is what exposed BUG-422's real
cause. The two defects are independent but they share a symptom, an empty prepend list, which is why
one masked the other.
