---
id: "DEBUG-05-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "DEBUG-05"
folders:
  - "[[features/debug/05-remote-debugging/requirements|requirements]]"
---

# DEBUG-05: Risks & Gaps

## Critical Risks

### Risk 1.1: Phase 3 cannot compile until [[DEBUG-06]] is implemented

- **Impact**: `LuaAttachRunConfiguration.checkConfiguration()` calls `LuaTargetValidator.validate`
  and `LuaTargetChecks.attach(this)` — types that exist only in DEBUG-06's design (§2.1–§2.5), which
  is `planned`, not merged. Phase 3 does not build until DEBUG-06 Phases 1–2 land.
- **Likelihood**: high — DEBUG-06 was planned immediately before this feature and nothing has been
  implemented from it.
- **Mitigation**: the plan isolates the dependency. **Phases 1, 2, 4 and 5 touch nothing DEBUG-06
  introduces**, and Phase 1 — which carries the `DEBUG-05-09` frame-path defect — depends on
  nothing at all. If
  DEBUG-06 slips, ship Phases 1–2 and hold Phase 3. Do **not** work around it by hand-rolling a
  `checkConfiguration()` in the attach configuration: that would create the third near-duplicate
  not-configured message DEBUG-06 `-22` exists to remove.

### Risk 1.2: `toWire` changes shipped behaviour for a breakpoint outside the working directory

- **Impact**: today `LuaPosition.createRemotePosition` sends
  `FileUtil.getRelativePath(workingDir, target) ?: target.path` (`run/LuaPosition.kt:43`), which for
  a file outside the working directory produces `../other/main.lua`. Design §3.2 step 4 sends the
  **absolute** path instead. Any user or test depending on the `../` form changes behaviour.
- **Likelihood**: medium — the `../` case is reachable whenever the script and the working directory
  diverge, which DEBUG-06 `-11` records as an ordinary misconfiguration.
- **Mitigation, and why it is a fix rather than a regression**: measured. mobdebug's `debug_hook`
  rewrites a `^%.%./` chunk name to `basedir.."../…"` and then normalises it
  (`src/main/lua/mobdebug/init.lua:648-649`), so a `../`-prefixed `SETB` can never match; Probe J1
  (design §0) shows an **absolute** `SETB` outside the basedir does bind and does pause. No existing
  test asserts the `../` form — `TestLuaPosition.testCreateRemotePosition` asserts only
  `endsWith(virtualFile.name)` (`src/test/kotlin/.../TestLuaPosition.kt:32-35`), which an absolute
  path also satisfies, and `testCreateRemotePositionFallsBackToAbsolutePath` (`:56-67`) already
  expects an absolute path for a null root. TC-05-07b pins the new behaviour with the old rule as
  its named mutation.

### Risk 1.3: [[BUG-450]] edits the same lines as Phase 1

- **Impact**: BUG-450 §3b names `LuaRemoteStackEntry`'s `init` block and its per-frame
  `LocalFileSystem.findFileByPath` (`run/LuaRemoteStack.kt:41-47`) as half of the eager-realization
  defect. Phase 1 task 1.4 **deletes that block**. Two open changes to the same six lines.
- **Likelihood**: medium — BUG-450 is `todo` and gates DEBUG-07.
- **Mitigation**: the two changes agree in direction, so whichever lands second adapts rather than
  reverts. The binding constraint is stated once, here: **frame resolution must remain lazy.**
  Design §3.8 makes `virtualFile` a `by lazy` on `LuaRemoteStackFrame` and caches negative results,
  which is strictly what BUG-450 asks for; a later BUG-450 fix must not reintroduce an eager `init`.
  BUG-450's other half — bounding the `STACK` payload with `maxlevel` — is untouched here except
  that design §0 Probe M supplies it with a **measured correction**: `maxlevel` does work
  (10102 → 1348 bytes at `maxlevel=1`) but `maxnum` truncates the frame tuple itself and must not be
  used, which BUG-450 §3a does not distinguish.

### Risk 1.4: `DEBUG-05-09` is now hostage to DEBUG-05 being scheduled

- **Impact**: `-09` is a live defect in **shipped local debugging** — any project whose script's
  absolute path reaches 60 characters gets `<internal C>` frames with no navigation. Folding it into
  a feature about remote attach could delay it indefinitely.
