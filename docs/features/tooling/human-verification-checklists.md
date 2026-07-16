---
id: "TOOLING-VERIFY"
title: "Human Verification Checklists"
type: "spec"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# Human Verification Checklists: Unified Lua Toolchain Management (TOOLING Epic)

Manual, human/agent-driven verification scenarios for the TOOLING epic — the checks that
automated unit tests cannot give: live IDE behavior, real-network provisioning, terminal
injection, Windows execution, and the epic's end-to-end user flows. Each scenario is
reproducible from a clean state with an explicit observable result.

**Environments**

- **IDE (Linux)** — the containerized GoLand driven over VNC per the `verify-in-ide` skill
  (`.agents/skills/verify-in-ide/SKILL.md`): container lifecycle via
  `docker/docker-helper.sh`, clean GoLand relaunch (never `docker restart`), plugin jar
  hot-swap, `scrot` + `xdotool` driving, VNC on `localhost:5900`. The sandbox IDE
  (`tooling/gce-builder/gce-builder.sh run runIde`) is an acceptable substitute where a
  scenario says "sandbox".
- **Windows** — the existing **`win11`** VM under KVM/virt-manager on the workstation,
  driven over VNC with the same conventions (screenshot before/after every action; banners
  read via `vnc_ocr_region` OCR). Precondition for every Windows scenario: revert the clean
  snapshot and boot — `virsh snapshot-revert win11 "Fresh Install" && virsh start win11`
  (guest `TESTING\tester`, empty password). **No ISO install** — the VM already exists
  (TOOLING-00 design §2.2). The `Fresh Install` snapshot is a **bare command-line Windows box
  with no IDE/plugin installed** — it covers running provisioned binaries from CMD/PowerShell
  (and is the *preferred* baseline for TOOLING-00-02's SmartScreen/MOTW observation, where a
  pristine box shows true first-run behavior).
- **Windows two-snapshot model:** scenarios needing the *running plugin* — in-IDE
  provisioning (TOOLING-04, scenario 04.5) and integrated-terminal PATH injection on Windows
  (TOOLING-03-12) — are **not** runnable on the bare box. They run against the second
  snapshot, **`IDE Installed`** (GoLand + JetBrains Toolbox), which **already exists**
  (`virsh snapshot-list win11`). Per test: revert `IDE Installed` and boot, sign in / apply
  the JetBrains license via Toolbox if not persisted, hot-swap the current Lunar plugin jar
  (the verify-in-ide technique works the same over VNC on Windows), then drive the check. The
  plugin is **not** baked into the snapshot — it is injected per run so each test uses the
  build under test. Actually running these IDE-side checks is TOOLING-03/-04 verification work
  (plan M2/M3). Keep both snapshots: `Fresh Install` (clean box — binary + SmartScreen tests)
  and `IDE Installed` (in-IDE checks).
- **Builder (Linux)** — the gce-builder VM for shell-spike runs
  (`tooling/gce-builder/gce-builder.sh`).

Scenario format: **Preconditions** (state to start from), **Steps**, **Expected**
(precise observable outcome), **Result** checkbox. Feature/test-case IDs reference the
per-feature `requirements.md` Test Cases tables.

---

## TOOLING-00: De-risking & Technical Spikes

Spike execution is agent/human work by definition — nothing here runs in CI. Results docs
land under `docs/features/tooling/00-de-risking/results/`.

### Scenario 00.1: POSIX PUC-Lua source-build spike run (TOOLING-00-01, TC 1)
- **Preconditions**: gce-builder Linux image with `gcc`, `ar`, `ranlib`; no
  `libreadline-dev` required; `tooling/spikes/tooling-00/build-lua-posix.sh` committed.
- **Steps**:
  1. Run `tooling/spikes/tooling-00/build-lua-posix.sh /tmp/lunar-spike-54`.
  2. Run `/tmp/lunar-spike-54/bin/lua -v`.
  3. Run `/tmp/lunar-spike-54/bin/lua -e 'print(package.path)'`.
  4. Run `ldd /tmp/lunar-spike-54/bin/lua`.
- **Expected**: script exits 0; `lua -v` prints `Lua 5.4.8  Copyright (C) 1994-2025
  Lua.org, PUC-Rio`; `package.path` output starts with
  `/tmp/lunar-spike-54/share/lua/5.4/?.lua` (baked prefix); `ldd` output contains no
  `readline`. Verdict + timings + macosx flag-set + SHA-256 pin recorded in
  `results/posix-source-build.md`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 00.2: Windows prebuilt provisioning — live VM execution (TOOLING-00-02, TC 2)
