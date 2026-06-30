---
id: "PLANNING-GAPS"
title: "Planning Gaps Audit"
type: "spec"
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

## Caveat: this audit measured completeness, not grounding (added 2026-06-15)

This audit's PASS/FAIL ratings judged only the **completeness** axis (every section filled,
algorithms specified, registrations stated). It did **not** verify the second axis —
**grounding**: whether the PSI types / methods / services a design names actually exist in
Lunar. Two designs that would score PASS here, **INSP-03** and **INSP-09**, were later found
to reference EmmyLua's API throughout (`LuaLocalDef`, `LuaReturnStat`, `getExpressionType`,
`isAssignable`, a non-existent `LuaBitwiseOp`) and, in INSP-09's case, to silently duplicate
the live `LuaLanguageLevelAnnotator`. Both were corrected on 2026-06-15.

**Implication for the re-review (#7):** a fresh pass must grep every named symbol against the
codebase, not just check that sections are filled. The plan-feature bar now carries a
**Grounding** axis (SKILL.md → "The second axis") to prevent recurrence.

## Grounding audit — Wave 7 formatter cluster (Wave 6 readiness §3.1, 2026-06-15)

First application of the new **Grounding** axis to a next-to-implement batch: the Wave 7 serial
formatter cluster (`FORMAT-03/04/05/06`), which had only stub requirements at the 2026-06-13 audit
and have since gained full designs. One subagent per design (sequential), each grepping every named
PSI type / method / service / extension point against `src/main`, `src/main/gen`, and `plugin.xml`.

**Result: all four PASS (grounded).** No fictional or EmmyLua-ported symbols, no components
duplicated instead of extended. The designs cite real files/lines accurately (e.g.
`LuaFormatBlock.kt:227-230`, `LuaCodeStyleSettings.kt:50/57`) and the formatter infra they build on
(`LuaSpacingBuilder`, `LuaCodeStyleSettings`, `LuaLanguageCodeStyleSettingsProvider`,
`LuaCommenter`, real element types `SHORTCOMMENT`/`LUACATS_COMMENT`/`ASSIGN`) all exist. The
INSP-03/INSP-09 EmmyLua-API failure mode does **not** recur here.

| Design | Verdict | Non-blocking notes for the implementer (not grounding defects) |
| :--- | :--- | :--- |
| FORMAT-03 Blank-line mgmt | ✅ PASS | `LuaTrailingNewlinePostProcessor` + `<postFormatProcessor>` are new (fine). §2.1's `createSpacing(0,0,…)` drops the existing `1,1` min/max — harmless in a newline context, just a sanity note. |
| FORMAT-04 Expression wrapping | ✅ PASS | (1) §2.3 uses platform-deprecated `WRAP_OPTIONS`/`WRAP_VALUES`; prefer `CodeStyleSettingsCustomizableOptions.getInstance()` (already used at `LuaCodeStyleSettings.kt:59`). (2) `LuaFormatBlock` doesn't yet hold a `LuaCodeStyleSettings` ref — thread it in (the `LuaSpacingBuilder` already holds `luaSettings` at `LuaFormatBlock.kt:156`); `getInstance` is nullable. |
| FORMAT-05 Alignment logic | ✅ PASS | (1) `createAlignment(true)` diverges from the in-file no-arg precedent (`LuaFormatBlock.kt:53`) — intentional (allowBackwardShift); note it so it isn't "tidied" away. (2) `showCustomOption` shorthand needs `LuaCodeStyleSettings::class.java` + a real `SettingsType` group (the design's "ALIGNMENT_SETTINGS" isn't an IntelliJ `SettingsType`; nearest is `WRAPPING_AND_BRACES_SETTINGS`). (3) the `=` is the `ASSIGN` leaf token with no `LuaField` accessor — tag during the child walk. |
| FORMAT-06 Comment formatting | ✅ PASS | `LuaCommentWrapPostProcessor` + `WRAP_LONG_COMMENTS` are new (fine). §3.2's "no new code" for the leading space is correct — the commenter options at `LuaCodeStyleSettings.kt:92-95` already wire it. |

These four are cleared to implement on grounding. The non-blocking notes are precision/idiom
tightening an implementer should fold in, not blockers. **Scope note:** this batch was the next one
slated for implementation; the remaining `planned` designs (Wave 8 intentions, Wave 10 TOOL/ROCKS,
etc.) have **not** had a grounding pass and should get one before their wave begins.

## Retro design backfill — Wave 6 completion (COMP-06/07/08, 2026-06-15)

COMP-06/07/08 were *implemented and tested* but carried only 22–24-line skeleton `design.md` docs
(a component name + an XML reg) marked `done` — well below the bar — and `roadmap.md` still
listed them as "spec pending". Backfilled real, source-grounded designs (every symbol read from
`src/main`, `file:line` cited) and reconciled `roadmap.md`. The backfill surfaced two
status-overstatements the skeleton "done" had hidden:

- **COMP-06 is actually partial.** `.if` (`LuaIfPostfixTemplate`) is built+tested, but **`.not`
  (COMP-06-02, a `Must`) is NOT implemented**. Design now specifies `LuaNotPostfixTemplate`;
  roadmap downgraded to `partial`.
- **COMP-07 req↔impl naming drift.** Shipped abbreviations are `fun`/`fori`/`forp`/`loc`, but the
  requirement names `func` and calls `fori` an "ipairs loop" (it is numeric). Documented; needs a
  requirement-text reconcile (not a code change).
- **COMP-08 "duplicate" Enter handler is a false alarm.** `lang/completion/LuaEnterHandler`
  (block `end` insertion, `preprocessEnter` — COMP-08) and `lang/format/LuaEnterHandlerDelegate`
  (LuaDoc `---` continuation, `postProcessEnter` — a DOC feature) are **complementary**, disjoint
  triggers/phases, intentionally both registered. Only the near-identical names are confusing;
  an optional clarity rename is noted in the COMP-08 design, not required.

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

---

# Wave 10 Grounding Audit (2026-06-16)

A pre-implementation grounding pass over the **TOOL** (Tool Inventory) and **ROCKS**
(LuaRocks) tracks — verifying every symbol each design names exists (or is a legitimately
new class to create) against `src/main`, `src/main/gen`, `plugin.xml`, and the 2026.1
platform source (`~/Documents/src/lua/intellij-community`). Two track-level subagents.

## Verdicts (go / fix-first)

| Feature | Verdict | Blocking gap (if any) |
| :--- | :--- | :--- |
| TOOL-00 De-risking | **NEEDS-FIXES** | Only spike 00-01 (terminal API) — see Terminal fix below |
| TOOL-01 Registry & discovery | **READY** | — (mirrors `LuaInterpreter*`/settings patterns cleanly) |
| TOOL-02 Project binding & env | **NEEDS-FIXES** | Terminal API + `EnvironmentProvider` + invented topic/service (below) |
| TOOL-03 UI & health | **READY** | — (most rigorously grounded; note: adds 2 fields to TOOL-01 `LuaTool`) |
| ROCKS-01 Project init | **READY** *(after `LuaIcons.ROCKET`)* | shared icon field only |
| ROCKS-02 Package browser | **READY** *(after `LuaIcons.ROCKET`)* | shared icon + porcelain-format risk note |
| ROCKS-03 Dependency resolution | **NEEDS-FIXES** | stale `export.lua` task + `src/main/lua`→`resources/lua` relocation gate |
| ROCKS-04 Task execution | **READY** *(after `LuaIcons.ROCKET`)* | defines shared `LuaRocksSettings` (consistent) |
| ROCKS-08 Publishing | **NEEDS-FIXES** | 9-line stub — under-specified; "Could" priority, defer |

## Cross-cutting fixes required before implementation

1. **Terminal PATH-injection API is wrong everywhere it appears** (TOOL-00-01, TOOL-02
   `design.md` + `design-terminal-path.md`). The named `com.intellij.terminal.TerminalCustomizer`,
   `customizeTerminal(terminalType, environment, initCommands)`, `TerminalType` enum, and the
   fabricated `LocalTerminalDirectRunner.EpExtension`/`ProcessExecutor`/`localTerminalDirectRunner`
   EP **do not exist in 2026.1**. Standardize on
   `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer.customizeExecOptions(project,
   MutableShellExecOptions)` (EP `org.jetbrains.plugins.terminal.shellExecOptionsCustomizer`,
   `@ApiStatus.Experimental`, background thread); deprecated fallback is
   `org.jetbrains.plugins.terminal.LocalTerminalCustomizer.customizeCommandAndEnvironment(...)`
   (EP `…localTerminalCustomizer`) — both inject PATH by mutating the `envs` map (no `initCommands`).
2. **`LuaIcons.ROCKET` does not exist** — referenced by ROCKS-01/02/03/04 plugin.xml/icon code.
   `LuaIcons` currently has only `FILE` (which already maps to `/icons/rocket_16.png`). One-line fix:
   add `val ROCKET = getIcon("/icons/rocket_16.png", …)` (or reuse `FILE`). Hard blocker for 4 designs.
3. **TOOL-02 invented symbols:** there is no platform `EnvironmentProvider` interface — integrate run
   configs via `RunConfigurationExtension.patchCommandLine` (confirm GoLand ships the Java
   `execution-impl` module) or patch `GeneralCommandLine.environment` directly in
   `command/LuaCommandLine.kt` (definitely available). Reuse the existing
   `LuaSettingsChangedListener.TOPIC` (not an invented `LUA_SETTINGS_TOPIC`); reconcile the two
   contradictory `LuaTerminalEnvironmentService` definitions into one project-level service.
4. **ROCKS-03 stale task:** the "`export.lua` latent bug `ipairs(name)`→`ipairs(names)`" is already
   fixed in the tree (line 7 uses `ipairs(names)`) — drop that task; instead verify the bridge's
   actual JSON output shape against a real rockspec. The `src/main/lua/` → `src/main/resources/lua/`
   relocation (to package the bridge scripts) is genuine un-done build work — make it an explicit gate.
5. **ROCKS-08** is too thin to implement: package name inconsistency (`lang.rocks` vs the track's
   `rocks.*`), no `<action>` registration, no `PasswordSafe` for the API key, no `LuaRocksSettings`
   reuse for the binary path. Needs a small design pass; it is `Could`-priority, so defer to last.

## Readiness summary

- **Ready to implement now** (4): TOOL-01, TOOL-03, ROCKS-04, ROCKS-01 — plus ROCKS-02 once the
  shared `LuaIcons.ROCKET` lands. The `LuaRocksSettings` shared dependency (defined in ROCKS-04,
  consumed by ROCKS-02) is consistent across designs; no drift.
- **Need a bounded design fix first** (4): TOOL-00 + TOOL-02 (the terminal API rewrite — one shared
  fix covers both), ROCKS-03 (drop stale task + gate the resource relocation), ROCKS-08 (flesh out).
- **Recommended order** (unchanged from roadmap): **TOOL** `00 → 01 → 02 → 03`;
  **ROCKS** `04 → {02 ∥ 03} → 01 → 08`. The two tracks are independent and run concurrently.
