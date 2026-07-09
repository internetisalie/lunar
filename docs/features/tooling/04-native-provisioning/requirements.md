---
id: "TOOLING-04"
title: "04: Native Provisioning Engine"
type: "feature"
status: "done"
priority: "high"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-04: Native Provisioning Engine

## Overview

The in-plugin replacement for the Python `hererocks` provisioner (user decision, 2026-07-05:
no Python dependency; manage more than lua/luarocks). `LuaToolProvisioner.provision(project,
LuaProvisionRequest)` downloads/builds a Lua runtime, LuaRocks, and dev tools (luacheck,
stylua, busted, luacov, lua-language-server) into an environment tree, then registers every
produced binary as a `PROVISIONED` tool in the TOOLING-01 registry and activates the
environment via TOOLING-02. Strategy order, URL patterns, build recipes, and tree layout
follow the [hererocks dossier](../research-hererocks-dossier.md) and the
[platform provisioning research](../research-platform-provisioning.md); the responsibility
boundary is [architecture contract §6](../tooling-architecture.md).

## Scope

### In Scope
- `toolchain.provision` package: orchestrator, per-kind ordered strategies
  (`ReleaseBinaryStrategy`, `SourceBuildStrategy`, `LuaRocksInstallStrategy`), contract §6.
- Bundled JSON version feed (JdkList-style) with alias tables and SHA-256 pins; parser,
  Kotlin data model, and maintainer update procedure.
- Download → size check → SHA-256 → extract pipeline (`HttpRequests`, Guava `Hashing`,
  `Decompressor`), filename-keyed download cache under `PathManager.getSystemPath()`.
- POSIX PUC-Lua source build using hererocks' exact per-translation-unit recipe (baked
  `LUA_PATH_DEFAULT`/`LUA_CPATH_DEFAULT`, readline OFF); POSIX LuaRocks
  `configure`/`make build`/`make install`.
- Windows prebuilt-first: LuaBinaries Lua, standalone `luarocks-*-windows-64.zip`,
  `luacheck.exe`, StyLua/LuaLS release assets. No compilation on Windows in v1.
- LuaJIT git+make strategy (POSIX only), gated on the TOOLING-00-03 spike outcome, with a
  defined descope fallback.
- Tool installs into the environment via `<prefix>/bin/luarocks install <rock> [<ver>]`
  through the TOOLING-03 execution service (INSTALL timeout class).
- Environment tree manifest (`.lunar-env.json`) with identifiers-hash idempotency
  (hererocks-manifest semantics + JdkInstaller marker-file idea).
- POSIX C-toolchain preflight (cc/gcc/ar/ranlib/make presence probe) with actionable
  failure messaging.
- Registration of results: `LuaRegisteredTool(origin = PROVISIONED, environmentId = …)` in
  the TOOLING-01 registry; `LuaEnvironmentState` record + activation via TOOLING-02 ops.
- Replacement of the hererocks action group and dialog: Provision / Change Versions /
  Recreate / Remove / Batch Provision, with a Kotlin-UI-DSL provisioning dialog.

### Out of Scope
- Registry/model internals — TOOLING-01 (this feature calls its registration API).
- Resolution precedence and environment lifecycle semantics — TOOLING-02 (this feature
  calls its upsert/activate/remove ops).
- Execution-service internals, PATH/LUA_PATH/LUA_CPATH injection for consumers —
  TOOLING-03 (this feature is a client, INSTALL timeout class).
- Settings UI tree — TOOLING-06. Health monitoring — TOOLING-07.
- **Git-source installs (`repo@ref`)** — explicitly deferred (dossier risk 7); Future Work.
- Windows source builds / MSVC bootstrap (dossier risk 1) — Windows is prebuilt-only in v1.
- readline-enabled builds (dossier risk 4 — readline is OFF, no opt-in in v1).
- Inline lua.org bug patches (hererocks `--patch`) — not applied in v1.
- Activation shell scripts (hererocks `bin/activate*`) — superseded by TOOLING-03 env
  injection; not generated.
- moonjit / RaptorJIT; remote/WSL targets (PRD non-goal); legacy `rocks/env` code deletion
  and matrix-runner migration to new envs — TOOLING-05 (only plugin.xml action
  registrations are swapped here).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TOOLING-04-01 | **Provision orchestration** | M | `LuaToolProvisioner.provision(project, request)` runs each requested `(kindId, versionSpec)` through its kind's ordered strategies on one cancellable `Task.Backgroundable`, serialized per normalized rootDir (second concurrent request for the same dir is refused with a warning balloon, no work started). |
