# Change Log

## [0.18] — MVP milestone & first tagged release

### Runtime & Platform Support (TARGET)
- **Target Selection**: project environment selection with platform + version granularity.
- **Platforms**: explicit targets for **Standard Lua (5.1–5.5)**, **LuaJIT**, **Redis (5/6/7)**,
  **Valkey (7.2/8)**, plus scaffolding for Tarantool, OpenResty, and Pandoc.
- **Dynamic Standard Libraries**: automatic resolution of platform-specific library stubs
  (Standard/Redis/Valkey are stub-backed) from the selected target.
- **Environment-Aware Luacheck**: `--std` follows the active target.

### Legal & Distribution
- **Apache-2.0 license** adopted. `LICENSE`, `NOTICE`, and `THIRD-PARTY.md` (attributing the
  Sylvanaar "Lua for IDEA" plugin, the IntelliJ Platform, EmmyLua, MobDebug/RemDebug, the `lua.l`
  lexer, and the Lua.org standard-library stubs) are bundled at the plugin root in every zip.

### Documentation
- README refreshed (accurate versions, live doc links, full epic list, Lua 5.1–5.5).

### Fixes (0.18.2)
- **LuaRocks Packages crash** (BUG-379): the package-browser debounce `Alarm` was created
  without a parent `Disposable`, throwing on every open of the LuaRocks Packages view. The
  `Alarm` is now parented to a disposable panel.
- **RockspecBridge log noise** (BUG-380): the "no Lua runtime configured" message was logged at
  `warn`, flooding the IDE log for projects without a configured runtime; demoted to `debug`.
- **Release build** (build): `patchPluginXml` now accepts milestone-style CHANGELOG headers, so
  overriding the plugin version to one without a matching CHANGELOG section no longer fails.

### Fixes (0.18.3)
- **Redis sandbox false positive** (REDIS-06): the "not available in the Redis sandbox" inspection
  no longer flags a global name that is shadowed by a local binding in scope — it now performs a
  side-effect-free local-resolution check before warning.
- **Redis command quick-doc over-triggering** (REDIS-06): command documentation now surfaces only
  when the caret is on the command-name string literal, instead of anywhere in the call.

### Language & Editor (0.18.4)
- **Parser error recovery for block constructs** (SYNTAX-18): `do`/`while`/`repeat`/function block
  rules now `pin` after their opening keyword, so an unterminated or half-written block yields a
  partial PSI node scoped to that block instead of letting the error cascade to the end of file.
  Completion, highlighting, and structural editing stay accurate while a block is still being typed.
- **Typed lambda parameters from expected callback types** (TYPE-10): when a lambda is passed to a
  function whose parameter is a callback type (`fun(...)`), its own un-annotated parameters now infer
  the expected types with no manual `---@param`. `redis.register_function('f', function(keys, args)
  … end)` types `keys`/`args` as `string[]` (and `keys[1]` as `string`), and `table.sort(t,
  function(a, b) … end)` types the comparator from the stub signature. A direct `---@param` on the
  lambda still wins. Retires REDIS-05's descoped callback typing (Gap 2.4).

