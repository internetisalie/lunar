---
id: "TOOLING-00"
title: "00: De-risking & Technical Spikes"
type: "feature"
status: "planned"
priority: "high"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-00: De-risking & Technical Spikes

## Overview

Runs the six spikes that retire the highest technical risks of the
[TOOLING epic](../requirements.md) before TOOLING-01…07 are implemented: the POSIX
source-build recipe, the Windows prebuilt path, LuaJIT feasibility, C-rock installs on
toolchain-less hosts, the platform download-infrastructure classpath, and the clean-break
persistence model. Each spike answers a question tracked in
[tooling-risks-and-gaps.md](../tooling-risks-and-gaps.md) and hands a named deliverable to
the feature it unblocks. Spikes are throwaway prototypes plus committed results — no
production code or `plugin.xml` registrations ship from this feature.

## Scope

### In Scope
- Standalone build/provisioning prototypes under `tooling/spikes/tooling-00/` (scripts)
  and committed result write-ups under `docs/features/tooling/00-de-risking/results/`.
- Two in-repo spike tests (classpath check, serialization round-trip) that run in the
  normal unit-test suite via gce-builder.
- The bundled version-feed JSON format definition with a concrete sample document.
- Live Windows execution verification on the existing `win11` KVM VM over VNC
  (TOOLING-00-02) — revert its `Fresh Install` snapshot and boot; no ISO install.
- Binding ship/descope decision: LuaJIT v1 scope (TOOLING-00-03).

### Out of Scope
- The production `toolchain.provision` engine, strategies, progress UI (TOOLING-04).
- The registry/resolver/exec services and their persistence (TOOLING-01/-02/-03) — the
  serialization spike only proves the mechanism, it does not ship the components.
- Windows source builds (descoped for v1 — risks doc Risk 1.1) and remote targets.
- Event-topic and run-config-dropdown migrations (design-time mitigations in
  TOOLING-02/-05; risks doc Risks 1.8/1.9).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| TOOLING-00-01 | **POSIX PUC-Lua source-build spike** | M | Reimplement hererocks' per-translation-unit build recipe (dossier §2a) for Lua 5.4.8 as a standalone script; provisioned prefix runs `lua -v` with baked `LUA_PATH_DEFAULT`, no readline linkage. |
| TOOLING-00-02 | **Windows prebuilt-provisioning spike** | M | Acquire LuaBinaries Win64 zip + standalone luarocks-windows-64, assemble a provisioned dir; layout validated on Linux, then `lua.exe -v` / `luarocks.exe --version` executed from the provisioned tree live on the Windows 11 KVM VM, verified over VNC (screenshot/OCR); SmartScreen/AV caveats observed and documented. |
| TOOLING-00-03 | **LuaJIT git+make spike** | S | Clone + `make` LuaJIT `v2.1` on POSIX (keeping `.git`), hand-install per hererocks; assess darwin-arm64 on paper; outcome decides ship-POSIX-only vs descope-to-register-existing-binary for TOOLING-04. |
| TOOLING-00-04 | **C-rock install spike** | M | `luarocks install busted` into the TOOLING-00-01 tree succeeds with `cc` present; without `cc`, capture the failure signature and specify the guidance-notification UX (detection heuristic + copy). |
| TOOLING-00-05 | **Platform download-infra classpath check + feed format** | M | `HttpRequests`, `Decompressor` (Tar + Zip `withZipExtensions`), and Guava `Hashing` compile and execute from the plugin's classpath (in-repo test); the bundled version-feed JSON format is defined with a concrete sample document. |
| TOOLING-00-06 | **Clean-break serialization spike** | M | New `LuaToolchainAppState`/`LuaToolchainProjectState` classes round-trip via the XML serializer with deep equality; a legacy `lunar.xml` fragment (old `interpreters`, `toolInventory`, `hererocksEnvs`, `interpreterMode`, `explicitInterpreter`/`explicitTarget`, `activeEnvId` tags) loads without errors and stale tags are absent after re-serialization. |

