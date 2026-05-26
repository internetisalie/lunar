---
id: "COMP-03-03-DESIGN"
parent_id: "COMP-03-03"
folders:
  - "[[features/completion/03-cross-file-completion/03-auto-import/requirements|parent]]"
title: "COMP-03-03: Technical Design"
type: "design"
---

# COMP-03-03: Technical Design

**Phase 3 of COMP-03: Cross-file Completion**

## Architecture Overview

Phase 3 is an extension of Phase 2: it attaches an `InsertHandler` to non-imported symbol lookup elements. The handler fires when the user selects an item from the completion popup and orchestrates path resolution, template selection, deduplication, and document mutation.

```
LuaCrossFileCompletionProvider (Phase 2)
    └── getProjectGlobalSymbols()
            ↓
        GlobalSymbolCompletion (non-imported items, isImported = false)
            ↓ toLookupElement(insertHandler = LuaAutoImportInsertHandler(...))
                ↓
            LuaAutoImportInsertHandler.handleInsert()
                ├── (ReadAction) LuaModulePathResolver.resolve(targetFile, project)
                ├── (ReadAction) LuaDeduplicationChecker.isAlreadyRequired(currentFile, modulePath)
                ├── (ReadAction) LuaExportStyleDetector.detect(targetFile, project)
                ├── (ReadAction) LuaImportNameResolver.resolve(targetFile, exportStyle, currentFile)
                └── (WriteCommandAction) LuaImportInserter.insert(editor, currentFile, importStatement)
```

> **Threading Contract**: `InsertHandler.handleInsert` is invoked on the **EDT**. All PSI reads are wrapped in `runReadAction { ... }`. The document mutation is wrapped in `WriteCommandAction.runWriteCommandAction`. No I/O, network, or disk access is permitted in this call path.

## Components

### LuaAutoImportInsertHandler

**Package**: `lang/completion/`  
**Implements**: `InsertHandler<LookupElement>`  
**Lifecycle**: Stateless per-item; one instance created per non-imported `GlobalSymbolCompletion`.

```kotlin
class LuaAutoImportInsertHandler(
    private val targetFile: VirtualFile,
    private val modulePathResolver: LuaModulePathResolver,
    private val exportStyleDetector: LuaExportStyleDetector,
    private val importNameResolver: LuaImportNameResolver,
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val project = context.project
        val currentPsiFile = context.file as? LuaFile ?: return
        if (!targetFile.isValid) return

        ProgressManager.checkCanceled()

        val modulePath = runReadAction { modulePathResolver.resolve(targetFile, project) } ?: return

        val alreadyRequired = runReadAction {
            LuaDeduplicationChecker.isAlreadyRequired(currentPsiFile, modulePath)
        }
        if (alreadyRequired) return

        val exportStyle = run {
            val detected = runReadAction { exportStyleDetector.detect(targetFile, project) }
            val override = LuaProjectSettings.getInstance(project).autoImportStyle
            when (override) {
                AutoImportStyle.FORCE_LOCAL_ASSIGN -> LuaExportStyle.RETURN_STYLE
                AutoImportStyle.FORCE_GLOBAL -> LuaExportStyle.GLOBAL_STYLE
                AutoImportStyle.AUTO_DETECT -> detected
            }
        }
        val localName = runReadAction { importNameResolver.resolve(targetFile, exportStyle, currentPsiFile, project) }
        val importStatement = buildImportStatement(modulePath, exportStyle, localName)

        WriteCommandAction.runWriteCommandAction(
            project,
            "Auto-import $modulePath",
            null,
            { LuaImportInserter.insert(context.editor, currentPsiFile, importStatement) }
        )
    }

    private fun buildImportStatement(
        modulePath: String,
        exportStyle: LuaExportStyle,
        localName: String?,
    ): String = when (exportStyle) {
        LuaExportStyle.RETURN_STYLE ->
            "local ${localName ?: modulePath.substringAfterLast('.')} = require(\"$modulePath\")"
        LuaExportStyle.GLOBAL_STYLE ->
            "require(\"$modulePath\")"
    }
}
```

### LuaModulePathResolver

**Package**: `lang/path/`  
**Purpose**: Convert a `VirtualFile` to a dot-separated Lua module path string using the project's `PathConfiguration` source path patterns.

**Algorithm**: The project uses `PathConfiguration.getProjectSourcePathPatterns(project)`, which returns `SourcePathPattern` objects derived from the Lua `package.path`-style string stored in `LuaProjectSettings`. Each `SourcePathPattern` has a `spec` (e.g., `$PROJECT_DIR$/?.lua` after macro expansion) with a `?` placeholder. Resolution reverses the `interpolate()` function:

