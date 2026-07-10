---
id: "REDIS-02-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "REDIS-02"
folders:
  - "[[features/redis/02-ldb-debugger/requirements|requirements]]"
---

# Verification Checklists: REDIS-02 — LDB Debug Adapter

Manual, human-run scenarios that confirm the LDB debug adapter works in a real IDE — the interactive
behaviors that unit and integration tests cannot cover (gutter/frame visuals, panels, dialogs,
banners, session lifecycle). Each scenario starts from a clean state and has an explicit expected
result. Every `Must` behavior deferred to human verification (per the briefing) appears here.

**Common setup**: a project whose Lua target platform is Redis (`Settings | Languages & Frameworks |
Lua | Lua Project` → target Redis), a REDIS-01 "Redis Script" run configuration pointing at a script
`script.lua`, and a REDIS-01 connection. Unless a scenario says "remote", use a session-local
connection (LocalBinary `redis-server` or Docker `redis:8`). Repeat §1, §2, §4 once against
`valkey/valkey:8` to confirm dual-flavor behavior (AC-10).

## 1. Breakpoint hit, step, continue (AC-1, AC-2, AC-4)

### Scenario 1.1: Breakpoint is hit and the frame shows
- **Setup**: `script.lua` with several statement lines; a Redis Script run config in **Forked** mode.
- **Steps**:
  1. Set a line breakpoint on a statement line (line 3).
  2. Click **Debug**.
- **Expected**: the session starts; execution pauses at line 3; the gutter shows the current-line
  marker on line 3; the Frames panel shows one frame at `script.lua:3`; no error dialog / fatal-error
  report appears.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Breakpoint cannot be placed on a non-code line
- **Setup**: `script.lua` with a blank line and a `-- comment` line.
- **Steps**:
  1. Attempt to set a breakpoint on the blank line and on the comment line.
- **Expected**: the breakpoint is not accepted (no valid breakpoint marker) on the blank/comment
  lines; it is accepted on statement lines.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Step Over / Step Into / Resume
- **Setup**: paused at line 3 (Scenario 1.1).
- **Steps**:
  1. Click **Step Over** — observe the current line advance by one statement.
  2. Click **Step Into** on a `redis.call`/local-function line.
  3. Click **Resume**.
- **Expected**: Step Over/Into advance the current-line marker; Resume runs the script to completion;
  the run reply appears in the console; the session ends cleanly.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.4: Step Out is disabled with a tooltip
- **Setup**: paused at a breakpoint.
- **Steps**:
  1. Hover / attempt the **Step Out** toolbar action.
- **Expected**: Step Out is unavailable (grayed); the tooltip reads "Step Out is not supported by the
  Redis Lua debugger".
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.5: Add/remove a breakpoint while paused
- **Setup**: paused at line 3.
- **Steps**:
  1. Add a breakpoint on a later line (line 5) while paused; Resume.
  2. On the next pause, remove the line-5 breakpoint; Resume.
- **Expected**: the session pauses at line 5 after step 1; after removing it, Resume runs to
  completion without pausing at line 5.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Locals, watches, evaluate (AC-5)

### Scenario 2.1: Variables view shows locals including tables
- **Setup**: paused where a local scalar `x` and a local table `t = {a=1, b={c=2}}` are in scope.
- **Steps**:
  1. Open the Variables panel.
  2. Expand `t`, then `t.b`.
- **Expected**: `x` shows its scalar value; `t` is expandable; expanding shows `a = 1` and a nested
  `b` that expands to `c = 2`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Watch expression
- **Setup**: paused as in 2.1.
- **Steps**:
  1. Add a watch `x + 1`.
- **Expected**: the watch evaluates and shows the result (e.g. `11` for `x = 10`); an invalid watch
  (`nosuch()`) shows an error indicator, not a hang.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Evaluate dialog
- **Setup**: paused as in 2.1.
- **Steps**:
  1. Open **Evaluate Expression**; evaluate `t.a` and then a deliberately broken expression `t.`.
- **Expected**: `t.a` returns `1`; the broken expression reports an evaluation error in the dialog
  (via `errorOccurred`), not a silent blank or a hang.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.4: Truncated table value
