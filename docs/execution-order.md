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

> **Status is a point-in-time snapshot** (last refreshed 2026-06-14). The authoritative, live
> per-feature status is each feature's `requirements.md` front-matter, aggregated into
> [status.md](status.md) by `scripts/gen_status.py`. As of this refresh: **Waves 0–2 are `done`**;
> **Wave 3 is in progress** (NAV-10 + REFACT-02 done; REFACT-03 next). This doc's value is the
> *ordering and dependency edges*, which remain valid regardless of status drift.

A dependency-aware sequencing of every **executable** feature (status `planned` or
`in_progress`) so implementation agents can pull work in a safe, high-leverage order. This is
**not** a priority sort — ordering precedence is:

1. **Hard dependencies** (topological — an item is only *ready* when all its `Depends on` are done).
2. **Finish in-flight work** (partly-built features lead — low risk, they unblock consumers).
3. **Lead theme — Type-system intelligence** (chosen), then Navigation/Refactoring, then
   LuaRocks/Tooling, then Formatting/polish.
4. **Priority (MoSCoW / front-matter)** as the within-wave tie-break.

## How an agent uses this

- **Pick the lowest-numbered wave with a *ready* item** — ready = every `Depends on` entry is
  `done`. Within a wave, prefer higher priority.
- **Parallelism**: items marked **Parallel ✓** add new files / a distinct extension point and
  can run concurrently in separate worktrees. Items marked **Serial: <cluster>** mutate a shared
  hot file (the type engine, `LuaFormatBlock`, …) and must be done one at a time within that
  cluster.
- **Update `status` to `done`** in the feature's `requirements.md` as you finish; that makes its
  dependents *ready*. Re-run `scripts/gen_status.py`.
- Several "Wave 0/1" features are **already partly implemented** — read the design's "Current
  implementation status" note before starting; the remaining work is scoped there.

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
| REFACT-03 | Safe delete | planned | M | **NAV-02, REFACT-02** | — | ✓ new processor |

## Wave 4 — LuaRocks & Tooling  *(two independent tracks — run A and B concurrently)*

**Track A — LuaRocks** (shares `LuaRocksSettings`, defined in ROCKS-04, and the rockspec bridge):

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| ROCKS-04 | Task execution & run configs | planned | H | — (defines `LuaRocksSettings`) | ROCKS-02, ROCKS-03 | ✓ new package |
| ROCKS-03 | Dependency resolution | planned | H | ROCKS-04 (`LuaRocksSettings`) | — | ✓ |
| ROCKS-02 | Package browser | planned | M | ROCKS-04 (`LuaRocksSettings`) | — | ✓ |
| ROCKS-01 | Project initialization | planned | H | — | — | ✓ (standalone wizard) |

**Track B — Tool management:**

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TOOL-00 | De-risking spikes | planned | H | — | TOOL-01, TOOL-02 | ✓ spikes |
| TOOL-01 | Tool registry & discovery | planned | H | TOOL-00 | TOOL-03 | ✓ |
| TOOL-02 | Project binding & env | planned | H | TOOL-00 | TOOL-03 | ✓ |
| TOOL-03 | UI & health monitoring | planned | H | **TOOL-01, TOOL-02** | — | ✓ |

## Wave 5 — Formatting & polish  *(low coupling; good parallel filler whenever there's spare capacity)*

| ID | Title | Status | Prio | Depends on | Unblocks | Parallel |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| FORMAT-03 | Blank-line management | planned | M | — | — | Serial: formatter (`LuaFormatBlock`/`LuaCodeStyleSettings`) |
| FORMAT-04 | Expression wrapping | planned | M | FORMAT-03 *(file-sharing only)* | — | Serial: formatter |
| FORMAT-05 | Alignment logic | planned | M | FORMAT-04 *(file-sharing)* | — | Serial: formatter |
| FORMAT-06 | Comment formatting | planned | M | FORMAT-05 *(file-sharing)* | — | Serial: formatter |
| RUN-03 | Interactive console (REPL) | planned | L | — | — | ✓ new package |

---

## Dependency summary (the hard edges)

```
TYPE-09-P0 ──▶ TYPE-09-P2 ──▶ TYPE-09-P3 ─┐
TYPE-09-P1 ───────────────────────────────┴─▶ TYPE-09-P4
COMP-04 (helper) ──▶ SYNTAX-17
TYPE-02 ┄(soft)┄▶ NAV-05, NAV-06
NAV-02 ──▶ NAV-10
NAV-02, REFACT-02 ──▶ REFACT-03
ROCKS-04 (LuaRocksSettings) ──▶ ROCKS-02, ROCKS-03
TOOL-00 ──▶ TOOL-01, TOOL-02 ──▶ TOOL-03
FORMAT-03 ─(files)─▶ FORMAT-04 ─▶ FORMAT-05 ─▶ FORMAT-06
```
Everything else is independent and can start as soon as its wave is reached.

## Parallelization guidance for multiple agents

- **Safe to run concurrently** (separate worktrees): any **Parallel ✓** items whose deps are
  done — e.g. the whole of Wave 1 can run in parallel once Wave 0 lands; ROCKS Track A and TOOL
  Track B are fully independent.
- **Keep one agent per "Serial" cluster** at a time: the **type engine** (Wave 0), and the
  **formatter** (FORMAT-03..06). Concurrent edits there will conflict.
- A reasonable 3-agent split once Wave 0 is done: Agent 1 = type-system (Wave 1), Agent 2 =
  navigation/refactoring (Waves 2–3), Agent 3 = ROCKS Track A. Tooling (Track B) and Formatting
  slot in as capacity frees.

## Maintenance

- This is a point-in-time plan (created 2026-06-13; status column refreshed 2026-06-14). When a
  feature reaches `done`, mark it in its `requirements.md`, re-run `scripts/gen_status.py`, and its
  dependents become ready. Treat the status column here as advisory — `status.md` is canonical.
- If cross-epic priorities change, re-order the waves; the **Depends on** column is the
  invariant that must always hold.
- See [planning-gaps.md](planning-gaps.md) for the original audit and
  [planning-handoff.md](planning-handoff.md) for the planning brief that produced these specs.
