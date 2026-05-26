---
id: "ROCKS-02-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "ROCKS-02"
status: "planned"
priority: "medium"
folders:
  - "[[features/rocks/02-package-browser/requirements|requirements]]"
---

# Implementation Plan: Package Browser (ROCKS-02)

## Phase 1: Search Service & Caching [Must]
- [ ] Implement `LuaRocksSearchService` for parsing `luarocks search` output.
- [ ] Create `LuaRocksManifestCache` to store list of installed packages and search results.
- [ ] Implement `LuaRocksMetadataService` to fetch detailed package info via `luarocks show`.

## Phase 2: Split-View Tool Window [Must]
- [ ] Register the `LuaRocksPackages` tool window tab.
- [ ] Implement `PackageSearchPanel` with a debounced search field.
- [ ] Build the `SplitView` layout with a list on the left and a detailed preview on the right.
- [ ] Implement basic rendering for package metadata (Title, Description, Homepage).

## Phase 3: Action Handlers & Versioning [Must]
- [ ] Implement `LuaRocksActionHandler` to execute `install` and `uninstall` commands.
- [ ] Add async progress indicators (Task Background) for installation actions.
- [ ] Implement the version selection dropdown in the detail pane.
- [ ] Add refresh logic to invalidate the cache after local changes.

## Verification Tasks
- [ ] **Unit Test**: Parse `luarocks search` and `luarocks show` output into data models.
- [ ] **Integration Test**: Verify that the cache is correctly updated after a mock install command.
- [ ] **Manual Test**: Search for "inspect" and "busted", verify split-view rendering and installation success.
