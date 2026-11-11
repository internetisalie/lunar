---
id: "BUG-96"
title: "fallo's `init.lua` has an own snapshot build that does not finish within 60 seconds"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-96: fallo's `init.lua` has an own snapshot build that does not finish within 60 seconds

## Scope (MAINT-60 Phase 3)
MAINT-60's sweep (design 3.3, `attribute`) files this bug for one cause: **the own build of fallo
`lua/fallo/init.lua`** is the only build that takes 250 ms or more because of that file alone and is not
shared with other files. Its cold read hit the 60 s deadline with 60 016 ms of its own self time and 7
builds (DR-06, DR-08: 970 215 checked pairs in one pass, cancelled in pass 1), so its after-edit pass is
`not-run` and is filed here as unmeasured. Any other file whose dominant dependency is this one is
filed here too; the sweep found none. The profile in section 4 locates the time in `checkNodePairs`,
`VariableElement.resolveRead`/`resolveWrite` and `LuaGraphType$Table.hashCode`. Fixing it must keep
MAINT-60-03: audit, conformance and sweep digests unchanged or each difference recorded. MAINT-60-04's
workload on `init.lua` is added by this fix.

### Files over target
Generated from the union of three MAINT-60 Phase 3 sweeps (libvirt builder, commit `9fb91435`). A pass
is here when it missed in at least one run and the item most of those runs filed it under is this
one (on a tie, the second run's). *Value* is the largest time measured, or the status when a run did
not complete the pass.

| Member | File | Bound | Value | Runs missed | Cause |
|---|---|---|---|---|---|
| fallo | `lua/fallo/init.lua` | cold-250 | timeout | 3/3 | own build |
| fallo | `lua/fallo/init.lua` | edit-250 | not-run | 3/3 | unmeasured |

Found by MAINT-56's representative run. Its `fallo` member is one 514-line file and took 6 821 s to
measure (`[type-corpus:fallo] files=1 population=149 sites=53 elapsedMs=6821246`). MAINT-54's harness
reads the file's type snapshot up to three times per site.

## 1. Reproduction
Confirmed reproduction: the whole of `lua/fallo/init.lua` (§4: 120 459 ms). The 40-line cut below was
measured at 128 648 ms once and at 364 ms in §4's setup, so it is not a confirmed reproduction.

The corpus member `fallo` (`tooling/corpus/type-corpus.json`): `NTBBloodbath/fallo` at commit
`b95e4996477e4f023bf1b86190c946476cca210e`, file `lua/fallo/init.lua`, target Standard Lua 5.4. The
file below is its lines 1–10 followed by lines 430–459, unchanged:

```lua
---Rust-like Result type for structured error handling

---@alias Error { message: string?, source: string?, stack: string? }
---
---@generic T, E: string|Error
---@class Result<T, E>
---@field ok boolean|function True for success (Ok), false for error (Err)
---@field value T Contained value when ok=true
---@field error E Contained error when ok=false
local Result = {}
---Add context message to an error
---The context is prepended to the existing error message, creating a chain of contexts
---For success Results, returns self unchanged
---@generic T, E
---@param self Result<T, E>
---@param message string Context message to attach
---@return Result<T, E> New Result with enriched error message or self if Ok
function Result:with_context(message)
   if self.ok then return self end

   local err = self.error
   if type(err) == "table" then
      -- Shallow-copy the error to keep immutability
      local copy = {}
      for k, v in pairs(err) do
         copy[k] = v
      end

      -- Prepend context to the existing message
      local original_message = err.message or tostring(err)
      copy.message = message .. ": " .. original_message

      return Result.err(copy)
   else
      -- For string errors, structure and add context
      local structured = Result.structure_error(err, 2)
      structured.message = message .. ": " .. (structured.message or tostring(err))
      return Result.err(structured)
   end
end
```

In a `BasePlatformTestCase` (light fixture, builder `libvirt`, GoLand 2026.2.3 test platform), pin the
target to Standard Lua 5.4. Add the file with `myFixture.addFileToProject("v.lua", text)`, then time
`LuaTypesSnapshot.forFile(file).getErrors()`.

## 2. Expected vs Actual Behavior
- **Expected**: a 514-line file is analysed in well under a second.
- **Actual**: 128 648 ms, 13 errors. Measurements on the whole file and on its parts (one run each):

| input | ms | errors |
|---|---|---|
| whole `init.lua` (514 lines), first read | 130 286 | 246 |
| whole `init.lua` after appending a newline, three times | 124 904, 123 663, 128 421 | 246 |
| `init.lua` cut after line 436, before `with_context` | 1 906 | 70 |
| `init.lua` cut after line 459, after `with_context` | 111 205 | 234 |
| the 40-line file above | 128 648 | 13 |
| lines 1–429, then `with_context` without its doc comment | 72 616 | 137 |
| lines 1–436, then `with_context` keeping only the `then` branch | 99 373 | 168 |
| lines 1–436, then `with_context` keeping only the `else` branch | 108 498 | 168 |
| lines 1–436, then `with_context` without the `for k, v in pairs(err)` loop | 120 197 | 176 |
| whole `init.lua` with every `---@generic` line removed and `Result<…>` written `Result` | > 900 000, stopped | — |

- EDT stack 25 s into the whole-file read: `VariableElement.resolveWrite` → `writeWith` → `resolveWrite`
  repeated (`LuaTypeNodes.kt:215`, `:148`), deepest frame `VariableElement.resolveWrite$flatten`
  (`LuaTypeNodes.kt:206`) adding to a `HashSet`, which calls `LuaGraphType$Table.hashCode` →
  `AbstractMap.hashCode`.

## 3. Related
[[BUG-97]]: the same frames in a whole-directory slowdown. MAINT-56's `results.md` records `fallo` as
timed out, so its rule weights are to be re-measured after this bug is fixed.

## 4. Profile
Measured 2026-11-10 on `d64890df`, builder `libvirt`, GoLand 2026.2.3 test platform, test JVM JBR
25.0.4. A throwaway light-fixture test, not committed, pinned Standard Lua 5.4 with
`CorpusTarget.pin(project, LuaLanguageLevel.LUA54)`, added each input with
`myFixture.addFileToProject`, and timed one `LuaTypesSnapshot.forFile(file).getErrors()` on the EDT.
Each input ran in its own test method. Run:
`builder-run libvirt "test --rerun --no-build-cache --tests '*ProbeBug96*' -PjfrProfile"`.
`-PjfrProfile` is the build's Java Flight Recorder switch: `settings=profile`, `stackdepth=1024`,
`DebugNonSafepoints`.

| input | ms | errors |
|---|---|---|
| the 40-line file of §1 | 364 | 21 |
| the 40-line file of §1, with the representative audit's `setUp` (settings subscriber, no definition libraries) | 86 | 21 |
| whole `init.lua` (514 lines) | 120 459 | 881 |

**In this setup the 40-line file of §1 does not reproduce the slowdown**: 364 ms, compared with
128 648 ms in §2, and 21 errors, compared with 13. The whole file reproduces it (120 459 ms), but with
881 errors, compared with 246 in §2. What made the 40-line file slow in §2's measurement has not been
found. The profile below is of the whole file.

**Samples.** The analysis counts JFR execution samples taken between the probe's BEGIN and END
timestamps, on any thread whose stack has a `net.internetisalie` frame. All 11 288 samples are on
`AWT-EventQueue-0` (about 94 per second, consistent with the 10 ms period). Mean stack depth is 199.
Two stacks reached the 1 024-frame cap. In 99.9 % of samples, one `LuaTypesVisitor.buildSnapshot`
frame is on the stack, so the cost is within this file's own snapshot and not in other files.

Top frames by self time (the frame on top of the stack):

| samples | % | frame |
|---|---|---|
| 2 419 | 21.4 | `java.util.HashMap.putVal` |
| 1 473 | 13.0 | `java.util.Objects.hashCode` (all at line 97) |
| 815 | 7.2 | `java.util.LinkedHashMap$LinkedHashIterator.nextNode` |
| 730 | 6.5 | `VariableElement.mergeTableDemands` (693 at line 395) |
| 512 | 4.5 | `java.lang.String.hashCode` |
| 449 | 4.0 | `kotlin.jvm.internal.Intrinsics.areEqual` |
| 364 | 3.2 | `VariableElement.resolveWrite` |
| 251 | 2.2 | `java.util.LinkedHashMap.afterNodeAccess` |
| 236 | 2.1 | `java.util.HashMap.hash` |
| 233 | 2.1 | `kotlin.sequences.FilteringSequence$iterator$1.calcNext` |
| 207 | 1.8 | `java.util.AbstractMap.hashCode` |
| 202 | 1.8 | `java.util.HashSet.add` |
| 196 | 1.7 | `VariableElement.resolveWrite$flatten` |
| 161 | 1.4 | a `VariableElement` lambda (`invoke`) |
| 152 | 1.3 | `java.util.HashMap$Node.getKey` |

Self time attributed to the topmost `net.internetisalie` frame (the Lunar frame that made the
library call):

| samples | % | frame |
|---|---|---|
| 2 546 | 22.6 | `LuaGraphType$Table.hashCode` |
| 2 154 | 19.1 | `VariableElement.mergeTableDemands` |
| 1 171 | 10.4 | `VariableElement.resolveRead` |
| 1 146 | 10.2 | `VariableElement.resolveWrite` |
| 863 | 7.6 | `LuaTypeGraph.checkNodePairs` |
| 841 | 7.5 | `VariableElement.resolveWrite$flatten` |
| 286 | 2.5 | `OrderedSet.add` |
| 190 | 1.7 | `LuaGraphType.getMembers` |
| 165 | 1.5 | `VariableElement.writeWith` |
| 162 | 1.4 | a `VariableElement` lambda (`invoke`) |
| 154 | 1.4 | `LuaGraphType.displayName` |
| 145 | 1.3 | `LuaGraphType$Table.equals` |
| 135 | 1.2 | `LuaGraphType$Undefined.equals` |
| 117 | 1.0 | `LuaGraphType$Function.hashCode` |
| 114 | 1.0 | `VariableElement.resolveRead$lambda$1` |

Inclusive time (samples with the method anywhere on the stack, counted once per sample). The list
excludes the probe, `forFile`, the recorder, PSI `accept` and visitor dispatch, and lambdas:

| samples | % | method |
|---|---|---|
| 11 236 | 99.5 | `LuaTypeGraph.checkTypes` → `runFixedPointPass` |
| 11 232 | 99.5 | `LuaTypeGraph.checkNodePairs` |
| 8 551 | 75.8 | `LuaTypeGraph.checkAdmittedPair` |
| 6 835 | 60.6 | `LuaTypeGraph.checkCompatibility` |
| 5 419 | 48.0 | `LuaTypeGraph.isCompatible` |
| 5 142 | 45.6 | `LuaTypeGraph.isFunctionCompatible` |
| 5 079 | 45.0 | `VariableElement.getWrite` |
| 5 078 | 45.0 | `VariableElement.resolveWrite` |
| 4 993 | 44.2 | `VariableElement.writeWith` |
| 3 868 | 34.3 | `VariableElement.getRead` |
| 3 865 | 34.2 | `VariableElement.resolveRead` |
| 3 682 | 32.6 | `VariableElement.resolveWrite$flatten` |
| 2 546 | 22.6 | `LuaGraphType$Table.hashCode` |
| 2 159 | 19.1 | `VariableElement.mergeTableDemands` |
| 729 | 6.5 | `LazyValueElement.writeWith` |

`LuaGenericInstantiation$BindingReader.resolvedOnce` is at 6.4 %, and `checkTableCompatibility` is at
6.0 %.

**Allocation**: `jdk.ObjectAllocationSample`, 28 510 samples in the window, whose weights sum to an
estimated 19 286 MB.

| class | % of weight | | topmost Lunar frame | % of weight |
|---|---|---|---|---|
| `LinkedHashMap$LinkedKeyIterator` | 19.1 | | `LuaGraphType$Table.hashCode` | 15.8 |
| `LinkedHashMap$Entry` | 17.7 | | `VariableElement.resolveRead:245` | 12.4 |
| `Object[]` | 16.2 | | `VariableElement.resolveWrite$flatten:206` | 11.1 |
| `HashMap$Node[]` | 8.4 | | `VariableElement.mergeTableDemands:395` | 6.2 |
| `LinkedHashMap$LinkedEntryIterator` | 6.8 | | `LuaTypeGraph.checkNodePairs:406` | 6.0 |

Over the whole recording, which also covers platform start-up and the two fast inputs, `jfr view
hot-methods` ranks `HashMap.putVal` (17.8 %), `Objects.hashCode` (10.8 %),
`LinkedHashIterator.nextNode` (6.0 %) and `VariableElement.mergeTableDemands` (5.4 %) first. `jfr
view allocation-by-class` ranks `LinkedHashMap$LinkedKeyIterator` (14.8 %),
`LinkedHashMap$Entry` (13.8 %), `Object[]` (13.5 %) and `byte[]` (12.1 %) first.

Not established: call counts, because sampling does not give them. Also not established: why the
error counts differ from §2's.