## Detailed Specifications

### TOOLING-00-01: POSIX PUC-Lua source-build spike
Reimplements the dossier §2a recipe (download → checksum → per-TU compile → archive →
link → install with `luaconf.h` prefix patching) for **Lua 5.4.8** on Linux with the
**no-readline default** (risks doc Risk 1.4). The exact command sequence, `luaconf.h`
patch format, and flag sets (including the `macosx` variant answering Gap 2.1) are pinned
in [design.md](design.md) §2.1. Pass = `<prefix>/bin/lua -v` prints `Lua 5.4.8`,
`package.path` shows the baked prefix, and `ldd` shows no `libreadline`.
**Unblocks**: TOOLING-04 `SourceBuildStrategy`.

### TOOLING-00-02: Windows prebuilt-provisioning spike
**Execution-environment decision (binding):** the spike runs in two stages, both
agent-executable. (1) Asset acquisition, checksum pinning, extraction, and layout
validation are **automated on Linux** (zips are OS-independent artifacts). (2) Execution
is **verified live on the existing `win11` VM under KVM/virt-manager on the workstation,
driven over VNC** with the same tooling/conventions as the containerized GoLand
(verify-in-ide skill): `lua.exe -v` and `luarocks.exe --version` are run from the
provisioned tree inside the VM and the pass/fail verdict is read from VNC screenshots/OCR.
The **VM already exists** — no ISO install; the prerequisite is to revert its clean
snapshot and boot (`virsh snapshot-revert win11 "Fresh Install" && virsh start win11`;
guest `TESTING\tester`, empty password). It is a **bare command-line box with no IDE**,
which is all this spike needs (design §2.2).
Assets: LuaBinaries `lua-5.4.2_Win64_bin.zip`-pattern zip for the newest 5.4 line
available (record the exact version + SourceForge group observed) and
`luarocks-3.13.0-windows-64.zip` (standalone `luarocks.exe`). SmartScreen/MOTW and AV
caveats are **observed live** on the VM and documented (design §2.2).
**Unblocks**: TOOLING-04 `ReleaseBinaryStrategy` (Windows story).

### TOOLING-00-03: LuaJIT git+make spike
POSIX-only: shallow-clone `https://github.com/LuaJIT/LuaJIT` at branch `v2.1` **keeping
`.git`** (the build derives its rolling version from git metadata — dossier §1), `make`,
then hand-copy artifacts per hererocks (dossier §2c): `luajit` installed as
`bin/lua`, `jit/` → `share/lua/5.1/jit`. darwin-arm64 is assessed on paper
(`MACOSX_DEPLOYMENT_TARGET`, recent-`v2.1`-checkout requirement) — no macOS hardware in
the harness. **Outcome is a binding decision** (design §2.3 decision matrix): PASS →
TOOLING-04 ships LuaJIT provisioning POSIX-only; FAIL/uncertain → the `luajit` kind gets
an empty `provisioning` list (register existing binary only).
**Unblocks**: TOOLING-04 scope; TOOLING-01 `luajit` kind descriptor.

### TOOLING-00-04: C-rock install spike
Provisions LuaRocks 3.13.0 into the TOOLING-00-01 prefix (`./configure --prefix=<prefix>
--with-lua=<prefix> && make build && make install` — dossier §2d), then
`<prefix>/bin/luarocks install busted`. Two runs: (a) with `gcc` present → busted wrapper
lands in `<prefix>/bin`, `busted --version` exits 0; (b) with the compiler hidden
(design §2.4 method) → capture exit code + stderr, derive the detection heuristic and the
guidance-notification copy (target group `notification.group.lunar.tools`,
`src/main/resources/META-INF/plugin.xml:543`).
**Unblocks**: TOOLING-04 `LuaRocksInstallStrategy`; TOOLING-07 guidance notifications.

