---
id: INSP-01-DESIGN
title: "Technical Design"
type: design
parent_id: INSP-01
status: "done"
priority: "medium"
folders:
  - "[[features/inspections/01-undeclared-variable/requirements|requirements]]"
---

# Technical Design: INSP-01 Undeclared Variable

## 1. Architecture Overview

### Current State
The plugin already resolves names through `net.internetisalie.lunar.lang.LuaNameReference`
(returned by `LuaNameRefBaseImpl.getReference()` in
`net.internetisalie.lunar.lang.psi.LuaBaseElements`). `multiResolve` performs a two-phase
lookup:

1. **Phase 1 (local)** walks up the PSI tree with `LuaScopeProcessor`, processing
   declarations via `LuaBlock.processDeclarations` (`LuaBlockExt.kt`). That method already
   enforces Lua **early binding**: the loop `break`s when
   `statement.textOffset >= lastParent.textOffset` (`LuaBlockExt.kt:31`), and `multiResolve`
   passes the reference element as both `lastParent` and `place` (`LuaNameReference.kt:50`).
   A use textually before its `local` therefore does **not** bind to that local.
2. **Phase 2 (external)** consults `PlatformLibraryIndex.getPackageFiles(project)` (standard
   library), `require`-imported files, and the stub indexes `LuaGlobalDeclarationIndex`,
   `LuaClassNameIndex`, `LuaAliasIndex`.

No inspection currently surfaces *failed* resolution to the user. `LuaProjectSettings.State`
has `suppressUnderscorePrefixedGlobals: Boolean` but **no** allowlist of additional globals.

### Target State
A new `LocalInspectionTool` visits each `LuaNameRef` in read position and reports a problem
when the name resolves to nothing and is not exempt (standard global, allowlisted global,
underscore-suppressed, or comment-suppressed). The inspection delegates all resolution to the
existing `LuaNameReference`; it adds only **classification** (read vs. declaration vs. write)
and **exemption** logic.

```
LuaNameRef ──visit──▶ LuaUndeclaredVariableInspection.buildVisitor
                         │  isReadUse?  (§3.1)
                         │  reference.multiResolve(false) empty?  (§3.2 step 8)
                         │  not exempt? (§3.2)  → registerProblem(GENERIC_ERROR_OR_WARNING, "Undeclared variable '<name>'")
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection`
- **Responsibility**: Flag read-position `LuaNameRef`s whose reference does not resolve and
  which are not exempt.
