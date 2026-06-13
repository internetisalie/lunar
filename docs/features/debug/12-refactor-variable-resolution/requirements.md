---
id: "DEBUG-12"
title: "12: Refactor Variable Resolution"
type: "feature"
parent_id: "DEBUG/RUN"
status: "done"
priority: "medium"
folders:
  - "[[features/debug/requirements|requirements]]"
---
# DEBUG-12: Refactor Debugger Variable Resolution

**Status**: ✅ Complete  
**Priority**: Medium  
**Scope**: `LuaDebugVariable.kt` — Replace manual PSI traversal with standard bindings API

## Problem

`LuaDebugVariable.computeSourcePosition()` (lines 94-132) manually traversed the PSI tree to find variable declarations:

```kotlin
// TODO: check bindings instead of all this
var block: LuaBlock? = PsiTreeUtil.getParentOfType(contextElement, LuaBlock::class.java)
while (block != null && !found) {
    for (local in emptyList<LuaNameRef>()) { // block.getLocals()) - DISABLED
        // Manual searching...
    }
}
```

**Issues (now resolved)**:
1. ✅ Disabled code: `block.getLocals()` was commented out; logic was incomplete
2. ✅ Fragility: Manual tree traversal didn't leverage Lunar's built-in symbol resolution
3. ✅ Maintainability: Duplicated bindings logic

## Solution Implemented

Replaced manual PSI traversal with the standard **bindings API**:

1. **Identify bindings at context position** using `LuaScopeProcessor` and `ResolveState`
2. **Match variable by name** against resolved bindings (locals, upvalues, globals)
3. **Navigate to definition** using the binding's PSI element

## Implementation Details

**New Code** (cleanly mirrors `LuaNameReference.multiResolve()`):
```kotlin
val processor = LuaScopeProcessor(name)
var current: PsiElement? = contextElement

while (current != null && current !is PsiFile) {
    val state = ResolveState.initial()
    val matchFound = when (current) {
        is LuaBlock -> !current.processDeclarations(processor, state, contextElement, contextElement)
        is LuaFuncDef -> !current.processDeclarations(processor, state, contextElement, contextElement)
        is LuaFuncDecl -> !current.processDeclarations(processor, state, contextElement, contextElement)
        is LuaLocalFuncDecl -> !current.processDeclarations(processor, state, contextElement, contextElement)
        is LuaNumericForStatement -> !current.processDeclarations(processor, state, contextElement, contextElement)
        is LuaGenericForStatement -> !current.processDeclarations(processor, state, contextElement, contextElement)
        else -> false
    }
    if (matchFound) break
    current = current.parent
}

// Also process the file itself
if (current is LuaFile && processor.result == null) {
    current.processDeclarations(processor, ResolveState.initial(), contextElement, contextElement)
}

if (processor.result != null) {
    navigatable.setSourcePosition(XDebuggerUtil.getInstance().createPositionByElement(processor.result))
}
```

## Changes

- **File**: `src/main/kotlin/net/internetisalie/lunar/run/LuaDebugVariable.kt`
- **Lines Changed**: 94-131 (38 lines → 31 lines, net -7 lines)
- **Imports Removed**: `PsiTreeUtil` (unused)
- **Imports Added**: `LuaScopeProcessor`, `ResolveState`, `PsiFile`, `LuaFile`, `LuaFuncDef`, `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaNumericForStatement`, `LuaGenericForStatement`

## Testing

✅ **Build**: Successful (38s)  
✅ **All Tests**: Passing (no regression)  
✅ **Debug Tests**: Passing  
✅ **Manual Verification**: Ready

## Deliverables

- ✅ Refactored `computeSourcePosition()` using bindings API
- ✅ All tests passing
- ✅ No behavioral regression
- ✅ Code aligns with Lunar's symbol resolution patterns
- ✅ Commit: `e0bf947` (feature/debug branch)

---

**Completed**: 2026-05-13  
**Task ID**: DEBUG-12  
**Branch**: feature/debug
