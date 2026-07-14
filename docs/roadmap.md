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

## Wave 18 — AI integration  *(AI epic)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| AI-01 | MCP Server Integration | todo | M | `com.intellij.mcpServer` bundled plugin (optional dep) | AI-02, AI-03 | Serial: registration foundation |
| AI-02 | Semantic Context Toolset | todo | S | AI-01 *(lunar-mcp.xml infra)* | — | after 01 |
| AI-03 | Debugger Toolset | todo | C | AI-01; **MobDebug hardening (docs/review.md, unscheduled MAINT)**; REDIS-02 *(soft, LDB binding)* | — | after 01 + hardening |
| AI-04 | LuaCATS Annotation Generator | todo | S | — *(type engine done)* | — | ✓ (engine-only, no MCP) |

## Wave 19 — Backlog & deferred follow-ups  *(all remaining non-AI open work; per-feature deferrals stay in each risks-and-gaps.md — promote here only when top-level tracking is warranted)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| SYNTAX-18 | Parser Error Recovery for Block Constructs | planned | M | — | EDITOR-08 simplification *(soft)* | ✓ new files: grammar `pin`/`recoverWhile` on 9 block rules + regen `src/main/gen`; planned & reviewed 2026-07-13 |
| MAINT-23| Test hygiene: `ValkeyStubResourceTest` tearDown (target leak) | planned | S | — | — | ✓ REDIS-03's `ValkeyStubResourceTest` sets `Target(VALKEY,…)` via `setTargetAndNotify` without restoring — the same latent leak fixed in `RedisAmbientTypingTest`/`LuaRedisCommandInspectionTest`; an un-restored target leaks into alphabetically-later suites. Add a `tearDown` restoring `Target(STANDARD,"5.4")` + `PlatformLibraryIndex.reload()`. Currently masked (later suites happen to re-set their own target); fix before it bites |
| TYPE-10 | Expected-type → lambda-parameter inference | planned | C | TYPE-01 *(Type engine)* | REDIS-05 AC-2 *(full callback typing)* | ✓ new: in `LuaTypesVisitor.visitFuncCall` unification, propagate a parameter's declared `fun(...)` type onto a passed lambda's params. Ground-truthed 2026-07-14: engine types params only from a **direct** `---@param`, not from the expected argument type (probe V3 → `Undefined`), so `redis.register_function('f', function(keys,args)…)` doesn't type `keys` as `string[]`. High-blast-radius shared-engine change → gate with the REDIS-04 §3.1c-style regression contract (`.../lang/types/*` + consumers) + positive tests (table.sort comparator, pcall, register_function). Re-enables REDIS-05 TC-STUB-1 `keys[1]→string` (currently descoped, REDIS-05 risks Gap 2.4) |
| REDIS-06 | Redis sandbox + quick-doc gating refinements | planned | C | REDIS-04 *(done)* | — | ✓ two REDIS-04 correctness refinements deferred 2026-07-14 (non-blocking, not TC-covered): (1) `LuaRedisSandboxInspection` skips only declaration positions, not full global-resolution (design §3.7 step 2) → a shadowed local `print`/`io` gets a false-positive WARNING; add a side-effect-free resolution check (earlier VFS-based resolve caused TestLogger errors — verify against the full gate). (2) `RedisCommandDocumentationTargetProvider` doesn't gate on caret-on-STRING (design §3.6 step 1) → quick-doc over-triggers when the caret is elsewhere in the call |
| MAINT-21| IJPGP 2.17 + Gradle 9 bump (deferred from MAINT-03-04) | deferred | L | **2026.2 platform release** *(build 262; not yet shipped as of 2026-07-03)* | — | Spike proved Gradle 9.1 + IJPGP 2.17 build clean but 2.17's test framework needs a platform newer than 261; land with the SDK bump. Untried: intermediate IJPGP ~2.7–2.16 for a 261-compatible Gradle-9 upgrade |
