---
id: "TOOLING-00-DESIGN"
title: "Spike Methodology & Acceptance"
type: "design"
parent_id: "TOOLING-00"
folders:
  - "[[features/tooling/00-de-risking/requirements|requirements]]"
---

# Technical Design: TOOLING-00 — De-risking & Technical Spikes

De-risking actions are exploratory, so "the bar" here is not a production implementation —
it is that **each spike has a defined question, a fully-pinned method (exact commands,
inputs, formats), a measurable pass/fail threshold, and a named deliverable**, so a
non-frontier executor can run each spike and decide unambiguously whether it passed and
what to hand downstream. This mirrors the reviewed-and-passed shape of
[TOOL-00's design](../../tool/00-de-risking/design.md).

## 1. Architecture Overview

### Current State
Provisioning today shells out to Python hererocks
(`src/main/kotlin/net/internetisalie/lunar/rocks/env/HererocksLocator.kt:16` probes
`hererocks` on PATH then `python3 -m hererocks`); the epic replaces it natively
(contract §6). Nothing in `src/main/kotlin` currently imports
`com.intellij.util.io.HttpRequests`, `com.intellij.util.io.Decompressor`, or
`com.google.common.*` (verified by grep, 2026-07-05) — hence the classpath spike.
Persistence lives in `LuaApplicationSettings.State`
(`src/main/kotlin/net/internetisalie/lunar/settings/LuaApplicationSettings.kt:36-54`) and
`LuaProjectSettings.State`
(`src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt:49-164`) — the
clean break (contract §7) deletes fields from both, hence the serialization spike.

### Prior Art in This Repo
- **TOOL-00 spike suite** (`docs/features/tool/00-de-risking/`) — methodology template;
  this feature copies its method/threshold/deliverable discipline. Its
  `LuaSettingsSerializationTest` idea is *extended* by TOOLING-00-06 (legacy-tag
  tolerance is new).
- **`LuaProcessUtil.capture`**
  (`src/main/kotlin/net/internetisalie/lunar/util/LuaProcessUtil.kt:17`) — existing
  subprocess capture; in-repo spike tests reuse it where a process must run.
- **`HererocksProvisioner` / `HererocksEnvState`**
  (`src/main/kotlin/net/internetisalie/lunar/rocks/env/`) — the legacy lifecycle the
  spikes' findings will *replace* (deleted in TOOLING-05); spikes read it only as a
  behavioral reference (`HererocksEnvState.luaExe()` layout expectations,
  `HererocksEnvState.kt:28-34`).
- **`notification.group.lunar.tools`** (`src/main/resources/META-INF/plugin.xml:543`) —
  existing notification group the 00-04 guidance UX targets (reused, not duplicated).
- Searched for existing downloader/extractor/hashing code (`grep -rn "HttpRequests\|
  Decompressor\|com.google.common" src/main/kotlin`): none found — TOOLING-00-05 breaks
  new ground, no overlap.

### Target State
Six throwaway spikes: three shell prototypes under `tooling/spikes/tooling-00/`
(git-tracked; `tooling/` already hosts dev tooling), two in-repo JUnit spike tests, and
one acquisition script — each emitting a results doc under
`docs/features/tooling/00-de-risking/results/`.

## 2. Per-Spike Specifications

### 2.1 TOOLING-00-01 — POSIX PUC-Lua source build
- **Question**: Does hererocks' per-TU recipe (dossier §2a), reimplemented standalone with
  no-readline defaults, produce a working prefix-baked Lua 5.4.8?
- **Prototype**: `tooling/spikes/tooling-00/build-lua-posix.sh <prefix>` (bash, ~100
  lines; this is the executable specification for TOOLING-04's `SourceBuildStrategy`).
- **Method** (exact steps; Linux flag-set — the macosx variant is listed after):
  1. Download `https://www.lua.org/ftp/lua-5.4.8.tar.gz` (fallback mirror
     `https://webserver2.tecgraf.puc-rio.br/lua/mirror/ftp/lua-5.4.8.tar.gz`) into a
     cache dir (`${XDG_CACHE_HOME:-$HOME/.cache}/lunar-spikes`).
  2. Verify SHA-256 against the pin published on the <https://www.lua.org/ftp/> listing
     page; hardcode the pin into the script at authoring time (it also seeds the §2.5
     feed sample). Mismatch → abort with the expected/actual digests.
  3. Extract; `cd lua-5.4.8`.
  4. **Patch `src/luaconf.h`** (the prefix-baking step, dossier §2a step 4): immediately
     before the final `#endif`, insert (with `<P>` = the absolute prefix):
     ```c
     #undef LUA_PATH_DEFAULT
     #define LUA_PATH_DEFAULT "<P>/share/lua/5.4/?.lua;<P>/share/lua/5.4/?/init.lua;./?.lua;./?/init.lua"
     #undef LUA_CPATH_DEFAULT
     #define LUA_CPATH_DEFAULT "<P>/lib/lua/5.4/?.so;<P>/lib/lua/5.4/loadall.so;./?.so"
     ```
     (5.4 ordering per dossier §3: `./?.lua` last, `./?/init.lua` appended.)
  5. Flags (Lua 5.4, linux, **no readline** — risks doc Risk 1.4):
     `CC=gcc`;
     `CFLAGS="-O2 -Wall -Wextra -std=gnu99 -DLUA_USE_POSIX -DLUA_USE_DLOPEN -DLUA_COMPAT_5_3"`;
     `LDFLAGS="-Wl,-E -ldl -lm"`.
     No `-DLUA_USE_READLINE`, no `-lreadline`.
  6. Compile per TU: for every `src/*.c` sorted, excluding `onelua.c`:
     `gcc $CFLAGS -c -o build/<name>.o src/<name>.c`.
  7. Archive: `ar rcu build/liblua54.a` all objects **except** `lua.o` and `luac.o`
     (5.4 has no `print.c`; when a version ships one — 5.1 — `print.o` is also excluded
     from the lib and added to the luac link); then `ranlib build/liblua54.a`.
  8. Link: `gcc -o <prefix>/bin/lua build/lua.o build/liblua54.a $LDFLAGS` and
     `gcc -o <prefix>/bin/luac build/luac.o build/liblua54.a $LDFLAGS`.
  9. Install: create `<prefix>/{bin,include,lib/lua/5.4,share/lua/5.4}`; copy
     `lua.h`, the **patched** `luaconf.h`, `lualib.h`, `lauxlib.h`, `lua.hpp` →
     `<prefix>/include/`; `liblua54.a` → `<prefix>/lib/`.
  - **macosx variant** (documented in the results doc; answers risks Gap 2.1 — not
    executed, no macOS host): `CC=cc`, same `CFLAGS`, `LDFLAGS="-lm"` (no `-Wl,-E`, no
    `-ldl` — dossier §2a step 3).
- **Pass threshold** (= requirements TC 1): script exits 0;
  `<prefix>/bin/lua -v` prints `Lua 5.4.8` on line 1;
  `<prefix>/bin/lua -e 'print(package.path)'` output begins with
  `<prefix>/share/lua/5.4/?.lua`; `ldd <prefix>/bin/lua` contains no `readline`.
- **Deliverable**: the script + `results/posix-source-build.md` (verdict, timings, the
  macosx flag-set, the recorded SHA-256 pin). → TOOLING-04 `SourceBuildStrategy`.

### 2.2 TOOLING-00-02 — Windows prebuilt provisioning
- **Question**: Can a working Windows toolchain be assembled purely from prebuilt
  downloads (no compiler), and what are the SmartScreen/AV caveats?
- **Execution decision (binding, per requirements)**: two agent-executable stages —
  acquisition/extraction/layout run **automated on Linux**; execution is verified
  **live on a Windows 11 VM under KVM/virt-manager**, driven over VNC with the same
  tooling and driving conventions as the containerized GoLand (see the `verify-in-ide`
  skill: screenshot-verify-act loop, OCR of terminal output).
- **Prototype**: `tooling/spikes/tooling-00/fetch-windows-prebuilt.sh <destdir>` (Linux
  stage) + a VM-driving session recorded in the results doc (Windows stage).
- **Method — stage 1, Linux acquisition**:
  1. Download the LuaBinaries Win64 zip via the SourceForge redirect pattern (dossier §6):
     `https://sourceforge.net/projects/luabinaries/files/<ver>/Tools%20Executables/lua-<ver>_Win64_bin.zip/download`
     with `curl -L`, for the newest 5.4-line version listed on
     <https://luabinaries.sourceforge.net> (record the exact version and confirm the
     `Tools Executables` group name — dossier flags it unrecorded).
  2. Download the standalone LuaRocks:
     `https://luarocks.github.io/luarocks/releases/luarocks-3.13.0-windows-64.zip`.
  3. `sha256sum` both archives → record pins (→ §2.5 feed sample).
  4. Extract both into `<destdir>/bin` (note: `unzip` loses no data here — PE files carry
     no POSIX exec bits; on the JVM side `Decompressor.Zip(...).withZipExtensions()` is
     the equivalent, exercised by 00-05).
  5. Assert layout: a `lua*.exe` + matching `lua*.dll` (record exact asset names — LuaBinaries
     names the exe `lua54.exe`) and `luarocks.exe` exist and are non-empty.
- **Method — stage 2, live VM execution (over VNC)**:
  1. **Prerequisite — ensure the Windows 11 VM exists**: `virsh list --all` on the
     workstation. If absent, first-time setup is required (create the VM in
     virt-manager from a Windows 11 ISO — TPM/secure-boot per Win11 requirements) and is
     part of this spike's recorded work; the spike must **not** silently assume the VM
     exists. Record the VM name and any setup performed in the results doc.
  2. Start the VM and connect with the VNC tooling used for the containerized GoLand
     (`vnc_connect` → `vnc_screenshot`/`vnc_type_string`/`vnc_key_tap`, per the
     `verify-in-ide` driving conventions: screenshot before/after every action).
  3. Assemble the provisioned tree **inside the VM**: open PowerShell and re-run the
     acquisition there (`Invoke-WebRequest` both zips → `Expand-Archive` into
     `C:\lunar-spike\bin`). Doing the download in-VM with `Invoke-WebRequest` is
     deliberate: it writes the `Zone.Identifier` ADS (Mark-of-the-Web), letting the
     spike **observe** SmartScreen behavior live rather than argue it on paper.
  4. Execute from the tree in both CMD and PowerShell:
     `C:\lunar-spike\bin\lua54.exe -v` and `C:\lunar-spike\bin\luarocks.exe --version`.
     Read the banners via VNC screenshot + OCR (`vnc_ocr_region`).
  5. **Caveat observation (live)**: record (a) whether SmartScreen/MOTW blocked or
     prompted on first execution (and the unblock path used, e.g.
     `Unblock-File C:\lunar-spike\bin\*`); (b) any Defender/AV reaction to the unsigned
     `lua54.exe`; (c) the expected difference for plugin-downloaded files — the JVM
     (`HttpRequests`) does not write `Zone.Identifier`, so TOOLING-04 downloads should
     not prompt; note this contrast against the observed in-VM behavior.
- **Pass threshold** (= TC 2): stage-1 downloads + layout assertions hold; stage-2 VNC
  OCR shows a `Lua 5.4` banner for `lua54.exe -v` and a `3.13.0` version line for
  `luarocks.exe --version`; results doc contains the pins, the VM name/setup record,
  and the observed SmartScreen/AV section (with screenshots).
- **Deliverable**: script + `results/windows-prebuilt.md` (incl. VM name/setup record
  and VNC evidence screenshots). → TOOLING-04 `ReleaseBinaryStrategy` (Windows story).

### 2.3 TOOLING-00-03 — LuaJIT git+make
- **Question**: Is git+make LuaJIT provisioning reliable enough to ship POSIX-only in v1?
- **Prototype**: `tooling/spikes/tooling-00/build-luajit-posix.sh <prefix>`.
- **Method**:
  1. `git clone --depth=1 --branch v2.1 https://github.com/LuaJIT/LuaJIT <src>` —
     **do not delete `.git`** (the build derives its rolling version from git metadata;
     dossier §1 `needs_git_dir_for_build`).
  2. `make -C <src> -j$(nproc)` (plain POSIX make path, dossier §2c; no `XCFLAGS` — no
     5.2-compat in the spike).
  3. Hand-install per hererocks (no `make install`): `src/luajit` → `<prefix>/bin/lua`;
     `src/libluajit.a` → `<prefix>/lib/libluajit-5.1.a`; `src/jit/` →
     `<prefix>/share/lua/5.1/jit/`.
  4. darwin-arm64 **paper assessment** in the results doc: `MACOSX_DEPLOYMENT_TARGET`
     must be set if unset; only recent `v2.1` checkouts support arm64 macs (dossier §8.3);
     no live run (no macOS hardware).
- **Pass threshold** (= TC 3): `<prefix>/bin/lua -v` first line starts with `LuaJIT 2.1`;
  `<prefix>/bin/lua -e 'print(jit.version)'` exits 0.
- **Decision matrix (binding on TOOLING-04)**:
  | Linux run | darwin-arm64 assessment | Decision for the `luajit` kind |
  |---|---|---|
  | PASS | no blocking finding | `provisioning = [SourceBuildStrategy(git+make)]`, POSIX-only; Windows excluded |
  | PASS | blocking finding | as above but macOS excluded too (Linux-only) |
  | FAIL | — | `provisioning = []` — register-existing-binary only (discovery unaffected) |
- **Deliverable**: script + `results/luajit-git-make.md` (verdict + assessment + the
  recorded decision). → TOOLING-04 scope; TOOLING-01 `luajit` kind descriptor.

### 2.4 TOOLING-00-04 — C-rock install & failure UX
- **Question**: Does `luarocks install busted` work against a spike-provisioned tree, and
  what exactly happens without a C compiler?
- **Prototype**: `tooling/spikes/tooling-00/install-crock.sh <prefix>` (expects the §2.1
  prefix).
- **Method**:
  1. Provision LuaRocks 3.13.0 into the tree (dossier §2d POSIX):
     download `https://luarocks.github.io/luarocks/releases/luarocks-3.13.0.tar.gz`,
     verify recorded SHA-256, extract, then
     `./configure --prefix=<prefix> --with-lua=<prefix> && make build && make install`.
  2. **Run A (cc present)**: `<prefix>/bin/luarocks install busted 2.2.0-1` (pinned for
     reproducibility, dossier §7). Assert exit 0, `<prefix>/bin/busted` exists,
     `<prefix>/bin/busted --version` exits 0.
  3. **Run B (cc absent, simulated deterministically)**: fresh tree, then
     `<prefix>/bin/luarocks install busted 2.2.0-1 CC=/nonexistent/cc LD=/nonexistent/cc`
     (LuaRocks accepts `VAR=value` config overrides on the command line — this simulates
     a host without a compiler without mutating the host). Capture exit code + combined
     output verbatim into the results doc.
  4. From Run B's real output, finalize the detection heuristic (§3.2) and write the
     guidance-notification copy, e.g.: *"Installing busted requires a C compiler
     (several of its dependencies are C rocks). Install gcc/clang (e.g.
     `apt install build-essential`) and retry."* — final wording recorded in the results
     doc, targeted at `notification.group.lunar.tools`
     (`src/main/resources/META-INF/plugin.xml:543`).
- **Pass threshold** (= TCs 4–5): Run A green; Run B captured with a heuristic that
  matches its output and would **not** match Run A's output.
- **Deliverable**: script + `results/c-rock-install.md`. → TOOLING-04
  `LuaRocksInstallStrategy`; TOOLING-07 notifications.

### 2.5 TOOLING-00-05 — Download-infra classpath check + version-feed format
- **Question**: Are `HttpRequests`, `Decompressor` (Tar + Zip `withZipExtensions`), and
  Guava `Hashing` on the plugin's compile classpath (some live in `platform/lang-impl` —
  platform research doc, Open Questions), and what is the feed format?