| TOOLING-04-02 | **Bundled version feed** | M | A JSON classpath resource lists provisionable versions per kind with aliases (`latest`, `5.4`, …), per-OS/arch assets, URL(s), SHA-256, size, packaging, and layout. Alias/version resolution is deterministic; unknown specs fail with the known-version list. |
| TOOLING-04-03 | **Download/verify/extract pipeline** | M | Artifacts are fetched with `HttpRequests…saveToFile(indicator)`, size-checked, SHA-256-verified (Guava `Hashing`), and extracted with `Decompressor.Tar`/`Zip(withZipExtensions)` + `removePrefixPath`. Downloads are cached by filename under `<system>/lunar/downloads` and re-verified on every reuse; mismatch deletes and re-downloads. |
| TOOLING-04-04 | **POSIX PUC-Lua source build** | M | The exact hererocks recipe: patch `src/luaconf.h` (baked `LUA_PATH_DEFAULT`/`LUA_CPATH_DEFAULT`), per-TU compile with the version/OS cflags table, `ar rcu`+`ranlib`, two link commands, install into `<rootDir>/{bin,include,lib}`. readline OFF (no `-DLUA_USE_READLINE`, no readline libs). |
| TOOLING-04-05 | **C-toolchain preflight** | M | Before any POSIX source build: probe cc/gcc (+ ar, ranlib; make for LuaRocks/LuaJIT) via `PathEnvironmentVariableUtil.findInPath`; on failure, abort before downloading with an actionable message (install command per OS). No MSVC bootstrap. |
| TOOLING-04-06 | **LuaRocks provisioning** | M | POSIX: source tarball, `./configure --prefix=<p> --with-lua=<p>`, `make build`, `make install`, then append `variables = { CFLAGS = "-O2 -fPIC" }` to the generated config. Windows: standalone `luarocks-{ver}-windows-64.zip` single binary + generated `luarocks-config.lua`. |
| TOOLING-04-07 | **Windows prebuilt-first** | M | On Windows every kind resolves to `ReleaseBinaryStrategy` only: LuaBinaries `lua-{ver}_Win64_bin.zip` (canonical `lua.exe`/`luac.exe` copies), standalone LuaRocks, `luacheck.exe`, StyLua/LuaLS zips. `SourceBuildStrategy.supports()` is `false` on Windows. |
| TOOLING-04-08 | **Release-binary tool provisioning** | M | StyLua (all platforms), luacheck (linux-x86_64 + windows), lua-language-server (tree layout, no-`v`-prefix tag quirk) install from pinned GitHub release assets; exec bits are restored after zip extraction (`rwxr-xr-x`). |
| TOOLING-04-09 | **Rock installs into the env** | M | `LuaRocksInstallStrategy` runs `<prefix>/bin/luarocks install <rock> [<ver>]` via the TOOLING-03 execution service (INSTALL = 600 s); success requires exit 0 **and** the expected `bin/` wrapper to exist. Failures classified: C-toolchain pattern → guidance notification (final copy per TOOLING-00-04); otherwise generic failure with a 20-line output tail. |
| TOOLING-04-10 | **Manifest & idempotency** | M | `<rootDir>/.lunar-env.json` records the request and, per component, the resolved version, strategy, binaries, and an identifiers hash (SHA-256 over defined inputs). Re-provision skips components whose hash matches and whose binaries still exist; the file doubles as the "Lunar-provisioned tree" marker. |
| TOOLING-04-11 | **Registration & activation** | M | On full success, every produced binary is registered as `LuaRegisteredTool(origin = PROVISIONED, environmentId)` (TOOLING-01) and a `LuaEnvironmentState(id, name, rootDir, toolIds)` is upserted + activated (TOOLING-02). Nothing is registered on partial failure. |
| TOOLING-04-12 | **Actions & provisioning dialog** | M | The hererocks action group is replaced by `Lunar.Toolchain.*` actions (Provision / Change Versions / Recreate / Remove / Batch Provision) in a Tools-menu popup group; the dialog (Kotlin UI DSL, JdkDownloadDialog shape) offers name, root dir, runtime kind+version combos fed from the feed, LuaRocks version, and tool checkboxes with version pins. |
| TOOLING-04-13 | **LuaJIT (gated)** | S | POSIX-only git clone + `make PREFIX=<p>` + hand-copy install (dossier §2c), gated on TOOLING-00-03. Descope fallback: feed ships no `luajit` versions → the kind never appears in the dialog; no other code path changes. |
| TOOLING-04-14 | **Batch provisioning** | S | A batch dialog derives one request per (runtime kind, version) row under `<baseDir>/<kindId>-<version>`, each provisioned on its own background task (per-dir serialization makes them safely concurrent). |
| TOOLING-04-15 | **Progress & cancellation** | S | Indicator text names the current component + strategy; fraction advances per component; `checkCanceled()` runs between download, extract, each build command, and each install. Cancel leaves the manifest consistent (completed components recorded). |
| TOOLING-04-16 | **Manifest re-detection** | C | On project open, a directory containing `.lunar-env.json` but no matching environment record is offered for one-click re-registration (successor to the hererocks detect-startup idea). |

