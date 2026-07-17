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
> here is advisory. Only **open** work is listed — completed waves (0–10, 13–17) are done and
> live in git history.

> **MVP scope (2026-07-14; expanded 2026-07-17).** The product is **feature-complete for MVP**
> once Wave 18's items land — **SYNTAX-18, MAINT-23, TYPE-10, REDIS-06**, plus **ROCKS-16** and **TOOLING-08** (added 2026-07-16
> under the testing-surfaced-issues clause: user-feedback browser + settings UX consolidation) —
> **and Wave 19 (codebase-review remediation, MAINT-24…32) completes** — added to MVP 2026-07-17
> given its breadth: it drains the review's confirmed P1 crashes, destructive quick fixes, and
> silently-broken features across every subsystem, which is quality debt an MVP shouldn't ship.
> **MAINT-21**, **TARGET-07**, and **TARGET-08** are *not* MVP-gating (MAINT-21 is
> externally blocked on the unreleased 2026.2 platform; TARGET-07 and TARGET-08 are post-MVP
> stub/library-coverage follow-ons) — they live in **Wave 21**.
> The **AI epic (Wave 20) is post-MVP.**

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

## Wave 18 — MVP feature work  *(per-feature deferrals stay in each risks-and-gaps.md — promote here only when top-level tracking is warranted)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| SYNTAX-18 | Parser Error Recovery for Block Constructs | done | M | — | EDITOR-08 simplification *(soft)* | shipped 2026-07-16: `pin` (no `recoverWhile` — empirically unusable, see feature risks doc) on 9 block rules + regen + downstream adaptations; full suite green |
| MAINT-23| Test hygiene: `ValkeyStubResourceTest` tearDown (target leak) | done | S | — | — | ✓ REDIS-03's `ValkeyStubResourceTest` set `Target(VALKEY,…)` via `setTargetAndNotify` without restoring — the same latent leak fixed in `RedisAmbientTypingTest`/`LuaRedisCommandInspectionTest`; an un-restored target leaks into alphabetically-later suites. Fixed 2026-07-16: added a `tearDown` restoring `Target(STANDARD,"5.4")` + `PlatformLibraryIndex.reload()` (mirrors the sibling suites). Full ktlintCheck + test suite green on gce-builder |
| TYPE-10 | Expected-type → lambda-parameter inference | done | C | TYPE-01 *(Type engine)* | REDIS-05 AC-2 *(full callback typing)* | ✓ new: in `LuaTypesVisitor.visitFuncCall` unification, propagate a parameter's declared `fun(...)` type onto a passed lambda's params. Ground-truthed 2026-07-14: engine types params only from a **direct** `---@param`, not from the expected argument type (probe V3 → `Undefined`), so `redis.register_function('f', function(keys,args)…)` doesn't type `keys` as `string[]`. High-blast-radius shared-engine change → gate with the REDIS-04 §3.1c-style regression contract (`.../lang/types/*` + consumers) + positive tests (table.sort comparator, pcall, register_function). Re-enables REDIS-05 TC-STUB-1 `keys[1]→string` (currently descoped, REDIS-05 risks Gap 2.4) |
| TOOLING-08 | Lua settings restructure | done | S | TOOLING-06 *(done)* | — | ✓ settings-page rework: explicit platform-target control (BUG-362 root cause: target only ever derived via `LuaTargetSynchronizer`, no user control existed), capability-based bindings split (evicts the capability-less redis/valkey server kinds), wires the orphaned `setGlobalBinding` UI, DSL migration for the two FormBuilder panels (BUG-369); planned 2026-07-16 → `docs/features/tooling/08-settings-restructure/` (6 phases) |
| ROCKS-16 | Plugins-style LuaRocks package browser redesign | done (VNC pending) | S | ROCKS-02/-03/-05/-12 *(done)*; TOOLING-02 *(done)* | — | ✓ delivered 2026-07-17 — all 8 phases landed under `rocks/browser/` (canonical `--tree` install target, two-tab Marketplace/Installed surface, JBHtmlPane detail pane, honest error/empty states, update + add-to-rockspec + popular-list); absorbs BUG-363/365/366/367/368 and review findings #48/#64/#70/#71b; full suite 2055/0, ktlint clean. Live UI checklist (font/alignment/empty/deps/error-link, tabs, stripe names, install-visibility) deferred to a supervised verify-in-ide pass; DR-01/DR-04 annotated "awaiting VNC gate" |
| REDIS-06 | Redis sandbox + quick-doc gating refinements | done | C | REDIS-04 *(done)* | — | ✓ done 2026-07-16 (PR #5, squash `5b7c9d0c`): (1) `LuaRedisSandboxInspection` now exempts shadowed locals via a side-effect-free `LuaResolveUtil.scopeCrawlUp` + local-only `LocalBindingScopeProcessor` (no VFS/type-engine — the earlier TestLogger cause is not reintroduced; DR-01 clean). (2) `RedisCommandDocumentationTargetProvider` gates on caret-on-command-STRING (`LuaElementTypes.STRING` + `!==` identity). Full GCE suite 2001/0; manual sandbox-IDE checklist still advisory |

---

## Wave 19 — MVP quality: codebase-review remediation  *(MAINT; MVP-gating, added 2026-07-17)*

Drains the 57 still-open findings of the 2026-07 full codebase review ([docs/review.md](review.md);
remediation status verified 2026-07-17 — 5 fixed, 4 moot, 6 partial incidentally via earlier waves).
Coalesced by root cause per the review's §2.5 "fix once, not per-site" analysis rather than filed as
~57 individual bugs. Browser/settings-adjacent findings are **absorbed by ROCKS-16 (#48, #64, #70,
#71b) and TOOLING-08 (#41, #44, #50)** — tracked in those features' risks-and-gaps, not here.
Isolated fixes went to BUG-382…386 (#23, #45, #46, #49, #15); #22 was already BUG-361.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| MAINT-31 | Dead-code sweep (review §3) | done | C | — | every other Wave-19 feature *(soft — shrinks their diffs)* | ✓ pure deletion; **do first** |
| MAINT-25 | Type-graph immutability & safety | todo | M | TYPE-10 *(serialize — same hot files `LuaTypesVisitor`/`LuaTypeGraph`)* | — | Serial: type engine |
| MAINT-24 | Debugger & test-runner hardening | todo | S | MAINT-22 *(done — coroutine debugger base)* | **AI-03** | ✓ run/ subsystem |
| MAINT-26 | Luacheck pipeline correctness | todo | S | — | — | ✓ analysis/luacheck |
| MAINT-27 | LuaCATS doc & lexer correctness | todo | S | — | — | ✓ luacats/ (needs local parser-gen jars for the .flex regen) |
| MAINT-28 | Completion correctness & performance | todo | S | — | — | ✓ completion stack |
| MAINT-29 | Control-flow & inspection accuracy | todo | S | — | — | ✓ analysis/ + quick fixes |
| MAINT-30 | Indexing & resolution caching | todo | S | — | — | Serial vs MAINT-28 *(both touch `LuaCompletionContributor`/resolution seams)* |
| MAINT-32 | Process-execution discipline (`LuaProcessUtil`) | todo | S | — | — | ✓ util/ primitive + caller migration (#11, §2.1) |

---

## Wave 20 — AI integration  *(AI epic)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| AI-01 | MCP Server Integration | todo | M | `com.intellij.mcpServer` bundled plugin (optional dep) | AI-02, AI-03 | Serial: registration foundation |
| AI-02 | Semantic Context Toolset | todo | S | AI-01 *(lunar-mcp.xml infra)* | — | after 01 |
| AI-03 | Debugger Toolset | todo | C | AI-01; **MAINT-24** *(debugger hardening — formerly "MobDebug hardening, unscheduled MAINT"; scoped 2026-07-17)*; REDIS-02 *(soft, LDB binding)* | — | after 01 + hardening |
| AI-04 | LuaCATS Annotation Generator | todo | S | — *(type engine done)* | — | ✓ (engine-only, no MCP) |

---

## Wave 21 — Post-MVP follow-ons  *(non-gating; moved out of Wave 18 on 2026-07-17)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TARGET-07 | Lua 5.5 standard-library stubs | planned | C | SYNTAX-09 *(Lua 5.5, done)* | — | ✓ planned 2026-07-16 → `docs/features/target/07-lua-5.5-stdlib-stubs/`. Additive resource-only: `runtime/standard/lua-5.5/` = copy of 5.4 + `table.create` + `_VERSION` bump; resolution already wired (`PlatformVersionRegistry`/`Target`/`RuntimeLibraryProvider` map 5.5→`lua-5.5`), **zero production Kotlin**. Note: roadmap brief was imprecise — `table.move` is already 5.3+, `global` is a keyword (SYNTAX-09), not a stub file |
| TARGET-08 | On-demand LuaLS / LuaCATS definition libraries | planned | C | TARGET-04 *(Library Root Resolution, done)*; LuaCATS parsing *(done)* | — | ✓ planned 2026-07-16 → `docs/features/target/08-on-demand-definition-libraries/` (5 phases): bundled curated JSON catalog → per-project enable list in `.idea/lunar.xml` → leaf `LuaArtifactDownloader`/`LuaArchiveExtractor` fetch (not the heavy provision pipeline) → `LuaDefinitionLibraryProvider : AdditionalLibraryRootsProvider` (models `LuaRocksLibraryProvider`) → settings UI. Bundle-nothing licensing (URLs+hashes only). Kept under TARGET; graduate to a `DEFS` epic only if a browse/install catalog UI is greenlit. Original scoping note: support consuming community **LuaLS/LuaCATS definition libraries** (the [LuaCATS](https://github.com/LuaCATS) org repos + LuaLS addon ecosystem — love2d, busted, luassert, openresty, …) for **third-party** libraries a project uses, **fetched/enabled on demand** rather than bundled. Distinct from the first-party platform stdlib stubs under `runtime/` (TARGET-07 / TARGET-04): reuse `PlatformLibraryProvider`'s library-root injection to register a fetched definition tree, parse its `@meta` LuaCATS annotations (existing support), and cache per project. Fetch mechanic overlaps TOOLING provisioning (cf. TOOLING-04 already downloads the `lua-language-server` binary). Community defs carry their own licenses (mostly MIT) — attribute any that get bundled/cached; a purely on-demand, user-selected fetch is package-manager-like. **Epic placement is a judgment call** — filed under TARGET for the library-resolution reuse; could graduate to its own epic (or a TOOLING id) if it grows |
| MAINT-21| IJPGP 2.17 + Gradle 9 bump (deferred from MAINT-03-04) | deferred | L | **2026.2 platform release** *(build 262; not yet shipped as of 2026-07-03)* | — | Spike proved Gradle 9.1 + IJPGP 2.17 build clean but 2.17's test framework needs a platform newer than 261; land with the SDK bump. Untried: intermediate IJPGP ~2.7–2.16 for a 261-compatible Gradle-9 upgrade |
