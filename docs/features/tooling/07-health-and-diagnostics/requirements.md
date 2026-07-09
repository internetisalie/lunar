---
id: "TOOLING-07"
title: "07: Health Monitoring & Diagnostics"
type: "feature"
status: "in_progress"
priority: "medium"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-07: Health Monitoring & Diagnostics

## Overview

Evolves the proven TOOL-03 health subsystem (`tool/health/*`) onto the unified toolchain
model: a three-stage health checker that writes the contract §2.3 `LuaToolHealth` shape
through registry mutation methods (never through mutating reads), a project-scoped VFS
monitor that also watches provisioned environment directories, editor banners extended to
RUNTIME kinds, state-transition balloon notifications with dedup, and a full-toolchain
diagnostics snapshot. Parent epic: [TOOLING](../requirements.md); binding contract:
[tooling-architecture.md](../tooling-architecture.md) (esp. §2.3).

## Scope

### In Scope
- `net.internetisalie.lunar.toolchain.health.LuaToolHealthChecker` — the 3-stage check
  (fast file checks → mtime gate → probe) producing `LuaToolHealth`
  (`fileExists`/`executable`/`probeOk`/`probedAtMtime`/`reason`).
- `net.internetisalie.lunar.toolchain.health.LuaToolHealthMonitor` — project-scoped VFS
  watcher over inventory paths **and** environment root/bin dirs; batched revalidation;
  EDT marshaling for UI updates; env-root-deleted handling.
- `net.internetisalie.lunar.toolchain.health.LuaToolEditorNotificationProvider` — banner on
  Lua files for broken *engaged* tools and for "no usable Lua runtime", linking the
  TOOLING-06 Toolchain settings page.
- Health-driven balloon notifications: tool became unusable (once per state transition),
  environment root deleted (once per env per session).
- `net.internetisalie.lunar.toolchain.health.LuaToolDiagnostics` — snapshot logging of the
  full toolchain state (kinds, inventory + health, bindings, environments, resolver
  outcomes per kind).
- `plugin.xml` registrations for the above (replacements for the legacy `tool/health/*`
  entries; TOOLING-05 owns the deletion commit).

### Out of Scope
- The model shapes themselves (`LuaToolKind`, `LuaRegisteredTool`, `LuaToolHealth`,
  `LuaToolProbe`) — TOOLING-01.
- Resolver precedence, bindings, environments, the `LuaToolchainListener` topic —
  TOOLING-02 (this feature *consumes* and *fires* them via registry methods).
- The process-execution service and its timeout classes — TOOLING-03 (the probe runs
  through it).
- Provisioning-failure notifications (download/build/install errors) — TOOLING-04.
- Settings pages (the Toolchain configurable this feature links to) — TOOLING-06.
- Deletion of the legacy `tool/` package and its `plugin.xml` entries — TOOLING-05 (this
  feature specifies the replacement entries only).
- Version-update ("new version available") checks — epic non-goal (PRD).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| TOOLING-07-01 | **Three-stage health check** | M | Full | `LuaToolHealthChecker.check` runs fast file checks, an mtime gate that skips the probe for unchanged previously-probe-OK binaries, and a probe via the TOOLING-03 exec service (PROBE timeout), producing the contract §2.3 `LuaToolHealth`. |
| TOOLING-07-02 | **Registry-mediated health writes** | M | Not Implemented | Health results are written only through `LuaToolchainRegistry` mutation methods, which fire `LuaToolchainListener.TOPIC`. No read path mutates health (removes the `LuaToolManager.getTools()` anti-pattern). |
| TOOLING-07-03 | **Reactive VFS monitoring** | M | Not Implemented | A project-scoped `AsyncFileListener` watches inventory binary paths, environment root dirs, and environment `bin/` dirs; matching events schedule a batched background revalidation (500 ms merge window). No polling. |
| TOOLING-07-04 | **Editor banner for broken engaged tools** | M | Not Implemented | Lua-file editor banner when an *engaged* kind's intended tool is unusable, naming kind + reason with a link to the Toolchain settings page; and "No usable Lua runtime for this project" when no RUNTIME kind resolves. |
| TOOLING-07-05 | **State-transition notifications** | M | Not Implemented | One WARNING balloon when tools transition usable→unusable (deduped per transition); one balloon when an environment root directory is deleted (deduped per env per session), with member tools' reason set to the env-root cause. |
| TOOLING-07-06 | **Diagnostics snapshot** | S | Not Implemented | `LuaToolDiagnostics.logSnapshot` writes kinds, full inventory with health, global/project bindings, environments (with active flag), and per-kind resolver outcomes to the IDE log in a fixed line format. |
| TOOLING-07-07 | **Diagnostics action** | C | Not Implemented | A "Lua: Toolchain Diagnostics" action in the Tools menu triggers `logSnapshot` on demand. |

