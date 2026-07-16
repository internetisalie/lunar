---
id: "REDIS-06-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "REDIS-06"
folders:
  - "[[features/redis/06-sandbox-quickdoc-refinements/requirements|requirements]]"
---

# Technical Design: REDIS-06 — Sandbox & Quick-Doc Gating Refinements

Two independent gating fixes to existing REDIS-04 components. No new registrations, data
models, or external parsing. Each fix edits exactly one existing class and adds test coverage.

## 1. Architecture Overview

### Current State
- `LuaRedisSandboxInspection` (`.../analysis/redis/LuaRedisSandboxInspection.kt`) flags root
  name-refs that name a blocked sandbox library. It skips only *declaration positions* via
  `isDeclaration` (line 124). A *use* of a shadowing local (`local print = ...; print(x)`) is
  not a declaration and is wrongly flagged (false positive).
- `RedisCommandDocumentationTargetProvider`
  (`.../analysis/redis/RedisCommandDocumentationTargetProvider.kt`) returns a command doc
  target for any caret inside a `redis.call(...)` expression, because
  `RedisCallSiteMatcher.match` walks up from any descendant to the enclosing `LuaFuncCall`
  (`RedisCallSiteMatcher.kt:60-62`). It never checks that the caret element is the command
  STRING (REDIS-04 design §3.6 step 1 specified this gate but it was not implemented).

### Prior Art in This Repo
- **Side-effect-free local resolution** already exists: `LuaResolveUtil.scopeCrawlUp(processor,
  element)` (`.../lang/psi/LuaResolveUtil.kt:9`) is a standalone Phase-1 scope walk, behaviorally
  equivalent to the inline Phase-1 walk in `LuaNameReference.multiResolve` (~lines 41–78) — it is a
  **separate copy**, NOT a shared factoring (one minor divergence: `multiResolve` passes `element`
  as the `LuaBlock` `lastParent`, while `scopeCrawlUp` passes `prev`; both correctly exclude the
  self-shadow `local print = print` and forward decls, so this does not affect Fix 1). **Shipped
  precedent for this exact use:** `LuaShadowingVariableInspection.inspectIdentifier`
  (`.../analysis/inspections/LuaShadowingVariableInspection.kt:66`) already calls `scopeCrawlUp` with
  a custom `PsiScopeProcessor` from inside a `buildVisitor`, with no extra read-action wrapping —
  validating both the approach and the threading. It walks `LuaBlock` / `LuaFuncDef`
  / `LuaFuncDecl` / `LuaLocalFuncDecl` / numeric+generic for-statements and finally the
  `LuaFile`, calling each element's `processDeclarations`, with **no** VFS, stub-index, or type
  engine access. Fix 1 **reuses** this — it does not re-implement a walk.
- **A local-collecting processor** exists (`LuaScopeProcessor`, `.../lang/LuaScopeProcessor.kt`)
  but it also matches globals (`LuaGlobalVarDecl`, `LuaGlobalFuncDecl`, `LuaFuncDecl`, `LuaVar`),
  so it would over-exempt file-level globals. Fix 1 therefore adds a **new local-only**
  processor rather than reusing `LuaScopeProcessor`.
- **Caret element-type gating** prior art: `LuaDocumentationTargetProvider`
  (`.../lang/doc/LuaDocumentationTargetProvider.kt:39-41`) reads `element.elementType` from
  `findElementAt(offset)` and branches on token type. Fix 2 mirrors this idiom using
  `LuaTokenTypes.STRING` (`.../lang/lexer/LuaTokenTypes.kt:62`).
- No existing component duplicates either fix; both **edit** the named REDIS-04 classes.

### Target State
- Sandbox inspection consults a local-only scope walk before flagging a root name-ref; a name
  bound to a local is exempt.
- Quick-doc provider returns a target only when the caret element is the command-name STRING of
  the matched site.

## 2. Core Components

### 2.1 net.internetisalie.lunar.analysis.redis.LocalBindingScopeProcessor  [NEW]
- **Responsibility**: a `PsiScopeProcessor` that stops (returns `false` from `execute`) only
  when it encounters a **local** declaration (local var / local func / parameter / numeric or
  generic for-variable) whose name equals the target name; it ignores all global-kind
  declarations, so a file-level global never counts as a shadow.
- **Threading**: read context (invoked inside the inspection visitor, which already runs under a
  platform read action). No I/O.
- **Collaborators**: fed by `LuaResolveUtil.scopeCrawlUp` (§2.2); matches the local PSI types
  `LuaLocalVarDecl`, `LuaLocalFuncDecl`, `LuaParList`, `LuaNumericForStatement`,
  `LuaGenericForStatement` — all verified real (`.../lang/psi/`).
