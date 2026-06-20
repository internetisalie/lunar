---
id: "DOC-06-04-PLAN"
title: "Implementation Plan"
type: "plan"
status: "done"
parent_id: "DOC-06-04"
folders:
  - "[[features/documentation/06-04-full-text-documentation-search/requirements|requirements]]"
---

# DOC-06-04: Implementation Plan

## Phases

### Phase 1: Complete LuaDescriptionIndex indexer [Must]
- **Goal**: The `LuaDescriptionIndex` actually indexes description text from LuaCATS comments, replacing the no-op stub.
- **Tasks**:
  - [x] **Replace `LuaDescriptionIndex.Indexer.map()`** (`lang/indexing/LuaDescriptionIndex.kt:50–62`) — implements design §2.1. The new indexer uses `PsiTreeUtil.findChildrenOfType(psiFile, LuaCommentOwner::class.java)` to visit every documentation-attached element, extracts description text via `collectDescriptionText(comment)`, tokenizes, and emits `word → "qualifiedName\tfileUrl\ttextOffset"` entries. When several declarations in the same file share a word, merge their records into one `|`-separated value (`result.merge(token, value) { existing, new -> "$existing|$new" }`, Behavior Rule #5) — never drop the collision.
  - [x] **Add `collectDescriptionText` helper** (`internal` top-level function in `lang/syntax/LuaComment.kt`) — realizes design §3.1. Iterates `comment.descriptionList` and every tag list with `getDescription()`, concatenating all text. Shared by both the indexer and the contributor's re-check.
  - [x] **Bump `getVersion()` from 1 to 2** — triggers re-indexing on next IDE start.
- **Exit criteria**:
  - `Indexer.map()` returns non-empty map for files with `LuaCatsComment` blocks containing description text (TC 1, 8).
  - `Indexer.map()` returns empty map for files without doc comments.
  - Unit test: `LuaDescriptionIndexTest` (new) — configure a `.lua` file with `---@class Vector Represents a 2D vector; local Vector = {}`, assert the index contains key `"vector"` and key `"represents"` with values matching `"Vector\tfile:path\t<N>"`.

### Phase 2: Search Everywhere contributor [Must]
- **Goal**: Users see "Lua Documentation" as a tab in Search Everywhere and can search by description text.
- **Tasks**:
  - [x] **Create `LuaDocSearchItem`** (`lang/doc/LuaDocSearchItem.kt`) — realizes design §2.2. Implements `NavigationItem`, stores `project`, `symbolName`, `fileUrl`, `declarationOffset`. `getPresentation()` returns name + relative path + icon. `navigate()` opens file at offset.
  - [x] **Create `LuaDocSearchEverywhereContributor`** (`lang/doc/LuaDocSearchEverywhereContributor.kt`) — realizes design §2.3. Implements `SearchEverywhereContributor<LuaDocSearchItem>` + inner `Factory : SearchEverywhereContributorFactory`. `fetchElements()` tokenizes pattern, looks up first token in index, splits each index value on `|` to recover per-declaration records, deduplicates, re-checks multi-word patterns, processes matching items. Overrides `isShownInSeparateTab()` → `true` so results render under a "Lua Documentation" tab. Cancellation uses `ProgressManager.checkCanceled()` (not the fictional `ProgressIndicatorUtils.checkCancelled()`). Threading: in production use `yieldToPendingWriteActions()` + `runInReadActionWithWriteActionPriority(task, indicator)`; under `ApplicationManager.getApplication().isUnitTestMode` run the lookup inside a plain `runReadAction { }` so unit tests don't trip `assertIsNonDispatchThread`.
  - [x] **Add `descriptionContainsAllTokens` helper** (private to contributor) — realizes design §3.2. Loads PSI, extracts full description text using the shared `collectDescriptionText()`, confirms all tokens are present.
  - [x] **Register in `plugin.xml`**: `<searchEverywhereContributor implementation="net.internetisalie.lunar.lang.doc.LuaDocSearchEverywhereContributor$Factory"/>`.
- **Exit criteria**:
  - Search Everywhere (Double Shift) shows "Lua Documentation" tab when a Lua project is open.
  - Typing a single word from a documentation description returns matching symbols (TC 2).
  - Typing a multi-word phrase returns matching symbols (TC 3, 4).
  - Typing text that matches nothing returns empty results (TC 5).
  - Dumb mode returns empty results (TC 9).
  - Unit test: `LuaDocSearchEverywhereContributorTest` (new) — uses `myFixture.configureByText` to create files with doc comments, then calls `fetchElements` with patterns and asserts the returned items by name.

### Phase 3: Search result navigation [Must]
- **Goal**: Selecting a search result opens the file at the documented symbol.
- **Tasks**:
  - [x] Verified in Phase 2's `LuaDocSearchItem.navigate()` implementation.
- **Exit criteria**:
  - Selecting a result in Search Everywhere navigates to the declaration (TC 6).
  - Integration assertion in Phase 2 test: `item.navigate(true)` followed by asserting editor caret position.

### Phase 4: Result presentation polish [Should]
- **Goal**: Each search result shows a meaningful label with relative file path.
- **Tasks**:
  - [x] Verify `LuaDocSearchItem.getPresentation()` returns correct `ItemPresentation` with `presentableText` (symbol name), `locationString` (relative path), and non-null icon.
- **Exit criteria**:
  - In Search Everywhere list, item shows name + relative file path (TC 7).
  - Icon matches the PSI element type (function icon for functions, class icon for `@class`, etc.).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| DOC-06-04-01 | M | Phase 1 |
| DOC-06-04-02 | M | Phase 2 |
| DOC-06-04-03 | M | Phase 3 |
| DOC-06-04-04 | S | Phase 4 |

## Verification Tasks
- [x] **Unit: `LuaDescriptionIndexTest`** — configures Lua files with `@class`, `@param`, `@return`, `@field` descriptions, asserts exact index keys and values. Covers TC 1, 8.
- [x] **Unit: `LuaDocSearchEverywhereContributorTest`** — configures Lua files with doc comments, calls `fetchElements` with single and multi-word patterns, asserts returned item count and names. Covers TC 2, 3, 4, 5.
- [x] **Integration: dumb-mode safety** — verifies `fetchElements` returns immediately when project is dumb. Covers TC 9.
- [x] **Integration: navigation** — selects a result, asserts editor opens at correct offset. Covers TC 6.
- [x] **IDE verification** (manual, via `verify-in-ide` skill): open Search Everywhere in sandbox GoLand, type a documentation keyword from test project, confirm results appear and navigate correctly.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Complete LuaDescriptionIndex indexer | done | Must |
| Phase 2: Search Everywhere contributor | done | Must |
| Phase 3: Search result navigation | done | Must |
| Phase 4: Result presentation polish | done | Should |