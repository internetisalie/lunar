---
id: DEBUG-05
title: "05: Remote Debugging"
type: feature
status: "planned"
vf_icon: 📋
priority: "medium"
parent_id: DEBUG/RUN
folders: ["[[features/debug/requirements|requirements]]"]
---

# 05: Remote Debugging

Debug a Lua process the IDE did not start, running under a source tree the IDE does not own.

## The contradiction this document resolves

Two documents in this repo disagree about DEBUG-05:

- [[features/debug/requirements|the DEBUG epic table]] marks it **Full** — *"Support connecting to
  external Lua processes (e.g., via Mobdebug)"*.
- [[plugin-feature-comparison]] marks Lunar **✗** on the "Remote debugger" row and credits
  IntelliJ-EmmyLua with **✔ (Mobdebug)**.

**The epic table is the one that is wrong.** The comparison matrix is right on this row, and it is
right for the reason it looks like it should be wrong: "remote debugging" names two different
capabilities, and Lunar ships only the one nobody means by the phrase.

| | **A. Loopback attach-back** | **B. Remote attach** |
| :-- | :-- | :-- |
| Who dials whom | debuggee dials the listening IDE | same |
| Who starts the debuggee | **the IDE**, as a local child process | a human, on another host |
| Filesystem | one, shared | two, unrelated |
| Path handling needed | strip one prefix | a mapping, in both directions |
| Lunar | **shipped** | **absent** |

Capability A is real and works: `LuaDebuggerController.connect()` binds a `ServerSocket` and the
debuggee dials back into it, which is *architecturally* a remote debugger and is what made the epic
table's claim plausible. But it is the transport DEBUG-01/-02/-03 already needed in order to debug a
local script at all — mobdebug has no in-process mode, so every Lunar debug session has always been
"remote" in this sense. Nothing DEBUG-05 uniquely names is implemented: there is no host field, no
attach-style run configuration, no path mapping, and a five-second window in which to connect.

Capability B is what the matrix's ✗ is about and what IntelliJ-EmmyLua's ✔ delivers — a separate
`LuaMobConfigurationType` ("Lua Remote(Mobdebug)") whose runner never calls `state.execute(...)`, plus
`LuaMobDebugProcess.recognizeBaseDir`, which *infers* the local↔remote root mapping by suffix-matching
the debuggee's chunk name against a project file found by name, then re-sends every breakpoint.

**The matrix is stale in general and should not be read as vindicated wholesale** — its Lunar column
was last touched 2026-06-30 and still marks keyword and symbol completion ✗ while every
`docs/features/completion/*/requirements.md` is `status: done`. It is correct here by being specific,
not by being current.

### Provenance of the false `Full`

This feature's doc has the same origin as [[DEBUG-07]]'s, which [[BUG-450]] §4 traced: `5632a81d`
created it as one of 16 placeholder `requirements.md` files for zero-coverage epics, and `47df3605`
applied ✅ to done work items en masse. `git log` over the directory shows **four commits, all bulk
documentation edits, and no implementation commit**. The status was never earned.

## How these requirements were derived

**Not from Lunar's code** — a specification read off its own implementation cannot fail. The rows
below come from four external sources, and Lunar was checked against them afterwards:

1. **mobdebug's own remote model** (`src/main/lua/mobdebug/init.lua`, v0.805). The protocol decides
   what is possible: `start(host, port)` makes the *debuggee* the TCP client and `listen(host, port)`
   is mobdebug's own reference controller, so the IDE is always the server; the port is `8172` or
   `MOBDEBUG_PORT`; `BASEDIR <dir>` plus `removebasedir` is the **entire** path-translation
   mechanism — one prefix string, no mapping table; and `stack()` reports each frame's path twice,
   once basedir-stripped and once as Lua's raw `short_src`.
2. **The IntelliJ Platform's attach contract**, from `~/Documents/src/lua/intellij-community`. An
   attaching configuration is a different shape from a launching one:
   `RemoteConfiguration implements RunConfigurationWithSuppressedDefaultRunAction, RemoteRunProfile`
   (Debug only, no Run action), carries `HOST` / `PORT` / `SERVER_MODE` / `AUTO_RESTART`, and its
   `RunProfileState` is `RemoteStateState`, whose `execute` returns a `RemoteDebugProcessHandler` —
   a synthetic handler with `detachIsDefault() = true` that owns no OS process. Path translation is
   `PathMappingSettings` / `AbstractPathMapper.convertToLocal|convertToRemote`, surfaced per-language
   as a bidirectional converter (`PyPositionConverter.convertToPython` / `convertFromPython`).
   "Attach to Process…" is the separate `com.intellij.xdebugger.attachDebuggerProvider` EP.