- **Likelihood**: medium.
- **Mitigation**: it is **Phase 1**, the first phase, which depends on nothing else in this feature
  and on nothing in DEBUG-06, and which is independently shippable. Design §10.6 records why a
  separate bug report was rejected: `-09`'s entire fix is the same one-line change (`file` instead
  of `path`) that `-08` must make, so a separate bug would be rewritten or conflict. **If this
  feature is deprioritised, split Phase 1 out as its own change rather than filing a duplicate bug.**

### Risk 1.5: the root-mismatch probe adds a round trip and a PSI parse to every attach connect

- **Impact**: `detectRootMismatch()` (design §3.5) sends `STACK -- {maxlevel=1}` and parses the
  1348-byte response through `LuaDebugValueParser.parseChunk`, which builds a PSI file. That is
  real work at the moment the user is waiting for the session to come up.
- **Likelihood**: low — measured at 1348 bytes versus the 10102-byte `STACK` that every pause
  already sends (design §0, Probe M), and it happens once per session.
- **Mitigation**: `detectRootMismatch()` returns before sending anything when
  `target.mapper.remoteRoot.isNullOrBlank() || target.mapper.isIdentity()` (design §3.7 step 1 —
  **both** disjuncts, since `isIdentity()` is `false` for an unrooted mapper such as
  `LuaDebugTarget.fallback()`'s `LuaPathMapper(null, null)`). Every session with one root — the
  overwhelmingly common case, and the only one that exists today — therefore pays nothing. If the parse ever shows up in a profile, the fallback is a `Regex` over the first frame
  tuple; that is deliberately **not** done now because it would add a second, untested parser for a
  format `LuaRemoteStack` already reads.

### Risk 1.6: the console shows Lua-serialised output, not the debuggee's literal `print` text

- **Impact**: measured (design §0, Probe F): `print("out:" .. b)` arrives on the wire as
  `"out:42"\n` — **quoted**, because mobdebug pipes each argument through
  `mobdebug.line(…, {nocode = true, comment = false})` (`src/main/lua/mobdebug/init.lua:1023-1026`).
  An attached session's console therefore does not look byte-identical to the same program run
  locally.
- **Likelihood**: certain — this is the protocol, not a defect.
- **Mitigation**: accepted, and design §3.6 states it rather than hiding it. Unquoting a single
  string argument would be lossy the moment a program prints a table or a number, and a
  "sometimes unquote" rule is exactly the kind of invented heuristic the planning bar forbids. The
  behaviour is called out as **HV-03** in the plan's verification tasks so a reviewer does not file
  it as a bug.

### Risk 1.7: `LuaAttachSettingsEditor` is a new hand-assembled UI surface

- **Impact**: `docs/engineering-contract.md:102-166` binds new UI, and its own audit ([[BUG-448]],
  [[BUG-449]]) found that *"every surface we hand-assembled had drifted"*. Unit tests cannot observe
  alignment, casing, elision or a re-parented component.
- **Likelihood**: medium.
- **Mitigation**: design §2.7 fixes the two things the contract makes non-negotiable up front —
  sentence case and a colon on every leading label — and the plan makes `verify-in-ide` a required
  verification task after Phase 3, not an optional one. `FormBuilder` is reused deliberately: the
  contract's §6 SCOPE clause exempts the *surviving* `FormBuilder` run-config editors, and a new
  editor that looks unlike the four beside it would be a worse outcome than one that matches them.
  That choice is recorded here rather than assumed.

### Risk 1.8: the base directory of a **default** local session changes, and it is the common case

- **Impact**: `LuaDebugTarget.of(LuaRunConfiguration)` (design §2.3) derives the mapper root from
  `configuration.effectiveWorkDirectory()` (`run/LuaRunConfiguration.kt:228`). The shipped controller
  uses `listOfNotNull(configuration.workingDirectory, session.project.basePath, "").first()`
  (`run/LuaDebuggerController.kt:80-90`). `myWorkingDirectory` is `string("")` (`:78-82`), so an
  untouched configuration holds the **empty string, not null**, `listOfNotNull` keeps it, and today's
  default session sends **`BASEDIR /`** with `SETB home/u/proj/a.lua`. Under this design it sends
  `BASEDIR /home/u/proj/` with `SETB a.lua`. **This is the configuration most users have**, and an
  earlier draft of the design wrongly asserted the wire bytes were identical.
- **Likelihood**: certain, for every launched session whose working-directory field is empty.
- **Mitigation, and why the old rule is not kept**: three measurements, design §0 Probe N.
  1. The shipped rule is **not expressible** through `PathMappingSettings.PathMapping`:
     `PathMapping("/", "/").canReplaceLocal("/home/u/proj/a.lua")` is `false`, because
     `canReplaceLocal` requires a `/` at the prefix boundary and there is none at depth 1
     (`PathMappingSettings.java:259-266`). Reproducing it means the hand-rolled `startsWith`
     design §2.1 forbids.
  2. The hand-rolled result would not even work: with `BASEDIR /`, an **absolute** `SETB` does
     **not** bind (Probe N2 — no pause, the debuggee ran to completion), because `debug_hook`
     strips `^basedir` from the chunk name (`src/main/lua/mobdebug/init.lua:657`) while
     `set_breakpoint` stores the argument verbatim (`:353-358`).
  3. The old wire path is itself the defect. `BASEDIR /` makes every pause report a *relative* path
     (`home/u/proj/a.lua`), which is precisely what `LocalFileSystem.findFileByPath` cannot take —
     the mechanism of [[BUG-463]]. Aligning `BASEDIR` with `effectiveWorkDirectory()` also aligns it
     with the child's actual process working directory (`run/LuaRunConfiguration.kt:314-315`), which
     is what `basedir` is for. Probe N3 measures it binding.
  A third shape exists and is also declared: an empty working directory **and** a null
  `project.basePath` yields `baseDirArgument() == null`, so **no `BASEDIR` is sent** where today `/`
  is. Probe N4 measures that binding too, with absolute wire paths that resolve directly.
  **TC-05-03c pins the change**, with a fixture whose working directory is empty and whose mutation
  is the shipped derivation; design §8's `DEBUG-05-03` row and §6's edge-case table both carry the
  note so it cannot be read as an accident.

## Design Gaps

### Gap 2.1: `LuaTargetMessages` literals vs `LuaBundle` keys — **CLOSED**

> **Resolution (2026-08-22).** Fixed upstream by `1a5d5b70`, *"DEBUG-06 §3.3 claimed Lunar has no
> message bundle — it has one"*, which landed **before** this artifact's revision. §3.3 no longer
> asserts the false premise and re-takes the literals decision on one that holds:
> `noRuntimeConfigured()` delegates to `LuaToolResolver.notConfiguredMessage(kindId)`, which
> **composes** the headline message at call time from
> `LuaToolKindRegistry.findById(kindId)?.displayName ?: kindId`
> (`toolchain/resolve/LuaToolResolver.kt:93-97`) and therefore cannot be a static key — *"a
> convention that excludes its own headline is a split, not a convention"*. §3.3 reason 3 also draws
> the line this feature needs: contract §6's text rules *"reach control labels, not validation
> prose"*. So the two decisions agree rather than diverge — DEBUG-05 §2.7's nine `debug.attach.*`
> **control labels** are `LuaBundle` keys, DEBUG-05 §2.8's two **validation** strings are literals.
> `DEBUG-05-00-DR-01` is **done**; nothing is owed by this feature or by DEBUG-06.

The finding that produced this gap is kept below, because it is the reason the upstream section was
rewritten and because *how* it survived is the transferable part.

- **Original question**: should `LuaTargetMessages` (and this feature's two additions to it) hold
  literal strings, or `LuaBundle` keys?
- **The finding.** DEBUG-06 design §3.3 stated *"Lunar has no message bundle"* and backed it with two
  executed commands: `ls -d src/main/resources/messages` (no such directory) and
  `grep -rn 'LunarBundle\|DynamicBundle' src/main/kotlin/` (no hits). Both searched for the wrong
  name. Lunar **does** have a bundle:
  - `src/main/kotlin/net/internetisalie/lunar/LuaBundle.kt:15-18` — `object LuaBundle` over
    `BUNDLE = "net.internetisalie.lunar.LuaBundle"`.
  - `src/main/resources/net/internetisalie/lunar/LuaBundle.properties` — 145 lines, including a
    `# debugging` section at `:109`.
  - Used in **12** Kotlin files (`grep -rln LuaBundle src/main/kotlin/`), among them
    `run/LuaExecutionStack.kt:28` (`LuaBundle.message("debug.stack.thread.main")`) — i.e. inside the
    very package DEBUG-06 was editing.
  - Referenced from `src/main/resources/META-INF/plugin.xml:682` as
    `bundle="net.internetisalie.lunar.LuaBundle"`.
- **Scope of the open question, narrowed.** Only `LuaTargetMessages`' **validation strings** are
  open. This feature's own **UI control labels** are decided and closed: design §2.7 puts all nine
  `debug.attach.*` keys in `LuaBundle`, because `docs/engineering-contract.md:162-163` names *"a
  bundle assertion that no control label is Title Case"* as a UI invariant that should be tested and
  that assertion needs keys, and because a **new** editor is inside the contract's §6 SCOPE (the
  exemption covers *surviving* `FormBuilder` editors). TC-05-07e is the assertion. That is a
  different question from a sibling feature's validation-message convention, and answering it here
  changes nothing DEBUG-06 owns.
- **Outcome**: option (a) — both features stay on literals for **validation prose**, and DEBUG-06
  §3.3 now says why on a premise that survives checking. DEBUG-05 §2.8's two messages are literals
  under that rule; §2.7's control labels are keys under contract §6. No migration is outstanding
  and no sibling design was changed by this feature.
- **What generalises.** The false claim carried *executed* evidence and passed three review rounds
  because the reviewer independently re-ran the **same wrong names**. Two agents guessing the same
  name is agreement, not confirmation — executed evidence is only as good as the name you search
  for. That is why every search in this artifact's §0 pastes the command *and* the name it used.
- **Closed by**: `1a5d5b70` (upstream). `DEBUG-05-00-DR-01` is marked **done** in the table below.

### Gap 2.2: the `localhost` → `::1` hypothesis is unverified

- **Question**: on an IPv6-first host, does a debuggee that resolves `MOBDEBUG_HOST` (default
  `"localhost"`, `src/main/lua/lunar/debug.lua:12`) reach a listener bound to
  `InetAddress.getLoopbackAddress()` (`127.0.0.1`)?
- **What is measured**: on *this* host it does — design §0, Probe L, both with `MOBDEBUG_HOST`
  unset and with it set explicitly. The failure mode did **not** reproduce, and this design does not
  claim it exists.
- **Options / leaning**: setting `MOBDEBUG_HOST` explicitly (design §2.6) costs one map entry and
  removes the dependency on the debuggee host's resolver either way, so it is done regardless. What
  is unresolved is only whether it *fixes* anything.
- **Resolved by**: `DEBUG-05-00-DR-02`. Not a blocker for any phase.

### Gap 2.4: no `human-verification-checklists.md` is authored yet

- **Question**: four rows in this feature are not settleable by a unit test — the *Lua Remote
  (Mobdebug)* template appearing with a Debug-but-no-Run action, the attach editor's label column
  and casing, the quoted console output of Risk 1.6, and the §3.5 mismatch message rendering. The
  standard artifact set includes a manual checklist and this feature does not have one.
- **Options / leaning**: author it as part of Phase 3, when the surface it describes exists. Writing
  it now would describe a dialog nobody has seen.
- **Resolved by**: `DEBUG-05-00-DR-04`. The five checks are enumerated as HV-01…HV-05 in
  [implementation-plan.md](implementation-plan.md)'s Verification Tasks so they are not lost if the
  file slips. This mirrors [[DEBUG-06]]'s own Gap 2.6 / DR-03, which made the same call.

### Gap 2.3: `LuaTargetSpec` cannot carry `remoteRoot` or `bindHost`, so two ATTACH checks are closures

- **Question**: DEBUG-06 design §2.10 point 1 says *"`LuaTargetSpec` names no configuration class"*
  and enumerates the six fields it carries. Neither `remoteRoot` nor `bindHost` is among them, yet
  checks A1 and A2 (design §2.8) need both.
- **Resolution, taken here and not deferred**: the attach check list is not a `val ATTACH` but
  `fun attach(configuration: LuaAttachRunConfiguration): List<LuaTargetCheck>`, whose last two
  entries are lambdas closing over the configuration. This keeps `LuaTargetSpec`
  configuration-agnostic (§2.10 point 1 survives intact), keeps every check a `fun interface` value
  (§2.10 point 3 survives intact), and adds no field to a type DEBUG-06 owns. The alternative —
  adding `remoteRoot`/`bindHost` to `LuaTargetSpec` — would put two attach-only fields on a carrier
  that `LuaRunConfiguration` and `LuaTestRunConfiguration` also build, which is exactly the coupling
  §2.10 was written to avoid. **No DR task**: this is decided.

## What this feature believes is wrong elsewhere

Recorded here rather than acted on, per the dispatcher's instruction that `requirements.md` is
frozen input and DEBUG-06 is a merged sibling.

| Document | Claim | Verdict | Evidence |
| :-- | :-- | :-- | :-- |
| [[DEBUG-06]] design §3.3 | *"Lunar has no message bundle"*, with two executed commands as support | **WAS WRONG — CORRECTED UPSTREAM, no longer outstanding** | `1a5d5b70` (*"DEBUG-06 §3.3 claimed Lunar has no message bundle — it has one"*) rewrote the section before this artifact's revision; it now names `LuaBundle`, 11 caller files / 22 call sites, and re-takes the literals decision on `noRuntimeConfigured()`'s call-time composition (`toolchain/resolve/LuaToolResolver.kt:93-97`). Gap 2.1 is closed and `DEBUG-05-00-DR-01` with it. |
| [[BUG-450]] §3a | *"mobdebug already supports the fix — the STACK handler reads `maxlevel`, `maxnum` and `sparse`. Lunar has simply never passed any of them."* | **INCOMPLETE, in a way that would mislead an implementer** | Design §0, Probe M: `maxlevel=1` cuts the payload 10102 → 1348 bytes with the frame tuple intact, but `maxnum=3` truncates the **frame tuple itself** to three elements and `maxnum=0` empties the response entirely. Passing `maxnum` as the bound would silently destroy `linedefined`, `currentline`, `what`, `namewhat` and `short_src`. |
| [[BUG-456]] §3 | *"Loopback is correct for every use Lunar supports today"* and the fix is *"one argument"* | **CORRECT** | Design §0, BindProbe: `ServerSocket(port)` → `0.0.0.0`; `ServerSocket(port, 1, loopback)` → `127.0.0.1`, LAN connect refused at the kernel. Retired by **Phase 2**, whose `openListener(target)` (§3.1) replaces the wildcard bind at `run/LuaDebuggerController.kt:114-117` and is the first point at which the change has a testable seam (TC-05-12a) — see the implementation plan's *Why the loopback fix is not here* note under Phase 1. §3 also asks for an origin check, which `requirements.md` does not have a row for; design §3.4 adds it as an unreachable-on-Linux invariant and says so. |
| `requirements.md` `DEBUG-05-03` | *"`setBaseDir()` sends `BASEDIR` … from the run config's working directory **or `project.basePath`**"* | **WRONG about the shipped code — the `basePath` arm is unreachable, and it is what makes `-03` a change rather than a preservation** | The controller derives its base directory with `listOfNotNull(configuration.workingDirectory, session.project.basePath, "").first()` (`run/LuaDebuggerController.kt:80-90`), but `myWorkingDirectory` is declared `string("")` (`run/LuaRunConfiguration.kt:78-82`), so `workingDirectory` is the **empty string, not null**, `listOfNotNull` keeps it, `.first()` is `""` and `session.project.basePath` is **never read** — today a default configuration sends `BASEDIR /`, which §0 Probe N2 measured and Risk 1.8 carries. Design §2.3 switches the derivation to `configuration.effectiveWorkDirectory()` (`run/LuaRunConfiguration.kt:228`), which *does* fall back to `basePath` — i.e. the requirement describes the intended behaviour this feature **introduces**, not the one it finds. **No edit to `requirements.md`** — it is frozen input; the row is right about the destination and wrong about the starting point. |
| `requirements.md` `DEBUG-05-07` | *"mobdebug answers `200 OK` to a breakpoint on a file it will never load"* | **CORRECT** | Design §0, Probe G: `SETB does/not/exist.lua 7` → `200 OK`. |
| `requirements.md` `DEBUG-05-10` | *"such a frame would fall to `log.error` with its payload left unread… This is latent, not live"* | **CORRECT on the mechanism; the "latent" framing understates it** | Design §0, Probe K measured the exact interleaving: with `OUTPUT` active the `204` frame precedes the in-flight `200 OK` **while paused**, so the branch must be first in `handleLine`, not inside `if (running)`. A plausible implementation that only handles output "while running" reproduces the defect. Nothing to correct in the row — noted so the implementer does not take `if (running)` as sufficient. |
| `requirements.md` Verification | *"`TestLuaRemoteStackFrames`… paths are 43-character absolutes"* | **CORRECT** | `python3 -c "print(len('/home/mini/Documents/src/lua/test/stack.lua'))"` → `43`; fixture at `TestLuaRemoteStackFrames.kt:15-19`. |
| `requirements.md` `DEBUG-05-16` | the 16 wire verbs | **CORRECT** | `grep -n 'command == "' src/main/lua/mobdebug/init.lua` in `debugger_loop` → exactly SETB, DELB, EXEC, LOAD, SETW, DELW, RUN, STEP, OVER, OUT, BASEDIR, SUSPEND, DONE, STACK, OUTPUT, EXIT (`:800-1035`). None retrieves source. |
| [[DEBUG-06]] design §2.10 | checks 1 and 3 must be dropped for correctness; 2/4/6/7 are inert; 5 and 8 are reusable | **CORRECT, re-derived here check by check** | Design §2.8. Check 1 is `FATAL` on `runtime == null`, which `RunManagerImpl.kt:162-164` turns into `return false`; check 3 fires on `scriptPath.isNullOrEmpty()`. Both are the attach spec's normal state. The only thing §2.10 does not anticipate is Gap 2.3, which is resolved without changing anything §2.10 asserts. |

## Technical Debt & Future Work

- **TBD: infer the remote root from the first frame** (EmmyLua's `recognizeBaseDir`). Design §10.3
  rejects it for now: a wrong inference silently debugs the wrong copy of a file, which is worse
  than the silent non-binding it replaces. Revisit only with a rule that refuses to guess when more
  than one project file matches.
- **TBD: N-way path mapping with a table editor.** Design §10.1 rejects it because mobdebug carries
  exactly one `basedir`, so a second mapping could only be honoured in one direction. The design
  keeps `PathMappingSettings.PathMapping` as its engine so the growth is additive.
- **TBD: `DEBUG-05-15` / `-16` remain `Won't`.** Lua exposes no external attach API, and
  `debugger_loop`'s 16 verbs include none that retrieves source. Both would require forking the
  vendored debuggee.
- **TBD: a `LuaPositionConverter` in the platform's `PyPositionConverter` shape.** The requirements
  cite it as the per-language surface for bidirectional conversion. `LuaPathMapper` is the same idea
  without the platform interface, which Lunar does not implement anywhere; adopting the interface
  buys nothing until a second consumer exists.
- **[[BUG-463]] — every non-breakpoint pause has had no source position since the debugger
  shipped.** Found during this planning pass and filed under its own ID; do not re-describe it here.
  Phase 1 fixes it incidentally by routing `localPosition` through the mapper (design §3.8), and
  **TC-05-08c is its acceptance row** — without that row a mis-wired mapper would leave the bug
  live and nothing would go red. Two refinements to the report's framing, recorded here because the
  report is another document's to edit:
  - *"Step and watchpoint"* understates the scope. The else branch at
    `run/LuaDebuggerController.kt:326` is taken by **any** pause whose position is not in
    `myPos2Breakpoints`, which includes a run-to-cursor stop as well as steps and watchpoints.
  - The relative wire path is relative to whatever `BASEDIR` was sent, and for a default
    configuration that is `/` (Risk 1.8, design §0 Probe N1) — so the failing lookup is
    `findFileByPath("home/u/proj/a.lua")`, not `findFileByPath("sub/a.lua")`. Same defect, one level
    more absolute; `findFileByPath` rejects both.
  - The report's own trap still governs the fixture: *a fixture rooted at `/` stays green under the
    mutation*, so TC-05-08c nests its file under `$scratch/ide/sub/`.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| `DEBUG-05-00-DR-01` | ~~Correct [[DEBUG-06]] design §3.3's "no message bundle" claim and decide, once, whether `LuaTargetMessages` holds literals or `LuaBundle` keys.~~ **Closed — done upstream in `1a5d5b70`**, which rewrote §3.3 with the corrected premise and re-took the literals decision on `noRuntimeConfigured()`'s call-time composition. Nothing is owed by this feature. | Gap 2.1 | **done** |
| `DEBUG-05-00-DR-02` | Measure, on an IPv6-first host (or with `-Djava.net.preferIPv6Addresses=true` plus a `::1`-only `/etc/hosts` entry for `localhost`), whether a mobdebug debuggee with `MOBDEBUG_HOST` unset reaches a listener bound to `InetAddress.getLoopbackAddress()`. Paste the transcript into design §0 Probe L either way. | Gap 2.2 | todo |
| `DEBUG-05-00-DR-04` | Author `human-verification-checklists.md` during Phase 3 with HV-01…HV-05 as enumerated in [implementation-plan.md](implementation-plan.md), then run one `verify-in-ide` pass per the `verify-in-ide` skill. HV-05 (the background-progress **Cancel**) is the only executed evidence `DEBUG-05-06`'s user-facing half will have — TC-05-06a cancels the `Job` programmatically. | Gap 2.4; `-05`, `-06`, `-07`, `-10`, `-12` (UX halves) | todo |
| `DEBUG-05-00-DR-03` | Before Phase 4, confirm on the real harness that a `204 Output` frame can arrive **between** a `Run`-group command's `200 OK` and the subsequent `202 Paused` (design §0 Probe K measured the paused case only). If it can, TC-05-10a needs a sibling row for the running case; if it cannot, record why in design §3.6. | Test-case gap 4.1 | todo |

## Test Case Gaps

- **4.1 — `204 Output` while `running == true`.** Probe K measured the frame arriving during an
  in-flight `EXEC` at a **pause**. The design's branch is placed before both Case A and Case B, so
  it is correct either way, but no test drives the running case. `DEBUG-05-00-DR-03` decides whether
  one is needed.
- **4.2 — `rejectForeignPeer` has no test, deliberately.** Its branch is unreachable on Linux: a
  loopback-bound listener never receives a non-loopback peer (design §0, BindProbe — the LAN connect
  was refused by the kernel). A test that cannot reach its own subject is the decoration this
  planning bar exists to prevent. Design §9 states the omission and its reason.
- **4.5 — the auto-restart *loop* is tested in two halves, not end to end.** TC-05-13a asserts that
  a disconnect with `autoRestart == true` routes to `restartListener()` rather than to termination,
  and TC-05-13b asserts that `listener()` re-uses a live `ServerSocket`. Nothing drives a real
  debuggee through disconnect → reconnect. That is deliberate: the missing link is the JDK property
  that a `ServerSocket` survives a peer disconnect and accepts again, which §0's `PollProbe` already
  measured and which no Lunar code can break. An end-to-end row would assert the JDK, not us, and
  `-13` is a `Could`.
- **4.3 — a genuinely cross-host session is untested.** Every probe and every test runs both halves
  on one machine, differing only in *directory*. That covers path mapping, which is the hard part,
  but not a real network partition, latency, or a debuggee on a different OS with `\` separators.
  `PathMapping` normalises with `FileUtil.toSystemIndependentName` (`PathMappingSettings.java:212-222`)
  and `createRemotePosition` already does `replace('\\', '/')` (`run/LuaPosition.kt:45`), so the
  Windows-debuggee case is *designed for* but not *measured*. The repo has a drivable Windows VM
  (see `.agents/` memory, `win11`); a cross-OS run belongs in the manual checklist of Gap 2.4, not in
  the unit suite.
- **4.4 — no test asserts the attach editor's rendering.** By construction: unit tests cannot observe
  alignment, casing or elision (`docs/engineering-contract.md:159-163`). HV-01/HV-02 own it.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Extensibility contract this feature consumes: [[DEBUG-06]] design §2.10
- Retired by Phase 2: [[BUG-456]]
- Adjacent, same lines: [[BUG-450]]
