---
id: "DOC-06-04-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "DOC-06-04"
folders:
  - "[[features/documentation/06-04-full-text-documentation-search/requirements|requirements]]"
---

# Technical Design: DOC-06-04 — Full-Text Documentation Search

## 1. Architecture Overview

### Current State

`LuaDescriptionIndex` (`lang/indexing/LuaDescriptionIndex.kt:19`) is a
`FileBasedIndexExtension<String, String>` already registered in `plugin.xml` (line 426). Its
`Indexer.map()` (line 51) returns an empty `Map`, making the index a no-op. The
infrastructure (registration, `InputFilter`, `DataExternalizer`, version `1`) is complete —
only the extraction logic is missing.

No `SearchEverywhereContributor` exists for Lunar today. The existing stub indexes
(`LuaClassNameIndex`, `LuaAliasIndex`, `LuaGlobalDeclarationIndex`) support
Go-to-Symbol / Go-to-Class / Go-to-File, but no contributor searches description text.

### Prior Art in This Repo

| Component | Location | Relationship |
|-----------|----------|-------------|
| `LuaDescriptionIndex` | `lang/indexing/LuaDescriptionIndex.kt:19` | **Extends** — complete its indexer |
| `LuaCatsTypeNameIndex` | `lang/indexing/LuaCatsTypeNameIndex.kt:37` | Reference for `FileBasedIndexExtension` pattern |
| `LuaFileBindingsIndex` | `lang/indexing/LuaFileBindingsIndex.kt:31` | Reference for file-level compound value serialization |
| `LuaDocumentationTargetProvider` | `lang/doc/LuaDocumentationTargetProvider.kt:25` | Reference for resolving `LuaCommentOwner` and using `StubIndex` |
| `LuaCatsSummary` | `lang/syntax/LuaComment.kt:18` | Reference for `LuaCatsComment` utility patterns; note: `getText()` only extracts free-form description lines, not tag descriptions — the new `collectDescriptionText()` in the same file handles the full extraction |
| `LuaCatsDocumentationRenderer` | `luacats/lang/doc/LuaCatsDocumentationRenderer.kt` | Reference for which tags expose `getDescription()` |

No existing component performs the same function — the design is net-new in terms of
functionality, extending the existing placeholder index.

### Target State

```
┌─────────────────────┐     ┌──────────────────────────┐
│ LuaDescriptionIndex │────▶│ FileBasedIndex           │
│ (completed indexer) │     │ Key: word (String)       │
│                     │     │ Value: "name\turl\toffset"│
└─────────────────────┘     └──────────┬───────────────┘
                                       │ getValues(word)
                                       ▼
┌─────────────────────────────────────────────────────────┐
│ LuaDocSearchEverywhereContributor                       │
│ SearchEverywhereContributor<LuaDocSearchItem>           │
│                                                         │
│ fetchElements(pattern, indicator, consumer):            │
│   1. Tokenize pattern into words                        │
│   2. Lookup first word in LuaDescriptionIndex           │
│   3. Deduplicate, re-check multi-word                  │
│   4. Resolve file+offset → LuaCommentOwner              │
│   5. Create LuaDocSearchItem, call consumer.process()   │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────┐
│ LuaDocSearchItem : NavigationItem           │
│                                             │
│ - name: String     (symbol name)            │
│ - location: String (relative file path)     │
│ - navigate(): opens file at declaration     │
└─────────────────────────────────────────────┘
```

## 2. Core Components

### 2.1 Complete `LuaDescriptionIndex.Indexer.map()` (extension of existing type)

`net.internetisalie.lunar.lang.indexing.LuaDescriptionIndex` (existing, file:line = `lang/indexing/LuaDescriptionIndex.kt:19`)

The `private class Indexer` (line 50) is currently a stub returning an empty map. This
design **completes** it.

- **Responsibility**: Traverse each `.lua` file's PSI tree, find every `LuaCommentOwner`
  with an attached `LuaCatsComment`, collect all description text, tokenize into words, and
  emit one index entry per word.
