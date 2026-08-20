---
id: "BUG-442"
title: "`LuaTestRunnerTest.testBustedPathPrependedFromEnvBuilder` throws an NPE intermittently under the full suite"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-442: a second intermittent PATH/env test, and it is NOT [[BUG-422]]

Observed 2026-08-20 during BUG-436's verification.

```
LuaTestRunnerTest > testBustedPathPrependedFromEnvBuilder() FAILED
    java.lang.NullPointerException
```

No message, no assertion text — a bare NPE out of `LuaTestCommandLineState(config, env).buildCommandLine()`
(`LuaTestRunnerTest.kt:414-430`), which binds a temp-dir `busted` executable and asserts it lands in
`commandLine.exePath`.

## Why this is filed separately from BUG-422

The resemblance is close enough to be a trap. [[BUG-422]] is
`LuaInterpreterCommandLinesTest.testForProjectResolvesRuntimeAndAppliesEnvironment` failing an
**assertion** — `expected the runtime dir prepended to PATH` — and its report explains the mechanism
(an empty prepend list leaves `PATH` as the parent process's, so the assertion fails rather than
errors). This is a **different class** throwing a **different failure mode**: an NPE, i.e. something
is null that the code does not expect to be null, which BUG-422's analysis does not cover.

Folding the two together on the strength of the shared word "prepend" would record a mechanism that
has not been measured for this one. They may share a root cause; that is a hypothesis, not a finding.

## Intermittency — measured, not assumed

| run | result |
| :-- | :-- |
| full suite + corpus, 2026-08-20 | **FAILED** (NPE) |
| full suite, same tree, immediately after | **passed** |
| `--tests '*LuaTestRunnerTest*'` in isolation | **passed** |
| full suite earlier the same day, essentially the same tree | **passed** |

So it is intermittent under full-suite conditions and green in isolation — the same *shape* as
BUG-422's flakiness even if the failure mode differs.

## Why it matters

Between this and BUG-422 there are now two intermittent env/PATH tests, and each costs a full-suite
re-run to attribute — BUG-436's verification spent one 8-minute run doing exactly that, and BUG-441's
spent another on BUG-422. A flaky gate is a tax on every change that has to pass it, and two of them
in the same area is worth one investigation rather than two attributions per week.

## First thing to try

Capture the stack. The XML records only `java.lang.NullPointerException` with no frames surfaced in
the summary; the full `<failure>` body in
`build/test-results/test/TEST-…LuaTestRunnerTest.xml` on the builder will name the null. Run the full
suite (not the class alone) until it reproduces, then read that element rather than re-deriving from
the source.