- **Preconditions**: stage-1 Linux acquisition done
  (`tooling/spikes/tooling-00/fetch-windows-prebuilt.sh` ran; layout assertions and
  SHA-256 pins recorded). The `win11` VM booted from its clean snapshot
  (`virsh snapshot-revert win11 "Fresh Install" && virsh start win11`) and reachable over
  VNC — no IDE needed (command-line only).
- **Steps**:
  1. Connect to the VM over VNC (verify-in-ide driving conventions: screenshot
     before/after every action).
  2. Open PowerShell in the VM; re-acquire both zips in-VM via `Invoke-WebRequest`
     (deliberate — writes the `Zone.Identifier` MOTW stream) and `Expand-Archive` into
     `C:\lunar-spike\bin`.
  3. Run `C:\lunar-spike\bin\lua54.exe -v` and
     `C:\lunar-spike\bin\luarocks.exe --version` in **both** CMD and PowerShell.
  4. Read the banners via VNC screenshot + OCR.
  5. Observe and record: (a) any SmartScreen/MOTW block or prompt on first execution and
     the unblock path used (e.g. `Unblock-File C:\lunar-spike\bin\*`); (b) any
     Defender/AV reaction to the unsigned `lua54.exe`; (c) note the contrast for
     plugin-downloaded files (JVM `HttpRequests` writes no `Zone.Identifier`).
- **Expected**: OCR shows a `Lua 5.4` banner for `lua54.exe -v` and a `3.13.0` version
  line for `luarocks.exe --version` in both shells. `results/windows-prebuilt.md` records
  the SmartScreen/AV observations and the VNC evidence screenshots (the VM is the known
  `win11`/`Fresh Install` baseline — no per-run setup to record).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 00.3: LuaJIT git+make decision spike (TOOLING-00-03, TC 3)
- **Preconditions**: Linux builder with `git`, `make`, `gcc`;
  `tooling/spikes/tooling-00/build-luajit-posix.sh` committed.
- **Steps**:
  1. Run the script (shallow clone `v2.1` **keeping `.git`**, `make`, hand-install:
     `src/luajit` → `bin/lua`, `jit/` → `share/lua/5.1/jit`).
  2. Run `<prefix>/bin/lua -v` and `<prefix>/bin/lua -e 'print(jit.version)'`.
  3. Write the darwin-arm64 paper assessment; apply the design §2.3 decision matrix.
- **Expected**: `lua -v` first line starts with `LuaJIT 2.1`; the `jit.version` command
  exits 0. Either verdict is a valid completion: PASS/FAIL + the binding ship/descope
  decision recorded in `results/luajit-git-make.md` **and** in the risks-doc DR table
  (drives TOOLING-04-13 gating).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 00.4: C-rock install — success and failure signature (TOOLING-00-04, TCs 4–5)
- **Preconditions**: the Scenario 00.1 prefix on the builder; `gcc` on PATH;
  `tooling/spikes/tooling-00/install-crock.sh` committed.
- **Steps**:
  1. Run the script's **Run A**: LuaRocks 3.13.0 configured into the prefix, then
     `<prefix>/bin/luarocks install busted 2.2.0-1`.
  2. Run `<prefix>/bin/busted --version`.
  3. Run **Run B** on a fresh tree with `CC=/nonexistent/cc LD=/nonexistent/cc`
     overrides (deterministic missing-compiler simulation).
  4. Verify the finalized detection heuristic (design §3.2) against both outputs.
- **Expected**: Run A exits 0 and `busted --version` prints a `2.x` version. Run B exits
  non-zero; its verbatim output is captured in `results/c-rock-install.md`; the heuristic
  matches Run B and does **not** match Run A; the guidance-notification copy is recorded
  verbatim (consumed by TOOLING-04-09/TOOLING-07).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 00.5: Feed-pin completeness cross-check (TOOLING-00-05, part of TC 6)
- **Preconditions**: Scenarios 00.1–00.4 done; `LuaProvisioningClasspathSpikeTest` green.
- **Steps**:
  1. Open `src/main/resources/toolchain/toolchain-feed.json`.
  2. Check every `<pin recorded by …>` placeholder for assets actually downloaded by the
     spikes.
- **Expected**: every such placeholder is replaced with the real SHA-256 digest; the feed
  re-parses green in the spike test.