3. **[[plugin-feature-comparison]]** and the IntelliJ-EmmyLua sources it credits.
4. **Executed probes**, 2026-08-22 — transcripts below. Every behavioural claim in the table was run,
   not read.

Where a capability is unimplemented, the row says which of the two capabilities it belongs to, so the
table cannot be read as a demand that local debugging be rebuilt.

## Executed evidence (2026-08-22)

A three-line Python controller standing in for the IDE, against the bundled `src/main/lua/mobdebug`
and `src/main/lua/debug.lua` preloader, `lua 5.4` + LuaSocket 3.0.0.

**Probe A — shared filesystem, IDE root == debuggee cwd.** Works, and shows the wire format:

```
>> BASEDIR /…/debug05/proj/      << 200 OK
>> SETB sub/target.lua 2         << 200 OK
>> RUN                           << 200 OK
                                 << 202 Paused sub/target.lua 2
>> STACK  << 200 OK do local _={{{"inner","sub/target.lua",1,2,"Lua","local","sub/target.lua"},…
```

Pause positions and frame field 2 are basedir-relative; frame field 7 (`short_src`, the field
`LuaRemoteStackFrame.path` reads at index 6) is whatever the debuggee was launched with.

**Probe B — the remote case. The IDE's root and the debuggee's root differ.** Same commands, only
`BASEDIR` changed to a root the debuggee knows nothing about:

```
>> BASEDIR /…/debug05/ideroot/   << 200 OK
>> SETB sub/target.lua 2         << 200 OK
>> RUN                           << 200 OK
                                 << <EOF — debuggee exited, NO PAUSE>
debuggee stdout: 42
```

**The breakpoint never fires and nothing reports it.** `debug_hook` strips `basedir` from the running
file, the prefix does not match, `has_breakpoint("/…/remoteroot/sub/target.lua", 2)` misses, and the
program runs to completion. The IDE sees a clean disconnect. This is the whole of capability B in one
transcript.

**Probe C — `short_src` truncation.** Launching with an absolute path 66 characters long:

```
<< 200 OK do local _={{{"inner","sub/target.lua",1,2,"Lua","local",
                        ".../.cache/claude-scratch/lunar/debug05/proj/sub/target.lua"},…
```

Measured threshold, one directory-name character at a time:

```
pathlen=59 -> len 59  exact
pathlen=60 -> len 59  TRUNCATED
pathlen=64 -> len 59  TRUNCATED
```

Lua caps `short_src` at `LUA_IDSIZE` (60 incl. NUL) and prefixes `...`. Any script whose absolute
path is **≥ 60 characters** arrives mangled.

**Probe D — the listener's bind address**, `corretto-21.0.10`:

```
new ServerSocket(18999) -> bound to 0.0.0.0/0.0.0.0  wildcard=true
```