- **Threading**: Called during indexing on a background thread by the platform. Access to
  `FileContent.psiFile` is already safe.
- **Collaborators**:
  - `LuaCatsComment.getDescriptionList()` (gen source: `LuaCatsComment.java:25` —
    `List<LuaCatsDescription>`)
  - `LuaCatsClassTag.getDescription()` (gen source: `LuaCatsClassTag.java:17` —
    `@Nullable LuaCatsDescription`)
  - `LuaCatsAliasTag.getDescription()` (gen source: `LuaCatsAliasTag.java:16`)
  - `LuaCatsParamTag.getDescription()`, `LuaCatsReturnTag.getDescription()`,
    `LuaCatsFieldTag.getDescription()`, `LuaCatsTypeTag.getDescription()`,
    `LuaCatsDeprecatedTag.getDescription()`, `LuaCatsSeeTag.getDescription()`,
    `LuaCatsOverloadTag.getDescription()` — all gen PSI interfaces with
    `@Nullable LuaCatsDescription getDescription()`
  - `LuaCatsDescription.text` (`PsiElement.text`)
  - `LuaCommentOwner.catsComment` (Kotlin: `LuaCatsBaseElements.kt:7` — interface property)
  - `LuaCommentOwner` (Kotlin: `LuaBaseElements.kt:109` — interface extending
    `LuaCatsCommentOwner`)
  - `PsiElement.textOffset`, `VirtualFile.url`
- **Key API** — replacement for the inner `Indexer` class, and the shared description-text extraction function:

  `collectDescriptionText()` is a top-level `internal` function added to
  `lang/syntax/LuaComment.kt` (alongside `LuaCatsSummary`) so both the indexer and the
  contributor (§2.4) can use the same extraction logic.

  ```kotlin
  // lang/syntax/LuaComment.kt — top-level internal function
  internal fun collectDescriptionText(comment: LuaCatsComment): String {
      val sb = StringBuilder()
      for (desc in comment.descriptionList) {
          sb.append(desc.text).append(' ')
      }
      for (tag in comment.classTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      for (tag in comment.aliasTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      for (tag in comment.paramTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      for (tag in comment.returnTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      for (tag in comment.fieldTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      for (tag in comment.typeTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      for (tag in comment.deprecatedTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      for (tag in comment.seeTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      for (tag in comment.overloadTagList) {
          tag.description?.text?.let { sb.append(it).append(' ') }
      }
      return sb.toString()
  }

  // inside LuaDescriptionIndex, replacing the existing stub
  private class Indexer : DataIndexer<String, String, FileContent> {
      override fun map(inputData: FileContent): Map<String, String> {
          val result = mutableMapOf<String, String>()
          val psiFile = inputData.psiFile
          if (psiFile !is LuaFile) return result

          val fileUrl = inputData.file.url
          PsiTreeUtil.findChildrenOfType(psiFile, LuaCommentOwner::class.java)
              .forEach { owner ->
                  val catsComment = owner.catsComment ?: return@forEach
                  val descriptionText = collectDescriptionText(catsComment)
                  if (descriptionText.isBlank()) return@forEach
                  val tokens = descriptionText
                      .lowercase()
                      .split(Regex("[^a-z0-9]+"))
                      .filter { it.length >= 2 }
                  if (tokens.isEmpty()) return@forEach
                  val ownerName = (owner as? PsiNamedElement)?.name ?: owner.text
                  val value = "$ownerName\t$fileUrl\t${owner.textOffset}"
                  for (token in tokens) {
                      result.merge(token, value) { existing, _ -> existing }
                  }
              }
          return result
      }
  }
  ```

- **Version bump**: increment `getVersion()` from `1` to `2` in `LuaDescriptionIndex` to
  trigger re-indexing.

### 2.2 `LuaDocSearchItem`

`net.internetisalie.lunar.lang.doc.LuaDocSearchItem`

- **Responsibility**: Navigation item for Search Everywhere results. Holds a file URL +
  offset, lazily resolves to a `NavigationItem`.
