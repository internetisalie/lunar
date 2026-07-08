---
id: "TOOLING-04-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOLING-04"
folders:
  - "[[features/tooling/04-native-provisioning/requirements|requirements]]"
---

# Technical Design: TOOLING-04 — Native Provisioning Engine

Authoritative inputs: the [hererocks dossier](../research-hererocks-dossier.md) (URL
patterns, build recipes, layout — copied here, not re-derived) and the
[platform provisioning research](../research-platform-provisioning.md) (JdkInstaller flow:
`HttpRequests` → size → SHA-256 → `Decompressor`). Naming/boundaries per the
[architecture contract](../tooling-architecture.md) §1/§6.

## 1. Architecture Overview

### Current State
Provisioning shells out to Python hererocks and covers only lua/luarocks:
- `HererocksLocator.resolvePrefix()` probes PATH `hererocks`, then `python3/python -m
  hererocks` (`rocks/env/HererocksLocator.kt:22-34`), with a pip remediation string
  (`:17-19`).
- `HererocksProvisioner` (`@Service(Service.Level.PROJECT)`,
  `rocks/env/HererocksProvisioner.kt:24-25`) runs one `Task.Backgroundable` per request
  (`:40-50`), **serialized per directory** via `ConcurrentHashMap.newKeySet` (`:28,36-39`,
  test seams `tryReserve`/`release` `:81-85`), captures output with
  `LuaProcessUtil.capture` at a 600 s timeout (`:66,97`), tails 20 stderr lines on failure
  (`:74,98`), and on exit 0 binds via
  `LuaProjectSettings.upsertAndActivate` (`settings/LuaProjectSettings.kt:236-254`).
- `HererocksEnvBinder.bind` registers `bin/luarocks` through
  `LuaToolManager.registerTool` (`rocks/env/HererocksEnvBinder.kt:42`,
  `tool/LuaToolManager.kt:49`) and repoints the interpreter/target
  (`HererocksEnvBinder.kt:49-60`); `normalizeDir` canonicalizes directory keys (`:94-95`).
- `HererocksEnvState` models the spec + tree accessors (`binDir`/`luaExe`/`luarocksExe`,
  `rocks/env/HererocksEnvState.kt:27-34`) and target mapping (`:45-50`).
- Actions Create/Upgrade/Recreate/Remove (`rocks/env/HererocksEnvActions.kt:10-85`),
  dialog (`rocks/env/CreateHererocksEnvDialog.kt:21-85`, default dir
  `<projectBase>/.lua` `:34-35`, flavor-gated version combo `:54-60`), batch provision
  (`rocks/env/matrix/BatchProvisionAction.kt:20-53`), detect-on-open
  (`rocks/env/HererocksDetectStartup.kt:17-27`, registered `plugin.xml:433-435`), action
  group `plugin.xml:643-668`.

Deficiencies: external Python dependency, lua/luarocks only, no checksums, no version
feed, no idempotency manifest, no dev-tool installs.

### Prior Art in This Repo
Searched `rocks/env/*`, `tool/*`, `util/*`, `rocks/*` for download/build/registration code:
- **Replaced** (registrations swapped here; class deletion in TOOLING-05): all
  `rocks/env/Hererocks*` classes and `CreateHererocksEnvDialog` cited above.
- **Kept as the pattern to mirror**: `HererocksProvisioner`'s background-task +
  per-directory serialization (`HererocksProvisioner.kt:28,35-51`) — the new orchestrator
  keeps exactly these semantics.
- **Consumed, not duplicated**: `LuaToolExecutionService` (TOOLING-03, contract §5)
  replaces direct `LuaProcessUtil.capture` (`util/LuaProcessUtil.kt:17`);
  `LuaToolchainRegistry`/`LuaEnvironmentState` ops (TOOLING-01/-02, contract §2/§3)
  replace `LuaToolManager.registerTool` + `LuaProjectSettings.upsertAndActivate`.
- **Precedent for platform APIs in this repo**: bundled Gson already used
  (`rocks/RockspecBridge.kt:3-4`); `PathManager` already used
  (`rocks/LuaRocksBridgeFiles.kt:3`); `PathEnvironmentVariableUtil` already used
  (`rocks/env/HererocksLocator.kt:4,36`); `SystemInfo` already used
  (`rocks/env/HererocksEnvState.kt:3`); app-service idiom `@Service(Service.Level.APP)`
  (`settings/LuaApplicationSettings.kt:29`); notification group
  `notification.group.lunar.tools` registered (`plugin.xml:543`).
- No existing downloader/extractor/checksum code exists in the repo (searched
  `HttpRequests|Decompressor|Hashing` — no hits) — those components are new, per the
  platform research.

### Target State
```
LuaProvision*Action ──► LuaProvisionDialog ──► LuaProvisionRequest
                                                   │
                                        LuaToolProvisioner (APP service)
                                                   │  Task.Backgroundable, per-rootDir lock
                        ┌──────────────────────────┼───────────────────────────┐
                 LuaToolchainFeed          strategy per item              LuaEnvManifest
                 (bundled JSON)     ReleaseBinary / SourceBuild /        (.lunar-env.json,
                                        LuaRocksInstall                  identifiers hash)
                                   │            │           │
                        LuaArtifactDownloader   build recipes    LuaToolExecutionService
                        (HttpRequests+SHA256)   (pure command    (TOOLING-03, INSTALL)
                        LuaArchiveExtractor      plans)
                                                   │
                             LuaToolchainRegistry.registerProvisioned (TOOLING-01)
                             environment upsert + activate (TOOLING-02)
```

## 2. Core Components

All new classes live in `net.internetisalie.lunar.toolchain.provision` (contract §1).

### 2.1 `net.internetisalie.lunar.toolchain.provision.LuaProvisionRequest`
- **Responsibility**: immutable description of one provisioning job.
- **Key API**:
  ```kotlin
  data class LuaProvisionItem(val kindId: String, val versionSpec: String)
  data class LuaProvisionRequest(
      val environmentName: String,
      val rootDir: String,                 // canonicalized on construction
      val items: List<LuaProvisionItem>,
  )
  ```

### 2.2 `net.internetisalie.lunar.toolchain.provision.LuaToolProvisioner`
- **Responsibility**: orchestrator — validates, serializes per rootDir, runs the pipeline
  (§3.1), registers results.
- **Threading**: `provision()` callable from EDT; all work inside `Task.Backgroundable`
  (mirrors `HererocksProvisioner.kt:40-50`). `@Service(Service.Level.APP)`; no `Project`
  field — project passed per call (contract §11; idiom: `tool/LuaToolManager.kt:28`).
- **Collaborators**: `LuaToolchainFeedLoader`, strategies, `LuaEnvManifest`,
  `LuaToolchainRegistry` + TOOLING-02 env ops, `LuaToolExecutionService`.
- **Key API**:
  ```kotlin
  @Service(Service.Level.APP)
  class LuaToolProvisioner {
      fun provision(project: Project, request: LuaProvisionRequest)
      internal fun tryReserve(rootDir: String): Boolean   // test seam, as today
      internal fun release(rootDir: String)
      companion object { fun getInstance(): LuaToolProvisioner }
  }
  ```
  Reservation key = `FileUtil.toCanonicalPath(File(rootDir).absolutePath)` (exactly
  `HererocksEnvBinder.normalizeDir`, `rocks/env/HererocksEnvBinder.kt:94-95`); refusal
  balloon on group `notification.group.lunar.tools` (`plugin.xml:543`).

### 2.3 `net.internetisalie.lunar.toolchain.provision.LuaHostPlatform`
- **Responsibility**: current OS/arch pair used for strategy selection and feed asset match.
- **Key API**:
  ```kotlin
  enum class LuaOs { LINUX, MACOS, WINDOWS }          // FreeBSD out of v1 feed; recipe table keeps its row
  enum class LuaArch { X86_64, AARCH64 }
  data class LuaHostPlatform(val os: LuaOs, val arch: LuaArch) {
      companion object { fun current(): LuaHostPlatform }
  }
  ```
- **Algorithm** (`current()`): os from `SystemInfo.isWindows/isMac/isLinux`; arch from
  `System.getProperty("os.arch")`: `"amd64"|"x86_64"` → X86_64, `"aarch64"|"arm64"` →
  AARCH64; anything else → provisioning unsupported error (message names the arch).

