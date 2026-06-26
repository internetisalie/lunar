---
id: "ROADMAP"
title: "Project Roadmap"
type: "guide"
status: "done"
priority: "high"
folders:
  - "[[features]]"
---

# Project Roadmap

## Taxonomy
- **Wave**: A sequential phase of execution.
- **Epic**: A high-level grouping (e.g., `COMP`).
- **Feature**: The deliverable unit (e.g., `COMP-01`).
- **Story**: An atomic task within a Feature.

> **This doc's durable value is the ordering and dependency edges — not the `Status` column.**
> Live per-feature status lives in each feature's `requirements.md` front-matter, aggregated into
> [status.md](status.md) by `scripts/gen_status.py`; that is canonical, so treat the `Status`
> columns here as advisory and possibly stale. Wave-level priorities last revised 2026-06-15.

A dependency-aware sequencing of every **executable** feature (status `planned` or
`in_progress`) so implementation agents can pull work in a safe, high-leverage order. This is
**not** a priority sort — ordering precedence is:

1. **Hard dependencies** (topological — an item is only *ready* when all its `Depends on` are done).
2. **Finish in-flight work** (partly-built features lead — low risk, they unblock consumers).
3. **Lead theme (Waves 4+) — close the biggest competitive gaps first**: Inspections →
   Type-system hardening → Completion polish → Formatting → Refactoring/Intentions → quick wins
   (DOC/RUN) → new areas (TOOL/ROCKS) → internal maintenance (BUG/MAINT).
4. **Priority (MoSCoW / front-matter)** as the within-wave tie-break.
5. **DoD gate (learned the hard way):** a feature that surfaces through a platform extension point
   (inspection, annotator, completion, refactoring, safe-delete) is only "done" when a **real-flow**
   test drives that machinery — `myFixture.enableInspections(...) + doHighlighting()`,
   `completeBasic()`, `SafeDeleteHandler.invoke`, etc. — and asserts the user-visible result.
   Snapshot/engine-only tests gave false confidence and hid a real REFACT-03 bug.

## How an agent uses this

- **Pick the lowest-numbered wave with a *ready* item** — ready = every `Depends on` entry is
  `done`. Within a wave, prefer higher priority.
- **Parallelism**: items marked **Parallel ✓** add new files / a distinct extension point and
  can run concurrently in separate worktrees. Items marked **Serial: <cluster>** mutate a shared
  hot file (the type engine, `LuaFormatBlock`, …) and must be done one at a time within that
  cluster.
- **Update `status` to `done`** in the feature's `requirements.md` as you finish; that makes its
  dependents *ready*. Re-run `scripts/gen_status.py`.
- Some items are partly built (`in_progress`) — read the design's "Current implementation status"
  note first; the remaining work is scoped there.

---

## Wave 0 — Finish the in-flight type engine  *(serial — shared `LuaTypeGraph`/`LuaTypesVisitor`/`LuaTypeManagerImpl`)*

These are partly built and unblock the entire lead theme. Do them first, in this order.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TYPE-09-P0 | Union: De-risking spikes | done | H | — | TYPE-09-P2 | ✓ (throwaway spikes) |
| TYPE-09-P1 | Union: Infra & Flattening | done | H | — | TYPE-09-P3/P4 | Serial: type-engine |
| TYPE-09-P2 | Union: Compatibility (limits+memo) | done | H | TYPE-09-P0 | TYPE-09-P3/P4 | Serial: type-engine |
| TYPE-09-P3 | Union: Error reporting | done | H | TYPE-09-P2 | TYPE-09-P4 | Serial: type-engine |
| TYPE-09-P4 | Union: Verification & perf | done | H | P1, P2, P3 | — | Serial: type-engine |
| COMP-04 | Type-inferred completion (engine: self/`__index`) | done | H | — | SYNTAX-17, NAV-05/06 richness | Serial: type-engine, then ✓ (provider) |
| TYPE-02 | Class/Table defs (implicit fields) | done | H | — | NAV-05/06, COMP-04 richness | Serial: type-engine |