- **Threading**: `navigate()` is called on EDT. PSI lookup inside
  `runReadAction { ... }`.
- **Collaborators**:
  - `com.intellij.navigation.NavigationItem`
  - `com.intellij.navigation.ItemPresentation`
  - `VirtualFileManager.getInstance().findFileByUrl(String): VirtualFile` (IntelliJ API)
  - `PsiManager.getInstance(project).findFile(VirtualFile): PsiFile` (IntelliJ API)
  - `ProjectFileIndex.getInstance(project).getContentRootForFile(VirtualFile): VirtualFile` (IntelliJ API)
  - `VfsUtilCore.getRelativePath(VirtualFile, VirtualFile): String` (IntelliJ API)
  - `PsiElement.getIcon(int): Icon` (IntelliJ API)
- **Key API**:

  ```kotlin
  class LuaDocSearchItem(
      private val project: Project,
      val symbolName: String,
      private val fileUrl: String,
      private val declarationOffset: Int
  ) : NavigationItem {
      override fun getName(): String? = symbolName

      override fun getPresentation(): ItemPresentation? {
          val vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return null
          val relativePath = ProjectFileIndex.getInstance(project)
              .getContentRootForFile(vFile)?.let { VfsUtilCore.getRelativePath(vFile, it) }
              ?: vFile.name
          return object : ItemPresentation {
              override fun getPresentableText(): String = symbolName
              override fun getLocationString(): String = relativePath
              override fun getIcon(unused: Boolean): Icon? {
                  return runReadAction {
                      PsiManager.getInstance(project).findFile(vFile)
                          ?.findElementAt(declarationOffset)
                          ?.parent
                          ?.getIcon(0)
                  }
              }
          }
      }

      override fun navigate(requestFocus: Boolean) {
          val vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return
          runReadAction {
              val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@runReadAction
              val element = psiFile.findElementAt(declarationOffset)
              (element as? Navigatable)?.navigate(requestFocus)
          }
      }

      override fun canNavigate(): Boolean = true
      override fun canNavigateToSource(): Boolean = true
  }
  ```

### 2.3 `LuaDocSearchEverywhereContributor`

`net.internetisalie.lunar.lang.doc.LuaDocSearchEverywhereContributor`

- **Responsibility**: Search Everywhere contributor for Lua documentation text. Queries
  `LuaDescriptionIndex` and returns `LuaDocSearchItem` matches.
- **Threading**: `fetchElements` is called on a background thread. PSI lookups use
  `ProgressIndicatorUtils.runInReadActionWithWriteActionPriority` (following the YAML
  plugin pattern at `intellij-community/plugins/yaml/backend/src/navigation/YAMLKeysSearchEverywhereContributor.java:74`).
- **Collaborators**:
  - `com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor<T>`
  - `com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory<T>`
  - `com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager`
  - `com.intellij.ide.util.NavigationItemListCellRenderer`
  - `com.intellij.openapi.progress.ProgressIndicatorUtils`
  - `FileBasedIndex.getInstance().getValues(LuaDescriptionIndexName, word, scope)`
  - `com.intellij.openapi.project.DumbService.isDumb(project)`
