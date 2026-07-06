---
id: "TOOLING-RISKS"
title: "Risks & Gaps"
type: "spec"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING: Risks & Gaps

Technical risks and design gaps for the whole TOOLING epic, drawn from the two research
docs ([hererocks dossier](research-hererocks-dossier.md) §8,
[platform provisioning](research-platform-provisioning.md)) and the
[architecture contract](tooling-architecture.md). Every gap that matters at planning time
is resolved by a `TOOLING-00` spike (table at the bottom); risks without a spike carry a
design-time mitigation owned by a named feature.

## Critical Risks

### Risk 1.1: Windows source compilation is the hard 30%
- **Impact**: Reimplementing hererocks' MSVC bootstrap (vswhere → vcvars import →
  recursive re-invocation, ~300 lines; dossier §4) would dominate TOOLING-04's cost.
  Shipping it half-working breaks the flagship "zero-to-toolchain" use case on Windows.
- **Likelihood**: high (if source builds are attempted on Windows).
- **Mitigation**: Windows is **prebuilt-first, source-build not offered on the user's
  machine in v1** (dossier recommendation) + the standalone
  `luarocks-{ver}-windows-64.zip` (self-contained `luarocks.exe`) from `luarocks.github.io`.
  Spike **TOOLING-00-02** proved the compile-free path **live on the Windows 11
  KVM/virt-manager VM over VNC** (banners + SmartScreen/AV observed).
- **Prebuilt *source* decision (2026-07-06)**: the Windows Lua binary is **no longer sourced
  from SourceForge/LuaBinaries** (untrusted host + Cloudflare block — see Gap 2.2). Note the
  MSVC bootstrap this risk warns about is **user-machine, provision-time** compilation; building
  the Windows binary in **our own CI via MinGW cross-compile from Linux** is trivial (PUC Lua is
  dependency-free C with a `mingw` target — the same way LuaBinaries and dyne build theirs) and
  does **not** trigger this risk. **Target**: self-build from official lua.org source in CI,
  self-hosted (our GitHub release) or bundled in the plugin. **Interim**: consume
  [dyne/luabinaries](https://github.com/dyne/luabinaries) (GitHub-hosted, MinGW-from-source,
  MIT, publishes `SHA256SUMS`). Both keep the user's machine compiler-free on Windows.
- **Provisioning policy (2026-07-06)**: because trustworthy prebuilt binaries now exist for
  **both** Windows and macOS (dyne interim / self-built target), **user-side source-build
  auto-installs are limited to Linux**; macOS and Windows auto-install a prebuilt (fetch, not
  compile). Source-build stays available on POSIX as the primary Linux path and an advanced
  opt-in elsewhere. See Gap 2.1, Gap 2.2.

### Risk 1.2: Non-relocatable provisioned prefixes
- **Impact**: Source builds bake `LUA_PATH_DEFAULT`/`LUA_CPATH_DEFAULT` into the binary via
  a patched `luaconf.h`, and LuaRocks' configure writes a hardcoded-prefix config (dossier
  §2a step 4, §3). A user moving/renaming an environment directory gets a silently broken
  interpreter; naive "copy the tree" workflows fail.
- **Likelihood**: medium.
- **Mitigation**: Accept baked prefixes for source builds (hererocks-compatible trees;
  spike **TOOLING-00-01** verifies the baking recipe works), rely on
  `LuaExecutionEnvironmentBuilder` env injection for prebuilt binaries (contract §5), and
  have TOOLING-07 health checks flag a moved tree (manifest path ≠ actual path). Never
  offer a "move environment" action in v1.

### Risk 1.3: LuaJIT is git-only rolling-release
- **Impact**: No release tarballs exist (luajit.org: git-only); the build needs `.git`
  metadata in the tree, `MACOSX_DEPLOYMENT_TARGET` on macOS, and `msvcbuild.bat` on
  Windows (dossier §1, §2c). If we promise LuaJIT provisioning and it fails on common
  hosts, the provisioning feature loses trust.
- **Likelihood**: high (darwin-arm64 and Windows), medium (Linux).
- **Mitigation**: Spike **TOOLING-00-03** runs the git+make path on POSIX and produces a
  ship/descope decision for TOOLING-04: either "LuaJIT provisioning ships POSIX-only in
  v1" or "LuaJIT is register-existing-binary only" (discovery still finds installed
  `luajit` binaries either way — the `luajit` kind stays in the registry).
