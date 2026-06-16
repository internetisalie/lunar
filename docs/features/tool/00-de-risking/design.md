---
id: TOOL-00-DESIGN
title: "Spike Methodology & Acceptance"
type: design
parent_id: TOOL-00
status: "planned"
priority: "high"
folders:
  - "[[features/tool/00-de-risking/requirements|requirements]]"
---

# TOOL-00: Spike Methodology & Acceptance

> **⚠ Grounding correction (2026-06-16):** spike **00-01** targets a terminal API that does not
> exist in 2026.1. The real path is `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer.customizeExecOptions(project, MutableShellExecOptions)`
> (EP `…shellExecOptionsCustomizer`; deprecated fallback `org.jetbrains.plugins.terminal.LocalTerminalCustomizer`)
> — there is no `TerminalCustomizer`/`initCommands`/`TerminalType`. All other TOOL-00 spikes ground.
> See [planning-gaps.md](../../../planning-gaps.md#wave-10-grounding-audit-2026-06-16).

De-risking actions are inherently exploratory, so "the bar" for this feature is not a
production implementation — it is that **each spike has a defined question, a method, a
measurable pass/fail threshold, and a concrete deliverable artifact**. A non-frontier executor
can run each spike and decide unambiguously whether it passed and what to hand back. This
document pins those down; `requirements.md` carries the same thresholds in table form.

## 1. Common Method
Each spike is a small, throwaway prototype (a scratch module + a test) plus a written result.
Environments: Linux (Bash/Zsh), Windows (CMD/PowerShell) where the action names them. Process
work uses the existing `net.internetisalie.lunar.util.LuaProcessUtil`. Every spike produces
(a) a pass/fail verdict against its threshold and (b) a named artifact committed under
`docs/features/tool/00-de-risking/results/` (or a test class where stated).

## 2. Per-spike acceptance

### TOOL-00-01 — Terminal PATH injection
- **Question**: Does prepending a tool dir to `PATH` via the env map of
  `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer` (deprecated fallback
  `org.jetbrains.plugins.terminal.LocalTerminalCustomizer`) reach each target shell?
- **Method**: implement a minimal `ShellExecOptionsCustomizer` whose `customizeExecOptions(project,
  options)` prepends a known dir to `options.environment["PATH"]`; open a fresh built-in terminal per
  shell and run `echo $PATH` / `echo %PATH%` and `which/where luarocks`. (No `export`/`set` init
  commands — injection is purely via the env map.)
- **Pass threshold**: on Bash, Zsh, CMD, PowerShell the injected dir appears **first** and
  `which/where` resolves to it. Any shell that fails is documented with a fallback.
- **Deliverable**: `results/terminal-path.md` (per-shell table) + the prototype customizer.

### TOOL-00-02 — OS-specific tool filenames
- **Question**: What candidate filenames must discovery try per OS?
- **Method**: define `LuaToolDescriptor(toolType, candidates(os): List<String>)` and resolve
  luarocks/luacheck/stylua via `PathEnvironmentVariableUtil.findInPath` on each OS.
- **Pass threshold**: each of the three tools resolves on Linux (no ext) and Windows
  (`.bat`/`.exe`/`.cmd` as applicable) in a manual run; the descriptor table is complete.
- **Deliverable**: the `LuaToolDescriptor` mapping (handed to TOOL-01) + `results/tool-filenames.md`.

### TOOL-00-03 — PersistentStateComponent serialization
- **Question**: Do `MutableList<LuaTool>` / `MutableMap<LuaToolType,String>` round-trip cleanly
  in `lunar.xml`?
- **Method**: a test settings component; set values → `getState()` → `loadState()` (simulated
  restart) → compare.
- **Pass threshold**: deep equality after round-trip; **no** stray/`null`/leaky tags in the
  serialized XML; concurrent writes from 2 coroutines do not corrupt state.
- **Deliverable**: passing `LuaSettingsSerializationTest`.

### TOOL-00-04 — Async/cancellable CLI wrapper
- **Question**: Can CLI calls run off-EDT, cancellable, with timeouts?
- **Method**: a wrapper over `LuaProcessUtil.capture` on `Dispatchers.IO`; drive cancel + timeout.
- **Pass threshold**: EDT never blocked (assert via `ThreadingAssertions.assertBackgroundThread`);
  cancellation propagates `ProcessCanceledException` within ~100 ms; a 10 s timeout returns the
  timeout sentinel (`exitCode == -1`).
- **Deliverable**: passing `LuaProcessCoroutineTest` + the wrapper utility.

### TOOL-00-05 — Cross-platform E2E infrastructure
- **Question**: Can discovery/PATH/exec be validated on Windows + Linux in CI?
- **Method**: Dockerfiles for a minimal Linux and Windows image with lua + luarocks; a runner
  that executes one discovery+exec scenario inside each.
- **Pass threshold**: the scenario is **green on Linux**; Windows is green **or** explicitly
  deferred with a documented blocker (Windows-container availability). At least one platform CI
  job runs the scenario.
- **Deliverable**: Dockerfiles + a runner + one passing E2E test (`results/e2e-setup.md`).

### TOOL-00-06 — VFS-listener performance
- **Question**: Does an `AsyncFileListener` on a busy tool dir add unacceptable overhead?
- **Method**: register the listener on a dir like `/usr/local/bin`; bulk-touch 1000 files with
  and without the listener; measure wall-clock.
- **Pass threshold**: added overhead **< 5 %** (or < 50 ms absolute) on the bulk op. If
  exceeded, recommend the on-access/eager-only fallback (TOOL-03 §3.1).
- **Deliverable**: `results/vfs-perf.md` with the measured numbers + recommendation.

## 3. Integration Points
No `plugin.xml` registration ships from TOOL-00 — prototypes are throwaway. Findings feed
TOOL-01 (descriptor, serialization, async), TOOL-02 (terminal PATH), and TOOL-03 (VFS perf).

## 4. Requirement Coverage

| Requirement | Priority | Acceptance (section) |
|-------------|----------|----------------------|
| TOOL-00-01 | High | §2 TOOL-00-01 |
| TOOL-00-02 | Medium | §2 TOOL-00-02 |
| TOOL-00-03 | Medium | §2 TOOL-00-03 |
| TOOL-00-04 | Medium | §2 TOOL-00-04 |
| TOOL-00-05 | High | §2 TOOL-00-05 |
| TOOL-00-06 | Low | §2 TOOL-00-06 |

## 5. Open Questions

_None — each spike has a defined method, threshold, and deliverable._
