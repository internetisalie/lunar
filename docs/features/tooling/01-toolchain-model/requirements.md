---
id: "TOOLING-01"
title: "01: Unified Toolchain Model & Registry"
type: "feature"
status: "in_progress"
priority: "high"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING-01: Unified Toolchain Model & Registry

## Overview

The foundation of the [[features/tooling/requirements|TOOLING]] epic: one descriptor-driven
model for every external Lua binary (interpreters included), one application-level inventory
service, one discovery scanner, and one version/banner probe engine. It replaces the closed
`LuaToolType` enum + `LuaTool` inventory (`tool/`), the parallel interpreter subsystem
(`platform/LuaInterpreterService.findInterpreters`, `Banner`, `LuaInterpreterFamily`), and the
per-mutation-silent `LuaToolManager` registration path with the `toolchain.model` /
`toolchain.registry` / `toolchain.discovery` / `toolchain.probe` packages defined by the
binding [architecture contract](../tooling-architecture.md) (§1, §2, §4, §7).

This feature lands **dark**: no existing consumer is rewired (that is TOOLING-05). It delivers
the new model, registry, discovery, and probe with full unit coverage, persisting to a new
application-level state alongside — not migrating — the legacy fields.

## Scope

### In Scope

- `toolchain.model`: `LuaToolKind` descriptor (id, display name, binary-name candidates
  incl. globs, `ProbeSpec`, capabilities, minimum version, `ProvisioningSpec` **shape only**),
  `LuaRegisteredTool`, `LuaToolHealth`, `LuaRuntimeInfo`, `SemanticVersion`.
- `toolchain.registry`: `LuaToolchainRegistry` APP service — inventory CRUD, global bindings
  storage, app-level persistence (`lunar.xml`); `LuaToolKindRegistry` built-in kind list;
  `LuaToolchainListener` application topic fired on **every** mutation.
- `toolchain.discovery`: `LuaToolDiscovery` — the single PATH + well-known-directory scanner
  replacing both `tool/LuaToolDiscoveryService` and
  `platform/LuaInterpreterService.findInterpreters` (interpreter families become
  RUNTIME-capability kinds with glob binary-name candidates).
- `toolchain.probe`: `LuaToolProbe` — the single version/banner probe replacing
  `tool/LuaToolValidator` and the `platform` `Banner` parsing; RUNTIME kinds additionally fill
  `LuaRuntimeInfo` (product / version / language level / platform).
- Built-in kinds with **complete** probe specs: `lua`, `luajit`, `tarantool`, `luarocks`,
  `luacheck`, `stylua`, `luacov`, `busted` — plus documentation of how a future kind
  (e.g. `redis-cli`, `lua-language-server`) is added as data.
- Kind inference from a binary filename (manual "Add tool" flows and discovery both use it).
- Threading discipline (contract §10) and non-mutating reads (health is data; re-probing is an
  explicit, background-only operation).

### Out of Scope

- Resolution/binding precedence, environments, project-level bindings — **TOOLING-02**.
- Process-execution service and PATH/`LUA_PATH`/`LUA_CPATH` injection — **TOOLING-03**.
- Provisioning strategy *implementations* (downloads, builds, `luarocks install`) —
  **TOOLING-04**. This feature defines only the `ProvisioningSpec` data shape; every built-in
  kind ships with an empty `provisioning` list.
- Consumer cutover and deletion of legacy state/services — **TOOLING-05**. Until then the
  legacy fields (`LuaApplicationSettings.State.interpreters` at
  `settings/LuaApplicationSettings.kt:39`, `toolInventory` at `:45`, `globalToolBindings` at
  `:53`) continue to exist and serve today's consumers.
