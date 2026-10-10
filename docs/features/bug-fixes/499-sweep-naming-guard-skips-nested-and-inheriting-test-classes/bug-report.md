---
id: "BUG-499"
title: "The sweep naming guard skips nested test classes and classes that only inherit their tests"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-499: `SweepNamingGuardTest` misses two class shapes

Found by [[MAINT-37]]'s Step 9 review (round 13), reading the guard [[BUG-497]] added.

## Reproduction

`SweepNamingGuardTest` fails the routine suite when a test class or test method outside the sweep
classes contains `Corpus`, because Gradle's `excludeTestsMatching("*Corpus*")` would silently move
it out of the routine suite. Two shapes escape it:

1. **A nested test class.** `classNamesUnder` drops every class name containing `$`
   (`SweepNamingGuardTest.kt:121`), so a nested class such as `Outer$CorpusCase` holding tests is
   never examined. JUnit still runs nested test classes (a static nested JUnit3 class runs under
   the vintage engine), and Gradle's filter matches their names.
2. **A class that only inherits its tests.** `testMethodNames` reads `declaredMethods` only
   (`:85-89`), so `class FooCorpusTest : SomeBaseTest()` with no tests of its own reports none.
   `filteredNames` then returns early for a class with no test methods (`:76-80`), so its name is
   never checked. Gradle runs the inherited tests under `FooCorpusTest`'s name, and the filter
   matches it.

## Expected

Both shapes fail the guard, like a top-level class with its own test methods.

## Actual

Both pass. This is from reading the code; no fixture of either shape has been run.

## Fix strategy

- Stop dropping `$` names. Skip only anonymous and synthetic classes, and decide nested classes by
  whether they hold tests.
- Collect test methods through the class hierarchy (`methods` plus inherited non-public ones, or
  walk `superclass`), not `declaredMethods` alone.

## Test strategy

Two cases, alongside the guard's existing cases in its own test class: a nested test class whose
name contains `Corpus`, and a class that only inherits its tests. Both must be reported. Prove each
red against the current code before fixing.
