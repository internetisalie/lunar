---
id: ROCKS-11-DESIGN
title: "Technical Design"
type: design
parent_id: ROCKS-11
folders:
  - "[[features/rocks/11-makefile-tasks/requirements|requirements]]"
---

# Technical Design: ROCKS-11 — Makefile Task Integration

## 1. Architecture Overview

### Current State
`LuaRocksTemplates.makefile(name)` (`rocks/init/LuaRocksTemplates.kt:64-74`) emits a thin
Makefile with only `build` (`luarocks make`), `test` (`busted`), `install`
(`luarocks install --local <name>-scm-1.rockspec`), and `clean`. It LACKS `lint`, `format`, and
`coverage` targets. The scaffolder writes it conditionally on the opt-in flag at
`rocks/init/LuaRocksScaffolder.kt:51-53`:

```kotlin
// 5. Makefile
if (s.makefile) {
    writeText(baseDir, "Makefile", LuaRocksTemplates.makefile(name))
}
```

The opt-in flag is `LuaRocksProjectSettings.makefile: Boolean = false`
(`rocks/init/LuaRocksProjectSettings.kt:14`).

### Prior Art in This Repo
Searched `rocks/init/`, `tool/`, `analysis/luacheck/`, `coverage/`, `run/test/`, and `plugin.xml`:
- **`LuaRocksTemplates.makefile`** (`LuaRocksTemplates.kt:64`) — the existing thin template. This
  design **extends** it (replaces the function body); it does not add a second Makefile generator.
- **Tool CLIs already integrated** (the recipes invoke the same binaries, NOT a new integration):
  `luacheck` via `analysis/luacheck/LuaCheckCommandLine.kt:22` (`GeneralCommandLine(executablePath)`
  with `--codes --ranges`); `stylua` registered as `LuaToolType.STYLUA`
  (`tool/LuaToolDescriptor.kt:49`); `luacov` via `coverage/LuaCoverageProgramRunner.kt:29`
  (`getEffectiveTool(project, LuaToolType.LUACOV)`, produces `luacov.stats.out`); `busted` test
  runs under `run/test/` (`run/test/LuaTestRunConfiguration`, referenced at
  `coverage/LuaCoverageProgramRunner.kt:15`). This feature does **not** touch any of these; it only
  references the same binary names in shell recipes.
- **Optional-dependency precedent** — `plugin.xml:25-28` declares
  `<depends optional="true" config-file="lunar-terminal.xml">org.jetbrains.plugins.terminal</depends>`,
  with `META-INF/lunar-terminal.xml` loaded only when the terminal plugin is present. This design
  **mirrors** that pattern for `com.jetbrains.lang.makefile` + `lunar-makefile.xml`. No existing
  Makefile dependency or config-file exists (grepped `plugin.xml`, `META-INF/*.xml`).

No custom Makefile PSI/parser/line-marker exists in the repo and none is added (out of scope).

### Target State
1. `LuaRocksTemplates.makefile(name)` returns an enriched, portable Makefile string with seven
   canonical targets and a correct `.PHONY` line (§2.1, §3.1).
2. `plugin.xml` gains one optional `<depends>` line; `META-INF/lunar-makefile.xml` is added
   (§7). When the Makefile plugin is installed, GoLand provides target gutter-run markers and a
   Makefile run-config for the scaffolded file automatically; when absent, nothing breaks.

Component sketch: scaffolding flow is unchanged (`LuaRocksScaffolder.scaffold` →
`writeText("Makefile", LuaRocksTemplates.makefile(name))`); only the template content changes,
plus a declarative `plugin.xml` registration.

## 2. Core Components

### 2.1 net.internetisalie.lunar.rocks.init.LuaRocksTemplates (existing object — modify)
- **Responsibility**: produce the scaffolded Makefile text. Only `makefile(name)` changes.
- **Threading**: none — pure string building, called from within the existing scaffolder
  `WriteAction` (`LuaRocksScaffolder.kt:20`).
- **Collaborators**: called by `LuaRocksScaffolder.scaffoldSingleRock` (`LuaRocksScaffolder.kt:52`).
  No new collaborators; recipes reference CLI tool names as plain shell text.
- **Key API** (signature unchanged):
  ```kotlin
  fun makefile(name: String): String
  ```
  The returned string is the exact template in §3.1.

No new classes are created. No new services, indexes, or PSI types.

## 3. Algorithms

### 3.1 Enriched Makefile template (exact text)
`makefile(name)` returns exactly the following (with `$name` interpolated; recipe lines are
**hard-tab** indented — shown here as a literal tab). This is the complete, copy-ready body:

