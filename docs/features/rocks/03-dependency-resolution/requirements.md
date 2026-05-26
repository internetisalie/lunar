---
id: "ROCKS-03"
title: "03: Dependency Resolution"
type: "feature"
parent_id: "ROCKS"
status: "planned"
priority: "high"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-03: Dependency Resolution

## Scope
The Dependency Resolution feature provides a visual, hierarchical view of the project's dependency graph. It allows developers to understand complex dependency relationships, detect version conflicts, and manage transitive dependencies.

### In Scope
- **Hierarchical Tree View**: A tool window component showing all top-level and transitive dependencies.
- **Conflict Detection**: Visual indicators for version mismatches (e.g., two packages requiring different versions of the same dependency).
- **Metadata Inspection**: Viewing package details (license, homepage, version constraints) directly from the tree.
- **Impact Analysis**: Showing which packages will be affected if a dependency is updated or removed.
- **Version Pinning**: Identifying and highlighting packages with strict vs. loose version constraints.

### Out of Scope
- Automatic resolution of conflicts (requires manual user decision).
- Direct editing of the `.rockspec` file through the tree (use the editor for that).

## Syntax/Behavior

### Dependency Tree Rendering
- The tree is populated by parsing the local `.rockspec` and cross-referencing with the `lua_modules/lib/luarocks/rocks` manifest or the project-bound `luarocks show` command.
- Nodes represent packages; child nodes represent their dependencies.
- **Transitive Dependencies**: Clearly distinguishable from direct dependencies.

### Conflict Visualization
- **Red/Yellow Warning Icons**: Displayed next to packages with conflicting version requirements.
- **Conflict Tooltip**: Explains why a conflict exists (e.g., "Package A requires >1.0, but Package B requires <0.9").

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **ROCKS-03-01** | **Dependency Tree View** | **Must** | **Pending** | Provide a tool window displaying the hierarchical tree of all dependencies. |
| **ROCKS-03-02** | **Transitive Resolution** | **Must** | **Pending** | Correctly resolve and display dependencies of dependencies. |
| **ROCKS-03-03** | **Conflict Detection** | **Must** | **Pending** | Identify and visually flag version conflicts within the graph. |
| **ROCKS-03-04** | **Package Metadata** | **Should** | **Pending** | Show package info (license, homepage) on node selection. |
| **ROCKS-03-05** | **Reverse Dependency View** | **Could** | **Pending** | Show "What depends on this?" for a selected package. |
| **ROCKS-03-06** | **Search in Tree** | **Should** | **Pending** | Filter the dependency tree by package name or version. |

## Test Cases

### TC-ROCKS-03-01: Deep Tree Resolution
- **Input**: A project with `A -> B -> C`.
- **Action**: Open the Dependency Resolution tool window.
- **Expected Output**: Node `A` shows node `B` as a child, and `B` shows `C` as a child.

### TC-ROCKS-03-02: Conflict Identification
- **Input**: `A` requires `lib >= 2.0`, `B` requires `lib < 1.5`.
- **Action**: View graph.
- **Expected Output**: Both `A` and `B` (or the `lib` node) are flagged with a conflict warning.
