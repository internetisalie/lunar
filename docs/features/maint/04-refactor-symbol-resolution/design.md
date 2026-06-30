---
id: MAINT-04-DESIGN
folders:
  - "[[features/maint/04-refactor-symbol-resolution/requirements|requirements]]"
title: "Technical Design"
type: design
parent_id: MAINT-04
---

# Technical Design: MAINT-04 — Refactor Symbol Resolution (PsiScopeProcessor)

## 1. Architecture Overview

### Current State (Pre-MAINT-04)
```
LuaNameReference.multiResolve()
  ↓
LuaBindingsVisitor.getBindings(file)  [EAGER: walks entire PSI tree]
  ↓
Cache (CachedValuesManager)
  ↓
Returns LuaBindings { references: Map[offset → Reference], global: Scope }
  ↓
Lookup: references[element.textOffset]
```

**Problem:** Full file traversal on every keypress → cache invalidation → latency.

### Target State (Post-MAINT-04)
```
LuaNameReference.multiResolve(name)
  ↓
PsiTreeUtil.treeWalkUp(LuaScopeProcessor(name), element, file)  [LAZY: stops on first match]
  ↓
LuaScopeProcessor.execute(element, state)
  ├─ Check if element is a matching declaration
  ├─ If yes: record result, return FALSE (stop walk)
  └─ If no: return TRUE (continue)
  ↓
If result found: return local binding
Else: fall through to external resolution (unchanged)
```

**Benefit:** Lazy evaluation, scope-driven, conformant to IntelliJ Platform conventions.

---

## 2. Core Components

### 2.1 LuaScopeProcessor

**File:** `src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt` (new)

**Class Hierarchy:**
```
com.intellij.psi.scope.PsiScopeProcessor (interface)
  ↑
  └─ BaseScopeProcessor (abstract, optional helper)
       ↑
       └─ LuaScopeProcessor (concrete)
```

**Pseudocode:**
```kotlin
class LuaScopeProcessor(val name: String) : PsiScopeProcessor {
    var result: PsiElement? = null
    private var found = false

    override fun execute(
        element: PsiElement,
        state: ResolveState
    ): Boolean {
        // Already found a match; stop the walk
        if (found) return false

        // Check if element is a named declaration matching our search
        when {
            element is LuaLocalVarDecl -> {
                // Extract identifier from attName
                if (element.attNameList.any { it.nameRef.identifier.text == name }) {
                    result = element.attNameList.first { it.nameRef.identifier.text == name }.nameRef.identifier
                    found = true
                    return false  // Stop walk
                }
            }
            element is LuaLocalFuncDecl -> {
                if (element.nameRef.identifier.text == name) {
                    result = element.nameRef.identifier
                    found = true
                    return false
                }
            }
            element is LuaParList -> {
                // Check parameter names
                val nameList = element.nameList
                if (nameList != null) {
                    val match = nameList.nameRefList.firstOrNull { it.identifier.text == name }
                    if (match != null) {
                        result = match.identifier
                        found = true
                        return false
                    }
                }
                // Check variadic arg (...)
                if (element.vararg != null && name == "...") {
                    result = element.vararg
                    found = true
                    return false
                }
            }
            element is LuaNumericForStatement -> {
                if (element.identifier.text == name) {
                    result = element.identifier
                    found = true
                    return false
                }
            }
            element is LuaGenericForStatement -> {
                val match = element.nameList.nameRefList.firstOrNull { it.identifier.text == name }
                if (match != null) {
                    result = match.identifier
                    found = true
                    return false
                }
            }
            // Self parameter in method declarations
            element is LuaFuncDecl -> {
                if (name == "self" && element.funcName.funcNameMethod != null) {
                    // Return implicit self identifier
                    result = element.funcName.funcNameMethod!!.nameRef.identifier
                    found = true
                    return false
                }
            }
            element is LuaLocalFuncDecl -> {
                if (name == "self" && element.parent is LuaFuncDefStmt) {
                    // Check if it's a method declaration (TODO: validate structure)
                    // Local functions can't be methods in Lua, so skip
                }
            }
        }

        return true  // Continue walk
    }

    override fun getHint(hintKey: com.intellij.psi.PsiElement.Key<*>): Any? = null

    override fun handleEvent(
        event: com.intellij.psi.PsiScopeProcessor.Event,
        associated: Any?
    ) {}
}
```

