---
id: "BUG-434"
title: "`LuaTypeReference`'s memoized answer carries no recording frame, so a pre-forced reference hides a consumed file"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-434: a memoized type reference is a second door with no frame

Found by TYPE-11 Phase 4 (DR-07, 2026-08-12), which set out to probe Risk 1.3 — "a lazily-resolved
`LuaTypeReference` escapes the recording frame". The risk is real; **the mechanism is the reverse of
the one that entry described**, and it is the sixth under-recording channel in TYPE-11's pin
decision, after the absence, the warm inner snapshot, the in-progress inner snapshot, the rescued
global and the module absence.

## What it is

TYPE-11 stops discarding a provisioned library file's type snapshot on every keystroke by *pinning*
it to a generation tracker — but only when the recorded `SourceFrame` proves the build consumed
nothing outside the provisioned roots. Every memoized cross-file answer therefore has to replay the
provenance of the answer it serves, or a file that really does depend on a project file will be
judged clean and pinned. `LuaTypeManagerImpl`'s three doors do exactly that (`CachedAnswer`,
design §3.6). `LuaTypeReference` did not:

```kotlin
val resolved: LuaType by lazy {
    LuaTypeManager.getInstance(context.project).resolveType(name, context) ?: LuaPrimitiveType.UNKNOWN
}
```

`materializeClass` builds one of these for every `@field` member type, every `@class` supertype,
every function parameter and return, and every alias target
(`LuaTypeManagerImpl.kt:356`, `:365`, `:412`, `:424`, `:592`, `:597-598`, `:617`), and
`LuaGraphType.fromLuaType` flattens them during a snapshot build (`LuaGraphType.kt:251`). If anything
already forced the reference — a hover, a completion, `LuaOverrideLineMarkerProvider`, a hierarchy
walk, an assignability inspection, all at `depth() == 0` — the `by lazy` short-circuits **before**
`LuaTypeManager.resolveType`, so neither the cold path's `recordInto` nor the cache hit's `replay`
runs, and the frame open at that moment learns nothing about the file the reference resolved into.

Reachability is not marginal: the pre-force only has to land in the same `PsiModificationTracker`
epoch as the library build, and a wrong pin taken once survives until the next roots or target tick.

## Reproduced end to end, and attributed by mutation

`TypeElevenDr07LazyReferenceProbeTest` — a provisioned library `lib.lua` with
`---@class Widget` / `---@field part Gadget` / `---@type Widget libWidget`, over a **project** file
`gadget.lua` with `---@class Gadget` / `---@field spin number`. The arms differ only in whether
anything forced `Widget.part`'s reference at `depth() == 0` first. On `main` @ `6f238e7c`:

```
TYPE11-DR07 pre-force depth=0 part=LuaTypeReference members=[spin]
TYPE11-DR07 arm1 cold       urls=[lib.lua, gadget.lua] absences=[] rescued=[] warm=[] inProgress=[] pinnable=false
TYPE11-DR07 arm2 pre-forced urls=[lib.lua]             absences=[] rescued=[] warm=[] inProgress=[] pinnable=true
TYPE11-DR07 arm3 pre-forced before=[spin] after=[spin] sameSnapshot=true    <- a stale type the user reads
TYPE11-DR07 arm4 cold       before=[spin] after=[spun] sameSnapshot=false
```

Arms 3 and 4 rename the project field `spin → spun` through `rewriteAssertingRootsAreStill`, which
asserts the roots tracker is **still** across the edit — so the only thing that changed is a project
file, and a green cannot be inherited from an unrelated roots tick. The pre-forced arm keeps the same
snapshot instance and keeps reporting `[spin]`.

Attribution was proven by mutation, not by reading: with `resolved`'s `by lazy` replaced by a plain
`get()`, every arm reported the cold result (`urls=[lib.lua, gadget.lua]`, `pinnable=false`,
`after=[spun]`).

## The fix

`LuaTypeReference` memoizes the `SourceFrame` **beside** the answer and replays it on every read:

