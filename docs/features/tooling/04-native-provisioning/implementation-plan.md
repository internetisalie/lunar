---
id: "TOOLING-04-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING-04"
folders:
  - "[[features/tooling/04-native-provisioning/requirements|requirements]]"
---

# TOOLING-04: Implementation Plan

Precondition: TOOLING-01 (registry + `LuaToolKind` descriptors), TOOLING-02 (environment
ops), and TOOLING-03 (`LuaToolExecutionService`) have landed; TOOLING-00 spike outcomes
-02/-04/-05 are recorded (they adjust data/copy, not structure). Every task references the
[design](design.md) section it realizes; no task requires a design decision.

## Phases

### Phase 1: Feed — model, resource, resolution [Must]
- **Goal**: the bundled version feed loads, resolves aliases, and is platform-aware.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed` (+
        `LuaFeedKind/Version/Source/Asset/Rock`) and `LuaToolchainFeedLoader` (Gson
        parser, strict null checks) — design §2.5, §4.1.
  - [x] Create `src/main/resources/toolchain/lunar-toolchain-feed.json` with the full
        version set (PUC 5.1–5.5.0, LuaRocks 3.0.0–3.13.0, StyLua 2.5.2, LuaLS 3.18.2,
        luacheck 1.2.0, busted/luacov rocks) and alias tables. **Pins filled (`6fd5b7f2`):**
        real SHA-256/size for every download-based source/asset — PUC-Lua cross-checked against
        lua.org's published checksums (§4.2) — replacing the initial `TODO-PIN` sentinels; the git
        LuaJIT source carries no checksum. That commit also pruned 6 phantom LuaRocks versions
        (3.1.4 / 3.2.2–3.2.4 / 3.3.2–3.3.3) the dense range invented but that were never released.
        See `src/main/resources/toolchain/README-feed-pins.md` (LuaBinaries Win64 asset/group
        strings per TOOLING-00-02; luacheck linux standalone asset name per TOOLING-00-05).
  - [x] Implement `resolveVersion` (single alias application, exact-first, platform-aware
        prefix max) —
        design §3.2 — and the version comparator — design §3.11.
  - [x] Create `LuaHostPlatform` (`current()` os/arch mapping) — design §2.3.
- **Exit criteria**: `LuaToolchainFeedTest` green — loads the real resource, alias
  closure (every chain terminates on a shipped entry, no cycles; incl. the `5.1.0 → 5.1`
  stop-at-shipped case, §3.2), TC 3 resolution cases, comparator ordering
  (`2.1.0-beta3 < 2.1.0`, `5.4.10 > 5.4.9`); no network.

### Phase 2: Download, verify, extract [Must]
- **Goal**: verified artifact acquisition with cache.
- **Tasks**:
  - [x] Create `LuaArtifactDownloader` (`HttpRequests` + size + Guava SHA-256, mirror
        loop, `.part` + atomic move, cache under
        `PathManager.getSystemPath()/lunar/downloads`, SourceForge `/download` key rule)
        — design §2.6, §3.4.
  - [x] Create `LuaArchiveExtractor` (`Decompressor.Tar` / `Zip(withZipExtensions)`,
        `removePrefixPath`, cancellation entry filter, `restoreExecBit`; the raw-`binary`
        copy path lives in the release-binary strategy, not the extractor) — design §2.7.
- **Exit criteria**: unit tests with local fixture archives (zip w/o exec bits, tar.gz
  with prefix dir) — covers TC 4 cache semantics via an injected cacheDir; a compile-time
  import check of `HttpRequests`/`Decompressor`/`Hashing` (TOOLING-00-05).

### Phase 3: Manifest & identifiers hash [Must]
- **Goal**: idempotency substrate.
- **Tasks**:
  - [x] Create `LuaEnvManifest` (+`LuaManifestComponent`), Gson round-trip, null-on-corrupt
        read — design §2.10, §4.5.
  - [x] Implement the identifiers-hash function (fixed 9-line input, §3.3) and the skip
        rule (hash equal + binaries executable).
- **Exit criteria**: hash unit tests (rootDir change → new hash; readline/compat lines
  present; stable across runs); manifest round-trip test; corrupt-file → `null`.

### Phase 4: Strategies — release binary & source build [Must]
- **Goal**: the two download-driven strategies produce correct trees.
- **Tasks**:
  - [x] Create `LuaProvisioningStrategy` + `LuaProvisionContext` +
        `LuaProvisionedComponent` — design §2.4.
  - [x] Create `ReleaseBinaryStrategy` (layouts `single-binary`/`tree`/
        `win-lua-binaries`, canonical `lua.exe`/`luac.exe` copies, Windows
        `luarocks-config.lua` write) — design §3.7, §4.6.
  - [x] Create `LuaCompilerProbe` (cc/gcc+ar+ranlib+make via
        `PathEnvironmentVariableUtil`, `REMEDIATION` text) — design §2.8.
  - [x] Create `PucLuaBuildRecipe` (cflags/ldflags tables, per-TU plan, ar/ranlib/link,
        install copies) and `patchLuaconf` — design §3.5, §3.6.
  - [x] Create `LuaRocksBuildRecipe` (configure/make build/make install + CFLAGS config
        append) — design §3.5 step L.
  - [x] Create `SourceBuildStrategy` (kind dispatch to recipes; `supports()` false on
        Windows; runs `BuildStep`s through `LuaToolExecutionService` INSTALL class;
        build-dir lifecycle) — design §2.4, §2.9.
- **Exit criteria**: pure-plan unit tests assert the **exact** command lists of TC 1
  (5.4.8/linux) plus a 5.1 and a 5.2 variant (compat/std/ldflags differences) and the
  luaconf splice output (TC covers §3.6 table rows); `win-lua-binaries` extraction test
  with a fixture zip.

### Phase 5: Rock installs [Must]
- **Goal**: tools installed into the env via its own luarocks.
- **Tasks**:
  - [x] Create `LuaRocksInstallStrategy` (POSIX/Windows command forms, INSTALL timeout,
        wrapper-existence success check, failure classification regex + guidance copy per
        TOOLING-00-04) — design §3.8, §4.4.
- **Exit criteria**: command-construction unit tests (TC 9 POSIX, Windows flag form);
  classification test feeding canned failure outputs (TC 10).

### Phase 6: Orchestrator, registration, progress [Must]
- **Goal**: end-to-end `provision()` with per-dir serialization and result registration.
- **Tasks**:
  - [x] Create `LuaToolProvisioner` (validation, reservation set + `tryReserve`/`release`
        seams, `Task.Backgroundable`, item ordering, preflight gating, per-component
        skip/execute/manifest-write loop, fail-fast, notifications) — design §2.2, §3.1.
        The pipeline lives in an injectable `LuaProvisionEngine` (fake strategies / spy
        sink+notifier), keeping `LuaToolProvisioner` a thin service.
  - [x] Wire strategy selection over the local `LuaProvisioningPlan` table (the §3.12
        Phase-0-revised approach: a `kindId → ordered strategy-id` map in the
        `toolchain.provision` package; `LuaToolKind.provisioning` stays `emptyList()`,
        untouched) — design §3.12.
  - [x] Implement registration on success: `registerProvisioned` per component's primary
        binary (via the `LuaProvisionResultSink` seam) + `LuaEnvironmentState`
        upsert/activate via TOOLING-02 — design §3.1 step 10.
- **Exit criteria**: orchestrator unit tests with fake strategies/exec service — TC 2
  (serialization refusal), TC 11/12 (skip vs rebuild), TC 14 (no registration on partial
  failure), TC 19 (cancellation keeps manifest); suite green via
  `tooling/gce-builder/gce-builder.sh run test`.

### Phase 7: Actions & dialogs, plugin.xml swap [Must]
- **Goal**: user-facing surface replaces the hererocks group.
- **Tasks**:
  - [x] Create the five actions in
        `net.internetisalie.lunar.toolchain.provision.LuaToolchainActions.kt`
        (Provision / ChangeVersions / Recreate / Remove / BatchProvision, enablement +
        confirm dialogs) — design §2.11. Change/Recreate enablement + prefill use
        `LuaEnvManifest.read(activeEnv.rootDir).request`; Remove calls the two-arg
        `LuaToolchainProjectSettings.removeEnvironment(envId, deleteDir)` (which already
        performs the TOOLING-01 `unregisterByEnvironment` + pooled dir delete). BGT
        `getActionUpdateThread` on the manifest/env-reading actions; Recreate deletes on a
        pooled thread before `provision`.
  - [x] Create `LuaProvisionDialog` (Kotlin UI DSL `panel { }`, auto-name via
        `DocumentListener`/`userEditedName`, feed-driven combos, forced-LuaRocks rule,
        `doValidate`, `toRequest`) — design §2.12. Pure derivation/validation factored into
        Swing-free `LuaProvisionFormState` + `LuaToolCatalog` for testability.
  - [x] Create `LuaBatchProvisionDialog` (base dir + add/remove `ListTableModel` row table)
        + pure `LuaBatchDerivation.toRequests` — design §2.13, §3.10.
  - [x] plugin.xml: added the `Lunar.Toolchain.EnvironmentGroup` block; deleted the
        `HererocksEnvGroup` block and the `HererocksDetectStartup` `postStartupActivity`;
        moved `Lunar.Hererocks.RunMatrix` (id/class unchanged) into the new group — design §7.
- **Exit criteria**: TC 15 (menu contents) and TC 16 (validation) pass; dialog request
  derivation covered by unit tests (`toRequest` item ordering); build green
  (`run build` includes plugin verification).

### Phase 8: Should/Could extensions [Should]
- **Goal**: LuaJIT (gate permitting), batch concurrency polish, re-detection.
- **Gate decision (2026-07-08)**: TOOLING-00-03 (LuaJIT git+make) **PASSED**
  (`docs/features/tooling/00-de-risking/results/luajit-git-make.md`), so the **full** LuaJIT
  path shipped with the feed gate **OPENED** (un-gated `luajit` entry), not the descope.
- **Tasks**:
  - [x] Create `LuaJitBuildRecipe` (pure git-clone-FULL/checkout/`make PREFIX=` plan + hand-copy
        install incl. `src/jit` runtime; macOS deployment-target env) + git/make gating in
        `SourceBuildStrategy.supports` (injectable `LuaJitToolProbes`); `identityHash` artifact
        `git={ref}`; add the un-gated `luajit v2.1` git-source feed entry — design §3.9, §3.2/§3.3.
  - [x] Open the LuaJIT gate in the dialog: `LuaToolCatalog.RUNTIME_KINDS = [lua, luajit]`.
  - [x] Batch flow test: two rows → distinct rootDirs → concurrent per-rootDir reservations (TC 18).
  - [x] [Could] Manifest re-detection `ProjectActivity` (`LuaEnvRedetectionStartup` +
        pure `LuaEnvRedetection` core; scans `<projectBase>/.lua`, off-EDT `Dispatchers.IO`,
        offers one-click re-registration via `notification.group.lunar.tools`) — **KEPT**
        (well-scoped, reuses the removed `HererocksDetectStartup` pattern) — design §9 note.
- **Exit criteria**: TC 17 (LuaJIT plan/gating) + TC 18 (batch) pass in their gate-**open** form.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-04-01 | M | Phase 6 |
| TOOLING-04-02 | M | Phase 1 |
| TOOLING-04-03 | M | Phase 2 |
| TOOLING-04-04 | M | Phase 4 |
| TOOLING-04-05 | M | Phase 4 (probe), Phase 6 (gating) |
| TOOLING-04-06 | M | Phase 4 (recipe/Windows binary), Phase 6 (pipeline) |
| TOOLING-04-07 | M | Phase 4 |
| TOOLING-04-08 | M | Phase 4 |
| TOOLING-04-09 | M | Phase 5 |
| TOOLING-04-10 | M | Phase 3 |
| TOOLING-04-11 | M | Phase 6 |
| TOOLING-04-12 | M | Phase 7 |
| TOOLING-04-13 | S | Phase 8 |
| TOOLING-04-14 | S | Phase 7 (dialog) + Phase 8 (flow test) |
| TOOLING-04-15 | S | Phase 6 |
| TOOLING-04-16 | C | Phase 8 |

## Verification Tasks
- [x] Unit: feed load/alias/comparator — covers TC 3 (Phase 1).
- [x] Unit: downloader cache + checksum with injected cacheDir/fixtures — covers TC 4
      (Phase 2).
- [x] Unit: build-plan snapshots (5.1 / 5.2 / 5.4 linux; luaconf splice) — covers TC 1's
      command sequence and TC 5 preflight (Phase 4).
- [x] Unit: rock-install command + failure classification — covers TC 9/10 (Phase 5).
- [x] Unit: orchestrator fakes — covers TC 2/11/12/14/19 (Phase 6).
- [x] Unit: dialog validation + `toRequest`/batch derivation — covers TC 16/18, plus TC 15
      menu-registration (`LuaProvisionDerivationTest`, `LuaToolchainActionRegistrationTest`)
      (Phase 7).
- [x] Full suite + build gate: `tooling/gce-builder/gce-builder.sh run test` (0 failures) and
      `run build` after Phases 6 and 7 — build compiles + plugin verification passes; only the
      3 known pre-existing IDE-Starter `integrationTest` env failures remain.
- [x] Live: `verify-in-ide` session — **PASSED (2026-07-09)** on the `lunar-builder` VM (GoLand
      runIde on Xvfb). Provisioned `{lua 5.4.8, luarocks 3.13.0, luacheck 1.2.0}` end to end:
      real-pin checksum verify → source-build lua + luarocks / release-binary luacheck →
      `.lunar-env.json` written → INFO balloon "Provisioned Lua toolchain 'lua-5.4.8' (3 tools)";
      the provisioned `lua -v` (5.4.8), a script eval, `luarocks --version`, and `luacheck --version`
      all ran. **TC 15** (Tools ▸ Lua Toolchain group + correct action enablement, before/after an
      active env), the dialog (feed-driven combos, auto-name, §3.11 ordering, forced-LuaRocks), and
      **TC 11** idempotency (re-run via Change Versions → "already up to date (3 tools)", no rebuild)
      were all confirmed live.
- [x] Live: **Windows (TC 6)** — **PASSED (2026-07-09)** on the `win11` KVM VM (real GoLand, fresh
      plugin installed from disk). Provisioned `{lua 5.4.2 (win-lua-binaries → canonical `lua.exe`),
      luarocks 3.13.0 (standalone zip + `luarocks-config.lua`), luacheck 1.2.0 (`luacheck.exe`)}` →
      "Provisioned … (3 tools)"; `lua.exe` runs and resolves via the terminal PATH injection. This
      run **found + fixed a real bug** (`12928b99`): single-binary assets whose `binaryPath` already
      ends in `.exe` got a doubled extension (`luarocks.exe.exe` / `luacheck.exe.exe`) → not on PATH.
      Now regression-tested (`ReleaseBinaryStrategyTest.testWindowsExecNameDoesNotDoubleExeExtension`).
      **Unblocked by real pins:** the feed's SHA-256/size pins are now computed and committed
      (`6fd5b7f2`; PUC-Lua cross-checked against lua.org), so the download/verify pipeline no longer
      refuses the (removed) `TODO-PIN` sentinels.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Feed — model, resource, resolution | done | Must |
| Phase 2: Download, verify, extract | done | Must |
| Phase 3: Manifest & identifiers hash | done | Must |
| Phase 4: Strategies — release binary & source build | done | Must |
| Phase 5: Rock installs | done | Must |
| Phase 6: Orchestrator, registration, progress | done | Must |
| Phase 7: Actions & dialogs, plugin.xml swap | done | Must |
| Phase 8: Should/Could extensions | done | Should |
