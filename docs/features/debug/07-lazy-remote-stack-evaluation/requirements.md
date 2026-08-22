---
id: RUN-07
title: "07: Lazy Remote Stack Evaluation"
type: feature
status: "todo"
vf_icon: 📋
priority: "medium"
parent_id: DEBUG/RUN
folders: ["[[features/debug/requirements|requirements]]"]
---

# 07: Lazy Remote Stack Evaluation

> **Measured 2026-08-22 ([[BUG-450]] §5b).** This feature's terminal state was left to that
> measurement and is now settled: it stays `todo`, not `cancelled`. Parse cost exceeds the
> debuggee's serialization at every payload size tested (43 ms vs ~43 ms at 5 frames; 152 ms vs
> ~48 ms at 135 KB), so deferral does address the larger half. Two caveats belong in any
> requirements written from here: parse has a **~30 ms fixed floor** in PSI construction that no
> payload bound can cross, and the whole path runs on `Dispatchers.Default`, so this is populate
> latency (~125 ms in the realistic 60-frame case), never a frozen IDE. The alternative fix
> [[BUG-450]] §3a proposed — passing `maxlevel`/`maxnum` — was measured unavailable: `maxlevel` is
> inert because mobdebug adds 4 to it, and `maxnum` truncates the frame tuple itself.

> **This was marked `done` / **Full** until 2026-08-22 and was never implemented.** The status came
> from a bulk edit, not from a verification: this file was one of 16 placeholders created by
> `5632a81d` for zero-coverage epics, which later received `status: done` and a ✅ wholesale. No
> commit ever implemented the behaviour, and the code does the opposite of what the epic table
> described. See [[BUG-450]] for the evidence and the measurement that decides this feature's fate.

## What it was supposed to be

The epic table's one-line description — *"Defer parsing of frame details until explicitly
accessed"* — is the whole specification that ever existed. No requirements were written, so there
was never anything for `done` to be true against.

## What actually happens

`LuaDebuggerController.variables()` sends `STACK`, and `LuaRemoteStack.create` hands the entire
response to `LuaDebugValueParser.parseChunk`, which parses it into PSI and then walks it,
materializing every frame, every local, every upvalue and every nested table to full depth as
Kotlin `LuaValue`/`LuaTable` before a single tree node renders.

The `frame` / `locals` / `upvalues` accessors on `LuaRemoteStackEntry` are `get()` properties and
look like the feature, but they are not: they wrap an already-materialized `LuaTable`, they are not
memoized, and they shipped in `c1610436` — the first debugging commit, predating the requirement.

## Why this is not simply "todo"

[[BUG-450]] found a cheaper lever than deferral. Lunar's `STACK` command sends no parameters, so
mobdebug serializes with `maxlevel = math.huge` over up to 100 frames including `_ENV`. Bounding
the payload at the protocol — which mobdebug already supports — attacks transmission, PSI parse and
realization at once, where deferral can only avoid the last of the three.

**So this feature may be the wrong answer to a real problem.** Its terminal state is BUG-450's to
decide: `todo` if deferral is still wanted after the measurement, `cancelled` if bounding the
payload makes it unnecessary.