```kotlin
private val memoizedAnswer: Pair<LuaType, LuaTypeSourceRecorder.SourceFrame> by lazy {
    LuaTypeSourceRecorder.recording {
        LuaTypeManager.getInstance(context.project).resolveType(name, context) ?: LuaPrimitiveType.UNKNOWN
    }
}

val resolved: LuaType
    get() {
        val (answer, sourceFrame) = memoizedAnswer
        LuaTypeSourceRecorder.replay(sourceFrame)
        return answer
    }
```

This is Phase 2's `CachedAnswer` idiom, applied to the layer that was missing one. The invariant it
restores is the one the channel broke: **a reference read inside a frame contributes its consumed
sources whether or not it was resolved earlier.**

Three things worth stating about the shape:

- **Not the `by lazy` → `get()` the roadmap row proposed.** That is correct too, and it is what the
  attribution mutation used, but it deletes the memoization: every member access would then pay a
  synchronized map lookup and a reentrancy-guard round trip, and — the part that matters more — it
  leaves the frame nobody's property, so the next consumer of a memoized type re-opens the hole.
- **Not "report at the consumption site" either.** `fromLuaType` is only one of four readers
  (`resolveMember`, `getMembers`, `isAssignableTo` are the others), and at the consumption site there
  is nothing to report without asking the manager again — which is the same memoization question one
  layer up. A frame belongs with the answer it explains.
- **The cold path replays too, and that costs nothing.** `report` and its siblings already write to
  every open frame on the way in, so the replay is set-wise idempotent — the reasoning
  `reportWarmSnapshot` already states for `forFile`'s cold path.

`LuaTypeSourceRecorder.replay` gained an empty-frame short-circuit (`SourceFrame.isEmpty()`), which
is an optimisation and provably nothing else: `absorb` is five `addAll`s, and five `addAll`s of empty
collections change no receiver. It earns its place because the fix makes every read of a memoized
reference a replay and most references resolve names that consumed nothing — a primitive, or a type
no file declares.

Lifetime, since this stores state on a per-instance object rather than in a project cache: a frame
holds `String` URLs only, so no hard `VirtualFile`/`PsiFile` reference is added (engineering contract
§4), and the instance is already bounded by the `typeCache` entry holding its enclosing
`LuaClassType`, which every `PsiModificationTracker` tick discards. That bound is the *reason* the
lifetime question is easy here, but the answer does not depend on it: if an instance did outlive its
epoch, replaying its frame would contribute URLs recorded earlier, and extra URLs in `urls` can only
cost a pin (§3.3 step 3 is an `any {}` over them), never grant one. The failure direction is the safe
one, which is the property §1.12 asks of every mark.

## Measured

Because this is a hot-path change inside a performance feature, everything the roadmap row demanded
was re-measured on gce-builder (`debian13`), 2026-08-12.