- **Prototype**: `src/test/kotlin/net/internetisalie/lunar/toolchain/LuaProvisioningClasspathSpikeTest.kt`
  (plain JUnit; hermetic — fixture archives under
  `src/test/resources/toolchain/` — no network).
- **Method**:
  1. The test **imports and executes** (compilation alone proves classpath presence;
     execution proves runtime linkage):
     - `com.intellij.util.io.HttpRequests.request("https://example.invalid")` — construct
       the builder only (no `connect()`), assert non-null.
     - `com.intellij.util.io.Decompressor.Tar(fixtureTarGz).extract(tempDir)` — fixture
       contains one file with mode `0755`; assert the extracted file is executable
       (exec-bit preservation, platform research §3).
     - `com.intellij.util.io.Decompressor.Zip(fixtureZip).withZipExtensions().extract(tempDir)`
       — assert content extracted.
     - `com.google.common.io.Files.asByteSource(f).hash(com.google.common.hash.Hashing.sha256())`
       on a fixture file; assert equality with its precomputed hex digest (the
       `JdkInstaller.kt:379` idiom).
  2. Commit the **feed sample** at `src/main/resources/toolchain/toolchain-feed.json`; the
     test parses it (any JSON parser on the test classpath, e.g. the platform's bundled
     Jackson/Gson — record which) and asserts required fields per item.
