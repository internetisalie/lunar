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