### 2.4 `net.internetisalie.lunar.toolchain.provision.LuaProvisioningStrategy` (+ 3 impls)
- **Responsibility**: one provisioning mechanism (contract §6 names them).
- **Threading**: background task thread only.
- **Key API**:
  ```kotlin
  data class LuaProvisionedComponent(
      val kindId: String, val resolvedVersion: String, val strategyId: String,
      val primaryBinary: Path, val extraBinaries: List<Path>, val identifiersHash: String,
  )
  interface LuaProvisioningStrategy {
      val id: String   // "release-binary" | "source-build" | "luarocks-install"
      fun supports(item: LuaProvisionItem, platform: LuaHostPlatform, feed: LuaToolchainFeed): Boolean
      fun provision(context: LuaProvisionContext, item: LuaProvisionItem): LuaProvisionedComponent
  }
  class ReleaseBinaryStrategy : LuaProvisioningStrategy      // §3.7
  class SourceBuildStrategy : LuaProvisioningStrategy        // dispatches to §2.9 recipes; supports()==false on WINDOWS
  class LuaRocksInstallStrategy : LuaProvisioningStrategy    // §3.8
  ```
  `LuaProvisionContext` bundles `(project, request, platform, feed, rootDir: Path,
  indicator: ProgressIndicator, toolchain: LuaCompilerProbe.Toolchain?)` (≤3-arg tripwire).

### 2.5 `net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed` (+ loader)
- **Responsibility**: typed model + parser of the bundled version feed.
- **Key API**:
  ```kotlin
  data class LuaToolchainFeed(val feedVersion: Int, val kinds: Map<String, LuaFeedKind>)
  data class LuaFeedKind(val aliases: Map<String, String>, val versions: List<LuaFeedVersion>)
  data class LuaFeedVersion(
      val version: String, val gatedOn: String?,        // e.g. "TOOLING-00-03"
      val source: LuaFeedSource?, val assets: List<LuaFeedAsset>, val rock: LuaFeedRock?,
  )
  data class LuaFeedSource(val urls: List<String>, val sha256: String, val size: Long, val rootPrefix: String)
  data class LuaFeedAsset(
      val os: String, val arch: String, val url: String, val sha256: String, val size: Long,
      val packaging: String,       // "tar.gz" | "zip" | "binary"
      val rootPrefix: String?,     // stripped via removePrefixPath
      val layout: String,          // "single-binary" | "tree" | "win-lua-binaries"
      val binaryPath: String,      // executable path inside the extracted payload
  )
  data class LuaFeedRock(val rockName: String, val pinnedVersion: String?, val binName: String,
                         val needsCToolchain: Boolean)
  object LuaToolchainFeedLoader {
      const val RESOURCE = "/toolchain/lunar-toolchain-feed.json"
      fun load(): LuaToolchainFeed                       // parsed once, cached
      fun resolveVersion(feed: LuaToolchainFeed, kindId: String, spec: String,
                         platform: LuaHostPlatform): LuaFeedVersion   // §3.2; throws LuaProvisionException
  }
  ```
- Parser: bundled Gson (`com.google.gson` — already used at `rocks/RockspecBridge.kt:3-4`),
  read via `LuaToolchainFeedLoader::class.java.getResourceAsStream(RESOURCE)`; any
  missing/mistyped field → `LuaProvisionException("Corrupt toolchain feed: …")` (a plugin
  packaging bug, covered by a unit test that loads the real resource).

### 2.6 `net.internetisalie.lunar.toolchain.provision.LuaArtifactDownloader`
- **Responsibility**: mirror-aware download with cache + verification (§3.4).
- **Key API**:
  ```kotlin
  class LuaArtifactDownloader(private val cacheDir: Path = defaultCacheDir()) {
      fun fetch(urls: List<String>, sha256: String, size: Long, indicator: ProgressIndicator): Path
      companion object {
          fun defaultCacheDir(): Path = Path.of(PathManager.getSystemPath(), "lunar", "downloads")
      }
  }
  ```
- Uses `com.intellij.util.io.HttpRequests.request(url).productNameAsUserAgent()
  .saveToFile(tmp, indicator)` (research §2; exemplar `JdkInstaller.kt:353`) and Guava
  `com.google.common.io.Files.asByteSource(file).hash(Hashing.sha256())` (research §4,
  `JdkInstaller.kt:379`). `PathManager` precedent: `rocks/LuaRocksBridgeFiles.kt:3`.

### 2.7 `net.internetisalie.lunar.toolchain.provision.LuaArchiveExtractor`
- **Responsibility**: archive → directory with prefix stripping, cancellation, exec bits.
- **Key API**:
  ```kotlin
  object LuaArchiveExtractor {
      fun extract(archive: Path, targetDir: Path, rootPrefix: String?, indicator: ProgressIndicator)
      fun restoreExecBit(file: Path)   // POSIX only: PosixFilePermissions.fromString("rwxr-xr-x")
  }
  ```
- `*.zip` → `Decompressor.Zip(archive).withZipExtensions()`; `*.tar.gz|*.tgz` →
  `Decompressor.Tar(archive)`; both `.removePrefixPath(rootPrefix)` when non-null and
  `.entryFilter { indicator.checkCanceled(); true }` (research §3, `Decompressor.java:43`).
  Packaging `"binary"` bypasses extraction (plain copy + `restoreExecBit`).

### 2.8 `net.internetisalie.lunar.toolchain.provision.LuaCompilerProbe`
- **Responsibility**: POSIX toolchain preflight (TOOLING-04-05). NO MSVC bootstrap.
- **Key API**:
  ```kotlin
  object LuaCompilerProbe {
      data class Toolchain(val cc: Path, val ar: Path, val ranlib: Path, val make: Path?)
      fun probe(platform: LuaHostPlatform): Toolchain?   // null when cc/ar/ranlib missing
      const val REMEDIATION = "No C toolchain found on PATH (need cc/gcc, ar, ranlib). " +
          "Install build tools (Linux: `sudo apt install build-essential`; macOS: " +
          "`xcode-select --install`) or pick a version with a prebuilt binary."
  }
  ```