## Detailed Specifications

### TOOLING-07-01: Three-stage health check
Stages (design §3.1): (1) fast — `fileExists`/`executable` via `java.io.File`, no
subprocess; (2) mtime gate — if the previous health has `probeOk == true` and
`probedAtMtime == file.lastModified()` and a version is recorded, return the previous
health unchanged **without spawning a process**; (3) probe — run the kind's probe spec
through `LuaToolProbe` (which uses the TOOLING-03 exec service in capture mode with the
PROBE timeout class, 10 s), parse the version, and produce
`LuaToolHealth(fileExists=true, executable=true, probeOk, probedAtMtime=mtime, reason)`.
Reasons are exactly: `"Binary missing"`, `"Permission denied"`, `"Timeout"`,
`"Not executable"`, `"OK <version>"`, or the first non-blank probe output line on an
unrecognized banner. A probe failure is **not** gated: it is re-probed on the next
revalidation (matches TOOL-03 behavior).

### TOOLING-07-02: Registry-mediated health writes
The checker is pure (returns a result; writes nothing). The monitor writes results via
`LuaToolchainRegistry.updateToolCheck(toolId, health, version, luaVersion, runtime)` (arch §10.1), which replaces
the immutable inventory entry and fires the TOOLING-02 topic. The write is skipped when
the new health + version equal the stored ones (no event storm). All registry reads
(`tools()`, `toolsOfKind(kindId)`) are pure.

### TOOLING-07-03: Reactive VFS monitoring
Watch set (recomputed per event batch from registry + project toolchain state):
canonical inventory binary paths, environment `rootDir`s, environment `rootDir/bin` dirs.
Match rules (design §3.2): delete/move events match on the exact path **or any watched
path being under the event path** (ancestor deletion); content/property-change events
match on an exact binary path or a direct child of a watched `bin/` dir. Matches schedule
revalidation through a 500 ms `MergingUpdateQueue` so bursts (e.g. `rm -rf` of an env)
produce one revalidation pass.

### TOOLING-07-04: Editor banner
Banner conditions are **decided** as follows (design §3.4). A kind is *engaged* when:
- kind `luacheck`: the `LuaCheck` inspection is enabled in the project's current
  inspection profile, **or** the kind is explicitly selected;
- any other kind (including RUNTIME kinds): the kind is *explicitly selected* — a project
  binding, a global binding, or a tool of that kind inside the active environment.

The banner shows (first match wins, at most one banner):
1. **Runtime banner** — no kind with `Capability.RUNTIME` resolves to a usable tool:
   "No usable Lua runtime for this project." with actions *Configure toolchain* (opens the
   TOOLING-06 Toolchain page) and *Dismiss* (suppresses this banner for the project until
   the IDE restarts).
2. **Broken-tool banner** — for the first engaged kind (RUNTIME kinds first, then others,
   each group ordered by kind id) whose *intended* tool (precedence **without** usability
   filtering: active env → project binding → global binding; never inventory fallback) is
   non-null and not usable: "Lua tool '<displayName>' is unavailable: <reason>" with
   *Configure toolchain*.

A kind with no intended tool shows no broken-tool banner (parity with TOOL-03: missing
optional tools never nag). Non-Lua files never show a banner.