- **Key API**:

  ```kotlin
  class LuaDocSearchEverywhereContributor(
      private val project: Project
  ) : SearchEverywhereContributor<LuaDocSearchItem> {

      override fun getSearchProviderId(): String =
          this::class.java.simpleName

      override fun getGroupName(): String = "Lua Documentation"

      override fun getSortWeight(): Int = 600  // after Symbols (300), before YAML keys (1000)

      override fun showInFindResults(): Boolean = true

      override fun fetchElements(
          pattern: String,
          progressIndicator: ProgressIndicator,
          consumer: Processor<in LuaDocSearchItem>
      ) {
          if (project.isDisposed || DumbService.isDumb(project) || pattern.isBlank()) return

          val tokens = pattern.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
          if (tokens.isEmpty()) return

          ApplicationManager.getApplication().assertIsNonDispatchThread()

          val task = Runnable {
              if (DumbService.isDumb(project)) return@Runnable
              val scope = try {
                  if (SearchEverywhereManager.getInstance(project).isEverywhere)
                      GlobalSearchScope.allScope(project)
                  else GlobalSearchScope.projectScope(project)
              } catch (_: IllegalStateException) {
                  GlobalSearchScope.projectScope(project)
              }

              // Step 1: candidate lookup from first token
              val index = FileBasedIndex.getInstance()
              val candidateValues = index.getValues(LuaDescriptionIndexName, tokens[0], scope)
              val seen = hashSetOf<String>()

              for (value in candidateValues) {
                  ProgressIndicatorUtils.checkCancelled()
                  val parts = value.split('\t')
                  if (parts.size != 3) continue
                  val (name, fileUrl, offsetStr) = parts
                  val offset = offsetStr.toIntOrNull() ?: continue
                  val dedupKey = "$name:$fileUrl"
                  if (!seen.add(dedupKey)) continue

                  // Step 2: multi-word re-check
                  if (tokens.size > 1) {
                      if (!descriptionContainsAllTokens(fileUrl, offset, tokens)) continue
                  }

                  val item = LuaDocSearchItem(project, name, fileUrl, offset)
                  if (!consumer.process(item)) return
              }
          }
          ProgressIndicatorUtils.yieldToPendingWriteActions()
          ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(task, progressIndicator)
      }

      override fun processSelectedItem(
          selected: LuaDocSearchItem,
          modifiers: Int,
          searchText: String
      ): Boolean {
          selected.navigate(true)
          return true
      }

      override fun getElementsRenderer(): ListCellRenderer<in Any> =
          NavigationItemListCellRenderer()

      class Factory : SearchEverywhereContributorFactory<LuaDocSearchItem> {
          override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<LuaDocSearchItem> {
              return LuaDocSearchEverywhereContributor(initEvent.project!!)
          }
      }
  }
  ```

### 2.4 Description verification helper (private to contributor)

A private function in `LuaDocSearchEverywhereContributor`. Uses the **same
`collectDescriptionText()`** utility as the indexer (§2.1) — imported from
`net.internetisalie.lunar.lang.syntax.collectDescriptionText` — so that tag
descriptions (e.g. the `"Represents a 2D vector"` part of
`---@class Vector Represents a 2D vector`) are included in the re-check,
not just free-form description lines.

```kotlin
import net.internetisalie.lunar.lang.syntax.collectDescriptionText

private fun descriptionContainsAllTokens(
    fileUrl: String,
    declOffset: Int,
    tokens: List<String>
): Boolean {
    val vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return false
    return runReadAction {
        val psiFile = PsiManager.getInstance(project).findFile(vFile) as? LuaFile ?: return@runReadAction false
        val element = psiFile.findElementAt(declOffset) ?: return@runReadAction false
        val owner = element.parent as? LuaCommentOwner ?: return@runReadAction false
        val comment = owner.catsComment ?: return@runReadAction false
        val fullText = collectDescriptionText(comment)
        if (fullText.isBlank()) return@runReadAction false
        tokens.all { token -> fullText.lowercase().contains(token) }
    }
}
```

## 3. Algorithms

### 3.1 Description text extraction

- **Input**: `LuaCatsComment` (the PSI root of a `---` doc comment block)
- **Output**: `String` — whitespace-separated concatenation of all description text in the
  comment.
- **Steps**:
  1. Initialize empty `StringBuilder`.
  2. For each `LuaCatsDescription` in `comment.getDescriptionList()`, append
     `desc.text` + `" "`.
  3. For each tag type `T` with `getDescription()` (see table below), iterate
     `comment.get<TagName>List()`, and for each tag with a non-null description, append
     `tag.description.text` + `" "`.
  4. Return `sb.toString()`.

