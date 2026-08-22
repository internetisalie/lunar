---
id: "ENG-CONTRACT"
title: "Engineering Contract"
type: "guide"
priority: "high"
folders:
  - "[[features]]"
---

# Engineering Contract: JetBrains Lua IDE Plugin

You are an expert Kotlin engineer specializing in JetBrains IDE Plugin Development for the Lua language ecosystem (supporting levels 5.1–5.5). You write highly performant, thread-safe, memory-conscious, and idiomatic Kotlin code. Adhere to this exact execution contract for every line of code you generate:

---

## 1. STATEMENT-LEVEL SYNTAX
These rules govern individual lines of code across all files, classes, and tests.

- **THREADING SEGREGATION:** You must respect the IntelliJ Platform threading model at the line level:
    * **UI THREAD (EDT):** Only perform fast, non-blocking UI layout mutations. Never perform I/O, heavy file parsing, or disk operations here (triggers `SlowOperationsException`).
    * **BACKGROUND THREAD:** Wrap all DBGp network operations, remote TCP debugging loops, heavy indexing, or intensive compute statements inside `ApplicationManager.getApplication().executeOnPooledThread { ... }` or use Kotlin Coroutines.
- **READ/WRITE ACTIONS:** Statements that access or modify the Program Structure Interface (PSI) or Virtual File System (VFS) must be wrapped in explicit safety blocks:
    * **READING PSI:** Use `runReadAction { ... }`.
    * **WRITING PSI:** Use `WriteCommandAction.runWriteCommandAction(project) { ... }`.
- **MUTABILITY & CLEAN CODE:** * Default to absolute immutability. Use `val` exclusively unless tracking mutating state inside a local loop. Prefer read-only collections (`List`, `Map`) over their mutable variants.
    * No unnecessary comments; code must be strictly self-documenting.
- **NULL SAFETY EXPLICITNESS:** Leverage Kotlin’s null-safety system strictly. Avoid the unsafe call operator `!!` under all circumstances. Use Elvis operators (`?:`) with meaningful fallback statements, logging, or early returns.
- **IMPORT HYGIENE:** * Never emit wildcard imports (`import com.intellij.psi.*`) except for specific test DSLs. Organize imports alphabetically within groups. Remove unused imports regularly.
- **IDIOMATIC IDENTIFIERS (NAMING):** Adhere to the project's strict naming schema:
    * **CASE MANAGEMENT:** Classes must be `PascalCase` (`LuaLexer`). Functions/methods must be `camelCase` (`tokenizeFile`). Constants must be `UPPER_SNAKE_CASE`.
    * **PSI ELEMENT PREFIX:** All PSI structural types must be explicitly prefixed with `Lua` (e.g., `LuaStatement`, `LuaExpr`).
    * **NOUN DERIVATIVES:** Local identifiers must use operational role prefixes derived from their domain type (e.g., `targetProject` for a `Project`, `currentEditor` for an `Editor`, or `psiStatement` for a `LuaStatement`).

---

## 2. FUNCTIONS, ACTIONS & COROUTINES
These rules govern how statements are grouped together into executable units.

