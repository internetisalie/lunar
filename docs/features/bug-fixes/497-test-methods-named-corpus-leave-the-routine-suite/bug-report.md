---
id: "BUG-497"
title: "Test methods whose names contain `Corpus` leave the routine suite and join the corpus sweep's JVM"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-497: two `BaselineRatchetTest` methods never run in the routine suite

Found by [[MAINT-37]]'s Step 9 review (round 7). Same species as [[BUG-461]]: tests that look
covered and are not. BUG-461's guard does not see it, because these methods are collected; they
are filtered out by name.

## Reproduction

`build.gradle.kts` excludes the corpus sweeps from the routine suite with
`excludeTestsMatching("*Corpus*")` unless `-PwithCorpus` is set. Gradle matches that pattern
against the **method** name as well as the class name. `BaselineRatchetTest`'s KDoc says the class
is "deliberately **not** named `*Corpus*`" so that it runs in the routine suite, but two of its
methods are:

- `BaselineRatchetTest.absentCorpusFailsWithFetchInstruction` (`BaselineRatchetTest.kt:383`)
- `BaselineRatchetTest.tortureFalseAcceptsAreGatedUnlikeTheProjectCorpus` (`BaselineRatchetTest.kt:546`)

## Expected

Both run in the routine `test` task, like the rest of `BaselineRatchetTest`. Nothing outside the
sweep classes runs in the sweeps' JVM.

## Actual

Executed on `main` at `a3c32989` (2026-10-10, GCE builder), CI's corpus shape
`test --rerun --stacktrace -PwithCorpus --tests '*Corpus*'`. The resulting
`TEST-net.internetisalie.lunar.corpus.BaselineRatchetTest.xml` holds `tests="2"`, and exactly these
two:

```
testcase name="tortureFalseAcceptsAreGatedUnlikeTheProjectCorpus"
testcase name="absentCorpusFailsWithFetchInstruction"
```

So the two methods:

1. **never run in the routine suite or in CI's `build` job.** They are excluded by name, and CI's
   `build` job does not pass `-PwithCorpus`.
2. **run inside the sweeps' JVM in every corpus gate and every baseline record.** The baselines'
   contract is that recording and checking share one invocation shape ([[BUG-418]]), and
   `build.gradle.kts` records a measured case of a co-resident test class moving luacheck's
   `LuaTypeAssignability` by +12.

## Root cause

The `*Corpus*` naming rule is documented, in `BaselineRatchetTest`'s KDoc and in the comments
beside the exclusions in `build.gradle.kts`, as a rule about **class** names. The filter applies it
to **method** names too. Nothing checks either.

## Fix strategy

1. Rename both methods so neither contains `Corpus`, e.g. `absentManifestEntryFailsWithFetchInstruction`
   and `tortureFalseAcceptsAreGatedUnlikeTheProjectSweep`.
2. Add a guard in the same spirit as BUG-461's `UncollectedTestMethodGuardTest`. Any test method or
   class outside the sweep classes (`LuaCorpusSweepTest`, `LuaTortureCorpusTest`) whose name
   contains `Corpus` fails the routine suite. Prove it red with one of the old names restored.
3. Correct the KDoc and the `build.gradle.kts` comments to say the rule covers method names.

## Test strategy

- The guard, proved red then green.
- The routine suite's executed count rises by 2, and `BaselineRatchetTest` reports its full method
  count there.
- **Re-run CI's corpus shape and confirm every baseline is byte-identical.** Removing the two
  methods from the sweeps' JVM changes that JVM's contents, which [[BUG-418]] says can move counts.
  If a baseline moves, re-record it in the same change and say so.

## Resolution

- Renamed `BaselineRatchetTest.absentCorpusFailsWithFetchInstruction` to
  `absentManifestEntryFailsWithFetchInstruction` and
  `tortureFalseAcceptsAreGatedUnlikeTheProjectCorpus` to
  `tortureFalseAcceptsAreGatedUnlikeTheProjectSweep`.
- Added `SweepNamingGuardTest`: scans the compiled test classes and fails the routine suite if any
  test class or test method outside `LuaCorpusSweepTest` / `LuaTortureCorpusTest` has a name containing
  `Corpus`. Proved red with `absentCorpusFailsWithFetchInstruction` restored (message named
  `net.internetisalie.lunar.corpus.BaselineRatchetTest.absentCorpusFailsWithFetchInstruction`), then green.
- Corrected `BaselineRatchetTest`'s KDoc and the `build.gradle.kts` comments: the rule covers method names.
- Evidence (GCE builder): routine `test --rerun` BUILD SUCCESSFUL, `BaselineRatchetTest` ran 35 tests
  including both renamed methods; corpus shape `-PwithCorpus --tests '*Corpus*'` BUILD SUCCESSFUL with
  results only for `LuaCorpusSweepTest` and `LuaTortureCorpusTest`; a full baseline record in that shape
  left every file under `src/test/resources/corpus/` byte-identical.