`LuaDebuggerController.connect()` uses exactly this constructor, so the debug port is reachable from
any host on the network with no way to restrict it.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `DEBUG-05-01` | **IDE listens; the debuggee dials in** | **M** | **Full** | Capability A. `LuaDebuggerController.connect()` binds `ServerSocket(serverPort)` and `accept()`s; the debuggee's `mobdebug.start(host, port)` is the TCP client. The inverse of mobdebug's own `listen()` console controller, and the shape every mobdebug IDE must take. |
| `DEBUG-05-02` | **Debug port is configurable, default 8172** | **M** | **Full** | `LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT = 8172` matches mobdebug's default; the "Debug port" spinner in `LuaRunSettingsEditor` persists it and it reaches the debuggee as `MOBDEBUG_PORT`. |
| `DEBUG-05-03` | **Wire paths are root-relative (`BASEDIR`)** | **M** | **Full** | `setBaseDir()` sends `BASEDIR` immediately after accept, from the run config's working directory or `project.basePath`; `LuaPosition.createRemotePosition` relativises breakpoints against the same root. Probe A confirms the debuggee then reports `sub/target.lua`, not an absolute path. |
| `DEBUG-05-04` | **Debuggee host is configurable** | **M** | **Not Implemented** | The bundled preloader already reads it — `src/main/lua/lunar/debug.lua`: `os.getenv("MOBDEBUG_HOST") or "localhost"` — but `MOBDEBUG_HOST` appears **nowhere else in the repository**, and `LuaRunSettingsEditor` has a port spinner and no host field. The debuggee half is remote-ready; the IDE half never uses it. |
| `DEBUG-05-05` | **A configuration that attaches rather than launches** | **M** | **Not Implemented** | Capability B's entry point. `LuaDebugRunner.canRun` accepts only `LuaRunConfiguration`, `doExecute` calls `state.execute(environment.executor, this)` — which spawns a local `lua` — and `LuaDebugProcess` requires that `ExecutionResult`, destroys its process in `stop()`, and terminates the controller from a `processTerminated` listener. No `<configurationType>` in `plugin.xml` offers a remote profile. The platform shape to adopt is `RunConfigurationWithSuppressedDefaultRunAction` + a `RemoteDebugProcessHandler`-style handler (`detachIsDefault() = true`); EmmyLua's `LuaMobDebuggerRunner.doExecute` ignores its `RunProfileState` entirely for the same reason. |
| `DEBUG-05-06` | **Listen window sized for a human** | **M** | **Not Implemented** | `LuaDebuggerController.CONNECT_TIMEOUT_MS = 5_000` on the accept. Adequate when the IDE just spawned the debuggee two lines earlier; unusable when a person must go and start a program on another machine. An attach session needs an unbounded (cancellable) wait. |
| `DEBUG-05-07` | **Breakpoints reach a differently-rooted debuggee** | **M** | **Not Implemented** | IDE→debuggee direction. `LuaPosition.createRemotePosition(pos, workingDir)` can only strip the *local* working directory, so `SETB` carries a path relative to the IDE's root. Probe B: when the debuggee's root differs, the breakpoint silently never fires. There is no `PathMappingSettings` equivalent, and no diagnostic when a `SETB` matches nothing — mobdebug answers `200 OK` to a breakpoint on a file it will never load. |
| `DEBUG-05-08` | **Frames resolve to local files** | **M** | **Not Implemented** | debuggee→IDE direction. `LuaRemoteStackEntry.init` does `LocalFileSystem.getInstance().findFileByPath(frame.path)` on the raw `short_src`, so navigation works only where the debuggee's absolute paths are also valid on the IDE host — and *silently mis-resolves* to an unrelated local file where they collide. `LuaExecutionStack.computeStackFrames` turns the resulting null into a frame with no position, which `LuaStackFrame.customizePresentation` renders as `<internal C>`. Compare `recognizeBaseDir`, which derives the mapping from the first frame that suffix-matches a project file. |
| `DEBUG-05-09` | **Frame paths survive `short_src` truncation** | **S** | **Not Implemented** | Probe C: paths ≥ 60 characters arrive as `.../tail`, `findFileByPath` returns null, and an ordinary Lua frame renders as `<internal C>` with no navigation. **This is not a remote-only defect** — it fires on a purely local session under any moderately deep project path. Frame field 2 (basedir-relative, untruncated) is already on the wire and is the field to use; `LuaRemoteStackFrame` reads index 6 instead. |
| `DEBUG-05-10` | **Debuggee output reaches the console** | **S** | **Not Implemented** | An attached session has no local `ProcessHandler`, so `LuaDebugProcess.createConsole`'s `executionResult.executionConsole` has nothing to attach to. mobdebug's answer is the `OUTPUT stdout r` command, which redirects `print` into `204 Output` frames — but `DebugCommandKind` declares no `OUTPUT`, and `LuaDebugConnection.handleLine` has no branch for `DebuggerStatus.Output` in either the in-flight or the running case, so such a frame would fall to `log.error` with its payload left unread, desynchronising the stream. |
| `DEBUG-05-11` | **Stale breakpoints cleared on connect** | **S** | **Not Implemented** | A debuggee that reconnects, or one started by a user who ran it before, may hold breakpoints from a prior session. `drainInstalledBreakpoints` only adds. EmmyLua opens every connection with `DELB * 0`; mobdebug supports exactly that spelling — `remove_breakpoint` special-cases `file == '*' and line == 0` to `breakpoints = {}`. |
| `DEBUG-05-12` | **Listener is loopback-only unless remote is requested** | **S** | **Not Implemented** | Probe D: `ServerSocket(port)` binds `0.0.0.0`, so port 8172 accepts connections from the network during every debug session, and the first thing the controller does with a peer is grant it `EXEC` — arbitrary Lua evaluation in the debuggee, on the IDE's initiative. There is no bind-address option, and `clientAddress` is captured only to log and to print "Debugger connected at …" — never checked. Capability A should bind loopback; capability B should make the exposure explicit. |
| `DEBUG-05-13` | **Repeat / auto-restart listening** | **C** | **Not Implemented** | `connect()` accepts exactly one client and `close()` — reached from `onDisconnected` — closes the `ServerSocket` and cancels the session scope. The platform models the alternative as `RemoteConfiguration.AUTO_RESTART`, which keeps the listener up so successive runs of the debuggee reattach without restarting the session. |
| `DEBUG-05-14` | **Debug a Lua chunk running on another host, at all** | **S** | **Partial** | Lunar *does* have one working remote debugger — REDIS-02's LDB session (`LuaRedisDebugRunner`, `LuaLdbController`) drives a script on a Redis/Valkey server at a configurable `LuaRedisConnectionSettings.host`, defaulting to `127.0.0.1`. Path mapping is a non-problem there because the IDE ships the script it debugs. It is a different protocol under a different epic, so it neither satisfies DEBUG-05 nor leaves the matrix's ✗ unqualified. |
| `DEBUG-05-15` | **Attach to an already-running Lua process** | **C** | **Won't** | The `com.intellij.xdebugger.attachDebuggerProvider` EP models picking a live PID. Lua has no external attach API — nothing can make a running interpreter `require("mobdebug")` from outside, and mobdebug offers no such entry point (`start`/`listen`/`on`/`loop` are all in-process). A *cooperating* process that already loaded mobdebug and calls `start()` on demand is served by `DEBUG-05-05`, not by an attach provider. |
| `DEBUG-05-16` | **Fetch sources from the debuggee** | **W** | **Won't** | The IDE could show frames for files it does not have locally if the debuggee could serve them. The 16 wire verbs `debugger_loop` accepts (`SETB`/`DELB`/`SETW`/`DELW`/`EXEC`/`LOAD`/`STACK`/`OUTPUT`/`BASEDIR`/`SUSPEND`/`RUN`/`STEP`/`OVER`/`OUT`/`DONE`/`EXIT`) include no source-retrieval verb; `LOAD` pushes a chunk *to* the debuggee. Adding one means forking the vendored debuggee, and every other IDE in the comparison expects a local copy instead. |