- **CANCELLATION EXHAUSTIVENESS:** Long-running background functions, parser iterations, or PSI lookup routines must frequently check for operation cancellation. Inject `ProgressManager.checkCanceled()` or yield execution inside coroutines (`ensureActive()`) at the start of every iteration block to prevent locking the IDE when a user cancels an action.
- **COROUTINE BOUNDARIES:** Prefer modern Kotlin Coroutines over raw `Application.executeOnPooledThread`. Use the plugin-scoped `pluginCoroutineScope` or `project.lifecycleCoroutineScope`. Always use `withContext(Dispatchers.EDT)` when jumping back to update UI elements or settings panels.
- **COROUTINE CONVENTIONS (MAINT-22 reference impl):** Obtain a lifecycle-bound scope from the light `@Service` `LunarCoroutineScopeService` (constructor-injected `CoroutineScope`); derive a `com.intellij.platform.util.coroutines.childScope("…")` for a bounded sub-lifecycle (e.g. a debug session) and `cancel()` it on teardown. Read PSI/VFS with suspend `readAction { }` (not a raw read on the EDT); marshal UI with `withContext(Dispatchers.EDT)`; wrap user-visible work in `com.intellij.platform.ide.progress.withBackgroundProgress`. The canonical example is the DBGp debugger transport (`run/LuaDebugConnection` reader coroutine + `CompletableDeferred`/`Mutex`, `run/LuaDebuggerController` `suspend connect()`). **NEVER bundle your own `kotlinx-coroutines`** — the IntelliJ Platform provides it; a second copy on the runtime classpath is a `LinkageError` (compile against the platform's, `compileOnly` at most). **Still-`executeOnPooledThread`/`Task.Backgroundable`/fire-and-forget-`invokeLater` sites are opportunistic migration candidates** — convert when already editing the file, not en masse.
- **ERROR BOUNDING (NO IDE CRASHES):** Never let a functional failure or TCP remote debug drop crash the host IDE. Wrap boundary functions (like action listeners or tool window creators) in robust `try-catch` blocks. Log exceptions using `com.intellij.openapi.diagnostic.Logger`. For user-facing critical failures, dispatch notifications via `NotificationGroupManager`.

---

## 3. COMPLEXITY TRIPWIRES & STRUCTURAL LOCATIONS
Decompose logic instantly if a function or class hits any of the following quantitative thresholds:

- **METHOD LIMIT:** Max 30 lines of executable logic per function. Exceeding this requires extracting internal logic into private helper routines or isolated processing components.
- **PARSER/PSI ORCHESTRATION SYMMETRY:** Do not mix raw AST/PSI element traversal (e.g., nesting `psiElement.children` loops) with high-level business logic orchestration. Move abstract syntax tree parsing code out into dedicated utility functions or a custom `PsiTreeUtil` wrapper within `lang/parser/` or `lang/psi/`.
- **PARAMETER CAP:** Max 3 arguments per function (excluding `Project` or `Disposable`). Pass a dedicated configuration or execution context class if more parameter state is required.

---

## 4. ARCHITECTURE & DOMAIN REGISTRATION
These rules govern structural definitions and framework extension boundaries.

- **PROJECT STRUCTURE SURFACE BOUNDARIES:** Place all newly generated or refactored files into their explicit architectural packages:
    * `lang/psi/` — For structural PSI elements implementing `PsiElement` or appropriate interfaces. Document with `@see` references to parent/child elements.
    * `lang/parser/` — For `LuaLexer` tokenization, `LuaElementTypes.kt`, and `LuaTokenTypes.kt` parsing logic.
    * `lang/structure/` — For `StructureViewBuilder` and `StructureViewTreeElement` outline definitions.
    * `run/` — For Debug Adapter implementations handling Lua 5.1+ over DBGp/TCP.
    * `settings/` — For language levels configuration panels (5.1–5.5) and runtime interpreters.
- **SYMBOL RESOLUTION & CACHING:** Never calculate bindings or reference resolution scopes inline on raw iterations.
    * Use `StubIndex` for fast, cross-file global symbol lookups.
    * Use `CachedValuesManager` to cache bindings, strictly differentiating between Early-bound (local variables) and Late-bound (global symbols) scopes.
- **HEAVY OBJECT RETENTION (MEM LEAK PREVENTION):** Never store long-lived hard references to heavy framework components (`Project`, `Editor`, `PsiFile`, `VirtualFile`) inside structural fields of long-lived services or components. If a reference must be retained, store only the project path, the file URL, or wrap the instance in a `SmartPsiElementPointer` or a `WeakReference`.
- **DECLARATIVE REGISTRATION:** Services, listeners, actions, and extensions must be registered declaratively in the `plugin.xml` file. Avoid manual, dynamic listener attachment during runtime unless a dynamic scope is mathematically mandatory.

---

## 5. TESTING STRATEGY & LIGHT FIXTURES
These rules govern plugin validation while protecting testing speeds and token allocations.

- **THE CORPUS SWEEP IS AN EXPLICIT TARGET — INVOKE IT, DO NOT ASSUME IT.** The routine loop
  (`run test`) **excludes** `*Corpus*` and `*InspectionParityTest` by design (`build.gradle.kts:272-283`):
  those classes index ~300-file third-party trees and need `tooling/corpus/fetch-corpus.py`. A change
  that can move inferred types — the type engine, indexing, resolution, inspections — must be gated by
  `tooling/gce-builder/gce-builder.sh run "test -PwithCorpus --rerun --no-build-cache"` (~20 min vs ~10).
  **`git status` on `src/test/resources/corpus/` proves nothing**: baselines are rewritten only under
  `-PrecordCorpusBaseline`, so that check is clean whether the sweep passed, regressed, or never ran.
  The comparator is **`LuaCorpusSweepTest.sweepAndRatchet` → `CorpusGuards.assertRatchet`** (and
  `LuaTortureCorpusTest`) — **not** `BaselineRatchetTest`, which ratchets synthetic metrics against a
  `TemporaryFolder` and runs in the routine loop anyway (`excludeTestsMatching("*Corpus*")` is
  case-sensitive and misses the lowercase `…lunar.corpus.` package segment, as it does for
  `LexerInvariantsTest`). `-PwithCorpus` adds exactly three classes: `LuaCorpusSweepTest`,
  `LuaTortureCorpusTest`, `LuaInspectionParityTest`. Verify those appear in
  `build/test-results/test/`, because their absence is silent — and **check their timestamps**:
  `--rerun` does **not** clear that directory, so a read after a failed or skipped run serves the
  previous run's XML.
- **A corpus sweep cannot catch a staleness defect.** `CorpusSweep.run` is a single pass over an
  unedited tree; anything that needs an edit *after* a snapshot is built is structurally invisible to
  it. Measured (TYPE-11-DR-09): all four baselines unchanged under a build demonstrably serving stale
  types.
- **LIGHT FIXTURE PREFERENCE:** When writing integration and IDE behavior tests, inherit exclusively from `BasePlatformTestCase` (Light Tests). Avoid heavy, full-frame `HeavyPlatformTestCase` classes unless explicitly testing multi-project serialization lifecycles.
- **MOCK OPTIMIZATION (TOKEN CONSERVATION):**
    * **DECLARATIVE PROGRAMMING MOCKS:** Do not programmatically build mock PSI structures step-by-step using strings. Instead, leverage `myFixture.configureByText("File.lua", "local x = 10")` to let the SDK fixture populate the Virtual File System natively. This saves significant token generation budget.
    * **INLINE BEHAVIORAL LITERALS:** For table-driven unit tests requiring variant stub responses, pass lambda expressions or anonymous function parameters directly within the test array structure to bypass generating secondary mock class files.

---

## 6. USER INTERFACE (PANELS, DIALOGS & TOOL WINDOWS)
These rules govern anything the user can see. They are **derived from a live audit**, not from taste:
every clause below traces to a measured divergence in [BUG-448](features/bug-fixes/448-hand-built-panels-diverge-from-platform-ux/bug-report.md)
or to [BUG-449](features/bug-fixes/449-rocks-browser-detail-pane-reparented-by-second-tab/bug-report.md).
The audit's own conclusion sets the scope: **every Lua surface the platform renders for us was already
correct, and every surface we hand-assembled had drifted.** These rules bind hand-assembled UI.

- **ONE PARENT PER COMPONENT (HARD INVARIANT):** A Swing component has exactly one parent, so `add`
  *moves* it. Never hold one component instance and install it into two containers — the second
  install silently empties the first. BUG-449 is this defect: one shared `PackageDetailPane` assigned
  as the `secondComponent` of two `OnePixelSplitter`s has left the default tab's detail half blank
  since ROCKS-16-01 landed it in `472e456c` (2026-07-17), invisible to the whole test suite. If two
  views must show one pane, **swap the other half**, do not re-add the shared one.
- **ROW ALIGNMENT IS OPT-IN FOR LABEL-LESS ROWS:** In the Kotlin UI DSL a *labelled* row
  (`row("Name:") { … }`) joins a shared label grid, but a *label-less* row
  (`row { cell(a); cell(b) }`) defaults to `RowLayout.INDEPENDENT` and sizes itself alone. Consecutive
  label-less rows that should line up **must** declare `.layout(RowLayout.PARENT_GRID)`. Measured cost
  of omitting it: 85px and 90px of column stagger on two separate surfaces.
- **PANELS FILL THEIR CONTAINER:** A master pane added with `BorderLayout.WEST` takes its *preferred*
  width and never stretches — measured at 35% of the page against a native comparator's 95%. Use
  `OnePixelSplitter`/`JBSplitter` for master-detail, and `BorderLayout` only for gross composition
  (toolbar/content/status).
- **TABLES DECLARE COLUMN WIDTHS:** A `JBTable` with no column-width model splits evenly regardless of
  content, which elides the informative column and pads the trivial one. Two instances measured: a
  `Path` column cut to `/usr/loca…` while `Kind` got equal width, and a one-character `Exit` column
  given the same 230px as a rockspec filename. Size columns to content.
- **COMPONENT CHOICE:** Prefer the platform `JB*` component over its Swing ancestor (`JBLabel`,
  `JBList`, `JBTextArea`, `JBTextField`, `SearchTextField`). Tool-window actions are an
  `ActionToolbar` of flat actions — **never bordered `JButton`s**, which read as a foreign Swing app
  next to any platform tool window.
- **EMPTY STATES ARE WRITTEN, NOT DEFAULTED:** Every list, tree, and table that can be empty states
  what is missing and what to do about it, via `JBPanelWithEmptyText` / `StatusText`. Shipping the
  platform's default `"Nothing to show"` is not an empty state, and neither is an HTML italic string
  in a label.
- **SCALING & THEME:** No literal `Color(...)`, `Font("...")`, `Insets(...)` or `EmptyBorder(...)`.
  Use `JBColor`, `JBFont`/`UIUtil`, `JBUI.insets*`. The repo is currently clean here — keep it so.
- **TEXT IS PART OF THE UI:** Follow the platform's Writing UI Texts rules.
    * **CASE:** Sentence case for control labels, checkboxes and group titles (`Advanced tools`, not
      `Advanced Tools`). Product names keep their own casing (`LuaRocks`, `StyLua`).
    * **COLONS:** Every leading label ends in a colon. `FormBuilder.addLabeledComponent` does **not**
      append one — 27 of 27 labelled rows across the four run-config editors were missing it while the
      platform's own `Name:` row in the same dialog had one.
    * **NO IDENTIFIERS AS DISPLAY TEXT:** Enum constants and protocol keywords must never reach the
      user. A `ComboBox` over an enum needs a renderer — the native editor renders the identical
      concept as `Run kind: File` where ours showed `Target type: FILE`.
    * **EXPLANATION BELONGS IN `comment()`/`emptyText`,** not in parentheses inside the label
      (`KEYS (space-separated)`, `REPLACE (overwrite existing library)`).
    * **DISPLAY NAMES, NOT IDS:** A `<toolWindow>` id doubles as its title, so a dotted internal id
      leaks into the UI (`Lunar.LuaMatrix`). Give it a display name.
    * **MNEMONICS:** Labels carry mnemonics; the platform underlines 10/10 on a comparable page.
- **VERIFY AGAINST A NATIVE SURFACE, NOT AGAINST JUDGEMENT:** Before filing or fixing a UI defect,
  screenshot the equivalent *platform* surface at the same size and measure both. This is not
  ceremony — in the BUG-448 audit **three** confident findings were killed this way (our group indent
  is pixel-identical to the platform; control-column stagger across groups is what the native
  Appearance page does; a placeholder repeating its label is native Go Build's own behaviour). Every
  one of the three came from measuring *our* surface carefully and never measuring the platform's:
  a screenshot of our panel alone is enough to produce a confident, wrong finding.
- **THE SCREENSHOT PASS IS THE GATE:** A change that adds or restructures a visible surface is
  verified live via the `verify-in-ide` flow. Unit tests cannot observe alignment, spacing, elision,
  casing, or a component that was silently re-parented — every defect in BUG-448/449 shipped through a
  green suite. Two things here *are* cheaply testable and should be: component-tree invariants (each
  tab still owns its detail pane) and a bundle assertion that no control label is Title Case.
- **SCOPE:** These bind **new and restructured** UI. Do not open a retroactive sweep; the surviving
  `FormBuilder` run-config editors are acceptable until touched. Fix a surface when you are already
  editing it.