- **Feed format** (JdkList-style, per platform research recommendation — this is the
  schema TOOLING-04 productionizes):
  ```json
  {
    "feedVersion": 1,
    "aliases": { "lua": { "5.4": "5.4.8", "latest": "5.5.0" },
                 "luarocks": { "3": "3.13.0", "latest": "3.13.0" } },
    "items": [
      {
        "kind": "lua",
        "version": "5.4.8",
        "os": "*",
        "arch": "*",
        "strategy": "SOURCE_BUILD",
        "url": "https://www.lua.org/ftp/lua-5.4.8.tar.gz",
        "sha256": "<pin recorded by TOOLING-00-01 from the lua.org/ftp listing>",
        "size": 0,
        "packageType": "tar.gz",
        "rootPrefix": "lua-5.4.8"
      },
      {
        "kind": "lua",
        "version": "5.4.2",
        "os": "windows",
        "arch": "x86_64",
        "strategy": "RELEASE_BINARY",
        "url": "https://sourceforge.net/projects/luabinaries/files/5.4.2/Tools%20Executables/lua-5.4.2_Win64_bin.zip/download",
        "sha256": "<pin recorded by TOOLING-00-02>",
        "size": 0,
        "packageType": "zip",
        "rootPrefix": ""
      },
      {
        "kind": "luarocks",
        "version": "3.13.0",
        "os": "windows",
        "arch": "x86_64",
        "strategy": "RELEASE_BINARY",
        "url": "https://luarocks.github.io/luarocks/releases/luarocks-3.13.0-windows-64.zip",
        "sha256": "<pin recorded by TOOLING-00-02>",
        "size": 0,
        "packageType": "zip",
        "rootPrefix": ""
      },
      {
        "kind": "stylua",
        "version": "2.5.2",
        "os": "linux",
        "arch": "x86_64",
        "strategy": "RELEASE_BINARY",
        "url": "https://github.com/JohnnyMorganz/StyLua/releases/download/v2.5.2/stylua-linux-x86_64.zip",
        "sha256": "<self-pinned: download once, hash, commit — upstream ships no checksums>",
        "size": 0,
        "packageType": "zip",
        "rootPrefix": ""
      }
    ]
  }
  ```
  Field semantics: `kind` = `LuaToolKind.id` (contract §2.1); `os` ∈ `linux|macos|
  windows|*`; `arch` ∈ `x86_64|aarch64|*`; `strategy` ∈ `SOURCE_BUILD|RELEASE_BINARY|
  LUAROCKS_INSTALL` (contract §6 strategy names); `packageType` ∈ `tar.gz|zip`;
  `rootPrefix` = archive root dir to strip via `Decompressor.removePrefixPath`
  (empty = archive has no root dir); `size` = expected byte count, `0` = unchecked
  (spikes fill real values as recorded); `sha256` placeholders are filled by the spikes
  that download the asset — the committed sample must have at least the four entries
  above with real pins for every asset a spike actually downloaded. LuaJIT entries are
  added only if TOOLING-00-03 decides PASS (git source: `packageType": "git"` with
  `url` = repo, new field `gitRef`, `sha256` omitted — format extension recorded in the
  results doc if used).
