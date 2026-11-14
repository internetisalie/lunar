---
id: "MAINT-65-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-65"
folders:
  - "[[features/maint/65-cycle-aware-inference/requirements|requirements]]"
---

# Technical Design: MAINT-65 — Lambda-Gated Callee Resolution

## 1. Architecture Overview

### Current State
`LuaTypesVisitor.visitFuncCall` calls `propagateExpectedLambdaParams(o, argExprs, calleeUnwrapped)`
(`LuaTypesVisitor.kt:940`) for every call. That function (`LuaTypesVisitor.kt:1181`) does two things:
- constructs `LuaExpectedCallbackResolver` and calls `resolveCalleeType()`;
- then loops over the arguments and seeds only those that unwrap to a `LuaFuncDef`.

`resolveCalleeType()` takes one of two routes:
- **Method call** (`methodExpr != null`): `resolveMethodCalleeType` resolves the method name's
  reference, then calls `functionTypeOf(decl)`, which calls `LuaTypesSnapshot.forFile(declFile)`.
- **Otherwise**: `resolveStandardCalleeType`. It first calls
  `LuaTypesSnapshot.forFile(call.containingFile)` and reads the callee's value type. Because the
  caller's own file is still being built, that call is served by
  `LuaTypesVisitor.inProgressSnapshot`, and `forFile` records the file as an in-progress hit
  (`LuaTypeSourceRecorder.reportInProgressHit`). If the value type is not a function,
  `resolveCalleeDeclaration` finds the declaration, and `functionTypeOf(decl)` calls
  `forFile(declFile)`.

Either way, a call with no lambda argument does this work and discards the result. DR-07 records
the resulting in-progress hit for a call with no lambda. DR-02 attributes the cross-file hits; it
was measured under the unit-build variant, not on base.

### Prior Art in This Repo
- `LuaTypesVisitor.propagateExpectedLambdaParams` / `seedLambdaParams` / `unwrapExpression`
  (`LuaTypesVisitor.kt:1181`, `:1198`, `:248`), TYPE-10. **Extended**: the guard is added here.
- `LuaExpectedCallbackResolver` (`LuaExpectedCallbackResolver.kt:21`). **Unchanged.**
- `LuaTypeSourceRecorder.snapshotFrames` and `SourceFrame.inProgressHits`
  (`LuaTypeSourceRecorder.kt:98`, `:46`), TYPE-11. **Reused** by TC 1 and TC 2 as the observable
  for "no callee resolution". `LuaTypeSourceRecorderTest` already reads `snapshotFrames`.
- `LuaSnapshotCostSweepTest` (`LuaSnapshotCostSweepTest.kt:86`). **Reused** for acceptance.
  **Edited**: its private `readErrors` (`:327`) and `errorDigest` (`:352`) move to §2.3 unchanged,
  so that §2.2 computes the same digest.
- `LuaTypeFalseNegativeAuditTest` (`corpus/audit/LuaTypeFalseNegativeAuditTest.kt:23`) and
  `LuaCatsConformanceTest`. **Reused** for acceptance, unchanged.
- No existing test reads a file alone in a fresh fixture. Every `copyDirectoryToProject` caller
  under `src/test/kotlin/…/corpus/` copies a whole tree and then reads its files in sequence:
  - `LuaSnapshotCostSweepTest`;
  - `LuaAnnotatedFixtureSweepTest`;
  - `LuaInspectionParityTest`;
  - `CorpusSweep.kt`;
  - `audit/AuditMemberRun.kt`.

### Target State
`propagateExpectedLambdaParams` first collects the lambda arguments. If there are none, it returns
before constructing the resolver. Nothing else in production code changes. An opt-in test
(§2.2) reads MAINT-65-02's candidate files alone.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor` (edit)
- **Responsibility**: the snapshot build visitor. Only `propagateExpectedLambdaParams` changes.
- **Threading**: unchanged. It runs inside a snapshot build, under the caller's read action.
- **Collaborators**: `LuaExpectedCallbackResolver.resolveCalleeType()`,
  `LuaExpectedCallbackResolver.expectedCallbackAt(index, calleeType, selfOffset)`,
  `seedLambdaParams(lambda, expected)`, `unwrapExpression(expr)`.
- **Key API** (signature unchanged, private):
  ```kotlin
  private fun propagateExpectedLambdaParams(o: LuaFuncCall, argExprs: List<PsiElement>, calleeUnwrapped: PsiElement?)
  ```

### 2.2 `net.internetisalie.lunar.corpus.LuaAloneDigestTest` (new, test source)
- **Responsibility**: for each candidate file (§3.2), read it first in a fresh fixture and record
  its findings digest.
- **Threading**: the test thread. Reads go through §2.3's `readErrors`, which runs
  `LuaTypesSnapshot.forFile(file).getErrors()` in `runReadAction` under a cancellable indicator.