- Settings UI — **TOOLING-06**. Health monitor, banners, notifications — **TOOLING-07**.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| TOOLING-01-01 | **Kind descriptor model** | M | Partial | `LuaToolKind` is a data descriptor (id, displayName, binaryNames, ProbeSpec, capabilities, minVersion, provisioning specs) replacing the `LuaToolType` enum and `LuaInterpreterFamily`. |
| TOOLING-01-02 | **Built-in kind registry** | M | Full | `LuaToolKindRegistry` ships 8 built-in kinds (`lua`, `luajit`, `tarantool`, `luarocks`, `luacheck`, `stylua`, `luacov`, `busted`) with complete probe specs; lookup by id is O(1); the set is a data list. |
| TOOLING-01-03 | **Registered-tool & health model** | M | Partial | `LuaRegisteredTool` (immutable) is the single inventory entry for tools *and* interpreters; `LuaToolHealth` separates fileExists / executable / probeOk(nullable) / probedAtMtime / reason; `isUsable` derives from them. |
| TOOLING-01-04 | **Runtime info model** | M | Partial | RUNTIME-capability kinds carry `LuaRuntimeInfo` (product, version, `LuaLanguageLevel`, `LuaPlatform`, banner line) filled by the probe; mapping rules reproduce today's `LuaInterpreterFamily` levelers. |
| TOOLING-01-05 | **Semantic version handling** | M | Partial | `SemanticVersion` parses `major[.minor[.patch]][-suffix]` and compares numerically component-by-component (never lexicographically). |
| TOOLING-01-06 | **Inventory CRUD** | M | Not Implemented | `LuaToolchainRegistry` registers (probe + persist), refreshes in place (dedup by canonical path — same path never creates a twin), unregisters by id, and lists tools; registration of a probe-failing binary still records it with failing health. |
| TOOLING-01-07 | **Global bindings storage** | M | Not Implemented | The registry stores app-level default bindings `kindId → toolId` (string-keyed map) with get/set/clear API. Precedence/resolution is TOOLING-02. |
| TOOLING-01-08 | **Change events on every mutation** | M | Not Implemented | Every register / update / unregister / binding change fires `LuaToolchainListener.TOPIC` on the application message bus (fixes the silent `registerTool` → stale `LuaTerminalEnvironmentService` cache defect). |
| TOOLING-01-09 | **Unified discovery** | M | Not Implemented | One scanner searches PATH then well-known directories for all kinds' binary candidates (with Windows `.bat`/`.exe`/`.cmd` variants, `${ENV}` substitution, directory and filename globs), deduplicates by canonical path, and claims exact-name matches before glob matches. |
| TOOLING-01-10 | **Unified probe engine** | M | Not Implemented | One probe runs `path + probe.args` with the spec's timeout, merges stdout+stderr (stdout first), applies the kind's regexes in defined order, enforces `minVersion`, and returns version + health + (RUNTIME) runtime info. Non-zero exit alone is not a failure; only timeout/exec-failure/regex-miss/product-mismatch/below-min are. |
| TOOLING-01-11 | **Kind inference from filename** | M | Full | Given a filename, strip a `.exe`/`.bat`/`.cmd` suffix, lowercase, then match kinds' binaryNames exact-first, glob-second, in registry order. |
| TOOLING-01-12 | **App-level persistence (clean break)** | M | Not Implemented | New top-level state `LuaToolchainAppState` (`tools: List<RegisteredToolState>`, `globalBindings: Map<String,String>`, `kindOptions: Map<String,String>`) persists in `lunar.xml` via a new `PersistentStateComponent<LuaToolchainAppState>`, round-trips through the XML serializer, and does **not** read or migrate the legacy fields (deleted by TOOLING-05). |
| TOOLING-01-13 | **Pure reads & threading** | M | Not Implemented | `tools()`/`tool(id)`/binding getters never touch the filesystem or mutate state (unlike `LuaToolManager.getTools()`, `tool/LuaToolManager.kt:110-122`); probe/discovery/registration are background-thread-only; mutations are thread-safe. |
| TOOLING-01-14 | **Data-only extensibility** | S | Full | Adding a kind (e.g. `redis-cli`) requires only a new `LuaToolKind` entry in the built-in list — no registry, discovery, probe, or persistence code changes. Documented with a worked example. |
| TOOLING-01-15 | **LuaRocks Lua-compat capture** | S | Partial | The probe captures LuaRocks' "for Lua X.Y" hint when present (today's `detectLuaVersion`, `tool/LuaToolValidator.kt:97-102`) into `LuaRegisteredTool.luaVersion` for later compatibility checks (TOOLING-02/07). |

## Detailed Specifications

### TOOLING-01-01/02: Kinds

A kind is data, never an enum branch. The 8 built-in kinds and their complete probe specs
(binary candidates, probe args, version regexes, timeouts, runtime mappings, minimum versions)
are pinned in [design.md §2.2/§4](design.md) — extracted from the legacy code they replace:
version regexes and flags from `tool/LuaToolValidator.kt:32-68,174-180`, binary-name candidates
from `tool/LuaToolDescriptor.kt:34-39`, interpreter families/levelers from
`platform/LuaInterpreter.kt:100-140`, banner parsing from
`platform/LuaInterpreterService.kt:172-201`.

### TOOLING-01-03: Health is data

