---
id: "BUG-450"
title: "The STACK response is serialized unbounded and realized eagerly — and DEBUG-07 claimed the opposite was already shipped"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-450: nothing bounds the debugger's stack payload, at either end

Found 2026-08-22 while answering a question about whether debugger variable realization was ever
made lazy. It was not — and the reason that was hard to answer is that [[DEBUG-07]] said it had
been, in a `done` feature doc with no requirements in it.

## 1. Reproduction

1. Start a Lua debug session on a script with a reasonably deep call stack.
2. Pause at a breakpoint.
3. Observe the size of the `STACK` response and the delay before the Variables pane populates.

No reproduction threshold is stated on purpose — §5 measures it, because the cost is currently a
prediction from reading the code, not an observation.

## 2. Expected vs actual

- **Expected**: the debugger asks for as much of the stack as it can display, and turns it into
  Kotlin values at a cost proportional to what the user actually expands.
- **Actual**: the entire stack is serialized to unbounded depth by the debuggee, transmitted,
  parsed into PSI, and walked into a complete `LuaValue`/`LuaTable` tree before one node renders.

## 3. Root cause — two independent halves

### 3a. Nothing bounds the payload at the source

[`LuaDebugConnection.kt:93`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaDebugConnection.kt)
declares `STACK` with no `minArgs`/`maxArgs` and sends no parameters. In
[`src/main/lua/mobdebug/init.lua`](../../../../src/main/lua/mobdebug/init.lua) the handler parses
the absent parameter table into `{}`, so `maxlevel` stays nil, and serpent falls back to
`maxl = opts.maxlevel or math.huge`.

Meanwhile `stack()` collects locals **and upvalues** for frames `0..100`, storing live values. One
of those upvalues is routinely `_ENV` — the whole global table, with every loaded module hanging
off it. The observed sample in `LuaRemoteStack`'s own comment shows it:

```lua
-- { d = { 1, "1" }, e = { 2, "2" }, _ENV = { {...}, "table: 0x5e930bee3c50" } }
```

**mobdebug already supports the fix** — the STACK handler reads `maxlevel`, `maxnum` and `sparse`
from its parameter table. Lunar has simply never passed any of them.

### 3b. Nothing defers the realization at the receiving end

`LuaDebuggerController.variables()` → `LuaRemoteStack.create` → `LuaDebugValueParser.parseChunk`
builds a PSI file from the response text and then `evaluateTableConstructor` recurses every field
of every table unconditionally. `LuaRemoteStack.entries` is an eagerly-computed `val`, and each
`LuaRemoteStackEntry`'s `init` block runs a `LocalFileSystem.findFileByPath` per frame.

## 4. Why DEBUG-07 is part of this bug and not a separate one

[[DEBUG-07]] ("Lazy Remote Stack Evaluation") was marked **Full** in the epic table and `done` in
its feature doc, described as *"Defer parsing of frame details until explicitly accessed"*. Both
were false:

- No commit ever implemented it. `git log` over its doc directory shows only bulk documentation
  edits — `5632a81d` created it as one of **16 placeholder `requirements.md` files for
  zero-coverage epics**, and `47df3605` later applied ✅ to done work items en masse.
- The code that superficially resembles it — `frame` / `locals` / `upvalues` as `get()` accessors
  on `LuaRemoteStackEntry` — shipped in `c1610436`, the first debugging commit, **before the
  requirement existed**. They wrap an already-materialized `LuaTable` and are not memoized, so they
  defer wrapper allocation, not parsing.

Corrected on 2026-08-22: the feature is `todo` / **Not Implemented**, with its terminal state left
to §5.

**The other 15 placeholders are not assumed wrong.** They share this one's provenance — bulk
creation, bulk `done` — so none of their statuses was individually verified, but most are visibly
backed by real implementations (rename, 17 files; luacheck, 31). DEBUG-07 is the one where the code
contradicts the claim. The honest summary is that all 16 `done`s are *unevidenced* and one is
*false*, which argues for a spot-check, not a mass re-audit.

## 5. How to settle it — measure before building either half

1. Instrument `LuaDebuggerController.variables()` to record the response byte count, the
   `parseChunk` wall time, and the realized node count, on a session paused deep in a real script
   (the vendored zerobrane corpus offers deep stacks; `test/debug.lua` does not).
2. Repeat with `maxlevel` passed on the STACK command at a few values.
3. Decide from the numbers:
   - **payload dominates** → send `maxlevel`/`maxnum`; this is the cheap fix and it attacks
     transmission, parse and realization together. [[DEBUG-07]] becomes `cancelled` — deferral
     would be solving a problem that no longer exists.
   - **realization dominates even when bounded** → deferral has a real case; [[DEBUG-07]] stays
     `todo` and gets requirements written for the first time.
   - **neither is measurable** → say so, `cancel` [[DEBUG-07]], and close this as a status fix.

