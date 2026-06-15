---
id: ROCKS-01-DESIGN
title: "Technical Design"
type: design
parent_id: ROCKS-01
status: "planned"
priority: "high"
folders:
  - "[[features/rocks/01-project-initialization/requirements|requirements]]"
---

# Technical Design: Project Initialization & Setup (ROCKS-01)

## 1. Architecture Overview

### Current State
No project-generation code exists. The plugin's build target is **GoLand**
(`gradle.properties` `platformType = GO`) — a *small IDE*. Per the platform
(`DirectoryProjectGenerator.java` javadoc + the `ModuleBuilder` "IDEA-only" note), small IDEs
use **`com.intellij.platform.DirectoryProjectGenerator<T>`**, *not* `ModuleBuilder`. The prior
draft's `ModuleBuilder` choice is therefore corrected here. File templates are bundled as
plugin resources; `luarocks` (if present) is invoked via `LuaProcessUtil`.

### Target State
A "LuaRocks" generator appears in the New Project wizard. It collects settings via a peer
panel, then a pure `LuaRocksScaffolder` deterministically writes the project from templates
(no dependency on the `luarocks` binary for the `Must` outputs), optionally enriching with
`luarocks init`, and patches the Lua run-config template with `LUA_INIT` when loader setup is
chosen.

```
DirectoryProjectGenerator ─peer──▶ LuaRocksProjectSettings
            │ generateProject (EDT, WriteAction)
            ▼
      LuaRocksScaffolder ──writes──▶ rockspec, src/, [setup.lua], [Makefile], [spec/], .gitignore
            │                         (template bodies = §4)
            └─patches──▶ RunManager Lua run-config template (LUA_INIT)  [if loaderSetup]
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.init.LuaRocksProjectSettings`
- **Responsibility**: The generator settings value object `T`.
  ```kotlin
  enum class RockKind { SINGLE_ROCK, WORKSPACE }
  enum class RockType { LIBRARY, APPLICATION }
  data class LuaRocksProjectSettings(
      var name: String = "",
      var kind: RockKind = RockKind.SINGLE_ROCK,
      var type: RockType = RockType.LIBRARY,
      var luaVersions: String = "5.1,5.2,5.3,5.4",   // for `luarocks init --lua-versions`
      var loaderSetup: Boolean = false,              // src/setup.lua (Application)
      var bustedConfig: Boolean = false,             // spec/
      var makefile: Boolean = false,
      var workspaceName: String = "",                // WORKSPACE only
      var initialRocks: List<String> = emptyList(),  // WORKSPACE only
  )
  ```

### 2.2 `net.internetisalie.lunar.rocks.init.LuaRocksProjectGenerator`
- **Responsibility**: Wizard entry; delegates to the scaffolder.
- **Threading**: `generateProject` is `@RequiresEdt`; file writes go through `WriteAction`.
- **Key API**:
  ```kotlin
  class LuaRocksProjectGenerator : DirectoryProjectGeneratorBase<LuaRocksProjectSettings>() {
      override fun getName(): String = "LuaRocks"
      override fun getLogo(): Icon = LuaIcons.ROCKET
      override fun createPeer(): ProjectGeneratorPeer<LuaRocksProjectSettings> =
          LuaRocksGeneratorPeer()
      override fun validate(baseDirPath: String): ValidationResult   // name non-blank
      override fun generateProject(project: Project, baseDir: VirtualFile,
                                   settings: LuaRocksProjectSettings, module: Module) {
          WriteAction.run<Throwable> { LuaRocksScaffolder.scaffold(project, baseDir, settings) }
      }
  }
  ```

### 2.3 `net.internetisalie.lunar.rocks.init.LuaRocksGeneratorPeer`
- **Responsibility**: Settings UI panel (peer).
- **Key API** (per `ProjectGeneratorPeer<T>`):
  ```kotlin
  class LuaRocksGeneratorPeer : ProjectGeneratorPeer<LuaRocksProjectSettings> {
      override fun getComponent(locationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent
      override fun buildUI(settingsStep: SettingsStep)   // add rows via FormBuilder/SettingsStep
      override fun getSettings(): LuaRocksProjectSettings // read widgets into the data class
      override fun validate(): ValidationInfo?           // null = OK
      override fun isBackgroundJobRunning(): Boolean = false
      override fun addSettingsListener(listener: ProjectGeneratorPeer.SettingsListener) {}
  }
  ```
  Widgets: name field, Kind radio (Single Rock / Workspace), Type radio (Library /
  Application — enabled for Single Rock), Lua-versions checkboxes, three option checkboxes
  (Loader Setup / Busted / Makefile), and Workspace-only fields (name, initial rocks). The
  panel shows/hides the Single-Rock vs Workspace groups based on Kind.

### 2.4 `net.internetisalie.lunar.rocks.init.LuaRocksScaffolder`
- **Responsibility**: All file generation + optional CLI + run-config patching. Pure enough to
  integration-test against a temp `VirtualFile` dir.