### TOOLING-00-05: Platform download-infra classpath check + feed format
An in-repo test (`LuaProvisioningClasspathSpikeTest`) imports and exercises
`com.intellij.util.io.HttpRequests`, `com.intellij.util.io.Decompressor.Tar`,
`Decompressor.Zip(...).withZipExtensions()`, and
`com.google.common.hash.Hashing.sha256()` against local fixture archives (no network) —
proving the classes are on the compile classpath established by
`build.gradle.kts:63` (`create(platformType, platformVersion)`); nothing in
`src/main/kotlin` imports them today (verified by grep, 2026-07-05). The same spike fixes
the **bundled version-feed JSON format** (JdkList-style; design §2.5 carries the schema
and a concrete sample) at resource path `src/main/resources/toolchain/toolchain-feed.json`
(sample committed by the spike; productionized in TOOLING-04).
**Unblocks**: TOOLING-04 download/extract/verify skeleton and feed.

### TOOLING-00-06: Clean-break serialization spike
Prototypes the contract §7 persistence: new `LuaToolchainAppState` (fields `tools`,
`globalBindings` — the app inventory field is deliberately named `tools` so no legacy
tag shares a name with a reshaped field) and `LuaToolchainProjectState` (fields
`bindings`, `environments`, `activeEnvironmentId`) round-trip through
`com.intellij.util.xmlb.XmlSerializer` with deep equality. Then a **legacy-XML tolerance
check**: a fixture `lunar.xml` fragment containing today's real tags — app
`interpreters`/`toolInventory`/`globalToolBindings`
(`src/main/kotlin/net/internetisalie/lunar/settings/LuaApplicationSettings.kt:39-53`) and
project `interpreter`/`interpreterMode`/`explicitInterpreter`/`explicitTarget`/
`hererocksEnvs`/`activeEnvId`/`projectToolBindings`
(`src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt:53-130`) — is
deserialized into the new state classes with **no exception**, and re-serialization emits
**none** of the legacy tag names. Exact fixture and assertions in design §2.6.
**Unblocks**: TOOLING-01 (registry persistence), TOOLING-02 (env/binding persistence),
TOOLING-05 (legacy deletion safety).

## Behavior Rules
- Spikes never run on the EDT and never modify user machines outside their scratch
  prefix (`/tmp`-scoped or repo-ignored dirs); in-repo spike tests must be hermetic (no
  network).
- A spike is **done** only when its pass/fail criterion is met **and** its named
  deliverable is committed; a FAIL outcome is a valid completion when the results doc
  records the fallback decision (e.g. 00-03 descope).
- Results docs follow the naming `results/<spike-slug>.md` and record: verdict, exact
  commands/versions used, outputs, and the decision handed downstream.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | TOOLING-00-01 | Linux host with `gcc`, `ar`, `ranlib`; no `libreadline-dev` required | Run `tooling/spikes/tooling-00/build-lua-posix.sh <prefix>` | Exit 0; `<prefix>/bin/lua -v` prints `Lua 5.4.8  Copyright (C) 1994-2025 Lua.org, PUC-Rio`; `<prefix>/bin/lua -e 'print(package.path)'` output starts with `<prefix>/share/lua/5.4/?.lua`; `ldd <prefix>/bin/lua` contains no `libreadline` |