- **Result**: ⬜ Pass / ⬜ Fail

---

## TOOLING-01: Unified Toolchain Model & Registry

TOOLING-01 lands **dark** (no consumer, no UI), so its verification is almost entirely
automated (requirements TCs 1–20). One optional manual spot-check exists until TOOLING-06
provides UI.

### Scenario 01.1: Auto-discover persistence spot-check (TOOLING-01-06/-09/-12; plan Verification Tasks)
- **Preconditions**: dev machine (or sandbox IDE) with at least one real `lua`/`luarocks`
  on PATH; TOOLING-01 merged.
- **Steps**:
  1. From a scratch test or the IDE's internal evaluator, invoke
     `LuaToolchainRegistry.getInstance().autoDiscover()` on a pooled thread.
  2. Trigger a settings save (or close the IDE) and open the application-level
     `lunar.xml`.
- **Expected**: a `<component name="LuaToolchainRegistry">` element exists with a
  `toolInventory` list of `RegisteredToolState` beans matching design §4.3 (id, kindId,
  path, version, product/languageLevel/platform for RUNTIME entries, origin
  `DISCOVERED`, probeStatus, probedAtMtime) and a `globalBindings` map; legacy
  `LuaApplicationSettings` fields are untouched (dark landing).
- **Result**: ⬜ Pass / ⬜ Fail

---

## TOOLING-02: Resolution, Binding & Environments

Live verification is **deferred to TOOLING-05/06** — no user-visible entry points exist
until consumers/UI cut over (plan, Verification Tasks). The one smoke-checkable surface is
the detection notification, once its startup activity is registered by TOOLING-05.

### Scenario 02.1: Environment detection & Adopt notification smoke check (TOOLING-02-14, TC 17 — run after TOOLING-05 Phase 5)
- **Preconditions**: containerized GoLand with the plugin; a project whose base dir
  contains an env-shaped directory (e.g. `<project>/.lua/bin/lua` +
  `<project>/.lua/bin/luarocks`, both executable) that is **not** recorded in the
  project's toolchain state; legacy `HererocksDetectStartup` already deleted (no double
  notification).
- **Steps**:
  1. Open the project; wait for post-startup activities.
  2. Observe the notification area (screenshot).
  3. Click the **Adopt** action on the notification.
  4. Open Settings → Languages & Frameworks → Lua → Lua Project (TOOLING-06) or inspect
     `.idea/lunar.xml`.
- **Expected**: exactly one notification offers adoption of the detected directory.
  After Adopt: both binaries are registered in the inventory with `environmentId` set,
  an environment record for the dir exists and is the **active** environment. Re-opening
  the project does not re-offer the already-recorded directory.
- **Result**: ⬜ Pass / ⬜ Fail

---

## TOOLING-03: Execution & Environment Injection

### Scenario 03.1: Terminal PATH injection for bound tools + runtime (TOOLING-03-12; plan Phase 4 exit criteria)
- **Preconditions**: containerized GoLand; registry has a usable `stylua` bound (project
  or global) and a usable RUNTIME `lua` tool that resolves for the project;
  `META-INF/lunar-terminal.xml` points at
  `net.internetisalie.lunar.toolchain.terminal.LuaShellExecOptionsCustomizer`.
- **Steps**:
  1. Open the project; open a **new** Integrated Terminal tab.
  2. Run `which stylua` and `which lua`.
  3. Run `echo $PATH`.
- **Expected**: `which stylua` prints the bound tool's path; `which lua` prints the
  resolved runtime's path (RUNTIME kinds are included — an improvement over the legacy
  terminal service); `$PATH` has the tool directories prepended **ahead** of the
  inherited PATH, in kind-declaration order with duplicates removed (TCs 10, 17).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 03.2: Fresh-registration cache invalidation, end to end (TOOLING-03-11, TC 16 live)
- **Preconditions**: Scenario 03.1 environment; a second usable tool binary on disk that
  is *not yet registered* (e.g. `/opt/x/luacheck`).
- **Steps**:
  1. Open a terminal; note `echo $PATH` (no `/opt/x`).
  2. Register the new tool (Toolchain page **Add**, or `registerTool` via a scratch
     call), bind it for the project.
  3. Open a **new** terminal tab (do not reuse the old session).
  4. Run `echo $PATH` and `which luacheck`.
