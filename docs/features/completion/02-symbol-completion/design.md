---
id: COMP-02-DESIGN
title: "Technical Design"
type: design
parent_id: COMP-02
status: "done"
priority: "high"
folders:
  - "[[features/completion/02-symbol-completion/requirements|requirements]]"
---

# COMP-02: Basic Symbol Completion Design

## Architecture

The symbol completion system will leverage the existing Lua PSI (Program Structure Interface) and scope resolution logic to identify available symbols at a given offset.

### Components

1.  **`LuaSymbolCompletionProvider`**: A completion provider registered to trigger for any Lua identifier context that isn't a table field or keyword.
2.  **`LuaScopeProcessor`**: An implementation of `PsiScopeProcessor` to walk up the PSI tree and collect available declarations.

### Data Models

- **`LuaCompletionVariant`**: A wrapper around `LookupElement` that includes metadata about the symbol:
    - **Tail Text**: Displays the origin scope (e.g., `local`, `parameter`, `global`) and type information if available.
    - **Metadata**: Stores the origin PSI element to support "Quick Documentation" (`Ctrl+Q`).

### Scope Resolution Strategy

- Start from the `PsiElement` at the cursor.
- Walk up the tree using `PsiTreeUtil.getParentOfType(element, LuaBlock.class, true)`.
- For each block, collect declarations using `LuaPsiImplUtil.getLocalDeclarations(block)`.
- Stop at the `LuaFile` level for global symbols.

### Iconography

- **Local**: `AllIcons.Nodes.Variable`
- **Parameter**: `AllIcons.Nodes.Parameter`
- **Global**: `AllIcons.Nodes.Field` (or specific Lua global icon)