- **Threading**: called inside a `WriteAction` (VFS writes); the optional `luarocks init` runs
  via `LuaProcessUtil.capture` (caller may move it off-EDT — see §3.1 note).
- **Key API**:
  ```kotlin
  object LuaRocksScaffolder {
      fun scaffold(project: Project, baseDir: VirtualFile, s: LuaRocksProjectSettings)
  }
  ```

### 2.5 `net.internetisalie.lunar.rocks.init.LuaRocksTemplates`
- **Responsibility**: Produce the exact file bodies (§4) given the settings.
  ```kotlin
  object LuaRocksTemplates {
      fun rockspec(name: String, type: RockType): String
      fun setupLua(): String
      fun mainModule(name: String, type: RockType): String
      fun makefile(name: String): String
      fun bustedSpec(name: String): String
      fun gitignore(): String
      fun workspaceLua(name: String, rocks: List<String>): String
  }
  ```

## 3. Algorithms

### 3.1 `scaffold` (single rock) — ROCKS-01-01/02/03
- **Steps** (all VFS ops via `baseDir.createChildDirectory(this, …)` /
  `createChildData(this, …)` + `VfsUtil.saveText(file, body)`):
  1. Write `<name>-scm-1.rockspec` = `LuaRocksTemplates.rockspec(name, type)`.
  2. Create `src/`; write `src/<name>.lua` (LIBRARY) or `src/main.lua` (APPLICATION) =
     `mainModule(name, type)`.
  3. If `loaderSetup && type == APPLICATION`: write `src/setup.lua` = `setupLua()`.
  4. If `bustedConfig`: create `spec/`; write `spec/<name>_spec.lua` = `bustedSpec(name)`.
  5. If `makefile`: write `Makefile` = `makefile(name)`.
  6. Create empty `lua_modules/` dir (local rock tree).
  7. Write `.gitignore` = `gitignore()`; if a `git` executable is on PATH, optionally
     `GeneralCommandLine("git","init")` in `baseDir` via `LuaProcessUtil.capture` (ROCKS-01-04,
     best-effort; ignored on failure).
  8. If `loaderSetup`: patch the run-config template (§3.3).
  9. **Optional enrichment**: if a `luarocks` binary is configured, run
     `luarocks init --lua-versions "<luaVersions>"` (via `LuaProcessUtil.capture`) to add
     `.luarocks/` + wrappers. Failure is non-fatal (templates already satisfy the `Must`s).
- **Note**: steps 1–8 are deterministic and binary-free → integration-testable; step 9 is the
  only binary-dependent part and is optional. `luarocks init` is run **outside** the
  `WriteAction` (process I/O), then `baseDir.refresh(false, true)` re-syncs VFS.

### 3.2 `scaffold` (workspace) — ROCKS-01 workspace path
- **Steps**: write `workspace.lua` = `workspaceLua(workspaceName, initialRocks)`; create a
  directory per entry in `initialRocks` (no per-rock init — deferred to a workspace-management
  feature); write `.gitignore` (incl. rock-dir exclusions); optional `git init`.