- **Pass threshold** (= TC 6): test green under
  `tooling/gce-builder/gce-builder.sh run "test --tests *LuaProvisioningClasspathSpikeTest*"`;
  sample parses with all required fields.
- **Deliverable**: the test + the sample feed + `results/download-infra.md` (which
  classes resolved from which platform module, JSON parser used). → TOOLING-04
  download/verify/extract skeleton.

### 2.6 TOOLING-00-06 — Clean-break serialization
- **Question**: Do the new state classes round-trip, and does a legacy `lunar.xml` load
  cleanly into them with stale tags dropped on save (contract §7)?
- **Prototype**: `src/test/kotlin/net/internetisalie/lunar/toolchain/LuaToolchainSerializationSpikeTest.kt`
  plus spike-local state classes in the same test file (throwaway; TOOLING-01 ships the
  production versions under `net.internetisalie.lunar.toolchain`):
  ```kotlin
  class RegisteredToolState {
      var id: String = ""
      var kindId: String = ""
      var path: String = ""
      var version: String? = null
      var origin: String = "DISCOVERED"
      var environmentId: String? = null
  }
  class LuaToolchainAppState {
      var tools: MutableList<RegisteredToolState> = mutableListOf()
      var globalBindings: MutableMap<String, String> = HashMap()
  }
  class ToolEnvironmentState {
      var id: String = ""
      var name: String = ""
      var rootDir: String = ""
      var toolIds: MutableList<String> = mutableListOf()
  }
  class LuaToolchainProjectState {
      var bindings: MutableMap<String, String> = HashMap()
      var environments: MutableList<ToolEnvironmentState> = mutableListOf()
      var activeEnvironmentId: String? = null
      var luacheckArguments: String = ""
      var rocksServerUrl: String = ""
  }
  ```
  (Class and field names per contract §7 — the app inventory field is `tools`, chosen
  precisely so no legacy tag shares a name with a reshaped field; `var` + defaults per
  the sanctioned XML-serializer exception, as documented on
  `LuaProjectSettings.State.hererocksEnvs`, `settings/LuaProjectSettings.kt:118-124`.)