| 2 | TOOLING-00-02 | Linux host with network; the `win11` KVM VM booted from its `Fresh Install` snapshot, reachable over VNC (bare box, no IDE) | Run the acquisition script for LuaBinaries Win64 + luarocks-windows-64; assemble the tree on the VM and run `lua.exe -v` and `luarocks.exe --version` from it, driven over VNC | Both zips download and hash-record; extracted dir contains `lua54.exe` (or `lua.exe` per asset naming, recorded) + `lua54.dll` and `luarocks.exe`; VNC screenshot/OCR shows a `Lua 5.4` banner for `lua.exe -v` and a `3.13.0` version line for `luarocks.exe --version`; `results/windows-prebuilt.md` records the observed SmartScreen/AV behavior |
| 3 | TOOLING-00-03 | Linux host with `git`, `make`, `gcc` | Run the LuaJIT spike script (clone `v2.1`, `make`, hand-install) | `<prefix>/bin/lua -v` prints a `LuaJIT 2.1` banner; `<prefix>/bin/lua -e 'print(jit.version)'` exits 0; `results/luajit-git-make.md` records the PASS/FAIL verdict + darwin-arm64 assessment + the binding ship/descope decision |
| 4 | TOOLING-00-04 | The TOOLING-00-01 prefix; `gcc` on PATH | `<prefix>/bin/luarocks install busted` | Exit 0; `<prefix>/bin/busted --version` exits 0 printing a `2.x` version |
| 5 | TOOLING-00-04 | Same prefix; compiler hidden (design §2.4) | `<prefix>/bin/luarocks install busted` | Non-zero exit; stderr/stdout matches the recorded failure signature; `results/c-rock-install.md` specifies the detection heuristic (design §3.2) and the notification copy verbatim |
| 6 | TOOLING-00-05 | Plugin test classpath; fixture `.tar.gz` and `.zip` under test resources | Run `LuaProvisioningClasspathSpikeTest` via gce-builder | Test green: all four APIs compile and execute (extraction preserves the fixture's exec bit via `Tar`; `Hashing.sha256()` of a fixture file equals its precomputed digest); the feed sample at `src/main/resources/toolchain/toolchain-feed.json` parses against the design §2.5 schema |
| 7 | TOOLING-00-06 | New state classes + legacy-XML fixture (design §2.6) | Run `LuaToolchainSerializationSpikeTest` via gce-builder | Round-trip deep-equals; legacy fixture deserializes without exception; re-serialized XML contains no `interpreters`, `toolInventory`, `globalToolBindings`, `hererocksEnv`, `hererocksEnvs`, `interpreterMode`, `explicitInterpreter`, `explicitTarget`, `activeEnvId`, or `projectToolBindings` tag |

## Acceptance Criteria
- [ ] TOOLING-00-01: build script committed; TC 1 passes on the gce-builder image; results doc records the macosx flag-set (Gap 2.1 answer).
- [ ] TOOLING-00-02: acquisition and live execution on the `win11` VM (booted from `Fresh Install`) validated per TC 2; the observed SmartScreen/AV behavior recorded in the results doc.
- [ ] TOOLING-00-03: TC 3 executed; the ship/descope decision is recorded and reflected in the risks doc DR table.
- [ ] TOOLING-00-04: TCs 4–5 pass; notification heuristic + copy specified.
- [ ] TOOLING-00-05: TC 6 green in the unit suite; feed schema + sample committed.
- [ ] TOOLING-00-06: TC 7 green in the unit suite.
- [ ] All six results/deliverables linked from the risks doc DR table with updated statuses.

## Non-Functional Requirements
- In-repo spike tests are plain unit tests (no `BasePlatformTestCase` needed for 00-05;
  00-06 may use light fixtures if service wiring is exercised) and add < 10 s to the suite.
- Shell spikes are idempotent (re-run → same verdict) and self-contained (no state outside
  their prefix and the download cache dir they create).
- Per the engineering contract, any code that later graduates from a spike must move
  under `net.internetisalie.lunar.toolchain` — spike artifacts themselves are not
  production code.

## Dependencies
- Research inputs: [hererocks dossier](../research-hererocks-dossier.md),
  [platform provisioning](../research-platform-provisioning.md) (both done).
- Architecture contract: [tooling-architecture.md](../tooling-architecture.md) (§6, §7).
- Blocks: TOOLING-01, TOOLING-02, TOOLING-04 (per the epic dependency order `00 → 01 → …`).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [../tooling-risks-and-gaps.md](../tooling-risks-and-gaps.md)