`LuaTool.isValid` (`tool/LuaTool.kt:41`) conflates "file exists", "executable", and "version
probe succeeded". The new `LuaToolHealth` keeps them distinct, keeps the TOOL-03 mtime gate
(`tool/LuaTool.kt:51`), and is only ever *written* by an explicit probe — never by a read.

### TOOLING-01-06: Registration semantics

- New path → probe → append entry (fresh UUID) → fire `TOOL_REGISTERED`.
- Known canonical path → re-probe → replace entry fields in place (same id) → fire
  `TOOL_UPDATED`. (Refresh-in-place mirrors `tool/LuaToolManager.kt:69-77`, but keyed by
  *canonical* path so a symlink twin is not re-added.)
- A binary that exists and is executable but fails its probe **is registered** with
  `probeOk = false` and a reason — visible-but-unusable beats silently dropped (differs from
  `LuaToolManager.registerTool`, which returns the tool with `isValid=false` only when the
  regex missed but rejects unknown kinds; kind inference failure without a `kindIdHint` is
  still a rejection here, since no ProbeSpec exists to run).

### TOOLING-01-09: Discovery sources

The unified search-root list merges today's two scanners (both replaced):
`tool/LuaToolDiscoveryService.kt:27-41` (`EXTRA_DIRS_UNIX`/`EXTRA_DIRS_WINDOWS`) and
`platform/LuaInterpreterService.kt:133-152` (`PATHS_UNIX`/`PATHS_WINDOWS`, incl.
`${HOME}` substitution and `C:\Program Files\Lua 5.*` directory globs). Interpreter
discovery by filename glob (`lua5.*`) is new capability: today `findInterpreters` only checks
the literal `lua` name per family (`platform/LuaInterpreter.kt:101-119`), missing Debian-style
`lua5.4` binaries.

### TOOLING-01-12: Persistence

