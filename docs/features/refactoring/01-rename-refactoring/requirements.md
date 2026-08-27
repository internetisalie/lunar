---
id: REFACT-01
title: "01: Rename Refactoring"
type: feature
status: "in_progress"
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
  `labelName` (`lua.bnf:251`). Labels are the only declarations Lunar models as named owners.
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
| `REFACT-01-01` | **Rename a local and all its references** | **M** | **Full** | **Audited before Phase 2:** The core of the feature. No `renamePsiElementProcessor`, no `renameHandler`, no `handleElementRename` override on `LuaNameReference` — `grep -rn "RenamePsiElementProcessor\|RenameHandler" src/main/` is empty. The epic table's claim *"Implemented (`LuaNameReference.handleElementRename`)"* names a method that does not exist; `LuaNameReference` overrides `multiResolve`, `resolve`, `isReferenceTo`, `getVariants` and nothing else. **(inferred)** invoking rename on the declaration renames the declaration alone and leaves every usage bound to the old name — silent breakage, worse than a refusal. **Phase 2 (2026-08-23):** `LuaRenameProcessor` renames the declaration and every usage; TC-01 asserts the whole file text. Mutation-proved: removing the usage-rewrite loop reddens all 12 positive rename cases. **Cancel path is not covered ([[BUG-468]], 2026-08-23):** the happy path renames declaration and all references, but `renameElement` rewrites usages before the declaration, so cancelling at usage *k* leaves *k*-1 usages on the new name and the declaration on the old one, with no rollback and no surfaced exception. That is precisely what this document's Verification section calls *"a rename that reports success while leaving the code broken … a data-loss-class defect"*. **Full** was claimed for the uncancelled path only. **`REFACT-01-00-DR-10` (2026-08-25) settled the one thing that was still inferred about this row: the cancel path is reachable by a USER, not only by a test harness.** Driving a live GoLand, the `PotemkinProgress` **Stop** button paints for ≈ 1719 ms on a 2000-usage rename and clicking it left the project holding both names in 170-186 files with the declaration unrenamed, on 5 of 5 attempts; on a 500-usage rename the button paints for only ≈ 64 ms, and at 100 usages and below the rename finishes inside the platform's 300 ms paint delay so no button exists at all. This row was **Partial** at that point — nothing had been implemented — but the missing half was measured as live, not merely as possible. See `risks-and-gaps.md`, "DR-10 result". **Phase 8 (2026-08-25) closes it and the row is now Full:** `renameElement` resolves the declaration rewrite, every usage rewrite and the `---@param` tag rewrite in a cancellable preparation phase, then applies only prepared closures inside one `ProgressManager.getInstance().executeNonCancelableSection`. A Cancel before the first edit leaves the file byte-identical (TC-43); a Cancel after it is ignored and the rename completes (TC-45, and Gap 2.18 records that ignored Cancel as the accepted residual); the cancellation point is per usage, not per rename (TC-44). Mutation-proved in both directions: restoring the Phase-2 apply loop reddens TC-43 on a half-applied file, and removing the non-cancelable section reddens TC-45 on a split one. |
| `REFACT-01-02` | **Rename invoked from a usage site** | **M** | **Full** | **Audited before Phase 2:** Platform contract: `substituteElementToRename` exists so a usage redirects to its declaration. Never overridden. **(inferred)** the caret-on-usage path dies earlier than that, at `canRename`, because `resolve()` hands back an IDENTIFIER leaf (see above). **Phase 2 (2026-08-23):** `substituteElementToRename` redirects a usage to its declaration leaf (TC-02). |
| `REFACT-01-03` | **Scope-exact rewrite under shadowing** | **M** | **Full** | **Audited before Phase 2:** The Lua-specific correctness bar: `local x` … inner `local x` … a global `x` … a second `local x = x + 1` are four distinct bindings sharing a spelling. Nothing renames them, so nothing gets this right or wrong yet. The substrate is in place and is the reason this is cheap to finish: `LuaNameReference.isReferenceTo` re-resolves each candidate rather than matching text, and `LuaNameReferenceSearcher` delegates isolation to it. **Phase 2 (2026-08-23):** TC-03. **This did not come for free**, contrary to design §6: an inner `local x`'s own name resolved outward to the outer `x` and was rewritten as a usage. Closed by `LuaNameReference.shadowsRatherThanUses`; see `risks-and-gaps.md`. |
| `REFACT-01-04` | **Rename a function parameter** | **M** | **Full** | **Audited before Phase 2:** A parameter is a `nameRef` in a `LuaNameList` under `LuaParList`; `canFindUsagesFor` accepts it, so the search half exists. The rename half does not. **Phase 2 (2026-08-23):** TC-04. |
| `REFACT-01-05` | **Rename a `for` control variable** | **S** | **Partial** | **Audited before Phase 2:** Both forms, and they differ in PSI: a generic-for variable is a `nameRef` under `LuaNameList`/`LuaGenericForStatement`, while `canFindUsagesFor` special-cases `LuaNumericForStatement` on the leaf's *direct* parent. A processor must handle both shapes. **Phase 2 (2026-08-23):** Both forms rename correctly (TC-05, TC-06), but the numeric-`for` declaration cannot be reached by the caret — `TargetElementUtil.findTargetElement` returns null on `for <caret>i`, measured. Rename from a usage works. Gap recorded in `risks-and-gaps.md`. |
| `REFACT-01-06` | **Rename `local function f`, including its recursive self-call** | **M** | **Full** | **Audited before Phase 2:** Lua-specific: `local function f` puts `f` in scope inside its own body (`local f = function() … end` does not), so the body's recursive call is a reference that must be rewritten. `LuaScopeProcessor` already models this via the `LuaLocalFuncDecl` and `LuaFuncDecl` branches. **Phase 2 (2026-08-23):** TC-07, including the recursive self-call. |
| `REFACT-01-07` | **Rename a global — across every file** | **M** | **Full** | **Audited before Phase 2:** A Lua global is `_ENV.x`, so its rename is not file-local. The cross-file search substrate exists and is tested (`LuaFindUsagesCrossFileTest`, `LuaNameReferenceSearcher` narrowing by `CacheManager.getFilesWithWord`); the rename that would consume it does not. **Phase 2 (2026-08-23):** All four global forms across files: `function greet()` (TC-08), `config = {}` (TC-27), Lua 5.5 `global x` (TC-28) and `global function f` (TC-29). |
| `REFACT-01-08` | **Rename a method or dotted member declaration** | **S** | **Partial** | **Audited before Phase 2:** `function Obj:m()` / `function Obj.m()` — `canFindUsagesFor` accepts `LuaFuncName` and `LuaFuncNameMethod` grandparents, so declarations are findable. Note the known resolution caveat that constrains any implementation: references key on receiver *text* (`b.setName`), so `local b = Builder; b:setName()` does not resolve to `function Builder:setName` and would be missed. **Phase 2 (2026-08-23):** Deliberately **refused**, not attempted: `function Obj:m()` with `refactoring.rename.colonMethod` and a function-name receiver segment with `refactoring.rename.functionNameSegment` (TC-19a, TC-34a, TC-34b). Phase 4 owns the dotted form. **Phase 4 (2026-08-23):** The dotted form renames with its call sites — `function M.<caret>run()` → `start` rewrites the declaration *and* every `M.run()` (TC-09) — and the colon form is refused by name (TC-10, asserting the `refactoring.rename.colonMethod` message and a byte-for-byte unchanged file, not merely that something declined). **Partial, not Full, and the missing half is deliberate:** `function Obj:m()` stays refused because `findReferences` on it returns **zero** references — measured — so renaming it would half-apply. Receiver-type-based method resolution is DR-03 in `risks-and-gaps.md`; until it exists the refusal is the correct behaviour, and the row cannot claim the requirement's first noun. Phase 4 added no production code for this row: §3.1 step 4b shipped with Phase 2 and the `DOTTED_FUNCTION` path needed nothing, so the phase's work was executing the claim and pinning it. **DR-05 (2026-08-23) found a second, independent reason this row is not `Full`:** the dotted form renames from its DECLARATION caret, but a caret on an `M.run()` **call site** is refused by the platform (`error.cannot.be.renamed`) before this processor is consulted — `TargetElementUtil` hands back the whole `LuaFuncDecl`, which `canProcessElement` deliberately does not claim (Gap 2.10). Measured, contained and safe — never a half-rename — and recorded as Gap 2.14, filed 2026-08-23 as [[BUG-465]] because it outlives this feature and needs the same `TargetElementEvaluatorEx2` Gap 2.9 needs. **Phase-4 remediation (2026-08-23):** the dotted rename this row does claim was also passing conflict detection blind — see `REFACT-01-14` — which is now fixed and pinned; it changes no claim here, but it is what makes the claim safe. |
| `REFACT-01-09` | **Rename a table field / constructor key** | **C** | **Not Implemented** | `t.field` is a `nameRef` reachable through `LuaMemberFieldNavigation`; `{ field = 1 }` is a bare IDENTIFIER leaf (`lua.bnf:319`) with no wrapper, no reference and no `PsiNamedElement` — it cannot be a rename target at all without a grammar change. Correctness also needs type inference to know *which* table, and the string form `t["field"]` would have to move with it. |
| `REFACT-01-10` | **New name is a valid, non-reserved identifier** | **M** | **Full** | Delegated, not duplicated: `LuaNamesValidator` is registered at `plugin.xml:393-395` and specified by [[REFACT-05]]. It is the one piece of REFACT-01's platform contract that is genuinely finished — and it is inert today, because the rename UI that consults it is unreachable for identifiers. |
| `REFACT-01-11` | **Validity tracks the configured language level** | **C** | **Not Implemented** | `LuaNamesValidator` ignores its `project` argument and consults a single fixed `LuaKeywords.RESERVED` set. The practical exposure is smaller than it looks, and both halves were checked rather than assumed: `global` is **not** in `RESERVED`, which is correct — `lua.bnf:212` documents it as a *soft* keyword remapped from IDENTIFIER only when a declaration follows, so `global` stays a legal name at every level including 5.5. `goto` **is** in the set unconditionally, which over-rejects for a Lua 5.1 project where `goto` is an ordinary identifier — though `lua.flex:74` returns `GOTO` at every level, so Lunar could not parse such a file anyway. The gap is real but is one name wide, and its root is in the lexer, not here. |
| `REFACT-01-12` | **In-place rename in the editor** | **S** | **Full** | Delegated to [[REFACT-07]], not duplicated. What REFACT-01 ships and keeps: the availability predicate's expression — `element is LuaNameRef && LuaDeclarationSite.kindOf(element.identifier)?.isFileLocal == true` (design §2.6) — and `LuaInplaceRenameTest`'s discriminating cases. REFACT-07 moves that expression from `isInplaceRenameAvailable` to `isMemberInplaceRenameAvailable`, supplies the primitive both in-place routes require — a `PsiNameIdentifierOwner` on the declaring `LuaNameRef` — and adds the `renameHandler` that serves every caret whose data-context element is a declaration IDENTIFIER leaf — a usage caret and a parameter declaration caret — and which therefore reaches no platform in-place handler. `Full` on REFACT-07's live verification: Shift+F6 on a Lua local starts an inline template with no dialog, typing updates the declaration and every usage together, Enter commits and Esc restores the file byte-for-byte. |
| `REFACT-01-13` | **Dialog with preview and Find Conflicts** | **S** | **Full** | **Audited before Phase 2:** The dialog, the preview pane and the usages view are platform-supplied and would need no code — but they are unreachable while `REFACT-01-01`/`-02` fail at `canRename`. Recorded as not implemented for the *user*, with the caveat that the eventual cost is zero. **Phase 2 (2026-08-23):** The dialog is reachable now that rename is not refused. **Completed by Phase 3 (2026-08-23):** all three parts are delivered and each was checked in the tree rather than read off the design: the dialog and preview pane are platform-supplied, `showRenamePreviewButton` is overridden nowhere in `src/main/` so it keeps its `true` default, and the conflicts dialog is now fed by `LuaRenameProcessor.findCollisions` (`LuaRenameProcessor.kt:152`) via `LuaRenameConflictDetector`. Nothing about this row is outstanding; the *content* of the conflict set is `REFACT-01-14`'s row, and the comment/string search the same dialog's checkbox offers is `REFACT-01-15`'s. |
| `REFACT-01-14` | **Conflict detection before applying** | **S** | **Full** | The distinctly Lua-shaped hazard, and the one the platform will not supply for free: renaming `x` to `y` where a `y` is already visible does not collide the way a Java field would — it silently *rebinds*, either capturing the inner `y` or shadowing the outer one, and the file still compiles. **Audited before Phase 3:** neither `findExistingNameConflicts` nor `findCollisions` was overridden anywhere in `src/main/`. **Shipped by Phase 3 (2026-08-23):** `LuaRenameConflictDetector` implements design §3.4's four rules — C1 capture, C2 shadow, C3 an existing global of the new name, C4 the renamed global declared more than once — and `LuaRenameProcessor.findCollisions` snapshots the usage list, calls it and appends. `findCollisions` is the hook rather than `findExistingNameConflicts` because the latter runs on the EDT (design §9 Alternative D). `LuaRenameConflictTest` covers TC-14/15/16/17/31, each asserting the *specific* rule's message; every mutant the implementation plan's Phase-3 record lists was executed, including the required one (deleting C2's declaration-site skip reddens TC-16) and the discriminating one (disabling C1/C3/C4 leaves TC-15 green, so its assertion rides on C2 alone). **Phase 4 (2026-08-23):** two cost cases added to the same class — the detector's cancellation is now checked once per stub hit and once per usage, pinned differentially rather than by an absolute call count, and Phase 3's "not individually pinnable by a test" is withdrawn as an overclaim. **This row's `Full` was NOT earned for dotted declarations until the Phase-4 review, and says so rather than letting the status imply coverage it gained only afterwards.** Phase 3 shipped and measured C1-C4 against bare globals only — TC-31's fixture is `config = {}` — and every dotted declaration passed conflict detection in silence, because C3/C4 searched the target's bare last segment while the stub is filed under its qualified name. Measured on two files each declaring `function M.run() end`: 0 stub hits, 0 references, 0 conflicts, rename applied. **Closed by the Phase-4 remediation (2026-08-23):** both rules search the qualified key (`LuaRenameConflictDetector.searchKeyOf`), TC-37 and TC-38 pin C4 and C3 on that shape with the bare-segment lookup as their executed mutant, and TC-39 pins the shadow rule's cancellation block, which the two Phase-4 cost cases could not reach. **A second remediation in the same phase closed the last uncounted ambiguity source ([[BUG-466]]):** C3/C4's candidate set now reads `LuaMemberFieldNavigation.find` as well, so it is exactly the set `LuaNameReference.doMultiResolve` consults, and a dotted function beside a same-named field assignment is reported (TC-41). That gap had been filed as deferred on a **false** platform claim — that anchoring a collision on an element which is also a usage would make the platform skip rewriting it — which TC-42 now refutes by execution. C4 is therefore complete for every path `LuaNameReference` resolves a global through; it *reports* the resulting ambiguity and does not repair the unresolvable call sites, which is what a conflict rule is for. |
| `REFACT-01-15` | **Search in comments and strings** | **C** | **Full** | **Audited before Phase 2:** `isToSearchInComments` / `isToSearchForTextOccurrences` default to the platform's persisted settings and are never overridden. For Lua this matters more than usual: `_G["name"]`, `require`d module tables and `---@param` text all carry identifiers the code search cannot reach. **Phase 2 (2026-08-23):** One of its six accessors ships early — `getQualifiedNameAfterRename` — because the checkbox is added unconditionally and one click otherwise drives a platform `LOG.error` (TC-13d, mutation-proved: `TestLoggerAssertionError: Unknown element type : PsiElement(LuaTokenType.IDENTIFIER)`). The search itself is Phase 7. | **Phase 7 (2026-08-25):** the remaining five accessors ship, together with the `LuaRefactoringSettings` application service that persists the two checkbox choices — the platform has `RENAME_SEARCH_IN_COMMENTS_FOR_FILE` but no `…_FOR_VARIABLE`, so there was nothing to delegate to. `getElementToSearchInStringsAndComments` returns `element.parent as? LuaNameRef`, **not** the leaf: the leaf is not a `PsiNamedElement`, so `ElementDescriptionUtil` would fall through to `element.toString()` (`ElementDescriptionUtil.java:26`) and the search would run against a debug string with the checkbox ticked. TC-13c and TC-13e are the gates and both were mutation-proved red on deleting that override — TC-13e on a `FileComparisonFailedError` with the comment left unrewritten, i.e. on the file rather than on an exception.
| `REFACT-01-16` | **Rename propagates into LuaCATS annotations** | **S** | **Partial** | **Audited before Phase 2:** Renaming a parameter must move its `---@param name`, and renaming a `@class`/`@alias` must move its uses. Neither could: no `PsiReference` implementation exists anywhere under `src/main/kotlin/net/internetisalie/lunar/luacats/`, so annotation names are unlinked text — the same root cause as the known "LuaCATS tags are not stubbed" limitation. **Phase 6 (2026-08-23):** the `---@param` half ships. `LuaCatsParamRenamer` (design §2.8/§3.6) propagates structurally *instead of* through a reference, walking from the renamed parameter to its declaration's attached comment, and `LuaRenameProcessor.renameElement` calls it as design §3.3 step 5. **Every** step of §3.6 as it then stood was executed against real PSI before implementation and every one survived, including the two the design asserts rather than shows: `ARG_NAME` has exactly one child, a `NAME` `LeafElement`, and the comment owner of a `function` **expression** is the enclosing `LuaLocalVarDecl` (TC-20f). Steps 3 and 4 are shipped as a single selection *by that leaf*, which is the one deviation: as two steps, step 4's `?: return` is unreachable for any tag step 3 can match, so it would read as a silent failure branch while being none. **The row stays `Partial`, and the missing half is the one §3.6 puts out of scope:** `@class` / `@alias` name propagation, whose names reach navigation only through the file-based `LuaCatsTypeNameIndex` and need a separate mechanism (`risks-and-gaps.md` TBD-2, DR-04). **Gap 2.13 named this phase as the first candidate to restore a visible half-apply**, because a comment-text edit does not funnel through `LuaElementFactory`. It does not, and that is measured rather than argued: the renamer has no failure outcome — every exit short of the rewrite means there is no `@param` tag spelled with the old name, which is a correct no-op — and TC-20d pins the converse, that a REFUSED rename (`end`) leaves the tag byte-identical. Hoisting the call above the refusal pre-checks was executed and reddens TC-20d with a `FileComparisonFailedError`: `---@param end number` beside an unrenamed parameter. |
| `REFACT-01-17` | **Rename a `::label::`** | **C** | **Full** | Delegated to [[REFACT-04]], not duplicated. Listed only because `LuaRefactoringSupportProvider`'s KDoc mis-attributes it here. It works end to end — declaration, `goto` reference, and per-function scope isolation — and it works for the two reasons the rest of the feature lacks: `labelName` is a real `PsiNameIdentifierOwner`, and `LuaLabelReference` overrides `handleElementRename`. `LuaLabelRenameTest` covers each of them, and pins `labelName`'s `PsiNameIdentifierOwner` contract besides. |
| `REFACT-01-18` | **Renaming a `.lua` file updates `require(...)`** | **S** | **Full** | Delivered in Phase 5 by `LuaRequireReference.handleElementRename` (design §2.7/§3.7) plus `LuaElementFactory.createStringLiteral`. The row's previous **(inferred)** claim is now **measured**: on the parent commit `a8424c14` renaming a file any `require` names threw `PluginException: No ElementManipulator instance registered for TERMINAL_EXPR`/`ARGS` and abandoned the refactoring — reproduced in all three call shapes. The rewrite preserves the user's delimiters and dotted package prefix (`require 'app.util'` → `require 'app.helpers'`, `require [[util]]` → `require [[helpers]]`), and declines rather than corrupt when the new file name is not a Lua string body. TC-18a/b/c/d. |
| `REFACT-01-19` | **Renaming `...` or `self`** | **W** | **Won't** | **Audited before Phase 2:** `...` has no name to change — `parList ::= nameList [',' '...'] \| '...'` (`lua.bnf:313`) binds no identifier. `self` is a plain IDENTIFIER (absent from `lua.flex`) but is *implicit* in `function T:m()`: there is no declaration site to rename, and renaming the body's occurrences alone would unbind them. Both are language facts, not gaps. **Phase 2 (2026-08-23):** Locked by TC-19a/TC-19b. `self` resolves to the METHOD-NAME leaf, not the class, so the colon-method refusal is what stops it — there is no `self` guard. |
| `REFACT-01-20` | **Dynamic access is out of reach** | **W** | **Won't** | `_G["x"]`, `load("return x")`, `rawget(_ENV, "x")` and any string-keyed table access are invisible to a static rename, and no amount of PSI work changes that. Recorded deliberately: rename over Lua is best-effort by construction, and a design that does not say so will be read as claiming soundness it cannot have. The mitigation is `REFACT-01-15` (search in strings) plus the preview pane of `REFACT-01-13`, not a cleverer resolver. | **Phase 7 (2026-08-25):** the mitigation this row names is now available rather than merely intended — `REFACT-01-15`'s string search ships default-off (`LuaRefactoringSettings` defaults both flags to `false`, because Lua's `_G["name"]` idioms make a string match likelier to be coincidental than in a statically typed language), and `REFACT-01-13`'s preview pane is already reachable. The row itself stays `Won't`: the limitation is unchanged and no code removes it.

## Verification

**Audited before Phase 2 (2026-08-22): no test in `src/test/kotlin` renamed a non-label identifier.** `grep -rn "renameElementAtCaret\|RenameProcessor" src/test/kotlin` returned three hits, all in `LuaLabelRenameTest` — the whole of REFACT-01's direct coverage, and it covered `REFACT-01-17` only. The table below is the state **after** Phase 2 and its review.

| Row | Covered by |
| :--- | :--- |
| `REFACT-01-10` | `LuaNamesValidatorTest` — booleans only; see [[REFACT-05]]. |
| `REFACT-01-17` | `LuaLabelRenameTest` — `testRenameFromDeclaration`, `testRenameFromReference`, `testScopeIsolatedRename`. |
| `-01`, `-02`, `-03`, `-04`, `-05`, `-06`, `-13`, `-19` | `LuaRenameTest`. |
| `-07` | `LuaRenameCrossFileTest` — one case per global declaration form. |
| `-08` | `LuaRenameTest` — the REFUSAL cases only (`testSelfInsideAMethodIsRefusedAsTheMethod`, `testFunctionNameReceiverIsRefused`, `testIntermediateFunctionNameSegmentIsRefused`). No dotted rename ships. |
| `-15` | `LuaRenameTest.testSearchInCommentsDoesNotLogAnUnknownElementType` — the `getQualifiedNameAfterRename` half only. |
| `-16` | `LuaCatsParamRenameTest` — the `---@param` half only. **The gates are** `testParamTagFollowsParameter`, `testOnlyTheMatchingParamTagMoves` and `testTagOnAFunctionExpressionAssignedToALocal`: unwiring `LuaCatsParamRenamer` reddens exactly those. **Every other case in the class** asserts that something does **not** move and is therefore green on the parent commit — recorded as guards, not gates, each with its own executed mutant. |
| `-14` | `LuaRenameConflictTest` — one case per rule, plus the negative that keeps the detector from crying wolf. |
| `-09`, `-11`, `-12` | **Nothing.** |

The *substrate* these rows would be built on is tested, which is worth stating so the remaining work
is not over-estimated: `LuaFindUsagesTest` and `LuaFindUsagesCrossFileTest` exercise
`LuaNameReferenceSearcher` for locals, parameters and cross-file globals; `LuaSafeDeleteTest`
consumes the same search; `LuaReferenceTest`, `LuaNavigationTest` and
`ShadowingVariableInspectionTest` exercise the scope-exact resolution that `REFACT-01-03` needs.
Rename is missing its processor, not its analysis.

**Phase 4's remediation landed on 2026-08-23** and added the cases in the table below, each with its executed mutant.
The `-08` and `-14` rows of the table above are a **Phase-2 snapshot** and are left as one; these are
the current additions:

| Case | Pins | Reddened by |
| :--- | :--- | :--- |
| TC-37 `LuaRenameConflictTest.testDottedFunctionDeclaredTwiceIsReported` | C4 over a dotted declaration — the qualified index key | restoring C4's bare-segment lookup: the rename applies silently |
| TC-38 `…testExistingDottedFunctionOfTheNewNameIsReported` | C3 over a dotted declaration — the *new* name carries the receiver too | the same mutant, through the other rule |
| TC-39 `…testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall` | the shadow rule's per-name-ref cancellation, unreachable from a global target | deleting that `checkCanceled()`: the 4→14 delta collapses to 0 |
| TC-40 `LuaDeclarationSiteTest.testAMemberFieldAccessNormalisesToNoDeclarationLeaf` | DR-05's two-part coupling, which its deleted probe left enforced by nothing | dropping `identifierLeafOf`'s `kindOf` guard on the `LuaNameRef` branch |
| TC-41 `LuaRenameConflictTest.testDottedFunctionBesideAFieldAssignmentIsReported` | C4 over the *other* source `LuaNameReference` resolves a dotted name through — a field assignment beside the function ([[BUG-466]]) | dropping `globalDeclarationsNamed`'s `LuaMemberFieldNavigation.find` term: the rename applies silently |
| TC-42 `…testCollisionAnchoredOnAUsageIsStillRewritten` | that a collision anchored on an element which is also a usage is still rewritten on Continue — the platform fact five documents had backwards | the same mutant: zero collisions, so the anchor assertion finds no `b.lua` |

**Phase 2 landed on 2026-08-23**, and with it the interim BUG-457 refusal
(`LuaUnsupportedRenameProcessor`) was deleted in the same commit that registered its replacement.
Every positive case above is mutation-proved against the defect itself: with
`LuaRenameProcessor.renameElement`'s usage-rewrite loop removed, **every** positive rename case then
in the suite went red with the declaration renamed and the usages left behind — BUG-457 verbatim
(measured at Phase 2, 2026-08-23).

**The Phase-2 review (2026-08-23) failed the commit and two of its findings were defects in the code.**
`renameElement` rewrote every usage *before* two unguarded `?: return`s that could abandon the
declaration rewrite — BUG-457 inverted, and specified that way by design §3.3 — and
`LuaElementFactory.createIdentifier` reached that decision through a `!!`. Both are closed: the
replacement and the declaration's AST swap are resolved before the first edit and a rename that
cannot be applied is refused outright (`refactoring.rename.rewriteUnavailable`), pinned by
`LuaRenameTest.testUnbuildableNewNameRefusesBeforeAnythingIsRewritten` (TC-36) with the reviewed
ordering as its executed mutant. Three plan test cases the phase had dropped without saying so —
TC-11, TC-13b, TC-19c — were also added, and three gaps recorded (`risks-and-gaps.md` 2.11-2.13).

Two claims in `design.md` were measured false while doing it, and both are corrected there and in
`risks-and-gaps.md`: §6's *"Two locals of the same name in nested blocks — handled by resolution, not
by rename"* (it was not — see `REFACT-01-03` above), and §6's row saying a caret on the receiver `M`
of `function M.run()` is redirected when `M = {}` exists (it is refused instead, and a caret on
`M = {}` itself does not rename at all).

**Nothing in this table is recorded anywhere else in the repo.** `grep -rn "REFACT-01" docs/`
returns only the epic row, this file, and a stale mention in [[REFACT-05]]'s design; no bug report
mentions rename. The two findings that most deserve one are the false epic-table attribution to
`LuaNameReference.handleElementRename`, and the **(inferred)** silent partial rewrite in
`REFACT-01-01` — a rename that reports success while leaving the code broken is a data-loss-class
defect, and it should be executed against a live IDE before anyone decides how urgent it is.

The front-matter status was corrected from `done` to `todo` on 2026-08-22 for the same reason
[[DEBUG-07]]'s was: it was never earned.

**Phase 1 landed on 2026-08-22** (implementation-plan Phase 1: declaration-site model + global
indexing). No row above changes status yet — rename itself arrives in Phase 2, and every `Not
Implemented` row here is about renaming. What Phase 1 delivers is the shared classifier those rows
will be built on (`LuaDeclarationSite`), plus two effects a user can see today and neither of which
is a rename: `function M.run()` became a findable, safe-deletable declaration (`REFACT-01-08`'s
search half), and Lua 5.5 `global x = 1` / `global function f() end` are indexed and resolvable
across files (`REFACT-01-07`'s cross-file half — without it a 5.5 global rename would have rewritten
one file and silently left every other bound to the old name).