Status distribution: **Full 3, Partial 1, Not Implemented 10, Won't 2** (16 rows; **M** 8, **S** 5,
**C** 2, **W** 1). Five of the eight `Must` rows are unimplemented, which is why the front-matter is
`todo` and not `done`.

## Scope

**In scope.** Everything needed to point the IDE at a Lua process it did not start: an attach-style
configuration, host/port, a listen window, bidirectional path mapping, and console output for a
session with no local process.

**Out of scope.** The DBGp transport, breakpoint/step/evaluate semantics and the value model — those
are DEBUG-01/-02/-03/-04 and are unchanged by this feature. `DEBUG-05-09` is listed here because the
remote path story is where it was found, but it is a local defect and should be fixed as one.

## Verification

Existing coverage, all of it capability A:

- **`TestLuaDebugHarness.testBreakpointAndExec`** is the only end-to-end debug test. It spawns a
  local `lua` child, accepts on `ServerSocket(8172)`, and sends `BASEDIR` with a trailing slash —
  i.e. it asserts the *shared-filesystem, matching-root* case, which is exactly the case Probe A also
  passes and Probe B fails.
- **`TestLuaRunConfiguration.testDebugPortRoundTripsThroughEditor`** covers `DEBUG-05-02`;
  `testEmptyWorkingDirectoryFallsBackToBasePath` covers the root that `DEBUG-05-03` sends.
- **`TestLuaPosition`** (`testCreateRemotePosition`, `testCreateRemotePositionFallsBackToAbsolutePath`,
  `testRoundTripLineConversion`) covers the IDE→wire half of `DEBUG-05-03` only.
- **`TestLuaRemoteStackFrames`**, **`TestLuaExecutionStack`** build frames from a hand-written fixture
  chunk whose paths are 43-character absolutes — below the 60-character `short_src` cap, so
  `DEBUG-05-09` is **structurally invisible** to the suite.
- **`TestLuaDebugConnection`**, **`TestLuaDebugConnectionParsing`** cover the command/status model and
  the pause-line patterns; neither exercises `DebuggerStatus.Output`.

**No test exercises a mismatched `BASEDIR`, a non-loopback peer, a truncated `short_src`, or a
reconnect.** Any implementation of `DEBUG-05-07`/`-08` must add a case where the debuggee's root and
the project root differ — the harness makes this cheap, since it can launch the child from a copied
tree — and `DEBUG-05-09` needs only a fixture path past 60 characters.

**`DEBUG-05-09`, `-10` and `-12` were found by writing this table** and are recorded nowhere else in
the repository. `-09` is a live defect in shipped local debugging, `-12` is a security exposure in
every debug session today, and neither has a bug report. `-10` is latent: no code path currently
sends `OUTPUT`, so the missing handler cannot fire until someone adds one.
