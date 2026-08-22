# Change Log

## [0.21] — On-demand definition libraries, and the completion fixes needed to make them work

- **The LuaRocks Packages browser shows package details again** (BUG-449): the Marketplace tab's
  detail half had been blank since the two-tab browser shipped — both tabs were handed the same
  detail pane, and a Swing component has one parent, so the second tab silently took it. Each
  tab now has its own pane, which also makes the "No package selected" empty state visible for
  the first time.
- **Add to Watches works on debugger variables** (BUG-447): the action was offered on every
  variable and silently did nothing, because Lunar never supplied the expression the platform
  needs; watches now resolve nested members too, as `cfg["name"]` and `items[1]`
  ([af4e1bf9](https://github.com/internetisalie/lunar/commit/af4e1bf9)).
- **Jump to Source on a debugger variable no longer risks an exception or a stale result**
  (BUG-414): the lookup walked the PSI tree from a background thread without taking a read lock,
  so a concurrent edit could make it fail
  ([45fd5693](https://github.com/internetisalie/lunar/commit/45fd5693)).
- **Redis connections can be given an ephemeral server, and the settings page stops erasing one**
  (BUG-381): a Server choice offers a Docker image or a local `redis-server`/`valkey-server` binary,
  and editing any connection no longer silently rewrites every connection back to remote
  ([f0a9b6a7](https://github.com/internetisalie/lunar/commit/f0a9b6a7)).
- **Docker-provisioned Redis servers now work at all, and stop leaking containers** (BUG-446): the
  launcher read the container id from a stream the platform was already draining, and neither
  provisioning kind waited for the server to accept commands
  ([8e7c6e28](https://github.com/internetisalie/lunar/commit/8e7c6e28)).
- **Completing a `---@class`'s members no longer parses the file that declares it** (BUG-438):
  answered from the index instead, taking a 3 600-member class from 323 ms to inside the 100 ms
  budget; 73 of 75 shipped library files are no longer parsed at all
  ([dbd94bb4](https://github.com/internetisalie/lunar/commit/dbd94bb4)).
- **A reloaded toolchain no longer leaves Lua runs with a stale `PATH`** (BUG-444): opening a project,
  switching a branch or importing settings replaces the toolchain without announcing it, and the
  cached PATH prepends are now retired with it
  ([f2f4f965](https://github.com/internetisalie/lunar/commit/f2f4f965)).
- **Calling a function with fewer arguments than it names is no longer an error** (BUG-419): Lua fills
  the rest with `nil`, so arity is checked only against a signature somebody wrote — removing 629 of
  the 714 remaining type-inspection warnings across four real projects
  ([c4c958ce](https://github.com/internetisalie/lunar/commit/c4c958ce)).
- **`x and f()` / `x or default` no longer report their unused branch as a type error** (BUG-428,
  fixed by BUG-441): 89 of 100 remaining assignability and return-type warnings across four projects
  were this ([d6ce62e0](https://github.com/internetisalie/lunar/commit/d6ce62e0)).
- **A value the engine cannot fully account for is no longer reported as an error** (BUG-441): an
  unknown such as `local d = wx.thing` now widens what `d` may hold, and a later conflict reads as a
  hypothesis rather than a mistake in your code
  ([d6ce62e0](https://github.com/internetisalie/lunar/commit/d6ce62e0)).
- **Declarations in `.rockspec`, `.luacheckrc` and `.busted` files are indexed** (BUG-436): five
  indexes matched `.lua` rather than the registered file types, so anything declared in the other
  three was absent rather than stale
  ([48eabe7d](https://github.com/internetisalie/lunar/commit/48eabe7d)).
- **Quick Documentation works for a `---@field` member** (BUG-440): Ctrl+Q on a field — how most
  definition libraries declare their constants — returned "No documentation found"; it now shows the
  declared type and description ([e9f48be0](https://github.com/internetisalie/lunar/commit/e9f48be0)).
- **Indexing a large Lua file does less work** (BUG-437): the receiver-member index read each file
  seven times to collect five kinds of declaration and now reads it once, saving ~40 ms of a 67 ms
  per-file cost with a byte-identical index
  ([bdec6130](https://github.com/internetisalie/lunar/commit/bdec6130)).
- **A `type(x) == "table"` guard no longer hides the value's members** (BUG-435): narrowing a value to
  `table` removed every member it had, making completion worse inside the guard than outside it
  ([a715abf1](https://github.com/internetisalie/lunar/commit/a715abf1)).
- **A nested table's members complete** (BUG-430): `Config.db.` offered nothing at all, because
  members written through a two-segment receiver were recorded under no receiver
  ([7f3533ef](https://github.com/internetisalie/lunar/commit/7f3533ef)).
- **A global's members declared in a sibling file are offered** (BUG-439): `love.` offered its 40
  direct members and none of its 19 submodules, each assigned in a file of its own; every declaring
  file is now read ([dc712238](https://github.com/internetisalie/lunar/commit/dc712238)).
- **Member completion answered from an index** (COMP-09): a global's members are offered without
  building the declaring file's type graph first, taking the first completion against a large
  definition library from ~491 ms to ~15 ms ([d4179561](https://github.com/internetisalie/lunar/commit/d4179561)).
- **`---@field` members are offered** (COMP-09): a field declared on a `---@class` but never assigned
  now completes — including the ten Redis and Valkey script constants such as `redis.LOG_WARNING` and
  `redis.REPL_ALL` ([d4179561](https://github.com/internetisalie/lunar/commit/d4179561)).
- **A grandchild is no longer offered as a member** (BUG-430): `Shapes.nested.deep = 1` stops
  offering `deep` on `Shapes.`, where it does not exist ([d4179561](https://github.com/internetisalie/lunar/commit/d4179561)).
- **A `---@class` can declare an operator metamethod** (COMP-09, closing BUG-426's known limitation):
  a `---@field __add fun(a: Vec, b: Vec): Vec` makes `a + b` legal on its instances as `setmetatable`
  already did, inherited metamethods included
  ([4a614d25](https://github.com/internetisalie/lunar/commit/4a614d25)).
- **`---@class` members no longer cost a project-wide scan** (COMP-09): materializing a class read
  every global-declaration key to find one receiver's methods — 4 145 keys for 50 members on the
  measured fixture. It is now a receiver-keyed lookup whose work does not grow when unrelated code is
  indexed, with the offered members unchanged ([b346f6c1](https://github.com/internetisalie/lunar/commit/b346f6c1)).
- **Dot/colon member-name collisions resolve to the first declaration** (COMP-09): where a receiver
  declares the same member name both as `function R.m` and as `function R:m`, the type shown for that
  member — and with it quick documentation, signature help and parameter info — is now always the
  declaration that comes first in file order. It previously varied: the winner was decided by index
  traversal state rather than by the source, so two receivers written identically could be answered
  differently ([b346f6c1](https://github.com/internetisalie/lunar/commit/b346f6c1)).
- **Library snapshot invalidation** (TYPE-11): library types depend on the dependency generation —
  library roots and language target — instead of every keystroke; 334 ms → ~11 ms per keystroke with
  a 123 KiB definition library ([8d536e87](https://github.com/internetisalie/lunar/commit/8d536e87)).
- **Lazy type reference escapes the recording frame** (BUG-434): a type name read before the library's
  graph was built hid the file it resolved into, leaving stale types ([e7f0ba4d](https://github.com/internetisalie/lunar/commit/e7f0ba4d)).
- **`resolveType` logs an IDE error during indexing** (BUG-432): type resolution degrades quietly
  while the indexes build ([b6cc37d3](https://github.com/internetisalie/lunar/commit/b6cc37d3)).
- **Global functions declared in another file** (BUG-427): `function count(n) end` at file scope now
  resolves across files with its annotations, and a project's own global wins over a bundled stub
  ([a40a3e19](https://github.com/internetisalie/lunar/commit/a40a3e19)).
- **Cross-file `---@param` checking** (BUG-425): a parameter type declared in one file now constrains
  calls in every other ([426ac162](https://github.com/internetisalie/lunar/commit/426ac162)).
- **`setmetatable` and operator metamethods** (BUG-426, BUG-424): a named metatable no longer infers
  `Undefined`, and `__add`/`__mul`/`__pow`/`__concat`/`__len` are modelled ([ef48c172](https://github.com/internetisalie/lunar/commit/ef48c172)).
- **String↔number coercion in operators** (BUG-423): `"10" + 5` is no longer a type error ([aa51396d](https://github.com/internetisalie/lunar/commit/aa51396d)).
- **LuaCATS extraction unification** (MAINT-34, BUG-402): annotations are read once through one
  extractor instead of three, and a keyed `---@field` renders its key rather than "Unknown"
  ([149a5144](https://github.com/internetisalie/lunar/commit/149a5144)).
- **Free globals typed for the whole engine** (BUG-397, closes BUG-359): free-global typing reaches
  inference, not just completion ([f08d7ca3](https://github.com/internetisalie/lunar/commit/f08d7ca3)).
- **Un-pinning a platform target** (BUG-404): returning a pinned target to Auto reflows to the runtime
  ([8f8fd0e5](https://github.com/internetisalie/lunar/commit/8f8fd0e5)).
- **LDoc `@param` descriptions** (BUG-406): an untyped LDoc `@param` is no longer read as a LuaCATS
  type ([7b3585c8](https://github.com/internetisalie/lunar/commit/7b3585c8)).
- **Documented declarations in Search Everywhere** (BUG-408): a path separator in the description
  record no longer drops the declaration from the index ([b3c9b99a](https://github.com/internetisalie/lunar/commit/b3c9b99a)).
- **LDoc prose in doc comments** (BUG-393): valid LDoc annotations no longer report syntax errors
  ([d23e0743](https://github.com/internetisalie/lunar/commit/d23e0743)).
- **On-demand LuaLS / LuaCATS definition libraries** (TARGET-08): a catalog, per-project enable list,
  fetch-on-demand and a settings page ([48aa0aaf](https://github.com/internetisalie/lunar/commit/48aa0aaf)).
- **Standard-library completion** (BUG-394): library globals are offered at a bare-identifier caret
  ([711672db](https://github.com/internetisalie/lunar/commit/711672db)).
- **Members of globals from other files** (BUG-395, BUG-398, BUG-399): members complete for globals,
  `@class` names declared apart from their local, and `@class` declarations in library files
  ([307d6e54](https://github.com/internetisalie/lunar/commit/307d6e54)).
- **Definition-libraries settings page off the EDT** (BUG-396): cache-state reads no longer stall the
  UI thread ([bd60e129](https://github.com/internetisalie/lunar/commit/bd60e129)).

## [0.20] — Terminology & settings-label polish

- **Corpus sweep regression ratchet** (MAINT-33): an opt-in `-PwithCorpus` sweep over pinned
  checkouts of luacheck, luarocks and ZeroBrane Studio — 363 files — gating parse errors, `require`
  resolution and inspection counts against committed baselines ([1bb8177c](https://github.com/internetisalie/lunar/commit/1bb8177c)).
- **Long strings opening on a blank line** (BUG-392): `[[` followed by two or more newlines lexed as
  three tokens instead of one, breaking parsing for any file containing one ([34d553b0](https://github.com/internetisalie/lunar/commit/34d553b0)).
- **Terminology unification** (BUG-378): interpreter → runtime across the UI ([7673fe25](https://github.com/internetisalie/lunar/commit/7673fe25)).
- **Clearer toolchain binding labels** (BUG-387): "No default" / "nothing bound" replace ambiguous
  empty states ([b36bbc3c](https://github.com/internetisalie/lunar/commit/b36bbc3c)).

## [0.19] — LuaRocks browser, settings restructure, bug sweep & MVP-quality wave

- **Debugger & test runner** (MAINT-24): byte-accurate DBGp framing, crash-proof payload parsing, a
  thread-safe breakpoint model, 1-based line display, a busted rerun filter and Run to Cursor ([5820e870](https://github.com/internetisalie/lunar/commit/5820e870)).
- **Type engine** (MAINT-25): removes a cross-file type leak and speeds up LuaCATS comment scanning ([e08b662e](https://github.com/internetisalie/lunar/commit/e08b662e)).
- **Luacheck** (MAINT-26): diagnostics index the live editor buffer via stdin, so ranges stay correct
  while typing ([b8beef8a](https://github.com/internetisalie/lunar/commit/b8beef8a)).
- **LuaCATS docs & lexer** (MAINT-27): lexer containment fixes for `\r\n` and Unicode identifiers, plus
  annotator dead-branch cleanup ([7fdef286](https://github.com/internetisalie/lunar/commit/7fdef286)).
- **Completion** (MAINT-28): restores silently-disabled cross-file completion and guards the
  enter-between-blocks case ([7cd472be](https://github.com/internetisalie/lunar/commit/7cd472be)).
- **Control-flow & inspections** (MAINT-29): safe integer-division and make-local quick fixes, and
  `__concat` is respected ([f532493a](https://github.com/internetisalie/lunar/commit/f532493a)).
- **Indexing & resolution** (MAINT-30): faster reference resolution and type snapshots ([0519630b](https://github.com/internetisalie/lunar/commit/0519630b)).
- **Process execution** (MAINT-32): fixes an IDE freeze caused by launching a subprocess under a lock ([8279d23d](https://github.com/internetisalie/lunar/commit/8279d23d)).
- **Dead-code sweep** (MAINT-31): ~940 lines of dead declarations removed ([f16a9c01](https://github.com/internetisalie/lunar/commit/f16a9c01)).
- **LuaRocks package browser redesign** (ROCKS-16): a Plugins-style two-tab browser, a canonical
  `--tree <project rock tree>` install target, and honest error and empty states ([5cc0c365](https://github.com/internetisalie/lunar/commit/5cc0c365)).
- **Lua settings restructure** (TOOLING-08, BUG-362, BUG-369): a discoverable platform-target control,
  a Common/Advanced bindings split with server-kind eviction, global default bindings, DSL-standardized
  panels and honest Cancel/Reset ([31d7037d](https://github.com/internetisalie/lunar/commit/31d7037d)).
- **Long-bracket annotator crash mid-typing** (BUG-386, BUG-382, BUG-383, BUG-384, BUG-385): bounds
  checks on truncated delimiters, reformat no longer forces `t[ 1 ]`, equal-version exclusive bounds
  are honoured, `LUA_CPATH` stops hardcoding `?.so` on Windows, and the scaffolder reuses the
  registered run-configuration type ([113b9ef4](https://github.com/internetisalie/lunar/commit/113b9ef4)).
- **Orphaned Lua Workspace file type** (BUG-374): dead `LuaWorkFileType` and its registration removed ([716ec91d](https://github.com/internetisalie/lunar/commit/716ec91d)).
- **lua-language-server missing from the kind registry** (BUG-373): the kind is provisionable again ([34d2daab](https://github.com/internetisalie/lunar/commit/34d2daab)).
- **Provision dialog shows raw kind ids** (BUG-370): checkboxes resolve display names ([7e8b6734](https://github.com/internetisalie/lunar/commit/7e8b6734)).
- **Change Versions dialog leaves root directory editable** (BUG-371): the field is disabled ([117eaffe](https://github.com/internetisalie/lunar/commit/117eaffe)).
- **Env status-bar widget in non-Lua projects** (BUG-375): gated on the project having a Lua env ([6206ddf3](https://github.com/internetisalie/lunar/commit/6206ddf3)).
- **App-level Provision targets the wrong project** (BUG-372): the active project is resolved correctly ([88084136](https://github.com/internetisalie/lunar/commit/88084136)).
- **Publish Rock API key unmanageable after rotation** (BUG-376): a bad key is cleared and re-prompted ([f05e51de](https://github.com/internetisalie/lunar/commit/f05e51de)).
- **Run Test Matrix covers only the first rockspec** (BUG-377): all discovered rockspecs run ([91b754fb](https://github.com/internetisalie/lunar/commit/91b754fb)).
- **`global` lexed as a hard keyword pre-5.5** (BUG-361): it lexes contextually — identifier before
  5.5, declaration from 5.5 ([0566cfbc](https://github.com/internetisalie/lunar/commit/0566cfbc)).

## [0.18] — MVP milestone & first tagged release

- **Runtime & platform support** (TARGET): project target selection with platform + version
  granularity; Standard Lua 5.1–5.5, LuaJIT, Redis 5/6/7 and Valkey 7.2/8, with scaffolding for
  Tarantool, OpenResty and Pandoc; platform-specific stdlib stubs resolved from the target; and
  `--std` follows the active target.
- **Apache-2.0 licensing**: `LICENSE`, `NOTICE` and `THIRD-PARTY.md` bundled at the plugin root in
  every zip.
- **README refresh**: accurate versions, live doc links, the full epic list and Lua 5.1–5.5.
- **LuaRocks Packages crash** (BUG-379): the package-browser debounce `Alarm` had no parent
  `Disposable` and threw on every open ([1b6a8ee2](https://github.com/internetisalie/lunar/commit/1b6a8ee2)).
- **RockspecBridge log noise** (BUG-380): the "no Lua runtime configured" message is `debug`, not
  `warn` ([bab34472](https://github.com/internetisalie/lunar/commit/bab34472)).
- **Release build**: `patchPluginXml` accepts milestone-style CHANGELOG headers, so overriding the
  plugin version no longer fails.
- **Redis sandbox false positive and quick-doc over-triggering** (REDIS-06): the sandbox inspection
  respects a shadowing local, and command docs surface only on the command-name literal ([5b7c9d0c](https://github.com/internetisalie/lunar/commit/5b7c9d0c)).
- **Parser error recovery for block constructs** (SYNTAX-18): `do`/`while`/`repeat`/function rules pin
  after their opening keyword, so a half-written block yields a partial PSI node instead of cascading
  to end of file ([5ad20590](https://github.com/internetisalie/lunar/commit/5ad20590)).
- **Typed lambda parameters from expected callback types** (TYPE-10): a lambda passed to a `fun(...)`
  parameter infers its own parameter types with no manual `---@param` ([b7c71001](https://github.com/internetisalie/lunar/commit/b7c71001)).

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