- **Threading**: Runs in the inspection read action supplied by the platform (no extra
  read/write action needed; never blocks the EDT — resolution is the platform's cached path).
- **Collaborators**: `LuaNameReference.multiResolve()` (§3.2 step 8), `LuaStandardGlobals`
  (§2.2), `LuaInspectionSuppression` (§2.3), `LuaProjectSettings` (§2.4).
- **Key API**:
  ```kotlin
  class LuaUndeclaredVariableInspection : LocalInspectionTool() {
      override fun getShortName(): String = "LuaUndeclaredVariable"
      override fun getGroupDisplayName(): String = "Lua"
      override fun getDisplayName(): String = "Undeclared variable"
      override fun isEnabledByDefault(): Boolean = true
      override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
          object : net.internetisalie.lunar.lang.psi.LuaVisitor() {
              override fun visitNameRef(o: LuaNameRef) = inspectNameRef(o, holder)
          }

      // ≤30 lines; helpers below carry the logic
      private fun inspectNameRef(ref: LuaNameRef, holder: ProblemsHolder)
  }
  ```
  > Uses the generated visitor base `net.internetisalie.lunar.lang.psi.LuaVisitor`
  > (`src/main/gen/.../LuaVisitor.java`, which declares `visitNameRef(LuaNameRef)` and extends
  > `PsiElementVisitor`); `LuaNameRefImpl.accept` dispatches to it. The platform calls
  > `element.accept(visitor)` per element during inspection, so `visitNameRef` fires for every
  > `LuaNameRef`. The class lives under a new `analysis/inspections/` package (sibling to the
  > existing `analysis/LuaTypeAssignabilityInspection`).

### 2.2 `net.internetisalie.lunar.analysis.inspections.LuaStandardGlobals`
- **Responsibility**: Deterministic allowlist of built-in global names per language level, so
  TC-04 passes even when no platform library is configured (the index path is a superset, not
  a prerequisite).
- **Threading**: Pure in-memory sets; thread-safe (immutable).
- **Key API**:
  ```kotlin
  object LuaStandardGlobals {
      fun forLevel(level: LuaLanguageLevel): Set<String>
      fun contains(name: String, level: LuaLanguageLevel): Boolean
  }
  ```
- **Data** (exact membership — see §3.3).

### 2.3 `net.internetisalie.lunar.analysis.inspections.LuaInspectionSuppression`
- **Responsibility**: Decide whether a given source offset/name is suppressed by a nearby
  `---@diagnostic` or `-- luacheck: ignore` comment.
- **Threading**: Read action. Collects comment leaves by element type — `PsiTreeUtil
  .collectElements(file) { it.elementType in LuaSyntax.CommentTokens }` — which covers
  `LuaElementTypes.SHORTCOMMENT` (line comments, holding `-- luacheck: ignore …`) and
  `LuaLazyElementTypes.LUACATS_COMMENT` (the lazy-parsed `---@diagnostic …` doc comments);
  matching is on each leaf's `.text`. Cache the parsed suppression map per-file via
  `CachedValuesManager` keyed on the `PsiFile` (invalidated on PSI change).
- **Key API**:
  ```kotlin
  object LuaInspectionSuppression {
      // diagnosticId is fixed to "undefined-global" for this inspection
      fun isSuppressed(ref: LuaNameRef, name: String, diagnosticId: String): Boolean
  }
  ```

### 2.4 `LuaProjectSettings.State` — new `additionalGlobals` field
- **Responsibility**: Persist the user's "Additional Globals" allowlist.
- **Change** (in `net.internetisalie.lunar.settings.LuaProjectSettings`):
  ```kotlin
  class State {
      // … existing fields …
      var additionalGlobals: MutableList<String> = mutableListOf()
  }
  // new accessor on LuaProjectSettings:
  val additionalGlobals: List<String> get() = state.additionalGlobals
  ```
  `com.intellij.util.xmlb` serializes `MutableList<String>` to `lunar.xml` as
  `<additionalGlobals><option value="love"/></additionalGlobals>` — no custom converter
  needed. Default empty preserves backward compatibility with existing `lunar.xml` files.

### 2.5 Quick fixes (Should)
- `net.internetisalie.lunar.analysis.inspections.LuaAddToGlobalsQuickFix` — implements
  `LocalQuickFix`; `applyFix` adds `ref.name` to `LuaProjectSettings.getInstance(project)
  .state.additionalGlobals` inside a write action and re-runs analysis.
- Registered by attaching it in `holder.registerProblem(ref, message, fix)` — no separate
  `plugin.xml` entry.

## 3. Algorithms

### 3.1 Read-use classification (`isReadUse`) — INSP-01-06
- **Input → Output**: `LuaNameRef` → `Boolean` (true = a read this inspection should check).
- **Steps** (return `false` = skip — i.e. it is a declaration site or pure write target):
  1. Let `parent = ref.parent`.
  2. **Declaration sites — skip** if `parent` is any of:
     - `LuaAttName` (target of `local x` / `local x <const>`),
     - a `LuaNameList` whose own parent is `LuaParList` (function parameter) or
       `LuaGenericForStatement` (generic-for variable).
     - (Numeric-for variables are an `IDENTIFIER` leaf, not a `LuaNameRef`, so they never
       reach this visitor.)
  3. **Function-name head** — `parent` is `LuaFuncName` (the `nameRef` head of
     `function … end`). `LuaFuncName` has `getNameRef()` (head) and
     `getFuncNamePropertyList()` / `getFuncNameMethod()` (the `.b`, `.c`, `:m` suffixes).
     - If the property list is empty **and** there is no method (plain `function name() end`)
       → this declares global `name`: **skip**.
     - Otherwise (`function a.b.c() end` / `function a:m() end`) the head `a` is a **read** of
       an existing table → return `true` (consistent with treating an index base as a read —
       see the step-4 note and step 5).
  4. **Write target — skip** if all hold:
     - `parent` is `LuaVar`,
     - `(parent as LuaVar).varSuffixList` is empty (simple `name`, not `name.field`/`name[i]`),
     - the `LuaVar`'s parent is `LuaVarList` (a `LuaVar` only ever appears under `LuaVarList`,
       i.e. the LHS of a `LuaAssignmentStatement`).
       → this is `name = …`, implicit global creation, handled by INSP-05, not here.
     Note: for `name.field = …` the `LuaVar` has a non-empty `varSuffixList`, so the base
     `name` is **not** skipped — it is a read (TC-06).
  5. Otherwise return `true`.
