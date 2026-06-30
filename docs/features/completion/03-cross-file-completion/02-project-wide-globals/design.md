---
id: "COMP-03-02-DESIGN"
parent_id: "COMP-03-02"
folders:
  - "[[features/completion/03-cross-file-completion/02-project-wide-globals/requirements|requirements]]"
title: "COMP-03-02: Technical Design"
type: "design"
---

# COMP-03-02: Technical Design

**Phase 2 of COMP-03: Cross-file Completion**

## Architecture Overview

```
LuaCompletionContributor (Phase 1)
    ↓
LuaCrossFileCompletionProvider
    ├── getImportedSymbols() [Phase 1]
    └── getProjectGlobalSymbols() [Phase 2 NEW]
            ↓
        GlobalSymbolRankingService
            ├── queryStubIndex()
            ├── filterVisibility()
            ├── deduplicateWithLocal()
            └── rankByProximity()
```

## Components

### LuaExportedGlobalIndex
**Purpose**: New index (or enhancement) to distinguish exported symbols from local/internal ones.

**Index Contract**:
- **Indexed Symbols**: Top-level function definitions, @class-decorated functions, table return assignments
- **Excluded**: Locals, block-scoped symbols, private (_-prefixed internals)
- **Metadata**: Export type (FUNCTION, CLASS, TABLE_RETURN), file path, line number

**Implementation**:
```kotlin
interface LuaExportedGlobalIndex : StringStubIndexExtension<LuaNamedElement> {
    override fun getVersion(): Int = 3
    override fun get(key: String, project: Project, scope: GlobalSearchScope)
        : Collection<LuaNamedElement>
}
```

### GlobalSymbolRankingService
**Purpose**: Query, filter, rank, and deduplicate global symbols (lightweight, proximity-only).

**Key Methods**:
```kotlin
fun getProjectGlobalSymbols(
    completionContext: CompletionContext,
    localSymbols: Set<String>,
    importedSymbols: Set<String>
): List<GlobalSymbolCompletion>

data class GlobalSymbolCompletion(
    val name: String,
    val psiElement: PsiElement,
    val proximityWeight: Double,
    val isClassType: Boolean = false
)
```

**Implementation Steps**:
1. Query `LuaExportedGlobalIndex.processElements()` with project scope
2. Filter out `_`-prefixed symbols (visibility rule)
3. Deduplicate against `localSymbols` (by name) and `importedSymbols` (by PSI element)
4. Calculate proximity weight for each symbol
5. Use `PrioritizedLookupElement` to integrate ranking with IDE's weigher system
6. Return deduplicated list sorted by weight (no result limit — let IDE handle rendering)

### ProximityCalculator
**Purpose**: Compute proximity weight based on file/module structure (fast, deterministic).

**Proximity Weights**:
- Same module: 0.9
- Same directory: 0.7
- Different module: 0.5

**Note**: Usage frequency ranking is deferred to Phase 4 due to performance constraints with large projects.

**Implementation**:
```kotlin
fun calculateProximityWeight(
    currentFile: PsiFile,
    symbolFile: PsiFile
): Double {
    return when {
        isSameModule(currentFile, symbolFile) -> 0.9
        isSameDirectory(currentFile, symbolFile) -> 0.7
        else -> 0.5
    }
}
```

### Integration with LuaCrossFileCompletionProvider
**Modify existing provider** to call `GlobalSymbolRankingService` after Phase 1:

```kotlin
override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
) {
    val localSymbols = getLocalSymbols(parameters)
    val importedSymbols = getImportedSymbols(parameters)
    
    // Phase 1: Add imported symbols
    importedSymbols.forEach { result.addElement(it.toLookupElement()) }
    
    // Phase 2: Add project-wide globals (with proximity weighting)
    val globalSymbols = globalSymbolService.getProjectGlobalSymbols(
        parameters,
        localSymbols,
        importedSymbols
    )
    globalSymbols.forEach { symbol ->
        val element = symbol.toLookupElement()
        val weighted = PrioritizedLookupElement.withPriority(
            element, 
            symbol.proximityWeight
        )
        result.addElement(weighted)
    }
}
```

## Data Models

