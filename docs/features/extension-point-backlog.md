---
id: "EP-BACKLOG"
title: "Extension-Point Tail Backlog"
type: "guide"
folders:
  - "[[features]]"
---

# Extension-Point Tail Backlog

A curated catalogue of the **long tail of IntelliJ Platform extension points** Lunar could still
implement, beyond the ~70 already registered and beyond the eight already planned in the
[EDITOR epic](editor/requirements.md). Every item below was checked against the live
`plugin.xml` surface on **2026-07-09** — none are already registered.

This is a **reference/backlog**, not a work tracker: nothing here has a status. When an item is
promoted to real work, it becomes a feature under a (possibly new) epic and picks up front-matter
status there. The canonical execution backlog remains [roadmap.md](../roadmap.md).

**Relevance legend** (Lua-specific judgment, not generic platform value):
◆ high · ◇ medium · · marginal-for-Lua.

## Already covered — do not re-mine

- **Head + mid-tail (registered):** parser/lexer, syntax highlighter, 8× annotator + external
  annotator (luacheck), completion, documentation (+ new `platform.backend.documentation` API),
  folding, brace matcher, commenter, find-usages, rename (`namesValidator` + `refactoringSupport`),
  safe-delete, structure view, breadcrumbs, formatter (+ `formattingService` + `postFormatProcessor`),
  code-style settings, line markers + run-line markers, parameter info, 5× declarative inlays,
  type hierarchy, highlight-usages + read/write detector, usage-type provider, goto class/symbol,
  reference contributors, color settings, live + postfix templates, enter handler, stub + file
  indexes, library roots, project-view decorator, editor-notification provider, search-everywhere,
  JSON-schema integration, coverage, run configs, debugger breakpoint type.
- **Planned (EDITOR epic, 01–08):** smart typing (`TypedHandler`/`QuoteHandler`/`BackspaceHandler`),
  spellchecking, TODO indexing, smart word selection, Surround With, Unwrap/Remove,
  move-statement/element, Smart Enter.

## Where each cluster lands — mostly existing epics

Cross-checked against the epic set (2026-07-09): **five of seven clusters extend an epic that
already exists** — only `INJECT` and `SSR` would be new epics. Confirm the exact next feature ID
against the home epic's `requirements.md` before minting.

| Cluster | Home | New features | Standout items | Priority |
| :--- | :--- | :--- | :--- | :---: |
| 1. Refactoring expansion | extend **`REFACT/INTENT`** (has Rename/Introduce-Var/Safe-Delete only) | `REFACT-07/08/09` | Inline, Extract Function, Change Signature | ◆ |
| 2. Type-engine leverage | extend **`NAV`** (+ `TYPE`) | new NAV features | Type Info, Go-To-Type | ◆ |
| 3. Analysis correctness | extend **`INSP`** / **`ANALYSIS`** / **`SYNTAX`** | features across the three | implicit-usage, inspection-suppressor, rainbow | ◆ |
| 4. Run / Test / Console | extend **`DEBUG/RUN`** — **`RUN-05` test runner already `done`** | one RUN feature | clickable tracebacks (console filter) | ◇ |
| 5. Language injection | 🆕 **new epic `INJECT`** | — | SQL/regex/JSON in strings | ◇ |
| 6. Structural Search | 🆕 **new epic `SSR`** | — | `StructuralSearchProfile` for Lua | ◇ |
| 7. Editor micro-tail | extend **`EDITOR`** | `EDITOR-09+` | join-lines, code-vision, region folding, Copy Reference | ◇ |

---

## 1. Refactoring expansion — extend `REFACT/INTENT` (the biggest real gap)

Lunar has only *introduce-variable*, *safe-delete*, and *rename*. The daily-driver refactorings are absent.

