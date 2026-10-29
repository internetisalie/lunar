---
id: "ROADMAP"
title: "Project Roadmap"
type: "guide"
priority: "high"
folders:
  - "[[features]]"
---

# Project Roadmap

> **Durable value = the ordering and dependency edges, not the `Status` column.** Canonical
> per-feature status is each feature's `requirements.md` front-matter (`status:`); the `Status`
> here is advisory. Only **open** work is listed — completed waves (0–19) and every row that
> reached `done` are stripped from this file and live in git history (`git log -p docs/roadmap.md`).

> **MVP COMPLETE (2026-07-18).** The product is **feature-complete for MVP** — Wave 18 (SYNTAX-18,
> MAINT-23, TYPE-10, REDIS-06, ROCKS-16, TOOLING-08) and Wave 19 (codebase-review remediation,
> MAINT-24 through 32) both shipped and released (v0.19.0 / v0.19.1). Those two completed waves are
> removed from this roadmap and live in git history. **Remaining work is all post-MVP:** the
> **Wave 20 follow-ons** (corpus-sweep fixes + the consolidated loose backlog, incl. MAINT-21,
> the Gradle 9 / GoLand 2026.2 platform bump), the **Wave 21 definition-library
> stream** (TARGET-07/09/10 and their bug follow-ons), and the **AI epic (Wave 22)**.
>
> **Waves map to releases: wave N ships as `v0.N.x`.** Wave 20 → v0.20.0/v0.20.1,
> Wave 21 → v0.21.0.

## How an agent uses this

- **Pick the lowest-numbered wave with a *ready* item** — ready = every `Depends on` is `done`;
  within a wave prefer higher priority.
- **Parallel ✓** = new files / a distinct extension point → safe to run concurrently in separate
  worktrees. **Serial: <cluster>** = mutates a shared hot file → one agent at a time in that cluster.
- **`Depends on` and `Unblocks` carry edges, not prose** — an ID, at most with a short
  parenthetical (`TARGET-08 *(done)*`). Rationale, measurements and planning state belong in the
  feature's own docs, which are canonical. A cell that restates them goes stale on its own: by
  2026-08-22 ANALYSIS-07's `Unblocks` had grown to 886 characters of planning narrative wrapped
  around two edges that were no longer true, both bugs having since been fixed elsewhere.
- **Update `status` to `done`** in the feature's `requirements.md` as you finish; that makes its
  dependents ready.
- **DoD gate (learned the hard way):** a feature surfacing through a platform extension point
  (inspection, annotator, completion, refactoring, safe-delete) is "done" only when a **real-flow**
  test drives that machinery (`enableInspections()+doHighlighting()`, `completeBasic()`,
  `SafeDeleteHandler.invoke`, …) and asserts the user-visible result — engine-only tests hid a real
  REFACT-03 bug.

## ⚠️ Unmerged feature branches to recover (flagged 2026-07-06)

Front-matter reports **every epic done** (TOOLING completed 2026-07-09), but three git branches
carry **unmerged commits** for supposedly-complete features. They were **kept** (not deleted) during a
branch cleanup. Before trusting "all done," reconcile each against `main`/the front-matter
and either integrate it (verify with the real-flow DoD gate above) or consciously discard it:

| Branch (local; ✎ = also on gitea) | Tip | What it is | Action |
| :--- | :--- | :--- | :--- |
| `feature/COMP-03-02-global-symbol-suggestions` | `dac5fb83` | **Completed feature** — "Implement Global Symbol Suggestions" (not WIP; not on `main`) | Verify vs COMP-03-02 status; likely integrate |
| `feature/syntax-inlay-hints-method-chaining` ✎ | `105e87b3` | WIP — method-chaining inlay hints (relates to SYNTAX-07/-17) | Finish or discard |
| `wip/lua-types-visitor` | `c820bf63` | WIP — `LuaTypesVisitor`, rescued from a stash (type engine) | Finish or discard |

Each is 1 commit ahead of `main`. Recover a branch's work with `git cherry-pick <tip>` (or merge)
onto a fresh feature branch; the SHAs above are stable references even if a branch is later pruned.

---

