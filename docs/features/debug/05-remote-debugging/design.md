---
id: "DEBUG-05-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "DEBUG-05"
folders:
  - "[[features/debug/05-remote-debugging/requirements|requirements]]"
---

# Technical Design: DEBUG-05 — Remote Debugging

Realizes [requirements.md](requirements.md). Every symbol below is either grounded to a real
`file:line` in this repo or in `~/Documents/src/lua/intellij-community`, or explicitly marked
**NEW**. Every behavioural claim is backed by an executed probe whose transcript is pasted here
(§0); nothing about mobdebug, the JDK socket API or the platform is asserted from reading.

**Hard dependency.** This design consumes [[DEBUG-06]]'s validation model
(`LuaTargetSpec` / `LuaTargetValidator` / `LuaTargetChecks`, DEBUG-06 design §2.1–§2.5), whose
§2.10 was written as this feature's extensibility contract. Phase 3 of the plan cannot compile
until DEBUG-06 Phases 1–2 are merged; Phases 1–2 of *this* feature depend on nothing.

## 0. Executed evidence (2026-08-22)

Probes A–D are in [requirements.md](requirements.md) and are **not** repeated. Probes E–M below
are new, run against the bundled `src/main/lua/mobdebug` (v0.805) with `lua 5.4.7` +
LuaSocket 3.0.0 and `corretto-21.0.10`. Scripts live in `~/.cache/claude-scratch/lunar/debug05/`
(`ctl.py`, `probeE2.py`, `probeFG.py`, `probeI.py`, `probeJ.py`, `probeK2.py`, `probeM3.py`,
`BindProbe.java`, `IntrProbe.java`, `PollProbe.java`).

### Probe E — `STACK` works **before** `RUN`, and frame field 2 diagnoses a root mismatch

```
=== A: absolute launch, MATCHED basedir
   BASEDIR -> 200 OK
   STACK f1 -> {nil,"sub/target.lua",0,5,"main","",".../debug05/proj/sub/target.lua"
   SETB     -> 200 OK
   RUN      -> 200 OK
   next     -> 202 Paused sub/target.lua 2

=== B: absolute launch, MISMATCHED basedir
   BASEDIR -> 200 OK
   STACK f1 -> {nil,"/home/mini/.cache/claude-scratch/lunar/debug05/proj/sub/target.lua",0,5,"main","",".../proj/sub/target.lua"
   SETB     -> 200 OK
   RUN      -> 200 OK
   next     -> None        <-- no pause; debuggee ran to completion (reproduces Probe B)
   debuggee stdout: 'out:42\n'

=== B2: RELATIVE launch (lua sub/target.lua), MISMATCHED basedir
   STACK f1 -> {nil,"sub/target.lua",0,5,"main","","sub/target.lua"
   next     -> 202 Paused sub/target.lua 2     <-- breakpoint DOES fire
```

Three facts this pins, none of which was safe to assume:

1. **The debuggee is already inside `debugger_loop` and already paused at its first line when the
   IDE accepts.** `seen_hook` is set by then, so `STACK` returns a real stack before any `RUN`
   (`src/main/lua/mobdebug/init.lua:983-988` is the "have we seen the hook" guard that would
   otherwise return an empty result).
2. **Frame field 2 is `removebasedir(src, basedir)`** (`init.lua:343-347`). When the configured
   basedir prefixes the debuggee's real path it is **relative**; when it does not, the strip at
   `init.lua:657` is a no-op and it stays **absolute**. That single test is the whole
   root-mismatch diagnostic (§3.5).
3. **A relatively-launched debuggee masks the mismatch entirely** — the wire is already relative
   and the breakpoint binds. The diagnostic must therefore not fire on a relative field 2.

### Probe I — field 2 survives the `short_src` cap; field 6 does not (`DEBUG-05-09`)

```
absolute script path length: 117
BASEDIR -> 200 OK
frame1 raw: {nil,"src/module/target.lua",0,5,"main","","...eply_nested_project_directory_name/src/module/target.lua"
field2 (basedir-relative) = 'src/module/target.lua'                                    len 21
field7 (short_src)        = '...eply_nested_project_directory_name/src/module/target.lua'  len 59
SETB -> 200 OK   RUN -> 200 OK
pause-> 202 Paused src/module/target.lua 2
```

`LuaRemoteStackFrame.path` reads index 6 = field 7 (`run/LuaRemoteStack.kt:82-83`); index 1 =
field 2 is already exposed as `LuaRemoteStackFrame.file` (`:73-74`) and is untruncated. The
`202 Paused` line also carries the basedir-relative form.

### Probe J — an absolute `SETB` outside the basedir **does** bind; `MOBDEBUG_HOST` works

```
=== J1: BASEDIR ideroot; SETB <absolute path under proj> 2
   SETB  -> 200 OK   RUN -> 200 OK
   next  -> 202 Paused /home/mini/.cache/claude-scratch/lunar/debug05/proj/sub/target.lua 2

=== J2: MOBDEBUG_HOST=192.168.2.25, listener bound to that NIC
   peer  = ('192.168.2.25', 52708)
   next  -> 202 Paused sub/target.lua 2
```

J1 is why `toWire` (§3.2) falls back to the **absolute remote path** rather than to a `../`
relative path: mobdebug's own `gsub(file, "^"..q(basedir), "")` leaves an out-of-basedir file
absolute, so an absolute `SETB` matches and a `../`-prefixed one cannot. J2 is the transport half
of capability B, end to end.

### Probe F/G — `OUTPUT` and `DELB * 0` wire behaviour

```
OUTPUT stdout r  -> 200 OK        then, on print("out:" .. b):
                                  204 Output stdout 9
                                  payload = b'"out:42"\n'          <-- Lua-SERIALIZED, quoted
OUTPUT stdout c  -> 200 OK        same frame, and 'out:42\n' still on the child's stdout
OUTPUT stderr r  -> 400 Bad Request
OUTPUT stdout x  -> 400 Bad Request
OUTPUT           -> 400 Bad Request

DELB * 0         -> 200 OK
SETB does/not/exist.lua 7 -> 200 OK      <-- a breakpoint on a file it will never load
SETB sub/target.lua 2     -> 200 OK
DELB * 0                  -> 200 OK
RUN -> 200 OK ; next -> None ; stdout 'out:42\n'   <-- the wildcard really cleared it
```

**Probe G2 — the mutation TC-05-11a names, executed.** Same rig, two runs against a real
mobdebug child (`~/bin/lua` 5.4.7, `src/main/lua/debug.lua` preloader, working directory `app/`,
script `script.lua` whose line 2 is `print("hit")`), differing only in the `DELB` line:

```
=== with 'DELB * 0'
   BASEDIR              -> 200 OK
   SETB script.lua 2    -> 200 OK
   DELB * 0             -> 200 OK
   RUN                  -> 200 OK
   after RUN            -> <EOF: the debuggee ran to completion and closed the socket>
   child stdout: 'hit' ; exit=0
=== with 'DELB * 1'
   BASEDIR              -> 200 OK
   SETB script.lua 2    -> 200 OK
   DELB * 1             -> 200 OK
   RUN                  -> 200 OK
   after RUN            -> 202 Paused script.lua 2
   child stdout: '' ; exit=None (still suspended)
```

Both `DELB` lines are answered `200 OK`, which is the whole point: **the acknowledgement carries no
information**, so a test that only asserts the reply asserts nothing. The difference is entirely in
what happens after `RUN` — `line == 0` takes `remove_breakpoint`'s wildcard branch
(`breakpoints = {}`, `src/main/lua/mobdebug/init.lua:360-365`) while `line == 1` falls through to
`breakpoints[1]['*'] = nil`, leaving the line-2 breakpoint installed. This is the executed basis for
TC-05-11a's mutation being red rather than merely plausible.

### Probe K — a `204 Output` frame arrives **while a command is in flight**

```
pause     -> 202 Paused sub/target.lua 2
>> EXEC print('side')
   line0 = '204 Output stdout 7'      payload = b'"side"\n'
   line1 = '200 OK 26'                payload = b'do local _={};return _;end'
```

This is the sequence that breaks `LuaDebugConnection.handleLine` today: `running` is `false`
(the pause cleared it at `run/LuaDebugConnection.kt:299`), `Output` is not in `EXEC.responses`
(`:101-111`), so control reaches `log.error(...)` at `:337` with **7 payload bytes unread**. The
next `readLine` then returns `"side"`, `handleLine` throws
`IOException("unknown status in response…")` at `:269`, and the reader coroutine dies. §3.6 fixes
the ordering.

### Probe M — `maxlevel` bounds the `STACK` payload; `maxnum=0` empties it

```
STACK                       len= 10102
STACK -- {maxlevel=1}       len=  1348   frame tuple intact: {nil,"sub/target.lua",0,5,"main","",...}
STACK -- {maxlevel=2}       len=  6426
STACK -- {maxnum=0}         len=    33   do local _={};return _;end     <-- everything gone
STACK -- {maxnum=3}         len=   257   {nil,"sub/target.lua",0}       <-- frame tuple TRUNCATED
```

The connect-time diagnostic (§3.5) therefore sends `STACK -- {maxlevel=1}` and must **never** use
`maxnum`, which truncates the frame tuple itself. (An earlier run of this probe combined both and
produced a false negative; the corrected run is the one above.)

### Probe N — what a **default** local configuration actually puts on the wire (`DEBUG-05-03`)

`LuaRunConfigurationOptions.myWorkingDirectory` is `string("")` (`run/LuaRunConfiguration.kt:79-83`),
so a configuration the user never edited holds the **empty string, not null**. The shipped
`listOfNotNull(workingDirectory, project.basePath, "").first()`
(`run/LuaDebuggerController.kt:80-90`) therefore keeps `""` and never reaches `basePath`, and a
default session today sends `BASEDIR /`. Measured (`probeN.py`, `probeN2.py`):

```
=== N1: BASEDIR / (today's DEFAULT config) + SETB <root-relative>  -- the shipped wire
  BASEDIR /       -> 200 OK
  STACK f1        -> 200 OK do local _={{{nil,"home/mini/.cac…
  SETB home/mini/.cache/claude-scratch/lunar/debug05/proj/sub/target.lua 2  -> 200 OK
  RUN             -> 200 OK
  next            -> 202 Paused home/mini/.cache/claude-scratch/lunar/debug05/proj/sub/target.lua 2

=== N2: BASEDIR / + SETB <ABSOLUTE>  -- what a PathMapping rooted at '/' would send
  BASEDIR /       -> 200 OK
  SETB <abs> 2    -> 200 OK
  RUN             -> 200 OK
  next            -> None                    <-- NO PAUSE; the debuggee ran to completion
  stdout: 'out:42\n'

=== N3: BASEDIR <proj>/ + SETB sub/target.lua  -- what effectiveWorkDirectory() yields
  BASEDIR proj/   -> 200 OK
  SETB sub/target.lua 2 -> 200 OK
  RUN             -> 200 OK
  next            -> 202 Paused sub/target.lua 2

=== N4: NO BASEDIR sent at all + SETB <ABSOLUTE>   (the project.basePath == null shape)
  SETB <abs> 2    -> 200 OK
  RUN             -> 200 OK
  next            -> 202 Paused /home/mini/.cache/claude-scratch/lunar/debug05/proj/sub/target.lua 2
```

And the mapper half, run against the real `com.intellij.util.PathMappingSettings` loaded from
`util-261.25134.145.jar` (`MapProbe.java`, corretto-21.0.10):

```
== root '/' ==
PathMapping(/, /) -> localRoot='/' remoteRoot='/'
   canReplaceLocal('/home/u/proj/a.lua') = false ; mapToRemote = '/home/u/proj/a.lua'
== root '/home/u/proj' ==
PathMapping(/home/u/proj, /home/u/proj) -> localRoot='/home/u/proj' remoteRoot='/home/u/proj'
   canReplaceLocal('/home/u/proj/a.lua') = true  ; mapToRemote = '/home/u/proj/a.lua'
== differing roots ==
PathMapping(/ide/proj, /srv/app) -> canReplaceLocal('/ide/proj/sub/a.lua') = true ; mapToRemote = '/srv/app/sub/a.lua'
```

Four facts, and together they decide §2.3:

1. **Today's default wire is `BASEDIR /` plus a root-relative `SETB`** (N1) — not the
   `BASEDIR <workdir>/` an earlier draft of this design assumed. It binds, and the pause it reports
   is `home/mini/…/target.lua`: **relative**, which is precisely the input `findFileByPath`
   cannot take and therefore precisely why [[BUG-463]] reproduces on every project not rooted at `/`.
2. **`PathMappingSettings.PathMapping` cannot reproduce that wire.** With both roots `/`,
   `canReplaceLocal` is **false**: `localPrefix` is `"/"`, length 1, and the rule requires
   `localPath.charAt(1) == '/'` (`PathMappingSettings.java:259-266`). A `/`-rooted `LuaPathMapper`
   therefore returns the **absolute** path from `toWire`, not `home/u/proj/a.lua`.
3. **And that absolute form does not bind under `BASEDIR /`** (N2). `debug_hook` strips
   `^basedir` from the chunk name (`src/main/lua/mobdebug/init.lua:657`) while `set_breakpoint`
   stores the `SETB` argument verbatim (`:353-358`), so the two never meet. Probe J1's "an absolute
   `SETB` binds" holds only where the strip is a **no-op** — which it is not at basedir `/`.
4. **Sending no `BASEDIR` at all is safe** (N4): mobdebug's `basedir` stays `""` (`init.lua:129`),
   `removebasedir` is a no-op, chunk names stay absolute, and an absolute `SETB` matches.

Facts 2 and 3 together mean *"keep today's derivation"* is not implementable inside this design's
chosen prefix engine — it would need the hand-rolled `startsWith` §2.1 forbids, and the hand-rolled
version is the one that yields a wire path `findFileByPath` cannot resolve. §2.3 therefore changes
the derivation **deliberately**; the change is declared there, risked in
[risks-and-gaps.md](risks-and-gaps.md) Risk 1.8, and pinned by TC-05-03c.

### Probe D′ / Bind — the listener's bind address and a cancellable accept

```
ServerSocket(18901)                      -> 0.0.0.0/0.0.0.0  wildcard=true
ServerSocket(18902,1,loopback)           -> localhost/127.0.0.1  wildcard=false
  connect via 127.0.0.1     -> CONNECTED
  connect via 192.168.2.25  -> REFUSED (ConnectException: Connection refused)
ServerSocket(18903,1,192.168.2.25)       -> /192.168.2.25
  connect via 127.0.0.1     -> REFUSED (ConnectException: Connection refused)
soTimeout=0 accept() + close() after 1.2s -> java.net.SocketException: Socket closed  after 1201 ms
soTimeout=0 accept() still blocked after 2000ms -> true
```

```
after Thread.interrupt(): alive=true result=null elapsedMs=2000
socket closed by interrupt? false
after close(): result=java.net.SocketException: Socket closed
```

```
  1st timeout: java.net.SocketTimeoutException: Accept timed out; socket still open? true
accepted after 4 SocketTimeoutExceptions, 1402 ms, peer=/127.0.0.1 loopback=true
```

Consequences, all load-bearing:

- Binding an explicit NIC **excludes loopback** — so "bind host" is a real choice, not a widening.
- `Thread.interrupt()` does **not** unblock `ServerSocket.accept()` on corretto-21, so
  `kotlinx.coroutines.runInterruptible` cannot implement a cancellable accept. §3.3 uses a
  `soTimeout` poll loop instead, which the third transcript proves is re-enterable.
- `SocketTimeoutException` leaves the `ServerSocket` open and usable; the accepted peer address
  is available and `isLoopbackAddress()` answers the origin question.

### Probe L — `MOBDEBUG_HOST` unset against a loopback-bound listener

```
bind 127.0.0.1, MOBDEBUG_HOST unset (->localhost)    CONNECTED peer=('127.0.0.1', 42828)
bind 127.0.0.1, MOBDEBUG_HOST=127.0.0.1              CONNECTED peer=('127.0.0.1', 42844)
```

**Measured on this host only.** The hypothesis that an IPv6-first host would resolve `localhost`
to `::1` and fail against a `127.0.0.1` bind did **not** reproduce here and is *not* asserted.
Setting `MOBDEBUG_HOST` explicitly (§2.6) removes the dependency on the debuggee host's resolver;
confirming the failure mode on an IPv6-first host is `DEBUG-05-00-DR-02`, not a claim of this
document.

### Probe O — a `/` remote root, run against the real `PathMapping`

Executed against GoLand 2026.1.3's own `util-8.jar`
(`~/.gradle/caches/8.14.4/transforms/…/goland-2026.1.3/lib`) on corretto-21, calling
`com.intellij.util.PathMappingSettings.PathMapping` directly:

```
PathMapping(/, /): localRoot=/ remoteRoot=/ canReplaceLocal(/home/u/a.lua)=false mapToRemote=/home/u/a.lua mapToLocal=/home/u/a.lua
PathMapping(/, /): localRoot=/ remoteRoot=/ canReplaceLocal(//sub/a.lua)=true  mapToRemote=//sub/a.lua  mapToLocal=//sub/a.lua
PathMapping(/ide/proj, /srv/app): canReplaceLocal(/ide/proj/sub/a.lua)=true mapToRemote=/srv/app/sub/a.lua mapToLocal=/ide/proj/sub/a.lua
normalised remote root of "/"        = [/]
trailing-slash root "/srv/app/"      = [/srv/app]
naive   joinRemote("sub/a.lua")      -> //sub/a.lua ; mapToLocal -> //sub/a.lua
guarded joinRemote("sub/a.lua")      -> /sub/a.lua  ; mapToLocal -> /sub/a.lua
naive   baseDirArgument              -> [//]
guarded baseDirArgument              -> [/]
non-"/" root, guarded vs naive       -> /srv/app/sub/a.lua in both
```

Three things this settles, none of them inferable from reading:

1. **`trimSlash` special-cases `"/"`** (`PathMappingSettings.java:289-291`), so `"/"` is the one
   root that reaches `joinRemote`/`baseDirArgument` **already ending in a slash** — every other
   root is trimmed. Naively appending `"/"` therefore yields `//` for that root and only that root.
2. **`mapToLocal` does not collapse the doubled slash** — it returns `//sub/a.lua` unchanged. The
   `//` is not absorbed downstream by `PathMapping`.
3. **`canReplaceLocal` is `false` for a `/` root against an ordinary path** (`charAt(1)` is `h`,
   not `/`), so `toWire` takes step 4 and returns the absolute path. The `/` root breaks
   `baseDirArgument()`, not `toWire`.

§3.2's `.removeSuffix("/")` guard is derived from this transcript, and the last line shows it is a
**no-op for every other root**.

### Probe P — the handshake fixture's socket shape, measured (TC-05-11b)

TC-05-11b drives the real `connect()` against a **scripted fake debuggee**, so two mechanics have to
hold before that row can be written: the test must be able to name a port `openListener` will bind,
and a client-side peer must be able to answer every handshake command in order. Both were run
(corretto-21, loopback only, three consecutive runs), borrowing an ephemeral port with
`ServerSocket(0, 1, loopback)`, closing it, rebinding that exact port as `openListener` would, then
letting a retry-connecting fake reply `200 OK\n` to each newline-framed command:

```
borrowed port=43355
rebind ok after 0.05 ms, bound=localhost/127.0.0.1:43355
accepted after 2.05 ms from /127.0.0.1
  reply to 'BASEDIR /srv/app/': 200 OK
  reply to 'DELB * 0': 200 OK
  reply to 'OUTPUT stdout r': 200 OK
debuggee recorded, in order: [BASEDIR /srv/app/, DELB * 0, OUTPUT stdout r]
```

Three things this settles:

1. **Re-binding a just-released ephemeral port succeeds**, in 0.05 ms, three runs for three
   (ports 43355 / 45311 / 45613 / 45077). A listening socket closed with no accepted connection
   leaves no `TIME_WAIT` state, so the borrow-close-rebind idiom is sound; it is how the fixture
   learns the port without a Phase-5 `listener()` seam.
2. **The fake debuggee records the commands in send order**, which is what makes the ordering half
   of §3.7 assertable at all — a mutation that swaps `clearRemoteBreakpoints()` and
   `redirectOutput()` changes the recorded list, not just its contents.
3. **A retry-connect loop removes the start-order constraint**: the fake may be started before the
   controller binds (2.05 ms of retries here), so the test needs no rendezvous beyond the port.

The fixture speaks the production framing — `DbgpFraming.readLine(input)` / `writeLine(output, line)`
(`run/DbgpFraming.kt:26`, `:58`) — rather than a hand-rolled reader, so a framing change cannot make
the row silently pass.

## 1. Architecture Overview

### 1.1 Current State

A Lunar debug session is hard-wired to one filesystem and one root. Three facts make it so:

1. `LuaDebuggerController.init` derives a single `baseDir` from the run configuration's working
   directory (`run/LuaDebuggerController.kt:80-90`) and `setBaseDir()` sends it verbatim as
   `BASEDIR` (`:199-201`).
2. The same `workingDir` is the *only* input to `LuaPosition.createRemotePosition`
   (`:220`, `:230`; `run/LuaDebugProcess.kt:82`), which is
   `FileUtil.getRelativePath(workingDir, target) ?: target.path` (`run/LuaPosition.kt:38-48`).
3. Frames resolve with `LocalFileSystem.getInstance().findFileByPath(frame.path)` on the raw,
   `LUA_IDSIZE`-truncated `short_src` (`run/LuaRemoteStack.kt:41-47`, `:82-83`).

The listener is `ServerSocket(serverPort)` with `soTimeout = CONNECT_TIMEOUT_MS = 5_000`
(`:114-117`, `:348`) — wildcard-bound and five seconds wide. `LuaDebugRunner.canRun` accepts only
`LuaRunConfiguration` (`run/LuaDebugRunner.kt:52-57`) and `doExecute` always spawns a local
interpreter through `state.execute(...)` (`:73`). `plugin.xml:602-603` declares one Lua execution
`<configurationType>`.

### 1.2 Prior Art in This Repo — extended, replaced, or left alone