> The TYPE-09 phases are the sub-stories of the union epic; P0 (spikes) informs P2 (the limits),
> and P4 verifies P1–P3. COMP-04 and TYPE-02 cores already exist — only the named deltas remain.

## Wave 1 — Type-system intelligence  *(lead theme — mostly new files, parallel-safe)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| INSP-01 | Undeclared-variable inspection | done | M | — (uses existing `resolve`) | — | ✓ new inspection |
| COMP-03-03 | Auto-import completion | done | H | COMP-03 (cross-file baseline) | — | ✓ |
| SYNTAX-07-11 | Inlay: large-file threshold | done | M | — | SYNTAX-07-07 | ✓ inlay infra |
| SYNTAX-07-07 | Inlay: method-chaining hints | done | M | SYNTAX-07-11 | — | ✓ |
| SYNTAX-17 | Inferred-type highlighting | done | L | COMP-04 (receiver helper) | — | ✓ new annotator |
| NAV-05 | Method override markers | done | M | TYPE-02 *(soft — richer)* | — | ✓ new marker |
| NAV-06 | Type hierarchy view | done | M | TYPE-02 *(soft)* | — | ✓ new hierarchy |

## Wave 2 — Navigation & references core  *(parallel-safe)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| NAV-02 | Find usages | done | M | — | NAV-10, REFACT-03 | ✓ (small edit to find-usages provider) |
| NAV-03 | Go to class/file/symbol | done | M | — | — | ✓ new contributors |
| NAV-09 | Return highlighter | done | M | — | — | ✓ new handler |

## Wave 3 — Navigation dependents & refactoring

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| NAV-10 | Read/Write access detector | done | M | **NAV-02** | — | ✓ new detector |
| REFACT-02 | Introduce variable | done | M | — | REFACT-03 | ✓ new handler (consolidates the refactoring provider) |
| REFACT-03 | Safe delete | done | M | **NAV-02, REFACT-02** | — | ✓ new processor |

> **Waves 4+ are the re-prioritized backlog** (2026-06-15). Some feature-level specs (notably the
> new INSP/INTENT items) are being finalized by the planning agent; where an exact `ID` isn't yet
> minted, the row names the work and notes "spec pending". Confirm the ID against `requirements.md`
> before starting. The dependency edges and ordering are the durable part.

## Wave 4 — Inspections  *(Sprint 1 — close the biggest competitive gap: 4 inspections vs 13–25+ in peers)*

