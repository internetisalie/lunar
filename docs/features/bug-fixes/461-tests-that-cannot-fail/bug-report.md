---
id: "BUG-461"
title: "Tests that cannot fail — four methods no engine collects, and three that pin defects as intended behaviour"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-461: coverage that asserts nothing

Assembled 2026-08-22 from findings across the retroactive-requirements sweep. Filed as one report
because these share a consequence — a green suite that means less than it appears — and because
this repo already carries two entries of the same species in its own guidance.

## 1. Four test methods are never collected

`BaseDocumentTest` is a plain `open class`, not a JUnit3 `TestCase`, so its subclasses' methods are
collected **only** by `@Test` annotation. These four have none:

| File | Method |
| :-- | :-- |
| `TestLuaFormatBlock.kt:195` | `testArgs` (also carries `@Ignore`) |
| `TestLuaFormatBlock.kt:316` | `testLocalVarDecl` |
| `LuaFoldingTest.kt:10` | `testFolding` |
| `CrossFileInlayHintsTest.kt:4` | `testCrossFileParameterHints` |

They neither run **nor report as skipped**, so from a file listing or a coverage summary the area
reads as covered. `testArgs` is the clearest case: it sets `CONTINUATION_INDENT_SIZE = 4` and
expects 4 columns, while production actually yields the platform default of 8 — a real defect the
test would have caught had anything run it.

A repo-wide sweep found exactly these four; every other `test*` method lives in a JUnit3 class where
the name alone is sufficient. **The sweep is worth keeping as a build-time check** — see §4.

## 2. Three tests pin a defect as intended behaviour

These pass *because* the code is wrong, so fixing the code turns them red and the fix looks like a
regression:

- **`LuaCheckInvokerClassifyTest` TC11** asserts `exit 2` maps to `CRASHED`. Exit 2 is luacheck's
  "errors were reported" — see [[BUG-452]].
- **`LuaCheckCommandLineTest` TC1** seeds `--std max` and asserts the parameter list *contains* it.
  It does — followed by `--std lua54`, which wins. See [[BUG-451]].
- **`TestLuaFormatBlock.testLabel`** asserts `::start::` sits at column 0 inside an `if` body,
  which is the `Indent.getAbsoluteLabelIndent()` defect, not a requirement.

Each is a legitimate characterization of *current* behaviour. The problem is that none says so, so
a later reader cannot tell a pinned defect from a specified requirement.

## 3. Two tests assert something that cannot be false

- **`TestLuaLineBreakpointHandler`**: its only assertion is `assertNotNull(handlerClass)` — a class
  object is non-null. The entire `canPutAt` contract is untested.
- **`LuaCheckInvokerClassifyTest` TC12** feeds the parser hand-written *plain-formatter* output that
  production never requests, so it proves the parser handles a format Lunar does not ask for. See
  [[BUG-453]].

## 4. Fix strategy

**The four uncollected methods**: add `@Test`, then *run them* — expect some to fail, because they
were written against behaviour nobody checked. `testArgs` is expected to fail. That is the point:
each failure is a defect the suite was believed to cover.

**Add a build-time guard.** A `test*`-named method in a non-JUnit3 class with no `@Test` should fail
the build, not pass silently. This is the cheap, durable half of the fix.

**The three pinning tests**: leave the assertions, but rename or annotate each to say it pins known
defective behaviour and cite the bug. Then, when the defect is fixed, the test is meant to change.

**`TestLuaLineBreakpointHandler`**: replace the non-null assertion with `canPutAt` cases.

## 5. Why this is filed rather than fixed in passing

Each item is small; the pattern is not. This repo's guidance already records two instances —
`gradlew test` served `FROM-CACHE` without `--rerun` (a full pass executing nothing), and
`ktlintFormat` on the builder followed by `ktlintCheck` (a gate that reformatted the VM's copy and
checked *that*). Both were discovered the same way: someone asked what a green result actually
proved.

The durable output of this report is not the seven fixes. It is §4's build-time guard plus the habit
of asking, of any new test, what change would make it red.