| Tag PSI type (`LuaCats*`) | Accessor on `LuaCatsComment` | Has `getDescription()`? |
|---------------------------|-----------------------------|-------------------------|
| `LuaCatsClassTag` | `getClassTagList()` | ✅ `LuaCatsClassTag.java:17` |
| `LuaCatsAliasTag` | `getAliasTagList()` | ✅ `LuaCatsAliasTag.java:16` |
| `LuaCatsParamTag` | `getParamTagList()` | ✅ |
| `LuaCatsReturnTag` | `getReturnTagList()` | ✅ |
| `LuaCatsFieldTag` | `getFieldTagList()` | ✅ |
| `LuaCatsTypeTag` | `getTypeTagList()` | ✅ |
| `LuaCatsDeprecatedTag` | `getDeprecatedTagList()` | ✅ |
| `LuaCatsSeeTag` | `getSeeTagList()` | ✅ |
| `LuaCatsOverloadTag` | `getOverloadTagList()` | ✅ |
| `LuaCatsEnumTag` | `getEnumTagList()` | ✅ (via `typeOptionList`) |
| `LuaCatsMetaTag` | `getMetaTagList()` | ✅ |
| `LuaCatsNodiscardTag` | `getNodiscardTagList()` | ✅ |
| `LuaCatsAsyncTag` | `getAsyncTagList()` | ✅ |

### 3.2 Search pattern matching (multi-word re-check)

- **Input**: `fileUrl: String`, `declOffset: Int`, `tokens: List<String>` (tokenized
  user pattern, all lowercase)
- **Output**: `Boolean` — whether the full description of the symbol contains ALL tokens
- **Steps**:
  1. Resolve `VirtualFile` from `fileUrl`.
  2. In `runReadAction`, load `PsiFile`, find element at `declOffset`.
  3. Walk up to `LuaCommentOwner`.
  4. Get `catsComment`, extract full text via `collectDescriptionText(comment)` (the same shared
     utility as the indexer — §2.1, §3.1 — which includes tag descriptions).
  5. Lowercase the full text.
  6. Return `tokens.all { token -> lowercasedText.contains(token) }`.

  - **Empty/None handling**: If any step returns null (file not found, no PSI, no comment,
    no summary text), return `false`.

### 3.3 Tokenization (word extraction for index keys)

- **Input**: `descriptionText: String` (the concatenated output from §3.1)
- **Output**: `Set<String>` of lowercase alphanumeric tokens, length ≥ 2
- **Steps**:
  1. `lowercased = descriptionText.lowercase()`
  2. Split on `[^a-z0-9]+` (one or more non-alphanumeric characters)
  3. Filter: keep tokens with `length >= 2`
  4. Return as `Set` (deduplicates within a single comment — a word appearing twice in the
     same description still produces only one index entry for that comment)

## 4. External Data & Parsing

This feature consumes no external CLI output, network responses, or file formats. All data
comes from PSI (`LuaCatsComment` children), which is already parsed by the existing
`LuaCatsParser` (gen source: `luacats/lang/parser/LuaCatsParser.java`). No new parsing
required.

## 5. Data Flow

### Example 1: User searches for "vector"

Input file `src/geom.lua`:
```lua
---@class Vector Represents a 2D vector with x and y components
---@field x number The X coordinate
local Vector = {}
```

1. **Indexing** (happens on file save/modification):
   - Indexer visits `Vector` `LuaLocalVarDecl` (a `LuaCommentOwner`).
   - Gets `catsComment` → `LuaCatsComment` for the `---` block.
   - Extracts description text: `"Represents a 2D vector with x and y components"` +
     `"The X coordinate"` = full text.
   - Tokenizes → `{"represents", "2d", "vector", "with", "and", "components", "the",
     "coordinate"}`.
   - For each token, emits key→value: e.g. `"vector"` →
     `"Vector\tsrc/geom.lua\t<offset>"`.

2. **Search** (user types `"vector"` in Search Everywhere):
   - `fetchElements("vector", indicator, consumer)` invoked.
   - Tokenized: `["vector"]`.
   - `FileBasedIndex.getValues(LuaDescriptionIndexName, "vector", scope)` returns
     `["Vector\tsrc/geom.lua\t<offset>"]`.
   - Single-token pattern → no re-check needed.
   - Creates `LuaDocSearchItem("Vector", "src/geom.lua", <offset>)`, calls
     `consumer.process(item)`.