**Do not implement deferral without step 3.** That is the failure this bug records: a feature was
described, marked shipped, and never questioned, and the description turns out to name the weaker
of the two available fixes.

## 5b. Measured, 2026-08-22 — and §3a's proposed fix does not exist

Probes in `~/.cache/claude-scratch/lunar/bug450/`, against a real `lua 5.4.7` + LuaSocket debuggee
running the vendored `src/main/lua/mobdebug`. Parse measured through Lunar's own
`LuaDebugValueParser.parseChunk` on a `BasePlatformTestCase`, warmed twice, median of five.

| case | payload | debuggee serialize | parse (median / min) | realized nodes | distinct tables |
| :-- | --: | --: | --: | --: | --: |
| 5 frames, no extra globals | 13,770 B | ~43 ms | **42.8** / 30.4 ms | 580 | 136 |
| 60 frames | 39,063 B | ~47 ms | **79.6** / 65.5 ms | 2,505 | 796 |
| few frames, 200 globals | 35,001 B | ~48 ms | **46.8** / 36.3 ms | 2,502 | 639 |
| few frames, 1,000 globals | 135,035 B | ~48 ms | **152.0** / 106.6 ms | 11,302 | 3,039 |

**The cheap fix this bug predicted is not available.** §3a says mobdebug "already supports the fix"
via `maxlevel`, `maxnum` and `sparse`. Measured:

- **`maxlevel` is nearly inert.** `init.lua:1001` does `params.maxlevel = tonumber(params.maxlevel)+4`
  to pay for the frame/vars envelope, so `maxlevel=1` is really serpent depth 5 — already the
  minimum useful value. At 60 frames it cuts 39,070 B to 32,133 B, an **18%** reduction.
- **`maxnum` truncates the frame tuple itself.** `maxnum=3` cuts the payload 97% and reduces each
  frame's 7-element tuple to `{nil,"deep.lua",10}`, destroying `currentline`, `what`, `namewhat` and
  `short_src`. Unusable at any value low enough to matter.
- **There is no frame-count parameter.** `stack(start)` takes an offset, but the STACK handler calls
  `coroyield("stack")` with no argument, so the `0..100` loop is unreachable from the wire.

Bounding the payload therefore means patching the vendored debuggee — a larger decision than §5
anticipated, and not the "cheap fix" branch.

**§3a's `_ENV` claim is right in substance, wrong in arithmetic.** `_ENV` *is* serialized in full,
`package.path` and every loaded module included — the `--[[..skipped..]]` markers are `opts.nocode`
eliding function *bodies*, not the table. But serpent **deduplicates by table reference**: `_ENV` is
emitted once and every later occurrence is a post-assignment that reinjects it. So it does not
multiply by frame count. Measured cost is ~124 B per global, counted once.

What *does* scale per frame is the post-assignment section: 48 statements / 2,203 B at 5 frames,
**268 statements / 12,086 B at 60 frames — 30% of the payload** — and flat at 21 statements whether
`_ENV` holds 200 or 1,000 globals. That is why 12x the frames produced only 2.8x the bytes.
`LuaDebugValueParser.parse` does execute these (`LuaAssignmentStatement` -> `executeAssignment`),
so the shared graph is correctly reassembled.

**Severity is lower than this report implies: none of it is on the EDT.** `onPause` does
`scope.launch { ... variables() ... }` (`LuaDebuggerController.kt:314-319`) on a plain
`CoroutineScope`, i.e. `Dispatchers.Default`. The cost is latency before the Variables pane
populates, not a frozen IDE. The secondary effect is a read action held for up to 152 ms, which can
delay write actions.

**Decision — §5 branch 2: realization dominates, so [[DEBUG-07]] stays `todo`.** Parse exceeds
debuggee serialization at every size, and the gap widens with payload. But two findings argue
against building deferral *now*:

1. **A payload bound would not deliver much.** Parse cost per KB is *worst* at the smallest payload
   (3.18 ms/KB vs 1.15), and the floor is ~30 ms — fixed cost in PSI construction, not in walking
   values. Shrinking the payload cannot go below it.
2. **The realistic case is ~125 ms end to end** (60 frames: ~47 ms serialize + ~80 ms parse), off
   the EDT. The 152 ms case needs 1,000 global module tables, which is not a typical project.

So DEBUG-07 is real but low value, and its requirements should be written against these numbers
rather than against the description that was marked shipped. The measurement is the deliverable
here; the fix is not urgent.

## 6. Notes

- **Bounding is not free.** `maxlevel` truncates deep structures the user may want to expand;
  a truncated table is a different kind of wrong from a slow one. If the numbers favour bounding,
  the value must be chosen against a real stack, not picked round.
- The feature doc carries `id: RUN-07` while the epic table calls it `DEBUG-07`. Pre-existing and
  left alone — renaming an `id` moves every wikilink that resolves to it.
