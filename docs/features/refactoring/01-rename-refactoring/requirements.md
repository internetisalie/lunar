---
id: REFACT-01
title: "01: Rename Refactoring"
type: feature
status: "todo"
vf_icon: 📋
priority: "medium"
parent_id: REFACT/INTENT
folders: ["[[features/refactoring/requirements|requirements]]"]
---

# 01: Rename Refactoring

Rename a Lua identifier — a local, a parameter, a `for` variable, a function, a global, a method —
and rewrite exactly the references that Lua's scoping rules bind to it, in this file and, for a
global, across the project.

`::labels::` are **not** specified here. They are the one name kind Lunar renames today, and they
belong to [[REFACT-04]]; `LuaRefactoringSupportProvider`'s KDoc attributing them to REFACT-01 is
stale. Only `REFACT-01-17` appears below, as a delegation row.

## How these requirements were derived

**Not from Lunar's code.** A specification read off its own implementation cannot fail; that is the
defect that left [[DEBUG-07]] marked shipped for months ([[BUG-450]] §4). This feature has the same
provenance as DEBUG-07 — `5632a81d` created its `requirements.md` as one of the 16 placeholder files
for zero-coverage epics, `47df3605` stamped ✅ on it in bulk, and no commit ever touched it in
between. So the rows below were derived from three sources outside this repo, and only then checked
against it:

1. **The IntelliJ rename contract.** `RefactoringSupportProvider.isInplaceRenameAvailable` /
   `isMemberInplaceRenameAvailable`, `RenamePsiElementProcessor` (and its base's
   `substituteElementToRename`, `findReferences`, `prepareRenaming`, `findExistingNameConflicts`,
   `findCollisions`, `isToSearchInComments`, `isToSearchForTextOccurrences`), `NamesValidator`, and
   the `PsiNamedElement` / `PsiNameIdentifierOwner` shape the platform expects a renameable element
   to have. Each capability is a question this feature must answer either "we provide it" or "we
   deliberately do not".
2. **Lua's own scoping rules**, which define *correctness* here and are what makes rename over Lua
   different from rename over Java: a local's scope runs from after its declaration to the end of
   its block; a later `local x` is a *different* variable from an earlier one; an inner `local x`
   shadows an outer one; a parameter is a local of the body; `local function f` is in scope inside
   its own body but `local f = function()` is not; a global is `_ENV.x` and therefore visible in
   every file; a table field is a string key with no lexical scope at all.
3. **[[plugin-feature-comparison]]** — its `Rename identifier` row already reads
   **`✔ (labels only)`** for lunar against `✔` for the four comparison plugins. That row is the only
   accurate statement about REFACT-01 anywhere in the repo, and this table agrees with it.

### Evidence class — read this before trusting a status

Two different kinds of claim appear below, and they are not equally strong:

- **Verified by reading this repo** — that a class, override, or `plugin.xml` registration exists or
  does not. Every "Not Implemented" rests on an *absence* established by grep over `src/main/` and
  `src/main/resources/META-INF/plugin.xml`, which is a sound way to establish absence.
- **Inferred from platform control flow, NOT executed.** Where a row says what the user *sees* —
  an error hint, a silently partial rewrite — that is traced through `TargetElementUtilBase`,
  `PsiElementRenameHandler` and `RenameUtilBase` in `intellij-community`, not observed. No test in
  `src/test/kotlin` renames a non-label identifier and no live IDE session was run for this table.
  Such claims are marked **(inferred)**.

### The repo constraint that shapes every row: there is no declaration PSI

Verified in `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt` and `lua.bnf`:

- `LuaNameDeclElement : PsiNameIdentifierOwner` has exactly **one** grammar rule behind it —
  `labelName` (`lua.bnf:252`). Labels are the only declarations Lunar models as named owners.
- Every other name — local, parameter, `for` variable, function name, method segment, global — is
  `nameRef` (`lua.bnf:169`), whose mixin `LuaNameRefBaseImpl` implements `LuaNameRefElement :
  PsiNamedElement` only. A declaration is not a type; it is *a `LuaNameRef` in a particular parent
  container*, which is precisely how `LuaFindUsagesProvider.canFindUsagesFor` identifies one
  (`LuaAttName`, `LuaLocalFuncDecl`, `LuaFuncName`, `LuaFuncNameMethod`, a `LuaNameList` under
  `LuaParList`/`LuaGenericForStatement`, `LuaNumericForStatement`).