- **Method**:
  1. **Round-trip**: populate both states (≥2 tools, ≥1 env, ≥1 binding each);
     `com.intellij.util.xmlb.XmlSerializer.serialize(state)` → JDOM `Element` →
     `XmlSerializer.deserialize(element, <State>::class.java)`; assert field-by-field
     deep equality (data-class-style comparison helpers in the test).
  2. **Legacy tolerance**: `com.intellij.openapi.util.JDOMUtil.load(...)` a fixture
     string modeling today's real serialized shape — an element per component carrying
     `<option>` tags for the fields being deleted: app `interpreters` (list of
     `LuaInterpreter` beans), `toolInventory` (old `LuaTool` shape), `globalToolBindings`
     (`settings/LuaApplicationSettings.kt:39-53`); project `interpreter`,
     `interpreterMode`, `interpreterModeMigrated`, `explicitInterpreter`,
     `explicitTarget`, `hererocksEnvs`, `activeEnvId`, `projectToolBindings`
     (`settings/LuaProjectSettings.kt:53-130`). The fixture should be captured from a
     real sandbox `lunar.xml` where practical and pasted verbatim.
  3. Deserialize the fixture into the **new** state classes — assert **no exception**.
     No legacy tag shares a name with a new field (the contract renamed the app
     inventory to `tools` for exactly this reason), so the legacy `toolInventory` tag —
     like all other legacy tags — must be silently ignored; assert the loaded `tools`
     list is empty and record the actual serializer behavior.
  4. Re-serialize the loaded state; assert the emitted XML contains **none** of:
     `interpreters`, `toolInventory`, `globalToolBindings`, `hererocksEnv`,
     `hererocksEnvs`, `interpreterMode`, `interpreterModeMigrated`,
     `explicitInterpreter`, `explicitTarget`, `activeEnvId`, `projectToolBindings`.
     (The deprecated singular `hererocksEnv` — `settings/LuaProjectSettings.kt:117` —
     joins the fixture in step 2 as well. Contract §7's `kindOptions` map is deliberately
     absent from the spike state classes: its serializer mechanics are identical to
     `globalBindings` — same `Map<String,String>` shape — so it adds no spike signal.)
  5. Record in the results doc whether the serializer logged warnings for unknown tags
     (informational; warnings are acceptable, exceptions are not).
