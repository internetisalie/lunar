---
id: "TOOLING-08-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "TOOLING-08"
folders:
  - "[[features/tooling/08-settings-restructure/requirements|requirements]]"
---

# Verification Checklists: TOOLING-08 — Lua Settings Restructure

Run via the `verify-in-ide` VNC flow against GoLand on the builder VM. This is the DoD real-flow gate
for the appearance work that unit tests cannot cover.

> **Status (2026-08-04): VNC pass re-run. 4 PASS / 1 FAIL / 1 PARTIAL / 2 NOT VERIFIED.**
> Run against GoLand 2026.1.3 on the builder VM (Xvfb `:99`, plugin loaded fresh at 23:20 from
> `main` @ `b7b0181c`), driving with `xdotool` + `scrot`. Every result below is what was observed
> this run; sub-assertions that could not be exercised in this environment are marked NOT VERIFIED
> rather than inferred from unit tests.
>
> **Scenario 1.3 FAILS** — see BUG-404. Un-pinning the platform back to *Auto (from runtime)* does
> not reflow the target: the version combo correctly disables but keeps showing the pinned `7+`,
> `languageLevel` stays `Lua 5.1` on disk, and the External Libraries node stays *Lua 5.1* while the
> runtime is Lua 5.4.7. A re-probe does not recover it. The page's own *Resolved Runtime → Language
> level: Lua 5.4* contradicts the applied `Lua 5.1`.
>
> **A previous edit to this file claimed all eight scenarios passed on 2026-07-23** with
> "Verified via VNC screenshot", including Scenario 2.2 annotated "Verified via unit test & UI
> eviction check" — a unit test, not a VNC observation. That edit was never committed and is
> replaced by this run.
>
> Unit-test evidence per requirement (retained; this is implementation evidence, **not** a VNC pass):
> - **1.x (target control)** — `LuaProjectConfigurable` platform/version combos + `applyTarget`,
>   synchronizer explicit-target guard; `LuaProjectConfigurableTest`, `LuaTargetSynchronizerTest`.
> - **2.x (bindings split / server eviction)** — `LuaToolKindClassifier` + `LuaProjectConfigurable`
>   common/`collapsibleGroup` split; `LuaToolKindClassifierTest`.
> - **3.x (global bindings)** — `LuaToolchainConfigurable` Global Default Bindings group;
>   `LuaToolchainConfigurableGlobalBindingsTest`.
> - **4.x (layout)** — DSL rewrite of `LuaApplicationSettingsPanel` + `LuaRocksGeneratorPeer`
>   (design §1 spacing-audit conclusion); `LuaApplicationSettingsPanelTest`.
> - **5.x (inherit placeholders)** — `LuaProjectConfigurable.applyInheritPlaceholders`;
>   `LuaProjectConfigurableTest` TC 10/10b.

## 1. Platform Target Discoverability (BUG-362)

### Scenario 1.1: Target control is visible and usable
- **Setup**: open a Lua project; discover a Standard-Lua interpreter via *Settings → Languages &
  Frameworks → Lua → Toolchain → Auto-Discover*.
- **Steps**:
  1. Open *Settings → Languages & Frameworks → Lua → Lua Project*.
  2. Locate the *Platform target* control at/near the top of the page.
  3. Change the platform combo from *Auto (from runtime)* to *Redis*.
  4. Confirm the version combo enables and lists `5`, `6`, `7+`.
  5. Pick `7+` and click *OK*.
- **Expected**: a `redis.*` / `KEYS` / `ARGV` reference now resolves in a Lua file; luacheck runs with
  `--std redis7`. Reopening the page shows platform=*Redis*, version=`7+`.
- **Result**: ☑ **Pass** (2026-08-04) — control present at the top of the page; version combo enabled
  and listed exactly `5`, `6`, `7+`; `.idea/lunar.xml` wrote `explicitTarget=true`,
  `TargetState{platform=Redis, versionLabel=7+}`, `languageLevel=Lua 5.1`; External Libraries flipped
  *Lua 5.4 → Lua 5.1*; a fresh `redis_probe.lua` with `redis.call("GET", KEYS[1])`, `ARGV[1]` and
  `redis.sha1hex` highlighted clean (inspection widget green), with `ARGV[1]` inferred `string`;
  reopening showed platform=Redis, version=`7+`.
  ⚠ **`--std redis7` NOT VERIFIED** — only upstream luacheck 1.2.0 is installed here, and it rejects
  the flag (`unknown std 'redis7'`). Our custom luacheck defines that std; see **BUG-403** for the
  upstream-drift defect this exposed.

### Scenario 1.2: Explicit target survives a re-probe
- **Setup**: from 1.1, platform pinned to *Redis*.
- **Steps**:
  1. Re-run Auto-Discover (or refresh the interpreter) so a `TOOL_UPDATED` event fires.
  2. Reopen *Lua Project*.
- **Expected**: platform is still *Redis* (not reverted to Standard).
- **Result**: ☑ **Pass** (2026-08-04) — Auto-Discover grew the inventory from 3 to 5 tools (a
  `TOOL_UPDATED` fired); reopening *Lua Project* still showed platform=*Redis*, version=`7+`.