- A table-constructor key is not even that: `field ::= '[' expr ']' '=' expr | IDENTIFIER '=' expr |
  expr` (`lua.bnf:319`) makes it a bare IDENTIFIER leaf with no wrapper and no reference.

Any requirement written as "the declaration element implements `PsiNameIdentifierOwner`" would
therefore be wrong for this codebase. The rows below are written against what does exist.

Two consequences run through the whole table, both **(inferred)**:

- **On a usage**, `TargetElementUtilBase.doFindTargetElement` tries `REFERENCED_ELEMENT_ACCEPTED`
  first and returns `ref.resolve()`. `LuaScopeProcessor` sets `result` to
  `attName.nameRef.identifier` — the **IDENTIFIER leaf**, not a named element. A leaf is not a
  `PsiNamedElement`, no `renamePsiElementProcessor` is registered for Lua, so
  `PsiElementRenameHandler.getRenameErrorMessage` returns `error.wrong.caret.position.symbol.to.rename`.
- **On a declaration**, resolution finds nothing (the crawl excludes the reference's own declaring
  statement), so the fallback `ELEMENT_NAME_ACCEPTED` branch returns the enclosing `LuaNameRef`,
  which *is* a `PsiNamedElement` with a working `setName`. Rename then proceeds — but
  `RenamePsiElementProcessorBase.findReferences` searches for references to that **composite**,
  while `LuaNameReferenceSearcher.isNameDeclarationLeaf` returns early unless
  `elementType == IDENTIFIER`. Zero usages are collected.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `REFACT-01-01` | **Rename a local and all its references** | **M** | **Not Implemented** | The core of the feature. No `renamePsiElementProcessor`, no `renameHandler`, no `handleElementRename` override on `LuaNameReference` — `grep -rn "RenamePsiElementProcessor\|RenameHandler" src/main/` is empty. The epic table's claim *"Implemented (`LuaNameReference.handleElementRename`)"* names a method that does not exist; `LuaNameReference` overrides `multiResolve`, `resolve`, `isReferenceTo`, `getVariants` and nothing else. **(inferred)** invoking rename on the declaration renames the declaration alone and leaves every usage bound to the old name — silent breakage, worse than a refusal. |
