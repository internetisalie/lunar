---
id: "AI-03"
parent_id: "AI"
type: "feature"
status: "todo"
priority: "medium"
folders:
  - "[[features/ai/requirements|requirements]]"
title: "AI-03: Debugger Toolset"
---

# AI-03: Debugger Toolset (Agentic Debugging)

**Requirement**: MCP tools that let an AI agent drive a real debug session — breakpoints,
stepping, frames, locals, evaluation — over Lunar's debug adapters.
**Priority**: Could
**Status**: Not Implemented

---

## Overview

Promotes AI-01's explicitly deferred "interactive debugger controllers" into a planned
feature. "The agent reproduces the bug under a real debugger" is the frontier of agentic
tooling; no Lua environment offers it. The toolset drives the same XDebugger session
infrastructure a human uses, initially over the MobDebug adapter, with the LDB adapter
(REDIS-02) as a later extension exposing the identical tool surface for Redis/Valkey
scripts.

**Hard prerequisite**: the MobDebug adapter hardening documented in
[docs/review.md](../../../review.md) (framing/charset correctness, connect-failure paths,
error propagation, thread-safe state). An agent hammering the current connection layer
would hit every known hang/desync defect at machine speed. This feature's design doc must
not start until that hardening lands.

### Toolset

| Tool | Behavior |
|---|---|
| `lua_debug_start(run_config \| file, args?)` | Starts a debug session; returns a session handle. **Execution confirmation required** (same `checkUserConfirmationIfNeeded` gate as AI-01 execution tools — this runs user code) |
| `lua_debug_set_breakpoint(file, line, condition?)` / `lua_debug_remove_breakpoint(...)` | Manage line breakpoints (conditions evaluated per the adapter's mechanism) |
| `lua_debug_continue(session)` / `step_over` / `step_into` / `step_out` | Resume/step; each returns the resulting stop state (paused location or terminated) within a timeout |
| `lua_debug_frames(session)` | Stack frames with file/line/function |
| `lua_debug_locals(session, frame)` | Variables for a frame, structured (nested tables expanded to a depth cap) |
| `lua_debug_evaluate(session, frame, expr)` | Expression evaluation in the paused frame |
| `lua_debug_stop(session)` | Terminate session and release resources |

### Session safety model

Confirmation is required **once, at session start** (starting a session = running code);
stepping/inspection tools on an established session are unconfirmed. Sessions carry a
wall-clock cap and an idle timeout, are limited to one concurrent agent session per
project, and are force-terminated when the MCP client disconnects — an abandoned agent
must never leave a paused process or bound port behind.

## Acceptance Criteria

- [ ] Toolset registered via the AI-01 optional-plugin mechanism; absent MCP plugin →
      normal Lunar load
- [ ] `lua_debug_start` enforces the execution-confirmation gate (Brave-Mode aware) and
      returns a session handle plus the initial state (running/paused/failed with reason)
- [ ] Breakpoint set/remove works before start and while paused; invalid lines return a
      structured `valid: false` result
- [ ] Every resume/step tool blocks (with per-call timeout) until the next stop event and
      returns it: paused (file/line/frame) or terminated (exit info); timeout returns a
      `still_running` state without killing the session
- [ ] Frames/locals/evaluate operate only in a paused state; structured error otherwise;
      locals expansion is depth- and size-capped with truncation markers
- [ ] Evaluation failures return structured errors to the agent (never IDE fatal-error
      reports; never hang — per the review.md error-propagation patterns)
- [ ] Lifecycle safety: one concurrent agent session per project; wall-clock and idle
      timeouts; client disconnect force-terminates the session, the debuggee process, and
      the listening port
- [ ] Session events mirrored in the normal IDE debug tool window (the human can watch
      and take over; taking over detaches the agent session gracefully)
- [ ] Adapter abstraction: the tool surface is adapter-agnostic, verified by a design-time
      seam for the REDIS-02 LDB adapter (implementation of the LDB binding may ship with
      the REDIS epic)
- [ ] Integration test: agent-driven session against a fixture script — breakpoint, step,
      locals, evaluate, terminate — plus a disconnect-cleanup test