| Component | `file:line` | Disposition |
| :-- | :-- | :-- |
| `LuaDebuggerController` | `run/LuaDebuggerController.kt:49` | **EXTENDED.** Gains a `LuaDebugTarget` (§2.3) in place of the three fields it derives from `session.runProfile`; the DBGp command surface is untouched. |
| `LuaPosition.createRemotePosition(XSourcePosition, File?)` | `run/LuaPosition.kt:38-48` | **EXTENDED, not replaced.** The `File?` overload survives with identical behaviour for `null` and is re-expressed over the mapper; a `LuaPathMapper` overload is added. `TestLuaPosition` stays green. |
| `LuaRemoteStackEntry` / `LuaRemoteStackFrame` | `run/LuaRemoteStack.kt:37-86` | **MODIFIED.** The `virtualFiles` memo map is replaced by `LuaFrameResolver` (§2.2), which owns both the memo and the mapping. |
| `LuaDebugConnection.handleLine` | `run/LuaDebugConnection.kt:266-338` | **EXTENDED.** One new branch, placed first (§3.6). The Case A / Case B state machine is otherwise byte-for-byte preserved. |
| `LuaRedisDebugRunner` / `LuaLdbController` | `redis/debug/LuaRedisDebugRunner.kt:31`, `redis/debug/LuaLdbController.kt` | **LEFT ALONE.** `DEBUG-05-14`: a different protocol (LDB, not mobdebug) under a different epic. `LuaRedisDebugRunner.canRun` gates on `LuaRedisRunConfiguration` (`:34-40`), so the two runners cannot collide. Nothing here is merged into it. |
| `LuaRedisConnectionsConfigurable`'s host/port form | `redis/connection/LuaRedisConnectionsConfigurable.kt:174-176`, `:211` | **PATTERN REUSED** (a `JBTextField` host + a labelled row), not the class. |
| `addMnemonicLabeledComponent` / `withMnemonic` | `src/main/kotlin/net/internetisalie/lunar/ui/LuaFormBuilders.kt:25`, `:39` | **REUSED VERBATIM.** These are the only two calls that produce a working label mnemonic in this repo (BUG-448); §2.7's editor uses them and adds no builder helper of its own. |
| `RunConfigurationEditorTextTest` | `src/test/kotlin/net/internetisalie/lunar/ui/RunConfigurationEditorTextTest.kt:140-146`, `:159` | **EXTENDED.** Its hard-coded `editors()` list of four gains `LuaAttachSettingsEditor` and `AUDITED_ROW_COUNT` goes 27 → 32 (TC-05-07f). Without that edit a fifth editor is invisible to every assertion in the file and the suite stays green on an editor with no colons and no mnemonics. |
| `LuaBundleCasingTest` | `src/test/kotlin/net/internetisalie/lunar/LuaBundleCasingTest.kt:42-55` | **EXTENDED BY DATA.** It scans the whole bundle, so the nine new keys are covered automatically; **no exclusion is added**. It is not sufficient — see §2.7 — which is why TC-05-07e still exists. |
| DEBUG-06 `LuaTargetValidator` / `LuaTargetChecks` | DEBUG-06 design §2.5 (NEW there) | **EXTENDED** by adding `LuaTargetChecks.attach(configuration)` (§2.8). No existing check, severity or problem type changes. |
| `LuaLineBreakpointType` | `run/LuaLineBreakpointType.kt:33-38` | **LEFT ALONE.** It keys on the `VirtualFile`'s PSI (`:50`), not on a run configuration, so it already serves an attach session. |
| `startLuaDebugHarness` | `src/test/kotlin/.../run/LuaDebugHarness.kt:26-55` | **EXTENDED.** Gains a spec carrier so a child can be launched from a *different* root (§2.9); the existing one-line call site keeps working through a default. |

**Searched and found nothing:** `grep -rn "PathMappingSettings\|convertToLocal\|convertToRemote" src/` → no hits; `grep -rn "MOBDEBUG_HOST" src/` → only `src/main/lua/lunar/debug.lua:12`; `grep -rn "RunConfigurationWithSuppressedDefaultRunAction\|RemoteRunProfile\|DefaultDebugProcessHandler" src/` → no hits. There is no existing path-mapping, attach or remote-host machinery to duplicate.

### 1.3 Target State

One immutable **`LuaDebugTarget`** describes a session: where to listen, how long, and how to
translate paths. It is built one way for a launched configuration and another for an attach
configuration; `LuaDebuggerController` reads only the target and no longer inspects
`session.runProfile`. Path translation moves out of `LuaPosition`/`LuaRemoteStack` into a single
**`LuaPathMapper`** that delegates its prefix arithmetic to the platform's
`PathMappingSettings.PathMapping`, so the segment-boundary and normalisation rules are the
platform's and not re-invented.

```
LuaAttachRunConfiguration ──┐
                            ├─► LuaDebugTarget ─► LuaDebuggerController ─► LuaDebugConnection
LuaRunConfiguration ────────┘        │                    │
                                     │                    ├─ BASEDIR  = target.remoteRoot
                                     ▼                    ├─ SETB/DELB = mapper.toWire(local)
                              LuaPathMapper ◄─────────────┤
                                     ▲                    └─ STACK    ─► LuaFrameResolver
                                     └── LuaFrameResolver ────────────► VirtualFile
```

## 2. Core Components

New files under `src/main/kotlin/net/internetisalie/lunar/run/` and
`src/main/kotlin/net/internetisalie/lunar/run/attach/`, matching the existing `run/test/`,
`run/console/` subpackages and the engineering contract's `run/` placement rule (§4).

### 2.1 `net.internetisalie.lunar.run.LuaPathMapper` (NEW)

- **Responsibility**: the single bidirectional translator between an IDE-local absolute path and
  the wire path mobdebug uses. Nothing else in the plugin may compute a wire path.
- **Threading**: pure, allocation-free after construction, no I/O. Called from the session
  coroutine (`Dispatchers.IO`) and from `LuaFrameResolver` inside a read action. Thread-safe by
  immutability.
- **Retention**: holds two `String`s. No `Project`, `Editor`, `PsiFile` or `VirtualFile` — contract §4.
- **Collaborators**: `com.intellij.util.PathMappingSettings.PathMapping`
  (`platform/util/src/com/intellij/util/PathMappingSettings.java:203-210`).

```kotlin
class LuaPathMapper(
    val localRoot: String?,
    val remoteRoot: String?,
) {
    /** `BASEDIR` argument: [remoteRoot] with exactly one trailing `/`, or null when unset. */
    fun baseDirArgument(): String?

    /** IDE-local absolute path -> the path to put in SETB/DELB. */
    fun toWire(localPath: String): String

    /** A wire path (relative to [remoteRoot], or absolute) -> an IDE-local absolute path. */
    fun toLocalPath(wirePath: String): String

    /** True when [localRoot] and [remoteRoot] denote the same directory (capability A). */
    fun isIdentity(): Boolean

    companion object {
        fun identity(workingDirectory: File?): LuaPathMapper
    }
}
```

Two arguments; within the contract's cap. `identity(workingDirectory)` is the capability-A
constructor and is what `LuaRunConfiguration` sessions get: `localRoot == remoteRoot ==
workingDirectory.path`.

`PathMapping` is used as the **engine**, constructed once in an `init` block:
`private val mapping = PathMappingSettings.PathMapping(localRoot, remoteRoot)`. Its constructor
normalises with `FileUtil.toSystemIndependentName` + trailing-slash trim
(`PathMappingSettings.java:210-222`, `:283-293`), and `canReplaceLocal`/`canReplaceRemote` enforce a
**segment boundary** (`:254-266`, `:334-338`) — which is why `/p/projekt/a.lua` is not treated as
being under `/p/proj`. Do not hand-roll `startsWith`.

### 2.2 `net.internetisalie.lunar.run.LuaFrameResolver` (NEW)

- **Responsibility**: turn a stack frame's wire path into a `VirtualFile`, memoised per stack
  snapshot. Replaces the `virtualFiles: MutableMap<String, VirtualFile?>` threaded through
  `LuaRemoteStack` / `LuaRemoteStackEntry` / `LuaRemoteStackFrame` today
  (`run/LuaRemoteStack.kt:14`, `:39`, `:69`).
- **Threading**: `resolve` performs VFS lookup; every existing call site is already inside
  `ApplicationManager.getApplication().runReadAction` (`run/LuaDebuggerController.kt:292-294`),
  and that is unchanged.
- **Retention**: holds `VirtualFile` values for the lifetime of one `LuaRemoteStack`, i.e. one
  suspend. That is exactly the lifetime of the map it replaces; it is **not** a service and is
  never retained past `session.resume()`. Contract §4's prohibition is on *long-lived* services.

```kotlin
class LuaFrameResolver(
    private val mapper: LuaPathMapper,
) {
    private val cache: MutableMap<String, VirtualFile?> = mutableMapOf()

    fun resolve(wireFile: String): VirtualFile?

    companion object {
        fun identity(workingDirectory: File?): LuaFrameResolver =
            LuaFrameResolver(LuaPathMapper.identity(workingDirectory))
    }
}
```

### 2.3 `net.internetisalie.lunar.run.LuaDebugTarget` (NEW)

- **Responsibility**: the immutable per-session description of *what we are debugging and how we
  reach it*. It exists because `LuaDebuggerController` otherwise needs six constructor arguments,
  which the contract's `PARAMETER CAP` forbids and whose remedy it names — *"Pass a dedicated
  configuration or execution context class"* (`docs/engineering-contract.md:51`).
- **Threading**: constructed on the EDT inside `LuaDebuggerController.init`, read from the session
  coroutine. Immutable.
- **Retention**: `InetAddress` + primitives + a `LuaPathMapper`. No heavy framework objects.

```kotlin
data class LuaDebugTarget(
    val bindAddress: InetAddress,
    val port: Int,
    val acceptTimeoutMs: Long,          // <= 0 means "unbounded, cancellable"
    val mapper: LuaPathMapper,
    val redirectOutput: Boolean,
    val clearRemoteBreakpoints: Boolean,
    val autoRestart: Boolean,
) {
    /** The value to hand the debuggee as MOBDEBUG_HOST. */
    fun debuggeeHost(): String = bindAddress.hostAddress

    companion object {
        fun of(configuration: LuaRunConfiguration): LuaDebugTarget
        fun of(configuration: LuaAttachRunConfiguration): LuaDebugTarget
        fun fallback(): LuaDebugTarget
    }
}
```

The three factories are specified in full; none is left to inference.

```kotlin
fun of(configuration: LuaRunConfiguration): LuaDebugTarget =
    LuaDebugTarget(
        bindAddress = InetAddress.getLoopbackAddress(),                   // DEBUG-05-12 / BUG-456
        port = configuration.debugPort,                                   // run/LuaRunConfiguration.kt:237-241
        acceptTimeoutMs = LuaDebuggerController.CONNECT_TIMEOUT_MS.toLong(),
        mapper = LuaPathMapper.identity(
            configuration.effectiveWorkDirectory()?.let(::File),          // :228 — see the note below
        ),
        redirectOutput = false,          // a launched child already owns the console (§6)
        clearRemoteBreakpoints = false,  // a freshly-started debuggee holds none (§6)
        autoRestart = false,
    )

fun of(configuration: LuaAttachRunConfiguration): LuaDebugTarget =
    LuaDebugTarget(
        bindAddress = resolveBindAddress(configuration.bindHost),         // §3.1
        port = configuration.debugPort,
        acceptTimeoutMs = configuration.listenTimeoutSeconds * 1000L,     // 0 => unbounded
        mapper = LuaPathMapper(configuration.localRoot, configuration.remoteRoot),
        redirectOutput = configuration.redirectOutput,
        clearRemoteBreakpoints = true,                                    // DEBUG-05-11
        autoRestart = configuration.autoRestart,
    )

fun fallback(): LuaDebugTarget = of-equivalent with
    bindAddress = InetAddress.getLoopbackAddress(),
    port = LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT,                 // :185
    acceptTimeoutMs = LuaDebuggerController.CONNECT_TIMEOUT_MS.toLong(),
    mapper = LuaPathMapper.identity(null),
    redirectOutput = false, clearRemoteBreakpoints = false, autoRestart = false
```

`fallback()` is what `LuaDebuggerController.init` uses when `session.runProfile` is neither
configuration type — reproducing today's `?: DEFAULT_DEBUG_PORT` behaviour at `:77-78` rather
than throwing.

#### The base-directory derivation changes for a launched session, and the change is deliberate

`of(LuaRunConfiguration)` uses **`configuration.effectiveWorkDirectory()`**
(`run/LuaRunConfiguration.kt:229`, `workingDirectory?.takeIf { it.isNotEmpty() } ?: project.basePath`).
The shipped controller uses `listOfNotNull(configuration.workingDirectory, session.project.basePath,
"").first()` (`run/LuaDebuggerController.kt:80-90`). **These differ for the most common configuration
there is**, and an earlier draft of this design wrongly claimed the wire bytes were identical:

| Configuration | Today (shipped) | This design |
| :-- | :-- | :-- |
| working directory `""` (the default — `myWorkingDirectory` is `string("")`, `:79-83`), `basePath = /home/u/proj` | `BASEDIR /` + `SETB home/u/proj/a.lua` | `BASEDIR /home/u/proj/` + `SETB a.lua` |
| working directory `/home/u/proj` (explicitly set) | `BASEDIR /home/u/proj/` + `SETB a.lua` | identical |
| working directory `""`, `basePath == null` | `BASEDIR /` + `SETB home/u/proj/a.lua` | **no `BASEDIR` sent** + `SETB /home/u/proj/a.lua` |

`listOfNotNull` keeps the empty string, so `.first()` is `""` and `session.project.basePath` is
**dead code today**; the `""` tail element is only ever reached when `workingDirectory` is
explicitly `null`, which no editor path produces.

**Why the shipped rule is not merely kept.** It is not implementable through
`PathMappingSettings.PathMapping`, and its hand-rolled equivalent is a defect. Measured, §0 Probe N:
`PathMapping("/", "/")` reports `canReplaceLocal("/home/u/proj/a.lua") == false`
(`PathMappingSettings.java:259-266` requires a `/` at the prefix boundary, and there is none at
depth 1), so a `/`-rooted mapper emits the **absolute** path — and `BASEDIR /` plus an absolute
`SETB` **does not bind** (N2: no pause, the debuggee ran to completion), because `debug_hook`
strips `^basedir` from the chunk name (`src/main/lua/mobdebug/init.lua:657`) while `set_breakpoint`
stores the `SETB` argument verbatim (`:353-358`). Reproducing today's bytes would therefore mean
re-introducing the `startsWith` arithmetic §2.1 forbids **and** keeping a wire path
(`home/u/proj/a.lua`) that `LocalFileSystem.findFileByPath` cannot resolve — the root cause of
[[BUG-463]].

**Why the new rule is right.** `effectiveWorkDirectory()` is already what the launched child gets as
its process working directory (`run/LuaRunConfiguration.kt:315-316`), so `BASEDIR` and the debuggee's
cwd finally agree — which is what `basedir` means. N3 measures it binding, with a short relative
wire path. The `basePath == null` row degrades to "no `BASEDIR`", which N4 measures as binding too,
with **absolute** wire paths that resolve directly.

This is a behaviour change to shipped local debugging. It is recorded as
[risks-and-gaps.md](risks-and-gaps.md) Risk 1.8, surfaced against `DEBUG-05-03` in §8, and pinned by
**TC-05-03c**, whose fixture has an *empty* working directory and whose mutation is the shipped
derivation.

### 2.4 `net.internetisalie.lunar.run.LuaDebuggerController` (MODIFIED)

Fields `serverPort`, `baseDir` and `workingDir` (`:56`, `:69-70`) collapse into one
`private val target: LuaDebugTarget`, resolved in `init` (replacing `:77-90`):

```kotlin
private val target: LuaDebugTarget =
    when (val profile = session.runProfile) {
        is LuaRunConfiguration -> LuaDebugTarget.of(profile)
        is LuaAttachRunConfiguration -> LuaDebugTarget.of(profile)
        else -> LuaDebugTarget.fallback()
    }

fun workingDirectory(): File? = target.mapper.localRoot?.let(::File)   // was :72, now nullable
fun pathMapper(): LuaPathMapper = target.mapper                        // NEW
fun port(): Int = target.port                                          // NEW (DEBUG-06 §3.7 also adds this)
```

`connect()` (`:111-131`) is restructured into three ≤30-line functions:

```kotlin
@Throws(IOException::class)
suspend fun connect() {
    val server = openListener(target)                       // internal seam, §3.1
    serverSocket = server
    val clientSocket = awaitClient(server, target.acceptTimeoutMs)   // internal seam, §3.3
    rejectForeignPeer(clientSocket)                                  // §3.4
    clientAddress = clientSocket.inetAddress
    val conn = LuaDebugConnection(clientSocket, DebugObserver(), scope).also { it.start() }
    connection = conn
    printToConsole("Debugger connected at $clientAddress", ConsoleViewContentType.SYSTEM_OUTPUT)
    isReady = true
    handshake()                                                       // §3.7
}

internal fun openListener(target: LuaDebugTarget): ServerSocket
internal suspend fun awaitClient(server: ServerSocket, deadlineMs: Long): Socket
private suspend fun handshake()
```

`internal`, **not** `private`, on the two seams — the idiom is established in this repo:
`PublishRockAction.isAuthFailure` is `internal` at `rocks/publish/PublishRockAction.kt:144` and is
called from `src/test/kotlin/net/internetisalie/lunar/rocks/publish/PublishRockAuthFailureTest.kt:14`.

`setBaseDir()` (`:199-201`) sends `target.mapper.baseDirArgument()` and becomes a no-op when that
is `null`. `addBreakPoint`/`removeBreakPoint` (`:220`, `:230`) and `runToCursor` call
`LuaPosition.createRemotePosition(sourcePosition, target.mapper)`. `variables()` (`:290-295`)
constructs its `LuaRemoteStack` with a `LuaFrameResolver(target.mapper)`.
`DebugObserver.onPause` (`:326`) calls `pos.localPosition(target.mapper)`.

New suspend members, each ≤10 logic lines: `clearRemoteBreakpoints()`, `redirectOutput()`,
`detectRootMismatch()` (§3.5, §3.6, §3.7).

**One new field, and it is not optional.** An attach session owns no OS process, so nothing ever
fires the `processTerminated` event that ends a launched session
(`run/LuaDebugProcess.kt:100-108`). Without a bridge, a debuggee that disconnects leaves the Debug
tool window showing a live session over a dead socket. The controller therefore exposes a single
callback, and `LuaDebugProcess` uses it to terminate its synthetic handler:

```kotlin
// LuaDebuggerController
private var disconnectListener: (() -> Unit)? = null
fun onDisconnect(listener: () -> Unit) { disconnectListener = listener }

// DebugObserver.onDisconnected — replaces run/LuaDebuggerController.kt:341-344
override fun onDisconnected() {
    log.info("Disconnected")
    if (target.autoRestart) { restartListener(); return }        // §3.9
    close()
    disconnectListener?.invoke()
}

// LuaDebugProcess.sessionInitialized, immediately before the sessionScope.launch at :110
controller.onDisconnect {
    if (!myClosing) executionResult.processHandler?.destroyProcess()
}
```

`destroyProcess()` is safe for **both** shapes: on `DefaultDebugProcessHandler` it is
`notifyProcessTerminated(0)` (`platform/xdebugger-api/src/com/intellij/xdebugger/DefaultDebugProcessHandler.java:23-26`),
and on the launched session's `ColoredProcessHandler` the child has already exited, so
`ProcessHandler.destroyProcess` is a no-op on an already-terminated handler. The `!myClosing` guard
reuses the flag `stop()` and `processTerminated` already set (`run/LuaDebugProcess.kt:50`, `:69`,
`:104`), so a user-initiated stop does not re-enter.

### 2.5 `net.internetisalie.lunar.run.attach.LuaAttachRunConfiguration` and friends (NEW)

One file, `run/attach/LuaAttachRunConfiguration.kt`, laid out exactly like
`run/LuaRunConfiguration.kt` (type, factory, options, configuration, editor in one file):

```kotlin
class LuaAttachRunConfigurationType : ConfigurationTypeBase(
    ID,
    LuaBundle.message("debug.attach.type.name"),           // §2.7
    LuaBundle.message("debug.attach.type.description"),    // §2.7
    NotNullLazyValue.createValue { LuaIcons.FILE },        // lang/LuaIcons.kt:8
) {
    init { addFactory(LuaAttachRunConfigurationFactory(this)) }
    companion object { const val ID: String = "LuaAttachRunConfiguration" }
}

class LuaAttachRunConfigurationFactory(type: ConfigurationTypeBase) : ConfigurationFactory(type) {
    override fun getId(): String = LuaAttachRunConfigurationType.ID
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        LuaAttachRunConfiguration(project, this, "Lua Remote")
    override fun getOptionsClass(): Class<out BaseState> = LuaAttachRunConfigurationOptions::class.java
}

class LuaAttachRunConfigurationOptions : RunConfigurationOptions() {
    var bindHost: String?              // default "127.0.0.1"
    var debugPort: Int                 // default LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT
    var localRoot: String?             // default ""
    var remoteRoot: String?            // default ""
    var listenTimeoutSeconds: Int      // default 0 == unbounded
    var redirectOutput: Boolean        // default true
    var autoRestart: Boolean           // default false
}

class LuaAttachRunConfiguration(project: Project, factory: ConfigurationFactory?, name: String?) :
    RunConfigurationBase<LuaAttachRunConfigurationOptions?>(project, factory, name),
    RunConfigurationWithSuppressedDefaultRunAction,
    RemoteRunProfile {

    override fun getOptions(): LuaAttachRunConfigurationOptions = …
    var bindHost: String;  var debugPort: Int;  var localRoot: String?
    var remoteRoot: String?;  var listenTimeoutSeconds: Int
    var redirectOutput: Boolean;  var autoRestart: Boolean

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> =
        LuaAttachSettingsEditor(project)

    override fun checkConfiguration() =
        LuaTargetValidator.validate(LuaTargetSpec.of(this), LuaTargetChecks.attach(this))   // §2.8

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        LuaAttachState(project)
}
```