## Detailed Specifications

### TOOLING-04-01: Provision orchestration
Pipeline order within one request: RUNTIME kind → `luarocks` → release-binary tools →
rock-installed tools. Fail-fast: the first failed component aborts the rest; completed
components stay on disk and in the manifest (idempotent retry), but no registry/environment
mutation happens (TOOLING-04-11). Serialization mirrors the current per-directory guard in
`HererocksProvisioner` (`rocks/env/HererocksProvisioner.kt:28,35-51`).

### TOOLING-04-02: Bundled version feed
Resource `/toolchain/lunar-toolchain-feed.json`. Ships (from the dossier §1/§6): PUC Lua
5.1, 5.1.1–5.1.5, 5.2.0–5.2.4, 5.3.0–5.3.6, 5.4.0–5.4.8, 5.5.0 with lua.org + Tecgraf
mirror URLs; LuaRocks 3.0.0–3.13.0; StyLua v2.5.2; lua-language-server 3.18.2; luacheck
1.2.0; rock pins for busted/luacov. Alias tables per kind (`"latest"`, `"5"`, `"5.4"` →
concrete versions). Feed format follows the JdkList item shape and is validated by the
TOOLING-00-05 spike; SHA-256 pins are computed by maintainers per the update procedure
(design §4.2). LuaRocks < 3.0.0 is not shipped (5.4 requires ≥ 3.0.0; one floor keeps the
matrix simple).

### TOOLING-04-04: POSIX PUC-Lua source build
The full command plan (compiler, cflags/ldflags tables per OS+version, `luaconf.h` splice
content, per-TU compile, archive, link, install copy list) is specified in design §3.5–§3.6
and is copied from the dossier §2a — implementers must not re-derive it. Builds run in
`<rootDir>/.build/lua-{version}` and the plan is a pure function (unit-testable without a
compiler).

