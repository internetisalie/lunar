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

## 6. Notes

- **Bounding is not free.** `maxlevel` truncates deep structures the user may want to expand;
  a truncated table is a different kind of wrong from a slow one. If the numbers favour bounding,
  the value must be chosen against a real stack, not picked round.
- The feature doc carries `id: RUN-07` while the epic table calls it `DEBUG-07`. Pre-existing and
  left alone — renaming an `id` moves every wikilink that resolves to it.