```
.PHONY: build test lint format coverage rocks clean

build:
	luarocks make

test:
	busted

lint:
	luacheck src spec

format:
	stylua src spec

coverage:
	busted --coverage
	luacov

rocks:
	luarocks install --local $name-scm-1.rockspec

clean:
	rm -rf lua_modules .luarocks luacov.stats.out luacov.report.out
```

Kotlin (raw triple-quoted string, `.trimStart()`, tabs preserved exactly as in the current
function at `LuaRocksTemplates.kt:64`):

```kotlin
fun makefile(name: String): String = """
.PHONY: build test lint format coverage rocks clean

build:
	luarocks make

test:
	busted

lint:
	luacheck src spec

format:
	stylua src spec

coverage:
	busted --coverage
	luacov

rocks:
	luarocks install --local $name-scm-1.rockspec

clean:
	rm -rf lua_modules .luarocks luacov.stats.out luacov.report.out
""".trimStart()
```

- **Input → Output**: `name: String` → `String` (Makefile contents).
- **Rules / edge handling**:
  - `.PHONY` lists every target, in declaration order — verifiable by exact string match
    (TC #3).
  - The `rocks` target replaces the old `install` target name (canonical naming per the
    requirement), and interpolates `$name` exactly as the old `install` recipe did
    (`LuaRocksTemplates.kt:71`): `luarocks install --local $name-scm-1.rockspec`.
  - `coverage` runs `busted --coverage` (busted's built-in luacov hook produces
    `luacov.stats.out`, consistent with `coverage/LuaCoverageProgramRunner.kt:50` and the
    `.gitignore` entries at `LuaRocksTemplates.kt:90`) then `luacov` (generates
    `luacov.report.out`). Both files are removed by `clean`.
  - `lint`/`format` target `src spec` (the two directories the scaffolder creates —
    `LuaRocksScaffolder.kt:36,47`); if `spec` is absent the tools simply skip it (non-fatal for
    `luacheck`/`stylua` on a missing path is acceptable for a scaffold default — see §6).
  - Recipe tabs are hard tabs; the raw string literal must contain literal `\t`, never spaces
    (Make rejects space-indented recipes with "missing separator").
- **Complexity**: O(1) string build.

### 3.2 Tool-binary reference strategy (why bare names, not TOOL-registry absolute paths)
The Makefile is a **portable CI/CLI artifact** committed to the repo and run on machines that do
not have Lunar/GoLand. Therefore recipes reference the **bare binary names** (`luacheck`, `stylua`,
`busted`, `luacov`, `luarocks`) resolved from `PATH` at make-run time — the same convention the
existing thin template already uses for `luarocks`/`busted` (`LuaRocksTemplates.kt:67-73`).
Hard-coding absolute paths resolved from `LuaToolManager.getEffectiveTool(...)`
(`tool/LuaToolManager.kt:163`) is explicitly rejected: those paths are developer-machine-specific
and IDE-bound, breaking CI portability. The TOOL registry remains the source of truth for the
**in-editor** UX (annotator/formatter/coverage), which this feature does not touch (§Out of Scope).

## 4. External Data & Parsing
None. This feature consumes no CLI output, file content, or network response. It only **emits** a
static text file (the Makefile). The Makefile plugin (when installed) parses the file, but that
parsing is the plugin's responsibility, not Lunar's. Section omitted as N/A.

## 5. Data Flow

### Example 1: Scaffold with Makefile option on
1. User runs the LuaRocks project generator (ROCKS-01) with
   `LuaRocksProjectSettings(name="my-lib", makefile=true)`.
2. `LuaRocksScaffolder.scaffoldSingleRock` reaches `LuaRocksScaffolder.kt:51-53`, calls
   `LuaRocksTemplates.makefile("my-lib")`.
3. The enriched §3.1 string is written to `Makefile` via `writeText` (`LuaRocksScaffolder.kt:89`).
4. If the Makefile plugin is installed, GoLand indexes the file and shows gutter run markers per
   target; otherwise the file is a plain text/Make file the user runs from the terminal.

### Example 2: One-click target run (Makefile plugin present)
1. User opens the generated `Makefile` in GoLand (with `com.jetbrains.lang.makefile` installed).
2. The plugin renders run gutter icons next to `lint:`/`format:`/`coverage:` etc.
3. User clicks the `lint` gutter icon → the plugin runs `make lint` in a run console → `luacheck`
   executes from `PATH`. Lunar contributes nothing to this flow beyond having scaffolded the file.

## 6. Edge Cases
- **`spec/` not scaffolded** (`LuaRocksProjectSettings.bustedConfig == false`): `lint`/`format`/
  `test`/`coverage` reference `spec`, which may not exist. `luacheck`/`stylua` warn or skip a
  missing path (non-fatal default); `busted` exits non-zero if there are no specs. This is
  acceptable for a scaffold template — the user adds specs as the project grows. Not changed by
  this feature.
- **A tool not on `PATH`**: `make lint` fails with `luacheck: command not found` (standard shell
  behavior). This is the documented CLI/CI failure mode; the in-editor UX is unaffected because it
  resolves tools via the TOOL registry, not the Makefile.
- **Windows / `rm -rf`**: the existing template already uses `rm -rf` (`LuaRocksTemplates.kt:73`);
  this feature preserves that behavior unchanged (out of scope to fix cross-platform `clean`).
- **Makefile plugin absent**: `lunar-makefile.xml` is silently skipped by the platform's optional
  `<depends>` mechanism (same as the terminal precedent). No markers, no errors.

## 7. Integration Points

`plugin.xml` — add one line next to the existing optional terminal dependency (`plugin.xml:25-28`):

```xml
<!-- ROCKS-11: optional dependency on the marketplace JetBrains "Makefile Language" plugin so the
     scaffolded Makefile's targets are one-click runnable when present. Soft dependency: Lunar
     loads normally when the plugin is absent. pluginId UNVERIFIED until DR ROCKS-11-00-01. -->
<depends optional="true" config-file="lunar-makefile.xml">com.jetbrains.lang.makefile</depends>
```

`src/main/resources/META-INF/lunar-makefile.xml` — new file, mirroring `lunar-terminal.xml`:

```xml
<!-- ROCKS-11: optional config-file, loaded only when com.jetbrains.lang.makefile is present
     (see <depends optional=.../> in plugin.xml). Per DR ROCKS-11-00-01 this contributes NOTHING
     today: target gutter-run markers and the Makefile run-config are provided by the Makefile
     plugin itself for the scaffolded file. This file exists solely to satisfy the optional
     config-file contract and as a hook for any future Makefile-plugin extension confirmed by the
     spike. -->
<idea-plugin>
</idea-plugin>
```

Rationale for the empty config-file: the requirement is graceful one-click runnability, which the
Makefile plugin delivers natively on any `Makefile` it indexes — Lunar needs to contribute no
extension. The `config-file` is still required by the optional-`<depends>` syntax. If
DR ROCKS-11-00-01 surfaces a useful Makefile-plugin extension point (e.g. a target/run-config
contributor), it would be added here; until verified, it stays empty. **No** custom Lunar EP is
registered (would duplicate the plugin — out of scope).

No new `<extensions>`, services, indexes, or settings keys are introduced by Lunar.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-11-01 | M | §2.1, §3.1 |
| ROCKS-11-02 | M | §3.1 (lint/format/coverage recipes) |
| ROCKS-11-03 | M | §3.1 (`.PHONY` line) |
| ROCKS-11-04 | S | §7 (plugin.xml `<depends>` + lunar-makefile.xml), gated on DR ROCKS-11-00-01 |
| ROCKS-11-05 | C | §6 (Makefile-plugin-absent edge case), §7 (rationale) |

## 9. Alternatives Considered
- **Hard-code TOOL-registry absolute paths in recipes** — rejected (§3.2): breaks CI portability;
  the Makefile is committed and run off-IDE.
- **Build a custom Lunar Makefile target run-config / line-marker** — rejected (Out of Scope):
  duplicates the JetBrains Makefile Language plugin; high cost, no added value over the marketplace
  plugin.
- **Bundle the Makefile plugin** — rejected: it is marketplace-only and not bundled in GoLand
  2026.1.3; bundling third-party plugins is not in scope and may have licensing constraints.
- **Add `install` alias alongside `rocks`** — rejected: keep `.PHONY` minimal and canonical; the
  requirement specifies `rocks` as the canonical target name.

## 10. Open Questions

_None — feature has cleared the planning bar._

### 10.1 De-risking note (not an open question)

The Makefile Language plugin's exact `pluginId`, run-config/target API, and optional-config-file
behavior are a tracked **de-risking task** (ROCKS-11-00-01 in
[risks-and-gaps.md](risks-and-gaps.md)), **not** an open design decision: the Must-scope template
work (§2.1/§3.1/§3.2) is fully specified and independent of the spike; only the Should-scope
`<depends>` line (ROCKS-11-04) is gated on it. See §9 (Alternatives) and the risks doc for the
full rationale.