3. **Navigation**: User selects the result → `navigate(true)` opens `src/geom.lua` at the
   `local Vector = {}` line.

### Example 2: Multi-word search for "2d vector"

1. `fetchElements("2d vector", indicator, consumer)`.
2. Tokenized: `["2d", "vector"]`.
3. First token `"2d"` → index lookup gives candidate `"Vector\tsrc/geom.lua\t<offset>"`.
4. Multi-word re-check: loads `src/geom.lua` PSI at offset, finds `Vector`'s comment,
   extracts full text, checks `fullText.lowercase().contains("2d") && fullText.lowercase().contains("vector")` → `true`.
5. Processes `LuaDocSearchItem`.

## 6. Edge Cases

| Case | Handling |
|------|----------|
| File has no `LuaCommentOwner` elements | Indexer produces empty map for that file |
| `LuaCatsComment` has tags but no description text (e.g. `---@class Foo`) | `collectDescriptionText()` returns blank → indexer skips (no entries) |
| User pattern contains only stop-words (e.g. `"the"`, `"a"`) — tokenized to words of length < 2 | No tokens → contributor returns empty |
| Multiple symbols in same file share description words | Each produces its own index entry. The contributor deduplicates by `name:fileUrl` |
| File deleted between index and search | `findFileByUrl()` returns null → re-check returns `false`, item skipped |
| Pattern matches description but symbol name is ambiguous (same name in multiple files) | Each file produces distinct `LuaDocSearchItem`; user sees both in results differentiated by file path |
| Dumb mode during search | `fetchElements` returns immediately (no NPE) |
| Index version mismatch | Incrementing version to 2 triggers re-index on next IDE start |
| Comment attached to unnamed element (should not happen, but defensive) | `ownerName` falls back to `owner.text` (e.g. `"function"` keyword) |

## 7. Integration Points

### 7.1 Existing registration (no change needed)

```xml
<!-- plugin.xml line 426 — already present -->
<fileBasedIndex implementation="net.internetisalie.lunar.lang.indexing.LuaDescriptionIndex"/>
```

### 7.2 New registration — Search Everywhere contributor

```xml
<!-- plugin.xml — add under <extensions defaultExtensionNs="com.intellij"> -->
<searchEverywhereContributor
    implementation="net.internetisalie.lunar.lang.doc.LuaDocSearchEverywhereContributor$Factory"/>
```

The `$Factory` suffix references the inner class `Factory` implementing
`SearchEverywhereContributorFactory<LuaDocSearchItem>`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| DOC-06-04-01 | M | §2.1 (completed `LuaDescriptionIndex` indexer), §3.1 (description extraction), §3.3 (tokenization) |
| DOC-06-04-02 | M | §2.3 (`LuaDocSearchEverywhereContributor`), §3.2 (multi-word re-check), §5 (data flow examples) |
| DOC-06-04-03 | M | §2.2 (`LuaDocSearchItem.navigate()`) |
| DOC-06-04-04 | S | §2.2 (`LuaDocSearchItem.getPresentation()`) |

## 9. Alternatives Considered

| Option | Rejected because |
|--------|------------------|
| Store full description text in index value and avoid PSI re-check for multi-word | Would inflate index size; PSI re-check is infrequent (only when multi-word pattern AND first token matches). Acceptable for Could priority. |
| Use `StubIndex` names as the primary lookup (search by symbol name, not description) | This is already covered by Go-to-Symbol (`LuaGotoSymbolContributor`) — this feature specifically searches *description text*, not names. |
| Integrate with Find in Files (use existing `FileTextSearch` scope) | Find in Files already greps file content; no custom integration needed. Search Everywhere is more discoverable. |
| Implement fuzzy/tolerant matching | Could upgrade path — exact substring matching is sufficient for initial delivery of a Could-priority feature. |

## 10. Open Questions

_None — feature has cleared the planning bar._