| `REFACT-01-02` | **Rename invoked from a usage site** | **M** | **Not Implemented** | Platform contract: `substituteElementToRename` exists so a usage redirects to its declaration. Never overridden. **(inferred)** the caret-on-usage path dies earlier than that, at `canRename`, because `resolve()` hands back an IDENTIFIER leaf (see above). |
| `REFACT-01-03` | **Scope-exact rewrite under shadowing** | **M** | **Not Implemented** | The Lua-specific correctness bar: `local x` … inner `local x` … a global `x` … a second `local x = x + 1` are four distinct bindings sharing a spelling. Nothing renames them, so nothing gets this right or wrong yet. The substrate is in place and is the reason this is cheap to finish: `LuaNameReference.isReferenceTo` re-resolves each candidate rather than matching text, and `LuaNameReferenceSearcher` delegates isolation to it. |
| `REFACT-01-04` | **Rename a function parameter** | **M** | **Not Implemented** | A parameter is a `nameRef` in a `LuaNameList` under `LuaParList`; `canFindUsagesFor` accepts it, so the search half exists. The rename half does not. |
| `REFACT-01-05` | **Rename a `for` control variable** | **S** | **Not Implemented** | Both forms, and they differ in PSI: a generic-for variable is a `nameRef` under `LuaNameList`/`LuaGenericForStatement`, while `canFindUsagesFor` special-cases `LuaNumericForStatement` on the leaf's *direct* parent. A processor must handle both shapes. |
| `REFACT-01-06` | **Rename `local function f`, including its recursive self-call** | **M** | **Not Implemented** | Lua-specific: `local function f` puts `f` in scope inside its own body (`local f = function() … end` does not), so the body's recursive call is a reference that must be rewritten. `LuaScopeProcessor` already models this via the `LuaLocalFuncDecl` and `LuaFuncDecl` branches. |
| `REFACT-01-07` | **Rename a global — across every file** | **M** | **Not Implemented** | A Lua global is `_ENV.x`, so its rename is not file-local. The cross-file search substrate exists and is tested (`LuaFindUsagesCrossFileTest`, `LuaNameReferenceSearcher` narrowing by `CacheManager.getFilesWithWord`); the rename that would consume it does not. |
| `REFACT-01-08` | **Rename a method or dotted member declaration** | **S** | **Not Implemented** | `function Obj:m()` / `function Obj.m()` — `canFindUsagesFor` accepts `LuaFuncName` and `LuaFuncNameMethod` grandparents, so declarations are findable. Note the known resolution caveat that constrains any implementation: references key on receiver *text* (`b.setName`), so `local b = Builder; b:setName()` does not resolve to `function Builder:setName` and would be missed. |
| `REFACT-01-09` | **Rename a table field / constructor key** | **C** | **Not Implemented** | `t.field` is a `nameRef` reachable through `LuaMemberFieldNavigation`; `{ field = 1 }` is a bare IDENTIFIER leaf (`lua.bnf:319`) with no wrapper, no reference and no `PsiNamedElement` — it cannot be a rename target at all without a grammar change. Correctness also needs type inference to know *which* table, and the string form `t["field"]` would have to move with it. |
| `REFACT-01-10` | **New name is a valid, non-reserved identifier** | **M** | **Full** | Delegated, not duplicated: `LuaNamesValidator` is registered at `plugin.xml:391` and specified by [[REFACT-05]]. It is the one piece of REFACT-01's platform contract that is genuinely finished — and it is inert today, because the rename UI that consults it is unreachable for identifiers. |
| `REFACT-01-11` | **Validity tracks the configured language level** | **C** | **Not Implemented** | `LuaNamesValidator` ignores its `project` argument and consults a single fixed `LuaKeywords.RESERVED` set. The practical exposure is smaller than it looks, and both halves were checked rather than assumed: `global` is **not** in `RESERVED`, which is correct — `lua.bnf:212` documents it as a *soft* keyword remapped from IDENTIFIER only when a declaration follows, so `global` stays a legal name at every level including 5.5. `goto` **is** in the set unconditionally, which over-rejects for a Lua 5.1 project where `goto` is an ordinary identifier — though `lua.flex:74` returns `GOTO` at every level, so Lunar could not parse such a file anyway. The gap is real but is one name wide, and its root is in the lexer, not here. |
| `REFACT-01-12` | **In-place rename in the editor** | **S** | **Not Implemented** | `LuaRefactoringSupportProvider.isInplaceRenameAvailable` returns a hard `false`, and `isMemberInplaceRenameAvailable` returns `elementToRename is LuaLabelName`. `VariableInplaceRenameHandler.isAvailable` consults exactly the first of those, so no identifier ever gets the inline template — matching [[plugin-feature-comparison]]'s `✔ (labels only)`. |
| `REFACT-01-13` | **Dialog with preview and Find Conflicts** | **S** | **Not Implemented** | The dialog, the preview pane and the usages view are platform-supplied and would need no code — but they are unreachable while `REFACT-01-01`/`-02` fail at `canRename`. Recorded as not implemented for the *user*, with the caveat that the eventual cost is zero. |
| `REFACT-01-14` | **Conflict detection before applying** | **S** | **Not Implemented** | The distinctly Lua-shaped hazard, and the one the platform will not supply for free: renaming `x` to `y` where a `y` is already visible does not collide the way a Java field would — it silently *rebinds*, either capturing the inner `y` or shadowing the outer one, and the file still compiles. `findExistingNameConflicts` and `findCollisions` are the designated hooks; neither is overridden anywhere in `src/main/`. |
| `REFACT-01-15` | **Search in comments and strings** | **C** | **Not Implemented** | `isToSearchInComments` / `isToSearchForTextOccurrences` default to the platform's persisted settings and are never overridden. For Lua this matters more than usual: `_G["name"]`, `require`d module tables and `---@param` text all carry identifiers the code search cannot reach. |
| `REFACT-01-16` | **Rename propagates into LuaCATS annotations** | **S** | **Not Implemented** | Renaming a parameter must move its `---@param name`, and renaming a `@class`/`@alias` must move its uses. It cannot: no `PsiReference` implementation exists anywhere under `src/main/kotlin/net/internetisalie/lunar/luacats/`, so annotation names are unlinked text. This is the same root cause as the known "LuaCATS tags are not stubbed" limitation — the tags have no name-owning PSI to hang a reference on. |
| `REFACT-01-17` | **Rename a `::label::`** | **C** | **Full** | Delegated to [[REFACT-04]], not duplicated. Listed only because `LuaRefactoringSupportProvider`'s KDoc mis-attributes it here. It works end to end — declaration, `goto` reference, and per-function scope isolation — and it works for the two reasons the rest of the feature lacks: `labelName` is a real `PsiNameIdentifierOwner`, and `LuaLabelReference` overrides `handleElementRename`. `LuaLabelRenameTest` covers all three cases. |
| `REFACT-01-18` | **Renaming a `.lua` file updates `require(...)`** | **S** | **Not Implemented** | The module-level counterpart of rename, and the one the platform routes through `bindToElement`/`handleElementRename` on the string reference. `LuaRequireReference` overrides `resolve()` and nothing else, and no `com.intellij.lang.elementManipulator` is registered anywhere in this plugin — `PsiReferenceBase.getManipulator()` throws `PluginException("No ElementManipulator instance registered for …")` when there is none. **(inferred)** a file rename that reaches a `require` string fails rather than rewriting it. |
| `REFACT-01-19` | **Renaming `...` or `self`** | **W** | **Won't** | `...` has no name to change — `parList ::= nameList [',' '...'] \| '...'` (`lua.bnf:313`) binds no identifier. `self` is a plain IDENTIFIER (absent from `lua.flex`) but is *implicit* in `function T:m()`: there is no declaration site to rename, and renaming the body's occurrences alone would unbind them. Both are language facts, not gaps. |
| `REFACT-01-20` | **Dynamic access is out of reach** | **W** | **Won't** | `_G["x"]`, `load("return x")`, `rawget(_ENV, "x")` and any string-keyed table access are invisible to a static rename, and no amount of PSI work changes that. Recorded deliberately: rename over Lua is best-effort by construction, and a design that does not say so will be read as claiming soundness it cannot have. The mitigation is `REFACT-01-15` (search in strings) plus the preview pane of `REFACT-01-13`, not a cleverer resolver. |

