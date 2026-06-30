---
id: "BUG-BASELINE-TESTS"
title: "Pre-existing baseline test failures (3, non-ROCKS)"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# Bug Report: Pre-existing baseline test failures

## Context

Discovered while recording the pre-run test baseline for the Wave-13 ROCKS
implementation (HEAD `9d92a20f`, after ROCKS-09). **None of these are caused by
ROCKS-09** (all are non-rocks tests), and — corrected from an earlier loose claim —
**none are "environment-related"**; all three are stale-assertion / logic
regressions from other agents' recent work.

> **Correction on the count.** An initial full-suite run reported *8* failures, but
> that run was **killed mid-execution** (`Canceling supervisor scopes: Shutdown is
> in progress: either SIGTERM or SIGKILL is caught`). Five of those were
> `RuntimeException → java.io.IOException` teardown artifacts of the interrupted
> JVM, not real failures. A clean isolated re-run of the six suspected classes
> reported **`41 tests completed, 3 failed`** — the three genuine failures below.

Reproduce the genuine set with:
```bash
export JAVA_HOME=/home/mini/.jdks/corretto-21.0.10
./gradlew test --offline \
  --tests "*TargetTest*" \
  --tests "*LuaBraceMatchingTest*" \
  --tests "*LuaTestRunConfigurationTest*"
# => 3 failed
```

---

## BUG-350 — `TargetTest.testStandardLua55FallsBackToLua54` is stale after Lua 5.5 support

- **Reproduction**: `./gradlew test --offline --tests "*TargetTest*"`
- **Expected vs Actual**:
  ```
  org.opentest4j.AssertionFailedError: expected: <Lua 5.4> but was: <Lua 5.5>
      at TargetTest.kt:36
  ```
- **Root cause** (grounded): `src/test/.../platform/target/TargetTest.kt:34-36` asserts
  `Target(STANDARD, VersionEntry("5.5","lua-5.5")).getImplicitLanguageLevel() == LUA54`,
  encoding the **pre-5.5** assumption that a 5.5 target falls back to 5.4. **SYNTAX-09
  (Lua 5.5 Support)** — recently committed (`feat(syntax-09): implement Lua 5.5 parsing…`)
  — added `LuaLanguageLevel.LUA55`, so `getImplicitLanguageLevel()` now correctly returns
  `LUA55`. The production change is correct; the **test is stale**.
- **Fix strategy**: update the test to expect `LuaLanguageLevel.LUA55` and rename it
  (e.g. `testStandardLua55ResolvesToLua55`). Confirm with SYNTAX-09's owner that 5.5 is
  intended to no longer fall back.
- **Test strategy**: the corrected assertion *is* the regression test; add a sibling
  asserting an unknown future version (e.g. `5.6`) still falls back to the latest known level.

## BUG-351 — `LuaBraceMatchingTest.testBracePairs` asserts unprefixed token strings

- **Reproduction**: `./gradlew test --offline --tests "*LuaBraceMatchingTest*"`
- **Expected vs Actual**:
  ```
  org.opentest4j.AssertionFailedError:
   expected: <[((, )), ([, ]), ({, }), (repeat, until), (do, end), (function, end), (if, end)]>
   but was:  <[(LuaTokenType.(, LuaTokenType.)), (LuaTokenType.[, LuaTokenType.]), … ]>
      at LuaBraceMatchingTest.kt:40
  ```
- **Root cause** (grounded): `src/test/.../lang/syntax/LuaBraceMatchingTest.kt:29-40`
  builds `actual` from `it.leftBraceType.toString()` (line 39), and per the documented
  invariant in `CLAUDE.md`/`AGENTS.md` *Lessons Learned* — "**Token strings:**
  `IElementType.toString()` **is prefixed** (e.g. `LuaTokenType.(`); assert on that exact
  format" — `.toString()` yields the `LuaTokenType.`-prefixed form. The test's `expected`
  set (lines 29-37) uses bare punctuation/keywords. The **test contradicts the documented
  behavior**; production is correct.