Three of these already have infrastructure (`in_progress`) — the work is wiring the analysis logic,
not green-field. Each is a new/extended `<localInspection>` → parallel-safe.
**DoD gate (precedence #5):** ships only with a *real-flow* test —
`myFixture.enableInspections(<TheInspection>()) + doHighlighting()` asserting the warning at the right
range — never a `LuaTypesSnapshot.forFile(...).getErrors()`-only check (that pattern hid the REFACT-03
bug and gave the type inspections false confidence until this session's coverage was added).

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| INSP-05 | Global-creation inspection | done | M | — | — | ✓ wire analysis logic |
| INSP-06 | Variable-shadowing inspection | done | M | — | — | ✓ |
| INSP-08 | Deprecated-usage inspection | done | M | — | — | ✓ |
| INSP-02 | Unused local / parameter (must-have) | done | M | NAV-02 (usages) ✓ | — | ✓ |
| INSP-04 | Unreachable code inspection | done | M | ANALYSIS-06 (CFG) ✓ | — | ✓ |
| INSP-07 | Suspicious concatenation inspection | done | S | TYPE-02 (types) ✓ | — | ✓ |
| INSP-03 | Type mismatch inspection | done | S | TYPE-09 (unions) | — | ✓ |
| INSP-09 | Language level compliance | done | M | — | — | ✓ |

## Wave 5 — Type-system hardening  *(Sprint 2 — powers completion/inspections/hints; serial cluster: the type engine)*

> **Correction (2026-06-15):** an earlier pass dropped this on the strength of the TYPE
> *front-matter* (`done`). That was wrong — the TYPE-09 doc **body** says `in_progress`, and a source
> read agrees: in-file `@type` injection works (`LuaTypeGraphBridge.injectTypeAnnotation`), but there
> is **no cross-file `require`→stub type flow** in `LuaTypeManagerImpl` (TYPE-07-03/04), and the
> engine carries live simplifications — a nested-generics TODO (`LuaTypeGraphBridge`) and several
> "keep it simple for now" union/structural shortcuts (`LuaTypeGraph`). The single-file behaviors
> verified this session (inspections, implicit-field completion, one union diagnostic) do **not**
> cover these. The front-matter is the stale outlier; trust the source-verified status.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TYPE-07 | External-API stubs — cross-file `require`→stub resolution + stub type injection (TYPE-07-03/04) | done | S | — | cross-file accuracy for COMP / INSP / hints | Serial: type-engine |
| TYPE-09 | Union distribution hardening — canonicalization limits + memoization (P2), member-specific diagnostics (P3), de-risking spikes (P0), verification & perf (P4) | done | H | — | diagnostic quality | Serial: type-engine |

## Wave 6 — Completion polish  *(parallel-safe; user-visible)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| COMP-03 | Cross-file completion | done | H | TYPE-07 *(soft — richer cross-file)* | — | 7/7; recursive/transitive resolution + cycle guard in `LuaCrossFileCompletionProvider`, verified by heavy + integration guards (Wave 6 readiness 2.1/2.2) |
| COMP-06 | Postfix completion templates | done | S | — | — | 11 templates shipped (was `.if` only): Must `.not`/`.var`/`.for`/`.forp`/`.fori` + Should `.ifnot`/`.nil`/`.notnil`/`.return`/`.print`; shared `LuaExprSelector`. Surfaced+fixed a unary-`not` formatter bug (`not x` → `notx`). Could/Watch parked |
| COMP-07 | Live templates | done | S | — | — | 16 templates shipped (was 4): +4 Must (`if`/`ifel`/`lfun`/`while`) +4 Should insertion (`repeat`/`forip`/`req`/`mod`) +4 surround; **`LuaCodeContextType` fixes the strings/comments/numbers defect**. `LuaIfContextType`/`elseif` parked (Could) |
| COMP-08 | Block auto-complete | done | S | — | — | base hardened: **balance-check bug fix (no redundant `end`/`until`/`}`)** + full opener coverage incl. table `{}` + between-pair indent (`LuaEnterBetweenBlockHandler`) + stateless reformat; shared `LuaBlockPairs` (brace matcher left intact per design §2.3) |

## Wave 7 — Formatting  *(high daily-use; serial cluster `LuaFormatBlock`/`LuaCodeStyleSettings`)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| FORMAT-03 | Blank-line management | done | M | — | FORMAT-04 | settings-driven function spacing + keep-max + `LuaTrailingNewlinePostProcessor` (whole-file EOF newline) |
| FORMAT-04 | Expression wrapping | done | M | FORMAT-03 *(files)* | FORMAT-05 | `WRAP_ARGUMENTS`/`WRAP_TABLE_CONSTRUCTOR` shared `Wrap`s on arg/field item lists |
| FORMAT-05 | Alignment logic | done | M | FORMAT-04 *(files)* | FORMAT-06 | `ALIGN_CONSECUTIVE_ASSIGNMENTS`/`ALIGN_TABLE_FIELDS` thread `Alignment` onto `=` (default off) |
| FORMAT-06 | Comment formatting | done | M | FORMAT-05 *(files)* | — | `LuaCommentWrapPostProcessor` hard-wraps long `--` lines (opt-in); doc comments untouched |

## Wave 8 — Refactoring & Intentions  *(core refactorings done in Waves 2–3; gap is intentions)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| INTENT-01 | String quote conversion intention (`'…'` ↔ `"…"` ↔ `[[…]]`) | done | S | — | — | `LuaStringConversionIntention`; decode/re-encode via `LuaLiterals`, `[=[` level-raising |
| INTENT-02 | Invert-`if` intention (negate condition + swap `then`/`else`) | done | S | — | — | `LuaInvertIfIntention` + `LuaConditionInverter`; `LuaIfStatement`/`LuaBinOpExpr` rebuild |
| INTENT-03 | Variable name suggestion (`getUser()` → `user`) | done | S | REFACT-02 *(extends `LuaIntroduceVariableHandler`)* | — | `LuaNameSuggestionProvider` + shared `LuaNameDeriver` (IntroduceVariable now prefix-strips) |
| REFACT-05 | Rename names validator (keyword + identifier checks) | done | M | — | — | `LuaNamesValidator`; `LuaKeywords.RESERVED` + ASCII identifier regex |
| REFACT-06 | Create-from-usage intentions (local var / function) | done | S | INSP-01 *(undeclared-var overlap)* | — | `LuaCreate{LocalVariable,Function}Intention`; shared `LuaUndeclaredNames` extracted from the inspection |

## Wave 9 — Quick wins & differentiators

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| DOC-06 | Documentation indexing (stub index + type map) | done | S | — | — | already complete — the "full-text search" label was stale; DOC epic 100% |
| RUN-01 | Lua Interpreter SDK | done | M | — | RUN-02, RUN-03 | ✓ |
| RUN-02 | Run Configurations | done | M | RUN-01 | RUN-04, RUN-05 | ✓ |
| RUN-04 | Run Configuration Validation | done | M | RUN-02 | — | ✓ |
| RUN-03 | Interactive console (REPL) | done | L | — | — | new `run/console/` pkg: `LuaConsoleRunner`/`View`/`ExecuteHandler` + `LuaChunkCompletion` trial-parse multi-line + history; live REPL behavior pending a VNC pass |
| SYNTAX-07 (tail) | Remaining inlay-hint sub-items | done | L | — | — | 07-07/09/11 were already implemented in code; reconciled stale doc rows |
| SYNTAX-05 / SYNTAX-15 | Method separators / lexer optimization | done | L | — | — | SYNTAX-05 `LuaMethodSeparatorProvider`; SYNTAX-15 already satisfied (lexer is state-based — no churn) |

## Wave 10 — New feature areas  *(two independent tracks — run A and B concurrently)*

> **✅ COMPLETE (2026-06-16).** Both tracks shipped and merged to `main`; all 9 features `done`
> (TOOL 4/4, ROCKS 5/5). Implemented via two parallel worktrees with model-triaged subagents; the
> integrated suite is green (1064 tests). Live/VNC verification of UI-heavy gates (per-shell terminal
> PATH, Swing settings/tool-windows, project wizard, live `luarocks upload`) remains the only
> outstanding manual item — see the `Partial / manual-verification` notes in the feature docs.

> **Grounding-audited 2026-06-16** — readiness per feature below; full findings + fixes in
> [planning-gaps.md](planning-gaps.md#wave-10-grounding-audit-2026-06-16). **Two shared pre-reqs:**
> (a) add `LuaIcons.ROCKET` (blocks all ROCKS plugin.xml/icon code; one line — `FILE` already maps
> to `rocket_16.png`); (b) rewrite the **terminal PATH-injection API** in TOOL-00-01/TOOL-02 to the
> real 2026.1 `ShellExecOptionsCustomizer` (the named `TerminalCustomizer`/`initCommands`/
> `LocalTerminalDirectRunner.EpExtension` don't exist).

**Track A — Tool inventory** (auto-discovery of lua / luacheck / luarocks):

| ID | Title | Status | Readiness | Depends on | Unblocks |
| :--- | :--- | :--- | :--- | :--- | :--- |
| TOOL-00 | De-risking spikes | done | **fix-first**: correct the 00-01 terminal spike to `ShellExecOptionsCustomizer` | — | TOOL-01, TOOL-02 |
| TOOL-01 | Tool registry & discovery | done | **READY** (mirrors `LuaInterpreter*`/settings) | TOOL-00 | TOOL-03 |
| TOOL-02 | Project binding & env | done | **fix-first**: terminal API + no `EnvironmentProvider` iface (use `RunConfigurationExtension`/direct cmdline env) + reuse `LuaSettingsChangedListener.TOPIC` + dedupe `LuaTerminalEnvironmentService` | TOOL-00 | TOOL-03 |
| TOOL-03 | UI & health monitoring | done | **READY** (most grounded; adds 2 fields to TOOL-01 `LuaTool`) | **TOOL-01, TOOL-02** | — |

**Track B — LuaRocks** (largest effort; shares `LuaRocksSettings` defined in ROCKS-04):

| ID | Title | Status | Readiness | Depends on | Unblocks |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ROCKS-04 | Task execution & run configs | done | **READY** after `LuaIcons.ROCKET`; defines `LuaRocksSettings` (clones `LuaRunConfiguration`/`LuaCheckSettings`) | — | ROCKS-02, ROCKS-03 |
| ROCKS-03 | Dependency resolution | done | **fix-first**: drop stale `export.lua` task (already fixed); gate `src/main/lua`→`resources/lua` relocation | ROCKS-04 *(soft — uses interpreter, not luarocks)* | — |
| ROCKS-02 | Package browser | done | **READY** after `LuaIcons.ROCKET`; add porcelain-format/network risk note | ROCKS-04 | — |
| ROCKS-01 | Project initialization | done | **READY** after `LuaIcons.ROCKET` (standalone; `DirectoryProjectGenerator`) | — | — |
| ROCKS-08 | Publishing (Could) | done | **fix-first**: 9-line stub — rename pkg `lang.rocks`→`rocks.publish`, `PasswordSafe` key, `<action>` reg, reuse `LuaRocksSettings`; defer to last | — | — |

## Wave 11 — Backlog & Future Enhancements *(parallel-safe; deferred or unprioritized)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| COMP-05 | Parameter Name Hints | done | S | — | — | ✓ |
| RUN-05 | Test Runner Integration | done | S | RUN-02 *(Run Configs)* | — | ✓ |
| FORMAT-07 | Stylua Compatibility | done | S | FORMAT-03..06 *(Formatter)* | — | Serial: formatter |
| TYPE-08 | Flow-Sensitive Analysis | done | C | TYPE-01 *(Type engine)* | — | ✓ |
| DOC-06-04 | Full-Text Documentation Search | done | C | DOC-06-01 *(Stub Indexing)* | — | ✓ |
| SYNTAX-09 | Lua 5.5 Support | done | C | — | — | ✓ |

## Wave 12 — Internal & maintenance  *(invisible to users; address opportunistically)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-134 | `@return` Comma Parsing | done | H | — | — | ✓ |
| BUG-132 | Duplicate Problems Reporting | done | M | — | — | ✓ |
| BUG-133 | Union Inlay Hints (OR) | done | M | — | — | ✓ |
| BUG-135 | Stdlib Inlay Hints | done | M | — | — | ✓ |
| BUG-349 | Flaky Inlay Hint Tests | done | M | — | — | ✓ |
| MAINT-XX| Test Coverage Improvement | todo | H | — | — | ✓ |
| MAINT-01| Kotlin Conversion | in_progress | M | — | — | ✓ |
| MAINT-02| Label Refactoring | done | M | — | — | ✓ |
| MAINT-06| LuaCATS Literal Highlighting | todo | M | — | — | ✓ |
| MAINT-07| Interpreter Search Path Globs | todo | M | — | — | ✓ |
| MAINT-03| Deprecation Cleanup | todo | L | — | — | ✓ |
| MAINT-08| LuaCheck UI Grouping | todo | L | — | — | ✓ |
| MAINT-15| Remove Legacy Annotators | todo | L | — | — | ✓ |

## Wave 13 — LuaRocks: multi-rock workspaces & environment  *(reopened ROCKS epic; parallel-safe except the discovery foundation)*

> **✅ COMPLETE (2026-06-26).** All 6 features shipped and merged to `main` (ROCKS-05, ROCKS-06, ROCKS-09,
> ROCKS-10, ROCKS-11, ROCKS-12). Multi-rock workspace discovery and environment integration is complete,
> including topo-sorted build orchestration, source-root and external-library marking, module resolution,
> and Makefile task integration. All features verified green in the automated suite and live-tested.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| ROCKS-09 | Multi-Rock Workspace Discovery | done | M | — | ROCKS-05, ROCKS-10, ROCKS-12 | ✓ 2026-06-25: `LuaRockspecDiscoveryService` (index-backed, cached, exclusion-aware), `resolveAll` forest, `workspace.lua` removal, glob-override globs. Threading bug (read-action split) found+fixed via VNC. Deferred: bridge cache, settings UI, forest grouping. |
| ROCKS-05 | Rockspec Module Resolution (+ run/debug `LUA_PATH`) | done | S | ROCKS-09 | ROCKS-12 | ✓ 2026-06-25: `RockspecSourcePathProvider` feeds `PathConfiguration`; `LUA_PATH` and `LUA_CPATH` built from derived roots directly without blocking EDT. Fixed an infinite recursion cycle with file-index during implementation. |
| ROCKS-06 | Project LuaRocks Environment | done | M | TOOL-02 *(executable binding)* | — *(redefines 02/03/08 server-awareness)* | ✓ 2026-06-25: `LuaRocksEnvironment` (resolver, `withServer` global-flag prepend), per-server `LuaRocksApiKeyStore`, `LuaRocksSettingsConfigurable`, project `rocksServerUrl` override. VNC verified: settings UI, server resolution precedence, TOOL-02 binding, fallback. Deferred: publish/credential VNC (headless TC 7/8 green). |
| ROCKS-10 | Workspace Build Orchestration (dep order) | done | M | ROCKS-09, ROCKS-03, ROCKS-04 | — | ✓ 2026-06-25: Graph/topo-sort, orchestrator, sequential runner reusing ROCKS-04, and UI registration. |
| ROCKS-11 | Makefile Task Integration | done | C | ROCKS-01; opt. `name.kropp.intellij.makefile` *(spike)* | — | ✓ 2026-06-26: Makefile template enriched. Optional plugin dependency added and verified over VNC. |
| ROCKS-12 | Project-View Roots & Marking | done | M | ROCKS-05, ROCKS-09 | — | ✓ 2026-06-26: `LuaRocksLibraryProvider` (Piece A) and `LuaRockSourceRootDecorator` (Piece B). Handled Platform index recursion bug without crashes. |

> Dropped: **ROCKS-07** (custom luarocks task panel) — redundant against Make + Lunar's native
> format/lint/coverage/test integrations; the Makefile (ROCKS-11) is the task aggregator.

## Wave 14 — Schema-Driven Data Files  *(SCHEMA epic; engine is serial, providers are parallel)*

> The **SCHEMA epic was planned (2026-06-24)** to deliver JSON-Schema-driven validation, completion,
> and documentation by adapting the platform's JSON Schema engine to the Lua PSI. This avoids
> duplicate hand-rolled validators. SCHEMA-01 delivers the engine, while SCHEMA-02..04 are the
> declarative schema providers mapping specific file patterns. SCHEMA-02 supersedes the standalone
> ROCKS-13.

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| SCHEMA-01 | Lua JSON-Schema Engine | planned | M | — | SCHEMA-02, SCHEMA-03, SCHEMA-04 | Serial: schema-engine |
| SCHEMA-02 | Rockspec Schema Provider | todo | S | SCHEMA-01 | — | ✓ |
| SCHEMA-03 | Luacheckrc Schema Provider | todo | S | SCHEMA-01 | — | ✓ |
| SCHEMA-04 | Busted Config Schema Provider | todo | C | SCHEMA-01 | — | ✓ |

---

## Dependency summary (the hard edges)

Historical (Waves 0–3, all done): `COMP-04→SYNTAX-17`; `TYPE-02 ┄soft┄▶ NAV-05/06`;
`NAV-02→NAV-10`; `NAV-02,REFACT-02→REFACT-03`. (NB: TYPE-09 phases P0–P4 and TYPE-07 are **not**
done — see Wave 5.)

Active edges (Waves 4+):
```
TYPE-07, TYPE-09 (P0–P4)  ── type engine, SERIAL (one agent at a time), in_progress
TYPE-07 ┄soft┄▶ COMP-03   (richer cross-file completion accuracy)
INSP-02 ──depends──▶ NAV-02 (done, so INSP-02 is ready)
COMP-03 ── cross-file completion incl. recursive resolution (done) — Wave 6 complete
FORMAT-03 ─(files)─▶ FORMAT-04 ─▶ FORMAT-05 ─▶ FORMAT-06   (serial cluster)
ROCKS-04 (LuaRocksSettings) ──▶ ROCKS-02, ROCKS-03
ROCKS-09 (done 2026-06-25) ──▶ ROCKS-05, ROCKS-10, ROCKS-12  (Wave 13 — unblocked)
TOOL-00 ──▶ TOOL-01, TOOL-02 ──▶ TOOL-03
SCHEMA-01 ──▶ SCHEMA-02, SCHEMA-03, SCHEMA-04
```
Everything else is independent and can start as soon as its wave is reached.

## Parallelization guidance for multiple agents

- **Safe to run concurrently** (separate worktrees): any **Parallel ✓** items whose deps are
  done — e.g. all of Wave 4's inspections are independent new `<localInspection>`s; Wave 10's TOOL
  Track A and ROCKS Track B are fully independent.
- **Keep one agent per "Serial" cluster** at a time: the **type engine** (Wave 5, TYPE-07/TYPE-09 —
  shared `LuaTypeGraph`/`LuaTypesVisitor`/`LuaTypeManagerImpl`), the **formatter** (Wave 7,
  FORMAT-03..06, shared `LuaFormatBlock`), and the **schema engine** (Wave 14, SCHEMA-01, shared
  `LuaJsonLikePsiWalker` and adapters). Concurrent edits in any of these will conflict.
- A reasonable concurrent split for Waves 4+: Agent 1 = Wave 4 inspections, Agent 2 = Wave 5 type
  engine (serial within itself), Agent 3 = Wave 6 completion polish; the Wave 7 formatter is its own
  serial cluster. Waves 8–12 slot in as capacity frees; TOOL, ROCKS (Wave 10), and SCHEMA (Wave 14) are independent.

## Maintenance

- This is a point-in-time plan (created 2026-06-13; status refreshed 2026-06-14; **Waves 4+
  re-prioritized 2026-06-15** from the planning agent's source-verified epic assessment). Note the
  TYPE-07/TYPE-09 front-matter currently reads `done` but is source-verified `in_progress` (Wave 5) —
  that front-matter should be corrected to match. When a feature reaches `done`, mark it in its
  `requirements.md`, re-run
  `scripts/gen_status.py`, and its dependents become ready. Treat the status column here as
  advisory — `status.md` is canonical.
- If cross-epic priorities change, re-order the waves; the **Depends on** column is the
  invariant that must always hold.
- See [planning-gaps.md](planning-gaps.md) for the original audit and
  [planning-handoff.md](planning-handoff.md) for the planning brief that produced these specs.