**Completion Variant:**
```kotlin
class LuaCompletionScopeProcessor : PsiScopeProcessor {
    val results: MutableSet<String> = mutableSetOf()

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        // Collect all names, don't stop
        when (element) {
            is LuaLocalVarDecl -> {
                element.attNameList.forEach { results.add(it.nameRef.identifier.text) }
            }
            is LuaLocalFuncDecl -> results.add(element.nameRef.identifier.text)
            is LuaParList -> {
                element.nameList?.nameRefList?.forEach { results.add(it.identifier.text) }
                if (element.vararg != null) results.add("...")
            }
            is LuaNumericForStatement -> results.add(element.identifier.text)
            is LuaGenericForStatement -> {
                element.nameList.nameRefList.forEach { results.add(it.identifier.text) }
            }
        }
        return true  // Always continue
    }

    override fun getHint(hintKey: com.intellij.psi.PsiElement.Key<*>): Any? = null

    override fun handleEvent(
        event: com.intellij.psi.PsiScopeProcessor.Event,
        associated: Any?
    ) {}
}
```

---

### 2.2 PSI Element: processDeclarations()

Each scope-introducing PSI element must override `processDeclarations()` to feed declarations to the processor.

**File:** `LuaElementImpl` base class (or mixin implementations for each element)

**Pattern (apply to all scope elements):**

```kotlin
interface PsiScopeProcessorHelper {
    fun processLocalDeclarations(
        processor: PsiScopeProcessor,
        state: ResolveState,
        lastParent: PsiElement?,
        place: PsiElement
    ): Boolean
}

// Mixin for LuaBlock
fun LuaBlock.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // 1. Iterate statements in order
    for (statement in statementList) {
        // 2. Stop at or after lastParent (stop forward references)
        if (lastParent != null && statement.textOffset >= lastParent.textOffset) {
            break
        }

        // 3. Process local variable declarations
        if (statement is LuaLocalVarDeclStatement) {
            val localVarDecl = statement.localVarDecl
            for (attName in localVarDecl.attNameList) {
                if (!processor.execute(localVarDecl, state)) {
                    return false  // Processor found match, stop
                }
            }
        }

        // 4. Process local function declarations
        if (statement is LuaLocalFuncDeclStatement) {
            val localFuncDecl = statement.localFuncDecl
            if (!processor.execute(localFuncDecl, state)) {
                return false
            }
        }
    }

    return true  // Continue walk to parent scope
}

// For LuaFile
fun LuaFile.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // Delegate to root block's processDeclarations
    val block = findChildByClass(LuaBlock::class.java) ?: return true
    return block.processDeclarations(processor, state, lastParent, place)
}

// For function scopes (LuaFuncDef, LuaFuncDecl)
fun LuaFuncDef.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // 1. Process parameter list first
    val parList = parList
    if (parList != null) {
        if (!processor.execute(parList, state)) {
            return false
        }
    }

    // 2. Process function body
    val block = block ?: return true
    return block.processDeclarations(processor, state, lastParent, place)
}

fun LuaFuncDecl.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    // For methods, expose implicit "self" parameter
    if (funcName.funcNameMethod != null) {
        // Create a synthetic binding for "self"
        // This is a design choice: we can either:
        // A) Have LuaScopeProcessor check for "self" specially
        // B) Execute a synthetic element
        // Prefer A (check in processor)
    }

    // Process parList and body
    val parList = funcName.parList ?: return true
    if (!processor.execute(parList, state)) {
        return false
    }

    val block = block ?: return true
    return block.processDeclarations(processor, state, lastParent, place)
}

// For loop statements
fun LuaNumericForStatement.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    if (!processor.execute(this, state)) {
        return false
    }
    val block = block
    return block?.processDeclarations(processor, state, lastParent, place) ?: true
}

fun LuaGenericForStatement.processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement
): Boolean {
    if (!processor.execute(nameList, state)) {
        return false
    }
    val block = block
    return block?.processDeclarations(processor, state, lastParent, place) ?: true
}
```

