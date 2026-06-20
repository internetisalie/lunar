---
id: DOC-06-04
title: "04: Full-Text Documentation Search"
type: feature
parent_id: DOC-06
status: done
priority: medium
folders:
  - "[[features/documentation/requirements|requirements]]"
---

# DOC-06-04: Full-Text Documentation Search

## Overview

Enable users to find Lua symbols by searching within their LuaCATS / LuaDoc documentation
descriptions. The feature completes the existing placeholder `LuaDescriptionIndex` and
exposes its data through an IntelliJ **Search Everywhere** contributor, so typing a
documentation keyword (e.g. `"vector"`, `"deprecated"`, `"Internal helper"`) in Search
Everywhere (Double Shift) surfaces matching symbols alongside classes, files, and actions.
Depends on DOC-06-01 (Stub Indexing), which is already done.

## Scope

### In Scope
- Complete the `LuaDescriptionIndex` `Indexer.map()` to extract description text from every
  `LuaCatsComment` in a `.lua` file and index it for full-text lookup.
- Provide a `SearchEverywhereContributor` ("Lua Documentation") that matches user input
  against indexed description text and navigates to the documented element.
- Index covers `@class`, `@alias`, `@field`, `@param`, `@return`, `@type`, and free-form
  description text found in `---` doc comments attached to declarations.
- Description text from all tags within a single `LuaCatsComment` block is concatenated and
  indexed under the qualified name of the attached declaration.

### Out of Scope
- Indexing non-LuaCATS comments (plain `--` comments without `@` tags). These are handled by
  IntelliJ's built-in Find in Files.
- Indexing descriptions of symbols defined in external library / SDK `.lua` files that are
  not part of the project's source roots — those are covered by DOC-06-06 (Platform Symbol
  Documentation).
- Fuzzy / typo-tolerant search — exact substring matching only (Could upgrade path).
- Full-text search via standard Find in Files (uses IntelliJ's file-content grep; no custom
  index integration).
- Indexing field-level descriptions independently of their parent class.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| DOC-06-04-01 | **Index LuaCATS descriptions** | M | Complete `LuaDescriptionIndex.Indexer.map()` to extract the full description text (plain text, tag descriptions, and any free-form `LuaCatsDescription` lines) from every `LuaCatsComment` attached to a `LuaCommentOwner`, and emit index entries mapping each description-te
xt word to the owner's qualified name and file location. |
| DOC-06-04-02 | **Search Everywhere integration** | M | Register a `SearchEverywhereContributor` that accepts a search pattern, queries `LuaDescriptionIndex` for symbols whose description text contains the pattern (case-insensitive substring), and returns navigable items. |
| DOC-06-04-03 | **Result navigation** | M | Selecting a search result navigates to the source file at the declaration offset, focusing the documented symbol. |
| DOC-06-04-04 | **Result presentation** | S | Each search-result item shows the symbol name, the file path (relative to project content root), and a truncated snippet of the matching description line. |

## Detailed Specifications

### DOC-06-04-01: Index LuaCATS descriptions

The `LuaDescriptionIndex` (existing at `lang/indexing/LuaDescriptionIndex.kt:19`) has a
placeholder `Indexer` that returns an empty map. This requirement **completes** (extends,
does not replace) that indexer to perform real extraction.

**Index key**: each distinct word (lowercased, alphanumeric, length ≥ 2) from the
concatenated description text of a `LuaCatsComment` block.

**Index value**: a tab-delimited compound *record*:
`"${ownerQualifiedName}\t${virtualFilePath}\t${declarationOffset}"`

A `FileBasedIndex` maps one file to one `Map<Key, Value>`, so each word key has a **single
value per file**. When two or more declarations in the *same file* share a word, their
records are concatenated into that one value with a `|` separator
(`"recordA|recordB"`) — see Behavior Rule #5. The search contributor splits the value on
`|` before parsing each record on `\t`.

Where:
- `ownerQualifiedName`: the documented declaration's name, extracted **per owner type**
  (none of these implement `PsiNamedElement`, so there is no generic `.name`):
  - `LuaLocalVarDecl` → `attNameList.firstOrNull()?.nameRef?.text`
    (`LuaLocalVarDecl.getAttNameList()` → `LuaAttName.getNameRef()`)
  - `LuaFuncDecl` → `funcName.text` (`LuaFuncDecl.getFuncName()`, `@NotNull`)
  - `LuaLocalFuncDecl` → `nameRef.text` (`LuaLocalFuncDecl.getNameRef()`, `@NotNull`)

  Fall back to `owner.text` when the per-type accessor yields null.
- `virtualFilePath`: `VirtualFile.url` of the containing `.lua` file.
- `declarationOffset`: `PsiElement.textOffset` of the `LuaCommentOwner`.