- **Collaborators**:
  - `CorpusManifest.entry(repoRoot, name, CorpusManifest.TYPE_MANIFEST_PATH)`;
  - `CorpusGuards.assertCorpusFetched`;
  - `CorpusTarget.pin` / `CorpusTarget.memberTarget` / `CorpusTarget.restore`;
  - `CorpusDefinitionLibraries.requireSettingsSubscriber` / `provision` / `release` / `refreshRoots`;
  - `CorpusSourcePath.snapshot` / `restore`;
  - `SnapshotCostVerdict.COLD_DEADLINE_MS`;
  - `SweepRow` (`SnapshotCostVerdict.kt:6`).
- **Shape**:
  - `@RunWith(JUnit4::class)` and `@FixMethodOrder(MethodSorters.NAME_ASCENDING)`, extending
    `BasePlatformTestCase`.
  - `setUp`, `tearDown`, `getTestDataPath`, the member restore and `applyModuleRoot`
    (`LuaSnapshotCostSweepTest.kt:369`) are copied from `LuaSnapshotCostSweepTest`. `BasePlatformTestCase` gives each test method a fresh temp
    directory, and that is what makes a read "alone". Snapshots of definition-library files
    outside the temp directory can survive between methods. This is the setup DR-08 measured.
  - Forty methods, `testSlot00()` … `testSlot39()`, each annotated `@Test`, because under the
    `JUnit4` runner a method without the annotation does not run. Method N calls `readSlot(N)`.
- **Key API**:
  ```kotlin
  private fun candidates(): List<Pair<String, String>>    // §3.2, (member, file), sorted
  private fun readSlot(slot: Int)                         // §3.3
  ```

### 2.3 `net.internetisalie.lunar.corpus.CorpusSnapshotReads` (new `internal object`, test source)
- **Responsibility**: the single home of the sweep's read-and-digest logic, shared by §2.2 and
  `LuaSnapshotCostSweepTest`.
- **Threading**: as `LuaSnapshotCostSweepTest.readErrors` today.
- **Key API** (bodies moved verbatim from `LuaSnapshotCostSweepTest`; the sweep calls these):
  ```kotlin
  fun readErrors(file: LuaFile, deadlineMs: Long): Pair<String, List<ElementError>?>
  fun errorDigest(errors: List<ElementError>): Int
  ```

There is no `plugin.xml` change.

## 3. Algorithms

### 3.1 Lambda-gated callee resolution
- **Input → Output**: `(o: LuaFuncCall, argExprs: List<PsiElement>, calleeUnwrapped: PsiElement?)`
  → `Unit`. It adds graph edges as a side effect.
- **Steps**:
  1. Collect `lambdas: List<IndexedValue<LuaFuncDef>>`. For each `(index, argExpr)` in
     `argExprs.withIndex()`, keep `IndexedValue(index, lambda)` where
     `lambda = unwrapExpression(argExpr) as? LuaFuncDef`. Drop the entries where that is null.
  2. If `lambdas` is empty, return. No resolver is constructed.
  3. `val resolver = LuaExpectedCallbackResolver(o, calleeUnwrapped)`, then
     `val calleeType = resolver.resolveCalleeType() ?: return`.
  4. `val nameAndArgs = o.nameAndArgsList.firstOrNull() ?: return`.
  5. Compute `selfOffset` exactly as today: `1` if `nameAndArgs.methodExpr != null` and
     `calleeType.params.firstOrNull()?.name == "self"`, otherwise `0`.
  6. For each `(index, lambda)` in `lambdas`: compute
     `val expected = resolver.expectedCallbackAt(index, calleeType, selfOffset)`. If it is null,
     continue. Otherwise call `seedLambdaParams(lambda, expected)`.
- **Rules / edge handling**:
  - The predicate in step 1 is exactly today's loop guard
    (`unwrapExpression(argExpr) as? LuaFuncDef ?: return@forEachIndexed`).
  - Indices are the original positions in `argExprs`, so `expectedCallbackAt` sees the same index as
    today.
  - Seeding order is unchanged: ascending index.
  - For a call with at least one lambda, behaviour is identical to today.
  - For a call with none, today's code seeds nothing. The graph this file builds is therefore
    unchanged, except where the skipped resolution changed shared caches. Those are build-order
    effects; see Risk 1.1.
- **Complexity / bounds**: one `unwrapExpression` per argument, as today. It removes up to one
  callee resolution per lambda-less call.

### 3.2 MAINT-65-02 candidate selection
- **Input → Output**: two `sweep.jsonl` paths → `List<Pair<String, String>>`. The paths come from
  the system properties `lunar.aloneDigest.baseSweep` and `lunar.aloneDigest.changeSweep`, which
  §7 sets from `-PaloneDigestBase` and `-PaloneDigestChange`. These are the first base sweep and
  the first change sweep of TC 6.
- **Steps**:
  1. Parse each file as `LuaSnapshotCostSweepTest.readRows` does: Gson, one `SweepRow` per
     non-blank line.
  2. Key each row by `(member, file)`.
  3. Keep the keys present in both files where both rows have `coldStatus == "ok"` and the two
     `digest` values differ.
  4. Sort by `member`, then by `file`.
