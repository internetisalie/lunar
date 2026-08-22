---
id: "BUG-463"
title: "Step and watchpoint pauses have had no source position since the debugger shipped — every non-breakpoint pause renders as `<internal C>`"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-463: `localPosition()` resolves a basedir-relative path against the local filesystem

Found 2026-08-22 by the [[DEBUG-05]] planning agent, and corroborated end to end before filing.
It has **no requirement row in any feature** — [[DEBUG-03]] (Step Over/Into/Out) does not cover
where the resulting pause is displayed, so no spec was in a position to catch it.

## 1. What happens

Step over, step into, step out, or a watchpoint hit. The debugger pauses correctly and the
variables tree populates, but the top frame carries **no source position**: no editor line is
highlighted, Jump to Source does nothing, and the frame renders as `<internal C>`.

Breakpoint pauses are unaffected, which is why this survived — the common path looks right.

## 2. The mechanism

`LuaDebuggerController.kt:326` is the **only** caller of `localPosition()`, and it sits on the
non-breakpoint pause path:

```kotlin
val sp: XSourcePosition? = pos.localPosition()
```

`LuaPosition.localPosition()` (`run/LuaPosition.kt:31-35`) resolves the position by

```kotlin
LocalFileSystem.getInstance().findFileByPath(path)
```

where `path` is the **wire** path — mobdebug reports pause positions relative to `BASEDIR`
(`sub/target.lua`, not `/home/u/proj/sub/target.lua`; see [[DEBUG-05]]'s Probe A transcript).
`findFileByPath` takes an absolute path, so for any project not rooted at the filesystem root it
returns `null`. `createLocalPosition` then short-circuits (`:54`, `if (virtualFile == null) return
null`), `LuaSuspendContext` builds a top frame with a null position, and
`LuaStackFrame.customizePresentation` (`run/LuaStackFrame.kt:125-134`) renders `<internal C>`.

**Why breakpoint pauses escape.** `LuaSuspendContext:41` assigns `this.position =
breakpoint?.sourcePosition` — the position the IDE already holds for the breakpoint, never routed
through `localPosition()`. Two constructors, two sources; only one is broken.

## 3. Scope

Every Lunar debug session on every platform, for every project whose sources are not at `/`. It is
not remote-specific and not new — it has been present since the debugger shipped.

## 4. Fix strategy

Resolve the wire path against the session's base directory before hitting the filesystem, rather
than handing a relative path to `findFileByPath`. [[DEBUG-05]]'s Phase 1 does this incidentally as
part of `LuaFrameResolver`, which centralises wire→local translation for a different reason
(`DEBUG-05-08`/`-09`).

**Do not fix this and DEBUG-05 Phase 1 separately.** They rewrite the same translation step, so two
fixes means one is rewritten within the week. Either take Phase 1, or — if [[DEBUG-05]] is
deprioritised — split Phase 1 out and land it under this ID. That choice is recorded in
[[DEBUG-05]]'s risks-and-gaps as Risk 1.4's sibling.

## 5. Test strategy

A regression test must pause somewhere **other** than a breakpoint, which no existing test does —
`TestLuaDebugHarness.testBreakpointAndExec` is the only end-to-end debug test and asserts the
breakpoint path, the one path that works.

The decisive fixture is a project whose script is **not** at the filesystem root (i.e. any real
project), stepped once. Mutation that must turn it red: restore `findFileByPath(path)` in place of
the basedir-resolved lookup. Note that a fixture rooted at `/` would leave the test green under
that mutation — the bug is invisible at the filesystem root, so the fixture's depth *is* the test.
