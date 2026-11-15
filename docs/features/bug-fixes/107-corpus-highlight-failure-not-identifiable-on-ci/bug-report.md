---
id: "BUG-107"
title: "A corpus highlight failure on CI cannot be traced to its file or exception"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-107: A corpus highlight failure on CI cannot be traced

## 1. Reproduction
On PR 252's first CI run (head `10424504`, run 37405, corpus job 58130), the ratchet failed:
`inspection.highlightFailures: baseline 0 → observed 1` on zerobrane. A re-run of the job passed.
The same commit also passed `test -PwithCorpus --tests '*Corpus*'` on the libvirt builder.

## 2. Expected vs actual
- **Expected**: CI's log names the file and exception of every highlight failure, so a one-off
  can be diagnosed.
- **Actual**: `CorpusSweep.inspectionHits` prints them with `println`, which goes to the test's
  stdout. The CI job keeps only the Gradle console and the ratchet diagnostics, so neither the
  file nor the exception is recorded.

## 3. Root cause
`src/test/kotlin/net/internetisalie/lunar/corpus/CorpusSweep.kt`, `inspectionHits`: the
`catch (throwable: Throwable)` branch reports only to stdout, and the finding it records carries the
exception's simple name but no stack.

## 4. Fix strategy
Carry the file path, the exception class and its first stack frames in the finding, and print them
in the ratchet diagnostics that the CI job already writes. Then the zerobrane failure, if it recurs,
can be filed against its cause.

## 5. Test strategy
A sweep test whose file throws during highlighting asserts that the diagnostics contain its path
and the exception class.
