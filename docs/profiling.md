---
id: "PROFILING"
title: "Profiling Lunar — attributing time to frames"
type: "guide"
priority: "medium"
folders:
  - "[[features]]"
---

# Profiling Lunar

Lunar can *detect* a performance defect and, until [[MAINT-38]], could not *diagnose* one. Three
suites assert wall-clock budgets (`GlobalSymbolCompletionPerformanceTest`,
`GlobalSymbolPerformanceOptimizationTest`, `LuaUnionDistributionBenchmarkTest`) and none of them
caught [[BUG-473]], because a budget says *that* something is slow and never *where*.

This page is the recipe for getting a per-frame answer. It uses **Java Flight Recorder**, which
ships with the JDK the build already runs on — no dependency, no agent, no provisioning.

`LuaTypeSourceRecorder` is **not** a profiler and should not be reached for as one: its
`SourceFrame` records which files and index keys a snapshot *consumed*, which answers a
cache-correctness question, not a timing one.

## Record

```bash
tooling/gce-builder/gce-builder.sh run "test -PjfrProfile --rerun --no-build-cache"
```

`-PjfrProfile` is the whole switch. Without it the test JVM gets no flight-recording argument at
all, so the routine loop pays nothing; `JfrRecordingFlagTest` asserts that both directions of that
hold from inside the forked JVM.

Narrow the run to the code you care about — a whole-suite recording is mostly platform startup:

```bash
tooling/gce-builder/gce-builder.sh run \
  "test -PwithPerf -PjfrProfile --tests '*LuaClassTagSnapshotPerformanceTest*' --rerun --no-build-cache"
```

| Form | Event set | When |
| :-- | :-- | :-- |
| `-PjfrProfile` | `profile` — 10 ms execution sampling, allocation sampling, full lock/IO events | the default; what you want for "where does the time go" |
| `-PjfrProfile=default` | `default` — 20 ms sampling, no allocation profiling, ~1 % overhead | long or throughput-sensitive runs where `profile` would distort the thing being measured |

Two non-obvious flags come with the gate and are load-bearing:

- **`filename=` points at the `build/jfr` *directory*, not a file.** The JVM then mints
  `hotspot-pid-<pid>-id-<n>-<timestamp>.jfr` per process, so forked test JVMs cannot overwrite one
  another's recording.
- **`stackdepth=1024`** (JFR defaults to 64). PSI and type-engine stacks are far deeper than 64
  frames, and a truncated stack drops the *caller* frames — which are exactly what distinguishes
  "called many times" from "one call that is internally slow". A profile taken at the default depth
  will not answer the question you took it for.

`-XX:+DebugNonSafepoints` is also set, so sampled frames land on the real hot line rather than on
the nearest safepoint.

**Kover instruments the `test` task's bytecode**, and its `ClassReader`/`ByteVector` frames show up
in a recording (measured: ~2 % of samples, plus a visible share of the byte-array allocation). It
inflates absolute numbers a little; it does not move relative attribution between Lunar's own
frames. Subtract it by eye, or take the recording with coverage off if you need absolute figures.

## Retrieve

The build host keeps recordings under its checkout's `build/jfr/`, which is gitignored and therefore
excluded from the builder sync — it survives the next `run`, and the next `run` will add to it.

```bash
rsync -avz <build-host>:<checkout>/build/jfr/ build/jfr/
```

Use the SSH host and remote checkout path from `tooling/gce-builder/config.sh` — note that the
account the wrapper builds under and the account an interactive SSH alias lands you in are not
necessarily the same, so give the **absolute** remote path rather than a `~`-relative one. Delete `build/jfr/` on both ends when you are done — a `profile`
recording of a full suite run is tens of megabytes and there is no retention policy.

## Read

Everything below is `jfr`, the JDK's own reader. No IDE required.

```bash
jfr summary build/jfr/<recording>.jfr
```

The event-count table is the triage step: it tells you whether you have enough
`jdk.ExecutionSample` events to say anything (rule of thumb: fewer than a few hundred and the
answer is noise), and whether the cost shows up as GC, lock contention or I/O instead of CPU.

**Which frames are hot** — collapse the sampled stacks and count leaves:

```bash
jfr print --events ExecutionSample --stack-depth 1024 build/jfr/<recording>.jfr \
  | grep -oE 'net\.internetisalie\.lunar\.[A-Za-z0-9_.$]+' | sort | uniq -c | sort -rn | head -40
```

**Is one method entered once or many times** — the question a flame graph answers and a budget test
cannot. Count how many samples contain the frame at all, versus how many *distinct* caller prefixes
reach it:

```bash
jfr print --events ExecutionSample --stack-depth 1024 build/jfr/<recording>.jfr \
  | grep -c 'LuaTypesVisitor\.buildSnapshot'
```

A method that is repeatedly re-entered shows a *varying* set of frames above it and a shallow,
repeating shape; a method that is internally superlinear shows one stable prefix above it and the
variation strictly below. Re-entrancy — the same frame appearing more than once in a single stack —
is a third shape and is distinguishable from both.

**Where allocation goes** (only under `-PjfrProfile`, the `profile` set):

```bash
jfr print --events ObjectAllocationSample --stack-depth 64 build/jfr/<recording>.jfr | head -100
```

`jfr view hot-methods <recording>.jfr` and `jfr view allocation-by-site <recording>.jfr` give the
same information pre-aggregated when you do not need to script over it. JDK Mission Control opens
the same file if a GUI is available, but nothing in this recipe needs one.

## Live JVMs

`jcmd`, `jstack` and `jmap` ship with the same JDK and need no build flag. For a hung sandbox IDE or
a test JVM that will not finish, a thread dump is usually faster than a recording:

```bash
jcmd -l                       # find the pid
jstack <pid> > dump.txt       # repeat 3-5 times a few seconds apart
```

A frame that is present in every dump is a hot or blocked frame; that is the poor-man's profiler and
it costs nothing to try first. `jcmd <pid> JFR.start settings=profile` and
`jcmd <pid> JFR.dump name=1 filename=/tmp/live.jfr` attach a recording to an already-running JVM —
the way to profile `runIde` or the containerized IDE, neither of which goes through the `test` task's
flag.

## What this recipe does not do

- **No CI perf-regression harness.** Wall-clock budgets on a shared runner are flaky, and a flaky
  gate is worse than none. Deferred deliberately ([[MAINT-38]] tier 3).
- **No in-repo timing instrumentation.** Attribution in Lunar's own terms — snapshot builds, index
  queries, resolutions — is [[MAINT-38]] tier 2, and only earns its cost where JFR's frame-level view
  proves insufficient.