### GlobalSymbolMetadata
Store in symbol PSI (via stubs):
```kotlin
data class GlobalSymbolMetadata(
    val name: String,
    val type: SymbolType, // Function, Class, Alias
    val visibility: Visibility, // Public, Internal (_prefix), External
    val filePath: String,
    val lineNumber: Int,
    val isExport: Boolean // Whether explicitly exported
)

enum class SymbolType { FUNCTION, CLASS, ALIAS, VARIABLE }
enum class Visibility { PUBLIC, INTERNAL }
```

## Performance & Caching Strategy

### Query Optimization
- **No result limits**: Return all results; let IDE handle rendering/pagination
- **Batch visibility filtering**: Single pass over candidates
- **Deduplication**: Use Set for O(1) lookups against local/imported symbols
- **Proximity calculation**: O(1) per symbol (no tree traversal)
- **Sorting**: In-memory sort acceptable for typical projects (<1000 globals)

### Performance Targets
- **Per-symbol operation**: ~0.1ms (proximity calc, dedup check)
- **StubIndex query**: ~20-50ms (for 1000-5000 symbols)
- **Phase 2 total**: ~50-100ms (including ranking)
- **Phase 1 + 2 combined**: ~150-250ms
- **Large projects (10k symbols)**: Graceful degradation via cancellation

### Caching Strategy
- **Request-scope**: StubIndex results cached during single completion session
- **No persistent cache**: Let IDE's index handle invalidation
- **Cancellation support**: Check `ProgressManager.checkCanceled()` in loops

### Dumb Mode Support
- If `DumbService.isDumb(project)`, return empty list (indexing disabled)
- No errors; graceful degradation

## Configuration & Toggles

### Feature Flags
```kotlin
// Suppress underscore-prefixed symbols (default: true)
val suppressUnderscorePrefixed: Boolean = 
    LuaSettings.getInstance(project).suppressUnderscorePrefixedGlobals

// Suppress stdlib symbols (default: false)
val suppressStdlib: Boolean =
    LuaSettings.getInstance(project).suppressStdlibGlobals
```

### Lua Version Compatibility
- **Lua 5.1**: Recognize module patterns (return table, global assignments)
- **Lua 5.4 + Luau**: Recognize `@class` annotations (LuaCATS)
- **Default Heuristic**: Assume exported globals are module-style for 5.1-compatible projects

## Edge Cases & Mitigations

| Edge Case | Impact | Mitigation |
|-----------|--------|-----------|
| Circular dependencies | May cause duplicate symbols | Deduplication by PSI identity |
| Symbol name collisions | Ambiguous suggestions | Include file path in suggestion UI |
| Very large projects (10k+ symbols) | Slow completion | Limit to top results; support cancellation |
| Stale index | Wrong or missing suggestions | Rely on IDE's index invalidation |
| Dumb mode (indexing disabled) | No suggestions available | Return empty; graceful degradation |
| Lua version mismatch | Wrong export detection | Use version-specific heuristics |

## Pre-Implementation Requirements

These must be **completed before** Phase 2 implementation:
- [ ] Implement `LuaExportedGlobalIndex` (or enhance existing to distinguish exports)
- [ ] Define Lua version compatibility rules for export detection
- [ ] Add cancellation support to `LuaCrossFileCompletionProvider`
- [ ] Document settings for visibility filtering

## Testing Strategy

### Unit Tests
- Test `GlobalSymbolRankingService.getProjectGlobalSymbols()` in isolation
- Mock `LuaExportedGlobalIndex` with fixed test symbols
- Verify deduplication logic (by name and PSI identity)
- Test proximity calculation for various file structures

### Integration Tests
- Test with multi-file project setup (10+ files, 100+ symbols)
- Verify ranking order matches heuristics
- Test cache invalidation after file modifications
- Verify no regression in Phase 1 (imported symbols)
- Test dumb mode graceful degradation

### Manual Tests
- Type global function name in unrelated file
- Verify suggestions include project-wide symbols
- Verify `_` prefixed symbols are hidden
- Verify performance with large codebase (1000+ symbols)
- Test with mixed Lua versions (5.1, 5.4, Luau)

## See Also

- **Requirements**: [[requirements|Phase 2 Requirements]]
- **Risks & Gaps**: [[risks-and-gaps|Risk Assessment]]
- **Phase 1**: [[../01-auto-import|Phase 1: Imported Symbols]]
- **Phase 3**: [[../03-auto-import|Phase 3: Auto-import]]