- **Expected**: the new terminal's PATH contains `/opt/x` prepended and `which luacheck`
  resolves to `/opt/x/luacheck` — no stale cache (the legacy
  `registerTool`-fires-no-event defect is fixed by the topic-invalidated cache).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 03.3: Per-project terminal isolation (carried forward from TOOL-02 Phase 4 parity)
- **Preconditions**: two projects open in separate windows with different project
  bindings for the same kind.
- **Steps**:
  1. Open a new terminal in each project window.
  2. Run `echo $PATH` in both.
- **Expected**: each terminal's PATH front matches its own project's resolved tool
  directories (the builder is a PROJECT service; no cross-project leakage).
- **Result**: ⬜ Pass / ⬜ Fail

---

## TOOLING-04: Native Provisioning Engine

### Scenario 04.1: Live provisioning session — Linux container, real network (Acceptance Criteria; plan Verification Tasks)
- **Preconditions**: containerized GoLand with network access; `gcc`/`ar`/`ranlib`/`make`
  present in the container; a project open; no environment at the target rootDir.
- **Steps**:
  1. Tools menu → **Lua Toolchain** → **Provision Lua Toolchain…**.
  2. In the dialog: name the environment, pick rootDir `<project>/.lua`, runtime
     `lua 5.4`, LuaRocks `latest`, check `luacheck`.
  3. OK; watch the background task (progress text + fraction).
  4. When done, inspect the Toolchain settings page (or `lunar.xml`) and the project's
     active environment.
  5. Run a Lua script via a run configuration; open a Lua file with a lint-able issue.
- **Expected**: the task names the current component + strategy while running; on success
  an environment exists at the rootDir with `bin/lua`, `bin/luarocks`, `bin/luacheck`
  (or `tools/...` per layout), `.lunar-env.json` written; registry shows the tools with
  origin `PROVISIONED` and a shared `environmentId`; the environment is **active**
  (TC 1, TC 13). The script runs with the provisioned `lua`; luacheck annotations appear
  from the provisioned binary. No Python/hererocks involved at any point.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 04.2: Idempotent re-provision (TOOLING-04-10, TC 11 live)
- **Preconditions**: Scenario 04.1 completed successfully.
- **Steps**:
  1. Re-run **Provision Lua Toolchain…** with the identical request (same name, rootDir,
     versions).
- **Expected**: completes near-instantly with 0 downloads and 0 build commands; an info
  notification "already up to date"; the environment is (re-)activated.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 04.3: Actions menu & dialog validation (TOOLING-04-12, TCs 15–16 live)
- **Preconditions**: plugin loaded, project open.
- **Steps**:
  1. Open the Tools menu; expand the **Lua Toolchain** popup group.
  2. Open **Provision Lua Toolchain…**; clear the name field; set rootDir to an
     existing non-empty directory that is not a Lunar environment.
- **Expected**: the group contains exactly Provision Lua Toolchain… / Change Toolchain
  Versions… / Recreate Environment / Remove Environment / Run Test Matrix… / Provision
  Version Matrix…; no `Lunar.Hererocks.*` lifecycle actions remain. The dialog shows
  field-level validation ("Name is required" / "Directory is not empty and is not a
  Lunar environment") and OK is disabled until fixed.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 04.4: Cancellation mid-provision (TOOLING-04-15, TC 19 live)
- **Preconditions**: as Scenario 04.1, fresh rootDir.
- **Steps**:
  1. Start a provision of `{lua 5.4, luarocks latest}`.
  2. Cancel the background task during the LuaRocks build step.
- **Expected**: the running process is terminated promptly; balloon "Provisioning
  cancelled"; `.lunar-env.json` still lists the completed `lua` component (manifest
  consistent); **nothing** registered in the registry and no environment activated.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 04.5: Windows prebuilt provisioning — manual QA before release (TOOLING-04-07, TC 6)