| gate | result |
| :-- | :-- |
| `ktlintCheck test --rerun --no-build-cache` | **2630 tests, 0 failures, 1 skipped** — identical to the `6f238e7c` baseline. (First attempt was 2630/1: `LuaInterpreterCommandLinesTest.kt:56`, **BUG-422**'s fourth occurrence, green on an immediate re-run with no change — its own protocol.) |
| `test -PwithCorpus --rerun --no-build-cache` | **2638 tests, 0 failures, 1 skipped**, 422 classes — the `bf715eb2` corpus baseline of 2631 **plus Phase 4's 7 new tests** (`TypeElevenDr06StampProbeTest` 3 + `TypeElevenDr07LazyReferenceProbeTest` 4; that commit records `baseline 2623 + 7 new` for the routine loop, and corpus adds a constant 8). Phase 4b adds no test, it converts four prints to assertions. `LuaCorpusSweepTest` (4), `LuaTortureCorpusTest` (1) and `LuaInspectionParityTest` (1) all ran, XML mtimes `03:59:35` inside the `03:40:27–03:59:35` run window, and the recorded baselines compared unchanged |
| `TypeElevenPinnableCostTest` | **`provisioned=11 pinnable=11`** — the fix costs **zero** pins |
| `TypeElevenDr04LatencyTest` | no material regression, below |

### The pin count is the number that decides whether this is a fix at all

`provisioned=11 pinnable=11` — all 10 bundled `lua-5.4` stdlib stubs plus the definition library stay
pinnable. A fix that closed the hole by pinning nothing would have been a revert with extra steps;
this one closes it by making the *pre-forced* build record what the cold build already recorded, and
the cold build's verdict on those 11 files was already `pinnable`.

### DR-04 latency

`TypeElevenDr04LatencyTest` has no assertions by design (design §1.5 / COMP-09 §1.2 — a threshold on
a figure whose baseline moves several ms between runs on one machine is noise dressed as a contract),
so the numbers are read, not gated. Medians of 5 samples:

| | arm A (no library) | arm B (123 KiB library) |
| :-- | --: | --: |
| Phase 3 reference, routine suite | 6 287 µs | 22 859 µs |
| this fix, routine suite, run 1 | 3 739 µs | 15 147 µs |
| this fix, routine suite, run 2 | 2 434 µs | 10 906 µs |
| `6f238e7c` baseline, isolated ×3 | 15 008 / 14 996 / 10 577 µs | 28 234 / 28 597 / 28 128 µs |
| this fix, isolated ×3 | 11 308 / 17 632 / 16 312 µs | 30 616 / 27 115 / 29 839 µs |

Isolated runs are ~2× slower than in-suite ones (no JIT warmup from 2 600 preceding tests), so only
like-for-like rows compare. On the paired isolated rows arm B's median moves **28 234 → 29 839 µs,
+5.7 %** — while arm A, which has no library and no mechanism by which this change could touch it,
moves −25 % to +17 % across the same runs. So +5.7 % is at or under the noise floor of the harness.
The in-suite figures, which are the ones Phase 3's reference was taken from, are **lower** than that
reference in both runs. The feature's win is intact; the fix does not erase it.

## Why the regression test can go red

`TypeElevenDr07LazyReferenceProbeTest` was committed at Phase 4 **printing rather than asserting**,
deliberately, so that recording the defect would not cement it. It now asserts, and the assertions
were shown red under a named mutation — the replay dropped from `resolved`, everything else
unchanged:

```kotlin
val (answer, sourceFrame) = memoizedAnswer
if (sourceFrame.urls.isNotEmpty()) Unit // MUTATION: the replay is dropped
return answer
```

```
TypeElevenDr07LazyReferenceProbeTest > testWhetherTheEscapedPinSurvivesAnEditToTheProjectFileItDependsOn FAILED
    junit.framework.AssertionFailedError: arm3: the edited project file must not leave the library
    snapshot in place
TypeElevenDr07LazyReferenceProbeTest > testTheReferenceIsAlreadyForcedWhenSomethingReadItFirst FAILED
    junit.framework.AssertionFailedError: arm2 pre-forced: gadget.lua declares the type `Widget.part`
    names, so building lib.lua's snapshot consumed it; the recorded sources were [lib.lua]

4 tests completed, 2 failed
```

Arms 1 and 4 — the cold controls — stayed **green** under the mutation, which is the point of having
them: they were green with the defect present, so only arms 2 and 3 are evidence about the fix. That
2-of-4 split is the same shape BUG-401 taught (a test whose name describes the bug most directly can
still be on the wrong path); here the split is the expected one and it was confirmed, not assumed.

## One thing this does not close

`LuaTypeManagerImpl.resolveType`'s reentrancy guard (`if (name in resolvingTypes.get()) return null`)
records **no** absence, where `resolveModule`'s and `resolveGlobal`'s equivalents both do. Design
§3.1 step 5 states the omission for the *computed* null and gives a measured reason for it
(widening absence to `resolveType` costs `io.lua` its pin for names like `boolean|nil` that can never
be declared), but it does not discuss the reentrancy branch, which is a different null. Not
investigated here and **not claimed to be a defect** — it is a cycle inside a declaration that does
exist, not an answer a later declaration would change — but it is the one remaining `return null`
in the three doors that no frame mark accompanies, and it is written down so the next person does not
have to re-derive that it was considered.