- **Rules / edge handling**: a `LuaNameRef` used as the base of an index read (`a.b`, `a[i]`)
  is a read of `a` (default branch). `self` inside a method is resolved by `LuaScopeProcessor`
  (`LuaFuncDecl` implicit-self branch), so it is never flagged.

### 3.2 Top-level inspection logic (`inspectNameRef`) — INSP-01-01..05,07,08
- **Input → Output**: `LuaNameRef`, `ProblemsHolder` → side-effect (0 or 1 problem).
- **Steps**:
  1. If `!isReadUse(ref)` → return.
  2. `name = ref.identifier.text`; if `name == "_"` → return (Lua throwaway).
  3. `level = LuaProjectSettings.getInstance(project).state.languageLevel`.
  4. If `LuaStandardGlobals.contains(name, level)` → return.  *(§3.3)*
  5. If `name` ∈ `LuaProjectSettings…additionalGlobals` → return.
  6. If `suppressUnderscorePrefixedGlobals && name.startsWith("_")` → return.
  7. `val reference = ref.reference as? PsiPolyVariantReference ?: return` (no reference ⇒
     nothing to flag).
  8. If `reference.multiResolve(false).isNotEmpty()` → return.  **Use `multiResolve`, not
     `resolve()`**: `LuaNameReference.resolve()` returns `null` when there are *2+* candidates
     (`LuaNameReference.kt:233` — `if (size == 1) … else null`), so a global defined in
     multiple files would be a false positive under `resolve()`. A name is "declared" iff
     `multiResolve` yields ≥1 result. Early binding is handled inside `multiResolve` via
     `LuaBlockExt.kt:31`; no extra position check is needed (TC-03 / INSP-01-05).
  9. If `LuaInspectionSuppression.isSuppressed(ref, name, "undefined-global")` → return. *(§4)*
  10. `holder.registerProblem(ref, "Undeclared variable '$name'",
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING, LuaAddToGlobalsQuickFix(name))`.
- **Rules / edge handling**: severity is *not* hard-coded — `ProblemHighlightType
  .GENERIC_ERROR_OR_WARNING` lets the user re-map the level (Error/Warning/Weak Warning) in
  *Settings ▸ Editor ▸ Inspections* (satisfies INSP-01-04's "configurable severity").

### 3.3 Standard-globals membership (`LuaStandardGlobals`)
Source of truth: the *Basic Functions* and *Standard Libraries* sections of each Lua reference
manual (5.1 §5, 5.2/5.3/5.4 §6). Membership rule: `contains(name, level) = name ∈ BASE ∪
DELTA[level]`. Each set below is the **complete** list; `BASE` and each `DELTA` are disjoint;
nothing is ever subtracted. Build once as `val` constants.

- **`BASE` (present in every level 5.1–5.4)** — 31 names:
  ```
  assert  collectgarbage  dofile  error  _G  _VERSION  getmetatable  ipairs  load  next
  pairs  pcall  print  rawequal  rawget  rawset  require  select  setmetatable  tonumber
  tostring  type  xpcall
  coroutine  string  table  math  io  os  debug  package
  ```
  (21 functions + the `_G` / `_VERSION` values + 8 library tables = 31; `BASE.size == 31`.)
- **`DELTA[LUA51]`**: `loadstring  unpack  gcinfo  module  getfenv  setfenv  newproxy`
- **`DELTA[LUA52]`**: `rawlen  bit32`
- **`DELTA[LUA53]`**: `rawlen  utf8`
- **`DELTA[LUA54]`**: `rawlen  utf8  warn`
- **`DELTA[LUA50]`** (if the enum value is ever encountered): same as `DELTA[LUA51]`.

`LuaLanguageLevel` enum values are `LUA50, LUA51, LUA52, LUA53, LUA54`
(`net.internetisalie.lunar.lang.LuaLanguageLevel`); `forLevel` is a `when` over them.

## 4. External Data & Parsing — suppression comments (INSP-01-08)

`LuaInspectionSuppression.isSuppressed` scans the **comment leaves** of `ref.containingFile`
collected by element type as in §2.3 (`it.elementType in LuaSyntax.CommentTokens`, i.e.
`SHORTCOMMENT` and `LUACATS_COMMENT`), cached per file. Two grammars are recognised; matching
is on each comment leaf's `.text`.

### 4.1 LuaCATS `---@diagnostic`
- **Format** (one per comment line):
  ```
  ---@diagnostic disable-next-line: undefined-global
  ---@diagnostic disable-line: undefined-global
  ---@diagnostic disable: undefined-global
  ---@diagnostic enable: undefined-global
  ```
- **Parse strategy**: regex
  `^---@diagnostic\s+(disable-next-line|disable-line|disable|enable)\s*:\s*([\w,\s-]+)$`
  against the trimmed comment text. The id list (group 2) is split on `,`/whitespace; the
  comment applies only if it contains `undefined-global` **or** the bare form with no id list
  (no `:`), which means "all diagnostics".
- **Scope semantics** (by keyword):
  - `disable-line`: suppresses any `undefined-global` on the **same source line** as the
    comment.
  - `disable-next-line`: suppresses the **next source line** (first non-blank line after the
    comment line).
  - `disable` … `enable`: suppresses every line from the `disable` comment's line (inclusive)
    up to the matching later `enable` (exclusive); if no `enable`, to end of file.
  - Line numbers via `editor`-free `PsiDocumentManager.getInstance(project)
    .getDocument(file)?.getLineNumber(offset)`.
- **Maps to**: a `Boolean` for `(refLineNumber, "undefined-global")`.
- **Failure handling**: a comment that does not match the regex is ignored (no suppression).

### 4.2 Luacheck `-- luacheck: ignore`
- **Format**:
  ```
  -- luacheck: ignore                 (bare → suppress all on its scope)
  -- luacheck: ignore foo bar         (names → suppress only those names)
  ```
- **Parse strategy**: regex `--\s*luacheck:\s*ignore\b(.*)$`; group 1 trimmed and split on
  whitespace gives the name list (empty ⇒ all names).
- **Scope semantics** (bounded subset — full `push`/`pop` is a de-risking task,
  `INSP-DR-02`): a `luacheck: ignore` comment suppresses matching `undefined-global` warnings
  on **its own source line** (trailing comment, TC-09) and the **immediately following source
  line** (leading comment). Name match: bare ⇒ any name; otherwise the flagged `name` must be
  in the list.
- **Maps to**: a `Boolean` for `(refLineNumber, name)`.
- **Failure handling**: non-matching comments ignored.

## 5. Data Flow

### Example 1: `print(undeclaredVar)` (TC-02)
`visitElement(undeclaredVar:LuaNameRef)` → `isReadUse` true (argument of a call) → not a
standard/additional/underscore global → `resolve()` returns null (no local, no global, not in
platform lib) → not suppressed → `registerProblem` WARNING `Undeclared variable
'undeclaredVar'`.

### Example 2: `print(x); local x = 10` (TC-03)
For `x` on line 1: `isReadUse` true → `resolve()` walks up; `LuaBlock.processDeclarations`
skips the `local x` statement because its `textOffset` ≥ the reference's `textOffset`
(`LuaBlockExt.kt:31`) → phase 1 empty → phase 2 empty → `resolve()` null → flagged. For `x`
on line 2: parent is `LuaAttName` → `isReadUse` false → skipped.

### Example 3: `existing.field = 6` (TC-06)
Visitor sees `existing` (parent `LuaVar` with non-empty `varSuffixList`) → write-target test
fails its empty-suffix condition → `isReadUse` true → `resolve()` null → flagged. Visitor sees
`field` only if it is a `LuaNameRef`; it is the suffix name inside `LuaVarSuffix`/`LuaIndexExpr`
(`.field`), classified as a declaration/field position → not flagged (it is not a free
variable). `newGlobal` (simple LHS) → write-target → skipped.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Multi-assignment `a, b = f()` where `a`,`b` undeclared | Each `LuaVar` is a simple LHS write target → both skipped (INSP-05 territory). |
| `local x = x` (RHS reads outer `x`) | RHS `x` is read; `resolve()` honors early binding, so it does not see the `local` being declared on the same statement → resolves to outer/global or flags. Correct Lua semantics. |
| `self` in a method | Resolved by `LuaScopeProcessor` implicit-self branch → never flagged. |
| `_` throwaway | Skipped in §3.2 step 2. |
| Name inside a `require("…")` string | A string literal, not a `LuaNameRef` → never visited. |
| `goto`/label names | Not `LuaNameRef` (separate PSI) → never visited. |
| Platform library not configured in a test fixture | `LuaStandardGlobals` allowlist still exempts built-ins (§3.3) → TC-04 deterministic without a configured stdlib. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<localInspection
    language="Lua"
    shortName="LuaUndeclaredVariable"
    displayName="Undeclared variable"
    groupName="Lua"
    enabledByDefault="true"
    level="WARNING"
    implementationClass="net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection"/>
```

- **Settings UI** (INSP-01-07): the "Additional Globals" list editor is added to the existing
  Lua project settings configurable (the panel backing `LuaProjectSettings`); it binds to
  `state.additionalGlobals`. No new `applicationConfigurable`/`projectConfigurable`
  registration is required if reusing the existing Lua settings page; if a dedicated row is
  needed, it is added to that page's form, not a new extension point.
- **Resolution / indexes**: unchanged — reuses `LuaNameReference`, `LuaGlobalDeclarationIndex`,
  `PlatformLibraryIndex`. No new index.
- **Settings**: extends `LuaProjectSettings.State` (§2.4); persisted to `lunar.xml`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| INSP-01-01 | M | §2.1, §3.2 (step 8 via Phase-1 resolve) |
| INSP-01-02 | M | §3.2 (step 8 via Phase-2 resolve) |
| INSP-01-03 | M | §2.2, §3.2 (step 4), §3.3 |
| INSP-01-04 | M | §3.2 (step 10), §7 (level/severity) |
| INSP-01-05 | M | §1, §3.2 (step 8), §5 Example 2 |
| INSP-01-06 | M | §3.1, §5 Example 3 |
| INSP-01-07 | S | §2.4, §3.2 (step 5), §7 |
| INSP-01-08 | S | §2.3, §3.2 (step 9), §4 |

## 9. Alternatives Considered

- **`Annotator` vs `LocalInspectionTool`**: an earlier draft used an `Annotator`. Chosen
  `LocalInspectionTool` because it (a) integrates with *Settings ▸ Inspections* for
  user-configurable severity (INSP-01-04), (b) is batch-runnable ("Inspect Code"), and (c)
  supports `LocalQuickFix` and the platform's own suppression. This matches the repo's
  existing `LuaTypeAssignabilityInspection` registration pattern.
- **Re-implementing resolution in the inspection** vs **delegating to `LuaNameReference`**:
  delegation chosen to keep one resolution path and inherit early binding for free.
- **Hard-coded globals only** vs **index + allowlist**: index resolution (`PlatformLibraryIndex`)
  is the general path; the hard-coded `LuaStandardGlobals` is a deterministic floor so the
  inspection never false-positives on built-ins when stubs are absent.

## 10. Open Questions

_None — feature has cleared the planning bar._