## Verification

**No test in `src/test/kotlin` renames a non-label identifier.** `grep -rn "renameElementAtCaret\|RenameProcessor" src/test/kotlin` returns three hits, all in `LuaLabelRenameTest`. That is the whole of REFACT-01's direct coverage, and it covers `REFACT-01-17` only.

| Row | Covered by |
| :--- | :--- |
| `REFACT-01-10` | `LuaNamesValidatorTest` — booleans only; see [[REFACT-05]]. |
| `REFACT-01-17` | `LuaLabelRenameTest` — `testRenameFromDeclaration`, `testRenameFromReference`, `testScopeIsolatedRename`. |
| `-01`…`-09`, `-11`…`-16`, `-18` | **Nothing.** |

The *substrate* these rows would be built on is tested, which is worth stating so the remaining work
is not over-estimated: `LuaFindUsagesTest` and `LuaFindUsagesCrossFileTest` exercise
`LuaNameReferenceSearcher` for locals, parameters and cross-file globals; `LuaSafeDeleteTest`
consumes the same search; `LuaReferenceTest`, `LuaNavigationTest` and
`ShadowingVariableInspectionTest` exercise the scope-exact resolution that `REFACT-01-03` needs.
Rename is missing its processor, not its analysis.

**Nothing in this table is recorded anywhere else in the repo.** `grep -rn "REFACT-01" docs/`
returns only the epic row, this file, and a stale mention in [[REFACT-05]]'s design; no bug report
mentions rename. The two findings that most deserve one are the false epic-table attribution to
`LuaNameReference.handleElementRename`, and the **(inferred)** silent partial rewrite in
`REFACT-01-01` — a rename that reports success while leaving the code broken is a data-loss-class
defect, and it should be executed against a live IDE before anyone decides how urgent it is.

The front-matter status was corrected from `done` to `todo` on 2026-08-22 for the same reason
[[DEBUG-07]]'s was: it was never earned.
