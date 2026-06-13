---
id: "PLANNING-GAPS"
title: "Planning Gaps Audit"
type: "spec"
status: "done"
priority: "high"
folders:
  - "[[features]]"
---

# Planning Gaps Audit

Point-in-time audit (2026-06-13) of how completely each open feature is planned,
measured against one bar:

> **A feature is "fully planned" when a non-frontier (weaker, non-reasoning) model
> could implement it correctly from the docs alone, making no design decisions of its
> own.**

This bar measures planning *depth*, not document format. It is satisfied only when the
docs pin down the things a weak implementer cannot invent: fully-qualified class names
to create, IntelliJ extension-point / `plugin.xml` registration, the algorithm or data
flow, named existing APIs/utilities to call, data-model field lists, and acceptance
criteria as concrete input→output test cases. "What" without "how" does not pass.

Scope: the 33 **open** leaf features (status not `done`/`cancelled`). The 65 `done`
features and 3 retroactively story-decomposed features (COMP-02, DOC-01, MAINT-04) are
out of scope.

## Headline

- **Only 5 of 33 open features (15%) are implementable blind.**
- **Having `design.md` + `implementation-plan.md` is not sufficient** — 9 of the 14
  features that carry both still fall short (PARTIAL or FAIL).
- Requirements tables that specify *what* (e.g. NAV-02/03) but not *how* do **not** pass.

| Verdict | Count |
| :--- | :---: |
| ✅ PASS — implementable blind | 5 |
| 🟡 PARTIAL — a few design decisions remain | 7 |
| 🔴 FAIL — has a plan but too vague | 2 |
| 🔴 FAIL — scoped (requirements only), design unmade | 8 |
| 🔴 FAIL — empty stub | 11 |

## ✅ PASS — ready for blind execution (5)

These share a signature: FQ class names, exact registration, explicit algorithms,
data-model field lists, and input→output test cases. Use them as the planning template.

| ID | Title | Why it passes |
| :--- | :--- | :--- |
| COMP-03-03 | Auto-import Completion | Full Kotlin class bodies, named APIs, algorithms, data models, TC-03-01…30 |
| SYNTAX-07-07 | Method Chaining Hints | Provider class + group + ID, detection algorithm, resolver, shared util, 9 I/O test cases, phased plan |
| SYNTAX-07-11 | Large File Threshold | Exact property + default (10000), exact method to edit, explicit skip algorithm, UI widget, 4 TCs |
| TOOL-01 | Tool Registry & Discovery | FQ class/enum names, full field list, named APIs, storage location, I/O test cases |
| TOOL-02 | Project Binding & Env Integration | Full Kotlin `State` snippets, storage path, resolution algorithm, `plugin.xml` reg, test cases |

## 🟡 PARTIAL — close, one or two design decisions remain (7)

| ID | Title | Gap to close |
| :--- | :--- | :--- |
| ROCKS-01 | Project Initialization | `plugin.xml` moduleBuilder reg; template bodies (rockspec/Makefile/workspace) absent; `LUA_INIT` run-config patching unspecified; req↔design contradiction on `.luacheckrc`/`.stylua.toml` |
| ROCKS-02 | Package Browser | Parsing of unstructured `luarocks search`/`show` output undefined (no format/regex); toolWindow reg details; cache format + staleness policy undefined |
| ROCKS-03 | Dependency Resolution | **Weakest** — LuaRocks semver comparator (`scm-1`, `dev-1`, `3.1-0`) has no algorithm; conflict-satisfaction only narrative; manifest format + rockspec bridge script contents missing |
| ROCKS-04 | Task Execution & Run Configs | `plugin.xml` `<configurationType>` reg/ID; persisted XML schema for serialization test; `Before Launch` and C-library build env (both `Must`) have no design |
| TOOL-00 | De-risking & Spikes | Inherently non-deterministic — research tasks with no success thresholds or expected outputs; not "implementable" by nature |
| TOOL-03 | UI & Health Monitoring | Editor-banner notification API unspecified; health-check scheduling/periodicity undefined; thin test coverage |
| INSP-01 | Undeclared Variable | Annotator method sig + extension-point snippet; suppression parsing (`@diagnostic`, `luacheck: ignore`); used-before-declaration algorithm; settings model for ignored globals |

## 🔴 FAIL — has a plan but too vague (2)

| ID | Title | Core unmade decisions |
| :--- | :--- | :--- |
| COMP-04 | Type-Inferred Completion | Union resolution (intersection vs union), `__index` function return inference, generic substitution, visibility-scope rule, self-typing — all "what" with no "how"; no registration |
| SYNTAX-17 | Inferred-Type Highlighting | Annotator vs HighlightingPass undecided; no `TextAttributesKey` defs / colorSettingsPage; viewport analysis + caching mechanics undefined |

## 🔴 FAIL — scoped only, design unmade (8)

Each has a requirements table (often with prioritized, status-tracked rows) but **no
`design.md` and no `implementation-plan.md`** — the *how* is entirely absent.

NAV-02 (Find Usages), NAV-03 (Go to Class/File/Symbol), NAV-05 (Method Override
Markers), NAV-06 (Hierarchy View), NAV-09 (Return Highlighter), NAV-10 (Access
Detector), TYPE-02 (Class/Table Definitions), RUN-03 (Interactive Console / REPL).

## 🔴 FAIL — empty stub (11)

13-line placeholders with no real requirements (e.g. FORMAT-03's body is "Placeholder
requirements for FORMAT-03."). These need requirements *before* design.

FORMAT-03/04/05/06, REFACT-02/03, TYPE-09-P0/P1/P2/P3/P4.

## Red flags (status overstates planning)

- **NAV-02 (Find Usages)** and **TYPE-02 (Class/Table Definitions)** were `in_progress`
  with no design or plan — being built without a written design. **Resolved 2026-06-13**:
  downgraded to `todo` along with the other six no-design features now gated by
  `scripts/lint_planning.py`.
- **TYPE-09 (Union Distribution Logic)** is `in_progress` at the parent, yet all five
  phases (P0–P4) are empty `todo` stubs. The parent status still overstates reality
  (the parent is non-leaf, so the planning gate does not catch it) — demote it or fill
  the phases.

## Recommendation (priority order)

1. **Cheap wins first — close the 7 PARTIAL to PASS.** Each needs only a bounded
   addition (a parsing format, one algorithm, a registration snippet). Highest leverage:
   ROCKS-03's version comparator and the missing `plugin.xml` registrations.
2. ~~Fix the two `in_progress` red flags (NAV-02, TYPE-02)~~ — **done 2026-06-13**: both
   downgraded to `todo`. They still need design before returning to `planned`.
3. **Reconcile TYPE-09**: demote the parent from `in_progress` or fill the five phase
   stubs; current status is misleading.
4. **Design the 8 scoped-only features** (NAV epic + RUN-03): requirements exist; add
   `design.md` + `implementation-plan.md` to PASS depth using the 5 exemplars as a model.
5. **The 11 stubs need requirements first** — they are the furthest from executable and
   should not be counted as "planned" in any status rollup.

## Method

For each open leaf feature, read its full doc bundle (`requirements.md`, `design.md`,
`implementation-plan.md`, plus any `spec/` and test docs). Features lacking both design
and plan fail structurally (the *how* is unwritten). The 14 features carrying both were
read and scored individually against the bar above. Status vocabulary and the leaf-feature
definition match `scripts/gen_status.py`.