- **Status (2026-07-06)**: **Resolved — PASS.** Linux x86_64 build confirmed green (LuaJIT
  2.1.1782726002, git HEAD `a2bde60`). darwin-arm64: no blocking finding (arm64 supported
  in v2.1 HEAD; `MACOSX_DEPLOYMENT_TARGET` is a configuration pre-condition, not a
  blocker). Windows: excluded (Risk 1.1). **Decision: `provisioning = [SourceBuildStrategy(git+make)]`,
  POSIX-only in v1.** See [results/luajit-git-make.md](00-de-risking/results/luajit-git-make.md).

### Risk 1.4: readline is a default-on POSIX link dependency
- **Impact**: hererocks links `-DLUA_USE_READLINE` + `-lreadline` by default with **no
  probing** (dossier §4); headless/CI machines routinely lack `libreadline-dev`, so a
  faithful port would fail source builds on exactly the machines provisioning targets.
- **Likelihood**: high (unmitigated).
- **Mitigation**: The native engine defaults to **no-readline** semantics (dossier
  recommendation). **TOOLING-00-01**'s recipe omits `-DLUA_USE_READLINE`/`-lreadline` and
  its success criterion includes "no readline linkage".
- **UX impact (bounded corner case)**: The no-readline build loses the standalone `lua`
  REPL's interactive line-editing extras — command history (up/down recall), intra-line
  cursor movement (left/right arrows), tab completion, and emacs/vi keybindings — which
  readline only provides when stdin is a TTY. The **only** place a user can observe this is
  running a *provisioned* (source-built) `lua` **interactively in a real terminal** (the
  IntelliJ integrated terminal — a pty4j PTY — or an external shell). It does **not** affect:
  - **the hosted IDE REPL** (`run/console/LuaConsoleRunner`): input is fed to `lua -i` over a
    plain pipe (`GeneralCommandLine.createProcess()` + `OSProcessHandler`, no PTY), so readline
    is inert regardless of how the binary was built; history/editing/completion are supplied by
    the IDE layer (`ConsoleHistoryController`, `LanguageConsoleImpl`, `LuaChunkCompletion`);
  - **script execution** (`lua foo.lua`), `luac`, embedding, or any tool (luarocks/luacheck/
    stylua/busted) — readline is never on their path;
  - **BYO/registered system interpreters** (distro/Homebrew builds keep their readline) or
    **Windows** (PUC Lua uses the console API; we ship prebuilt binaries there).
  - *Workarounds if it matters:* `rlwrap lua`, or bind a system interpreter. Opportunistic
    re-enablement (probe `libreadline` and link only when present, never a hard failure) or
    bundling **linenoise** (single-file, dependency-free) are possible future scope, not v1.

### Risk 1.5: C rocks need a host C toolchain even with a prebuilt interpreter
- **Impact**: `luarocks install busted` pulls C rocks (luasystem, lua-term, luafilesystem
  via penlight — dossier §7); on a host without `cc`/headers the install fails with a raw
  compiler error deep in luarocks output. On Windows, mixing a MinGW-built `lua54.dll`
  with MSVC-compiled rocks is an ABI hazard.
