---
id: "MAINT-24-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-24"
folders:
  - "[[features/maint/24-debugger-hardening/requirements|requirements]]"
---

# MAINT-24: Risks & Gaps

Hardening a legacy subsystem that MAINT-22 partially rewrote and REDIS-02 forked (LDB). The chief
risk is verification: most of the debugger is only honestly testable against a live debuggee, so the
plan leans on pure-function extraction for unit coverage plus a VNC DoD gate (MAINT-22 precedent).

## Critical Risks

### Risk 1.1: Framing rewrite regresses the working debug flow
- **Impact**: switching `LuaDebugConnection` from `BufferedReader` to raw `InputStream` framing
  (§3.1) touches the hot path; a boundary bug (off-by-one on the length prefix, dropped `\n`) breaks
  every pause/eval.
- **Likelihood**: medium.
- **Mitigation**: extract `DbgpFraming` as a pure object with direct unit tests over
  `ByteArrayInputStream` (byte-count read, CRLF, short read) — no socket needed, mirroring the
  existing socket-free `TestLuaDebugConnectionParsing`. Then VNC-verify a real breakpoint + a
  UTF-8 variable value (HV-04). DR-01.

### Risk 1.2: `ConcurrentHashMap` null-key rejection (#18)
- **Impact**: `ConcurrentHashMap` throws `NullPointerException` on a null key/value, unlike the
  current `HashMap`. The maps' declared types are nullable (`XBreakpoint<*>?` / `LuaPosition?`).
- **Likelihood**: low.
- **Mitigation**: `addBreakPoint`/`removeBreakPoint` already early-`return` on a null
  `sourcePosition` (`LuaDebuggerController.kt:187,197`), so only non-null keys/values reach the maps
  — documented in design §3.3. Verify by inspection during Phase 3.

### Risk 1.3: mobdebug ignores `MOBDEBUG_PORT` after a prior `start()` (C1)
- **Impact**: the configurable port (§3.7) silently falls back to 8172.
- **Likelihood**: low.
- **Mitigation**: `lunar/debug.lua` is updated to read the env and pass `start(host, port)`
  explicitly (`init.lua:1063` accepts the port), not rely on the module default. VNC-verify a
  non-8172 port (HV-07). DR-03.

## Design Gaps

None open — every decision the design left ambiguous was resolved into §2–§3. The two items below
are deliberate scope deferrals, not open questions.

## Technical Debt & Future Work

- **TBD: per-test streaming busted events** — busted's `--output=json` emits one report object at
  process end, so a live test *tree* cannot stream during the run. §2.8 delivers live *console*
  output as the honest middle ground; true per-test streaming would require a custom busted output
  handler (a Lua-side artifact) and is out of scope. Tracked as DR-02.
- **TBD: non-`run/` `!!` sites** — review §2.2 lists ~20 more `!!` outside `run/` (LuaCATS renderer,
  luacheck, settings, psi). Owned by MAINT-25/26/27/28; explicitly out of scope here.
- **TBD: EDT-safety audit of `runToPosition` entry** — `runToPosition` dispatches to `sessionScope`
  immediately (§3.2), so no PSI/VFS runs on the EDT; a deeper audit of all `XDebugProcess` overrides
  is left to a future review.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-00-DR-01 | Prototype `DbgpFraming` + unit tests over `ByteArrayInputStream` (byte-count read, CRLF, short read) before touching `LuaDebugConnection` | Risk 1.1 | todo |
| MAINT-00-DR-02 | Confirm empirically that busted `--output=json` emits a single terminal report (no per-test lines) — records the streaming limit behind §2.8 | Future Work (streaming) | todo |
| MAINT-00-DR-03 | Launch a debug session on a non-8172 port over VNC; confirm mobdebug binds it via `MOBDEBUG_PORT` + `start(host, port)` | Risk 1.3 | todo |

## Test Case Gaps

- **VNC-gated rows** (only honestly verifiable live, per MAINT-22 DoD): MAINT-24-01 (real UTF-8
  variable value end to end), MAINT-24-04 (Run to Cursor SETB/RUN/DELB sequencing), MAINT-24-07
  live console output, MAINT-24-08 (port binding + graceful EXIT). Their pure-function halves
  (framing codec, Lua-pattern escaper, JSON scanner) ARE unit-tested; the socket/UI halves are
  HV-04/06/07/08.
- **No unit coverage for the reader-coroutine race (#18)**: `ConcurrentHashMap` correctness is by
  construction; a deterministic race test is impractical. Covered by inspection + HV-04.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