### TOOLING-07-05: State-transition notifications
After each revalidation pass the monitor compares each tool's pre-write `isUsable` with
the new result: tools transitioning `true → false` are collected into **one** WARNING
balloon ("Lua tool(s) became unavailable: <displayNames>…") on the existing
`notification.group.lunar.tools` group. Dedup = the transition itself: a persistently
broken tool never re-notifies; a tool that recovers and breaks again notifies again.
Startup uses persisted health as the "previous" state, so a tool that broke while the IDE
was closed notifies exactly once on open. Environment roots: an env whose `rootDir` no
longer exists produces one balloon naming the env ("Lua environment '<name>' was deleted
from disk…"), deduped by env id per IDE session (the id re-arms if the root reappears);
its member tools' health reason is overridden to `"Environment root missing: <rootDir>"`.

### TOOLING-07-06: Diagnostics snapshot
Fixed, greppable line format (prefix `[TOOLCHAIN-DIAG]`), one `log.info` per line — see
design §4.1 for the exact grammar. Logged automatically after every revalidation pass and
on demand via TOOLING-07-07.

## Behavior Rules
- All health checks, probes, and diagnostics run on background threads; the EDT only
  renders banners/balloons and refreshes editor notifications (marshaled via
  `invokeLater`).
- Banner data collection performs no I/O and spawns no process — it reads cached registry
  state and the inspection profile only.
- The monitor's topic subscription refreshes banners only; it never schedules health
  checks (no event → check → event loops).
- Health writes that change nothing fire no event.

## Test Cases

### TC-TOOLING-07-01: Fast-check failure shapes (design §3.1 stage 1)
- **Input**: a `LuaRegisteredTool` whose `path` points at a deleted file.
- **Action**: `LuaToolHealthChecker.check(tool, kind, probe)`.
- **Expected Output**: `LuaToolHealth(fileExists=false, executable=false, probeOk=null,
  probedAtMtime=null, reason="Binary missing")`; the injected probe records **zero**
  invocations. For an existing non-executable file: `LuaToolHealth(fileExists=true,
  executable=false, probeOk=null, probedAtMtime=null, reason="Permission denied")`.

### TC-TOOLING-07-02: Probe success (design §3.1 stage 3)
- **Input**: an executable file; a fake `LuaToolProbe` returning
  `LuaToolProbeResult(ok=true, version="1.1.0", luaVersion=null, runtime=null, failure=null)`.
- **Action**: `check(tool, kind, probe)`.
- **Expected Output**: `LuaToolHealth(fileExists=true, executable=true, probeOk=true,
  probedAtMtime=file.lastModified(), reason="OK 1.1.0")` and result `version == "1.1.0"`.

### TC-TOOLING-07-03: mtime gating asserts no process spawn (design §3.1 stage 2)
- **Input**: an executable file; tool with
  `health = LuaToolHealth(true, true, true, file.lastModified(), "OK 1.1.0")`,
  `version = "1.1.0"`; a recording fake probe.
- **Action**: `check(tool, kind, probe)`.
- **Expected Output**: the stored health returned unchanged; the fake probe records
  **zero** invocations. Touching the file (`setLastModified(mtime + 1000)`) and re-running
  yields exactly **one** probe invocation.

### TC-TOOLING-07-04: Banner data collection — broken engaged tool (design §3.4)
- **Input**: registry containing a `luacheck` tool with
  `health.fileExists=false, reason="Binary missing"`; a project binding
  `luacheck → toolId`; a `.lua` file open.
- **Action**: `LuaToolEditorNotificationProvider.collectNotificationData(project, luaFile)`.
- **Expected Output**: non-null `Function` producing an `EditorNotificationPanel`
  (Warning) whose text contains `luacheck` and `Binary missing`; for a non-Lua file →
  `null`; with the binding removed (kind not engaged, inspection disabled) → `null`.

### TC-TOOLING-07-05: Banner — no usable runtime (design §3.4 rule 1)
- **Input**: registry with no usable RUNTIME-kind tool (empty inventory or all runtime
  entries unusable); a `.lua` file open.
- **Action**: `collectNotificationData(project, luaFile)`.
- **Expected Output**: panel text `"No usable Lua runtime for this project."`; after
  `LuaToolHealthMonitor.dismissRuntimeBanner()` → `null`; with a usable `lua` tool
  registered → `null`.

### TC-TOOLING-07-06: Notification dedup — once per transition (design §3.3)
- **Input**: a usable registered tool; its binary is then deleted.
- **Action**: run `revalidateAll()` twice.
- **Expected Output**: exactly **one** balloon on `notification.group.lunar.tools` naming
  the kind (first pass: usable→unusable transition); the second pass fires none. Restoring
  the binary, revalidating, deleting it, and revalidating again fires a second balloon.

### TC-TOOLING-07-07: Environment root deleted (design §3.3 step 6)
- **Input**: project toolchain state with environment `E` (rootDir on disk, one member
  tool in the registry with `environmentId = E.id`); the rootDir is deleted.
- **Action**: `revalidateAll()` twice.
- **Expected Output**: the member tool's health becomes
  `fileExists=false, reason="Environment root missing: <rootDir>"`; exactly one balloon
  naming environment `E` across both passes.

### TC-TOOLING-07-08: Diagnostics snapshot format (design §4.1)
- **Input**: registry with two tools (one usable `lua`, one broken `luacheck`), one global
  binding, one project binding, one active environment.
- **Action**: `LuaToolDiagnostics.logSnapshot(project)`.
- **Expected Output**: log lines prefixed `[TOOLCHAIN-DIAG]` including one `tool …` line
  per inventory entry carrying `kind=`, `path=`, `origin=`, `version=`, `env=`, and
  `health=[exists=… exec=… probe=… reason="…"]`; one `binding scope=… kind=…` line per
  binding; one `env id=…` line with `active=true`; one `resolve kind=… ->` line per known
  kind (`-> none` where resolution fails).

### TC-TOOLING-07-09: Registry-mediated writes fire the topic; reads are pure (design §2.2)
- **Input**: a registered tool; a subscriber on `LuaToolchainListener.TOPIC`.
- **Action**: (a) `LuaToolchainRegistry.getInstance().tools()` repeatedly with the binary
  deleted; (b) monitor revalidation writing the new health.
- **Expected Output**: (a) no state change, no event (stored health untouched by reads);
  (b) exactly one topic event; a second identical write fires no further event.

### TC-TOOLING-07-10: VFS event filtering (design §3.2)
- **Input**: watch set {binary `/opt/t/bin/luacheck`, env root `/opt/env`, bin dir
  `/opt/env/bin`}.
- **Action**: evaluate the match predicate on synthetic events.
- **Expected Output**: delete of `/opt/env` → match; delete of `/opt` (ancestor) → match;
  content change of `/opt/env/bin/lua` → match; content change of `/opt/env/README` → no
  match; delete of `/unrelated` → no match.

## Acceptance Criteria
- [ ] TOOLING-07-01/02: checker produces the §2.3 health shape; all writes go through the
      registry and fire the topic; no mutating reads remain in `toolchain.health`.
- [ ] TOOLING-07-03: deleting a bound binary or an environment root triggers exactly one
      batched revalidation and the banner/balloon flow (TC-06/07/10).
- [ ] TOOLING-07-04: banners render per the decided engagement rules, including the
      runtime banner, and link to the Toolchain page (TC-04/05).
- [ ] TOOLING-07-05: balloons are deduped per state transition / per env per session
      (TC-06/07).
- [ ] TOOLING-07-06: `logSnapshot` output matches the §4.1 grammar (TC-08).
- [ ] Full suite green via `tooling/gce-builder/gce-builder.sh run test`; live check per
      `human-verification` flow (banner appears/disappears in the sandbox IDE).

## Non-Functional Requirements
- **Threading**: checks/probes/diagnostics background-only (engineering contract; arch
  §10); probe timeout = TOOLING-03 `PROBE` class (10 s); UI via `invokeLater`.
- **Performance**: banner collection is allocation-light and I/O-free; revalidation merges
  VFS bursts (500 ms window); unchanged health short-circuits both the probe (mtime gate)
  and the registry write (no event).
- **Memory**: the monitor holds no `VirtualFile`/`PsiFile` refs; watch sets are computed
  per event batch from registry state (strings only).

## Dependencies
- **TOOLING-01** — `LuaToolKind`/`LuaToolKindRegistry`, `LuaRegisteredTool`,
  `LuaToolHealth`, `LuaToolProbe` (arch §2).
- **TOOLING-02** — `LuaToolchainRegistry` mutation methods + `LuaToolchainListener.TOPIC`
  (arch §4), project toolchain state (bindings/environments/activeEnvironmentId, arch §7),
  `LuaToolResolver` (arch §3).
- **TOOLING-03** — `LuaToolExecutionService` with the PROBE timeout class (arch §5), used
  by `LuaToolProbe`.
- **TOOLING-05** — deletes the legacy `tool/health/*` classes and their `plugin.xml`
  entries (this doc enumerates the replacements).
- **TOOLING-06** — `LuaToolchainConfigurable` settings page id targeted by banner links.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Prior art: [TOOL-03 design](../../tool/03-ui-and-health-checks/design.md)