1. Obtain `patterns = PathConfiguration.getProjectSourcePathPatterns(project)`.
2. For each pattern, compute `prefix = pattern.leadingPath` and `suffix = spec.substringAfter('?')` (e.g., `.lua` or `/init.lua`).
3. If `filePath` starts with `prefix` and ends with `suffix`, the module path is the middle segment with `/` replaced by `.`.
4. `init.lua` normalisation is implicit: the `?/init.lua` pattern variant already strips the `/init.lua` suffix.
5. If no pattern matches, fall back to project-relative path and log a warning.

```kotlin
class LuaModulePathResolver {

    fun resolve(file: VirtualFile, project: Project): String? {
        if (!file.isValid) return null
        val filePath = file.path
        val patterns = PathConfiguration.getProjectSourcePathPatterns(project)

        for (pattern in patterns) {
            val prefix = pattern.leadingPath
            val suffix = pattern.spec.substringAfter(PathConfiguration.SUBSTITUTION_MARK)
            if (filePath.startsWith(prefix) && filePath.endsWith(suffix)) {
                return filePath.removePrefix(prefix).removeSuffix(suffix)
                    .replace(PathConfiguration.DIRECTORY_SEPARATOR, '.')
            }
        }

        log.warn("No source path pattern matched ${file.path}; using project-relative fallback")
        val base = project.basePath ?: return null
        val relative = filePath.removePrefix("$base/").removeSuffix(".lua")
        return relative.replace('/', '.')
    }
}
```

### LuaExportStyleDetector

**Package**: `lang/completion/`  
**Purpose**: Determine whether a module uses return-style or global-style exports.

**Detection strategy**:
1. **Fast path (stub available)**: Read `LuaFile.stub?.exportedTypeString`. Non-null means the file has an annotated root `return` → `RETURN_STYLE`.
2. **PSI fallback (stub null or exportedTypeString null)**: Scan the file's root block for any `LuaFinalStatement` beginning with `return`. Found → `RETURN_STYLE`; not found → `GLOBAL_STYLE`.
3. **Dumb mode**: Return `RETURN_STYLE` as the safe fallback (avoids global-only require for most modules).

```kotlin
enum class LuaExportStyle { RETURN_STYLE, GLOBAL_STYLE }

class LuaExportStyleDetector {

    fun detect(targetFile: VirtualFile, project: Project): LuaExportStyle {
        if (DumbService.isDumb(project)) return LuaExportStyle.RETURN_STYLE

        val psiFile = PsiManager.getInstance(project).findFile(targetFile) as? LuaFile
            ?: return LuaExportStyle.GLOBAL_STYLE

        // Fast path: use stub metadata
        val stub = psiFile.stub
        if (stub != null) {
            return if (stub.exportedTypeString != null) LuaExportStyle.RETURN_STYLE
            else detectFromPsi(psiFile)
        }

        return detectFromPsi(psiFile)
    }

    private fun detectFromPsi(file: LuaFile): LuaExportStyle {
        val hasRootReturn = file.getBlockList().any { block ->
            block.statementList.any { stmt ->
                stmt is LuaFinalStatement && stmt.text.trimStart().startsWith("return")
            }
        }
        return if (hasRootReturn) LuaExportStyle.RETURN_STYLE else LuaExportStyle.GLOBAL_STYLE
    }
}
```

### LuaImportNameResolver

**Package**: `lang/completion/`  
**Purpose**: Derive the local variable name for the return-style template.

**Priority order**:
1. Single `@class` annotation name from the target file.
2. Filename-derived identifier (lowercase, hyphens → underscores).
3. Keyword guard: append `_module` if the result is a reserved Lua keyword.
4. Conflict suffix: append incrementing number if the name is already declared locally.

```kotlin
class LuaImportNameResolver {

    fun resolve(file: VirtualFile, exportStyle: LuaExportStyle, currentFile: LuaFile, project: Project): String? {
        if (exportStyle == LuaExportStyle.GLOBAL_STYLE) return null

        val base = resolveFromClassAnnotation(file, project) ?: resolveFromFilename(file)
        val guarded = if (LuaKeywords.isReserved(base)) "${base}_module" else base
        return resolveConflict(guarded, currentFile)
    }

    private fun resolveFromClassAnnotation(file: VirtualFile, project: Project): String? {
        if (DumbService.isDumb(project)) return null
        val psiFile = PsiManager.getInstance(project).findFile(file) as? LuaFile ?: return null
        val classNames = PsiTreeUtil.findChildrenOfType(psiFile, LuaCatsComment::class.java)
            .flatMap { it.getClassTagList() }
            .mapNotNull { it.argType?.text }
        return classNames.singleOrNull()
    }

    private fun resolveFromFilename(file: VirtualFile): String =
        file.nameWithoutExtension
            .replace(Regex("[-\\s]+"), "_")
            .lowercase()

    private fun resolveConflict(name: String, currentFile: LuaFile): String {
        val localNames = collectLocalNames(currentFile)
        if (name !in localNames) return name
        var suffix = 2
        while ("$name$suffix" in localNames) suffix++
        return "$name$suffix"
    }
}
```