- **Fix strategy**: either (a) assert on the prefixed strings (`"LuaTokenType.("` …), or
  preferably (b) compare the `IElementType` instances directly (e.g. against
  `LuaTokenTypes`/`LuaElementTypes` constants) instead of `.toString()`, which is robust to
  display-format changes.
- **Test strategy**: option (b) becomes the regression test; it pins the 7 structural pairs
  (`(`/`)`, `[`/`]`, `{`/`}`, `repeat`/`until`, `do`/`end`, `function`/`end`, `if`/`end`)
  by element type.

## BUG-352 — `LuaTestRunConfigurationTest.testProducerFromContextBustedDescribe` gets FILE, expects PATTERN

- **Reproduction**: `./gradlew test --offline --tests "*LuaTestRunConfigurationTest*"`
- **Expected vs Actual**:
  ```
  junit.framework.ComparisonFailure: expected:<[PATTERN]> but was:<[FILE]>
      at LuaTestRunConfigurationTest.testProducerFromContextBustedDescribe(LuaTestRunConfigurationTest.kt:102)
  ```
- **Root cause** (grounded): `src/test/.../run/test/LuaTestRunConfigurationTest.kt:94-104`
  builds a `ConfigurationContext` on a busted `describe` element and asserts the produced
  config's `testTargetType == "PATTERN"` (a single test-block target). `LuaTestRunConfigurationProducer.createConfigurationFromContext`
  (RUN-05) instead yields `"FILE"` — i.e. it does **not** recognize the `describe`/`it`
  block to scope the run to a pattern, falling back to whole-file. Unlike BUG-350/351 this
  is **either a stale test or a real producer gap**; needs the RUN-05 owner to confirm
  intent (was per-block `PATTERN` scoping descoped, or is the producer under-detecting?).
- **Fix strategy**: TBD pending intent —
  - if per-block scoping is intended: fix `LuaTestRunConfigurationProducer` to set
    `testTargetType = "PATTERN"` + the block name for a `describe`/`it` context;
  - if descoped: update the test to expect `"FILE"` and the file-level name.
- **Test strategy**: whichever path, assert the produced `testTargetType`/`testTarget`/`name`
  for a `describe`-context produce.

---

## Related (non-test) baseline finding — build was RED on a stale artifact

Not a test failure, but found in the same baseline: `./gradlew build`/`:jar` failed with
`Entry net/internetisalie/lunar/project/PlatformLibraryIndex.class is a duplicate`. Source
defines `PlatformLibraryIndex` **once** (Kotlin, `project/PlatformLibraryProvider.kt:105`),
but a stale `build/classes/java/main/PlatformLibraryIndex.class` (Java→Kotlin conversion
leftover) collided in the jar. `./gradlew clean` clears it; green afterward. Consider a
`duplicatesStrategy`/clean-on-conversion guard so incremental builds don't resurrect it.

---

## Appendix — raw current output (clean isolated rerun, HEAD `9d92a20f`)

```
> Task :test
TargetTest > testStandardLua55FallsBackToLua54() FAILED
    org.opentest4j.AssertionFailedError: expected: <Lua 5.4> but was: <Lua 5.5> at TargetTest.kt:36
LuaBraceMatchingTest > testBracePairs() FAILED
    org.opentest4j.AssertionFailedError: expected: <[((, )), …]> but was: <[(LuaTokenType.(, …)]> at LuaBraceMatchingTest.kt:40
LuaTestRunConfigurationTest > testProducerFromContextBustedDescribe FAILED
    junit.framework.ComparisonFailure: expected:<[PATTERN]> but was:<[FILE]> at LuaTestRunConfigurationTest.kt:102

41 tests completed, 3 failed
```

> Note: a prior full-suite run reported 8 failures; 5 (`LuaTypeAssignabilityTest`,
> `LuaNavigationTest` ×2, `LuaMarkdownDocumentationTest` ×2 — all `RuntimeException →
> IOException`) were SIGTERM teardown artifacts of an interrupted run and do **not**
> reproduce in isolation.