- **Pass threshold** (= TC 7): all assertions green in the unit suite.
- **Deliverable**: the test + `results/clean-break-serialization.md`. → TOOLING-01/-02
  persistence design; TOOLING-05 deletion safety.

## 3. Algorithms

### 3.1 Download-and-verify flow (shared by shell spikes)
- **Input → Output**: `(url, sha256pin, cacheDir)` → verified local archive path.
- **Steps**: (1) if `cacheDir/<filename>` exists, hash it; on match, reuse (hererocks
  cache semantics, dossier §5); (2) else `curl -fL --retry 3 -o` temp file; (3)
  `sha256sum` == pin, else delete + abort printing expected/actual; (4) move into cache.
- **Edge handling**: empty pin (first authoring run) → print the computed hash and abort
  with instruction to pin it; no partial files left behind (download to `.part`, rename).

### 3.2 C-toolchain-missing detection heuristic (finalized by TOOLING-00-04)
- **Input → Output**: `(exitCode, combinedOutput)` of a `luarocks install` run →
  `TOOLCHAIN_MISSING | OTHER_FAILURE | SUCCESS`.
- **Candidate rule** (Run B refines it against real output): `exitCode != 0` **and**
  combined output matches the case-insensitive regex
  `(command not found|No such file or directory|Failed installing dependency|error: failed compiling)`
  **and** at least one match line references the configured `CC`/compiler invocation.
  `SUCCESS` when `exitCode == 0` regardless of output.