### LuaDeduplicationChecker

**Package**: `lang/completion/`  
**Purpose**: Detect whether a module path is already present in a top-level `require(...)` call.

```kotlin
object LuaDeduplicationChecker {

    fun isAlreadyRequired(file: LuaFile, modulePath: String): Boolean =
        collectRequirePaths(file).contains(modulePath)

    private fun collectRequirePaths(file: LuaFile): Set<String> {
        return PsiTreeUtil.findChildrenOfType(file, LuaFuncCall::class.java)
            .filter { call ->
                call.varOrExp?.var_?.nameRef?.identifier?.text == "require"
            }
            .mapNotNull { call ->
                call.args?.argList?.firstOrNull()
                    ?.let { it as? LuaLiteralExpr }
                    ?.takeIf { it.node.firstChildNode?.elementType == LuaTypes.STRING }
                    ?.text?.trim('"', '\'')
            }
            .toSet()
    }
}
```

> **Known Limitation**: `require` calls with non-string-literal arguments (e.g., `require(varName)`) are not collected; this is intentional and correct (we cannot statically resolve the path).

### LuaImportInserter

**Package**: `lang/completion/`  
**Purpose**: Find the correct insertion anchor and mutate the document.

The sealed `InsertionAnchor` hierarchy captures the three possible insert positions and keeps `when` expressions exhaustive:

```kotlin
sealed class InsertionAnchor
data class AfterLastRequire(val element: PsiElement) : InsertionAnchor()
data class AfterHeaderComments(val element: PsiElement) : InsertionAnchor()
object AtFileStart : InsertionAnchor()
```

```kotlin
object LuaImportInserter {

    fun insert(editor: Editor, file: LuaFile, importStatement: String) {
        // Commit document so PSI tree reflects any text the completion framework
        // already inserted before our handler was called.
        PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)

        if (!editor.document.isWritable) {
            ReadonlyStatusHandler.getInstance(file.project)
                .ensureFilesWritable(listOf(file.virtualFile))
            if (!editor.document.isWritable) {
                Notifications.Bus.notify(
                    Notification("Lunar", "Auto-import failed",
                        "File is read-only: ${file.name}", NotificationType.ERROR)
                )
                return
            }
        }

        val anchor = resolveAnchor(file)
        val (offset, text) = when (anchor) {
            is AfterLastRequire -> Pair(anchor.element.textRange.endOffset, "\n$importStatement")
            is AfterHeaderComments -> Pair(anchor.element.textRange.endOffset, "\n\n$importStatement")
            is AtFileStart -> Pair(0, "$importStatement\n")
        }

        editor.document.insertString(offset, text)
        PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
    }

    private fun resolveAnchor(file: LuaFile): InsertionAnchor {
        val topLevel = file.getBlockList().firstOrNull()?.statementList ?: emptyList()

        val contiguousRequires = topLevel
            .takeWhile { it.isRequireCallStatement() || it.isHeaderElement() }
            .filter { it.isRequireCallStatement() }
        if (contiguousRequires.isNotEmpty()) {
            return AfterLastRequire(contiguousRequires.last())
        }

        val headerElements = file.firstChild
            ?.siblings(forward = true, withSelf = true)
            ?.takeWhile { it.isHeaderElement() }
            ?.toList()
        if (!headerElements.isNullOrEmpty()) {
            return AfterHeaderComments(headerElements.last())
        }

        return AtFileStart
    }
}
```

## Data Models

```kotlin
// Export style for template selection (in LuaExportStyleDetector)
enum class LuaExportStyle { RETURN_STYLE, GLOBAL_STYLE }

// User-configurable override for auto-import template style (in LuaProjectSettings)
enum class AutoImportStyle { AUTO_DETECT, FORCE_LOCAL_ASSIGN, FORCE_GLOBAL }
```

## Integration with Phase 2

`GlobalSymbolCompletion` (Phase 2 data class) must be extended to carry:

