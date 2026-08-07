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
> **Wave 20 follow-ons** (corpus-sweep fixes + the consolidated loose backlog, incl. MAINT-21 —
> deferred, externally blocked on the unreleased 2026.2 platform), the **Wave 21 definition-library
> stream** (TARGET-07/09/10, MAINT-37 and their bug follow-ons), and the **AI epic (Wave 22)**.
>
> **Waves map to releases: wave N ships as `v0.N.x`.** Wave 20 → v0.20.0/v0.20.1,
> Wave 21 → v0.21.0.

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

## Wave 20 — Corpus sweep & consolidated backlog  *(non-gating; shipped as v0.20.x)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| MAINT-21 | IJPGP 2.17 + Gradle 9 bump (deferred from MAINT-03-04) | deferred | L | **2026.2 platform release** *(build 262; not yet shipped as of 2026-07-03)* | — | — |
| REDIS-07 | Reuse an IntelliJ Database Redis data source | planned | C | REDIS-01 *(connections, done)* | — | ✓ |
| BUG-381 | Ephemeral Redis/Valkey provisioning **UI** | planned | M | provisioning capability *(built, done)* | — | ✓ |
| BUG-388 | Dimmed-text binding-state renderer | backlog | C | BUG-387 *(done)* | — | ✓ |
| BUG-358 | Reformatting a read-only file throws a write-unsafe exception | todo | C | — | — | ✓ |
| BUG-411 | Vertical tab and form feed are not treated as whitespace | todo | C | — | — | ✓ |
| BUG-413 | Generated doc comments infer wrong `@param` types | todo | C | — | — | ✓ |
| BUG-414 | Debugger variable navigation walks PSI with no read action | todo | S | — | — | ✓ |
| BUG-419 | Type engine reports incompatibility it cannot know (unknowns omitted; inferred demands checked as contracts) | todo | M | BUG-416/417 *(done — built the provenance and certainty machinery this completes)* | — | ✓ |
| BUG-421 | Wildcard imports keep `no-wildcard-imports` disabled, against the engineering contract | todo | C | — | — | ✓ Filed 2026-08-07 adopting the ktlint standard ruleset. 37 of 43 rules are enforced and five are disabled by name as deliberate, permanent exceptions; this is the **sixth, and the only one that is debt** — `docs/engineering-contract.md` asks for no wildcard imports, so the rule should be ON. 68 imports across 57 files, 27 of them `net.internetisalie.lunar.lang.psi.*`. ktlint cannot auto-fix it (expanding `import pkg.*` needs symbol-level resolution), and hand-editing risks changing what a name resolves to. Note `java.util.*` is explicitly allowed by the repo's own `ij_kotlin_packages_to_use_star_imports`, so part of the count is not a policy violation at all — deciding that is part of the fix. Route: IDE Optimize Imports per file, then delete the `.editorconfig` disable → `docs/features/bug-fixes/421-wildcard-imports-block-a-contract-rule/` |
| BUG-422 | `LuaInterpreterCommandLinesTest` PATH-prepend test is flaky under the full suite | todo | M | — | — | ✓ Found 2026-08-07 during the ktlint gate, at the worst possible moment — immediately after a 905-file reformat, where it read exactly like a real regression. It is not: three runs (green → **red** → green with *no* change between the last two), and the three files implementing the behaviour are **token-identical** pre/post reformat (whitespace and commas stripped). Suspected mechanism, marked as hypothesis not conclusion: `LuaLaunchEnvironment.applyPath` returns early on an empty `pathPrependDirs`, leaving the parent PATH; that list comes from the per-project-cached `LuaExecutionEnvironmentBuilder`, and the test's own `setUp` calls `invalidate()` — itself evidence stale state is reachable. Fix direction: **reproduce before patching**; another `invalidate()` would hide an ordering dependency rather than remove it. With **BUG-410** that is two independent flakes, which argues for test isolation generally rather than two patches → `docs/features/bug-fixes/422-interpreter-command-lines-path-test-flaky/` . **Hardened 2026-08-07, still OPEN**: the same stale-publish shape as BUG-410 was found in `LuaExecutionEnvironmentBuilder.pathPrependDirs()` — an unsynchronized read-compute-write whose stale write is *served*, because an empty list is non-null — and is now generation-guarded. Found by reading, **not** reproduced, unlike BUG-410; whether it removes the observed flake is unverified, so this stays open until the test runs clean over a meaningful number of full-suite runs or fails again |

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
| TARGET-09 | Addon auto-detection (turn TARGET-08 from capability into fix) | **planned** | S | TARGET-08 *(done)* | — | ✓ |
| TARGET-10 | `wx`/`wxstc`/`wxaui` definition libraries — investigate, then catalog | todo | C | — | MAINT-37 *(zerobrane member scope)* | ✓ |
| BUG-420 | A parameterized `@class` parent never resolves, so inheritance through a generic base is lost | todo | C | — | — | ✓ |
| MAINT-37 | Corpus sweeps run with pinned definition libraries | todo | S | **BUG-417** *(inspection independence — **done**; had to land first or the re-baseline would be unattributable)*; TARGET-08 *(done)* | — | ✓ |
| BUG-403 | Lunar hard-depends on the `glimmer/luacheck` fork without declaring or enforcing it | todo | S | — | — | ✓ |
| BUG-405 | OpenResty/NGX target emits no luacheck std although `ngx_lua` exists | todo | C | — | — | ✓ |

---

## Wave 22 — AI integration  *(AI epic)*

> **Deprioritised on 2026-08-06 — ordering only.** AI work does not precede quality work, so the epic's front-matter `priority` is `low`. The **Prio** column below is unchanged and deliberately so: MoSCoW records whether a feature is *required for its epic*, which is a different axis from *what gets picked up next*. AI-01 is still a `Must` for the AI epic — there is no AI epic without the MCP server — it is simply not scheduled while MAINT and BUG items remain open.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| AI-01 | MCP Server Integration | todo | M | `com.intellij.mcpServer` bundled plugin (optional dep) | AI-02, AI-03 | Serial: registration foundation |
| AI-02 | Semantic Context Toolset | todo | S | AI-01 *(lunar-mcp.xml infra)* | — | — |
| AI-03 | Debugger Toolset | todo | C | AI-01; **MAINT-24** *(done; debugger hardening — formerly "MobDebug hardening, unscheduled MAINT"; scoped 2026-07-17)*; REDIS-02 *(soft, LDB binding)* | — | — |
| AI-04 | LuaCATS Annotation Generator | todo | S | — *(type engine done)* | — | ✓ |