---

### 2.3 LuaNameReference: multiResolve() Refactoring

**File:** `LuaNameReference.kt` (refactored)

**Current code (to replace):**
```kotlin
override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val element = myElement ?: return emptyArray()
    val bindings = LuaBindingsVisitor.getBindings(element)  // ← REMOVE THIS
    val reference = bindings.references[element.textOffset] ?: return emptyArray()
    // ... rest of external resolution
}
```

**New code:**
```kotlin
override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val element = myElement ?: return emptyArray()
    val name = element.text.substring(textRange.startOffset, textRange.endOffset)
    val results = mutableListOf<ResolveResult>()

    // === PHASE 1: Local Resolution (LAZY) ===
    val processor = LuaScopeProcessor(name)
    PsiTreeUtil.treeWalkUp(processor, element, element.containingFile, ResolveState.initial())

    if (processor.result != null) {
        results.add(PsiElementResolveResult(processor.result!!))
        return results.distinctBy { it.element }.toTypedArray()
    }

    // === PHASE 2: External Resolution (unchanged) ===
    // Only construct LuaImports if local resolution failed
    val platformQuery = VirtualFilesQuery(
        ProjectScope.getLibrariesScope(element.project),
        PlatformLibraryIndex.getPackageFiles(element.project),
    )
    val requiresQuery = RequiredFilesQuery(
        ProjectScope.getProjectScope(element.project),
        PathConfiguration.getProjectSourcePathPatterns(element.project),
        // TODO: Extract requires list (see below)
    )

    val importedResults = queryFiles(platformQuery, requiresQuery)
    val referenceName = name  // For now, assume simple name (no dots)

    importedResults.forEach { filesQueryResults ->
        filesQueryResults.results.forEach { filesQueryResult ->
            collectFileResults(results, referenceName, filesQueryResult)
        }
    }

    val project = element.project
    val scope = GlobalSearchScope.allScope(project)

    StubIndex.getElements(LuaClassNameIndex.KEY, referenceName, project, scope, LuaLocalVarDecl::class.java).forEach { decl ->
        results.add(PsiElementResolveResult(decl))
    }

    StubIndex.getElements(LuaAliasIndex.KEY, referenceName, project, scope, LuaLocalVarDecl::class.java).forEach { decl ->
        results.add(PsiElementResolveResult(decl))
    }

    StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, referenceName, project, scope, LuaFuncDecl::class.java).forEach { decl ->
        results.add(PsiElementResolveResult(decl))
    }

    return results.distinctBy { it.element }.toTypedArray()
}
```

**Challenge: Extract requires list**
Currently, `requires` is extracted by `LuaBindingsVisitor.getBindings()`. We need an alternative:
```kotlin
private fun extractRequires(file: PsiFile): List<String> {
    val requires = mutableListOf<String>()
    
    file.accept(object : LuaRecursiveVisitor() {
        override fun visitFuncCall(o: LuaFuncCall) {
            val varVal = o.varOrExp.`var` ?: return
            val varElements = getVarElements(varVal) ?: return
            if (varElements.size != 1 || varElements.first.text != "require") return
            if (o.nameAndArgsList.size != 1) return
            
            val nameAndArgs = o.nameAndArgsList[0]
            if (nameAndArgs.methodExpr != null) return
            
            var exprString: PsiElement? = nameAndArgs.args.string
            if (exprString == null) {
                val exprList = nameAndArgs.args.exprList?.exprList ?: return
                if (exprList.size != 1) return
                exprString = (exprList.first() as? LuaTerminalExpr)?.string ?: return
            }
            
            val packageName = extractLuaString(exprString.text)
            requires.add(packageName)
        }
    })
    
    return requires
}
```

