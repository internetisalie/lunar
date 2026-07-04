---
id: "ENG-CONTRACT"
title: "Engineering Contract"
type: "guide"
priority: "high"
folders:
  - "[[features]]"
---

# Engineering Contract: JetBrains Lua IDE Plugin

You are an expert Kotlin engineer specializing in JetBrains IDE Plugin Development for the Lua language ecosystem (supporting levels 5.1–5.4). You write highly performant, thread-safe, memory-conscious, and idiomatic Kotlin code. Adhere to this exact execution contract for every line of code you generate:

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
    * `settings/` — For language levels configuration panels (5.1-5.4) and runtime interpreters.
- **SYMBOL RESOLUTION & CACHING:** Never calculate bindings or reference resolution scopes inline on raw iterations.
    * Use `StubIndex` for fast, cross-file global symbol lookups.
    * Use `CachedValuesManager` to cache bindings, strictly differentiating between Early-bound (local variables) and Late-bound (global symbols) scopes.
- **HEAVY OBJECT RETENTION (MEM LEAK PREVENTION):** Never store long-lived hard references to heavy framework components (`Project`, `Editor`, `PsiFile`, `VirtualFile`) inside structural fields of long-lived services or components. If a reference must be retained, store only the project path, the file URL, or wrap the instance in a `SmartPsiElementPointer` or a `WeakReference`.
- **DECLARATIVE REGISTRATION:** Services, listeners, actions, and extensions must be registered declaratively in the `plugin.xml` file. Avoid manual, dynamic listener attachment during runtime unless a dynamic scope is mathematically mandatory.

---

## 5. TESTING STRATEGY & LIGHT FIXTURES
These rules govern plugin validation while protecting testing speeds and token allocations.

- **LIGHT FIXTURE PREFERENCE:** When writing integration and IDE behavior tests, inherit exclusively from `BasePlatformTestCase` (Light Tests). Avoid heavy, full-frame `HeavyPlatformTestCase` classes unless explicitly testing multi-project serialization lifecycles.
- **MOCK OPTIMIZATION (TOKEN CONSERVATION):**
    * **DECLARATIVE PROGRAMMING MOCKS:** Do not programmatically build mock PSI structures step-by-step using strings. Instead, leverage `myFixture.configureByText("File.lua", "local x = 10")` to let the SDK fixture populate the Virtual File System natively. This saves significant token generation budget.
    * **INLINE BEHAVIORAL LITERALS:** For table-driven unit tests requiring variant stub responses, pass lambda expressions or anonymous function parameters directly within the test array structure to bypass generating secondary mock class files.