| EP / mechanism | What it gives | Rel. |
| :--- | :--- | :---: |
| `com.intellij.refactoring.inlineHandler` (`InlineActionHandler`) | **Inline** variable / function | ◆ |
| `extractMethod` machinery | **Extract Function** (extract-variable exists; this doesn't) | ◆ |
| `com.intellij.refactoring.changeSignature` | **Change Signature** — reorder/add/remove params, update all call sites | ◆ |
| `com.intellij.refactoring.moveHandler` + `moveFileHandler` | **Move** file/function; fix up `require` paths | ◇ |
| `com.intellij.renamePsiElementProcessor` | Rename that also updates string-keyed access / `require` strings | ◇ |
| `com.intellij.suggestedRefactoringSupport` | Floating "apply rename/change-signature everywhere?" hint after hand-edits | ◇ |
| `automaticRenamerFactory`, `elementDescriptionProvider` | Related-name rename; nicer refactoring-preview labels | · |

## 2. Type-engine leverage — extend `NAV` (+ `TYPE`); cheap wins off the existing engine

| EP / mechanism | What it gives | Rel. |
| :--- | :--- | :---: |
| `com.intellij.codeInsight.typeInfo` (`ExpressionTypeProvider`) | **View Type Info** (Ctrl+Shift+P) — inferred type of the selected expression | ◆ |
| `com.intellij.typeDeclarationProvider` | **Go To Type Declaration** — variable → its `@class` | ◆ |
| completion `weigher` (id `completion`) | Rank completion members by inferred receiver type | ◇ |

## 3. Analysis correctness — extend `INSP` / `ANALYSIS` / `SYNTAX` (Lua idioms)

| EP / mechanism | What it gives | Rel. |
| :--- | :--- | :---: |
| `com.intellij.implicitUsageProvider` | Stop flagging framework callbacks as unused (`love.load`, busted `describe`/`it`, entry points) | ◆ |
| `com.intellij.lang.inspectionSuppressor` | Honour inline suppression (`-- luacheck: ignore`, LuaCATS `---@diagnostic disable-next-line`) | ◆ |
| `com.intellij.globalInspection` | Project-wide checks (unused *global* function across the project) | ◇ |
| `com.intellij.daemon.highlightVisitor` / `lang.rainbowVisitor` | Semantic / rainbow highlighting of locals & params | ◇ |
| `com.intellij.spellchecker.bundledDictionaryProvider` | Ship a stdlib dictionary so `ipairs`/`tostring` never read as typos (pairs with EDITOR-02) | · |

## 4. Run / Test / Console — extend `DEBUG/RUN` (`RUN-05` test runner already done)

`RUN-05` (Busted/Lunity test runner + Test Results window) is `done`, so the test-tree converter is
already delivered. The remaining real gap is the **console traceback filter**.

| EP / mechanism | What it gives | Rel. |
| :--- | :--- | :---: |
| `com.intellij.consoleFilterProvider` (`Filter`) | **Clickable stack traces** in the run console (`foo.lua:42` → jump) | ◆ |
| ~~`outputToGeneralTestEventsConverter`~~ | ~~busted output → native test tree~~ — **done via `RUN-05`** | ✓ |
| `com.intellij.testFinder` + `testCreator` | Navigate spec ↔ source; "Create Test" | ◇ |
| `com.intellij.execution.console.ConsoleFolding` | Fold noisy C-side frames in tracebacks | · |

## 5. Language injection — 🆕 new epic `INJECT` (a differentiator)

| EP / mechanism | What it gives | Rel. |
| :--- | :--- | :---: |
| `com.intellij.multiHostInjector` / `languageInjectionContributor` | Inject **SQL / regex / JSON / HTML** into Lua string literals (OpenResty, love, web frameworks); honour `--[[language=SQL]]` IntelliLang hints | ◇ |

## 6. Structural Search & Replace — 🆕 new epic `SSR` (advanced, differentiating)

| EP / mechanism | What it gives | Rel. |
| :--- | :--- | :---: |
| `com.intellij.structuralsearch.profile` (`StructuralSearchProfile`) | SSR templates over Lua PSI. No competitor plugin has it; large effort. | ◇ |

## 7. Editor micro-tail — extend `EDITOR` as `EDITOR-09+` (small, high polish-per-line)

| EP / mechanism | What it gives | Rel. |
| :--- | :--- | :---: |
| `com.intellij.joinLinesHandler` (`JoinLinesHandlerDelegate`) | Smart **Join Lines** (collapse a concat, an `if`, a table) | ◇ |
| `com.intellij.codeInsight.codeVisionProvider` | Inline reference/usage counts above declarations | ◇ |
| `com.intellij.lang.customFoldingProvider` | `--region` / `--endregion` folding | ◇ |
| `com.intellij.qualifiedNameProvider` | **Copy Reference** (copy a function's qualified name) | ◇ |
| `com.intellij.gotoDeclarationHandler` | Augment Ctrl-click (e.g. `require("a.b")` → the file) | ◇ |
| `com.intellij.lang.lineIndentProvider` | Correct indent-on-Enter for half-written/broken code without the full formatter | ◇ |
| `com.intellij.copyPastePostProcessor` | Reformat / reference-fix on paste | ◇ |
| live-template `macro` | Custom template macros (suggest a loop var, iterate a table) | ◇ |
| `com.intellij.colorProvider` (`ElementColorProvider`) | Gutter color swatch for `{r,g,b}` / hex color literals (love/UI code) | · |
| `com.intellij.navbar.NavBarModelExtension` | Richer navigation-bar chain | · |

## Marginal for Lua — recommend skipping

`gotoSuper`, `MethodImplementor` / override-implement, `importOptimizer`, `lang.rearranger`,
`pullUp` / `pushDown` / `extractSuperclass` — all lean on class inheritance or an import system Lua
does not have.
