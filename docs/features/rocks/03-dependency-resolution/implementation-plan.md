---
folders:
  - "[[features/rocks/03-dependency-resolution/requirements|requirements]]"
title: "Implementation Plan"
---

# Implementation Plan: Dependency Resolution (ROCKS-03)

## Phase 1: Data Extraction [Must]
- [ ] Bundle `lunajson.lua` into plugin resources for JSON bridge support.
- [ ] Implement `RockspecParser` using the `rockspec.lua` bridge script.
- [ ] Implement `ManifestReader` to parse local `lua_modules` manifest files.

## Phase 2: Graph Logic & Conflict Engine [Must]
- [ ] Implement the recursive `LuaRocksDependencyResolver` to build the full graph.
- [ ] Create `VersionConflictEngine` to identify version mismatches between nodes.
- [ ] Implement the "Reverse Dependency" map for impact analysis.

## Phase 3: Tree View & Visualization [Must]
- [ ] Register the `Dependencies` tab in the LuaRocks tool window.
- [ ] Implement `DependencyTreeComponent` with custom icons for conflicts and transitive nodes.
- [ ] Build the `DependencyInspectorPanel` to show detailed info and reverse dependencies.
- [ ] Add filtering/search logic for the dependency tree.

## Verification Tasks
- [ ] **Unit Test**: Build a complex graph from synthetic rockspecs and verify transitive resolution.
- [ ] **Unit Test**: Test the conflict engine with overlapping and conflicting version constraints.
- [ ] **Manual Test**: Open a project with deep dependencies (e.g., `luacheck` or `busted`) and verify the tree structure and inspector data.