- **Algorithm**: candidates via `PathEnvironmentVariableUtil.findInPath` (idiom:
  `rocks/env/HererocksLocator.kt:36`): cc = first of `["cc","gcc"]` on MACOS else
  `["gcc","cc"]` (dossier §2a); ar = `"ar"`; ranlib = `"ranlib"`; make = `"make"`
  (nullable — required only by the LuaRocks/LuaJIT recipes, which fail with "GNU make not
  found on PATH…" if null). Presence-only — no version output parsing.

### 2.9 Source-build recipes — `PucLuaBuildRecipe`, `LuaRocksBuildRecipe`, `LuaJitBuildRecipe`
- **Responsibility**: pure command-plan builders (unit-testable without a compiler);
  `SourceBuildStrategy` dispatches on `kindId` (`lua` / `luarocks` / `luajit`) so the
  contract's single `SourceBuildStrategy` name is preserved.
- **Key API**:
  ```kotlin
  data class BuildStep(val command: List<String>, val workDir: Path, val env: Map<String, String> = emptyMap())
  data class BuildPlan(val steps: List<BuildStep>,
                       val installCopies: List<Pair<Path, Path>>,  // source → dest inside rootDir
                       val executables: List<Path>)                // chmod + register candidates
  object PucLuaBuildRecipe {
      fun plan(version: String, os: LuaOs, toolchain: LuaCompilerProbe.Toolchain,
               buildDir: Path, prefix: Path): BuildPlan             // §3.5
      fun patchLuaconf(luaconfText: String, version: String, prefix: Path): String   // §3.6, pure
  }
  object LuaRocksBuildRecipe { fun plan(...): BuildPlan }           // §3.5 step L
  object LuaJitBuildRecipe { fun plan(...): BuildPlan }             // §3.9, gated
  ```
- Each `BuildStep` is turned into a `GeneralCommandLine(step.command)` with
  `.withWorkDirectory(step.workDir.toFile())` and `.withEnvironment(step.env)`, then run
  via `LuaToolExecutionService.getInstance().capture(cmd, LuaExecTimeout.INSTALL, indicator)`
  (contract §10.6 — the env map and working dir ride on the command line; there is no
  `execute`-style `(command, env, workDir)` facade). INSTALL = 600 s per step (matching today's
  `PROVISION_TIMEOUT_MS`, `HererocksProvisioner.kt:97`); a non-`COMPLETED` outcome or
  non-zero exit → `LuaProvisionException` carrying the command and the last 20 output lines
  (mirror `HererocksProvisioner.kt:74,98`).

### 2.10 `net.internetisalie.lunar.toolchain.provision.LuaEnvManifest`
- **Responsibility**: `<rootDir>/.lunar-env.json` — idempotency record (hererocks
  `hash_identifiers()` semantics, dossier §3/§5) and "Lunar-provisioned tree" marker
  (JdkInstaller marker-file idea, research §Recommendations).
- **Key API**:
  ```kotlin
  data class LuaManifestComponent(val resolvedVersion: String, val strategyId: String,
      val identifiersHash: String, val binaries: List<String>,   // rootDir-relative
      val provisionedAtEpochMs: Long)
  data class LuaEnvManifest(val manifestVersion: Int, val environmentId: String,
      val environmentName: String, val request: LuaProvisionRequest,
      val components: Map<String, LuaManifestComponent>) {
      companion object {
          const val FILE_NAME = ".lunar-env.json"
          fun read(rootDir: Path): LuaEnvManifest?   // null on absent/corrupt → full re-provision
          fun write(rootDir: Path, manifest: LuaEnvManifest)
      }
  }
  ```
- Serialized with Gson; schema in §4.5; hash algorithm in §3.3.

### 2.11 Actions — `net.internetisalie.lunar.toolchain.provision.LuaToolchainActions.kt`
Five `DumbAwareAction`s (idiom: `rocks/env/HererocksEnvActions.kt:10-85`), registered per §7:
- `LuaProvisionToolchainAction` — opens `LuaProvisionDialog(initial = null)`; OK →
  `LuaToolProvisioner.getInstance().provision(project, dialog.toRequest())`.
- `LuaChangeToolchainVersionsAction` — enabled iff the project has an active environment
  (TOOLING-02 state) whose rootDir manifest is readable; opens the dialog pre-filled from
  `manifest.request`; OK → provision (same rootDir; hash diff drives rebuilds, §3.3).
- `LuaRecreateToolchainAction` — enabled like Change; `Messages.showYesNoDialog` confirm
  (mirror `HererocksEnvActions.kt:45-50`) → `FileUtil.delete(rootDir)` on the task thread
  then provision `manifest.request` (mirror RECREATE, `HererocksProvisioner.kt:65`).
- `LuaRemoveToolchainAction` — `Messages.showYesNoCancelDialog` Delete / Unbind Only /
  Cancel (mirror `HererocksEnvActions.kt:66-75`); calls the TOOLING-02 remove/deactivate
  op + TOOLING-01 unregister of the env's PROVISIONED tools; optional dir delete on a
  pooled thread (mirror `HererocksEnvBinder.kt:86-90`).
- `LuaBatchProvisionToolchainsAction` — §3.10.

### 2.12 `net.internetisalie.lunar.toolchain.provision.LuaProvisionDialog`
- **Responsibility**: collect a `LuaProvisionRequest` (JdkDownloadDialog Kotlin-UI-DSL
  shape, research §5 `JdkDownloadDialog.kt:169`).
- **Threading**: EDT; reads only the bundled feed (no I/O beyond the resource).
- **Fields** (top to bottom, `panel { }` rows):
  1. **Name** — `JBTextField`; default `"lua-{runtimeVersion}"`, auto-updated on version
     change until the user edits it (a `userEditedName` flag flipped by a
     `DocumentListener` when the change isn't programmatic).
  2. **Root directory** — `TextFieldWithBrowseButton` + single-folder chooser (mirror
     `CreateHererocksEnvDialog.kt:36-40`); default `<projectBase>/.lua` (mirror `:34-35`,
     via `project.guessProjectDir()`).
  3. **Runtime** — `ComboBox<String>` of feed kinds with a runtime role that have ≥1
     visible (un-gated) version: `["lua"]`, plus `"luajit"` when TOOLING-00-03 opens the
     gate. Default `lua`.
  4. **Runtime version** — non-editable `ComboBox<String>`; items = visible versions of
     the selected kind, sorted by §3.11 descending; default = resolution of `"latest"`
     for the current platform. Repopulated on runtime change (mirror
     `CreateHererocksEnvDialog.kt:54-60`). Custom/git refs are NOT accepted in v1
     (deviation from the old editable combo — git installs deferred, dossier risk 7).
  5. **Include LuaRocks** — `JBCheckBox`, default checked; forced checked+disabled while
     any rock-installed tool below is checked. **LuaRocks version** combo beside it
     (default `latest` resolution).
  6. **Tools** — one row per tool kind: checkboxes `luacheck`, `stylua`, `busted`,
     `luacov`, `lua-language-server` (default unchecked), each with a version combo
     (default = feed `latest` resolution; single-version kinds show the pin).
- **Validation** (`doValidate()`): name non-blank and unique among existing TOOLING-02
  environment names → `"Name is required"` / `"An environment named '{n}' already
  exists"`; rootDir non-blank, and if the directory exists non-empty it must contain
  `.lunar-env.json` → `"Directory is not empty and is not a Lunar environment"`; rootDir
  must not contain `"` or `;` → `"Directory must not contain quotes or semicolons"`;
  runtime version selected.
- **Key API**: `fun toRequest(): LuaProvisionRequest` — items ordered runtime, luarocks,
  release-binary tools, rock tools.

### 2.13 `net.internetisalie.lunar.toolchain.provision.LuaBatchProvisionDialog`
Base directory field (default `<projectBase>/.lua-matrix`) + an add/remove row table of
(runtime kind, version) combos fed like §2.12. `fun toRequests(): List<LuaProvisionRequest>`
per §3.10 (derivation mirrors `matrix/BatchProvisionAction.kt:43-53`).

## 3. Algorithms

### 3.1 Provision pipeline (orchestrator)
- **Input → Output**: `(Project, LuaProvisionRequest)` → registered tools + active env, or
  notification of failure.
- **Steps**:
  1. Validate: `environmentName` non-blank; canonicalize rootDir
     (`FileUtil.toCanonicalPath(File(rootDir).absolutePath)`); reject rootDir containing
     `"` or `;`; `items` non-empty.
  2. `tryReserve(canonicalRootDir)`; on false → WARNING balloon "Provisioning already in
     progress for {dir}" and return (mirror `HererocksProvisioner.kt:36-39`).
  3. Queue `Task.Backgroundable(project, "Provisioning Lua toolchain", true)`; all
     subsequent steps run in `run(indicator)` with `release()` in `finally`.
  4. Load feed; `LuaHostPlatform.current()`; resolve every item's version (§3.2) —
     fail-fast before any download on unknown version.
  5. Order items: (a) runtime kind (`lua`/`luajit`), (b) `luarocks`, (c) items whose
     selected strategy is `release-binary`, (d) `luarocks-install` items; within a class,
     request order. If any (d) item exists and `luarocks` is neither requested nor already
     in the manifest → fail: "Installing {tools} requires LuaRocks in the environment."
  6. If any item selects `source-build` (or a (d) item has `needsCToolchain=true` on
     POSIX): `LuaCompilerProbe.probe()`; null → fail with `REMEDIATION` **before any
     download**.
  7. `manifest = LuaEnvManifest.read(rootDir)`; `environmentId = manifest?.environmentId
     ?: UUID.randomUUID().toString()`.
  8. For each item in order: compute identifiers hash (§3.3); if the manifest has a
     matching component whose recorded binaries all exist and are executable → skip
     (indicator text "{kind} {version} — up to date"). Else select + run the strategy
     (§3.12); on success merge the component into the manifest and `write` it
     immediately. `indicator.text = "Provisioning {kind} {version} ({strategyId})"`,
     `indicator.fraction = completed / items.size`, `checkCanceled()` before and after
     every download/extract/BuildStep.
  9. First component failure aborts the loop → ERROR notification "{kind} {version}
     failed: {message + 20-line tail}"; **no registry/environment mutation**.
  10. All succeeded → register every component's `primaryBinary` (plus `luac` as an extra
      binary of the `lua` tool record, not a separate tool):
      `LuaRegisteredTool(id = UUID, kindId, path, origin = PROVISIONED, environmentId)`
      via the TOOLING-01 registry op; then upsert + activate
      `LuaEnvironmentState(environmentId, environmentName, rootDir, toolIds)` via the
      TOOLING-02 op (which fires `LuaToolchainListener.TOPIC`, contract §4). INFO
      notification "Provisioned Lua toolchain '{name}' ({n} tools)".
- **Edge handling**: skip-all → step 10 still runs (re-activation is idempotent) with
  message "already up to date"; cancellation → `ProcessCanceledException` propagates,
  manifest keeps completed components, balloon "Provisioning cancelled".

### 3.2 Feed version resolution (aliases, platform-aware)
- **Input → Output**: `(feed, kindId, spec, platform)` → `LuaFeedVersion`.
- Definitions: a `LuaFeedVersion` is **visible** iff `gatedOn == null` (gates are opened
  by deleting the field when the referenced TOOLING-00 spike passes). It is
  **provisionable on platform P** iff visible AND (it has an asset matching P's
  `os`/`arch`, OR (`source != null` AND P.os != WINDOWS), OR `rock != null`).
- **Alias rule (hererocks single-application semantics, dossier §1) — authoritative:**
  **aliases are applied once; a value that is itself a feed version entry terminates
  resolution.** Concretely, at each step the alias map is applied to the *current* spec at
  most one time, and the instant the current value names a shipped/exact version entry
  present in the feed (a version with its own `versions[]` record), resolution **stops** —
  that value is never re-fed through the alias map. This is exactly hererocks' hardcoded
  table: `"5.1.0" → "5.1"` because 5.1.0 shipped as `lua-5.1.tar.gz`, and because `5.1` is a
  real shipped version, resolution terminates on it and does **not** continue `5.1 → 5.1.5`.
- **Steps**:
  1. `spec = spec.trim().ifEmpty { "latest" }`; treat `"^"` as `"latest"` (hererocks
     compat, dossier §1). Remember `originalSpec = spec` (used by the platform-aware
     fallback in step 4b).
  2. Exact-first termination: if a **provisionable** version with `version == spec` exists,
     return it immediately — a value that is itself a feed version entry ends resolution
     before any alias lookup. (A version that matches but is gated → error explaining why.)
  3. Single alias application: else, if `aliases[spec]` exists, set `spec = aliases[spec]`
     and **go back to step 2** — re-checking for a shipped entry first. Because the rule
     stops the moment the value is a shipped, on-platform-provisionable version,
     `"5.1.0" → "5.1"` halts on the `5.1` entry (it never re-applies `aliases["5.1"]`).
     Alias keys that only point at another alias key (`"latest" → "5"`, `"5" → "5.5.0"`)
     resolve because each hop re-enters step 2: `5` is not a shipped entry, so its alias
     `5.5.0` is looked up once and — being a shipped entry — terminates. A cycle or a value
     that is neither a shipped entry nor an alias key falls through to step 4. (Guard: if
     step 3 re-applies more than the number of alias keys, the feed has a cycle →
     feed-corrupt error.)
  4. Prefix match, tried in this order (first non-empty match wins):
     - **4a.** If the current `spec` matches `^\d+(\.\d+)?$` (a bare line like `5.4`), filter
       provisionable versions where `version.startsWith("$spec.")` → max by §3.11.
     - **4b. Platform-aware fallback.** Else, if step 3 alias-resolved a *bare line*
       `originalSpec` (matching `^\d+(\.\d+)?$`) to a concrete patch (e.g. `"5.4" → "5.4.8"`)
       that is **not provisionable on this platform** (Windows has no `5.4.8` Win64 asset),
       re-run 4a on `originalSpec` — i.e. filter provisionable versions
       `startsWith("$originalSpec.")` → max by §3.11. This yields 5.4.8 on Linux (its source
       entry is provisionable and terminates at step 2 via the alias) but the newest 5.4.x
       **with a Win64 asset** on Windows (= 5.4.2 in the §4.1 sample). It never fires for
       `"5.1"`, a real shipped entry matched exactly at step 2.
  5. Otherwise → `LuaProvisionException("Unknown {kindId} version '{spec}'. Known:
     {provisionable versions on this platform, sorted desc}")`.

  Worked resolutions under this rule: `5.1.0` → alias → `5.1` (shipped, provisionable) →
  **stop** (does NOT chase to `5.1.5`). `5.4` on Linux → alias → `5.4.8` (source-provisionable)
  → stop. `5.4` on Windows → alias → `5.4.8` (**not** Win64-provisionable) → step 4b
  re-prefixes `originalSpec="5.4"` over Win64-provisionable versions → 5.4.2. `latest` → `5`
  → `5.5.0` (shipped) → stop. `5.4.3` → provisionable shipped entry at step 2 → stop (exact
  hit). `9.9` → no entry, no alias, no prefix match → step 5 error.

### 3.3 Identifiers hash (idempotency)
- **Input → Output**: component inputs → 64-hex SHA-256.
- Hash = SHA-256 of the UTF-8 bytes of these lines joined with `"\n"` (fixed order):
  1. `kind={kindId}`
  2. `version={resolvedVersion}`
  3. `strategy={strategyId}`
  4. `os={LuaOs}` and 5. `arch={LuaArch}`
  6. `root={canonicalRootDir}` (source builds bake paths — location is part of the key,
     exactly like hererocks' build cache, dossier §5)
  7. `artifact={sha256-of-feed-source-or-asset}` — for `luarocks-install`:
     `rock={rockName}@{pinnedVersion ?: "latest"}`; for the gated git strategy:
     `git={ref}`
  8. `readline=false` (fixed in v1; kept in the hash so a future opt-in invalidates)
  9. `compat={space-joined compat defines from §3.5}` (empty line for non-source kinds)
- **Skip rule** (§3.1 step 8): hashes equal AND every recorded binary exists with the
  exec bit. Guava `Hashing.sha256().hashString(...)` — same dependency as §2.6.

### 3.4 Download cache keying & verification
- **Input → Output**: `(urls, sha256, size, indicator)` → cached file `Path`.
- **Cache key**: the URL's last path segment; if that segment equals `download`
  (SourceForge pattern `…/files/{ver}/{group}/{file}/download`, dossier §6), use the
  second-to-last segment. Keying-by-filename mirrors hererocks §5.
- **Steps**:
  1. `cacheDir.resolve(key)` exists → hash it; match → return; mismatch → delete
     (re-verification on every use, dossier §5).
  2. For each url in order (mirror list, e.g. lua.org then Tecgraf): download to
     `key + ".part"` via `HttpRequests…saveToFile(tmp, indicator)`; check
     `Files.size(tmp) == size`, then SHA-256 == pin; success → atomic move to `key`,
     return.
  3. Per-URL failure (IO, size, hash) is recorded and the next mirror tried; all mirrors
     exhausted → `LuaProvisionException` listing every URL with its error.

### 3.5 PUC-Lua POSIX build plan (`PucLuaBuildRecipe.plan`) — dossier §2a, verbatim
Let `X.Y` = major.minor (e.g. `5.4`), `XY` = digits (`54`), `{p}` = canonical prefix
(= rootDir), `{cc}` from the probe, buildDir = `<rootDir>/.build/lua-{version}` (tarball
extracted with `removePrefixPath("lua-{version}")`).

**CFLAGS table** — concatenation, space-separated, in this order:
| Component | Value |
|---|---|
| base | `-O2 -Wall -Wextra` |
| std (5.3/5.4/5.5 only) | `-std=gnu99` |
| target defines (LINUX/MACOS/FREEBSD) | `-DLUA_USE_POSIX -DLUA_USE_DLOPEN` |
| 5.2 extras | `-DLUA_USE_STRTODHEX -DLUA_USE_AFORMAT -DLUA_USE_LONGLONG` |
| readline | *(omitted — readline OFF in v1)* |
| compat: 5.1 | *(none)* |
| compat: 5.2 | `-DLUA_COMPAT_ALL` |
| compat: 5.3 | `-DLUA_COMPAT_5_1 -DLUA_COMPAT_5_2` |
| compat: 5.4 | `-DLUA_COMPAT_5_3` |
| compat: 5.5 | `-DLUA_COMPAT_MATHLIB` |

**LDFLAGS table** (readline libs dropped since readline is OFF):
| OS | Value |
|---|---|
| LINUX | `-Wl,-E -ldl -lm` |
| FREEBSD | `-Wl,-E -lm` |
| MACOS | `-lm` |

**Steps** (all `workDir = buildDir/src`):
1. Rewrite `src/luaconf.h` per §3.6 (in `installCopies` the **patched** file ships to
   `include/`).
2. `sources` = file names matching `*.c` in `src/`, sorted lexicographically, excluding
   `onelua.c`.
3. For each source `s.c`: `BuildStep([{cc}, …cflags…, "-c", "-o", "s.o", "s.c"])`.
4. `libObjs` = all objects except `lua.o`, `luac.o`, `print.o`;
   `BuildStep(["ar", "rcu", "liblua{XY}.a", …libObjs])`; then
   `BuildStep(["ranlib", "liblua{XY}.a"])` (paths from the probe).
5. If `luac.c` existed: `BuildStep([{cc}, "-o", "luac", "luac.o"] + (["print.o"] if
   `print.c` existed, 5.1 only) + ["liblua{XY}.a"] + ldflags)`.
6. `BuildStep([{cc}, "-o", "lua", "lua.o", "liblua{XY}.a"] + ldflags)`.
7. `installCopies`: `src/lua → {p}/bin/lua`, `src/luac → {p}/bin/luac` (if built),
   `src/{lua.h,luaconf.h,lualib.h,lauxlib.h} → {p}/include/`, `lua.hpp` from `src/` if
   present else `etc/` → `{p}/include/lua.hpp`, `src/liblua{XY}.a → {p}/lib/`. Also
   create empty `{p}/share/lua/{X.Y}/` and `{p}/lib/lua/{X.Y}/` (LuaRocks install
   targets, dossier §3). `executables = [{p}/bin/lua, {p}/bin/luac]`.
8. Orchestrator deletes `buildDir` after a successful install; a failed build's dir is
   retained for diagnosis and deleted at the start of the next attempt.

**Step L — LuaRocks recipe** (`LuaRocksBuildRecipe.plan`, dossier §2d; buildDir =
`<rootDir>/.build/luarocks-{version}`, `removePrefixPath("luarocks-{version}")`; requires
`make` from the probe):
1. `BuildStep(["./configure", "--prefix={p}", "--with-lua={p}"], workDir = buildDir)`
2. `BuildStep(["make", "build"], workDir = buildDir)` (all shipped versions are ≥ 3.0.0;
   the 2.0.x plain-`make` variant is not needed)
3. `BuildStep(["make", "install"], workDir = buildDir)`
4. Append to `{p}/etc/luarocks/config-{X.Y}.lua` (create if missing; `{X.Y}` = the
   provisioned runtime's version):
   ```lua
   variables = {
      CFLAGS = "-O2 -fPIC",
   }
   ```
   (dossier §2d post-install config write; POSIX default `-O2 -fPIC`.)
5. Verify `{p}/bin/luarocks` exists → `executables = [{p}/bin/luarocks]`.

### 3.6 `luaconf.h` patch (`patchLuaconf`, pure function) — dossier §2a item 4, §3
- **Input → Output**: `(fileText, version, prefix)` → patched text.
- **Steps**:
  1. Split into lines; find the **last** line whose trimmed form starts with `#endif`;
     error if none (unrecognized luaconf.h).
  2. Insert immediately before it:
     ```c
     /* patched by Lunar provisioner */
     #undef LUA_PATH_DEFAULT
     #define LUA_PATH_DEFAULT "{path}"
     #undef LUA_CPATH_DEFAULT
     #define LUA_CPATH_DEFAULT "{cpath}"
     ```
- **Baked path strings** (`{p}` = prefix, `{X.Y}` = version label; ordering rules from
  dossier §3: `./?.lua` first for 5.1, last otherwise; 5.3+ append `./?/init.lua` — 5.5
  follows the 5.4 pattern):
| Version | `LUA_PATH_DEFAULT` | `LUA_CPATH_DEFAULT` |
|---|---|---|
| 5.1 | `./?.lua;{p}/share/lua/5.1/?.lua;{p}/share/lua/5.1/?/init.lua` | `./?.so;{p}/lib/lua/5.1/?.so;{p}/lib/lua/5.1/loadall.so` |
| 5.2 | `{p}/share/lua/5.2/?.lua;{p}/share/lua/5.2/?/init.lua;./?.lua` | `{p}/lib/lua/5.2/?.so;{p}/lib/lua/5.2/loadall.so;./?.so` |
| 5.3/5.4/5.5 | `{p}/share/lua/{X.Y}/?.lua;{p}/share/lua/{X.Y}/?/init.lua;./?.lua;./?/init.lua` | `{p}/lib/lua/{X.Y}/?.so;{p}/lib/lua/{X.Y}/loadall.so;./?.so` |

### 3.7 `ReleaseBinaryStrategy.provision`
1. Asset = the resolved `LuaFeedVersion`'s entry with `os/arch` matching the platform
   (`supports()` already guaranteed one).
2. `file = downloader.fetch([asset.url], asset.sha256, asset.size, indicator)`.
3. By `asset.layout`:
   - `single-binary`: packaging `zip` → extract to a temp dir, take `binaryPath`;
     packaging `binary` → the downloaded file itself. Copy to
     `<rootDir>/bin/{kind.binaryName}{".exe" on Windows}`; `restoreExecBit` (zip loses
     POSIX bits — LuaRocks linux standalone & StyLua, dossier §6 / research §3).
   - `tree`: extract to `<rootDir>/tools/{kindId}/` with `removePrefixPath(rootPrefix)`;
     primary binary = `<rootDir>/tools/{kindId}/{binaryPath}`; `restoreExecBit` on it.
   - `win-lua-binaries`: extract into `<rootDir>/bin/`; copy `lua{XY}.exe → lua.exe` and
     `luac{XY}.exe → luac.exe` (originals + `lua{XY}.dll` stay beside them — the dll is
     load-bearing).
4. **Windows LuaRocks extra step**: after installing `bin\luarocks.exe`, write
   `<rootDir>/luarocks-config.lua` (§4.6) — the standalone exe has no baked tree
   (dossier §6).

### 3.8 `LuaRocksInstallStrategy.provision`
1. `luarocks = <rootDir>/bin/luarocks` (POSIX; baked config — absolute path suffices,
   dossier §7) or `<rootDir>\bin\luarocks.exe` (Windows).
2. Argv: POSIX `[luarocks, "install", rock.rockName] + (pin?)`; Windows
   `[luarocks, "--lua-dir", rootDir, "--tree", rootDir, "install", rock.rockName] +
   (pin?)`. `pin` = `rock.pinnedVersion` when non-null (e.g. `busted 2.2.0-1`, dossier §7).
3. Build `cmd = GeneralCommandLine(argv).withWorkDirectory(File(rootDir))`; on Windows also
   `cmd.withEnvironment(mapOf("LUAROCKS_CONFIG" to "<rootDir>/luarocks-config.lua"))` (the
   standalone exe has no baked tree). Run
   `LuaToolExecutionService.getInstance().capture(cmd, LuaExecTimeout.INSTALL, indicator)`
   (contract §10.6; INSTALL = 600 s). Env + working dir ride on the command line — there is
   no `execute(env, workDir)` facade.
4. Success = exit 0 AND `<rootDir>/bin/{rock.binName}` (Windows: `{binName}.bat`) exists;
   exit 0 without the wrapper → failure "install succeeded but no {binName} wrapper".
5. Failure classification: §4.4.

### 3.9 LuaJIT git+make (gated on TOOLING-00-03) — dossier §2c
`supports()` = platform.os != WINDOWS AND the feed's `luajit` entry is un-gated AND `git`
found on PATH (same `findInPath` probe) AND probe `make` non-null.
1. `BuildStep(["git", "clone", "https://github.com/LuaJIT/LuaJIT", buildDir])` — **full**
   clone, `.git` retained (`needs_git_dir_for_build`, dossier §1); then
   `BuildStep(["git", "-C", buildDir, "checkout", ref])` (`ref` = feed version string,
   e.g. `v2.1`).
2. `BuildStep(["make", "PREFIX={p}"], workDir = buildDir, env = mapOf(
   "MACOSX_DEPLOYMENT_TARGET" to "11.0"))` — env entry only on MACOS and only when the
   variable is unset (dossier §2c; the `11.0` value and `PREFIX` baking are exactly what
   TOOLING-00-03 validates before the gate opens).
3. Hand-copy install (no `make install`, dossier §2c): `src/luajit → {p}/bin/lua`;
   `src/libluajit.a → {p}/lib/libluajit-5.1.a`; `src/libluajit.so →
   {p}/lib/libluajit-5.1.so.2` (if built); `src/{lua.h,lauxlib.h,lualib.h,luaconf.h,
   lua.hpp,luajit.h} → {p}/include/`; `src/jit/*.lua → {p}/share/lua/5.1/jit/`.
4. **Descope fallback** (spike fails): the feed ships zero `luajit` versions → the kind
   has no visible versions → it never appears in the dialog runtime combo (§2.12 field 3)
   and `resolveVersion` rejects it; no other code changes.

### 3.10 Batch derivation
`toRequests()`: for each row `(kindId, version)` →
`LuaProvisionRequest(environmentName = "{kindId}-{version}", rootDir =
"{baseDir}/{kindId}-{version}", items = [(kindId, version), ("luarocks", "latest")])`
(mirrors `BatchProvisionAction.deriveSpecs`, `matrix/BatchProvisionAction.kt:43-53`).
Each request goes through `provision()` independently — the per-rootDir reservation
(§3.1 step 2) makes concurrent rows safe, duplicate rows collapse to one refusal balloon.

### 3.11 Version ordering (combo sort, prefix-match max)
Compare two version strings: split each on `[.-]` into tokens. Compare token-wise: two
numeric tokens numerically; missing token = `0` **unless** the other side's token is
non-numeric (a pre-release suffix like `beta3` — then the *shorter* version is GREATER:
`2.1.0 > 2.1.0-beta3`); two non-numeric tokens by (alpha part lexicographically, then
trailing digits numerically) — so `beta1 < beta2 < beta3 < rc1`. First difference wins.

### 3.12 Strategy selection per item
- **Input → Output**: `(item, platform, feed, kind.provisioning)` → executed strategy
  result.
- The ordered strategy list is **data on the kind descriptor** (`LuaToolKind.provisioning`,
  contract §2.1). TOOLING-04 defines the shipped order (dossier §Recommendations):

> **Phase-0 reconciliation (2026-07-08).** TOOLING-01 shipped `provisioning:
> List<ProvisioningSpec>` as an **OS-agnostic** list whose variants carry URL templates
> (`ReleaseBinary(urlTemplate,…)`, `SourceBuild(sourceUrlTemplate)`, `LuaRocksInstall(rockName)`),
> and every built-in kind currently ships `provisioning = emptyList()`. The per-OS table
> below is therefore realized as a **single ordered UNION list per kind** (e.g. `lua =
> [SourceBuild, ReleaseBinary]`), with the POSIX-vs-Windows split enforced entirely by each
> strategy's `supports(item, platform, feed)` — exactly the skip-if-unsupported iteration in
> **Steps** below (`SourceBuildStrategy.supports()==false` on Windows; `ReleaseBinaryStrategy`
> is false when the feed has no asset for the platform; gated LuaJIT stays out via its closed
> gate). The `ProvisioningSpec` **URL-template fields are vestigial** — the feed (§4.1) is the
> single source of URLs/SHA/size; the specs function purely as ordered strategy-id markers here
> and the URL fields are removed with the rest of the legacy shape in TOOLING-05. This is a
> code-organization choice only: `provisioning` is compiled-in `BUILT_IN` data, never persisted,
> so it carries **no** cross-OS/serialization consequence (the persisted `strategyId` in
> `.lunar-env.json` is an independent plain string). No change to the shipped `ProvisioningSpec`
> type shape is made in this feature.

| kindId | POSIX order | Windows order |
|---|---|---|
| `lua` | `[source-build]` | `[release-binary]` (LuaBinaries) |
| `luajit` | `[source-build]` (gated §3.9) | `[]` — not offered v1 |
| `luarocks` | `[source-build]` (configure/make) | `[release-binary]` (standalone zip) |
| `luacheck` | `[release-binary, luarocks-install]` (binary asset is linux-x86_64 only → macOS/aarch64 fall through to the rock) | `[release-binary]` (`luacheck.exe`) |
| `stylua` | `[release-binary]` | `[release-binary]` |
| `lua-language-server` | `[release-binary]` | `[release-binary]` |
| `busted` | `[luarocks-install]` | `[luarocks-install]` (C-rock → fails with v1 guidance, §4.4) |
| `luacov` | `[luarocks-install]` | `[luarocks-install]` (pure Lua — works) |

- **Steps**: iterate the kind's list in order; skip entries whose `supports()` is false
  (no asset for the platform, Windows source-build, closed gate); the first supporting
  strategy runs. If it throws, the next supporting strategy is tried (e.g. a failed
  luacheck binary download falls back to the rock); when all are exhausted the component
  fails with every attempt's error. Empty effective list → "No provisioning method for
  {kind} on {os}-{arch}".

## 4. External Data & Parsing

### 4.1 Version feed resource — `/toolchain/lunar-toolchain-feed.json`
File: `src/main/resources/toolchain/lunar-toolchain-feed.json`. Format is the JdkList-item
shape adapted per research §Recommendations; ratified by the TOOLING-00-05 spike. Sample
(structure normative; hashes/sizes are the maintainer-computed pins):

```json
{
  "feedVersion": 1,
  "kinds": {
    "lua": {
      "aliases": { "latest": "5", "^": "5", "5": "5.5.0", "5.1": "5.1.5", "5.1.0": "5.1",
                    "5.2": "5.2.4", "5.3": "5.3.6", "5.4": "5.4.8", "5.5": "5.5.0" },
      "versions": [
        { "version": "5.4.8",
          "source": { "urls": ["https://www.lua.org/ftp/lua-5.4.8.tar.gz",
                               "https://webserver2.tecgraf.puc-rio.br/lua/mirror/ftp/lua-5.4.8.tar.gz"],
                      "sha256": "<pin>", "size": 0, "rootPrefix": "lua-5.4.8" },
          "assets": [] },
        { "version": "5.4.2",
          "source": { "urls": ["https://www.lua.org/ftp/lua-5.4.2.tar.gz",
                               "https://webserver2.tecgraf.puc-rio.br/lua/mirror/ftp/lua-5.4.2.tar.gz"],
                      "sha256": "<pin>", "size": 0, "rootPrefix": "lua-5.4.2" },
          "assets": [
            { "os": "windows", "arch": "x86_64",
              "url": "https://sourceforge.net/projects/luabinaries/files/5.4.2/Tools%20Executables/lua-5.4.2_Win64_bin.zip/download",
              "sha256": "<pin>", "size": 0, "packaging": "zip", "rootPrefix": null,
              "layout": "win-lua-binaries", "binaryPath": "lua54.exe" } ] }
      ]
    },
    "luarocks": {
      "aliases": { "latest": "3", "^": "3", "3": "3.13.0" },
      "versions": [
        { "version": "3.13.0",
          "source": { "urls": ["https://luarocks.github.io/luarocks/releases/luarocks-3.13.0.tar.gz"],
                      "sha256": "<pin>", "size": 0, "rootPrefix": "luarocks-3.13.0" },
          "assets": [
            { "os": "windows", "arch": "x86_64",
              "url": "https://luarocks.github.io/luarocks/releases/luarocks-3.13.0-windows-64.zip",
              "sha256": "<pin>", "size": 0, "packaging": "zip",
              "rootPrefix": "luarocks-3.13.0-windows-64",
              "layout": "single-binary", "binaryPath": "luarocks.exe" } ] }
      ]
    },
    "stylua": {
      "aliases": { "latest": "2.5.2" },
      "versions": [
        { "version": "2.5.2", "assets": [
          { "os": "linux", "arch": "x86_64",
            "url": "https://github.com/JohnnyMorganz/StyLua/releases/download/v2.5.2/stylua-linux-x86_64.zip",
            "sha256": "<pin>", "size": 0, "packaging": "zip", "rootPrefix": null,
            "layout": "single-binary", "binaryPath": "stylua" } ] }
      ]
    },
    "lua-language-server": {
      "aliases": { "latest": "3.18.2" },
      "versions": [
        { "version": "3.18.2", "assets": [
          { "os": "linux", "arch": "x86_64",
            "url": "https://github.com/LuaLS/lua-language-server/releases/download/3.18.2/lua-language-server-3.18.2-linux-x64.tar.gz",
            "sha256": "<pin>", "size": 0, "packaging": "tar.gz", "rootPrefix": null,
            "layout": "tree", "binaryPath": "bin/lua-language-server" } ] }
      ]
    },
    "luacheck": {
      "aliases": { "latest": "1.2.0" },
      "versions": [
        { "version": "1.2.0",
          "assets": [
            { "os": "linux", "arch": "x86_64",
              "url": "https://github.com/lunarmodules/luacheck/releases/download/v1.2.0/luacheck",
              "sha256": "<pin>", "size": 0, "packaging": "binary", "rootPrefix": null,
              "layout": "single-binary", "binaryPath": "luacheck" },
            { "os": "windows", "arch": "x86_64",
              "url": "https://github.com/lunarmodules/luacheck/releases/download/v1.2.0/luacheck.exe",
              "sha256": "<pin>", "size": 0, "packaging": "binary", "rootPrefix": null,
              "layout": "single-binary", "binaryPath": "luacheck.exe" } ],
          "rock": { "rockName": "luacheck", "pinnedVersion": null, "binName": "luacheck",
                    "needsCToolchain": true } }
      ]
    },
    "busted": { "aliases": { "latest": "2.2.0" }, "versions": [
      { "version": "2.2.0", "assets": [],
        "rock": { "rockName": "busted", "pinnedVersion": "2.2.0-1", "binName": "busted",
                  "needsCToolchain": true } } ] },
    "luacov": { "aliases": { "latest": "0.16.0" }, "versions": [
      { "version": "0.16.0", "assets": [],
        "rock": { "rockName": "luacov", "pinnedVersion": null, "binName": "luacov",
                  "needsCToolchain": false } } ] }
  }
}
```
Shipped PUC versions: 5.1, 5.1.1–5.1.5, 5.2.0–5.2.4, 5.3.0–5.3.6, 5.4.0–5.4.8, 5.5.0
(dossier §1); LuaRocks 3.0.0–3.13.0. LuaJIT entries (each `"gatedOn": "TOOLING-00-03"`,
`"source"` carrying `{"urls": ["git"], "sha256": "", …}` unused — the git strategy reads
only the version-as-ref) are added when the spike passes. URL quirks encoded in data:
LuaLS has **no `v` tag prefix**; StyLua/luacheck do; LuaBinaries SourceForge URLs end in
`/download` (cache-key rule §3.4). The exact LuaBinaries group strings are observed on the
Windows VM by TOOLING-00-02; the luacheck linux standalone asset name is pinned by the
TOOLING-00-05 feed/asset spike — both before the pins are committed.

- **Parse strategy**: Gson tree → data classes with explicit null checks (no reflection
  defaults hiding missing pins); `sha256`/`size` mandatory on every source/asset (we
  self-pin even where upstream publishes no checksums — dossier risk 6).
- **Failure handling**: missing resource or parse error → `LuaProvisionException` at
  `load()`; the actions surface it as an ERROR balloon (plugin packaging bug).

### 4.2 Feed update procedure (maintainers)
1. Add/adjust the version entry + URLs in the JSON; update the kind's alias table
   (`latest`, minor aliases).
2. Pin: `curl -fL <url> -o /tmp/a && sha256sum /tmp/a && stat -c%s /tmp/a` → fill
   `sha256`/`size`. For PUC Lua, cross-check against the checksums published on
   <https://www.lua.org/ftp/> (dossier risk 6). LuaRocks `.asc` GPG check optional.
3. Run the feed unit tests (`LuaToolchainFeedTest`): schema completeness, alias closure
   (every alias value is either a shipped version entry — which terminates resolution per
   §3.2 — or another alias key that itself closes on a shipped version; no cycles),
   version-string sortability, URL well-formedness. No network in CI.
4. Note the bump in `CHANGELOG.md` when it changes user-visible defaults.

### 4.3 Build/install command output
No structured parsing anywhere: every `BuildStep` and rock install is judged by **exit
code only**; on failure the last 20 lines of merged stdout+stderr are kept for the
notification (mirrors `HererocksProvisioner.kt:74,98`). `configure`/`make`/`gcc`/`ar`
output is never inspected. The compiler preflight is presence-only (§2.8) — no `--version`
parsing.

### 4.4 Rock-install failure classification
Applied to the merged output of a failed (`exitCode != 0`) `luarocks install`:
- **Regex** (case-insensitive, multiline, `containsMatchIn`):
  `(?i)(gcc|cc1|cl)\b.*(not found|no such file)|error: failed (compiling|building)|lua\.h: no such file|could not find a c compiler`
- Match → **C-toolchain failure**: notification text (final copy owned by the
  TOOLING-00-04 outcome; this default ships until then): "Installing {rock} requires a C
  toolchain (it builds native modules). Linux: `sudo apt install build-essential`; macOS:
  `xcode-select --install`. On Windows, C rocks are not supported by the provisioner in
  v1." + 20-line tail.
- No match → generic: "luarocks install {rock} failed (exit {code})" + 20-line tail.

### 4.5 Environment manifest — `<rootDir>/.lunar-env.json`
```json
{
  "manifestVersion": 1,
  "environmentId": "3f9c…",
  "environmentName": "env54",
  "request": { "environmentName": "env54", "rootDir": "/p/.lua",
               "items": [ { "kindId": "lua", "versionSpec": "5.4" },
                           { "kindId": "luarocks", "versionSpec": "latest" } ] },
  "components": {
    "lua": { "resolvedVersion": "5.4.8", "strategyId": "source-build",
              "identifiersHash": "<64-hex>", "binaries": ["bin/lua", "bin/luac"],
              "provisionedAtEpochMs": 1751673600000 }
  }
}
```
Gson round-trip; `read` returns `null` on absent file or any parse/shape error (treated
as "provision everything"); binaries stored rootDir-relative with `/` separators.

### 4.6 Windows LuaRocks config — `<rootDir>/luarocks-config.lua`
Written by §3.7 step 4 (only on Windows; POSIX config comes from LuaRocks' own
`configure`):
```lua
lua_dir = [[{rootDir}]]
lua_version = "{X.Y}"
rocks_trees = {
    { name = "env", root = [[{rootDir}]] },
}
```
`{X.Y}` = the environment's runtime version. **This feature's only responsibility is to
write this file** at `<rootDir>/luarocks-config.lua`. The provisioner sets
`LUAROCKS_CONFIG=<rootDir>/luarocks-config.lua` on the `GeneralCommandLine` for its **own**
Windows rock-install step (§3.8) so the just-written config is picked up during
provisioning. For all later consumer-side invocations, the provisioner does **not** inject
the env var — TOOLING-03's env builder DERIVES `LUAROCKS_CONFIG` at run time from the active
environment root by the same convention (`<rootDir>/luarocks-config.lua`), contract §6.

## 5. Data Flow

### Example 1: fresh provision, linux-x86_64 (requirements TC 1)
Dialog OK → `provision(project, {env54, /p/.lua, [(lua,5.4),(luarocks,latest)]})` →
reserve `/p/.lua` → background task → feed: `5.4`→5.4.8, `latest`→3.13.0 → order:
lua, luarocks → compiler probe finds gcc/ar/ranlib/make → no manifest → **lua**:
downloader fetches `lua-5.4.8.tar.gz` (lua.org, SHA pin), extract to
`/p/.lua/.build/lua-5.4.8`, `patchLuaconf`, ~33 per-TU gcc steps + ar + ranlib + 2 links
via exec service, install copies, manifest written → **luarocks**: fetch tarball,
configure/make build/make install, CFLAGS append, manifest updated → register 2
`LuaRegisteredTool(PROVISIONED, envId)` → upsert+activate `LuaEnvironmentState` → INFO
balloon. Downstream (resolution, run configs) picks the env up via contract §3 — outside
this feature.

### Example 2: Windows, prebuilt-only (TC 6)
Same flow, no compiler probe; lua → `win-lua-binaries` extract + canonical copies;
luarocks → standalone zip + `luarocks-config.lua`; any busted request would fail per §4.4
guidance (C rock, no toolchain path on Windows v1).

### Example 3: idempotent re-run + version change (TC 11/12)
Re-run of an identical request: every hash matches + binaries exist → zero commands,
"already up to date", re-activate. Change lua spec to `5.4.6`: lua hash differs (line 2)
→ lua alone rebuilds; luarocks skipped (its hash does not include the runtime version —
by design, matching hererocks, whose luarocks identifiers are independent; the baked
`--with-lua` prefix is unchanged).

## 6. Edge Cases
- **Second provision for the same rootDir** → refused with a warning balloon; different
  rootDirs run concurrently (batch relies on this). (§3.1 step 2)
- **Cancellation** mid-download (indicator-driven abort inside `saveToFile`), mid-build
  (exec-service cancellation kills the process): manifest keeps completed components;
  no registration. (§3.1)
- **rootDir exists, non-empty, no manifest** → dialog validation error; if reached
  programmatically, `provision` fails the same check in step 1.
- **Offline with warm cache** → §3.4 step 1 satisfies source/asset fetches; a cold cache
  fails with per-mirror IO errors.
- **Checksum mismatch on cached file** → silent delete + re-download; on fresh download →
  mirror fallback → hard failure (never installs unverified bytes).
- **Zip exec-bit loss** (LuaRocks linux standalone, StyLua, dossier §6/risk 9) →
  `restoreExecBit` after every zip-sourced binary placement.
- **luacheck on macOS/aarch64-linux** → no binary asset → `supports()` false → falls
  through to `luarocks-install` (§3.12); requires luarocks in the request (checked at
  §3.1 step 5).
- **Gated LuaJIT requested by stale spec string** → `resolveVersion` rejects with the
  known-versions message (gated versions are not "visible").
- **`ar`/`ranlib` present but `make` missing** → PUC lua builds fine; luarocks recipe
  fails preflight with the make-specific message (§2.8).
- **Unknown CPU arch** (e.g. riscv64) → `LuaHostPlatform.current()` error names the arch;
  nothing attempted.
- **Manifest corrupt** → treated as absent: full re-provision over the same tree
  (idempotent copies overwrite).

## 7. Integration Points

New action group replaces the hererocks group (currently `plugin.xml:643-668`); the old
`<group id="net.internetisalie.lunar.rocks.env.HererocksEnvGroup">…</group>` block and the
detect-startup registration (`plugin.xml:433-435`) are **removed from plugin.xml in this
feature** (class deletion follows in TOOLING-05). `Lunar.Hererocks.RunMatrix` migrates
into the new group unchanged (same id/class — its env-model migration is TOOLING-05).

```xml
<!-- plugin.xml — <actions> section -->
<!-- Native toolchain provisioning (TOOLING-04) -->
<group id="Lunar.Toolchain.EnvironmentGroup" text="Lua Toolchain" popup="true">
  <add-to-group group-id="ToolsMenu" anchor="last"/>
  <action id="Lunar.Toolchain.Provision"
          class="net.internetisalie.lunar.toolchain.provision.LuaProvisionToolchainAction"
          text="Provision Lua Toolchain…"
          description="Download or build a Lua runtime, LuaRocks and dev tools into a project environment"/>
  <action id="Lunar.Toolchain.ChangeVersions"
          class="net.internetisalie.lunar.toolchain.provision.LuaChangeToolchainVersionsAction"
          text="Change Toolchain Versions…"
          description="Re-provision the active environment with different versions"/>
  <action id="Lunar.Toolchain.Recreate"
          class="net.internetisalie.lunar.toolchain.provision.LuaRecreateToolchainAction"
          text="Recreate Environment"
          description="Delete and rebuild the active environment directory"/>
  <action id="Lunar.Toolchain.Remove"
          class="net.internetisalie.lunar.toolchain.provision.LuaRemoveToolchainAction"
          text="Remove Environment"
          description="Unregister the environment's tools and optionally delete the directory"/>
  <action id="Lunar.Hererocks.RunMatrix"
          class="net.internetisalie.lunar.rocks.env.matrix.RunMatrixAction"
          text="Run Test Matrix…"
          description="Run the rockspec build/test command against every provisioned environment"/>
  <action id="Lunar.Toolchain.BatchProvision"
          class="net.internetisalie.lunar.toolchain.provision.LuaBatchProvisionToolchainsAction"
          text="Provision Version Matrix…"
          description="Provision a whole matrix of Lua versions in one action"/>
</group>
```

Other integration:
- `LuaToolProvisioner` is a `@Service(Service.Level.APP)` light service — **no plugin.xml
  entry** (same registration style as `HererocksProvisioner.kt:24`,
  `LuaApplicationSettings.kt:29`).
- Notifications on the existing group `notification.group.lunar.tools`
  (`plugin.xml:543`) — no new notification group.
- Bundled resource `src/main/resources/toolchain/lunar-toolchain-feed.json` (§4.1).
- Consumed APIs (dependencies, not new EPs), exactly per the contract's pinned signatures:
  - TOOLING-01 registry (contract §10.1):
    `LuaToolchainRegistry.getInstance().registerProvisioned(tool)` /
    `unregisterByEnvironment(environmentId)`.
  - TOOLING-02 project settings (contract §10.5 — **instance methods, no `project`
    parameter**): `LuaToolchainProjectSettings.getInstance(project)
    .upsertEnvironmentAndActivate(env)` and `.removeEnvironment(environmentId)`.
  - TOOLING-03 execution (contract §10.6): construct a `GeneralCommandLine` carrying the env
    map + working dir via `.withEnvironment(...)` / `.withWorkDirectory(...)`, then
    `LuaToolExecutionService.getInstance().capture(cmd, LuaExecTimeout.INSTALL, indicator)`
    (or `stream(...)` for streamed build output). There is **no**
    `execute`-style `(command, timeoutClass, env, workDir)` method.
- `LuaEnvStatusBarWidgetFactory` / matrix tool window (`plugin.xml:600-603`) are
  untouched here (TOOLING-05/-06).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-04-01 orchestration | M | §2.2, §3.1 |
| TOOLING-04-02 version feed | M | §2.5, §3.2, §4.1, §4.2 |
| TOOLING-04-03 download/verify/extract | M | §2.6, §2.7, §3.4 |
| TOOLING-04-04 PUC source build | M | §2.9, §3.5, §3.6 |
| TOOLING-04-05 C-toolchain preflight | M | §2.8, §3.1 step 6 |
| TOOLING-04-06 LuaRocks provisioning | M | §3.5 step L, §3.7 step 4, §4.6 |
| TOOLING-04-07 Windows prebuilt-first | M | §3.12 table, §3.7, §2.4 (`supports()` false) |
| TOOLING-04-08 release-binary tools | M | §3.7, §4.1 |
| TOOLING-04-09 rock installs | M | §3.8, §4.4 |
| TOOLING-04-10 manifest & idempotency | M | §2.10, §3.3, §4.5 |
| TOOLING-04-11 registration & activation | M | §3.1 steps 9–10 |
| TOOLING-04-12 actions & dialog | M | §2.11–§2.13, §7 |
| TOOLING-04-13 LuaJIT (gated) | S | §3.9, §3.2 (visibility) |
| TOOLING-04-14 batch provisioning | S | §2.13, §3.10 |
| TOOLING-04-15 progress & cancellation | S | §3.1 step 8, §6 |
| TOOLING-04-16 manifest re-detection | C | §2.10 marker (startup activity deferred; see §9) |

## 9. Alternatives Considered
- **Bundle/embed hererocks (Python)** — rejected by user decision (no Python dependency).
- **Platform `SdkType`/`ProjectJdkTable` machinery** — rejected; Lunar has its own
  registry/persistence model (research §Recommendations; contract §2/§7). Only the
  UI/list and installer *patterns* are borrowed.
- **Env-var paths for source builds** (no `luaconf.h` baking) — rejected: LuaRocks'
  configure hardcodes the prefix anyway and hererocks-compatible trees keep `luarocks
  install` working with a bare absolute path (dossier §7). Prebuilt binaries take the
  opposite choice (no baked prefix → TOOLING-03 injection), per dossier risk 2.
- **JGit for LuaJIT/git installs** — deferred with git-source installs generally
  (dossier risk 7); v1 LuaJIT uses external `git`, and `repo@ref` rock installs are
  Future Work.
- **Windows MSVC bootstrap (vswhere/vcvars)** — rejected for v1 (~300 lines, dossier
  risk 1); Windows is prebuilt-first by design.
- **Manifest-based re-detection at startup (TOOLING-04-16, Could)** — designed as a thin
  `ProjectActivity` successor to `HererocksDetectStartup.kt:17-27` reading
  `.lunar-env.json` from `<projectBase>/.lua`; deliberately scheduled last (plan Phase 8)
  and droppable without affecting any Must.
- **Registering `luac` as its own tool** — rejected: it is an auxiliary binary of the
  `lua` component (extra binary in the manifest), not a resolvable kind.

## 10. Open Questions

_None — the planning bar is cleared. Deferred decisions are TOOLING-00 de-risking outcomes referenced inline (§4.1 TOOLING-00-02/-05, §3.9 TOOLING-00-03, §4.4 TOOLING-00-04)._