The Linux-side automated coverage is an asset-download dry-run only (no Windows host in
the CI loop) — the plan flags this path **for manual QA before release**.
- **Preconditions**: the `win11` VM over VNC (booted from `Fresh Install`). The VM is a
  **bare command-line box with no IDE/plugin**, so the in-IDE *Provision…* flow can't run
  there. Instead, obtain the tree the provisioner would produce for a Windows target — run
  the provisioner against a Windows target on a dev machine, or assemble the tree per the
  TOOLING-00-02 acquisition — and copy it to `C:\lunar-env\` on the VM. (The true in-IDE
  Windows provisioning flow, invoking *Provision…* from GoLand on Windows, needs a
  plugin-loaded IDE on the guest — run it against the **`IDE Installed`** snapshot with the
  Lunar jar hot-swapped in; on the bare `Fresh Install` box this scenario is tree-copy +
  execute only.)
- **Steps**:
  1. Place the provisioned `{lua 5.4, luarocks latest}` Windows tree at `C:\lunar-env\`.
  2. Inspect the rootDir tree.
  3. Run `<rootDir>\bin\lua.exe -v` and `<rootDir>\bin\luarocks.exe --version` (CMD and
     PowerShell), reading banners via VNC/OCR.
- **Expected**: no compiler probe ran; `lua-5.4.x_Win64_bin.zip` extracted with canonical
  `lua.exe`/`luac.exe` copies alongside `lua54.exe`; standalone `luarocks.exe` present;
  `luarocks-config.lua` written at rootDir; both banners correct; two PROVISIONED tools
  registered. A C-rock item (e.g. busted) fails fast with the v1 guidance ("C rocks are
  not supported by the provisioner on Windows in v1").
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 04.6: C-toolchain preflight message (TOOLING-04-05, TC 5 live — optional)
- **Preconditions**: a container/VM without `gcc`/`cc` on PATH (or PATH doctored to hide
  them).
- **Steps**:
  1. Provision `(lua, "5.4")`.
- **Expected**: the component fails **before any download** with the actionable message
  naming the missing tools and per-OS install commands (`sudo apt install
  build-essential` / `xcode-select --install`) and suggesting a prebuilt version.
- **Result**: ⬜ Pass / ⬜ Fail

---

## TOOLING-05: Consumer Migration & Legacy Removal

### Scenario 05.1: Debugger smoke after runtime cutover (plan Phase 3 exit criteria)
- **Preconditions**: sandbox IDE (`run runIde`) after TOOLING-05 Phase 3; a project with
  a resolvable RUNTIME tool and a Lua debug run configuration (DBGp).
- **Steps**:
  1. Set a breakpoint in a Lua file.
  2. Start the run configuration in Debug mode.
- **Expected**: the DBGp attach works unchanged — the breakpoint hits, stack/variables
  render; the command line was built by the TOOLING-03 factory with env-builder
  injection (caller-owned `LUA_INIT`/`LUNAR_*` debug variables intact).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 05.2: Post-cutover bind → lint → run → matrix flow (Acceptance Criteria; plan Phase 6 — the real-flow DoD gate)
- **Preconditions**: containerized GoLand after the final Phase 6 removal commit;
  registry seeded (via auto-discover, TOOLING-06 UI, or provisioning) with a usable
  runtime, `luacheck`, and `luarocks`; two provisioned environments for the matrix step
  (may reuse Scenario E2E.3's setup).
- **Steps**:
  1. Bind a RUNTIME tool and a `luacheck` tool for the project (TOOLING-06 pages, or
     registry seeding if 06 has not landed).
  2. Open a Lua file containing a luacheck-detectable problem.
  3. Run a Lua script run configuration.
  4. Tools → Lua Toolchain → **Run Test Matrix…** across the environments.
- **Expected**: lint annotations appear from the bound luacheck (no
  `/usr/local/bin/luacheck` default anywhere); the script runs with the bound runtime and
  env-builder PATH/LUA_PATH/LUA_CPATH; each matrix row executes with **that row's**
  environment luarocks (TC 12) — a row lacking one shows a FAIL row with a "not
  provisioned" message without aborting the others.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 05.3: Clean-break load tolerance on a real legacy project (TOOLING-05-09, TC 14 live)
- **Preconditions**: a project whose `.idea/lunar.xml` (and an application `lunar.xml`)
  still contain pre-TOOLING tags (`interpreters`, `toolInventory`, `hererocksEnvs`,
  `interpreterMode`, `explicitInterpreter`, `explicitTarget`, `activeEnvId`,
  `projectToolBindings`) — e.g. captured from a pre-cutover sandbox.
- **Steps**:
  1. Open the project in the post-cutover IDE.
  2. Check `idea.log` for deserialization errors.
  3. Change any Lua setting to force a save; re-inspect both `lunar.xml` files.
- **Expected**: the project opens silently (no error notifications, no exceptions in the
  log); retained fields (`languageLevel`, `sourcePath`, `rocksServerUrl`) survive; the
  re-serialized files contain **none** of the legacy tags. CHANGELOG documents that
  tools/interpreters must be re-registered and environments re-provisioned once.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 05.4: New Project wizard provisions via the new stack (TOOLING-05-07, TC 13 live — optional)
- **Preconditions**: post-Phase-4 IDE build; network available.
- **Steps**:
  1. File → New Project → Lua (rocks generator); check the "provision" option, select
     Lua 5.4.
  2. Generate and open the project.
- **Expected**: a TOOLING-04 provision request (lua 5.4 + luarocks) is queued post-open
  and completes; the resulting environment is bound/active via TOOLING-02; no
  hererocks/Python involvement.
- **Result**: ⬜ Pass / ⬜ Fail

---

## TOOLING-06: Settings UI Consolidation

These are the human-only rows of the requirements Test Cases note (tree placement,
toolbar icons, dialog opening, Settings search) — plan Phase 4's VNC checklist.

### Scenario 06.1: One Lua settings tree (TOOLING-06-01/-08)
- **Preconditions**: containerized GoLand (or `runIde` sandbox) with the plugin; a
  project open.
- **Steps**:
  1. Open Settings; expand Languages & Frameworks.
  2. Inspect the Lua node and its children.
  3. Expand Settings → Tools and scan for Lua-related entries.
- **Expected**: exactly `Lua` → { `Lua Project`, `Toolchain` } (alphabetical sibling
  order); **nothing** Lua-related under Tools (Lua Tools, LuaRocks, LuaCheck pages gone);
  the Lua app page no longer shows an interpreters table.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 06.2: Inventory table & toolbar actions live (TOOLING-06-02/-03/-12)
- **Preconditions**: as 06.1; at least one real tool on PATH; one registered entry whose
  binary was deleted (for the health rendering check).
- **Steps**:
  1. Open the Toolchain page; inspect the table.
  2. Click **Add** and pick a tool binary via the file picker.
  3. Click **Auto-Discover**; while it runs, interact with the IDE (move mouse, open
     menus) — watch for UI freezes.
  4. Select a row; click **Re-check**; then **Remove**.
  5. Click **Provision…**.
  6. Hover the broken entry's Health cell.
- **Expected**: table columns are exactly Kind, Name, Path, Version, Origin, Health;
  RUNTIME entries appear as ordinary rows with product/version in Name. Add registers
  and probes off the EDT (no spinner freeze); Auto-Discover populates rows with **no EDT
  freeze even on a slow PATH**; Re-check updates health; Remove deletes the row;
  Provision… opens the TOOLING-04 dialog and does nothing else. The broken entry renders
  `Missing` with the full reason as tooltip.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 06.3: Project page — bindings, environment, resolved runtime (TOOLING-06-05/-06/-07)
- **Preconditions**: inventory with ≥2 tools of one kind and a usable RUNTIME tool; a
  project with ≥1 environment record.
- **Steps**:
  1. Open the Lua Project page.
  2. Inspect the environment selector and the per-kind binding combos.
  3. Select a specific tool in one binding combo; Apply.
  4. Check the resolved-runtime display.
- **Expected**: environment combo lists environments by name plus `None (use bindings)`;
  one binding row per built-in kind, each combo showing `Inherit` (with what inheriting
  resolves to) plus that kind's inventory entries; Apply persists the binding and the
  dependent UI/consumers react (topic fired — e.g. a new terminal reflects it). The old
  interpreter combo, platform/version combos, and hererocks-managed checkbox are gone;
  the read-only display shows the resolved runtime (path, product, version) and derived
  language level — or `No runtime configured` with the default level when nothing
  resolves. Rocks server URL override, source path, and underscore suppression still
  work.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 06.4: Settings search (TOOLING-06-10)
- **Preconditions**: as 06.1.
- **Steps**:
  1. In the Settings search box, type `Toolchain`.
  2. Then type `luacheck arguments`.
- **Expected**: both searches navigate to/highlight the Toolchain page (stable
  searchable IDs; `buildSearchableOptions` remains disabled, so navigation-by-name is
  the expectation, not option-level highlighting).
- **Result**: ⬜ Pass / ⬜ Fail

---

## TOOLING-07: Health Monitoring & Diagnostics

Plan Verification Tasks: live verification per the `verify-in-ide` skill in the
sandbox/containerized IDE.

### Scenario 07.1: Bound tool deleted → balloon + banner + fix flow (TOOLING-07-03/-04/-05; TC-06, TC-04)
- **Preconditions**: IDE with a usable `luacheck` tool bound for the project (and the
  LuaCheck inspection enabled); a `.lua` file open; the tool's binary at a disposable
  path.
- **Steps**:
  1. Delete the luacheck binary from disk (`rm <path>`).
  2. Wait for the batched revalidation (≥ 500 ms merge window).
  3. Observe notifications and the open editor.
  4. Trigger revalidation again (touch an unrelated watched path or re-check) — confirm
     no duplicate balloon.
  5. Click the banner's **Configure toolchain** link.
  6. Fix the tool (restore the binary or re-bind a working one); return to the editor.
- **Expected**: exactly **one** WARNING balloon on `notification.group.lunar.tools`
  naming the tool; the editor shows one banner "Lua tool 'luacheck' is unavailable:
  Binary missing" whose *Configure toolchain* link opens the TOOLING-06 Toolchain page
  (the single place to fix it); no re-notification for the persistently broken tool.
  After the fix + revalidation the banner disappears. Health state distinguishes
  file-missing from probe-failed (PRD Use Case 4).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 07.2: Environment root deleted (TOOLING-07-05; TC-07)
- **Preconditions**: a provisioned/adopted environment active for the project, its
  member tools registered with `environmentId`.
- **Steps**:
  1. `rm -rf <env rootDir>` outside the IDE.
  2. Wait for revalidation; observe notifications; inspect the member tools' health on
     the Toolchain page.
- **Expected**: exactly one balloon naming the environment ("Lua environment '<name>'
  was deleted from disk…"), once per env per session; member tools show
  `Environment root missing: <rootDir>` as their health reason; the `rm -rf` burst
  produced a single revalidation pass (no balloon storm).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 07.3: No-runtime banner + dismiss (TOOLING-07-04 rule 1; TC-05)
- **Preconditions**: project where no RUNTIME-kind tool resolves usable (empty inventory
  or all runtime entries broken); a `.lua` file open.
- **Steps**:
  1. Observe the editor banner.
  2. Click **Dismiss**; confirm the banner stays gone for other Lua files.
  3. Register/bind a usable `lua`; reopen the file.
- **Expected**: banner "No usable Lua runtime for this project." with *Configure
  toolchain* and *Dismiss*; Dismiss suppresses it project-wide until IDE restart;
  a usable runtime removes it. Non-Lua files never show a banner.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 07.4: Diagnostics snapshot in idea.log (TOOLING-07-06/-07; TC-08)
- **Preconditions**: populated registry (≥2 tools, one broken), a binding, an active
  environment.
- **Steps**:
  1. Trigger a revalidation (or run the "Lua: Toolchain Diagnostics" action if shipped).
  2. `grep TOOLCHAIN-DIAG` in the sandbox log
     (`build/idea-sandbox/GO-*/log/idea.log` or the container's log path).
- **Expected**: `[TOOLCHAIN-DIAG]` lines per the §4.1 grammar: one `tool …` line per
  inventory entry (kind/path/origin/version/env/health), one `binding …` line per
  binding, one `env id=… active=true` line, one `resolve kind=… ->` line per known kind
  (`-> none` where unresolved).
- **Result**: ⬜ Pass / ⬜ Fail

---

## Epic End-to-End (PRD Use Cases)

Run these after TOOLING-05/06/07 have all landed — they are the epic's Definition-of-Done
flows (PRD "Key Use Cases" + Success Metrics: *"live VNC verification of the provisioning
and binding flows"*). Environment: containerized GoLand over VNC unless stated.

### Scenario E2E.1: Zero-to-toolchain on a fresh machine (PRD Use Case 1 → TOOLING-04, -02, -03)
- **Preconditions**: a **fresh** container image with no Lua, no LuaRocks, and no Python
  installed (verify: `which lua luarocks python3 hererocks` all empty or python absent);
  build toolchain (`gcc`, `make`) present; network available; a Lua project open.
- **Steps**:
  1. Tools → Lua Toolchain → Provision Lua Toolchain…; pick Lua 5.4 + latest LuaRocks +
     luacheck, stylua, busted; OK and wait.
  2. Run a Lua script run configuration.
  3. Open a Lua file with a lint problem — check annotations.
  4. Reformat a Lua file (stylua external formatter).
  5. Run a busted test run configuration.
  6. Open a new terminal: `which lua luarocks luacheck stylua busted`.
  7. Open the LuaRocks package browser and perform a search.
- **Expected**: everything provisions into the project-scoped environment, which is
  bound/active automatically; run, lint, format, test, terminal, and package browser all
  use the provisioned binaries — with **no Python, no pre-installed Lua, and no manual
  PATH work** at any step. This is also the CI-image E2E success metric (provision 5.4 +
  luarocks + luacheck, run lint + a script, all green).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario E2E.2: Bring-your-own binaries — discover, inspect, bind (PRD Use Case 2 → TOOLING-01, -02, -05, -06)
- **Preconditions**: container with system-installed tools in standard locations
  (e.g. `apt`-installed `lua5.4`, `luarocks`, `luacheck`; a `stylua` in
  `/usr/local/bin`); empty Lunar inventory.
- **Steps**:
  1. Settings → Lua → Toolchain → **Auto-Discover**.
  2. Inspect the resulting rows (kinds, versions, health) — including the Debian-style
     `lua5.4` glob discovery.
  3. Set a global default binding for one kind; set a *project* binding for another
     (Lua Project page).
  4. Exercise luacheck (open a file with an issue), a LuaRocks action (search/install),
     and stylua (reformat).
- **Expected**: PATH + well-known-dir discovery finds the tools and shows them in **one**
  inventory with probed version + health per row (interpreters included); luacheck and
  luarocks honor the bindings exactly the way stylua does (the four legacy bypass
  patterns are gone); project binding overrides the global default where both are set.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario E2E.3: Multi-version testing via environments + matrix (PRD Use Case 3 → TOOLING-02, -04, -05)
- **Preconditions**: a library project with a busted test suite and rockspec; network;
  build toolchain present.
- **Steps**:
  1. Tools → Lua Toolchain → **Provision Version Matrix…** (batch dialog): base dir
     `<project>/envs`, rows `lua 5.1.5` and `lua 5.4.8` (+ LuaJIT if TOOLING-00-03
     decided PASS and the feed gates it open).
  2. Wait for the concurrent background tasks to finish; confirm the environment records
     exist.
  3. Tools → Lua Toolchain → **Run Test Matrix…** across the environments.
  4. Inspect the matrix results tool window per row.
- **Expected**: one environment per (kind, version) row under
  `envs/lua-5.1.5` / `envs/lua-5.4.8`, each with its own lua + luarocks; the matrix runs
  each row strictly with **that environment's** toolchain (per-environment resolution —
  a 5.1 row never uses the project's 5.4 luarocks); results render per row; an
  environment missing a tool yields a FAIL row without aborting the rest.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario E2E.4: Understanding and fixing a broken setup (PRD Use Case 4 → TOOLING-07, -06)
- **Preconditions**: Scenario E2E.1 or E2E.2 state — a bound, usable `luacheck`; a Lua
  file open.
- **Steps**:
  1. Delete the bound luacheck binary from disk.
  2. Wait for revalidation; note every user-visible surface (balloons, banners).
  3. Follow the banner's settings link; observe the health rendering of the broken tool.
  4. Separately: replace the binary with a non-Lua executable of the same name (probe
     will fail) and re-check — compare the rendered health state.
  5. Fix the tool (restore/re-bind) and confirm all surfaces clear.
- **Expected**: exactly **one** banner/balloon flow naming the tool and the reason, with
  a settings link landing on the **single** page (Toolchain) where it can be fixed —
  no duplicate or contradictory notifications from other subsystems. The health display
  distinguishes "file missing" (`Missing` / "Binary missing") from "version probe
  failed" (probe-failure reason). After the fix, banner and broken-health rendering
  clear without an IDE restart.
- **Result**: ⬜ Pass / ⬜ Fail

---

## Sign-off

> **Note (2026-07-16):** the TOOLING epic is `done` and was verified **per-feature** — see each
> feature's `requirements.md` verification notes (e.g. TOOLING-07's live-verification note dated
> 2026-07-09 in `07-health-and-diagnostics/requirements.md`). The formal scenario-by-scenario
> sign-off below was never performed, so the table is vacuously empty; it is kept for a future
> full verification pass.

| Section | Scenarios | Verified by | Date |
|---------|-----------|-------------|------|
| TOOLING-00 | 00.1–00.5 | | |
| TOOLING-01 | 01.1 | | |
| TOOLING-02 | 02.1 | | |
| TOOLING-03 | 03.1–03.3 | | |
| TOOLING-04 | 04.1–04.6 | | |
| TOOLING-05 | 05.1–05.4 | | |
| TOOLING-06 | 06.1–06.4 | | |
| TOOLING-07 | 07.1–07.4 | | |
| Epic E2E | E2E.1–E2E.4 | | |