- **Edge handling**: the rule must not match Run A (success) output; network failures
  (`Could not fetch`) classify as `OTHER_FAILURE`, not `TOOLCHAIN_MISSING`.

### 3.3 `lua -v` banner check (spike assertions)
- **Input → Output**: first line of `lua -v` (note: 5.4 prints to stdout; some versions
  use stderr — spikes read combined output) → pass/fail.
- **Rule**: PUC pass iff line matches regex `^Lua 5\.4\.8\b`; LuaJIT pass iff
  `^LuaJIT 2\.1\.`. (Production probing already exists in the interpreter subsystem and
  is unified later by TOOLING-01; the spikes assert with these regexes only.)

## 4. External Data & Parsing
- **Version-feed JSON** — schema + sample pinned in §2.5 (that section is normative).
- **SourceForge redirect URLs** (§2.2): `curl -L` follows the `…/download` redirect
  chain; failure mode = HTML error page instead of a zip — detect via `file`/magic-bytes
  check (`PK\x03\x04`) before hashing.
- **`luarocks install` output** (§2.4/§3.2): free-text, line-oriented; only the §3.2
  classification regex is applied — no structured parse.
- **lua.org/ftp checksum listing** (§2.1): human-readable page listing per-tarball
  `sha256:` values; the pin is copied manually at script-authoring time, not parsed at
  runtime.