**Description extraction**: for a given `LuaCatsComment`, collect text from:
1. All `LuaCatsDescription` children (free-form text lines between/before tags) — via
   `comment.getDescriptionList()`, each `element.text`.
2. Per-tag descriptions: for every tag type that has `getDescription()`, concatenate
   `tag.description?.text`. This includes `LuaCatsClassTag`, `LuaCatsAliasTag`,
   `LuaCatsParamTag`, `LuaCatsReturnTag`, `LuaCatsFieldTag`, `LuaCatsTypeTag`,
   `LuaCatsDeprecatedTag`, `LuaCatsSeeTag`, `LuaCatsOverloadTag`, and any other tag
   whose generated PSI interface exposes `getDescription()`.

The concatenated full text is then split on whitespace and non-alphanumeric runs to produce
individual tokens.

### DOC-06-04-02: Search Everywhere integration

Register `LuaDocSearchEverywhereContributor` implementing
`com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor<LuaDocSearchItem>`.

**`fetchElements(pattern, indicator, consumer)`**:
1. If `pattern.isBlank()` or the project is in dumb mode
   (`DumbService.isDumb(project)`), return immediately.
2. Tokenize `pattern` into words the same way the indexer does (lowercase, split on
   non-word runs `[^a-zA-Z0-9_]+`, drop tokens of length < 2). A pattern of only short
   tokens/punctuation yields no tokens → return immediately.
3. For the **first** token, enumerate the index keys with
   `FileBasedIndex.getInstance().processAllKeys(LuaDescriptionIndex.KEY, processor, scope, null)`
   and keep every key that contains `firstToken` as a case-insensitive substring (index keys
   are stored lowercased, so `key.contains(firstToken)` is case-insensitive). Then call
   `FileBasedIndex.getInstance().getValues(LuaDescriptionIndex.KEY, key, scope)` for each
   retained key to get candidate value strings. Use `LuaDescriptionIndex.KEY` (the public `ID`
   handle) — `LuaDescriptionIndexName` is `private`.
4. For each candidate value, split it on `|` to recover the per-declaration records, then
   parse each record with `split('\t')` to obtain `(qualifiedName, fileUrl, offset)`.
5. Deduplicate candidates by qualified name + fileUrl (same symbol might be hit by multiple
   words in its description). Keep the first occurrence.
6. For each candidate, if `tokens.size > 1`, re-check: retrieve the description's full
   text from the containing file's PSI using the **same `collectDescriptionText()` logic**
   as the indexer (includes free-form description lines and all tag descriptions), and
   confirm **every** token appears in that text as a case-insensitive substring
   (`tokens.all { fullText.lowercase().contains(it) }` — order-independent, see design §3.2).
   If the pattern is a single word, skip re-check (the step-3 substring key match is
   sufficient).
7. For each passing candidate, resolve the file via
   `VirtualFileManager.getInstance().findFileByUrl(fileUrl)`, load the PSI at `offset` to
   obtain the `LuaCommentOwner` navigation target, and call
   `consumer.process(LuaDocSearchItem(...))`.
8. Check `ProgressIndicator.checkCanceled()` before every file load.

**Part 2 — Registration**: a `SearchEverywhereContributorFactory` inner class
`LuaDocSearchEverywhereContributor.Factory` that creates the contributor from the
`AnActionEvent`.

### DOC-06-04-03: Result navigation

`LuaDocSearchItem` implements `NavigationItem` (from
`com.intellij.navigation.NavigationItem`). Its `navigate(requestFocus)` calls
`navigatablePsiElement.navigate(requestFocus)`, where the element is obtained by scanning
the file PSI for the `LuaCommentOwner` at the stored offset.

### DOC-06-04-04: Result presentation

`LuaDocSearchItem.getPresentation()` returns:
- `presentationText`: the symbol name (e.g. `"MyClass"`, `"calculate"`).
- `locationText`: the relative file path (content-root-relative, as computed by
  `ProjectFileIndex.getInstance(project).getContentRootForFile(virtualFile)` → relative
  path).
- `icon`: the icon for the PSI element type, obtained via
  `element.getIcon(0)`.

The `getElementsRenderer()` in the contributor returns
`com.intellij.ide.util.NavigationItemListCellRenderer` (no custom renderer needed).

## Behavior Rules
1. **Dumb-mode safety**: The contributor returns immediately when the project is dumb (indexes not ready).
2. **No EDT blocking**: `fetchElements` runs on a background thread already. PSI reads use
   `runReadAction` inside `ProgressIndicatorUtils.runInReadActionWithWriteActionPriority`.
3. **Cancellation**: Between file loads and before heavy PSI work, check
   `ProgressIndicator.checkCanceled()`.
