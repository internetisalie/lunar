---
id: "BUG-432"
title: "`resolveType` has no dumb-mode guard, so every call during indexing raises an IDE internal error"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-432: `resolveType` turns an indexing-in-progress signal into an IDE error report

Found by COMP-09 DR-10, which set out to establish what each resolution door does while the project
is dumb so the new index path could match it. Two of the three doors do something reasonable. The
third reports a bug to the user.

## Measured (2026-08-08, `CompNineDr10Test`, `DumbModeTestUtils.runInDumbModeSynchronously`)

```
--- SMART (baseline) ---
resolveGlobal(wx)                -> returned type
resolveType(wxFrame)             -> returned type
materialize(resolveGlobal(wx))   -> returned [wxFileExists, wxID_ANY]

--- DUMB (isDumb=true) ---
resolveGlobal(wx)                -> returned null
resolveType(wxFrame)             -> THREW TestLoggerAssertionError: Error resolving type wxFrame
materialize(resolveGlobal(wx))   -> returned null

completion  SMART  wx.  -> [wxFileExists, wxID_ANY]
completion  DUMB   wx.  -> []
```

## The defect

`LuaTypeManagerImpl:129` guards `resolveGlobal`:

```kotlin
if (DumbService.isDumb(project)) return null
```

`resolveType` (`:74-94`) has no such guard. It calls `doResolveType`, which queries `StubIndex` and
`FileBasedIndex`, and those throw `IndexNotReadyException` while indexing. Its catch block then does:

```kotlin
} catch (e: ProcessCanceledException) {
    throw e
} catch (e: Exception) {
    logError("Error resolving type $name", e)   // -> Logger.error(...)
    throw e
}
```

`logError` is `Logger.error` (`:532-540`). In a running IDE that is an **internal error report** —
the red notification and the exception-reporter dialog — not a log line. So a background inspection,
hover or gutter pass that touches `resolveType` during indexing tells the user the plugin has
crashed, when nothing has gone wrong at all: the indexes simply were not ready yet.

`IndexNotReadyException` is a control-flow signal, exactly like `ProcessCanceledException` — the
platform's own guidance is "change caller according to
`com.intellij.openapi.project.IndexNotReadyException` documentation", i.e. check `DumbService` first.
It is the one exception in that catch block that must not be logged.

## Why the test suite never caught it

Nothing exercises `resolveType` in dumb mode. A test that did would have failed loudly, because the
platform's `TestLogger` converts `Logger.error` into an assertion error — which is precisely how
DR-10 found it on the first run.

## Scope of the fix

Two parts, and the second matters more than the first:

1. Guard `resolveType` the way `resolveGlobal` is guarded, so it returns null while dumb.
2. Stop `logError` swallowing the distinction: `IndexNotReadyException` must be rethrown without
   logging, alongside `ProcessCanceledException`. A guard alone leaves the `Logger.error` reachable
   by any other index query that races the dumb check, and the catch block is shared.

~~`resolveModule` (`:96`) has the same catch shape and should be checked in the same pass.~~
**Wrong — see Fixed below.** It has no catch at all.

## Fixed (2026-08-08)

Both parts landed in `LuaTypeManagerImpl.resolveType`, and the second is not redundant with the
first — **mutation-proved**, by removing the guard and keeping the catch:

```
before the fix            TestLoggerAssertionError: Error resolving type Widget   <- the crash report
guard removed, catch kept IndexNotReadyException: Please change caller according to …
```

The failure mode changes from *"the plugin reported an error"* to *"the exception propagated"*. The
guard stops the exception being raised; the catch stops it being **reported** when dumb mode begins
between the check and the query, which the guard cannot prevent.

Regression tests: `LuaTypeResolutionDumbModeTest`. They can fail because the platform's `TestLogger`
turns `Logger.error` into a `TestLoggerAssertionError` — both dumb-mode tests were verified red on
the pre-fix code with exactly the message above, and the smart-mode test green throughout. These are
the first tests in the suite to exercise dumb mode at all, which is how this survived.

**One claim in this report was wrong.** `resolveModule` does *not* "have the same catch shape" — it
is `try/finally` with no catch, so there was nothing to fix there. It does reach `resolveType`
internally, so `testResolveModuleWhileDumbDoesNotReportAnError` failed before the fix and passes
after; it is kept to pin that `resolveModule` does not acquire a logging catch later.

Corpus baselines: not re-run, and not expected to move — smart-mode behaviour is unchanged and the
guard only affects dumb mode, which a corpus sweep does not enter. Full suite green (2 525 tests, 0 failures).