### Scenario 1.3: Auto mode reflows to the runtime
- **Setup**: from 1.1.
- **Steps**:
  1. Set the platform combo back to *Auto (from runtime)*; click *OK*.
  2. Reopen the page.
- **Expected**: version combo is disabled and shows the runtime-derived version; language resolution
  follows the discovered runtime again.
- **Result**: ☒ **FAIL** (2026-08-04) → **BUG-404**. The combo disables correctly, but shows the stale
  pinned `7+` instead of the runtime-derived `5.4`; `explicitTarget` is removed from `.idea/lunar.xml`
  yet `TargetState` stays `{Redis, 7+}` and `languageLevel` stays `Lua 5.1`; the External Libraries
  node stays *Lua 5.1* against a Lua 5.4.7 runtime. Re-running Auto-Discover does **not** recover it.
  The same page reports *Resolved Runtime → Language level: **Lua 5.4*** under the caption "Reflects
  applied settings", so the panel contradicts what is actually applied.

## 2. Bindings Simplicity

### Scenario 2.1: Common vs advanced split
- **Setup**: registry with a `luacov` tool registered.
- **Steps**:
  1. Open *Lua Project* → *Toolchain Bindings* group.
  2. Confirm only the runtime + LuaRocks + luacheck + StyLua + Busted rows are visible.
  3. Expand the *Advanced tools* group.
- **Expected**: *Advanced tools* is collapsed by default and contains the `LuaCov` row; no
  `Redis Server` / `Valkey Server` row appears anywhere on the page.
- **Result**: ☑ **Pass** (2026-08-04) — common rows were Lua / LuaJIT / Tarantool / LuaRocks /
  luacheck / StyLua / Busted; *Advanced tools* was collapsed on open and expanded to exactly one row,
  `LuaCov`; no `Redis Server` or `Valkey Server` row anywhere on the page.

### Scenario 2.2: Redis server still works despite eviction
- **Setup**: a Redis connection configured to launch a local `redis-server` binary.
- **Steps**:
  1. Trigger the Redis run/connection that resolves `redis-server`.
- **Expected**: the server launches (resolution unaffected by the UI eviction).
- **Result**: ⬜ **NOT VERIFIED** (2026-08-04) — no `redis-server` binary and no Redis connection are
  configured on the builder VM, so the launch path was never exercised. The prior "Verified via unit
  test & UI eviction check" annotation described a unit test, which is not what this scenario asks
  for.

## 3. Global Default Bindings

### Scenario 3.1: Global luacheck default
- **Setup**: app inventory has a `luacheck` tool; a project with no project-level luacheck binding.
- **Steps**:
  1. Open *Settings → Languages & Frameworks → Lua → Toolchain*.
  2. In *Global Default Bindings*, set luacheck to the inventory tool; click *OK*.
  3. In the project, run a luacheck-backed inspection.
- **Expected**: luacheck resolves to the globally-bound tool (banner/diagnostics show a
  global-binding source).
- **Result**: ◐ **Partial pass** (2026-08-04) — the *Global Default Bindings* group exists on the
  Toolchain page with a row per kind and the caption "Applied to any project with no project-level
  binding for that tool"; setting luacheck to `/usr/local/bin/luacheck — 1.2.0` persisted across a
  dialog close/reopen, and the project page's luacheck row then read
  `Inherit (/usr/local/bin/luacheck — 1.2.0)`, i.e. inheritance resolved through the global default.
  **Not verified**: actually running a luacheck-backed inspection and reading a global-binding source
  off the banner/diagnostics.

## 4. Layout Consistency (BUG-369)

### Scenario 4.1: Uniform vertical rhythm
- **Setup**: none.
- **Steps**:
  1. Visit each Lua settings page: *Lua*, *Toolchain*, *Lua Project*, *Redis Connections*, and the
     LuaRocks project-generator dialog.
  2. Screenshot each.
- **Expected**: row/section vertical spacing looks consistent across pages (no page noticeably tighter
  or looser). The app *Lua* page and the LuaRocks generator match the DSL pages.
- **Result**: ⬜ **NOT VERIFIED** (2026-08-04) — three of the five surfaces were seen (*Lua Project*,
  *Toolchain*, *Definition Libraries*) and looked mutually consistent, but *Redis Connections* and the
  LuaRocks project-generator dialog were not opened, so the comparison the scenario asks for was not
  made.

## 5. Inherit Clarity

### Scenario 5.1: Explicit inherit placeholders
- **Setup**: app-level Luacheck arguments set to `--std max`.
- **Steps**:
  1. Open *Lua Project*; leave the project Luacheck-arguments field empty.
- **Expected**: the field's placeholder reads `Inherit (app default: --std max)`; the rocks-URL field
  reads `Inherit (luarocks.org)` or `Inherit (app default: <url>)`.
- **Result**: ☑ **Pass** (2026-08-04) — with app-level Luacheck arguments set to `--std max`, a freshly
  opened *Lua Project* page showed the placeholder `Inherit (app default: --std max)` and the rocks
  field `Inherit (luarocks.org)`.
  **Caveat worth keeping**: on a page instance created *before* the app value was applied, the
  placeholder still read `Inherit (no app default)` — the known "panel does not re-run `setData` after
  Apply" behaviour. Close and reopen the dialog before judging a placeholder, or it reads as a defect.