- **Setup**: paused where a large/deeply-nested table is in scope (exceeds LDB `maxlen`).
- **Steps**:
  1. Expand the large table in the Variables panel.
- **Expected**: the value renders as far as LDB provides and shows a truncation indicator; the IDE
  does not error or hang on the truncated repr.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Sync vs Forked guarding, banners, errors (AC-7, AC-8, AC-9)

### Scenario 3.1: Forked banner (default)
- **Setup**: Forked-mode run config.
- **Steps**:
  1. Start a debug session.
- **Expected**: a session banner/message states writes are rolled back (e.g. "Forked debug session:
  all writes are rolled back when the session ends").
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Sync-mode confirmation on a remote connection
- **Setup**: run config in **Sync** mode; a **remote** (provisioning = Remote) connection.
- **Steps**:
  1. Start a debug session.
- **Expected**: before any `SCRIPT DEBUG SYNC` is sent, a confirmation dialog warns that the server
  event loop will be BLOCKED and writes COMMITTED; declining stops the session; accepting proceeds and
  shows the sync banner.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.3: Sync-mode against a session-local server does not prompt
- **Setup**: **Sync** mode; a LocalBinary/Docker (session-local) connection.
- **Steps**:
  1. Start a debug session.
- **Expected**: no confirmation dialog (the launched server is disposable); the sync banner still shows.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.4: `SCRIPT DEBUG` rejected (managed server) — optional
- **Setup**: a connection to a managed instance that forbids `SCRIPT DEBUG` (if available).
- **Steps**:
  1. Start a debug session.
- **Expected**: an actionable error surfaces ("this server does not permit script debugging — debug
  against a local server instead"); no hang, no IDE fatal-error report.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.5: Compile error in the script
- **Setup**: `script.lua` with a syntax error.
- **Steps**:
  1. Click **Debug**.
- **Expected**: the compile error (with the `user_script:<N>` location) is reported in the session UI;
  the session ends cleanly; no fatal-error report.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Mid-debug Redis commands (AC-6)

### Scenario 4.1: Run a Redis command while paused
- **Setup**: paused at a breakpoint.
- **Steps**:
  1. Open the **Redis** console tab in the debug window.
  2. Run `SET k 1`, then `GET k`, then `HSET h f v` and `HGETALL h`.
- **Expected**: each command executes in the paused session; scalar replies render inline; the
  `HGETALL` reply renders as an expandable reply tree (reusing the REDIS-01 reply-tree console).
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Fork vs Sync session lifecycle & teardown (AC-9, lifecycle)

### Scenario 5.1: Forked writes are rolled back
- **Setup**: Forked mode; `script.lua` performs a write (e.g. `redis.call('SET','probe','1')`).
- **Steps**:
  1. Debug the script to completion (or abort mid-way).
  2. In a separate Redis client, `GET probe`.
- **Expected**: `probe` is **not** set (forked writes rolled back).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.2: Sync writes are committed
- **Setup**: Sync mode (session-local server); the same write script.
- **Steps**:
  1. Debug to completion; then `GET probe` in a separate client.
- **Expected**: `probe` **is** set (sync writes committed) — consistent with the sync banner.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.3: Forked-session server timeout
- **Setup**: Forked mode; pause at a breakpoint and leave the session idle past the server's
  forked-session timeout.
- **Steps**:
  1. Wait for the server to close the forked session.
- **Expected**: the IDE shows a clean "Redis debug session ended by the server (forked-session
  timeout)" message; no exception / fatal-error report.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.4: No orphaned server/client after Stop and Rerun
- **Setup**: Docker (or LocalBinary) provisioning.
- **Steps**:
  1. Start a debug session; click **Stop**.
  2. Click **Rerun**; then Stop again.
  3. Check for leftover processes/containers (`docker ps` / process list).
- **Expected**: each session's launched server is torn down on Stop; no orphaned `redis-server`
  process or container remains after Stop/Rerun; the `RespClient` is disposed.
- **Result**: ⬜ Pass / ⬜ Fail

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
</content>