### TOOLING-04-09: Rock installs
C-rock knowledge (dossier §7): busted pulls C rocks (luasystem, lua-term, luafilesystem via
penlight) → needs cc + headers; luacov is pure Lua; luacheck's rock needs luafilesystem
(C). On POSIX the preflight (TOOLING-04-05) runs before any rock marked `needsCToolchain`;
on Windows such rocks fail fast with the v1 guidance ("C rocks are not supported by the
provisioner on Windows in v1") per the TOOLING-00-04 outcome.

### TOOLING-04-12: Actions & dialog
Exact action IDs, classes, group placement (Tools menu, popup group — mirroring the current
`HererocksEnvGroup` at `plugin.xml:643-668`), and the full dialog field/validation spec are
in design §2.13–§2.14 and §7. `Lunar.Hererocks.RunMatrix` moves into the new group
unchanged (matrix consumption of new envs is TOOLING-05).

## Behavior Rules
- All provisioning work runs off the EDT on one background task per request; UI mutations
  (notifications, dialog) on EDT only.
- Every external command goes through the TOOLING-03 execution service; direct
  `CapturingProcessHandler` use is forbidden in this package.
- Checksum verification is mandatory — there is no `--ignore-checksums` equivalent.
- Source builds bake absolute prefixes (trees are non-relocatable, as hererocks');
  prebuilt binaries carry no baked prefix and rely on TOOLING-03 env injection
  (dossier risk 2).
- rootDir is canonicalized before use and rejected if it contains `"` or `;` (the baked
  `luaconf.h` strings and LuaRocks config cannot escape them).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | 04-01/-04/-06/-11 | linux-x86_64, gcc/ar/ranlib/make on PATH, request `{name: "env54", rootDir: /p/.lua, items: [(lua, "5.4"), (luarocks, "latest")]}` | `provision(project, request)` | Aliases resolve 5.4→5.4.8, latest→3.13.0. Commands in order: download+verify `lua-5.4.8.tar.gz`; patch `src/luaconf.h`; `gcc -O2 -Wall -Wextra -std=gnu99 -DLUA_USE_POSIX -DLUA_USE_DLOPEN -DLUA_COMPAT_5_3 -c -o <stem>.o <stem>.c` for every `src/*.c` except `onelua.c`; `ar rcu liblua54.a <objs minus lua.o,luac.o>`; `ranlib liblua54.a`; `gcc -o luac luac.o liblua54.a -Wl,-E -ldl -lm`; `gcc -o lua lua.o liblua54.a -Wl,-E -ldl -lm`; copy to `/p/.lua/bin/{lua,luac}`, `include/{lua.h,luaconf.h,lualib.h,lauxlib.h,lua.hpp}`, `lib/liblua54.a`; then `./configure --prefix=/p/.lua --with-lua=/p/.lua`, `make build`, `make install`, CFLAGS append. `.lunar-env.json` written; registry gains 2 PROVISIONED tools (`lua`, `luarocks`) with the same `environmentId`; env `env54` active. |
| 2 | 04-01 | A provision already running for `/p/.lua` | Second `provision` for `/p/.lua` (any case) | Warning balloon "Provisioning already in progress…"; no task queued; first run unaffected. |
| 3 | 04-02 | Feed with `lua` aliases `{"latest":"5.4.8","5.4":"5.4.8","5.1.0":"5.1"}` | Resolve specs `latest`, `5.4`, `5.1.0`, `5.4.3`, `9.9` | → `5.4.8`, `5.4.8`, `5.1`, `5.4.3` (exact hit), and an error naming kind `lua` + the known versions for `9.9`. |
| 4 | 04-03 | Cached `lua-5.4.8.tar.gz` whose SHA-256 no longer matches the pin | Fetch for a new provision | Cached file deleted, re-downloaded from `https://www.lua.org/ftp/lua-5.4.8.tar.gz`, verified; on second mismatch the Tecgraf mirror is tried; both failing aborts the component with both errors listed. |
| 5 | 04-05 | linux, no `gcc` and no `cc` on PATH | `provision` with `(lua, "5.4")` | Component fails **before any download** with: "No C toolchain found on PATH (need cc/gcc, ar, ranlib). Install build tools (Linux: `sudo apt install build-essential`; macOS: `xcode-select --install`) or pick a version with a prebuilt binary." |
| 6 | 04-06/-07 | windows-x86_64, request `{(lua, "5.4"), (luarocks, "latest")}` | `provision` | No compiler probe. Downloads `lua-5.4.2_Win64_bin.zip` (feed-pinned URL) → extract into `bin/`, copy `lua54.exe`→`lua.exe`, `luac54.exe`→`luac.exe`; download `luarocks-3.13.0-windows-64.zip` → `bin\luarocks.exe` + exec of nothing (Windows); `luarocks-config.lua` written at rootDir. 2 PROVISIONED tools registered. |
| 7 | 04-08 | linux-x86_64, item `(stylua, "latest")` | ReleaseBinaryStrategy | Downloads `https://github.com/JohnnyMorganz/StyLua/releases/download/v2.5.2/stylua-linux-x86_64.zip`, SHA-verified, single binary extracted to `<rootDir>/bin/stylua`, POSIX perms set to `rwxr-xr-x`. |
| 8 | 04-08 | linux-x86_64, item `(lua-language-server, "latest")` | ReleaseBinaryStrategy | Downloads `…/releases/download/3.18.2/lua-language-server-3.18.2-linux-x64.tar.gz` (no `v` prefix), extracts tree to `<rootDir>/tools/lua-language-server/`, registers `<rootDir>/tools/lua-language-server/bin/lua-language-server`. |
| 9 | 04-09 | Provisioned env at `/p/.lua`, item `(busted, "latest")`, feed pin `2.2.0-1` | LuaRocksInstallStrategy | Executes `/p/.lua/bin/luarocks install busted 2.2.0-1` via exec service (INSTALL, 600 s); success = exit 0 and `/p/.lua/bin/busted` exists; tool registered PROVISIONED. |
| 10 | 04-09 | Same install, gcc removed mid-way; luarocks exits 1 with `gcc: command not found` in output | LuaRocksInstallStrategy failure path | Output matches the C-toolchain regex (design §4.4) → error notification with the C-toolchain guidance text + 20-line tail; no tool registered. |
| 11 | 04-10 | `/p/.lua` provisioned per TC1; identical request re-run | `provision` | Every component's identifiers hash matches the manifest and binaries exist → 0 downloads, 0 commands; info notification "already up to date"; environment re-activated. |
| 12 | 04-10 | Same env; request changed to `(lua, "5.4.6")` | `provision` | `lua` hash differs → lua rebuilt (full §3.5 plan for 5.4.6); `luarocks` hash unchanged → skipped; manifest updated. |
| 13 | 04-11 | TC1 succeeds | Inspect registry + project state | Registry contains tools with `origin=PROVISIONED`, `environmentId=<env id>`, paths `/p/.lua/bin/lua` and `/p/.lua/bin/luarocks`; `LuaEnvironmentState(name="env54", rootDir=/p/.lua, toolIds=[…])` exists and is the active environment. |
| 14 | 04-11 | TC1's luarocks step fails (make exits 2) | `provision` | Error notification with tail; lua binaries remain on disk and in the manifest, but **no** tool is registered and no environment is created/activated. |
| 15 | 04-12 | Project open, Tools menu | Inspect menu | Popup group "Lua Toolchain" with Provision Lua Toolchain… / Change Toolchain Versions… / Recreate Environment / Remove Environment / Run Test Matrix… / Provision Version Matrix…; no `Lunar.Hererocks.Create/Upgrade/Recreate/Remove/BatchProvision` actions registered. |
| 16 | 04-12 | Provision dialog open, name blank / rootDir = existing non-empty non-Lunar dir | `doValidate()` | ValidationInfo on the offending field ("Name is required" / "Directory is not empty and is not a Lunar environment"); OK disabled. |
| 17 | 04-13 | Feed with `luajit` entries gated open; linux, git+make+gcc present; item `(luajit, "v2.1")` | provision | `git clone https://github.com/LuaJIT/LuaJIT <rootDir>/.build/LuaJIT` + `git checkout v2.1`; `make PREFIX=<rootDir>`; hand-copy `src/luajit`→`bin/lua`, `libluajit.a`→`lib/libluajit-5.1.a`, headers, `jit/`→`share/lua/5.1/jit`. Gated closed (descope): `luajit` absent from the dialog's runtime combo. |
| 18 | 04-14 | Batch dialog: base `/envs`, rows `[(lua,5.1.5),(lua,5.4.8)]` | OK | Two requests with rootDirs `/envs/lua-5.1.5`, `/envs/lua-5.4.8`, each `[(lua,<v>),(luarocks,latest)]`, two background tasks run concurrently. |
| 19 | 04-15 | TC1 cancelled during the luarocks `make build` | Cancel | Process terminated via the exec service; manifest still lists the completed `lua` component; nothing registered; balloon "Provisioning cancelled". |

## Acceptance Criteria
- [ ] TC 1–16 pass (all Musts): full linux + windows pipelines, feed resolution, cache
      verification, preflight, manifest idempotency, registration, action/dialog swap.
- [ ] `python3 scripts/lint_docs.py docs` green; unit suite green (build-plan, feed,
      hash, and dialog-validation tests are pure and CI-safe — no network, no compiler).
- [ ] Live VNC verification: provision `{lua 5.4, luarocks latest, luacheck}` in the
      container and run a script + lint with the provisioned tools (per `verify-in-ide`).
- [ ] No `hererocks`/Python reference in any new code path.

## Non-Functional Requirements
- No EDT blocking: downloads, builds, probes, and installs on the background task thread
  only (contract §11); the dialog constructs from the feed without I/O beyond the bundled
  resource read.
- App-level provisioner service holds no `Project` reference; project passed per call.
- Downloads honor IDE proxy settings (inherent to `HttpRequests`); redirects capped at the
  platform default (10).
- Feed parse < 50 ms (single bundled resource, parsed once and cached in the loader).

## Dependencies
- **TOOLING-00** spike outcomes: TOOLING-00-02 (Windows prebuilt-provisioning: LuaBinaries
  Win64 + standalone LuaRocks execution verified on the Windows VM), TOOLING-00-03 (LuaJIT
  git build), TOOLING-00-04 (C-rock failure UX copy), TOOLING-00-05 (download-infra
  classpath check for `HttpRequests`/`Decompressor`/Guava **and** the feed-format
  ratification — the feed/asset spike is where the prebuilt asset names, incl. the luacheck
  linux standalone asset name, are pinned).
- **TOOLING-01**: `LuaToolchainRegistry`, `LuaToolKind` descriptors (incl. `ProvisioningSpec`
  order data), `LuaRegisteredTool`.
- **TOOLING-02**: environment upsert/activate/remove ops over `LuaEnvironmentState`.
- **TOOLING-03**: `LuaToolExecutionService` (INSTALL timeout class).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Research: [hererocks dossier](../research-hererocks-dossier.md),
  [platform provisioning idioms](../research-platform-provisioning.md)
- Contract: [tooling-architecture.md](../tooling-architecture.md) §6