New component `LuaToolchainRegistry` (`@State(name = "LuaToolchainRegistry",
storages = [Storage("lunar.xml")])`) with XML-serializable beans, string-keyed maps per the
existing serializer rationale (`settings/LuaApplicationSettings.kt:48-53`: *"Keyed by the
enum's `name` (not the enum itself) so the IntelliJ XML serializer round-trips it reliably"*).
Legacy fields it supersedes (deletion is TOOLING-05, cited here per the clean-break decision):
`LuaApplicationSettings.State.interpreters` (`settings/LuaApplicationSettings.kt:39`),
`State.toolInventory` (`:45`), `State.globalToolBindings` (`:53`).

## Behavior Rules

- **Event on every mutation**: no registry state change without a synchronous
  `LuaToolchainListener.TOPIC` publish. (Today `LuaToolManager.registerTool` fires nothing,
  so the `LuaTerminalEnvironmentService` cache — invalidated only by
  `LuaSettingsChangedListener.TOPIC`, `tool/LuaTerminalEnvironmentService.kt:40-49` —
  goes stale.)
- **Reads never mutate**: a deleted binary shows its last-known health until an explicit
  `refreshTool` runs (background). No lazy re-validation inside getters.
- **Probe failure ≠ registration failure**: health records the reason; the inventory keeps
  the entry.
- **Exact beats glob**: in discovery claim order and kind inference alike.
- **PATH beats well-known dirs**: first discovery occurrence of a canonical path wins.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | 01-04, 01-10 | Fake `lua` binary printing `Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio` on **stdout**, exit 0 | `LuaToolProbe.getInstance().probe(lua kind, path)` | `ok=true`, `version="5.4.6"`, `runtime.product="Lua"`, `runtime.languageLevel=LUA54`, `runtime.platform=STANDARD`, `failure=null` |
| 2 | 01-10 | Fake `lua` printing `Lua 5.1.5  Copyright (C) 1994-2012 Lua.org, PUC-Rio` on **stderr** (PUC ≤5.3 prints `-v` to stderr), exit 0 | probe with lua kind | merged-stream match → `ok=true`, `version="5.1.5"`, `languageLevel=LUA51` |
| 3 | 01-04 | Output `LuaJIT 2.1.1700008891 -- Copyright (C) 2005-2023 Mike Pall. https://luajit.org/` | probe with luajit kind | `version="2.1.1700008891"`, `languageLevel=LUA51` (fixed), `platform=LUAJIT` |
| 4 | 01-10, 01-15 | Output `/usr/local/bin/luarocks 3.11.0`⏎`LuaRocks main command-line interface` | probe with luarocks kind | `ok=true`, `version="3.11.0"` (regex `LuaRocks\s+(\S+)` case-insensitive finds the path-line token), `luaVersion=null` |
| 5 | 01-15 | Same as #4 plus a line containing `for Lua 5.4` | probe with luarocks kind | `luaVersion="5.4"` |
| 6 | 01-05, 01-10 | Output `LuaRocks 2.4.4` | probe with luarocks kind (minVersion 3.0.0) | `ok=false`, `version="2.4.4"`, `failure="LuaRocks 2.4.4"` (first non-blank merged line per the §3.4 taxonomy); derived health `probeOk=false, reason="LuaRocks 2.4.4"` |
| 7 | 01-10 | Output `Luacheck: 0.26.0` / `stylua 0.20.0` / bare `2.2.0` (busted) / `--help` text containing `LuaCov 0.15.0 - coverage analyzer for Lua` | probe with respective kind | versions `0.26.0` / `0.20.0` / `2.2.0` / `0.15.0`; luacov probed with `--help` (it has no `--version`, `tool/LuaToolValidator.kt:179`) |
| 8 | 01-10 | `lua`-kind probe of a binary printing `LuaJIT 2.1.0 ...` | probe | `ok=false`, `runtime=null`, `failure="LuaJIT 2.1.0 ..."` (first non-blank merged line — the mismatching banner — per the §3.4 taxonomy) |
| 9 | 01-10 | Path that does not exist | probe | `ok=false`, `failure="Not executable"`, derived health `fileExists=false`, `probeOk` untracked/null, no process spawned |
| 10 | 01-10 | Fake binary sleeping longer than the spec timeout | probe (sentinel `LuaProcessUtil.PROCESS_TIMEOUT_EXCEPTION_CODE`) | `ok=false`, `failure="Timeout"` (exact taxonomy string) |
| 11 | 01-05 | `"3.11.0-1"`, `"3.9"`, `"not-a-version"` | `SemanticVersion.parse` | `(3,11,0)`, `(3,9,0)`, `null`; and `(3,9,2) < (3,11,0)` (numeric, not lexicographic) |
| 12 | 01-09 | Temp dir A with executable `lua`, temp dir B containing a symlink to it; both passed as search roots | `LuaToolDiscovery.discover(lua kind, roots)` | exactly **one** candidate (canonical-path dedup), path = first-seen (dir A) |
| 13 | 01-09 | Temp dir with executables `lua5.4` and `luajit` | `discoverAll` over both kinds | `lua5.4` claimed by `lua` kind (glob `lua5.*`); `luajit` claimed by `luajit` kind (exact), **not** by any glob |
| 14 | 01-11 | Filenames `luarocks.bat`, `LUA5.4.EXE`, `stylua`, `foo` | `LuaToolKindRegistry.inferKind` | `luarocks`, `lua` (suffix stripped, lowercased, glob match), `stylua`, `null` |
| 15 | 01-06, 01-08 | Empty registry + app-bus subscriber on `LuaToolchainListener.TOPIC` | `registerTool(validPath)` | inventory size 1; listener received exactly one event with `kind=TOOL_REGISTERED` and the new tool's id |
| 16 | 01-06, 01-08 | Registry containing tool at path P | `registerTool(P)` again (same canonical path) | still 1 entry, same `id`, refreshed fields; event `TOOL_UPDATED` |
| 17 | 01-07, 01-08 | Registered tool T of kind `stylua` | `setGlobalBinding("stylua", T.id)` then `globalBindings()` | map contains `"stylua" -> T.id`; event `GLOBAL_BINDING_CHANGED` fired; `setGlobalBinding("stylua", null)` removes the entry + fires again |
| 18 | 01-12 | Registry state with 2 tools (one RUNTIME with full `LuaRuntimeInfo`, one probe-failed) + 1 binding | `XmlSerializer.serialize(state)` → deserialize → map to models | model-level equality: ids, kindIds, versions, health tri-state, runtime fields, binding map all round-trip |
| 19 | 01-13 | Registered tool whose binary is then deleted from disk | `tools()` (twice) | returned health unchanged (last probed state); state object unmodified; **no** disk access. `refreshTool(id)` afterwards → `fileExists=false`, `TOOL_UPDATED` fired |
| 20 | 01-06 | Executable file that is **not** a known kind, no hint | `registerTool(path)` | returns `null`, no event, inventory unchanged (no ProbeSpec to run) |
| 21 | 01-01 | The `lua` kind literal from `LuaToolKindRegistry.BUILT_IN` | read its descriptor fields | `id="lua"`, `displayName="Lua"`, `binaryNames=["lua","lua5.*","lua-5.*"]`, `probe.args=["-v"]`, `capabilities` contains `RUNTIME`, `probe.runtime.productToken="Lua"`; `isRuntime==true` |
| 22 | 01-02 | `LuaToolKindRegistry` | `all()`; then `findById` for each of `lua, luajit, tarantool, luarocks, luacheck, stylua, luacov, busted`, and `findById("nope")` | `all()` has exactly 8 kinds in §4.1 order; each id resolves to its kind (O(1) map lookup); `findById("nope")==null` |
| 23 | 01-03 | `LuaToolHealth` combinations, one `LuaRegisteredTool` per row: (a) `fileExists=T, executable=T, probeOk=true`; (b) `T,T,null` (never probed); (c) `T,T,false`; (d) `T,false,true`; (e) `false,false,true` | evaluate `isUsable` | (a)→`true`, (b)→`true` (probeOk `!= false`), (c)→`false`, (d)→`false`, (e)→`false` (truth table: `fileExists && executable && probeOk != false`) |
| 24 | 01-03, 01-12 | One RUNTIME `LuaRegisteredTool` (full `LuaRuntimeInfo`, `origin=DISCOVERED`, `environmentId=null`) | map model → `RegisteredToolState` bean → back to model | resulting model equals the input field-for-field (id, kindId, path, version, luaVersion, runtime.product/version/languageLevel/platform/banner, origin, environmentId, health tri-state) — round-trip identity |
| 25 | 01-06, 01-08 | Registered tool T whose current health/version already equal the values to be written | `updateToolCheck(T.id, sameHealth, sameVersion, sameLuaVersion, sameRuntime)` | inventory unchanged; **no** `LuaToolchainListener.TOPIC` event fired (no-op when unchanged); a subsequent `updateToolCheck` with a *changed* health fires exactly one `TOOL_UPDATED` |

## Acceptance Criteria

- [ ] All model types exist under `net.internetisalie.lunar.toolchain.model` with the exact
  shapes in design §2 (TOOLING-01-01…05).
- [ ] `LuaToolKindRegistry.BUILT_IN` contains the 8 kinds with the probe-spec table of design
  §4 verbatim (TOOLING-01-02).
- [ ] `LuaToolchainRegistry` is registered as an application service in `plugin.xml` and
  persists/round-trips its state in `lunar.xml` (TOOLING-01-06/07/12).
- [ ] Every mutation observably fires `LuaToolchainListener.TOPIC` (TOOLING-01-08, TC 15-17).
- [ ] Discovery and probe behave per TC 1-13 on POSIX CI (Windows-specific candidate expansion
  covered by pure unit tests with `windows=true` parameters, as `LuaToolDescriptor.candidates`
  tests do today).
- [ ] No legacy code path is modified or removed; full existing suite stays green.
- [ ] Test cases 1-25 automated; `python3 scripts/lint_docs.py docs` clean.

## Non-Functional Requirements

- **Threading (contract §10, engineering contract):** discovery, probing, and any registry
  method that probes (`registerTool`, `refreshTool`, `autoDiscover`) run on background threads
  only and assert non-EDT; reads and binding setters are cheap and thread-safe (synchronized
  mutation, snapshot reads); events publish synchronously via the application message bus.
- **No memory-model violations:** the app service holds no `Project`/`Editor`/PSI references
  (none are needed; project scoping arrives in TOOLING-02, passed per-call as
  `LuaToolManager.getEffectiveTool` does today, `tool/LuaToolManager.kt:163`).
- **Performance:** probe timeout per spec (default 10 s, `tool/LuaToolValidator.kt:22`
  precedent); discovery is O(roots × kinds × candidates) with no VFS access (pure
  `java.io`/`java.nio`, avoiding `LuaInterpreterService`'s VFS-based directory listing).

## Dependencies

- **Depends on:** TOOLING-00 only for provisioning-spec grounding (TOOLING-00-05 download
  infra / version-feed format informs `ProvisioningSpec` fields; the shape here is
  deliberately minimal so 00 outcomes cannot invalidate it).
- **Blocks:** TOOLING-02 (resolver reads this registry), TOOLING-03 (exec consumes
  registered tools), TOOLING-04 (provisioner registers results here), TOOLING-05/06/07.

## See Also

- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Architecture contract: [../tooling-architecture.md](../tooling-architecture.md)
- Epic PRD: [../tooling-product-requirements.md](../tooling-product-requirements.md)
