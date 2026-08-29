---
id: "MAINT-38"
title: "38: Time Attribution — On-Demand Profiling"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-38: Time Attribution — On-Demand Profiling

The plugin can **detect** a performance defect and cannot **diagnose** one.

Three suites assert wall-clock budgets — `GlobalSymbolCompletionPerformanceTest`,
`GlobalSymbolPerformanceOptimizationTest`, `LuaUnionDistributionBenchmarkTest`. None of them caught
[`BUG-473`](../../bug-fixes/473-luatypessnapshot-forfile-is-superlinear-with-a-class-tag/bug-report.md),
where one `---@class` makes `LuaTypesSnapshot.forFile` superlinear in colon-call-site count (×2.2,
×4.4, ×9.3 per doubling; 12 807 ms at n = 80). A budget says *that* something is slow. It never says
*where*, so every performance diagnosis in this repo has been source-reading presented as analysis.

`LuaTypeSourceRecorder` looks adjacent and is not: its `SourceFrame` records which files and index
keys a snapshot **consumed**, which is a cache-correctness question, not a timing one.

## Three tiers — do not conflate them

| Tier | What | Estimate | Verdict |
| :-- | :-- | :-- | :-- |
| **1** | **JFR on demand** — a `-P`-gated JVM argument on the `test` task plus a documented recipe for retrieving and reading the `.jfr`. No new dependency: `jfr`, `jcmd`, `jstack` and `jmap` all ship with the build host's JDK 21. | 2–4 h | **This feature.** |
| 2 | In-repo timing instrumentation on `LuaTypeSourceRecorder`, which already brackets the type build — attribution in Lunar's own terms rather than JVM frames. | 2–3 d | Deferred. Only earns its cost if tier 1 proves insufficient. |
| 3 | A CI perf-regression harness with budgets. | 1–2 w | **Not recommended.** Wall-clock budgets are known-flaky on the shared runner, and a flaky gate is worse than no gate. |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-38-01 | Recording is off by default | M | Full | A run that does not pass the flag adds no JVM argument, starts no recording and creates no output. The routine loop's cost is unchanged — this is diagnostic tooling, not a permanent tax. |
| MAINT-38-02 | `-PjfrProfile` starts a recording on the `test` task | M | Full | `settings=profile` by default, `-PjfrProfile=default` for the low-overhead event set. Written to `build/jfr/`. |
| MAINT-38-03 | Recordings survive forking and are readable | M | Full | `filename=` targets the `build/jfr` **directory**, so the JVM mints one uniquely named file per process and forked test JVMs cannot overwrite each other. `stackdepth=1024` (JFR defaults to 64) — a truncated stack drops the caller frames that distinguish repeated entry from internal cost. `-XX:+DebugNonSafepoints` for accurate attribution. |
| MAINT-38-04 | The gate is asserted, not assumed | S | Full | `JfrRecordingFlagTest` reads the forked JVM's own launch arguments and fails if a recording is running without `-PjfrProfile`, or missing with it. The build publishes the gate decision unconditionally and the JVM argument conditionally, so hoisting the argument out of its guard turns the **default** run red. |
| MAINT-38-05 | A profiling target for BUG-473 | S | Full | `LuaClassTagSnapshotPerformanceTest` builds the BUG-473 fixture (one `---@class`, 80 colon call sites) plus its tag-free control. Asserts correctness only — a wall-clock assertion here would be the same instrument that failed to catch BUG-473. Named `*Performance*`, so the routine loop excludes it. |
| MAINT-38-06 | A written recipe, in the repository | M | Full | [`docs/profiling.md`](../../../profiling.md): record, retrieve, read, and the live-JVM (`jcmd`/`jstack`) path for a sandbox IDE that never goes through the `test` task. |
| MAINT-38-07 | Tier 1 answers BUG-473's open fork | M | Full | The capability is only real if it answers the question it was built for: is `buildSnapshot` internally superlinear, or is the `CachedValuesManager` entry thrashing? Recorded verdict and evidence live in BUG-473's report. |
| MAINT-38-08 | In-repo timing instrumentation | C | Future Work | Tier 2. Deferred; see the table above. |
| MAINT-38-09 | CI perf-regression harness | W | Future Work | Tier 3. Not recommended now. |

## Scope

### In Scope
- `build.gradle.kts` — the `test` task's `-PjfrProfile` gate.
- `docs/profiling.md` — the recipe.
- Two test classes: the gate's own guard, and a BUG-473 profiling target.

### Out of Scope
- **Fixing BUG-473.** This feature produces the diagnosis; the fix is separate work.
- Tiers 2 and 3 above.
- `runIde`, `integrationTest`, and the containerized IDE — none takes the `test` task's flag. They
  are reachable through `jcmd JFR.start` against a live pid, which the recipe documents but the
  build does not wire.
- Continuous or always-on profiling of any kind.

## Test Cases

| TC | Req | Input | Action | Expected |
| :-- | :-- | :-- | :-- | :-- |
| TC-01 | 01, 04 | no `-PjfrProfile` | `test --rerun --no-build-cache` | `lunar.jfr.requested=false` and the forked JVM carries no `-XX:StartFlightRecording`; `build/jfr/` is not created |
| TC-02 | 02, 04 | `-PjfrProfile` | same | `lunar.jfr.requested=true` and exactly one `-XX:StartFlightRecording` argument, targeting `build/jfr` |
| TC-03 | 03 | `-PjfrProfile` | inspect `build/jfr/` after the run | at least one non-empty `hotspot-pid-*.jfr`; `jfr summary` reports `jdk.ExecutionSample` events |
| TC-04 | 05 | n = 80 with the class tag | `LuaClassTagSnapshotPerformanceTest` | the receiver still resolves to `Builder` (no timing assertion) |
| TC-05 | 05 | n = 80 without the class tag | same | the receiver resolves to a defined type — the control arm |
| TC-06 | 07 | the TC-03 recording | read the sampled stacks | the shape above `LuaTypesVisitor.buildSnapshot` is stable or varying, which decides thrash vs internal blowup |

**Regression gate:** the ordinary suite, unchanged apart from the single test this feature adds.