4. **No stale references**: The contributor never retains hard refs to `Editor`, `PsiFile`,
   or `VirtualFile` as fields, and never caches a `Project` in a static or otherwise
   long-lived holder. A **session-scoped** `Project` field on the contributor (or on a
   per-result `LuaDocSearchItem`) is permitted: the contributor is created per Search
   Everywhere session via its `Factory` and is disposed with that session, so the field's
   lifetime is bounded by the session — it does not outlive the project. All PSI/VFS lookups
   still happen within the method call (resolving `VirtualFile`/`PsiFile` on demand, never
   stored).
5. **Same-file collisions are preserved**: A word appearing in multiple declarations within
   the same file must surface every declaration. Because a `FileBasedIndex` stores one value
   per (key, file), those declarations' records are joined into that single value with a `|`
   separator (the indexer must not drop collisions). The contributor splits on `|` to recover
   each record, then deduplicates across files at search time by `qualifiedName + fileUrl`.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | DOC-06-04-01 | `test.lua` with `---@class Vector Represents a 2D vector; local Vector = {}` | Indexer runs | `LuaDescriptionIndex` maps key `"represents"` → value `"Vector\t<file-url>\t<offset>"` and key `"vector"` → the same single record `"Vector\t<file-url>\t<offset>"`. One declaration → one record, so the value has **no** `|` separator; tokens are deduplicated per comment so `"vector"` yields exactly one record. (A second declaration in the same file sharing a word would make that key's value `"recordA\|recordB"`.) |
| 2 | DOC-06-04-02 | Project contains `---My helper function; function helper() end` in `lib.lua` | User types `"helper"` in Search Everywhere | Contributor returns one `LuaDocSearchItem` for function `helper` |
| 3 | DOC-06-04-02 | Project contains `---@class Vector Represents a 2D vector; local Vector = {}` | User types `"2d vector"` in Search Everywhere | Contributor returns `Vector` class as a result |
| 4 | DOC-06-04-02 | Project contains `---@param x number The X coordinate` in function `setPos` | User types `"coordinate"` in Search Everywhere | Contributor returns `setPos` in results |
| 5 | DOC-06-04-02 | No Lua symbols have descriptions | User types `"nothing"` in Search Everywhere | Contributor returns empty results |
| 6 | DOC-06-04-03 | Search result for function `helper` in `lib.lua` with description `My helper function` | User selects the result and presses Enter | Editor opens `lib.lua`, caret at function `helper` declaration |
| 7 | DOC-06-04-04 | Search result for `Vector` class in `src/vec.lua` | Result appears in Search Everywhere list | Item shows: name="Vector", location="src/vec.lua", a snippet of the matched description |
| 8 | DOC-06-04-01 | `test.lua` with `---@field name string The player name` inside `---@class Player` block | Indexer runs | Each word from "The player name" is indexed under the parent `LuaCommentOwner`'s qualified name (e.g. `"Player"`) |
| 9 | DOC-06-04-02 | Dumb mode (indexes not ready) | User opens Search Everywhere and types | Contributor returns empty result immediately (no NPE) |

## Acceptance Criteria
- [ ] `LuaDescriptionIndex.Indexer.map()` extracts description text from all `LuaCatsComment` blocks and emits word-level keys (TC 1, 8).
- [x] `LuaDescriptionIndex` is already registered in `plugin.xml` (line 430); no new registration needed.
- [ ] `LuaDocSearchEverywhereContributor` is registered in `plugin.xml` and appears as a tab in Search Everywhere (TC 2).
- [ ] Single-word and multi-word patterns return correct matches (TC 2, 3, 4).
- [ ] No results when nothing matches or in dumb mode (TC 5, 9).
- [ ] Selecting a result navigates to the declaration (TC 6).
- [ ] Result presentation includes name, file path, and is navigable (TC 7).
- [ ] Build passes: `./gradlew test` green with new test class.

## Non-Functional Requirements
- **Threading**: `fetchElements` runs on a pooled thread already (Search Everywhere contract). All PSI access inside `runReadAction`. Check `ProgressIndicator.checkCanceled()` per candidate file.
- **Memory**: No hard refs to `VirtualFile`/`PsiFile` retained in fields, and no static/long-lived caching of `Project`. A session-scoped `Project` field (held by the per-session contributor or a per-result `LuaDocSearchItem`) is permitted because its lifetime is bounded by the Search Everywhere session, not the project. Index values are short strings (qualified name + URL + offset), so index size is bounded by symbol count × token count.
- **Performance**: Index lookup is O(1) per token via `FileBasedIndex.getValues()`. The re-check for multi-word patterns loads the PSI of candidate files — acceptable for Could priority. A future optimization could store the full description text in the index value to avoid PSI reload.

## Dependencies
- `DOC-06-01` (Stub Indexing) — done. The contributor may optionally use `StubIndex` to resolve symbol names to PSI elements as a backup navigation path, but the primary path is via the file URL + offset stored in the index.
- `LuaDescriptionIndex` (existing at `lang/indexing/LuaDescriptionIndex.kt`) — already registered in `plugin.xml`, needs its indexer completed.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)