Or, cache this separately (similar to `LuaBindingsVisitor` but focused only on `require()` calls).

---

### 2.4 LuaNameReference: getVariants() Refactoring

**Current (to replace):**
```kotlin
override fun getVariants(): Array<Any> {
    val element = myElement ?: return emptyArray()
    val bindings = LuaBindingsVisitor.getBindings(element)  // ← REMOVE THIS
    val reference = bindings.references[element.textOffset] ?: return emptyArray()
    if (!reference.defined) return emptyArray()
    return arrayOf(LookupElementBuilder...)
}
```

**New:**
```kotlin
override fun getVariants(): Array<Any> {
    val element = myElement ?: return emptyArray()
    val results = mutableListOf<LookupElement>()

    // Collect all visible local names
    val processor = LuaCompletionScopeProcessor()
    PsiTreeUtil.treeWalkUp(processor, element, element.containingFile, ResolveState.initial())

    processor.results.forEach { name ->
        val lookupElement = LookupElementBuilder
            .create(name)
            .withIcon(PlatformIcons.VARIABLE_ICON)
        results.add(lookupElement)
    }

    // TODO: Add global names, require'd modules, etc.

    return results.toTypedArray()
}
```

---

## 3. Data Flow Examples

### Example 1: Simple Local Variable Resolution

**Code:**
```lua
local x = 5
print(x)  -- cursor here
```

**Flow:**
1. User hovers over/navigates to `x` (the reference)
2. `LuaNameReference.multiResolve()` called
3. Create `LuaScopeProcessor("x")`
4. `PsiTreeUtil.treeWalkUp(processor, refElement, file, ResolveState.initial())`
   - Start at reference `x` (inside `print(x)` call)
   - Walk up: `LuaFuncCall` → `LuaExpr` → `LuaStatement` → `LuaBlock`
5. In `LuaBlock.processDeclarations()`:
   - Iterate statements in order
   - Find `LuaLocalVarDecl` with name "x"
   - `processor.execute(localVarDecl, state)` → `execute()` sees "x" matches
   - Return `false` (stop)
6. `processor.result` set to the identifier
7. Return as `ResolveResult`

---

### Example 2: Shadowing

**Code:**
```lua
local x = 1
do
  local x = 2
  print(x)  -- cursor here
end
```

**Flow:**
1. Reference to `x` inside inner do block
2. `treeWalkUp` starts at reference
3. Walk up: ... → inner `LuaBlock`
4. Inner block's `processDeclarations()` finds `local x = 2` first
5. `processor.result` = identifier from inner declaration
6. Stops; never reaches outer `local x = 1`

---

### Example 3: Function Parameter

**Code:**
```lua
function foo(x)
  print(x)  -- cursor here
end
```

**Flow:**
1. Reference to `x` inside function body
2. `treeWalkUp`: ... → `LuaBlock` (function body)
3. `LuaBlock.processDeclarations()` calls parent's `processDeclarations()`
4. Parent is `LuaFuncDef`, which processes `LuaParList` first
5. `processor.execute(parList, state)` → parList executes and finds "x"
6. Result = parameter identifier

---

## 4. Edge Cases

### 4.1 Forward References
**Issue:** Can we reference a variable declared later in the same block?
```lua
print(x)  -- Reference
local x = 5  -- Declaration
```

**Solution:** In `LuaBlock.processDeclarations()`, check `lastParent`:
```kotlin
if (lastParent != null && statement.textOffset >= lastParent.textOffset) {
    break  // Stop; don't see declarations at or after the usage site
}
```

**Result:** Reference fails to resolve (correct Lua behavior).

---

### 4.2 Redeclaration in Same Scope
```lua
local x = 1
local x = 2  -- Redeclaration
print(x)  -- Should resolve to which x?
```