- **Key API**:
  ```kotlin
  class LocalBindingScopeProcessor(private val targetName: String) : PsiScopeProcessor {
      var foundLocal: Boolean = false
          private set
      override fun execute(element: PsiElement, state: ResolveState): Boolean  // §3.1
      override fun <T> getHint(hintKey: Key<T>): T? = null
      override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
  }
  ```

### 2.2 net.internetisalie.lunar.analysis.redis.LuaRedisSandboxInspection  [EDIT]
- **Responsibility**: unchanged except a new exemption gate in `visitNameRef` after
  `isDeclaration` and before `blockedMessage`.
- **Threading**: unchanged (inspection visitor).
- **Collaborators**: adds `LuaResolveUtil.scopeCrawlUp` + `LocalBindingScopeProcessor`.
- **Key API** (new private helper in the companion object):
  ```kotlin
  // true when `ref`'s root name binds to a local in scope (→ exempt from sandbox flagging)
  private fun bindsToLocal(ref: LuaNameRef, rootName: String): Boolean  // §3.1
  ```

### 2.3 net.internetisalie.lunar.analysis.redis.RedisCommandDocumentationTargetProvider  [EDIT]
- **Responsibility**: unchanged except a caret-on-STRING gate at the top of
  `documentationTargets` and an identity check against the matched site's command literal.
- **Threading**: unchanged (documentation provider read context).
- **Collaborators**: adds `element.elementType == LuaTokenTypes.STRING` check; uses the already-
  matched `RedisCallSite.nameLiteral` (`RedisCallSiteMatcher.kt` — real).
- **Key API**: no signature change; internal gate per §3.2.

## 3. Algorithms

### 3.1 Shadowed-local exemption (REDIS-06-01)
- **Input → Output**: `(ref: LuaNameRef, rootName: String)` → `Boolean` (true = exempt).
- **Steps** (`bindsToLocal`):
  1. Construct `processor = LocalBindingScopeProcessor(rootName)`.
  2. Call `LuaResolveUtil.scopeCrawlUp(processor, ref)`. (This walks scopes bottom-up feeding
     each `processDeclarations`; it stops on the first `execute → false`.)
  3. Return `processor.foundLocal`.
- **`LocalBindingScopeProcessor.execute(element, state)` rule** — return `false` (stop, match)
  when a name equal to `targetName` is found on a local kind, else `true` (continue):
  - `LuaLocalVarDecl` → for each `attNameList` entry, if
    `attName.nameRef.identifier.text == targetName` → `foundLocal = true; return false`.
  - `LuaLocalFuncDecl` → if `element.nameRef.identifier.text == targetName` →
    `foundLocal = true; return false`.
  - `LuaParList` → for each `nameList?.nameRefList` entry, if
    `nameRef.identifier.text == targetName` → `foundLocal = true; return false`.
  - `LuaNumericForStatement` → if `element.identifier.text == targetName` →
    `foundLocal = true; return false`.
  - `LuaGenericForStatement` → for each `nameList.nameRefList` entry, if
    `nameRef.identifier.text == targetName` → `foundLocal = true; return false`.
  - **Any other element type** (including `LuaGlobalVarDecl`, `LuaGlobalFuncDecl`, `LuaFuncDecl`,
    `LuaVar`, `LuaAssignmentStatement`) → `return true` (do NOT match; a file-level global is
    not a shadow for sandbox purposes).
- **Integration in `visitNameRef`** — the guard is inserted as:
  1. `if (isDeclaration(o)) return` (unchanged).
  2. `val rootName = rootNameOf(o) ?: return` (unchanged).
  3. **NEW**: `if (bindsToLocal(o, rootName)) return`.
  4. platform guard + `blockedMessage` + `registerProblem` (unchanged).
- **Rules / edge handling**:
  - `scopeCrawlUp` enforces early-binding (forward-decl statements past the ref's offset are not
    visible) via the `lastParent`/`textOffset` gate already inside `processDeclarations`
    (`LuaBlockExt.kt:34`, `LuaFile.kt:51`) — no extra handling needed.
  - No VFS / stub-index / type-engine call is made: `scopeCrawlUp` and the processor touch only
    in-tree PSI. This is the sole mechanism (Fix-1 primary risk, risks §1.1).
  - Empty/absent binding → `foundLocal` stays false → not exempt → evaluated as a global (today's
    behavior preserved for genuine globals).
- **Complexity**: O(depth × decls-per-scope) PSI walk from the ref to the file root; bounded by
  the enclosing function/file, same cost profile as `LuaNameReference` Phase-1.

