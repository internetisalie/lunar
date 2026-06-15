---
id: ROCKS-03
title: "03: Dependency Resolution"
type: feature
parent_id: ROCKS
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

### TC-ROCKS-03-03: Version Comparator (unit — INSP design §3.1/§3.2)
- **Input → Expected** (via `LuaRocksVersion.parse(...).compareTo(...)`):
  - `parse("3.1-0") < parse("3.1-1")` → true (revision tiebreak).
  - `parse("3.1") < parse("3.2")` → true.
  - `parse("1.0") < parse("scm-1")` → true (`scm` delta `1.1e8` ≫ `1.0`).
  - `parse("dev-1") > parse("scm-1")` → true (`dev` 1.2e8 > `scm` 1.1e8).
  - `parse("1.0.0").compareTo(parse("1.0"))` → `0` (zero-padding).
- **Action**: Run unit test.
- **Expected Output**: all assertions pass.

### TC-ROCKS-03-04: Constraint Satisfaction (unit — design §3.3/§3.4)
- **Input → Expected**:
  - `DependencySpec.parse("lib >= 2.0, < 4.0")` satisfied by `2.5`, not by `1.9` or `4.0`.
  - `DependencySpec.parse("copas ~> 2.1")` satisfied by `2.1.7`, not by `2.2` or `2.0`.
  - `DependencySpec.parse("penlight")` (no constraint) satisfied by any version.
- **Action**: Run unit test.
- **Expected Output**: all assertions pass.

### TC-ROCKS-03-05: Missing Dependency
- **Input**: project rockspec depends on `ghost`, no `ghost` rock installed.
- **Action**: Open the tool window.
- **Expected Output**: a `ghost` node flagged `MISSING_DEPENDENCY` ("required but not installed").