```kotlin
data class GlobalSymbolCompletion(
    val name: String,
    val psiElement: PsiElement,
    val sourceVirtualFile: VirtualFile,   // NEW: required by Phase 3
    val isImported: Boolean,              // NEW: true = Phase 1 path, false = Phase 2 global
    val proximityWeight: Double,
    val isClassType: Boolean = false,
)

fun GlobalSymbolCompletion.toLookupElement(project: Project): LookupElement {
    val handler: InsertHandler<LookupElement>? = if (!isImported) {
        LuaAutoImportInsertHandler(
            targetFile = sourceVirtualFile,
            modulePathResolver = LuaModulePathResolver(),
            exportStyleDetector = LuaExportStyleDetector(),
            importNameResolver = LuaImportNameResolver(),
        )
    } else null

    val tailText = if (!isImported) " (auto-import)" else null

    return LookupElementBuilder.create(name)
        .withInsertHandler(handler)
        .withTailText(tailText, true)
        // ...icon, typeText, etc.
}
```

**Rule**: Only items with `isImported = false` receive `LuaAutoImportInsertHandler`. Phase 1 (imported) items never trigger auto-import.

## Performance Profile

| Operation | Expected Time | Notes |
| :--- | :--- | :--- |
| Module path resolution | < 5ms | String ops on source root list |
| Export style detection | < 2ms | Single warm index lookup |
| Name resolution | < 2ms | Single `@class` index query + filename ops |
| Deduplication scan | < 10ms | PSI child iteration on current file only |
| Document mutation | < 5ms | Single `document.insertString` |
| **Handler total** | **< 25ms** | Well within user perception threshold |

All PSI traversals call `ProgressManager.checkCanceled()` at loop entry points.

## Edge Cases & Mitigations

| Edge Case | Impact | Mitigation |
| :--- | :--- | :--- |
| `targetFile.isValid == false` | Handler operates on stale VirtualFile | Check `isValid` at handler entry; return early if false |
| Multiple source roots match same path | Ambiguous module path | Use longest-prefix root match (most specific wins) |
| Source root not configured | No prefix to strip | Fall back to project-relative path; log warning |
| `@class` name is a Lua keyword | Generated code is invalid | Detected by `LuaKeywords.isReserved`; append `_module` |
| Insert into read-only file | `document.insertString` throws | Wrap in `ReadonlyStatusHandler`; show error notification |
| Shebang `#!/usr/bin/env lua` on line 1 | Must not insert before it | `findLastHeaderElement` classifies shebang as a header element |
| File contains only blank lines | `findLastHeaderElement` returns null | Treated as empty file; insert at offset 0 |
| Very large file (10k+ lines) | Slow dedup scan | `PsiTreeUtil.findChildrenOfType` is O(n); acceptable for single-file scan |
| `init.lua` at project root | Empty module path after stripping | Produce empty string → warn; caller uses filename as fallback |

## Configuration

All toggles live in `LuaProjectSettings` (project-level, `.idea/lunar.xml`):

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `showAutoImportHints` | `Boolean` | `true` | Show `(auto-import)` tail text in completion popup |
| `autoImportStyle` | `AutoImportStyle` | `AUTO_DETECT` | Override template: `AUTO_DETECT`, `FORCE_LOCAL_ASSIGN`, `FORCE_GLOBAL` |

## Testing Strategy

### Unit Tests (`LuaAutoImportTest.kt`, new)
- `LuaModulePathResolver`: nested path, `init.lua`, no matching source path pattern, multiple patterns, invalid file
- `LuaExportStyleDetector`: stub fast path (non-null `exportedTypeString`), PSI `LuaFinalStatement` fallback, dumb mode fallback
- `LuaImportNameResolver`: @class single, @class multiple (→ filename), keyword guard, conflict suffix
- `LuaDeduplicationChecker`: duplicate found with double-quotes, no duplicate, single-quoted require, no-paren `require "foo"` form, non-string-arg `require` ignored
- `LuaImportInserter`: after contiguous leading requires, after `--` header comments, after `--[[...]]` block comment, empty file, shebang, non-contiguous require ignored, read-only file notification

### Integration Tests (`LuaAutoImportIntegrationTest.kt`, new)
- TC-03-01 through TC-03-26 using `myFixture.configureByText` / multi-file fixture setup
- Undo/Redo behaviour: verify `WriteCommandAction` is independently undoable

### Manual Tests (sandbox IDE)
- Auto-import from real multi-module project
- Undo after auto-import
- Large file performance (500+ statements)
- Settings toggle: `showAutoImportHints = false` hides tail text
- Mixed Lua versions (5.1, 5.4, Luau project)

## See Also

- **Requirements**: [[requirements|Phase 3 Requirements]]
- **Risks & Gaps**: [[risks-and-gaps|Risks & Gaps]]
- **Implementation Plan**: [[implementation-plan|Implementation Plan]]
- **Phase 2 Design**: [[../02-project-wide-globals/design|Phase 2 Technical Design]]
- **Parent Architecture**: [[../design|COMP-03 Architecture Overview]]
- **Tracker**: [[saga://task/345|Task 345]]
