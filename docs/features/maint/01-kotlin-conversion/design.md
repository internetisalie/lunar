---
id: MAINT-01-DESIGN
title: "Technical Design"
type: design
parent_id: MAINT-01
folders:
  - "[[features/maint/01-kotlin-conversion/requirements|requirements]]"
---

# Technical Design: MAINT-01 — Kotlin Conversion

## 1. Architecture Overview

### Current State
Six hand-written Java files remain under `src/main/java/net/internetisalie/lunar/`. This feature
converts **four** of them; rows 1 and 5 are **deferred to MAINT-19** (see §2.1/§2.5 and
requirements.md §Out of Scope):

| # | File | Package | Kind | This feature |
|---|------|---------|------|--------------|
| 1 | `LuaTokenTypes.java` | `lang.lexer` | `interface` of `IElementType` constants | **DEFERRED → MAINT-19** |
| 2 | `LuaPsiUtils.java` | `lang.psi` | `final class` of static PSI helpers | Convert (§2.2) |
| 3 | `LuaTokenType.java` | `lang.psi` | `class extends IElementType` | Convert (§2.3) |
| 4 | `LuaCatsElementType.java` | `luacats.lang.lexer` | `class extends IElementType` | Convert (§2.4) |
| 5 | `LuaCatsTokenTypes.java` | `luacats.lang.lexer` | `interface` of `IElementType` constants + `TokenSet` | **DEFERRED → MAINT-19** |
| 6 | `LuaPluginDisposable.java` | (root) `net.internetisalie.lunar` | `@Service` dual-level `Disposable` | Convert (§2.6) |

Everything else under `src/main/java/` is **generated** (`src/main/gen/...`) and out of scope
(requirements.md §Out of Scope).

### Prior Art in This Repo
Searched for: any existing Kotlin `object`/`class` pattern already used for an `IElementType`
holder or an `IElementType` subclass, to match style.
- `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaElementType.kt` — **already Kotlin**,
  `class LuaElementType(private val name: String) : IElementType(name, LuaLanguage)` with
  `override fun toString(): String = name`. This is the exact shape `LuaTokenType.kt` and
  `LuaCatsElementType.kt` must follow (constructor-property + override, no companion, no
  singleton wrapper) — **MAINT-01-03 and MAINT-01-04 extend this established pattern**, they do
  not invent a new one.
- No existing Kotlin `object` holds `IElementType` constants yet (`LuaElementTypes` itself is
  still the generated Java file, out of scope) — `LuaTokenTypes.kt`/`LuaCatsTokenTypes.kt`
  (MAINT-01-01/05) are the first Kotlin `object`s of this shape; the design specifies their exact
  form below since there is no prior Kotlin example to copy.
- No existing Kotlin `@Service` dual-level class in this repo was found via
  `grep -rln "Service.Level.APP, Service.Level.PROJECT"` under `src/main/kotlin/` (no matches) —
  `LuaPluginDisposable.kt` (MAINT-01-06) is the first; the design specifies the exact
  companion-object shape since `@Service` annotations on a Kotlin class are a well-documented but
  unprecedented-in-this-repo platform pattern.