- **Likelihood**: high on fresh machines (the epic's target audience).
- **Mitigation**: Spike **TOOLING-00-04** captures the exact failure signature and
  specifies the guidance-notification UX (detection heuristic + message) that TOOLING-04
  implements; per-tree toolchain consistency is a TOOLING-04 design rule. Pure-Lua rocks
  (luacov) are unaffected.

### Risk 1.6: The version/alias/checksum table is ours to own
- **Impact**: hererocks hardcodes version lists, alias resolution (`"5.4"→"5.4.8"`), and
  SHA-256 pins in its script (dossier §1). LuaBinaries publishes no simple checksum feed;
  StyLua/lua-language-server releases ship no checksum assets (dossier §6). Without an
  owned, versioned table the engine either can't verify downloads or breaks when upstream
  moves. The current UI version list comes from `PlatformVersionRegistry.getVersions`
  (`src/main/kotlin/net/internetisalie/lunar/rocks/env/CreateHererocksEnvDialog.kt:57`),
  which knows nothing of download URLs or checksums.
- **Likelihood**: certain (it is a required deliverable, not a possibility).
- **Mitigation**: Spike **TOOLING-00-05** defines the bundled JdkList-style JSON feed
  (kind/version/os/arch/url/sha256/size/packageType/rootPrefix/strategy) with a concrete
  sample; TLS + self-pinned checksums for assets whose upstream publishes none;
  lua.org/ftp published checksums cross-verify PUC pins. Updating pins is a normal
  plugin-release chore (documented in TOOLING-04).

### Risk 1.7: Clean-break serialization corrupts or resurrects legacy state
- **Impact**: The epic deletes persisted fields in place — app `lunar.xml` loses
  `interpreters`/`toolInventory`/`globalToolBindings`
  (`src/main/kotlin/net/internetisalie/lunar/settings/LuaApplicationSettings.kt:39-53`),
  project `.idea/lunar.xml` loses `interpreter`/`interpreterMode`/`explicitInterpreter`/
  `explicitTarget`/`hererocksEnv(s)`/`activeEnvId`/`projectToolBindings`
  (`src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt:53-130`) —
  while keeping the same component names/storage file (contract §7). If the XML
  serializer chokes on now-unknown tags, or old tags survive saves indefinitely, users
  hit load errors or ghost state after upgrading.
- **Likelihood**: low-medium (the platform normally drops unknown options silently, but
  "normally" is exactly what a spike should confirm — nested serialized types like
  `LuaInterpreter` and `HererocksEnvState` are non-trivial).
- **Mitigation**: Spike **TOOLING-00-06** round-trips the new state classes and proves
  stale legacy XML loads without errors and is dropped on next save, before TOOLING-01
  builds on the persistence design.

### Risk 1.8: Event-topic migration leaves stale caches behind
- **Impact**: Today app-level registry mutations fire **no** event —
  `LuaToolManager.registerTool`
  (`src/main/kotlin/net/internetisalie/lunar/tool/LuaToolManager.kt:49`) never touches a
  message bus, while `LuaTerminalEnvironmentService` caches tool directories and
  invalidates only on the project-level `LuaSettingsChangedListener.TOPIC`
  (`src/main/kotlin/net/internetisalie/lunar/tool/LuaTerminalEnvironmentService.kt:38-45`,
  topic at `src/main/kotlin/net/internetisalie/lunar/settings/LuaSettingsChangedEvent.kt:29`).
  The contract's fix — a new app-level `LuaToolchainListener.TOPIC` fired by every
  registry/binding/env mutation (§4) — introduces a migration window where a consumer
  still subscribes to the old topic (or neither) and shows stale state.
- **Likelihood**: medium.
- **Mitigation**: TOOLING-02 owns the topic and the rule "every mutator fires it";
  TOOLING-05's consumer-migration checklist (contract §9) includes the subscription swap
  per consumer, and its verification greps for remaining `LuaSettingsChangedListener`
  subscriptions in toolchain consumers. No spike needed — this is design discipline plus
  an explicit migration checklist, both already contracted.

### Risk 1.9: Run-config interpreter dropdown regresses with RUNTIME kinds
- **Impact**: The run/debug interpreter combo is hand-built by
  `customizeLuaInterpreterComboBox`
  (`src/main/kotlin/net/internetisalie/lunar/platform/LuaInterpreterComponent.kt:18`):
  model = typed path + project interpreter + `LuaApplicationSettings.validInterpreters()`
  (`settings/LuaApplicationSettings.kt:75`), de-duplicated by path, with a
  DocumentListener model-rebuild that must keep the project interpreter (the ROCKS-16
  regression fixed in commit `29d1b636`, guarded by
  `src/test/kotlin/net/internetisalie/lunar/run/TestLuaRunConfiguration.kt`). When the
  inventory becomes `LuaRegisteredTool` RUNTIME kinds resolved via `LuaToolResolver`
  (contract §3), a naive rewrite loses the typed-path/dedup/project-entry behavior and
  re-introduces the "managed interpreter disappears from the dropdown" bug.
- **Likelihood**: medium.
- **Mitigation**: TOOLING-05's design must port the existing combo semantics onto the
  resolver (typed entry + resolved RUNTIME tool + usable RUNTIME inventory, deduped by
  path) and keep `TestLuaRunConfiguration` green (adapted to the new model) as the
  regression gate. The platform `SdkListModelBuilder` pattern (platform research §5) is
  the reference shape. No spike — the behavior is already pinned by an existing test.

## Design Gaps

### Gap 2.1: macOS PUC-Lua prebuilt gap
- **Question**: LuaBinaries ships no macOS assets (dossier §6). Is source-build-only
  acceptable on macOS?
- **Options / leaning**: (a) source-build only on macOS (leaning — macOS ships a usable
  `cc`, and the POSIX recipe already covers `macosx` flags); (b) self-hosted CI-built
  binaries (rejected for v1: infra we don't have).
- **Resolved by**: **TOOLING-00-01** — the recipe is specified per-OS including `macosx`
  defines; passing on Linux with the macOS flag-set documented answers (a) for TOOLING-04.
- **Update (2026-07-06)**: option (b) is **no longer infra we lack** — the Gap 2.2 decision
  gives us a CI cross-build pipeline (or the dyne interim, which ships **macOS arm64 + x64**
  prebuilts). So macOS can go **prebuilt-first** like Windows, not source-build-only.
  **Caveat**: unsigned/unnotarized macOS binaries hit **Gatekeeper quarantine** (a harder wall
  than Windows SmartScreen) — running a downloaded `lua` may require de-quarantine
  (`xattr -d com.apple.quarantine`) or ad-hoc codesign. That is a **new macOS de-risk** to
  verify before committing macOS to prebuilt-first (no macOS hardware in the current harness).

### Gap 2.2: LuaBinaries download reliability & checksum acquisition
- **Question**: Are SourceForge redirect URLs scriptable and stable, and where do the
  SHA-256 pins for LuaBinaries zips come from (SourceForge exposes checksums only via
  UI/API — dossier §6, flagged [UNVERIFIED])?
- **Options / leaning**: pin checksums ourselves at feed-authoring time (download once,
  hash, commit the pin) — leaning; or scrape the SourceForge API per release (rejected:
  runtime dependency on an unverified API).
- **Resolved by**: **TOOLING-00-02** (proves the URL pattern end-to-end and executes the
  binaries live on the Windows 11 KVM VM) + **TOOLING-00-05** (feed format carries the
  self-pinned `sha256`).
- **Finding (2026-07-06, TOOLING-00-02 Stage 2) — new production risk for TOOLING-04**:
  the `downloads.sourceforge.net` direct-CDN pattern is **no longer reliably scriptable**.
  From the Windows VM, PowerShell `Invoke-WebRequest` (even with a browser `User-Agent`) hit a
  **Cloudflare managed JavaScript challenge** and received the HTML challenge page instead of the
  ZIP; only a real (JS-executing) browser got through. The same URL had fetched fine via `curl`
  from the Linux workstation earlier the same day, so the block is **heuristic/intermittent**
  (IP reputation / TLS fingerprint / UA), not a stable gate — which is precisely the reliability
  risk. Lunar's JVM/HTTP downloader is the challenged client class. **Mitigations to weigh in
  TOOLING-04**: a non-SourceForge mirror for the LuaBinaries zips, bundling the Windows binaries,
  or a retry-with-clear-error path. The existing `PK\x03\x04` magic-bytes guard (design §4) already
  **fails closed** on the challenge HTML (aborts rather than installing garbage) — validated as
  necessary, not merely defensive. `luarocks` (GitHub Pages) has no such gate. See
  [results/windows-prebuilt.md](00-de-risking/results/windows-prebuilt.md) Finding A.
- **Decision (2026-07-06) — SourceForge is dropped**: for reliability (Cloudflare) **and trust**
  (SourceForge's adware-bundling history), Lua binaries are no longer acquired from SourceForge.
  - **Target — PROVEN (2026-07-06)**: Lunar builds its **own** Windows (and macOS) Lua binaries
    from **official lua.org source** via **MinGW/cross-compile in CI**, self-hosted as a Lunar
    GitHub release asset (or bundled in the plugin). Canonical source → our reproducible build →
    our artifact; no third-party binary trust, no SourceForge, no user-side compiler. Unifies
    Windows/macOS on the **same Lua 5.4.8** the POSIX source-build (TOOLING-00-01) already targets.
    Spike `tooling/spikes/tooling-00/build-lua-windows-mingw.sh` cross-compiled `lua54.exe`/
    `lua54.dll`/`luac54.exe` from the pinned lua.org 5.4.8 tarball and **ran them on the win11 VM**
    (`lua54.exe -v` → Lua 5.4.8; executes code; no missing-DLL) — see
    [results/windows-prebuilt.md](00-de-risking/results/windows-prebuilt.md) Stage 3. The CI
    wiring + self-hosting is TOOLING-04's to implement.
  - **Interim**: consume [dyne/luabinaries](https://github.com/dyne/luabinaries) — GitHub-hosted
    (plain `curl`, no Cloudflare), MinGW-cross-compiled from lua.org source via GitHub Actions,
    **MIT**, publishes per-platform `SHA256SUMS`. Provides 5.1.5/5.3.6/5.4.8/5.5.0 for
    win-x64 + linux + macOS (arm64/x64). Verified 2026-07-06: `curl` fetch works and computed
    SHA-256 matches their published sums; `lua54.exe`/`lua54.dll` are valid PE32+. Caveats: small
    unofficial repo (pin exact release + self-hash), loose-file packaging (not a zip → feed entry
    is direct-file, not zip-extract), git-SHA tags (Lua version read from release notes).
  - `luarocks` unchanged — official standalone from `luarocks.github.io` (GitHub, not SourceForge).
  - **Feed impact**: the `windows`/`macos` Lua entries switch `packageType` from zip-extract to
    direct file(s); the `PK\x03\x04` guard generalizes to a per-file magic/size check. Feed
    self-pins the SHA-256 regardless of source. TOOLING-04/-05 own the implementation.

### Gap 2.3: Failure UX for tool installs on toolchain-less hosts
- **Question**: What exactly does `luarocks install busted` emit on a host without `cc`,
  and what should the user see instead of raw compiler spew?
- **Options / leaning**: detect the failure signature and raise a guidance notification
  ("Installing busted requires a C compiler…") on the existing
  `notification.group.lunar.tools` group (`src/main/resources/META-INF/plugin.xml:543`).
- **Resolved by**: **TOOLING-00-04** — captures the real signature, fixes the detection
  heuristic and notification copy for TOOLING-04.

### Gap 2.4: LuaJIT v1 scope
- **Question**: Does LuaJIT provisioning (git+make) ship in v1, POSIX-only, or not at all?
- **Options / leaning**: POSIX git+make if the spike passes cleanly; otherwise
  register-existing-binary only. Windows LuaJIT builds are out regardless (Risk 1.1).
- **Resolved by**: **TOOLING-00-03** — its decision matrix is binding on TOOLING-04's
  `ProvisioningSpec` list for the `luajit` kind.
- **Decision (2026-07-06)**: **POSIX git+make ships in v1.** Spike PASS on Linux x86_64;
  no blocking darwin-arm64 finding. TOOLING-04 `luajit` kind gets
  `provisioning = [SourceBuildStrategy(git+make)]`, POSIX-only; Windows excluded.
  See [results/luajit-git-make.md](00-de-risking/results/luajit-git-make.md).

### Gap 2.5: Legacy-XML tolerance of the clean break
- **Question**: Do the new persistence components load a `lunar.xml` written by today's
  plugin (with `interpreters`, `toolInventory`, `hererocksEnvs`, `interpreterMode`,
  `explicitInterpreter/Target`, `activeEnvId` tags) without errors, and do stale tags
  disappear on the next save?
- **Options / leaning**: rely on the XML serializer's unknown-tag tolerance (leaning,
  must be proven); else keep dead `@Transient`-style fields one release (fallback).
- **Resolved by**: **TOOLING-00-06**.

## Technical Debt & Future Work
- **TBD: git `@ref` installs** — hererocks supports `repo@ref` sources via a `git`
  subprocess (dossier §1); deferred from v1 (JGit is the candidate if ever needed). The
  feed covers released versions only.
- **TBD: Windows source builds** — revisit only if prebuilt coverage proves insufficient
  (Risk 1.1 mitigation holds for v1).
- **TBD: version-update notifications** — PRD non-goal, unchanged.
- **TBD: remote/WSL/SSH provisioning** — PRD non-goal; the platform seam (`EelApi`) is
  noted in the platform research §6.
- **TBD: Windows CI automation** — Windows execution checks are agent-drivable on the
  existing `win11` KVM/virt-manager VM over VNC (TOOLING-00-02 uses this: revert the
  `Fresh Install` snapshot, run the provisioned `lua.exe`/`luarocks.exe` from CMD/PowerShell).
  The `Fresh Install` snapshot is a **bare box with no IDE installed**, so it verifies *binary
  execution* only. The IDE-side Windows checks (integrated-terminal PATH injection, in-IDE
  provisioning) need a plugin-loaded GoLand on the guest; they run against the second
  **`IDE Installed`** snapshot (GoLand + JetBrains Toolbox, already created — revert it, license
  via Toolbox, hot-swap the Lunar jar per run). Executing those checks is TOOLING-03/-04
  verification (plan M2/M3), keeping `Fresh Install` alongside. Even then these are interactive
  VM sessions, not CI jobs; a CI-integrated Windows E2E pipeline remains future work.

## Pre-Implementation De-risking Tasks

The de-risking actions are the TOOLING-00 feature; full method, thresholds, and
deliverables live in [00-de-risking/design.md](00-de-risking/design.md).

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| TOOLING-00-01 | POSIX PUC-Lua source-build spike (per-TU recipe, baked paths, no readline) | Risk 1.2, 1.4; Gap 2.1 | done — PASS; see [results/posix-source-build.md](00-de-risking/results/posix-source-build.md) |
| TOOLING-00-02 | Windows prebuilt-provisioning spike (LuaBinaries + standalone luarocks; live execution on the Win11 KVM VM over VNC) | Risk 1.1; Gap 2.2 | done — PASS; `lua54.exe -v`→5.4.2 & `luarocks.exe --version`→3.13.0 live in both shells, SHA-256 matches pin, no SmartScreen/AV block. **New risk surfaced**: SourceForge is now Cloudflare-gated against programmatic fetch (see Gap 2.2). See [results/windows-prebuilt.md](00-de-risking/results/windows-prebuilt.md) |
| TOOLING-00-03 | LuaJIT git+make spike (POSIX; darwin-arm64 assessment) | Risk 1.3; Gap 2.4 | done — PASS; provisioning ships POSIX-only; see [results/luajit-git-make.md](00-de-risking/results/luajit-git-make.md) |
| TOOLING-00-04 | C-rock install spike (`luarocks install busted`; no-cc failure UX) | Risk 1.5; Gap 2.3 | done — PASS; see [results/c-rock-install.md](00-de-risking/results/c-rock-install.md) |
| TOOLING-00-05 | Platform download-infra classpath check + version-feed JSON format | Risk 1.6; Gap 2.2 | done — PASS; see [results/download-infra.md](00-de-risking/results/download-infra.md) |
| TOOLING-00-06 | Clean-break serialization spike (new state round-trip; legacy XML tolerated & dropped) | Risk 1.7; Gap 2.5 | done — PASS; see [results/clean-break-serialization.md](00-de-risking/results/clean-break-serialization.md) |

Risks 1.8 and 1.9 carry design-time mitigations owned by TOOLING-02/-05 (see above); they
need no spike because the required behavior is already pinned by the contract (§4) and an
existing regression test.

## Test Case Gaps
- Windows-execution assertions (`lua.exe -v`, SmartScreen/AV behavior) are covered live
  by TOOLING-00-02 on the Windows 11 KVM/virt-manager VM over VNC; the remaining gap is
  that these are agent-driven interactive sessions, not repeatable CI checks (see
  Technical Debt: Windows CI automation).
- darwin-arm64 LuaJIT build is assessed on paper in v1 (no macOS hardware in the
  harness) — TOOLING-00-03 records the assessment; a live run is future work.

## See Also
- Epic: [requirements.md](requirements.md) · PRD: [tooling-product-requirements.md](tooling-product-requirements.md)
- Architecture contract: [tooling-architecture.md](tooling-architecture.md)
- Research: [research-hererocks-dossier.md](research-hererocks-dossier.md), [research-platform-provisioning.md](research-platform-provisioning.md)
- Spikes: [00-de-risking/requirements.md](00-de-risking/requirements.md)