- **Rules**:
  - A key missing from either file, or not `ok` in either, is not a candidate.
  - Either property absent or blank: fail with
    `"aloneDigest needs -PaloneDigestBase and -PaloneDigestChange"`.

### 3.3 Alone read (`readSlot`)
- **Steps**:
  1. `val list = candidates()`.
  2. If `slot == 0`:
     - replace `build/alone-digest/digests.tsv` with an empty file, so that zero candidates leave
       an empty file rather than none;
     - then assert `list.size <= 40`, with the message `"${list.size} candidates exceed 40 slots"`.
  3. If `slot >= list.size`, return.
  4. `(member, file) = list[slot]`. Set up the member as `LuaSnapshotCostSweepTest.measureMember`
     does, in the same order:
     - `CorpusGuards.assertCorpusFetched`;
     - `CorpusTarget.pin`;
     - `CorpusDefinitionLibraries.provision`;
     - the module root;
     - `myFixture.copyDirectoryToProject` for each of `entry.roots`.
  5. Find `file` with `myFixture.findFileInTempDir(file)` and `PsiManager.findFile`. Then
     `(status, errors) = CorpusSnapshotReads.readErrors(luaFile, SnapshotCostVerdict.COLD_DEADLINE_MS)`.
     No other file is read first.
  6. Append the line `member\tfile\tstatus\tdigest` to `build/alone-digest/digests.tsv`, where
     `digest` is `CorpusSnapshotReads.errorDigest(errors)`, or `0` when `errors` is null.
- **Comparison (TC 7)**: rows join on `(member, file)` across the two runs. Each row needs
  `status == "ok"` in both runs and equal `digest` values.

## 4. External Data & Parsing
The only inputs are `sweep.jsonl` rows, which are produced by this repo's own
`LuaSnapshotCostSweepTest` and parsed as `SweepRow` (§3.2).

## 5. Data Flow

### Example 1: `run(1)`, with `run` a LuaCATS-annotated local in the same file (TC 1)
`visitFuncCall` → `propagateExpectedLambdaParams`. The argument `1` does not unwrap to a
`LuaFuncDef`, so `lambdas` is empty and the function returns. Nothing calls `forFile` on the file
under construction, so the frame records no in-progress hit.

### Example 2: `run(function(x) end)` (TC 2)
`lambdas = [(0, funcDef)]` → `resolveCalleeType()` → `resolveStandardCalleeType` →
`forFile(main.lua)`. That call is served in progress and recorded as a hit. The callee's value type
is `fun(cb: fun(x: string))`, so `expectedCallbackAt(0, …)` returns `fun(x: string)`, and
`seedLambdaParams` gives `x` the type `string`.

## 6. Edge Cases
- **Parenthesised lambda** `f((function(x) end))`: `unwrapExpression` already unwraps it. Same
  result as today.
- **Lambda after a non-lambda** `f(1, function(x) end)`: `lambdas = [(1, …)]`, so index 1 is
  preserved (TC 3).
- **String or table-constructor call** `f"x"` / `f{}`: `argExpressionsOf` returns a non-`LuaFuncDef`.
  No resolution, as for TC 1.
- **Method call with `self`**: `selfOffset` is computed after the gate, from the same `calleeType`.
  Unchanged.

## 7. Integration Points
There is no `plugin.xml`, index or settings change. `build.gradle.kts` gets one opt-in block,
placed after the `withSnapshotSweep` block:

```kotlin
// MAINT-65: alone-read findings digests (MAINT-65-02). Opt-in:
// `./gradlew test -PwithAloneDigest -PaloneDigestBase=<sweep.jsonl> -PaloneDigestChange=<sweep.jsonl> --tests '*LuaAloneDigestTest*'`.
if (!project.hasProperty("withAloneDigest")) {
    filter {
        excludeTestsMatching("*AloneDigest*")
        isFailOnNoMatchingTests = false
    }
} else {
    systemProperty("lunar.aloneDigest.baseSweep", project.findProperty("aloneDigestBase")?.toString() ?: "")
    systemProperty("lunar.aloneDigest.changeSweep", project.findProperty("aloneDigestChange")?.toString() ?: "")
}
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-65-01 | M | §2.1, §3.1 |
| MAINT-65-02 | M | §2.2, §2.3, §3.2, §3.3, §7 (the check); §3.1 rules (why it holds) |
| MAINT-65-03 | M | §3.1 (removed resolutions); risks DR-04, DR-09 |

## 9. Alternatives Considered
Measurements are in [risks-and-gaps.md](risks-and-gaps.md).
- **Build each import cycle (static require SCC) as one unit.** Not taken. It does not beat base
  and it changes cold digests (DR-03).
- **Gate plus MAINT-63's svca invalidation under the r10 rule.** Not taken. It is no better than
  the gate on base invalidation (DR-04).
- **Make `LuaExpectedCallbackResolver` cheaper**, for example by resolving through stubs without
  building a snapshot. Not needed for lambda-less calls, which are the measured volume. The cost for
  calls with a lambda is unmeasured and out of scope.

## 10. Open Questions
_None — feature has cleared the planning bar._