### Target State
Each of the 6 files is replaced 1:1 by a Kotlin file at the mirrored path under
`src/main/kotlin/net/internetisalie/lunar/...`, with the `.java` original deleted. No consumer
file changes. No generated file changes.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.lang.lexer.LuaTokenTypes` — DEFERRED → MAINT-19

> ⛔ **This conversion is deferred and NOT part of MAINT-01.** The `interface → object` shape below
> is **unsafe**: the generated JFlex lexer `src/main/gen/.../lang/lexer/_LuaLexer.java:13` is
> `class _LuaLexer implements FlexLexer, LuaTokenTypes` (from `lua.flex:17-18`'s
> `%implements FlexLexer, LuaTokenTypes`) and uses the constants **bare/unqualified** (`return WS;`,
> `return IDENTIFIER;` at `_LuaLexer.java:680,750,780`). A Kotlin `object` is a final class Java
> cannot `implements`; a Kotlin `interface` cannot hold initialized `val` constants. Converting this
> requires editing `lua.flex` (drop `%implements`, qualify tokens, add `@JvmField`) **and** a manual
> JFlex regeneration — owned by **MAINT-19** (platform.syntax migration). The text below is retained
> only as the eventual target shape for MAINT-19, not as MAINT-01 work.

- **File**: `src/main/kotlin/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.kt` (new; replaces
  `src/main/java/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.java`)
- **Responsibility**: Hold every lexer-level `IElementType` token constant as a `val`, constructed
  exactly once at object-init.
- **Threading**: None (pure static data, no PSI/VFS access).
- **Collaborators**: `net.internetisalie.lunar.lang.psi.LuaElementType` (already Kotlin),
  `com.intellij.psi.TokenType.BAD_CHARACTER`/`WHITE_SPACE`.
- **Shape (CORRECTED — why this is deferred)**: A naive earlier grounding claimed
  `grep -rn "implements LuaTokenTypes" src/` returns no matches, concluding an `object` was safe.
  That grep was **wrong** — it is too literal: the real declaration is
  `implements FlexLexer, LuaTokenTypes` (interface second in a comma list), which the adjacent-token
  pattern misses. The broadened `grep -rnE "implements.*LuaTokenTypes" src/` (and the `%implements`
  line in `lua.flex`) **do** match `_LuaLexer`. So there IS a subtype that inherits these fields:
  the generated lexer. An `object` (final class) cannot be `implements`-ed; this conversion is
  therefore deferred to MAINT-19. For MAINT-19, the target shape will additionally need either
  `@JvmField`/`const` on each constant (so generated Java can read them statically) or a move to
  `com.intellij.platform.syntax`'s `SyntaxElementType` holder + converter factory.
- **Key API**:
  ```kotlin
  object LuaTokenTypes {
      val WRONG: IElementType = TokenType.BAD_CHARACTER
      val NL_BEFORE_LONGSTRING: IElementType = LuaElementType("newline after longstring start bracket")
      val WS: IElementType = TokenType.WHITE_SPACE
      val NEWLINE: IElementType = LuaElementType("new line")
      // ... one `val` per constant in the current file, same name, same construction
      // expression as today (LuaElementType(...) or a TokenType.* alias), same order.
  }
  ```
- **Full constant list to preserve** (verbatim names/values from
  `src/main/java/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.java:33-152`): `WRONG`,
  `NL_BEFORE_LONGSTRING`, `WS`, `NEWLINE`, `MARKER`, `SHEBANG`, `LONGCOMMENT`, `SHORTCOMMENT`,
  `LONGCOMMENT_BEGIN`, `LONGCOMMENT_END`, `IDENTIFIER`, `NUMBER`, `STRING`, `LONGSTRING`,
  `LONGSTRING_BEGIN`, `LONGSTRING_END`, `DIV`, `MULT`, `LPAREN`, `RPAREN`, `LBRACK`, `RBRACK`,
  `LCURLY`, `RCURLY`, `COLON`, `COMMA`, `DOT`, `ASSIGN`, `SEMI`, `EQ`, `NE`, `PLUS`, `MINUS`, `GE`,
  `GT`, `EXP`, `LE`, `LT`, `ELLIPSIS`, `CONCAT`, `GETN`, `MOD`, `INTDIV`, `AMP`, `NEG`, `PIPE`,
  `BSR`, `BSL`, `IF`, `ELSE`, `ELSEIF`, `WHILE`, `WITH`, `THEN`, `FOR`, `IN`, `RETURN`, `BREAK`,
  `CONTINUE`, `TRUE`, `FALSE`, `NIL`, `FUNCTION`, `DO`, `NOT`, `AND`, `OR`, `LOCAL`, `REPEAT`,
  `UNTIL`, `END`, `GOTO`, `GLOBAL`.

### 2.2 `net.internetisalie.lunar.lang.psi.LuaPsiUtils`
- **File**: `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaPsiUtils.kt` (new; replaces the
  `.java` file)
- **Responsibility**: Stateless PSI-tree traversal/helper functions.
- **Threading**: Read-only traversal methods (`nestingLevel`, `blockNestingLevel`, `elementAfter`,
  `findEnclosingBlock`, `getElementLineNumber`, `getElementEndLineNumber`, `createRange`,
  `nodeType`, `findNextSibling`, `findPreviousSibling`, `toPsiElementArray`,
  `hasDirectChildErrorElements`, `processChildDeclarationsS`, `processChildDeclarations`) require
  no write action — same as today (no `WriteCommandAction` wrapping in the current Java). The one
  mutating method, `replaceElement`, performs PSI-tree writes and per the engineering contract
  must run inside a caller-owned `WriteCommandAction.runWriteCommandAction(project) { ... }` — this
  was already true of the Java version (no internal wrapping there either) and **must remain true
  in Kotlin**: do not add a `WriteCommandAction` wrapper inside `LuaPsiUtils.kt` itself, since
  `LuaPsiUtils` has no `Project` reference available to construct one, and doing so would also
  silently change behavior for any future caller that already wraps its own write action (nested
  write actions are exactly the kind of behavior change this conversion must avoid).
- **Collaborators**: `com.intellij.lang.ASTNode`, `com.intellij.openapi.util.TextRange`,
  `com.intellij.psi.PsiElement`/`ResolveState`/`FileViewProvider`/`PsiErrorElement`,
  `com.intellij.psi.scope.PsiScopeProcessor`, `com.intellij.psi.tree.IElementType`,
  `com.intellij.psi.util.PsiTreeUtil` (imported, unused in the current file body — verify on
  conversion whether it is genuinely unused and drop it; do not carry forward an unused import),
  `com.intellij.util.IncorrectOperationException`, `net.internetisalie.lunar.lang.psi.LuaBlock`
  (used by the private `isValidContainer` helper).
- **Shape**: Kotlin `object LuaPsiUtils` (not a `class` with private constructor, not top-level
  functions) — matches the "static utility holder" intent of the original `final class` with only
  static members, and keeps `LuaPsiUtils.methodName(...)` call syntax identical for any future
  Kotlin or Java caller (an `object`'s members are exposed to Java callers as
  `LuaPsiUtils.INSTANCE.methodName(...)` UNLESS annotated `@JvmStatic` — **every method must be
  annotated `@JvmStatic`** so that the zero-Java-caller-today fact does not become a silent API
  break if a Java caller is ever added; this is the one place this conversion must add an
  annotation beyond a bare port).
- **Key API** (every method, full signature, mirroring requirements.md MAINT-01-02):
  ```kotlin
  object LuaPsiUtils {
      @JvmStatic
      fun nestingLevel(element: PsiElement): Int

      @JvmStatic
      fun blockNestingLevel(element: PsiElement): Int

      @JvmStatic
      fun elementAfter(element: PsiElement): PsiElement? {
          val node = element.node ?: return null
          val next = node.treeNext ?: return null
          return next.psi
      }

      @JvmStatic
      fun findEnclosingBlock(element: PsiElement): PsiElement?

      private fun isValidContainer(element: PsiElement): Boolean = element is LuaBlock

      @JvmStatic
      fun processChildDeclarationsS(
          parentContainer: PsiElement,
          processor: PsiScopeProcessor,
          resolveState: ResolveState,
          parent: PsiElement,
          place: PsiElement,
      ): Boolean

      @JvmStatic
      fun processChildDeclarations(
          element: PsiElement,
          processor: PsiScopeProcessor,
          substitutor: ResolveState,
          lastParent: PsiElement?,
          place: PsiElement,
      ): Boolean

      @JvmStatic
      fun getElementLineNumber(element: PsiElement): Int

      @JvmStatic
      fun getElementEndLineNumber(element: PsiElement): Int

      @JvmStatic
      fun createRange(node: PsiElement): TextRange =
          TextRange.from(node.textOffset, node.textLength)

      @JvmStatic
      fun nodeType(element: PsiElement): IElementType? = element.node?.elementType

      @JvmStatic
      fun findNextSibling(start: PsiElement, ignoreType: IElementType): PsiElement?

      @JvmStatic
      fun findPreviousSibling(start: PsiElement, ignoreType: IElementType): PsiElement?

      @JvmStatic
      @Throws(IncorrectOperationException::class)
      fun replaceElement(original: PsiElement, replacement: PsiElement): PsiElement

      @JvmStatic
      fun toPsiElementArray(collection: Collection<PsiElement>): Array<PsiElement>

      @JvmStatic
      fun hasDirectChildErrorElements(element: PsiElement): Boolean
  }
  ```
  `processChildDeclarationsS`/`processChildDeclarations`'s 5-argument signature exceeds the
  engineering contract's 3-argument tripwire — this is a **pre-existing** signature shape being
  ported as-is (not new code being written), so it is not a violation to introduce; do not
  "fix" it by collapsing parameters into a context object as part of this syntax-preserving
  conversion (that would be a behavior-neutral but API-breaking change outside this feature's
  scope — track it as a follow-up if desired, not silently inside MAINT-01).

### 2.3 `net.internetisalie.lunar.lang.psi.LuaTokenType`
- **File**: `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaTokenType.kt` (new)
- **Responsibility**: One `IElementType` instance per lexer token, with a debug `toString()`
  prefixed `"LuaTokenType."`.
- **Threading**: None.
- **Collaborators**: `com.intellij.psi.tree.IElementType` (supertype),
  `net.internetisalie.lunar.lang.LuaLanguage` (already a Kotlin `object`/companion — confirmed via
  `LuaElementType.kt:4,6` passing `LuaLanguage` directly as the `IElementType` constructor's
  `Language` argument, no `.INSTANCE` suffix needed in Kotlin).
- **Shape**: A normal Kotlin `class` (matches `LuaElementType.kt`'s established pattern exactly —
  see §1 Prior Art). **Must NOT become an `object`** — the generated, out-of-scope
  `LuaElementTypes.java` constructs ~85 separate `new LuaTokenType("...")` instances, one per
  token; an `object` would collapse them all into a shared singleton, breaking every token's
  distinct identity (and violating the `IElementType` registry's requirement that each distinct
  token type be its own registered instance — see §3 below).
- **Key API**:
  ```kotlin
  class LuaTokenType(debugName: String) : IElementType(debugName, LuaLanguage) {
      override fun toString(): String = "LuaTokenType." + super.toString()
  }
  ```
  The constructor parameter drops the Java version's `@NotNull @NonNls` annotations because
  Kotlin's `String` (non-nullable by default) and the lack of an idiomatic `@NonNls` equivalent in
  this codebase's existing Kotlin (confirmed: `grep -rn "@NonNls" src/main/kotlin/` → no matches)
  make them redundant — this is a no-op semantic change (the type system already enforces
  non-null), not a behavior change.

### 2.4 `net.internetisalie.lunar.luacats.lang.lexer.LuaCatsElementType`
- **File**: `src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsElementType.kt` (new)
- **Responsibility**: One `IElementType` instance per LuaCATS lexer token, with a debug
  `toString()` returning the raw `debugName` (no prefix — distinct from `LuaTokenType`).
- **Threading**: None.
- **Collaborators**: `com.intellij.psi.tree.IElementType`, `net.internetisalie.lunar.lang.LuaLanguage`.
- **Shape**: Normal Kotlin `class`, same reasoning as §2.3 (the generated, out-of-scope
  `LuaCatsElementTypes.java` constructs ~100 separate instances).
- **Key API**:
  ```kotlin
  class LuaCatsElementType(private val debugName: String) : IElementType(debugName, LuaLanguage) {
      override fun toString(): String = debugName
  }
  ```
  The current Java stores `debugName` as a redundant field (the supertype already stores it
  internally) purely to implement `toString()` without calling `super.toString()` — preserved
  here as `private val debugName` for the same reason; this is intentionally NOT unified with
  `LuaTokenType`'s `super.toString()`-based approach, because the two classes' `toString()` output
  formats are different and both are asserted on by name-equality in existing/new tests (TC 3/4 in
  requirements.md) — do not "simplify" them into one shared base class as part of this conversion.

### 2.5 `net.internetisalie.lunar.luacats.lang.lexer.LuaCatsTokenTypes` — DEFERRED → MAINT-19

> ⛔ **Deferred, NOT part of MAINT-01** — same constraint as §2.1. The generated
> `src/main/gen/.../luacats/lang/lexer/_LuaCatsLexer.java:25` is
> `public class _LuaCatsLexer implements FlexLexer, LuaCatsTokenTypes` (from `luacats.flex:24`).
> Retained below only as the MAINT-19 target shape.

- **File**: `src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsTokenTypes.kt` (new)
- **Responsibility**: Hold every LuaCATS lexer `IElementType` constant plus the `LUACATS_TOKENS`
  membership set.
- **Threading**: None.
- **Collaborators**: `com.intellij.psi.TokenType.BAD_CHARACTER`,
  `com.intellij.psi.tree.TokenSet`, `LuaCatsElementType` (§2.4).
- **Shape (CORRECTED)**: the `grep -rn "implements LuaCatsTokenTypes" src/` "no matches" claim is
  the same false negative as §2.1 — `_LuaCatsLexer` declares `implements FlexLexer, LuaCatsTokenTypes`
  and the literal grep misses the comma list. Deferred to MAINT-19 for the same reason.
- **Key API**:
  ```kotlin
  object LuaCatsTokenTypes {
      val LCATS_DASHES: IElementType = LuaCatsElementType("LCATS_DASHES")
      val LCATS_WHITESPACE: IElementType = LuaCatsElementType("LCATS_WHITESPACE")
      val LCATS_TEXT: IElementType = LuaCatsElementType("LCATS_TEXT")
      val LCATS_NAME: IElementType = LuaCatsElementType("LCATS_NAME")
      val LCATS_STRING: IElementType = LuaCatsElementType("LCATS_STRING")
      val LCATS_SYMBOL: IElementType = LuaCatsElementType("LCATS_SYMBOL")
      val LCATS_TAG: IElementType = LuaCatsElementType("LCATS_TAG")
      val LCATS_KEYWORD: IElementType = LuaCatsElementType("LCATS_KEYWORD")
      val LCATS_CODE: IElementType = LuaCatsElementType("LCATS_CODE")
      val LCATS_NUMBER: IElementType = LuaCatsElementType("LCATS_NUMBER")
      val LCATS_BAD_CHARACTER: IElementType = TokenType.BAD_CHARACTER

      val LUACATS_TOKENS: TokenSet = TokenSet.create(
          LCATS_DASHES,
          LCATS_WHITESPACE,
          LCATS_TEXT,
          LCATS_NAME,
          LCATS_STRING,
          LCATS_SYMBOL,
          LCATS_TAG,
          LCATS_KEYWORD,
          LCATS_CODE,
          LCATS_NUMBER,
      )
  }
  ```
  `LCATS_BAD_CHARACTER` is declared but intentionally **excluded** from `LUACATS_TOKENS`,
  preserving the asymmetry already present in
  `src/main/java/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsTokenTypes.java:20-33` — this
  is a faithful port, not an oversight to "fix".

### 2.6 `net.internetisalie.lunar.LuaPluginDisposable`
- **File**: `src/main/kotlin/net/internetisalie/lunar/LuaPluginDisposable.kt` (new)
- **Responsibility**: App-level and project-level disposable parent for plugin-owned listeners/
  connections to attach to (per the IntelliJ Platform's `Disposer` convention).
- **Threading**: None directly; `dispose()` is invoked by the platform's disposal machinery, not
  called directly by plugin code.
- **Collaborators**: `com.intellij.openapi.Disposable`,
  `com.intellij.openapi.application.ApplicationManager`,
  `com.intellij.openapi.components.Service`, `com.intellij.openapi.project.Project`.
- **Shape**: A normal Kotlin `class` (NOT `object`) — the platform's service container
  instantiates this type via reflection per `@Service`-declared level (one app-level instance,
  one instance per open project); a Kotlin `object` is a single JVM-wide singleton and cannot
  represent "one instance per project," so it would be structurally wrong here, not just
  stylistically non-idiomatic.
- **Key API**:
  ```kotlin
  @Service(Service.Level.APP, Service.Level.PROJECT)
  class LuaPluginDisposable : Disposable {
      override fun dispose() = Unit

      companion object {
          @JvmStatic
          fun getInstance(): Disposable =
              ApplicationManager.getApplication().getService(LuaPluginDisposable::class.java)

          @JvmStatic
          fun getInstance(project: Project): Disposable =
              project.getService(LuaPluginDisposable::class.java)
      }
  }
  ```
  Both `getInstance` overloads are `@JvmStatic` companion functions so that
  `LuaPluginDisposable.getInstance()` / `LuaPluginDisposable.getInstance(project)` continue to
  resolve identically from both Kotlin and any future Java caller, exactly mirroring the original
  two `public static` Java methods. No `plugin.xml` entry is added or required — `@Service` is
  itself the declarative registration mechanism (confirmed: no existing `<applicationService>`/
  `<projectService>` entry for this class in `src/main/resources/META-INF/plugin.xml` today, and
  none should be added, since adding one would conflict with annotation-based registration).

## 3. Algorithms

### 3.1 `IElementType` singleton-construction invariant (applies to §2.3, §2.4; §2.1/§2.5 deferred to MAINT-19)
- **Input → Output**: N/A — this is a structural invariant, not a runtime algorithm.
- **Rule**: Every `IElementType` subclass instance referenced by this feature
  (`LuaElementType`, `LuaTokenType`, `LuaCatsElementType`) must be constructed **exactly once**,
  as a `val` initializer of a Kotlin `object` (for the constant-holder files, §2.1/§2.5) or as a
  field initializer in the generated, out-of-scope `LuaElementTypes.java`/
  `LuaCatsElementTypes.java` (for the `IElementType`-subclass files, §2.3/§2.4) — never inside a
  function body that could be invoked more than once (e.g. never inside `nodeType()`, never
  inside a lexer `getTokenType()` override, never as a default-parameter expression evaluated
  per-call).
- **Why this matters here specifically**: Kotlin `object`s are guaranteed by the JVM/Kotlin
  runtime to run their property initializers exactly once, on first touch, behind a class-init
  lock — this is a strictly equivalent (not weaker) guarantee to Java's `static final` field
  initialization, so converting `interface` constants to `object` `val`s does not change
  cardinality. The risk this guards against (documented in `CLAUDE.md`/`AGENTS.md` lessons
  learned) is a hypothetical *future* refactor that moves a `val` initializer into a function —
  the design explicitly forbids that for all 4 affected files.
- **Verification**: TC 1 (requirements.md) asserts referential (`===`) identity of
  `LuaTokenTypes.IDENTIFIER` is preserved; the full test suite (`tooling/gce-builder/gce-builder.sh
  run test`) is the practical guard against registry exhaustion, since that failure mode
  (`ArrayIndexOutOfBoundsException`) only manifests under the bulk-parse load the suite exercises.

### 3.2 `LuaPsiUtils.replaceElement` fallback chain (applies to §2.2)
- **Input → Output**: `(original: PsiElement, replacement: PsiElement) -> PsiElement`
- **Steps** (verbatim port of
  `src/main/java/net/internetisalie/lunar/lang/psi/LuaPsiUtils.java:254-281`):
  1. Attempt `original.replace(replacement)`. If it returns normally, return that result —
     **done**.
  2. If step 1 throws `IncorrectOperationException` or `UnsupportedOperationException`, swallow it
     (no logging in the original — preserve as-is) and fall through to step 3.
  3. Read `original.parent`. If non-null: call `parent.addBefore(replacement, original)` to insert
     the replacement immediately before the original, then call `original.delete()` to remove the
     original, then return the inserted element — **done**.
  4. If `parent` is null (step 3's else-branch): fall back to raw AST mutation —
     `original.node.replaceAllChildrenToChildrenOf(replacement.node)`, then return `original`
     itself (not the replacement) — **done**.
- **Rules / edge handling**: The two-exception catch in step 2 must remain exactly those two
  types (not a broad `catch (e: Exception)`) — narrowing or widening the catch changes which
  failures fall through to the parent-based retry, which is an observable behavior change this
  syntax-preserving conversion must not introduce. The original Java's `finally {}` block (line
  279-280) is an empty no-op and is dropped in the Kotlin port (Kotlin does not require a
  `finally` to pair with `try`/`catch`; an empty `finally` has no behavioral effect to preserve).
- **Complexity / bounds**: O(1) PSI operations; no recursion.
- **Caller obligation (unchanged)**: the function does not open its own
  `WriteCommandAction` — callers must already be inside one. This was true in the Java version (no
  internal wrapping) and remains true in Kotlin; do not add one (see §2.2).

### 3.3 No other non-trivial algorithms
`nestingLevel`/`blockNestingLevel`/`findEnclosingBlock` are simple parent-walk loops with no
ordering/tie-break/cycle-handling subtleties beyond "stop at null" (PSI parent chains are
acyclic by platform construction); `findNextSibling`/`findPreviousSibling` are simple
linear-scan-skip-matching-type loops. These are mechanical line-for-line Kotlin ports of the
existing Java loops and do not require separate algorithm specs beyond the signatures already
given in §2.2.

## 4. External Data & Parsing
Not applicable — this feature converts internal Java source to Kotlin source; it consumes no
CLI output, file contents, or network responses. (Stated explicitly per the design template's
requirement to address this section even when N/A.)

## 5. Data Flow

### Example 1: Lexing a `(` token end-to-end after conversion
1. `_LuaLexer` (generated JFlex lexer, untouched) emits a raw token.
2. `LuaLexer.kt` (existing, untouched) looks up `LuaTokenTypes.LPAREN` (now resolved from the
   converted `object LuaTokenTypes` in §2.1) and remaps it via the `tokenTypes` map to
   `LuaElementTypes.LPAREN` (generated, untouched, itself constructed via
   `new LuaTokenType("(")` — the converted `class LuaTokenType` from §2.3).
3. `LuaElementTypes.LPAREN.toString()` (inherited from `LuaTokenType.toString()`, §2.3) returns
   `"LuaTokenType.("` — unchanged from pre-conversion behavior. TC 3 in requirements.md asserts
   this directly on a fresh instance.

### Example 2: Resolving a PSI reference via `LuaPsiUtils.findEnclosingBlock`
1. A future caller (none exists today — see requirements.md "Confirmed dead code") would call
   `LuaPsiUtils.findEnclosingBlock(someElement)`.
2. The converted `object LuaPsiUtils` (§2.2) walks `element.context` ancestors, calling the
   private `isValidContainer` check (`element is LuaBlock`) at each step, returning the first
   match or `null` — identical control flow to the Java `while` loop it replaces.

## 6. Edge Cases
- **`nodeType()` on a node-less element**: returns `null` (typed `IElementType?`), not a thrown
  NPE — this is the one signature whose nullability annotation is added during conversion (see
  §2.2 and requirements.md MAINT-01-08); covered by TC 6.
- **`replaceElement` with a parent-less, exception-throwing original**: falls through to the raw
  `ASTNode.replaceAllChildrenToChildrenOf` branch — see §3.2 step 4; no existing test exercises
  this today (the class is dead code), so this remains an unverified-by-test edge case carried
  forward unchanged, not newly introduced risk.
- **`LuaPluginDisposable.getInstance()` called with no project in scope**: resolves the app-level
  instance via `ApplicationManager` — unambiguous because Kotlin (like Java) resolves the no-arg
  overload independently of the 1-arg overload; covered by TC 9.
- **`LuaCatsTokenTypes.LCATS_BAD_CHARACTER` vs `LUACATS_TOKENS` membership**: intentionally
  asymmetric (excluded) — see §2.5; covered by TC 5.

## 7. Integration Points
No new `plugin.xml` registrations. `LuaPluginDisposable` remains registered purely via its
`@Service(Service.Level.APP, Service.Level.PROJECT)` annotation (confirmed no existing
`<applicationService>`/`<projectService>` entry exists or is needed — see §2.6). No extension
points, listeners, or actions are added by this feature.

```xml
<!-- plugin.xml — NO CHANGES required by this feature. -->
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|---------------------------|
| MAINT-01-01 | M | **Deferred → MAINT-19** (§2.1 retained as target shape) |
| MAINT-01-02 | M | §2.2, §3.2, §3.3 |
| MAINT-01-03 | M | §2.3, §3.1 |
| MAINT-01-04 | M | §2.4, §3.1 |
| MAINT-01-05 | M | **Deferred → MAINT-19** (§2.5 retained as target shape) |
| MAINT-01-06 | M | §2.6 |
| MAINT-01-07 | M | §5 (Data Flow examples), Requirement Coverage as a whole (no consumer edits implied by any §2.x shape) |
| MAINT-01-08 | M | §2.2 (`nodeType` nullability, wildcard-import removal), §2.3 (drop redundant `@NotNull`/`@NonNls`) |
| MAINT-01-09 | S | Addressed procedurally in implementation-plan.md (ktlintCheck gate per phase), not a design-time concern |
| MAINT-01-10 | S | Out of design.md's scope — a documentation edit, tracked in implementation-plan.md |

## 9. Alternatives Considered
- **Collapsing `LuaTokenType` and `LuaCatsElementType` into one shared base class** (both extend
  `IElementType` and override `toString()`): rejected — their `toString()` formats differ
  (prefixed vs. unprefixed) and are independently asserted on by name/format in tests (TC 3 vs.
  TC 4); unifying them is an unrelated refactor outside a syntax-preserving conversion's scope.
- **Making `LuaPluginDisposable` an `object` with a single `getInstance()`**: rejected — the
  platform genuinely needs two distinct instances (app vs. per-project), which an `object`
  (one JVM-wide instance) cannot represent; see §2.6.
- **Pruning `LuaPsiUtils`'s unused methods since it has zero callers today**: rejected — out of
  scope for a 1:1 conversion (requirements.md explicitly calls this out); a future cleanup
  feature can independently decide whether to delete the dead class.
- **Converting the generated `LuaElementTypes.java`/`LuaCatsElementTypes.java` in the same pass**:
  rejected — explicitly out of scope (requirements.md §Out of Scope); these are regenerated by a
  separate manual IDE workflow (`CLAUDE.md`) and converting hand-maintained vs. generated sources
  are different-risk-profile efforts that should not be bundled.
- **Converting `LuaTokenTypes`/`LuaCatsTokenTypes` to a Kotlin `object` (or `interface`) within
  MAINT-01**: rejected → deferred to **MAINT-19**. The generated lexers `_LuaLexer`/`_LuaCatsLexer`
  `implements` these interfaces to inherit the token constants as bare fields (`%implements` in
  `lua.flex`/`luacats.flex`). A Kotlin `object` is a final class Java cannot `implements`; a Kotlin
  `interface` cannot hold initialized `val` constants. Either path forces `.flex` edits + a manual
  JFlex regeneration (and `@JvmField`, or a move to `com.intellij.platform.syntax`), which is the
  platform.syntax migration — out of scope for a syntax-preserving port. Keeping them as Java
  preserves the generated lexers verbatim (the pattern all three Lua reference plugins also use).

## 10. Open Questions

_None — feature has cleared the planning bar._
