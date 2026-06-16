---
id: "EXECUTION-ORDER"
title: "Backlog Execution Order"
type: "guide"
status: "done"
priority: "high"
folders:
  - "[[features]]"
---

# Backlog Execution Order

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
| INSP-04 | Unreachable code inspection | planned | M | ANALYSIS-06 (CFG) ✓ | — | ✓ |
| INSP-07 | Suspicious concatenation inspection | planned | S | TYPE-02 (types) ✓ | — | ✓ |
| INSP-03 | Type mismatch inspection | planned | S | TYPE-09 (unions) | — | ✓ |
| INSP-09 | Language level compliance | planned | M | — | — | ✓ |

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
| COMP-03 | Cross-file completion — finish last sub-req | in_progress | H | TYPE-07 *(soft — richer cross-file)* | — | ✓ 6/7 done; verified live + heavy/integration guards (wave6-readiness 2.1/2.2) |
| COMP-06 | Postfix completion templates | **partial** | S | — | — | `.if` built+tested; **`.not` (Must) NOT implemented** — design backfilled (grounded) |
| COMP-07 | Live templates | done | S | — | — | `fun`/`fori`/`forp`/`loc` shipped; design backfilled; req text needs naming reconcile (`fun`≠`func`) |
| COMP-08 | Block auto-complete (`function`→`end`) | done | S | — | — | `lang.completion.LuaEnterHandler` built+tested (+`repeat`→`until`); design backfilled; 2nd Enter handler is the complementary DOC one |

## Wave 7 — Formatting  *(high daily-use; serial cluster `LuaFormatBlock`/`LuaCodeStyleSettings`)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| FORMAT-03 | Blank-line management | planned | M | — | FORMAT-04 | Serial: formatter |
| FORMAT-04 | Expression wrapping | planned | M | FORMAT-03 *(files)* | FORMAT-05 | Serial: formatter |
| FORMAT-05 | Alignment logic | planned | M | FORMAT-04 *(files)* | FORMAT-06 | Serial: formatter |
| FORMAT-06 | Comment formatting | planned | M | FORMAT-05 *(files)* | — | Serial: formatter |

## Wave 8 — Refactoring & Intentions  *(core refactorings done in Waves 2–3; gap is intentions)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| INTENT (set) | Intentions: string conversion, invert-`if`, name suggestion, … | planned | M/S | — | — | ✓ specs pending; each a new `IntentionAction` |

## Wave 9 — Quick wins & differentiators

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| DOC-06 | Full-text search in documentation indexing | planned | S | — | — | ✓ last DOC item |
| RUN-03 | Interactive console (REPL) | planned | L | — | — | ✓ new package — differentiator |
| SYNTAX-07 (tail) | Remaining inlay-hint sub-items | in_progress | L | — | — | ✓ |
| SYNTAX (rest) | Method separators (cosmetic), lexer optimization (perf) | planned | L | — | — | ✓ low-value |
| — | Lua 5.5 support | deferred | — | language unreleased | — | — |

## Wave 10 — New feature areas  *(two independent tracks — run A and B concurrently)*

**Track A — Tool inventory** (auto-discovery of lua / luacheck / luarocks):

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TOOL-00 | De-risking spikes | planned | H | — | TOOL-01, TOOL-02 | ✓ spikes |
| TOOL-01 | Tool registry & discovery | planned | H | TOOL-00 | TOOL-03 | ✓ |
| TOOL-02 | Project binding & env | planned | H | TOOL-00 | TOOL-03 | ✓ |
| TOOL-03 | UI & health monitoring | planned | H | **TOOL-01, TOOL-02** | — | ✓ |

**Track B — LuaRocks** (largest effort; shares `LuaRocksSettings` defined in ROCKS-04):

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| ROCKS-04 | Task execution & run configs | planned | H | — (defines `LuaRocksSettings`) | ROCKS-02, ROCKS-03 | ✓ new package |
| ROCKS-03 | Dependency resolution | planned | H | ROCKS-04 | — | ✓ |
| ROCKS-02 | Package browser | planned | M | ROCKS-04 | — | ✓ |
| ROCKS-01 | Project initialization | planned | H | — | — | ✓ standalone wizard |

## Wave 11 — Internal & maintenance  *(invisible to users; address opportunistically)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| BUG (set) | Edge cases: union inlay hints, `@return` parsing, flaky tests | planned | M/S | — | — | ✓ opportunistic |
| MAINT (set) | Kotlin conversion, legacy annotator removal, deprecation cleanup | planned | M/S | — | — | mixed |

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
COMP-03 ── finish last cross-file sub-req (in_progress)
FORMAT-03 ─(files)─▶ FORMAT-04 ─▶ FORMAT-05 ─▶ FORMAT-06   (serial cluster)
ROCKS-04 (LuaRocksSettings) ──▶ ROCKS-02, ROCKS-03
TOOL-00 ──▶ TOOL-01, TOOL-02 ──▶ TOOL-03
```
Everything else is independent and can start as soon as its wave is reached.

## Parallelization guidance for multiple agents

- **Safe to run concurrently** (separate worktrees): any **Parallel ✓** items whose deps are
  done — e.g. all of Wave 4's inspections are independent new `<localInspection>`s; Wave 10's TOOL
  Track A and ROCKS Track B are fully independent.
- **Keep one agent per "Serial" cluster** at a time: the **type engine** (Wave 5, TYPE-07/TYPE-09 —
  shared `LuaTypeGraph`/`LuaTypesVisitor`/`LuaTypeManagerImpl`) and the **formatter** (Wave 7,
  FORMAT-03..06, shared `LuaFormatBlock`). Concurrent edits in either will conflict.
- A reasonable concurrent split for Waves 4+: Agent 1 = Wave 4 inspections, Agent 2 = Wave 5 type
  engine (serial within itself), Agent 3 = Wave 6 completion polish; the Wave 7 formatter is its own
  serial cluster. Waves 8–11 slot in as capacity frees; TOOL and ROCKS (Wave 10) are independent.

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