Every `StoredProperty` follows the shape of `LuaRunConfigurationOptions`
(`run/LuaRunConfiguration.kt:66-188`): a `private val myX: StoredProperty<…> =
string("")/property(default).provideDelegate(this, "x")` plus a `var x` accessor pair.

**Grounding for the two marker interfaces.**
`com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction` is an empty
interface (`platform/execution-impl/src/com/intellij/execution/runners/RunConfigurationWithSuppressedDefaultRunAction.java:18`)
whose sole consumer is `DefaultRunProgramRunner.kt:37`:
`DefaultRunExecutor.EXECUTOR_ID == executorId && profile !is RunConfigurationWithSuppressedDefaultRunAction`.
Implementing it is exactly what removes the **Run** action while leaving **Debug**.
`com.intellij.execution.configurations.RemoteRunProfile`
(`platform/execution/src/com/intellij/execution/configurations/RemoteRunProfile.java:23`) is an
empty **sub-interface of `RunProfile`** — empty of members, but not inert: its one platform consumer
is `XDebuggerTree.isUnderRemoteDebug`
(`platform/xdebugger-impl/ui/src/com/intellij/xdebugger/impl/ui/tree/XDebuggerTree.java:317-323`),
which reads `environment.runProfile is RemoteRunProfile` to decide whether the variables tree is
showing a remote session. Implementing it costs nothing and tells the platform the truth.

`org.jetbrains.debugger.RemoteDebugConfiguration`
(`platform/script-debugger/debugger-ui/src/org/jetbrains/debugger/RemoteDebugConfiguration.java:39-40`)
is cited for the *shape* of a listen-side remote configuration only, and only for
`RunConfigurationWithSuppressedDefaultRunAction`: it implements
`RunConfigurationWithSuppressedDefaultRunAction, DebuggableRunConfiguration` — **not**
`RemoteRunProfile` — so it is precedent for one of the two markers, not for the pair. It is also
`@Deprecated` (`:36-38`) and is not extended.

### 2.6 `net.internetisalie.lunar.run.attach.LuaAttachState` (NEW) and the runner (MODIFIED)

```kotlin
class LuaAttachState(private val targetProject: Project) : RunProfileState {
    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = DefaultDebugProcessHandler()
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(targetProject).console
        console.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler)
    }
}
```

- `com.intellij.xdebugger.DefaultDebugProcessHandler`
  (`platform/xdebugger-api/src/com/intellij/xdebugger/DefaultDebugProcessHandler.java:22-41`) owns
  no OS process: `destroyProcessImpl` is `notifyProcessTerminated(0)` and
  `detachIsDefault()` returns `true` (`:33-36`). This is the platform-level equivalent of the
  `RemoteDebugProcessHandler` the requirements name; that class lives in
  `java/debugger/impl/.../RemoteDebugProcessHandler.kt:10` and is **not** on a GoLand classpath.
- `com.intellij.execution.filters.TextConsoleBuilderFactory.getInstance().createBuilder(project)`
  (`platform/execution/src/com/intellij/execution/filters/TextConsoleBuilderFactory.java:10,14`).
- `com.intellij.execution.DefaultExecutionResult(console, processHandler)`
  (`platform/execution/src/com/intellij/execution/DefaultExecutionResult.java:37`).

Because `getState` returns a **real** state, `LuaDebugRunner.doExecute` is unchanged: its
`state.execute(environment.executor, this)` at `run/LuaDebugRunner.kt:73` yields the
`ExecutionResult` `LuaDebugProcess` requires, and `createConsole` (`run/LuaDebugProcess.kt:88-92`)
finds a real `ConsoleView`. Only `canRun` changes (`:52-57`):

```kotlin
override fun canRun(executorId: String, runProfile: RunProfile): Boolean =
    executorId == DefaultDebugExecutor.EXECUTOR_ID &&
        (runProfile is LuaRunConfiguration || runProfile is LuaAttachRunConfiguration)
```

`LuaRunConfiguration` (MODIFIED) gains one constant and one `internal` seam so the debuggee's
dial-back host is testable without spawning a process. `startProcess` (`:321-336`) replaces its
four `withEnvironment` calls with the single three-argument call shown below.

**Both new declarations go in the existing `companion object` (`run/LuaRunConfiguration.kt:356-363`),
beside `ENV_MOBDEBUG_PORT` (`:362`) — not on the class.** That is what makes TC-05-04a's
`LuaRunConfiguration.debuggerEnvironment(…)` and `LuaRunConfiguration.ENV_MOBDEBUG_HOST` resolve;
written as instance members they would not compile at that call site. **That is the whole reason —
not reachability from `startProcess`.** `startProcess` reaches either placement equally: it already
calls the instance members `effectiveWorkDirectory()` (`run/LuaRunConfiguration.kt:315`) and
`debugPort` (`:335`) on the outer receiver from inside the anonymous `CommandLineState`
(`:292-293`), so an instance `debuggerEnvironment` would compile there fine. It would not compile in
TC-05-04a, which has no instance.

```kotlin
// inside LuaRunConfiguration's companion object, run/LuaRunConfiguration.kt:356-363
internal fun debuggerEnvironment(
    pluginLuaPath: String,
    preloaderPath: String,
    target: LuaDebugTarget,
): Map<String, String>          // three arguments — at the cap, not over it
```

Both paths are **`String`s resolved by the caller**, which is what keeps the helper free of `!!`
(contract §1 NULL SAFETY) and free of the VFS. `startProcess` already does both resolutions and
already throws on either failure — that code is unchanged and simply feeds the helper:

```kotlin
val pluginLuaPath = LuaFileUtil.getPluginVirtualDirectoryChild("lua")
    ?: throw ExecutionException("Failed to locate plugin directory")            // :321-323, verbatim
val debuggerPreloaderFile = pluginLuaPath.findChild(DEBUGGER_PRELOADER_FILE)
    ?: throw ExecutionException("Failed to locate debugger preloader")          // :324-326, verbatim
val target = LuaDebugTarget.of(this@LuaRunConfiguration)                        // NEW — §2.3
commandLine.withEnvironment(debuggerEnvironment(pluginLuaPath.path, debuggerPreloaderFile.path, target))
```

**Where `target` comes from at this call site.** `startProcess` is the body of the anonymous
`CommandLineState` declared inside `LuaRunConfiguration.getState` (`run/LuaRunConfiguration.kt:292-293`),
so the qualified receiver `this@LuaRunConfiguration` is in scope — the same receiver `:315`'s
`effectiveWorkDirectory()` and `:335`'s `debugPort` already use. `LuaDebugTarget.of(LuaRunConfiguration)`
(§2.3) is the launched-session factory, so `target.port` is `configuration.debugPort` — byte-identical
to the `MOBDEBUG_PORT` value `:335` writes today — and `target.debuggeeHost()` is the loopback address
the same factory pins. The line is added inside the existing
`if (executor.getId() == DefaultDebugExecutor.EXECUTOR_ID)` block (`:321`): the four
`withEnvironment` calls at `:329-335` collapse into one, so the block is two statements shorter even
with the new `val`.

`debuggerEnvironment` returns exactly

| Key | Value | Was |
| :-- | :-- | :-- |
| `LUNAR_LUA_PATH_TEMPLATE` | `"$pluginLuaPath/?/init.lua;$pluginLuaPath/?.lua"` | `:330-332` |
| `LUNAR_DEBUGGER_PACKAGE` | `DEBUGGER_PACKAGE` (`"mobdebug"`, `:358`) | `:333` |
| `LUA_INIT` | `"@$preloaderPath"` | `:334` |
| `MOBDEBUG_PORT` | `target.port.toString()` | `:335` |
| **`MOBDEBUG_HOST`** | `target.debuggeeHost()` | **NEW** — `const val ENV_MOBDEBUG_HOST = "MOBDEBUG_HOST"` beside `:362` |

Taking `String`s rather than a `VirtualFile` also makes the helper **pure**: TC-05-04a needs no
VFS, no temp directory and no refresh.

`src/main/lua/lunar/debug.lua:12` already reads it (`os.getenv("MOBDEBUG_HOST") or "localhost"`),
which is why no Lua change is needed. This is `DEBUG-05-04`.

### 2.7 `net.internetisalie.lunar.run.attach.LuaAttachSettingsEditor` (NEW)