### LuaRocks package browser redesign (ROCKS-16)
- **Plugins-style two-tab browser**: the LuaRocks Packages tool window is rebuilt in the IDE
  Plugins-page idiom — a **Marketplace** tab (debounced search) and an **Installed** tab
  (zero-query list of the project's rocks), both `JB*`-component surfaces, sharing a rich detail
  pane with a `JBHtmlPane` description, a clickable dependency list, a version picker, and inline
  Install / Uninstall / Update / Add-to-rockspec actions.
- **Canonical install target**: browser installs/uninstalls now pass `--tree <project rock tree>`,
  so an installed rock is visible to module resolution, the dependency tree, and the library
  provider — no longer landing in the binary's default global tree.
- **Honest error & empty states**: an unresolved `luarocks` binary or a failed CLI call now shows
  an error card with a **Configure** link to the Toolchain settings, never the misleading "No
  packages found"; the no-selection state is a proper empty-text panel. The zero-query Marketplace
  view optionally shows a "Popular / Trending" list scraped from luarocks.org, degrading silently
  to a neutral prompt on any fetch failure.
- **Fixes**: absorbs BUG-363 (monospaced detail font → standard UI font), BUG-365 (detail-pane
  alignment), BUG-366 (the two LuaRocks tool windows now have unambiguous stripe titles —
  "LuaRocks Packages" vs "LuaRocks Dependencies"), BUG-367 (`(no package selected)` label →
  empty-text panel), and BUG-368 (newline-joined dependencies → a clickable list).

### Fixes (0.18.5)
- **Long-bracket annotator crash mid-typing** (BUG-386): `LuaLongStringAnnotator` and
  `LuaLongCommentAnnotator` raw-indexed token text without bounds checks, throwing
  `StringIndexOutOfBoundsException` when a truncated delimiter (e.g. `[==` or `--[=`) was
  lexed at EOF while typing. Fixed by delegating to the existing bounds-checked helpers
  `getLuaStringDelimiterLength` / `getLuaCommentDelimiterLength`.
- **Reformat forces spaces inside brackets** (BUG-382): reformat always produced `t[ 1 ]`
  because a rule labelled "No spacing inside brackets" mistakenly returned `SINGLE_SPACING`,
  making the *Spaces → Within → Brackets* code-style setting unreachable. Fixed by removing
  the erroneous override and deferring to the `spacingBuilder.withinPair` rule that already
  respects `SPACE_WITHIN_BRACKETS`.
- **Version-conflict engine misses equal-version exclusive bounds** (BUG-383): `>= 2.0` +
  `< 2.0` was not flagged as unsatisfiable because the engine only checked
  `lower.version > upper.version`. Fixed to also flag pairs where the versions are equal but
  at least one bound is exclusive (`>= 2.0 + <= 2.0` remains satisfiable by exactly 2.0).
- **LUA_CPATH hardcodes `?.so` on Windows** (BUG-384): `RockspecRunPathProvider.luaCPath`
  hardcoded `?.so` and read the deprecated `state.languageLevel`. Fixed to use the native
  extension per `SystemInfo` (`.dll` on Windows, `.so` elsewhere) and derive the language
  level from the active target — the same source `LuaRocksLibraryProvider` uses.
- **Scaffolder instantiates a fresh run-configuration type** (BUG-385): `LuaRocksScaffolder`
  constructed a fresh `LuaRunConfigurationType()` instead of the platform-registered
  singleton, so template patching operated on a divergent instance. Fixed to look up the
  singleton via `ConfigurationTypeUtil.findConfigurationType`.
- **Orphaned Lua Workspace file type** (BUG-374): `LuaWorkFileType` and its `plugin.xml`
  registration survived the removal of the workspace concept; deleted the dead class and
  registration so `*.luawork` is no longer claimed by the plugin.
- **lua-language-server missing from kind registry** (BUG-373): the kind was provisionable
  (present in the feed and provision dialog) but absent from `LuaToolKindRegistry.BUILT_IN`,
  so its inventory Kind column showed a raw id and no binding row appeared on the Lua Project
  page. Added as kind #11 with displayName "Lua Language Server".
- **Provision dialog checkboxes show raw kind ids** (BUG-370): tool checkboxes in the
  provision dialog used the raw kind id (e.g. `stylua`) as the checkbox label. Fixed by
  resolving through `LuaToolKindRegistry` so the dialog now shows "StyLua", "Busted",
  "LuaCov", "Lua Language Server", etc.
- **Change Versions dialog leaves root directory editable** (BUG-371): the *Change Versions*
  flow documents that the root directory is fixed, but `prefill()` set the text without
  disabling the field or its browse button. Fixed by calling `rootDirField.isEnabled = false`
  when prefilling.
- **Env status-bar widget shown in non-Lua projects** (BUG-375): the factory's `isAvailable`
  was hardcoded `true`, showing the widget in every project. Now gates on
  `LuaToolchainProjectSettings.environments().isNotEmpty()` — an EDT-safe in-memory check.
- **App-level Provision silently targets wrong project** (BUG-372): with multiple projects
  open, the toolchain inventory's Provision button guessed via `openProjects.firstOrNull()`.
  Now shows a project-chooser popup when multiple are open; disabled with "No open project"
  tooltip when none are open.
- **Publish Rock API key not manageable after rotation** (BUG-376): on a bad/rotated key the
  action reused the stored credential with no recovery. Now detects auth failures (Invalid
  API key / Unauthorized / Forbidden) in `luarocks upload` output, clears the stored key,
  and notifies the user to re-run Publish to enter a new key.
- **Run Test Matrix covers only the first rockspec** (BUG-377): `firstRockspec()` silently
  dropped all but the first discovered rockspec. Now iterates all discovered rockspecs,
  launching one matrix per rockspec (env × rockspec product). The results table gains a
  Rockspec column to distinguish rows across multiple rocks.

### Lua settings restructure (TOOLING-08)
- **Discoverable platform-target control** (BUG-362): the *Lua Project* settings page now has an
  always-visible *Platform target* + *Version* pair of combos. *Auto (from runtime)* follows the
  discovered interpreter; picking a concrete platform (e.g. Redis) pins the target explicitly, and a
  later interpreter re-probe no longer overwrites it. Previously the target could only ever be
  derived from the runtime, so a Redis project whose interpreter probed as Standard was un-pinnable.
- **Common / Advanced bindings split with server-kind eviction**: the *Toolchain Bindings* group now
  shows only the common tools (runtime + LuaRocks + luacheck + StyLua + Busted); the rest move to a
  collapsed *Advanced tools* group. The capability-less `redis-server` / `valkey-server` platform
  kinds are removed from the bindings UI entirely while staying fully resolvable for the Redis
  subsystem.
- **Global default bindings UI**: the app-level *Toolchain* page gains a *Global Default Bindings*
  group — one combo per common kind — that writes through the previously orphaned
  `setGlobalBinding`, so a globally-bound tool applies to any project with no project-level binding.
- **DSL-standardized settings panels** (BUG-369): the app *Lua* page and the LuaRocks project-generator
  dialog are rebuilt on the Kotlin UI DSL, replacing the FormBuilder layouts so the settings tree's
  vertical spacing is uniform.
- **Honest Cancel/Reset on the app settings page**: the app *Lua* configurable now implements the
  full lifecycle (`reset()` / `disposeUIResources()`) and only commits its toggles on *Apply*, so
  *Cancel* truly reverts.
- **Explicit inherit labelling**: the project Luacheck-arguments and LuaRocks server-URL fields render
  the effective app default in their placeholder (`Inherit (app default: …)` / `Inherit (luarocks.org)`).

## [0.17] — Redis & Valkey integration (REDIS epic)

- **Connections & Script Run Configuration** (REDIS-01): RESP client + connection management.
- **LDB Debug Adapter** (REDIS-02): server-side Lua debugging.
- **Valkey Runtime Target** (REDIS-03): Valkey 7.2/8 as first-class targets with `server.*` stubs
  and a "Valkey-only API under Redis target" inspection + quick fix.
- **Language-Engine Integration** (REDIS-04): ambient `redis.*`/`KEYS`/`ARGV` typing & suppression.
- **Redis Functions Workflow** (REDIS-05): `register_function` support and the Functions panel.

## [0.16] — Editor ergonomics & structural editing (EDITOR epic)

- **Smart Typing** (EDITOR-01): auto-close and keyword-pair completion.
- **Spellchecking** (EDITOR-02): comments, strings, and declaration names.
- **TODO / FIXME Indexing** (EDITOR-03) in Lua and LuaCATS comments.
- **Smart Word Selection** (EDITOR-04): construct-aware `Ctrl+W`.
- **Surround With** (EDITOR-05) and **Unwrap / Remove** (EDITOR-06).
- **Move Statement / Element** (EDITOR-07): block-aware structural moves.
- **Smart Enter** (EDITOR-08): complete half-written blocks and calls.

## [0.15] — Unified Lua toolchain management (TOOLING epic)

- **Toolchain Model & Registry** (TOOLING-01): unified discovery + version probing.
- **Resolution, Binding & Environments** (TOOLING-02): project/global precedence + environments.
- **Execution & Environment Injection** (TOOLING-03): one PATH/`LUA_PATH`/`LUA_CPATH` service.
- **Native Provisioning Engine** (TOOLING-04): in-plugin builds, no Python/hererocks dependency.
- **Consumer Migration & Legacy Removal** (TOOLING-05): clean-break cutover.
- **Settings UI Consolidation** (TOOLING-06): a single Lua settings tree.
- **Health Monitoring & Diagnostics** (TOOLING-07).

## [0.14] — Schema-driven data files (SCHEMA epic)

- **Lua JSON-Schema Engine** (SCHEMA-01).
- **Schema Providers**: rockspec (SCHEMA-02), `.luacheckrc` (SCHEMA-03), busted config (SCHEMA-04).

## [0.13] — LuaRocks multi-rock workspaces & environment (ROCKS, reopened)

- **Multi-Rock Workspace Discovery** (ROCKS-09): index-backed, cached rockspec forest.
- **Rockspec Module Resolution** (ROCKS-05): `LUA_PATH`/`LUA_CPATH` from derived roots.
- **Project LuaRocks Environment** (ROCKS-06): per-server resolver, API-key store, server override.
- **Workspace Build Orchestration** (ROCKS-10): dependency-ordered, topo-sorted builds.
- **Makefile Task Integration** (ROCKS-11) and **Project-View Roots & Marking** (ROCKS-12).

## [0.12] — Internal & maintenance (MAINT epic)

- Test-coverage features (MAINT-10–18), Kotlin-native token holders (MAINT-19), headless
  parser/lexer generation (MAINT-20), and the DBGp transport rewrite (MAINT-22) — largely
  user-invisible.
- **Fix**: `@return` comma parsing — parse error on comma-separated types in `@return` (BUG-134).

## [0.11] — Backlog & differentiators

- **Parameter Name Hints** (COMP-05).
- **Test Runner Integration** (RUN-05).
- **StyLua Compatibility** (FORMAT-07).
- **Flow-Sensitive Analysis** (TYPE-08).
- **Full-Text Documentation Search** (DOC-06-04).
- **Lua 5.5 Support** (SYNTAX-09): `global` declarations and language-level model.

## [0.10] — Tool inventory & LuaRocks (TOOL + ROCKS epics)

- **Tool Registry & Discovery** (TOOL-01), **Project Binding & Env** (TOOL-02), **UI & Health
  Monitoring** (TOOL-03) for external Lua binaries (`luarocks`, `luacheck`, `lua-format`).
- **LuaRocks**: Task Execution & Run Configs (ROCKS-04), Dependency Resolution (ROCKS-03), Package
  Browser (ROCKS-02), Project Initialization (ROCKS-01), Publishing (ROCKS-08).

## [0.9] — Quick wins & differentiators

- **Lua Interpreter SDK** (RUN-01), **Run Configurations** (RUN-02) + **Validation** (RUN-04).
- **Interactive Console / REPL** (RUN-03): multi-line trial-parse + history.
- **Documentation Indexing** (DOC-06): stub index + type map.
- **Method Separators** (SYNTAX-05), lexer optimization (SYNTAX-15), remaining inlay hints.

## [0.8] — Refactoring & intentions

- **String Quote Conversion** (INTENT-01): `'…'` ↔ `"…"` ↔ `[[…]]`.
- **Invert `if`** (INTENT-02): negate condition + swap `then`/`else`.
- **Variable Name Suggestion** (INTENT-03): `getUser()` → `user`.
- **Rename Names Validator** (REFACT-05) and **Create-from-Usage** intentions (REFACT-06).

## [0.7] — Formatting

- **Blank-Line Management** (FORMAT-03), **Expression Wrapping** (FORMAT-04), **Alignment**
  (FORMAT-05, opt-in), **Comment Formatting** (FORMAT-06, opt-in).

## [0.6] — Completion polish

- **Cross-File Completion** (COMP-03): recursive/transitive resolution with cycle guard.
- **Postfix Templates** (COMP-06): 11 templates. **Live Templates** (COMP-07): 16 templates.
- **Block Auto-Complete** (COMP-08): balanced `end`/`until`/`}` insertion.

## [0.5] — Type-system hardening

- **External-API Stubs** (TYPE-07): cross-file `require`→stub resolution + type injection.
- **Union Distribution Hardening** (TYPE-09): canonicalization limits, memoization, member-specific
  diagnostics.

## [0.4] — Inspections

- **Global-creation inspection** (INSP-05).
- **Variable-shadowing inspection** (INSP-06).
- **Deprecated-usage inspection** (INSP-08).
- **Unused local / parameter inspection** (INSP-02).
- **Unreachable-code inspection** (INSP-04).
- **Suspicious-concatenation inspection** (INSP-07).
- **Type-mismatch inspection** (INSP-03).
- **Language-level compliance inspection** (INSP-09).

## [0.3] — Navigation dependents & refactoring

- **Read/Write Access Detector** (NAV-10).
- **Introduce Variable** (REFACT-02) and **Safe Delete** (REFACT-03).

## [0.2] — Navigation & references core

- **Find Usages** (NAV-02), **Go to Class/File/Symbol** (NAV-03), **Return Highlighter** (NAV-09).

## [0.1] — Type-system intelligence

- **Undeclared-Variable Inspection** (INSP-01), **Auto-Import Completion** (COMP-03-03).
- **Method-Chaining Inlay Hints** (SYNTAX-07-07, +large-file threshold) and **Inferred-Type
  Highlighting** (SYNTAX-17).
- **Method-Override Markers** (NAV-05) and **Type-Hierarchy View** (NAV-06).

## [0.0] — Type engine foundation

- **Union Types** (TYPE-09 P0–P4): infrastructure, flattening, compatibility limits + memoization,
  error reporting, verification & perf.
- **Type-Inferred Completion** (COMP-04): `self` / `__index` resolution.
- **Class/Table Definitions** (TYPE-02): implicit fields discovered from assignments.

### Initial Work

The base plugin, established before the first versioned milestone:

- **Lexer** and **parser / PSI** for the Lua grammar.
- **LuaCATS / LuaDoc** annotation support.
- **Syntax highlighting**.
- **Initial type engine**.
