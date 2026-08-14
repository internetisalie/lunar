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
| BUG-421 | Wildcard imports keep `no-wildcard-imports` disabled, against the engineering contract | todo | C | — | — | ✓ |
| BUG-422 | `LuaInterpreterCommandLinesTest` PATH-prepend test is flaky under the full suite | todo | M | — | **recurred 2026-08-12** during COMP-09 Phase 4 remediation: `testForProjectResolvesRuntimeAndAppliesEnvironment` failed once (`expected the runtime dir prepended to PATH`) then passed on two further full runs over identical inputs. No causal path from that change — consistent with the PATH/env caching this row already suspects | ✓ |
| BUG-428 | A `nil` arm from `and`/`or` is reported as a certain nil instead of optionality | todo | S | — | — | ✓ |
| BUG-430 | `a.b.c = v` leaves `a.b` empty — a two-segment receiver offers NOTHING | todo | **M** | — | **re-measured 2026-08-14 (implement-bug): the bug moved and the report's fix strategy is stale.** Defect 1 (the hoist) no longer reaches the user — COMP-09's index arm answers `Foo.` and correctly excludes `baz`, though the type graph still carries it. Live defect is `Foo.bar.` offering **`[]`**, from two gaps both in COMP-09's path, neither in the type engine: **G-1** `LuaReceiverMemberIndex.dottedTarget:368` `varSuffixList.singleOrNull()` records `Foo.bar.baz = 1` under no key at all; **G-2** `LuaCompletionContributor.addIndexedGlobalMembers:181` `bareNameOf(...)` is null for `Foo.bar`, so fixing G-1 alone changes nothing visible. Constrained by DR-09's superset finding + COMP-09-06 ("if any baseline moves, stop") and grows the key space DR-19 sized. **Same area as BUG-439 — fix together or the second re-opens the first.** Reproduction parked (5 tests, 2 red, incl. a control) | ✓ |
| BUG-436 | Five whole-project indexers accept only `*.lua`, so `.rockspec` / `.luacheckrc` / `.busted` declarations are invisible | todo | M | — | found by COMP-09 Phase 3 review. `LuaFileType` is registered for `extensions="lua;rockspec"` + `fileNames=".luacheckrc;.busted"` (`plugin.xml:99-100`) and every index re-states it as `file.extension == "lua"`. `LuaGlobalAssignmentIndex` is load-bearing (completion cannot see a rockspec-declared global at all); `LuaCatsTypeNameIndex` hides a bare `---@class` from Go to Class. `LuaReceiverMemberIndex` is already fixed in `fcce5966` — copy that shape, and **instantiate** `DefaultFileTypeSpecificInputFilter`, never subclass it (`RequiredIndexesEvaluator.toHint` checks `javaClass ==`, so a subclass degrades to accept-everything). Every existing gate is superset-shaped and blind to this | ✓ |
| BUG-435 | Inside `if type(x) == "table"`, the narrowed variable offers **no members at all** | todo | M | — | found by COMP-09 Phase 2's TC 10j. `local Shadow = { fromLocal = 1 }` offers `[fromLocal]` at file scope and `[else, elseif, end]` inside the guard. **Proven pre-existing** — measured byte-identical on a detached worktree at `fb79c038`, before Phase 2 landed. Suspect `LuaTypesVisitor.kt:462` re-declares the name with the guard's memberless `table` type over the literal's inferred one; confirm by reading the installed node, do not assume | ✓ |
| BUG-437 | `LuaReceiverMemberIndex.Indexer.map` walks the file five times where one walk would do | todo | C | — | found by COMP-09 Phase 5 (DR-25), measured not assumed. **Five** traversal call sites over three element types, three of them the identical `LuaAssignmentStatement` walk; one shared `processElements` walk costs the same as one `findChildrenOfType` (10 016 µs vs 10 767), so ~**40 ms of the index's 67 ms** per-file cost is redundant. One-off, persisted, on no latency path — which is why it was deferred. **Re-measurement trap, written down because it cost a run**: the first `findChildrenOfType` caller pays the file's whole AST expansion (`FileContentImpl` defers it past `getPsiFile()` to first tree access), so timing `map` first reports 256 ms and third reports 67 ms for identical code. Expansion-free the three indexers are 67 / 20 / 6 ms, confirming DR-18's warm 61 / 20 / 6 | ✓ |
| BUG-438 | The `@class` completion door misses the 100 ms time-to-first budget — 323 ms at 3 600 members | todo | M | — | found by COMP-09 Phase 5 (DR-29). First cold medians-of-five figure this door has ever had: **269 459 µs** for `resolveType` + `materialize` + `getMembers`, **322 692 µs** end to end through `completeBasic()` on `---@type Klass` + `v.`. **Not a regression and no improvement claimed** — no prior figure exists — and **not** COMP-09-08's gate, which is on the completion door and measures 12 225 µs. Ruled out as the cause: the two remaining COMP-09-02 walk sites are 29 ms of the 269 ms, and narrowing invalidation cannot help a **first** build. Bucket the door before proposing anything, and remember no ratio between two figures of one harness is quotable | ✓ |
| BUG-439 | A global's members declared in a **sibling file** are never offered — love2d's whole submodule API is unreachable | todo | **M** | — | **attempt 1 reverted 2026-08-14.** The two-line union works (`membershipOver` unions declaring files; `candidates` must ALSO consult this index's own key space, because `LuaGlobalAssignmentIndex` lists only files that assign the *global*, so a file that merely extends it was never a candidate — the union alone is a no-op). It then fails **six** tests that are the design: COMP-09's one-file work bound (§4.10b assertion 4), **DR-09's superset guard**, the pinned first-declaring-file rule, door parity, and the golden. A conditional union — same library root, or only when the first file binds the receiver to a table — is the shape to explore, and it needs COMP-09-08's latency harness re-run. **Regression-vs-pre-existing is now ANSWERED: pre-existing**, pinned as intended behaviour by those tests | ✓ |
| BUG-441 | Unknowns are omitted rather than represented — an unknown write vanishes instead of widening | todo | **L** | — | carved out of BUG-419 (closed 2026-08-14). **Attempt 1 implemented and REVERTED the same day** — read the report before starting, it invalidates three of this item's founding premises. Measured: representing the unknown works (`d` infers `any \| string`) and does **not** change the diagnostic, because `checkTypes` checks each reaching definition against the demand *on its own* and never consults the merged type; `certain` gates only the `Nil` branch, so the report's certainty story is wrong; and a provenance flag threaded through the fixpoint loop never fires because the emissions come from `addEdge`/`propagate*` at graph-**construction** time (instrumented: loop says `unknownProvenance=true`, every `REPORT` says `false`). The type-algebra framing targets a layer the diagnostics do not read. **Wants a `plan-feature` pass, not a bug fix** — small payoff, large change | ✓ |
| BUG-440 | Quick Doc returns "No documentation found" for openresty members, while love2d works | todo | S | — | found by COMP-09's live verification while judging checklist 2.3. `Ctrl+Q` gives love2d's full signature and **nothing** for every `ngx.*` tried, including a real `---@field` and a real `function`. Inlays are degraded on the same members (`nil | string`, `fun(...)`), so **suspect resolution, not the doc provider** — measure that before touching any provider. Matters because COMP-09's no-type-text trade was accepted *on the grounds that one keystroke restores the signature*. Blast radius unmeasured (n=2) | ✓ |

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
| TARGET-10 | `wx`/`wxstc`/`wxaui` definition libraries — investigate, then catalog | **in_progress** | C | — | MAINT-37 *(zerobrane member scope)* | ✓ |
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