`SettingsEditor<LuaAttachRunConfiguration>` built with `FormBuilder`, mirroring
`LuaRunSettingsEditor` (`run/LuaRunConfiguration.kt:366-433`) with **all three** of the contract §6
text rules that bind **new** UI: sentence case, a colon on every leading label
(`docs/engineering-contract.md:138-143`), **and a mnemonic on every label and checkbox**
(`docs/engineering-contract.md:151` — *"Labels carry mnemonics; the platform underlines 10/10 on a
comparable page"*). §6 SCOPE (`docs/engineering-contract.md:164`) exempts only the *surviving*
`FormBuilder` editors; a new one is inside it.

**The class, with every override signature.** `SettingsEditor` declares three members an
implementation must supply, and all three are stated here so none is invented:

```kotlin
// src/main/kotlin/net/internetisalie/lunar/run/attach/LuaAttachRunConfiguration.kt (same file, §2.5)
class LuaAttachSettingsEditor(
    project: Project,
) : SettingsEditor<LuaAttachRunConfiguration>() {
    private val myPanel: JPanel
    private val bindHostField = JBTextField()
    private val debugPortField = JBIntSpinner(LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT, 1, 65535)
    private val listenTimeoutField = JBIntSpinner(0, 0, 3600)
    private val localRootField = TextFieldWithBrowseButton()
    private val remoteRootField = JBTextField()
    private val redirectOutputCheckbox =
        JBCheckBox(LuaBundle.message("debug.attach.redirectOutput")).withMnemonic()
    private val autoRestartCheckbox =
        JBCheckBox(LuaBundle.message("debug.attach.autoRestart")).withMnemonic()

    init {
        bindHostField.emptyText.text = "127.0.0.1 (loopback only)"
        remoteRootField.emptyText.text = "Path on the debuggee host, e.g. /srv/app"
        localRootField.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleDir())

        myPanel =
            FormBuilder
                .createFormBuilder()
                .addMnemonicLabeledComponent(LuaBundle.message("debug.attach.bindHost"), bindHostField)
                .addMnemonicLabeledComponent(LuaBundle.message("debug.attach.port"), debugPortField)
                .addMnemonicLabeledComponent(LuaBundle.message("debug.attach.timeout"), listenTimeoutField)
                .addTooltip("In seconds. 0 waits until you cancel.")
                .addMnemonicLabeledComponent(LuaBundle.message("debug.attach.localRoot"), localRootField)
                .addMnemonicLabeledComponent(LuaBundle.message("debug.attach.remoteRoot"), remoteRootField)
                .addComponent(redirectOutputCheckbox)
                .addComponent(autoRestartCheckbox)
                .panel
    }

    override fun createEditor(): JComponent = myPanel

    override fun resetEditorFrom(runConfiguration: LuaAttachRunConfiguration) {
        bindHostField.text = runConfiguration.bindHost
        debugPortField.number = runConfiguration.debugPort
        listenTimeoutField.number = runConfiguration.listenTimeoutSeconds
        localRootField.text = runConfiguration.localRoot ?: ""
        remoteRootField.text = runConfiguration.remoteRoot ?: ""
        redirectOutputCheckbox.isSelected = runConfiguration.redirectOutput
        autoRestartCheckbox.isSelected = runConfiguration.autoRestart
    }

    override fun applyEditorTo(runConfiguration: LuaAttachRunConfiguration) {
        runConfiguration.bindHost = bindHostField.text
        runConfiguration.debugPort = debugPortField.number
        runConfiguration.listenTimeoutSeconds = listenTimeoutField.number
        runConfiguration.localRoot = localRootField.text
        runConfiguration.remoteRoot = remoteRootField.text
        runConfiguration.redirectOutput = redirectOutputCheckbox.isSelected
        runConfiguration.autoRestart = autoRestartCheckbox.isSelected
    }
}
```

The three signatures are copied from the sibling verbatim — `createEditor(): JComponent`
(`run/LuaRunConfiguration.kt:432`), `resetEditorFrom(runConfiguration: T)` (`:410`) and
`applyEditorTo(runConfiguration: T)` (`:421`); neither declares a checked exception, and neither
does this one.

**The two calls that produce the mnemonics — use these, not `FormBuilder`'s own `String`
overload.** `net.internetisalie.lunar.ui.addMnemonicLabeledComponent(labelText, component)`
(`src/main/kotlin/net/internetisalie/lunar/ui/LuaFormBuilders.kt:25`) for a labelled row, and
`net.internetisalie.lunar.ui.withMnemonic()` on an `AbstractButton` (`:39`) for a checkbox. Both read
the letter marked with `&` out of the text they are given and hand it to
`com.intellij.util.ui.DialogUtil.registerMnemonic`, then drop the marker. `FormBuilder`'s own
`addLabeledComponent(String, JComponent)` **cannot** be used with a marked label: it runs the text
through `UIUtil.replaceMnemonicAmpersand`, which rewrites `&R` to `U+001B R`, and hands the result to
a bare `JLabel` that sets no mnemonic and leaves the control character in the visible text — measured
on the GoLand 2026.1 test platform and recorded in that helper's KDoc (`LuaFormBuilders.kt:12-24`)
and in `RunConfigurationEditorTextTest`'s `test no label leaks the mnemonic escape character`
(`src/test/kotlin/net/internetisalie/lunar/ui/RunConfigurationEditorTextTest.kt:57-64`). The four
shipped editors all use the helper exclusively (`run/LuaRunConfiguration.kt:399-406`,
`run/test/LuaTestRunConfiguration.kt:329-335`, `rocks/run/LuaRocksRunConfiguration.kt:311-315`,
`redis/run/LuaRedisRunConfiguration.kt:343-353`), which is the sibling shape this editor mirrors.

**Where the `&` lives: in the bundle *value*.** The helper takes the marker out of the label text it
is handed, and that text is `LuaBundle.message(key)`, so the marker is written into
`LuaBundle.properties`. `&` has no special meaning in a `.properties` file, so no escaping is
involved. This is also why TC-05-07e strips `&` before its casing scan.

**Every label is a `LuaBundle` key, not a literal — and this AGREES with DEBUG-06 §3.3 rather than
departing from it.** §3.3 (as corrected in `1a5d5b70`, before this artifact's revision) decides that
`LuaTargetMessages` holds literals, and its reason 3 states the boundary explicitly: contract §6's
text rules — including *"a bundle assertion that no control label is Title Case"*
(`docs/engineering-contract.md:163`) — *"reach control labels, not validation prose"*, and its ten
strings are `RuntimeConfigurationException` messages rendered in the validation banner, not labels.
Control labels are exactly the category this section puts in the bundle, so the two features apply
one rule to two different categories of text. `LuaBundle`
(`src/main/kotlin/net/internetisalie/lunar/LuaBundle.kt:15-18`) is backed by
`src/main/resources/net/internetisalie/lunar/LuaBundle.properties`, is used in 12 Kotlin files,
already has a `# debugging` section (`LuaBundle.properties:109-110`), and already supplies control
labels elsewhere — `LuaApplicationSettingsPanel.kt:37-39` builds its two checkboxes from
`LuaBundle.message(...)`. Two consequences worth stating outright:

- The §6 bundle assertion *"no control label is Title Case"* (`docs/engineering-contract.md:162-163`)
  is only possible if the labels are keys. TC-05-07e is that test.
- This feature's own two **validation** strings — `remoteRootUnset()` and `bindHostUnresolvable(host)`
  in §2.8 — stay literals **because** DEBUG-06 §3.3's rule governs them. Nothing here restyles a
  sibling's `planned` design.

**Prior art on the casing rule: `LuaBundleCasingTest` — EXTENDED BY DATA, NOT DUPLICATED, AND NOT
SUFFICIENT.** `src/test/kotlin/net/internetisalie/lunar/LuaBundleCasingTest.kt:42-55` already scans
**every** value in `LuaBundle.properties` for Title Case, so the nine new keys fall under it the
moment they are added — nothing has to be registered and **no exclusion may be added for them**.
Two things follow, and both are load-bearing:

- **All nine values pass it as written.** Its `isTitleCase` requires *every* non-product word to
  match `CAPITALIZED_WORD = ^[A-Z][a-z]+$` (`LuaBundleCasingTest.kt:7`, `:57-60`).
  `Lua Remote (Mobdebug)` survives because `(Mobdebug)` cannot match that shape, and the sentence-case
  values survive on their lower-case words. An implementer who sees a red `noControlLabelIsTitleCase`
  has mistyped a value, not found a needed exclusion.
- **It cannot replace TC-05-07e, because it cannot see a Title-Case *leading label at all*.** The
  trailing colon this feature's five leading labels all carry makes the last word (`Root:`) fail
  `^[A-Z][a-z]+$`, so `all { … }` is false and `Remote Source Root:` is **not** flagged; a leading `&`
  defeats it the same way (`&Remote` does not match either). TC-05-07e is therefore a strictly
  stronger, DEBUG-05-scoped scan — first word capitalised, no later word upper-case-initial after the
  `&` marker is stripped — and it is deliberately *not* a widening of `LuaBundleCasingTest`, whose
  repo-wide loosening is BUG-448's business and not this feature's.

Nine keys are added to `LuaBundle.properties`, under the existing `# debugging` section. **The
`Value (verbatim)` column is the exact right-hand side to type, `&` included.**

| Key | Value (verbatim) | Mnemonic | Component | Bound to |
| :-- | :-- | :-- | :-- | :-- |
| `debug.attach.bindHost` | `Listen on &host:` | `H` | `JBTextField` with `emptyText` `"127.0.0.1 (loopback only)"` | `bindHost` |
| `debug.attach.port` | `&Debug port:` | `D` | `JBIntSpinner(DEFAULT_DEBUG_PORT, 1, 65535)` — verbatim `:381` | `debugPort` |
| `debug.attach.timeout` | `Listen &timeout:` | `T` | `JBIntSpinner(0, 0, 3600)`, followed by `.addTooltip("In seconds. 0 waits until you cancel.")` | `listenTimeoutSeconds` |
| `debug.attach.localRoot` | `&Local source root:` | `L` | `TextFieldWithBrowseButton` + `FileChooserDescriptorFactory.singleDir()` — verbatim `:391-394` | `localRoot` |
| `debug.attach.remoteRoot` | `&Remote source root:` | `R` | `JBTextField` with `emptyText` `"Path on the debuggee host, e.g. /srv/app"` | `remoteRoot` |
| `debug.attach.redirectOutput` | `Redirect debuggee &output to the console` | `O` | `JBCheckBox(...).withMnemonic()` | `redirectOutput` |
| `debug.attach.autoRestart` | `&Keep listening after the debuggee disconnects` | `K` | `JBCheckBox(...).withMnemonic()` | `autoRestart` |
| `debug.attach.type.name` | `Lua Remote (Mobdebug)` | — (not a control label) | — | `LuaAttachRunConfigurationType`'s display name |
| `debug.attach.type.description` | `Attach to a running Lua process that dials back with mobdebug` | — (not a control label) | — | its description |

**Collision check for the seven letters — `H D T L R O K`.** Three scopes, checked in order:

1. **Within this editor.** All seven are distinct. This is the scope
   `RunConfigurationEditorTextTest`'s `test mnemonics are unique inside each editor` enforces:
   `collisionIn` groups `formLabelsOf(editor)` **per editor**
   (`src/test/kotlin/net/internetisalie/lunar/ui/RunConfigurationEditorTextTest.kt:163-170`).
2. **Against the platform chrome drawn around the editor in the same dialog.** The Run/Debug
   Configurations dialog contributes `Allow m&ultiple instances` (`U`) and `&Store as project file`
   (`S`) — `platform/execution/resources/messages/ExecutionBundle.properties:266`, `:267`. Neither
   `U` nor `S` is used here. (The shipped `LuaRunSettingsEditor` does collide with `S` via
   `&Script file:`; that is pre-existing and is **not** propagated into this editor.)
3. **Against the four sibling editors.** *No collision is possible*, because they are never on
   screen together: the dialog mounts exactly one `SettingsEditor`, the one belonging to the selected
   configuration type. `LuaRunSettingsEditor` uses `R S W T E A P D`
   (`run/LuaRunConfiguration.kt:399-406`), `LuaTestSettingsEditor` `f T a R W E v`
   (`run/test/LuaTestRunConfiguration.kt:329-335`), `LuaRocksRunSettingsEditor` `C A R G v`
   (`rocks/run/LuaRocksRunConfiguration.kt:311-315`), `LuaRedisSettingsEditor` `S C E D K A F` plus
   checkbox `o l n` (`redis/run/LuaRedisRunConfiguration.kt:324-353`). Overlaps with this editor's
   letters therefore carry no consequence, and none is treated as one.

**No label carries a parenthetical.** `Listen timeout:` does **not** say `(seconds)`: contract
§6 puts explanation in a hint, not in parentheses inside the label
(`docs/engineering-contract.md:147-148` — it names `comment()`/`emptyText`, the Kotlin UI DSL v2
idiom; `FormBuilder`'s equivalent, and the only one available in a `FormBuilder` panel, is
`addTooltip`), and
`RunConfigurationEditorTextTest`'s `test no label carries a parenthetical hint`
(`src/test/kotlin/net/internetisalie/lunar/ui/RunConfigurationEditorTextTest.kt:66-73`) is a live
assertion that fails on one. The unit and the `0` semantics go to
`FormBuilder.addTooltip(String)` — the house call for a sub-label hint
(`platform/platform-api/src/com/intellij/util/ui/FormBuilder.java:125`, in-repo at
`redis/run/LuaRedisRunConfiguration.kt:348`, `:353`, `:355`, `:357`). **`FormBuilder` has no
`comment(...)` method**; the `comment(` call sites in this repo are all Kotlin UI DSL v2
`Configurable`s, a different builder, and naming one here would not compile.

The two `emptyText` strings and the tooltip stay literals: they are placeholders and hints, not
control labels, and the contract's casing rule is written for labels. `emptyText` is set through
`ComponentWithEmptyText`, which `JBTextField` implements
(`platform/platform-api/src/com/intellij/ui/components/JBTextField.java:27`); in-repo idiom at
`toolchain/ui/LuaProjectConfigurable.kt:330`.

`JBTextField`/`JBCheckBox` are the platform components the contract mandates over their Swing
ancestors (`docs/engineering-contract.md:128-131`); both are already used in this repo
(`redis/connection/LuaRedisConnectionsConfigurable.kt:175`,
`settings/LuaApplicationSettingsPanel.kt:37`). Checkbox labels take no trailing colon — they are
not leading labels.

**Row count.** The form has **five** labelled rows (`formLabelsOf` counts a `JLabel` with a non-null
`labelFor`, so the `addTooltip` label is excluded — `RunConfigurationEditorProbe.kt:26-29`) and two
checkbox rows. That five is what raises `AUDITED_ROW_COUNT` from 27 to 32 in TC-05-07f.

**The screenshot pass is the gate** (`docs/engineering-contract.md:159-163`): this is a new
visible surface, so `verify-in-ide` is a required verification task, listed in the plan (HV-02).

### 2.8 `LuaTargetChecks.attach(configuration)` (extends DEBUG-06) (NEW list, NEW checks)

Per DEBUG-06 design §2.10, `LuaTargetValidator.validate(spec, checks)` already takes the list as a
parameter, so nothing in `LuaTargetValidator`, `LuaTargetSeverity` or `LuaTargetProblem` changes.

The attach check list is a **function**, not a `val` — Gap 2.3 and the closing paragraphs of this
section explain why. `LuaTargetSpec.of(configuration: LuaAttachRunConfiguration)` is the third factory, built exactly
as §2.10 point 1 prescribes:

```kotlin
fun of(configuration: LuaAttachRunConfiguration): LuaTargetSpec =
    LuaTargetSpec(
        runtime = null,
        runtimePath = null,
        scriptPath = null,
        workingDirectory = configuration.localRoot?.takeIf { it.isNotEmpty() },
        projectLanguageLevel =
            LuaProjectSettings.getInstance(configuration.project).state.languageLevel,
        envFilePaths = emptyList(),
    )
```

**Why checks 1 and 3 are absent, restated so the omission cannot be read as one.** DEBUG-06 §2.10
is correct and was re-derived against its own §3.2 here. Check 1 `RUNTIME_MISSING` fires on
`spec.runtime == null` at severity `FATAL`; `RunManagerImpl.canRunConfiguration` catches
`RuntimeConfigurationError` and returns `false`
(`platform/execution-impl/src/com/intellij/execution/impl/RunManagerImpl.kt:162-164`), so
including it would refuse **every** attach launch. Check 3 `SCRIPT_UNSET` fires on
`scriptPath.isNullOrEmpty()`, which is the attach spec's normal state, and would warn
*"No script file configured"* forever. Checks 2, 4, 6, 7 are inert on this spec and are dropped
for clarity; check 8 `ENV_FILES` iterates an empty list and is dropped for the same reason.
`WORKDIR_MISSING` (check 5) is reused **verbatim** — it reads only `spec.workingDirectory`, which
this spec supplies.

Two new checks, in this declared order (ties break by declaration order per DEBUG-06 §3.1 step 3):

| # | Name | Fires when | Severity | Message |
| :-: | :-- | :-- | :-- | :-- |
| 5 | `WORKDIR_MISSING` | reused verbatim from `LOCAL_SCRIPT` | `WARNING` | `LuaTargetMessages.workingDirectoryMissing(path)` |
| A1 | `REMOTE_ROOT_UNSET` | `spec.workingDirectory` non-empty **and** the configuration's `remoteRoot` is null/blank | `WARNING` | `LuaTargetMessages.remoteRootUnset()` |
| A2 | `BIND_HOST_UNRESOLVABLE` | `resolveBindAddress(bindHost)` throws `UnknownHostException` (§3.1) | `FATAL` | `LuaTargetMessages.bindHostUnresolvable(host)` |

`LuaTargetSpec` names no configuration class (§2.10 point 1), so A1 and A2 cannot read
`remoteRoot`/`bindHost` off it. They are therefore **not** `LuaTargetCheck` values built from the
spec alone, and there is **no `val ATTACH`**. The single declaration added to `object
LuaTargetChecks` is

```kotlin
fun attach(configuration: LuaAttachRunConfiguration): List<LuaTargetCheck> =
    listOf(WORKDIR_MISSING, remoteRootUnset(configuration), bindHostUnresolvable(configuration))
```

where the last two are `LuaTargetCheck` lambdas closing over the configuration. That keeps `LuaTargetSpec` configuration-agnostic, keeps every check a
`fun interface` value (§2.10 point 3), and adds no field to the spec. The call site is

```kotlin
override fun checkConfiguration() =
    LuaTargetValidator.validate(LuaTargetSpec.of(this), LuaTargetChecks.attach(this))
```

Two new messages on `LuaTargetMessages` (DEBUG-06 §3.3), as fixed strings in that object:

| Function | Exact text |
| :-- | :-- |
| `remoteRootUnset()` | `Remote source root is not set. Breakpoints will be sent relative to the local root, which only works when the debuggee runs from the same path.` |
| `bindHostUnresolvable(host)` | `Cannot resolve the listen host "$host". Use an address of this machine, or 0.0.0.0 to accept connections from any interface.` |

> **These two stay literals, and the reason is DEBUG-06's — no longer a deferred disagreement.**
> An earlier revision of this section recorded that DEBUG-06 §3.3 justified its literals with the
> false premise *"Lunar has no message bundle"*. **That is fixed upstream**: `1a5d5b70`
> (*"DEBUG-06 §3.3 claimed Lunar has no message bundle — it has one"*) rewrote the section, and it
> now re-takes the decision on a premise that survives checking — `noRuntimeConfigured()` delegates
> to `LuaToolResolver.notConfiguredMessage(kindId)`, which **composes** the headline message at call
> time from `LuaToolKindRegistry.findById(kindId)?.displayName ?: kindId`
> (`toolchain/resolve/LuaToolResolver.kt:93-97`), so it cannot be a static key, and a convention
> that excludes its own headline is a split rather than a convention. `remoteRootUnset()` and
> `bindHostUnresolvable(host)` are validation prose of exactly that kind, so they follow the rule
> that governs the other ten. Nothing about the bundle is in dispute between the two features:
> §2.7's control labels are keys, these validation strings are literals, and §3.3 reason 3 is where
> that line is drawn. `DEBUG-05-00-DR-01` is **closed** — see
> [risks-and-gaps.md](risks-and-gaps.md) Gap 2.1.

### 2.9 `startLuaDebugHarness` (MODIFIED, test source)

`src/test/kotlin/net/internetisalie/lunar/run/LuaDebugHarness.kt:26-30` takes `(script, observer)`
and hard-codes `ServerSocket(8172)` (`:30`) with no working directory (`:32-41`). Capability-B
coverage needs a child launched from a *different* root and a free port. Four parameters exceed
the cap, so a carrier is introduced in the same file:

```kotlin
data class LuaHarnessSpec(
    val script: File,
    val workingDirectory: File? = null,
    val port: Int = LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT,
    val environment: Map<String, String> = emptyMap(),
)

fun startLuaDebugHarness(spec: LuaHarnessSpec, observer: LuaDebugObserver): LuaHarness
fun startLuaDebugHarness(script: File, observer: LuaDebugObserver): LuaHarness =
    startLuaDebugHarness(LuaHarnessSpec(script), observer)     // keeps TestLuaDebugHarness.kt:53 compiling
```

`ProcessBuilder` gains `.directory(spec.workingDirectory)` and the listener becomes
`ServerSocket(spec.port, 1, InetAddress.getLoopbackAddress())`.

**The port has to reach the child too, or `spec.port` is a lie.** `LuaDebugHarness.kt:36-40` sets
three environment variables and **no `MOBDEBUG_PORT`**, and the debuggee's preloader falls back to
`tonumber(os.getenv("MOBDEBUG_PORT")) or 8172` (`src/main/lua/lunar/debug.lua:13`) — so a harness
bound to a free port would be dialled at 8172 and TC-05-07d could never connect. The existing
`.apply { … }` block therefore gains two lines, after the three it already has:

```kotlin
environment()["MOBDEBUG_PORT"] = spec.port.toString()
environment().putAll(spec.environment)                 // last, so a spec entry can override
```

`spec.environment` exists for exactly this shape of override (a `MOBDEBUG_HOST` case, or a
`LUA_PATH` a future row needs); with the default `emptyMap()` the `putAll` is a no-op and
`TestLuaDebugHarness.testBreakpointAndExec` keeps its current environment byte for byte, except
that `MOBDEBUG_PORT` is now stated rather than defaulted — `LuaHarnessSpec.port` defaults to
`LuaRunConfigurationOptions.DEFAULT_DEBUG_PORT`, which is the same `8172`
(`run/LuaRunConfiguration.kt:186`) the preloader falls back to.

## 3. Algorithms

### 3.1 Bind-address resolution and listener creation (`DEBUG-05-12`, BUG-456)

**Input → Output**: `String? → InetAddress`, then `LuaDebugTarget → ServerSocket`.

`resolveBindAddress(host)`:

1. If `host` is null or blank → `InetAddress.getLoopbackAddress()`.
2. If `host.trim()` equals `"0.0.0.0"` or `"*"` → `InetAddress.getByName("0.0.0.0")` (the wildcard).
3. Otherwise → `InetAddress.getByName(host.trim())`, which throws `UnknownHostException`
   (an `IOException`) on failure. Check A2 (§2.8) is the only place that catches it; everywhere
   else it propagates.

`openListener(target)` (≤6 logic lines):

```kotlin
internal fun openListener(target: LuaDebugTarget): ServerSocket =
    ServerSocket(target.port, LISTEN_BACKLOG, target.bindAddress)
        .apply { soTimeout = ACCEPT_POLL_MS }
```

with `const val LISTEN_BACKLOG: Int = 1` (one debuggee at a time — `connect()` accepts exactly
one) and `const val ACCEPT_POLL_MS: Int = 500`.

**Measured**: `ServerSocket(port)` binds `0.0.0.0` (`wildcard=true`); `ServerSocket(port, 1,
loopback)` binds `127.0.0.1`, accepts a loopback connection and **refuses** one to
`192.168.2.25`; binding an explicit NIC refuses loopback (§0, BindProbe). Loopback is therefore
the correct default for a launched child and a real narrowing for everyone else.

### 3.2 Path translation (`DEBUG-05-03`, `-07`, `-08`)

Two private helpers used below, defined here so neither is inferred:

```kotlin
/** [remoteRoot] put through PathMapping's own normalisation; "" when remoteRoot is null/blank. */
private val normalisedRemoteRoot: String =
    remoteRoot?.takeIf { it.isNotBlank() }
        ?.let { PathMappingSettings.PathMapping(it, it).getRemoteRoot() } ?: ""

/** The remote root with any trailing slash removed; "" when no root is set. See the `/` note below. */
private val remoteRootPrefix: String = normalisedRemoteRoot.removeSuffix("/")

/** A relative wire path joined onto the remote root; the input unchanged when no root is set. */
private fun joinRemote(wirePath: String): String =
    if (normalisedRemoteRoot.isEmpty()) wirePath else "$remoteRootPrefix/$wirePath"
```

**Why `removeSuffix("/")` and not plain concatenation — the `remoteRoot == "/"` edge case.**
`trimSlash` strips a trailing slash from every root **except `"/"`, which it special-cases and
returns unchanged** (`PathMappingSettings.java:289-291`). So `"/"` is the single root that arrives
already slash-terminated, and `"$normalisedRemoteRoot/$wirePath"` would produce `//sub/a.lua` while
`baseDirArgument()` would produce `BASEDIR //` — a prefix mobdebug's literal
`gsub(file, "^"..q(basedir), "")` (`init.lua:657`) can never match, so every chunk name would stay
absolute and no breakpoint sent relative to it would bind. `remoteRootPrefix` is `""` for that root
and identical to `normalisedRemoteRoot` for every other, which §0 Probe O measures directly. This
shape is reachable without an attach configuration at all: a launched session whose working
directory is `/` builds `LuaPathMapper.identity(File("/"))`, which is exactly `PathMapping("/", "/")`.

`PathMapping(x, x).getRemoteRoot()` is `trimSlash(FileUtil.toSystemIndependentName(x))`
(`PathMappingSettings.java:210-222`, `:283-293`): backslashes become forward slashes, a trailing
slash is trimmed, and `"/"` is special-cased to stay `"/"`. Using it is what keeps `isIdentity()`,
`baseDirArgument()` and the two translators on one normalisation rule instead of three.

**`toWire(localPath): String`** — IDE → debuggee. ≤8 logic lines.

1. `val remoteAbsolute = mapping.mapToRemote(localPath)`. `PathMapping.mapToRemote` returns the
   input unchanged when the mapping is empty or `localPath` is not under `localRoot`
   (`PathMappingSettings.java:268-277`).
2. If `remoteRoot` is null/blank → return `remoteAbsolute` (no `BASEDIR` will be sent either).
3. If `PathMappingSettings.PathMapping(remoteRoot, remoteRoot).canReplaceLocal(remoteAbsolute)`
   — i.e. `remoteAbsolute` is at or under `remoteRoot` at a segment boundary — return
   `remoteAbsolute.removePrefix(normalisedRemoteRoot).removePrefix("/")`.
4. Otherwise return `remoteAbsolute` **unchanged and absolute**.

Step 4 is where this design deliberately differs from the shipped
`FileUtil.getRelativePath(workingDir, target) ?: target.path` (`run/LuaPosition.kt:43`): for a
file outside the root, `getRelativePath` yields `../other/main.lua`, which mobdebug's `debug_hook`
turns into `basedir.."../other/main.lua"` and then normalises
(`src/main/lua/mobdebug/init.lua:648-649`), so it never matches. Probe J1 shows the **absolute**
form binds and pauses. This is a behaviour change to shipped local debugging and it is a fix.

**`toLocalPath(wirePath): String`** — debuggee → IDE. ≤7 logic lines.

1. If `wirePath` is blank → return `wirePath`.
2. `val remoteAbsolute = if (File(wirePath).isAbsolute) wirePath else joinRemote(wirePath)`.
3. Return `mapping.mapToLocal(remoteAbsolute)` — `PathMappingSettings.PathMapping.mapToLocal`
   (`:250-252` → `:131-149`) returns the input unchanged when the mapping is empty or the prefix
   does not match.

**`baseDirArgument()`**: `null` when `remoteRoot` is null/blank; otherwise
`remoteRootPrefix + "/"` — which is `"/"` for a `/` root (matching what the shipped controller
sends today, §0 Probe N2) and `normalisedRemoteRoot + "/"` for every other. The trailing slash is
required — `TestLuaDebugHarness.kt:49-51`
already records why, and mobdebug's `gsub(file, "^"..q(basedir), "")` (`init.lua:657`) is a raw
prefix strip.

**`isIdentity()`**: `mapping.getLocalRoot() != null && mapping.getLocalRoot() == mapping.getRemoteRoot()`
— the two sides of the already-constructed `mapping`, compared after `PathMapping`'s own
normalisation (`PathMappingSettings.java:225-232`), so no second normalisation rule is invented.
The `!= null` conjunct makes the result `false` for an **unrooted** mapper, where `normalize(null)`
leaves both sides null and `null == null` would otherwise report identity; §3.5 step 1 tests
`remoteRoot.isNullOrBlank()` as well, so the conjunct is a short-circuit rather than the only
guard, and it changes no outcome §3.5 depends on. Measured: for `PathMapping("/", "/")` the two
sides are equal (§0, Probes N and O), so a `/`-rooted session is correctly classified as identity
even though its `canReplaceLocal` is `false`.

**Worked table** (mapper `local=/ide/proj`, `remote=/srv/app`):

| Input | `toWire` | `toLocalPath` |
| :-- | :-- | :-- |
| `/ide/proj/sub/a.lua` | `sub/a.lua` | — |
| `/ide/projekt/a.lua` | `/ide/projekt/a.lua` (segment boundary — not under `/ide/proj`) | — |
| `/elsewhere/x.lua` | `/elsewhere/x.lua` | — |
| `sub/a.lua` | — | `/ide/proj/sub/a.lua` |
| `/srv/app/sub/a.lua` | — | `/ide/proj/sub/a.lua` |
| `/opt/lua/socket.lua` | — | `/opt/lua/socket.lua` (unmapped, returned unchanged) |
| `=[C]` | — | `=[C]` (never resolved — §3.8 guard) |

For an identity mapper (`local == remote == W`) the first row is `sub/a.lua` and the fourth is
`W/sub/a.lua` — i.e. capability A is unchanged on the happy path.

### 3.3 The cancellable, unbounded accept (`DEBUG-05-06`)

**Input → Output**: `(ServerSocket, Long) → Socket`, or throws `SocketTimeoutException` /
`CancellationException`.

```kotlin
internal suspend fun awaitClient(server: ServerSocket, deadlineMs: Long): Socket {
    val deadlineNanos =
        if (deadlineMs <= 0L) Long.MAX_VALUE
        else System.nanoTime() + deadlineMs * 1_000_000L
    while (true) {
        coroutineContext.ensureActive()
        try {
            return withContext(Dispatchers.IO) { server.accept() }
        } catch (timeout: SocketTimeoutException) {
            if (System.nanoTime() >= deadlineNanos) throw timeout
        }
    }
}
```

Two arguments; ≤10 logic lines. `ACCEPT_POLL_MS = 500` is already on the socket from §3.1, so each
iteration blocks at most 500 ms and cancellation is observed within that window.

**Why a poll loop and not `runInterruptible`.** Measured (§0, `IntrProbe`): a thread blocked in
`ServerSocket.accept()` on corretto-21 is **not** released by `Thread.interrupt()` — it was still
alive after 2000 ms and the socket was not closed. Only `close()` releases it. A
`suspendCancellableCoroutine` + `invokeOnCancellation { server.close() }` would also work but
needs an off-scope executor for the blocking call and a resume-with-close to avoid leaking an
accepted socket; the poll loop needs neither and satisfies the contract's
`CANCELLATION EXHAUSTIVENESS` rule literally (`docs/engineering-contract.md:39`).

**Why the socket survives the timeouts.** Measured (§0, `PollProbe`): after
`SocketTimeoutException: Accept timed out`, `isClosed()` is false and the fifth call accepted a
real client. The same property is what makes §3.9's auto-restart a `continue`.

`CONNECT_TIMEOUT_MS = 5_000` (`run/LuaDebuggerController.kt:348`) is **kept**, now as the launched
configuration's `acceptTimeoutMs` (§2.3), because the IDE spawned the child two lines earlier.
An attach configuration's default `listenTimeoutSeconds = 0` maps to unbounded.

### 3.4 Peer origin check (`DEBUG-05-12`, BUG-456 §3 last paragraph)

`rejectForeignPeer(clientSocket)`, ≤7 logic lines:

1. `val peer = clientSocket.inetAddress`.
2. If `target.bindAddress.isLoopbackAddress` and `!peer.isLoopbackAddress` → close the socket and
   `throw IOException("Refused a debugger connection from $peer: this session listens on loopback only")`.
3. Otherwise `log.info("Client Connected $peer")` (preserving `:122`) and return.

Step 2 is unreachable on Linux because a loopback-bound listener never receives a non-loopback
peer (measured, §0 BindProbe: the LAN connect was **refused** at the kernel). It is retained as a
cheap invariant that fails loudly if a future change widens the bind; it is explicitly **not**
claimed as a security control, and it has no test of its own for exactly that reason (§9).

### 3.5 Root-mismatch detection (`DEBUG-05-07`'s missing diagnostic)

Probe B's whole failure is silent: `SETB` gets `200 OK`, nothing pauses, the debuggee exits. Probe
E shows the signal is already available at connect: **frame 1's field 2 is absolute exactly when
the configured `BASEDIR` did not prefix the debuggee's real path.**

`LuaRootMismatch` (NEW, `run/LuaRootMismatch.kt`) — a pure object so the rule is unit-testable
without a socket:

```kotlin
object LuaRootMismatch {
    fun detect(entryWireFile: String, remoteRoot: String?): String?
}
```

**Steps** (≤10 logic lines):

1. If `remoteRoot` is null or blank → return `null` (no root was declared; nothing to contradict).
2. If `entryWireFile` is blank, or starts with `"="`, or equals `"[C]"` → return `null`. mobdebug
   passes non-`@` chunk names through unchanged (`init.lua:337-341`), so `=[C]` and
   `loadstring` chunks are not filenames and carry no verdict.
3. If `!File(entryWireFile).isAbsolute` → return `null`. Probe E case B2: a relatively-launched
   debuggee reports a relative path and its breakpoints bind, mismatch or not.
4. Otherwise return
   `"""Breakpoints will not bind: the debuggee reports its entry chunk as "$entryWireFile", which is not under this session's remote source root "$remoteRoot". Set "Remote source root" to the directory the debuggee runs from."""`

The controller's `detectRootMismatch()` supplies the input, and is skipped entirely unless the
session has two *different* roots — so no local session pays for it:

1. If `target.mapper.remoteRoot.isNullOrBlank() || target.mapper.isIdentity()` → return. The first
   disjunct matters because `isIdentity()` is `false` for an **unrooted** mapper
   (`LuaPathMapper(null, null)`, which `LuaDebugTarget.fallback()` and a null `project.basePath`
   both produce): with no remote root there is nothing for a frame path to contradict, and
   `LuaRootMismatch.detect` would return `null` anyway — this just avoids paying for the round trip
   to find that out.
2. `val text = sendCommand(DebugCommand(DebugCommandKind.STACK, listOf(STACK_PROBE_PARAMS)))`
   where `const val STACK_PROBE_PARAMS = "-- {maxlevel=1}"`. Measured: 1348 bytes instead of
   10102, frame tuple intact (§0, Probe M).
3. `val entry = runReadAction { LuaRemoteStack.create(session.project, text, LuaFrameResolver(target.mapper)) }.entries.firstOrNull() ?: return`
   — the existing parser (`run/LuaRemoteStack.kt:20-27`), not a new one.
4. `val message = LuaRootMismatch.detect(entry.frame.file, target.mapper.remoteRoot) ?: return`.
5. `printToConsole(message, ConsoleViewContentType.ERROR_OUTPUT)` and
   `session.reportError(message)` — the idiom already at `run/LuaDebuggerController.kt:336-337`.

`DebugCommandKind.STACK` currently declares `minArgs = 0, maxArgs = 0`
(`run/LuaDebugConnection.kt:93-100`, defaults from `:59-60`). It becomes `maxArgs = 1` — **as a
declaration-consistency edit only, not as an enabler.** `minArgs`/`maxArgs` are *never read in
production*: `grep -rn "minArgs\|maxArgs" src/ | grep -v '^src/main/gen'` returns the two
declarations (`:59-60`), the per-constant values, `TestLuaDebugConnectionParsing.kt:64-65`
(which asserts `SETB`'s pair), and — unrelated — `analysis/redis/LuaRedisCommandInspection.kt:89-102`,
whose `minArgs` is a local computed from a Redis `arity`. Nothing validates a `DebugCommand` against
its kind's declared arity, so the `-- {maxlevel=1}` argument would be sent correctly with or without
this edit; `DebugCommand.toString` (`:162-170`) space-joins `args` unconditionally. Make the edit so
the declared model does not lie about the protocol, and **do not** claim a test covers it — see the
plan's Phase 2 note.

**Report, do not infer.** EmmyLua's `recognizeBaseDir` guesses the mapping by suffix-matching the
chunk name against a project file found by name. That is deliberately **not** done here: an
inferred root that is wrong produces a session that silently debugs the wrong copy of a file,
which is a worse failure than the one being fixed. The message contains the debuggee's own
absolute path, from which the user can read the correct root directly. Inference is recorded as
deferred future work in [risks-and-gaps.md](risks-and-gaps.md).

### 3.6 `204 Output` handling (`DEBUG-05-10`)

Two edits to `run/LuaDebugConnection.kt`.

**(a) A new command kind**, alongside the existing ones (`:52-156`):

```kotlin
OUTPUT(
    group = DebugCommandGroup.Config,
    responses = mapOf(
        DebuggerStatus.OK to DebuggerResponseDataKind.None,
        DebuggerStatus.BadRequest to DebuggerResponseDataKind.None,
    ),
    minArgs = 2,
    maxArgs = 2,
),
```

Measured (§0, Probe F): `OUTPUT stdout r` → `200 OK`; `OUTPUT stderr r`, `OUTPUT stdout x` and a
bare `OUTPUT` → `400 Bad Request`. mobdebug accepts only `stdout` and only modes `d`/`c`/`r`
(`init.lua:1011-1013` — `:1012` is the `([dcr])` pattern, `:1013` the `stream == "stdout"` test).

**(b) The `Output` branch, placed FIRST in `handleLine`** — before the Case A test at `:277` and
before the `if (running)` test at `:296`:

```kotlin
if (status == DebuggerStatus.Output) {
    val separator = data.lastIndexOf(' ')
    val byteCount = data.substring(separator + 1).toIntOrNull()
    if (separator < 0 || byteCount == null) {
        log.warn("malformed 204 Output header: '${data.take(80)}'")
        return
    }
    val stream = data.substring(0, separator)
    observer.onOutput(stream, DbgpFraming.readExactly(input, byteCount))
    return
}
```

**Why first and not inside `if (running)`.** Measured (§0, Probe K): with `OUTPUT stdout r`
active and the debuggee **paused** (`running == false`), an `EXEC` whose expression prints emits
the `204 Output` frame *before* the `200 OK` response. Placing the branch inside `if (running)`
reproduces today's defect exactly — the frame falls to `log.error` at `:337`, `byteCount` bytes
are left unread, and the next `readLine` throws at `:269`, killing the reader coroutine.

**Header format**, from `init.lua:1027` and `:1045`:
`"204 Output " .. stream .. " " .. tostring(#file) .. "\n" .. file`. `DebuggerStatus.Output.message`
is `"204 Output"` (`run/LuaDebugConnection.kt:29`), so `handleLine`'s existing
`removePrefix(status.message).removePrefix(" ")` (`run/LuaDebugConnection.kt:270`) leaves `data == "stdout 9"`. Splitting
on the **last** space, not the first, is deliberate: mobdebug only ever emits `stdout`, but a
last-space split is total over any single-token stream name.

**Payload format** — measured, and not what a reader would assume: `print("out:" .. b)` arrives as
`b'"out:42"\n'`, **quoted**. mobdebug pipes each argument through
`mobdebug.line(tbl[n], {nocode = true, comment = false})` and tab-joins them with a trailing
newline (`init.lua:1023-1026`). The payload is therefore a **Lua-serialised, tab-separated** value
list, not raw program output. The design does **not** unquote it: the console shows exactly what
the debuggee serialised, which is unambiguous for tables and non-string values. Recorded in
[risks-and-gaps.md](risks-and-gaps.md) as a known cosmetic difference from a local run.

**(c) `LuaDebugObserver` gains** `fun onOutput(stream: String, text: String)`
(`run/LuaDebugConnection.kt:179-191`). Three implementors update:
`LuaDebuggerController.DebugObserver` (`:297`) prints via
`printToConsole(text.trimEnd('\n'), ConsoleViewContentType.NORMAL_OUTPUT)`; the two test observers
(`TestLuaDebugHarness.kt:33-47` and any new one) get an empty body.

**(d) `LuaDebuggerController.redirectOutput()`**, sent during the handshake when
`target.redirectOutput`:

```kotlin
private suspend fun redirectOutput() {
    if (!target.redirectOutput) return
    sendCommand(DebugCommand(DebugCommandKind.OUTPUT, listOf(OUTPUT_STREAM, OUTPUT_MODE_REDIRECT)))
}
```

with `internal const val OUTPUT_STREAM = "stdout"` and `internal const val OUTPUT_MODE_REDIRECT = "r"`
(placement and visibility fixed in §3.7). Mode `r`
(redirect) rather than `c` (copy) because an attached debuggee's stdout goes to *its* terminal,
not ours; measured difference in §0, Probe F/F2.

### 3.7 The connect handshake (`DEBUG-05-11` + ordering)

`handshake()` runs immediately after `isReady = true`, replacing the bare `setBaseDir()` at
`run/LuaDebuggerController.kt:130`. Order is fixed and load-bearing:

1. `setBaseDir()` — must precede everything: `BASEDIR` resets `lastsource`
   (`init.lua:965-971`), and every subsequent path is interpreted against it.
2. `clearRemoteBreakpoints()` — `if (target.clearRemoteBreakpoints) sendCommand(DebugCommand(DebugCommandKind.DELB, listOf(DELB_ALL_FILES, DELB_ALL_LINES)))` with
   `internal const val DELB_ALL_FILES = "*"` and `internal const val DELB_ALL_LINES = "0"`. mobdebug's
   `remove_breakpoint` special-cases `file == '*' and line == 0` to `breakpoints = {}`
   (`init.lua:360-364`); measured `200 OK` and a real clear (§0, Probe G). It must precede
   `drainInstalledBreakpoints`, which happens in `LuaDebugProcess.sessionInitialized` at
   `run/LuaDebugProcess.kt:117` — i.e. after `connect()` returns. Ordering is therefore already
   correct and needs no change to `LuaDebugProcess`.
3. `redirectOutput()` (§3.6d).
4. `detectRootMismatch()` (§3.5) — last, because it needs `BASEDIR` applied.

`DELB` already declares `minArgs = 2, maxArgs = 2` (`run/LuaDebugConnection.kt:71-75`), so `* 0`
fits the existing model with no change. **That is also why a test that merely puts `DELB * 0` on the
wire asserts nothing about Lunar** — the bytes are already legal today; see TC-05-11a's restatement
in §9.

**Where the four constants live, and why they are `internal`.** `OUTPUT_STREAM`,
`OUTPUT_MODE_REDIRECT`, `DELB_ALL_FILES` and `DELB_ALL_LINES` are declared in
`LuaDebuggerController`'s **existing companion object** (`run/LuaDebuggerController.kt:347-351`),
beside `CONNECT_TIMEOUT_MS` (`:348`), as `internal const val`. `internal` rather than `private`
because the two harness rows (TC-05-10c, TC-05-11a) must send *the very values production sends* —
otherwise mutating a constant leaves their hand-typed literals untouched and both rows stay green,
which is the defect those rows carried before this revision. Main-source `internal` is visible to
this module's test compilation: `PublishRockAction.isAuthFailure` is `internal` at
`rocks/publish/PublishRockAction.kt:144` and is called from
`src/test/kotlin/net/internetisalie/lunar/rocks/publish/PublishRockAuthFailureTest.kt:14`.

**How the handshake itself is asserted.** `handshake()` stays `private`; it is reached through the
public `connect()` by **TC-05-11b**, which drives the controller against a scripted fake debuggee on
a loopback socket and asserts the recorded command list is exactly
`["BASEDIR <root>/", "DELB * 0", "OUTPUT stdout r"]` — contents *and* order. That single row is what
makes each of the following turn red: dropping either call from `handshake()`, reordering them,
mutating any of the four constants, or moving `setBaseDir()` out of first place. Measured socket
mechanics for the fixture: §0, Probe P.

### 3.8 Frame resolution (`DEBUG-05-08`, `-09`)

`LuaFrameResolver.resolve(wireFile)`, ≤10 logic lines:

1. If `wireFile` is blank, starts with `"="`, or equals `"[C]"` → return `null`. This is the guard
   that keeps `=[C]` frames (`run/LuaRemoteStack.kt:66`) out of the VFS and out of the mapper.
2. `cache[wireFile]?.let { return it }`; if `cache.containsKey(wireFile)` return `null`
   (a negative result is cached too — today's `:43-46` caches only hits and re-stats every miss).
3. `val localPath = mapper.toLocalPath(wireFile)` (§3.2).
4. `val resolved = LocalFileSystem.getInstance().findFileByPath(localPath)`.
5. `cache[wireFile] = resolved`; return `resolved`.

**The three constructors and the resolver default, stated in full** — Phase 1's exit criterion
requires `TestLuaExecutionStack` and `TestLuaRemoteStackFrames` green *unmodified*, and
`TestLuaExecutionStack.kt:14` calls `LuaRemoteStack(null)` while `TestLuaRemoteStackFrames.kt:34,51`
call `LuaRemoteStack.create(myFixture.file)`. Both keep compiling because the resolver is a
**defaulted second parameter**:

```kotlin
class LuaRemoteStack(
    stack: LuaTable?,
    private val resolver: LuaFrameResolver = LuaFrameResolver.identity(null),
) {
    val entries: List<LuaRemoteStackEntry> =
        stack?.indexed?.mapNotNull { it.checkTable()?.let { table -> LuaRemoteStackEntry(table, resolver) } }
            ?: emptyList()

    companion object {
        fun create(project: Project, text: String): LuaRemoteStack =                 // unchanged signature
            LuaRemoteStack(LuaDebugValueParser.parseChunk(project, text))
        fun create(project: Project, text: String, resolver: LuaFrameResolver): LuaRemoteStack =  // NEW
            LuaRemoteStack(LuaDebugValueParser.parseChunk(project, text), resolver)
        fun create(file: PsiFile): LuaRemoteStack =                                  // unchanged signature
            LuaRemoteStack(LuaDebugValueParser.parseFile(file))
    }
}

class LuaRemoteStackEntry(private val stackEntryTable: LuaTable, private val resolver: LuaFrameResolver)
class LuaRemoteStackFrame(private val stackFrameTable: LuaTable?, private val resolver: LuaFrameResolver)
```

**The default is `LuaFrameResolver.identity(null)`**, i.e. `LuaPathMapper(null, null)` — the same
mapper `LuaDebugTarget.fallback()` carries. With both roots null, `PathMapping.isEmpty()` is true,
`toLocalPath` returns the wire path unchanged, and `resolve` is a bare
`findFileByPath(wireFile)`. Consequences, both intended:

- An **absolute** wire path still resolves under the default, so `create(project, text)` and
  `create(file)` keep working for any caller that has no mapper.
- A **relative** wire path resolves to `null` under the default. In `TestLuaRemoteStackFrames` the
  frames' field 2 is `"stack.lua"`, so `frame.virtualFile` is `null` there — as it already is today,
  because the shipped code keys on field 7 (`/home/mini/Documents/src/lua/test/stack.lua`, a path
  that does not exist on a CI checkout). **Neither of that file's three tests asserts on
  `virtualFile`** (they assert `file`, `path`, `name`, `entries.size` and variable values), so the
  default cannot break them either way.
- The only production caller that matters passes a real resolver:
  `LuaDebuggerController.variables()` uses `create(project, text, LuaFrameResolver(target.mapper))`.

`LuaRemoteStackFrame` (`run/LuaRemoteStack.kt:67-86`) then changes in exactly one place:

```kotlin
val virtualFile: VirtualFile? by lazy { resolver.resolve(file) }   // was: virtualFiles.getOrDefault(path, null)
```

`file` is `getByIndex(1)` (`:73-74`) — mobdebug frame field 2, `removebasedir(src, basedir)`
(`init.lua:344`). `path` (`getByIndex(6)` = field 7 = `short_src`, `:82-83`) is **kept** as a
read-only accessor because `TestLuaRemoteStackFrames.kt:37` asserts on it, but nothing in
production reads it any more.

`LuaRemoteStackEntry.init` (`:41-47`) is **deleted**: the eager per-frame
`LocalFileSystem.findFileByPath` moves into the `by lazy` above. This is the same eagerness
[[BUG-450]] §3b names, so the two changes agree rather than fight; whichever lands second must
keep the resolution lazy.

`LuaExecutionStack.computeStackFrames` (`run/LuaExecutionStack.kt:31-58`) is unchanged — it
already branches on `it.frame.file == "=[C]"` (`:38`) and already reads `it.frame.virtualFile`
(`:49`).

`LuaPosition.localPosition()` (`run/LuaPosition.kt:31-35`) gains a mapper parameter:

```kotlin
fun localPosition(mapper: LuaPathMapper): XSourcePosition? =
    createLocalPosition(LocalFileSystem.getInstance().findFileByPath(mapper.toLocalPath(path)), line)
```

Its single production call site is `run/LuaDebuggerController.kt:326`. **This also fixes a live
capability-A defect**: today `path` is the basedir-*relative* wire path, so
`findFileByPath("sub/target.lua")` returns null and a step or watchpoint pause reaches
`LuaSuspendContext` with a null position (`run/LuaSuspendContext.kt:46-58`, `:63`), which
`LuaStackFrame.customizePresentation` renders as `<internal C>`
(`run/LuaStackFrame.kt:125-134`). It is recorded, not silently absorbed —
[risks-and-gaps.md](risks-and-gaps.md) §3.

`LuaPosition.createRemotePosition` gains the mapper overload; the `File?` overload is retained and
re-expressed:

```kotlin
fun createRemotePosition(xSourcePosition: XSourcePosition, mapper: LuaPathMapper): LuaPosition =
    LuaPosition(mapper.toWire(xSourcePosition.file.path).replace('\\', '/'), xSourcePosition.line + 1)

fun createRemotePosition(xSourcePosition: XSourcePosition, workingDir: File?): LuaPosition =
    createRemotePosition(xSourcePosition, LuaPathMapper.identity(workingDir))
```

`LuaPathMapper.identity(null)` yields `localRoot == remoteRoot == null`, so `toWire` returns the
absolute path — matching `TestLuaPosition.testCreateRemotePositionFallsBackToAbsolutePath`
(`src/test/kotlin/.../TestLuaPosition.kt:56-67`) exactly.

### 3.9 Auto-restart (`DEBUG-05-13`, Could)

Today `close()` (`:150-171`) closes the `ServerSocket` and cancels the session scope, reached from
`DebugObserver.onDisconnected` (`:341-344`). With `target.autoRestart`:

1. `onDisconnected()` → if `!target.autoRestart` → `close()` then `disconnectListener?.invoke()`
   (§2.4) and return.
2. Otherwise call `restartListener()`, a named `internal` member so the branch has a seam:

   ```kotlin
   internal fun restartListener() {
       connection?.let { runCatching { it.close() } }
       connection = null
       isReady = false
       printToConsole("Debuggee disconnected; still listening on ${target.port}",
           ConsoleViewContentType.SYSTEM_OUTPUT)
       scope.launch { connect() }
   }
   ```

   `serverSocket` is **not** touched and the scope stays alive, so `connect()` re-enters
   `awaitClient` on the *same* `ServerSocket`. Measured re-enterable in §0 (`PollProbe`).
3. `connect()` therefore must not re-open a live listener. Its first statement becomes
   `val server = listener()`, over a second `internal` seam:

   ```kotlin
   internal fun listener(): ServerSocket = serverSocket ?: openListener(target).also { serverSocket = it }
   ```

4. `LuaDebugProcess.stop()` (`run/LuaDebugProcess.kt:67-72`) already sets `myClosing = true` and
   calls `controller.terminate()` → `close()`, which is the user-facing way out of the loop.

**`restartListener()` calls `printToConsole`, so any test that reaches it must install a console.**
With `console == null`, `printToConsole` takes `log.error("Console not set")`
(`run/LuaDebuggerController.kt:97-101`), which fails a platform test outright. `setConsole` is public
(`:173-175`); TC-05-13a installs a `java.lang.reflect.Proxy` over `ConsoleView` — the repo's
mock-free idiom at `src/test/kotlin/net/internetisalie/lunar/redis/debug/TestLuaLdbController.kt:97-101`
— and asserts on what it recorded. Wrapping in `LoggedErrorProcessor.executeWith`
(`src/test/kotlin/net/internetisalie/lunar/toolchain/exec/LuaToolExecutionServiceTest.kt:96-97`)
would *tolerate* the missing console instead of exercising the print, so it is not used here.

The platform models this as `RemoteConfiguration.AUTO_RESTART`, implemented in
`java/debugger/impl/.../RemoteDebugProcessHandler.kt:20-27` by *not* calling
`notifyProcessDetached` and re-attaching; the mechanism above is the same idea expressed on the
listener because Lunar's handler owns no process.

## 4. External Data & Parsing

Everything the IDE reads from the debuggee is DBGp text. Three formats are consumed; two already
have parsers.

### 4.1 `204 Output` frame — NEW parse

- **Format**: `204 Output <stream> <byteCount>\n<payload>` where `<payload>` is exactly
  `<byteCount>` **bytes** (not characters). Sample, measured: `204 Output stdout 9\n"out:42"\n`.
- **Parse strategy**: §3.6b — strip the status prefix (existing code, `:270`), split the remainder
  on its **last** space, `toIntOrNull()` the tail, then `DbgpFraming.readExactly(input, byteCount)`
  (`run/DbgpFraming.kt:41-55`, which exists precisely because the prefix counts bytes).
- **Maps to**: `LuaDebugObserver.onOutput(stream, text)` → `printToConsole`.
- **Failure handling**: a header whose last token is not an integer is logged at `warn` and the
  frame is skipped **without** consuming a payload — any other choice guesses at a byte count and
  desynchronises the stream, which is the very defect being fixed.

### 4.2 `STACK` frame tuple — EXISTING parser, one index changes

- **Format**, from `init.lua:343-347`:
  `{name, removebasedir(src, basedir), linedefined, currentline, what, namewhat, short_src}`.
  0-based as `LuaTable.getByIndex` sees it: `0`=name, `1`=file, `2`=linedefined, `3`=currentline,
  `6`=short_src.
- **Parse strategy**: unchanged — `LuaDebugValueParser.parseChunk` →
  `LuaRemoteStack.create(project, text, resolver)` (`run/LuaRemoteStack.kt:20-27`). No new parser.
- **Maps to**: `LuaRemoteStackFrame.file` (index 1) is now the resolution key; index 6 is retained
  but unread in production (§3.8).
- **Failure handling**: `getByIndex` already returns `null`-safe defaults (`:72-83`); a frame whose
  field 2 is `=[C]` or a `load()` chunk is filtered by §3.8 step 1.

### 4.3 `STACK` request parameters — NEW, one call site

- **Format**: mobdebug matches `string.match(line, "--%s*(%b{})%s*$")` (`init.lua:994`), so the
  argument is a Lua table literal preceded by a `--` comment marker: `STACK -- {maxlevel=1}`.
- **Maps to**: `DebugCommand(DebugCommandKind.STACK, listOf("-- {maxlevel=1}"))`.
- **Failure handling**: an unparsable table yields `params = {}` and mobdebug proceeds with
  defaults (`:995-997`) — i.e. a malformed probe degrades to a full `STACK`, never to an error.
- **Do not use `maxnum`**: measured, `maxnum=3` truncates the **frame tuple itself** to three
  elements and `maxnum=0` empties the response (§0, Probe M).

### 4.4 Bind-host resolution — an executed note on threading and hermeticity

`resolveBindAddress` (§3.1) ends in `InetAddress.getByName(host)`, which for a non-literal host is a
resolver call. Two things had to be checked rather than assumed.

**It never runs on the EDT.** Check A2 is only reached from `checkConfiguration()`, and every
platform path into it is off-EDT by construction — `RunManagerImpl.canRunConfiguration` opens with
`ThreadingAssertions.assertBackgroundThread()`
(`platform/execution-impl/src/com/intellij/execution/impl/RunManagerImpl.kt:154-157`), the editor's
validation runs inside `ReadAction.nonBlocking`
(`platform/execution-impl/src/com/intellij/execution/impl/SingleConfigurationConfigurable.java:316-319`),
and so does the Run-widget icon refresh
(`platform/execution-impl/src/com/intellij/execution/impl/RunConfigurationIconAndInvalidCache.kt:61-67`).
No contract §1 violation, and no need for a background wrapper of our own.

**Its test fixture must not depend on a resolver.** Measured (`HostProbe.java`, corretto-21.0.10):

```
'no-such-host.invalid'         24.7 ms  UnknownHostException: no-such-host.invalid: Name or service not known
'a b'                           0.2 ms  UnknownHostException: a b: Name or service not known
'-x-'                           0.1 ms  UnknownHostException: -x-: Name or service not known
'999.999.999.999'              91.3 ms  UnknownHostException: 999.999.999.999: Name or service not known
'0.0.0.0'                       0.8 ms  OK /0.0.0.0
'127.0.0.1'                     0.0 ms  OK /127.0.0.1
```

`no-such-host.invalid` and `999.999.999.999` reach the resolver (24.7 ms and 91.3 ms); `a b` and
`-x-` are rejected locally in ~0.2 ms because the syntax never gets past `getaddrinfo`. TC-05-12c
therefore uses **`"a b"`**: no network, no DNS wildcard, no CI flake. (`.invalid` is
RFC 2606-reserved and *should* never resolve, but "should" is a property of somebody else's
resolver, which is exactly the kind of premise this document is supposed to stop inheriting.)

## 5. Data Flow

### 5.1 A launched local session (capability A) — what changes

1. User presses Debug on a `LuaRunConfiguration`. `LuaDebugRunner.canRun` matches (`:52-57`),
   `doExecute` calls `state.execute` (`:73`) which spawns `lua` with the debug environment,
   **now including `MOBDEBUG_HOST=127.0.0.1`** (§2.6).
2. `LuaDebugProcess.sessionInitialized` (`:96-134`) launches `controller.connect()`.
3. `LuaDebugTarget.of(configuration)` → loopback bind, 5 s deadline, identity mapper.
4. `openListener` binds `127.0.0.1:8172` instead of `0.0.0.0:8172` (§3.1). `awaitClient` polls at
   500 ms until the 5 s deadline.
5. `handshake()`: `BASEDIR <effectiveWorkDirectory>/`; no `DELB * 0`; no `OUTPUT`;
   `detectRootMismatch` returns immediately because `mapper.isIdentity()`. **The `BASEDIR` argument
   and every wire path change for a configuration whose working directory is empty** — the default —
   from `BASEDIR /` + `SETB home/u/proj/a.lua` to `BASEDIR /home/u/proj/` + `SETB a.lua`. §2.3's
   table and §0 Probe N state the change, its measurement and why the old form is not recoverable;
   risks-and-gaps Risk 1.8 owns it and TC-05-03c pins it.
6. Breakpoints, steps and frames behave as today, except that a frame whose absolute path exceeds
   `LUA_IDSIZE` now resolves (§3.8) and a step pause now has a source position (§3.8 note).

### 5.2 An attach session with differing roots (capability B)

1. User creates a **Lua Remote (Mobdebug)** configuration: listen host `0.0.0.0`, port `8172`,
   local root `/ide/proj`, remote root `/srv/app`, listen timeout `0`.
2. `checkConfiguration()` runs `LuaTargetChecks.attach(this)`: the local root exists, the remote
   root is set, `0.0.0.0` resolves → no problem.
3. Debug is pressed. `LuaAttachState.execute` returns a `DefaultExecutionResult` over a
   `DefaultDebugProcessHandler` and a fresh console; `LuaDebugProcess` attaches to both.
4. `awaitClient(server, 0)` blocks indefinitely under `withBackgroundProgress(project,
   "Connecting to debugger")` (`run/LuaDebugProcess.kt:112`); the loop notices cancellation within
   `ACCEPT_POLL_MS` (500 ms). **Grounded in the platform's declared contract, not measured here**:
   the three-argument overload Lunar calls delegates with `TaskCancellation.cancellable()`
   (`platform/progress/shared/src/tasks.kt:18-23`) and the KDoc of the overload it delegates to
   states *"@throws CancellationException if the calling coroutine was canceled, **or if the
   indicator was canceled by the user in the UI**"* (`:73`). TC-05-06a exercises only the
   programmatic half (`job.cancelAndJoin()`); the **user-facing** half — that the progress
   indicator's Cancel button ends the listen — is not unit-testable without a UI and is
   **HV-05** in the human-verification checklist.
5. The user starts `lua /srv/app/main.lua` on the other host with
   `MOBDEBUG_HOST=<ide-address> MOBDEBUG_PORT=8172`. mobdebug dials in (Probe J2).
6. `handshake()`: `BASEDIR /srv/app/`; `DELB * 0`; `OUTPUT stdout r`;
   `STACK -- {maxlevel=1}` → frame 1 field 2 is `main.lua` (relative) → no warning.
7. A breakpoint on `/ide/proj/sub/a.lua:12` becomes `SETB sub/a.lua 12` (§3.2 step 3).
8. The pause reports `202 Paused sub/a.lua 12`; `LuaPosition.localPosition(mapper)` maps it back to
   `/ide/proj/sub/a.lua`. Frames resolve through `LuaFrameResolver` the same way.
9. `print(x)` in the debuggee arrives as `204 Output stdout N` and is printed to the IDE console.

### 5.3 The same session with the remote root left at the local one

Steps 1–5 as above but remote root `/ide/proj` while the debuggee runs from `/srv/app`.

6. `BASEDIR /ide/proj/` is accepted with `200 OK`. `STACK -- {maxlevel=1}` returns frame 1 with
   field 2 = `/srv/app/main.lua` — **absolute**, because the strip did not match.
7. `LuaRootMismatch.detect` fires; the console shows the message naming both paths and
   `session.reportError` surfaces it. The user fixes the field instead of watching a breakpoint
   never bind (Probe B).

## 6. Edge Cases

| Case | Handling |
| :-- | :-- |
| `remoteRoot` unset on an attach configuration | `baseDirArgument()` is `null` → **no `BASEDIR` is sent**; mobdebug's `basedir` stays `""` (`init.lua:129`) and every wire path is the debuggee's own. `toWire` returns absolute local paths, which only bind if the two filesystems agree. Check A1 warns at edit time (§2.8). |
| `localRoot` unset on an attach configuration | `mapper.localRoot == null` → `toLocalPath` returns the wire path unchanged and frames resolve only if it happens to be a valid local path. `WORKDIR_MISSING` does not fire on an empty value (DEBUG-06 §3.2 check 5 requires non-empty), so this is silent by design — the same silence `LuaRunConfiguration` has today. |
| Debuggee launched with a **relative** script path | Field 2 is already relative and matches a relative `SETB` regardless of `BASEDIR` (Probe E, case B2). `LuaRootMismatch` step 3 returns `null` so no false warning. |
| A frame from outside both roots (`/usr/share/lua/5.4/socket.lua`) | `toLocalPath` returns it unchanged (`mapToLocal` no-ops on a non-matching prefix), `findFileByPath` returns `null`, and `computeStackFrames` renders `<internal C>` — today's behaviour, unchanged. |
| `=[C]` and `load()` chunks | §3.8 step 1 and §3.5 step 2 both filter on a leading `=`; `computeStackFrames`'s existing `"=[C]"` branch (`run/LuaExecutionStack.kt:38`) is untouched. |
| Two Lunar debug sessions on port 8172 | Unchanged here — DEBUG-06 `-15`'s `LuaDebugPortProbe` owns it. `openListener` throws `BindException`, caught at `run/LuaDebugProcess.kt:121-132`. |
| `bindHost` = a NIC address, debuggee on `localhost` | The listener refuses loopback (measured, §0). Check A2 cannot detect this (the address resolves); the console's `"Debugger connected at …"` never appears and the listen timeout expires. Documented as HV-01…HV-05 in [implementation-plan.md](implementation-plan.md); see risks-and-gaps Gap 2.4. |
| Attach configuration under the **Run** executor | Impossible: `RunConfigurationWithSuppressedDefaultRunAction` removes the action (`DefaultRunProgramRunner.kt:37`) and `LuaDebugRunner.canRun` requires `DefaultDebugExecutor.EXECUTOR_ID`. |
| Attach session stopped before any debuggee connects | `LuaDebugProcess.stop()` → `controller.terminate()` → `close()` closes the `ServerSocket`; `awaitClient`'s in-flight `accept()` throws `SocketException: Socket closed` (measured, §0), which `connect()` propagates to the existing `catch (e: Exception)` at `run/LuaDebugProcess.kt:121`. `myClosing` is already `true`, so no error dialog is shown (`run/LuaDebugProcess.kt:124`). |
| Launched session with an **empty** working directory (the default) | `effectiveWorkDirectory()` returns `project.basePath`, so `BASEDIR <basePath>/` is sent and wire paths are project-relative. Today's `BASEDIR /` with root-relative wire paths is **replaced** — declared in §2.3, measured in §0 Probe N, risked as Risk 1.8, pinned by TC-05-03c. |
| Launched session with an empty working directory **and** `project.basePath == null` | `effectiveWorkDirectory()` returns `null` → `LuaPathMapper.identity(null)` → `baseDirArgument()` is `null` → **no `BASEDIR` is sent** (§3.7 step 1 returns without sending). mobdebug's `basedir` stays `""` (`init.lua:129`), chunk names stay absolute, and `toWire` returns absolute paths, which match. Measured to bind: §0 Probe N4. Today this shape sends `BASEDIR /`. |
| Remote root (or a launched session's working directory) is exactly `/` | `trimSlash` returns `"/"` unchanged (`PathMappingSettings.java:289-291`), so it is the only root that arrives slash-terminated. §3.2's `remoteRootPrefix` (`normalisedRemoteRoot.removeSuffix("/")`) collapses it to `""`, giving `BASEDIR /` — what the shipped controller sends today — and `joinRemote("sub/a.lua") == "/sub/a.lua"`. Without the guard both would double the slash (`BASEDIR //`, which `init.lua:657`'s literal `gsub` can never match). `toWire` is unaffected: `canReplaceLocal` is `false` for a `/` root against any deeper path, so step 4 returns it absolute. Measured in §0 Probe O; pinned by TC-05-03d. |
| `redirectOutput` on a **launched** session | Off by default (§2.3). The child's stdout is already piped into the console by `ProcessHandlerFactory.createColoredProcessHandler` (`run/LuaRunConfiguration.kt:345-348`); mode `r` would silence it and mode `c` would double it. |
| `clearRemoteBreakpoints` on a launched session | Off by default: a process the IDE just spawned holds no breakpoints, and the extra round trip is pure latency. |
| `autoRestart` with the listener already closed | Step 3 of §3.9 re-opens it; `openListener` is idempotent from the caller's point of view because `serverSocket` is checked first. |
| `204 Output` with a zero byte count | `DbgpFraming.readExactly(input, 0)` returns `""` (`run/DbgpFraming.kt:46`); the observer is called with an empty string and `printToConsole` prints a bare newline. Harmless. |
| Multibyte output payload | `readExactly` reads **bytes** then decodes once (`run/DbgpFraming.kt:40-55`) — the reason that helper exists (MAINT-24). |

## 7. Integration Points

Exactly **one** new `plugin.xml` registration, inserted immediately after the existing Redis
producer at `src/main/resources/META-INF/plugin.xml:615-616`, inside the same
`<extensions defaultExtensionNs="com.intellij">` block:

```xml
<!-- DEBUG-05-05: attach to a Lua process the IDE did not start (mobdebug dials back) -->
<configurationType
        implementation="net.internetisalie.lunar.run.attach.LuaAttachRunConfigurationType"/>
```

Nothing else is added:

- `<programRunner implementation="net.internetisalie.lunar.run.LuaDebugRunner"/>` already exists
  (`:656`); the runner is extended in Kotlin, not re-registered.
- `<xdebugger.breakpointType implementation="net.internetisalie.lunar.run.LuaLineBreakpointType"/>`
  already exists (`:658`) and is configuration-agnostic.
- `<notificationGroup id="notification.group.lunar.debugger" …/>` already exists (`:680-683`) and
  is what `LuaDebugRunner.notifyExecutionError` uses (`run/LuaDebugRunner.kt:129`).
- No `runConfigurationProducer` for the attach type: a remote target cannot be derived from a
  right-click on a local file. (DEBUG-06 `-18` adds one for `LuaRunConfiguration` only.)
- No new index, no new application/project service, no new settings key. The only new persisted
  state is `LuaAttachRunConfigurationOptions`, serialised by the existing
  `RunConfigurationOptions`/`BaseState` mechanism.

**Toolchain/settings interaction:** none. An attach configuration has no runtime
(DEBUG-06 §2.10 point 1), so `LuaToolResolver`, `LuaToolchainRegistry` and
`LuaExecutionEnvironmentBuilder` are not consulted.

## 8. Requirement Coverage

| Requirement | Priority | Status in requirements.md | Implemented by |
| :-- | :-: | :-- | :-- |
| `DEBUG-05-01` | M | Full | §2.4 — the listen/accept shape survives; only the bind address, deadline and cancellation change |
| `DEBUG-05-02` | M | Full | §2.3 — `debugPort` still feeds the target and `MOBDEBUG_PORT`; no change to the spinner |
| `DEBUG-05-03` | M | Full | §3.2 `baseDirArgument()` (including the `/`-root guard, TC-05-03d), §3.7 step 1. **Not byte-identical for a default local session**: the base directory is now `effectiveWorkDirectory()` rather than `listOfNotNull(…).first()`, so an empty working directory yields `BASEDIR <basePath>/` where it used to yield `BASEDIR /` (§2.3, §0 Probe N, Risk 1.8, TC-05-03c). Explicitly-set working directories are unchanged. |
| `DEBUG-05-04` | M | Not Implemented | §2.6 — `ENV_MOBDEBUG_HOST` from `LuaDebugTarget.debuggeeHost()`; §2.7 the attach editor's host field |
| `DEBUG-05-05` | M | Not Implemented | §2.5, §2.6 (`canRun`, `LuaAttachState`), §2.4 (the disconnect bridge that ends a process-less session), §7 (registration) |
| `DEBUG-05-06` | M | Not Implemented | §3.3 `awaitClient` (unbounded when `acceptTimeoutMs <= 0`, cancellable within `ACCEPT_POLL_MS`). Executed coverage is split: TC-05-06a/b cover the *programmatic* cancellation; the **user-facing** Cancel button is HV-05 (§5.2 step 4). |
| `DEBUG-05-07` | M | Not Implemented | §3.2 `toWire`, §3.7 step 1, and §3.5 for the missing diagnostic |
| `DEBUG-05-08` | M | Not Implemented | §2.2, §3.8 |
| `DEBUG-05-09` | S | Not Implemented | §3.8 — resolution keys on frame field 2 (`file`), not field 7 (`path`) |
| `DEBUG-05-10` | S | Not Implemented | §3.6, §4.1; §2.6 supplies the console an attached session otherwise lacks. Both halves are asserted: the **receiver** by TC-05-10a/b, the **sender** (`redirectOutput()` and its `handshake()` call) by TC-05-11b, with TC-05-10c pinning the constant values against a real debuggee |
| `DEBUG-05-11` | S | Not Implemented | §3.7 step 2 (`DELB * 0`) — sent **on connect**, which is TC-05-11b's ordered-transcript assertion; TC-05-11a pins the constant values against a real debuggee |
| `DEBUG-05-12` | S | Not Implemented | §3.1 (loopback default, explicit bind host), §3.4 (origin check) |
| `DEBUG-05-13` | C | Not Implemented | §3.9 |
| `DEBUG-05-14` | S | Partial | §1.2 — REDIS-02's LDB debugger is named as **left alone**; no design |
| `DEBUG-05-15` | C | **Won't** | No design. Lua exposes no external attach API; a cooperating process is served by `-05`. |
| `DEBUG-05-16` | W | **Won't** | No design. `debugger_loop`'s 16 verbs include no source-retrieval verb (`init.lua:919-1035`). |

Every `Must` row (`-01`…`-08`) has a section above and at least one test case below. `-06` is the
one row whose acceptance is split between an executed test and a human check, and §5.2 step 4 says
which half is which.

## 9. Acceptance Test Cases

`requirements.md` has no TC table, so acceptance lives here. **Every row names the mutation that
turns it red; every mutation compiles; every mutation is reachable from that row's own fixture.**
The failure mode being guarded against is the one `requirements.md`'s own Verification section
demonstrates — `TestLuaDebugHarness.testBreakpointAndExec` asserts the matching-root case and is
green whether or not any of this works — and the one DEBUG-06 round 1 shipped, a mutation that
returned a *supertype* of the declared return type and therefore could not compile.

**Files.**

- `src/test/kotlin/net/internetisalie/lunar/run/TestLuaPathMapper.kt` (**NEW**), a plain class with
  `kotlin.test.Test` — no fixture needed; the mapper is pure. Idiom:
  `TestLuaDebugConnectionParsing.kt:12`.
- `src/test/kotlin/net/internetisalie/lunar/run/TestLuaFrameResolver.kt` (**NEW**), extending
  `net.internetisalie.lunar.BaseDocumentTest` — needs the VFS and `LuaDebugValueParser`. Idiom:
  `TestLuaRemoteStackFrames.kt:10`. **Every method needs `@Test`** — `BaseDocumentTest` is a plain
  `open class`, so an un-annotated method is silently never collected ([[BUG-461]] §1).

**Real files must be refreshed into the VFS before `findFileByPath` sees them.** Production code
calls `LocalFileSystem.getInstance().findFileByPath` (§3.8 step 4), which does **not** refresh. Every
fixture below that writes a real file must first do

```kotlin
val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(scratch.toPath())
    ?: error("temp dir not found in VFS")
root.refresh(false, true)
```

— verbatim the idiom at `src/test/kotlin/net/internetisalie/lunar/util/LuaFileUtilTest.kt:30-33`.
Omitting it makes every path-resolution row fail for a reason unrelated to its mutation.
- `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebuggerListener.kt` (**NEW**), extending
  `BaseDocumentTest` — socket-level coverage of §3.1/§3.3/§3.9 via the `internal` seams. Kotlin
  `internal` is **module**-scoped, so a test in the same source set reaches them; prior art
  `PublishRockAuthFailureTest.kt:14` → `PublishRockAction.kt:144`.

  **A real `LuaDebuggerController` is constructible in a light fixture, and the repo already does
  it.** `TestLuaDebuggerEvaluator.kt:50` builds
  `LuaDebuggerController(fakeSession(project), CoroutineScope(SupervisorJob()))` over a hand-rolled
  anonymous `XDebugSession` (`:76-77`) whose `getRunProfile()` returns **null** (`:80`) — which is
  exactly the `LuaDebugTarget.fallback()` path (§2.3) and the reason that factory exists. Copy that
  fake; the alternative idiom, a `java.lang.reflect.Proxy` recording only the callbacks under test,
  is at `redis/debug/TestLuaLdbController.kt:97-101`. **No mocking framework is on the test
  classpath** (`TestLuaDebuggerEvaluator.kt:36-37`) — do not reach for one.
- `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebugTarget.kt` (**NEW**), extending
  `BaseDocumentTest` — needs a `Project` (for `basePath`) and a real `LuaRunConfiguration`. Build the
  configuration exactly as `TestLuaRunConfiguration.kt:158-159` does:
  `LuaRunConfiguration(myFixture.project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")`.
  **Every method needs `@Test`.**
- `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebugOutputFrames.kt` (**NEW**), a plain
  class driving a real loopback socket pair into `LuaDebugConnection`.
- `src/test/kotlin/net/internetisalie/lunar/run/attach/TestLuaAttachRunConfiguration.kt` (**NEW**),
  extending `net.internetisalie.lunar.BaseDocumentTest` — needs a `Project` for the configuration and
  the console. Idiom: `TestLuaRunConfiguration.kt:26` (`class TestLuaRunConfiguration :
  BaseDocumentTest()`), which is the repo's run-configuration test base. `BaseDocumentTest` is a
  plain JUnit5 `open class`, **not** JUnit3, so **every method needs `@Test`** — the same trap as
  `TestLuaFrameResolver` above ([[BUG-461]] §1).
- `src/test/kotlin/net/internetisalie/lunar/run/TestLuaDebugHarness.kt` (**MODIFIED**) — one new
  end-to-end case, `testBreakpointBindsAcrossDifferingRoots`.
- `src/test/kotlin/net/internetisalie/lunar/run/LuaDebugHarness.kt` (**MODIFIED**) — §2.9.
- `src/test/kotlin/net/internetisalie/lunar/ui/RunConfigurationEditorTextTest.kt` (**MODIFIED**) —
  TC-05-07f. A `BasePlatformTestCase` (JUnit3 naming, **no `@Test`**) whose assertions are
  file-wide but whose subject list is hard-coded; the edit is three lines, listed as a Phase 3 task.
  Note the `Disposer.register(testRootDisposable, editor)` wrapper (`:149-152`) — the new editor
  goes through the same `register(...)` helper, not raw construction.

**Shared fixture helper** in `TestLuaFrameResolver` and the harness test:

```kotlin
private fun treeWith(root: File, relative: String, text: String): File  // mkdirs + writeText, returns the file
```

**`$scratch` — who creates it and who deletes it.** Every row below that writes a real file reads
`$scratch` as a per-test temporary directory; it is **not** a shared constant and nothing in the
repo supplies one. Each of the two files declares its own, with these exact members:

```kotlin
private lateinit var scratch: File          // "$scratch" in the rows below is scratch.path

@BeforeTest fun createScratch() { scratch = Files.createTempDirectory("lunar-debug05").toFile() }
@AfterTest  fun deleteScratch() { scratch.deleteRecursively() }
```

`treeWith(scratch, "sub/a.lua", text)` then creates the file, and the VFS refresh above runs on
`scratch.toPath()`.

**Do not copy `LuaFileUtilTest`'s lifecycle shape.** Its create/delete pair is
`override fun setUp()` / `override fun tearDown()` (`src/test/kotlin/net/internetisalie/lunar/util/LuaFileUtilTest.kt:11-22`)
— correct there, because that class extends `BasePlatformTestCase` (`:8`), a **JUnit 3** base whose
lifecycle *is* those two overridable methods. `TestLuaFrameResolver` extends `BaseDocumentTest`,
which is a plain JUnit 5 class using `kotlin.test`'s `@BeforeTest`/`@AfterTest`
(`src/test/kotlin/net/internetisalie/lunar/BaseDocumentTest.kt:33-34`, `:54-57`) — and its `after()`
is **not `open`**, so there is nothing to override. Declare *additional* `@BeforeTest`/`@AfterTest`
methods, which JUnit 5 permits alongside the base class's own; ordering does not matter because the
scratch directory and `myFixture` are independent. Only `LuaFileUtilTest.kt:30-33` — the
`refreshAndFindFileByNioFile` + `refresh` pair quoted above — is the idiom being borrowed from that
file. The harness cases (`TestLuaDebugHarness`, a plain `kotlin.test` class with no base,
`TestLuaDebugHarness.kt:12`) use the same two annotated methods.

**Assertion discipline.** Class assertions use `assertEquals(X::class, thrown::class)` on the
exact class, never `assertFailsWith<Supertype>`.

| TC | Requirement | Fixture (input) | Assertion (output) | Mutation that turns it red — compiles, and reachable from THIS fixture |
| :-- | :-- | :-- | :-- | :-- |
| TC-05-03a | `-03`, `-07` | `LuaPathMapper("/ide/proj", "/srv/app")`; local path `/ide/projekt/a.lua` | `assertEquals("/ide/projekt/a.lua", mapper.toWire("/ide/projekt/a.lua"))` | Replace §3.2 step 1's `mapping.mapToRemote(localPath)` with a hand-rolled `if (localPath.startsWith(localRoot)) remoteRoot + localPath.removePrefix(localRoot) else localPath`. Compiles (both `String`). Reachable: `/ide/projekt` shares the *character* prefix `/ide/proj` but not the *segment*, so the mutant returns `/srv/appekt/a.lua`. |
| TC-05-03b | `-03` | `LuaPathMapper.identity(File("/ide/proj"))`; local path `/ide/proj/sub/a.lua` | `assertEquals("sub/a.lua", mapper.toWire(...))`; `assertEquals("/ide/proj/", mapper.baseDirArgument())`; `assertTrue(mapper.isIdentity())` | Drop §3.2 step 3 (the `removePrefix` of the remote root). Compiles. Reachable: the mutant returns `/ide/proj/sub/a.lua`, so the first assertion fails — this is the capability-A regression guard. |
| TC-05-03c | `-03` | `TestLuaDebugTarget`; `LuaRunConfiguration(myFixture.project, LuaRunConfigurationFactory(LuaRunConfigurationType()), "cfg")` left with **`workingDirectory == ""`** — the untouched default, pinned first with `assertEquals("", config.workingDirectory)` so the fixture cannot silently drift to `null`; `val basePath = assertNotNull(myFixture.project.basePath)`; then `LuaDebugTarget.of(config)` | `assertEquals(basePath, target.mapper.localRoot)`; `assertEquals("$basePath/", target.mapper.baseDirArgument())`; `assertTrue(target.mapper.isIdentity())` | Replace §2.3's `configuration.effectiveWorkDirectory()` with the shipped derivation `listOfNotNull(configuration.workingDirectory, configuration.project.basePath, "").first()` (`run/LuaDebuggerController.kt:80-90`). Compiles — both expressions feed `?.let(::File)` as `String?`/`String`, and `.first()` on a non-empty list is total. Reachable **because this fixture's working directory is empty, not unset**: `listOfNotNull` keeps `""`, so the mutant's root is `""`, `LuaPathMapper("", "")` is an empty `PathMapping`, `baseDirArgument()` returns `null` and the second assertion fails. TC-05-03b cannot catch it (it constructs the mapper directly and never touches the derivation). **`project.basePath` is non-null in this fixture, and the row is safe even if that were ever to change** — the fixture binds it with `assertNotNull`, which *fails the test* rather than yielding `null`, so this row can never degrade into a null-equals-null tautology no matter what the platform returns. The premise is separately evidenced: `TestLuaPosition.kt:21` and `:85` both do `File(myFixture.project.basePath!!)` from the same `BaseDocumentTest` base (`TestLuaPosition.kt:12`) and are green, so a null would already be crashing the shipped suite. (`TestLuaRunConfiguration.testEmptyWorkingDirectoryFallsBackToBasePath` (`:142-153`) is **not** evidence for this: it asserts `assertEquals(myFixture.project.basePath, config.effectiveWorkDirectory())`, whose two sides move together and stay green when both are null.) |
| TC-05-03d | `-03`, `-08` | `LuaPathMapper.identity(File("/"))` — the launched-session shape whose working directory is the filesystem root (§3.2's `/`-root note); no fixture, the mapper is pure | `assertEquals("/", mapper.baseDirArgument())`; `assertEquals("/sub/a.lua", mapper.toLocalPath("sub/a.lua"))`; `assertEquals("/home/u/a.lua", mapper.toWire("/home/u/a.lua"))`; `assertTrue(mapper.isIdentity())` | Replace `remoteRootPrefix` with `normalisedRemoteRoot` in **both** `joinRemote` and `baseDirArgument()` (§3.2) — i.e. drop the `.removeSuffix("/")`. Compiles: both are `String` and every use site is string concatenation. Reachable **because this fixture's root is `/`, the one root `trimSlash` returns slash-terminated** (`PathMappingSettings.java:289-291`): the mutant yields `"//"` and `"//sub/a.lua"`, so the first two assertions fail. Measured both ways in §0 Probe O. TC-05-03b cannot catch it — its `/ide/proj` root is trimmed, so mutant and correct code agree. |
| TC-05-07a | `-07` | `LuaPathMapper("/ide/proj", "/srv/app")`; local path `/ide/proj/sub/a.lua` | `assertEquals("sub/a.lua", mapper.toWire(...))` | Change §3.2 step 1 to `val remoteAbsolute = localPath` (skip the mapping), so `toWire` strips `remoteRoot` from a path that is under `localRoot`. Compiles — `remoteAbsolute` keeps its declaration and its type. (**Not** "delete step 1": that would leave `remoteAbsolute` undeclared for steps 2–4 and fail to compile, which is a mutation that asserts nothing.) Reachable: no prefix matches, so the mutant returns `/ide/proj/sub/a.lua`. TC-05-03b cannot catch this (there `localRoot == remoteRoot`, so step 1 is a no-op and the mutant stays green). |
| TC-05-07b | `-07` | `LuaPathMapper("/ide/proj", "/srv/app")`; local path `/elsewhere/x.lua` | `assertEquals("/elsewhere/x.lua", mapper.toWire(...))` | Replace §3.2 steps 2–4 with the shipped rule `FileUtil.getRelativePath(localRoot?.let(::File), File(localPath)) ?: localPath` (`run/LuaPosition.kt:43`). Compiles **with the `?.let`, which is not decoration**: `localRoot` is `String?` (§2.1), so `File(localRoot)` would not compile, while `getRelativePath`'s `base` parameter is an unannotated Java `File` (platform type `File!`, `FileUtil.java:100`) and accepts a `File?` — exactly as `run/LuaPosition.kt:43` already passes its nullable `workingDir`. Reachable: measured against GoLand 2026.1.3's `util-8.jar` on corretto-21, `getRelativePath(/ide/proj, /elsewhere/x.lua)` returns `../../elsewhere/x.lua` (and `sub/a.lua` for a path under the root), so the mutant yields the `..`-prefixed form Probe J1 shows cannot bind and the assertion fails. |
| TC-05-08a | `-08` | `LuaPathMapper("$scratch/ide", "/srv/app")`; a real file at `$scratch/ide/sub/a.lua`; `LuaFrameResolver(mapper)` | `assertEquals("$scratch/ide/sub/a.lua", resolver.resolve("sub/a.lua")?.path)` | Change §3.2 `toLocalPath` step 2 to return `wirePath` unchanged for relative inputs (drop the `joinRemote`). Compiles. Reachable: `findFileByPath("sub/a.lua")` is `null`, so the assertion fails on a null receiver. |
| TC-05-08b | `-08` | `LuaPathMapper("$scratch/ide", "/srv/app")`; a real file at `$scratch/ide/sub/a.lua`; wire path `/srv/app/sub/a.lua` (absolute) | `assertEquals("$scratch/ide/sub/a.lua", resolver.resolve("/srv/app/sub/a.lua")?.path)` | Invert `toLocalPath` step 2's test to `!File(wirePath).isAbsolute`, so every wire path is joined to the remote root. Compiles cleanly (an inversion, not an `if (false)` the compiler flags as unreachable). Reachable: the mutant builds `/srv/app//srv/app/sub/a.lua`, which does not resolve. TC-05-08a cannot catch it (its input is relative). |
| TC-05-08c | `-08`, [[BUG-463]] | `TestLuaFrameResolver`; a real file at `$scratch/ide/sub/a.lua` refreshed into the VFS (`LuaFileUtilTest.kt:30-33` idiom); `LuaPathMapper.identity(File("$scratch/ide"))`; `LuaPosition("sub/a.lua", 2).localPosition(mapper)` | `assertNotNull(position)`; `assertEquals("$scratch/ide/sub/a.lua", position.file.path)`; `assertEquals(1, position.line)` (0-indexed in the IDE — `createLocalPosition` subtracts one, `run/LuaPosition.kt:55`) | Restore the shipped body `findFileByPath(path)` in `localPosition` (`run/LuaPosition.kt:31-35`), ignoring the mapper. Compiles — the parameter may go unused. Reachable **because the fixture is nested under `$scratch/ide` rather than at `/`**: the mutant calls `findFileByPath("sub/a.lua")`, which returns `null`, `createLocalPosition` short-circuits at `:54` and `assertNotNull` fails. [[BUG-463]] §5 records the trap this row is built to avoid — *a fixture rooted at `/` stays green under this mutation*, so the fixture's depth **is** the test. **What this row does NOT cover.** It pins `LuaPosition.localPosition` itself, not the call site that reaches it: `run/LuaDebuggerController.kt:326` could be rewired to `localPosition(LuaPathMapper(null, null))` and this row would stay green while [[BUG-463]] stayed live in the pause path. The argument at that call site is therefore fixed **by name** in Phase 1 task 3, and the end-to-end behaviour is **HV-07** — it has no unit row because it needs a live pause from a real debuggee. |
| TC-05-09a | `-09` | `TestLuaFrameResolver`; a real file at `$scratch/proj/src/module/target.lua`; a stack chunk whose frame tuple is `{ nil, "src/module/target.lua", 0, 5, "main", "", "...ted_project_directory_name/src/module/target.lua" }` — field 7 verbatim from Probe I, i.e. `...`-truncated; identity mapper on `$scratch/proj` | `assertEquals("target.lua", stack.entries.first().frame.virtualFile?.name)` | In `LuaRemoteStackFrame`, resolve from `path` (index 6) instead of `file` (index 1) — the shipped code. Compiles (`path` and `file` are both `String`). Reachable: this fixture's field 7 begins with `...`, so `findFileByPath` returns `null` and the assertion fails. **This is the only row where field 6 and field 2 disagree**, which is why the existing 43-character fixtures in `TestLuaRemoteStackFrames.kt:15-19` are blind to it. |
| TC-05-09b | `-09`, `-08` | Same file. The fixture **creates a real file literally named `=[C]`** at `$scratch/proj/=[C]` (a legal POSIX filename) and refreshes it into the VFS, then parses the `=[C]` frame from `TestLuaRemoteStackFrames.kt:19` (`{ nil, "=[C]", -1, -1, "C", "", "[C]" }`) with an identity mapper on `$scratch/proj` | `assertNull(stack.entries.last().frame.virtualFile)`; `assertEquals("=[C]", stack.entries.last().frame.file)` | Delete §3.8 step 1's leading-`=` guard. Compiles (the guard is a plain `if (…) return null`). Reachable **because of the odd fixture file**: without the guard, `toLocalPath("=[C]")` yields `$scratch/proj/=[C]`, which now exists, `findFileByPath` returns it, and `assertNull` fails. The file exists for exactly this reason — with an ordinary fixture the mutant would resolve to `null` anyway and this row would assert nothing. |
| TC-05-04a | `-04` | `TestLuaAttachRunConfiguration`; `val env = LuaRunConfiguration.debuggerEnvironment("/plugins/lunar/lua", "/plugins/lunar/lua/debug.lua", LuaDebugTarget.fallback())` — two plain `String`s, **no VFS and no temp directory** (§2.6) | `assertEquals("127.0.0.1", env[LuaRunConfiguration.ENV_MOBDEBUG_HOST])`; `assertEquals("8172", env[LuaRunConfiguration.ENV_MOBDEBUG_PORT])`; `assertEquals("@/plugins/lunar/lua/debug.lua", env[LuaRunConfiguration.ENV_LUA_INIT])` | Delete the `MOBDEBUG_HOST` entry from `debuggerEnvironment` (§2.6). Compiles — the other four entries still build a valid `Map<String, String>`. Reachable: the map lookup returns `null` and the first assertion fails. (The `LUA_INIT` assertion has its own mutation: build the value from `pluginLuaPath` instead of `preloaderPath`, which compiles because both are `String` and yields `@/plugins/lunar/lua`.) |
| TC-05-05a | `-05` | `LuaDebugRunner()`; an attach configuration built from `LuaAttachRunConfigurationType().configurationFactories[0]` | `assertTrue(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, attachConfig))`; `assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, attachConfig))`; `assertTrue(attachConfig is RunConfigurationWithSuppressedDefaultRunAction)` | Drop the `\|\| runProfile is LuaAttachRunConfiguration` disjunct from `canRun` (§2.6). Compiles. Reachable: the first assertion flips. (The third assertion has its own mutation — remove the marker interface from the class's supertype list; it compiles because `is` against an unrelated interface is legal for a non-final class.) |
| TC-05-05b | `-05`, `-10` | `LuaAttachState(myFixture.project).execute(DefaultDebugExecutor.getDebugExecutorInstance(), LuaDebugRunner())` | `assertEquals(DefaultDebugProcessHandler::class, result.processHandler::class)`; `assertTrue(result.processHandler.detachIsDefault())`; `assertTrue(result.executionConsole is ConsoleView)` | Return `DefaultExecutionResult(console, NopProcessHandler())`. Compiles (both are `ProcessHandler`). Reachable: `NopProcessHandler.detachIsDefault()` is `false` (`platform/util/src/com/intellij/execution/process/NopProcessHandler.java:20-22`), so the second assertion fails and the first fails on the class. |
| TC-05-05c | `-05` | `LuaDebuggerController(fakeSession(project), CoroutineScope(SupervisorJob()))` — the fake of `TestLuaDebuggerEvaluator.kt:76-77`, whose `getRunProfile()` is `null` so the target is `LuaDebugTarget.fallback()` (`autoRestart == false`); a counting lambda registered with `controller.onDisconnect { … }`; then `controller.DebugObserver().onDisconnected()` called directly | `assertEquals(1, invocations)` | Delete the `disconnectListener?.invoke()` line from `onDisconnected` (§2.4). Compiles — the field is nullable and read nowhere else. Reachable: with auto-restart off this fixture takes the `close(); disconnectListener?.invoke()` path, which is the only route to that line, so the mutant leaves `invocations == 0`. |
| TC-05-06a | `-06` | `TestLuaDebuggerListener`; `openListener(target)` on port 0 with a loopback bind; `runBlocking { withTimeout(3_000) { val job = launch { awaitClient(server, 0L) }; delay(1_500); assertTrue(job.isActive); job.cancelAndJoin() } }` | The job is still active at 1500 ms and completes within 500 ms of `cancelAndJoin` | Change §3.3's `if (deadlineMs <= 0L)` to `if (deadlineMs < 0L)`. Compiles. Reachable: this fixture passes `0L`, so the mutant's deadline is `now`, the first `SocketTimeoutException` at ~500 ms is rethrown, and `job.isActive` is false at 1500 ms. |
| TC-05-06b | `-06` | Same seam; `val thrown = assertFailsWith<Throwable> { runBlocking { withTimeout(4_000) { awaitClient(server, 700L) } } }` | `assertEquals(SocketTimeoutException::class, thrown::class)` — the **exact** class, so a `TimeoutCancellationException` from the outer `withTimeout` does not pass | Delete the deadline test in the `catch` block, leaving `catch (_: SocketTimeoutException) { }`. Compiles. Reachable: the loop then never exits and `withTimeout` fails the test. TC-05-06a cannot catch this (its deadline is unbounded by design). |
| TC-05-12a | `-12` | `openListener(LuaDebugTarget.fallback().copy(port = 0))` | `assertTrue(server.inetAddress.isLoopbackAddress)`; `assertFalse(server.inetAddress.isAnyLocalAddress)` | Revert `openListener` to `ServerSocket(target.port)` (the shipped constructor at `run/LuaDebuggerController.kt:116`). Compiles. Reachable: measured `wildcard=true` (§0), so both assertions flip. |
| TC-05-12b | `-12` | An attach configuration with `bindHost = "0.0.0.0"`; `LuaDebugTarget.of(config)` | `assertTrue(target.bindAddress.isAnyLocalAddress)`; and with `bindHost = ""`, `assertTrue(target.bindAddress.isLoopbackAddress)` | Hard-code `InetAddress.getLoopbackAddress()` in `resolveBindAddress` (§3.1). Compiles. Reachable: the `0.0.0.0` half of this fixture's two-value table flips. |
| TC-05-12c | `-12` | An attach configuration with `bindHost = "a b"`, a `localRoot` that exists and a non-blank `remoteRoot`; `val thrown = assertFailsWith<Throwable> { config.checkConfiguration() }` — i.e. through `LuaTargetValidator.validate(LuaTargetSpec.of(config), LuaTargetChecks.attach(config))` (§2.5), because `LuaTargetChecks.attach` only **builds** the list and never throws | `assertEquals(RuntimeConfigurationError::class, thrown::class)`; `assertTrue(thrown.message.orEmpty().contains("a b"))` — **`orEmpty()`, not `!!`**: `Throwable.message` is `String?`, and the contract forbids `!!` (`docs/engineering-contract.md:27`); a null message would fail the `contains` assertion, which is the outcome wanted | Change check A2's severity `FATAL` → `WARNING`. Compiles (both are `LuaTargetSeverity` constants). Reachable: this fixture's only problem is A2, so at `WARNING` `validate` throws `RuntimeConfigurationWarning` instead and the exact-class assertion flips. **The fixture is hermetic**: measured, `InetAddress.getByName("a b")` throws `UnknownHostException` in **0.2 ms** without reaching a resolver (the space is rejected by `getaddrinfo` locally), where `no-such-host.invalid` took 24.7 ms and `999.999.999.999` took 91.3 ms — both real lookups, and both hostage to a wildcard resolver. See §4.4. |
| TC-05-10a | `-10` | `TestLuaDebugOutputFrames`; a loopback socket pair; `LuaDebugConnection` on the server side, `running == false` (no `Run`-group command has been sent); the test writes `204 Output stdout 7\n"side"\n200 OK 26\ndo local _={};return _;end` in that order while a `send(EXEC "print('side')")` is in flight | `assertEquals("do local _={};return _;end", result)`; the observer recorded exactly one `onOutput("stdout", "\"side\"\n")` | Move the §3.6b `Output` branch inside the existing `if (running)` block at `run/LuaDebugConnection.kt:296`. Compiles. Reachable: this fixture is paused, so `running` is `false`, the branch never fires, 7 payload bytes are left unread, `readLine` returns `"side"`, and `handleLine` throws `IOException` at `:269` — `send` then completes exceptionally and the first assertion fails. This is exactly the sequence Probe K measured. |
| TC-05-10b | `-10` | Same file; the test writes `204 Output stdout notanumber\n` | The observer recorded **no** `onOutput`; `send` still completes when the real response follows | Make the malformed-header guard emit before bailing out: `if (separator < 0 \|\| byteCount == null) { observer.onOutput(data, ""); return }`. Compiles — both arguments are `String`, and the `return` **must stay**: dropping it leaves `readExactly(input, byteCount)` reading an `Int?` and the file would not compile, which is a mutation that asserts nothing. (For the same reason the mutant cannot name `stream`: §3.6b declares `val stream` *after* the guard.) Reachable: this fixture's header is unparsable, so the mutant emits a spurious output frame and the "no `onOutput`" assertion fails. |
| TC-05-10c | `-10` | `TestLuaDebugHarness`; a real debuggee launched through `LuaHarnessSpec`; `connection.send(DebugCommand(DebugCommandKind.OUTPUT, listOf(LuaDebuggerController.OUTPUT_STREAM, LuaDebuggerController.OUTPUT_MODE_REDIRECT)))` — **the production constants of §3.7, never the literals `"stdout"`/`"r"`** — then `SETB`/`RUN` on a script whose second line is `print("hi")` | `send` completes normally (no `DebuggerError`); the observer records `onOutput("stdout", "\"hi\"\n")` within 4 s | Change `OUTPUT_STREAM` from `"stdout"` to `"stderr"` (§3.6d). Compiles (both `String`). Reachable **only because the fixture reads the constant instead of typing its value** — with a literal, mutant and correct code send identical bytes and the row is decoration. With the constant: mobdebug's `OUTPUT` branch requires `stream == "stdout"` (`src/main/lua/mobdebug/init.lua:1011-1013`) and answers `400 Bad Request` otherwise (measured, §0 Probe F). `BadRequest` **is** a declared response for `OUTPUT` (§3.6a), so `handleLine` takes Case A and completes the deferred *exceptionally* with `DebuggerError` (`run/LuaDebugConnection.kt:287-289`) — `send` **throws**, failing the first assertion; and no `204 Output` frame is ever emitted, failing the second. (An earlier draft of this row claimed `send` returns `""` on `BadRequest`; `:287-289` says otherwise.) Not green on `main`: `DebugCommandKind` declares no `OUTPUT` there (`:52-156`) and `handleLine` has no `Output` branch, so the fixture does not compile, let alone pass. |
| TC-05-11a | `-11` | `TestLuaDebugHarness`; a 3-line script whose line 2 is `print("hit")`; `SETB <name> 2`, then `connection.send(DebugCommand(DebugCommandKind.DELB, listOf(LuaDebuggerController.DELB_ALL_FILES, LuaDebuggerController.DELB_ALL_LINES)))` — **the production constants of §3.7, never the literals `"*"`/`"0"`** — then `RUN` | No pause frame within 4 s; the observer's `onDisconnected` fires; the child's stdout is `hit` and it exits 0 | Change `DELB_ALL_LINES` from `"0"` to `"1"` (§3.7 step 2). Compiles (both `String`). Reachable **only because the fixture sends the constants**: measured end to end (§0, **Probe G2**), `DELB * 1` leaves the line-2 breakpoint installed — `remove_breakpoint`'s wildcard branch requires `line == 0` exactly (`src/main/lua/mobdebug/init.lua:360-365`) — so `202 Paused script.lua 2` arrives after `RUN`, the "no pause" assertion fails and the child never reaches its `print`. **What this row is, and what it deliberately is not.** It is the *integration* half of `-11`: it proves the constant **values** Lunar sends are the ones mobdebug honours, which TC-05-11b cannot (a recording fake accepts any bytes). It asserts nothing about `DELB` argument arity — that is already legal on `main` (`minArgs = 2, maxArgs = 2`, `run/LuaDebugConnection.kt:71-75`), which is why the previous form of this row (literal `DELB * 0` on the harness connection) **passed on unmodified `main`** and asserted mobdebug's protocol rather than Lunar's code. With the constants referenced it does not even compile on `main` — `LuaDebuggerController.DELB_ALL_FILES` arrives with Phase 4 — and once Phase 4 lands it is red under the named mutation. |
| TC-05-11b | `-10`, `-11`, `-03` | **The handshake itself, over a real socket.** Appended to `TestLuaDebuggerListener.kt` in Phase 4 (it already carries the copied `fakeSession` and the `Proxy` console idiom). Borrow a port with `ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }` and close it (§0 Probe P: rebinding it takes 0.05 ms); an **attach** configuration constructed as in TC-05-12b but with `bindHost = ""` (so `resolveBindAddress` yields the loopback the fake dials, §3.1), that `debugPort`, and `localRoot = remoteRoot = "/srv/app"` (identity, so §3.5's `detectRootMismatch` returns at its step 1 and the transcript is exactly three commands), `redirectOutput = true`, `listenTimeoutSeconds = 5`, and `autoRestart = false` — pinned explicitly so the row is unaffected when Phase 5 puts an auto-restart branch in `onDisconnected`; a `fakeSession` whose `getRunProfile()` returns it, so `init` resolves `LuaDebugTarget.of(attach)` — which pins `clearRemoteBreakpoints = true` (§2.3); a `java.lang.reflect.Proxy` `ConsoleView` installed with `setConsole(...)` (the `Proxy` idiom of `redis/debug/TestLuaLdbController.kt:97-101`) so `connect()`'s `printToConsole` does not hit `log.error("Console not set")` (`run/LuaDebuggerController.kt:99`); a daemon thread that retry-connects to the port (20 × 100 ms) and then three times does `DbgpFraming.readLine(input)` → record → `DbgpFraming.writeLine(output, "200 OK")` (`run/DbgpFraming.kt:26`, `:58`); then `runBlocking { withTimeout(20_000) { controller.connect() } }` — **20 s, deliberately four times `listenTimeoutSeconds`**, so that on a slow or loaded machine the *inner* accept deadline is what expires and `connect()` fails with its own diagnostic, rather than the outer guard cutting in at the same instant and reporting an opaque `TimeoutCancellationException`; the outer guard exists only to stop a wedged run hanging the suite — `thread.join(2_000)` (bounded, and the thread is a daemon, so a mutation that sends **fewer** commands leaves it blocked in `readLine` without hanging the suite — the assertion still runs, 2 s later, and fails), and `controller.close()` in a `finally` | `assertEquals(listOf("BASEDIR /srv/app/", "DELB * 0", "OUTPUT stdout r"), recorded)` — **one assertion covering contents and order** | Delete the `clearRemoteBreakpoints()` call from `handshake()` (§3.7 step 2). Compiles: the function survives as an unused `private suspend fun`, which Kotlin reports as a warning and this build does not escalate (`build.gradle.kts` sets no `allWarningsAsErrors`). Reachable: this fixture's target is an attach target, so `target.clearRemoteBreakpoints` is `true` and the call is on the executed path — the mutant records two entries and the list assertion fails. **Four further mutations fail this same row**, which is why it is written as one ordered list rather than three membership checks: `DELB_ALL_LINES "0"→"1"` (records `DELB * 1`), `OUTPUT_STREAM "stdout"→"stderr"` (records `OUTPUT stderr r`), swapping the `clearRemoteBreakpoints()`/`redirectOutput()` calls (order flips), and moving `setBaseDir()` below them (§3.7's step 1 is load-bearing — `BASEDIR` resets `lastsource`). **Not green on `main`**: `connect()` there sends `setBaseDir()` and nothing else (`run/LuaDebuggerController.kt:130`), so the recorded list has one entry. This is the row that covers Phase 4's fourth task — `redirectOutput()`, `clearRemoteBreakpoints()`, the four constants and their `handshake()` invocation in §3.7 order — none of which any harness row can reach, because `LuaDebugHarness.kt:52` builds its own `LuaDebugConnection` and never constructs a `LuaDebuggerController`. |
| TC-05-07c | `-07` | `LuaRootMismatch.detect` over a three-row table: `("/srv/app/main.lua", "/ide/proj")`, `("main.lua", "/ide/proj")`, `("=[C]", "/ide/proj")` | Row 1 returns a non-null message containing both `/srv/app/main.lua` and `/ide/proj`; rows 2 and 3 return `null` | Change §3.5 step 3's `!File(entryWireFile).isAbsolute` test to `entryWireFile.isEmpty()`. Compiles. Reachable: row 2 (`main.lua`) then returns a message and its `assertNull` fails, while row 1 stays green — which is why all three rows live in one table. |
| TC-05-07d | `-07`, `-08` | **End-to-end, differing roots.** `LuaHarnessSpec(script = $scratch/remote/sub/a.lua, workingDirectory = $scratch/remote, port = <free>)`; an identical copy at `$scratch/ide/sub/a.lua`; the test drives `BASEDIR $scratch/remote/`, `SETB sub/a.lua 2`, `RUN` and maps the pause back with `LuaPathMapper("$scratch/ide", "$scratch/remote")` | A pause is observed within 4 s at line 2; `mapper.toLocalPath(pausedPos.path)` equals `$scratch/ide/sub/a.lua` | Send the **local** root as `BASEDIR` (i.e. `baseDirArgument()` returns `localRoot + "/"`, the shipped behaviour at `run/LuaDebuggerController.kt:87-88`). Compiles. Reachable: Probe B measured that the debuggee then never pauses, so the latch times out. **This is the capability-B end-to-end case the existing suite has never had**; `TestLuaDebugHarness.testBreakpointAndExec` launches from the script's own directory and is green under this mutation. |
| TC-05-07e | `-04`, `-05`, `-12` (the UI halves) | `TestLuaAttachRunConfiguration`; the seven **control-label** keys of §2.7 (`debug.attach.bindHost`, `.port`, `.timeout`, `.localRoot`, `.remoteRoot`, `.redirectOutput`, `.autoRestart`) read through `LuaBundle.message(key)`. `debug.attach.type.name`/`.type.description` are excluded by name — a configuration-type display name follows the platform's Title Case, like *Go Build* | **The `&` mnemonic marker is stripped first** (`value.replace("&", "")`), then, for each of the seven: the first word is capitalised and no later word starts with an upper-case letter unless it is a product name (`LuaRocks`, `StyLua`, `Mobdebug`); no value contains `'('`; each of the five leading-label keys ends in `':'`; the two checkbox keys do **not** | Change `debug.attach.remoteRoot` in `LuaBundle.properties` to `&Remote Source Root:`. Reachable: after the `&` strip the scan fails on `Source`. Two further mutations fail it — drop the trailing colon from `debug.attach.port` (colon assertion), and restore `Listen &timeout (seconds):` (the `'('` assertion, which is the §6 rule at `docs/engineering-contract.md:147-148`). All three are `.properties` edits, so all three "compile" trivially. **Why this is not a duplicate of `LuaBundleCasingTest`** (`src/test/kotlin/net/internetisalie/lunar/LuaBundleCasingTest.kt:42-55`): that test's `CAPITALIZED_WORD = ^[A-Z][a-z]+$` must match *every* generic word, and the trailing colon on `Root:` — plus the leading `&` on `&Remote` — never match it, so `&Remote Source Root:` passes there unflagged. Verified by inspection of `LuaBundleCasingTest.kt:7`, `:57-60`. |
| TC-05-07f | `-04`, `-05`, `-12` (the UI halves) | **The built editor, not the bundle.** Extend the existing `src/test/kotlin/net/internetisalie/lunar/ui/RunConfigurationEditorTextTest.kt`: add `"Lua Remote" to register(LuaAttachSettingsEditor(project))` to its hard-coded `editors()` list (`:140-146`), raise `AUDITED_ROW_COUNT` 27 → **32** (`:159`) for the editor's five labelled rows, and widen `test every checkbox carries a mnemonic` (`:123-127`) from `checkBoxesOf(redisEditor())` to `editors().flatMap { (_, editor) -> checkBoxesOf(editor) }` — safe, because Redis and this editor are the only two with checkboxes (`grep -rn "JBCheckBox" src/main/kotlin/net/internetisalie/lunar/run/ …/rocks/run/` → no hits) | The six file-wide assertions now also cover the attach editor: every leading label ends in `':'`; every leading label has `displayedMnemonic != 0`; no label contains `U+001B`; no label contains `'('`; mnemonics are unique within the editor; the total labelled-row count is exactly 32. Both attach checkboxes have `mnemonic != 0` | Drop the `&` from `debug.attach.localRoot` in `LuaBundle.properties` → `displayedMnemonic == 0` → `test every labelled row carries a mnemonic` is red. Three further mutations fail this same file: move the marker to `Local source roo&t:` (collides with `Listen &timeout:`'s `T` → the uniqueness test is red); swap one `addMnemonicLabeledComponent` call for `FormBuilder`'s own `addLabeledComponent(String, JComponent)` (compiles — same receiver, same return type — and leaks `U+001B` into the label, per `LuaFormBuilders.kt:17-23`); delete one row from the builder chain (row count 31 ≠ 32). **Not green on `main`**: `LuaAttachSettingsEditor` does not exist there, so the edited `editors()` does not compile until Phase 3 lands. |
| TC-05-13a | `-13` | `TestLuaDebuggerListener`; a `fakeSession` (the `TestLuaDebuggerEvaluator.kt:76-77` anonymous `XDebugSession`) whose `getRunProfile()` returns an **attach** configuration with `autoRestart = true` and `debugPort = 0`, so the target is `LuaDebugTarget.of(attach)`; a `java.lang.reflect.Proxy` `ConsoleView` recording `print(String, ConsoleViewContentType)` installed with `controller.setConsole(...)`; `var invocations = 0` registered via `controller.onDisconnect { invocations++ }`; then `controller.DebugObserver().onDisconnected()` called directly; finally `scope.cancel()` in a `finally` so the relaunched `connect()` does not outlive the test | `assertEquals(0, invocations)` — the terminate path was **not** taken; `assertFalse(controller.isReady)`; the proxy recorded one `print` whose text contains `"still listening"` | Delete the `if (target.autoRestart) { restartListener(); return }` line from `onDisconnected` (§3.9 step 1). Compiles — the remaining body is the shipped `close()` plus the §2.4 callback. Reachable **because this fixture's target has `autoRestart == true`**, which is the only input that selects the deleted branch: the mutant falls through to `close(); disconnectListener?.invoke()`, so `invocations` becomes 1 and no `"still listening"` line is ever printed. TC-05-05c cannot catch it (its target is `fallback()`, `autoRestart == false`). |
| TC-05-13b | `-13` | Same file; a controller built over the same fake attach session (`debugPort = 0`); `val first = controller.listener(); val second = controller.listener()` — the §3.9 step 3 seam, no client and no coroutine | `assertSame(first, second)`; `assertFalse(first.isClosed)`; `assertTrue(first.inetAddress.isLoopbackAddress)` | Drop the `serverSocket ?:` guard from `listener()`, leaving `openListener(target).also { serverSocket = it }`. Compiles (same return type). Reachable: with `port = 0` the mutant binds a **second** ephemeral socket, so `assertSame` fails. This is the assertion that keeps auto-restart re-entering the *same* listener; the raw "a `ServerSocket` survives a client disconnect" property is a JDK fact already measured in §0 (`PollProbe`) and is deliberately **not** re-asserted as a Lunar test. |

**Rows with no test, and why.** `-01` and `-02` are structurally asserted by TC-05-03c, TC-05-07d
and the existing `TestLuaRunConfiguration.testDebugPortRoundTripsThroughEditor`. `-14` is a statement about
another epic and adds no code. `-15` and `-16` are `Won't`. §3.4's `rejectForeignPeer` has no test
because the branch is **unreachable on Linux** — a loopback-bound listener never sees a
non-loopback peer (measured, §0) — and a test that cannot reach its own subject is the decoration
this table exists to prevent; the invariant is asserted indirectly by TC-05-12a.

## 9b. Contract Conformance Audit

`docs/engineering-contract.md` is binding, and two of its clauses are the ones this repo's reviews
repeatedly catch. Both are audited here so a reviewer does not have to redo it.

**Parameter cap (§3, "Max 3 arguments per function, excluding `Project` or `Disposable`").** Every
*function* introduced or changed above, with its count:

| Function | Args | Function | Args |
| :-- | :-: | :-- | :-: |
| `LuaPathMapper(localRoot, remoteRoot)` | 2 | `LuaFrameResolver(mapper)` | 1 |
| `toWire(localPath)` | 1 | `resolve(wireFile)` | 1 |
| `toLocalPath(wirePath)` | 1 | `LuaFrameResolver.identity(workingDirectory)` | 1 |
| `baseDirArgument()` / `isIdentity()` | 0 | `LuaRootMismatch.detect(entryWireFile, remoteRoot)` | 2 |
| `LuaPathMapper.identity(workingDirectory)` | 1 | `openListener(target)` | 1 |
| `LuaDebugTarget.of(configuration)` | 1 | `awaitClient(server, deadlineMs)` | 2 |
| `LuaDebugTarget.fallback()` / `debuggeeHost()` | 0 | `rejectForeignPeer(clientSocket)` | 1 |
| `resolveBindAddress(host)` | 1 | `handshake()` / `clearRemoteBreakpoints()` / `redirectOutput()` / `detectRootMismatch()` | 0 |
| `LuaDebugConnection` `onOutput(stream, text)` | 2 | `LuaDebuggerController.onDisconnect(listener)` | 1 |
| `debuggerEnvironment(pluginLuaPath, preloaderPath, target)` | **3** | `LuaAttachState(targetProject)` | 1 (a `Project`, excluded) |
| `LuaDebuggerController.restartListener()` / `listener()` | 0 | `LuaPathMapper.joinRemote(wirePath)` | 1 |
| `LuaAttachState.execute(executor, runner)` | 2 | `LuaTargetChecks.attach(configuration)` | 1 |
| `remoteRootUnset(configuration)` / `bindHostUnresolvable(configuration)` | 1 | `LuaRemoteStack.create(project, text, resolver)` | 2 (`Project` excluded) |
| `LuaRemoteStackEntry(table, resolver)` | 2 | `LuaRemoteStackFrame(table, resolver)` | 2 |
| `LuaRemoteStack(stack, resolver = …)` | 2 | `LuaRemoteStack.create(project, text, resolver)` | 2 (`Project` excluded) |
| `createRemotePosition(xSourcePosition, mapper)` | 2 | `localPosition(mapper)` | 1 |
| `startLuaDebugHarness(spec, observer)` | 2 | `treeWith(root, relative, text)` (test helper, §9) | **3** |
| `LuaAttachSettingsEditor(project)` | 0 (a `Project`, excluded) | `createEditor()` | 0 |
| `resetEditorFrom(runConfiguration)` | 1 | `applyEditorTo(runConfiguration)` | 1 |

`treeWith` also sits **at** the cap and is a *private test helper* — the contract's §3 cap is written
for every function, private helpers included (the recurring finding this audit exists to pre-empt), so
it is listed rather than omitted. `debuggerEnvironment` sits **at** the cap, not over it, and the third parameter is what removes the
`!!` the contract forbids: both plugin paths are resolved (and their failures thrown on) by the
existing caller at `run/LuaRunConfiguration.kt:322-327`, so the helper never touches the VFS. §2.6
shows the call site verbatim.

**Two data carriers exceed three fields and are the contract's own remedy, not exceptions to it.**
`LuaDebugTarget` (7) and `LuaHarnessSpec` (4) are the *"dedicated configuration or execution context
class"* §3 prescribes for exactly this case; DEBUG-06 design §2.1 makes the identical argument for
its 6-field `LuaTargetSpec`. Neither carries behaviour.

**Threading (§1, §2).** No new work runs on the EDT. `openListener` and `awaitClient` are called
from the session coroutine and dispatch the blocking calls with `withContext(Dispatchers.IO)`, as
`connect()` already does (`run/LuaDebuggerController.kt:115`, `:120`). `awaitClient` calls
`coroutineContext.ensureActive()` at the head of every iteration — the literal
`CANCELLATION EXHAUSTIVENESS` requirement (`docs/engineering-contract.md:39`).
`LuaFrameResolver.resolve` touches the VFS and is reached only from inside the existing
`runReadAction` at `run/LuaDebuggerController.kt:292-294`; `detectRootMismatch` wraps its parse in
`readAction`/`runReadAction` for the same reason. Nothing writes PSI, so no
`WriteCommandAction` appears. `LuaAttachState.execute` runs on whatever thread the platform calls
it on and does only object construction.

**Heavy-object retention (§4).** `LuaPathMapper` and `LuaDebugTarget` hold only `String`s,
primitives and an `InetAddress`. `LuaFrameResolver` holds `VirtualFile` values for the lifetime of
one `LuaRemoteStack` — the same lifetime as the `MutableMap<String, VirtualFile?>` it replaces
(`run/LuaRemoteStack.kt:14`) — and is not a service. No new class holds a `Project`, `Editor` or
`PsiFile` in a field; `LuaAttachState`'s `Project` lives for one `execute` call, matching
`LuaRunConfiguration`'s existing anonymous `CommandLineState` (`:292`).

**Other clauses.** No `!!` is introduced (§1 NULL SAFETY — the one place a preloader path could
have needed it is hoisted into the caller, which already throws `ExecutionException` there today,
§2.6). No wildcard imports. Every new function is under
30 logic lines, with the two largest (`connect`, `handshake`) split into named helpers. The single
new extension is registered declaratively in `plugin.xml` (§7); the one manual listener
(`onDisconnect`) is an intra-object callback of the kind `LuaDebugProcess` already installs at
`run/LuaDebugProcess.kt:100`, not a platform extension.

## 10. Alternatives Considered

### 10.1 Full `PathMappingSettings` with N mappings and a table editor

The platform's own remote debuggers carry an unbounded list of `(localRoot, remoteRoot)` pairs.
**Rejected**: mobdebug carries exactly one `basedir` (`src/main/lua/mobdebug/init.lua:129`,
`:965-971`), so a second mapping could only ever be honoured on the IDE side of the round trip —
breakpoints for files under mapping 2 would be sent as absolute remote paths and would bind by
luck, while frames from mapping 2 would resolve. That asymmetry is worse than the limitation. The
design still uses `PathMappingSettings.PathMapping` as its prefix engine, so growing to N is
additive: `LuaPathMapper` becomes a `List<PathMapping>` and `baseDirArgument()` picks the first.

### 10.2 Send no `BASEDIR` and translate absolute paths entirely in the IDE

Attractive because `PathMappingSettings.convertToLocal/convertToRemote` then apply directly.
**Rejected** on measured grounds: Probe E case B2 shows a debuggee launched with a relative script
path reports **relative** chunk names regardless, so "the wire is absolute" is not a property the
IDE can rely on; the design would need the relative-join fallback anyway, and would additionally
give up the wire compatibility that keeps capability A working through the same `BASEDIR`
mechanism it uses today (the base directory *value* changes for a default configuration — §2.3,
Risk 1.8 — but the protocol shape does not).

### 10.3 Infer the remote root from the first frame (EmmyLua `recognizeBaseDir`)

**Rejected**: an inferred root that is wrong yields a session silently attached to the wrong copy
of a file, which is strictly worse than the silent non-binding it replaces. §3.5 reports the
debuggee's own path instead and lets the user set the field. Recorded as future work.

### 10.4 `runInterruptible { server.accept() }` for the cancellable wait

**Rejected on measured grounds**: `Thread.interrupt()` does not release a thread blocked in
`ServerSocket.accept()` on corretto-21 — after 2000 ms the thread was still alive and the socket
was still open (§0, `IntrProbe`). Only `close()` releases it.

### 10.5 `getState` returning `EmptyRunProfileState.INSTANCE`, with the runner branching

This is what `org.jetbrains.debugger.RemoteDebugConfiguration:87-89` does, and it is what
EmmyLua's runner does. **Rejected**: `state.execute` then returns `null` and
`LuaDebugRunner.doExecute` bails at `run/LuaDebugRunner.kt:73`, forcing a branch in the runner and
a second construction path for the console and handler. §2.6's real `RunProfileState` leaves
`LuaDebugRunner.doExecute` and all of `LuaDebugProcess` untouched.

### 10.6 Fix `DEBUG-05-09` as a standalone bug report

**Rejected — with the reasoning stated because the requirements ask for it.** `-09`'s entire fix is
"resolve from frame field 2 instead of field 7", which is the *same line* `-08` must change
(`run/LuaRemoteStack.kt:85`, and `:41-47`). A separate bug would either land first and be rewritten
by `-08` a week later, or land second onto a conflicting edit — two tests and two reviews for one
one-line change. It is instead scheduled as **Phase 1**, which depends on nothing else in this
feature and ships alone, so `-09` does not wait on the attach work. The residual risk — that `-09`
is now hostage to DEBUG-05 being scheduled at all — is [risks-and-gaps.md](risks-and-gaps.md)
Risk 1.4.

### 10.7 Bind the wildcard address and filter peers in `rejectForeignPeer`

**Rejected**: a userspace filter still accepts the TCP connection and still reads from it before
deciding. Refusing at the kernel is measurably stronger (§0: the LAN connect was refused with
`ConnectException`, so the peer never reached the JVM) and is one constructor argument, exactly as
[[BUG-456]] §3 says.

## 11. Open Questions

_None — every deferral is a tracked de-risking task in [risks-and-gaps.md](risks-and-gaps.md)._