## 5. Data Flow

### Example: POSIX chain (00-01 → 00-04)
1. `build-lua-posix.sh /tmp/lunar-spike-54` → verified tarball → patched `luaconf.h` →
   per-TU objects → `liblua54.a` → linked `bin/lua`, `bin/luac` → headers installed.
2. `install-crock.sh /tmp/lunar-spike-54` → luarocks configured against the prefix →
   Run A installs busted (wrappers in `bin/`, dossier §7) → Run B (CC override) fails →
   output captured → §3.2 heuristic finalized → results docs committed.

### Example: In-repo spikes (00-05 / 00-06)
Fixture archives / XML string → platform APIs (`Decompressor`, `XmlSerializer`) →
assertions → green tests in the normal suite → results docs summarize findings for
TOOLING-01/-04.

## 6. Edge Cases
- **Preempted builder VM** during spike-test runs: rerun per CLAUDE.md gce-builder
  guidance; spikes are idempotent (cache + fresh prefixes).
- **gcc present but broken** (Run B leakage): the `CC=/nonexistent/cc` override makes
  the "missing compiler" case deterministic instead of depending on host state.
- **LuaBinaries asset naming drift** (`lua54.exe` vs `lua.exe`): the layout assertion
  accepts `lua*.exe` and records the exact name for the feed.
- **`lua -v` on stderr**: spikes always capture combined output (§3.3).
- **Legacy `toolInventory` tag vs new `tools` field** (§2.6 step 3): no name collision by
  contract-§7 design; the spike asserts the old tag is ignored and dropped.
- **Windows 11 VM absent or unbootable** (§2.2 stage 2): the prerequisite `virsh list
  --all` check surfaces it; first-time ISO provisioning is in-scope, recorded work — the
  spike never fails silently on a missing VM.

## 7. Integration Points
**No `plugin.xml` registration, extension point, or production service ships from
TOOLING-00** — prototypes are throwaway; the two in-repo tests live in `src/test` only.
Findings feed: TOOLING-01 (kind descriptors, persistence shape), TOOLING-04 (strategies,
feed, failure UX), TOOLING-07 (notification heuristic). The only committed
`src/main` artifact is the sample feed resource `src/main/resources/toolchain/toolchain-feed.json`
(inert data; nothing reads it until TOOLING-04).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-00-01 | M | §2.1, §3.1, §3.3 |
| TOOLING-00-02 | M | §2.2, §3.1, §4 |
| TOOLING-00-03 | S | §2.3, §3.3 |
| TOOLING-00-04 | M | §2.4, §3.2 |
| TOOLING-00-05 | M | §2.5, §4 |
| TOOLING-00-06 | M | §2.6 |

## 9. Alternatives Considered
- **Kotlin-script spikes instead of bash** — rejected: the build recipe's value is the
  exact command sequence; bash is the most direct executable spec, and TOOLING-04
  transliterates it into `SourceBuildStrategy` with `LuaToolExecutionService`.
- **Running Windows checks under Wine** — rejected: Wine's loader behavior does not
  answer SmartScreen/AV questions and adds a false-confidence execution path.
- **Deferring Windows execution to a manual checklist** — the original fallback when no
  Windows host was available; superseded by the Windows 11 KVM/virt-manager VM, which is
  agent-drivable over VNC and makes the execution check part of the spike itself.
- **Live darwin-arm64 LuaJIT run** — no macOS hardware in the harness; paper assessment
  + decision matrix instead (risks doc, Test Case Gaps).
- **Skipping the classpath spike** ("the platform obviously bundles these") — rejected:
  the platform research doc explicitly flags that some classes live in
  `platform/lang-impl` and asks for a compile-check against our actual classpath.

## 10. Open Questions

_None — every spike has a pinned method, threshold, and deliverable; the questions they answer are their §2 subjects, tracked as gaps in [../tooling-risks-and-gaps.md](../tooling-risks-and-gaps.md)._