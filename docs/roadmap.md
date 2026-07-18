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
> here is advisory. Only **open** work is listed — completed waves (0–19) are done and
> live in git history.

> **MVP COMPLETE (2026-07-18).** The product is **feature-complete for MVP** — Wave 18 (SYNTAX-18,
> MAINT-23, TYPE-10, REDIS-06, ROCKS-16, TOOLING-08) and Wave 19 (codebase-review remediation,
> MAINT-24 through 32) both shipped and released (v0.19.0 / v0.19.1). Those two completed waves are
> removed from this roadmap and live in git history. **Remaining work is all post-MVP:** the **AI
> epic (Wave 20)** and the **Wave 21 follow-ons** — TARGET-07/08 (post-MVP stub/library coverage)
> and MAINT-21 (deferred, externally blocked on the unreleased 2026.2 platform).

## How an agent uses this

- **Pick the lowest-numbered wave with a *ready* item** — ready = every `Depends on` is `done`;
  within a wave prefer higher priority.
- **Parallel ✓** = new files / a distinct extension point → safe to run concurrently in separate
  worktrees. **Serial: <cluster>** = mutates a shared hot file → one agent at a time in that cluster.
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

## Wave 20 — AI integration  *(AI epic)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| AI-01 | MCP Server Integration | todo | M | `com.intellij.mcpServer` bundled plugin (optional dep) | AI-02, AI-03 | Serial: registration foundation |
| AI-02 | Semantic Context Toolset | todo | S | AI-01 *(lunar-mcp.xml infra)* | — | after 01 |
| AI-03 | Debugger Toolset | todo | C | AI-01; **MAINT-24** *(done; debugger hardening — formerly "MobDebug hardening, unscheduled MAINT"; scoped 2026-07-17)*; REDIS-02 *(soft, LDB binding)* | — | after 01 + hardening |
| AI-04 | LuaCATS Annotation Generator | todo | S | — *(type engine done)* | — | ✓ (engine-only, no MCP) |

---

## Wave 21 — Post-MVP follow-ons  *(non-gating)*

> Also the consolidated loose backlog (folded in 2026-07-18) so the roadmap is the single source:
> the standalone **REDIS-07** feature, designed-but-unshipped UIs (**BUG-381 / BUG-388**), and the
> unreconciled older bug reports (**BUG-354/355/358/359/360**). Bugs also live under `docs/features/bug-fixes/`.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TARGET-07 | Lua 5.5 standard-library stubs | planned | C | SYNTAX-09 *(Lua 5.5, done)* | — | ✓ planned 2026-07-16 → `docs/features/target/07-lua-5.5-stdlib-stubs/`. Additive resource-only: `runtime/standard/lua-5.5/` = copy of 5.4 + `table.create` + `_VERSION` bump; resolution already wired (`PlatformVersionRegistry`/`Target`/`RuntimeLibraryProvider` map 5.5→`lua-5.5`), **zero production Kotlin**. Note: roadmap brief was imprecise — `table.move` is already 5.3+, `global` is a keyword (SYNTAX-09), not a stub file |
| TARGET-08 | On-demand LuaLS / LuaCATS definition libraries | planned | C | TARGET-04 *(Library Root Resolution, done)*; LuaCATS parsing *(done)* | — | ✓ planned 2026-07-16 → `docs/features/target/08-on-demand-definition-libraries/` (5 phases): bundled curated JSON catalog → per-project enable list in `.idea/lunar.xml` → leaf `LuaArtifactDownloader`/`LuaArchiveExtractor` fetch (not the heavy provision pipeline) → `LuaDefinitionLibraryProvider : AdditionalLibraryRootsProvider` (models `LuaRocksLibraryProvider`) → settings UI. Bundle-nothing licensing (URLs+hashes only). Kept under TARGET; graduate to a `DEFS` epic only if a browse/install catalog UI is greenlit. Original scoping note: support consuming community **LuaLS/LuaCATS definition libraries** (the [LuaCATS](https://github.com/LuaCATS) org repos + LuaLS addon ecosystem — love2d, busted, luassert, openresty, …) for **third-party** libraries a project uses, **fetched/enabled on demand** rather than bundled. Distinct from the first-party platform stdlib stubs under `runtime/` (TARGET-07 / TARGET-04): reuse `PlatformLibraryProvider`'s library-root injection to register a fetched definition tree, parse its `@meta` LuaCATS annotations (existing support), and cache per project. Fetch mechanic overlaps TOOLING provisioning (cf. TOOLING-04 already downloads the `lua-language-server` binary). Community defs carry their own licenses (mostly MIT) — attribute any that get bundled/cached; a purely on-demand, user-selected fetch is package-manager-like. **Epic placement is a judgment call** — filed under TARGET for the library-resolution reuse; could graduate to its own epic (or a TOOLING id) if it grows |
| MAINT-21| IJPGP 2.17 + Gradle 9 bump (deferred from MAINT-03-04) | deferred | L | **2026.2 platform release** *(build 262; not yet shipped as of 2026-07-03)* | — | Spike proved Gradle 9.1 + IJPGP 2.17 build clean but 2.17's test framework needs a platform newer than 261; land with the SDK bump. Untried: intermediate IJPGP ~2.7–2.16 for a 261-compatible Gradle-9 upgrade |
| REDIS-07 | Reuse an IntelliJ Database Redis data source | planned | C | REDIS-01 *(connections, done)* | — | ✓ redis/ → `docs/features/redis/07-database-datasource-integration/`: reuse an IntelliJ Database-plugin Redis data source as a connection source (vs a hand-entered connection) |
| BUG-381 | Ephemeral Redis/Valkey provisioning **UI** | planned | S | provisioning capability *(built, done)* | — | ✓ Docker/local-binary provisioning is implemented end-to-end but has **no UI entry point** (unreachable without hand-editing XML); fully planned (design + impl-plan, 3 phases) → `docs/features/bug-fixes/381-.../` |
| BUG-388 | Dimmed-text binding-state renderer | backlog | C | BUG-387 *(done)* | — | ✓ UX polish: convey inherited/no-default via `GRAYED_ATTRIBUTES` in a `ColoredListCellRenderer` instead of a parenthetical → `docs/features/bug-fixes/388-.../` |
| BUG-triage | Reconcile older bug reports (354/355/358/359/360) | todo | C | — | — | ✓ status ambiguous — reports lack resolution notes; verify each vs current code (354/355 LuaCATS parse, 358 reformat-readonly TransactionGuard, 359 package.path-nil false positive, 360 container-UID → likely env-only) and mark resolved-via-absorption or schedule. (356/363/365/366/367/368/369 already confirmed fixed) |