### 3.2 Caret-on-command-STRING gate (REDIS-06-02)
- **Input → Output**: `(file: PsiFile, offset: Int)` → `List<DocumentationTarget>`.
- **Steps** (revised head of `documentationTargets`, replacing lines 26–27 of the current file):
  1. `val element = file.findElementAt(offset) ?: return emptyList()`.
  2. **NEW**: `if (element.elementType != LuaTokenTypes.STRING) return emptyList()`.
  3. `val site = RedisCallSiteMatcher.match(element) ?: return emptyList()`.
  4. **NEW**: `if (site.nameLiteral?.string !== element) return emptyList()` — the caret STRING
     must be *the command-name literal*, not some other string argument.
  5. Remaining steps unchanged: `val name = site.commandName ?: return emptyList()`; platform
     guard; `info = spec.lookup(name) ?: return emptyList()`;
     `return listOf(RedisCommandDocumentationTarget(info))`.
- **Rules / edge handling**:
  - `findElementAt` returns the leaf token at the offset; a caret on `redis`/`call` yields an
    `IDENTIFIER`, on a comma yields punctuation, on `KEYS` yields an `IDENTIFIER` — all fail
    step 2. A caret on a *different* string argument passes step 2 but fails step 4 (identity
    mismatch with `nameLiteral.string`).
  - Identity (`!==`) is used, not offset/text equality, so two identical string literals in the
    same call are distinguished.
- **Complexity**: O(1) beyond the existing `RedisCallSiteMatcher.match` cost.

## 4. External Data & Parsing
None. Neither fix consumes CLI output, files, or network responses. All input is in-memory PSI.

## 5. Data Flow

### Example 1: `local print = redis.log\nprint("x")` under Redis 7+ (REDIS-06-01)
`visitNameRef(print-use)` → not a declaration → `rootName = "print"` →
`bindsToLocal`: `scopeCrawlUp` reaches the enclosing `LuaBlock`, `processDeclarations` feeds the
`local print` `LuaLocalVarDecl`, the processor matches → `foundLocal = true` → `visitNameRef`
returns → **no warning**.

### Example 2: `redis.call("GET", KEYS[1])`, caret on `KEYS` (REDIS-06-02)
`documentationTargets` → `findElementAt` = `KEYS` IDENTIFIER token → step 2 fails
(`elementType != STRING`) → **empty list** (no quick-doc).

### Example 3: `print("x")` (no local) under Redis 7+ (REDIS-06-01)
`bindsToLocal`: `scopeCrawlUp` finds no local `print` (file scope has only the `print(...)`
call, no local decl) → `foundLocal = false` → falls through to `blockedMessage("print", …)` →
`registerProblem` → **warning** (genuine global still flagged).

## 6. Edge Cases
- **Shadowing `os` local**: `local os = {}; os.time()` — `bindsToLocal("os")` true → exempt, even
  though `os.getenv` would normally be member-gated. Correct (a local table is not the stdlib).
- **Parameter shadow**: `function f(io) io.read() end` — `LuaParList` match → exempt.
- **Two string args, caret on the non-command one**: `redis.call("GET", "field")` caret on
  `"field"` → step 2 passes, step 4 fails (`nameLiteral.string` is the `"GET"` element) → empty.
- **Dynamic command**: `redis.call(cmd)` — `site.nameLiteral` is null → the caret can never be on
  a command STRING; if caret is on some later string arg, step 4's `null !== element` → empty.
- **Global assignment shadow**: `print = function() end; print()` — the `LuaAssignmentStatement`
  is NOT matched by `LocalBindingScopeProcessor` (it is a global), so the use is still evaluated
  as a global. This preserves REDIS-04 behavior for user-created globals (out of scope to change).

## 7. Integration Points
No `plugin.xml` change. Both classes are already registered:
- `RedisCommandDocumentationTargetProvider` at `plugin.xml:156`
  (`<platform.backend.documentation.targetProvider …>` — the unscoped `DocumentationTargetProvider`
  EP, no `language` attribute).
- `LuaRedisSandboxInspection` at `plugin.xml:305` (`<localInspection …>`).

The new `LocalBindingScopeProcessor` is a plain class instantiated by the inspection; it needs
no registration.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| REDIS-06-01 | M | §2.1, §2.2, §3.1 |
| REDIS-06-02 | M | §2.3, §3.2 |

## 9. Alternatives Considered
- **Reuse `LuaNameReference.resolve()`**: rejected — it runs Phase-2 external resolution
  (VFS / `LuaTypeManagerImpl.resolveModule`), the documented source of `TestLoggerAssertionError`
  (`LuaRedisSandboxInspectionTest.kt:134-151`). The scope-walk-only approach avoids it.
- **Reuse `LuaScopeProcessor`**: rejected — it also matches globals, over-exempting file-level
  globals; the local-only processor is precise.
- **Fix 2 via offset math on the string range**: rejected — identity check against
  `site.nameLiteral.string` is simpler and disambiguates duplicate literals.

## 10. Open Questions

_None — feature has cleared the planning bar._