**Lua semantics:** The innermost declaration (closest to reference) shadows.

**Solution:** Walk backwards (or iterate forward and keep overwriting). Current approach (iterate forward, break at lastParent) naturally finds the *first* declaration. We need to iterate *backward* or collect all and pick the closest.

**Revised approach:**
```kotlin
// Collect all matching declarations up to lastParent
val matches = mutableListOf<PsiElement>()

for (statement in statementList.reversed()) {  // Reverse to process from bottom up
    if (lastParent != null && statement.textOffset >= lastParent.textOffset) {
        continue  // Skip declarations after usage
    }

    if (statement is LuaLocalVarDeclStatement) {
        statement.localVarDecl.attNameList.forEach { attName ->
            if (attName.nameRef.identifier.text == targetName) {
                matches.add(attName.nameRef.identifier)
            }
        }
    }
}

// Use the closest match (first in reversed order = most recent)
if (matches.isNotEmpty()) {
    processor.execute(matches.first(), state)
}
```

---

### 4.3 Cross-Scope Shadowing
```lua
local x = 1
do
  local y = 2
  do
    local x = 3  -- Shadows outer x
    print(x)     -- Should resolve to inner x = 3
  end
end
```

**Solution:** `treeWalkUp` naturally traverses scopes in order, so the innermost scope's `processDeclarations()` is called first. Once a match is found, the walk stops.

---

### 4.4 Method "self" Parameter
```lua
function obj:method(a)
  print(self)  -- Reference to implicit self
  print(a)     -- Reference to parameter
end
```

**Solution:** In `LuaScopeProcessor.execute()`:
```kotlin
if (element is LuaFuncDecl && name == "self") {
    if (element.funcName.funcNameMethod != null) {
        // Implicit self; return method name identifier
        result = element.funcName.funcNameMethod!!.nameRef.identifier
        return false
    }
}
```

---

## 5. Integration Points

### 5.1 Removal of LuaBindingsVisitor Calls

**Call sites to remove:**

1. **`LuaNameReference.multiResolve()`**
   - Current: `val bindings = LuaBindingsVisitor.getBindings(element)`
   - New: Direct `treeWalkUp` + `LuaScopeProcessor`

2. **`LuaNameReference.getVariants()`**
   - Current: `val bindings = LuaBindingsVisitor.getBindings(element)`
   - New: Direct `treeWalkUp` + `LuaCompletionScopeProcessor`

3. **Other PsiReference implementations**
   - `LuaLabelReference` (label resolution — MAINT-02, out of scope here)
   - Any custom reference contributors

### 5.2 Preservation of LuaFileBindingsIndex

**Constraint:** `LuaFileBindingsIndexer` continues to use `LuaBindingsVisitor.getBindings()`:

```kotlin
class LuaFileBindingsIndexer : ForwardIndexer<LuaFileBindingsRecord>() {
    override fun computeValue(inputData: FileContent): LuaFileBindingsRecord {
        val fileBindings = mutableListOf<LuaBinding>()
        val bindings = LuaBindingsVisitor.getBindings(inputData.psiFile)  // ← OK: indexer only
        walk(fileBindings, bindings.global, null)
        return LuaFileBindingsRecord(fileBindings.sortedBy { it.name }, bindings.requires)
    }
}
```

**No changes needed.** The indexer remains the only consumer of `LuaBindingsVisitor`.

---

## 6. Refactoring Strategy

### Phase 1: Implement Core Infrastructure
1. Create `LuaScopeProcessor` (new file)
2. Create `LuaCompletionScopeProcessor` (new file or same file)
3. Add `processDeclarations()` to `LuaBlock` mixin
4. Test with unit tests: ensure `treeWalkUp` finds declarations correctly

### Phase 2: Implement Scope-Producing Elements
1. Add `processDeclarations()` to `LuaFile`, `LuaFuncDef`, `LuaFuncDecl`, `LuaLocalFuncDecl`
2. Add `processDeclarations()` to `LuaNumericForStatement`, `LuaGenericForStatement`
3. Test each incrementally