### 3.3 Run-config `LUA_INIT` patching — ROCKS-01-05
- **Input**: project, `baseDir`.
- **Steps**:
  1. `val factory = LuaRunConfigurationType().configurationFactories.first()` (the existing
     `net.internetisalie.lunar.run.LuaRunConfigurationFactory`).
  2. `val tmpl = RunManager.getInstance(project).getConfigurationTemplate(factory)`.
  3. `val cfg = tmpl.configuration as LuaRunConfiguration`.
  4. `cfg.environmentVariables = EnvironmentVariablesData.create(
        mapOf("LUA_INIT" to "@${baseDir.path}/src/setup.lua"), true, null)`.
  - New Lua run configurations created afterward inherit `LUA_INIT`, which makes the Lua
    runtime preload `src/setup.lua` (adjusting `package.path`/`package.cpath`). Verified by
    TC-ROCKS-01-06 (inspect the template's env).
- **Rule**: only applied when `loaderSetup` is true; the `@<file>` form is the Lua convention
  for "run this file at startup" (vs. inline code).

## 4. Template Bodies (exact)

### 4.1 `rockspec(name, type)` — `<name>-scm-1.rockspec`
```lua
rockspec_format = "3.0"
package = "{name}"
version = "scm-1"
source = {
   url = "*** please add a source URL ***",
}
description = {
   summary = "{name}",
   license = "MIT",
}
dependencies = {
   "lua >= 5.1",
}
build = {
   type = "builtin",
   modules = {
      ["{name}"] = "src/{name}.lua",     -- LIBRARY
   },
}
```
For `APPLICATION`, replace the `build` table with:
```lua
build = {
   type = "builtin",
   modules = { ["{name}"] = "src/main.lua" },
   install = { bin = { ["{name}"] = "src/main.lua" } },
}
```

### 4.2 `setupLua()` — `src/setup.lua`
```lua
-- setup.lua: prepend locally-installed rocks to the module search paths.
local version = _VERSION:match("%d+%.%d+")
package.path  = "lua_modules/share/lua/" .. version .. "/?.lua;"
            ..  "lua_modules/share/lua/" .. version .. "/?/init.lua;" .. package.path
package.cpath = "lua_modules/lib/lua/"   .. version .. "/?.so;" .. package.cpath
```

### 4.3 `mainModule(name, type)`
- LIBRARY → `src/<name>.lua`:
  ```lua
  local {name} = {}
  function {name}.hello()
     return "hello from {name}"
  end
  return {name}
  ```
- APPLICATION → `src/main.lua`:
  ```lua
  local function main(...)
     print("hello from {name}")
  end
  main(...)
  ```

### 4.4 `makefile(name)` — `Makefile`
```make
.PHONY: build test install clean
build:
	luarocks make
test:
	busted
install:
	luarocks install --local {name}-scm-1.rockspec
clean:
	rm -rf lua_modules .luarocks
```

### 4.5 `bustedSpec(name)` — `spec/<name>_spec.lua`
```lua
describe("{name}", function()
   it("loads", function()
      assert.is_table(require("{name}"))
   end)
end)
```

### 4.6 `gitignore()` — `.gitignore`
```
/lua_modules/
/.luarocks/
*.src.rock
*.rock
luacov.stats.out
luacov.report.out
```
(Workspace adds a trailing comment line `# add per-rock build outputs as needed`.)

### 4.7 `workspaceLua(name, rocks)` — `workspace.lua`
`rocks` renders as a Lua list of the `initialRocks` names, each double-quoted and
comma-separated. For `name="my-workspace"`, `rocks=["rock1","rock2"]`:
```lua
return {
   workspace = "my-workspace",
   rocks = {"rock1", "rock2"},
}
```
An empty `initialRocks` yields `rocks = {}`.

## 5. Data Flow

### Example: Single-rock Application with all options (TC-ROCKS-01-04)
Peer yields settings `{name="my-app", type=APPLICATION, loaderSetup, bustedConfig, makefile}`.
`scaffold` writes `my-app-scm-1.rockspec`, `src/main.lua`, `src/setup.lua`, `spec/my-app_spec.lua`,
`Makefile`, `lua_modules/`, `.gitignore`; patches the Lua run-config template with
`LUA_INIT=@<base>/src/setup.lua`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| `loaderSetup` chosen for a LIBRARY | `src/setup.lua` is App-oriented; for LIBRARY the checkbox is disabled in the peer (only meaningful for runnable apps). |
| No `luarocks` binary | Steps 1–8 still produce a valid project; step 9 skipped; `.luarocks/` absent (not a `Must`). |
| Name with spaces/invalid chars | Peer `validate()` rejects (rockspec package names must match `[A-Za-z0-9._-]+`). |
| Existing files in baseDir | Generator runs on a fresh/empty project dir (wizard contract); collisions overwrite via `saveText`. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<directoryProjectGenerator
    implementation="net.internetisalie.lunar.rocks.init.LuaRocksProjectGenerator"/>
```
- Icon `LuaIcons.ROCKET` (shared with ROCKS-03/04; backed by `icons/rocket_16.png`).
- Templates: bundle the literal bodies in `LuaRocksTemplates` (string constants) — no external
  resource extraction needed (unlike the ROCKS-03 bridge, these are not executed by Lua).
- Run-config patching reuses `net.internetisalie.lunar.run.LuaRunConfiguration{Type,Factory}`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-01-01 Initialization Wizard | M | §2.2, §2.3, §7 |
| ROCKS-01-02 Rockspec Generation | M | §3.1 step 1, §4.1 |
| ROCKS-01-03 Module Setup Script | M | §3.1 step 3, §4.2 |
| ROCKS-01-04 Git Integration | S | §3.1 step 7, §4.6 |
| ROCKS-01-05 Run Config Patching | M | §3.3 |
| ROCKS-01-06 Lua Version Selection | S | §2.1 `luaVersions`, §3.1 step 9 |

(ROCKS-01-07 removed — `.luacheckrc`/`.stylua.toml` are out of scope per the requirements
Scope; that requirement contradicted the Scope and the init flow and has been deleted.)

## 9. Alternatives Considered

- **`DirectoryProjectGenerator` vs `ModuleBuilder`**: the build target is GoLand (small IDE),
  where `ModuleBuilder` wizards are unavailable; `DirectoryProjectGenerator` is the correct EP.
  (For IDEA, a thin `ModuleBuilder` adapter delegating to `LuaRocksScaffolder` could be added
  later — not needed for the target.)
- **Template generation vs `luarocks init` as the source of truth**: chose template generation
  for the `Must` outputs so they are deterministic and testable without the binary; `luarocks
  init` is optional enrichment.
- **`GeneratorNewProjectWizard`** (newer API): viable and cross-IDE, but `DirectoryProjectGenerator`
  is simpler and sufficient for the single-module case; revisit if multi-step UX is needed.

## 10. Open Questions

_None — feature has cleared the planning bar._