## Wave 20 — Corpus sweep & consolidated backlog  *(non-gating; shipped as v0.20.x)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| MAINT-43 | CI image publish job — build and push `lunar-ci` to tier2 with a monotonic-semver gate; the GAR copy stays manual | todo | S | — | — | ✓ |
| MAINT-36 | Pre-contract residue — correctness, dead code and contract compliance (July review §4, findings #76–#92) | todo | M | — | — | ✓ |
| MAINT-41 | Enforce the July review's systemic patterns mechanically (Logger.error on expected failures, PSI outside read actions, PSI in data classes, wildcard imports) | todo | M | — | — | ✓ |
| BUG-520 | The require-based cross-file completion phase never returns anything, and its two tests cannot fail (reopens July review #24 / MAINT-28 TC-24) | todo | S | — | found in the third-pass review (#100); read and grepped, not run; self-`require` lead unconfirmed | ✓ |
| BUG-521 | The Color Scheme page offers five colour keys that nothing applies; the highlighter maps node types the lexer never emits | todo | S | — | found in the third-pass review (#98); verified by grep, not run | ✓ |
| BUG-522 | "Enable type inference" and "Add additional completions" do nothing — decision: remove both settings | todo | S | — | found in the third-pass review (#106); verified by grep, not run | ✓ |
| BUG-523 | The debugger's initial top frame is built without its stack entry, so it has no variables | todo | S | BUG-463, DEBUG-05 Phase 1 *(same lines — fix together)* | found in the third-pass review (#109); read against platform source, not run | — |
| RUN-09 | Lunar-shipped busted output handler: nested describe suites and live results (RUN-05-06 residual) | todo | M | — | BUG-527 | ✓ |
| BUG-527 | A busted spec that calls `pending(...)` produces no test tree: busted 2.3.0's JSON handler fails to encode the report | todo | S | — | — | ✓ |
| BUG-529 | LuaRenameConflictTest's cancellation-count test fails intermittently on the 2026.2 platform | todo | S | — | — | ✓ |
| BUG-532 | LuaRocks new-project page: typing a project name does not clear "Project name must not be empty" | todo | S | — | — | ✓ |
| BUG-535 | Provisioning sets a progress fraction on an indeterminate indicator, which the platform logs as IllegalStateException | todo | S | — | — | ✓ |
| BUG-538 | Debugging in a wizard-provisioned environment fails: mobdebug requires luasocket, which provisioning does not install | todo | S | — | — | ✓ |
| MAINT-42 | Third-pass residue — P3 cleanups in the files the July review never covered (review §6) | todo | C | — | — | ✓ |
| REDIS-07 | Reuse an IntelliJ Database Redis data source | planned | C | REDIS-01 *(connections, done)* | — | ✓ |
| BUG-388 | Dimmed-text binding-state renderer | backlog | C | BUG-387 *(done)* | — | ✓ |
| BUG-358 | Reformatting a read-only file throws a write-unsafe exception | todo | C | — | — | ✓ |
| BUG-411 | Vertical tab and form feed are not treated as whitespace | todo | C | — | — | ✓ |
| BUG-413 | Generated doc comments infer wrong `@param` types | todo | C | — | — | ✓ |
| BUG-421 | Wildcard imports keep `no-wildcard-imports` disabled, against the engineering contract | todo | C | — | — | ✓ |
| BUG-443 | Quick Doc reported empty for `ngx.say` live, never reproduced | todo | C | — | — | — |
| BUG-450 | The debugger's `STACK` payload is unbounded at both ends, and DEBUG-07 claimed otherwise | todo | C | — | — | ✓ |
| DEBUG-06 | Debug Target Configuration — the severity ladder that lets a launch be refused | planned | S | — | BUG-455 *(Phase 2 retires it)*; BUG-461's `testCheckConfigurationThrowsWithoutRuntime` entry; DEBUG-05 *(validation model it extends)* | Serial: debug target |
| DEBUG-05 | Remote Debugging — attach configuration, host field, bidirectional path mapping | planned | S | DEBUG-06 *(validation model)* | `-09` is a live defect in **local** debugging today | Serial: debug target |
| DEBUG-07 | Lazy Remote Stack Evaluation — confirmed real, low value | todo | C | — | **measured 2026-08-22** (BUG-450 §5b): parse beats serialization at every size, but there is a ~30 ms PSI floor a payload bound cannot cross, and it is off-EDT — realistic case ~125 ms end to end | ✓ |
| BUG-451 | luacheck `--std` overrides the project's `.luacheckrc`, false warnings on every rockspec and spec file | todo | S | — | one flag disables two mechanisms; user cannot override it | ✓ |
| BUG-452 | luacheck exit 2 is a lint result, not a crash — the whole report is discarded | todo | S | — | a test pins the defect as intended | ✓ |
| BUG-490 | A dotted or colon function's own name is counted as a usage of a same-named global function | todo | S | — | BUG-476 closed the lexical half; a test pins the global half as intended; rename corruption inferred, not executed | ✓ |
| BUG-498 | A class merged with a same-named stdlib class gets its members in a different order from one JVM to the next | todo | S | — | TYPE-12 DR-09: `---@class File` vs the stubs' `io.lua` rendered two ways across 8 JVMs; `materializeClass` is last-write-wins over index order, so a conflicting member's TYPE may also vary (unmeasured) | ✓ |
| BUG-499 | The sweep naming guard skips nested test classes and classes that only inherit their tests | todo | C | — | `SweepNamingGuardTest` drops `$` names and reads `declaredMethods` only; read, not run | ✓ |
| BUG-500 | A require of a module name without a dot resolves to any `init.lua` in the project | todo | S | — | MAINT-37 Phase 3b review: the `FilenameIndex` fallback's path-tail filter is skipped for undotted names (`LuaModuleFileResolver`); wrong Ctrl+B target and wrong module type; read, not run | ✓ |
| BUG-511 | Global completion offers another file's `---@class` local as a bare value, with no `require` | todo | S | — | found in BUG-510's review: since BUG-510, accepting `Iter`/`uv`/`cjson` raises *Undeclared variable* at once; read, not run | ✓ |
| BUG-512 | A name resolves to a declaration of the wrong kind: a bare name to a member, a member to a bare variable | todo | S | — | found in BUG-504's review: bare `close()` after `require("mod")` resolves to `M:close` (no *Undeclared variable*); `handlers[1].legacy` resolves to a bare global `legacy`; `config.timeout` resolves lexically to a `local timeout`; reproductions executed, root causes read | ✓ |
| BUG-453 | The human formatter is parsed instead of luacheck's documented editor interface | todo | S | — | escapes in tooltips; a configured formatter yields a silent false-clean | ✓ |
| BUG-454 | Toggling a breakpoint mid-run deadlocks the debug session | todo | S | — | unanswered SETB held under writeMutex | ✓ |
| BUG-455 | No run configuration can block a launch — the non-fatal exception tier is thrown | todo | S | — | RuntimeConfigurationError appears zero times in src/ | ✓ |
| BUG-482 | A multi-line argument list is joined onto the call line instead of continuation-indented | todo | S | — | `testArgs` revived by BUG-461; also FORMAT-01-17's description is wrong | ✓ |
| BUG-483 | A multi-line local declaration gets no continuation indent on its names | todo | S | — | `testLocalVarDecl` revived by BUG-461 | ✓ |
| BUG-484 | A `::label::` in a nested block is flushed to column 0 | todo | S | — | FORMAT-01-22; `testLabel` pins it | ✓ |
| BUG-485 | A line breakpoint cannot be placed on the first statement of any block | todo | S | — | `canPutAt` parent walk ends on the BLOCK; found writing real canPutAt tests (BUG-461) | ✓ |
| BUG-463 | Step/watchpoint pauses have never had a source position — every non-breakpoint pause renders `<internal C>` | todo | S | — | shipped defect, no requirement row; DEBUG-05 Phase 1 fixes it incidentally — do not fix twice | — |
| BUG-464 | Ragged requirement tables render the wrong status — 36 rows, 11 files | todo | C | — | REFACT-01's own table rendered **Full** beside "grep is empty"; the durable half is a `lint_docs.py` table-shape check | ✓ |
| BUG-458 | Safe Delete of a label leaves `::::`, unparseable on every Lua version | todo | C | — | — | ✓ |
| BUG-460 | Naming two indent options hides tabs/tab-width and suppresses 4 `.editorconfig` properties | todo | C | — | — | ✓ |

## Wave 21 — Definition libraries & resolution correctness  *(shipped as v0.21.0)*

> **Versioning convention: wave N ships as `v0.N.x`.** This wave holds the work released in
> **v0.21.0** — TARGET-08's on-demand definition libraries and the completion/resolution defects
> that had to be fixed for them to work — plus the follow-ons that stream is still carrying.
> It was originally filed under Wave 20 and moved here on 2026-08-04 to restore the convention;
> the v0.21.0 tag and its published release are unchanged. Wave 20 keeps what actually shipped as
> v0.20.x (the MAINT-33 corpus sweep and its four fixes) plus the consolidated loose backlog.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TARGET-07 | Lua 5.5 standard-library stubs | planned | C | SYNTAX-09 *(Lua 5.5, done)* | — | ✓ |
| ANALYSIS-07 | Value constraints over the CFG — the type engine and the control-flow graph are two flow analyses that never speak | planned | **L** | ANALYSIS-06 *(CFG, done)*, TYPE-08 *(flow-sensitive, done)* | — | — |
| TARGET-09 | Addon auto-detection (turn TARGET-08 from capability into fix) | **planned** | S | TARGET-08 *(done)* | — | ✓ |
| TARGET-10 | `wx`/`wxstc`/`wxaui` definition libraries — investigate, then catalog; includes the ZeroBrane corpus `wxlua` pin (TARGET-10-12, ex-MAINT-37 Phase 5) | **in_progress** | C | — | — | ✓ |
| BUG-541 | The zerobrane corpus sweep's `LuaSuspiciousConcatenation` count flickers 19/17 between runs once members are pinned to their real target | todo | S | BUG-515 *(done)* | found in BUG-515 planning: `menu_project.lua:96`/`:136` `nil`-operand hits present in some runs only; main steady at 19; cause not established | ✓ |
| BUG-516 | A stdlib file method does not resolve through a `---@type File` receiver: `io.stdout:close()` finds no declaration | todo | S | — | — | ✓ |
| BUG-518 | A deprecated field assignment never warns: the Deprecated API inspection reads no tag on `pkg.x = value` | todo | S | — | found in BUG-513: `---@deprecated` above `pkg.x = 1` gives no warning on a read, global or local; a deprecated function does | ✓ |
| BUG-519 | A definition library's type snapshots are never pinned, so they rebuild after every edit (stdlib globals such as `error` count as rescued) | todo | S | — | found in TARGET-11 DR-08: all 9 rebuilt Neovim snapshots `pinnable=false`; mechanism read from code; other libraries not measured; does not gate TARGET-11 | ✓ |
| BUG-505 | `function a.b.c()` members never complete under `a.b.` — the receiver-member index drops two-separator function names | todo | S | — | completes `vim.api.*` / `vim.fn.*` (TARGET-11-08) | ✓ |
| BUG-507 | A table reached through a field (`---@type`d field, field bound to `require`, nested constructor) offers no members | todo | M | — | completes `vim.uv.*`, `vim.fs.*`, `vim.log.levels.*` (TARGET-11-08) | ✓ |
| BUG-508 | A qualified call (`M.clear()`) also resolves to a library module's same-named member, so a deprecated library `M.clear` flags the project's own | todo | S | — | removes the residual deprecated-API hits on lazy.nvim with Neovim enabled | ✓ |
| TYPE-14 | Generics — the LuaLS closed baseline (nested positions, cross-file callees, methods, generic classes, grammar) plus a constraint's bound in the body | **planned** | **M** | TYPE-05 *(done)*, TYPE-10 *(done)*, TYPE-13 *(done)*, MAINT-34 *(done)* | closes BUG-420 | needs a parser regen (local-only) |
| BUG-420 | A parameterized `@class` parent never resolves, so inheritance through a generic base is lost | todo | C | **TYPE-14** *(Phase 5 closes it — do not fix twice)* | — | ✓ |
| BUG-481 | A table constructor is reported as not assignable to an array type — `---@type string[]` `local xs = {}` is a false ERROR | todo | S | — | — | ✓ |
| BUG-555 | A `---@field name T?` field is required: the trailing `?` does not make the type admit nil | todo | S | — | found triaging the LuaCATS grammar batch: 10 lazy.nvim hits (`url`); reproduces on `8e7a48f2` | ✓ |
| BUG-556 | A bracket-keyed `---@field [k] T` is a required member named `"[k]"`, which no constructor can supply | todo | S | — | found triaging the LuaCATS grammar batch: 21 lazy.nvim hits (`[1]`); reproduces on `8e7a48f2` | ✓ |
| BUG-557 | A comment block with several `@class` tags and a trailing `@type` checks the next statement against every class | todo | S | — | found triaging the LuaCATS grammar batch: 18 lazy.nvim hits at `pkg/init.lua:30`, 1 of them added; reproduces on `8e7a48f2` | ✓ |
| BUG-558 | A `@class` declaration's own table (`local M = {}`) is checked for every required instance field | todo | S | — | found triaging the LuaCATS grammar batch: net +2 lazy.nvim hits; the pattern predates it | ✓ |
| BUG-559 | A string-literal `@param` type (`---@param k "a"\|"b"`) silently becomes an LDoc tag | todo | S | — | deferred from the LuaCATS grammar batch (§8) | ✓ |
| BUG-560 | The `[]` after a parenthesised LuaCATS type (`(A\|B)[]`) is read as prose | todo | C | — | deferred from the LuaCATS grammar batch (§8) | ✓ |
| BUG-561 | The bundled Lua 5.1 stubs spell literals `'"nil"'` and varargs `fun(...any)`, which no lexer state reads as intended | todo | C | — | deferred from the LuaCATS grammar batch (§8); see BUG-514 | ✓ |
| BUG-562 | A numeric-literal `@return` type (`---@return 1\|2`) is a LuaCATS parse error | todo | C | — | deferred from the LuaCATS grammar batch (§8) | ✓ |
| BUG-563 | A vararg `fun(...)` type in a `---@cast` is read as a class named `fun` | todo | C | — | found implementing BUG-514 | ✓ |
| BUG-564 | A local declared without a value and assigned inside a `while` loop is reported as an unused local, though it is read | todo | S | — | found triaging BUG-554's corpus movement: 2 lazy.nvim hits (`to`, `url`) that a false *Too few arguments* ERROR had hidden | ✓ |
| BUG-565 | No test detects TYPE-12's A6TH mutation: the false positives that exposed it are gone | todo | S | — | found fixing BUG-554: `pipeUnion`, `fileUnionStdlib` and `luacheck-cache` no longer report the receiver hypotheses A6TH added to | ✓ |
| BUG-8 | A dot call of a colon method with an explicit `self` matches `self` to the first `@param` | todo | S | — | BUG-566 | ✓ |
| BUG-9 | A bare `---@class` block binds to the next local even across a blank line | todo | S | — | BUG-566 | ✓ |
| BUG-10 | An untyped `fun(self)` callback parameter is read as a table type | todo | S | — | BUG-566 | ✓ |
| BUG-11 | An optional parameter typed as a union with a table literal collapses to `nil` | todo | S | — | BUG-566 | ✓ |
| BUG-566 | A method call's argument types are never checked against its declared `@param` types | blocked | M | BUG-8, BUG-9, BUG-10, BUG-11 | found fixing BUG-554: `c:m(1, "y")` is silent where a free function's `g("x", 1)` is reported; fixing it will raise lazynvim's `LuaTypeAssignability` | ✓ |
| MAINT-40 | Corpus sweep results stored per commit — one CTRF failed test per finding, stored as loom `a3` suite `corpus` with an environment-derived identity, so a "before" is a fetch, not a re-record | planned | M | loom LCL-241 *(store step only)* | — | ✓ |
| MAINT-45 | Performance records stored per commit — CTRF counts and timings from `lunar-ci`, counts gate PRs against the merge-base, timings trend over time | todo | M | MAINT-40 | — | ✓ |
| MAINT-46 | Ticket IDs minted by `loom-cli id mint` after a scripted backfill of every used number, so concurrent sessions never pick the same ID | todo | S | — | — | ✓ |
| BUG-403 | Lunar hard-depends on the `glimmer/luacheck` fork without declaring or enforcing it | todo | S | — | — | ✓ |
| BUG-405 | OpenResty/NGX target emits no luacheck std although `ngx_lua` exists | todo | C | — | — | ✓ |
| BUG-462 | The package detail card strands its loading placeholders, and its description and dependency regions never render | todo | S | — | — | ✓ |

---

## Wave 22 — AI integration  *(AI epic)*

> **Deprioritised on 2026-08-06 — ordering only.** AI work does not precede quality work, so the epic's front-matter `priority` is `low`. The **Prio** column below is unchanged and deliberately so: MoSCoW records whether a feature is *required for its epic*, which is a different axis from *what gets picked up next*. AI-01 is still a `Must` for the AI epic — there is no AI epic without the MCP server — it is simply not scheduled while MAINT and BUG items remain open.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| AI-01 | MCP Server Integration | todo | M | `com.intellij.mcpServer` bundled plugin (optional dep) | AI-02, AI-03 | Serial: registration foundation |
| AI-02 | Semantic Context Toolset | todo | S | AI-01 *(lunar-mcp.xml infra)* | — | — |
| AI-03 | Debugger Toolset | todo | C | AI-01; **MAINT-24** *(done; debugger hardening — formerly "MobDebug hardening, unscheduled MAINT"; scoped 2026-07-17)*; REDIS-02 *(soft, LDB binding)* | — | — |
| AI-04 | LuaCATS Annotation Generator | todo | S | — *(type engine done)* | — | ✓ |