### Phase 3: Refactor LuaNameReference
1. Implement new `multiResolve()` using `treeWalkUp`
2. Run MAINT-04-DR Phase 1 tests (54 active tests)
3. If all pass, remove old `LuaBindingsVisitor.getBindings()` call

### Phase 4: Refactor getVariants()
1. Implement new `getVariants()` using `LuaCompletionScopeProcessor`
2. Manual testing in IDE

### Phase 5: Cleanup & Verification
1. Remove `LuaBindingsVisitor` call sites outside indexer
2. Run full test suite
3. Performance testing

---

## 7. Testing Strategy During Implementation

### Unit Tests (Per Component)
```kotlin
class LuaScopeProcessorTest {
    @Test
    fun testFindsLocalVariable() { }

    @Test
    fun testStopsOnFirstMatch() { }

    @Test
    fun testDoesNotFindForwardReferences() { }
}

class LuaBlockProcessDeclarationsTest {
    @Test
    fun testProcessesLocalVarDecl() { }

    @Test
    fun testProcessesLocalFuncDecl() { }

    @Test
    fun testStopsAtLastParent() { }
}
```

### Integration Tests (Per Refactoring Phase)
- **After Phase 1:** LuaSymbolResolutionTest (10 tests) should pass
- **After Phase 2:** LuaSymbolResolutionTest (10) + LuaGlobalResolutionTest (14) + LuaRequireResolutionTest (12) = 36 tests
- **After Phase 3:** All 54 MAINT-04-DR tests
- **Final:** Full `./gradlew test` suite

---

## 8. Performance Considerations

### 8.1 Walk Termination
`treeWalkUp` stops the moment `PsiScopeProcessor.execute()` returns `false`. This is **O(scope depth)**, not **O(file size)**.

For a 10,000-line file:
- Eager `LuaBindingsVisitor`: ~10,000 statements traversed
- Lazy `treeWalkUp`: ~5–10 scope ancestors (function nesting level)

**Expected speedup:** 100–1000x for name resolution in deeply nested scopes.

### 8.2 No Caching of Full Bindings
The old approach cached the entire `LuaBindings` object. The new approach computes on-demand.

**Trade-off:** 
- Memory: Lower (no full bindings cache)
- CPU: Per-reference computation but faster due to lazy termination

### 8.3 Requires List Extraction
Extracting the `requires` list (for external resolution) still requires a partial file walk, but:
- Only done if local resolution fails
- Can be cached separately (e.g., via a simpler `CachedValue`)
- Much lighter than full `LuaBindings`

---

## 9. Known Unknowns & Risks

| Issue | Mitigation |
|-------|-----------|
| PSI structure changes between Lua versions | Implement defensively (null checks, type guards) |
| `treeWalkUp` doesn't match `LuaBindingsVisitor` scope semantics | MAINT-04-DR tests validate exact matching |
| Edge cases in `processDeclarations()` ordering | Add comprehensive unit tests per element type |
| Label resolution not compatible with new architecture | Out of scope (MAINT-02); document constraint |
| Completion performance on `treeWalkUp` + full-file collection | Implement lazy collection with early termination |

---

## 10. Reference: IntelliJ Platform Conventions

### PsiScopeProcessor Examples
- **Kotlin:** `KtPsiElement.processDeclarations()` in `kotlin-plugin`
- **Java:** `PsiClass.processDeclarations()` in `intellij-community`
- **Python:** `PyPsiFunctionType.processDeclarations()` in `python-plugin`

All follow the same pattern:
1. Override `processDeclarations(processor, state, lastParent, place)`
2. Iterate declarations in source order
3. Call `processor.execute(element, state)` for each
4. Stop if processor returns `false`
5. Return `true` to allow parent scope to be processed

Lunar's implementation will follow this convention exactly.

